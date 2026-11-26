package android.com.crisbud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

internal val LocalCategoryIcons = compositionLocalOf<Map<String, String>> { emptyMap() }

internal enum class CategoryIcon(
    slug: String,
    val label: String,
    val vector: ImageVector,
    val legacySymbol: String? = null
) {
    General("general", "General", Icons.Rounded.Category, "•"),
    Food("restaurant", "Food & Drink", Icons.Rounded.Restaurant, "🍜"),
    Groceries("shopping_cart", "Groceries", Icons.Rounded.ShoppingCart),
    Transport("directions_transit", "Transport", Icons.Rounded.DirectionsTransit, "🚆"),
    Car("directions_car", "Car", Icons.Rounded.DirectionsCar),
    Home("cottage", "Home", Icons.Rounded.Cottage, "⌂"),
    Utilities("bolt", "Utilities", Icons.Rounded.Bolt),
    Shopping("shopping_bag", "Shopping", Icons.Rounded.ShoppingBag, "🛍"),
    Health("favorite", "Health", Icons.Rounded.Favorite, "＋"),
    Fitness("fitness_center", "Fitness", Icons.Rounded.FitnessCenter),
    Entertainment("movie", "Entertainment", Icons.Rounded.Movie),
    Travel("flight", "Travel", Icons.Rounded.Flight),
    Education("school", "Education", Icons.Rounded.School),
    Gifts("redeem", "Gifts", Icons.Rounded.Redeem),
    Pets("pets", "Pets", Icons.Rounded.Pets),
    Savings("savings", "Savings", Icons.Rounded.Savings),
    Income("south_west", "Income", Icons.Rounded.SouthWest, "↗");

    val id = "material:$slug"
}

private fun findCategoryIcon(symbol: String): CategoryIcon? =
    CategoryIcon.entries.firstOrNull { it.id == symbol || it.legacySymbol == symbol }

@Composable
internal fun CategoryIconField(symbol: String, initialSymbol: String, onSelect: (String) -> Unit) {
    val options = remember(initialSymbol) {
        val initialIcon = findCategoryIcon(initialSymbol)
        buildList {
            if (initialIcon == null) add(initialSymbol)
            CategoryIcon.entries.forEach { add(if (it == initialIcon) initialSymbol else it.id) }
        }
    }
    ChoiceField(
        label = "Icon",
        value = symbol,
        options = options,
        optionLabel = { findCategoryIcon(it)?.label ?: "Current icon" },
        optionIcon = { CategoryIconBadge(it, modifier = Modifier.size(28.dp).clearAndSetSemantics {}) },
        onSelect = onSelect
    )
}

@Composable
internal fun CategoryBadge(category: String?, modifier: Modifier = Modifier) {
    val icons = LocalCategoryIcons.current
    CategoryIconBadge(category?.let { icons[it] }, modifier, category)
}

@Composable
private fun CategoryIconBadge(symbol: String?, modifier: Modifier, category: String? = null) {
    val icon = if (symbol == null) {
        CategoryIcon.entries.firstOrNull { it.legacySymbol != null && it.label == category }
    } else {
        findCategoryIcon(symbol)
    }
    val style = categoryStyle(icon)
    Box(modifier.size(42.dp).background(style.background, style.shape), contentAlignment = Alignment.Center) {
        if (icon == null && !symbol.isNullOrBlank()) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, color = style.foreground)
        } else {
            Icon((icon ?: CategoryIcon.General).vector, null, Modifier.size(21.dp), tint = style.foreground)
        }
    }
}

private data class CategoryStyle(val shape: Shape, val background: Color, val foreground: Color)

@Composable
private fun categoryStyle(icon: CategoryIcon?): CategoryStyle {
    val colors = MaterialTheme.colorScheme
    val flower = RoundedCornerShape(topStartPercent = 48, topEndPercent = 48, bottomStartPercent = 22, bottomEndPercent = 48)
    return when (icon) {
        CategoryIcon.Food -> CategoryStyle(flower, colors.tertiaryContainer, colors.onTertiaryContainer)
        CategoryIcon.Transport -> CategoryStyle(CutCornerShape(25), colors.secondaryContainer, colors.onSecondaryContainer)
        CategoryIcon.Home -> CategoryStyle(RoundedCornerShape(28), colors.primaryContainer, colors.onPrimaryContainer)
        CategoryIcon.Shopping -> CategoryStyle(RoundedCornerShape(30), colors.tertiaryContainer, colors.onTertiaryContainer)
        CategoryIcon.Health -> CategoryStyle(flower, colors.errorContainer, colors.onErrorContainer)
        CategoryIcon.Income -> CategoryStyle(CircleShape, colors.secondaryContainer, colors.onSecondaryContainer)
        else -> CategoryStyle(RoundedCornerShape(34), colors.primaryContainer, colors.onPrimaryContainer)
    }
}
