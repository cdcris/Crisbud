package android.com.crisbud

import android.com.crisbud.db.BudgetRepository
import android.com.crisbud.db.currentIsoDate
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToLong

private enum class Screen(val title: String, val icon: ImageVector) {
    Overview("Overview", Icons.Rounded.SpaceDashboard),
    Activity("Activity", Icons.Rounded.SwapVert),
    Budgets("Budgets", Icons.Rounded.DonutLarge),
    Settings("Settings", Icons.Rounded.Tune)
}

internal data class Transaction(
    val id: Long,
    val title: String,
    val category: String,
    val unitAmount: Double,
    val income: Boolean,
    val date: String,
    val wallet: String,
    val day: String,
    val quantity: Long
) {
    val totalAmount: Double get() = unitAmount * quantity
}
internal data class Budget(val id: Long, val category: String, val spent: Double, val limit: Double) {
    val utilization: Double get() = if (limit > 0.0) spent / limit else 0.0
}
internal data class Recurring(val id: Long, val title: String, val category: String, val amount: Double, val dueDay: Long)

@Composable
fun CrisbudApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.Overview) }
    var showEntry by rememberSaveable { mutableStateOf(false) }
    var fileMessage by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val repository = remember { BudgetRepository() }
    val today = remember { currentIsoDate() }
    val month = today.take(7)

//    LaunchedEffect(month) {
//        repository.seedIfNeeded(month)
//        revision++
//    }

    val categories = remember(revision) { repository.categories() }
    val walletBalances = remember(revision) { repository.walletBalances() }
    val wallets = walletBalances.map { it.name }
    val transactions = remember(revision, today) {
        repository.transactions().map {
            Transaction(
                it.id, it.title, it.category, it.amount, it.income, it.date, it.wallet,
                if (it.date == today) "Today" else it.date, it.quantity
            )
        }
    }
    val monthTransactions = remember(transactions, month) { transactions.filter { it.date.take(7) == month } }
    val budgets = remember(revision, month) { repository.budgets(month).map { Budget(it.id, it.category, it.spent, it.limit) } }
    val recurring = remember(revision) { repository.recurring().map { Recurring(it.id, it.title, it.category, it.amount, it.dueDay) } }
    val screenStates = rememberSaveableStateHolder()

    CrisbudTheme {
        CompositionLocalProvider(LocalCategoryIcons provides remember(categories) { categories.toMap() }) {
            AppFrame(screen, onSelect = { screen = it }, onAdd = { showEntry = true }) { padding ->
                screenStates.SaveableStateProvider(screen.name) {
                    when (screen) {
                        Screen.Overview -> OverviewScreen(
                            padding, monthTransactions, budgets, month,
                            onBudgets = { screen = Screen.Budgets }, onActivity = { screen = Screen.Activity }
                        )
                        Screen.Activity -> ActivityScreen(
                            padding, transactions, categories.map { it.first }, wallets, month,
                            onDelete = { repository.deleteTransaction(it); revision++ },
                            onEdit = { transaction ->
                                repository.updateTransaction(
                                    transaction.id, transaction.title, transaction.category, transaction.unitAmount,
                                    transaction.income, transaction.date, transaction.wallet, transaction.quantity
                                )
                                revision++
                            }
                        )
                        Screen.Budgets -> BudgetsScreen(
                            padding, budgets, recurring, categories,
                            onAddBudget = { category, limit -> repository.addBudget(category, month, limit); revision++ },
                            onEditBudget = { id, category, limit -> repository.updateBudget(id, category, limit); revision++ },
                            onDeleteBudget = { repository.deleteBudget(it); revision++ },
                            onAddRecurring = { title, category, amount, day -> repository.addRecurring(title, category, amount, day); revision++ },
                            onEditRecurring = { id, title, category, amount, day -> repository.updateRecurring(id, title, category, amount, day); revision++ },
                            onDeleteRecurring = { repository.deleteRecurring(it); revision++ }
                        )
                        Screen.Settings -> SettingsScreen(
                            padding, categories, walletBalances,
                            onAddCategory = { repository.addCategory(it); revision++ },
                            onRenameCategory = { old, new -> repository.renameCategory(old, new).also { if (it) revision++ } },
                            onDeleteCategory = { name -> repository.deleteCategory(name).also { if (it) revision++ } },
                            onAddWallet = { repository.addWallet(it); revision++ },
                            onRenameWallet = { old, new -> repository.renameWallet(old, new).also { if (it) revision++ } },
                            onDeleteWallet = { name -> repository.deleteWallet(name).also { if (it) revision++ } },
                            onSetWalletBalance = { name, amount -> repository.updateWalletBalance(name, amount); revision++ },
                            sql = repository::exportSql,
                            onSqlImported = { sql -> repository.importSql(sql); revision++ },
                            csv = repository::exportCsv,
                            onCsvImported = { csv ->
                                repository.importCsv(csv)
                                revision++
                            },
                            onFileMessage = { fileMessage = it },
                            onResetData = {
                                val result = runCatching { repository.resetData() }
                                if (result.isSuccess) revision++
                                result.isSuccess
                            }
                        )
                    }
                }
            }
            if (showEntry) {
                AddTransactionDialog(categories.map { it.first }, wallets, onDismiss = { showEntry = false }) {
                    repository.addTransaction(it.title, it.category, it.unitAmount, it.income, it.date, it.wallet, it.quantity)
                    revision++
                    showEntry = false
                }
            }
            fileMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = { fileMessage = null },
                    icon = { Icon(Icons.Rounded.ImportExport, null) },
                    title = { Text("Data transfer") },
                    text = { Text(message) },
                    confirmButton = { Button(onClick = { fileMessage = null }) { Text("Done") } }
                )
            }
        }
    }
}

