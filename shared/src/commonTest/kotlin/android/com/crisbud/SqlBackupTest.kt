package android.com.crisbud

import android.com.crisbud.db.BackupStatement
import android.com.crisbud.db.BackupTable
import android.com.crisbud.db.SQL_BACKUP_HEADER
import android.com.crisbud.db.readSqlBackup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SqlBackupTest {
    @Test
    fun legacyBackupKeepsQuotedNamesAndSemicolons() {
        val statements = readSqlBackup(
            """
            $SQL_BACKUP_HEADER
            BEGIN TRANSACTION;
            DELETE FROM Entries;
            INSERT INTO Entries(id, title, category, amount, income, date, wallet)
            VALUES (19, 'Sam''s lunch; tip included', 'Food', 2.45e1, 0, '2026-11-20', 'Cash');
            COMMIT;
            """.trimIndent()
        )
        assertEquals(2, statements.size)
        assertEquals(BackupStatement.Delete(BackupTable.ENTRIES), statements.first())
        val row = assertIs<BackupStatement.Insert>(statements.last())
        assertEquals(19L, row.id())
        assertEquals("Sam's lunch; tip included", row.text("title"))
        assertEquals(24.5, row.decimal("amount"))
    }

    @Test
    fun legacyAppendAcceptsMultipleRowsWithoutIds() {
        val rows = readSqlBackup(
            """
            $SQL_BACKUP_HEADER
            INSERT INTO Entries(title, category, amount, income, date)
            VALUES ('Lunch', 'Food', 5, false, '2026-11-20'), ('Pay', 'Income', 100, true, '2026-11-21');
            """.trimIndent()
        ).map { assertIs<BackupStatement.Insert>(it) }

        assertEquals(2, rows.size)
        assertNull(rows.first().id())
        assertEquals("Cash", rows.first().text("wallet", "Cash"))
        assertEquals(listOf(0L, 1L), rows.map { it.integer("income") })
    }

    @Test
    fun legacyFormatRejectsMissingValuesAndUnsupportedColumns() {
        assertFailsWith<IllegalArgumentException> {
            readSqlBackup("$SQL_BACKUP_HEADER\nINSERT INTO Wallet(name, balance) VALUES ('Cash');")
        }
        assertFailsWith<IllegalArgumentException> {
            readSqlBackup("$SQL_BACKUP_HEADER\nINSERT INTO Entries(category_id) VALUES (1);")
        }
    }
}
