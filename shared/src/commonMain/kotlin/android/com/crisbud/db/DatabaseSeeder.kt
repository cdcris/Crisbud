package android.com.crisbud.db

/** Inserts the starter data used when the local database is empty. */
internal object DatabaseSeeder {
    fun seed(
        q: CrisbudDatabaseQueries,
        month: String,
        ensureCategory: (String, RecordKind) -> Long,
        walletId: (String) -> Long
    ) {
        if (q.selectWallets().executeAsList().isEmpty()) {
            listOf("GCash", "MariBank", "Cash").forEach(q::ensureWallet)
        }
        if (q.selectCategories().executeAsList().isEmpty()) {
            listOf(
                "Food & Drink" to "🍜", "Transport" to "🚆", "Home" to "⌂", "Shopping" to "🛍",
                "Health" to "＋", "Income" to "↗", "Other" to "•"
            ).forEach { (name, icon) ->
                q.ensureCategory(name, icon, RecordKind.fromIncome(name == "Income"))
            }
        }
        if (q.selectBudgetsForMonth(month).executeAsList().isEmpty()) {
            listOf("Food & Drink" to 500.0, "Transport" to 180.0, "Home" to 1200.0)
                .forEach { (name, limit) -> q.insertBudget(ensureCategory(name, RecordKind.EXPENSE), month, limit) }
        }
        if (q.selectRecurringItems().executeAsList().isEmpty()) {
            q.insertRecurringItem("Rent", ensureCategory("Home", RecordKind.EXPENSE), 1200.0, 1, RecurringCadence.MONTHLY.databaseValue)
            q.insertRecurringItem("Streaming", ensureCategory("Subscriptions", RecordKind.EXPENSE), 14.99, 18, RecurringCadence.MONTHLY.databaseValue)
        }
        if (q.selectTransactions(null).executeAsList().isEmpty()) {
            q.ensureWallet("Cash")
            val cash = walletId("Cash")
            q.insertTransaction("Grocery run", ensureCategory("Food & Drink", RecordKind.EXPENSE), 42.80, RecordKind.EXPENSE, "$month-12", cash, 1)
            q.insertTransaction("Monthly salary", ensureCategory("Income", RecordKind.INCOME), 3200.0, RecordKind.INCOME, "$month-01", cash, 1)
            q.insertTransaction("Train pass", ensureCategory("Transport", RecordKind.EXPENSE), 32.0, RecordKind.EXPENSE, "$month-13", cash, 1)
        }
    }
}
