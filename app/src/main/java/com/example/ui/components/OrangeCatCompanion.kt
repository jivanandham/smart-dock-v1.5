package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Orange tabby cat companion.
 *
 * Everything is drawn with vector paths and gradients (no bitmaps), in a local coordinate system:
 * origin = point on the floor under the cat, +x = the direction the cat faces, -y = up.
 * The caller mirrors the drawing for left-facing walks.
 *
 * Each activity maps to a natural cat behaviour:
 *   ROAMING      walks with a diagonal gait, tail up; looks around when it stops
 *   WORKING      sits at a laptop and taps the keyboard with a paw
 *   BUSY_MEETING sits upright with a tiny headset, head tilts, ear twitches, short meows
 *   EATING       crouches over a food bowl, chewing
 *   GYM          play-hunts a ball of yarn: butt wiggle, then pounce
 *   NAPPING      "loaf" pose, sleepy slit eyes
 *   SLEEPING     curled up with the tail over the nose, slow breathing, Zzz
 *   ALARM        startled: arched back, puffed bristled tail, ears flat, meowing
 *   TIMER        sits watching an hourglass, occasionally bats at it
 *   PANDORA      sits in calm focus, slow blinks, purring, orbiting focus orbs
 */

/** Selectable coats. Each is a full palette so shading, stripes and eyes all match. */
enum class CatCoat(
    val label: String,
    val base: Color, val light: Color, val dark: Color, val stripe: Color,
    val cream: Color, val nose: Color, val innerEar: Color,
    val irisLight: Color, val iris: Color, val irisEdge: Color,
    val outline: Color, val farTint: Color
) {
    ORANGE_TABBY(
        "Orange tabby",
        Color(0xFFE08A3C), Color(0xFFF6B86E), Color(0xFFB65A1F), Color(0xFFA34B16).copy(alpha = 0.62f),
        Color(0xFFF9E8CC), Color(0xFFD9847E), Color(0xFFF1A9A0),
        Color(0xFFF2D46B), Color(0xFFD5A22A), Color(0xFF7E5E10),
        Color(0xFF5E2D0D), Color(0xFF9A4A18)
    ),
    GREY_TABBY(
        "Grey tabby",
        Color(0xFF8E939B), Color(0xFFB9BEC5), Color(0xFF5F646C), Color(0xFF3E4248).copy(alpha = 0.6f),
        Color(0xFFE9EAEC), Color(0xFFC98A8E), Color(0xFFE7B1B4),
        Color(0xFFD7E58A), Color(0xFF9DB84A), Color(0xFF4E6420),
        Color(0xFF2E3136), Color(0xFF555A61)
    ),
    BLACK(
        "Black",
        Color(0xFF2A2724), Color(0xFF4A4541), Color(0xFF151311), Color(0xFF101010).copy(alpha = 0.25f),
        Color(0xFF3A3532), Color(0xFF4A3A3A), Color(0xFF8A6A6A),
        Color(0xFFE8F07A), Color(0xFFC4C93A), Color(0xFF6A6E12),
        Color(0xFF080706), Color(0xFF1C1A18)
    ),
    CREAM(
        "Cream",
        Color(0xFFE9CFA8), Color(0xFFF7E8D0), Color(0xFFC9A675), Color(0xFFB98D57).copy(alpha = 0.4f),
        Color(0xFFFFF7EA), Color(0xFFE29A96), Color(0xFFF4B9B4),
        Color(0xFFBFE3F7), Color(0xFF6FB3DD), Color(0xFF2B5E85),
        Color(0xFF7A5A33), Color(0xFFB08A58)
    );

    companion object {
        fun fromKey(key: String): CatCoat = entries.firstOrNull { it.name == key } ?: ORANGE_TABBY
    }
}

/** Full colour set used by the renderer. Presets come from [CatCoat]; pet twins build their own. */
data class CatPalette(
    val base: Color, val light: Color, val dark: Color, val stripe: Color,
    val cream: Color, val nose: Color, val innerEar: Color,
    val irisLight: Color, val iris: Color, val irisEdge: Color,
    val outline: Color, val farTint: Color,
    /** Second coat colour for calico/tortie patches. */
    val patch: Color = dark
)

fun CatCoat.toPalette() = CatPalette(base, light, dark, stripe, cream, nose, innerEar, irisLight, iris, irisEdge, outline, farTint)

/** Pet-twin markings layered on top of any palette. */
data class CatMarkings(
    val stripes: Float = 1f,
    val whiteChest: Boolean = false,
    val whiteSocks: Boolean = false,
    val tuxedo: Boolean = false,
    val darkPoints: Boolean = false,
    val patches: Boolean = false
)

/** Builds a matching palette from a pet's main colour, optional second colour and eye colour. */
fun paletteFromPet(base: Color, secondary: Color?, eye: Color): CatPalette {
    fun shade(c: Color, light: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[2] = (hsv[2] * (1f + light)).coerceIn(0f, 1f)
        if (light > 0) hsv[1] = (hsv[1] * (1f - light * 0.5f)).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    val veryDark = hsv[2] < 0.25f
    return CatPalette(
        base = base,
        light = shade(base, if (veryDark) 0.7f else 0.3f),
        dark = shade(base, -0.35f),
        stripe = shade(base, -0.5f).copy(alpha = if (veryDark) 0.25f else 0.6f),
        cream = if (veryDark) shade(base, 0.35f) else Color(0xFFF9EEDC),
        nose = if (veryDark) Color(0xFF4A3A3A) else Color(0xFFD9847E),
        innerEar = if (veryDark) Color(0xFF8A6A6A) else Color(0xFFF1A9A0),
        irisLight = shade(eye, 0.35f),
        iris = eye,
        irisEdge = shade(eye, -0.55f),
        outline = shade(base, -0.7f),
        farTint = shade(base, -0.3f),
        patch = secondary ?: shade(base, -0.4f)
    )
}

/** Palette and markings for the frame being drawn (set at the start of [drawOrangeCatCompanion]; UI thread only). */
private var activePalette: CatPalette = CatCoat.ORANGE_TABBY.toPalette()
private var activeMarkings: CatMarkings = CatMarkings()
/** Extra tail angle from the physics spring (degrees). */
private var activeTailKick: Float = 0f
private val SnowWhite = Color(0xFFF8F6F2)

/** Hat for the frame being drawn: "PARTY", "SANTA", "WITCH" or null. */
private var activeHat: String? = null

private val CatBase: Color get() = activePalette.base
private val CatLight: Color get() = activePalette.light
private val CatDark: Color get() = activePalette.dark
private val CatStripe: Color
    get() = activePalette.stripe.copy(alpha = activePalette.stripe.alpha * activeMarkings.stripes.coerceIn(0f, 1f))
private val CatCream: Color
    get() = if (activeMarkings.whiteChest || activeMarkings.tuxedo) SnowWhite else activePalette.cream
private val CatNose: Color get() = activePalette.nose
private val CatInnerEar: Color get() = activePalette.innerEar
private val CatIrisLight: Color get() = activePalette.irisLight
private val CatIris: Color get() = activePalette.iris
private val CatIrisEdge: Color get() = activePalette.irisEdge
private val CatPupil = Color(0xFF140E08)
private val CatOutline: Color get() = activePalette.outline
private val CatFarTint: Color get() = activePalette.farTint
private val CatPoint: Color get() = activePalette.dark.copy(alpha = 1f)

private fun furBrush(top: Float, bottom: Float): Brush =
    Brush.verticalGradient(listOf(CatLight, CatBase, CatDark), startY = top, endY = bottom)

private fun ovalPath(center: Offset, w: Float, h: Float): Path =
    Path().apply { addOval(Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)) }

/**
 * Draws the cat for one frame. Called from [InteractiveAiToyTopStage]'s Canvas.
 *
 * @param roamX world x while roaming; other activities sit at [stageCenterX]
 * @param facing +1 facing right, -1 facing left
 * @param walkDistancePx total distance walked, drives the gait so paws never slide
 * @param walkBlend 0 standing still .. 1 full walking stride
 * @param timeSec continuous animation clock in seconds
 * @param blink 0 open .. 1 closed
 * @param gazeX, gazeY -1..1 eye direction in world space
 * @return x position where the cat was drawn (used to anchor heart particles)
 */
