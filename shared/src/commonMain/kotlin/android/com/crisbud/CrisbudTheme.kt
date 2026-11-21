package android.com.crisbud

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The illustrated balance card keeps the same inks in both appearances.
internal val BalanceIndigo = Color(0xFF32296B)
internal val Marigold = Color(0xFFFFCE64)
internal val Lilac = Color(0xFFC7BDFF)
internal val Paper = Color(0xFFFFFBF3)

private val daylight = lightColorScheme(
    primary = Color(0xFF5142A1), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DFFF), onPrimaryContainer = Color(0xFF392D7A),
    secondary = Color(0xFF496958), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9ECDD), onSecondaryContainer = Color(0xFF234B36),
    tertiary = Color(0xFF825700), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4A1), onTertiaryContainer = Color(0xFF4C350B),
    background = Color(0xFFF8F6F0), onBackground = Color(0xFF242331),
    surface = Color(0xFFFFFDF8), onSurface = Color(0xFF242331),
    surfaceVariant = Color(0xFFECE9E2), onSurfaceVariant = Color(0xFF615E69),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFF0EDE6), surfaceContainerHigh = Color(0xFFECE8E1),
    surfaceContainerHighest = Color(0xFFE4E0D9),
    outline = Color(0xFF827D89), outlineVariant = Color(0xFFDDD9D2),
    inverseSurface = Color(0xFF332F3C), inverseOnSurface = Color(0xFFF7F2F9), inversePrimary = Lilac,
    error = Color(0xFF9B3732), onError = Color.White,
    errorContainer = Color(0xFFFFDCD5), onErrorContainer = Color(0xFF6A1B16)
)

private val night = darkColorScheme(
    primary = Lilac, onPrimary = Color(0xFF2C225E),
    primaryContainer = Color(0xFF463B76), onPrimaryContainer = Color(0xFFEAE3FF),
    secondary = Color(0xFFACD2B8), onSecondary = Color(0xFF1A3926),
    secondaryContainer = Color(0xFF304D3D), onSecondaryContainer = Color(0xFFD9ECDD),
    tertiary = Marigold, onTertiary = Color(0xFF432F00),
    tertiaryContainer = Color(0xFF594526), onTertiaryContainer = Color(0xFFFFE4A1),
    background = Color(0xFF19181D), onBackground = Color(0xFFF2EEF3),
    surface = Color(0xFF1F1D25), onSurface = Color(0xFFF2EEF3),
    surfaceVariant = Color(0xFF45414C), onSurfaceVariant = Color(0xFFC8C2CF),
    surfaceContainerLowest = Color(0xFF151419), surfaceContainerLow = Color(0xFF25232B),
    surfaceContainer = Color(0xFF2B2932), surfaceContainerHigh = Color(0xFF34313B),
    surfaceContainerHighest = Color(0xFF3E3A46),
    outline = Color(0xFF9993A2), outlineVariant = Color(0xFF494551),
    error = Color(0xFFFFB4A9), onError = Color(0xFF581D18),
    errorContainer = Color(0xFF6C302A), onErrorContainer = Color(0xFFFFDAD4)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CrisbudTheme(content: @Composable () -> Unit) {
    val family = budgetFontFamily()
    val typography = remember(family) { appTypography(family) }
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) night else daylight,
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(30.dp)
        ),
        content = content
    )
}

private fun appTypography(family: FontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
        letterSpacing = tracking.sp, fontFeatureSettings = "tnum"
    )
    return Typography(
        displayLarge = style(54, 59, FontWeight.Bold, -2.6),
        displayMedium = style(43, 49, FontWeight.Bold, -1.9),
        displaySmall = style(38, 44, FontWeight.ExtraBold, -1.7),
        headlineLarge = style(34, 40, FontWeight.Bold, -1.3),
        headlineMedium = style(28, 34, FontWeight.Bold, -1.0),
        headlineSmall = style(24, 30, FontWeight.Bold, -0.7),
        titleLarge = style(23, 29, FontWeight.Bold, -0.8),
        titleMedium = style(16, 22, FontWeight.Bold, -0.25),
        titleSmall = style(14, 20, FontWeight.SemiBold, -0.1),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 17, FontWeight.Normal),
        labelLarge = style(14, 19, FontWeight.SemiBold),
        labelMedium = style(12, 17, FontWeight.SemiBold, 0.1),
        labelSmall = style(11, 15, FontWeight.Bold, 0.4)
    )
}
