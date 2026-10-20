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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.haze

val GlassShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(50)

/** Latar bergradasi dengan bola warna yang bergerak pelan. Ini yang di-blur oleh kaca di atasnya. */
@Composable
fun AuroraBackground(haze: HazeState, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a",
    )
    val b by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "b",
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
                            listOf(color.copy(alpha = if (g.dark) 0.75f else 0.8f), Color.Transparent),
                            center = Offset(cx, cy), radius = r,
                        ),
                        radius = r, center = Offset(cx, cy),
                    )
                }
                blob(g.blobs[0], w * (0.2f + 0.5f * a), h * (0.15f + 0.1f * b), w * 0.8f)
                blob(g.blobs[1], w * (0.85f - 0.5f * b), h * (0.5f + 0.15f * a), w * 0.75f)
                blob(g.blobs[2], w * (0.3f + 0.3f * b), h * (0.9f - 0.12f * a), w * 0.85f)
            }
    )
}

/** Permukaan kaca: blur latar + tint + kilau atas + garis tepi bergradasi. */
@Composable
fun Modifier.glass(haze: HazeState, shape: Shape = GlassShape): Modifier {
    val g = LocalGlass.current
    return this
        .clip(shape)
        .hazeChild(state = haze, style = g.hazeStyle)
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(listOf(g.sheen, Color.Transparent), endY = size.height * 0.55f)
            )
        }
        .border(BorderStroke(1.dp, Brush.linearGradient(listOf(g.borderStart, g.borderEnd))), shape)
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
    val bg = if (primary) Modifier.background(g.accent.copy(alpha = 0.55f)) else Modifier
    Box(
        modifier
            .glass(haze, PillShape)
            .then(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) g.text else g.subtext, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text,
                color = if (enabled) g.text else g.subtext,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}
