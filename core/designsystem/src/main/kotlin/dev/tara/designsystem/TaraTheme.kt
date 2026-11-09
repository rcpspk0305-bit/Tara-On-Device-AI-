package dev.tara.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF176A61),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9EEE3),
    onPrimaryContainer = Color(0xFF062E29),
    background = Color(0xFFF8FAF8),
    surface = Color(0xFFF8FAF8),
    surfaceContainer = Color(0xFFECEFEC),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF92D4C7),
    onPrimary = Color(0xFF07382F),
    primaryContainer = Color(0xFF245047),
    onPrimaryContainer = Color(0xFFB9EEE3),
    background = Color(0xFF101614),
    surface = Color(0xFF101614),
    surfaceContainer = Color(0xFF1D2522),
)
private val TaraTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 41.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 35.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
)

@Composable
fun TaraTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = TaraTypography, content = content)
}
