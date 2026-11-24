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

// Matte charcoal / Spotify-inspired flagship palette (calm, neutral, zero neon clutter)
val ObsidianBg = Color(0xFF121212)
val ObsidianSurface = Color(0xFF181818)
val ObsidianCard = Color(0xFF242424)
val ObsidianCardElevated = Color(0xFF2C2C2C)
val ObsidianStroke = Color(0xFF2E2E2E)
val ObsidianStrokeSoft = Color(0xFF242424)

val EmeraldAccent = Color(0xFF1DB954)
val EmeraldDeep = Color(0xFF145C2E)
val CyanGlow = Color(0xFF4EA8DE)
val VioletPulse = Color(0xFF7B68EE)
val CoralHeart = Color(0xFFE94F64)
val AmberGold = Color(0xFFE0A458)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA7A7A7)
val TextMuted = Color(0xFF727272)

val LocalSonoraAccent = compositionLocalOf { EmeraldAccent }

val PlaylistCoverPalettes: List<Pair<Long, Long>> = listOf(
    0xFF1DB954L to 0xFF163322L,
    0xFF2D5A7BL to 0xFF162432L,
    0xFF7A3E48L to 0xFF2D191EL,
    0xFF4A4278L to 0xFF1E1A30L,
    0xFF6B5335L to 0xFF282016L,
    0xFF3E4A59L to 0xFF1A1E24L
)

private val SonoraColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color(0xFF000000),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = TextPrimary,
    secondary = Color(0xFF535353),
    onSecondary = TextPrimary,
    tertiary = TextSecondary,
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
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.2).sp
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
        letterSpacing = 0.2.sp
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
