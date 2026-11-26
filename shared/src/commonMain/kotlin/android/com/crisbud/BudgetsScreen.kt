package android.com.crisbud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface PlanDialog {
    data class BudgetForm(val budget: Budget? = null) : PlanDialog
    data class RecurringForm(val item: Recurring? = null) : PlanDialog
    data class DeleteBudget(val budget: Budget) : PlanDialog
    data class DeleteRecurring(val item: Recurring) : PlanDialog
}

@Composable
internal fun BudgetsScreen(
    padding: PaddingValues,
    budgets: List<Budget>,
    recurring: List<Recurring>,
    categories: List<Pair<String, String>>,
    onAddBudget: (String, Double) -> Unit,
    onEditBudget: (Long, String, Double) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onAddRecurring: (String, String, Double, Long) -> Unit,
    onEditRecurring: (Long, String, String, Double, Long) -> Unit,
    onDeleteRecurring: (Long) -> Unit
) {
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MonthlyBudgetSummary(
                spent = budgets.sumOf { it.spent },
                limit = budgets.sumOf { it.limit }
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            SectionHeading("Category budgets", "Add") { dialog = PlanDialog.BudgetForm() }
        }
        if (budgets.isEmpty()) {
            item { PlanEmptyRow("Add a category budget to set a monthly spending limit.") }
        }
        items(budgets, key = { "budget-${it.id}" }) { budget ->
            RecordActions(
                label = "${budget.category} budget",
                onEdit = { dialog = PlanDialog.BudgetForm(budget) },
                onDelete = { dialog = PlanDialog.DeleteBudget(budget) }
            ) { actions -> BudgetCard(budget, actions) }
        }
        item {
            Spacer(Modifier.height(12.dp))
            SectionHeading("Recurring payments", "Add") { dialog = PlanDialog.RecurringForm() }
        }
        if (recurring.isEmpty()) {
            item { PlanEmptyRow("No recurring payments scheduled.") }
        }
        items(recurring, key = { "recurring-${it.id}" }) { item ->
            RecordActions(
                label = item.title,
                onEdit = { dialog = PlanDialog.RecurringForm(item) },
                onDelete = { dialog = PlanDialog.DeleteRecurring(item) }
            ) { actions -> RecurringPaymentRow(item, actions) }
        }
    }

    val names = categories.map { it.first }
    when (val selection = dialog) {
        is PlanDialog.BudgetForm -> {
            val budget = selection.budget
            val choices = if (budget == null) names else {
                (listOf(budget.category) + names.filter { name -> budgets.none { it.category == name } }).distinct()
            }
            BudgetEntrySheet(budget, choices, onDismiss = { dialog = null }) { category, amount ->
                if (budget == null) onAddBudget(category, amount) else onEditBudget(budget.id, category, amount)
                dialog = null
            }
        }
        is PlanDialog.RecurringForm -> {
            val item = selection.item
            val choices = (names + listOfNotNull(item?.category)).distinct()
            RecurringEntrySheet(item, choices, onDismiss = { dialog = null }) { title, category, amount, day ->
                if (item == null) onAddRecurring(title, category, amount, day)
                else onEditRecurring(item.id, title, category, amount, day)
                dialog = null
            }
        }
        is PlanDialog.DeleteBudget -> ConfirmDelete("${selection.budget.category} budget", { dialog = null }) {
            onDeleteBudget(selection.budget.id)
            dialog = null
        }
        is PlanDialog.DeleteRecurring -> ConfirmDelete(selection.item.title, { dialog = null }) {
            onDeleteRecurring(selection.item.id)
            dialog = null
        }
        null -> Unit
    }
}

@Composable
private fun MonthlyBudgetSummary(spent: Double, limit: Double) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val remaining = limit - spent
    val progress = budgetFraction(spent, limit)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text(if (remaining < 0) "Over budget this month" else "Left to spend this month", style = type.titleSmall)
            Text(money(abs(remaining)), style = type.displayMedium)
            Spacer(Modifier.height(16.dp))
            BudgetMeter(progress, colors.onTertiaryContainer)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Spent", style = type.labelMedium)
                    Text(money(spent), style = type.titleMedium)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Monthly limit", style = type.labelMedium)
                    Text(money(limit), style = type.titleMedium)
                }
            }
        }
    }
}

