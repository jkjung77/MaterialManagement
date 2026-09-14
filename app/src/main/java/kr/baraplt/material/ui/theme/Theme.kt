package kr.baraplt.material.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF1C1C1C)
val InkSoft = Color(0xFF3D3D3D)
val InkMute = Color(0xFF6B6B6B)
val Line = Color(0xFFC8C8C8)
val Paper = Color(0xFFF2F2F2)
val Card = Color(0xFFFFFFFF)
val CardAlt = Color(0xFFE8E8E8)
val Chip = Color(0xFFD6D6D6)

private val GrayscaleScheme: ColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = InkSoft,
    onPrimaryContainer = Color.White,
    secondary = InkSoft,
    onSecondary = Color.White,
    secondaryContainer = CardAlt,
    onSecondaryContainer = Ink,
    tertiary = InkMute,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = CardAlt,
    onSurfaceVariant = InkSoft,
    outline = Line,
    outlineVariant = Chip,
    error = Ink,
    onError = Color.White,
    errorContainer = CardAlt,
    onErrorContainer = Ink,
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    inversePrimary = Chip
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Ink),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ink),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Ink),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = InkSoft),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, color = Ink, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, color = InkSoft, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = InkMute)
)

@Composable
fun MaterialMgmtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GrayscaleScheme,
        typography = AppTypography,
        content = content
    )
}
