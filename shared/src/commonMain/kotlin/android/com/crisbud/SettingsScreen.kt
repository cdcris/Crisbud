package android.com.crisbud

import android.com.crisbud.db.CSV_SAMPLE_NAME
import android.com.crisbud.db.ExpenseCsvRow
import android.com.crisbud.db.SqlFileActions
import android.com.crisbud.db.StoredWallet
import android.com.crisbud.db.readExpenseCsv
import android.com.crisbud.db.rememberCsvFileActions
import android.com.crisbud.resources.Res
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi

private enum class ManagedList(val title: String, val itemName: String) {
    Wallets("Wallets", "wallet"),
    Categories("Categories", "category")
}

private data class SettingsRecord(val name: String, val balance: Double? = null)

@Composable
@OptIn(ExperimentalResourceApi::class)
internal fun SettingsScreen(
    padding: PaddingValues,
    categories: List<Pair<String, String>>,
    wallets: List<StoredWallet>,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Boolean,
    onDeleteCategory: (String) -> Boolean,
    onAddWallet: (String) -> Unit,
    onRenameWallet: (String, String) -> Boolean,
    onDeleteWallet: (String) -> Boolean,
    onSetWalletBalance: (String, Double) -> Unit,
    sql: () -> String,
    onSqlImported: (String) -> Unit,
    csv: () -> String,
    onCsvImported: (List<ExpenseCsvRow>) -> Unit,
    onFileMessage: (String) -> Unit,
    onResetData: () -> Boolean
) {
    var managedList by rememberSaveable { mutableStateOf<ManagedList?>(null) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var resetSucceeded by remember { mutableStateOf<Boolean?>(null) }
    val csvSample by produceState<String?>(null) {
        value = Res.readBytes("files/$CSV_SAMPLE_NAME").decodeToString()
    }
    val csvFiles = rememberCsvFileActions(
        csv,
        { checkNotNull(csvSample) { "The CSV sample is still loading." } },
        onImport = { text ->
            val rows = readExpenseCsv(text)
            require(rows.isNotEmpty()) { "The CSV has no expense entries to import." }
            onCsvImported(rows)
            val count = if (rows.size == 1) "1 expense entry" else "${rows.size} expense entries"
            onFileMessage("Imported $count totaling ${money(rows.sumOf { it.lineTotal })}.")
        },
        onMessage = onFileMessage
    )
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            WalletSummary(wallets, onClick = { managedList = ManagedList.Wallets })
        }
        item {
            Surface(
                onClick = { managedList = ManagedList.Categories },
                shape = MaterialTheme.shapes.large,
                color = colors.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(48.dp).background(colors.tertiaryContainer, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.GridView, null, tint = colors.onTertiaryContainer) }
                    Column(Modifier.weight(1f)) {
                        Text("Categories", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (categories.size == 1) "1 category" else "${categories.size} categories",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Data & backup")
                Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FolderCopy, null, tint = colors.primary)
                            Text("SQL backup", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Export a copy of your budget data. Importing a backup replaces the data on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                        SqlFileActions(sql, onSqlImported, onFileMessage)
                    }
                }
                Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ImportExport, null, tint = colors.primary)
                            Text("Expense CSV", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "The amount column contains the total for each expense. " +
                                "Imports create any missing categories and wallets.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                        BackupActions(onExport = csvFiles.export, onImport = csvFiles.importExpenses)
                        TextButton(onClick = csvFiles.downloadSample, enabled = csvSample != null) {
                            Text("Download sample CSV")
                        }
                    }
                }
                Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Permanently clear all budget data on this device.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                resetSucceeded = null
                                showResetConfirmation = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
                        ) {
                            Text("Reset data")
                        }
                        resetSucceeded?.let { succeeded ->
                            Text(
                                if (succeeded) "Data reset complete." else "Couldn’t reset data. Try again.",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (succeeded) colors.onSurfaceVariant else colors.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirmation) {
        ConfirmDestructiveAction(
            title = "Reset all data?",
            message = "This will permanently delete all transactions, budgets, recurring items, categories, and wallets " +
                "on this device. This action cannot be undone.",
            actionLabel = "Reset data",
            onDismiss = { showResetConfirmation = false },
            onConfirm = {
                showResetConfirmation = false
                resetSucceeded = onResetData()
            }
        )
    }

    when (managedList) {
        ManagedList.Wallets -> ManageRecords(
            list = ManagedList.Wallets,
            records = wallets.map { SettingsRecord(it.name, it.balance) },
            onDismiss = { managedList = null },
            onSave = { oldName, name, balance ->
                if (oldName != null && !onRenameWallet(oldName, name)) {
                    "This wallet name is already in use."
                } else {
                    if (oldName == null) onAddWallet(name)
                    onSetWalletBalance(name, requireNotNull(balance))
                    null
                }
            },
            onDelete = { name ->
                when {
                    onDeleteWallet(name) -> null
                    wallets.size <= 1 -> "Keep at least one wallet for transactions."
                    else -> "A transaction still uses this wallet."
                }
            }
        )
        ManagedList.Categories -> ManageRecords(
            list = ManagedList.Categories,
            records = categories.map { SettingsRecord(it.first) },
            onDismiss = { managedList = null },
            onSave = { oldName, name, _ ->
                when {
                    oldName == null -> {
                        onAddCategory(name)
                        null
                    }
                    onRenameCategory(oldName, name) -> null
                    else -> "This name is already in use or has an overlapping budget."
                }
            },
            onDelete = { name ->
                if (onDeleteCategory(name)) null else "This category could not be deleted."
            }
        )
        null -> Unit
    }
}

