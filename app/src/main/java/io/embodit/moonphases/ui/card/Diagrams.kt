package io.embodit.moonphases.ui.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.embodit.moonphases.domain.Lesson
import io.embodit.moonphases.domain.toRad
import io.embodit.moonphases.domain.wrap360
import io.embodit.moonphases.ui.theme.MoonColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val PhaseDark = Color(0xFF1A1C2A)
private val PhaseLit = MoonColors.MoonLit
private val Ink = Color(0xFFEDEFF5)
private val InkDim = Color(0x80EDEFF5)

/**
 * The Moon as seen from the Earth at phase angle [theta]: the lit half on the sunward side and an
 * ellipse of half-width r·|cos θ| that either eats into it (crescent) or adds to it (gibbous).
 * Northern-hemisphere view: waxing phases are lit on the right.
 */
fun DrawScope.drawPhase(center: Offset, r: Float, theta: Float, dark: Color = PhaseDark, lit: Color = PhaseLit) {
    val t = wrap360(theta)
    val waxing = t < 180f
    drawCircle(dark, r, center)
    val half = if (waxing) Rect(center.x, center.y - r, center.x + r, center.y + r) else Rect(center.x - r, center.y - r, center.x, center.y + r)
    clipRect(half.left, half.top, half.right, half.bottom) { drawCircle(lit, r, center) }
    val k = cos(t.toRad())
    val hw = r * abs(k)
    val ellipse = if (k < 0f) lit else dark
    if (hw > 0.5f) drawOval(ellipse, topLeft = Offset(center.x - hw, center.y - r), size = Size(2f * hw, 2f * r))
    drawCircle(InkDim, r, center, style = Stroke(width = 1.5f))
}

/** A phase icon of [size]. */
@Composable
fun PhaseIcon(theta: Float, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        drawPhase(Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension / 2f - 2f, theta)
    }
}

/**
 * The textbook's top view: the Sun at the top, you in the middle, the orbit, and the Moon where you
 * have turned to ([theta], counter-clockwise from above). With [ringIcons] the eight phases are drawn
 * around the orbit (lap summary, finish card).
 */
@Composable
fun TopView(theta: Float, modifier: Modifier = Modifier, size: Dp = 180.dp, ringIcons: Boolean = false, learnedMask: Int = 0xFF) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f + h * 0.04f)
        val orbit = w * 0.30f
        // sun at the top, with a soft halo
        val sun = Offset(w / 2f, h * 0.11f)
        drawCircle(MoonColors.SunGlow.copy(alpha = 0.25f), w * 0.09f, sun)
        drawCircle(MoonColors.SunCore, w * 0.055f, sun)
        // light rays
        for (i in -2..2) {
            val x = w / 2f + i * w * 0.12f
            drawLine(MoonColors.SunGlow.copy(alpha = 0.35f), Offset(x, sun.y + w * 0.08f), Offset(x, c.y - orbit - w * 0.05f), strokeWidth = 1.5f)
        }
        // orbit
        drawCircle(InkDim, orbit, c, style = Stroke(width = 1.5f))
        // you (the Earth)
        drawCircle(MoonColors.Interaction, w * 0.045f, c)
        drawCircle(Ink, w * 0.045f, c, style = Stroke(width = 1.5f))
        if (ringIcons) {
            for (k in 0 until 8) {
                val a = (k * 45f).toRad()
                val p = Offset(c.x - orbit * sin(a), c.y - orbit * cos(a))
                val learned = (learnedMask shr k) and 1 == 1
                drawPhase(p, w * 0.045f, k * 45f, lit = if (learned) PhaseLit else PhaseLit.copy(alpha = 0.35f))
            }
        } else {
            val a = theta.toRad()
            val p = Offset(c.x - orbit * sin(a), c.y - orbit * cos(a))
            drawPhase(p, w * 0.06f, theta)
        }
    }
}

/** Eight small phase icons in a row (lap summary, finish card). */
@Composable
fun PhaseRow(learnedMask: Int, iconSize: Dp = 26.dp, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in 0 until Lesson.STEPS) {
            val learned = (learnedMask shr k) and 1 == 1
            Canvas(modifier = Modifier.size(iconSize)) {
                drawPhase(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f - 1.5f, k * 45f, lit = if (learned) PhaseLit else PhaseLit.copy(alpha = 0.3f))
            }
        }
    }
}