internal fun DrawScope.drawOrangeCatCompanion(
    activity: AiToyActivity,
    roamX: Float,
    stageCenterX: Float,
    floorY: Float,
    u: Float,
    facing: Float,
    walkDistancePx: Float,
    walkBlend: Float,
    timeSec: Float,
    blink: Float,
    gazeX: Float,
    gazeY: Float,
    hopOffsetPx: Float,
    squashX: Float,
    squashY: Float,
    tiltDeg: Float,
    accent: Color,
    sandProgress: Float,
    coat: CatCoat = CatCoat.ORANGE_TABBY,
    /** Wellness cue the companion acts out first: WATER, STRETCH, EYE_REST, BREATHE, WALK, LUNCH, FATIGUE, WIND_DOWN, END_OF_DAY, TOP3. */
    cue: String? = null,
    hat: String? = null,
    /** Pet twin palette/markings override the preset coat. */
    palette: CatPalette? = null,
    markings: CatMarkings = CatMarkings(),
    /** Ambient light tint from time of day and weather (alpha = strength). */
    ambientLight: Color = Color.Transparent,
    /** 0 sleepy .. 1 lively: from time of day and the user's self-care mood. */
    energy: Float = 1f,
    /** Extra tail angle from physics (degrees). */
    tailKickDeg: Float = 0f,
    /** Overall opacity (used to cross-fade between activities). */
    alpha: Float = 1f
): Float {
    activePalette = palette ?: coat.toPalette()
    activeMarkings = markings
    activeTailKick = tailKickDeg
    activeHat = hat
    val t = timeSec
    // Alarms always win over wellness cues.
    val effectiveCue = if (activity == AiToyActivity.ALARM) null else cue
    val cueActivity = when (effectiveCue) {
        "LUNCH" -> AiToyActivity.EATING
        "WIND_DOWN", "FATIGUE" -> AiToyActivity.NAPPING
        else -> null
    }
    val customCue = effectiveCue?.takeIf { it in setOf("WATER", "STRETCH", "EYE_REST", "BREATHE", "WALK", "END_OF_DAY", "TOP3") }
    val activity = cueActivity ?: activity
    val alarmShake = if (activity == AiToyActivity.ALARM) sin(t * 55f) * 0.7f * u else 0f
    val x = (if (activity == AiToyActivity.ROAMING && customCue == null) roamX else stageCenterX) + alarmShake
    val face = if (facing < 0f) -1f else 1f

    // Breathing: slow and deep when asleep, subtle when awake, none while walking.
    val breath = when (activity) {
        AiToyActivity.SLEEPING -> sin(t * 1.5f) * 0.035f
        AiToyActivity.NAPPING -> sin(t * 1.9f) * 0.028f
        AiToyActivity.ROAMING -> sin(t * 2.6f) * 0.012f * (1f - walkBlend)
        AiToyActivity.PANDORA_TIMER -> sin(t * 2.2f) * 0.016f + sin(t * 46f) * 0.003f // purr
        else -> sin(t * 2.4f) * 0.014f
    }

    // Soft contact shadow (world space, not mirrored).
    val hopLift = min(abs(hopOffsetPx) / (40f * u), 0.55f)
    val shadowW = when (activity) {
        AiToyActivity.SLEEPING -> 46f
        AiToyActivity.NAPPING -> 50f
        AiToyActivity.ROAMING, AiToyActivity.EATING, AiToyActivity.GYM, AiToyActivity.ALARM -> 62f
        else -> 42f
    } * u * (1f - hopLift)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.5f * (1f - hopLift)), Color.Transparent),
            center = Offset(x, floorY),
            radius = shadowW / 2f
        ),
        topLeft = Offset(x - shadowW / 2f, floorY - 3f * u),
        size = Size(shadowW, 6f * u)
    )

    // Eye direction in the cat's (possibly mirrored) local space.
    val gx = gazeX * face
    val gy = gazeY

    // Draw the cat into its own layer so lighting only tints the cat, and so it can fade.
    val layerBounds = Rect(x - 90f * u, floorY - 110f * u, x + 90f * u, floorY + 6f * u)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(layerBounds, Paint().apply { this.alpha = alpha.coerceIn(0f, 1f) })
    }
    withTransform({
        translate(left = x, top = floorY + hopOffsetPx)
        scale(scaleX = face * squashX, scaleY = squashY * (1f + breath), pivot = Offset.Zero)
        rotate(degrees = tiltDeg, pivot = Offset(0f, -20f * u))
    }) {
        if (customCue != null) {
            drawWellnessCue(customCue, u, t, blink, accent)
        } else when (activity) {
            AiToyActivity.ROAMING -> {
                val idle = 1f - walkBlend
                // Idle moments while standing still: yawn, groom a paw, stretch, look around.
                // A new moment is chosen every 6 s; each fades in and out with a smooth bell curve.
                val slot = (t / 6f).toInt()
                val local = (t % 6f) / 6f
                val bell = (sin(local * PI.toFloat())).coerceAtLeast(0f) * idle
                val moment = if (energy < 0.45f && slot % 2 == 0) 0 else ((slot * 7919) xor (slot shr 2)).mod(4)
                val yawn = if (moment == 0) bell else 0f
                val groom = if (moment == 1) bell else 0f
                val stretch = if (moment == 2) bell else 0f
                val groomLick = if (groom > 0.3f) abs(sin(t * 9f)) else 0f
                drawStandingCat(
                    u = u, t = t,
                    gaitPhase = walkDistancePx / (6f * u),
                    walk = walkBlend,
                    legLen = (15f - stretch * 3f) * u,
                    frontDrop = stretch * 6f * u,
                    headOffset = Offset(-groom * 3f * u, groom * 6f * u + stretch * 2f * u),
                    nearFrontPaw = if (groom > 0.05f) {
                        Offset(27f * u, (-24f - groomLick * 1.5f) * u)
                    } else {
                        null
                    },
                    eyeOpen = (1f - blink) * (1f - yawn * 0.85f) * (1f - groom * 0.6f) * (0.7f + 0.3f * energy.coerceIn(0f, 1f)),
                    pupil = 0.32f,
                    gx = gx, gy = gy,
                    headTilt = sin(t * 0.7f) * 5f * idle + groom * 14f - yawn * 8f,
                    mouthOpen = yawn * 1f + groomLick * groom * 0.25f,
                    tailStart = 200f + stretch * 40f, tailCurl = 13f,
                    tailSwish = 7f + 10f * idle, tailFreq = 1.6f
                )
            }

            AiToyActivity.EATING -> {
                val chew = abs(sin(t * 7f))
                drawStandingCat(
                    u = u, t = t, gaitPhase = 0f, walk = 0f,
                    legLen = 12f * u, frontDrop = 4f * u,
                    headOffset = Offset(5f * u, 11f * u + chew * 0.8f * u),
                    eyeOpen = 0.5f * (1f - blink), pupil = 0.25f,
                    gx = 0.3f, gy = 0.6f,
                    headTilt = 10f, mouthOpen = chew * 0.25f,
                    tailStart = 185f, tailCurl = 6f, tailSwish = 5f, tailFreq = 1.2f
                )
                drawFoodBowl(u, t)
            }

            AiToyActivity.GYM -> {
                // 2.6 s hunting loop: wiggle (0-0.6), pounce (0.6-0.8), land (0.8-1).
                val cycle = (t % 2.6f) / 2.6f
                val pounce = when {
                    cycle < 0.6f -> 0f
                    cycle < 0.8f -> (cycle - 0.6f) / 0.2f
                    else -> 1f - (cycle - 0.8f) / 0.2f
                }
                val arc = sin(pounce * PI.toFloat())
                val wiggle = if (cycle < 0.6f) sin(t * 24f) * 1.3f * u else 0f
                withTransform({ translate(left = pounce * 9f * u, top = -arc * 7f * u) }) {
                    drawStandingCat(
                        u = u, t = t, gaitPhase = 0f, walk = 0f,
                        legLen = (11f + arc * 3f) * u, frontDrop = (5f - arc * 5f) * u,
                        rearWiggle = wiggle,
                        eyeOpen = 1.1f * (1f - blink * 0.5f), pupil = 0.78f,
                        gx = 0.9f, gy = 0.3f,
                        earRot = -6f,
                        tailStart = 190f, tailCurl = 4f, tailSwish = 16f, tailFreq = 5f
                    )
                }
                drawYarnBall(u, t, kick = if (cycle in 0.7f..1f) (cycle - 0.7f) / 0.3f else 0f, accent = accent)
            }

            AiToyActivity.ALARM -> {
                val meow = 0.45f + abs(sin(t * 6f)) * 0.5f
                drawStandingCat(
                    u = u, t = t, gaitPhase = 0f, walk = 0f,
                    legLen = 16.5f * u, arch = 5f * u, puffed = true,
                    eyeOpen = 1.2f, pupil = 0.88f,
                    gx = 0f, gy = -0.2f,
                    earRot = 38f, mouthOpen = meow,
                    tailStart = -100f, tailCurl = 2f, tailSwish = 4f, tailFreq = 9f, tailWidth = 7.5f * u
                )
                drawAlertBurst(Offset(27f * u, -54f * u), u, t, accent)
            }

            AiToyActivity.NAPPING -> {
                drawLoafCat(u, t, eyeOpen = 0.14f, gx = gx)
                drawZzz(Offset(22f * u, -32f * u), u, t, count = 1)
            }

            AiToyActivity.SLEEPING -> {
                drawCurledCat(u, t)
                drawZzz(Offset(14f * u, -22f * u), u, t, count = 3)
            }

            AiToyActivity.WORKING -> {
                val tap = max(0f, sin(t * 9f))
                drawLaptop(u, t, accent)
                drawSittingCat(
                    u = u, t = t,
                    eyeOpen = 0.85f * (1f - blink), pupil = 0.22f,
                    gx = 0.75f, gy = 0.55f,
                    headTilt = 6f,
                    nearPawTarget = Offset(19f * u, -6f * u - tap * 2.5f * u),
                    tailSwish = 6f, tailFreq = 1.1f,
                    screenGlow = accent
                )
            }

            AiToyActivity.BUSY_MEETING -> {
                val twitch = if (sin(t * 3.1f) > 0.96f) 22f else 0f
                val meow = max(0f, sin(t * 1.4f) - 0.82f) * 5f
                drawSittingCat(
                    u = u, t = t,
                    eyeOpen = 1f - blink, pupil = 0.34f,
                    gx = gx * 0.6f + 0.2f, gy = gy * 0.5f,
                    headTilt = sin(t * 0.9f) * 8f,
                    earRotRight = twitch,
                    mouthOpen = meow,
                    tailSwish = 12f, tailFreq = 2.2f,
                    headset = accent
                )
            }

            AiToyActivity.TIMER -> {
                val bat = if ((t % 4.5f) < 0.45f) sin(((t % 4.5f) / 0.45f) * PI.toFloat()) else 0f
                drawHourglass(Offset(27f * u, 0f), u, sandProgress, accent)
                drawSittingCat(
                    u = u, t = t,
                    eyeOpen = 1.05f * (1f - blink), pupil = 0.55f,
                    gx = 0.85f, gy = 0.5f + sin(t * 2f) * 0.2f,
                    headTilt = 8f,
                    nearPawTarget = if (bat > 0f) Offset((12f + bat * 9f) * u, (-6f - bat * 7f) * u) else null,
                    tailSwish = 9f, tailFreq = 1.8f
                )
            }

            AiToyActivity.PANDORA_TIMER -> {
                // Cats "slow blink" when relaxed and trusting.
                val slowBlink = ((sin(t * 0.8f) + 1f) / 2f).let { 0.15f + it * 0.3f }
                drawSittingCat(
                    u = u, t = t,
                    eyeOpen = slowBlink, pupil = 0.3f,
                    gx = 0f, gy = 0.1f,
                    headTilt = 0f,
                    tailSwish = 3f, tailFreq = 0.7f
                )
                drawFocusOrbs(Offset(8f * u, -40f * u), u, t, accent)
                drawDoNotDisturbSign(Offset(-30f * u, 0f), u)
            }
        }
    }
    // Ambient lighting: light falls from above (warm dawn, golden hour, cool moonlight, storm grey).
    if (ambientLight.alpha > 0.01f) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(ambientLight, ambientLight.copy(alpha = ambientLight.alpha * 0.35f)),
                startY = layerBounds.top, endY = layerBounds.bottom
            ),
            topLeft = layerBounds.topLeft,
            size = layerBounds.size,
            blendMode = BlendMode.SrcAtop
        )
    }
    drawIntoCanvas { it.restore() }
    return x
}

