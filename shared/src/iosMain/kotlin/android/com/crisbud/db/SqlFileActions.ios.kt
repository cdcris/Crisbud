package android.com.crisbud.db

import android.com.crisbud.BackupActions
import androidx.compose.runtime.Composable
import platform.UIKit.UIPasteboard

@Composable
actual fun SqlFileActions(sql: () -> String, onImport: (String) -> Unit, onMessage: (String) -> Unit) {
    BackupActions(
        onExport = {
            runCatching { UIPasteboard.generalPasteboard.string = sql() }
                .onSuccess { onMessage("SQL backup copied to clipboard. Paste it into a .sql file to save it.") }
                .onFailure { onMessage(it.message ?: "Export failed.") }
        },
        onImport = {
            val content = UIPasteboard.generalPasteboard.string
            if (content.isNullOrBlank()) onMessage("Copy a Crisbud SQL backup to the clipboard first.")
            else runCatching { onImport(content) }
                .onSuccess { onMessage("Backup imported.") }
                .onFailure { onMessage(it.message ?: "Import failed.") }
        }
    )
}
