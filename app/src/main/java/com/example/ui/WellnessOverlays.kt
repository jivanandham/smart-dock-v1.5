package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wellness.MissedNudge
import com.example.wellness.Nudge
import com.example.wellness.NudgeType
import com.example.wellness.WellnessSettings
import com.example.wellness.WellnessStats
import com.example.wellness.formatWhileAwaySummary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Softer, slightly translucent palette so reminders remain visually secondary to the clock digits
private val SoftBubbleSurface = Color(0xFF10161E).copy(alpha = 0.84f)
private val SoftBubbleBorder = Color(0xFF8AB4CE).copy(alpha = 0.34f)
private val SoftWarmAccent = Color(0xFFD9A76A)
private val SoftAquaAccent = Color(0xFF7CC6D6)
private val SoftPrimaryText = Color(0xFFE3DDD5)
private val SoftSecondaryText = Color(0xFFB3ADA4)
private val NightDimDotColor = Color(0xFFD96B43).copy(alpha = 0.32f)

/**
 * Result of evaluating the protected clock bounds against the current screen height.
 */
data class ClockSafeStagePlacement(
    val safeStageHeightPx: Float,
    val canFitFullBubble: Boolean
)

/**
 * Rule 1: The clock is never covered.
 * Computes the vertical safe zone above the clock's top edge ([clockBounds].top) and determines
 * whether the full companion speech bubble fits without touching the clock digits, or if it
 * must shrink to the compact floating icon instead.
 */
internal fun computeClockSafeStagePlacement(
    windowHeightPx: Float,
    clockBounds: Rect,
    minFullBubbleHeightPx: Float
): ClockSafeStagePlacement {
    if (windowHeightPx <= 0f) {
        return ClockSafeStagePlacement(safeStageHeightPx = 0f, canFitFullBubble = false)
    }
    val hasValidClockRect = !clockBounds.isEmpty && clockBounds.top > 1f && clockBounds.top <= windowHeightPx
    val safeHeight = if (hasValidClockRect) {
        clockBounds.top.coerceIn(0f, windowHeightPx)
    } else {
        // Fallback before first layout pass: top 44% companion stage
        windowHeightPx * 0.44f
    }
    return ClockSafeStagePlacement(
        safeStageHeightPx = safeHeight,
        canFitFullBubble = safeHeight >= minFullBubbleHeightPx
    )
}

/**
 * Clock-safe companion reminders, speech bubble, "while you were away" summary,
 * guided sessions, Top-3 priorities dialog, and the Wellness sheet.
 */
