package com.sonora.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ObsidianBg = Color(0xFF080A0F)
val ObsidianSurface = Color(0xFF10141D)
val ObsidianCard = Color(0xFF171D2A)
val ObsidianCardElevated = Color(0xFF1E2636)
val ObsidianStroke = Color(0x26FFFFFF)
val ObsidianStrokeSoft = Color(0x14FFFFFF)

val EmeraldAccent = Color(0xFF1ED760)
val EmeraldDeep = Color(0xFF0F7A38)
val CyanGlow = Color(0xFF00E5FF)
val VioletPulse = Color(0xFF8C6EFF)
val CoralHeart = Color(0xFFFF4D6D)
val AmberGold = Color(0xFFFFB74D)

val TextPrimary = Color(0xFFF6F8FC)
val TextSecondary = Color(0xFFA7B1C2)
val TextMuted = Color(0xFF6B7689)

val LocalSonoraAccent = compositionLocalOf { EmeraldAccent }

val PlaylistCoverPalettes: List<Pair<Long, Long>> = listOf(
    0xFF1ED760L to 0xFF0B2F24L,
    0xFF00E5FFL to 0xFF0F2744L,
    0xFFFF4D6DL to 0xFF3E1025L,
    0xFF8C6EFFL to 0xFF1B1540L,
    0xFFFFB74DL to 0xFF3F230CL,
    0xFF38BDF8L to 0xFF1E1B4BL
)

private val SonoraColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color(0xFF04140A),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = TextPrimary,
    secondary = CyanGlow,
    onSecondary = Color(0xFF03171B),
    tertiary = VioletPulse,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianStroke,
    error = CoralHeart
)

private val SonoraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun SonoraTheme(
    dynamicAccent: Color = EmeraldAccent,
    content: @Composable () -> Unit
) {
    val scheme = SonoraColorScheme.copy(
        primary = dynamicAccent
    )
    CompositionLocalProvider(LocalSonoraAccent provides dynamicAccent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SonoraTypography,
            content = content
        )
    }
}
