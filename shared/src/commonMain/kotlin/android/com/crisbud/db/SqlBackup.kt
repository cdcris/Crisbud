package android.com.crisbud.db

internal const val SQL_BACKUP_HEADER = "-- Crisbud SQL backup v1"

internal enum class BackupTable(val sqlName: String, vararg val columns: String) {
    CATEGORY("Category", "name", "icon", "is_income"),
    WALLET("Wallet", "name", "balance"),
    ENTRIES("Entries", "id", "title", "category", "amount", "income", "date", "wallet", "quantity"),
    BUDGET("Budget", "id", "category", "month", "limit_amount"),
    RECURRINGITEM("RecurringItem", "id", "title", "category", "amount", "due_day", "cadence", "active")
}

internal sealed interface BackupStatement {
    data class Delete(val table: BackupTable) : BackupStatement

    data class Insert(val table: BackupTable, private val values: Map<String, String?>) : BackupStatement {
        fun nullableText(column: String): String? {
            require(column in values) { "Missing $column in the backup." }
            return values[column]
        }

        fun text(column: String, default: String? = null): String =
            if (column in values) requireNotNull(values[column]) { "$column must have a value." }
            else requireNotNull(default) { "Missing $column in the backup." }

        fun integer(column: String, default: String? = null): Long =
            requireNotNull(text(column, default).toLongOrNull()) { "$column must be an integer." }

        fun decimal(column: String, default: String? = null): Double =
            requireNotNull(text(column, default).toDoubleOrNull()?.takeIf { it.isFinite() }) {
                "$column must be a finite number."
            }

        fun id(): Long? = values["id"]?.let {
            requireNotNull(it.toLongOrNull()) { "id must be an integer." }
        }
    }
}

private val deletePattern = Regex("""DELETE\s+FROM\s+([a-z]+)""", RegexOption.IGNORE_CASE)
private val insertPattern = Regex(
    """INSERT\s+INTO\s+([a-z]+)\s*\(([a-z_,\s]+)\)\s*VALUES\s*([\s\S]+)""",
    RegexOption.IGNORE_CASE
)
private val rowStart = Regex("""\s*\(\s*""")
private val valuePattern = Regex(
    """\s*(?:'((?:[^']*'')*[^']*)'|([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?|NULL|TRUE|FALSE))\s*([,)])""",
    RegexOption.IGNORE_CASE
)

internal fun readSqlBackup(sql: String): List<BackupStatement> {
    val content = sql.removePrefix("\uFEFF")
    require(content.lineSequence().first().trimEnd() == SQL_BACKUP_HEADER) { "Choose a Crisbud SQL backup." }

    return splitStatements(content.substringAfter('\n', "")).flatMap { statement ->
        when {
            statement.equals("BEGIN TRANSACTION", ignoreCase = true) || statement.equals("COMMIT", ignoreCase = true) ->
                emptyList()
            else -> {
                val delete = deletePattern.matchEntire(statement)
                if (delete != null) listOf(BackupStatement.Delete(backupTable(delete.groupValues[1])))
                else readInserts(statement)
            }
        }
    }
}

private fun readInserts(statement: String): List<BackupStatement.Insert> {
    val insert = requireNotNull(insertPattern.matchEntire(statement)) { "The backup contains an unsupported statement." }
    val table = backupTable(insert.groupValues[1])
    val columns = insert.groupValues[2].split(',').map { it.trim().lowercase() }
    require(columns.distinct() == columns && columns.all { it in table.columns }) {
        "The backup contains unsupported or duplicate columns."
    }
    val rows = insert.groupValues[3]
    var position = 0
    return buildList {
        while (position < rows.length) {
            val start = requireNotNull(rowStart.matchAt(rows, position)) { "Expected a row in the backup." }
            position = start.range.last + 1
            val values = columns.mapIndexed { index, column ->
                val value = requireNotNull(valuePattern.matchAt(rows, position)) { "Invalid value for $column." }
                val expectedSeparator = if (index == columns.lastIndex) ")" else ","
                require(value.groupValues[3] == expectedSeparator) { "The backup has the wrong number of values." }
                position = value.range.last + 1
                val literal = value.groups[1]?.value?.replace("''", "'")
                val raw = value.groupValues[2]
                val decoded = literal ?: when (raw.uppercase()) {
                    "NULL" -> null
                    "TRUE" -> "1"
                    "FALSE" -> "0"
                    else -> raw
                }
                column to decoded
            }.toMap()
            add(BackupStatement.Insert(table, values))

            while (position < rows.length && rows[position].isWhitespace()) position++
            if (position == rows.length) break
            require(rows[position] == ',' && position < rows.lastIndex) { "Expected a comma between backup rows." }
            position++
        }
    }
}

private fun backupTable(name: String): BackupTable =
    requireNotNull(BackupTable.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }) {
        "The backup contains an unsupported table."
    }

private fun splitStatements(sql: String): List<String> = buildList {
    var start = 0
    var quoted = false
    sql.forEachIndexed { index, character ->
        if (character == '\'') quoted = !quoted
        if (character == ';' && !quoted) {
            sql.substring(start, index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            start = index + 1
        }
    }
    require(!quoted) { "The backup contains an unfinished string." }
    sql.substring(start).trim().takeIf { it.isNotEmpty() }?.let(::add)
}