@Composable
fun WellnessOverlays(viewModel: DockViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ws by viewModel.wellnessSettings.collectAsStateWithLifecycle()
    val stats by viewModel.wellnessStats.collectAsStateWithLifecycle()
    val nudgeQueue by viewModel.nudgeQueue.collectAsStateWithLifecycle()
    val activeNudge by viewModel.activeNudge.collectAsStateWithLifecycle()
    val isBubbleExpanded by viewModel.isBubbleExpanded.collectAsStateWithLifecycle()
    val missedNudges by viewModel.missedNudges.collectAsStateWithLifecycle()
    val whileAwayDismissed by viewModel.whileAwayBannerDismissed.collectAsStateWithLifecycle()
    val clockBounds by viewModel.clockProtectedBounds.collectAsStateWithLifecycle()
    val currentTimeMillis by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val guided by viewModel.guidedSession.collectAsStateWithLifecycle()
    val top3Open by viewModel.top3DialogOpen.collectAsStateWithLifecycle()
    val sheetOpen by viewModel.wellnessSheetOpen.collectAsStateWithLifecycle()
    val prios by viewModel.priorities.collectAsStateWithLifecycle()
    val buddy = settings.catName.ifBlank { "Mochi" }

    val isNightSilent = remember(currentTimeMillis, settings) {
        viewModel.isNightReminderSilent(currentTimeMillis)
    }

    var queueListExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(nudgeQueue.size) {
        if (nudgeQueue.size <= 1) queueListExpanded = false
    }

    val density = LocalDensity.current
    val minBubbleHeightPx = with(density) { 82.dp.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowHeightPx = constraints.maxHeight.toFloat()
        val placement = computeClockSafeStagePlacement(
            windowHeightPx = windowHeightPx,
            clockBounds = clockBounds,
            minFullBubbleHeightPx = minBubbleHeightPx
        )
        val stageMaxHeightDp = with(density) {
            placement.safeStageHeightPx.toDp().coerceAtLeast(44.dp)
        }

        // Rule 1 & Rule 2: Companion Stage Area strictly above the protected clock space
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .heightIn(max = stageMaxHeightDp)
                .padding(top = 10.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
                .testTag("companion_clock_safe_stage_overlay"),
            contentAlignment = Alignment.TopCenter
        ) {
            val currentNudge = activeNudge
            when {
                // Rule 7: In night mode, reminders become a very dim dot near the sleeping companion
                currentNudge != null && isNightSilent -> {
                    NightModeDimReminderDot(
                        queuedCount = nudgeQueue.size,
                        onClick = { viewModel.openWellnessSheet() }
                    )
                }

                // Active reminder in the companion stage above the clock
                currentNudge != null -> {
                    val showFullBubble = isBubbleExpanded && placement.canFitFullBubble
                    CompanionStageReminderContainer(
                        nudge = currentNudge,
                        queuedNudges = nudgeQueue,
                        useCatCompanion = settings.useCatCompanion,
                        showFullBubble = showFullBubble,
                        queueListExpanded = queueListExpanded,
                        onToggleQueueList = { queueListExpanded = !queueListExpanded },
                        onExpandBubble = { viewModel.expandNudgeBubble() },
                        onPrimaryAction = { target -> viewModel.onNudgeAction(target) },
                        onSnooze = { target -> viewModel.snoozeNudge(10, target) },
                        onDismiss = { target -> viewModel.dismissNudge(target) }
                    )
                }

                // Rule 6: "While you were away" soft summary line when no active reminder is showing
                !isNightSilent && !whileAwayDismissed && missedNudges.isNotEmpty() -> {
                    val summaryLine = formatWhileAwaySummary(missedNudges)
                    if (summaryLine != null) {
                        WhileYouWereAwaySummaryPill(
                            summaryText = summaryLine,
                            onOpenWellness = {
                                viewModel.dismissWhileAwayBanner()
                                viewModel.openWellnessSheet()
                            },
                            onDismiss = { viewModel.dismissWhileAwayBanner() }
                        )
                    }
                }
            }
        }
    }

    when (guided) {
        "STRETCH" -> GuidedStretchDialog(buddy = buddy, onFinish = { viewModel.finishGuidedSession(it) })
        "BREATHE" -> BreathingDialog(buddy = buddy, onFinish = { viewModel.finishGuidedSession(it) })
    }

    if (top3Open) {
        Top3Dialog(
            initial = prios.map { it.text },
            onSave = { viewModel.saveTop3(it) },
            onDismiss = { viewModel.closeTop3() }
        )
    }

    if (sheetOpen) {
        WellnessSheet(
            settings = ws,
            stats = stats,
            buddy = buddy,
            missedNudges = missedNudges,
            priorities = prios.map { Triple(it.id, it.text, it.done) },
            onActOnMissed = { viewModel.actOnMissedNudge(it) },
            onClearMissed = { viewModel.clearMissedNudges() },
            onTogglePriority = { id, done -> viewModel.togglePriority(id, done) },
            onEditTop3 = {
                viewModel.closeWellnessSheet()
                viewModel.openTop3()
            },
            onLogWater = { viewModel.logWellness("WATER") },
            onDecrementWater = { viewModel.decrementWater() },
            onIncrementBreak = { viewModel.logWellness("STRETCH") },
            onDecrementBreak = { viewModel.decrementBreak() },
            onStretch = {
                viewModel.closeWellnessSheet()
                viewModel.startGuidedSession("STRETCH")
            },
            onBreathe = {
                viewModel.closeWellnessSheet()
                viewModel.startGuidedSession("BREATHE")
            },
            onSave = {
                viewModel.saveWellnessSettings(it)
                viewModel.closeWellnessSheet()
            },
            onDismiss = { viewModel.closeWellnessSheet() }
        )
    }
}

