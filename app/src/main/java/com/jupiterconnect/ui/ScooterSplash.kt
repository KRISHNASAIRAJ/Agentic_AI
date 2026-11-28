package com.jupiterconnect.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin

private val White = Color(0xFFFFFFFF)
private val Red = Color(0xFFE60028)
private val Reflection = Color(0xFF8A8A8A)

/**
 * Line-art scooter drawn as vector paths so it scales with the screen. The
 * caller supplies the elapsed time in ms; all motion is derived from it, so
 * the draw phase alone is invalidated (no tree-wide recomposition).
 */
@Composable
fun ScooterSplash(
    timeMs: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val t = timeMs().coerceIn(0f, 5000f)
        val scale = (size.width / 260f).coerceAtMost(size.height / 200f)
        val cx = size.width / 2f
        val roadY = size.height * 0.52f

        // 0-400 ms: the road draws outward from the centre.
        val road = (t / 400f).coerceIn(0f, 1f)
        val halfRoad = 100f * scale * road
        drawLine(White, Offset(cx - halfRoad, roadY), Offset(cx + halfRoad, roadY),
            strokeWidth = 2f * scale)

        // 400-2600 ms: decelerating slide in from off-screen left.
        val slideRaw = ((t - 400f) / 2200f).coerceIn(0f, 1f)
        val slide = FastOutSlowInEasing.transform(slideRaw)
        val scooterX = (-size.width * 0.55f) +
            ((cx + size.width * 0.55f) * slide)
        val fade = if (t < 400f) 0f else 1f

        // Vertical bob fades out as the bike settles.
        val bob = sin(t / 1000f * 2f * Math.PI.toFloat() * 3f) *
            1.5f * scale * (1f - slide)
        // Small suspension squash at the stop (2600-3000 ms).
        val settle = ((t - 2600f) / 400f).coerceIn(0f, 1f)
        val squash = if (settle > 0f && settle < 1f)
            sin(settle * Math.PI.toFloat()) * kotlin.math.exp(-2f * settle) * 0.12f
        else 0f
        val scaleX = scale * (1f + squash * 0.5f)
        val scaleY = scale * (1f - squash)

        // Red dome rises behind the scooter like a headlight glow.
        if (settle > 0f) {
            drawCircle(
                color = Red.copy(alpha = 0.20f * settle * fade),
                radius = 76f * scale * (0.7f + 0.3f * settle),
                center = Offset(scooterX, roadY - 46f * scale + bob),
            )
        }

        translate(scooterX, roadY + bob) {
            drawScooter(scaleX, scaleY, slide * 1080f,
                speedAlpha = if (t < 400f) 0f else 1f - slide, alpha = fade)
        }

        // 3800-4400 ms: two road reflection lines pulse once.
        val reflect = ((t - 3800f) / 600f).coerceIn(0f, 1f)
        if (reflect > 0f) {
            val pulse = sin(reflect * Math.PI.toFloat())
            drawLine(Reflection.copy(alpha = 0.45f * pulse),
                Offset(cx - 26f * scale, roadY + 12f * scale),
                Offset(cx + 26f * scale, roadY + 12f * scale),
                strokeWidth = 2f * scale)
            drawLine(Reflection.copy(alpha = 0.30f * pulse),
                Offset(cx - 14f * scale, roadY + 20f * scale),
                Offset(cx + 14f * scale, roadY + 20f * scale),
                strokeWidth = 2f * scale)
        }
    }
}

/** Draw one scooter centred on the current origin; the road is the y=0 line. */
private fun DrawScope.drawScooter(
    sx: Float,
    sy: Float,
    wheelRotation: Float,
    speedAlpha: Float,
    alpha: Float,
) {
    // Three trailing speed lines, fading as the scooter slows.
    for (i in 0..2) {
        val y = (-18f + i * 15f) * sy
        val len = (26f - i * 5f) * sx
        drawLine(
            color = White.copy(alpha = 0.55f * speedAlpha * alpha),
            start = Offset(-78f * sx - len, y),
            end = Offset(-78f * sx, y),
            strokeWidth = 1.6f * sx,
        )
    }

    // Body dome: red fill with a white outline.
    val body = Path().apply {
        moveTo(-52f * sx, 22f * sy)
        cubicTo(-52f * sx, -6f * sy, -32f * sx, -14f * sy, -8f * sx, -14f * sy)
        lineTo(26f * sx, -14f * sy)
        cubicTo(42f * sx, -14f * sy, 52f * sx, -4f * sy, 54f * sx, 16f * sy)
        lineTo(54f * sx, 22f * sy)
        close()
    }
    drawPath(body, Red.copy(alpha = alpha))
    drawPath(body, White.copy(alpha = alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f * sx))

    // Floorboard and rear grab rail.
    drawLine(White.copy(alpha = alpha), Offset(-60f * sx, 26f * sy),
        Offset(62f * sx, 26f * sy), strokeWidth = 2.2f * sx)
    drawLine(White.copy(alpha = alpha), Offset(-40f * sx, -20f * sy),
        Offset(-18f * sx, -20f * sy), strokeWidth = 2.2f * sx)

    // Handlebar post and grip.
    drawLine(White.copy(alpha = alpha), Offset(34f * sx, -12f * sy),
        Offset(44f * sx, -38f * sy), strokeWidth = 2.2f * sx)
    drawLine(White.copy(alpha = alpha), Offset(36f * sx, -38f * sy),
        Offset(58f * sx, -38f * sy), strokeWidth = 2.2f * sx)

    // Wheels: black tyre, white rim, white hub and a rotating spoke.
    for (wx in floatArrayOf(-42f * sx, 42f * sx)) {
        val r = 15f * sx
        val centre = Offset(wx, 8f * sy)
        drawCircle(Color.Black.copy(alpha = alpha), radius = r, center = centre)
        drawCircle(White.copy(alpha = alpha), radius = r, center = centre,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f * sx))
        rotate(wheelRotation, centre) {
            drawLine(
                White.copy(alpha = 0.8f * alpha),
                Offset(centre.x, centre.y - r * 0.8f),
                Offset(centre.x, centre.y + r * 0.8f),
                strokeWidth = 1.4f * sx,
            )
        }
        drawCircle(White.copy(alpha = alpha), radius = 2.6f * sx, center = centre)
    }
}
