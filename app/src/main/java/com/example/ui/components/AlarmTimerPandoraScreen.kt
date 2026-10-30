package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import com.example.ui.SunriseState
import com.example.ui.util.DockHapticFeedback
import com.example.ui.util.rememberDockHaptics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.local.DockAlarmEntity
import com.example.data.weather.WeatherStationData
import com.example.ui.theme.ChargingEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KilljoyGold
import com.example.ui.theme.OmenViolet
import com.example.ui.theme.RadianiteCyan
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.ValorantCrimson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Full-Screen Hub for Alarm, Countdown Timer, and Pandora Focus Timer.
 * Accessible via swipe or quick navigation from the main Smart Dock screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmTimerPandoraStandByPage(
    isLandscape: Boolean,
    currentTimeMillis: Long,
    use24Hour: Boolean,
    alarms: List<DockAlarmEntity>,
    chronoState: ActiveChronoHudState,
    scheduleItems: List<UnifiedScheduleItem>,
    isAgentThinking: Boolean,
    onBackToClock: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddAlarm: (hour: Int, minute: Int, label: String) -> Unit,
    onToggleAlarm: (DockAlarmEntity) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit,
    onStartTimer: (durationSeconds: Int, label: String) -> Unit,
    onPauseResumeTimer: () -> Unit,
    onStartPandoraTimer: (durationMinutes: Int, phase: PandoraPhase) -> Unit,
    onPauseResumePandoraTimer: () -> Unit,
    onSnoozeChrono: (AiToyActivity) -> Unit,
    onStopChrono: (AiToyActivity) -> Unit,
    sunriseState: SunriseState = SunriseState(),
    sunriseSimulationEnabled: Boolean = true,
    onToggleSunriseSimulation: () -> Unit = {},
    onPreviewSunriseDemo: () -> Unit = {},
    onCancelSunrisePreview: () -> Unit = {},
    hapticsEnabled: Boolean = true,
    currentWeather: WeatherStationData? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberDockHaptics(hapticsEnabled)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Alarms, 1 = Timer, 2 = Pandora Focus
    var selectedPandoraSoundscape by rememberSaveable { mutableStateOf("Radianite Synth") }

    val timePattern = if (use24Hour) "HH:mm" else "h:mm a"
    val clockStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(currentTimeMillis))

    val activeToyMode = when {
        chronoState.isAlarmRinging -> AiToyActivity.ALARM
        chronoState.isTimerRinging || chronoState.isTimerRunning -> AiToyActivity.TIMER
        chronoState.isPandoraRinging || chronoState.isPandoraRunning -> AiToyActivity.PANDORA_TIMER
        selectedTab == 0 -> AiToyActivity.ALARM
        selectedTab == 1 -> AiToyActivity.TIMER
        else -> AiToyActivity.PANDORA_TIMER
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1722),
                        Color(0xFF05080E)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("alarm_timer_pandora_page")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF121C28),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBackToClock() }
                        .testTag("btn_back_to_clock_from_chrono")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Clock",
                            tint = RadianiteCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = clockStr,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column {
                    Text(
                        text = "ALARM • TIMER • FOCUS",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            chronoState.isAlarmRinging -> "ALARM RINGING: ${chronoState.ringingAlarmLabel}"
                            chronoState.isTimerRunning -> "TIMER RUNNING: ${formatSecondsMmSs(chronoState.timerRemainingSeconds)}"
                            chronoState.isPandoraRunning -> "${chronoState.pandoraPhase.label}: ${formatSecondsMmSs(chronoState.pandoraRemainingSeconds)}"
                            else -> "Chrono Hub & Focus Soundscapes"
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = activeToyMode.accentColor
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick Snooze & Stop in Header whenever any chrono is active or ringing
                if (chronoState.hasAnyActiveOrRinging) {
                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            onSnoozeChrono(activeToyMode)
                            Toast.makeText(context, "Snoozed +5m", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("screen_btn_snooze_chrono")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = "Snooze +5m",
                            tint = KilljoyGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Snooze +5m", style = MaterialTheme.typography.labelSmall, color = KilljoyGold)
                    }

                    Button(
                        onClick = {
                            haptics.toggle()
                            onStopChrono(activeToyMode)
                            Toast.makeText(context, "Stopped ${activeToyMode.chipLabel}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ValorantCrimson),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("screen_btn_stop_chrono")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(
                    onClick = onOpenSchedules,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF121C28))
                        .testTag("btn_goto_schedules_from_chrono")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Meeting Schedules",
                        tint = RadianiteCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF121C28))
                        .testTag("btn_goto_settings_from_chrono")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Mode Tabs: Alarm | Timer | Pandora Focus
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B121B))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChronoModeSegmentTab(
                title = "Alarms (${alarms.count { it.isEnabled }}/${alarms.size})",
                icon = Icons.Default.Alarm,
                selected = selectedTab == 0,
                accentColor = ValorantCrimson,
                badgeActive = chronoState.isAlarmRinging || chronoState.snoozedAlarmUntilMillis != null,
                onClick = {
                    haptics.tick()
                    selectedTab = 0
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chrono_screen_tab_alarms")
            )
            ChronoModeSegmentTab(
                title = if (chronoState.isTimerRunning || chronoState.isTimerRinging) {
                    "Timer • ${formatSecondsMmSs(chronoState.timerRemainingSeconds)}"
                } else {
                    "Countdown Timer"
                },
                icon = Icons.Default.Timer,
                selected = selectedTab == 1,
                accentColor = RadianiteCyan,
                badgeActive = chronoState.isTimerRunning || chronoState.isTimerRinging,
                onClick = {
                    haptics.tick()
                    selectedTab = 1
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chrono_screen_tab_timer")
            )
            ChronoModeSegmentTab(
                title = if (chronoState.isPandoraRunning || chronoState.isPandoraRinging) {
                    "Focus • ${formatSecondsMmSs(chronoState.pandoraRemainingSeconds)}"
                } else {
                    "Focus"
                },
                icon = Icons.Default.AutoAwesome,
                selected = selectedTab == 2,
                accentColor = OmenViolet,
                badgeActive = chronoState.isPandoraRunning || chronoState.isPandoraRinging,
                onClick = {
                    haptics.tick()
                    selectedTab = 2
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chrono_screen_tab_pandora")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLandscape) {
            // Landscape 2-Column Split:
            // Left Pane (38%): Live Robot Chrono Stage + Quick Snooze/Stop & Active Status
            // Right Pane (62%): Selected Mode Interactive Controls (Alarms / Timer / Pandora)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ChronoLiveStageSpotlightPane(
                    currentTimeMillis = currentTimeMillis,
                    scheduleItems = scheduleItems,
                    isAgentThinking = isAgentThinking,
                    activeToyMode = activeToyMode,
                    chronoState = chronoState,
                    onSnoozeChrono = onSnoozeChrono,
                    onStopChrono = onStopChrono,
                    onStartQuickChrono = { act ->
                        when (act) {
                            AiToyActivity.ALARM -> onTriggerAlarmNow(alarms.firstOrNull())
                            AiToyActivity.TIMER -> onStartTimer(300, "5m Dock Timer")
                            AiToyActivity.PANDORA_TIMER -> onStartPandoraTimer(25, PandoraPhase.FOCUS)
                            else -> onTriggerAlarmNow(alarms.firstOrNull())
                        }
                    },
                    hapticsEnabled = hapticsEnabled,
                    currentWeather = currentWeather,
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                )

                Box(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "chrono_landscape_content"
                    ) { tab ->
                        when (tab) {
                            0 -> DedicatedAlarmsPane(
                                context = context,
                                alarms = alarms,
                                chronoState = chronoState,
                                sunriseState = sunriseState,
                                sunriseSimulationEnabled = sunriseSimulationEnabled,
                                onToggleSunriseSimulation = onToggleSunriseSimulation,
                                onPreviewSunriseDemo = onPreviewSunriseDemo,
                                onCancelSunrisePreview = onCancelSunrisePreview,
                                haptics = haptics,
                                onAddAlarm = onAddAlarm,
                                onToggleAlarm = onToggleAlarm,
                                onDeleteAlarm = onDeleteAlarm,
                                onTriggerAlarmNow = onTriggerAlarmNow,
                                onSnooze = { onSnoozeChrono(AiToyActivity.ALARM) },
                                onStop = { onStopChrono(AiToyActivity.ALARM) }
                            )
                            1 -> DedicatedCountdownTimerPane(
                                chronoState = chronoState,
                                haptics = haptics,
                                onStartTimer = onStartTimer,
                                onPauseResumeTimer = onPauseResumeTimer,
                                onSnoozeTimer = { onSnoozeChrono(AiToyActivity.TIMER) },
                                onStopTimer = { onStopChrono(AiToyActivity.TIMER) }
                            )
                            else -> DedicatedPandoraFocusPane(
                                context = context,
                                chronoState = chronoState,
                                haptics = haptics,
                                selectedSoundscape = selectedPandoraSoundscape,
                                onSelectSoundscape = { selectedPandoraSoundscape = it },
                                onStartPandoraTimer = onStartPandoraTimer,
                                onPauseResumePandoraTimer = onPauseResumePandoraTimer,
                                onSnoozePandora = { onSnoozeChrono(AiToyActivity.PANDORA_TIMER) },
                                onStopPandora = { onStopChrono(AiToyActivity.PANDORA_TIMER) }
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait Stack: Compact Live Robot Stage on Top, Selected Mode Controls Below
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                ) {
                    InteractiveAiToyTopStage(
                        currentTimeMillis = currentTimeMillis,
                        schedule = scheduleItems,
                        isAgentThinking = isAgentThinking,
                        manualActivity = activeToyMode,
                        onSelectManualActivity = { act ->
                            when (act) {
                                AiToyActivity.ALARM -> selectedTab = 0
                                AiToyActivity.TIMER -> selectedTab = 1
                                AiToyActivity.PANDORA_TIMER -> selectedTab = 2
                                else -> {}
                            }
                        },
                        onQuickAddCalendarStatus = {},
                        chronoState = chronoState,
                        onSnoozeChrono = onSnoozeChrono,
                        onStopChrono = onStopChrono,
                        onStartQuickChrono = { act ->
                            when (act) {
                                AiToyActivity.ALARM -> onTriggerAlarmNow(alarms.firstOrNull())
                                AiToyActivity.TIMER -> onStartTimer(300, "5m Dock Timer")
                                AiToyActivity.PANDORA_TIMER -> onStartPandoraTimer(25, PandoraPhase.FOCUS)
                                else -> onTriggerAlarmNow(alarms.firstOrNull())
                            }
                        },
                        fillAvailableHeight = true,
                        hapticsEnabled = hapticsEnabled,
                        currentWeather = currentWeather,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "chrono_portrait_content"
                    ) { tab ->
                        when (tab) {
                            0 -> DedicatedAlarmsPane(
                                context = context,
                                alarms = alarms,
                                chronoState = chronoState,
                                sunriseState = sunriseState,
                                sunriseSimulationEnabled = sunriseSimulationEnabled,
                                onToggleSunriseSimulation = onToggleSunriseSimulation,
                                onPreviewSunriseDemo = onPreviewSunriseDemo,
                                onCancelSunrisePreview = onCancelSunrisePreview,
                                haptics = haptics,
                                onAddAlarm = onAddAlarm,
                                onToggleAlarm = onToggleAlarm,
                                onDeleteAlarm = onDeleteAlarm,
                                onTriggerAlarmNow = onTriggerAlarmNow,
                                onSnooze = { onSnoozeChrono(AiToyActivity.ALARM) },
                                onStop = { onStopChrono(AiToyActivity.ALARM) }
                            )
                            1 -> DedicatedCountdownTimerPane(
                                chronoState = chronoState,
                                haptics = haptics,
                                onStartTimer = onStartTimer,
                                onPauseResumeTimer = onPauseResumeTimer,
                                onSnoozeTimer = { onSnoozeChrono(AiToyActivity.TIMER) },
                                onStopTimer = { onStopChrono(AiToyActivity.TIMER) }
                            )
                            else -> DedicatedPandoraFocusPane(
                                context = context,
                                chronoState = chronoState,
                                haptics = haptics,
                                selectedSoundscape = selectedPandoraSoundscape,
                                onSelectSoundscape = { selectedPandoraSoundscape = it },
                                onStartPandoraTimer = onStartPandoraTimer,
                                onPauseResumePandoraTimer = onPauseResumePandoraTimer,
                                onSnoozePandora = { onSnoozeChrono(AiToyActivity.PANDORA_TIMER) },
                                onStopPandora = { onStopChrono(AiToyActivity.PANDORA_TIMER) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronoModeSegmentTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    badgeActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badgeActive) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
private fun ChronoLiveStageSpotlightPane(
    currentTimeMillis: Long,
    scheduleItems: List<UnifiedScheduleItem>,
    isAgentThinking: Boolean,
    activeToyMode: AiToyActivity,
    chronoState: ActiveChronoHudState,
    onSnoozeChrono: (AiToyActivity) -> Unit,
    onStopChrono: (AiToyActivity) -> Unit,
    onStartQuickChrono: (AiToyActivity) -> Unit,
    hapticsEnabled: Boolean = true,
    currentWeather: WeatherStationData? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0A111A))
            .border(1.dp, activeToyMode.accentColor.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            InteractiveAiToyTopStage(
                currentTimeMillis = currentTimeMillis,
                schedule = scheduleItems,
                isAgentThinking = isAgentThinking,
                manualActivity = activeToyMode,
                onSelectManualActivity = {},
                onQuickAddCalendarStatus = {},
                chronoState = chronoState,
                onSnoozeChrono = onSnoozeChrono,
                onStopChrono = onStopChrono,
                onStartQuickChrono = onStartQuickChrono,
                fillAvailableHeight = true,
                hapticsEnabled = hapticsEnabled,
                currentWeather = currentWeather,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Always-accessible Snooze & Stop Action Bar under the Robot Animation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onSnoozeChrono(activeToyMode) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stage_pane_snooze_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = "Snooze +5m",
                    tint = KilljoyGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Snooze +5m", color = KilljoyGold, style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = { onStopChrono(activeToyMode) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ValorantCrimson),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stage_pane_stop_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop / Reset", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DedicatedAlarmsPane(
    context: Context,
    alarms: List<DockAlarmEntity>,
    chronoState: ActiveChronoHudState,
    sunriseState: SunriseState,
    sunriseSimulationEnabled: Boolean,
    onToggleSunriseSimulation: () -> Unit,
    onPreviewSunriseDemo: () -> Unit,
    onCancelSunrisePreview: () -> Unit,
    haptics: DockHapticFeedback,
    onAddAlarm: (hour: Int, minute: Int, label: String) -> Unit,
    onToggleAlarm: (DockAlarmEntity) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    var newAlarmHour by rememberSaveable { mutableIntStateOf(7) }
    var newAlarmMinute by rememberSaveable { mutableIntStateOf(30) }
    var newAlarmLabel by rememberSaveable { mutableStateOf("Morning Wake-Up") }
    var showComposer by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0A111A))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (chronoState.isAlarmRinging) {
                        "ALARM RINGING: ${chronoState.ringingAlarmLabel} (${chronoState.ringingAlarmTimeText})"
                    } else if (chronoState.snoozedAlarmUntilMillis != null) {
                        "SNOOZED: ${chronoState.snoozedAlarmLabel}"
                    } else {
                        "Scheduled Wake & StandBy Alarms"
                    },
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (chronoState.isAlarmRinging) ValorantCrimson else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Triggers Dock Bell Robot animation with Snooze (+5m) & Stop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        haptics.alert()
                        onTriggerAlarmNow(alarms.firstOrNull())
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_ring_alarm_preview")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Test Alarm Animation",
                        tint = ValorantCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Ring", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        haptics.tick()
                        showComposer = !showComposer
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ValorantCrimson),
                    modifier = Modifier.testTag("btn_toggle_add_alarm")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showComposer) "Close" else "New Alarm", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (chronoState.isAlarmRinging || chronoState.snoozedAlarmUntilMillis != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ValorantCrimson.copy(alpha = 0.16f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ValorantCrimson.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (chronoState.isAlarmRinging) {
                            "ALARM ACTIVE • ${chronoState.ringingAlarmTimeText}"
                        } else {
                            "ALARM SNOOZED (+5M)"
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = ValorantCrimson
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            haptics.click()
                            onSnooze()
                        }) {
                            Text("Snooze +5m", color = KilljoyGold)
                        }
                        Button(
                            onClick = {
                                haptics.toggle()
                                onStop()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ValorantCrimson)
                        ) {
                            Text("Stop Alarm")
                        }
                    }
                }
            }
        }

        // Dedicated Sunrise Dawn Wake-Up Simulation Card (30 Minutes Before Alarm)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF101925),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (sunriseSimulationEnabled) {
                        KilljoyGold.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("sunrise_simulation_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (sunriseSimulationEnabled) KilljoyGold.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = "Sunrise Simulation",
                                tint = if (sunriseSimulationEnabled) KilljoyGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Sunrise Wake-Up Simulation",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (sunriseSimulationEnabled) KilljoyGold else MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = KilljoyGold.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "30 MIN",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KilljoyGold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Gradually increases screen brightness & shifts color temperature 30m prior to alarm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = sunriseSimulationEnabled,
                        onCheckedChange = {
                            haptics.toggle()
                            onToggleSunriseSimulation()
                        },
                        modifier = Modifier.testTag("toggle_sunrise_simulation")
                    )
                }

                if (sunriseSimulationEnabled) {
                    if (sunriseState.isActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E2616),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (sunriseState.isPreview) "DAWN PREVIEW IN PROGRESS" else "CIRCADIAN DAWN ACTIVE (ALARM ${sunriseState.nextAlarmTimeText})",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = KilljoyGold
                                    )
                                    Text(
                                        text = "${(sunriseState.progress * 100).toInt()}% Luminance",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = KilljoyGold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { sunriseState.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = KilljoyGold,
                                    trackColor = Color(0xFF111E2E)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (sunriseState.nextAlarmTimeText.isNotBlank()) {
                                "Dawn triggers 30m prior to ${sunriseState.nextAlarmTimeText}"
                            } else {
                                "Active with any enabled alarm"
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (sunriseState.isPreview) {
                            OutlinedButton(
                                onClick = {
                                    haptics.click()
                                    onCancelSunrisePreview()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_cancel_sunrise_preview")
                            ) {
                                Text("Stop Preview", color = ValorantCrimson, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    haptics.click()
                                    onPreviewSunriseDemo()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_preview_sunrise")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = KilljoyGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview 30m Sunrise", color = KilljoyGold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (showComposer) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF101924),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ValorantCrimson.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .testTag("add_alarm_composer")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SET ALARM TIME",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = ValorantCrimson
                            )
                            Text(
                                text = "%02d:%02d".format(newAlarmHour, newAlarmMinute),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 30.sp,
                                color = KilljoyGold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("-1h" to -60, "-15m" to -15, "+15m" to 15, "+1h" to 60).forEach { (lbl, delta) ->
                                OutlinedButton(
                                    onClick = {
                                        haptics.tick()
                                        val total = ((newAlarmHour * 60 + newAlarmMinute + delta) % 1440 + 1440) % 1440
                                        newAlarmHour = total / 60
                                        newAlarmMinute = total % 60
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(lbl, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Quick Wake Presets
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(6, 30, "06:30 Early Gym"),
                            Triple(7, 0, "07:00 Morning Wake"),
                            Triple(7, 30, "07:30 Standup Prep"),
                            Triple(8, 30, "08:30 Desk Start")
                        ).forEach { (h, m, presetTitle) ->
                            FilterChip(
                                selected = newAlarmHour == h && newAlarmMinute == m,
                                onClick = {
                                    haptics.tick()
                                    newAlarmHour = h
                                    newAlarmMinute = m
                                    newAlarmLabel = presetTitle.substringAfter(" ")
                                },
                                label = { Text(presetTitle, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newAlarmLabel,
                        onValueChange = { newAlarmLabel = it },
                        label = { Text("Alarm Label") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_alarm_label")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.click()
                                setSystemAndroidAlarm(context, newAlarmHour, newAlarmMinute, newAlarmLabel)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AlarmAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync to OS Clock", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = {
                                haptics.success()
                                onAddAlarm(
                                    newAlarmHour,
                                    newAlarmMinute,
                                    newAlarmLabel.ifBlank { "Dock Alarm" }
                                )
                                showComposer = false
                                Toast.makeText(
                                    context,
                                    "Alarm saved for %02d:%02d".format(newAlarmHour, newAlarmMinute),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ValorantCrimson),
                            modifier = Modifier.testTag("confirm_save_alarm_button")
                        ) {
                            Text("Save Dock Alarm")
                        }
                    }
                }
            }
        }

        // Scrollable Alarm List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(alarms, key = { it.id }) { alarm ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF101924),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (alarm.isEnabled) {
                                ValorantCrimson.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("alarm_row_${alarm.id}")
                ) {
                    Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (alarm.isEnabled) ValorantCrimson.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlarmOn,
                                    contentDescription = null,
                                    tint = if (alarm.isEnabled) ValorantCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "%02d:%02d".format(alarm.hour, alarm.minute),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp,
                                    color = if (alarm.isEnabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = alarm.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptics.alert()
                                    onTriggerAlarmNow(alarm)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("trigger_alarm_${alarm.id}")
                            ) {
                                Text("Ring", style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(
                                checked = alarm.isEnabled,
                                onCheckedChange = {
                                    haptics.toggle()
                                    onToggleAlarm(alarm)
                                },
                                modifier = Modifier.testTag("toggle_alarm_${alarm.id}")
                            )
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    onDeleteAlarm(alarm.id)
                                },
                                modifier = Modifier.testTag("delete_alarm_${alarm.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Alarm",
                                    tint = ValorantCrimson
                                )
                            }
                        }
                    }
                    AlarmOptionsRow(alarm = alarm)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DedicatedCountdownTimerPane(
    chronoState: ActiveChronoHudState,
    haptics: DockHapticFeedback,
    onStartTimer: (durationSeconds: Int, label: String) -> Unit,
    onPauseResumeTimer: () -> Unit,
    onSnoozeTimer: () -> Unit,
    onStopTimer: () -> Unit
) {
    val total = chronoState.timerTotalSeconds.coerceAtLeast(1)
    val remaining = if (chronoState.isTimerRunning || chronoState.timerRemainingSeconds > 0) {
        chronoState.timerRemainingSeconds
    } else {
        chronoState.timerTotalSeconds
    }
    val fraction by animateFloatAsState(
        targetValue = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "timer_ring_fraction"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0A111A))
            .border(1.dp, RadianiteCyan.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TACHOMETER COUNTDOWN TIMER",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (chronoState.isTimerRinging) {
                        "TIME UP • Tap Snooze (+5m) or Stop"
                    } else if (chronoState.isTimerRunning) {
                        "Running: ${chronoState.timerLabel}"
                    } else {
                        "Select duration preset or adjust custom time"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chronoState.isTimerRinging) KilljoyGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = RadianiteCyan.copy(alpha = 0.16f)
            ) {
                Text(
                    text = if (chronoState.isTimerRunning) "RUNNING" else if (chronoState.isTimerRinging) "RINGING" else "READY",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = RadianiteCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Circular Progress Gauge + Big Digital Readout
        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                drawArc(
                    color = Color(0xFF162436),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                drawArc(
                    color = if (chronoState.isTimerRinging) KilljoyGold else RadianiteCyan,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatSecondsMmSs(remaining),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = chronoState.timerLabel,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.sp,
                    color = RadianiteCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Duration Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                60 to "1 Min",
                180 to "3 Min",
                300 to "5 Min",
                600 to "10 Min",
                900 to "15 Min",
                1200 to "20 Min",
                1800 to "30 Min",
                2700 to "45 Min"
            ).forEach { (secs, label) ->
                FilterChip(
                    selected = chronoState.timerTotalSeconds == secs && chronoState.isTimerRunning,
                    onClick = {
                        haptics.tick()
                        onStartTimer(secs, "$label Timer")
                    },
                    label = { Text(label, fontFamily = JetBrainsMonoFontFamily) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RadianiteCyan.copy(alpha = 0.22f),
                        selectedLabelColor = RadianiteCyan
                    ),
                    modifier = Modifier.testTag("timer_preset_${secs}s")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Primary Transport Controls: Start/Pause, +5m Snooze, Stop/Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    haptics.click()
                    onPauseResumeTimer()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RadianiteCyan, contentColor = Color(0xFF041019)),
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("btn_pause_resume_timer")
            ) {
                Icon(
                    imageVector = if (chronoState.isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (chronoState.isTimerRunning) "Pause Timer" else "Start / Resume",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    haptics.click()
                    onSnoozeTimer()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Snooze, contentDescription = null, tint = KilljoyGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+5m", color = KilljoyGold)
            }

            OutlinedButton(
                onClick = {
                    haptics.toggle()
                    onStopTimer()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = ValorantCrimson, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", color = ValorantCrimson)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DedicatedPandoraFocusPane(
    context: Context,
    chronoState: ActiveChronoHudState,
    haptics: DockHapticFeedback,
    selectedSoundscape: String,
    onSelectSoundscape: (String) -> Unit,
    onStartPandoraTimer: (durationMinutes: Int, phase: PandoraPhase) -> Unit,
    onPauseResumePandoraTimer: () -> Unit,
    onSnoozePandora: () -> Unit,
    onStopPandora: () -> Unit
) {
    val total = chronoState.pandoraTotalSeconds.coerceAtLeast(1)
    val remaining = if (chronoState.isPandoraRunning || chronoState.pandoraRemainingSeconds > 0) {
        chronoState.pandoraRemainingSeconds
    } else {
        chronoState.pandoraTotalSeconds
    }
    val fraction by animateFloatAsState(
        targetValue = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "pandora_ring_fraction"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0A111A))
            .border(1.dp, OmenViolet.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FOCUS & AUDIO HUB",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${chronoState.pandoraPhase.label} • Cycle #${chronoState.pandoraCompletedCycles + 1} • Station: $selectedSoundscape",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmenViolet
                )
            }

            OutlinedButton(
                onClick = { launchPandoraMusicApp(context) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("btn_launch_pandora_app")
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Open Pandora Radio",
                    tint = OmenViolet,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pandora App", style = MaterialTheme.typography.labelSmall, color = OmenViolet)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Orbital Crystal Ring Gauge
        Box(
            modifier = Modifier.size(164.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                drawArc(
                    color = Color(0xFF1A142B),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(OmenViolet, RadianiteCyan, OmenViolet)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatSecondsMmSs(remaining),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = chronoState.pandoraPhase.label,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = OmenViolet
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pandora Focus Phase Presets
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                Triple(25, PandoraPhase.FOCUS, "25m Focus"),
                Triple(50, PandoraPhase.FOCUS, "50m Deep Focus"),
                Triple(5, PandoraPhase.SHORT_BREAK, "5m Short Break"),
                Triple(15, PandoraPhase.LONG_BREAK, "15m Recharge Break")
            ).forEach { (mins, phase, label) ->
                FilterChip(
                    selected = chronoState.pandoraTotalSeconds == mins * 60 &&
                        chronoState.pandoraPhase == phase &&
                        chronoState.isPandoraRunning,
                    onClick = { onStartPandoraTimer(mins, phase) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OmenViolet.copy(alpha = 0.24f),
                        selectedLabelColor = OmenViolet
                    ),
                    modifier = Modifier.testTag("pandora_preset_${mins}m")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pandora Station / Soundscape Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF101924))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = RadianiteCyan,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FOCUS PRESETS",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RadianiteCyan
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Radianite Synth",
                    "Thumbprint Radio",
                    "Lo-Fi Study Beats",
                    "Deep Coding Ambient",
                    "Night Rain & Bipolar"
                ).forEach { station ->
                    FilterChip(
                        selected = selectedSoundscape == station,
                        onClick = {
                            haptics.tick()
                            onSelectSoundscape(station)
                        },
                        label = { Text(station, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RadianiteCyan.copy(alpha = 0.2f),
                            selectedLabelColor = RadianiteCyan
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Primary Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    haptics.click()
                    onPauseResumePandoraTimer()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OmenViolet),
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("btn_pause_resume_pandora")
            ) {
                Icon(
                    imageVector = if (chronoState.isPandoraRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (chronoState.isPandoraRunning) "Pause Focus" else "Start Focus",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    haptics.click()
                    onSnoozePandora()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Snooze, contentDescription = null, tint = KilljoyGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+5m", color = KilljoyGold)
            }

            OutlinedButton(
                onClick = {
                    haptics.toggle()
                    onStopPandora()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = ValorantCrimson, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop", color = ValorantCrimson)
            }
        }
    }
}

private fun setSystemAndroidAlarm(context: Context, hour: Int, minute: Int, label: String) {
    try {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Smart Dock Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Saved in Smart Dock (%02d:%02d)".format(hour, minute),
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun launchPandoraMusicApp(context: Context) {
    val pandoraPackages = listOf("com.pandora.android", "com.pandora.android.atv")
    for (pkg in pandoraPackages) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }
    }
    try {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pandora.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "Focus Timer running on Smart Dock", Toast.LENGTH_SHORT).show()
    }
}
