package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.local.DockNoteEntity
import com.example.ui.components.AddDockAgendaDialog
import com.example.ui.components.AddDockNoteDialog
import com.example.ui.components.DockControlCenterSheet
import com.example.ui.components.DockSettingsStandByPage
import com.example.ui.components.InteractiveAiToyTopStage
import com.example.ui.components.StandByClockCard
import com.example.ui.components.providerColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RadianiteCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import com.example.data.calendar.AccountProviderBadge
import com.example.ui.components.AiToyActivity
import com.example.ui.components.AlarmTimerPandoraStandByPage
import com.example.ui.components.ScheduleItemCard
import com.example.ui.theme.ChargingEmerald
import com.example.ui.theme.GmailCoral
import com.example.ui.theme.KilljoyGold
import com.example.ui.theme.OutlookBlue
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.util.rememberDockHaptics
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.ln
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Maps ambient light sensor readings ([lux], 0..2000+) onto a smooth logarithmic display
 * brightness curve (`0.03f..1.0f`), scaled by the user's manual [userScale] ceiling.
 *
 * - ~0 lux (pitch-dark bedroom) -> 0.03f
 * - ~5 lux (dim nightstand) -> ~0.27f
 * - ~30 lux (warm evening desk) -> ~0.51f
 * - ~150 lux (normal indoor office) -> ~0.73f
 * - ~1000+ lux (bright daylight) -> 1.0f
 */
fun computeAdaptiveBrightnessForLux(lux: Float, userScale: Float = 1.0f): Float {
    val clampedLux = lux.coerceIn(0f, 2500f)
    val norm = (ln(1f + clampedLux) / ln(1f + 1000f)).coerceIn(0f, 1f)
    val curved = 0.03f + 0.97f * norm
    return (curved * userScale.coerceIn(0.25f, 1.0f)).coerceIn(0.02f, 1.0f)
}

/**
 * Exponential moving average filter so ambient light transitions never jump abruptly.
 */
fun smoothAdaptiveBrightness(currentBrightness: Float, targetBrightness: Float, alpha: Float = 0.25f): Float {
    val a = alpha.coerceIn(0.01f, 1.0f)
    return (currentBrightness + (targetBrightness - currentBrightness) * a).coerceIn(0.02f, 1.0f)
}

/**
 * Pure predicate determining whether Smart Dock should automatically return to the main
 * Companion & Clock screen (`page % 4 == 0`) after [timeoutMs] (default 60 seconds) of inactivity, never while a dialog, sheet or keyboard is open.
 */
/** Idle time before a secondary page returns to the companion clock. */
const val AUTO_RETURN_TIMEOUT_MS = 60_000L

fun shouldAutoReturnToCompanion(
    currentPageModulo: Int,
    isSheetOrDialogOpen: Boolean,
    nowMillis: Long,
    lastInteractionMillis: Long,
    timeoutMs: Long = AUTO_RETURN_TIMEOUT_MS,
    isKeyboardVisible: Boolean = false
): Boolean {
    // Never interrupt someone who is typing or has a dialog/sheet open: that could lose their text.
    if (isSheetOrDialogOpen || isKeyboardVisible) return false
    if (currentPageModulo == 0) return false
    return nowMillis - lastInteractionMillis >= timeoutMs
}

