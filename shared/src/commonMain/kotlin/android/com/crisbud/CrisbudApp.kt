package android.com.crisbud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.com.crisbud.db.BudgetRepository
import android.com.crisbud.db.currentIsoDate

private val forest = Color(0xFF285B45)
private val mint = Color(0xFFEAF3EC)
private val ink = Color(0xFF1E2A22)
private val muted = Color(0xFF748078)
private val orange = Color(0xFFE5A35A)

private enum class Screen(val title: String, val icon: ImageVector) {
    Overview("Overview", Icons.Default.Home),
    Activity("Activity", Icons.Default.ReceiptLong),
    Budgets("Budgets", Icons.Default.Savings),
    Settings("Settings", Icons.Default.Settings)
}

private data class Transaction(val id: Long, val title: String, val category: String, val amount: Double, val income: Boolean, val day: String)
private data class Budget(val category: String, val spent: Double, val limit: Double, val icon: String)
private data class Recurring(val title: String, val category: String, val amount: Double, val dueDay: Long)

@Composable
fun CrisbudApp() {
    var screen by remember { mutableStateOf(Screen.Overview) }
    var showEntry by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val repository = remember { BudgetRepository() }
    val today = remember { currentIsoDate() }
    val month = today.substring(0, 7)
    LaunchedEffect(Unit) { repository.seedIfNeeded(month); revision++ }
    val storedTransactions = remember(revision) { repository.transactions() }
    val currentMonthTransactions = remember(revision) { repository.transactions(month) }
    val categories = remember(revision) { repository.categories() }
    val budgets = remember(revision) { repository.budgets(month).map { stored -> Budget(stored.category, stored.spent, stored.limit, categories.firstOrNull { it.first == stored.category }?.second ?: "•") } }
    val recurring = remember(revision) { repository.recurring().map { Recurring(it.title, it.category, it.amount, it.dueDay) } }
    val transactions = storedTransactions.map { Transaction(it.id, it.title, it.category, it.amount, it.income, if (it.date == today) "Today" else it.date) }
    val monthTransactions = currentMonthTransactions.map { Transaction(it.id, it.title, it.category, it.amount, it.income, if (it.date == today) "Today" else it.date) }
    val spent = monthTransactions.filter { !it.income }.sumOf { it.amount }
    val income = monthTransactions.filter { it.income }.sumOf { it.amount }

    CrisbudTheme {
        Scaffold(
            containerColor = Color(0xFFF6F8F5),
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    Screen.values().forEach { item ->
                        NavigationBarItem(selected = screen == item, onClick = { screen = item },
                            icon = { Icon(item.icon, contentDescription = item.title) }, label = { Text(item.title) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = forest, selectedTextColor = forest, indicatorColor = mint))
                    }
                }
            },
            floatingActionButton = {
                if (screen != Screen.Settings) FloatingActionButton(onClick = { showEntry = true }, containerColor = forest, contentColor = Color.White) {
                    Icon(Icons.Default.Add, contentDescription = "Add transaction")
                }
            }
        ) { padding ->
            when (screen) {
                Screen.Overview -> OverviewScreen(padding, income, spent, monthTransactions, budgets, month, { screen = Screen.Activity })
                Screen.Activity -> ActivityScreen(
                    padding, transactions, categories.map { it.first },
                    onDelete = { id -> repository.deleteTransaction(id); revision++ },
                    onEdit = { id, title, category, amount, isIncome ->
                        repository.updateTransaction(id, title, category, amount, isIncome)
                        revision++
                    }
                )
                Screen.Budgets -> BudgetsScreen(padding, budgets, recurring, categories,
                    onAddBudget = { category, limit -> repository.addBudget(category, month, limit); revision++ },
                    onAddRecurring = { title, category, amount, dueDay -> repository.addRecurring(title, category, amount, dueDay); revision++ })
                Screen.Settings -> SettingsScreen(padding, categories, onAddCategory = { name -> repository.addCategory(name); revision++ }, onBackup = { showBackup = true })
            }
        }
        if (showEntry) AddTransactionDialog(categoryNames = categories.map { it.first }, onDismiss = { showEntry = false }, onSave = { item ->
            repository.addTransaction(item.title, item.category, item.amount, item.income, today)
            revision++
            showEntry = false
        })
        if (showBackup) AlertDialog(onDismissRequest = { showBackup = false },
            title = { Text("Your backup is ready") },
            text = { Text("Your SQLite data is ready to export as JSON. ${transactions.size} transactions, ${budgets.size} budgets and ${recurring.size} recurring items are included.\n\n${repository.exportJson().take(1600)}") },
            confirmButton = { TextButton(onClick = { showBackup = false }) { Text("Done") } })
    }
}

