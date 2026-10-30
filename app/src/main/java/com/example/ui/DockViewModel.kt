package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmRingBus
import com.example.alarm.AlarmRingService
import com.example.alarm.AlarmRingStatus
import com.example.alarm.AlarmScheduler
import com.example.alarm.ChronoChime
import com.example.data.DockRepository
import com.example.data.agent.LocalLlmBridge
import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.NativeCalendarReader
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.hardware.BatteryTelemetry
import com.example.data.hardware.BatteryTelemetryMonitor
import com.example.data.local.AgentMessageEntity
import com.example.data.local.ConnectedAccountEntity
import com.example.data.local.DockAgendaEntity
import com.example.data.local.DockAlarmEntity
import com.example.data.local.DockDatabase
import com.example.data.local.DockNoteEntity
import com.example.data.preferences.DockPreferencesRepository
import com.example.data.preferences.DockSettings
import com.example.data.weather.CityPreset
import com.example.data.weather.DualCityWeather
import com.example.data.weather.WeatherRepository
import com.example.data.weather.WeatherStationData
import com.example.ui.components.ActiveChronoHudState
import com.example.ui.components.AiToyActivity
import com.example.ui.components.PandoraPhase
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DockTab {
    CLOCK_HUB,
    AGENT_CORE,
    SYNC_BRIDGE,
    DOCK_NOTES
}

