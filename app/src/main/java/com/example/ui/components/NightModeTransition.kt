package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/*
 * Cinematic hand-off between the normal dock clock and the night-time-only full-screen clock.
 *
 * One master progress value runs linearly from 0 (day) to 1 (night). Each layer maps its own slice
 * of it through an ease curve, so going back to day is the exact mirror image:
 *
 *   0.00 – 0.55  day layout softly blurs, dims, shrinks and sinks
 *   0.15 – 0.85  dusk veil rises from the bottom with a setting moon (or a rising sun in the morning)
 *   0.45 – 1.00  the giant night clock focuses in from a slight blur and settles from 106% to 100%
 *
 * Layers are only composed while visible, so there's no extra cost once the transition ends.
 */

private const val TO_NIGHT_MS = 2600
private const val TO_DAY_MS = 2000

/** 0..1 slice of [p] between [start] and [end], eased. */
internal fun nightSegment(p: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((p - start) / (end - start)).coerceIn(0f, 1f))

/** Animated night progress. Honors the system "Remove animations" setting (instant switch). */
@Composable
fun rememberNightProgress(isNight: Boolean): Float {
    val context = LocalContext.current
    val animationsOff = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (isNight) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (animationsOff) 0 else if (isNight) TO_NIGHT_MS else TO_DAY_MS,
            easing = LinearEasing
        ),
        label = "night_mode_progress"
    )
    return progress
}

/** Day layout leaving (or coming back). */
fun Modifier.nightDayLayer(p: Float): Modifier {
    val out = nightSegment(p, 0f, 0.55f)
    val base = this.graphicsLayer {
        alpha = 1f - out
        val s = 1f - 0.06f * out
        scaleX = s
        scaleY = s
        translationY = out * 18.dp.toPx()
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && out > 0.01f) base.blur((out * 16f).dp) else base
}

/** Night clock arriving (or leaving). */
fun Modifier.nightClockLayer(p: Float): Modifier {
    val inn = nightSegment(p, 0.45f, 1f)
    val base = this.graphicsLayer {
        alpha = inn
        val s = 1.06f - 0.06f * inn
        scaleX = s
        scaleY = s
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && inn < 0.99f) base.blur(((1f - inn) * 12f).dp) else base
}

/**
 * Dusk (towards night) or dawn (towards day) veil drawn above both layers while the
 * transition runs: a darkening sky rising from the bottom, a soft horizon glow, and a crescent
 * moon setting into place, or a warm sun rising when the morning arrives.
 */
@Composable
fun NightTransitionVeil(p: Float, towardNight: Boolean, modifier: Modifier = Modifier) {
    if (p <= 0.001f || p >= 0.999f) return
    val window = nightSegment(p, 0.15f, 0.85f)
    val v = sin(window * PI.toFloat()).coerceAtLeast(0f)
    if (v <= 0.001f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tint = if (towardNight) Color(0xFF1C1650) else Color(0xFFFF8A3D)

        // Overall dimming
        drawRect(Color.Black.copy(alpha = 0.4f * v))

        // Sky rising from the bottom
        val top = h * (1f - 0.95f * v)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, tint.copy(alpha = 0.5f * v), Color.Black.copy(alpha = 0.75f * v)),
                startY = top, endY = h
            ),
            topLeft = Offset(0f, top),
            size = Size(w, h - top)
        )

        // Horizon glow line
        val glow = 46.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, tint.copy(alpha = 0.38f * v), Color.Transparent),
                startY = top - glow, endY = top + glow
            ),
            topLeft = Offset(0f, top - glow),
            size = Size(w, glow * 2f)
        )

        if (towardNight) {
            // Crescent moon drifting up into the sky
            val r = 24.dp.toPx()
            val c = Offset(w * 0.78f, h * (0.62f - 0.3f * window))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFE8E2FF).copy(alpha = 0.22f * v), Color.Transparent),
                    center = c, radius = r * 3.2f
                ),
                radius = r * 3.2f, center = c
            )
            val moon = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
            val bite = Path().apply { addOval(Rect(c.x - r * 0.45f, c.y - r * 1.15f, c.x + r * 1.55f, c.y + r * 0.85f)) }
            val crescent = Path().apply { op(moon, bite, PathOperation.Difference) }
            drawPath(crescent, Color(0xFFF3EEDB).copy(alpha = 0.9f * v))
            // A few stars appearing
            val stars = listOf(0.12f to 0.18f, 0.27f to 0.33f, 0.44f to 0.14f, 0.6f to 0.26f, 0.9f to 0.12f, 0.35f to 0.5f)
            stars.forEachIndexed { i, (sx, sy) ->
                val twinkle = (window * 3f - i * 0.25f).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = 0.75f * v * twinkle), radius = (1.2f + (i % 3) * 0.6f).dp.toPx(), center = Offset(w * sx, h * sy))
            }
        } else {
            // Warm sun rising from the horizon
            val rise = 1f - p // 0 at night, 1 at day
            val r = 34.dp.toPx()
            val c = Offset(w * 0.5f, top + r * 0.6f - rise * 40.dp.toPx())
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFD27A).copy(alpha = 0.55f * v), Color(0xFFFF8A3D).copy(alpha = 0.18f * v), Color.Transparent),
                    center = c, radius = r * 4f
                ),
                radius = r * 4f, center = c
            )
            drawCircle(Color(0xFFFFE3A3).copy(alpha = 0.95f * v), radius = r, center = c)
        }
    }
}

/**
 * Short, very dim caption after the switch ("Good night, Mochi is asleep" / "Good morning"),
 * visible for a few seconds only so it never competes with the clock.
 */
@Composable
fun NightGreetingCaption(isNight: Boolean, catName: String?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(isNight) {
        if (first) {
            first = false // don't greet on app start, only on an actual switch
            return@LaunchedEffect
        }
        delay(if (isNight) TO_NIGHT_MS.toLong() else TO_DAY_MS.toLong())
        visible = true
        delay(5_000L)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(900), label = "night_caption_alpha")
    if (alpha <= 0.01f) return
    val text = when {
        isNight && !catName.isNullOrBlank() -> "Good night · $catName is asleep"
        isNight -> "Good night"
        else -> "Good morning"
    }
    Text(
        text = text,
        color = if (isNight) Color(0xFF8A2A24) else Color(0xFFF6B86E),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        modifier = modifier
            .padding(bottom = 18.dp)
            .graphicsLayer { this.alpha = alpha * (if (isNight) 0.8f else 1f) }
    )
}