@Composable
private fun OverviewScreen(padding: PaddingValues, income: Double, spent: Double, transactions: List<Transaction>, budgets: List<Budget>, month: String, onSeeAll: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Good morning,", color = muted, fontSize = 14.sp); Text("Let’s check in", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = ink) }
                Box(Modifier.size(44.dp).background(mint, CircleShape), contentAlignment = Alignment.Center) { Text("🌿", fontSize = 22.sp) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(forest, RoundedCornerShape(24.dp)).padding(22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${month.uppercase()} OVERVIEW", color = Color.White.copy(alpha = .75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("This month", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp)); Text("Available this month", color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                Text(money(income - spent), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryPill("↘", "Spent", money(spent), Modifier.weight(1f))
                    SummaryPill("↗", "Income", money(income), Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budgets", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ink)
                Text("See all", color = forest, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onSeeAll))
            }
        }
        items(budgets.take(2)) { BudgetCard(it) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ink)
                Text("See all", color = forest, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onSeeAll))
            }
        }
        items(transactions.take(3)) { TransactionRow(it) }
    }
}

@Composable
private fun SummaryPill(symbol: String, label: String, value: String, modifier: Modifier) {
    Row(modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(symbol, color = Color.White, fontSize = 17.sp)
        Column { Text(label, color = Color.White.copy(alpha = .72f), fontSize = 11.sp); Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun BudgetCard(budget: Budget) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(mint, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text(budget.icon, fontSize = 18.sp) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(budget.category, color = ink, fontWeight = FontWeight.SemiBold); Text("${money(budget.limit - budget.spent)} left", color = muted, fontSize = 12.sp) }
            Text("${(budget.spent / budget.limit * 100).toInt()}%", color = forest, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(progress = { (budget.spent / budget.limit).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = if (budget.spent / budget.limit > .85) orange else forest, trackColor = mint)
        Spacer(Modifier.height(8.dp)); Text("${money(budget.spent)} of ${money(budget.limit)}", color = muted, fontSize = 11.sp)
    }
}

@Composable
private fun ActivityScreen(
    padding: PaddingValues,
    transactions: List<Transaction>,
    categories: List<String>,
    onDelete: (Long) -> Unit,
    onEdit: (Long, String, String, Double, Boolean) -> Unit
) {
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var actionsForTransaction by remember { mutableStateOf<Long?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(22.dp, 22.dp, 22.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Text("Activity", fontSize = 28.sp, color = ink, fontWeight = FontWeight.Bold); Text("Your money, one moment at a time", color = muted, fontSize = 14.sp) }
        item { Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("October 2026  ▾", color = ink, fontWeight = FontWeight.SemiBold); Text("${transactions.size} entries", color = muted) } }
        items(transactions, key = { it.id }) { item ->
            TransactionRow(
                item = item,
                showDelete = actionsForTransaction == item.id,
                onLongClick = { actionsForTransaction = if (actionsForTransaction == item.id) null else item.id },
                onEditClick = { transactionToEdit = item },
                onDeleteClick = { transactionToDelete = item }
            )
        }
    }
    transactionToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete activity?") },
            text = { Text("\"${item.title}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(item.id); transactionToDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("Cancel") } }
        )
    }
    transactionToEdit?.let { item ->
        EditTransactionDialog(
            item = item,
            categoryNames = categories,
            onDismiss = { transactionToEdit = null },
            onSave = { title, category, amount, income ->
                onEdit(item.id, title, category, amount, income)
                transactionToEdit = null
                actionsForTransaction = null
            }
        )
    }
}

@Composable
private fun TransactionRow(
    item: Transaction,
    showDelete: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(Color.White, RoundedCornerShape(16.dp))
        .padding(14.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).then(if (onLongClick != null) Modifier.pointerInput(item.id) {
                detectTapGestures(onLongPress = { onLongClick() })
            } else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).background(if (item.income) mint else Color(0xFFF5F0E9), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(if (item.income) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, contentDescription = null, tint = if (item.income) forest else orange)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, color = ink); Text("${item.category}  ·  ${item.day}", color = muted, fontSize = 12.sp) }
            Text((if (item.income) "+" else "−") + money(item.amount), color = if (item.income) forest else ink, fontWeight = FontWeight.Bold)
        }
        if (showDelete) {
            TextButton(onClick = { onEditClick?.invoke() }) { Text("Edit", color = forest) }
            if (onDeleteClick != null) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${item.title}", tint = muted)
                }
            }
        }
    }
}

