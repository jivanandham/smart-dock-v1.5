package com.example.dream

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.alarm.AlarmScheduler
import com.example.data.calendar.NativeCalendarReader
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.local.DockDatabase
import com.example.data.preferences.DockPreferencesRepository
import com.example.data.preferences.DockSettings
import com.example.ui.CompanionAppearance
import com.example.ui.LocalCompanionAppearance
import com.example.ui.components.CatCoat
import com.example.ui.components.InteractiveAiToyTopStage
import com.example.ui.rememberBurnInOffset
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Android screensaver ("Daydream"): shows the clock and the cat automatically while the phone
 * charges or docks — enable it in Settings › Display › Screen saver › Smart Dock.
 *
 * DreamService is not a LifecycleOwner, so this class provides the lifecycle and saved-state
 * owners Compose needs.
 */
class SmartDockDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // any touch exits to the lock screen / app, like the system clock
        isFullscreen = true
        isScreenBright = !isNightNow()

        val prefs = DockPreferencesRepository(applicationContext)
        val reader = NativeCalendarReader(applicationContext)
        val dao = DockDatabase.getInstance(applicationContext).dockDao()
        val webcamMonitor = com.example.data.hardware.WebcamActivityMonitor(applicationContext)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SmartDockDreamService)
            setViewTreeSavedStateRegistryOwner(this@SmartDockDreamService)
            setContent {
                val settings by prefs.settingsFlow.collectAsState(initial = DockSettings())
                val alarms by dao.observeAlarms().collectAsState(initial = emptyList())
                val webcamState by webcamMonitor.state.collectAsState()
                var schedule by remember { mutableStateOf<List<UnifiedScheduleItem>>(emptyList()) }
                var weather by remember { mutableStateOf(com.example.data.weather.DualCityWeather(null, null)) }
                // Refresh both cities every 15 minutes (and whenever the cities change),
                // preserving the last real weather reading if a network request fails.
                LaunchedEffect(settings.cityName, settings.secondaryCityName) {
                    val repo = com.example.data.weather.WeatherRepository()
                    while (true) {
                        try {
                            val fresh = repo.fetchDualCitiesWeather(settings.cityName, settings.secondaryCityName)
                            weather = weather.mergePreservingLast(fresh, settings.cityName, settings.secondaryCityName)
                        } catch (e: Exception) {
                            android.util.Log.w("SmartDockDream", "Weather refresh failed", e)
                        }
                        delay(15 * 60_000L)
                    }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        schedule = reader.queryUpcomingSystemEvents(24)
                        delay(10 * 60_000L)
                    }
                }
                val appearance = CompanionAppearance(
                    useCat = settings.useCatCompanion,
                    coat = CatCoat.fromKey(settings.catCoat),
                    name = settings.catName
                )
                CompositionLocalProvider(LocalCompanionAppearance provides appearance) {
                    MyApplicationTheme(nightRedMode = settings.nightRedMode || isNightNow(), agentTheme = settings.agentTheme) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            DreamClock(
                                weather = weather.forAnimation(settings.weatherAnimationCity),
                                use24h = settings.use24HourFormat,
                                hideTitles = settings.hideMeetingTitles,
                                schedule = schedule,
                                nextAlarm = AlarmScheduler.nextAlarmSummary(alarms),
                                burnIn = settings.burnInProtection,
                                isCameraInUse = webcamState.isCameraInUse
                            )
                            com.example.ui.components.CameraCutoutRingOverlay(
                                isActive = webcamState.isCameraInUse,
                                lensLabel = webcamState.activeLensLabel
                            )
                        }
                    }
                }
            }
        }
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun isNightNow(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 7
    }
}

@Composable
private fun DreamClock(
    hideTitles: Boolean,
    weather: com.example.data.weather.WeatherStationData?,
    use24h: Boolean,
    schedule: List<UnifiedScheduleItem>,
    nextAlarm: Pair<com.example.data.local.DockAlarmEntity, Long>?,
    burnIn: Boolean,
    isCameraInUse: Boolean = false
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - (System.currentTimeMillis() % 1000L) + 5L)
        }
    }
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val night = hour >= 22 || hour < 7
    val timeColor = if (night) Color(0xFFB3261E) else Color(0xFFF5EDE3)
    val subColor = if (night) Color(0xFF7A1D18) else Color(0xFFF6B86E)
    val shift = rememberBurnInOffset(burnIn, maxPx = 10)

    val timeText = SimpleDateFormat(if (use24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val dateText = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now))
    val nextEvent = schedule.firstOrNull { !it.isAllDay && it.startTimeMillis > now }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .offset { shift }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val premium = com.example.premium.PremiumManager.isPremium.collectAsState().value
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (!premium) {
                    Text("Unlock the companion screensaver with Premium", color = subColor.copy(alpha = 0.6f), fontSize = 14.sp)
                } else InteractiveAiToyTopStage(
                    currentTimeMillis = now,
                    schedule = schedule,
                    isAgentThinking = false,
                    manualActivity = if (isCameraInUse) com.example.ui.components.AiToyActivity.BUSY_MEETING else null,
                    onSelectManualActivity = {},
                    onQuickAddCalendarStatus = {},
                    fillAvailableHeight = true,
                    hapticsEnabled = false,
                    currentWeather = weather,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = timeText,
                color = timeColor,
                fontSize = 120.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 120.sp
            )
            Text(text = dateText, color = subColor, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val details = buildList {
                if (nextAlarm != null) {
                    add("Alarm ${AlarmScheduler.timeText(nextAlarm.first.hour, nextAlarm.first.minute)} · in ${AlarmScheduler.formatCountdown(nextAlarm.second - now)}")
                }
                if (nextEvent != null && !night) {
                    add("Next: ${if (hideTitles) "Meeting" else nextEvent.title} · ${SimpleDateFormat(if (use24h) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(nextEvent.startTimeMillis))}")
                }
            }
            if (details.isNotEmpty()) {
                Text(text = details.joinToString("   •   "), color = subColor.copy(alpha = 0.8f), fontSize = 15.sp)
            }
        }
    }
}
