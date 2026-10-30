package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.util.rememberDockHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.hardware.BatteryTelemetry
import com.example.data.weather.WeatherStationData
import com.example.ui.theme.ChargingEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KilljoyGold
import com.example.ui.theme.OmenViolet
import com.example.ui.theme.RadianiteCyan
import com.example.ui.theme.ValorantCrimson
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.random.Random

enum class AiToyActivity(val chipLabel: String, val hudBadge: String, val accentColor: Color) {
    WORKING("Working", "Working", RadianiteCyan),
    BUSY_MEETING("Meeting", "Meeting", ValorantCrimson),
    EATING("Lunch Time", "Lunch Time", KilljoyGold),
    GYM("Gym Time", "Gym Time", Color(0xFFF97316)),
    NAPPING("Napping", "Napping", OmenViolet),
    SLEEPING("Sleep Time", "Sleep Time", Color(0xFF60A5FA)),
    ROAMING("Roaming", "Roaming", ChargingEmerald),
    ALARM("Alarm", "Alarm", Color(0xFFFF3B5C)),
    TIMER("Timer", "Timer", Color(0xFF38BDF8)),
    PANDORA_TIMER("Focus Timer", "Focus", Color(0xFFA855F7))
}

enum class PandoraPhase(val label: String, val defaultMinutes: Int) {
    FOCUS("FOCUS", 25),
    SHORT_BREAK("SHORT BREAK", 5),
    LONG_BREAK("LONG RECHARGE", 15)
}

data class ActiveChronoHudState(
    val isAlarmRinging: Boolean = false,
    val ringingAlarmLabel: String = "Dock Alarm",
    val ringingAlarmTimeText: String = "07:30",
    val snoozedAlarmUntilMillis: Long? = null,
    val snoozedAlarmLabel: String = "",
    val isTimerRunning: Boolean = false,
    val isTimerRinging: Boolean = false,
    val timerRemainingSeconds: Int = 0,
    val timerTotalSeconds: Int = 300,
    val timerLabel: String = "Countdown Timer",
    val isPandoraRunning: Boolean = false,
    val isPandoraRinging: Boolean = false,
    val pandoraPhase: PandoraPhase = PandoraPhase.FOCUS,
    val pandoraRemainingSeconds: Int = 25 * 60,
    val pandoraTotalSeconds: Int = 25 * 60,
    val pandoraCompletedCycles: Int = 0
) {
    val hasAnyActiveOrRinging: Boolean
        get() = isAlarmRinging || snoozedAlarmUntilMillis != null ||
            isTimerRunning || isTimerRinging ||
            isPandoraRunning || isPandoraRinging
}

fun formatSecondsMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(mins, secs)
}

/**
 * Classifies a calendar/schedule item into the matching AI Toy activity
 * (Lunch Time, Gym Time, Sleep Time, Napping, Working, Alarm, Timer, Pandora, or Other Meeting).
 */
private fun keywordRegex(vararg words: String): Regex =
    Regex("""\b(?:${words.joinToString("|") { Regex.escape(it) }})\b""", RegexOption.IGNORE_CASE)

private val PANDORA_WORDS = keywordRegex("pandora", "pomodoro")
private val ALARM_WORDS = keywordRegex("alarm", "wake up", "wakeup")
private val TIMER_WORDS = keywordRegex("timer", "countdown", "stopwatch")
private val EATING_WORDS = keywordRegex(
    "lunch", "dinner", "breakfast", "brunch", "meal", "eat", "eating", "food", "coffee", "cafe",
    "café", "restaurant", "snack", "dining", "canteen", "bistro", "déjeuner", "dîner"
)
private val GYM_WORDS = keywordRegex(
    "gym", "workout", "work out", "fitness", "exercise", "run", "running", "jog", "jogging",
    "lift", "lifting", "weights", "yoga", "pilates", "crossfit", "cardio", "swim", "swimming",
    "cycling", "spin class", "football", "tennis", "padel", "climbing"
)
private val SLEEP_WORDS = keywordRegex("sleep", "bedtime", "bed time", "slumber", "night standby", "lights out")
private val NAP_WORDS = keywordRegex("nap", "siesta", "meditate", "meditation", "recharge")
private val WORK_WORDS = keywordRegex(
    "deep focus", "focus time", "focus block", "coding", "code review", "study", "studying",
    "deep work", "writing", "homework"
)

/**
 * Maps a calendar event to a companion activity using whole-word matching on the title and
 * location only. Whole words avoid false hits such as "eat" in "great", "run" in "rundown",
 * "nap" in "snapshot" or "sport" in "transport". The account e-mail is intentionally ignored.
 */
fun classifyScheduleEventActivity(event: UnifiedScheduleItem): AiToyActivity {
    val text = "${event.title} ${event.location}"
    return when {
        PANDORA_WORDS.containsMatchIn(text) -> AiToyActivity.PANDORA_TIMER
        ALARM_WORDS.containsMatchIn(text) -> AiToyActivity.ALARM
        TIMER_WORDS.containsMatchIn(text) -> AiToyActivity.TIMER
        EATING_WORDS.containsMatchIn(text) -> AiToyActivity.EATING
        GYM_WORDS.containsMatchIn(text) -> AiToyActivity.GYM
        SLEEP_WORDS.containsMatchIn(text) -> AiToyActivity.SLEEPING
        NAP_WORDS.containsMatchIn(text) -> AiToyActivity.NAPPING
        WORK_WORDS.containsMatchIn(text) -> AiToyActivity.WORKING
        else -> AiToyActivity.BUSY_MEETING
    }
}

/**
 * Resolves the AI Toy's live activity from Alarm/Timer/Pandora state, user manual selection,
 * or automatic Calendar + Time-of-Day + Agent inference state.
 */
fun resolveToyActivity(
    manualOverride: AiToyActivity?,
    isAgentThinking: Boolean,
    currentTimeMillis: Long,
    schedule: List<UnifiedScheduleItem>,
    chronoState: ActiveChronoHudState = ActiveChronoHudState(),
    isCameraInUse: Boolean = false
): Pair<AiToyActivity, String> {
    if (chronoState.isAlarmRinging) {
        return AiToyActivity.ALARM to "ALARM RINGING • ${chronoState.ringingAlarmTimeText}"
    }
    if (chronoState.isTimerRinging || chronoState.isTimerRunning) {
        return AiToyActivity.TIMER to "TIMER • ${formatSecondsMmSs(chronoState.timerRemainingSeconds)}"
    }
    if (chronoState.isPandoraRinging || chronoState.isPandoraRunning) {
        return AiToyActivity.PANDORA_TIMER to "${chronoState.pandoraPhase.label} • ${formatSecondsMmSs(chronoState.pandoraRemainingSeconds)}"
    }
    if (chronoState.snoozedAlarmUntilMillis != null) {
        val remSec = ((chronoState.snoozedAlarmUntilMillis - currentTimeMillis) / 1000L).toInt().coerceAtLeast(0)
        return AiToyActivity.ALARM to "SNOOZED • ${formatSecondsMmSs(remSec)}"
    }

    if (isCameraInUse) {
        return AiToyActivity.BUSY_MEETING to "ON CALL • WEBCAM"
    }

    if (isAgentThinking) {
        return AiToyActivity.WORKING to "Synthesizing AI Briefing…"
    }

    // Check if there is a live active calendar event right now (or starting within 10 mins)
    // All-day events (holidays, "Working from home", offsites) should not pin the companion
    // into meeting mode for 24 hours.
    val activeEvent = schedule.firstOrNull { ev ->
        !ev.isCompleted && !ev.isAllDay &&
            ev.startTimeMillis <= currentTimeMillis + (10 * 60_000L) &&
            ev.endTimeMillis >= currentTimeMillis
    }

    if (manualOverride != null) {
        return manualOverride to manualOverride.chipLabel
    }

    if (activeEvent != null) {
        val activity = classifyScheduleEventActivity(activeEvent)
        return activity to activeEvent.title
    }

    // Time-of-day automatic behavior when calendar is free
    val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val totalMins = hour * 60 + minute

    return when {
        hour >= 23 || hour < 7 ->
            AiToyActivity.SLEEPING to "Sleep Time"
        totalMins in (12 * 60)..(13 * 60 + 30) ->
            AiToyActivity.EATING to "Lunch Time"
        totalMins in (17 * 60 + 30)..(18 * 60 + 45) ->
            AiToyActivity.GYM to "Gym Time"
        totalMins in (19 * 60)..(20 * 60 + 15) ->
            AiToyActivity.EATING to "Dinner Time"
        totalMins in (14 * 60)..(14 * 60 + 45) ->
            AiToyActivity.NAPPING to "Power Nap"
        hour in 9..17 ->
            AiToyActivity.WORKING to "Working"
        else ->
            AiToyActivity.ROAMING to "Roaming"
    }
}

/**
 * Lightweight vector emotion particle for interactive companion tap reactions.
 */
data class ToyEmotionParticle(
    val id: Long,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val scale: Float,
    val alpha: Float,
    val isHeart: Boolean,
    val color: Color
)

/**
 * Draws a glowing neon cyber-heart vector.
 */
private fun drawNeonHeart(
    drawScope: DrawScope,
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float
) {
    val s = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y + s * 0.75f)
        cubicTo(
            center.x - s * 1.25f, center.y,
            center.x - s * 1.15f, center.y - s * 0.95f,
            center.x, center.y - s * 0.35f
        )
        cubicTo(
            center.x + s * 1.15f, center.y - s * 0.95f,
            center.x + s * 1.25f, center.y,
            center.x, center.y + s * 0.75f
        )
        close()
    }
    // Soft outer neon glow
    drawScope.drawPath(
        path = path,
        color = color.copy(alpha = (alpha * 0.35f).coerceIn(0f, 1f)),
        style = Stroke(width = size * 0.28f, cap = StrokeCap.Round)
    )
    // Solid filled heart
    drawScope.drawPath(
        path = path,
        color = color.copy(alpha = alpha)
    )
    // Inner white highlight
    drawScope.drawCircle(
        color = Color.White.copy(alpha = (alpha * 0.85f).coerceIn(0f, 1f)),
        radius = s * 0.22f,
        center = Offset(center.x - s * 0.32f, center.y - s * 0.28f)
    )
}

/**
 * Draws a 4-point glowing neon energy spark.
 */
private fun drawNeonSpark(
    drawScope: DrawScope,
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    // Outer glow
    drawScope.drawPath(
        path = path,
        color = color.copy(alpha = (alpha * 0.40f).coerceIn(0f, 1f)),
        style = Stroke(width = radius * 0.35f, cap = StrokeCap.Round)
    )
    drawScope.drawPath(
        path = path,
        color = color.copy(alpha = alpha)
    )
    // White spark core
    drawScope.drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = radius * 0.25f,
        center = center
    )
}

/**
 * Full-bleed Swipeable AI Toy Companion Animation Stage.
 * Upgraded High-Tech Mecha Companion with articulated joints, neon circuit inlays,
 * fluid physics walking with harmonic cadence, multi-phase idle breathing,
 * interactive tap reactions with playful jump springs, micro-haptic purr, and floating emotion particles.
 */