@Composable
private fun AppFrame(
    screen: Screen,
    onSelect: (Screen) -> Unit,
    onAdd: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= 720.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    NavigationRail(containerColor = colors.surface, windowInsets = WindowInsets(0)) {
                        Spacer(Modifier.height(12.dp))
                        Screen.entries.forEach { item ->
                            NavigationRailItem(
                                selected = screen == item, onClick = { onSelect(item) },
                                icon = { Icon(item.icon, null) }, label = { Text(item.title) }
                            )
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0),
                    containerColor = colors.background,
                    topBar = { Masthead(showAdd = screen != Screen.Settings, onAdd = onAdd) },
                    bottomBar = {
                        if (!wide) {
                            Column {
                                HorizontalDivider(color = colors.outlineVariant)
                                NavigationBar(containerColor = colors.surface, windowInsets = WindowInsets(0), tonalElevation = 0.dp) {
                                    Screen.entries.forEach { item ->
                                        NavigationBarItem(
                                            selected = screen == item, onClick = { onSelect(item) },
                                            icon = { Icon(item.icon, null) }, label = { Text(item.title) },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = colors.primary,
                                                selectedIconColor = colors.onPrimary,
                                                selectedTextColor = colors.primary,
                                                unselectedIconColor = colors.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) { content(padding) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Masthead(showAdd: Boolean, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.widthIn(max = 840.dp).fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            BrandMark(Modifier.size(35.dp))
            Text("Crisbud", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (showAdd) {
                FilledTonalButton(
                    onClick = onAdd,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add entry")
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

internal fun money(value: Double): String {
    if (!value.isFinite()) return "—"
    val cents = (abs(value) * 100).roundToLong()
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val sign = if (value < 0 && cents != 0L) "−" else ""
    return "${sign}₱${whole}.${(cents % 100).toString().padStart(2, '0')}"
}

internal fun monthLabel(month: String): String {
    val names = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val number = month.substringAfter('-', "").toIntOrNull() ?: return month
    val name = names.getOrNull(number - 1) ?: return month
    return "$name ${month.substringBefore('-')}"
}


