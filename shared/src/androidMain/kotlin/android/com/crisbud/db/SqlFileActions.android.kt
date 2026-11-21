package android.com.crisbud.db

import android.com.crisbud.BackupActions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun SqlFileActions(sql: () -> String, onImport: (String) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/sql")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(sql()) }
                ?: error("Could not write the SQL file.")
        }.onSuccess { onMessage("Backup exported.") }
            .onFailure { onMessage(it.message ?: "Export failed.") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { onImport(it.readText()) }
                ?: error("Could not read the SQL file.")
        }.onSuccess { onMessage("Backup imported.") }
            .onFailure { onMessage(it.message ?: "Import failed.") }
    }
    BackupActions(
        onExport = { exportLauncher.launch("crisbud-backup.sql") },
        onImport = { importLauncher.launch(arrayOf("application/sql", "text/plain", "application/octet-stream")) }
    )
}
