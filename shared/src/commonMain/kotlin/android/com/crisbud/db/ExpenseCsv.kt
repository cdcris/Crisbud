package android.com.crisbud.db

private val requiredColumns = listOf("title", "category", "amount", "date")
private val csvColumns = requiredColumns + listOf("wallet", "quantity")
private val isoDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

internal fun StringBuilder.appendExpenseCsvHeader() {
    // The BOM helps spreadsheet apps recognize UTF-8 text.
    append('\uFEFF')
    appendCsvRow(csvColumns)
}

internal fun StringBuilder.appendExpenseCsvRow(expense: StoredTransaction) {
    val lineTotal = expense.amount * expense.quantity
    appendCsvRow(listOf(
        expense.title, expense.category.orEmpty(), lineTotal.toString(), expense.date,
        expense.wallet, expense.quantity.toString()
    ))
}

private fun StringBuilder.appendCsvRow(fields: List<String>) {
    append(fields.joinToString(",") { value ->
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    })
    append("\r\n")
}

internal data class ExpenseCsvRow(
    val number: Int,
    val title: String,
    val category: String?,
    val lineTotal: Double,
    val date: String,
    val wallet: String,
    val quantity: Long
)

internal fun readExpenseCsv(csv: String): List<ExpenseCsvRow> {
    val records = csvRecords(csv.removePrefix("\uFEFF")).filterNot { (_, fields) -> fields.all { it.isBlank() } }
    val (headerNumber, header) = requireNotNull(records.firstOrNull()) { "The CSV file is empty." }
    val columns = header.map { it.trim().lowercase() }
    val legacyBackup = "record_type" in columns
    val hasExpenseColumns = columns.containsAll(requiredColumns) &&
        if (legacyBackup) "income" in columns else csvColumns.containsAll(columns)
    require(hasExpenseColumns && columns.distinct().size == columns.size) {
        "CSV row $headerNumber must use the column names from the expense CSV sample."
    }
    return records.drop(1).mapNotNull { (number, fields) ->
        require(fields.size == columns.size) { "CSV row $number must have ${columns.size} columns." }
        val values = columns.zip(fields).toMap()
        if (legacyBackup && !isLegacyExpense(values, number)) return@mapNotNull null

        fun required(column: String): String = values.getValue(column).also {
            require(it.isNotBlank()) { "CSV row $number needs a value for $column." }
        }
        val lineTotal = requireNotNull(required("amount").trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }) {
            "CSV row $number must have a positive, finite amount."
        }
        val date = required("date").trim()
        require(isoDate.matches(date) && epochMillisFromIsoDate(date) != null) {
            "CSV row $number must have a valid date in YYYY-MM-DD format."
        }
        val wallet = values["wallet"].orEmpty().ifBlank { "Cash" }
        val quantity = requireNotNull(values["quantity"].orEmpty().trim().ifEmpty { "1" }.toLongOrNull()) {
            "CSV row $number must have a whole number for quantity."
        }
        require(quantity > 0) { "CSV row $number: Quantity must be a positive whole number." }
        val category = values.getValue("category").takeUnless { it.isBlank() }
        ExpenseCsvRow(number, required("title"), category, lineTotal, date, wallet, quantity)
    }
}

private fun isLegacyExpense(values: Map<String, String>, number: Int): Boolean =
    when (val type = values.getValue("record_type").trim().lowercase()) {
        "transaction" -> {
            val income = requireNotNull(values.getValue("income").trim().toLongOrNull()) {
                "CSV row $number must have a whole number for income."
            }
            income == 0L
        }
        "category", "wallet", "budget", "recurring" -> false
        else -> throw IllegalArgumentException("CSV row $number has an unknown record type: $type.")
    }

private enum class CsvFieldState { START, UNQUOTED, QUOTED, AFTER_QUOTE }

private fun csvRecords(csv: String): List<Pair<Int, List<String>>> = buildList {
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var state = CsvFieldState.START
    var line = 1
    var recordLine = line
    var index = 0

    fun endField() {
        fields += field.toString()
        field.clear()
        state = CsvFieldState.START
    }

    fun endRecord() {
        endField()
        add(recordLine to fields.toList())
        fields.clear()
    }

    while (index < csv.length) {
        val char = csv[index++]
        if (state == CsvFieldState.QUOTED) {
            if (char == '"') {
                if (csv.getOrNull(index) == '"') {
                    field.append('"')
                    index++
                } else {
                    state = CsvFieldState.AFTER_QUOTE
                }
            } else {
                field.append(char)
                if (char == '\n' || (char == '\r' && csv.getOrNull(index) != '\n')) line++
            }
            continue
        }

        when (char) {
            ',' -> endField()
            '\r', '\n' -> {
                if (char == '\r' && csv.getOrNull(index) == '\n') index++
                endRecord()
                recordLine = ++line
            }
            '"' -> {
                require(state == CsvFieldState.START) { "CSV row $recordLine has an unexpected quote." }
                state = CsvFieldState.QUOTED
            }
            else -> {
                require(state != CsvFieldState.AFTER_QUOTE) { "CSV row $recordLine has text after a closing quote." }
                field.append(char)
                state = CsvFieldState.UNQUOTED
            }
        }
    }
    require(state != CsvFieldState.QUOTED) { "CSV row $recordLine has an unclosed quote." }
    if (fields.isNotEmpty() || state != CsvFieldState.START) endRecord()
}
