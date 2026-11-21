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