@Composable
private fun WalletSummary(wallets: List<StoredWallet>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 46.dp, bottomEnd = 28.dp, bottomStart = 28.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Total wallet balance", style = MaterialTheme.typography.labelLarge)
                    Text(money(wallets.sumOf { it.balance }), style = MaterialTheme.typography.headlineLarge)
                }
                WalletArt()
            }
            HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = .17f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (wallets.size == 1) "Manage 1 wallet" else "Manage ${wallets.size} wallets",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun WalletArt() {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.size(width = 78.dp, height = 68.dp)) {
        val unit = size.width / 78f
        val cardSize = Size(52f * unit, 35f * unit)
        val round = CornerRadius(6f * unit)
        val upperCorner = Offset(7f * unit, 10f * unit)
        rotate(-16f, upperCorner + Offset(cardSize.width / 2, cardSize.height / 2)) {
            drawRoundRect(colors.onPrimaryContainer, upperCorner, cardSize, round)
            drawLine(colors.primaryContainer, upperCorner + Offset(6f * unit, 7f * unit),
                upperCorner + Offset(45f * unit, 7f * unit), 2f * unit)
        }
        val lowerCorner = Offset(18f * unit, 26f * unit)
        rotate(8f, lowerCorner + Offset(cardSize.width / 2, cardSize.height / 2)) {
            drawRoundRect(colors.tertiaryContainer, lowerCorner, cardSize, round)
            drawRoundRect(colors.onPrimaryContainer, lowerCorner, cardSize, round, style = Stroke(1.4f * unit))
            drawRoundRect(colors.onTertiaryContainer, lowerCorner + Offset(6f * unit, 9f * unit),
                Size(10f * unit, 7f * unit), CornerRadius(2f * unit))
            drawLine(colors.onTertiaryContainer, lowerCorner + Offset(6f * unit, 26f * unit),
                lowerCorner + Offset(24f * unit, 26f * unit), 2f * unit)
        }
    }
}