@Composable
fun SmartDockScreen(
    viewModel: DockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTimeMillis by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val battery by viewModel.batteryTelemetry.collectAsStateWithLifecycle()
    val dualCityWeather by viewModel.dualCityWeather.collectAsStateWithLifecycle()
    val manualToyActivity by viewModel.manualToyActivity.collectAsStateWithLifecycle()
    val calendarPermissionGranted by viewModel.calendarPermissionGranted.collectAsStateWithLifecycle()
    val syncedAccounts by viewModel.syncedAccounts.collectAsStateWithLifecycle()
    val connectedAccounts by viewModel.connectedAccounts.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val chronoState by viewModel.chronoState.collectAsStateWithLifecycle()
    val sunriseState by viewModel.sunriseState.collectAsStateWithLifecycle()
    val worldCitySearchResults by viewModel.worldCitySearchResults.collectAsStateWithLifecycle()
    val unifiedSchedule by viewModel.unifiedSchedule.collectAsStateWithLifecycle()
    val providerFilter by viewModel.providerFilter.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val agentMessages by viewModel.agentMessages.collectAsStateWithLifecycle()
    val isAgentThinking by viewModel.isAgentThinking.collectAsStateWithLifecycle()
    val isScreensaverOverlayActive by viewModel.isScreensaverOverlayActive.collectAsStateWithLifecycle()
    val ambientLux by viewModel.ambientLux.collectAsStateWithLifecycle()

    val haptics = rememberDockHaptics(settings.hapticsEnabled)

    var showControlCenterSheet by rememberSaveable { mutableStateOf(false) }
    var controlCenterInitialSection by rememberSaveable { mutableIntStateOf(0) }
    var showAddAgendaDialog by rememberSaveable { mutableStateOf(false) }
    var showAddNoteDialog by rememberSaveable { mutableStateOf(false) }

    // 4-Screen Circular Pager starting at page 400 (400 % 4 == 0 = Clock & Weather):
    // - Swipe Left 1x (page + 1 -> % 4 == 1): Meeting Schedules Page
    // - Swipe Left 2x (page + 2 -> % 4 == 2): Dedicated Alarm, Timer & Focus Screen
    // - Swipe Right 1x (page - 1 -> % 4 == 3): Industry-Standard Settings Page
    val mainPagerState = rememberPagerState(initialPage = 400, pageCount = { 800 })
    val coroutineScope = rememberCoroutineScope()
    var lastInteractionMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tactile micro-haptic tick on page swiping & reset inactivity timer
    LaunchedEffect(mainPagerState.currentPage, mainPagerState.isScrollInProgress) {
        lastInteractionMillis = System.currentTimeMillis()
        if (!mainPagerState.isScrollInProgress) {
            haptics.tick()
        }
    }

    fun scrollToModuloPage(targetRem: Int) {
        val current = mainPagerState.currentPage
        val rem = ((current % 4) + 4) % 4
        val diff = when ((targetRem - rem + 4) % 4) {
            0 -> 0
            1 -> 1
            2 -> 2
            else -> -1
        }
        if (diff != 0) {
            coroutineScope.launch {
                mainPagerState.animateScrollToPage(current + diff)
            }
        }
    }

    fun scrollToClockPage() = scrollToModuloPage(0)
    fun scrollToSchedulesPage() = scrollToModuloPage(1)
    fun scrollToChronoPage() = scrollToModuloPage(2)
    fun scrollToSettingsPage() = scrollToModuloPage(3)

    // When screen-off screensaver mode activates, immediately return to the Companion & Clock screen
    LaunchedEffect(isScreensaverOverlayActive) {
        if (isScreensaverOverlayActive) {
            showControlCenterSheet = false
            showAddAgendaDialog = false
            showAddNoteDialog = false
            scrollToClockPage()
        }
    }

    // 60-second idle auto-return: if the user is on Meeting Schedules (1), Alarm/Timer (2),
    // or Settings (3), has nothing open and has not touched the screen for a minute, glide back.
    val currentPageModulo = ((mainPagerState.currentPage % 4) + 4) % 4
    val anySecondarySheetOpen = showControlCenterSheet || showAddAgendaDialog || showAddNoteDialog
    val keyboardVisible = androidx.compose.foundation.layout.WindowInsets.ime
        .getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    LaunchedEffect(
        settings.autoReturnToCompanionEnabled,
        currentPageModulo,
        mainPagerState.isScrollInProgress,
        anySecondarySheetOpen,
        keyboardVisible
    ) {
        if (!settings.autoReturnToCompanionEnabled) return@LaunchedEffect
        // Dialogs, sheets and the keyboard pause the auto-return completely.
        if (anySecondarySheetOpen || keyboardVisible) return@LaunchedEffect
        if (currentPageModulo == 0) return@LaunchedEffect
        while (true) {
            delay(500L)
            val now = System.currentTimeMillis()
            if (!mainPagerState.isScrollInProgress &&
                shouldAutoReturnToCompanion(
                    currentPageModulo = ((mainPagerState.currentPage % 4) + 4) % 4,
                    isSheetOrDialogOpen = showControlCenterSheet || showAddAgendaDialog || showAddNoteDialog,
                    nowMillis = now,
                    lastInteractionMillis = lastInteractionMillis,
                    isKeyboardVisible = keyboardVisible
                )
            ) {
                scrollToClockPage()
                break
            }
        }
    }

    if (showControlCenterSheet) {
        BackHandler {
            showControlCenterSheet = false
        }
    } else if (mainPagerState.currentPage % 4 != 0) {
        BackHandler {
            scrollToClockPage()
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCalendarPermissionResult(granted)
    }

    fun openSettingsSection(sectionIndex: Int) {
        controlCenterInitialSection = sectionIndex
        scrollToSettingsPage()
    }

    // Smooth Adaptive Luminance + Circadian Sunrise Simulation
    val adaptiveTarget = if (settings.adaptiveBrightnessEnabled) {
        // Combine UI alpha floor (0.35f..1.0f) with hardware window brightness so text stays crisp
        (0.35f + 0.65f * computeAdaptiveBrightnessForLux(ambientLux, settings.brightnessDimmer)).coerceIn(0.28f, 1.0f)
    } else {
        settings.brightnessDimmer
    }
    val effectiveLuminance = when {
        sunriseState.isActive && sunriseState.progress > 0f ->
            (adaptiveTarget + (1.0f - adaptiveTarget) * sunriseState.progress).coerceIn(0.25f, 1.0f)
        isScreensaverOverlayActive ->
            (adaptiveTarget * 0.82f).coerceIn(0.22f, 0.88f)
        else -> adaptiveTarget
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .alpha(effectiveLuminance)
            .pointerInput(isScreensaverOverlayActive) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed || it.previousPressed }) {
                            lastInteractionMillis = System.currentTimeMillis()
                            if (isScreensaverOverlayActive) {
                                viewModel.exitScreensaverMode()
                            }
                        }
                    }
                }
            }
    ) {
        // Reliably detect Landscape Mode on all phones (including Pixel 6a with safeDrawing insets)
        val isLandscapeDock = maxWidth > maxHeight || maxWidth >= 560.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            HorizontalPager(
                state = mainPagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("main_dock_pager")
            ) { pageIndex ->
                when (pageIndex % 4) {
                    0 -> {
                        // CENTER SCREEN: Main Clock, Weather & AI Toy Display (Full-Screen in both Landscape & Portrait)
                        Box(modifier = Modifier.fillMaxSize()) {
                            StandByClockCard(
                                currentTimeMillis = currentTimeMillis,
                                clockStyle = settings.clockStyle,
                                agentThemeName = settings.agentTheme,
                                use24Hour = settings.use24HourFormat,
                                forceLandscapeMode = settings.forceLandscapeMode,
                                isNightTimeOnly = settings.isNightTimeOnlyActive(currentTimeMillis),
                                showToyInsideCard = true,
                                fillAvailableHeight = isLandscapeDock,
                                showPortHubTelemetry = settings.showPortHubTelemetry,
                                battery = battery,
                                dualCityWeather = dualCityWeather,
                                primaryCityName = settings.cityName,
                                secondaryCityName = settings.secondaryCityName,
                                schedule = unifiedSchedule,
                                isAgentThinking = isAgentThinking,
                                manualToyActivity = manualToyActivity,
                                chronoState = chronoState,
                                onSelectManualToyActivity = { act ->
                                    haptics.click()
                                    viewModel.setManualToyActivity(act)
                                },
                                onQuickAddCalendarStatus = { act ->
                                    haptics.click()
                                    viewModel.quickLogCalendarStatusBlock(act)
                                },
                                onSnoozeChrono = { act ->
                                    haptics.click()
                                    viewModel.snoozeChrono(act)
                                    Toast.makeText(
                                        context,
                                        "Snoozed ${act.chipLabel} (+5m)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onStopChrono = { act ->
                                    haptics.success()
                                    viewModel.stopChrono(act)
                                    Toast.makeText(
                                        context,
                                        "Stopped ${act.chipLabel}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onStartQuickChrono = { act ->
                                    haptics.click()
                                    viewModel.startQuickChronoFromStage(act)
                                },
                                hapticsEnabled = settings.hapticsEnabled,
                                onRefreshWeather = { viewModel.refreshDualCitiesWeather() },
                                onCycleClockStyle = {
                                    haptics.tick()
                                    viewModel.cycleClockStyle()
                                },
                                onToggleLandscapeMode = {
                                    haptics.click()
                                    viewModel.toggleLandscapeMode()
                                },
                                onOpenSettings = {
                                    haptics.click()
                                    scrollToSettingsPage()
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Circadian Dawn Ambient Warmth (gradually shifts color temperature 30m before alarm)
                            if (sunriseState.isActive && sunriseState.progress > 0f) {
                                val dawnAmber = Color(0xFFFF5722)
                                val sunGold = Color(0xFFFFD54F)
                                val overlayAlpha = (sunriseState.progress * 0.38f).coerceIn(0.04f, 0.42f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    dawnAmber.copy(alpha = overlayAlpha),
                                                    sunGold.copy(alpha = overlayAlpha * 0.65f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF1E1408).copy(alpha = 0.92f),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp)
                                        .border(
                                            width = 1.dp,
                                            color = KilljoyGold.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            haptics.click()
                                            if (sunriseState.isPreview) {
                                                viewModel.cancelSunrisePreview()
                                            } else {
                                                scrollToChronoPage()
                                            }
                                        }
                                        .testTag("sunrise_active_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = null,
                                            tint = KilljoyGold,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (sunriseState.isPreview) {
                                                "SUNRISE DEMO • ${(sunriseState.progress * 100).toInt()}% • TAP TO CANCEL"
                                            } else {
                                                "SUNRISE DAWN • ${(sunriseState.progress * 100).toInt()}% • ALARM AT ${sunriseState.nextAlarmTimeText}"
                                            },
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = KilljoyGold
                                        )
                                    }
                                }
                            }

                            if (isScreensaverOverlayActive) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFF0A1016).copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 14.dp)
                                        .border(
                                            width = 1.dp,
                                            color = RadianiteCyan.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(50)
                                        )
                                        .clip(RoundedCornerShape(50))
                                        .clickable { viewModel.exitScreensaverMode() }
                                        .testTag("screensaver_active_pill")
                                ) {
                                    Text(
                                        text = "SCREENSAVER MODE • TAP ANYWHERE TO WAKE",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = RadianiteCyan.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                    )
                                }
                            }

                        }
                    }

                    1 -> {
                        // SWIPE LEFT PAGE 1: Full-Screen Meeting Schedules Page
                        MeetingSchedulesStandByPage(
                            isLandscape = isLandscapeDock,
                            currentTimeMillis = currentTimeMillis,
                            use24Hour = settings.use24HourFormat,
                            calendarPermissionGranted = calendarPermissionGranted,
                            scheduleItems = unifiedSchedule,
                            selectedFilter = providerFilter,
                            onSelectFilter = { filter ->
                                haptics.tick()
                                viewModel.setProviderFilter(filter)
                            },
                            onBackToClock = {
                                haptics.tick()
                                scrollToClockPage()
                            },
                            onOpenChronoScreen = {
                                haptics.click()
                                scrollToChronoPage()
                            },
                            onOpenAddMeeting = {
                                haptics.click()
                                showAddAgendaDialog = true
                            },
                            onQuickStartMeetingNow = {
                                haptics.click()
                                viewModel.quickLogCalendarStatusBlock(AiToyActivity.BUSY_MEETING)
                                Toast.makeText(
                                    context,
                                    "Added 45m Meeting block to Schedule",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onQuickScheduleActivity = { act ->
                                haptics.click()
                                viewModel.quickLogCalendarStatusBlock(act)
                                Toast.makeText(
                                    context,
                                    "Scheduled ${act.chipLabel} in Calendar",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRequestOrRefreshCalendar = {
                                haptics.click()
                                if (!calendarPermissionGranted) {
                                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                } else {
                                    viewModel.refreshCalendarEvents()
                                    Toast.makeText(context, "Calendars refreshed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleCompleted = { item ->
                                haptics.tick()
                                viewModel.toggleCustomAgendaCompleted(item)
                            },
                            onDeleteItem = { item ->
                                haptics.click()
                                viewModel.deleteCustomAgendaItem(item)
                            },
                            onOpenSettings = {
                                haptics.click()
                                scrollToSettingsPage()
                            }
                        )
                    }

                    2 -> {
                        // DEDICATED SCREEN: Full-Screen Alarm, Timer & Focus Hub
                        AlarmTimerPandoraStandByPage(
                            isLandscape = isLandscapeDock,
                            currentTimeMillis = currentTimeMillis,
                            use24Hour = settings.use24HourFormat,
                            alarms = alarms,
                            chronoState = chronoState,
                            scheduleItems = unifiedSchedule,
                            isAgentThinking = isAgentThinking,
                            sunriseState = sunriseState,
                            sunriseSimulationEnabled = settings.sunriseSimulationEnabled,
                            onToggleSunriseSimulation = viewModel::toggleSunriseSimulation,
                            onPreviewSunriseDemo = viewModel::previewSunriseDemo,
                            onCancelSunrisePreview = viewModel::cancelSunrisePreview,
                            hapticsEnabled = settings.hapticsEnabled,
                            currentWeather = dualCityWeather.forAnimation(com.example.ui.LocalWeatherAnimationCity.current),
                            onBackToClock = {
                                haptics.tick()
                                scrollToClockPage()
                            },
                            onOpenSchedules = {
                                haptics.click()
                                scrollToSchedulesPage()
                            },
                            onOpenSettings = {
                                haptics.click()
                                scrollToSettingsPage()
                            },
                            onAddAlarm = viewModel::addAlarm,
                            onToggleAlarm = viewModel::toggleAlarm,
                            onDeleteAlarm = viewModel::deleteAlarm,
                            onTriggerAlarmNow = viewModel::triggerAlarmNow,
                            onStartTimer = viewModel::startTimer,
                            onPauseResumeTimer = viewModel::pauseResumeTimer,
                            onStartPandoraTimer = viewModel::startPandoraTimer,
                            onPauseResumePandoraTimer = viewModel::pauseResumePandoraTimer,
                            onSnoozeChrono = { act -> viewModel.snoozeChrono(act) },
                            onStopChrono = viewModel::stopChrono,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        // SWIPE RIGHT PAGE: Full-Screen Industry-Standard Settings Page
                        DockSettingsStandByPage(
                            settings = settings,
                            calendarPermissionGranted = calendarPermissionGranted,
                            syncedAccounts = syncedAccounts,
                            connectedAccounts = connectedAccounts,
                            alarms = alarms,
                            chronoState = chronoState,
                            worldCitySearchResults = worldCitySearchResults,
                            scheduleItems = unifiedSchedule,
                            selectedFilter = providerFilter,
                            notes = notes,
                            agentMessages = agentMessages,
                            isAgentThinking = isAgentThinking,
                            onBackToClock = {
                                haptics.tick()
                                scrollToClockPage()
                            },
                            onOpenChronoScreen = {
                                haptics.click()
                                scrollToChronoPage()
                            },
                            onToggleLandscapeMode = viewModel::toggleLandscapeMode,
                            onSelectClockStyle = viewModel::updateClockStyle,
                            onSelectAgentTheme = viewModel::updateAgentTheme,
                            onToggle24Hour = viewModel::toggle24HourFormat,
                            onToggleKeepAwake = viewModel::toggleKeepScreenAwake,
                            onToggleNightRed = viewModel::toggleNightRedMode,
                            onToggleNightTimeOnly = viewModel::toggleNightTimeOnlyMode,
                            onUpdateNightTimeRange = viewModel::updateNightTimeRange,
                            onToggleShowPortHub = viewModel::toggleShowPortHubTelemetry,
                            onToggleShowAgendaTicker = viewModel::toggleShowAgendaTicker,
                            onChangeDimmer = viewModel::updateBrightnessDimmer,
                            onSelectCity = viewModel::updateWeatherCity,
                            onSelectSecondaryCity = viewModel::updateSecondaryWeatherCity,
                            onSaveDualCities = viewModel::updateDualWeatherCities,
                            onSearchWorldCities = viewModel::searchWorldCities,
                            onSaveLlmConfig = viewModel::updateLocalLlmConfig,
                            onSendAgentPrompt = viewModel::sendAgentQuery,
                            onLaunchPocketPalApp = { prompt ->
                                launchPocketPalOrShare(
                                    context = context,
                                    payload = viewModel.buildPocketPalSharePayload(prompt)
                                )
                            },
                            onClearAgentHistory = viewModel::clearAgentHistory,
                            onSelectFilter = viewModel::setProviderFilter,
                            onRequestCalendarPermission = {
                                calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            },
                            onRefreshCalendars = viewModel::refreshCalendarEvents,
                            onOpenAccountSyncSettings = { openSystemSyncSettings(context) },
                            onOpenAddAccountSettings = { openAddAccountSettings(context) },
                            onAddConnectedAccount = { email, name, provider ->
                                viewModel.addConnectedAccount(email, name, provider)
                            },
                            onToggleConnectedAccountSync = viewModel::toggleConnectedAccountSync,
                            onRemoveConnectedAccount = viewModel::removeConnectedAccount,
                            onAddAlarm = viewModel::addAlarm,
                            onToggleAlarm = viewModel::toggleAlarm,
                            onDeleteAlarm = viewModel::deleteAlarm,
                            onTriggerAlarmNow = viewModel::triggerAlarmNow,
                            onStartTimer = viewModel::startTimer,
                            onPauseResumeTimer = viewModel::pauseResumeTimer,
                            onStartPandoraTimer = viewModel::startPandoraTimer,
                            onPauseResumePandoraTimer = viewModel::pauseResumePandoraTimer,
                            onSnoozeChrono = { act -> viewModel.snoozeChrono(act) },
                            onStopChrono = viewModel::stopChrono,
                            onToggleSunriseSimulation = viewModel::toggleSunriseSimulation,
                            onPreviewSunriseDemo = viewModel::previewSunriseDemo,
                            onToggleHaptics = viewModel::toggleHaptics,
                            onInsertNativeCalendarEvent = { insertSystemCalendarEvent(context) },
                            onOpenAddDockItemDialog = { showAddAgendaDialog = true },
                            onToggleItemCompleted = viewModel::toggleCustomAgendaCompleted,
                            onDeleteCustomItem = viewModel::deleteCustomAgendaItem,
                            onOpenGmailApp = { openExternalPackageOrToast(context, "com.google.android.gm", "Gmail") },
                            onOpenOutlookApp = { openExternalPackageOrToast(context, "com.microsoft.office.outlook", "Microsoft Outlook") },
                            onOpenNewNoteDialog = { showAddNoteDialog = true },
                            onTogglePinNote = viewModel::toggleNotePinned,
                            onDeleteNote = viewModel::deleteNote
                        )
                    }
                }
            }
        }

        if (showControlCenterSheet) {
            DockControlCenterSheet(
                initialSection = controlCenterInitialSection,
                settings = settings,
                calendarPermissionGranted = calendarPermissionGranted,
                syncedAccounts = syncedAccounts,
                connectedAccounts = connectedAccounts,
                alarms = alarms,
                chronoState = chronoState,
                worldCitySearchResults = worldCitySearchResults,
                scheduleItems = unifiedSchedule,
                selectedFilter = providerFilter,
                notes = notes,
                agentMessages = agentMessages,
                isAgentThinking = isAgentThinking,
                onDismiss = { showControlCenterSheet = false },
                onOpenChronoScreen = { scrollToChronoPage() },
                onSelectClockStyle = viewModel::updateClockStyle,
                onSelectAgentTheme = viewModel::updateAgentTheme,
                onToggle24Hour = viewModel::toggle24HourFormat,
                onToggleKeepAwake = viewModel::toggleKeepScreenAwake,
                onToggleNightRed = viewModel::toggleNightRedMode,
                onToggleNightTimeOnly = viewModel::toggleNightTimeOnlyMode,
                onUpdateNightTimeRange = viewModel::updateNightTimeRange,
                onToggleShowPortHub = viewModel::toggleShowPortHubTelemetry,
                onToggleShowAgendaTicker = viewModel::toggleShowAgendaTicker,
                onChangeDimmer = viewModel::updateBrightnessDimmer,
                onSelectCity = viewModel::updateWeatherCity,
                onSelectSecondaryCity = viewModel::updateSecondaryWeatherCity,
                onSaveDualCities = viewModel::updateDualWeatherCities,
                onSearchWorldCities = viewModel::searchWorldCities,
                onSaveLlmConfig = viewModel::updateLocalLlmConfig,
                onSendAgentPrompt = viewModel::sendAgentQuery,
                onLaunchPocketPalApp = { prompt ->
                    launchPocketPalOrShare(
                        context = context,
                        payload = viewModel.buildPocketPalSharePayload(prompt)
                    )
                },
                onClearAgentHistory = viewModel::clearAgentHistory,
                onSelectFilter = viewModel::setProviderFilter,
                onRequestCalendarPermission = {
                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                },
                onRefreshCalendars = viewModel::refreshCalendarEvents,
                onOpenAccountSyncSettings = { openSystemSyncSettings(context) },
                onOpenAddAccountSettings = { openAddAccountSettings(context) },
                onAddConnectedAccount = { email, name, provider ->
                    viewModel.addConnectedAccount(email, name, provider)
                },
                onToggleConnectedAccountSync = viewModel::toggleConnectedAccountSync,
                onRemoveConnectedAccount = viewModel::removeConnectedAccount,
                onAddAlarm = viewModel::addAlarm,
                onToggleAlarm = viewModel::toggleAlarm,
                onDeleteAlarm = viewModel::deleteAlarm,
                onTriggerAlarmNow = viewModel::triggerAlarmNow,
                onStartTimer = viewModel::startTimer,
                onPauseResumeTimer = viewModel::pauseResumeTimer,
                onStartPandoraTimer = viewModel::startPandoraTimer,
                onPauseResumePandoraTimer = viewModel::pauseResumePandoraTimer,
                onSnoozeChrono = { act -> viewModel.snoozeChrono(act) },
                onStopChrono = viewModel::stopChrono,
                onToggleSunriseSimulation = viewModel::toggleSunriseSimulation,
                onPreviewSunriseDemo = viewModel::previewSunriseDemo,
                onToggleHaptics = viewModel::toggleHaptics,
                onInsertNativeCalendarEvent = { insertSystemCalendarEvent(context) },
                onOpenAddDockItemDialog = { showAddAgendaDialog = true },
                onToggleItemCompleted = viewModel::toggleCustomAgendaCompleted,
                onDeleteCustomItem = viewModel::deleteCustomAgendaItem,
                onOpenGmailApp = { openExternalPackageOrToast(context, "com.google.android.gm", "Gmail") },
                onOpenOutlookApp = { openExternalPackageOrToast(context, "com.microsoft.office.outlook", "Microsoft Outlook") },
                onOpenNewNoteDialog = { showAddNoteDialog = true },
                onTogglePinNote = viewModel::toggleNotePinned,
                onDeleteNote = viewModel::deleteNote
            )
        }

        if (showAddAgendaDialog) {
            AddDockAgendaDialog(
                connectedAccounts = connectedAccounts,
                onDismiss = { showAddAgendaDialog = false },
                onConfirm = { title, subtitle, providerType, startOffset, duration, loc ->
                    viewModel.addCustomAgendaItem(
                        title = title,
                        subtitle = subtitle,
                        providerType = providerType,
                        startOffsetMinutes = startOffset,
                        durationMinutes = duration,
                        locationOrSender = loc
                    )
                },
                onConfirmWithWindow = { title, subtitle, providerType, sHour, sMin, eHour, eMin, loc ->
                    viewModel.addCustomAgendaItemWithWindow(
                        title = title,
                        subtitle = subtitle,
                        providerType = providerType,
                        startHour = sHour,
                        startMinute = sMin,
                        endHour = eHour,
                        endMinute = eMin,
                        locationOrSender = loc
                    )
                }
            )
        }

        if (showAddNoteDialog) {
            AddDockNoteDialog(
                onDismiss = { showAddNoteDialog = false },
                onConfirm = { title, content, category, isPinned ->
                    viewModel.addNote(title, content, category, isPinned)
                }
            )
        }
    }
}

@Composable
private fun CompactTacticalTickerCard(
    nextEvents: List<UnifiedScheduleItem>,
    pinnedNote: DockNoteEntity?,
    latestAgentReply: String?,
    isAgentThinking: Boolean,
    onOpenAgentSection: () -> Unit,
    onOpenSyncSection: () -> Unit,
    onOpenNotesSection: () -> Unit,
    onQuickSummarize: () -> Unit
) {
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
        color = Color(0xFF0A1018),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
            )
            .testTag("glanceable_agenda_widget")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenSyncSection() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val firstEvent = nextEvents.firstOrNull()
                    if (firstEvent != null) {
                        val badgeColor = providerColor(firstEvent.providerBadge)
                         androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${timeFmt.format(Date(firstEvent.startTimeMillis))} • ${firstEvent.title}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "No upcoming meetings • Tap to sync Gmail / Outlook",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "AGENDA >",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    color = primaryColor
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0E1722))
                    .clickable { onOpenAgentSection() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (pinnedNote != null) Icons.Default.PushPin else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = RadianiteCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val summaryText = when {
                        isAgentThinking -> "Agent Core synthesizing daily briefing…"
                        !latestAgentReply.isNullOrBlank() -> latestAgentReply.replace("\n", " ").replace("#", "").trim()
                        pinnedNote != null -> "${pinnedNote.title}: ${pinnedNote.content}"
                        else -> "Tap 'BRIEFING' to summarize schedule & notes offline"
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = primaryColor.copy(alpha = 0.2f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onQuickSummarize() }
                        .testTag("quick_summarize_button")
                ) {
                    Text(
                        text = "BRIEFING",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeFitBottomBar(
    nextEvent: UnifiedScheduleItem?,
    onQuickSummarize: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenNotesSettings: () -> Unit
) {
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0A1018),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("glanceable_agenda_widget")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Next Calendar Event pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenSyncSettings() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (nextEvent != null) {
                        "${timeFmt.format(Date(nextEvent.startTimeMillis))} • ${nextEvent.title}"
                    } else {
                        "Calendar Sync Ready"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = primaryColor.copy(alpha = 0.18f),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onQuickSummarize() }
                    .testTag("quick_summarize_button")
            ) {
                Text(
                    text = "AI",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = primaryColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            LandscapeIconChip(
                icon = Icons.Default.Tune,
                desc = "Settings",
                tag = "nav_tab_clock_hub",
                onClick = onOpenDisplaySettings
            )
            LandscapeIconChip(
                icon = Icons.Default.Memory,
                desc = "Agent AI",
                tag = "nav_tab_agent_core",
                onClick = onOpenAgentSettings
            )
            LandscapeIconChip(
                icon = Icons.Default.CalendarMonth,
                desc = "Sync",
                tag = "nav_tab_sync_bridge",
                onClick = onOpenSyncSettings
            )
            LandscapeIconChip(
                icon = Icons.Default.StickyNote2,
                desc = "Notes",
                tag = "nav_tab_dock_notes",
                onClick = onOpenNotesSettings
            )
        }
    }
}

@Composable
private fun LandscapeIconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF111B27),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(5.dp)
                .size(13.dp)
        )
    }
}

