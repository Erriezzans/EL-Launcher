package com.minimo.launcher.ui.xmb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Simple vector-free icons for XMB categories and actions, drawn with a Canvas. */
enum class XmbGlyph { Star, Apps, Folder, Settings, Bell, Lock }

@Composable
fun XmbGlyphIcon(
    glyph: XmbGlyph,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(size)) {
        when (glyph) {
            XmbGlyph.Star -> drawStar(color)
            XmbGlyph.Apps -> drawAppsGrid(color)
            XmbGlyph.Folder -> drawFolder(color)
            XmbGlyph.Settings -> drawGear(color)
            XmbGlyph.Bell -> drawBell(color)
            XmbGlyph.Lock -> drawLock(color)
        }
    }
}

private fun DrawScope.drawStar(color: Color) {
    val w = size.width
    val cx = w / 2f
    val cy = w * 0.53f
    val outer = w * 0.48f
    val inner = w * 0.20f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val angle = -PI / 2.0 + i * PI / 5.0
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawAppsGrid(color: Color) {
    val w = size.width
    val cell = w * 0.24f
    val gap = w * 0.09f
    val start = (w - 3 * cell - 2 * gap) / 2f
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            drawRoundRect(
                color = color,
                topLeft = Offset(start + col * (cell + gap), start + row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.25f)
            )
        }
    }
}

private fun DrawScope.drawFolder(color: Color) {
    val w = size.width
    // tab
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.10f, w * 0.20f),
        size = Size(w * 0.38f, w * 0.20f),
        cornerRadius = CornerRadius(w * 0.06f)
    )
    // body
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.10f, w * 0.32f),
        size = Size(w * 0.80f, w * 0.50f),
        cornerRadius = CornerRadius(w * 0.07f)
    )
}

private fun DrawScope.drawGear(color: Color) {
    val w = size.width
    val c = Offset(w / 2f, w / 2f)
    drawCircle(color = color, radius = w * 0.22f, center = c, style = Stroke(width = w * 0.10f))
    for (i in 0 until 8) {
        val a = i * PI / 4.0
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            color = color,
            start = Offset(c.x + dx * w * 0.32f, c.y + dy * w * 0.32f),
            end = Offset(c.x + dx * w * 0.45f, c.y + dy * w * 0.45f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBell(color: Color) {
    val w = size.width
    // dome (half disc, centre at y = 0.43w)
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(w * 0.22f, w * 0.15f),
        size = Size(w * 0.56f, w * 0.56f)
    )
    // body
    drawRect(color = color, topLeft = Offset(w * 0.22f, w * 0.43f), size = Size(w * 0.56f, w * 0.27f))
    // lip
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, w * 0.68f),
        size = Size(w * 0.76f, w * 0.09f),
        cornerRadius = CornerRadius(w * 0.045f)
    )
    // clapper
    drawCircle(color = color, radius = w * 0.07f, center = Offset(w * 0.5f, w * 0.88f))
}

private fun DrawScope.drawLock(color: Color) {
    val w = size.width
    // shackle
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.32f, w * 0.14f),
        size = Size(w * 0.36f, w * 0.40f),
        style = Stroke(width = w * 0.09f)
    )
    // body
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.22f, w * 0.40f),
        size = Size(w * 0.56f, w * 0.44f),
        cornerRadius = CornerRadius(w * 0.08f)
    )
}
