package android.com.crisbud

import android.com.crisbud.resources.Res
import android.com.crisbud.resources.dm_sans_bold
import android.com.crisbud.resources.dm_sans_extrabold
import android.com.crisbud.resources.dm_sans_medium
import android.com.crisbud.resources.dm_sans_regular
import android.com.crisbud.resources.dm_sans_semibold
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font

@Composable
internal fun budgetFontFamily(): FontFamily = FontFamily(
    Font(Res.font.dm_sans_regular, FontWeight.Normal),
    Font(Res.font.dm_sans_medium, FontWeight.Medium),
    Font(Res.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.dm_sans_bold, FontWeight.Bold),
    Font(Res.font.dm_sans_extrabold, FontWeight.ExtraBold)
)
