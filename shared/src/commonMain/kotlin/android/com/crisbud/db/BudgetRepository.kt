package android.com.crisbud.db

import app.cash.sqldelight.db.SqlDriver

data class StoredTransaction(
    val id: Long,
    val title: String,
    val category: String,
    val amount: Double,
    val kind: RecordKind,
    val date: String,
    val wallet: String,
    val quantity: Long = 1
) {
    val income: Boolean get() = kind == RecordKind.INCOME
}

data class StoredBudget(val id: Long, val category: String, val spent: Double, val limit: Double)
data class StoredRecurring(val id: Long, val title: String, val category: String, val amount: Double, val dueDay: Long, val cadence: RecurringCadence)
data class StoredWallet(val name: String, val balance: Double)

class BudgetRepository internal constructor(driver: SqlDriver) {
    constructor() : this(createDatabaseDriver())

    private val database = CrisbudDatabase(
        driver,
        Category.Adapter(RecordKind.adapter),
        Entries.Adapter(RecordKind.adapter)
    )
    private val q = database.crisbudDatabaseQueries

    fun seedIfNeeded(month: String) = database.transaction {
        DatabaseSeeder.seed(q, month, ::ensureCategory, ::walletId)
    }

    fun transactions(): List<StoredTransaction> = readTransactions(null)
    fun transactions(month: String): List<StoredTransaction> = readTransactions(month)

    private fun readTransactions(month: String?): List<StoredTransaction> =
        q.selectTransactions(month).executeAsList().map {
            StoredTransaction(it.id, it.title, it.category, it.amount, it.kind, it.date, it.wallet, it.quantity)
        }

    fun categories(): List<Pair<String, String>> = q.selectCategories().executeAsList().map { it.name to it.icon }
    fun wallets(): List<String> = q.selectWallets().executeAsList().map { it.name }
    fun walletBalances(): List<StoredWallet> = q.selectWallets().executeAsList().map { StoredWallet(it.name, it.balance) }

    fun budgets(month: String): List<StoredBudget> = q.selectBudgetsForMonth(month).executeAsList().map {
        StoredBudget(it.id, it.category, it.spent, it.limit_amount)
    }

    fun recurring(): List<StoredRecurring> = q.selectRecurringItems().executeAsList().map {
        StoredRecurring(it.id, it.title, it.category, it.amount, it.due_day, RecurringCadence.fromDatabase(it.cadence))
    }

    fun addTransaction(
        title: String,
        category: String,
        amount: Double,
        income: Boolean,
        date: String,
        wallet: String,
        quantity: Long = 1
    ) {
        validateTransactionQuantity(quantity, amount, income)
        database.transaction {
            q.insertTransaction(
                null, title, categoryId(category), amount, RecordKind.fromIncome(income), date, walletId(wallet), quantity
            )
        }
    }

    fun deleteTransaction(id: Long) = q.deleteTransaction(id)

    fun updateTransaction(
        id: Long,
        title: String,
        category: String,
        amount: Double,
        income: Boolean,
        date: String,
        wallet: String,
        quantity: Long = 1
    ) = database.transaction {
        validateTransactionQuantity(quantity, amount, income)
        q.updateTransaction(
            title, categoryId(category), amount, RecordKind.fromIncome(income), date, walletId(wallet), quantity, id
        )
    }

    private fun validateTransactionQuantity(quantity: Long, amount: Double, income: Boolean) {
        require(quantity > 0) { "Quantity must be a positive whole number." }
        require(!income || quantity == 1L) { "Income transactions must have a quantity of 1." }
        require((amount * quantity).isFinite()) { "The transaction total is too large." }
    }

    fun addWallet(name: String) = q.ensureWallet(name)
    fun updateWalletBalance(name: String, balance: Double) = q.updateWalletBalance(balance, name)

    fun renameWallet(oldName: String, newName: String): Boolean = database.transactionWithResult {
        if (q.walletNameConflictCount(newName, oldName).executeAsOne() > 0) return@transactionWithResult false
        q.renameWallet(newName, oldName)
        true
    }

    fun deleteWallet(name: String): Boolean = database.transactionWithResult {
        val id = q.walletId(name).executeAsOneOrNull() ?: return@transactionWithResult false
        if (wallets().size <= 1 || q.walletUsageCount(id).executeAsOne() > 0) return@transactionWithResult false
        q.deleteWallet(id)
        true
    }

    fun addCategory(name: String, icon: String = "•") = q.ensureCategory(name, icon, RecordKind.EXPENSE)

