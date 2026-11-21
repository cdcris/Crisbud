package android.com.crisbud.db

import app.cash.sqldelight.db.SqlDriver

data class StoredTransaction(
    val id: Long,
    val title: String,
    val category: String,
    val amount: Double,
    val kind: RecordKind,
    val date: String,
    val wallet: String
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
        if (q.selectWallets().executeAsList().isEmpty()) {
            listOf("GCash", "MariBank", "Cash").forEach(q::ensureWallet)
        }
        if (q.selectCategories().executeAsList().isEmpty()) {
            listOf("Food & Drink" to "🍜", "Transport" to "🚆", "Home" to "⌂", "Shopping" to "🛍",
                "Health" to "＋", "Income" to "↗", "Other" to "•")
                .forEach { (name, icon) -> q.ensureCategory(name, icon, RecordKind.fromIncome(name == "Income")) }
        }
        if (q.selectBudgetsForMonth(month).executeAsList().isEmpty()) {
            listOf("Food & Drink" to 500.0, "Transport" to 180.0, "Home" to 1200.0)
                .forEach { (name, limit) -> q.insertBudget(ensureCategory(name), month, limit) }
        }
        if (q.selectRecurringItems().executeAsList().isEmpty()) {
            q.insertRecurringItem("Rent", ensureCategory("Home"), 1200.0, 1, RecurringCadence.MONTHLY.databaseValue)
            q.insertRecurringItem("Streaming", ensureCategory("Subscriptions"), 14.99, 18, RecurringCadence.MONTHLY.databaseValue)
        }
        if (q.selectTransactions(null).executeAsList().isEmpty()) {
            q.ensureWallet("Cash")
            val cash = walletId("Cash")
            q.insertTransaction(null, "Grocery run", ensureCategory("Food & Drink"), 42.80, RecordKind.EXPENSE, "$month-12", cash)
            q.insertTransaction(null, "Monthly salary", ensureCategory("Income", RecordKind.INCOME), 3200.0, RecordKind.INCOME, "$month-01", cash)
            q.insertTransaction(null, "Train pass", ensureCategory("Transport"), 32.0, RecordKind.EXPENSE, "$month-13", cash)
        }
    }

    fun transactions(): List<StoredTransaction> = readTransactions(null)
    fun transactions(month: String): List<StoredTransaction> = readTransactions(month)

    private fun readTransactions(month: String?): List<StoredTransaction> =
        q.selectTransactions(month).executeAsList().map {
            StoredTransaction(it.id, it.title, it.category, it.amount, it.kind, it.date, it.wallet)
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

    fun addTransaction(title: String, category: String, amount: Double, income: Boolean, date: String, wallet: String) {
        database.transaction {
            q.insertTransaction(null, title, categoryId(category), amount, RecordKind.fromIncome(income), date, walletId(wallet))
        }
    }

    fun deleteTransaction(id: Long) = q.deleteTransaction(id)

    fun updateTransaction(id: Long, title: String, category: String, amount: Double, income: Boolean, date: String, wallet: String) =
        database.transaction {
            q.updateTransaction(title, categoryId(category), amount, RecordKind.fromIncome(income), date, walletId(wallet), id)
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
        val tx = transactions().joinToString(",") { "{\"title\":\"${escape(it.title)}\",\"category\":\"${escape(it.category)}\",\"amount\":${it.amount},\"income\":${it.income},\"date\":\"${it.date}\",\"wallet\":\"${escape(it.wallet)}\"}" }
        val cats = categories().joinToString(",") { "{\"name\":\"${escape(it.first)}\",\"icon\":\"${escape(it.second)}\"}" }
        val recurringItems = q.exportRecurringItems().executeAsList().filter { it.active }.sortedBy { it.due_day }.joinToString(",") { "{\"title\":\"${escape(it.title)}\",\"category\":\"${escape(it.category)}\",\"amount\":${it.amount},\"dueDay\":${it.due_day},\"cadence\":\"${escape(it.cadence)}\"}" }
        val walletItems = walletBalances().joinToString(",") { "{\"name\":\"${escape(it.name)}\",\"balance\":${it.balance}}" }
        val budgetItems = q.exportBudgets().executeAsList().joinToString(",") { "{\"category\":\"${escape(it.category)}\",\"month\":\"${it.month}\",\"limit\":${it.limit_amount}}" }
        "{\"transactions\":[$tx],\"wallets\":[$walletItems],\"categories\":[$cats],\"budgets\":[$budgetItems],\"recurring\":[$recurringItems]}"
    }

    fun exportSql(): String = database.transactionWithResult {
        buildString {
            appendLine(SQL_BACKUP_HEADER)
            appendLine("BEGIN TRANSACTION;")
            listOf("Entries", "Budget", "RecurringItem", "Category", "Wallet").forEach { appendLine("DELETE FROM $it;") }
            q.selectCategories().executeAsList().forEach {
                appendInsert("Category(name, icon, is_income)", it.name, it.icon, it.kind.code)
            }
            walletBalances().forEach {
                appendInsert("Wallet(name, balance)", it.name, it.balance)
            }
            transactions().sortedBy { it.id }.forEach {
                appendInsert("Entries(id, title, category, amount, income, date, wallet)",
                    it.id, it.title, it.category, it.amount, it.kind.code, it.date, it.wallet)
            }
            q.exportBudgets().executeAsList().forEach {
                appendInsert("Budget(id, category, month, limit_amount)", it.id, it.category, it.month, it.limit_amount)
            }
            q.exportRecurringItems().executeAsList().forEach {
                appendInsert("RecurringItem(id, title, category, amount, due_day, cadence, active)",
                    it.id, it.title, it.category, it.amount, it.due_day, it.cadence, if (it.active) 1 else 0)
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
                val wallet = row.text("wallet", "Cash")
                q.ensureWallet(wallet)
                val kind = RecordKind.fromIncome(row.integer("income") != 0L)
                q.insertTransaction(row.id(), row.text("title"), ensureCategory(row.text("category"), kind),
                    row.decimal("amount"), kind, row.text("date"), walletId(wallet))
            }
            BackupTable.BUDGET -> q.restoreBudget(row.id(), ensureCategory(row.text("category")),
                row.text("month"), row.decimal("limit_amount"))
            BackupTable.RECURRINGITEM -> q.restoreRecurringItem(row.id(), row.text("title"),
                ensureCategory(row.text("category")), row.decimal("amount"), row.integer("due_day"),
                row.text("cadence", "Monthly"), row.integer("active", "1") != 0L)
        }
    }

    private fun StringBuilder.appendInsert(tableAndColumns: String, vararg values: Any) {
        val literals = values.joinToString(", ") { if (it is String) "'${it.replace("'", "''")}'" else it.toString() }
        appendLine("INSERT INTO $tableAndColumns VALUES ($literals);")
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
