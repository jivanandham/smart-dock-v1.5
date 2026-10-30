package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarm.AlarmRingBus
import com.example.alarm.AlarmRingService
import com.example.alarm.AlarmRingStatus
import com.example.ui.CameraStateViewModel
import com.example.ui.CompanionAppearance
import com.example.ui.DockGlobalOverlays
import com.example.ui.DockViewModel
import com.example.ui.LocalCameraStateViewModel
import com.example.ui.LocalCompanionAppearance
import com.example.ui.LocalDockViewModel
import com.example.ui.components.CatCoat
import com.example.ui.rememberBurnInOffset
import com.example.ui.SmartDockScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var darkRoomDimmed = false
    private var smoothedBrightness = 0.65f
    private var lastAppliedWindowBrightness = -1f

    private val dockViewModel: DockViewModel by viewModels {
        DockViewModel.Factory(applicationContext)
    }

    private val cameraStateViewModel: CameraStateViewModel by viewModels {
        CameraStateViewModel.Factory(applicationContext)
    }

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val st = dockViewModel.settings.value
            if (!st.screensaverOnScreenOff) return
            if (action == Intent.ACTION_SCREEN_OFF) {
                // Prepare the restricted clock view for the next time the screen turns on.
                // The screen is never forced on, and the full app stays behind the lock screen.
                dockViewModel.activateScreensaverMode()
                setLockScreenFlags(showWhenLocked = true, turnScreenOn = false)
            }
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyAlarmWindowFlags(intent)
        requestNotificationPermissionIfNeeded()
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                dockViewModel.chronoState
                    .map { it.isAlarmRinging || it.isTimerRinging }
                    .distinctUntilChanged()
                    .collect { ringing -> updateProximityListening(ringing) }
            }
        }
        sensorManager = getSystemService(SensorManager::class.java)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
            }
            registerReceiver(screenOffReceiver, filter)
        } catch (_: Exception) {
        }
        if (savedInstanceState == null) handleClockIntent(intent)

        // Allow rendering directly into camera cutout areas on all edges (portrait & landscape)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        hideSystemBars()

        setContent {
            val settings by dockViewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.keepScreenAwake) {
                if (settings.keepScreenAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Programmatically rotate into Landscape Dock Mode when toggled, or follow device sensor
            LaunchedEffect(settings.forceLandscapeMode) {
                requestedOrientation = if (settings.forceLandscapeMode) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
                hideSystemBars()
            }

            // Show over the lock screen and wake the display while an alarm is ringing or
            // when automatic screensaver-on-screen-off mode is enabled.
            val alarmStatus by AlarmRingBus.status.collectAsStateWithLifecycle()
            val isScreensaverOverlayActive by dockViewModel.isScreensaverOverlayActive.collectAsStateWithLifecycle()
            LaunchedEffect(
                alarmStatus is AlarmRingStatus.Ringing,
                settings.screensaverOnScreenOff,
                isScreensaverOverlayActive
            ) {
                val ringing = alarmStatus is AlarmRingStatus.Ringing
                setLockScreenFlags(
                    showWhenLocked = ringing || (settings.screensaverOnScreenOff && isScreensaverOverlayActive),
                    // Only a ringing alarm may turn the screen on.
                    turnScreenOn = ringing
                )
            }

            MyApplicationTheme(
                nightRedMode = settings.nightRedMode,
                agentTheme = settings.agentTheme
            ) {
                val isPremium by dockViewModel.isPremium.collectAsStateWithLifecycle()
                val twin by dockViewModel.petTwin.collectAsStateWithLifecycle()
                val activeTwin = twin?.takeIf { it.enabled && isPremium }
                val appearance = CompanionAppearance(
                    useCat = settings.useCatCompanion,
                    // Free version: orange tabby only; other coats are Premium.
                    coat = if (isPremium) CatCoat.fromKey(settings.catCoat) else CatCoat.ORANGE_TABBY,
                    name = settings.catName,
                    palette = activeTwin?.palette,
                    markings = activeTwin?.markings ?: com.example.ui.components.CatMarkings()
                )
                val burnInShift = rememberBurnInOffset(settings.burnInProtection && settings.keepScreenAwake)
                CompositionLocalProvider(
                    LocalDockViewModel provides dockViewModel,
                    LocalCameraStateViewModel provides cameraStateViewModel,
                    LocalCompanionAppearance provides appearance,
                    com.example.ui.LocalIsPremium provides isPremium,
                    com.example.ui.LocalWeatherAnimationCity provides settings.weatherAnimationCity
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().offset { burnInShift }) {
                            SmartDockScreen(viewModel = dockViewModel)
                        }
                        DockGlobalOverlays(viewModel = dockViewModel)
                        com.example.ui.components.CameraCutoutRingOverlay(
                            viewModel = cameraStateViewModel
                        )
                        // While the phone is locked, only a read-only clock + companion is shown.
                        // Any tap asks the user to unlock before the app can be used.
                        val deviceLocked by keyguardLocked.collectAsStateWithLifecycle()
                        if (deviceLocked) {
                            com.example.ui.LockedDockOverlay(
                                viewModel = dockViewModel,
                                onRequestUnlock = { requestUnlock() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyAlarmWindowFlags(intent)
        handleClockIntent(intent)
    }

    /** Voice assistant / other apps: "set an alarm for 7", "set a 10 minute timer". */
    private fun handleClockIntent(launchIntent: Intent?) {
        val message = dockViewModel.handleClockIntent(launchIntent) ?: return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        dockViewModel.setAppVisible(true)
        com.example.premium.PremiumManager.refreshPurchases()
    }

    override fun onStop() {
        dockViewModel.setAppVisible(false)
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    /**
     * Handles:
     * 1. Wave-to-snooze via the proximity sensor while an alarm or timer is ringing.
     * 2. Smooth continuous adaptive brightness via the ambient light sensor (`Sensor.TYPE_LIGHT`),
     *    or dark-room auto-dim fallback.
     */
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor?.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distanceCm = event.values.firstOrNull() ?: return
                val maxRange = event.sensor.maximumRange.takeIf { it > 0f } ?: 5f
                val isNear = distanceCm < maxRange.coerceAtMost(5f)
                // A deliberate wave (far → near → far) only; face-down or covered phones are ignored.
                if (waveDetector.onReading(isNear, System.currentTimeMillis())) {
                    val chrono = dockViewModel.chronoState.value
                    when {
                        chrono.isAlarmRinging -> {
                            // Snooze only. Stopping (and any math challenge) still needs a tap.
                            dockViewModel.snoozeChrono(com.example.ui.components.AiToyActivity.ALARM)
                            Toast.makeText(this, "Snoozed 5 min 👋", Toast.LENGTH_SHORT).show()
                        }
                        chrono.isTimerRinging -> {
                            dockViewModel.snoozeChrono(com.example.ui.components.AiToyActivity.TIMER)
                            Toast.makeText(this, "Timer +1 min 👋", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            Sensor.TYPE_LIGHT -> {
                val lux = event.values.firstOrNull() ?: return
                dockViewModel.updateAmbientLux(lux)
                val settings = dockViewModel.settings.value
                if (settings.adaptiveBrightnessEnabled) {
                    val target = com.example.ui.computeAdaptiveBrightnessForLux(lux, settings.brightnessDimmer)
                    smoothedBrightness = com.example.ui.smoothAdaptiveBrightness(smoothedBrightness, target, 0.25f)
                    if (kotlin.math.abs(smoothedBrightness - lastAppliedWindowBrightness) >= 0.015f) {
                        lastAppliedWindowBrightness = smoothedBrightness
                        window.attributes = window.attributes.apply {
                            screenBrightness = smoothedBrightness
                        }
                    }
                    return
                }
                if (!settings.autoDimInDark) {
                    if (darkRoomDimmed || lastAppliedWindowBrightness >= 0f) {
                        lastAppliedWindowBrightness = -1f
                        setDarkRoomDim(false)
                    }
                    return
                }
                if (!darkRoomDimmed && lux < 3f) setDarkRoomDim(true)
                else if (darkRoomDimmed && lux > 10f) setDarkRoomDim(false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun setDarkRoomDim(dim: Boolean) {
        darkRoomDimmed = dim
        window.attributes = window.attributes.apply {
            screenBrightness = if (dim) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun applyAlarmWindowFlags(launchIntent: Intent?) {
        if (launchIntent?.getBooleanExtra(AlarmRingService.EXTRA_OPENED_FROM_ALARM, false) == true) {
            setAlarmLockScreenMode(true)
        }
    }

    private fun setAlarmLockScreenMode(enabled: Boolean) {
        setLockScreenFlags(showWhenLocked = enabled, turnScreenOn = enabled)
    }

    private fun setLockScreenFlags(showWhenLocked: Boolean, turnScreenOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(showWhenLocked)
            setTurnScreenOn(turnScreenOn)
        } else {
            @Suppress("DEPRECATION")
            if (showWhenLocked) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            @Suppress("DEPRECATION")
            if (turnScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        refreshKeyguardState()
    }

    // --- Lock-screen safety ---
    private val keyguardLocked = kotlinx.coroutines.flow.MutableStateFlow(false)

    private fun refreshKeyguardState() {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        keyguardLocked.value = km?.isKeyguardLocked == true
    }

    private fun requestUnlock() {
        val km = getSystemService(android.app.KeyguardManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    keyguardLocked.value = false
                    dockViewModel.exitScreensaverMode()
                }
            })
        }
    }

    // --- Wave-to-snooze: the proximity sensor listens only while something is ringing ---
    private val waveDetector = com.example.ui.util.WaveGestureDetector()
    private var proximityRegistered = false

    private fun updateProximityListening(ringing: Boolean) {
        val sensor = proximitySensor ?: return
        if (ringing && !proximityRegistered) {
            waveDetector.reset()
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            proximityRegistered = true
        } else if (!ringing && proximityRegistered) {
            sensorManager?.unregisterListener(this, sensor)
            proximityRegistered = false
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        proximityRegistered = false
        val chrono = dockViewModel.chronoState.value
        updateProximityListening(chrono.isAlarmRinging || chrono.isTimerRinging)
        refreshKeyguardState()
        if (!keyguardLocked.value) dockViewModel.exitScreensaverMode()
        hideSystemBars()
        dockViewModel.refreshCalendarEvents()
        dockViewModel.refreshWeatherIfStale()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
        refreshKeyguardState()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

