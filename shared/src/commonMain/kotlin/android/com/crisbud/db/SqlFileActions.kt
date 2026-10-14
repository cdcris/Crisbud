package android.com.crisbud.db

import androidx.compose.runtime.Composable

@Composable
expect fun SqlFileActions(sql: () -> String, onImport: (String) -> Unit, onMessage: (String) -> Unit)