    fun renameCategory(oldName: String, newName: String): Boolean = database.transactionWithResult {
        if (oldName == newName) return@transactionWithResult true
        if (categories().any { (name) -> name.equals(newName, ignoreCase = true) }) return@transactionWithResult false
        q.renameCategory(newName, oldName)
        true
    }

    fun deleteCategory(name: String): Boolean = database.transactionWithResult {
        val id = q.categoryId(name).executeAsOneOrNull() ?: return@transactionWithResult false
        if (q.categoryUsageCount(id).executeAsOne() > 0) return@transactionWithResult false
        q.deleteCategory(id)
        true
    }

    fun addBudget(category: String, month: String, limit: Double) = database.transaction {
        q.insertBudget(categoryId(category), month, limit)
    }

    fun updateBudget(id: Long, category: String, limit: Double) = database.transaction {
        q.updateBudget(categoryId(category), limit, id)
    }

    fun deleteBudget(id: Long) = q.deleteBudget(id)

    fun addRecurring(title: String, category: String, amount: Double, dueDay: Long) = database.transaction {
        q.insertRecurringItem(title, categoryId(category), amount, dueDay, RecurringCadence.MONTHLY.databaseValue)
    }

    fun updateRecurring(id: Long, title: String, category: String, amount: Double, dueDay: Long) = database.transaction {
        q.updateRecurringItem(title, categoryId(category), amount, dueDay, id)
    }

    fun deleteRecurring(id: Long) = q.deleteRecurringItem(id)

    private fun categoryId(name: String) =
        requireNotNull(q.categoryId(name).executeAsOneOrNull()) { "Unknown category: $name" }

    private fun walletId(name: String) =
        requireNotNull(q.walletId(name).executeAsOneOrNull()) { "Unknown wallet: $name" }

    private fun ensureCategory(name: String, kind: RecordKind = RecordKind.EXPENSE): Long {
        q.ensureCategory(name, "•", kind)
        return categoryId(name)
    }

    fun exportJson(): String = database.transactionWithResult {
        val tx = transactions().joinToString(",") { "{\"title\":\"${escape(it.title)}\",\"category\":\"${escape(it.category)}\",\"amount\":${it.amount},\"income\":${it.income},\"date\":\"${it.date}\",\"wallet\":\"${escape(it.wallet)}\",\"quantity\":${it.quantity}}" }
        val cats = categories().joinToString(",") { "{\"name\":\"${escape(it.first)}\",\"icon\":\"${escape(it.second)}\"}" }
        val recurringItems = q.exportRecurringItems().executeAsList().filter { it.active }.sortedBy { it.due_day }.joinToString(",") { "{\"title\":\"${escape(it.title)}\",\"category\":\"${escape(it.category)}\",\"amount\":${it.amount},\"dueDay\":${it.due_day},\"cadence\":\"${escape(it.cadence)}\"}" }
        val walletItems = walletBalances().joinToString(",") { "{\"name\":\"${escape(it.name)}\",\"balance\":${it.balance}}" }
        val budgetItems = q.exportBudgets().executeAsList().joinToString(",") { "{\"category\":\"${escape(it.category)}\",\"month\":\"${it.month}\",\"limit\":${it.limit_amount}}" }
        "{\"transactions\":[$tx],\"wallets\":[$walletItems],\"categories\":[$cats],\"budgets\":[$budgetItems],\"recurring\":[$recurringItems]}"
    }

    private val backupClearOrder = listOf(
        BackupTable.ENTRIES, BackupTable.BUDGET, BackupTable.RECURRINGITEM, BackupTable.CATEGORY, BackupTable.WALLET
    )

    private fun forEachBackupRow(emit: (BackupTable, List<Any>) -> Unit) {
        q.selectCategories().executeAsList().forEach {
            emit(BackupTable.CATEGORY, listOf(it.name, it.icon, it.kind.code))
        }
        walletBalances().forEach {
            emit(BackupTable.WALLET, listOf(it.name, it.balance))
        }
        transactions().sortedBy { it.id }.forEach {
            emit(
                BackupTable.ENTRIES,
                listOf(it.id, it.title, it.category, it.amount, it.kind.code, it.date, it.wallet, it.quantity)
            )
        }
        q.exportBudgets().executeAsList().forEach {
            emit(BackupTable.BUDGET, listOf(it.id, it.category, it.month, it.limit_amount))
        }
        q.exportRecurringItems().executeAsList().forEach {
            emit(
                BackupTable.RECURRINGITEM,
                listOf(it.id, it.title, it.category, it.amount, it.due_day, it.cadence, if (it.active) 1 else 0)
            )
        }
    }

