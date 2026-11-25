package android.com.crisbud.db

import androidx.compose.runtime.Composable

internal const val CSV_EXPENSES_NAME = "crisbud-expenses.csv"
internal const val CSV_SAMPLE_NAME = "crisbud-sample.csv"

internal class CsvFileActions(
    val export: () -> Unit,
    val importExpenses: () -> Unit,
    val downloadSample: () -> Unit
)

@Composable
internal expect fun rememberCsvFileActions(
    csv: () -> String,
    sample: () -> String,
    onImport: (String) -> Unit,
    onMessage: (String) -> Unit
): CsvFileActions
