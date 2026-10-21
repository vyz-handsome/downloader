package com.vyz.downloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Tema putih bersih satu warna (hitam, abu, putih) dengan permukaan kaca. */
class GlassTheme(
    val bgTop: Color,
    val bgBottom: Color,
    val blobs: List<Color>,
    val glassTint: Color,
    val borderStart: Color,
    val borderEnd: Color,
    val sheen: Color,
    val shade: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val onAccent: Color,
    val fill: Color,
    val fillSelected: Color,
    val danger: Color,
    val success: Color,
) {
    val hazeStyle: HazeStyle = HazeStyle(
        backgroundColor = bgTop,
        tint = HazeTint(glassTint),
        blurRadius = 32.dp,
        noiseFactor = 0.02f,
    )
}

val CleanGlass = GlassTheme(
    bgTop = Color(0xFFF3F4F7),
    bgBottom = Color(0xFFFFFFFF),
    blobs = listOf(Color(0xFFCDD3E0), Color(0xFFDADDE5), Color(0xFFC6CAD3)),
    glassTint = Color(0x99FFFFFF),
    borderStart = Color(0xFFFFFFFF),
    borderEnd = Color(0x40A9B0BE),
    sheen = Color(0xB3FFFFFF),
    shade = Color(0x0D111318),
    shadowAmbient = Color(0x14111318),
    shadowSpot = Color(0x24111318),
    text = Color(0xFF111318),
    subtext = Color(0x99111318),
    accent = Color(0xFF111318),
    onAccent = Color(0xFFFFFFFF),
    fill = Color(0x0D111318),
    fillSelected = Color(0x1F111318),
    danger = Color(0xFFC0392B),
    success = Color(0xFF2E7D5B),
)

val LocalGlass = staticCompositionLocalOf { CleanGlass }

@Composable
fun DownloaderTheme(content: @Composable () -> Unit) {
    val g = CleanGlass
    val scheme = lightColorScheme(
        primary = g.accent, onPrimary = g.onAccent,
        onBackground = g.text, onSurface = g.text,
        background = g.bgTop, surface = g.bgTop,
    )
    CompositionLocalProvider(LocalGlass provides g) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
