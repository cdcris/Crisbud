package android.com.crisbud

import android.com.crisbud.db.currentIsoDate
import android.com.crisbud.db.epochMillisFromIsoDate
import android.com.crisbud.db.isoDateFromEpochMillis
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val ALL_TIME = "All time"

@Composable
internal fun ActivityScreen(
    padding: PaddingValues,
    transactions: List<Transaction>,
    categories: List<String>,
    walletNames: List<String>,
    month: String,
    onDelete: (Long) -> Unit,
    onEdit: (Transaction) -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedPeriod by rememberSaveable(month) { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val months = (transactions.map { it.date.take(7) } + month).distinct().sortedDescending()
    val periods = listOf(ALL_TIME) + months
    val activePeriod = selectedPeriod?.takeIf { it in periods }
        ?: transactions.firstOrNull()?.date?.take(7) ?: month
    val activeCategory = selectedCategory?.takeIf { it in categories }
    val entries = transactions.filter { entry ->
        (activePeriod == ALL_TIME || entry.date.startsWith(activePeriod)) &&
            (activeCategory == null || entry.category == activeCategory)
    }
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActivityFilterPicker(
                    selected = activePeriod,
                    options = periods,
                    label = ::monthLabel,
                    description = "Period filter",
                    modifier = Modifier.weight(1f),
                    onSelect = { selectedPeriod = it }
                )
                ActivityFilterPicker(
                    selected = activeCategory,
                    options = listOf(null) + categories,
                    label = { it ?: "All categories" },
                    description = "Category filter",
                    modifier = Modifier.weight(1f),
                    onSelect = { selectedCategory = it }
                )
            }
            Spacer(Modifier.height(8.dp))
            val count = entries.size
            Text(
                if (count == 1) "1 entry" else "$count entries",
                modifier = Modifier.fillMaxWidth(),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActivityTotal(
                    "Income", entries.filter { it.income }.sumOf { it.totalAmount },
                    Icons.Rounded.ArrowDownward, colors.primaryContainer, colors.onPrimaryContainer,
                    RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp), Modifier.weight(1f)
                )
                ActivityTotal(
                    "Expenses", entries.filter { !it.income }.sumOf { it.totalAmount },
                    Icons.Rounded.ArrowUpward, colors.tertiaryContainer, colors.onTertiaryContainer,
                    RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp), Modifier.weight(1f)
                )
            }
        }
        if (entries.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainerLow
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, tint = colors.primary)
                        Text(
                            when {
                                activeCategory != null -> "No transactions match this category"
                                activePeriod == ALL_TIME -> "No transactions"
                                else -> "No transactions this month"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
        entries.groupBy { it.date }.forEach { (date, dayEntries) ->
            item(key = "day-$date") {
                Text(
                    if (dayEntries.first().day == "Today") "Today" else activityDate(date),
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            itemsIndexed(dayEntries, key = { _, entry -> entry.id }) { index, entry ->
                val top = if (index == 0) 20.dp else 5.dp
                val bottom = if (index == dayEntries.lastIndex) 20.dp else 5.dp
                RecordActions(
                    label = entry.title,
                    onEdit = { editingId = entry.id },
                    onDelete = { deletingId = entry.id }
                ) { actions ->
                    LedgerRow(entry, RoundedCornerShape(top, top, bottom, bottom), actions)
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
    transactions.firstOrNull { it.id == editingId }?.let { entry ->
        TransactionForm(entry, categories, walletNames, onDismiss = { editingId = null }) { edited ->
            onEdit(edited)
            editingId = null
        }
    }
    transactions.firstOrNull { it.id == deletingId }?.let { entry ->
        ConfirmDelete(label = entry.title, onDismiss = { deletingId = null }) {
            onDelete(entry.id)
            deletingId = null
        }
    }
}

@Composable
private fun <T> ActivityFilterPicker(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    description: String,
    modifier: Modifier,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(label(selected), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    trailingIcon = {
                        if (option == selected) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ActivityTotal(
    label: String,
    total: Double,
    icon: ImageVector,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier
) {
    Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            Text(money(total), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
internal fun TransactionRow(item: Transaction) {
    LedgerRow(item, MaterialTheme.shapes.medium)
}

@Composable
private fun LedgerRow(item: Transaction, shape: Shape, actions: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val amount = (if (item.income) "+" else "−") + money(item.totalAmount)
    val amountColor = if (item.income) colors.primary else colors.onSurface
    val details = buildList {
        if (!item.income && item.quantity > 1) add("Qty ${item.quantity}")
        add(item.category)
        add(item.wallet)
        if (actions == null) add(if (item.day == "Today") "Today" else activityDate(item.date))
    }.filter(String::isNotBlank).joinToString(" · ")
    Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainerLow) {
        BoxWithConstraints {
            val stackAmount = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f || amount.length > 12
            Row(
                Modifier.fillMaxWidth().padding(
                    start = 12.dp, end = if (actions == null) 14.dp else 2.dp, top = 12.dp, bottom = 12.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(item.category)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        item.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (stackAmount) Text(amount, style = MaterialTheme.typography.titleMedium, color = amountColor)
                }
                if (!stackAmount) Text(amount, style = MaterialTheme.typography.titleMedium, color = amountColor)
                actions?.invoke()
            }
        }
    }
}

private fun activityDate(iso: String): String {
    val day = iso.substringAfterLast('-').toIntOrNull() ?: return iso
    val month = monthLabel(iso.take(7)).substringBefore(' ')
    return "$month $day"
}

@Composable
internal fun AddTransactionDialog(
    categoryNames: List<String>,
    walletNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    TransactionForm(null, categoryNames, walletNames, onDismiss, onSave)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionForm(
    entry: Transaction?,
    categoryNames: List<String>,
    walletNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val categories = categoryNames
    val wallets = (walletNames + listOfNotNull(entry?.wallet)).distinct()
    var title by rememberSaveable(entry?.id) { mutableStateOf(entry?.title.orEmpty()) }
    var amount by rememberSaveable(entry?.id) { mutableStateOf(entry?.unitAmount?.toString().orEmpty()) }
    var quantityText by rememberSaveable(entry?.id) { mutableStateOf((entry?.quantity ?: 1).toString()) }
    var income by rememberSaveable(entry?.id) { mutableStateOf(entry?.income ?: false) }
    var date by rememberSaveable(entry?.id) { mutableStateOf(entry?.date ?: currentIsoDate()) }
    var category by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.category ?: categories.firstOrNull { it == "Food & Drink" } ?: categories.firstOrNull().orEmpty())
    }
    var wallet by rememberSaveable(entry?.id) { mutableStateOf(entry?.wallet ?: wallets.firstOrNull().orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val value = amount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val quantity = if (income) 1L else quantityText.trim().toLongOrNull()?.takeIf { it > 0 }
    val total = if (value != null && quantity != null) value * quantity else null
    val totalTooLarge = total?.isFinite() == false
    val millis = remember(date) { epochMillisFromIsoDate(date) }

    EntrySheet(
        title = if (entry == null) "New transaction" else "Edit transaction",
        onDismiss = onDismiss,
        actionLabel = if (entry == null) "Add transaction" else "Save changes",
        saveEnabled = title.isNotBlank() && total != null && !totalTooLarge &&
            millis != null && category in categories && wallet in wallets,
        onSave = {
            if (value != null && quantity != null && !totalTooLarge) {
                onSave(Transaction(entry?.id ?: 0L, title.trim(), category, value, income, date, wallet, date, quantity))
            }
        }
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Expense", "Income").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = income == (index == 1),
                    onClick = { income = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2)
                ) { Text(label) }
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(if (income) "Amount" else "Unit price") },
            prefix = { Text("₱", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            isError = totalTooLarge,
            supportingText = if (totalTooLarge) { { Text("The transaction total is too large.") } } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        if (!income) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                isError = quantity == null,
                supportingText = when {
                    quantity == null -> { { Text("Enter a positive whole number.") } }
                    quantity > 1 && total != null && !totalTooLarge -> { { Text("Total ${money(total)}") } }
                    else -> null
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        ChoiceField(label = "Category", value = category, options = categories, onSelect = { category = it })
        ChoiceField(label = "Wallet", value = wallet, options = wallets, onSelect = { wallet = it })
        OutlinedButton(
            onClick = { showDatePicker = true }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Date", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(date)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(20.dp))
        }
    }
    if (showDatePicker) {
        val year = date.take(4).toIntOrNull() ?: DatePickerDefaults.YearRange.first
        val yearRange = minOf(year, DatePickerDefaults.YearRange.first)..maxOf(year, DatePickerDefaults.YearRange.last)
        val picker = rememberDatePickerState(initialSelectedDateMillis = millis, yearRange = yearRange)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let { date = isoDateFromEpochMillis(it) }
                        showDatePicker = false
                    }
                ) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = picker) }
    }
}