private fun budgetFraction(spent: Double, limit: Double): Float =
    if (limit > 0.0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f

@Composable
private fun BudgetMeter(fraction: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(20.dp).progressSemantics(fraction)) {
        val bars = 36
        val cell = size.width / bars
        val gap = minOf(3.dp.toPx(), cell * .4f)
        val width = cell - gap
        val radius = CornerRadius(width / 2, width / 2)
        repeat(bars) { index ->
            val position = Offset(index * cell + gap / 2, 0f)
            drawRoundRect(color.copy(alpha = .16f), position, Size(width, size.height), radius)
            val filled = (fraction * bars - index).coerceIn(0f, 1f)
            if (filled > 0f) drawRoundRect(color, position, Size(filled * width, size.height), radius)
        }
    }
}

@Composable
private fun BudgetCard(budget: Budget, actions: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val remaining = budget.limit - budget.spent
    val percent = (100 * budget.utilization).roundToInt()
    val progressColor = when {
        remaining < 0 -> colors.error
        percent >= 85 -> colors.tertiary
        else -> colors.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryBadge(budget.category)
                Column(Modifier.weight(1f)) {
                    Text(budget.category, style = type.titleMedium)
                    Text(
                        if (remaining < 0) "${money(-remaining)} over budget" else "${money(remaining)} left",
                        style = type.bodySmall,
                        color = if (remaining < 0) colors.error else colors.onSurfaceVariant
                    )
                }
                actions()
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { budget.utilization.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = progressColor,
                trackColor = colors.surfaceContainerHighest
            )
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${money(budget.spent)} of ${money(budget.limit)}",
                    modifier = Modifier.weight(1f),
                    style = type.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Text("$percent%", style = type.labelLarge, color = progressColor)
            }
        }
    }
}

@Composable
private fun RecurringPaymentRow(item: Recurring, actions: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                Column(
                    Modifier.widthIn(min = 48.dp).padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Day", style = type.labelSmall)
                    Text(item.dueDay.toString(), style = type.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = type.titleMedium)
                Text(item.category, style = type.bodySmall, color = colors.onSurfaceVariant)
                Text(money(item.amount), style = type.labelLarge, color = colors.primary)
            }
            actions()
        }
    }
}

@Composable
private fun PlanEmptyRow(message: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(
            message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BudgetEntrySheet(
    budget: Budget?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var category by rememberSaveable(budget?.id) { mutableStateOf(budget?.category ?: categories.firstOrNull().orEmpty()) }
    var amount by rememberSaveable(budget?.id) { mutableStateOf(budget?.limit?.toString().orEmpty()) }
    val limit = amount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    EntrySheet(
        title = if (budget == null) "New category budget" else "Edit category budget",
        onDismiss = onDismiss,
        actionLabel = "Save budget",
        onSave = { limit?.let { onSave(category, it) } },
        saveEnabled = category.isNotBlank() && limit != null
    ) {
        ChoiceField("Category", category, categories) { category = it }
        PlanAmountField("Monthly limit", amount) { amount = it }
    }
}

@Composable
private fun RecurringEntrySheet(
    item: Recurring?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Long) -> Unit
) {
    var title by rememberSaveable(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var category by rememberSaveable(item?.id) { mutableStateOf(item?.category ?: categories.firstOrNull().orEmpty()) }
    var amount by rememberSaveable(item?.id) { mutableStateOf(item?.amount?.toString().orEmpty()) }
    var day by rememberSaveable(item?.id) { mutableStateOf(item?.dueDay?.toString() ?: "1") }
    val value = amount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val due = day.toLongOrNull()?.takeIf { it in 1L..31L }
    EntrySheet(
        title = if (item == null) "New recurring payment" else "Edit recurring payment",
        onDismiss = onDismiss,
        actionLabel = "Save payment",
        onSave = { if (value != null && due != null) onSave(title.trim(), category, value, due) },
        saveEnabled = title.isNotBlank() && category.isNotBlank() && value != null && due != null
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Payment name") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        ChoiceField("Category", category, categories) { category = it }
        PlanAmountField("Amount", amount) { amount = it }
        OutlinedTextField(
            value = day,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) day = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Due day (1–31)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
    }
}

@Composable
private fun PlanAmountField(label: String, amount: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = amount,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text("₱") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}
