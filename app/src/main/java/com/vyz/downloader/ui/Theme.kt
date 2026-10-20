package com.vyz.downloader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Warna dan gaya "liquid glass" untuk mode terang/gelap. */
class GlassTheme(
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val blobs: List<Color>,
    val glassTint: Color,
    val borderStart: Color,
    val borderEnd: Color,
    val sheen: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val danger: Color,
    val success: Color,
) {
    val hazeStyle: HazeStyle = HazeStyle(
        backgroundColor = bgTop,
        tint = HazeTint(glassTint),
        blurRadius = 28.dp,
        noiseFactor = 0.03f,
    )
}

val DarkGlass = GlassTheme(
    dark = true,
    bgTop = Color(0xFF0B1020),
    bgBottom = Color(0xFF1A1440),
    blobs = listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE), Color(0xFFFF5CA8)),
    glassTint = Color(0x26FFFFFF),
    borderStart = Color(0x99FFFFFF),
    borderEnd = Color(0x1AFFFFFF),
    sheen = Color(0x2EFFFFFF),
    text = Color(0xFFF4F6FF),
    subtext = Color(0xB3F4F6FF),
    accent = Color(0xFF8E7CFF),
    danger = Color(0xFFFF7A90),
    success = Color(0xFF5EEAD4),
)

val LightGlass = GlassTheme(
    dark = false,
    bgTop = Color(0xFFEAF0FF),
    bgBottom = Color(0xFFF6EEFF),
    blobs = listOf(Color(0xFFA99BFF), Color(0xFF7DE3F4), Color(0xFFFFA3CB)),
    glassTint = Color(0x73FFFFFF),
    borderStart = Color(0xE6FFFFFF),
    borderEnd = Color(0x40FFFFFF),
    sheen = Color(0x66FFFFFF),
    text = Color(0xFF14172B),
    subtext = Color(0xB314172B),
    accent = Color(0xFF5B3FE0),
    danger = Color(0xFFD1304F),
    success = Color(0xFF0E8F7E),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

@Composable
fun DownloaderTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val g = if (dark) DarkGlass else LightGlass
    val scheme = if (dark) {
        darkColorScheme(primary = g.accent, onBackground = g.text, onSurface = g.text, background = g.bgTop, surface = g.bgTop)
    } else {
        lightColorScheme(primary = g.accent, onBackground = g.text, onSurface = g.text, background = g.bgTop, surface = g.bgTop)
    }
    CompositionLocalProvider(LocalGlass provides g) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