// ---------------------------------------------------------------------------------------------
// Poses
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawStandingCat(
    u: Float,
    t: Float,
    gaitPhase: Float,
    walk: Float,
    legLen: Float,
    frontDrop: Float = 0f,
    arch: Float = 0f,
    rearWiggle: Float = 0f,
    puffed: Boolean = false,
    headOffset: Offset = Offset.Zero,
    nearFrontPaw: Offset? = null,
    eyeOpen: Float,
    pupil: Float,
    gx: Float,
    gy: Float,
    headTilt: Float = 0f,
    earRot: Float = 0f,
    mouthOpen: Float = 0f,
    tailStart: Float,
    tailCurl: Float,
    tailSwish: Float,
    tailFreq: Float,
    tailWidth: Float = 4.8f * u
) {
    val bob = abs(sin(gaitPhase)) * 0.7f * u * walk
    val cy = -legLen - 8f * u - arch - bob
    val puff = if (puffed) 1.12f else 1f
    val stride = 6f * u * walk
    val lift = 3.2f * u * walk

    val frontHip = Offset(14f * u, cy + frontDrop + 5f * u)
    val backHip = Offset(-16f * u + rearWiggle, cy + 6f * u)
    val footY = -1.3f * u

    fun footAt(hip: Offset, phase: Float): Offset {
        val dx = stride * sin(phase)
        val up = max(0f, cos(phase)) * lift
        return Offset(hip.x + dx, footY - up)
    }

    // Diagonal gait: near-front with far-back, far-front with near-back.
    val pNearFront = gaitPhase
    val pFarFront = gaitPhase + PI.toFloat()
    val pNearBack = gaitPhase + PI.toFloat()
    val pFarBack = gaitPhase

    // Far side legs (behind the body, slightly darker)
    drawFrontLeg(frontHip + Offset(-2.5f * u, 0f), footAt(frontHip + Offset(-2.5f * u, 0f), pFarFront), u, far = true)
    drawHindLeg(backHip + Offset(2.5f * u, 0f), footAt(backHip + Offset(2.5f * u, 0f), pFarBack), u, far = true)

    // Tail behind the body
    drawCatTail(
        base = Offset(-24f * u + rearWiggle, cy - 3f * u),
        startDeg = tailStart, curlPerSeg = tailCurl,
        segLen = 3.5f * u, segments = 9, width = tailWidth * puff,
        t = t, swishAmp = tailSwish, swishFreq = tailFreq, bristled = puffed
    )

    // Torso: haunch + barrel + chest, merged into one silhouette.
    val body = Path().apply {
        addOval(Rect(-28f * u * puff + rearWiggle, cy - 10.5f * u * puff, 2f * u, cy + 12f * u))
        addOval(Rect(-23f * u, cy - 10f * u * puff, 24f * u, cy + 10f * u + frontDrop * 0.5f))
        addOval(Rect(6f * u, cy - 8.5f * u * puff + frontDrop, 26f * u, cy + 11.5f * u + frontDrop))
    }
    val bodyTop = cy - 11f * u * puff
    val bodyBottom = cy + 12f * u + frontDrop
    drawPath(body, furBrush(bodyTop, bodyBottom))
    clipPath(body) {
        // Cream underside
        drawOval(
            color = CatCream.copy(alpha = 0.75f),
            topLeft = Offset(-6f * u, cy + 5.5f * u + frontDrop * 0.6f),
            size = Size(30f * u, 9f * u)
        )
        drawCalicoPatches(Offset(-8f * u, cy - 4f * u), u)
        // Tabby stripes curving down the flank
        for (i in 0..6) {
            val sx = -24f * u + i * 6.6f * u
            drawLine(
                color = CatStripe,
                start = Offset(sx, bodyTop - 1f * u),
                end = Offset(sx - 3f * u, cy + 4f * u),
                strokeWidth = 2.1f * u,
                cap = StrokeCap.Round
            )
        }
        // Bottom shading for volume
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, CatOutline.copy(alpha = 0.25f)),
                startY = cy + 2f * u, endY = bodyBottom
            ),
            topLeft = Offset(-30f * u, cy + 2f * u),
            size = Size(60f * u, bodyBottom - cy)
        )
    }
    drawFurTufts(-24f * u, 20f * u, cy, 23f * u, 10.5f * u * puff, u, bristled = puffed)

    // Near side legs (in front of the body)
    drawHindLeg(backHip, footAt(backHip, pNearBack), u, far = false)
    if (nearFrontPaw == null) drawFrontLeg(frontHip, footAt(frontHip, pNearFront), u, far = false)

    // Neck + head
    val headC = Offset(28f * u, cy - 10f * u + frontDrop) + headOffset +
        Offset(0f, sin(gaitPhase * 2f) * 0.4f * u * walk)
    drawOval(
        brush = furBrush(headC.y, cy + 8f * u),
        topLeft = Offset(17f * u, headC.y - 2f * u),
        size = Size(13f * u, (cy + 8f * u + frontDrop) - headC.y + 2f * u)
    )
    drawCatHead(
        c = headC, r = 11f * u,
        eyeOpen = eyeOpen, pupil = pupil, gx = gx, gy = gy,
        earRotLeft = earRot, earRotRight = earRot,
        mouthOpen = mouthOpen, tilt = headTilt
    )
    // Grooming: paw raised to the mouth, drawn in front of the head.
    if (nearFrontPaw != null) drawFrontLeg(frontHip, nearFrontPaw, u, far = false)
}

