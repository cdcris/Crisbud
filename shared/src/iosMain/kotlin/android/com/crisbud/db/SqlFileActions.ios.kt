package android.com.crisbud.db

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import platform.UIKit.UIPasteboard

@Composable
actual fun SqlFileActions(sql: () -> String, onImport: (String) -> Unit, onMessage: (String) -> Unit) {
    TextButton(onClick = {
        UIPasteboard.generalPasteboard.string = sql()
        onMessage("SQL backup copied to clipboard. Paste it into a .sql file to save it.")
    }) { Text("Export") }
    TextButton(onClick = {
        val content = UIPasteboard.generalPasteboard.string
        if (content.isNullOrBlank()) onMessage("Copy a Crisbud SQL backup to the clipboard first.")
        else onImport(content)
    }) { Text("Import") }
}
