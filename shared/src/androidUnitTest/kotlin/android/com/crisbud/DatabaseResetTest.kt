package android.com.crisbud

import android.com.crisbud.db.BudgetRepository
import android.com.crisbud.db.CrisbudDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseResetTest {
    @Test
    fun resetClearsAllRecordsAndAllowsNewEntries() = withSeededDatabase { _, repository ->
        repository.addWallet("Savings")
        repository.updateWalletBalance("Savings", 500.0)
        repository.addBudget("Home", "2026-10", 250.0)
        repository.deleteRecurring(repository.recurring().first().id)

        repository.resetData()
        repository.resetData()

        assertTrue(repository.transactions().isEmpty())
        assertTrue(repository.budgets("2026-11").isEmpty())
        assertTrue(repository.budgets("2026-10").isEmpty())
        assertTrue(repository.recurring().isEmpty())
        assertTrue(repository.categories().isEmpty())
        assertTrue(repository.walletBalances().isEmpty())
        assertTrue(repository.exportSql().lineSequence().none { it.startsWith("INSERT INTO") })

        repository.addCategory("Travel")
        repository.addWallet("Cash")
        repository.addTransaction("Train", "Travel", 20.0, false, "2026-11-26", "Cash")
        assertEquals("Train", repository.transactions().single().title)
    }

    @Test
    fun failedResetPreservesAllRecords() = withSeededDatabase { driver, repository ->
        val backup = repository.exportSql()
        driver.execute(null, """
            CREATE TRIGGER block_wallet_deletion BEFORE DELETE ON Wallet
            BEGIN
                SELECT RAISE(ABORT, 'Cannot delete wallet');
            END
        """.trimIndent(), 0)

        assertFailsWith<SQLException> { repository.resetData() }
        assertEquals(backup, repository.exportSql())
    }

    private fun withSeededDatabase(run: (JdbcSqliteDriver, BudgetRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            CrisbudDatabase.Schema.create(driver)
            val repository = BudgetRepository(driver)
            repository.seedIfNeeded("2026-11")
            run(driver, repository)
        } finally {
            driver.close()
        }
    }
}