@Composable
private fun MinimalSettingsLauncherBar(
    onOpenDisplaySettings: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenNotesSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactLauncherChip(
            icon = Icons.Default.Tune,
            label = "Settings",
            onClick = onOpenDisplaySettings,
            tag = "nav_tab_clock_hub",
            modifier = Modifier.weight(1f)
        )
        CompactLauncherChip(
            icon = Icons.Default.Memory,
            label = "Agent AI",
            onClick = onOpenAgentSettings,
            tag = "nav_tab_agent_core",
            modifier = Modifier.weight(1f)
        )
        CompactLauncherChip(
            icon = Icons.Default.CalendarMonth,
            label = "Sync",
            onClick = onOpenSyncSettings,
            tag = "nav_tab_sync_bridge",
            modifier = Modifier.weight(1f)
        )
        CompactLauncherChip(
            icon = Icons.Default.StickyNote2,
            label = "Notes",
            onClick = onOpenNotesSettings,
            tag = "nav_tab_dock_notes",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactLauncherChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0B121B),
        modifier = modifier
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openSystemSyncSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open Account Sync Settings", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openAddAccountSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openSystemSyncSettings(context)
    }
}

private fun insertSystemCalendarEvent(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "Smart Dock Focus Session")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No system calendar app found", Toast.LENGTH_SHORT).show()
    }
}

