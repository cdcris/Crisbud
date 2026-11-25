package android.com.crisbud

import android.com.crisbud.db.BudgetRepository
import android.com.crisbud.db.CrisbudDatabase
import android.com.crisbud.db.RecordKind
import android.com.crisbud.db.RecurringCadence
import android.com.crisbud.db.SQL_BACKUP_HEADER
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseMigrationTest {
    @Test
    fun versionThreeUpgradeLinksExistingRowsByIdAndRepairsMissingParents() = withDatabase { driver ->
        listOf(
            "CREATE TABLE Category(name TEXT PRIMARY KEY, icon TEXT NOT NULL, is_income INTEGER NOT NULL)",
            "CREATE TABLE Wallet(name TEXT PRIMARY KEY, balance REAL NOT NULL)",
            "CREATE TABLE Entries(id INTEGER PRIMARY KEY, title TEXT, category TEXT, amount REAL, income INTEGER, date TEXT, wallet TEXT)",
            "CREATE TABLE Budget(id INTEGER PRIMARY KEY, category TEXT, month TEXT, limit_amount REAL, UNIQUE(category, month))",
            "CREATE TABLE RecurringItem(id INTEGER PRIMARY KEY, title TEXT, category TEXT, amount REAL, due_day INTEGER, cadence TEXT, active INTEGER)",
            "INSERT INTO Category VALUES ('Food', 'F', 0)",
            "INSERT INTO Wallet VALUES ('Bank', 240.50)",
            "INSERT INTO Entries VALUES (42, 'Lunch', 'Food', 12.50, 0, '2026-11-20', 'Cash')",
            "INSERT INTO Budget VALUES (3, 'Food', '2026-11', 100.0)",
            "INSERT INTO RecurringItem VALUES (9, 'Streaming', 'Subscriptions', 10.0, 15, 'Monthly', 1)"
        ).forEach { driver.execute(null, it, 0) }
        CrisbudDatabase.Schema.migrate(driver, 3, CrisbudDatabase.Schema.version)

        val repository = BudgetRepository(driver)
        assertEquals(42L, repository.transactions().single().id)
        assertEquals(1L, repository.transactions().single().quantity)
        assertEquals(RecordKind.EXPENSE, repository.transactions().single().kind)
        assertEquals(240.50, repository.walletBalances().single { it.name == "Bank" }.balance)
        assertEquals(0.0, repository.walletBalances().single { it.name == "Cash" }.balance)
        assertTrue(repository.renameCategory("Food", "Meals"))
        assertTrue(repository.renameWallet("Cash", "Pocket"))
        assertEquals("Pocket", repository.transactions().single().wallet)
        assertEquals("Meals", repository.transactions().single().category)
        assertEquals("Meals", repository.budgets("2026-11").single().category)
        assertEquals(12.5, repository.budgets("2026-11").single().spent)
        assertEquals(RecurringCadence.MONTHLY, repository.recurring().single().cadence)
        assertFalse(repository.deleteCategory("Subscriptions"))
        assertFalse(repository.deleteWallet("Pocket"))
        assertFailsWith<SQLException> {
            driver.execute(null, "UPDATE Entries SET category_id = -1", 0)
        }
    }

    @Test
    fun legacyBackupRestoresAndFailedReplacementRollsBack() = withDatabase { driver ->
        CrisbudDatabase.Schema.create(driver)
        val repository = BudgetRepository(driver)
        repository.importSql(
            """
            $SQL_BACKUP_HEADER
            INSERT INTO Entries(id, title, category, amount, income, date)
            VALUES (8, 'Day''s pay; bonus', 'Income', 100, 1, '2026-11-20');
            INSERT INTO RecurringItem(id, title, category, amount, due_day, cadence, active)
            VALUES (9, 'Legacy plan', 'Subscriptions', 5, 15, 'Custom', 0);
            """.trimIndent()
        )
        val backup = repository.exportSql()
        assertTrue(backup.contains("'Custom', 0"))
        assertEquals(RecordKind.INCOME, repository.transactions().single().kind)
        assertEquals("Cash", repository.transactions().single().wallet)
        assertEquals(1L, repository.transactions().single().quantity)

        repository.importSql(backup)
        assertEquals(backup, repository.exportSql())
        assertFailsWith<SQLException> {
            repository.importSql(backup.replace("COMMIT;", """
                INSERT INTO Entries(id, title, category, amount, income, date)
                VALUES (8, 'Duplicate', 'Other', 1, 0, '2026-11-20');
                COMMIT;
            """.trimIndent()))
        }
        assertEquals(backup, repository.exportSql())
    }

    @Test
    fun csvExportsOnlyExpensesAndAppendsThemWithoutReplacingExistingData() = withDatabase { driver ->
        CrisbudDatabase.Schema.create(driver)
        val repository = BudgetRepository(driver)
        repository.seedIfNeeded("2026-11")
        repository.updateWalletBalance("Cash", 580.25)
        repository.addTransaction(
            "Lunch, \"special\"\r\nwith tea", "Food & Drink", 50.0, false, "2026-11-25", "Cash", quantity = 3
        )
        val transactions = repository.transactions()
        val expenses = transactions.filter { it.kind == RecordKind.EXPENSE }
        val categories = repository.categories()
        val wallets = repository.walletBalances()
        val budgets = repository.budgets("2026-11")
        val recurring = repository.recurring()
        val csv = repository.exportCsv()
        assertTrue(csv.startsWith("\uFEFFtitle,category,amount,date,wallet,quantity\r\n"))
        assertFalse(csv.contains("Monthly salary"))
        assertFalse(csv.contains("580.25"))
        assertFalse(csv.contains("Streaming"))

        repository.importCsv(csv)
        val (existing, imported) = repository.transactions().partition { transaction ->
            transactions.any { it.id == transaction.id }
        }
        assertEquals(transactions, existing)
        assertEquals(expenses.map { it.copy(id = 0) }, imported.map { it.copy(id = 0) })
        assertEquals(categories, repository.categories())
        assertEquals(wallets, repository.walletBalances())
        assertEquals(budgets, repository.budgets("2026-11").map { it.copy(spent = it.spent / 2) })
        assertEquals(recurring, repository.recurring())
    }

    @Test
    fun csvReusesExistingCategoriesAndWalletsAndCreatesEachMissingOneOnce() = withDatabase { driver ->
        CrisbudDatabase.Schema.create(driver)
        val repository = BudgetRepository(driver)
        repository.addCategory("Food", "F")
        repository.addWallet("Cash")
        repository.updateWalletBalance("Cash", 100.0)

        repository.importCsv(
            """
            title,category,amount,date,wallet
            Lunch," food ",12.50,2026-11-25,CASH
            Train,Transport,6.00,2026-11-25,Transit
            Bus,transport,4.50,2026-11-25," transit "
            """.trimIndent()
        )

        assertEquals(listOf("Food" to "F", "Transport" to "•"), repository.categories())
        assertEquals(mapOf("Cash" to 100.0, "Transit" to 0.0), repository.walletBalances().associate { it.name to it.balance })
        val references = repository.transactions().associate { it.title to (it.category to it.wallet) }
        assertEquals(
            mapOf("Lunch" to ("Food" to "Cash"), "Train" to ("Transport" to "Transit"), "Bus" to ("Transport" to "Transit")),
            references
        )
    }

    @Test
    fun invalidExpenseCsvRollsBackExpensesAndNewReferences() = withDatabase { driver ->
        CrisbudDatabase.Schema.create(driver)
        val repository = BudgetRepository(driver)
        repository.seedIfNeeded("2026-11")
        val sql = repository.exportSql()
        val invalid = """
            title,category,amount,date,wallet,quantity
            Lunch,Meals,12.5,2026-11-25,New wallet,1
            Dinner,Meals,8.0,2026-11-25,New wallet,0
        """.trimIndent()
        val error = assertFailsWith<IllegalArgumentException> { repository.importCsv(invalid) }
        assertTrue(error.message.orEmpty().startsWith("CSV row 3: Quantity"))
        assertEquals(sql, repository.exportSql())
        assertFailsWith<IllegalArgumentException> {
            repository.importCsv("title,category,amount,date,income\nSalary,Income,100,2026-11-25,1")
        }
        assertEquals(sql, repository.exportSql())
    }

    @Test
    fun legacyCsvImportsOnlyExpenseTransactions() = withDatabase { driver ->
        CrisbudDatabase.Schema.create(driver)
        val repository = BudgetRepository(driver)
        repository.importCsv(
            """
            record_type,title,category,wallet,amount,date,income,name,balance,quantity
            wallet,,,,,,,Bank,250.00,
            wallet,,,,,,,Savings,80.00,
            transaction,Lunch,Food,Bank,12.50,2026-11-25,0,,,2
            transaction,Salary,Income,Savings,100.00,2026-11-25,1,,,1
            """.trimIndent()
        )

        val expense = repository.transactions().single()
        assertEquals("Lunch", expense.title)
        assertEquals(RecordKind.EXPENSE, expense.kind)
        assertEquals(2L, expense.quantity)
        assertEquals(listOf("Food"), repository.categories().map { it.first })
        assertEquals("Bank", repository.walletBalances().single().name)
        assertEquals(0.0, repository.walletBalances().single().balance)
    }

    @Test
    fun expenseQuantityAffectsBudgetSpendingAndCanBeEdited() = withDatabase { driver ->
        CrisbudDatabase.Schema.create(driver)
        val repository = BudgetRepository(driver)
        repository.importCsv(
            """
            title,category,amount,date
            Lunch,Food,12.5,2026-11-25
            """.trimIndent()
        )
        repository.addBudget("Food", "2026-11", 100.0)
        val transaction = repository.transactions().single()
        assertEquals(1L, transaction.quantity)
        assertEquals(12.5, repository.budgets("2026-11").single().spent)

        repository.updateTransaction(transaction.id, "Lunch", "Food", 12.5, false, "2026-11-25", "Cash", quantity = 4)
        assertEquals(4L, repository.transactions().single().quantity)
        assertEquals(50.0, repository.budgets("2026-11").single().spent)

        assertFailsWith<IllegalArgumentException> {
            repository.updateTransaction(transaction.id, "Lunch", "Food", 12.5, false, "2026-11-25", "Cash", quantity = 0)
        }
        assertEquals(4L, repository.transactions().single().quantity)
    }

    private fun withDatabase(run: (JdbcSqliteDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            run(driver)
        } finally {
            driver.close()
        }
    }
}
