package android.com.crisbud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun OverviewScreen(
    padding: PaddingValues,
    transactions: List<Transaction>,
    budgets: List<Budget>,
    month: String,
    onBudgets: () -> Unit,
    onActivity: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.background(colors.surfaceContainer, CircleShape).padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.CalendarToday, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Text(monthLabel(month), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        item {
            BalanceCard(
                income = transactions.filter { it.income }.sumOf { it.totalAmount },
                expenses = transactions.filterNot { it.income }.sumOf { it.totalAmount }
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeading("Monthly budgets", "View all", onBudgets)
        }
        item {
            if (budgets.isEmpty()) {
                Text("Your monthly category limits will appear here.", color = colors.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    budgets.sortedByDescending { it.utilization }.take(2).forEach { budget ->
                        BudgetSnapshot(budget, Modifier.weight(1f).fillMaxHeight(), onBudgets)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeading("Recent activity", "View all", onActivity)
        }
        if (transactions.isEmpty()) {
            item { Text("No entries this month.", color = colors.onSurfaceVariant) }
        }
        items(transactions.take(3), key = { it.id }) { TransactionRow(it) }
    }
}

@Composable
private fun BalanceCard(income: Double, expenses: Double) {
    val type = MaterialTheme.typography
    Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 46.dp, bottomEnd = 28.dp, bottomStart = 28.dp),
        color = BalanceIndigo, contentColor = Paper) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val balance = money(income - expenses)
                val textWidth = rememberTextMeasurer().measure(balance, type.displayMedium, softWrap = false).size.width
                val coinWidth = if (maxWidth >= 360.dp) 114.dp else 84.dp
                val showCoins = with(LocalDensity.current) { textWidth.toDp() + coinWidth + 8.dp <= maxWidth }
                Row(Modifier.fillMaxWidth().heightIn(min = 92.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Available this month", style = type.titleSmall, color = Lilac)
                        Text(balance, style = type.displayMedium)
                    }
                    if (showCoins) CoinStack(Modifier.size(coinWidth, 88.dp))
                }
            }
            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = Lilac.copy(alpha = .32f))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BalanceMetric("Income", income, Icons.Rounded.SouthWest, Lilac, Modifier.weight(1f))
                BalanceMetric("Expenses", expenses, Icons.Rounded.NorthEast, Marigold, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BalanceMetric(label: String, amount: Double, icon: ImageVector, accent: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(30.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = BalanceIndigo)
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Paper.copy(alpha = .8f))
            Text(money(amount), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BudgetSnapshot(budget: Budget, modifier: Modifier, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val progress = budget.utilization.toFloat()
    val balance = budget.limit - budget.spent
    val barColor = if (balance < 0) colors.error else colors.primary
    Surface(
        onClick = onOpen, modifier = modifier, shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(budget.category, Modifier.size(34.dp))
                Spacer(Modifier.weight(1f))
                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = barColor)
            }
            Spacer(Modifier.height(10.dp))
            Text(budget.categoryLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (balance < 0) "${money(-balance)} over" else "${money(balance)} left",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = barColor, trackColor = colors.surfaceContainerHighest
            )
        }
    }
}

@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val r = size.minDimension / 2
        val petals = Path()
        for (i in 0..160) {
            val angle = 2 * PI * i / 160
            val radius = r * (.905 + .085 * cos(10 * angle))
            val x = center.x + (cos(angle) * radius).toFloat()
            val y = center.y + (sin(angle) * radius).toFloat()
            if (i == 0) petals.moveTo(x, y) else petals.lineTo(x, y)
        }
        petals.close()
        drawPath(petals, Marigold)
        for (i in 0..2) {
            val x = r * (.59f + .38f * i)
            drawLine(BalanceIndigo, Offset(x, r * 1.34f), Offset(x + r * .14f, r * (1.02f - .24f * i)),
                strokeWidth = r * .18f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun CoinStack(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val factor = minOf(size.width / 120f, size.height / 96f)
        scale(factor, pivot = Offset.Zero) {
            rotate(-12f, pivot = Offset(62f, 62f)) {
                for (level in 0..3) {
                    val x = if (level % 2 == 0) 11f else 15f
                    val y = 53f - level * 8f
                    val fill = if (level == 3) Marigold else Lilac
                    drawOval(fill, Offset(x, y + 8f), Size(85f, 27f))
                    drawRect(fill, Offset(x, y + 13f), Size(85f, 8f))
                    drawArc(BalanceIndigo, 0f, 180f, false, Offset(x, y + 8f), Size(85f, 27f), style = Stroke(1.6f))
                    for (ridge in 1..12) {
                        val a = PI * ridge / 13
                        val start = Offset(x + 42.5f + (42f * cos(a)).toFloat(), y + 13.5f + (13f * sin(a)).toFloat())
                        drawLine(BalanceIndigo.copy(alpha = .65f), start, start + Offset(0f, 6.5f), 1.2f)
                    }
                    drawOval(fill, Offset(x, y), Size(85f, 27f))
                    drawOval(BalanceIndigo, Offset(x, y), Size(85f, 27f), style = Stroke(1.6f))
                    if (level == 3) {
                        drawOval(BalanceIndigo.copy(alpha = .65f), Offset(x + 7f, y + 4f), Size(71f, 19f), style = Stroke(1.0f))
                        drawSpark(x + 42f, y + 13.5f, 14f, 7f, BalanceIndigo)
                    }
                }
            }
            drawSpark(103f, 19f, 8f, 10f, Marigold)
            drawSpark(15f, 16f, 4f, 5f, Lilac)
            drawSpark(103f, 75f, 4f, 5f, Lilac)
        }
    }
}

private fun DrawScope.drawSpark(x: Float, y: Float, rx: Float, ry: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y - ry)
        quadraticTo(x, y, x + rx, y)
        quadraticTo(x, y, x, y + ry)
        quadraticTo(x, y, x - rx, y)
        quadraticTo(x, y, x, y - ry)
        close()
    }
    drawPath(path, color)
}
