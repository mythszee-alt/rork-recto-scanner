package com.rork.recto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Graphite = Color(0xFF101315)
val Ink = Color(0xFF171B1E)
val Panel = Color(0xFF202529)
val Paper = Color(0xFFF4F7F7)
val CalibrationCyan = Color(0xFF00D7E8)
val RegistrationMagenta = Color(0xFFFF2E88)
val SignalGreen = Color(0xFF5CE6A8)
val Muted = Color(0xFF9AA5AA)
val Hairline = Color(0xFF30373B)

private val RectoDarkColors = darkColorScheme(
    primary = CalibrationCyan,
    onPrimary = Graphite,
    secondary = RegistrationMagenta,
    tertiary = SignalGreen,
    background = Graphite,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = Hairline
)

private val RectoLightColors = lightColorScheme(
    primary = Color(0xFF007D89),
    onPrimary = Color.White,
    secondary = Color(0xFFB00056),
    background = Color(0xFFF1F4F4),
    onBackground = Graphite,
    surface = Color.White,
    onSurface = Graphite,
    surfaceVariant = Color(0xFFE5EBEC),
    onSurfaceVariant = Color(0xFF556166),
    outline = Color(0xFFC8D0D2)
)

private val RectoTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) RectoDarkColors else RectoLightColors,
        typography = RectoTypography,
        content = content
    )
}