@Composable
private fun CompanionStageReminderContainer(
    nudge: Nudge,
    queuedNudges: List<Nudge>,
    useCatCompanion: Boolean,
    showFullBubble: Boolean,
    queueListExpanded: Boolean,
    onToggleQueueList: () -> Unit,
    onExpandBubble: () -> Unit,
    onPrimaryAction: (Nudge) -> Unit,
    onSnooze: (Nudge) -> Unit,
    onDismiss: (Nudge) -> Unit
) {
    val extraCount = (queuedNudges.size - 1).coerceAtLeast(0)

    AnimatedContent(
        targetState = showFullBubble,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.88f, animationSpec = tween(220))) togetherWith
                (fadeOut(tween(180)) + scaleOut(targetScale = 0.88f, animationSpec = tween(180)))
        },
        label = "companion_bubble_shrink_transition"
    ) { expanded ->
        if (expanded) {
            CompanionFullSpeechBubble(
                nudge = nudge,
                extraQueuedCount = extraCount,
                queuedNudges = queuedNudges,
                useCatCompanion = useCatCompanion,
                queueListExpanded = queueListExpanded,
                onToggleQueueList = onToggleQueueList,
                onPrimaryAction = onPrimaryAction,
                onSnooze = onSnooze,
                onDismiss = onDismiss
            )
        } else {
            ShrunkCompanionFloatingIcon(
                nudge = nudge,
                extraQueuedCount = extraCount,
                onClick = onExpandBubble
            )
        }
    }
}

/**
 * Rule 2, 4, 5 & 7: Translucent companion speech bubble in the stage area above the clock.
 * - Swipe horizontally to dismiss.
 * - Swipe up to snooze for 10 minutes.
 * - Shows "+N" badge when multiple reminders pile up; tapping "+N" reveals the short list.
 */