    fun exportCsv(): String = database.transactionWithResult {
        buildString {
            appendExpenseCsvHeader()
            transactions().filter { it.kind == RecordKind.EXPENSE }.sortedBy { it.id }.forEach {
                appendExpenseCsvRow(it)
            }
        }
    }

    fun importCsv(csv: String) {
        val rows = readExpenseCsv(csv)
        database.transaction {
            val categoryIds = q.selectCategories().executeAsList().associate { it.name to it.id }.toMutableMap()
            val walletIds = q.selectWallets().executeAsList().associate { it.name to it.id }.toMutableMap()
            rows.forEach { row ->
                try {
                    validateTransactionQuantity(row.quantity, row.amount, income = false)
                    val category = categoryIds.resolveCsvReference(row.category, "category") { ensureCategory(it) }
                    val wallet = walletIds.resolveCsvReference(row.wallet, "wallet") { name ->
                        q.ensureWallet(name)
                        walletId(name)
                    }
                    q.insertTransaction(
                        null, row.title, category, row.amount, RecordKind.EXPENSE, row.date, wallet, row.quantity
                    )
                } catch (exception: Exception) {
                    throw IllegalArgumentException("CSV row ${row.number}: ${exception.message.orEmpty()}", exception)
                }
            }
        }
    }

    private fun MutableMap<String, Long>.resolveCsvReference(
        name: String,
        field: String,
        insert: (String) -> Long
    ): Long {
        this[name]?.let { return it }
        val trimmedName = name.trim()
        this[trimmedName]?.let { return it }
        val matches = keys.filter { it.trim().equals(trimmedName, ignoreCase = true) }
        require(matches.size <= 1) { "Multiple records match the $field \"$trimmedName\". Use its exact name." }
        return matches.singleOrNull()?.let { getValue(it) } ?: getOrPut(trimmedName) { insert(trimmedName) }
    }

    fun exportSql(): String = database.transactionWithResult {
        buildString {
            appendLine(SQL_BACKUP_HEADER)
            appendLine("BEGIN TRANSACTION;")
            backupClearOrder.forEach { appendLine("DELETE FROM ${it.sqlName};") }
            forEachBackupRow { table, fields ->
                appendInsert("${table.sqlName}(${table.columns.joinToString(", ")})", fields)
            }
            appendLine("COMMIT;")
        }
    }

    fun importSql(sql: String) {
        val statements = readSqlBackup(sql)
        database.transaction {
            statements.forEach { statement ->
                when (statement) {
                    is BackupStatement.Delete -> clearBackupTable(statement.table)
                    is BackupStatement.Insert -> importBackupRow(statement)
                }
            }
        }
    }

    private fun clearBackupTable(table: BackupTable) {
        when (table) {
            BackupTable.ENTRIES -> q.clearEntries()
            BackupTable.BUDGET -> q.clearBudgets()
            BackupTable.RECURRINGITEM -> q.clearRecurringItems()
            BackupTable.CATEGORY -> q.clearCategories()
            BackupTable.WALLET -> q.clearWallets()
        }
    }

    private fun importBackupRow(row: BackupStatement.Insert) {
        when (row.table) {
            BackupTable.CATEGORY -> q.restoreCategory(row.text("name"), row.text("icon", "•"),
                RecordKind.fromIncome(row.integer("is_income", "0") != 0L))
            BackupTable.WALLET -> q.restoreWallet(row.text("name"), row.decimal("balance", "0"))
            BackupTable.ENTRIES -> {
                val income = row.integer("income") != 0L
                val amount = row.decimal("amount")
                val quantity = row.integer("quantity", "1")
                validateTransactionQuantity(quantity, amount, income)
                val wallet = row.text("wallet", "Cash")
                q.ensureWallet(wallet)
                val kind = RecordKind.fromIncome(income)
                q.insertTransaction(
                    row.id(), row.text("title"), ensureCategory(row.text("category"), kind),
                    amount, kind, row.text("date"), walletId(wallet), quantity
                )
            }
            BackupTable.BUDGET -> q.restoreBudget(row.id(), ensureCategory(row.text("category")),
                row.text("month"), row.decimal("limit_amount"))
            BackupTable.RECURRINGITEM -> q.restoreRecurringItem(row.id(), row.text("title"),
                ensureCategory(row.text("category")), row.decimal("amount"), row.integer("due_day"),
                row.text("cadence", "Monthly"), row.integer("active", "1") != 0L)
        }
    }

    private fun StringBuilder.appendInsert(tableAndColumns: String, values: List<Any>) {
        val literals = values.joinToString(", ") { if (it is String) "'${it.replace("'", "''")}'" else it.toString() }
        appendLine("INSERT INTO $tableAndColumns VALUES ($literals);")
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