@Composable
fun InteractiveAiToyTopStage(
    currentTimeMillis: Long,
    schedule: List<UnifiedScheduleItem>,
    isAgentThinking: Boolean,
    manualActivity: AiToyActivity?,
    onSelectManualActivity: (AiToyActivity?) -> Unit,
    onQuickAddCalendarStatus: (AiToyActivity) -> Unit,
    chronoState: ActiveChronoHudState = ActiveChronoHudState(),
    onSnoozeChrono: (AiToyActivity) -> Unit = {},
    onStopChrono: (AiToyActivity) -> Unit = {},
    onStartQuickChrono: (AiToyActivity) -> Unit = {},
    fillAvailableHeight: Boolean = false,
    hapticsEnabled: Boolean = true,
    currentWeather: WeatherStationData? = null,
    /** Draw the realistic orange tabby cat (true) or the original mecha robot (false). */
    useCatCompanion: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Appearance comes from settings (provided at the top of the UI). Long-press opens the
    // companion sheet (coat, name, cat/robot) when a ViewModel is available.
    val appearance = com.example.ui.LocalCompanionAppearance.current
    val dockViewModel = com.example.ui.LocalDockViewModel.current
    val isCameraInUse = dockViewModel?.isWebcamActive?.collectAsState()?.value ?: false
    val catMode = useCatCompanion && appearance.useCat
    val activities = AiToyActivity.entries
    val (resolvedActivity, _) = resolveToyActivity(
        manualOverride = manualActivity,
        isAgentThinking = isAgentThinking,
        currentTimeMillis = currentTimeMillis,
        schedule = schedule,
        chronoState = chronoState,
        isCameraInUse = isCameraInUse
    )

    val activeCalendarEvent = schedule.firstOrNull { ev ->
        !ev.isCompleted && !ev.isAllDay &&
            ev.startTimeMillis <= currentTimeMillis + (10 * 60_000L) &&
            ev.endTimeMillis >= currentTimeMillis
    }

    val initialPage = remember {
        activities.indexOf(resolvedActivity).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { activities.size }
    )

    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberDockHaptics(hapticsEnabled)

    // Interactive physics animation properties
    val hopOffsetY = remember { Animatable(0f) }
    val hopTilt = remember { Animatable(0f) }
    val hopScaleX = remember { Animatable(1f) }
    val hopScaleY = remember { Animatable(1f) }
    var emotionParticles by remember { mutableStateOf<List<ToyEmotionParticle>>(emptyList()) }

    // Respect the system "Remove animations" / animator duration scale setting.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Particle physics, frame-synced and time-based so speed is identical at 60/90/120 Hz.
    LaunchedEffect(emotionParticles.isNotEmpty()) {
        var lastNanos = 0L
        while (isActive && emotionParticles.isNotEmpty()) {
            withFrameNanos { frameNanos ->
                val dtFrames = if (lastNanos == 0L) 1f else ((frameNanos - lastNanos) / 16_666_667f).coerceIn(0.25f, 4f)
                lastNanos = frameNanos
                val velDamp = 0.95f.pow(dtFrames)
                val driftDamp = 0.98f.pow(dtFrames)
                val grow = 1.01f.pow(dtFrames)
                emotionParticles = emotionParticles.mapNotNull { p ->
                    val nextAlpha = p.alpha - 0.024f * dtFrames
                    if (nextAlpha <= 0f) null
                    else p.copy(
                        x = p.x + p.vx * dtFrames,
                        y = p.y + p.vy * dtFrames,
                        vy = p.vy * velDamp - 0.12f * dtFrames,
                        vx = p.vx * driftDamp,
                        alpha = nextAlpha,
                        scale = p.scale * grow
                    )
                }
            }
        }
    }

    // ---- Companion "brain": roaming, stride, blinking and gaze, updated once per frame ----
    // These are plain float states read only inside the Canvas, so updates only redraw.
    var roamWave by remember { mutableFloatStateOf(0f) }          // -1..1 horizontal position
    var roamDistance by remember { mutableFloatStateOf(0f) }      // accumulated |dx| for stride phase
    var roamFacing by remember { mutableFloatStateOf(1f) }        // +1 right, -1 left
    var walkBlend by remember { mutableFloatStateOf(0f) }         // 0 standing .. 1 full stride
    var blinkAmount by remember { mutableFloatStateOf(0f) }       // 0 open .. 1 closed
    var gazeX by remember { mutableFloatStateOf(0f) }             // -1..1
    var gazeY by remember { mutableFloatStateOf(0f) }             // -1..1
    var gazeTargetX by remember { mutableFloatStateOf(0f) }
    var gazeTargetY by remember { mutableFloatStateOf(0f) }
    var gazeHoldUntilNanos by remember { mutableLongStateOf(0L) }
    var tapStreak by remember { mutableIntStateOf(0) }
    var stageTimeSec by remember { mutableFloatStateOf(0f) }      // continuous clock for the cat
    var tailKick by remember { mutableFloatStateOf(0f) }          // tail spring angle (deg)
    var tailKickVel by remember { mutableFloatStateOf(0f) }
    var tailImpulse by remember { mutableFloatStateOf(0f) }       // set on taps/hops

    // Energy "brain": lively mornings, after-lunch dip, sleepy evenings, lifted by the user's self-care mood.
    val wellnessStatsState = dockViewModel?.wellnessStats?.collectAsState()
    val wellnessSettingsState = dockViewModel?.wellnessSettings?.collectAsState()
    val wellnessEnabled = wellnessSettingsState?.value?.enabled ?: true
    val waterToday = wellnessStatsState?.value?.waterToday ?: 0
    val waterGoal = (wellnessStatsState?.value?.waterGoal ?: 8).coerceAtLeast(1)
    val breaksToday = wellnessStatsState?.value?.breaksToday ?: 0
    val top3Done = wellnessStatsState?.value?.top3Done ?: 0
    val top3Total = wellnessStatsState?.value?.top3Total ?: 0
    val waterBottleFillState = rememberWaterBottleFillAnimationState(
        waterToday = waterToday,
        waterGoal = waterGoal
    )
    val moodScore = wellnessStatsState?.value?.moodScore ?: 0.5f
    val hourNow = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }.get(Calendar.HOUR_OF_DAY)
    val baseEnergy = when (hourNow) {
        in 7..11 -> 1f
        in 12..13 -> 0.75f
        in 14..15 -> 0.6f
        in 16..18 -> 0.85f
        in 19..21 -> 0.6f
        else -> 0.3f
    }
    val energy = (baseEnergy * 0.8f + moodScore * 0.2f + 0.05f).coerceIn(0.2f, 1f)
    var energyState by remember { mutableFloatStateOf(1f) }
    energyState = energy
    var lastTapAtMillis by remember { mutableLongStateOf(0L) }

    // Low-power mode for the always-on dock: at night or in battery saver the companion's
    // "brain" updates at ~20 fps instead of every frame (it's mostly sleeping anyway).
    val powerManager = remember(context) { context.getSystemService(android.os.PowerManager::class.java) }
    var lowPowerState by remember { mutableStateOf(false) }
    lowPowerState = hourNow >= 22 || hourNow < 6 || powerManager?.isPowerSaveMode == true

    LaunchedEffect(reducedMotion) {
        var lastNanos = 0L
        var frameCount = 0
        var roamDir = 1f
        var pauseLeft = 0f
        var nextBlinkIn = 2.5f
        var blinkT = -1f
        var doubleBlinkPending = false
        var nextDartIn = 1.5f
        val baseRoamSpeed = 0.36f // normalized units per second (about 5.6 s edge to edge) at full energy

        while (isActive) {
            withFrameNanos { frameNanos ->
                frameCount++
                if (lowPowerState && lastNanos != 0L && frameCount % 3 != 0) return@withFrameNanos
                val dt = if (lastNanos == 0L) 0.016f else ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastNanos = frameNanos
                stageTimeSec = (stageTimeSec + dt) % 3600f

                // Roaming with pauses at the ends and occasional mid-stage stops to look around.
                val moving: Boolean
                if (reducedMotion) {
                    moving = false
                } else if (pauseLeft > 0f) {
                    pauseLeft -= dt
                    moving = false
                } else {
                    val roamSpeed = baseRoamSpeed * (0.55f + 0.45f * energyState)
                    val edgeSlow = (1f - abs(roamWave)).coerceIn(0f, 1f) * 0.6f + 0.4f
                    val dx = roamDir * roamSpeed * edgeSlow * dt
                    roamWave = (roamWave + dx).coerceIn(-1f, 1f)
                    roamDistance += abs(dx)
                    roamFacing = roamDir
                    moving = true
                    if (abs(roamWave) >= 1f) {
                        roamDir = -roamDir
                        pauseLeft = 0.8f + Random.nextFloat() * 1.6f
                    } else if (Random.nextFloat() < dt * (0.04f + 0.08f * (1f - energyState))) {
                        // Sleepier companions stop more often.
                        pauseLeft = 1.2f + Random.nextFloat() * (2f + 3f * (1f - energyState))
                    }
                }
                val targetBlend = if (moving) 1f else 0f
                walkBlend += (targetBlend - walkBlend) * (dt * 6f).coerceAtMost(1f)

                // Blinks: every 2-6 s, ~15% chance of a quick double blink. 140 ms close/open.
                if (blinkT >= 0f) {
                    blinkT += dt
                    val half = 0.07f
                    blinkAmount = if (blinkT < half) blinkT / half else (1f - (blinkT - half) / half).coerceAtLeast(0f)
                    if (blinkT >= half * 2f) {
                        blinkT = -1f
                        blinkAmount = 0f
                        if (doubleBlinkPending) {
                            doubleBlinkPending = false
                            nextBlinkIn = 0.12f
                        } else {
                            nextBlinkIn = 2f + Random.nextFloat() * 4f
                            doubleBlinkPending = Random.nextFloat() < 0.15f
                        }
                    }
                } else {
                    nextBlinkIn -= dt
                    if (nextBlinkIn <= 0f) blinkT = 0f
                }

                // Gaze: follow a tap target for a moment, otherwise small random eye darts.
                if (frameNanos > gazeHoldUntilNanos) {
                    nextDartIn -= dt
                    if (nextDartIn <= 0f) {
                        nextDartIn = 1f + Random.nextFloat() * 2.5f
                        gazeTargetX = (Random.nextFloat() - 0.5f) * 0.9f + roamFacing * 0.25f
                        gazeTargetY = (Random.nextFloat() - 0.5f) * 0.5f
                    }
                }
                // Tail spring: lags behind the walking direction, swings on taps, then settles.
                val tailTarget = -roamFacing * 10f * walkBlend + tailImpulse
                tailImpulse *= (1f - (dt * 3f).coerceAtMost(1f))
                tailKickVel += ((tailTarget - tailKick) * 28f - tailKickVel * 5f) * dt
                tailKick += tailKickVel * dt

                val gazeEase = (dt * 14f).coerceAtMost(1f) // fast, like a saccade
                gazeX += (gazeTargetX - gazeX) * gazeEase
                gazeY += (gazeTargetY - gazeY) * gazeEase
            }
        }
    }

    val triggerToyJumpReaction: (Color, Offset, Float, Float) -> Unit = { accentColor, tapPos, stageW, stageH ->
        // Look toward where the user tapped for ~1 s.
        if (stageW > 0f && stageH > 0f) {
            gazeTargetX = ((tapPos.x - stageW / 2f) / (stageW / 2f)).coerceIn(-1f, 1f)
            gazeTargetY = ((tapPos.y - stageH * 0.55f) / (stageH / 2f)).coerceIn(-1f, 1f)
            gazeHoldUntilNanos = System.nanoTime() + 1_000_000_000L
        }

        tailImpulse = if (Random.nextBoolean()) 30f else -30f

        // Escalating reactions: hop -> spin -> playful "stop it" wiggle.
        val nowMs = System.currentTimeMillis()
        tapStreak = if (nowMs - lastTapAtMillis < 2000L) tapStreak + 1 else 1
        lastTapAtMillis = nowMs
        val streak = tapStreak

        when {
            streak == 1 -> haptics.chirp()
            streak == 2 -> haptics.trill()
            else -> haptics.purr()
        }
        dockViewModel?.playCompanionTapSound(streak)

        if (!reducedMotion) {
            val heartCount = if (streak >= 3) 1 else 5
            val newParticles = List(heartCount) { i ->
                val isHeart = streak < 3 && i % 2 == 0
                ToyEmotionParticle(
                    id = System.nanoTime() + i,
                    x = ((-14..14).random()).toFloat(),
                    y = (-10f - i * 4f),
                    vx = ((-18..18).random() / 10f),
                    vy = ((-28..-14).random() / 10f),
                    scale = (0.75f + (0..4).random() * 0.12f),
                    alpha = 1f,
                    isHeart = isHeart,
                    color = if (isHeart) Color(0xFFFF2A85) else accentColor
                )
            }
            emotionParticles = emotionParticles + newParticles
        }

        coroutineScope.launch {
            if (reducedMotion) {
                // Gentle pulse only.
                hopScaleX.snapTo(1.06f); hopScaleY.snapTo(1.06f)
                launch { hopScaleX.animateTo(1f, tween(220)) }
                launch { hopScaleY.animateTo(1f, tween(220)) }
                return@launch
            }

            when {
                streak == 2 -> launch {
                    // Full spin, then settle with a spring.
                    hopTilt.snapTo(hopTilt.value % 360f)
                    hopTilt.animateTo(360f, tween(520, easing = FastOutSlowInEasing))
                    hopTilt.snapTo(0f)
                }
                streak >= 3 -> launch {
                    // Mock-annoyed head shake.
                    for (angle in listOf(-12f, 12f, -9f, 9f, -5f, 0f)) {
                        hopTilt.animateTo(angle, tween(70))
                    }
                }
                else -> launch {
                    hopTilt.animateTo(
                        targetValue = if (Random.nextBoolean()) -8.5f else 8.5f,
                        animationSpec = tween(120, easing = FastOutSlowInEasing)
                    )
                    hopTilt.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }

            if (streak >= 3) return@launch

            // Squash & stretch: X and Y now animate together (previously they ran one after
            // another, which produced a lopsided squash). Y is the volume-preserving inverse of X.
            suspend fun squash(x: Float, durationMs: Int) {
                launch { hopScaleY.animateTo(1f / x, tween(durationMs)) }
                hopScaleX.animateTo(x, tween(durationMs))
            }
            suspend fun settle() {
                launch { hopScaleY.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                hopScaleX.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            squash(1.16f, 60)                                   // anticipation
            launch { squash(0.90f, 120) }                       // stretch on take-off
            hopOffsetY.animateTo(-32f, tween(190, easing = FastOutSlowInEasing))
            hopOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            squash(1.18f, 65)                                   // landing impact
            settle()
        }
    }

    // Activity cross-fade state
    var fadeFrom by remember { mutableStateOf<AiToyActivity?>(null) }
    val activityFade = remember { Animatable(1f) }

    // Ambient light from time of day, real sunrise/sunset, and the live weather.
    val minuteTick = currentTimeMillis / 60_000L
    val isNightSky = remember(currentWeather, minuteTick, hourNow) {
        if (currentWeather != null) {
            !isDaytimeForWeather(currentWeather, currentTimeMillis)
        } else {
            hourNow >= 21 || hourNow < 6
        }
    }
    val ambientLight = remember(hourNow, currentWeather?.weatherCode, isNightSky) {
        val code = currentWeather?.weatherCode ?: 0
        when {
            code >= 95 -> Color(0xFF5A6B8C).copy(alpha = 0.22f)
            code in 51..82 -> Color(0xFF6E8FB8).copy(alpha = 0.14f)
            isNightSky -> Color(0xFF4A5BD1).copy(alpha = 0.24f)
            hourNow in 6..8 -> Color(0xFFFFB26B).copy(alpha = 0.18f)
            hourNow in 17..19 -> Color(0xFFFF9A4D).copy(alpha = 0.16f)
            else -> Color.Transparent
        }
    }

    // Wellness: the active nudge's scene, seasonal hats, and celebrations (hop + hearts).
    val activeNudgeState = dockViewModel?.activeNudge?.collectAsState()
    val wellnessCue = activeNudgeState?.value?.type?.cue
    val companionHat = dockViewModel?.companionHat?.collectAsState()?.value
    LaunchedEffect(dockViewModel) {
        dockViewModel?.celebrate?.collect {
            triggerToyJumpReaction(Color(0xFFFFD166), Offset.Zero, 0f, 0f)
        }
    }

    // Automatically animate the pager whenever the Calendar schedule, Webcam, Alarm, Timer, or Pandora state changes
    LaunchedEffect(
        resolvedActivity,
        isCameraInUse,
        activeCalendarEvent?.id,
        chronoState.isAlarmRinging,
        chronoState.isTimerRunning,
        chronoState.isTimerRinging,
        chronoState.isPandoraRunning,
        chronoState.isPandoraRinging
    ) {
        val targetIndex = activities.indexOf(resolvedActivity).coerceAtLeast(0)
        fadeFrom = null // a newer change interrupts any running cross-fade
        if (pagerState.currentPage != targetIndex && !pagerState.isScrollInProgress) {
            // Cross-fade the companion into its new activity instead of sliding the whole stage.
            fadeFrom = activities[pagerState.currentPage % activities.size]
            pagerState.scrollToPage(targetIndex)
            activityFade.snapTo(0f)
            activityFade.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            fadeFrom = null
        }
    }

    // Only set manual override if the user manually swiped to a different page
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val settledActivity = activities[pagerState.settledPage % activities.size]
            val autoOnlyActivity = resolveToyActivity(
                manualOverride = null,
                isAgentThinking = isAgentThinking,
                currentTimeMillis = currentTimeMillis,
                schedule = schedule,
                chronoState = chronoState,
                isCameraInUse = isCameraInUse
            ).first
            if (settledActivity != autoOnlyActivity && settledActivity != resolvedActivity) {
                onSelectManualActivity(settledActivity)
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "ai_toy_stage")

    // Fast step / action cycle (0..2PI)
    val actionPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "toy_action_phase"
    )

    // Slow breathing / floating Zzz cycle (0..1f)
    val slowProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "toy_slow_progress"
    )

    Box(
        modifier = (if (fillAvailableHeight) modifier.fillMaxSize() else modifier.fillMaxWidth().height(175.dp))
            .testTag("ai_toy_top_stage")
            .semantics {
                contentDescription = if (catMode) {
                    "${appearance.name} the cat, ${resolvedActivity.chipLabel.lowercase()}. Tap to pet, long-press for companion settings."
                } else {
                    "Companion robot, ${resolvedActivity.chipLabel.lowercase()}"
                }
            }
            .testTag("anker_nano_bot_visor")
    ) {
        // Atmospheric Weather Visualizer backdrop (real-time conditions like rain, sun, storm, snow)
        WeatherAtmosphereVisualizer(
            weatherData = currentWeather,
            currentTimeMillis = currentTimeMillis,
            showLiveBadge = false,
            modifier = Modifier.fillMaxSize()
        )

        // Swipeable HorizontalPager: swipe left or right to change the AI Toy animation!
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageActivity = activities[pageIndex % activities.size]
            val pageAccent = pageActivity.accentColor

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageActivity) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.click()
                                dockViewModel?.openCompanionSheet()
                            },
                            onTap = { pos ->
                                triggerToyJumpReaction(
                                    pageAccent,
                                    pos,
                                    size.width.toFloat(),
                                    size.height.toFloat()
                                )
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Responsive scale unit based on canvas height & width so the toy is large and fits cleanly
                    val u = kotlin.math.min(h / 78f, w / 140f)
                    val floorY = h - 6f * u

                    // Ambient stage radial spotlight
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(pageAccent.copy(alpha = 0.24f), Color.Transparent),
                            center = Offset(w / 2f, h * 0.52f),
                            radius = w * 0.46f
                        ),
                        radius = w * 0.46f,
                        center = Offset(w / 2f, h * 0.52f)
                    )

                    // Sleek glowing holographic floor line right above the Date & Time
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                pageAccent.copy(alpha = 0.85f),
                                pageAccent.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(4f * u, floorY),
                        end = Offset(w - 4f * u, floorY),
                        strokeWidth = 2.4f * u,
                        cap = StrokeCap.Round
                    )

                    // Cyber HUD ticks on the floor line
                    for (tick in -3..3) {
                        val tx = (w / 2f) + tick * 22f * u
                        drawLine(
                            color = pageAccent.copy(alpha = 0.45f),
                            start = Offset(tx, floorY - 2.5f * u),
                            end = Offset(tx, floorY + 2.5f * u),
                            strokeWidth = 1.2f * u
                        )
                    }

                    // Compute Toy X & Y position based on activity
                    val isWalking = pageActivity == AiToyActivity.ROAMING ||
                        pageActivity == AiToyActivity.BUSY_MEETING ||
                        pageActivity == AiToyActivity.GYM
                    val horizontalSpread = when (pageActivity) {
                        AiToyActivity.ROAMING -> w * 0.27f
                        AiToyActivity.BUSY_MEETING -> w * 0.16f
                        AiToyActivity.GYM -> w * 0.08f
                        AiToyActivity.ALARM -> w * 0.03f
                        else -> w * 0.06f
                    }

                    val alarmJitterX = if (pageActivity == AiToyActivity.ALARM) {
                        sin(actionPhase * 10f) * 3.2f * u
                    } else {
                        0f
                    }

                    val toyX = (w / 2f) + (roamWave * horizontalSpread) + alarmJitterX

                    // Step phase comes from distance actually travelled, so feet stop when the
                    // body stops (no more sliding/moonwalking at the turnarounds).
                    val stridePhase = (roamDistance * horizontalSpread / u) * 0.42f
                    val legPhase = if (isWalking) stridePhase else actionPhase
                    val strideAmount = if (isWalking) walkBlend else 1f
                    val walkBounce = when (pageActivity) {
                        AiToyActivity.GYM -> abs(sin(actionPhase * 2.5f)) * (6.5f * u)
                        AiToyActivity.ALARM -> abs(sin(actionPhase * 6f)) * (5.5f * u)
                        else -> if (isWalking) {
                            abs(sin(legPhase * 2f)) * (5.2f * u) * strideAmount +
                                sin(actionPhase) * (1.2f * u) * (1f - strideAmount)
                        } else {
                            sin(actionPhase) * (2.2f * u)
                        }
                    }
                    val isLyingDown = pageActivity == AiToyActivity.NAPPING ||
                        pageActivity == AiToyActivity.SLEEPING

                    // Large, expressive robot body dimensions (scaled by u)
                    val bodyWidth = (if (isLyingDown) 56f else 48f) * u
                    val bodyHeight = (if (isLyingDown) 34f else 40f) * u
                    val toyCenterY = (floorY - bodyHeight / 2f - 7f * u) - walkBounce + (hopOffsetY.value * u * 0.7f)

                    var particleAnchorX = toyX
                    if (catMode) {
                        val fadeP = activityFade.value
                        val previous = fadeFrom
                        val crossFading = previous != null && previous != pageActivity && fadeP < 1f
                        if (crossFading) {
                            drawOrangeCatCompanion(
                                activity = previous!!,
                                roamX = (w / 2f) + roamWave * (w * 0.18f),
                                stageCenterX = w / 2f,
                                floorY = floorY,
                                u = u,
                                facing = roamFacing,
                                walkDistancePx = roamDistance * (w * 0.18f),
                                walkBlend = walkBlend,
                                timeSec = stageTimeSec,
                                blink = blinkAmount,
                                gazeX = gazeX,
                                gazeY = gazeY,
                                hopOffsetPx = 0f,
                                squashX = 1f + 0.06f * fadeP,
                                squashY = 1f - 0.06f * fadeP,
                                tiltDeg = 0f,
                                accent = pageAccent,
                                sandProgress = slowProgress,
                                coat = appearance.coat,
                                palette = appearance.palette,
                                markings = appearance.markings,
                                ambientLight = ambientLight,
                                energy = energy,
                                tailKickDeg = tailKick,
                                alpha = 1f - fadeP
                            )
                            // Soft "poof" ring at the hand-off
                            val ring = sin(fadeP * PI.toFloat())
                            drawCircle(
                                color = pageAccent.copy(alpha = 0.35f * ring),
                                radius = (18f + 22f * fadeP) * u,
                                center = Offset(w / 2f, floorY - 18f * u),
                                style = Stroke(width = 1.4f * u)
                            )
                        }
                        particleAnchorX = drawOrangeCatCompanion(
                            alpha = if (crossFading) fadeP else 1f,
                            palette = appearance.palette,
                            markings = appearance.markings,
                            ambientLight = ambientLight,
                            energy = energy,
                            tailKickDeg = tailKick,
                            activity = pageActivity,
                            roamX = (w / 2f) + roamWave * (w * 0.18f),
                            stageCenterX = w / 2f,
                            floorY = floorY,
                            u = u,
                            facing = roamFacing,
                            walkDistancePx = roamDistance * (w * 0.18f),
                            walkBlend = walkBlend,
                            timeSec = stageTimeSec,
                            blink = blinkAmount,
                            gazeX = gazeX,
                            gazeY = gazeY,
                            hopOffsetPx = hopOffsetY.value * u * 0.7f,
                            squashX = hopScaleX.value,
                            squashY = hopScaleY.value,
                            tiltDeg = hopTilt.value,
                            accent = pageAccent,
                            sandProgress = slowProgress,
                            coat = appearance.coat,
                            cue = wellnessCue,
                            hat = companionHat
                        )
                    } else {
                    // Ground shadow beneath the Toy
                    val shadowScale = (1f - (walkBounce / (12f * u)) - (abs(hopOffsetY.value) / 45f)).coerceIn(0.45f, 1f)
                    drawOval(
                        color = Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(toyX - (bodyWidth * 0.46f * shadowScale), floorY - 2.5f * u),
                        size = Size(bodyWidth * 0.92f * shadowScale, 5.5f * u)
                    )
                    // Concentric cyber shadow ring
                    drawOval(
                        color = pageAccent.copy(alpha = 0.30f * shadowScale),
                        topLeft = Offset(toyX - (bodyWidth * 0.38f * shadowScale), floorY - 2.0f * u),
                        size = Size(bodyWidth * 0.76f * shadowScale, 4.2f * u),
                        style = Stroke(width = 1.2f * u)
                    )

                    // Physics multi-phase idle breathing & tap jump squash-stretch
                    val idleBreath = if (isLyingDown) {
                        1f + sin(slowProgress * 2f * PI.toFloat()) * 0.038f
                    } else {
                        1f + sin(slowProgress * 2f * PI.toFloat()) * 0.024f
                    }
                    val currentScaleX = hopScaleX.value * idleBreath
                    val currentScaleY = hopScaleY.value * idleBreath
                    val tiltDeg = hopTilt.value

                    // Render articulated Mecha chassis with spring transforms
                    withTransform({
                        scale(scaleX = currentScaleX, scaleY = currentScaleY, pivot = Offset(toyX, toyCenterY))
                        rotate(degrees = tiltDeg, pivot = Offset(toyX, toyCenterY))
                    }) {
                        // Strong ambient aura glow behind the Mecha
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(pageAccent.copy(alpha = 0.38f), Color.Transparent),
                                center = Offset(toyX, toyCenterY),
                                radius = 48f * u
                            ),
                            radius = 48f * u,
                            center = Offset(toyX, toyCenterY)
                        )

                        // --- ARTICULATED ROBOTIC LEGS (Knee servos, hydraulic shin pistons, booster boots) ---
                        val leftLegCycle = legPhase * 2f
                        val rightLegCycle = leftLegCycle + PI.toFloat()
                        val liftAmount = if (isWalking) strideAmount else 1f
                        val leftFootLift = if (isWalking || pageActivity == AiToyActivity.ALARM) {
                            (sin(leftLegCycle) * 6f * u * liftAmount).coerceAtLeast(0f)
                        } else 0f
                        val rightFootLift = if (isWalking || pageActivity == AiToyActivity.ALARM) {
                            (sin(rightLegCycle) * 6f * u * liftAmount).coerceAtLeast(0f)
                        } else 0f
                        // Planted foot drifts backwards relative to the hips in the walking direction.
                        val leftFootSlide = if (isWalking) -roamFacing * cos(leftLegCycle) * 3.8f * u * strideAmount else 0f
                        val rightFootSlide = if (isWalking) -roamFacing * cos(rightLegCycle) * 3.8f * u * strideAmount else 0f

                        val leftHipX = toyX - 10f * u
                        val rightHipX = toyX + 10f * u
                        val hipY = toyCenterY + bodyHeight / 2f - 3f * u

                        // Left Leg Assembly
                        val leftFootX = leftHipX + leftFootSlide
                        val leftFootY = floorY - 8.5f * u - leftFootLift
                        val leftKneeY = hipY + (leftFootY - hipY) * 0.52f

                        // Left Thigh strut (dark titanium armor)
                        drawLine(
                            color = Color(0xFF1E2B3E),
                            start = Offset(leftHipX, hipY),
                            end = Offset(leftFootX, leftKneeY),
                            strokeWidth = 5.2f * u,
                            cap = StrokeCap.Round
                        )
                        // Left Knee Servo Joint Hub
                        drawCircle(pageAccent.copy(alpha = 0.55f), radius = 4.2f * u, center = Offset(leftFootX, leftKneeY))
                        drawCircle(Color(0xFF111824), radius = 3.2f * u, center = Offset(leftFootX, leftKneeY))
                        drawCircle(Color(0xFFE2E8F0), radius = 1.3f * u, center = Offset(leftFootX, leftKneeY))

                        // Left Shin strut with hydraulic neon conduit
                        drawLine(
                            color = Color(0xFF27384E),
                            start = Offset(leftFootX, leftKneeY),
                            end = Offset(leftFootX, leftFootY),
                            strokeWidth = 4.8f * u,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = pageAccent,
                            start = Offset(leftFootX, leftKneeY + 1f * u),
                            end = Offset(leftFootX, leftFootY),
                            strokeWidth = 1.4f * u,
                            cap = StrokeCap.Round
                        )

                        // Left Mag-Lev Booster Boot
                        drawRoundRect(
                            color = pageAccent,
                            topLeft = Offset(leftFootX - 5.5f * u, leftFootY),
                            size = Size(11f * u, 7.5f * u),
                            cornerRadius = CornerRadius(3.5f * u, 3.5f * u)
                        )
                        // Left Rear Thruster Glow
                        if (leftFootLift > 0.5f) {
                            drawCircle(
                                color = pageAccent.copy(alpha = 0.85f),
                                radius = 3.5f * u,
                                center = Offset(leftFootX - 3.5f * u, leftFootY + 7.5f * u)
                            )
                        }

                        // Right Leg Assembly
                        val rightFootX = rightHipX + rightFootSlide
                        val rightFootY = floorY - 8.5f * u - rightFootLift
                        val rightKneeY = hipY + (rightFootY - hipY) * 0.52f

                        // Right Thigh strut
                        drawLine(
                            color = Color(0xFF1E2B3E),
                            start = Offset(rightHipX, hipY),
                            end = Offset(rightFootX, rightKneeY),
                            strokeWidth = 5.2f * u,
                            cap = StrokeCap.Round
                        )
                        // Right Knee Servo Joint Hub
                        drawCircle(pageAccent.copy(alpha = 0.55f), radius = 4.2f * u, center = Offset(rightFootX, rightKneeY))
                        drawCircle(Color(0xFF111824), radius = 3.2f * u, center = Offset(rightFootX, rightKneeY))
                        drawCircle(Color(0xFFE2E8F0), radius = 1.3f * u, center = Offset(rightFootX, rightKneeY))

                        // Right Shin strut with hydraulic neon conduit
                        drawLine(
                            color = Color(0xFF27384E),
                            start = Offset(rightFootX, rightKneeY),
                            end = Offset(rightFootX, rightFootY),
                            strokeWidth = 4.8f * u,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = pageAccent,
                            start = Offset(rightFootX, rightKneeY + 1f * u),
                            end = Offset(rightFootX, rightFootY),
                            strokeWidth = 1.4f * u,
                            cap = StrokeCap.Round
                        )

                        // Right Mag-Lev Booster Boot
                        drawRoundRect(
                            color = pageAccent,
                            topLeft = Offset(rightFootX - 5.5f * u, rightFootY),
                            size = Size(11f * u, 7.5f * u),
                            cornerRadius = CornerRadius(3.5f * u, 3.5f * u)
                        )
                        // Right Rear Thruster Glow
                        if (rightFootLift > 0.5f) {
                            drawCircle(
                                color = pageAccent.copy(alpha = 0.85f),
                                radius = 3.5f * u,
                                center = Offset(rightFootX - 3.5f * u, rightFootY + 7.5f * u)
                            )
                        }

                        // --- HEAD ANTENNA SPIRE WITH BEACON DIODE ---
                        val antennaTipX = toyX + sin(actionPhase) * 5.2f * u
                        val antennaTopY = toyCenterY - bodyHeight / 2f - 11.5f * u
                        drawLine(
                            color = Color(0xFF384B66),
                            start = Offset(toyX, toyCenterY - bodyHeight / 2f),
                            end = Offset(antennaTipX, antennaTopY),
                            strokeWidth = 3.2f * u,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = pageAccent,
                            start = Offset(toyX, toyCenterY - bodyHeight / 2f),
                            end = Offset(antennaTipX, antennaTopY),
                            strokeWidth = 1.5f * u,
                            cap = StrokeCap.Round
                        )
                        // Blinking Omni Beacon Diode
                        drawCircle(
                            color = pageAccent.copy(alpha = 0.38f + abs(sin(actionPhase * 2f)) * 0.45f),
                            radius = 7.5f * u,
                            center = Offset(antennaTipX, antennaTopY)
                        )
                        drawCircle(
                            color = pageAccent,
                            radius = 4.2f * u,
                            center = Offset(antennaTipX, antennaTopY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.8f * u,
                            center = Offset(antennaTipX, antennaTopY)
                        )

                        // --- SIDE THRUSTER / AUDIO SENSOR EAR PODS ---
                        // Left Ear Pod with Cooling Vents
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF26374D), Color(0xFF141F2E)),
                                startY = toyCenterY - 8f * u,
                                endY = toyCenterY + 8f * u
                            ),
                            topLeft = Offset(toyX - bodyWidth / 2f - 5f * u, toyCenterY - 7.5f * u),
                            size = Size(5f * u, 15f * u),
                            cornerRadius = CornerRadius(2.5f * u, 2.5f * u)
                        )
                        // Left Pod Neon Intake Slats
                        for (s in 0..2) {
                            val slatY = toyCenterY - 4.5f * u + s * 4f * u
                            drawLine(
                                color = pageAccent.copy(alpha = 0.85f),
                                start = Offset(toyX - bodyWidth / 2f - 4.2f * u, slatY),
                                end = Offset(toyX - bodyWidth / 2f - 0.8f * u, slatY),
                                strokeWidth = 1.4f * u,
                                cap = StrokeCap.Round
                            )
                        }

                        // Right Ear Pod with Cooling Vents
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF26374D), Color(0xFF141F2E)),
                                startY = toyCenterY - 8f * u,
                                endY = toyCenterY + 8f * u
                            ),
                            topLeft = Offset(toyX + bodyWidth / 2f, toyCenterY - 7.5f * u),
                            size = Size(5f * u, 15f * u),
                            cornerRadius = CornerRadius(2.5f * u, 2.5f * u)
                        )
                        // Right Pod Neon Intake Slats
                        for (s in 0..2) {
                            val slatY = toyCenterY - 4.5f * u + s * 4f * u
                            drawLine(
                                color = pageAccent.copy(alpha = 0.85f),
                                start = Offset(toyX + bodyWidth / 2f + 0.8f * u, slatY),
                                end = Offset(toyX + bodyWidth / 2f + 4.2f * u, slatY),
                                strokeWidth = 1.4f * u,
                                cap = StrokeCap.Round
                            )
                        }

                        // --- TITANIUM MECHA CHASSIS WITH CARBON TRIM ---
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF26374D), Color(0xFF162334), Color(0xFF0C131D)),
                                startY = toyCenterY - bodyHeight / 2f,
                                endY = toyCenterY + bodyHeight / 2f
                            ),
                            topLeft = Offset(toyX - bodyWidth / 2f, toyCenterY - bodyHeight / 2f),
                            size = Size(bodyWidth, bodyHeight),
                            cornerRadius = CornerRadius(12f * u, 12f * u)
                        )
                        // Metallic Armor Chamfer Bezel
                        drawRoundRect(
                            color = Color(0xFF384E6B),
                            topLeft = Offset(toyX - bodyWidth / 2f, toyCenterY - bodyHeight / 2f),
                            size = Size(bodyWidth, bodyHeight),
                            cornerRadius = CornerRadius(12f * u, 12f * u),
                            style = Stroke(width = 2.4f * u)
                        )
                        // Outer Neon Accent Glow Line
                        drawRoundRect(
                            color = pageAccent.copy(alpha = 0.70f),
                            topLeft = Offset(toyX - bodyWidth / 2f, toyCenterY - bodyHeight / 2f),
                            size = Size(bodyWidth, bodyHeight),
                            cornerRadius = CornerRadius(12f * u, 12f * u),
                            style = Stroke(width = 1.2f * u)
                        )

                        // --- NEON CIRCUIT TRACE INLAYS ALONG ARMOR SEAMS ---
                        val circuitBrush = Brush.linearGradient(
                            colors = listOf(
                                pageAccent.copy(alpha = 0.35f),
                                pageAccent,
                                Color.White.copy(alpha = 0.9f),
                                pageAccent
                            )
                        )
                        // Left armor seam circuit
                        drawLine(
                            brush = circuitBrush,
                            start = Offset(toyX - bodyWidth / 2f + 4f * u, toyCenterY - bodyHeight / 2f + 5f * u),
                            end = Offset(toyX - bodyWidth / 2f + 10f * u, toyCenterY + bodyHeight / 2f - 4f * u),
                            strokeWidth = 1.5f * u,
                            cap = StrokeCap.Round
                        )
                        // Right armor seam circuit
                        drawLine(
                            brush = circuitBrush,
                            start = Offset(toyX + bodyWidth / 2f - 4f * u, toyCenterY - bodyHeight / 2f + 5f * u),
                            end = Offset(toyX + bodyWidth / 2f - 10f * u, toyCenterY + bodyHeight / 2f - 4f * u),
                            strokeWidth = 1.5f * u,
                            cap = StrokeCap.Round
                        )

                        // --- CENTRAL ENERGY REACTOR CORE ---
                        val coreY = toyCenterY + bodyHeight * 0.28f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(pageAccent.copy(alpha = 0.55f), Color.Transparent),
                                center = Offset(toyX, coreY),
                                radius = 9f * u
                            ),
                            radius = 9f * u,
                            center = Offset(toyX, coreY)
                        )
                        drawCircle(Color(0xFF223246), radius = 5.2f * u, center = Offset(toyX, coreY))
                        drawCircle(Color(0xFF0F1722), radius = 4.2f * u, center = Offset(toyX, coreY))
                        val pulseRad = (2.6f + sin(actionPhase * 3f) * 0.8f) * u
                        drawCircle(pageAccent, radius = pulseRad, center = Offset(toyX, coreY))
                        drawCircle(Color.White, radius = pulseRad * 0.45f, center = Offset(toyX, coreY))

                        // --- OBSIDIAN OLED HUD VISOR WITH REAL-TIME SCANLINES ---
                        val visorW = bodyWidth - 9f * u
                        val visorH = bodyHeight - 11f * u
                        drawRoundRect(
                            color = Color(0xFF03070E),
                            topLeft = Offset(toyX - visorW / 2f, toyCenterY - visorH / 2f - 2f * u),
                            size = Size(visorW, visorH),
                            cornerRadius = CornerRadius(9f * u, 9f * u)
                        )
                        drawRoundRect(
                            color = Color(0xFF1E2D40),
                            topLeft = Offset(toyX - visorW / 2f, toyCenterY - visorH / 2f - 2f * u),
                            size = Size(visorW, visorH),
                            cornerRadius = CornerRadius(9f * u, 9f * u),
                            style = Stroke(width = 1.2f * u)
                        )

                        // Active 60 FPS Real-Time HUD Scanlines sweeping across visor
                        for (s in 0..3) {
                            val scanProgress = ((actionPhase * 0.7f + s * 0.25f) % 1f)
                            val scanY = (toyCenterY - visorH / 2f - 2f * u) + (scanProgress * visorH)
                            drawLine(
                                color = pageAccent.copy(alpha = 0.12f),
                                start = Offset(toyX - visorW / 2f + 2.5f * u, scanY),
                                end = Offset(toyX + visorW / 2f - 2.5f * u, scanY),
                                strokeWidth = 1.1f * u
                            )
                        }

                        // --- EXPRESSIVE CYBER OPTICS & ACTIVITY PROPS ---
                        val isAwakeFace = pageActivity != AiToyActivity.SLEEPING &&
                            pageActivity != AiToyActivity.NAPPING &&
                            pageActivity != AiToyActivity.ALARM
                        val faceShiftX = if (isAwakeFace) gazeX * 2.4f * u else 0f
                        val faceShiftY = if (isAwakeFace) gazeY * 1.4f * u else 0f
                        translate(left = faceShiftX, top = faceShiftY) {
                        when (pageActivity) {
                            AiToyActivity.SLEEPING, AiToyActivity.NAPPING -> {
                                // Standby low-power cloud dock
                                drawRoundRect(
                                    color = pageAccent.copy(alpha = 0.30f),
                                    topLeft = Offset(toyX - 36f * u, floorY - 7f * u),
                                    size = Size(72f * u, 7f * u),
                                    cornerRadius = CornerRadius(5f * u, 5f * u)
                                )

                                // Standby sleeping cyber eye arcs
                                drawArc(
                                    color = pageAccent,
                                    startAngle = 20f,
                                    sweepAngle = 140f,
                                    useCenter = false,
                                    topLeft = Offset(toyX - 15f * u, toyCenterY - 8f * u),
                                    size = Size(11f * u, 7.5f * u),
                                    style = Stroke(width = 2.8f * u, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = pageAccent,
                                    startAngle = 20f,
                                    sweepAngle = 140f,
                                    useCenter = false,
                                    topLeft = Offset(toyX + 4f * u, toyCenterY - 8f * u),
                                    size = Size(11f * u, 7.5f * u),
                                    style = Stroke(width = 2.8f * u, cap = StrokeCap.Round)
                                )

                                // Standby pulse breathing bubble
                                val bubbleRadius = (2.5f + sin(actionPhase) * 1.8f) * u
                                drawCircle(
                                    color = pageAccent.copy(alpha = 0.45f),
                                    radius = bubbleRadius,
                                    center = Offset(toyX + 4f * u, toyCenterY + 3f * u)
                                )
                                drawCircle(
                                    color = pageAccent,
                                    radius = bubbleRadius,
                                    center = Offset(toyX + 4f * u, toyCenterY + 3f * u),
                                    style = Stroke(width = 1.1f * u)
                                )

                                // Floating Zzz cyber glyphs rising upward
                                for (z in 0..2) {
                                    val zProg = (slowProgress + z * 0.33f) % 1f
                                    val zx = toyX + (26f + z * 11f) * u + sin(zProg * 6f) * 4f * u
                                    val zy = (toyCenterY - 8f * u) - (zProg * 30f * u)
                                    val zSize = (5.5f + z * 2.2f) * u
                                    val zAlpha = (1f - zProg).coerceIn(0.15f, 0.95f)
                                    drawLine(
                                        color = pageAccent.copy(alpha = zAlpha),
                                        start = Offset(zx, zy),
                                        end = Offset(zx + zSize, zy),
                                        strokeWidth = 2f * u,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = pageAccent.copy(alpha = zAlpha),
                                        start = Offset(zx + zSize, zy),
                                        end = Offset(zx, zy + zSize),
                                        strokeWidth = 2f * u,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = pageAccent.copy(alpha = zAlpha),
                                        start = Offset(zx, zy + zSize),
                                        end = Offset(zx + zSize, zy + zSize),
                                        strokeWidth = 2f * u,
                                        cap = StrokeCap.Round
                                    )
                                }

                                if (pageActivity == AiToyActivity.SLEEPING) {
                                    // Cyber crescent moon & stars
                                    drawCircle(
                                        color = KilljoyGold.copy(alpha = 0.9f),
                                        radius = 9.5f * u,
                                        center = Offset(26f * u, 22f * u)
                                    )
                                    drawCircle(
                                        color = Color(0xFF04070B),
                                        radius = 7.5f * u,
                                        center = Offset(30f * u, 19f * u)
                                    )
                                    val starAlpha = 0.4f + abs(sin(actionPhase)) * 0.6f
                                    drawCircle(
                                        color = KilljoyGold.copy(alpha = starAlpha),
                                        radius = 2.2f * u,
                                        center = Offset(48f * u, 16f * u)
                                    )
                                }
                            }

                            AiToyActivity.EATING -> {
                                // Happy curved cyber optics (^ ^)
                                drawArc(
                                    color = pageAccent,
                                    startAngle = 195f,
                                    sweepAngle = 150f,
                                    useCenter = false,
                                    topLeft = Offset(toyX - 15f * u, toyCenterY - 9f * u),
                                    size = Size(11f * u, 8.5f * u),
                                    style = Stroke(width = 3f * u, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = pageAccent,
                                    startAngle = 195f,
                                    sweepAngle = 150f,
                                    useCenter = false,
                                    topLeft = Offset(toyX + 4f * u, toyCenterY - 9f * u),
                                    size = Size(11f * u, 8.5f * u),
                                    style = Stroke(width = 3f * u, cap = StrokeCap.Round)
                                )

                                // Animated munching energy mouth
                                val mouthOpen = (abs(sin(actionPhase * 2.2f)) * 6.5f * u).coerceAtLeast(2f * u)
                                drawRoundRect(
                                    color = ValorantCrimson,
                                    topLeft = Offset(toyX - 5.5f * u, toyCenterY + 1.5f * u),
                                    size = Size(11f * u, mouthOpen),
                                    cornerRadius = CornerRadius(3.5f * u, 3.5f * u)
                                )

                                // Big Steaming Ramen / Power Fuel Bowl
                                val bowlX = toyX + 38f * u
                                val bowlY = floorY - 12f * u
                                drawArc(
                                    color = ValorantCrimson,
                                    startAngle = 0f,
                                    sweepAngle = 180f,
                                    useCenter = true,
                                    topLeft = Offset(bowlX - 15f * u, bowlY - 10f * u),
                                    size = Size(30f * u, 22f * u)
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(bowlX - 15f * u, bowlY),
                                    end = Offset(bowlX + 15f * u, bowlY),
                                    strokeWidth = 2.4f * u,
                                    cap = StrokeCap.Round
                                )
                                // Articulated mechanical arm lifting chopsticks
                                val noodleLift = abs(sin(actionPhase * 2f)) * 5f * u
                                drawLine(
                                    color = Color(0xFF283A52),
                                    start = Offset(toyX + bodyWidth / 2f, toyCenterY),
                                    end = Offset(toyX + 22f * u, toyCenterY + 2f * u),
                                    strokeWidth = 3.6f * u,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(pageAccent, radius = 2.8f * u, center = Offset(toyX + 22f * u, toyCenterY + 2f * u))
                                drawLine(
                                    color = KilljoyGold,
                                    start = Offset(toyX + 22f * u, toyCenterY + 2f * u),
                                    end = Offset(bowlX, bowlY - 4f * u - noodleLift),
                                    strokeWidth = 2.2f * u,
                                    cap = StrokeCap.Round
                                )
                                // Rising thermal steam waves
                                for (s in -1..1) {
                                    val sx = bowlX + s * 6f * u + sin(actionPhase + s) * 2f * u
                                    drawLine(
                                        color = pageAccent.copy(alpha = 0.75f),
                                        start = Offset(sx, bowlY - 5f * u),
                                        end = Offset(sx, bowlY - 16f * u),
                                        strokeWidth = 2f * u,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            AiToyActivity.GYM -> {
                                // Combat power sweatband across forehead
                                drawRoundRect(
                                    color = ValorantCrimson,
                                    topLeft = Offset(toyX - bodyWidth / 2f + 2f * u, toyCenterY - bodyHeight / 2f + 4f * u),
                                    size = Size(bodyWidth - 4f * u, 4.5f * u),
                                    cornerRadius = CornerRadius(2f * u, 2f * u)
                                )

                                // Determined Mecha Power Optics
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX - 14f * u, toyCenterY - 7f * u),
                                    size = Size(10.5f * u, 8.5f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX + 3.5f * u, toyCenterY - 7f * u),
                                    size = Size(10.5f * u, 8.5f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )

                                // Energetic grit smile
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.9f),
                                    topLeft = Offset(toyX - 6f * u, toyCenterY + 3.5f * u),
                                    size = Size(12f * u, 4f * u),
                                    cornerRadius = CornerRadius(2f * u, 2f * u)
                                )

                                // Overhead / Bicep Barbell Curl with Hydraulic Arm Servos
                                val curlLift = sin(actionPhase * 2.5f) * 9f * u
                                val barY = toyCenterY - 4f * u - curlLift
                                val leftPlateX = toyX - bodyWidth / 2f - 16f * u
                                val rightPlateX = toyX + bodyWidth / 2f + 16f * u

                                // Articulated Arms holding barbell
                                drawLine(
                                    color = Color(0xFF283A52),
                                    start = Offset(toyX - bodyWidth / 2f, toyCenterY),
                                    end = Offset(leftPlateX + 10f * u, barY),
                                    strokeWidth = 3.6f * u,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color(0xFF283A52),
                                    start = Offset(toyX + bodyWidth / 2f, toyCenterY),
                                    end = Offset(rightPlateX - 10f * u, barY),
                                    strokeWidth = 3.6f * u,
                                    cap = StrokeCap.Round
                                )

                                // Barbell shaft
                                drawLine(
                                    color = Color(0xFFCBD5E1),
                                    start = Offset(leftPlateX - 4f * u, barY),
                                    end = Offset(rightPlateX + 4f * u, barY),
                                    strokeWidth = 2.8f * u,
                                    cap = StrokeCap.Round
                                )

                                // Left & Right Heavy Gym Plates
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(leftPlateX - 3f * u, barY - 8f * u),
                                    size = Size(5f * u, 16f * u),
                                    cornerRadius = CornerRadius(2f * u, 2f * u)
                                )
                                drawRoundRect(
                                    color = KilljoyGold,
                                    topLeft = Offset(leftPlateX + 2.5f * u, barY - 6f * u),
                                    size = Size(4f * u, 12f * u),
                                    cornerRadius = CornerRadius(1.5f * u, 1.5f * u)
                                )
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(rightPlateX - 2f * u, barY - 8f * u),
                                    size = Size(5f * u, 16f * u),
                                    cornerRadius = CornerRadius(2f * u, 2f * u)
                                )
                                drawRoundRect(
                                    color = KilljoyGold,
                                    topLeft = Offset(rightPlateX - 6.5f * u, barY - 6f * u),
                                    size = Size(4f * u, 12f * u),
                                    cornerRadius = CornerRadius(1.5f * u, 1.5f * u)
                                )

                                // Hydraulic steam vents / sparks flying upward
                                for (sp in 0..2) {
                                    val spProg = (slowProgress + sp * 0.33f) % 1f
                                    val spX = toyX + (if (sp % 2 == 0) -1 else 1) * (28f + sp * 6f) * u
                                    val spY = (toyCenterY - 10f * u) - spProg * 20f * u
                                    drawCircle(
                                        color = RadianiteCyan.copy(alpha = (1f - spProg).coerceIn(0.2f, 0.9f)),
                                        radius = 2.4f * u,
                                        center = Offset(spX, spY)
                                    )
                                }
                            }

                            AiToyActivity.WORKING -> {
                                // Focused terminal optics
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX - 14f * u, toyCenterY - 8f * u),
                                    size = Size(10f * u, 8.5f * u),
                                    cornerRadius = CornerRadius(2.8f * u, 2.8f * u)
                                )
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX + 4f * u, toyCenterY - 8f * u),
                                    size = Size(10f * u, 8.5f * u),
                                    cornerRadius = CornerRadius(2.8f * u, 2.8f * u)
                                )
                                drawLine(
                                    color = pageAccent,
                                    start = Offset(toyX - 4f * u, toyCenterY + 4f * u),
                                    end = Offset(toyX + 4f * u, toyCenterY + 4f * u),
                                    strokeWidth = 2f * u,
                                    cap = StrokeCap.Round
                                )

                                // Holographic Workstation Desk & 3D Screen Projection
                                val deskX = toyX + 38f * u
                                val deskY = floorY - 4f * u
                                drawLine(
                                    color = pageAccent,
                                    start = Offset(deskX - 14f * u, deskY),
                                    end = Offset(deskX + 11f * u, deskY),
                                    strokeWidth = 3.2f * u,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = pageAccent,
                                    start = Offset(deskX + 8f * u, deskY),
                                    end = Offset(deskX + 15f * u, deskY - 19f * u),
                                    strokeWidth = 3.2f * u,
                                    cap = StrokeCap.Round
                                )
                                // Hologram projection grid
                                drawLine(
                                    color = RadianiteCyan.copy(alpha = 0.45f),
                                    start = Offset(deskX - 4f * u, deskY - 14f * u),
                                    end = Offset(deskX + 9f * u, deskY - 14f * u),
                                    strokeWidth = 2f * u,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = ValorantCrimson.copy(alpha = 0.55f),
                                    start = Offset(deskX - 2f * u, deskY - 9f * u),
                                    end = Offset(deskX + 8f * u, deskY - 9f * u),
                                    strokeWidth = 2f * u,
                                    cap = StrokeCap.Round
                                )
                                // Fast mechanical typing hands with servo wrist
                                val handBob = sin(actionPhase * 3.5f) * 3f * u
                                drawLine(
                                    color = Color(0xFF283A52),
                                    start = Offset(toyX + bodyWidth / 2f, toyCenterY),
                                    end = Offset(toyX + 22f * u, deskY - 6f * u + handBob),
                                    strokeWidth = 3.2f * u,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = pageAccent,
                                    radius = 3.6f * u,
                                    center = Offset(toyX + 22f * u, deskY - 6f * u + handBob)
                                )
                                // Floating binary code orbs
                                for (c in 0..2) {
                                    val cp = (slowProgress + c * 0.33f) % 1f
                                    drawCircle(
                                        color = RadianiteCyan.copy(alpha = 1f - cp),
                                        radius = 2.6f * u,
                                        center = Offset(deskX + (c - 1) * 7f * u, (deskY - 18f * u) - cp * 22f * u)
                                    )
                                }
                            }

                            AiToyActivity.BUSY_MEETING -> {
                                // Meeting cyber optics + tactical comm headset with animated waveform
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX - 14f * u, toyCenterY - 8f * u),
                                    size = Size(10f * u, 9.5f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX + 4f * u, toyCenterY - 8f * u),
                                    size = Size(10f * u, 9.5f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )
                                val talkH = (2f + abs(sin(actionPhase * 3f)) * 4.5f) * u
                                drawRoundRect(
                                    color = RadianiteCyan,
                                    topLeft = Offset(toyX - 5f * u, toyCenterY + 4f * u - talkH / 2f),
                                    size = Size(10f * u, talkH),
                                    cornerRadius = CornerRadius(2f * u, 2f * u)
                                )

                                // Headset frame & earcups
                                drawArc(
                                    color = RadianiteCyan,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(toyX - bodyWidth / 2f - 3f * u, toyCenterY - bodyHeight / 2f - 4f * u),
                                    size = Size(bodyWidth + 6f * u, bodyHeight),
                                    style = Stroke(width = 3.2f * u)
                                )
                                drawRoundRect(
                                    color = RadianiteCyan,
                                    topLeft = Offset(toyX - bodyWidth / 2f - 6.5f * u, toyCenterY - 7f * u),
                                    size = Size(6.5f * u, 15f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )
                                drawRoundRect(
                                    color = RadianiteCyan,
                                    topLeft = Offset(toyX + bodyWidth / 2f, toyCenterY - 7f * u),
                                    size = Size(6.5f * u, 15f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )
                                // Mic boom + pulsing red ON-AIR broadcast indicator
                                drawLine(
                                    color = RadianiteCyan,
                                    start = Offset(toyX + bodyWidth / 2f + 2f * u, toyCenterY + 4f * u),
                                    end = Offset(toyX + 9f * u, toyCenterY + 9f * u),
                                    strokeWidth = 2.4f * u
                                )
                                drawCircle(
                                    color = ValorantCrimson,
                                    radius = (3.4f + abs(sin(actionPhase)) * 2f) * u,
                                    center = Offset(toyX + 9f * u, toyCenterY + 9f * u)
                                )

                                // Audio visualizer bars
                                for (bar in 0..3) {
                                    val barH = (5f + abs(sin(actionPhase * 2f + bar)) * 12f) * u
                                    val bx = toyX - bodyWidth / 2f - (14f + bar * 5f) * u
                                    drawLine(
                                        color = ValorantCrimson.copy(alpha = 0.85f),
                                        start = Offset(bx, toyCenterY + barH / 2f),
                                        end = Offset(bx, toyCenterY - barH / 2f),
                                        strokeWidth = 2.6f * u,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            AiToyActivity.ALARM -> {
                                // Twin-bell cyber alert domes
                                val bellTopY = toyCenterY - bodyHeight / 2f - 6f * u
                                val leftBellX = toyX - 18f * u
                                val rightBellX = toyX + 18f * u

                                drawArc(
                                    color = pageAccent,
                                    startAngle = 195f,
                                    sweepAngle = 150f,
                                    useCenter = true,
                                    topLeft = Offset(leftBellX - 10f * u, bellTopY - 8f * u),
                                    size = Size(20f * u, 16f * u)
                                )
                                drawArc(
                                    color = pageAccent,
                                    startAngle = 195f,
                                    sweepAngle = 150f,
                                    useCenter = true,
                                    topLeft = Offset(rightBellX - 10f * u, bellTopY - 8f * u),
                                    size = Size(20f * u, 16f * u)
                                )

                                // Oscillating hammer
                                val hammerSwing = sin(actionPhase * 8f) * 9f * u
                                drawLine(
                                    color = KilljoyGold,
                                    start = Offset(toyX, toyCenterY - bodyHeight / 2f),
                                    end = Offset(toyX + hammerSwing, bellTopY - 9f * u),
                                    strokeWidth = 3f * u,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = KilljoyGold,
                                    radius = 4f * u,
                                    center = Offset(toyX + hammerSwing, bellTopY - 9f * u)
                                )

                                // Flashing alert eyes
                                val eyeFlashColor = if (sin(actionPhase * 4f) > 0f) pageAccent else KilljoyGold
                                drawCircle(
                                    color = eyeFlashColor,
                                    radius = 5.5f * u,
                                    center = Offset(toyX - 9f * u, toyCenterY - 4f * u)
                                )
                                drawCircle(
                                    color = eyeFlashColor,
                                    radius = 5.5f * u,
                                    center = Offset(toyX + 9f * u, toyCenterY - 4f * u)
                                )

                                // Ringing open mouth
                                val ringMouthH = (4f + abs(sin(actionPhase * 5f)) * 5f) * u
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(toyX - 6f * u, toyCenterY + 3f * u),
                                    size = Size(12f * u, ringMouthH),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )

                                // Pulsing sonic shockwaves
                                for (wave in 1..3) {
                                    val waveAlpha = ((1f - slowProgress) * (1f / wave)).coerceIn(0.18f, 0.9f)
                                    val waveOffset = (26f + wave * 8f + sin(actionPhase * 4f) * 2f) * u
                                    drawArc(
                                        color = pageAccent.copy(alpha = waveAlpha),
                                        startAngle = 135f,
                                        sweepAngle = 90f,
                                        useCenter = false,
                                        topLeft = Offset(toyX - waveOffset, toyCenterY - 18f * u),
                                        size = Size(waveOffset * 2f, 36f * u),
                                        style = Stroke(width = 2.4f * u, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = pageAccent.copy(alpha = waveAlpha),
                                        startAngle = -45f,
                                        sweepAngle = 90f,
                                        useCenter = false,
                                        topLeft = Offset(toyX - waveOffset, toyCenterY - 18f * u),
                                        size = Size(waveOffset * 2f, 36f * u),
                                        style = Stroke(width = 2.4f * u, cap = StrokeCap.Round)
                                    )
                                }
                            }

                            AiToyActivity.TIMER -> {
                                // Orbital Countdown Ring
                                val ringRadius = 35f * u
                                val timerProgress = if (chronoState.timerTotalSeconds > 0 &&
                                    (chronoState.isTimerRunning || chronoState.isTimerRinging)
                                ) {
                                    (chronoState.timerRemainingSeconds.toFloat() / chronoState.timerTotalSeconds.toFloat())
                                        .coerceIn(0.05f, 1f)
                                } else {
                                    (1f - slowProgress).coerceIn(0.1f, 1f)
                                }

                                drawCircle(
                                    color = pageAccent.copy(alpha = 0.22f),
                                    radius = ringRadius,
                                    center = Offset(toyX, toyCenterY),
                                    style = Stroke(width = 3f * u)
                                )
                                drawArc(
                                    color = if (chronoState.isTimerRinging) ValorantCrimson else pageAccent,
                                    startAngle = -90f,
                                    sweepAngle = timerProgress * 360f,
                                    useCenter = false,
                                    topLeft = Offset(toyX - ringRadius, toyCenterY - ringRadius),
                                    size = Size(ringRadius * 2f, ringRadius * 2f),
                                    style = Stroke(width = 3.5f * u, cap = StrokeCap.Round)
                                )

                                // Chrono Optics
                                val tickShift = cos(actionPhase * 2f) * 2.5f * u
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX - 13f * u + tickShift, toyCenterY - 8f * u),
                                    size = Size(9.5f * u, 9f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX + 3.5f * u + tickShift, toyCenterY - 8f * u),
                                    size = Size(9.5f * u, 9f * u),
                                    cornerRadius = CornerRadius(3f * u, 3f * u)
                                )
                                drawLine(
                                    color = KilljoyGold,
                                    start = Offset(toyX - 5f * u, toyCenterY + 4f * u),
                                    end = Offset(toyX + 5f * u, toyCenterY + 4f * u),
                                    strokeWidth = 2.4f * u,
                                    cap = StrokeCap.Round
                                )

                                // Holographic Hourglass
                                val hgX = toyX + 42f * u
                                val hgTop = toyCenterY - 12f * u
                                val hgBot = toyCenterY + 12f * u
                                drawLine(KilljoyGold, Offset(hgX - 8f * u, hgTop), Offset(hgX + 8f * u, hgTop), 2.5f * u, StrokeCap.Round)
                                drawLine(KilljoyGold, Offset(hgX - 8f * u, hgBot), Offset(hgX + 8f * u, hgBot), 2.5f * u, StrokeCap.Round)
                                drawLine(pageAccent, Offset(hgX - 7f * u, hgTop), Offset(hgX + 7f * u, hgBot), 2f * u, StrokeCap.Round)
                                drawLine(pageAccent, Offset(hgX + 7f * u, hgTop), Offset(hgX - 7f * u, hgBot), 2f * u, StrokeCap.Round)
                                val sandY = hgTop + 4f * u + (slowProgress * (hgBot - hgTop - 8f * u))
                                drawCircle(KilljoyGold, radius = 2.2f * u, center = Offset(hgX, sandY))
                            }

                            AiToyActivity.PANDORA_TIMER -> {
                                // 4 Orbiting Focus Crystals
                                val orbitRadius = 36f * u
                                for (node in 0 until 4) {
                                    val angle = actionPhase + (node * (PI / 2.0)).toFloat()
                                    val nx = toyX + cos(angle) * orbitRadius
                                    val ny = toyCenterY + sin(angle) * (orbitRadius * 0.65f)
                                    val nodeColor = if (node % 2 == 0) pageAccent else ChargingEmerald
                                    drawCircle(
                                        color = nodeColor.copy(alpha = 0.35f),
                                        radius = 5.5f * u,
                                        center = Offset(nx, ny)
                                    )
                                    drawCircle(
                                        color = nodeColor,
                                        radius = 3.2f * u,
                                        center = Offset(nx, ny)
                                    )
                                }

                                // Glowing crown gem
                                drawCircle(
                                    color = ChargingEmerald,
                                    radius = 3.6f * u,
                                    center = Offset(toyX, toyCenterY - bodyHeight / 2f + 5.5f * u)
                                )

                                // Zen focus optics
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX - 14f * u, toyCenterY - 7f * u),
                                    size = Size(10f * u, 8.5f * u),
                                    cornerRadius = CornerRadius(4f * u, 4f * u)
                                )
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX + 4f * u, toyCenterY - 7f * u),
                                    size = Size(10f * u, 8.5f * u),
                                    cornerRadius = CornerRadius(4f * u, 4f * u)
                                )
                                drawArc(
                                    color = ChargingEmerald,
                                    startAngle = 20f,
                                    sweepAngle = 140f,
                                    useCenter = false,
                                    topLeft = Offset(toyX - 5f * u, toyCenterY + 1.5f * u),
                                    size = Size(10f * u, 5.5f * u),
                                    style = Stroke(width = 2.2f * u, cap = StrokeCap.Round)
                                )
                            }

                            AiToyActivity.ROAMING -> {
                                // Dynamic looking cyber optics tracking movement
                                val lookShift = roamWave * 3.8f * u
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX - 14f * u + lookShift, toyCenterY - 9f * u),
                                    size = Size(10f * u, 10.5f * u),
                                    cornerRadius = CornerRadius(3.5f * u, 3.5f * u)
                                )
                                drawRoundRect(
                                    color = pageAccent,
                                    topLeft = Offset(toyX + 4f * u + lookShift, toyCenterY - 9f * u),
                                    size = Size(10f * u, 10.5f * u),
                                    cornerRadius = CornerRadius(3.5f * u, 3.5f * u)
                                )
                                // Happy cyber visor smile
                                drawArc(
                                    color = pageAccent,
                                    startAngle = 20f,
                                    sweepAngle = 140f,
                                    useCenter = false,
                                    topLeft = Offset(toyX - 5f * u + lookShift, toyCenterY + 1.5f * u),
                                    size = Size(10f * u, 6.5f * u),
                                    style = Stroke(width = 2.4f * u, cap = StrokeCap.Round)
                                )

                                // Articulated mechanical waving arms with elbow servo hubs
                                val rightWaveY = toyCenterY - 7f * u + sin(actionPhase * 2f) * 6.5f * u
                                val leftWaveY = toyCenterY + 2f * u - sin(actionPhase * 2f) * 5f * u

                                // Right arm: shoulder -> elbow servo -> forearm claw
                                val rightElbowX = toyX + bodyWidth / 2f + 6f * u
                                val rightElbowY = (toyCenterY + rightWaveY) / 2f
                                drawLine(
                                    color = Color(0xFF283A52),
                                    start = Offset(toyX + bodyWidth / 2f, toyCenterY),
                                    end = Offset(rightElbowX, rightElbowY),
                                    strokeWidth = 3.8f * u,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(pageAccent, radius = 2.8f * u, center = Offset(rightElbowX, rightElbowY))
                                drawLine(
                                    color = pageAccent,
                                    start = Offset(rightElbowX, rightElbowY),
                                    end = Offset(toyX + bodyWidth / 2f + 13f * u, rightWaveY),
                                    strokeWidth = 3.6f * u,
                                    cap = StrokeCap.Round
                                )
                                // Right mechanical gripper fingers
                                drawCircle(pageAccent, radius = 2.4f * u, center = Offset(toyX + bodyWidth / 2f + 13f * u, rightWaveY))

                                // Left arm: shoulder -> elbow servo -> forearm claw
                                val leftElbowX = toyX - bodyWidth / 2f - 6f * u
                                val leftElbowY = (toyCenterY + leftWaveY) / 2f
                                drawLine(
                                    color = Color(0xFF283A52),
                                    start = Offset(toyX - bodyWidth / 2f, toyCenterY),
                                    end = Offset(leftElbowX, leftElbowY),
                                    strokeWidth = 3.8f * u,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(pageAccent, radius = 2.8f * u, center = Offset(leftElbowX, leftElbowY))
                                drawLine(
                                    color = pageAccent,
                                    start = Offset(leftElbowX, leftElbowY),
                                    end = Offset(toyX - bodyWidth / 2f - 12f * u, leftWaveY),
                                    strokeWidth = 3.6f * u,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(pageAccent, radius = 2.4f * u, center = Offset(toyX - bodyWidth / 2f - 12f * u, leftWaveY))
                            }
                        }

                        // Eyelids: a quick visor-coloured shutter over the optics band for blinks.
                        if (isAwakeFace && blinkAmount > 0.01f) {
                            val lidHalfW = visorW / 2f - 1.5f * u
                            val eyeTop = toyCenterY - 10f * u
                            val eyeBottom = toyCenterY + 1.8f * u
                            val lidH = (eyeBottom - eyeTop) / 2f * blinkAmount
                            val lidColor = Color(0xFF03070E)
                            drawRect(
                                color = lidColor,
                                topLeft = Offset(toyX - lidHalfW, eyeTop),
                                size = Size(lidHalfW * 2f, lidH)
                            )
                            drawRect(
                                color = lidColor,
                                topLeft = Offset(toyX - lidHalfW, eyeBottom - lidH),
                                size = Size(lidHalfW * 2f, lidH)
                            )
                        }
                        } // end gaze translate
                    }

                    } // end robot (non-cat) branch

                    // --- COMPANION-SCALED DESK WATER BOTTLE PROP ON STAGE FLOOR ---
                    if (wellnessEnabled) {
                        drawCompanionWaterBottleProp(
                            stageWidth = w,
                            floorY = floorY,
                            u = u,
                            fillFraction = waterBottleFillState.fillFraction,
                            wavePhase = actionPhase,
                            isGoalMet = waterToday >= waterGoal,
                            goalMetProgress = waterBottleFillState.goalMetProgress,
                            sloshIntensity = waterBottleFillState.sloshIntensity,
                            waterGoal = waterGoal
                        )
                    }

                    // --- EMOTION PARTICLES (Rendered un-distorted in canvas space) ---
                    for (particle in emotionParticles) {
                        val px = particleAnchorX + particle.x * u
                        val py = toyCenterY + particle.y * u
                        if (particle.isHeart) {
                            drawNeonHeart(
                                drawScope = this,
                                center = Offset(px, py),
                                size = 14f * u * particle.scale,
                                color = particle.color,
                                alpha = particle.alpha
                            )
                        } else {
                            drawNeonSpark(
                                drawScope = this,
                                center = Offset(px, py),
                                radius = 8f * u * particle.scale,
                                color = particle.color,
                                alpha = particle.alpha
                            )
                        }
                    }
                }
            }
        }

        // Companion-scaled interactive Water Bottle & Break controls on the stage floor (left side)
        if (wellnessEnabled && dockViewModel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val waterAccent = if (waterToday >= waterGoal) ChargingEmerald else Color(0xFF38BDF8)
                val canDecrementWater = waterToday > 0
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF0A131E).copy(alpha = 0.86f),
                    modifier = Modifier
                        .border(
                            width = 0.9.dp,
                            color = waterAccent.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clip(RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = canDecrementWater) {
                                    haptics.click()
                                    dockViewModel.decrementWater()
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                .semantics {
                                    contentDescription = "Decrease water count"
                                }
                                .testTag("companion_water_minus_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = waterAccent.copy(alpha = if (canDecrementWater) 0.95f else 0.35f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    haptics.chirp()
                                    dockViewModel.logWellness("WATER")
                                    triggerToyJumpReaction(Color(0xFF38BDF8), Offset.Zero, 0f, 0f)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .semantics {
                                    contentDescription = "Companion water bottle: $waterToday of $waterGoal glasses drunk. Tap to log 1 glass."
                                }
                                .testTag("companion_water_bottle_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💧 $waterToday/$waterGoal",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = waterAccent
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.chirp()
                                    dockViewModel.logWellness("WATER")
                                    triggerToyJumpReaction(Color(0xFF38BDF8), Offset.Zero, 0f, 0f)
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                .semantics {
                                    contentDescription = "Increase water count"
                                }
                                .testTag("companion_water_plus_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = waterAccent
                            )
                        }
                    }
                }

                val canDecrementBreak = breaksToday > 0
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF0A131E).copy(alpha = 0.84f),
                    modifier = Modifier
                        .border(
                            width = 0.8.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clip(RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = canDecrementBreak) {
                                    haptics.click()
                                    dockViewModel.decrementBreak()
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                .semantics {
                                    contentDescription = "Decrease break count"
                                }
                                .testTag("companion_break_minus_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFD6DEE7).copy(alpha = if (canDecrementBreak) 0.92f else 0.35f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    haptics.click()
                                    dockViewModel.openWellnessSheet()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .semantics {
                                    contentDescription = "Breaks today: $breaksToday. Tap to open Wellness sheet."
                                }
                                .testTag("companion_break_badge_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🧘 $breaksToday",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = Color(0xFFD6DEE7)
                            )
                            if (top3Total > 0) {
                                Text(
                                    text = "· ✓$top3Done/$top3Total",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    color = KilljoyGold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.chirp()
                                    dockViewModel.logWellness("STRETCH")
                                    triggerToyJumpReaction(ChargingEmerald, Offset.Zero, 0f, 0f)
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                .semantics {
                                    contentDescription = "Increase break count"
                                }
                                .testTag("companion_break_plus_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ChargingEmerald
                            )
                        }
                    }
                }
            }
        }

        // Interactive SNOOZE & STOP Overlay for Alarm, Timer, and Pandora Timer directly on the animation!
        val currentDisplayedActivity = activities[pagerState.currentPage % activities.size]
        val isChronoPage = currentDisplayedActivity == AiToyActivity.ALARM ||
            currentDisplayedActivity == AiToyActivity.TIMER ||
            currentDisplayedActivity == AiToyActivity.PANDORA_TIMER

        if (chronoState.hasAnyActiveOrRinging || isChronoPage) {
            val activeChronoTarget = when {
                chronoState.isAlarmRinging || chronoState.snoozedAlarmUntilMillis != null -> AiToyActivity.ALARM
                chronoState.isTimerRinging || chronoState.isTimerRunning -> AiToyActivity.TIMER
                chronoState.isPandoraRinging || chronoState.isPandoraRunning -> AiToyActivity.PANDORA_TIMER
                else -> currentDisplayedActivity
            }

            val statusReadout = when (activeChronoTarget) {
                AiToyActivity.ALARM -> {
                    when {
                        chronoState.isAlarmRinging -> "ALARM ${chronoState.ringingAlarmTimeText}"
                        chronoState.snoozedAlarmUntilMillis != null -> {
                            val rem = ((chronoState.snoozedAlarmUntilMillis - currentTimeMillis) / 1000L).toInt().coerceAtLeast(0)
                            "SNOOZED ${formatSecondsMmSs(rem)}"
                        }
                        else -> "ALARM ${chronoState.ringingAlarmTimeText}"
                    }
                }
                AiToyActivity.TIMER -> {
                    val secs = if (chronoState.isTimerRunning || chronoState.isTimerRinging) {
                        chronoState.timerRemainingSeconds
                    } else {
                        chronoState.timerTotalSeconds
                    }
                    if (chronoState.isTimerRinging) "TIMER 00:00!" else "TIMER ${formatSecondsMmSs(secs)}"
                }
                else -> {
                    val secs = if (chronoState.isPandoraRunning || chronoState.isPandoraRinging) {
                        chronoState.pandoraRemainingSeconds
                    } else {
                        chronoState.pandoraTotalSeconds
                    }
                    "FOCUS ${formatSecondsMmSs(secs)}"
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF08101A).copy(alpha = 0.90f))
                    .border(
                        width = 1.dp,
                        color = activeChronoTarget.accentColor.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("anim_chrono_controls_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = statusReadout,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = activeChronoTarget.accentColor
                )

                if (!chronoState.hasAnyActiveOrRinging) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ChargingEmerald.copy(alpha = 0.22f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onStartQuickChrono(activeChronoTarget) }
                            .testTag("anim_start_chrono_button")
                    ) {
                        Text(
                            text = if (activeChronoTarget == AiToyActivity.ALARM) "RING" else "START",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = ChargingEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = KilljoyGold.copy(alpha = 0.22f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSnoozeChrono(activeChronoTarget) }
                        .testTag("anim_snooze_button")
                ) {
                    Text(
                        text = "SNOOZE",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = KilljoyGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ValorantCrimson.copy(alpha = 0.25f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onStopChrono(activeChronoTarget) }
                        .testTag("anim_stop_button")
                ) {
                    Text(
                        text = "STOP",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = ValorantCrimson,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Legacy/inline visor alias so any callers of AnkerNanoBotVisor continue to work seamlessly.
 */
@Composable
fun AnkerNanoBotVisor(
    isCharging: Boolean,
    isAgentThinking: Boolean,
    turboPreview: Boolean,
    modifier: Modifier = Modifier
) {
    var manualMood by remember { mutableIntStateOf(0) }
    val activities = AiToyActivity.entries
    val act = when {
        isAgentThinking -> AiToyActivity.WORKING
        turboPreview -> AiToyActivity.ROAMING
        else -> activities[manualMood % activities.size]
    }
    InteractiveAiToyTopStage(
        currentTimeMillis = System.currentTimeMillis(),
        schedule = emptyList(),
        isAgentThinking = isAgentThinking,
        manualActivity = act,
        onSelectManualActivity = { chosen ->
            if (chosen != null) manualMood = activities.indexOf(chosen).coerceAtLeast(0)
        },
        onQuickAddCalendarStatus = {},
        modifier = modifier
    )
}

@Composable
fun AnkerPowerFlowTelemetryBar(
    battery: BatteryTelemetry,
    turboPreview: Boolean,
    onToggleTurboPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "anker_10in1_flow")

    val particlePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (turboPreview) 650 else if (battery.isCharging) 1100 else 2200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_phase"
    )

    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_wave_phase"
    )

    val rawWatts = ((battery.voltageMv / 1000f) * (battery.currentMa / 1000f)).let {
        if (it < 0.5f) {
            if (battery.isCharging) 27.0f else 3.2f
        } else {
            it.coerceAtMost(85f)
        }
    }
    val targetWatts = if (turboPreview) 85.0f else rawWatts
    val animatedWatts by animateFloatAsState(
        targetValue = targetWatts,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "animated_watts"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val flowColor = if (turboPreview || battery.isCharging) RadianiteCyan else primaryColor

    Surface(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 6.dp),
        color = Color(0xFF070C12),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                color = flowColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 6.dp)
            )
            .clickable { onToggleTurboPreview() }
            .testTag("anker_power_flow_bar")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HubPortStatusPill(
                    icon = Icons.Default.Bolt,
                    label = if (turboPreview) "PD 100W->85W" else "PD ${"%.1f".format(animatedWatts)}W",
                    activeColor = flowColor
                )
                HubPortStatusPill(
                    icon = Icons.Default.Monitor,
                    label = "2K@240Hz DP",
                    activeColor = ValorantCrimson
                )
                HubPortStatusPill(
                    icon = Icons.Default.Usb,
                    label = "USB-C 5Gbps",
                    activeColor = KilljoyGold
                )
                HubPortStatusPill(
                    icon = Icons.Default.Router,
                    label = "1Gbps LAN",
                    activeColor = ChargingEmerald
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidBatteryTank(
                    levelPercent = battery.levelPercent,
                    wavePhase = wavePhase,
                    accentColor = flowColor,
                    modifier = Modifier.size(width = 72.dp, height = 64.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val u = h / 50f
                        val startX = 4f * u
                        val endX = w - 4f * u
                        val topY = h * 0.22f
                        val midY = h * 0.50f
                        val botY = h * 0.78f

                        val trackColor = flowColor.copy(alpha = 0.18f)
                        drawLine(trackColor, Offset(startX, topY), Offset(endX, topY), strokeWidth = 3.2f * u, cap = StrokeCap.Round)
                        drawLine(trackColor, Offset(startX, midY), Offset(endX, midY), strokeWidth = 4.4f * u, cap = StrokeCap.Round)
                        drawLine(trackColor, Offset(startX, botY), Offset(endX, botY), strokeWidth = 3.2f * u, cap = StrokeCap.Round)

                        val dashOffset = (1f - particlePhase) * 44f * u
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f * u, 14f * u), dashOffset)

                        drawLine(
                            color = ValorantCrimson.copy(alpha = 0.9f),
                            start = Offset(startX, topY),
                            end = Offset(endX, topY),
                            strokeWidth = 3.2f * u,
                            cap = StrokeCap.Round,
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = flowColor,
                            start = Offset(startX, midY),
                            end = Offset(endX, midY),
                            strokeWidth = 4.4f * u,
                            cap = StrokeCap.Round,
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = KilljoyGold.copy(alpha = 0.9f),
                            start = Offset(startX, botY),
                            end = Offset(endX, botY),
                            strokeWidth = 3.2f * u,
                            cap = StrokeCap.Round,
                            pathEffect = dashEffect
                        )

                        val orbX1 = startX + ((particlePhase + 0.2f) % 1f) * (endX - startX)
                        val orbX2 = startX + (particlePhase % 1f) * (endX - startX)
                        val orbX3 = startX + ((particlePhase + 0.6f) % 1f) * (endX - startX)

                        drawCircle(color = ValorantCrimson.copy(alpha = 0.35f), radius = 8f * u, center = Offset(orbX1, topY))
                        drawCircle(color = ValorantCrimson, radius = 4.2f * u, center = Offset(orbX1, topY))

                        drawCircle(color = flowColor.copy(alpha = 0.38f), radius = 9.5f * u, center = Offset(orbX2, midY))
                        drawCircle(color = flowColor, radius = 5.2f * u, center = Offset(orbX2, midY))

                        drawCircle(color = KilljoyGold.copy(alpha = 0.35f), radius = 8f * u, center = Offset(orbX3, botY))
                        drawCircle(color = KilljoyGold, radius = 4.2f * u, center = Offset(orbX3, botY))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%.1f".format(animatedWatts),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = flowColor
                        )
                        Text(
                            text = "W",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = flowColor,
                            modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                        )
                    }
                    Text(
                        text = "${"%.1f".format(battery.voltageMv / 1000f)}V • ${"%.0f".format(battery.temperatureCelsius)}°C",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HubPortStatusPill(
    icon: ImageVector,
    label: String,
    activeColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = activeColor.copy(alpha = 0.12f),
        modifier = Modifier.border(
            width = 0.8.dp,
            color = activeColor.copy(alpha = 0.4f),
            shape = RoundedCornerShape(6.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = activeColor
            )
        }
    }
}

@Composable
private fun LiquidBatteryTank(
    levelPercent: Int,
    wavePhase: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B131D))
            .border(
                width = 1.4.dp,
                color = accentColor.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val fillRatio = (levelPercent / 100f).coerceIn(0.08f, 0.96f)
            val waterTopY = h * (1f - fillRatio)

            val clipRectPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = 0f,
                        right = w,
                        bottom = h,
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                )
            }

            clipPath(clipRectPath) {
                val wavePath = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, waterTopY)
                    val steps = 20
                    val stepX = w / steps
                    for (i in 0..steps) {
                        val x = i * stepX
                        val y = waterTopY + sin(wavePhase + (i * 0.4f)) * 4.dp.toPx()
                        lineTo(x, y)
                    }
                    lineTo(w, h)
                    close()
                }

                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.6f),
                            accentColor.copy(alpha = 0.22f)
                        ),
                        startY = waterTopY,
                        endY = h
                    )
                )
            }
        }

        Text(
            text = "$levelPercent%",
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White
        )
    }
}

