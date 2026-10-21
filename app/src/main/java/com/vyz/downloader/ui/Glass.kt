package com.vyz.downloader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

val GlassShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(50)

/**
 * Latar putih dengan bayangan abu yang bergerak sangat pelan.
 * Inilah yang di-blur oleh kaca di atasnya, jadi efek kacanya tetap terlihat di tema putih.
 */
@Composable
fun AuroraBackground(haze: HazeState, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a",
    )
    val b by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "b",
    )
    Box(
        modifier
            .fillMaxSize()
            .haze(haze)
            .background(Brush.verticalGradient(listOf(g.bgTop, g.bgBottom)))
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = 0.85f), Color.Transparent),
                            center = Offset(cx, cy), radius = r,
                        ),
                        radius = r, center = Offset(cx, cy),
                    )
                }
                blob(g.blobs[0], w * (0.15f + 0.5f * a), h * (0.12f + 0.08f * b), w * 0.7f)
                blob(g.blobs[1], w * (0.9f - 0.45f * b), h * (0.5f + 0.1f * a), w * 0.65f)
                blob(g.blobs[2], w * (0.25f + 0.3f * b), h * (0.92f - 0.1f * a), w * 0.75f)
            }
    )
}

/**
 * Permukaan kaca: bayangan lembut, blur latar, tint putih, kilau di atas,
 * bayangan tipis di bawah (kesan tebal), dan tepi bening yang terang di sudut kiri atas.
 */
@Composable
fun Modifier.glass(haze: HazeState, shape: Shape = GlassShape, elevation: Int = 14): Modifier {
    val g = LocalGlass.current
    return this
        .shadow(elevation.dp, shape, ambientColor = g.shadowAmbient, spotColor = g.shadowSpot)
        .clip(shape)
        .hazeChild(state = haze, style = g.hazeStyle)
        .drawWithContent {
            drawContent()
            // kilau lembut dari atas
            drawRect(Brush.verticalGradient(listOf(g.sheen, Color.Transparent), endY = size.height * 0.5f))
            // ketebalan di tepi bawah
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, g.shade),
                    startY = size.height * 0.55f, endY = size.height,
                )
            )
        }
        .border(BorderStroke(1.2.dp, Brush.linearGradient(listOf(g.borderStart, g.borderEnd))), shape)
}

@Composable
fun GlassCard(
    haze: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    content: @Composable () -> Unit,
) {
    Box(modifier.glass(haze, shape)) { content() }
}

@Composable
fun GlassButton(
    text: String,
    haze: HazeState,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
    val fg = when {
        primary && enabled -> g.onAccent
        primary -> g.onAccent.copy(alpha = 0.7f)
        enabled -> g.text
        else -> g.subtext
    }
    val bg = if (primary) Modifier.background(g.accent.copy(alpha = if (enabled) 0.9f else 0.35f)) else Modifier
    Box(
        modifier
            .glass(haze, PillShape, elevation = if (primary) 10 else 6)
            .then(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.padding(end = 8.dp))
            }
            Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}