data class SunriseState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val nextAlarmLabel: String = "",
    val nextAlarmTimeText: String = "",
    val isPreview: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DockViewModel(
    private val appContext: Context,
    private val repository: DockRepository,
    private val preferencesRepository: DockPreferencesRepository,
    batteryMonitor: BatteryTelemetryMonitor,
    private val wellnessRepository: com.example.wellness.WellnessRepository
) : ViewModel() {

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    private val _selectedTab = MutableStateFlow(DockTab.CLOCK_HUB)
    val selectedTab: StateFlow<DockTab> = _selectedTab.asStateFlow()

    private val _providerFilter = MutableStateFlow<AccountProviderBadge?>(null)
    val providerFilter: StateFlow<AccountProviderBadge?> = _providerFilter.asStateFlow()

    private val _calendarPermissionGranted = MutableStateFlow(repository.hasCalendarPermission())
    val calendarPermissionGranted: StateFlow<Boolean> = _calendarPermissionGranted.asStateFlow()

    private val _nativeEvents = MutableStateFlow<List<UnifiedScheduleItem>>(emptyList())
    private val _deviceSyncedAccounts = MutableStateFlow<List<Pair<String, AccountProviderBadge>>>(emptyList())

    val connectedAccounts: StateFlow<List<ConnectedAccountEntity>> = repository.connectedAccountsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val alarms: StateFlow<List<DockAlarmEntity>> = repository.alarmsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val syncedAccounts: StateFlow<List<Pair<String, AccountProviderBadge>>> = combine(
        _deviceSyncedAccounts,
        connectedAccounts
    ) { deviceAccounts, userAccounts ->
        val mappedUser = userAccounts.filter { it.isSyncEnabled }.map { acc ->
            val badge = if (acc.providerType.equals("OUTLOOK", ignoreCase = true)) {
                AccountProviderBadge.OUTLOOK
            } else {
                AccountProviderBadge.GMAIL
            }
            acc.emailAddress to badge
        }
        (mappedUser + deviceAccounts).distinctBy { it.first.lowercase() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _chronoState = MutableStateFlow(ActiveChronoHudState())
    val chronoState: StateFlow<ActiveChronoHudState> = _chronoState.asStateFlow()

    private var activeRingingAlarmId: Long? = null

    // Timers are driven by absolute end times so they never drift and survive slow ticks.
    private var timerEndAtMillis: Long? = null
    private var pandoraEndAtMillis: Long? = null

    // Manual companion overrides (from swiping the stage) expire so the calendar takes over again.
    private var manualOverrideSetAtMillis: Long = 0L

    // --- Dismiss challenge, morning briefing, companion sheet, sleep sounds ---
    data class MathChallenge(val a: Int, val b: Int) {
        val question: String get() = "$a + $b"
        val answer: Int get() = a + b
    }

    private val _alarmChallenge = MutableStateFlow<MathChallenge?>(null)
    val alarmChallenge: StateFlow<MathChallenge?> = _alarmChallenge.asStateFlow()

    private val _morningBriefing = MutableStateFlow<String?>(null)
    val morningBriefing: StateFlow<String?> = _morningBriefing.asStateFlow()

    private val _companionSheetOpen = MutableStateFlow(false)
    val companionSheetOpen: StateFlow<Boolean> = _companionSheetOpen.asStateFlow()

    private val _reliabilitySheetOpen = MutableStateFlow(false)
    val reliabilitySheetOpen: StateFlow<Boolean> = _reliabilitySheetOpen.asStateFlow()

    val sleepSoundPlayer = com.example.alarm.SleepSoundPlayer()

    private var tts: android.speech.tts.TextToSpeech? = null

    // ---------------------------------------------------------------------------------------
    // Wellness: gentle nudges acted out by the companion
    // ---------------------------------------------------------------------------------------
    private val wellnessEngine = com.example.wellness.WellnessEngine()

    val wellnessSettings: StateFlow<com.example.wellness.WellnessSettings> = wellnessRepository.settingsFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, com.example.wellness.WellnessSettings()
    )
    private val wellnessLogs = wellnessRepository.recentLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val priorities = wellnessRepository.prioritiesToday().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wellnessStats: StateFlow<com.example.wellness.WellnessStats> = combine(
        wellnessLogs, priorities, wellnessSettings
    ) { logs, prios, ws ->
        com.example.wellness.WellnessRepository.computeStats(logs, prios, ws.waterGoal)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.wellness.WellnessStats())

    private val _nudgeQueue = MutableStateFlow<List<com.example.wellness.Nudge>>(emptyList())
    val nudgeQueue: StateFlow<List<com.example.wellness.Nudge>> = _nudgeQueue.asStateFlow()

    private val _activeNudge = MutableStateFlow<com.example.wellness.Nudge?>(null)
    val activeNudge: StateFlow<com.example.wellness.Nudge?> = _activeNudge.asStateFlow()

    /** True during the first 20s of a reminder (or after tapping to reopen); false when shrunk to a floating icon. */
    private val _isBubbleExpanded = MutableStateFlow(true)
    val isBubbleExpanded: StateFlow<Boolean> = _isBubbleExpanded.asStateFlow()
    private var bubbleExpandedAtMillis: Long = 0L

    /** Reminders that auto-expired while the user was away or didn't respond. */
    private val _missedNudges = MutableStateFlow<List<com.example.wellness.MissedNudge>>(emptyList())
    val missedNudges: StateFlow<List<com.example.wellness.MissedNudge>> = _missedNudges.asStateFlow()

    private val _whileAwayBannerDismissed = MutableStateFlow(false)
    val whileAwayBannerDismissed: StateFlow<Boolean> = _whileAwayBannerDismissed.asStateFlow()

    /** Protected screen bounds of the clock digits in window coordinates so reminders never cover the time. */
    private val _clockProtectedBounds = MutableStateFlow(androidx.compose.ui.geometry.Rect.Zero)
    val clockProtectedBounds: StateFlow<androidx.compose.ui.geometry.Rect> = _clockProtectedBounds.asStateFlow()

    fun updateClockProtectedBounds(bounds: androidx.compose.ui.geometry.Rect) {
        if (_clockProtectedBounds.value != bounds) {
            _clockProtectedBounds.value = bounds
        }
    }

    /** "STRETCH" or "BREATHE" while a guided session is open. */
    private val _guidedSession = MutableStateFlow<String?>(null)
    val guidedSession: StateFlow<String?> = _guidedSession.asStateFlow()

    private val _top3DialogOpen = MutableStateFlow(false)
    val top3DialogOpen: StateFlow<Boolean> = _top3DialogOpen.asStateFlow()

    private val _wellnessSheetOpen = MutableStateFlow(false)
    val wellnessSheetOpen: StateFlow<Boolean> = _wellnessSheetOpen.asStateFlow()

    private val _companionHat = MutableStateFlow<String?>(null)
    val companionHat: StateFlow<String?> = _companionHat.asStateFlow()

    /** Emits when the companion should celebrate (hop + hearts). */
    val celebrate = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 2)

    // --- Premium, pet twin, kids mode ---
    val isPremium: StateFlow<Boolean> = com.example.premium.PremiumManager.isPremium

    private val petTwinRepository = com.example.pettwin.PetTwinRepository(appContext)
    val petTwin: StateFlow<com.example.pettwin.PetTwin?> =
        petTwinRepository.twinFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _paywallOpen = MutableStateFlow(false)
    val paywallOpen: StateFlow<Boolean> = _paywallOpen.asStateFlow()

    private val _petTwinOpen = MutableStateFlow(false)
    val petTwinOpen: StateFlow<Boolean> = _petTwinOpen.asStateFlow()

    private val _kidsSheetOpen = MutableStateFlow(false)
    val kidsSheetOpen: StateFlow<Boolean> = _kidsSheetOpen.asStateFlow()

    fun openPaywall() { _paywallOpen.value = true }
    fun closePaywall() { _paywallOpen.value = false }

    /** Opens the pet twin editor, or the paywall when Premium isn't unlocked. */
    fun openPetTwin() {
        if (isPremium.value) _petTwinOpen.value = true else _paywallOpen.value = true
    }
    fun closePetTwin() { _petTwinOpen.value = false }

    fun savePetTwin(twin: com.example.pettwin.PetTwin) {
        viewModelScope.launch {
            petTwinRepository.save(twin)
            if (twin.name.isNotBlank()) {
                preferencesRepository.setCompanion(true, settings.value.catCoat, twin.name)
            }
        }
        _petTwinOpen.value = false
        celebrate.tryEmit(Unit)
    }

    fun setPetTwinEnabled(enabled: Boolean) {
        val current = petTwin.value ?: return
        viewModelScope.launch { petTwinRepository.save(current.copy(enabled = enabled)) }
    }

    fun openKidsSheet() { _kidsSheetOpen.value = true }
    fun closeKidsSheet() { _kidsSheetOpen.value = false }

    fun saveKidsMode(enabled: Boolean, bedHour: Int, bedMinute: Int, wakeHour: Int, wakeMinute: Int) {
        viewModelScope.launch { preferencesRepository.setKidsMode(enabled, bedHour, bedMinute, wakeHour, wakeMinute) }
        _kidsSheetOpen.value = false
    }

    private var appVisible = true
    private var deskSessionStart = System.currentTimeMillis()
    private var hiddenSince = 0L
    private var lastWellnessMinute = -1L

    private val _calendarError = MutableStateFlow<String?>(null)
    val calendarError: StateFlow<String?> = _calendarError.asStateFlow()

    private val _worldCitySearchResults = MutableStateFlow<List<CityPreset>>(WeatherRepository.CITY_PRESETS)
    val worldCitySearchResults: StateFlow<List<CityPreset>> = _worldCitySearchResults.asStateFlow()

    private val _weatherState = MutableStateFlow<WeatherStationData?>(null)
    val weatherState: StateFlow<WeatherStationData?> = _weatherState.asStateFlow()

    private val _dualCityWeather = MutableStateFlow(DualCityWeather(null, null))
    val dualCityWeather: StateFlow<DualCityWeather> = _dualCityWeather.asStateFlow()

    private val _isWeatherLoading = MutableStateFlow(false)
    val isWeatherLoading: StateFlow<Boolean> = _isWeatherLoading.asStateFlow()

    private val _isAgentThinking = MutableStateFlow(false)
    val isAgentThinking: StateFlow<Boolean> = _isAgentThinking.asStateFlow()

    private val _manualToyActivity = MutableStateFlow<AiToyActivity?>(null)
    val manualToyActivity: StateFlow<AiToyActivity?> = _manualToyActivity.asStateFlow()

    val settings: StateFlow<DockSettings> = preferencesRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DockSettings()
    )

    private val _previewSunriseProgress = MutableStateFlow<Float?>(null)

    val sunriseState: StateFlow<SunriseState> = combine(
        _currentTimeMillis,
        alarms,
        settings,
        _previewSunriseProgress
    ) { nowMillis, alarmList, dockSettings, previewProgress ->
        if (previewProgress != null) {
            val clamped = previewProgress.coerceIn(0f, 1f)
            return@combine SunriseState(
                isActive = true,
                progress = clamped,
                nextAlarmLabel = "Sunrise Dawn Simulation",
                nextAlarmTimeText = "PREVIEW",
                isPreview = true
            )
        }

        if (!dockSettings.sunriseSimulationEnabled) {
            return@combine SunriseState(isActive = false, progress = 0f)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val nowExactMins = nowMins + (cal.get(Calendar.SECOND) / 60f)

        val enabledAlarms = alarmList.filter { it.isEnabled }
        if (enabledAlarms.isEmpty()) {
            return@combine SunriseState(isActive = false, progress = 0f)
        }

        var nearestAlarm: DockAlarmEntity? = null
        var minDiff = Float.MAX_VALUE

        for (alarm in enabledAlarms) {
            val alarmMins = alarm.hour * 60 + alarm.minute
            var diff = alarmMins - nowExactMins
            if (diff < 0f) diff += 1440f
            if (diff < minDiff) {
                minDiff = diff
                nearestAlarm = alarm
            }
        }

        if (nearestAlarm != null && minDiff in 0f..30f) {
            val progress = ((30f - minDiff) / 30f).coerceIn(0f, 1f)
            SunriseState(
                isActive = true,
                progress = progress,
                nextAlarmLabel = nearestAlarm.label,
                nextAlarmTimeText = "%02d:%02d".format(nearestAlarm.hour, nearestAlarm.minute),
                isPreview = false
            )
        } else {
            SunriseState(
                isActive = false,
                progress = 0f,
                nextAlarmLabel = nearestAlarm?.label.orEmpty(),
                nextAlarmTimeText = nearestAlarm?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty(),
                isPreview = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SunriseState()
    )

    val batteryTelemetry: StateFlow<BatteryTelemetry> = batteryMonitor.observeTelemetry().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BatteryTelemetry()
    )

    private val cameraCoordinator = CameraActivityCoordinator.getInstance(appContext)

    val cameraActivityUiState: StateFlow<CameraActivityUiState> = cameraCoordinator.uiState
    val cameraActivityStatus: StateFlow<CameraActivityStatus> = cameraCoordinator.activityStatus
    val webcamState: StateFlow<com.example.data.hardware.WebcamTelemetryState> = cameraCoordinator.webcamState
    val isWebcamPreview: StateFlow<Boolean> = cameraCoordinator.isWebcamPreview
    val isWebcamActive: StateFlow<Boolean> = cameraCoordinator.isWebcamActive

    /**
     * Activates webcam indicator mode. When [durationMs] <= 0 (default), stays permanently on
     * until [stopWebcamPreview] or [toggleWebcamPreview] is called.
     */
    fun triggerWebcamPreview(durationMs: Long = 0L) {
        cameraCoordinator.triggerWebcamPreview(durationMs)
    }

    fun toggleWebcamPreview() {
        cameraCoordinator.toggleWebcamPreview()
    }

    fun stopWebcamPreview() {
        cameraCoordinator.stopWebcamPreview()
    }

    // Lock-screen / Screen-off Ambient Screensaver state & smooth ambient light sensor lux
    private val _isScreensaverOverlayActive = MutableStateFlow(false)
    val isScreensaverOverlayActive: StateFlow<Boolean> = _isScreensaverOverlayActive.asStateFlow()

    private val _ambientLux = MutableStateFlow(120f)
    val ambientLux: StateFlow<Float> = _ambientLux.asStateFlow()

    fun activateScreensaverMode() {
        _isScreensaverOverlayActive.value = true
    }

    fun exitScreensaverMode() {
        _isScreensaverOverlayActive.value = false
    }

    fun updateAmbientLux(lux: Float) {
        _ambientLux.value = lux.coerceAtLeast(0f)
    }

    val notes: StateFlow<List<DockNoteEntity>> = repository.notesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val agentMessages: StateFlow<List<AgentMessageEntity>> = repository.agentMessagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unifiedSchedule: StateFlow<List<UnifiedScheduleItem>> = combine(
        _nativeEvents,
        repository.customAgendaFlow,
        _providerFilter,
        connectedAccounts
    ) { nativeList, customEntities, filter, accounts ->
        val disabledEmails = accounts.filter { !it.isSyncEnabled }.map { it.emailAddress.lowercase() }.toSet()
        val mappedCustom = customEntities.map { entity ->
            entity.toUnifiedScheduleItem()
        }
        val merged = (nativeList + mappedCustom)
            .filter { item -> item.accountName.lowercase() !in disabledEmails }
            .sortedWith(
                compareBy<UnifiedScheduleItem> { it.isCompleted }.thenBy { it.startTimeMillis }
            )
        if (filter == null) {
            merged
        } else {
            merged.filter { it.providerBadge == filter }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultAccountsAndAlarmsSeeded()
        }

        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _currentTimeMillis.value = now
                tickChronoAndAlarms(now)
                // Wake up right after the next second boundary so the clock never lags.
                delay((1000L - (System.currentTimeMillis() % 1000L)).coerceAtLeast(20L) + 5L)
            }
        }

        // Keep the system AlarmManager schedule in sync with the alarm list (add/edit/toggle/delete).
        viewModelScope.launch {
            repository.alarmsFlow
                .distinctUntilChanged()
                .collect { list ->
                    try {
                        AlarmScheduler.rescheduleAll(appContext, list)
                    } catch (e: Exception) {
                        android.util.Log.e("DockViewModel", "Alarm scheduling failed", e)
                    }
                    restoreSnoozeFromDatabase(list)
                }
        }

        // Mirror the system alarm service state (ringing / snoozed / idle) into the HUD state.
        viewModelScope.launch {
            AlarmRingBus.status.collect { status -> applyAlarmStatus(status) }
        }

        // Refresh automatically when calendars sync, instead of only on resume.
        viewModelScope.launch {
            _calendarPermissionGranted
                .flatMapLatest { granted -> if (granted) repository.observeCalendarChanges() else emptyFlow() }
                .debounce(1500L)
                .collect { refreshCalendarEvents() }
        }

        // Periodic 15-minute refresh for both calendar events and live dual-city weather.
        viewModelScope.launch {
            while (isActive) {
                delay(15 * 60_000L)
                refreshCalendarEvents()
                refreshDualCitiesWeather()
            }
        }

        refreshCalendarEvents()
        refreshDualCitiesWeather()

        viewModelScope.launch {
            preferencesRepository.settingsFlow
                .map { it.cityName to it.secondaryCityName }
                .distinctUntilChanged()
                .collect { (primaryCity, secondaryCity) ->
                    refreshDualCitiesWeather(primaryCity, secondaryCity)
                }
        }
    }

    private fun tickChronoAndAlarms(nowMillis: Long) {
        // 1. Countdown timer (derived from its end time)
        val timerEnd = timerEndAtMillis
        if (_chronoState.value.isTimerRunning && timerEnd != null) {
            val remaining = secondsUntil(timerEnd, nowMillis)
            if (remaining <= 0) {
                timerEndAtMillis = null
                _chronoState.update {
                    it.copy(
                        timerRemainingSeconds = 0,
                        isTimerRunning = false,
                        isTimerRinging = true
                    )
                }
                _manualToyActivity.value = AiToyActivity.TIMER
                ChronoChime.play(appContext)
                playCompanionCue(com.example.alarm.CompanionSoundCue.TIMER_DONE, nowMillis, ignoreAlarmCheck = true)
            } else if (remaining != _chronoState.value.timerRemainingSeconds) {
                _chronoState.update { it.copy(timerRemainingSeconds = remaining) }
            }
        }

        // 2. Pandora (Pomodoro) focus timer
        val pandoraEnd = pandoraEndAtMillis
        if (_chronoState.value.isPandoraRunning && pandoraEnd != null) {
            val remaining = secondsUntil(pandoraEnd, nowMillis)
            if (remaining <= 0) {
                pandoraEndAtMillis = null
                _chronoState.update {
                    it.copy(
                        pandoraRemainingSeconds = 0,
                        isPandoraRunning = false,
                        isPandoraRinging = true,
                        pandoraCompletedCycles = it.pandoraCompletedCycles + 1
                    )
                }
                _manualToyActivity.value = AiToyActivity.PANDORA_TIMER
                ChronoChime.play(appContext)
                playCompanionCue(com.example.alarm.CompanionSoundCue.FOCUS_DONE, nowMillis, ignoreAlarmCheck = true)
            } else if (remaining != _chronoState.value.pandoraRemainingSeconds) {
                _chronoState.update { it.copy(pandoraRemainingSeconds = remaining) }
            }
        }

        // 3. Alarms are fired by AlarmManager + AlarmRingService (works with the app closed).
        //    Their state reaches this ViewModel through AlarmRingBus.

        // 4. Expire stale manual companion overrides so calendar events drive the companion again.
        expireManualOverrideIfNeeded(nowMillis)

        // 5. Auto-shrink reminder bubble after 20 seconds and expire stale reminders every second.
        tickNudgeLifecycle(nowMillis)

        // 6. Wellness nudges, evaluated once a minute.
        val minuteKey = nowMillis / 60_000L
        if (minuteKey != lastWellnessMinute) {
            lastWellnessMinute = minuteKey
            evaluateWellness(nowMillis)
        }
    }

    private fun secondsUntil(endMillis: Long, nowMillis: Long): Int {
        val diff = endMillis - nowMillis
        return if (diff <= 0L) 0 else ((diff + 999L) / 1000L).toInt()
    }

    private fun expireManualOverrideIfNeeded(nowMillis: Long) {
        val manual = _manualToyActivity.value ?: return
        if (manual == AiToyActivity.ALARM || manual == AiToyActivity.TIMER || manual == AiToyActivity.PANDORA_TIMER) {
            return // cleared by stop/snooze handlers
        }
        val setAt = manualOverrideSetAtMillis
        val expired = setAt > 0L && nowMillis - setAt > MANUAL_OVERRIDE_TTL_MS
        val newEventStarted = unifiedSchedule.value.any { ev ->
            !ev.isCompleted && !ev.isAllDay && ev.startTimeMillis in (setAt + 1)..nowMillis && ev.endTimeMillis > nowMillis
        }
        if (expired || newEventStarted) {
            _manualToyActivity.value = null
        }
    }

    private fun applyAlarmStatus(status: AlarmRingStatus) {
        when (status) {
            is AlarmRingStatus.Ringing -> {
                activeRingingAlarmId = status.alarmId.takeIf { it > 0 }
                _chronoState.update {
                    it.copy(
                        isAlarmRinging = true,
                        ringingAlarmLabel = status.label,
                        ringingAlarmTimeText = status.timeText.ifBlank { it.ringingAlarmTimeText },
                        snoozedAlarmUntilMillis = null
                    )
                }
                _manualToyActivity.value = AiToyActivity.ALARM
            }

            is AlarmRingStatus.Snoozed -> {
                activeRingingAlarmId = status.alarmId.takeIf { it > 0 }
                _chronoState.update {
                    it.copy(
                        isAlarmRinging = false,
                        ringingAlarmLabel = status.label,
                        ringingAlarmTimeText = status.timeText.ifBlank { it.ringingAlarmTimeText },
                        snoozedAlarmUntilMillis = status.untilMillis,
                        snoozedAlarmLabel = status.label
                    )
                }
            }

            AlarmRingStatus.Idle -> {
                _chronoState.update { it.copy(isAlarmRinging = false, snoozedAlarmUntilMillis = null) }
                if (_manualToyActivity.value == AiToyActivity.ALARM) {
                    _manualToyActivity.value = null
                }
            }
        }
    }

    /** After a process restart, show a still-pending snooze from the database. */
    private fun restoreSnoozeFromDatabase(list: List<DockAlarmEntity>) {
        if (AlarmRingBus.status.value != AlarmRingStatus.Idle) return
        val now = System.currentTimeMillis()
        val snoozed = list.firstOrNull { it.isEnabled && (it.snoozedUntilMillis ?: 0L) > now } ?: return
        AlarmRingBus.update(
            AlarmRingStatus.Snoozed(
                alarmId = snoozed.id,
                label = snoozed.label,
                timeText = AlarmScheduler.timeText(snoozed.hour, snoozed.minute),
                untilMillis = snoozed.snoozedUntilMillis ?: now
            )
        )
    }

    // --- Connected Gmail & Outlook Accounts Management ---

    fun addConnectedAccount(
        emailAddress: String,
        displayName: String,
        providerType: String,
        seedSampleSchedule: Boolean = true
    ) {
        if (emailAddress.isBlank()) return
        viewModelScope.launch {
            repository.addConnectedAccount(
                emailAddress = emailAddress,
                displayName = displayName,
                providerType = providerType,
                seedSampleSchedule = seedSampleSchedule
            )
        }
    }

    fun toggleConnectedAccountSync(account: ConnectedAccountEntity) {
        viewModelScope.launch {
            repository.toggleConnectedAccountSync(account)
        }
    }

    fun removeConnectedAccount(account: ConnectedAccountEntity) {
        viewModelScope.launch {
            repository.removeConnectedAccount(account)
        }
    }

    // --- Alarms, Countdown Timer & Pandora Timer Management ---

    fun addAlarm(hour: Int, minute: Int, label: String) {
        viewModelScope.launch {
            repository.addAlarm(hour, minute, label)
        }
    }

    fun toggleAlarm(alarm: DockAlarmEntity) {
        viewModelScope.launch {
            repository.toggleAlarmEnabled(alarm)
        }
    }

    fun deleteAlarm(id: Long) {
        viewModelScope.launch {
            repository.deleteAlarm(id)
        }
    }

    fun triggerAlarmNow(alarm: DockAlarmEntity? = null) {
        val target = alarm ?: alarms.value.firstOrNull()
        val timeText = if (target != null) {
            AlarmScheduler.timeText(target.hour, target.minute)
        } else {
            val cal = Calendar.getInstance()
            AlarmScheduler.timeText(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
        // Rings through the same service as scheduled alarms, so the test is realistic (sound,
        // vibration, notification). State arrives back via AlarmRingBus.
        AlarmRingService.startRinging(
            context = appContext,
            alarmId = target?.id ?: -1L,
            label = target?.label ?: "Smart Dock Alarm",
            timeText = timeText,
            vibrateOnly = target?.vibrateOnly ?: false,
            challenge = target?.dismissChallenge ?: false,
            soundUri = target?.soundUri
        )
    }

    fun startTimer(durationSeconds: Int, label: String = "Countdown Timer") {
        val safeSecs = durationSeconds.coerceAtLeast(5)
        _chronoState.update {
            it.copy(
                isTimerRunning = true,
                isTimerRinging = false,
                timerTotalSeconds = safeSecs,
                timerRemainingSeconds = safeSecs,
                timerLabel = label
            )
        }
        timerEndAtMillis = System.currentTimeMillis() + safeSecs * 1000L
        _manualToyActivity.value = AiToyActivity.TIMER
    }

    fun pauseResumeTimer() {
        updateTimerAndSyncEnd { state ->
            if (state.timerRemainingSeconds <= 0) {
                state.copy(
                    isTimerRunning = true,
                    isTimerRinging = false,
                    timerRemainingSeconds = state.timerTotalSeconds.coerceAtLeast(60)
                )
            } else {
                state.copy(
                    isTimerRunning = !state.isTimerRunning,
                    isTimerRinging = false
                )
            }
        }
    }

    fun startPandoraTimer(
        durationMinutes: Int = 25,
        phase: PandoraPhase = PandoraPhase.FOCUS
    ) {
        val totalSecs = (durationMinutes.coerceAtLeast(1)) * 60
        _chronoState.update {
            it.copy(
                isPandoraRunning = true,
                isPandoraRinging = false,
                pandoraPhase = phase,
                pandoraTotalSeconds = totalSecs,
                pandoraRemainingSeconds = totalSecs
            )
        }
        pandoraEndAtMillis = System.currentTimeMillis() + totalSecs * 1000L
        _manualToyActivity.value = AiToyActivity.PANDORA_TIMER
    }

    /** Applies [transform] to the timer state, then re-derives the end time from the result. */
    private fun updateTimerAndSyncEnd(transform: (ActiveChronoHudState) -> ActiveChronoHudState) {
        _chronoState.update(transform)
        val st = _chronoState.value
        timerEndAtMillis = if (st.isTimerRunning) {
            System.currentTimeMillis() + st.timerRemainingSeconds * 1000L
        } else {
            null
        }
    }

    private fun updatePandoraAndSyncEnd(transform: (ActiveChronoHudState) -> ActiveChronoHudState) {
        _chronoState.update(transform)
        val st = _chronoState.value
        pandoraEndAtMillis = if (st.isPandoraRunning) {
            System.currentTimeMillis() + st.pandoraRemainingSeconds * 1000L
        } else {
            null
        }
    }

    fun pauseResumePandoraTimer() {
        updatePandoraAndSyncEnd { state ->
            if (state.pandoraRemainingSeconds <= 0) {
                state.copy(
                    isPandoraRunning = true,
                    isPandoraRinging = false,
                    pandoraRemainingSeconds = state.pandoraTotalSeconds.coerceAtLeast(60)
                )
            } else {
                state.copy(
                    isPandoraRunning = !state.isPandoraRunning,
                    isPandoraRinging = false
                )
            }
        }
    }

    fun startQuickChronoFromStage(activity: AiToyActivity) {
        when (activity) {
            AiToyActivity.ALARM -> triggerAlarmNow()
            AiToyActivity.TIMER -> startTimer(durationSeconds = 5 * 60, label = "5m Dock Timer")
            AiToyActivity.PANDORA_TIMER -> startPandoraTimer(durationMinutes = 25, phase = PandoraPhase.FOCUS)
            else -> triggerAlarmNow()
        }
    }

    fun snoozeChrono(targetActivity: AiToyActivity, snoozeMinutes: Int = 5) {
        val extraSeconds = snoozeMinutes.coerceAtLeast(1) * 60
        val current = _chronoState.value
        when {
            current.isAlarmRinging || current.snoozedAlarmUntilMillis != null || targetActivity == AiToyActivity.ALARM -> {
                // The service silences the alarm, schedules the snooze with AlarmManager and
                // persists it, then publishes Snoozed through AlarmRingBus.
                AlarmRingService.snooze(
                    context = appContext,
                    alarmId = activeRingingAlarmId ?: -1L,
                    label = current.ringingAlarmLabel,
                    timeText = current.ringingAlarmTimeText,
                    minutes = snoozeMinutes
                )
            }

            current.isTimerRunning || current.isTimerRinging || targetActivity == AiToyActivity.TIMER -> {
                updateTimerAndSyncEnd {
                    val newRem = it.timerRemainingSeconds + extraSeconds
                    it.copy(
                        isTimerRunning = true,
                        isTimerRinging = false,
                        timerRemainingSeconds = newRem,
                        timerTotalSeconds = maxOf(it.timerTotalSeconds, newRem)
                    )
                }
                _manualToyActivity.value = AiToyActivity.TIMER
            }

            current.isPandoraRunning || current.isPandoraRinging || targetActivity == AiToyActivity.PANDORA_TIMER -> {
                updatePandoraAndSyncEnd {
                    val newRem = it.pandoraRemainingSeconds + extraSeconds
                    it.copy(
                        isPandoraRunning = true,
                        isPandoraRinging = false,
                        pandoraRemainingSeconds = newRem,
                        pandoraTotalSeconds = maxOf(it.pandoraTotalSeconds, newRem)
                    )
                }
                _manualToyActivity.value = AiToyActivity.PANDORA_TIMER
            }
        }
    }

    fun stopChrono(targetActivity: AiToyActivity) {
        val current = _chronoState.value
        when {
            current.isAlarmRinging || current.snoozedAlarmUntilMillis != null || targetActivity == AiToyActivity.ALARM -> {
                val ringing = AlarmRingBus.status.value as? AlarmRingStatus.Ringing
                if (ringing != null && ringing.requiresChallenge) {
                    // Must solve a small sum first (see confirmAlarmChallenge).
                    if (_alarmChallenge.value == null) {
                        _alarmChallenge.value = MathChallenge((12..48).random(), (7..39).random())
                    }
                    return
                }
                val wasRinging = current.isAlarmRinging
                // Silences the service, cancels any pending snooze and clears it in the database.
                AlarmRingService.stop(appContext, activeRingingAlarmId ?: -1L)
                if (wasRinging) deliverMorningBriefing()
                activeRingingAlarmId = null
                _chronoState.update {
                    it.copy(
                        isAlarmRinging = false,
                        snoozedAlarmUntilMillis = null
                    )
                }
                if (_manualToyActivity.value == AiToyActivity.ALARM) {
                    _manualToyActivity.value = null
                }
            }

            current.isTimerRunning || current.isTimerRinging || targetActivity == AiToyActivity.TIMER -> {
                _chronoState.update {
                    it.copy(
                        isTimerRunning = false,
                        isTimerRinging = false,
                        timerRemainingSeconds = it.timerTotalSeconds
                    )
                }
                timerEndAtMillis = null
                if (_manualToyActivity.value == AiToyActivity.TIMER) {
                    _manualToyActivity.value = null
                }
            }

            current.isPandoraRunning || current.isPandoraRinging || targetActivity == AiToyActivity.PANDORA_TIMER -> {
                _chronoState.update {
                    it.copy(
                        isPandoraRunning = false,
                        isPandoraRinging = false,
                        pandoraRemainingSeconds = it.pandoraTotalSeconds
                    )
                }
                pandoraEndAtMillis = null
                if (_manualToyActivity.value == AiToyActivity.PANDORA_TIMER) {
                    _manualToyActivity.value = null
                }
            }
        }
    }

    fun searchWorldCities(query: String) {
        viewModelScope.launch {
            val matches = repository.searchWorldwideCities(query)
            _worldCitySearchResults.value = matches
        }
    }

    fun selectTab(tab: DockTab) {
        _selectedTab.value = tab
    }

    fun setManualToyActivity(activity: AiToyActivity?) {
        _manualToyActivity.value = activity
        manualOverrideSetAtMillis = if (activity != null) System.currentTimeMillis() else 0L
    }

    fun quickLogCalendarStatusBlock(activity: AiToyActivity) {
        when (activity) {
            AiToyActivity.ALARM -> {
                triggerAlarmNow()
                return
            }
            AiToyActivity.TIMER -> {
                startTimer(5 * 60, "5m Countdown Timer")
                return
            }
            AiToyActivity.PANDORA_TIMER -> {
                startPandoraTimer(25, PandoraPhase.FOCUS)
                return
            }
            else -> {}
        }
        viewModelScope.launch {
            val (title, subtitle) = when (activity) {
                AiToyActivity.WORKING -> "Working // Deep Focus Block" to "Focused Desk Session"
                AiToyActivity.BUSY_MEETING -> "Meeting // Live Sync Call" to "Do Not Disturb • On Air"
                AiToyActivity.EATING -> "Lunch Time // Meal & Coffee Refuel" to "Lunch Break Window"
                AiToyActivity.GYM -> "Gym Time // Workout & Fitness" to "Training Session"
                AiToyActivity.NAPPING -> "Napping // Power Recharge" to "Rest Window"
                AiToyActivity.SLEEPING -> "Sleep Time // Night Standby" to "Bedside Quiet Hours"
                AiToyActivity.ROAMING -> "Free Time // Open Schedule" to "Available"
                AiToyActivity.ALARM -> "Alarm // Dock Bell" to "Alert"
                AiToyActivity.TIMER -> "Timer // Countdown" to "Chrono"
                AiToyActivity.PANDORA_TIMER -> "Focus Timer // Focus Cycle" to "Pomodoro Focus"
            }
            repository.addCustomAgendaItem(
                title = title,
                subtitle = subtitle,
                providerType = "LOCAL",
                startOffsetMinutes = 0,
                durationMinutes = 45,
                locationOrSender = "Smart Dock Status"
            )
            _manualToyActivity.value = null
        }
    }

    fun setProviderFilter(badge: AccountProviderBadge?) {
        _providerFilter.value = badge
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _calendarPermissionGranted.value = granted || repository.hasCalendarPermission()
        if (_calendarPermissionGranted.value) {
            refreshCalendarEvents()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Meeting alerts (system-scheduled, work with the app closed) and privacy
    // ---------------------------------------------------------------------------------------
    private val _meetingAlertsSheetOpen = MutableStateFlow(false)
    val meetingAlertsSheetOpen: StateFlow<Boolean> = _meetingAlertsSheetOpen.asStateFlow()

    fun openMeetingAlertsSheet() { _meetingAlertsSheetOpen.value = true }
    fun closeMeetingAlertsSheet() { _meetingAlertsSheetOpen.value = false }

    fun saveMeetingAlerts(enabled: Boolean, leadMinutes: Int, hideTitles: Boolean) {
        viewModelScope.launch { preferencesRepository.setMeetingAlerts(enabled, leadMinutes, hideTitles) }
        _meetingAlertsSheetOpen.value = false
    }

    private val meetingAlertSync = viewModelScope.launch {
        combine(
            unifiedSchedule,
            settings.map { Triple(it.meetingAlertsEnabled, it.meetingAlertLeadMinutes, it.hideMeetingTitles) }.distinctUntilChanged()
        ) { events, cfg -> events to cfg }
            .debounce(2_000L)
            .collect { (events, cfg) ->
                try {
                    com.example.alarm.MeetingAlertScheduler.reschedule(appContext, events, cfg.first, cfg.second, cfg.third)
                } catch (e: Exception) {
                    android.util.Log.w("DockViewModel", "Meeting alert scheduling failed", e)
                }
            }
    }

    fun refreshCalendarEvents() {
        viewModelScope.launch {
            val hasPerm = repository.hasCalendarPermission()
            _calendarPermissionGranted.value = hasPerm
            if (hasPerm) {
                _nativeEvents.value = repository.fetchNativeCalendarEvents()
                _deviceSyncedAccounts.value = repository.getSyncedAccounts()
                _calendarError.value = repository.lastCalendarError
            } else {
                _calendarError.value = null
            }
            // Manual overrides are no longer wiped on every refresh; they expire on their own.
        }
    }

    fun refreshDualCitiesWeather(
        firstCityName: String = settings.value.cityName,
        secondCityName: String = settings.value.secondaryCityName
    ) {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            val fresh = repository.fetchDualCitiesWeather(firstCityName, secondCityName)
            val merged = _dualCityWeather.value.mergePreservingLast(fresh, firstCityName, secondCityName)
            _dualCityWeather.value = merged
            if (merged.primaryCity != null) {
                _weatherState.value = merged.primaryCity
            } else if (merged.secondaryCity != null) {
                _weatherState.value = merged.secondaryCity
            }
            _isWeatherLoading.value = false
        }
    }

    fun refreshWeather(
        cityName: String = settings.value.cityName,
        lat: Double = settings.value.cityLat,
        lon: Double = settings.value.cityLon
    ) {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            val res = repository.fetchWeather(cityName, lat, lon)
            res.onSuccess { data ->
                _weatherState.value = data
            }
            val fresh = repository.fetchDualCitiesWeather(
                cityName,
                settings.value.secondaryCityName
            )
            _dualCityWeather.value = _dualCityWeather.value.mergePreservingLast(
                fresh,
                cityName,
                settings.value.secondaryCityName
            )
            _isWeatherLoading.value = false
        }
    }

    /**
     * Refreshes weather when returning to the app if no weather is loaded yet or if the current
     * reading is more than 10 minutes old.
     */
    fun refreshWeatherIfStale(nowMillis: Long = System.currentTimeMillis()) {
        val current = _dualCityWeather.value.forAnimation(settings.value.weatherAnimationCity)
            ?: _weatherState.value
        if (current == null || nowMillis - current.updatedAtMillis > WEATHER_STALE_MS) {
            refreshDualCitiesWeather()
        }
    }

    fun updateClockStyle(style: String) {
        viewModelScope.launch { preferencesRepository.setClockStyle(style) }
    }

    fun cycleClockStyle() {
        val modes = listOf("NANO", "FLIP", "MATRIX", "CHRONO")
        val nextIdx = (modes.indexOf(settings.value.clockStyle) + 1) % modes.size
        updateClockStyle(modes[nextIdx])
    }

    fun updateAgentTheme(theme: String) {
        viewModelScope.launch { preferencesRepository.setAgentTheme(theme) }
    }

    fun toggleLandscapeMode() {
        viewModelScope.launch {
            preferencesRepository.setForceLandscapeMode(!settings.value.forceLandscapeMode)
        }
    }

    fun toggle24HourFormat() {
        viewModelScope.launch {
            preferencesRepository.setUse24HourFormat(!settings.value.use24HourFormat)
        }
    }

    fun toggleKeepScreenAwake() {
        viewModelScope.launch {
            preferencesRepository.setKeepScreenAwake(!settings.value.keepScreenAwake)
        }
    }

    fun toggleNightRedMode() {
        viewModelScope.launch {
            preferencesRepository.setNightRedMode(!settings.value.nightRedMode)
        }
    }

    fun toggleNightTimeOnlyMode() {
        viewModelScope.launch {
            preferencesRepository.setNightTimeOnlyEnabled(!settings.value.nightTimeOnlyEnabled)
        }
    }

    fun toggleSunriseSimulation() {
        viewModelScope.launch {
            preferencesRepository.setSunriseSimulationEnabled(!settings.value.sunriseSimulationEnabled)
        }
    }

    fun toggleHaptics() {
        viewModelScope.launch {
            preferencesRepository.setHapticsEnabled(!settings.value.hapticsEnabled)
        }
    }

    private var previewJob: Job? = null

    fun previewSunriseDemo() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val steps = 50
            for (i in 0..steps) {
                _previewSunriseProgress.value = i / steps.toFloat()
                delay(80L)
            }
            delay(2500L)
            for (i in steps downTo 0) {
                _previewSunriseProgress.value = i / steps.toFloat()
                delay(30L)
            }
            _previewSunriseProgress.value = null
        }
    }

    fun cancelSunrisePreview() {
        previewJob?.cancel()
        _previewSunriseProgress.value = null
    }

    fun updateNightTimeRange(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        viewModelScope.launch {
            preferencesRepository.setNightTimeRange(startHour, startMinute, endHour, endMinute)
        }
    }

    fun toggleShowPortHubTelemetry() {
        viewModelScope.launch {
            preferencesRepository.setShowPortHubTelemetry(!settings.value.showPortHubTelemetry)
        }
    }

    fun toggleShowAgendaTicker() {
        viewModelScope.launch {
            preferencesRepository.setShowAgendaTicker(!settings.value.showAgendaTicker)
        }
    }

    fun updateBrightnessDimmer(value: Float) {
        viewModelScope.launch {
            preferencesRepository.setBrightnessDimmer(value)
        }
    }

    fun updateWeatherCity(cityName: String) {
        viewModelScope.launch {
            preferencesRepository.setWeatherCity(cityName)
        }
    }

    fun updateSecondaryWeatherCity(cityName: String) {
        viewModelScope.launch {
            preferencesRepository.setSecondaryWeatherCity(cityName)
        }
    }

    fun updateDualWeatherCities(primaryCityName: String, secondaryCityName: String) {
        viewModelScope.launch {
            preferencesRepository.setDualWeatherCities(primaryCityName, secondaryCityName)
        }
    }

    fun updateLocalLlmConfig(endpoint: String, modelName: String) {
        viewModelScope.launch {
            preferencesRepository.setLocalLlmConfig(endpoint, modelName)
        }
    }

    fun sendAgentQuery(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _isAgentThinking.value) return
        viewModelScope.launch {
            _isAgentThinking.value = true
            try {
                val cfg = settings.value
                repository.sendAgentPrompt(
                    prompt = trimmed,
                    endpointUrl = cfg.localLlmEndpoint,
                    modelName = cfg.localLlmModelName,
                    schedule = unifiedSchedule.value,
                    notes = notes.value,
                    battery = batteryTelemetry.value,
                    weather = _dualCityWeather.value.primaryCity ?: _weatherState.value
                )
            } finally {
                _isAgentThinking.value = false
            }
        }
    }

    fun clearAgentHistory() {
        viewModelScope.launch {
            repository.clearConversation()
        }
    }

    fun buildPocketPalSharePayload(customPrompt: String): String {
        return repository.buildExternalShareContext(
            userPrompt = customPrompt,
            schedule = unifiedSchedule.value,
            notes = notes.value,
            battery = batteryTelemetry.value,
            weather = _dualCityWeather.value.primaryCity ?: _weatherState.value
        )
    }

    fun addNote(title: String, content: String, category: String, isPinned: Boolean) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            repository.saveNote(
                title = title.ifBlank { "Dock Note" },
                content = content,
                category = category,
                isPinned = isPinned
            )
        }
    }

    fun toggleNotePinned(note: DockNoteEntity) {
        viewModelScope.launch {
            repository.toggleNotePinned(note)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun addCustomAgendaItem(
        title: String,
        subtitle: String,
        providerType: String,
        startOffsetMinutes: Int,
        durationMinutes: Int,
        locationOrSender: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addCustomAgendaItem(
                title = title,
                subtitle = subtitle,
                providerType = providerType,
                startOffsetMinutes = startOffsetMinutes,
                durationMinutes = durationMinutes,
                locationOrSender = locationOrSender
            )
            _manualToyActivity.value = null
        }
    }

    fun addCustomAgendaItemWithWindow(
        title: String,
        subtitle: String,
        providerType: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        locationOrSender: String
    ) {
        if (title.isBlank()) return
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour.coerceIn(0, 23))
            set(Calendar.MINUTE, startMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour.coerceIn(0, 23))
            set(Calendar.MINUTE, endMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var endMillis = endCal.timeInMillis
        if (endMillis <= startCal.timeInMillis) {
            // Crosses midnight (e.g. Sleep Time 23:00 – 07:00)
            endMillis += 24 * 60 * 60_000L
        }
        addCustomAgendaItemWithWindow(
            title = title,
            subtitle = subtitle,
            providerType = providerType,
            startTimeMillis = startCal.timeInMillis,
            endTimeMillis = endMillis,
            locationOrSender = locationOrSender
        )
    }

    fun addCustomAgendaItemWithWindow(
        title: String,
        subtitle: String,
        providerType: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        locationOrSender: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addCustomAgendaItemWithWindow(
                title = title,
                subtitle = subtitle,
                providerType = providerType,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                locationOrSender = locationOrSender
            )
            _manualToyActivity.value = null
        }
    }

    fun toggleCustomAgendaCompleted(item: UnifiedScheduleItem) {
        val localId = item.localEntityId ?: return
        viewModelScope.launch {
            repository.toggleCustomAgendaCompleted(localId, item.isCompleted)
        }
    }

    fun deleteCustomAgendaItem(item: UnifiedScheduleItem) {
        val localId = item.localEntityId ?: return
        viewModelScope.launch {
            repository.deleteCustomAgendaItem(localId)
        }
    }

    private fun DockAgendaEntity.toUnifiedScheduleItem(): UnifiedScheduleItem {
        val badge = when (providerType.uppercase()) {
            "GMAIL" -> AccountProviderBadge.GMAIL
            "OUTLOOK" -> AccountProviderBadge.OUTLOOK
            else -> AccountProviderBadge.SYSTEM
        }
        return UnifiedScheduleItem(
            id = "local_$id",
            title = title,
            accountName = subtitle.ifBlank { badge.label },
            calendarDisplayName = badge.label,
            providerBadge = badge,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            location = locationOrSender,
            isAllDay = false,
            isCustomLocalItem = true,
            localEntityId = id,
            isCompleted = isCompleted
        )
    }

    // ---------------------------------------------------------------------------------------
    // Alarm options (repeat days, skip next, vibrate only, challenge, sound)
    // ---------------------------------------------------------------------------------------

    fun updateAlarmOptions(
        alarm: DockAlarmEntity,
        repeatDaysMask: Int = alarm.repeatDaysMask,
        skipUntilMillis: Long? = alarm.skipUntilMillis,
        vibrateOnly: Boolean = alarm.vibrateOnly,
        dismissChallenge: Boolean = alarm.dismissChallenge,
        soundUri: String? = alarm.soundUri
    ) {
        viewModelScope.launch {
            repository.updateAlarmOptions(alarm.id, repeatDaysMask, skipUntilMillis, vibrateOnly, dismissChallenge, soundUri)
        }
    }

    /** Skips the next occurrence, or un-skips it if already skipped. */
    fun toggleSkipNext(alarm: DockAlarmEntity) {
        val now = System.currentTimeMillis()
        val skipActive = (alarm.skipUntilMillis ?: 0L) > now
        val skipUntil = if (skipActive) {
            null
        } else {
            AlarmScheduler.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask, null, now)
        }
        updateAlarmOptions(alarm, skipUntilMillis = skipUntil)
    }

    fun confirmAlarmChallenge(answer: String): Boolean {
        val challenge = _alarmChallenge.value ?: return true
        if (answer.trim().toIntOrNull() != challenge.answer) return false
        _alarmChallenge.value = null
        AlarmRingService.stop(appContext, activeRingingAlarmId ?: -1L)
        deliverMorningBriefing()
        return true
    }

    fun cancelAlarmChallenge() {
        _alarmChallenge.value = null
    }

    // ---------------------------------------------------------------------------------------
    // Morning briefing after an alarm is dismissed
    // ---------------------------------------------------------------------------------------

    private fun deliverMorningBriefing() {
        val cat = settings.value.catName.ifBlank { "Mochi" }
        val weather = _dualCityWeather.value.primaryCity ?: _weatherState.value
        val now = System.currentTimeMillis()
        val firstEvent = unifiedSchedule.value
            .filter { !it.isCompleted && !it.isAllDay && it.startTimeMillis > now }
            .minByOrNull { it.startTimeMillis }
        val timeFmt = java.text.SimpleDateFormat(
            if (settings.value.use24HourFormat) "HH:mm" else "h:mm a",
            java.util.Locale.getDefault()
        )
        val parts = mutableListOf<String>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        parts += when (hour) {
            in 4..11 -> "Good morning!"
            in 12..17 -> "Good afternoon!"
            else -> "Hello!"
        }
        if (weather != null) {
            parts += "It's ${kotlin.math.round(weather.temperatureC).toInt()}°C and " +
                "${weather.conditionLabel.lowercase()} in ${weather.cityName}, " +
                "high ${kotlin.math.round(weather.highC).toInt()}°, low ${kotlin.math.round(weather.lowC).toInt()}°."
        }
        parts += if (firstEvent != null) {
            "First up: ${firstEvent.title} at ${timeFmt.format(java.util.Date(firstEvent.startTimeMillis))}."
        } else {
            "Your calendar is clear."
        }
        val bodyText = parts.joinToString(" ")
        val text = "$cat says: $bodyText"
        _morningBriefing.value = text
        enqueueNudge(
            nudge = com.example.wellness.Nudge(
                type = com.example.wellness.NudgeType.MORNING_BRIEFING,
                shownAtMillis = now,
                detail = bodyText
            ),
            now = now
        )
        if (settings.value.speakMorningBriefing && isPremium.value) speak(bodyText)
    }

    fun dismissMorningBriefing() {
        _morningBriefing.value = null
        removeNudgeByType(com.example.wellness.NudgeType.MORNING_BRIEFING)
        tts?.stop()
    }

    private fun speak(text: String) {
        val existing = tts
        if (existing != null) {
            existing.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "briefing")
            return
        }
        var created: android.speech.tts.TextToSpeech? = null
        created = android.speech.tts.TextToSpeech(appContext) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                created?.language = java.util.Locale.getDefault()
                created?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "briefing")
            }
        }
        tts = created
    }

    // ---------------------------------------------------------------------------------------
    // Companion / onboarding / reliability sheets
    // ---------------------------------------------------------------------------------------

    fun openCompanionSheet() { _companionSheetOpen.value = true }
    fun closeCompanionSheet() { _companionSheetOpen.value = false }
    fun openReliabilitySheet() { _reliabilitySheetOpen.value = true }
    fun closeReliabilitySheet() { _reliabilitySheetOpen.value = false }

    fun saveCompanion(useCat: Boolean, coat: String, name: String) {
        viewModelScope.launch { preferencesRepository.setCompanion(useCat, coat, name) }
    }

    fun setWeatherAnimationCity(choice: String) {
        viewModelScope.launch { preferencesRepository.setWeatherAnimationCity(choice) }
    }

    fun setCompanionSounds(mode: String, volume: Float) {
        viewModelScope.launch { preferencesRepository.setCompanionSounds(mode, volume) }
    }

    fun saveDisplayCare(autoDim: Boolean, burnIn: Boolean, speakBriefing: Boolean) {
        viewModelScope.launch { preferencesRepository.setDisplayCare(autoDim, burnIn, speakBriefing) }
    }

    fun setAdaptiveBrightnessEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAdaptiveBrightnessEnabled(enabled) }
    }

    fun setScreensaverOnScreenOff(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setScreensaverOnScreenOff(enabled) }
    }

    fun setAutoReturnToCompanionEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoReturnToCompanionEnabled(enabled) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingDone(true) }
    }

    // ---------------------------------------------------------------------------------------
    // Voice assistant / system clock intents (AlarmClock.ACTION_SET_ALARM, SET_TIMER, SHOW_ALARMS)
    // ---------------------------------------------------------------------------------------

    /** Returns a short confirmation message for a toast, or null if the intent wasn't a clock intent. */
    fun handleClockIntent(intent: android.content.Intent?): String? {
        intent ?: return null
        return when (intent.action) {
            android.provider.AlarmClock.ACTION_SET_ALARM -> {
                val hour = intent.getIntExtra(android.provider.AlarmClock.EXTRA_HOUR, -1)
                if (hour !in 0..23) return "Open Alarms to choose a time"
                val minute = intent.getIntExtra(android.provider.AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59)
                val label = intent.getStringExtra(android.provider.AlarmClock.EXTRA_MESSAGE) ?: "Alarm"
                val days = intent.getIntegerArrayListExtra(android.provider.AlarmClock.EXTRA_DAYS)
                val mask = days?.fold(0) { acc, d -> acc or (1 shl AlarmScheduler.dayBit(d)) } ?: 0
                val vibrate = intent.getBooleanExtra(android.provider.AlarmClock.EXTRA_VIBRATE, false) &&
                    intent.getStringExtra(android.provider.AlarmClock.EXTRA_RINGTONE) ==
                    android.provider.AlarmClock.VALUE_RINGTONE_SILENT
                viewModelScope.launch {
                    val id = repository.addAlarm(hour, minute, label)
                    repository.updateAlarmOptions(id, mask, null, vibrate, false, null)
                }
                "Alarm set for ${AlarmScheduler.timeText(hour, minute)}"
            }
            android.provider.AlarmClock.ACTION_SET_TIMER -> {
                val seconds = intent.getIntExtra(android.provider.AlarmClock.EXTRA_LENGTH, -1)
                if (seconds <= 0) return "Open Timer to choose a length"
                val label = intent.getStringExtra(android.provider.AlarmClock.EXTRA_MESSAGE) ?: "Timer"
                startTimer(seconds, label)
                "Timer started: ${formatMinutesSeconds(seconds)}"
            }
            android.provider.AlarmClock.ACTION_SHOW_ALARMS -> "Alarms"
            else -> null
        }
    }

    private fun formatMinutesSeconds(seconds: Int): String =
        if (seconds >= 60) "${seconds / 60} min${if (seconds % 60 > 0) " ${seconds % 60} s" else ""}" else "$seconds s"

    override fun onCleared() {
        tts?.shutdown()
        tts = null
        sleepSoundPlayer.stop(immediate = true)
        super.onCleared()
    }

    // ---------------------------------------------------------------------------------------
    // Wellness
    // ---------------------------------------------------------------------------------------

    /** Called by MainActivity on start/stop. Desk time resets after 15 minutes away. */
    fun setAppVisible(visible: Boolean) {
        val now = System.currentTimeMillis()
        if (visible && !appVisible && hiddenSince > 0 && now - hiddenSince > 15 * 60_000L) {
            deskSessionStart = now
        }
        if (!visible) hiddenSince = now
        appVisible = visible
        if (visible) {
            com.example.wellness.WellnessNotifier.cancel(appContext)
            refreshWeatherIfStale(now)
        }
    }

    private fun isInMeetingNow(now: Long): Boolean =
        unifiedSchedule.value.any { ev ->
            !ev.isCompleted && !ev.isAllDay && ev.startTimeMillis <= now && ev.endTimeMillis > now
        }

    private fun isCompanionQuiet(now: Long, ignoreAlarmCheck: Boolean = false): Boolean {
        val chrono = _chronoState.value
        val alarmBusy = !ignoreAlarmCheck && (chrono.isAlarmRinging || chrono.isTimerRinging || chrono.isPandoraRinging)
        return alarmBusy ||
            chrono.isPandoraRunning ||
            isWebcamActive.value ||
            isInMeetingNow(now) ||
            isDndActive() ||
            isNightWindow(now) ||
            settings.value.nightRedMode
    }

    fun playCompanionCue(
        cue: com.example.alarm.CompanionSoundCue,
        nowMillis: Long = System.currentTimeMillis(),
        ignoreAlarmCheck: Boolean = false
    ) {
        val st = settings.value
        com.example.alarm.CompanionSoundPlayer.play(
            context = appContext,
            cue = cue,
            useCat = st.useCatCompanion,
            mode = st.companionSoundsMode,
            volume = st.companionSoundsVolume,
            quiet = isCompanionQuiet(nowMillis, ignoreAlarmCheck)
        )
    }

    fun playCompanionTapSound(streak: Int) {
        // Rule 5: Tapping the companion reopens a shrunk reminder bubble!
        if (_nudgeQueue.value.isNotEmpty() && !_isBubbleExpanded.value) {
            expandNudgeBubble()
        }
        val cue = when {
            streak == 1 -> com.example.alarm.CompanionSoundCue.TAP_HOP
            streak == 2 -> com.example.alarm.CompanionSoundCue.TAP_SPIN
            else -> com.example.alarm.CompanionSoundCue.TAP_SHAKE
        }
        playCompanionCue(cue)
    }

    private fun lastLog(type: String): Long =
        wellnessLogs.value.lastOrNull { it.type == type }?.atMillis ?: 0L

    private fun isNightWindow(now: Long): Boolean {
        val st = settings.value
        if (st.isNightTimeOnlyActive(now)) return true
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = st.nightStartHour * 60 + st.nightStartMinute
        val end = st.nightEndHour * 60 + st.nightEndMinute
        return if (start < end) mins in start until end else mins >= start || mins < end
    }

    fun isNightReminderSilent(now: Long = _currentTimeMillis.value): Boolean =
        settings.value.nightRedMode || isNightWindow(now)

    private fun isDndActive(): Boolean = try {
        val nm = appContext.getSystemService(android.app.NotificationManager::class.java)
        nm != null && nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    } catch (_: Exception) {
        false
    }

    private fun syncActiveFromQueue(queue: List<com.example.wellness.Nudge>, now: Long = System.currentTimeMillis()) {
        val sorted = com.example.wellness.WellnessEngine.sortByPriority(queue)
        val prevTop = _activeNudge.value
        val nextTop = sorted.firstOrNull()
        _nudgeQueue.value = sorted
        _activeNudge.value = nextTop
        if (nextTop != null && (prevTop == null || prevTop.type != nextTop.type || prevTop.shownAtMillis != nextTop.shownAtMillis)) {
            _isBubbleExpanded.value = true
            bubbleExpandedAtMillis = now
        } else if (nextTop == null) {
            _isBubbleExpanded.value = false
        }
    }

    fun enqueueNudge(nudge: com.example.wellness.Nudge, now: Long = System.currentTimeMillis()) {
        val filtered = _nudgeQueue.value.filterNot { it.type == nudge.type }
        syncActiveFromQueue(filtered + nudge, now)
    }

    private fun removeNudgeByType(type: com.example.wellness.NudgeType) {
        val remaining = _nudgeQueue.value.filterNot { it.type == type }
        if (remaining.size != _nudgeQueue.value.size) {
            syncActiveFromQueue(remaining)
        }
    }

    fun expandNudgeBubble(now: Long = System.currentTimeMillis()) {
        if (_nudgeQueue.value.isNotEmpty()) {
            _isBubbleExpanded.value = true
            bubbleExpandedAtMillis = now
        }
    }

    fun shrinkNudgeBubble() {
        _isBubbleExpanded.value = false
    }

    internal fun tickNudgeLifecycle(now: Long) {
        val currentQueue = _nudgeQueue.value
        if (currentQueue.isEmpty()) return

        // 1. Shrink full bubble to floating icon after 20 seconds of no response
        if (_isBubbleExpanded.value && bubbleExpandedAtMillis > 0L &&
            now - bubbleExpandedAtMillis >= com.example.wellness.Nudge.AUTO_SHRINK_AFTER_MS
        ) {
            _isBubbleExpanded.value = false
        }

        // 2. Auto-expire reminders whose relevance window has ended
        val (expired, stillValid) = currentQueue.partition { it.isExpired(now) }
        if (expired.isNotEmpty()) {
            val recordable = expired.filter {
                it.type != com.example.wellness.NudgeType.FRIDAY &&
                    it.type != com.example.wellness.NudgeType.BIRTHDAY
            }.map { exp ->
                com.example.wellness.MissedNudge(
                    type = exp.type,
                    message = exp.message,
                    shownAtMillis = exp.shownAtMillis,
                    expiredAtMillis = now
                )
            }
            if (recordable.isNotEmpty()) {
                _missedNudges.update { existing -> (existing + recordable).takeLast(10) }
                _whileAwayBannerDismissed.value = false
            }
            if (expired.any { it.type == com.example.wellness.NudgeType.MORNING_BRIEFING }) {
                _morningBriefing.value = null
            }
            syncActiveFromQueue(stillValid, now)
        }
    }

    private fun evaluateWellness(now: Long) {
        val ws = wellnessSettings.value
        val schedule = unifiedSchedule.value
        _companionHat.value = com.example.wellness.WellnessEngine.companionHat(schedule, now, ws.celebrationsEnabled)
        tickNudgeLifecycle(now)
        if (_guidedSession.value != null) return

        val weather = _dualCityWeather.value.forAnimation(settings.value.weatherAnimationCity)
        val chrono = _chronoState.value
        val ctx = com.example.wellness.WellnessContext(
            now = now,
            settings = ws,
            schedule = schedule,
            focusRunning = chrono.isPandoraRunning || isWebcamActive.value,
            alarmActive = chrono.isAlarmRinging || chrono.isTimerRinging,
            nightActive = isNightWindow(now),
            dndActive = isDndActive(),
            weatherTempC = weather?.temperatureC,
            weatherCode = weather?.weatherCode,
            isDaytime = weather?.let { com.example.ui.components.isDaytimeForWeather(it, now) } ?: true,
            deskSessionStart = if (appVisible) deskSessionStart else now,
            lastWater = lastLog("WATER"),
            lastStretch = maxOf(lastLog("STRETCH"), lastLog("WALK")),
            lastEyeRest = lastLog("EYE_REST"),
            lunchLoggedToday = wellnessLogs.value.any { it.type == "LUNCH" && it.dayKey == com.example.wellness.WellnessDay.key(now) },
            prioritiesSetToday = priorities.value.isNotEmpty()
        )
        val (nudge, onceKey) = wellnessEngine.evaluate(ctx) ?: return
        wellnessEngine.markShown(nudge.type, now, onceKey)
        enqueueNudge(nudge, now)

        val soundCue = when (nudge.type) {
            com.example.wellness.NudgeType.WATER -> com.example.alarm.CompanionSoundCue.WATER_CHIRP
            com.example.wellness.NudgeType.STRETCH -> com.example.alarm.CompanionSoundCue.STRETCH_YAWN
            com.example.wellness.NudgeType.EYE_REST -> com.example.alarm.CompanionSoundCue.EYE_REST_PRRT
            com.example.wellness.NudgeType.PRE_MEETING_BREATH,
            com.example.wellness.NudgeType.BREATHE -> com.example.alarm.CompanionSoundCue.BREATH_PURR
            com.example.wellness.NudgeType.LUNCH -> com.example.alarm.CompanionSoundCue.LUNCH_MEW
            com.example.wellness.NudgeType.WALK -> com.example.alarm.CompanionSoundCue.WALK_TRILL
            com.example.wellness.NudgeType.MEETING_FATIGUE,
            com.example.wellness.NudgeType.TOP3,
            com.example.wellness.NudgeType.MORNING_BRIEFING -> com.example.alarm.CompanionSoundCue.MEETING_CHIRP
            com.example.wellness.NudgeType.END_OF_DAY -> com.example.alarm.CompanionSoundCue.END_OF_DAY_MRROW
            com.example.wellness.NudgeType.WIND_DOWN -> com.example.alarm.CompanionSoundCue.WIND_DOWN_YAWN
            com.example.wellness.NudgeType.FRIDAY,
            com.example.wellness.NudgeType.BIRTHDAY -> com.example.alarm.CompanionSoundCue.CELEBRATE_CHIRP
        }
        playCompanionCue(soundCue, now)

        if (nudge.type == com.example.wellness.NudgeType.FRIDAY || nudge.type == com.example.wellness.NudgeType.BIRTHDAY) {
            celebrate.tryEmit(Unit)
        }
        if (!appVisible && nudge.type.notifyWhenAway) {
            com.example.wellness.WellnessNotifier.post(appContext, nudge, settings.value.catName.ifBlank { "Your buddy" })
        }
    }

    /** Main button on a nudge speech bubble (or a specific queued nudge). */
    fun onNudgeAction(target: com.example.wellness.Nudge? = _activeNudge.value) {
        val nudge = target ?: return
        removeNudgeByType(nudge.type)
        if (nudge.type == com.example.wellness.NudgeType.MORNING_BRIEFING) {
            _morningBriefing.value = null
            tts?.stop()
            return
        }
        when (nudge.type) {
            com.example.wellness.NudgeType.STRETCH ->
                if (isPremium.value) _guidedSession.value = "STRETCH" else logWellness("STRETCH")
            com.example.wellness.NudgeType.PRE_MEETING_BREATH,
            com.example.wellness.NudgeType.BREATHE ->
                if (isPremium.value) _guidedSession.value = "BREATHE" else logWellness("BREATH")
            com.example.wellness.NudgeType.TOP3 -> _top3DialogOpen.value = true
            com.example.wellness.NudgeType.MEETING_FATIGUE -> blockBreakSlot()
            else -> nudge.type.logType?.let { logWellness(it) }
        }
    }

    fun snoozeNudge(minutes: Int = 10, target: com.example.wellness.Nudge? = _activeNudge.value) {
        val nudge = target ?: return
        wellnessEngine.snooze(nudge.type, System.currentTimeMillis(), minutes)
        removeNudgeByType(nudge.type)
    }

    fun dismissNudge(target: com.example.wellness.Nudge? = _activeNudge.value) {
        val nudge = target ?: return
        if (nudge.type == com.example.wellness.NudgeType.MORNING_BRIEFING) {
            _morningBriefing.value = null
            tts?.stop()
        }
        removeNudgeByType(nudge.type)
    }

    fun dismissWhileAwayBanner() {
        _whileAwayBannerDismissed.value = true
    }

    fun clearMissedNudges() {
        _missedNudges.value = emptyList()
        _whileAwayBannerDismissed.value = true
    }

    fun actOnMissedNudge(missed: com.example.wellness.MissedNudge) {
        _missedNudges.update { list -> list.filterNot { it === missed } }
        when (missed.type) {
            com.example.wellness.NudgeType.STRETCH ->
                if (isPremium.value) _guidedSession.value = "STRETCH" else logWellness("STRETCH")
            com.example.wellness.NudgeType.PRE_MEETING_BREATH,
            com.example.wellness.NudgeType.BREATHE ->
                if (isPremium.value) _guidedSession.value = "BREATHE" else logWellness("BREATH")
            com.example.wellness.NudgeType.TOP3 -> _top3DialogOpen.value = true
            com.example.wellness.NudgeType.MEETING_FATIGUE -> blockBreakSlot()
            else -> missed.type.logType?.let { logWellness(it) }
        }
    }

    fun logWellness(type: String) {
        viewModelScope.launch { wellnessRepository.log(type) }
        if (type == "WATER") {
            val todayKey = com.example.wellness.WellnessDay.key(System.currentTimeMillis())
            val newWaterCount = wellnessLogs.value.count { it.type == "WATER" && it.dayKey == todayKey } + 1
            val cue = if (newWaterCount >= wellnessSettings.value.waterGoal) {
                com.example.alarm.CompanionSoundCue.CELEBRATE_CHIRP
            } else {
                com.example.alarm.CompanionSoundCue.WATER_LAP
            }
            playCompanionCue(cue)
        }
        if (type == "WATER" || type == "STRETCH" || type == "WALK") {
            celebrate.tryEmit(Unit)
        }
        if (type == "STRETCH" || type == "WALK") deskSessionStart = System.currentTimeMillis()
    }

    fun decrementWater() {
        viewModelScope.launch {
            wellnessRepository.removeLatestLog(listOf("WATER"))
        }
    }

    fun decrementBreak() {
        viewModelScope.launch {
            wellnessRepository.removeLatestLog(com.example.wellness.WellnessRepository.BREAK_TYPES)
        }
    }

    /** Guided stretch/breathing finished (or closed early). */
    fun finishGuidedSession(completed: Boolean) {
        val session = _guidedSession.value
        _guidedSession.value = null
        if (completed) logWellness(if (session == "STRETCH") "STRETCH" else "BREATH")
    }

    fun startGuidedSession(kind: String) {
        if (isPremium.value) _guidedSession.value = kind else _paywallOpen.value = true
    }

    private fun blockBreakSlot() {
        val now = System.currentTimeMillis()
        val offset = com.example.wellness.WellnessEngine.nextFreeSlotOffsetMinutes(unifiedSchedule.value, now)
        viewModelScope.launch {
            repository.addCustomAgendaItem(
                title = "Break // Step away from the screen",
                subtitle = "Blocked by your companion",
                providerType = "LOCAL",
                startOffsetMinutes = offset,
                durationMinutes = 15,
                locationOrSender = "Smart Dock"
            )
        }
    }

    fun openTop3() { _top3DialogOpen.value = true }
    fun closeTop3() { _top3DialogOpen.value = false }

    fun saveTop3(texts: List<String>) {
        viewModelScope.launch { wellnessRepository.savePriorities(texts) }
        _top3DialogOpen.value = false
    }

    fun togglePriority(id: Long, done: Boolean) {
        viewModelScope.launch { wellnessRepository.setPriorityDone(id, done) }
        if (done) {
            celebrate.tryEmit(Unit)
            playCompanionCue(com.example.alarm.CompanionSoundCue.CELEBRATE_CHIRP)
        }
    }

    fun openWellnessSheet() { _wellnessSheetOpen.value = true }
    fun closeWellnessSheet() { _wellnessSheetOpen.value = false }

    fun saveWellnessSettings(newSettings: com.example.wellness.WellnessSettings) {
        viewModelScope.launch { wellnessRepository.saveSettings(newSettings) }
    }

    companion object {
        private const val MANUAL_OVERRIDE_TTL_MS = 30 * 60_000L
        internal const val WEATHER_STALE_MS = 10 * 60_000L
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = DockDatabase.getInstance(appContext)
            val calendarReader = NativeCalendarReader(appContext)
            val weatherRepo = WeatherRepository()
            val llmBridge = LocalLlmBridge()
            val repository = DockRepository(
                dockDao = db.dockDao(),
                calendarReader = calendarReader,
                weatherRepository = weatherRepo,
                localLlmBridge = llmBridge
            )
            val prefsRepo = DockPreferencesRepository(appContext)
            val batteryMonitor = BatteryTelemetryMonitor(appContext)
            val wellnessRepo = com.example.wellness.WellnessRepository(appContext, db.dockDao())
            return DockViewModel(appContext, repository, prefsRepo, batteryMonitor, wellnessRepo) as T
        }
    }
}

