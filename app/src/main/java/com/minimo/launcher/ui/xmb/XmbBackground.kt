package com.minimo.launcher.ui.xmb

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/**
 * The flowing "wave" backdrop of the PS3 XMB.
 * The colour follows the time of day (like the original follows the month).
 * When [enableWallpaper] is true the system wallpaper is kept and only the waves are drawn on top.
 */
@Composable
fun XmbBackground(
    enableWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val palette = remember(hour) { paletteForHour(hour) }

    val transition = rememberInfiniteTransition(label = "xmbWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "xmbWavePhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (enableWallpaper) {
            drawRect(Color.Black.copy(alpha = 0.35f))
        } else {
            drawRect(Brush.verticalGradient(listOf(palette.first, palette.second)))
        }
        // phase is read here, inside the draw scope, so only drawing is invalidated each frame.
        drawWaves(phase)
    }
}

private fun paletteForHour(hour: Int): Pair<Color, Color> = when (hour) {
    in 5..7 -> Color(0xFF3A2A5C) to Color(0xFFD9825B)     // dawn
    in 8..11 -> Color(0xFF1B4F8F) to Color(0xFF58A6D6)    // morning
    in 12..16 -> Color(0xFF0F6E8C) to Color(0xFF3FB6C9)   // afternoon
    in 17..19 -> Color(0xFF4A1F5E) to Color(0xFFD8643B)   // evening
    in 20..23 -> Color(0xFF14183C) to Color(0xFF3B2A7A)   // night
    else -> Color(0xFF0B1026) to Color(0xFF1B2A5A)        // late night
}

private fun DrawScope.drawWaves(phase: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    // Three ribbons with different speed / height / thickness. Speeds are whole numbers so the
    // animation loops seamlessly when phase wraps from 2*PI back to 0.
    val ribbons = listOf(
        Ribbon(baseY = 0.62f, amplitude = 0.045f, thickness = 0.10f, waves = 1.4f, speed = 1, shift = 0f, alpha = 0.10f),
        Ribbon(baseY = 0.68f, amplitude = 0.060f, thickness = 0.07f, waves = 1.0f, speed = 2, shift = 1.7f, alpha = 0.08f),
        Ribbon(baseY = 0.74f, amplitude = 0.035f, thickness = 0.12f, waves = 1.9f, speed = 1, shift = 3.1f, alpha = 0.07f)
    )

    val step = 14f
    ribbons.forEach { r ->
        val path = Path()
        fun yAt(x: Float, extra: Float): Float {
            val t = x / w * r.waves * (2f * PI.toFloat()) + phase * r.speed + r.shift
            return h * (r.baseY + extra) + sin(t) * h * r.amplitude +
                    sin(t * 0.5f + 1f) * h * r.amplitude * 0.5f
        }

        var x = 0f
        path.moveTo(0f, yAt(0f, 0f))
        while (x < w) {
            x = (x + step).coerceAtMost(w)
            path.lineTo(x, yAt(x, 0f))
            if (x >= w) break
        }
        while (x > 0f) {
            x = (x - step).coerceAtLeast(0f)
            path.lineTo(x, yAt(x, r.thickness))
            if (x <= 0f) break
        }
        path.close()

        drawPath(path = path, color = Color.White.copy(alpha = r.alpha))
        drawPath(
            path = path,
            color = Color.White.copy(alpha = r.alpha * 1.6f),
            style = Stroke(width = 1.5f)
        )
    }
}

private data class Ribbon(
    val baseY: Float,
    val amplitude: Float,
    val thickness: Float,
    val waves: Float,
    val speed: Int,
    val shift: Float,
    val alpha: Float
)