@Composable
private fun BudgetsScreen(padding: PaddingValues, budgets: List<Budget>, recurring: List<Recurring>, categories: List<Pair<String, String>>, onAddBudget: (String, Double) -> Unit, onAddRecurring: (String, String, Double, Long) -> Unit) {
    var addBudget by remember { mutableStateOf(false) }
    var addRecurring by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(22.dp, 22.dp, 22.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Text("Your plan", fontSize = 28.sp, color = ink, fontWeight = FontWeight.Bold); Text("A little structure, a lot more ease.", color = muted, fontSize = 14.sp) }
        item { Column(Modifier.fillMaxWidth().background(mint, RoundedCornerShape(18.dp)).padding(18.dp)) { Text("MONTHLY TOTAL", color = forest, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text("${money(budgets.sumOf { it.spent })} spent of ${money(budgets.sumOf { it.limit })}", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("${money((budgets.sumOf { it.limit } - budgets.sumOf { it.spent }).coerceAtLeast(0.0))} remaining", color = forest, fontSize = 13.sp) } }
        item { Text("Categories", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ink) }
        items(budgets) { BudgetCard(it) }
        item { OutlinedButton(onClick = { addBudget = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a category budget") } }
        item { Text("Recurring items", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ink) }
        items(recurring) { item -> RecurringRow(item.title, "${item.category} · due day ${item.dueDay}", item.amount, "↻") }
        item { OutlinedButton(onClick = { addRecurring = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add recurring item") } }
    }
    if (addBudget) SimpleEntryDialog(title = "New category budget", categories = categories.map { it.first }, amountLabel = "Monthly limit", onDismiss = { addBudget = false }) { category, _, amount -> onAddBudget(category, amount); addBudget = false }
    if (addRecurring) RecurringEntryDialog(categories = categories.map { it.first }, onDismiss = { addRecurring = false }) { title, category, amount, dueDay -> onAddRecurring(title, category, amount, dueDay); addRecurring = false }
}

@Composable
private fun RecurringRow(name: String, detail: String, amount: Double, icon: String) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(mint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(icon) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(name, color = ink, fontWeight = FontWeight.SemiBold); Text(detail, color = muted, fontSize = 12.sp) }
        Text(money(amount), color = ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, categories: List<Pair<String, String>>, onAddCategory: (String) -> Unit, onBackup: () -> Unit) {
    var addCategory by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your space", fontSize = 28.sp, color = ink, fontWeight = FontWeight.Bold)
        Text("A budget that feels like yours.", color = muted)
        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable { addCategory = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Category, null, tint = forest); Column(Modifier.weight(1f).padding(start = 13.dp)) { Text("Categories", color = ink, fontWeight = FontWeight.SemiBold); Text(categories.joinToString(" · ") { it.first }, color = muted, fontSize = 12.sp) }; Text("›", color = muted, fontSize = 24.sp)
        }
        SettingsCard(Icons.Default.Savings, "Monthly plan", "Adjust your budget for this month")
        SettingsCard(Icons.Default.ReceiptLong, "Recurring items", "Keep regular bills in view")
        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable(onClick = onBackup).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, null, tint = forest); Column(Modifier.weight(1f).padding(start = 13.dp)) { Text("Export & backup", color = ink, fontWeight = FontWeight.SemiBold); Text("Take a copy of your budget data", color = muted, fontSize = 12.sp) }; Text("›", color = muted, fontSize = 24.sp)
        }
        Spacer(Modifier.weight(1f)); Text("Your money stays yours. Private by design.", color = muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    if (addCategory) CategoryEntryDialog(onDismiss = { addCategory = false }) { onAddCategory(it); addCategory = false }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, description: String) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = forest); Column(Modifier.weight(1f).padding(start = 13.dp)) { Text(title, color = ink, fontWeight = FontWeight.SemiBold); Text(description, color = muted, fontSize = 12.sp) }; Text("›", color = muted, fontSize = 24.sp)
    }
}

@Composable
private fun EditTransactionDialog(
    item: Transaction,
    categoryNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Boolean) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var amount by remember(item.id) { mutableStateOf(item.amount.toString()) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var income by remember(item.id) { mutableStateOf(item.income) }
    var expanded by remember { mutableStateOf(false) }
    val categories = (categoryNames + category).distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit activity", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().background(mint, RoundedCornerShape(14.dp)).padding(4.dp)) {
                    listOf(false to "Expense", true to "Income").forEach { (isIncome, label) ->
                        Box(
                            Modifier.weight(1f)
                                .background(if (income == isIncome) Color.White else Color.Transparent, RoundedCornerShape(11.dp))
                                .clickable { income = isIncome }
                                .padding(11.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (income == isIncome) forest else muted, fontWeight = FontWeight.SemiBold) }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("What was it for?") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, prefix = { Text("$") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$category     ▾", color = ink) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amount.toDoubleOrNull()
                    if (title.isNotBlank() && parsed != null && parsed > 0) onSave(title.trim(), category, parsed, income)
                },
                colors = ButtonDefaults.buttonColors(containerColor = forest)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddTransactionDialog(categoryNames: List<String>, onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food & Drink") }
    var income by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val categories = categoryNames.ifEmpty { listOf("Food & Drink", "Transport", "Home", "Other") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().background(mint, RoundedCornerShape(14.dp)).padding(4.dp)) {
                    listOf(false to "Expense", true to "Income").forEach { (isIncome, label) ->
                        Box(Modifier.weight(1f).background(if (income == isIncome) Color.White else Color.Transparent, RoundedCornerShape(11.dp)).clickable { income = isIncome }.padding(11.dp), contentAlignment = Alignment.Center) { Text(label, color = if (income == isIncome) forest else muted, fontWeight = FontWeight.SemiBold) }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("What was it for?") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, prefix = { Text("$") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$category     ▾", color = ink) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false }) } }
                }
            }
        },
        confirmButton = { Button(onClick = { val parsed = amount.toDoubleOrNull(); if (title.isNotBlank() && parsed != null && parsed > 0) onSave(Transaction(0, title.trim(), category, parsed, income, "Today")) }, colors = ButtonDefaults.buttonColors(containerColor = forest)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SimpleEntryDialog(title: String, categories: List<String>, amountLabel: String, onDismiss: () -> Unit, onSave: (String, String, Double) -> Unit) {
    var category by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(category.ifBlank { "Choose category" }, color = ink) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false }) } }
            }
            OutlinedTextField(amount, { amount = it }, label = { Text(amountLabel) }, prefix = { Text("$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        }
    }, confirmButton = { Button(onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(category, "", it) } }, colors = ButtonDefaults.buttonColors(containerColor = forest)) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RecurringEntryDialog(categories: List<String>, onDismiss: () -> Unit, onSave: (String, String, Double, Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("1") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add recurring item") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(category.ifBlank { "Choose category" }, color = ink) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false }) } }
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, prefix = { Text("$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, label = { Text("Day of month (1–31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
    }, confirmButton = { Button(onClick = { val value = amount.toDoubleOrNull(); val day = dueDay.toLongOrNull(); if (title.isNotBlank() && value != null && value > 0 && day != null && day in 1..31) onSave(title.trim(), category, value, day) }, colors = ButtonDefaults.buttonColors(containerColor = forest)) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun CategoryEntryDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New category") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = forest)) { Text("Add") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun money(value: Double): String = "$" + ((value * 100).toLong() / 100.0).let { amount ->
    val whole = amount.toLong()
    val cents = ((amount - whole) * 100).toInt()
    whole.toString() + "." + if (cents < 10) "0$cents" else cents.toString()
}

@Composable
private fun CrisbudTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = forest, secondary = Color(0xFF6A9E79), tertiary = orange, background = Color(0xFFF6F8F5), surface = Color.White, surfaceVariant = mint, onSurfaceVariant = muted), content = content)
}