private fun DrawScope.drawSittingCat(
    u: Float,
    t: Float,
    eyeOpen: Float,
    pupil: Float,
    gx: Float,
    gy: Float,
    headTilt: Float,
    earRotLeft: Float = 0f,
    earRotRight: Float = 0f,
    mouthOpen: Float = 0f,
    nearPawTarget: Offset? = null,
    tailSwish: Float,
    tailFreq: Float,
    headset: Color? = null,
    screenGlow: Color? = null
) {
    // Tail lies on the floor behind and curls up at the tip.
    drawCatTail(
        base = Offset(-17f * u, -4f * u),
        startDeg = 178f, curlPerSeg = 1f,
        segLen = 3.4f * u, segments = 9, width = 4.6f * u,
        t = t, swishAmp = tailSwish, swishFreq = tailFreq,
        tipCurlDeg = 70f
    )

    // Far front leg
    drawFrontLeg(Offset(10f * u, -16f * u), Offset(12f * u, -1.3f * u), u, far = true)

    // Haunch
    val haunch = ovalPath(Offset(-6f * u, -12f * u), 31f * u, 24f * u)
    drawPath(haunch, furBrush(-24f * u, 0f))
    clipPath(haunch) {
        drawCalicoPatches(Offset(-8f * u, -14f * u), u)
        for (i in 0..4) {
            val sx = -19f * u + i * 5f * u
            drawLine(CatStripe, Offset(sx, -25f * u), Offset(sx + 3f * u, -10f * u), 2.1f * u, StrokeCap.Round)
        }
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, CatOutline.copy(alpha = 0.28f)), startY = -8f * u, endY = 0f),
            topLeft = Offset(-22f * u, -8f * u),
            size = Size(32f * u, 8f * u)
        )
    }
    // Hind paw
    drawOval(furBrush(-4f * u, 0f), topLeft = Offset(-12f * u, -3.6f * u), size = Size(11f * u, 3.8f * u))

    // Upright chest, leaning slightly forward
    withTransform({ rotate(10f, pivot = Offset(4f * u, -22f * u)) }) {
        val chest = ovalPath(Offset(4f * u, -22f * u), 19f * u, 30f * u)
        drawPath(chest, furBrush(-37f * u, -7f * u))
        clipPath(chest) {
            drawOval(CatCream.copy(alpha = 0.85f), topLeft = Offset(5f * u, -30f * u), size = Size(9f * u, 19f * u))
            for (i in 0..3) {
                val sy = -32f * u + i * 5f * u
                drawLine(CatStripe, Offset(-7f * u, sy), Offset(1f * u, sy + 2f * u), 1.9f * u, StrokeCap.Round)
            }
        }
    }

    // Near front leg: straight down, or reaching to a target (typing, batting).
    val nearHip = Offset(6f * u, -15f * u)
    drawFrontLeg(nearHip, nearPawTarget ?: Offset(6.5f * u, -1.3f * u), u, far = false)

    // Head
    val headC = Offset(8f * u, -40f * u)
    drawCatHead(
        c = headC, r = 11.5f * u,
        eyeOpen = eyeOpen, pupil = pupil, gx = gx, gy = gy,
        earRotLeft = earRotLeft, earRotRight = earRotRight,
        mouthOpen = mouthOpen, tilt = headTilt
    )
    if (screenGlow != null) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(screenGlow.copy(alpha = 0.18f), Color.Transparent),
                center = headC + Offset(6f * u, 2f * u), radius = 14f * u
            ),
            radius = 14f * u, center = headC + Offset(6f * u, 2f * u)
        )
    }
    if (headset != null) drawHeadset(headC, 11.5f * u, headTilt, headset)
}

private fun DrawScope.drawLoafCat(u: Float, t: Float, eyeOpen: Float, gx: Float) {
    val dome = Path().apply {
        moveTo(-25f * u, -0.5f * u)
        cubicTo(-28f * u, -23f * u, 14f * u, -27f * u, 23f * u, -8f * u)
        cubicTo(25f * u, -3f * u, 21f * u, -0.5f * u, 16f * u, -0.5f * u)
        close()
    }
    drawPath(dome, furBrush(-24f * u, 0f))
    clipPath(dome) {
        drawCalicoPatches(Offset(-4f * u, -12f * u), u)
        for (i in 0..6) {
            val sx = -21f * u + i * 6f * u
            drawLine(CatStripe, Offset(sx, -26f * u), Offset(sx - 2f * u, -9f * u), 2.1f * u, StrokeCap.Round)
        }
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, CatOutline.copy(alpha = 0.3f)), startY = -7f * u, endY = 0f),
            topLeft = Offset(-28f * u, -7f * u), size = Size(56f * u, 7f * u)
        )
    }
    drawFurTufts(-22f * u, 12f * u, -10f * u, 22f * u, 13f * u, u, bristled = false)
    // Tucked front paws
    drawOval(CatCream, topLeft = Offset(13f * u, -3.4f * u), size = Size(6f * u, 3.4f * u))
    drawOval(lerp(CatCream, CatBase, 0.25f), topLeft = Offset(17.5f * u, -3f * u), size = Size(6f * u, 3.2f * u))
    // Tail wrapped along the side
    drawCatTail(
        base = Offset(-22f * u, -3f * u),
        startDeg = 8f, curlPerSeg = -1.5f,
        segLen = 3.6f * u, segments = 9, width = 4.4f * u,
        t = t, swishAmp = 2.5f, swishFreq = 0.9f
    )
    drawCatHead(
        c = Offset(19f * u, -21f * u), r = 10.5f * u,
        eyeOpen = eyeOpen, pupil = 0.3f, gx = gx * 0.3f, gy = 0.2f,
        earRotLeft = 8f, earRotRight = 8f, mouthOpen = 0f, tilt = -4f
    )
}

private fun DrawScope.drawCurledCat(u: Float, t: Float) {
    val body = Path().apply {
        addOval(Rect(-21f * u, -21f * u, 19f * u, 0f))
        addOval(Rect(-22f * u, -24f * u, 4f * u, -1f * u))
    }
    drawPath(body, furBrush(-24f * u, 0f))
    clipPath(body) {
        drawCalicoPatches(Offset(-4f * u, -12f * u), u)
        for (i in 0..5) {
            val a = (200f + i * 26f) * (PI.toFloat() / 180f)
            val c = Offset(-2f * u, -10f * u)
            drawLine(
                CatStripe,
                c + Offset(cos(a) * 9f * u, sin(a) * 6f * u),
                c + Offset(cos(a) * 22f * u, sin(a) * 14f * u),
                2.1f * u, StrokeCap.Round
            )
        }
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, CatOutline.copy(alpha = 0.3f)), startY = -6f * u, endY = 0f),
            topLeft = Offset(-24f * u, -6f * u), size = Size(48f * u, 6f * u)
        )
    }
    drawFurTufts(-19f * u, 12f * u, -10f * u, 20f * u, 13f * u, u, bristled = false)
    // Head resting on the body, eyes closed
    drawCatHead(
        c = Offset(11f * u, -9.5f * u), r = 9.5f * u,
        eyeOpen = 0f, pupil = 0.3f, gx = 0f, gy = 0f,
        earRotLeft = 14f, earRotRight = 10f, mouthOpen = 0f, tilt = -20f
    )
    // Tail wraps around the front, over the chin
    drawCatTail(
        base = Offset(-19f * u, -2.5f * u),
        startDeg = 6f, curlPerSeg = -1.2f,
        segLen = 4.1f * u, segments = 10, width = 4.6f * u,
        t = t, swishAmp = 1.5f, swishFreq = 0.5f
    )
}

// ---------------------------------------------------------------------------------------------
// Body parts
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawFrontLeg(hip: Offset, foot: Offset, u: Float, far: Boolean) {
    val color = if (far) lerp(CatBase, CatFarTint, 0.45f) else CatBase
    val wrist = Offset(foot.x - 0.4f * u, foot.y - 3.2f * u)
    drawLine(color, hip, wrist, 5.4f * u, StrokeCap.Round)
    val sock = if (activeMarkings.whiteSocks) (if (far) lerp(SnowWhite, CatFarTint, 0.3f) else SnowWhite) else color
    drawLine(sock, wrist, foot, 4.6f * u, StrokeCap.Round)
    drawOval(
        color = if (far) lerp(CatCream, CatFarTint, 0.4f) else CatCream,
        topLeft = Offset(foot.x - 2.9f * u, foot.y - 1.5f * u),
        size = Size(6.6f * u, 3.1f * u)
    )
}

private fun DrawScope.drawHindLeg(hip: Offset, foot: Offset, u: Float, far: Boolean) {
    val color = if (far) lerp(CatBase, CatFarTint, 0.45f) else CatBase
    // Hock bends backwards like a real cat's ankle.
    val hock = Offset(foot.x - 3f * u, foot.y - 5.5f * u)
    val thigh = Offset((hip.x + hock.x) / 2f + 2.2f * u, (hip.y + hock.y) / 2f)
    drawLine(color, hip, thigh, 8f * u, StrokeCap.Round)
    drawLine(color, thigh, hock, 5.4f * u, StrokeCap.Round)
    val sock = if (activeMarkings.whiteSocks) (if (far) lerp(SnowWhite, CatFarTint, 0.3f) else SnowWhite) else color
    drawLine(sock, hock, foot, 4.4f * u, StrokeCap.Round)
    drawOval(
        color = if (far) lerp(CatCream, CatFarTint, 0.4f) else lerp(CatCream, CatBase, 0.3f),
        topLeft = Offset(foot.x - 3.2f * u, foot.y - 1.5f * u),
        size = Size(7f * u, 3.1f * u)
    )
}