private fun openExternalPackageOrToast(context: Context, packageName: String, appLabel: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } else {
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "$appLabel is not installed on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private fun launchPocketPalOrShare(context: Context, payload: String) {
    val pocketPalPackages = listOf("com.pocketpalai", "com.pocketpal")
    for (pkg in pocketPalPackages) {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(pkg)
                putExtra(Intent.EXTRA_TEXT, payload)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(sendIntent)
                return
            } catch (_: Exception) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
    }

    try {
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Pixel 6a Smart Dock Context")
                putExtra(Intent.EXTRA_TEXT, payload)
            },
            "Send Dock Context to PocketPal AI / Local LLM"
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to launch external share target", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Page 1 (Swipe Left from Clock & Weather): Full-Screen Meeting Schedules Dashboard.
 * Adapts to both Landscape (2-column split: Next/Live Meeting Spotlight on Left, Scrollable Timeline on Right)
 * and Portrait (Vertical stack).
 */
@Composable
private fun MeetingSchedulesStandByPage(
    isLandscape: Boolean,
    currentTimeMillis: Long,
    use24Hour: Boolean,
    calendarPermissionGranted: Boolean,
    scheduleItems: List<UnifiedScheduleItem>,
    selectedFilter: AccountProviderBadge?,
    onSelectFilter: (AccountProviderBadge?) -> Unit,
    onBackToClock: () -> Unit,
    onOpenChronoScreen: () -> Unit = {},
    onOpenAddMeeting: () -> Unit,
    onQuickStartMeetingNow: () -> Unit,
    onQuickScheduleActivity: (AiToyActivity) -> Unit,
    onRequestOrRefreshCalendar: () -> Unit,
    onToggleCompleted: (UnifiedScheduleItem) -> Unit,
    onDeleteItem: (UnifiedScheduleItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val timePattern = if (use24Hour) "HH:mm" else "h:mm a"
    val clockStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(currentTimeMillis))
    val itemTimeFmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

    val activeOrNextMeeting = scheduleItems.firstOrNull { !it.isCompleted && it.endTimeMillis >= currentTimeMillis }
        ?: scheduleItems.firstOrNull()
    val isLiveNow = activeOrNextMeeting != null &&
        currentTimeMillis in activeOrNextMeeting.startTimeMillis..activeOrNextMeeting.endTimeMillis

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1722),
                        Color(0xFF060A10)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("meeting_schedules_page")
    ) {
        // Top Header Bar: Back to Clock + Title + Provider Filters + Add/Sync Actions
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
                        .testTag("btn_back_to_clock")
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

                Text(
                    text = "MEETING SCHEDULES",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScheduleFilterPill(
                    label = "ALL",
                    selected = selectedFilter == null,
                    accent = RadianiteCyan,
                    onClick = { onSelectFilter(null) }
                )
                ScheduleFilterPill(
                    label = "GMAIL",
                    selected = selectedFilter == AccountProviderBadge.GMAIL,
                    accent = GmailCoral,
                    onClick = { onSelectFilter(AccountProviderBadge.GMAIL) }
                )
                ScheduleFilterPill(
                    label = "OUTLOOK",
                    selected = selectedFilter == AccountProviderBadge.OUTLOOK,
                    accent = OutlookBlue,
                    onClick = { onSelectFilter(AccountProviderBadge.OUTLOOK) }
                )
                ScheduleFilterPill(
                    label = "DOCK",
                    selected = selectedFilter == AccountProviderBadge.SYSTEM,
                    accent = ChargingEmerald,
                    onClick = { onSelectFilter(AccountProviderBadge.SYSTEM) }
                )

                IconButton(
                    onClick = onOpenAddMeeting,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .testTag("btn_add_meeting_page")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Meeting",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenChronoScreen,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF121C28))
                        .testTag("btn_open_chrono_from_schedules")
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Alarm, Timer & Focus",
                        tint = KilljoyGold,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onRequestOrRefreshCalendar,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF121C28))
                        .testTag("btn_refresh_meeting_page")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Calendars",
                        tint = RadianiteCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF121C28))
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Calendar Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLandscape) {
            // Landscape 2-Column Split:
            // Left: Next / Active Meeting Spotlight + Quick Actions
            // Right: Scrollable Meeting Schedule List
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MeetingSpotlightPanel(
                    meeting = activeOrNextMeeting,
                    isLiveNow = isLiveNow,
                    calendarPermissionGranted = calendarPermissionGranted,
                    timeFmt = itemTimeFmt,
                    onOpenAddMeeting = onOpenAddMeeting,
                    onQuickStartMeetingNow = onQuickStartMeetingNow,
                    onQuickScheduleActivity = onQuickScheduleActivity,
                    onRequestOrRefreshCalendar = onRequestOrRefreshCalendar,
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                )

                MeetingTimelineList(
                    scheduleItems = scheduleItems,
                    timeFmt = itemTimeFmt,
                    onToggleCompleted = onToggleCompleted,
                    onDeleteItem = onDeleteItem,
                    onOpenAddMeeting = onOpenAddMeeting,
                    onQuickStartMeetingNow = onQuickStartMeetingNow,
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Portrait Stack: Spotlight on Top, Timeline List Below
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeetingSpotlightPanel(
                    meeting = activeOrNextMeeting,
                    isLiveNow = isLiveNow,
                    calendarPermissionGranted = calendarPermissionGranted,
                    timeFmt = itemTimeFmt,
                    onOpenAddMeeting = onOpenAddMeeting,
                    onQuickStartMeetingNow = onQuickStartMeetingNow,
                    onQuickScheduleActivity = onQuickScheduleActivity,
                    onRequestOrRefreshCalendar = onRequestOrRefreshCalendar,
                    modifier = Modifier.fillMaxWidth()
                )

                MeetingTimelineList(
                    scheduleItems = scheduleItems,
                    timeFmt = itemTimeFmt,
                    onToggleCompleted = onToggleCompleted,
                    onDeleteItem = onDeleteItem,
                    onOpenAddMeeting = onOpenAddMeeting,
                    onQuickStartMeetingNow = onQuickStartMeetingNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScheduleFilterPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else Color(0xFF101924),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun MeetingSpotlightPanel(
    meeting: UnifiedScheduleItem?,
    isLiveNow: Boolean,
    calendarPermissionGranted: Boolean,
    timeFmt: SimpleDateFormat,
    onOpenAddMeeting: () -> Unit,
    onQuickStartMeetingNow: () -> Unit,
    onQuickScheduleActivity: (AiToyActivity) -> Unit,
    onRequestOrRefreshCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isLiveNow) GmailCoral else RadianiteCyan

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A111A))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = if (isLiveNow) "LIVE MEETING NOW" else "NEXT SCHEDULED MEETING",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (meeting != null) {
                val badgeColor = providerColor(meeting.providerBadge)
                Text(
                    text = meeting.title,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (meeting.isAllDay) {
                        "ALL DAY"
                    } else {
                        "${timeFmt.format(Date(meeting.startTimeMillis))} – ${
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(meeting.endTimeMillis))
                        }"
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = badgeColor
                )
                if (meeting.location.isNotBlank() || meeting.accountName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        // Hide raw join URLs in the location line; the Join button handles them.
                        text = listOf(
                            meeting.location.takeUnless { loc -> meeting.joinUrl != null && loc.contains("http") }.orEmpty(),
                            meeting.accountName
                        )
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (meeting.alsoInAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Also in: ${meeting.alsoInAccounts.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val joinUrl = meeting.joinUrl
                if (joinUrl != null && !meeting.isCompleted) {
                    val joinContext = androidx.compose.ui.platform.LocalContext.current
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (!com.example.data.calendar.MeetingLinks.open(joinContext, joinUrl)) {
                                Toast.makeText(joinContext, "No app found to open this meeting link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ChargingEmerald),
                        modifier = Modifier.testTag("btn_join_meeting")
                    ) {
                        Text(
                            text = "Join ${com.example.data.calendar.MeetingLinks.platformLabel(joinUrl)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF04110B)
                        )
                    }
                }
            } else {
                Text(
                    text = "No Active Meetings",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your schedule is clear. Add a meeting block or sync your device calendars.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "QUICK SCHEDULE ANIMATIONS",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    AiToyActivity.EATING to "Lunch",
                    AiToyActivity.GYM to "Gym",
                    AiToyActivity.SLEEPING to "Sleep",
                    AiToyActivity.BUSY_MEETING to "Meeting"
                ).forEach { (act, shortLabel) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = act.accentColor.copy(alpha = 0.16f),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onQuickScheduleActivity(act) }
                            .testTag("quick_schedule_${act.name.lowercase()}")
                    ) {
                        Text(
                            text = shortLabel,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = act.accentColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAddMeeting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Schedule", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onQuickStartMeetingNow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("45m Meeting", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (!calendarPermissionGranted) {
                OutlinedButton(
                    onClick = onRequestOrRefreshCalendar,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connect Device Calendar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MeetingTimelineList(
    scheduleItems: List<UnifiedScheduleItem>,
    timeFmt: SimpleDateFormat,
    onToggleCompleted: (UnifiedScheduleItem) -> Unit,
    onDeleteItem: (UnifiedScheduleItem) -> Unit,
    onOpenAddMeeting: () -> Unit,
    onQuickStartMeetingNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (scheduleItems.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0A111A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = RadianiteCyan,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No Meetings Scheduled",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap '+ New Meeting' or '45m Now' to add a meeting to your schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(scheduleItems, key = { it.id }) { item ->
                ScheduleItemCard(
                    item = item,
                    timeFmt = timeFmt,
                    onToggleCompleted = { onToggleCompleted(item) },
                    onDelete = { onDeleteItem(item) }
                )
            }
        }
    }
}