@Composable
internal fun BackupActions(onExport: () -> Unit, onImport: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalButton(onClick = onExport, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageRecords(
    list: ManagedList,
    records: List<SettingsRecord>,
    onDismiss: () -> Unit,
    onSave: (String?, String, Double?) -> String?,
    onDelete: (String) -> String?
) {
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    if (editorOpen) {
        RecordEditor(
            list, editingName, records,
            onDismiss = { editorOpen = false },
            onSave = { name, balance ->
                val error = onSave(editingName, name, balance)
                if (error == null) editorOpen = false
                error
            }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(list.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close ${list.title.lowercase()}") }
            }
            deleteMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (records.isEmpty()) item {
                    Text("No ${list.title.lowercase()} yet", Modifier.padding(vertical = 16.dp), color = colors.onSurfaceVariant)
                }
                items(records, key = { it.name }) { record ->
                    RecordActions(
                        label = record.name,
                        onEdit = {
                            editingName = record.name
                            editorOpen = true
                            deleteMessage = null
                        },
                        onDelete = {
                            pendingDelete = record.name
                            deleteMessage = null
                        }
                    ) { actions ->
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (list == ManagedList.Wallets) {
                                    Box(
                                        Modifier.size(42.dp).background(colors.secondaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Rounded.AccountBalanceWallet, null, tint = colors.onSecondaryContainer) }
                                } else {
                                    CategoryBadge(record.name)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(record.name, style = MaterialTheme.typography.titleMedium)
                                    record.balance?.let {
                                        Text(money(it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                    }
                                }
                                actions()
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    editingName = null
                    editorOpen = true
                    deleteMessage = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add ${list.itemName}")
            }
        }
    }
    pendingDelete?.let { name ->
        val delete = {
            deleteMessage = onDelete(name)
            pendingDelete = null
        }
        if (list == ManagedList.Categories) {
            ConfirmDestructiveAction(
                title = "Delete $name?",
                message = "This clears the category from linked transactions, budgets, and recurring payments. Those records will stay in Crisbud.",
                actionLabel = "Delete",
                onDismiss = { pendingDelete = null },
                onConfirm = delete
            )
        } else {
            ConfirmDelete(label = name, onDismiss = { pendingDelete = null }, onDelete = delete)
        }
    }
}

@Composable
private fun RecordEditor(
    list: ManagedList,
    initialName: String?,
    records: List<SettingsRecord>,
    onDismiss: () -> Unit,
    onSave: (String, Double?) -> String?
) {
    val wallet = list == ManagedList.Wallets
    val initialBalance = records.firstOrNull { it.name == initialName }?.balance
    var name by rememberSaveable(initialName) { mutableStateOf(initialName.orEmpty()) }
    var amount by rememberSaveable(initialName) { mutableStateOf(initialBalance?.toString() ?: "0") }
    var errorMessage by rememberSaveable(initialName) { mutableStateOf<String?>(null) }
    val balance = amount.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val trimmedName = name.trim()
    val duplicate = records.any { it.name != initialName && it.name.equals(trimmedName, ignoreCase = true) }
    val nameError = if (duplicate) "This ${list.itemName} name is already in use." else errorMessage
    val nameLabel = if (wallet) "Wallet name" else "Category name"
    EntrySheet(
        title = if (initialName == null) "Add ${list.itemName}" else "Edit ${list.itemName}",
        onDismiss = onDismiss,
        actionLabel = if (initialName == null) "Add ${list.itemName}" else "Save changes",
        onSave = { errorMessage = onSave(trimmedName, if (wallet) balance else null) },
        saveEnabled = trimmedName.isNotEmpty() && !duplicate && (!wallet || balance != null)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(nameLabel) },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium
        )
        if (wallet) OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Current balance") },
            prefix = { Text("₱") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = amount.isNotEmpty() && balance == null,
            supportingText = if (amount.isNotEmpty() && balance == null) { { Text("Enter zero or a positive amount.") } } else null,
            shape = MaterialTheme.shapes.medium
        )
    }
}