/**
 * Tail as a chain of segments. Each segment bends by [curlPerSeg] plus a travelling sine wave,
 * so the swish starts at the base and flows to the tip like a real tail.
 */
private fun DrawScope.drawCatTail(
    base: Offset,
    startDeg: Float,
    curlPerSeg: Float,
    segLen: Float,
    segments: Int,
    width: Float,
    t: Float,
    swishAmp: Float,
    swishFreq: Float,
    tipCurlDeg: Float = 0f,
    bristled: Boolean = false
) {
    val points = ArrayList<Offset>(segments + 1)
    var p = base
    points.add(p)
    for (i in 0 until segments) {
        val k = (i + 1) / segments.toFloat()
        val tipCurl = if (k > 0.65f) tipCurlDeg * (k - 0.65f) / 0.35f / 3f else 0f
        val deg = startDeg + activeTailKick * (1f - k * 0.4f) + curlPerSeg * i + tipCurl * (i - segments * 0.65f).coerceAtLeast(0f) +
            sin(t * swishFreq * 2f - i * 0.55f) * swishAmp * k
        val rad = deg * (PI.toFloat() / 180f)
        p = Offset(p.x + cos(rad) * segLen, p.y + sin(rad) * segLen)
        points.add(p)
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    // Subtle dark edge, then fur
    drawPath(path, CatOutline.copy(alpha = 0.35f), style = Stroke(width + width * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, CatBase, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Tabby rings
    for (i in 1 until points.size - 1 step 2) {
        drawLine(CatStripe, points[i], points[i + 1], width * 0.96f, StrokeCap.Butt)
    }
    // Darker tip (whole lower tail for colour-point coats)
    if (activeMarkings.darkPoints) {
        for (i in points.size / 2 until points.size - 1) drawLine(CatPoint, points[i], points[i + 1], width * 0.95f, StrokeCap.Round)
    }
    drawLine(CatDark, points[points.size - 2], points.last(), width * 0.9f, StrokeCap.Round)
    // Highlight along the top edge
    drawPath(path, CatLight.copy(alpha = 0.35f), style = Stroke(width * 0.28f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (bristled) {
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val nx = -(b.y - a.y)
            val ny = b.x - a.x
            val len = sqrt(nx * nx + ny * ny).coerceAtLeast(0.001f)
            val n = Offset(nx / len, ny / len) * (width * 0.75f)
            drawLine(CatBase, b + n, b - n, width * 0.25f, StrokeCap.Round)
        }
    }
}

/** Short fur strokes along the top outline of an ellipse for a softer, furry silhouette. */
private fun DrawScope.drawFurTufts(
    fromX: Float, toX: Float, cy: Float, halfW: Float, halfH: Float, u: Float, bristled: Boolean
) {
    val steps = 14
    for (i in 0..steps) {
        val x = fromX + (toX - fromX) * i / steps
        val nx = (x / halfW).coerceIn(-0.98f, 0.98f)
        val y = cy - halfH * sqrt(1f - nx * nx)
        val len = (if (bristled) 3.2f else 1.6f) * u
        drawLine(
            color = (if (bristled) CatBase else CatLight).copy(alpha = if (bristled) 0.95f else 0.55f),
            start = Offset(x, y + 0.6f * u),
            end = Offset(x - len * 0.6f, y - len),
            strokeWidth = (if (bristled) 1.4f else 0.9f) * u,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawCatHead(
    c: Offset,
    r: Float,
    eyeOpen: Float,
    pupil: Float,
    gx: Float,
    gy: Float,
    earRotLeft: Float,
    earRotRight: Float,
    mouthOpen: Float,
    tilt: Float
) {
    withTransform({ rotate(tilt, pivot = c) }) {
        drawCatEar(c, r, side = -1f, rotDeg = earRotLeft)
        drawCatEar(c, r, side = 1f, rotDeg = earRotRight)

        val head = Path().apply {
            moveTo(c.x, c.y - 0.9f * r)
            cubicTo(c.x + 0.62f * r, c.y - 0.92f * r, c.x + 1.02f * r, c.y - 0.45f * r, c.x + 1.04f * r, c.y + 0.12f * r)
            cubicTo(c.x + 1.12f * r, c.y + 0.55f * r, c.x + 0.62f * r, c.y + 0.92f * r, c.x, c.y + 0.9f * r)
            cubicTo(c.x - 0.62f * r, c.y + 0.92f * r, c.x - 1.12f * r, c.y + 0.55f * r, c.x - 1.04f * r, c.y + 0.12f * r)
            cubicTo(c.x - 1.02f * r, c.y - 0.45f * r, c.x - 0.62f * r, c.y - 0.92f * r, c.x, c.y - 0.9f * r)
            close()
        }
        drawPath(
            head,
            Brush.radialGradient(
                listOf(CatLight, CatBase, CatDark),
                center = Offset(c.x - 0.1f * r, c.y - 0.35f * r),
                radius = 1.55f * r
            )
        )
        clipPath(head) {
            // Classic tabby "M" on the forehead
            for (i in -1..1) {
                val sx = c.x + i * 0.27f * r
                drawLine(
                    CatStripe,
                    Offset(sx, c.y - 0.98f * r),
                    Offset(sx + i * 0.05f * r, c.y - (if (i == 0) 0.4f else 0.55f) * r),
                    0.09f * r, StrokeCap.Round
                )
            }
            // Cheek stripes
            for (side in floatArrayOf(-1f, 1f)) {
                for (k in 0..1) {
                    drawLine(
                        CatStripe,
                        Offset(c.x + side * 1.12f * r, c.y + (0.02f + k * 0.2f) * r),
                        Offset(c.x + side * 0.7f * r, c.y + (0.1f + k * 0.16f) * r),
                        0.07f * r, StrokeCap.Round
                    )
                }
            }
            // Light fur around the eyes
            for (side in floatArrayOf(-1f, 1f)) {
                drawOval(
                    CatCream.copy(alpha = 0.35f),
                    topLeft = Offset(c.x + side * 0.4f * r - 0.32f * r, c.y - 0.32f * r),
                    size = Size(0.64f * r, 0.46f * r)
                )
            }
            // Colour-point face mask (Siamese-style) or a big white tuxedo muzzle
            if (activeMarkings.darkPoints) {
                drawOval(CatPoint.copy(alpha = 0.75f), topLeft = Offset(c.x - 0.6f * r, c.y - 0.05f * r), size = Size(1.2f * r, 0.95f * r))
            }
            if (activeMarkings.tuxedo) {
                val bib = Path().apply {
                    moveTo(c.x, c.y - 0.35f * r)
                    cubicTo(c.x + 0.35f * r, c.y - 0.05f * r, c.x + 0.75f * r, c.y + 0.5f * r, c.x + 0.6f * r, c.y + 1f * r)
                    lineTo(c.x - 0.6f * r, c.y + 1f * r)
                    cubicTo(c.x - 0.75f * r, c.y + 0.5f * r, c.x - 0.35f * r, c.y - 0.05f * r, c.x, c.y - 0.35f * r)
                    close()
                }
                drawPath(bib, SnowWhite)
            }
            // Cream whisker pads and chin
            drawOval(CatCream, topLeft = Offset(c.x - 0.43f * r, c.y + 0.25f * r), size = Size(0.45f * r, 0.35f * r))
            drawOval(CatCream, topLeft = Offset(c.x - 0.02f * r, c.y + 0.25f * r), size = Size(0.45f * r, 0.35f * r))
            drawOval(CatCream.copy(alpha = 0.92f), topLeft = Offset(c.x - 0.26f * r, c.y + 0.55f * r), size = Size(0.52f * r, 0.32f * r))
            // Whisker dots
            for (side in floatArrayOf(-1f, 1f)) {
                for (k in 0..2) {
                    drawCircle(
                        CatDark.copy(alpha = 0.5f), radius = 0.022f * r,
                        center = Offset(c.x + side * (0.14f + k * 0.07f) * r, c.y + (0.4f + (k % 2) * 0.05f) * r)
                    )
                }
            }
        }

        for (side in floatArrayOf(-1f, 1f)) {
            drawCatEye(Offset(c.x + side * 0.4f * r, c.y - 0.1f * r), r, eyeOpen, pupil, gx, gy, side)
        }

        // Nose
        val nose = Path().apply {
            moveTo(c.x - 0.12f * r, c.y + 0.23f * r)
            cubicTo(c.x - 0.05f * r, c.y + 0.2f * r, c.x + 0.05f * r, c.y + 0.2f * r, c.x + 0.12f * r, c.y + 0.23f * r)
            cubicTo(c.x + 0.08f * r, c.y + 0.31f * r, c.x + 0.03f * r, c.y + 0.36f * r, c.x, c.y + 0.37f * r)
            cubicTo(c.x - 0.03f * r, c.y + 0.36f * r, c.x - 0.08f * r, c.y + 0.31f * r, c.x - 0.12f * r, c.y + 0.23f * r)
            close()
        }
        drawPath(nose, CatNose)
        drawCircle(Color.White.copy(alpha = 0.4f), radius = 0.03f * r, center = Offset(c.x - 0.04f * r, c.y + 0.25f * r))

        // Mouth
        if (mouthOpen > 0.05f) {
            val mh = 0.26f * r * mouthOpen.coerceAtMost(1f)
            drawOval(Color(0xFF5A1C1C), topLeft = Offset(c.x - 0.13f * r, c.y + 0.42f * r), size = Size(0.26f * r, mh))
            drawOval(Color(0xFFE88A8A), topLeft = Offset(c.x - 0.08f * r, c.y + 0.42f * r + mh * 0.5f), size = Size(0.16f * r, mh * 0.45f))
        }
        val lineW = 0.035f * r
        drawLine(CatOutline, Offset(c.x, c.y + 0.37f * r), Offset(c.x, c.y + 0.45f * r), lineW, StrokeCap.Round)
        drawArc(
            CatOutline, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(c.x - 0.16f * r, c.y + 0.37f * r), size = Size(0.16f * r, 0.15f * r),
            style = Stroke(lineW, cap = StrokeCap.Round)
        )
        drawArc(
            CatOutline, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(c.x, c.y + 0.37f * r), size = Size(0.16f * r, 0.15f * r),
            style = Stroke(lineW, cap = StrokeCap.Round)
        )

        // Whiskers
        for (side in floatArrayOf(-1f, 1f)) {
            for (k in 0..2) {
                val start = Offset(c.x + side * 0.3f * r, c.y + (0.38f + k * 0.06f) * r)
                val end = Offset(c.x + side * 1.5f * r, c.y + (0.2f + k * 0.18f) * r)
                drawLine(Color.White.copy(alpha = 0.75f), start, end, 0.022f * r, StrokeCap.Round)
            }
        }
        activeHat?.let { drawCatHat(it, c, r) }
    }
}

/** Party cone, Santa hat or witch hat sitting between the ears. */
private fun DrawScope.drawCatHat(kind: String, c: Offset, r: Float) {
    val baseY = c.y - 0.72f * r
    when (kind) {
        "SANTA" -> {
            val hat = Path().apply {
                moveTo(c.x - 0.55f * r, baseY)
                cubicTo(c.x - 0.4f * r, c.y - 1.5f * r, c.x + 0.5f * r, c.y - 1.7f * r, c.x + 0.95f * r, c.y - 1.2f * r)
                lineTo(c.x + 0.55f * r, baseY)
                close()
            }
            drawPath(hat, Color(0xFFD62C2C))
            drawRoundRect(
                Color(0xFFF7F3EC), topLeft = Offset(c.x - 0.62f * r, baseY - 0.12f * r), size = Size(1.24f * r, 0.26f * r),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.13f * r)
            )
            drawCircle(Color(0xFFF7F3EC), radius = 0.15f * r, center = Offset(c.x + 0.97f * r, c.y - 1.18f * r))
        }
        "WITCH" -> {
            drawOval(Color(0xFF2B1E3C), topLeft = Offset(c.x - 0.85f * r, baseY - 0.1f * r), size = Size(1.7f * r, 0.28f * r))
            val cone = Path().apply {
                moveTo(c.x - 0.45f * r, baseY)
                lineTo(c.x + 0.12f * r, c.y - 1.85f * r)
                lineTo(c.x + 0.45f * r, baseY)
                close()
            }
            drawPath(cone, Color(0xFF3A2852))
            drawLine(Color(0xFFF2A623), Offset(c.x - 0.38f * r, baseY - 0.12f * r), Offset(c.x + 0.38f * r, baseY - 0.12f * r), 0.09f * r)
        }
        else -> { // PARTY
            val cone = Path().apply {
                moveTo(c.x - 0.42f * r, baseY)
                lineTo(c.x + 0.05f * r, c.y - 1.75f * r)
                lineTo(c.x + 0.42f * r, baseY)
                close()
            }
            drawPath(cone, Color(0xFF7B61FF))
            clipPath(cone) {
                for (k in 0..3) {
                    val y = baseY - k * 0.28f * r
                    drawLine(Color(0xFFFFD166), Offset(c.x - 0.6f * r, y), Offset(c.x + 0.6f * r, y - 0.2f * r), 0.08f * r)
                }
            }
            drawCircle(Color(0xFFFF5A8A), radius = 0.13f * r, center = Offset(c.x + 0.05f * r, c.y - 1.78f * r))
        }
    }
}

/** Wellness scenes: the companion "does it first" so the reminder feels friendly. */
private fun DrawScope.drawWellnessCue(cue: String, u: Float, t: Float, blink: Float, accent: Color) {
    when (cue) {
        "WATER" -> {
            val sip = (sin(t * 1.6f) + 1f) / 2f
            val paw = Offset(14f * u, (-30f - sip * 3f) * u)
            drawSittingCat(
                u = u, t = t, eyeOpen = 0.55f * (1f - blink), pupil = 0.3f, gx = 0.4f, gy = 0.5f,
                headTilt = 6f + sip * 4f, nearPawTarget = paw, tailSwish = 5f, tailFreq = 1f
            )
            // Little glass of water held to the mouth
            val cup = Path().apply {
                moveTo(paw.x - 2.6f * u, paw.y - 6.5f * u); lineTo(paw.x + 2.6f * u, paw.y - 6.5f * u)
                lineTo(paw.x + 2f * u, paw.y + 1f * u); lineTo(paw.x - 2f * u, paw.y + 1f * u); close()
            }
            drawPath(cup, Color(0xFFBFE3F7).copy(alpha = 0.45f))
            clipPath(cup) {
                drawRect(Color(0xFF4FA3E0).copy(alpha = 0.75f), topLeft = Offset(paw.x - 3f * u, paw.y - 3.5f * u), size = Size(6f * u, 5f * u))
            }
            drawPath(cup, Color.White.copy(alpha = 0.7f), style = Stroke(0.4f * u))
        }
        "STRETCH" -> {
            val reach = (sin(t * 0.9f) + 1f) / 2f
            drawStandingCat(
                u = u, t = t, gaitPhase = 0f, walk = 0f,
                legLen = (12f + reach) * u, frontDrop = (6.5f - reach) * u,
                headOffset = Offset(0f, 3f * u), eyeOpen = 0.25f, pupil = 0.3f, gx = 0f, gy = 0f,
                headTilt = -6f, mouthOpen = reach * 0.6f,
                tailStart = 245f, tailCurl = 6f, tailSwish = 6f, tailFreq = 1f
            )
        }
        "EYE_REST" -> {
            drawCueWindow(Offset(26f * u, -60f * u), u, t)
            drawSittingCat(
                u = u, t = t, eyeOpen = 0.9f * (1f - blink), pupil = 0.25f, gx = 1f, gy = -0.7f,
                headTilt = -10f, tailSwish = 4f, tailFreq = 0.8f
            )
        }
        "BREATHE" -> {
            val breath = (sin(t * (2f * PI.toFloat() / 8f)) + 1f) / 2f // 8 s cycle
            drawCircle(accent.copy(alpha = 0.12f + 0.12f * breath), radius = (24f + 8f * breath) * u, center = Offset(4f * u, -28f * u))
            drawCircle(accent.copy(alpha = 0.5f), radius = (24f + 8f * breath) * u, center = Offset(4f * u, -28f * u), style = Stroke(0.6f * u))
            withTransform({ scale(1f + 0.04f * breath, 1f + 0.06f * breath, pivot = Offset(0f, 0f)) }) {
                drawSittingCat(
                    u = u, t = t, eyeOpen = 0.12f, pupil = 0.3f, gx = 0f, gy = 0f,
                    headTilt = 0f, tailSwish = 2f, tailFreq = 0.5f
                )
            }
        }
        "WALK" -> {
            drawCircle(Color(0xFFFFD27A).copy(alpha = 0.85f), radius = 5f * u, center = Offset(34f * u, -58f * u))
            drawStandingCat(
                u = u, t = t, gaitPhase = t * 7f, walk = 1f, legLen = 15f * u,
                eyeOpen = 1f - blink, pupil = 0.25f, gx = 0.8f, gy = -0.2f,
                tailStart = 205f, tailCurl = 13f, tailSwish = 8f, tailFreq = 2f
            )
        }
        "END_OF_DAY" -> {
            // Little bag packed and ready
            drawRoundRect(Color(0xFF8A5A33), topLeft = Offset(19f * u, -10f * u), size = Size(14f * u, 10f * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * u))
            drawArc(Color(0xFF6B4426), 180f, 180f, false, topLeft = Offset(22f * u, -14f * u), size = Size(8f * u, 8f * u), style = Stroke(1.2f * u))
            drawSittingCat(
                u = u, t = t, eyeOpen = 1f - blink, pupil = 0.3f, gx = 0.6f, gy = 0.3f,
                headTilt = 8f, mouthOpen = max(0f, sin(t * 2f) - 0.7f) * 2f, tailSwish = 10f, tailFreq = 2f
            )
        }
        "TOP3" -> {
            // Sticky note with three lines
            drawRect(Color(0xFFFFE27A), topLeft = Offset(18f * u, -22f * u), size = Size(14f * u, 14f * u))
            for (k in 0..2) {
                drawLine(Color(0xFFB08A2E), Offset(20.5f * u, (-18.5f + k * 4f) * u), Offset(29.5f * u, (-18.5f + k * 4f) * u), 0.6f * u)
            }
            drawSittingCat(
                u = u, t = t, eyeOpen = 1f - blink, pupil = 0.35f, gx = 0.8f, gy = 0.5f,
                headTilt = 10f, nearPawTarget = Offset(17f * u, -15f * u), tailSwish = 7f, tailFreq = 1.4f
            )
        }
    }
}

private fun DrawScope.drawCueWindow(topLeft: Offset, u: Float, t: Float) {
    val w = 20f * u
    val h = 17f * u
    drawRect(Color(0xFF8EC9F0).copy(alpha = 0.35f), topLeft = topLeft, size = Size(w, h))
    drawCircle(Color(0xFFFFE3A3).copy(alpha = 0.7f), radius = 2.6f * u, center = topLeft + Offset(w * 0.72f, h * 0.32f))
    drawOval(Color.White.copy(alpha = 0.55f), topLeft = topLeft + Offset(w * 0.1f + sin(t * 0.3f) * u, h * 0.55f), size = Size(7f * u, 2.5f * u))
    drawRect(Color(0xFFD9D2C5), topLeft = topLeft, size = Size(w, h), style = Stroke(1f * u))
    drawLine(Color(0xFFD9D2C5), topLeft + Offset(w / 2f, 0f), topLeft + Offset(w / 2f, h), 0.8f * u)
    drawLine(Color(0xFFD9D2C5), topLeft + Offset(0f, h / 2f), topLeft + Offset(w, h / 2f), 0.8f * u)
}

/** Tiny red "do not disturb" sign on a stand, shown during Focus sessions. */
private fun DrawScope.drawDoNotDisturbSign(base: Offset, u: Float) {
    drawLine(Color(0xFF8A8F98), base, base + Offset(0f, -12f * u), 0.9f * u)
    val c = base + Offset(0f, -15f * u)
    drawCircle(Color(0xFFD64545), radius = 4.2f * u, center = c)
    drawLine(Color.White, c + Offset(-2.4f * u, 0f), c + Offset(2.4f * u, 0f), 1.3f * u, StrokeCap.Round)
}

private fun DrawScope.drawCatEar(c: Offset, r: Float, side: Float, rotDeg: Float) {
    val baseIn = Offset(c.x + side * 0.18f * r, c.y - 0.84f * r)
    val baseOut = Offset(c.x + side * 0.92f * r, c.y - 0.4f * r)
    val tip = Offset(c.x + side * 0.8f * r, c.y - 1.36f * r)
    val pivot = Offset((baseIn.x + baseOut.x) / 2f, (baseIn.y + baseOut.y) / 2f)
    withTransform({ rotate(rotDeg * side, pivot = pivot) }) {
        val ear = Path().apply {
            moveTo(baseIn.x, baseIn.y)
            cubicTo(baseIn.x + (tip.x - baseIn.x) * 0.35f, baseIn.y - 0.3f * r, tip.x - side * 0.1f * r, tip.y + 0.06f * r, tip.x, tip.y)
            cubicTo(tip.x + side * 0.07f * r, tip.y + 0.12f * r, baseOut.x + side * 0.06f * r, baseOut.y - 0.35f * r, baseOut.x, baseOut.y)
            close()
        }
        drawPath(
            ear,
            if (activeMarkings.darkPoints) Brush.verticalGradient(listOf(CatPoint, CatPoint), startY = tip.y, endY = baseOut.y)
            else Brush.verticalGradient(listOf(CatDark, CatBase), startY = tip.y, endY = baseOut.y)
        )
        val centroid = Offset((baseIn.x + baseOut.x + tip.x) / 3f, (baseIn.y + baseOut.y + tip.y) / 3f + 0.06f * r)
        fun shrink(p: Offset) = centroid + (p - centroid) * 0.58f
        val i0 = shrink(baseIn)
        val i1 = shrink(tip)
        val i2 = shrink(baseOut)
        val inner = Path().apply {
            moveTo(i0.x, i0.y)
            cubicTo(i0.x + (i1.x - i0.x) * 0.4f, i0.y - 0.2f * r, i1.x, i1.y + 0.04f * r, i1.x, i1.y)
            cubicTo(i1.x, i1.y + 0.08f * r, i2.x, i2.y - 0.2f * r, i2.x, i2.y)
            close()
        }
        drawPath(inner, Brush.verticalGradient(listOf(CatInnerEar.copy(alpha = 0.55f), CatInnerEar), startY = i1.y, endY = i2.y))
        // Ear fur tufts
        for (k in 0..2) {
            val s = i0 + (i2 - i0) * (0.25f + k * 0.25f)
            drawLine(CatCream.copy(alpha = 0.8f), s, s + (i1 - s) * 0.55f, 0.035f * r, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawCatEye(
    e: Offset, r: Float, open: Float, pupil: Float, gx: Float, gy: Float, side: Float
) {
    val w = 0.25f * r
    val h = 0.2f * r * open.coerceIn(0f, 1.25f)
    if (h < 0.035f * r) {
        // Closed, content eye
        drawArc(
            CatOutline, startAngle = 15f, sweepAngle = 150f, useCenter = false,
            topLeft = Offset(e.x - w, e.y - 0.45f * w), size = Size(2f * w, 0.9f * w),
            style = Stroke(0.055f * r, cap = StrokeCap.Round)
        )
        return
    }
    // Almond shape, outer corner slightly raised
    val outerX = e.x + side * w
    val innerX = e.x - side * w
    val outerY = e.y - 0.05f * r
    val innerY = e.y + 0.03f * r
    val eye = Path().apply {
        moveTo(innerX, innerY)
        cubicTo(innerX + side * 0.3f * w, e.y - h * 1.25f, outerX - side * 0.3f * w, e.y - h * 1.3f, outerX, outerY)
        cubicTo(outerX - side * 0.25f * w, e.y + h * 1.15f, innerX + side * 0.3f * w, e.y + h * 1.1f, innerX, innerY)
        close()
    }
    drawPath(
        eye,
        Brush.radialGradient(
            listOf(CatIrisLight, CatIris, CatIrisEdge),
            center = Offset(e.x, e.y + 0.02f * r), radius = w * 1.15f
        )
    )
    clipPath(eye) {
        val px = e.x + gx * 0.09f * r
        val py = e.y + gy * 0.05f * r
        val pupilW = 2f * w * pupil.coerceIn(0.12f, 0.9f)
        val pupilH = 2.6f * h + 0.1f * r
        drawOval(CatPupil, topLeft = Offset(px - pupilW / 2f, py - pupilH / 2f), size = Size(pupilW, pupilH))
        // Upper-lid shadow gives depth
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent),
                startY = e.y - h * 1.3f, endY = e.y - h * 0.3f
            ),
            topLeft = Offset(e.x - w * 1.1f, e.y - h * 1.4f),
            size = Size(w * 2.2f, h * 1.2f)
        )
        // Catchlights
        drawCircle(Color.White.copy(alpha = 0.92f), radius = 0.05f * r, center = Offset(px - 0.06f * r, py - h * 0.45f))
        drawCircle(Color.White.copy(alpha = 0.5f), radius = 0.022f * r, center = Offset(px + 0.06f * r, py + h * 0.4f))
    }
    drawPath(eye, CatOutline, style = Stroke(0.055f * r, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------------------------
// Props and effects
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawFoodBowl(u: Float, t: Float) {
    val bowl = Path().apply {
        moveTo(25f * u, -6.5f * u)
        lineTo(46f * u, -6.5f * u)
        cubicTo(45f * u, -2f * u, 43f * u, 0f, 40f * u, 0f)
        lineTo(31f * u, 0f)
        cubicTo(28f * u, 0f, 26f * u, -2f * u, 25f * u, -6.5f * u)
        close()
    }
    // Kibble peeking over the rim
    for (k in 0..5) {
        drawCircle(
            Color(0xFF8B5A2B), radius = 1.1f * u,
            center = Offset((28f + k * 2.8f) * u, (-7.2f + (k % 2) * 0.6f) * u)
        )
    }
    drawPath(bowl, Brush.verticalGradient(listOf(Color(0xFF4C8FD6), Color(0xFF1F4F86)), startY = -6.5f * u, endY = 0f))
    drawOval(Color(0xFF8CC3F2), topLeft = Offset(25f * u, -7.6f * u), size = Size(21f * u, 2.2f * u), style = Stroke(0.8f * u))
    drawLine(Color.White.copy(alpha = 0.35f), Offset(28f * u, -5f * u), Offset(30f * u, -2f * u), 0.9f * u, StrokeCap.Round)
}

private fun DrawScope.drawYarnBall(u: Float, t: Float, kick: Float, accent: Color) {
    val roll = sin(kick * PI.toFloat()) * 7f * u
    val c = Offset(40f * u + roll, -5f * u)
    val r = 5f * u
    // Loose thread
    drawLine(accent.copy(alpha = 0.8f), c + Offset(-r * 0.6f, r * 0.7f), Offset(c.x - 9f * u, -0.4f * u), 0.6f * u, StrokeCap.Round)
    drawCircle(
        Brush.radialGradient(listOf(lerp(accent, Color.White, 0.35f), accent, lerp(accent, Color.Black, 0.35f)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.4f),
        radius = r, center = c
    )
    val spin = t * 3f + roll / u
    for (k in 0..3) {
        val a = spin + k * 0.8f
        drawArc(
            lerp(accent, Color.Black, 0.3f).copy(alpha = 0.7f),
            startAngle = a * 57.3f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(c.x - r * (0.4f + k * 0.15f), c.y - r * (0.9f - k * 0.1f)),
            size = Size(r * (0.8f + k * 0.3f), r * (1.8f - k * 0.2f)),
            style = Stroke(0.45f * u)
        )
    }
}

private fun DrawScope.drawLaptop(u: Float, t: Float, accent: Color) {
    // Screen
    drawRoundRect(
        color = Color(0xFF1C2330),
        topLeft = Offset(20f * u, -19f * u), size = Size(17f * u, 12f * u),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2f * u)
    )
    drawRect(accent.copy(alpha = 0.22f), topLeft = Offset(21f * u, -18f * u), size = Size(15f * u, 10f * u))
    for (k in 0..3) {
        val lw = (5f + ((k * 37 + (t * 2f).toInt() * 13) % 8)) * u
        drawLine(accent.copy(alpha = 0.85f), Offset(22.5f * u, (-16.5f + k * 2.3f) * u), Offset(22.5f * u + lw, (-16.5f + k * 2.3f) * u), 0.7f * u, StrokeCap.Round)
    }
    // Keyboard base
    val base = Path().apply {
        moveTo(18f * u, -7f * u); lineTo(39f * u, -7f * u); lineTo(42f * u, -1.5f * u); lineTo(15f * u, -1.5f * u); close()
    }
    drawPath(base, Brush.verticalGradient(listOf(Color(0xFF3A4556), Color(0xFF232B38)), startY = -7f * u, endY = -1.5f * u))
    for (k in 0..4) {
        drawLine(Color(0xFF55627A), Offset((19.5f + k * 4f) * u, -5f * u), Offset((21.5f + k * 4f) * u, -5f * u), 0.7f * u, StrokeCap.Round)
    }
}

private fun DrawScope.drawHourglass(base: Offset, u: Float, progress: Float, accent: Color) {
    val top = base.y - 17f * u
    val bottom = base.y - 1f * u
    val mid = (top + bottom) / 2f
    val half = 4.5f * u
    val wood = Color(0xFF8A5A33)
    drawRoundRect(wood, topLeft = Offset(base.x - half - 1.5f * u, top - 1.5f * u), size = Size(2f * half + 3f * u, 1.6f * u))
    drawRoundRect(wood, topLeft = Offset(base.x - half - 1.5f * u, bottom - 0.2f * u), size = Size(2f * half + 3f * u, 1.6f * u))
    val glass = Path().apply {
        moveTo(base.x - half, top); lineTo(base.x + half, top); lineTo(base.x + 0.5f * u, mid)
        lineTo(base.x + half, bottom); lineTo(base.x - half, bottom); lineTo(base.x - 0.5f * u, mid); close()
    }
    drawPath(glass, Color.White.copy(alpha = 0.12f))
    clipPath(glass) {
        val p = progress.coerceIn(0f, 1f)
        val sand = lerp(Color(0xFFF2C66D), accent, 0.25f)
        drawRect(sand, topLeft = Offset(base.x - half, top + (mid - top) * p), size = Size(2f * half, (mid - top) * (1f - p)))
        drawRect(sand, topLeft = Offset(base.x - half, bottom - (bottom - mid) * p), size = Size(2f * half, (bottom - mid) * p))
        if (p < 0.98f) drawLine(sand, Offset(base.x, mid), Offset(base.x, bottom), 0.5f * u)
    }
    drawPath(glass, Color.White.copy(alpha = 0.45f), style = Stroke(0.5f * u))
}

private fun DrawScope.drawHeadset(c: Offset, r: Float, tilt: Float, accent: Color) {
    withTransform({ rotate(tilt, pivot = c) }) {
        drawArc(
            Color(0xFF2A2F38), startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(c.x - 1.05f * r, c.y - 1.05f * r), size = Size(2.1f * r, 1.7f * r),
            style = Stroke(0.12f * r, cap = StrokeCap.Round)
        )
        for (side in floatArrayOf(-1f, 1f)) {
            drawCircle(Color(0xFF2A2F38), radius = 0.24f * r, center = Offset(c.x + side * 1.0f * r, c.y - 0.05f * r))
            drawCircle(accent, radius = 0.1f * r, center = Offset(c.x + side * 1.0f * r, c.y - 0.05f * r))
        }
        drawLine(Color(0xFF2A2F38), Offset(c.x + 1.0f * r, c.y + 0.1f * r), Offset(c.x + 0.42f * r, c.y + 0.62f * r), 0.06f * r, StrokeCap.Round)
        drawCircle(accent, radius = 0.07f * r, center = Offset(c.x + 0.42f * r, c.y + 0.62f * r))
    }
}

private fun DrawScope.drawZzz(origin: Offset, u: Float, t: Float, count: Int) {
    for (k in 0 until count) {
        val prog = ((t * 0.3f) + k / count.toFloat()) % 1f
        val s = (1.3f + prog * 1.4f) * u
        val p = origin + Offset(prog * 9f * u, -prog * 15f * u)
        val alpha = (1f - prog) * 0.85f
        val z = Path().apply {
            moveTo(p.x - s, p.y - s); lineTo(p.x + s, p.y - s); lineTo(p.x - s, p.y + s); lineTo(p.x + s, p.y + s)
        }
        drawPath(z, Color.White.copy(alpha = alpha), style = Stroke(0.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawAlertBurst(c: Offset, u: Float, t: Float, accent: Color) {
    val pulse = 0.6f + abs(sin(t * 8f)) * 0.4f
    for (k in -1..1) {
        val a = (-90f + k * 28f) * (PI.toFloat() / 180f)
        val start = c + Offset(cos(a) * 4f * u, sin(a) * 4f * u)
        val end = c + Offset(cos(a) * (8f + pulse * 3f) * u, sin(a) * (8f + pulse * 3f) * u)
        drawLine(accent.copy(alpha = pulse), start, end, 1.2f * u, StrokeCap.Round)
    }
}

private fun DrawScope.drawFocusOrbs(center: Offset, u: Float, t: Float, accent: Color) {
    for (k in 0..2) {
        val a = t * 1.1f + k * (2f * PI.toFloat() / 3f)
        val p = center + Offset(cos(a) * 19f * u, sin(a) * 6f * u)
        val depth = (sin(a) + 1f) / 2f
        val r = (1.2f + depth * 0.9f) * u
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.6f), Color.Transparent), center = p, radius = r * 3.5f),
            radius = r * 3.5f, center = p
        )
        drawCircle(Color.White.copy(alpha = 0.6f + depth * 0.4f), radius = r * 0.55f, center = p)
    }
}

/** Calico / tortie patches in the pet's second colour (only when the markings ask for them). */
private fun DrawScope.drawCalicoPatches(center: Offset, u: Float) {
    if (!activeMarkings.patches) return
    val c = activePalette.patch
    drawOval(c, topLeft = center + Offset(-14f * u, -9f * u), size = Size(13f * u, 9f * u))
    drawOval(c, topLeft = center + Offset(4f * u, -6f * u), size = Size(11f * u, 8f * u))
    drawOval(c.copy(alpha = 0.9f), topLeft = center + Offset(-4f * u, 2f * u), size = Size(8f * u, 6f * u))
}