@Composable
private fun CompanionFullSpeechBubble(
    nudge: Nudge,
    extraQueuedCount: Int,
    queuedNudges: List<Nudge>,
    useCatCompanion: Boolean,
    queueListExpanded: Boolean,
    onToggleQueueList: () -> Unit,
    onPrimaryAction: (Nudge) -> Unit,
    onSnooze: (Nudge) -> Unit,
    onDismiss: (Nudge) -> Unit
) {
    var dragX by remember(nudge) { mutableFloatStateOf(0f) }
    var dragY by remember(nudge) { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(dragX, tween(90), label = "bubble_drag_x")
    val animatedOffsetY by animateFloatAsState(dragY, tween(90), label = "bubble_drag_y")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
            .widthIn(max = 430.dp)
            .pointerInput(nudge) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            // Rule 5: Swipe up to snooze for 10 minutes
                            dragY < -72f && abs(dragY) > abs(dragX) -> {
                                dragX = 0f
                                dragY = 0f
                                onSnooze(nudge)
                            }
                            // Rule 5: Swipe horizontally to dismiss
                            abs(dragX) > 110f -> {
                                dragX = 0f
                                dragY = 0f
                                onDismiss(nudge)
                            }
                            else -> {
                                dragX = 0f
                                dragY = 0f
                            }
                        }
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX = (dragX + dragAmount.x).coerceIn(-220f, 220f)
                        dragY = (dragY + dragAmount.y).coerceIn(-140f, 40f)
                    }
                )
            }
            .testTag("companion_speech_bubble")
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SoftBubbleSurface,
            modifier = Modifier
                .border(0.9.dp, SoftBubbleBorder, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = nudge.type.companionHeadline(useCatCompanion),
                                color = SoftPrimaryText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Rule 4: "+N" badge when several reminders pile up
                            if (extraQueuedCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = SoftAquaAccent.copy(alpha = 0.22f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onToggleQueueList() }
                                        .testTag("nudge_queue_count_badge")
                                ) {
                                    Text(
                                        text = "+$extraQueuedCount",
                                        color = SoftAquaAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (nudge.message.isNotBlank()) {
                            Text(
                                text = nudge.message,
                                color = SoftSecondaryText,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Compact action buttons ("Done" and "Later")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val actionLabel = nudge.type.action ?: "Done"
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SoftWarmAccent.copy(alpha = 0.22f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onPrimaryAction(nudge) }
                                .testTag("nudge_done_button")
                        ) {
                            Text(
                                text = actionLabel,
                                color = SoftWarmAccent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.06f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSnooze(nudge) }
                                .testTag("nudge_later_button")
                        ) {
                            Text(
                                text = "Later",
                                color = SoftSecondaryText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Rule 4: Short expandable list of other queued reminders when "+N" is tapped
                AnimatedVisibility(visible = queueListExpanded && queuedNudges.size > 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        queuedNudges.drop(1).take(3).forEach { queued ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${queued.type.emoji} ${queued.type.title}",
                                    color = SoftPrimaryText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = queued.type.action ?: "Done",
                                        color = SoftWarmAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onPrimaryAction(queued) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Text(
                                        text = "×",
                                        color = SoftSecondaryText,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onDismiss(queued) }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtle speech-bubble tail pointing down toward the companion
        Canvas(modifier = Modifier.size(width = 14.dp, height = 6.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width * 0.5f, size.height)
                close()
            }
            drawPath(path = path, color = SoftBubbleSurface)
        }
    }
}

/**
 * Rule 3: After 20 seconds (or on compact screens where nothing fits above the clock),
 * the bubble shrinks to a small floating icon near the companion with a gentle pulse.
 * Tap it to reopen the full bubble.
 */
@Composable
private fun ShrunkCompanionFloatingIcon(
    nudge: Nudge,
    extraQueuedCount: Int,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "shrunk_nudge_pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.62f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shrunk_icon_alpha"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = SoftBubbleSurface.copy(alpha = 0.78f * pulseAlpha),
        modifier = Modifier
            .border(0.8.dp, SoftBubbleBorder.copy(alpha = 0.45f * pulseAlpha), RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .semantics {
                contentDescription = "Reopen ${nudge.type.title} reminder"
            }
            .testTag("companion_shrunk_reminder_icon")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(text = nudge.type.emoji, fontSize = 14.sp)
            if (extraQueuedCount > 0) {
                Text(
                    text = "+$extraQueuedCount",
                    color = SoftAquaAccent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Rule 7: In night mode, reminders become a very dim dot near the sleeping companion.
 */
@Composable
private fun NightModeDimReminderDot(
    queuedCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .semantics {
                contentDescription = "$queuedCount quiet night reminder"
            }
            .testTag("night_mode_dim_reminder_dot"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(NightDimDotColor)
        )
    }
}

/**
 * Rule 6: "While you were away" single-line summary near the companion.
 * Tapping opens the Wellness sheet.
 */
@Composable
private fun WhileYouWereAwaySummaryPill(
    summaryText: String,
    onOpenWellness: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = SoftBubbleSurface.copy(alpha = 0.78f),
        modifier = Modifier
            .border(0.8.dp, SoftBubbleBorder, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable { onOpenWellness() }
            .testTag("while_you_were_away_summary")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🐾", fontSize = 12.sp)
            Text(
                text = summaryText,
                color = SoftSecondaryText,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "×",
                color = SoftSecondaryText,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDismiss() }
                    .padding(horizontal = 4.dp)
                    .testTag("dismiss_while_away_summary")
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// Guided 1-minute stretch & box breathing
// -------------------------------------------------------------------------------------------

private val StretchSteps = listOf(
    "Roll your shoulders back slowly, five times.",
    "Tilt your head gently left, then right.",
    "Reach both arms overhead and take a deep breath.",
    "Stand up, shake out your hands and ankles."
)

@Composable
private fun GuidedStretchDialog(buddy: String, onFinish: (Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(10) }
    LaunchedEffect(step) {
        secondsLeft = 10
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        if (step < StretchSteps.lastIndex) step++ else onFinish(true)
    }
    AlertDialog(
        onDismissRequest = { onFinish(false) },
        title = { Text("Stretch with $buddy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Step ${step + 1} of ${StretchSteps.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(StretchSteps[step], fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { (step * 10 + (10 - secondsLeft)) / (StretchSteps.size * 10f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Move gently and stop if anything hurts.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (step < StretchSteps.lastIndex) step++ else onFinish(true) }) {
                Text(if (step < StretchSteps.lastIndex) "Next" else "Done")
            }
        },
        dismissButton = { TextButton(onClick = { onFinish(false) }) { Text("Stop") } }
    )
}

/** Box breathing: in 4 s, hold 4 s, out 4 s, hold 4 s, four rounds (about one minute). */
@Composable
private fun BreathingDialog(buddy: String, onFinish: (Boolean) -> Unit) {
    val phases = listOf("Breathe in", "Hold", "Breathe out", "Hold")
    var phase by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (round <= 4) {
            for (p in phases.indices) {
                phase = p
                delay(4000L)
            }
            round++
        }
        onFinish(true)
    }
    val target = when (phase) { 0 -> 1f; 1 -> 1f; else -> 0.45f }
    val scale by animateFloatAsState(target, tween(if (phase == 0 || phase == 2) 4000 else 300, easing = FastOutSlowInEasing), label = "breath")
    AlertDialog(
        onDismissRequest = { onFinish(false) },
        title = { Text("Breathe with $buddy") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(170.dp)) {
                        val r = size.minDimension / 2f
                        drawCircle(SoftWarmAccent.copy(alpha = 0.18f), radius = r * scale)
                        drawCircle(SoftWarmAccent, radius = r * scale, style = Stroke(width = 3.dp.toPx()))
                        drawCircle(SoftWarmAccent.copy(alpha = 0.35f), radius = r, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(width = 1.dp.toPx()))
                    }
                    Text(phases[phase], fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Round ${round.coerceAtMost(4)} of 4", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onFinish(round > 2) }) { Text("Finish") } }
    )
}

// -------------------------------------------------------------------------------------------
// Top 3
// -------------------------------------------------------------------------------------------

@Composable
private fun Top3Dialog(initial: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(initial.getOrElse(0) { "" }) }
    var b by remember { mutableStateOf(initial.getOrElse(1) { "" }) }
    var c by remember { mutableStateOf(initial.getOrElse(2) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Top 3 for today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The three things that matter most today. Your buddy will cheer each one you finish.", fontSize = 13.sp)
                OutlinedTextField(a, { a = it.take(80) }, label = { Text("1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(b, { b = it.take(80) }, label = { Text("2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(c, { c = it.take(80) }, label = { Text("3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(listOf(a, b, c)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -------------------------------------------------------------------------------------------
// Wellness sheet: today, while-you-were-away missed list, weekly recap, settings
// -------------------------------------------------------------------------------------------

@Composable
private fun WellnessSheet(
    settings: WellnessSettings,
    stats: WellnessStats,
    buddy: String,
    missedNudges: List<MissedNudge>,
    priorities: List<Triple<Long, String, Boolean>>,
    onActOnMissed: (MissedNudge) -> Unit,
    onClearMissed: () -> Unit,
    onTogglePriority: (Long, Boolean) -> Unit,
    onEditTop3: () -> Unit,
    onLogWater: () -> Unit,
    onDecrementWater: () -> Unit = {},
    onIncrementBreak: () -> Unit = {},
    onDecrementBreak: () -> Unit = {},
    onStretch: () -> Unit,
    onBreathe: () -> Unit,
    onSave: (WellnessSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var s by remember { mutableStateOf(settings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wellness") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Today
                Text("$buddy is ${stats.moodLabel} today", fontWeight = FontWeight.SemiBold)
                Text("💧 ${stats.waterToday} of ${stats.waterGoal} glasses   ·   🧘 ${stats.breaksToday} breaks", fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { (stats.waterToday / stats.waterGoal.coerceAtLeast(1).toFloat()).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Water & Break +/- steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = onDecrementWater,
                            enabled = stats.waterToday > 0,
                            modifier = Modifier.testTag("wellness_sheet_water_minus")
                        ) { Text("−1 💧") }
                        TextButton(
                            onClick = onLogWater,
                            modifier = Modifier.testTag("wellness_sheet_water_plus")
                        ) { Text("+1 💧") }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = onDecrementBreak,
                            enabled = stats.breaksToday > 0,
                            modifier = Modifier.testTag("wellness_sheet_break_minus")
                        ) { Text("−1 🧘") }
                        TextButton(
                            onClick = onIncrementBreak,
                            modifier = Modifier.testTag("wellness_sheet_break_plus")
                        ) { Text("+1 🧘") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onStretch) { Text("Guided Stretch") }
                    TextButton(onClick = onBreathe) { Text("Guided Breathe") }
                }

                // Rule 6: While you were away missed reminders list
                if (missedNudges.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("While you were away", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onClearMissed) { Text("Clear") }
                    }
                    missedNudges.asReversed().forEach { missed ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${missed.type.emoji} ${missed.type.title}",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (missed.type.action != null) {
                                TextButton(onClick = { onActOnMissed(missed) }) {
                                    Text(missed.type.action)
                                }
                            }
                        }
                    }
                }

                // Top 3
                Text("Top 3 for today", fontWeight = FontWeight.SemiBold)
                if (priorities.isEmpty()) {
                    TextButton(onClick = onEditTop3) { Text("Set your top 3") }
                } else {
                    priorities.forEach { (id, text, done) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = done, onCheckedChange = { onTogglePriority(id, it) })
                            Text(text, fontSize = 14.sp, textDecoration = if (done) TextDecoration.LineThrough else null)
                        }
                    }
                    TextButton(onClick = onEditTop3) { Text("Edit") }
                }

                // Weekly recap
                Text("This week", fontWeight = FontWeight.SemiBold)
                Text(
                    "${stats.breaksWeek} breaks and ${stats.waterWeek} glasses of water." +
                        if (stats.waterStreakDays > 1) " Water goal streak: ${stats.waterStreakDays} days 🔥" else "",
                    fontSize = 13.sp
                )

                // Settings
                Spacer(Modifier.height(4.dp))
                Text("Reminders", fontWeight = FontWeight.SemiBold)
                SettingSwitch("All wellness reminders", s.enabled) { s = s.copy(enabled = it) }
                if (s.enabled) {
                    SettingSwitch("Drink water", s.waterEnabled) { s = s.copy(waterEnabled = it) }
                    if (s.waterEnabled) {
                        SettingSlider("Every ${s.waterIntervalMin} min", s.waterIntervalMin, 30, 120, 15) { s = s.copy(waterIntervalMin = it) }
                        SettingSlider("Daily goal: ${s.waterGoal} glasses", s.waterGoal, 4, 14, 1) { s = s.copy(waterGoal = it) }
                    }
                    SettingSwitch("Stretch breaks", s.stretchEnabled) { s = s.copy(stretchEnabled = it) }
                    if (s.stretchEnabled) {
                        SettingSlider("After ${s.stretchIntervalMin} min of sitting", s.stretchIntervalMin, 30, 90, 5) { s = s.copy(stretchIntervalMin = it) }
                    }
                    SettingSwitch("Eye rest (20-20-20)", s.eyeRestEnabled) { s = s.copy(eyeRestEnabled = it) }
                    SettingSwitch("Breathing before meetings & after many calls", s.breathingEnabled) { s = s.copy(breathingEnabled = it) }
                    SettingSwitch("Suggest a walk on nice days", s.walkEnabled) { s = s.copy(walkEnabled = it) }
                    SettingSwitch("Lunch break reminder", s.lunchEnabled) { s = s.copy(lunchEnabled = it) }
                    SettingSwitch("Meeting marathon check", s.fatigueEnabled) { s = s.copy(fatigueEnabled = it) }
                    SettingSwitch("Top 3 every morning", s.top3Enabled) { s = s.copy(top3Enabled = it) }
                    SettingSwitch("End of workday", s.endOfDayEnabled) { s = s.copy(endOfDayEnabled = it) }
                    SettingSwitch("Wind-down before bed", s.windDownEnabled) { s = s.copy(windDownEnabled = it) }
                    if (s.windDownEnabled) {
                        SettingSlider("Bedtime: %02d:00".format(s.bedtimeHour), s.bedtimeHour, 20, 23, 1) { s = s.copy(bedtimeHour = it) }
                    }
                    SettingSwitch("Celebrations (Fridays, birthdays, holidays)", s.celebrationsEnabled) { s = s.copy(celebrationsEnabled = it) }
                    SettingSlider("Active from %02d:00".format(s.workStartHour), s.workStartHour, 5, 12, 1) { s = s.copy(workStartHour = it) }
                    SettingSlider("Until %02d:00".format(s.workEndHour), s.workEndHour, 14, 22, 1) { s = s.copy(workEndHour = it) }
                    SettingSwitch("Weekdays only", s.weekdaysOnly) { s = s.copy(weekdaysOnly = it) }
                }
                Text(
                    "Reminders pause during meetings, Focus sessions, Do Not Disturb and at night. " +
                        "Everything stays on this phone. These are healthy-habit nudges, not medical advice.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(s) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingSlider(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.toFloat(),
            onValueChange = { v -> onChange(((v / step).toInt() * step).coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / step - 1).coerceAtLeast(0)
        )
    }
}