/**
 * Smooth ease-out curve for rising/draining water levels inside the companion's desk water bottle.
 */
val WaterBottleSmoothFillEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

const val WATER_BOTTLE_FILL_ANIMATION_DURATION_MS = 680
const val WATER_BOTTLE_GOAL_COLOR_DURATION_MS = 520

/**
 * Snapshot of the companion desk water bottle's animated fill state.
 */
data class WaterBottleFillAnimationState(
    val fillFraction: Float,
    val targetFillFraction: Float,
    val goalMetProgress: Float,
    val sloshIntensity: Float,
    val waterToday: Int,
    val waterGoal: Int
)

/**
 * Pure helper computing the normalized [0f..1f] water fill level inside the companion's water bottle.
 */
fun computeWaterBottleFillFraction(waterToday: Int, waterGoal: Int): Float {
    val safeGoal = waterGoal.coerceAtLeast(1)
    return (waterToday.coerceAtLeast(0).toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
}

/**
 * Computes a normalized [0f..1f] surface slosh agitation factor from the in-flight delta between
 * [animatedFill] and [targetFill]. Returns 0f when the liquid has settled at its target level.
 */
fun computeWaterBottleSloshIntensity(animatedFill: Float, targetFill: Float): Float {
    val delta = abs(targetFill.coerceIn(0f, 1f) - animatedFill.coerceIn(0f, 1f))
    return (delta * 6.5f).coerceIn(0f, 1f)
}

/**
 * Computes normalized (0f..1f) graduation tick positions along the companion's desk water bottle
 * relative to [waterGoal], capped to a clean readable density on the bottle wall.
 */
fun computeWaterBottleGoalTicks(waterGoal: Int): List<Float> {
    val safeGoal = waterGoal.coerceAtLeast(1)
    val divisions = when {
        safeGoal in 2..10 -> safeGoal
        else -> 4
    }
    return (1 until divisions).map { step -> step.toFloat() / divisions.toFloat() }
}

/**
 * Remembers and smoothly animates the companion desk water bottle fill level, goal color blend,
 * and surface slosh intensity whenever [waterToday] or [waterGoal] changes.
 */
@Composable
fun rememberWaterBottleFillAnimationState(
    waterToday: Int,
    waterGoal: Int
): WaterBottleFillAnimationState {
    val safeGoal = waterGoal.coerceAtLeast(1)
    val safeWater = waterToday.coerceAtLeast(0)
    val targetFill = computeWaterBottleFillFraction(safeWater, safeGoal)
    val animatedFill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(
            durationMillis = WATER_BOTTLE_FILL_ANIMATION_DURATION_MS,
            easing = WaterBottleSmoothFillEasing
        ),
        label = "companion_water_bottle_smooth_fill"
    )
    val targetGoalMet = if (safeWater >= safeGoal) 1f else 0f
    val animatedGoalMet by animateFloatAsState(
        targetValue = targetGoalMet,
        animationSpec = tween(
            durationMillis = WATER_BOTTLE_GOAL_COLOR_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "companion_water_bottle_goal_color"
    )
    val sloshIntensity = computeWaterBottleSloshIntensity(
        animatedFill = animatedFill,
        targetFill = targetFill
    )
    return WaterBottleFillAnimationState(
        fillFraction = animatedFill.coerceIn(0f, 1f),
        targetFillFraction = targetFill,
        goalMetProgress = animatedGoalMet.coerceIn(0f, 1f),
        sloshIntensity = sloshIntensity,
        waterToday = safeWater,
        waterGoal = safeGoal
    )
}

/**
 * Draws a companion-scaled desk water bottle on the stage floor beside the companion.
 * Scaled by the stage unit [u] so its size is always proportional to the companion in both
 * portrait and landscape layouts, with a smooth dual-wave meniscus, rising hydration bubbles,
 * and graduation ticks reflecting the user's daily water goal.
 */
private fun DrawScope.drawCompanionWaterBottleProp(
    stageWidth: Float,
    floorY: Float,
    u: Float,
    fillFraction: Float,
    wavePhase: Float,
    isGoalMet: Boolean,
    goalMetProgress: Float = if (isGoalMet) 1f else 0f,
    sloshIntensity: Float = 0f,
    waterGoal: Int = 8
) {
    val bottleCenterX = (stageWidth * 0.115f).coerceAtLeast(15f * u)
    val bottleW = 13.5f * u
    val bottleH = 23.5f * u
    val bottleBottomY = floorY - 1.2f * u
    val bottleTopY = bottleBottomY - bottleH
    val bottleLeft = bottleCenterX - bottleW * 0.5f
    val bottleCorner = 3.6f * u

    val clampedGoalBlend = goalMetProgress.coerceIn(0f, 1f)
    val liquidTopColor = lerp(Color(0xFF38BDF8), Color(0xFF34D399), clampedGoalBlend)
    val liquidBottomColor = lerp(Color(0xFF0284C7), Color(0xFF059669), clampedGoalBlend)
    val liquidBackWaveColor = lerp(Color(0xFF0EA5E9), Color(0xFF10B981), clampedGoalBlend)
    val rimAccent = lerp(RadianiteCyan, ChargingEmerald, clampedGoalBlend)

    // Ground shadow on the stage floor matching the companion
    drawOval(
        color = Color.Black.copy(alpha = 0.48f),
        topLeft = Offset(bottleCenterX - bottleW * 0.58f, floorY - 2.0f * u),
        size = Size(bottleW * 1.16f, 4.0f * u)
    )

    // Bottle neck & screw cap
    val neckW = 6.2f * u
    val neckH = 2.4f * u
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(bottleCenterX - neckW * 0.5f, bottleTopY - neckH + 0.5f * u),
        size = Size(neckW, neckH),
        cornerRadius = CornerRadius(1.2f * u, 1.2f * u)
    )
    val capW = 8.4f * u
    val capH = 3.0f * u
    val capTopY = bottleTopY - neckH - capH + 1.0f * u
    drawRoundRect(
        color = Color(0xFF334155),
        topLeft = Offset(bottleCenterX - capW * 0.5f, capTopY),
        size = Size(capW, capH),
        cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
    )
    drawRoundRect(
        color = rimAccent.copy(alpha = 0.8f),
        topLeft = Offset(bottleCenterX - capW * 0.5f, capTopY),
        size = Size(capW, capH),
        cornerRadius = CornerRadius(1.6f * u, 1.6f * u),
        style = Stroke(width = 0.9f * u)
    )
    // Carry handle loop on top of cap
    drawArc(
        color = rimAccent.copy(alpha = 0.75f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(bottleCenterX - 2.8f * u, capTopY - 2.6f * u),
        size = Size(5.6f * u, 4.4f * u),
        style = Stroke(width = 1.1f * u, cap = StrokeCap.Round)
    )

    // Translucent frosted bottle body backdrop
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF162232).copy(alpha = 0.82f),
                Color(0xFF0B131E).copy(alpha = 0.90f)
            ),
            startY = bottleTopY,
            endY = bottleBottomY
        ),
        topLeft = Offset(bottleLeft, bottleTopY),
        size = Size(bottleW, bottleH),
        cornerRadius = CornerRadius(bottleCorner, bottleCorner)
    )

    // Animated water fill inside clipped bottle chamber
    val inset = 1.3f * u
    val innerLeft = bottleLeft + inset
    val innerTop = bottleTopY + inset
    val innerW = bottleW - inset * 2f
    val innerH = bottleH - inset * 2f
    val innerBottom = innerTop + innerH
    val clampedFill = fillFraction.coerceIn(0f, 1f)
    val clampedSlosh = sloshIntensity.coerceIn(0f, 1f)

    if (clampedFill > 0.005f) {
        val chamberPath = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = innerLeft,
                    top = innerTop,
                    right = innerLeft + innerW,
                    bottom = innerBottom,
                    cornerRadius = CornerRadius(bottleCorner * 0.75f, bottleCorner * 0.75f)
                )
            )
        }
        val waterSurfaceY = innerBottom - innerH * clampedFill
        // Taper wave amplitude smoothly near empty (0%) and full (100%) chamber boundaries
        val edgeTaper = (4f * clampedFill * (1f - clampedFill)).coerceIn(0.18f, 1f)
        val primaryWaveAmp = (0.68f + clampedSlosh * 1.35f) * u * edgeTaper
        val secondaryWaveAmp = (0.52f + clampedSlosh * 1.05f) * u * edgeTaper
        val waveSpeed = wavePhase * (2.0f + clampedSlosh * 1.6f)

        clipPath(chamberPath) {
            // Secondary deeper back-wave for volumetric depth during fill transitions
            val backWavePath = Path().apply {
                moveTo(innerLeft, innerBottom)
                lineTo(innerLeft, waterSurfaceY)
                val segments = 10
                val stepX = innerW / segments
                for (i in 0..segments) {
                    val x = innerLeft + i * stepX
                    val waveY = waterSurfaceY - sin(waveSpeed + 1.6f + i * 0.65f) * secondaryWaveAmp
                    lineTo(x, waveY)
                }
                lineTo(innerLeft + innerW, innerBottom)
                close()
            }
            drawPath(
                path = backWavePath,
                color = liquidBackWaveColor.copy(alpha = 0.52f)
            )

            // Primary foreground liquid body with smooth sloshing surface
            val frontWavePath = Path().apply {
                moveTo(innerLeft, innerBottom)
                lineTo(innerLeft, waterSurfaceY)
                val segments = 10
                val stepX = innerW / segments
                for (i in 0..segments) {
                    val x = innerLeft + i * stepX
                    val waveY = waterSurfaceY + sin(waveSpeed + i * 0.7f) * primaryWaveAmp
                    lineTo(x, waveY)
                }
                lineTo(innerLeft + innerW, innerBottom)
                close()
            }
            drawPath(
                path = frontWavePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        liquidTopColor.copy(alpha = 0.94f),
                        liquidBottomColor.copy(alpha = 0.90f)
                    ),
                    startY = waterSurfaceY,
                    endY = innerBottom
                )
            )

            // Effervescent rising hydration bubbles inside the filled liquid column
            val filledColumnH = (innerBottom - waterSurfaceY).coerceAtLeast(0f)
            if (filledColumnH > 2.2f * u) {
                val bubbleCount = 4
                for (b in 0 until bubbleCount) {
                    val cycle = ((wavePhase * (0.55f + b * 0.14f + clampedSlosh * 0.45f)) + b * 0.27f) % 1f
                    val bubbleY = innerBottom - cycle * (filledColumnH - 0.8f * u)
                    val horizontalNormalized = 0.22f + ((b * 0.21f) % 0.62f)
                    val wobbleX = sin(wavePhase * 2.4f + b * 1.3f) * (0.45f * u)
                    val bubbleX = innerLeft + innerW * horizontalNormalized + wobbleX
                    val bubbleRadius = (0.48f + (b % 2) * 0.22f + clampedSlosh * 0.2f) * u
                    val bubbleAlpha = ((1f - cycle * 0.6f) * (0.38f + clampedSlosh * 0.35f)).coerceIn(0f, 0.8f)
                    drawCircle(
                        color = Color.White.copy(alpha = bubbleAlpha),
                        radius = bubbleRadius,
                        center = Offset(bubbleX, bubbleY)
                    )
                }
            }

            // Bright curved meniscus highlight crest along the surface wave
            val meniscusPath = Path().apply {
                val segments = 10
                val stepX = innerW / segments
                for (i in 0..segments) {
                    val x = innerLeft + i * stepX
                    val waveY = waterSurfaceY + sin(waveSpeed + i * 0.7f) * primaryWaveAmp
                    if (i == 0) moveTo(x, waveY) else lineTo(x, waveY)
                }
            }
            drawPath(
                path = meniscusPath,
                color = Color.White.copy(alpha = 0.72f + clampedSlosh * 0.18f),
                style = Stroke(width = 0.95f * u, cap = StrokeCap.Round)
            )
        }
    }

    // Graduation measurement ticks on the right side reflecting the user's daily water goal
    val goalTicks = computeWaterBottleGoalTicks(waterGoal)
    goalTicks.forEachIndexed { idx, tickFraction ->
        val tickY = innerBottom - innerH * tickFraction
        val isMidpoint = abs(tickFraction - 0.5f) < 0.04f
        val isReached = clampedFill + 0.005f >= tickFraction
        val tickLen = if (isMidpoint) 3.5f * u else 2.2f * u
        val tickColor = if (isReached) {
            rimAccent.copy(alpha = 0.82f)
        } else {
            Color.White.copy(alpha = 0.36f)
        }
        drawLine(
            color = tickColor,
            start = Offset(bottleLeft + bottleW - tickLen - 1.1f * u, tickY),
            end = Offset(bottleLeft + bottleW - 1.1f * u, tickY),
            strokeWidth = (if (isMidpoint) 0.9f else 0.72f) * u,
            cap = StrokeCap.Round
        )
    }

    // Left vertical glass specular reflection
    drawLine(
        color = Color.White.copy(alpha = 0.28f),
        start = Offset(bottleLeft + 2.2f * u, bottleTopY + 3.2f * u),
        end = Offset(bottleLeft + 2.2f * u, bottleBottomY - 3.2f * u),
        strokeWidth = 1.1f * u,
        cap = StrokeCap.Round
    )

    // Subtle outer glow when actively filling/draining or when the daily hydration goal is met
    val activeHaloAlpha = (clampedSlosh * 0.28f + clampedGoalBlend * 0.18f).coerceIn(0f, 0.42f)
    if (activeHaloAlpha > 0.01f) {
        drawRoundRect(
            color = rimAccent.copy(alpha = activeHaloAlpha),
            topLeft = Offset(bottleLeft - 0.6f * u, bottleTopY - 0.6f * u),
            size = Size(bottleW + 1.2f * u, bottleH + 1.2f * u),
            cornerRadius = CornerRadius(bottleCorner + 0.6f * u, bottleCorner + 0.6f * u),
            style = Stroke(width = 2.0f * u)
        )
    }

    // Crisp outer bottle outline
    drawRoundRect(
        color = rimAccent.copy(alpha = 0.85f),
        topLeft = Offset(bottleLeft, bottleTopY),
        size = Size(bottleW, bottleH),
        cornerRadius = CornerRadius(bottleCorner, bottleCorner),
        style = Stroke(width = 1.2f * u)
    )
}


