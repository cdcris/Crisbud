package android.com.crisbud.db

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberCsvFileActions(
    csv: () -> String,
    sample: () -> String,
    onImport: (String) -> Unit,
    onMessage: (String) -> Unit
): CsvFileActions {
    val resolver = LocalContext.current.contentResolver

    fun save(uri: Uri?, content: () -> String, success: String) {
        if (uri == null) return
        runCatching {
            val text = content()
            val stream = checkNotNull(resolver.openOutputStream(uri)) { "Could not write the CSV file." }
            stream.bufferedWriter().use { it.write(text) }
        }.onSuccess { onMessage(success) }
            .onFailure { onMessage(it.message ?: "Could not save the CSV file.") }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        save(it, csv, "Expense CSV exported.")
    }
    val sampleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        save(it, sample, "CSV sample saved.")
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val stream = checkNotNull(resolver.openInputStream(uri)) { "Could not read the CSV file." }
            val bytes = stream.use { it.readBytes() }
            onImport(bytes.decodeToString(throwOnInvalidSequence = true))
        }.onFailure { onMessage(it.message ?: "Could not import the CSV file.") }
    }

    return CsvFileActions(
        export = { exportLauncher.launch(CSV_EXPENSES_NAME) },
        importExpenses = {
            importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
        },
        downloadSample = { sampleLauncher.launch(CSV_SAMPLE_NAME) }
    )
}
