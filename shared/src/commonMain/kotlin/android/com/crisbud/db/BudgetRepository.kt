package android.com.crisbud.db


data class StoredTransaction(val id: Long, val title: String, val category: String, val amount: Double, val income: Boolean, val date: String)
data class StoredBudget(val category: String, val spent: Double, val limit: Double)
data class StoredRecurring(val id: Long, val title: String, val category: String, val amount: Double, val dueDay: Long, val cadence: String)

class BudgetRepository {
    private val database = CrisbudDatabase(createDatabaseDriver())
    private val q = database.crisbudDatabaseQueries

    fun seedIfNeeded(month: String) {
        if (q.selectCategories().executeAsList().isEmpty()) {
            listOf("Food & Drink" to "🍜", "Transport" to "🚆", "Home" to "⌂", "Shopping" to "🛍", "Health" to "＋", "Income" to "↗", "Other" to "•")
                .forEach { q.insertCategory(it.first, it.second, it.first == "Income") }
        }
        if (q.selectBudgetsForMonth(month).executeAsList().isEmpty()) {
            q.insertBudget("Food & Drink", month, 500.0)
            q.insertBudget("Transport", month, 180.0)
            q.insertBudget("Home", month, 1200.0)
        }
        if (q.selectRecurringItems().executeAsList().isEmpty()) {
            q.insertRecurringItem("Rent", "Home", 1200.0, 1, "Monthly")
            q.insertRecurringItem("Streaming", "Subscriptions", 14.99, 18, "Monthly")
        }
        if (q.selectTransactions().executeAsList().isEmpty()) {
            q.insertTransaction("Grocery run", "Food & Drink", 42.80, false, "$month-12")
            q.insertTransaction("Monthly salary", "Income", 3200.0, true, "$month-01")
            q.insertTransaction("Train pass", "Transport", 32.0, false, "$month-13")
        }
    }

    fun transactions(): List<StoredTransaction> = q.selectTransactions().executeAsList().map {
        StoredTransaction(it.id, it.title, it.category, it.amount, it.income, it.date)
    }

    fun transactions(month: String): List<StoredTransaction> = q.selectTransactionsForMonth(month).executeAsList().map {
        StoredTransaction(it.id, it.title, it.category, it.amount, it.income, it.date)
    }

    fun categories(): List<Pair<String, String>> = q.selectCategories().executeAsList().map { it.name to it.icon }

    fun budgets(month: String): List<StoredBudget> = q.selectBudgetsForMonth(month).executeAsList().map {
        StoredBudget(it.category, it.spent, it.limit_amount)
    }

    fun recurring(): List<StoredRecurring> = q.selectRecurringItems().executeAsList().map {
        StoredRecurring(it.id, it.title, it.category, it.amount, it.due_day, it.cadence)
    }

    fun addTransaction(title: String, category: String, amount: Double, income: Boolean, date: String) {
        q.insertTransaction(title, category, amount, income, date)
    }

    fun deleteTransaction(id: Long) = q.deleteTransaction(id)

    fun updateTransaction(id: Long, title: String, category: String, amount: Double, income: Boolean, date: String) =
        q.updateTransaction(title, category, amount, income, date, id)

    fun addCategory(name: String, icon: String = "•") = q.insertCategory(name, icon, false)
    fun addBudget(category: String, month: String, limit: Double) = q.insertBudget(category, month, limit)
    fun addRecurring(title: String, category: String, amount: Double, dueDay: Long) =
        q.insertRecurringItem(title, category, amount, dueDay, "Monthly")

    fun exportJson(): String {
        val tx = transactions().joinToString(",") { "{\"title\":\"${escape(it.title)}\",\"category\":\"${escape(it.category)}\",\"amount\":${it.amount},\"income\":${it.income},\"date\":\"${it.date}\"}" }
        val cats = categories().joinToString(",") { "{\"name\":\"${escape(it.first)}\",\"icon\":\"${escape(it.second)}\"}" }
        val recurringItems = recurring().joinToString(",") { "{\"title\":\"${escape(it.title)}\",\"category\":\"${escape(it.category)}\",\"amount\":${it.amount},\"dueDay\":${it.dueDay},\"cadence\":\"${it.cadence}\"}" }
        return "{\"transactions\":[$tx],\"categories\":[$cats],\"budgets\":[${q.exportBudgets().executeAsList().joinToString(",") { "{\"category\":\"${escape(it.category)}\",\"month\":\"${it.month}\",\"limit\":${it.limit_amount}}" }}],\"recurring\":[$recurringItems]}"
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
