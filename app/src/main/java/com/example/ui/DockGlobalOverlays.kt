package com.example.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CrashReporter
import com.example.alarm.AlarmScheduler
import com.example.alarm.SleepSoundPlayer
import com.example.ui.components.CatCoat
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * App-wide overlays drawn above [SmartDockScreen]:
 * onboarding, alarm reliability check, companion settings, dismiss challenge,
 * morning briefing, sleep sounds, and the "last crash" report.
 */
@Composable
fun DockGlobalOverlays(viewModel: DockViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val challenge by viewModel.alarmChallenge.collectAsStateWithLifecycle()
    val companionOpen by viewModel.companionSheetOpen.collectAsStateWithLifecycle()
    val reliabilityOpen by viewModel.reliabilitySheetOpen.collectAsStateWithLifecycle()
    val sleepState by viewModel.sleepSoundPlayer.state.collectAsStateWithLifecycle()
    val webcamPreviewActive by viewModel.isWebcamPreview.collectAsStateWithLifecycle()

    // Re-check permissions every time the user comes back from system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val checks = remember(resumeTick) { reliabilityChecks(context) }
    val criticalIssue = checks.any { it.critical && !it.ok }

    var crashReport by remember { mutableStateOf(CrashReporter.pendingReport(context)) }
    val isPremiumNow by viewModel.isPremium.collectAsStateWithLifecycle()
    var shareRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(shareRequest) {
        if (shareRequest > 0) {
            delay(450) // let the dialog close before capturing the screen
            shareCompanionScreenshot(context, settings.catName.ifBlank { "my buddy" })
        }
    }
    var onboardingStep by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Small warning chip while alarms could fail.
        if (criticalIssue && settings.onboardingDone) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF3A0F12).copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 52.dp)
                    .border(1.dp, Color(0xFFFF5A5F), RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { viewModel.openReliabilitySheet() }
            ) {
                Text(
                    "⚠  Alarms may not ring · Fix",
                    color = Color(0xFFFFC9CB),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        // Wellness chip, nudge cards and guided sessions.
        WellnessOverlays(viewModel)
        val premium by viewModel.isPremium.collectAsStateWithLifecycle()

        // Sounds chip: sleep sounds in the evening/night, focus sounds during a Focus session.
        val chrono by viewModel.chronoState.collectAsStateWithLifecycle()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val focusing = chrono.isPandoraRunning
        if (sleepState.playing != null || focusing || hour >= 21 || hour < 6) {
            SleepSoundChip(
                state = sleepState,
                focusMode = focusing,
                onPlay = { sound ->
                    if (!premium) {
                        viewModel.openPaywall()
                        return@SleepSoundChip
                    }
                    val minutes = if (focusing) (chrono.pandoraRemainingSeconds / 60 + 1).coerceAtLeast(5) else 30
                    viewModel.sleepSoundPlayer.play(sound, minutes = minutes)
                },
                onStop = { viewModel.sleepSoundPlayer.stop() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )
        }

        // Paywall, pet twin editor, kids OK-to-wake screen.
        PremiumAndTwinOverlays(viewModel)

        // Meeting alerts & privacy settings
        val meetingSheetOpen by viewModel.meetingAlertsSheetOpen.collectAsStateWithLifecycle()
        if (meetingSheetOpen) {
            MeetingAlertsDialog(
                enabled = settings.meetingAlertsEnabled,
                leadMinutes = settings.meetingAlertLeadMinutes,
                hideTitles = settings.hideMeetingTitles,
                onSave = { e, l, h -> viewModel.saveMeetingAlerts(e, l, h) },
                onDismiss = { viewModel.closeMeetingAlertsSheet() }
            )
        }
    }

    // ---- Dialogs (one at a time, most urgent first) ----
    val activeChallenge = challenge
    when {
        activeChallenge != null -> ChallengeDialog(
            question = activeChallenge.question,
            onSubmit = { viewModel.confirmAlarmChallenge(it) },
            onSnooze = {
                viewModel.cancelAlarmChallenge()
                viewModel.snoozeChrono(com.example.ui.components.AiToyActivity.ALARM)
            }
        )

        crashReport != null -> CrashDialog(
            report = crashReport!!,
            onShare = {
                shareText(context, "Smart Dock crash report", crashReport!!)
                CrashReporter.clear(context)
                crashReport = null
            },
            onDismiss = {
                CrashReporter.clear(context)
                crashReport = null
            }
        )

        !settings.onboardingDone -> when (onboardingStep) {
            0 -> CompanionDialog(
                title = "Meet your dock companion",
                subtitle = "Pick a coat and a name. You can change this any time by long-pressing the cat.",
                initialUseCat = settings.useCatCompanion,
                initialCoat = CatCoat.fromKey(settings.catCoat),
                initialName = settings.catName,
                autoDim = settings.autoDimInDark,
                burnIn = settings.burnInProtection,
                speakBriefing = settings.speakMorningBriefing,
                weatherCity = settings.weatherAnimationCity,
                primaryCityName = settings.cityName,
                secondaryCityName = settings.secondaryCityName,
                onWeatherCityChange = { viewModel.setWeatherAnimationCity(it) },
                soundsMode = settings.companionSoundsMode,
                soundsVolume = settings.companionSoundsVolume,
                onSoundsChange = { mode, vol -> viewModel.setCompanionSounds(mode, vol) },
                adaptiveBrightness = settings.adaptiveBrightnessEnabled,
                onAdaptiveBrightnessChange = { viewModel.setAdaptiveBrightnessEnabled(it) },
                screensaverOnScreenOff = settings.screensaverOnScreenOff,
                onScreensaverOnScreenOffChange = { viewModel.setScreensaverOnScreenOff(it) },
                autoReturnToCompanion = settings.autoReturnToCompanionEnabled,
                onAutoReturnToCompanionChange = { viewModel.setAutoReturnToCompanionEnabled(it) },
                isPremium = isPremiumNow,
                onOpenPaywall = { viewModel.openPaywall() },
                confirmLabel = "Next",
                onSave = { useCat, coat, name, autoDim, burnIn, speak ->
                    viewModel.saveCompanion(useCat, coat.name, name)
                    viewModel.saveDisplayCare(autoDim, burnIn, speak)
                    onboardingStep = 1
                },
                onDismiss = { onboardingStep = 1 }
            )
            else -> ReliabilityDialog(
                checks = checks,
                title = "Make sure your alarms ring",
                confirmLabel = "Done",
                onDone = { viewModel.completeOnboarding() }
            )
        }

        reliabilityOpen -> ReliabilityDialog(
            checks = checks,
            title = "Alarm reliability check",
            confirmLabel = "Close",
            onDone = { viewModel.closeReliabilitySheet() }
        )

        companionOpen -> CompanionDialog(
            title = "Companion",
            subtitle = null,
            initialUseCat = settings.useCatCompanion,
            initialCoat = CatCoat.fromKey(settings.catCoat),
            initialName = settings.catName,
            autoDim = settings.autoDimInDark,
            burnIn = settings.burnInProtection,
            speakBriefing = settings.speakMorningBriefing,
            weatherCity = settings.weatherAnimationCity,
            primaryCityName = settings.cityName,
            secondaryCityName = settings.secondaryCityName,
            onWeatherCityChange = { viewModel.setWeatherAnimationCity(it) },
            soundsMode = settings.companionSoundsMode,
            soundsVolume = settings.companionSoundsVolume,
            onSoundsChange = { mode, vol -> viewModel.setCompanionSounds(mode, vol) },
            adaptiveBrightness = settings.adaptiveBrightnessEnabled,
            onAdaptiveBrightnessChange = { viewModel.setAdaptiveBrightnessEnabled(it) },
            screensaverOnScreenOff = settings.screensaverOnScreenOff,
            onScreensaverOnScreenOffChange = { viewModel.setScreensaverOnScreenOff(it) },
            autoReturnToCompanion = settings.autoReturnToCompanionEnabled,
            onAutoReturnToCompanionChange = { viewModel.setAutoReturnToCompanionEnabled(it) },
            onOpenSystemScreensaver = {
                safeStart(context, Intent("android.settings.DREAM_SETTINGS"), Intent(Settings.ACTION_DISPLAY_SETTINGS))
            },
            confirmLabel = "Save",
            onSave = { useCat, coat, name, autoDim, burnIn, speak ->
                viewModel.saveCompanion(useCat, coat.name, name)
                viewModel.saveDisplayCare(autoDim, burnIn, speak)
                viewModel.closeCompanionSheet()
            },
            onDismiss = { viewModel.closeCompanionSheet() },
            onOpenReliability = {
                viewModel.closeCompanionSheet()
                viewModel.openReliabilitySheet()
            },
            onOpenWellness = {
                viewModel.closeCompanionSheet()
                viewModel.openWellnessSheet()
            },
            isPremium = isPremiumNow,
            onOpenPaywall = {
                viewModel.closeCompanionSheet()
                viewModel.openPaywall()
            },
            onOpenPetTwin = {
                viewModel.closeCompanionSheet()
                viewModel.openPetTwin()
            },
            onOpenKids = {
                viewModel.closeCompanionSheet()
                viewModel.openKidsSheet()
            },
            onShare = {
                viewModel.closeCompanionSheet()
                shareRequest++
            },
            onOpenMeetingAlerts = {
                viewModel.closeCompanionSheet()
                viewModel.openMeetingAlertsSheet()
            },
            webcamTestActive = webcamPreviewActive,
            onPreviewWebcam = {
                viewModel.closeCompanionSheet()
                viewModel.toggleWebcamPreview()
            }
        )
    }
}

// -------------------------------------------------------------------------------------------
// Reliability checks
// -------------------------------------------------------------------------------------------

private data class ReliabilityCheck(
    val title: String,
    val detail: String,
    val ok: Boolean,
    val critical: Boolean,
    val fixLabel: String,
    val fix: (Context) -> Unit
)

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private fun safeStart(context: Context, intent: Intent, fallback: Intent? = null) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity((fallback ?: appSettingsIntent(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Open Settings › Apps › Smart Dock", Toast.LENGTH_LONG).show()
        }
    }
}

private fun reliabilityChecks(context: Context): List<ReliabilityCheck> {
    val list = mutableListOf<ReliabilityCheck>()
    val pkg = context.packageName

    list += ReliabilityCheck(
        title = "Exact alarms",
        detail = "Lets alarms ring at the exact minute, even in battery saver.",
        ok = AlarmScheduler.canScheduleExact(context),
        critical = true,
        fixLabel = "Allow"
    ) { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            safeStart(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
        }
    }

    list += ReliabilityCheck(
        title = "Notifications",
        detail = "Needed to show the ringing alarm with Snooze and Stop.",
        ok = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        critical = true,
        fixLabel = "Allow"
    ) { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeStart(
                ctx,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            )
        } else {
            safeStart(ctx, appSettingsIntent(ctx))
        }
    }

    val fullScreenOk = if (Build.VERSION.SDK_INT >= 34) {
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true
    } else {
        true
    }
    list += ReliabilityCheck(
        title = "Full-screen alarms",
        detail = "Shows the alarm over the lock screen instead of a small banner.",
        ok = fullScreenOk,
        critical = false,
        fixLabel = "Allow"
    ) { ctx ->
        if (Build.VERSION.SDK_INT >= 34) {
            safeStart(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
        }
    }

    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    list += ReliabilityCheck(
        title = "Battery: Unrestricted",
        detail = "Stops the phone from closing Smart Dock overnight. Choose \"Unrestricted\" / \"Don't optimise\".",
        ok = pm?.isIgnoringBatteryOptimizations(pkg) ?: true,
        critical = false,
        fixLabel = "Open"
    ) { ctx -> safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

    list += ReliabilityCheck(
        title = "Calendar (optional)",
        detail = "Shows your Gmail and Outlook meetings, Join buttons and calendar-driven cat moods.",
        ok = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
        critical = false,
        fixLabel = "Allow"
    ) { ctx -> safeStart(ctx, appSettingsIntent(ctx)) }

    list += ReliabilityCheck(
        title = "Screensaver (recommended)",
        detail = "Shows the clock and cat automatically while charging. Pick \"Smart Dock\" as the screen saver.",
        ok = true, // cannot be read reliably; always offered
        critical = false,
        fixLabel = "Set up"
    ) { ctx -> safeStart(ctx, Intent("android.settings.DREAM_SETTINGS"), Intent(Settings.ACTION_DISPLAY_SETTINGS)) }

    val brand = Build.MANUFACTURER.lowercase()
    val aggressive = listOf("xiaomi", "redmi", "poco", "samsung", "oneplus", "huawei", "honor", "oppo", "vivo", "realme")
    val match = aggressive.firstOrNull { brand.contains(it) }
    if (match != null) {
        list += ReliabilityCheck(
            title = "Extra step for ${Build.MANUFACTURER}",
            detail = "This brand closes background apps aggressively. Also enable Autostart / \"Allow background activity\". The guide shows exactly where.",
            ok = true,
            critical = false,
            fixLabel = "Guide"
        ) { ctx ->
            val slug = when (match) {
                "redmi", "poco" -> "xiaomi"
                "honor" -> "huawei"
                else -> match
            }
            safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/$slug")))
        }
    }
    return list
}

@Composable
private fun ReliabilityDialog(
    checks: List<ReliabilityCheck>,
    title: String,
    confirmLabel: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                checks.forEach { check ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        check.ok -> Color(0xFF2E7D32)
                                        check.critical -> Color(0xFFC62828)
                                        else -> Color(0xFFB7791F)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (check.ok) "✓" else "!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(check.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(check.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!check.ok || check.fixLabel in listOf("Set up", "Guide")) {
                            TextButton(onClick = {
                                if (check.title.startsWith("Calendar")) {
                                    calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                                } else {
                                    check.fix(context)
                                }
                            }) { Text(check.fixLabel) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(confirmLabel) } }
    )
}

// -------------------------------------------------------------------------------------------
// Companion settings
// -------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionDialog(
    title: String,
    subtitle: String?,
    initialUseCat: Boolean,
    initialCoat: CatCoat,
    initialName: String,
    autoDim: Boolean,
    burnIn: Boolean,
    speakBriefing: Boolean,
    weatherCity: String,
    primaryCityName: String,
    secondaryCityName: String,
    onWeatherCityChange: (String) -> Unit,
    soundsMode: String = "ALL",
    soundsVolume: Float = 0.35f,
    onSoundsChange: (String, Float) -> Unit = { _, _ -> },
    adaptiveBrightness: Boolean = true,
    onAdaptiveBrightnessChange: (Boolean) -> Unit = {},
    screensaverOnScreenOff: Boolean = true,
    onScreensaverOnScreenOffChange: (Boolean) -> Unit = {},
    autoReturnToCompanion: Boolean = true,
    onAutoReturnToCompanionChange: (Boolean) -> Unit = {},
    onOpenSystemScreensaver: (() -> Unit)? = null,
    confirmLabel: String,
    onSave: (useCat: Boolean, coat: CatCoat, name: String, autoDim: Boolean, burnIn: Boolean, speak: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenReliability: (() -> Unit)? = null,
    onOpenWellness: (() -> Unit)? = null,
    isPremium: Boolean = true,
    onOpenPaywall: (() -> Unit)? = null,
    onOpenPetTwin: (() -> Unit)? = null,
    onOpenKids: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onOpenMeetingAlerts: (() -> Unit)? = null,
    webcamTestActive: Boolean = false,
    onPreviewWebcam: (() -> Unit)? = null
) {
    var useCat by remember { mutableStateOf(initialUseCat) }
    var coat by remember { mutableStateOf(initialCoat) }
    var name by remember { mutableStateOf(initialName) }
    var dim by remember { mutableStateOf(autoDim) }
    var shift by remember { mutableStateOf(burnIn) }
    var speak by remember { mutableStateOf(speakBriefing) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cat companion", modifier = Modifier.weight(1f))
                    Switch(checked = useCat, onCheckedChange = { useCat = it })
                }
                if (useCat) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CatCoat.entries.forEach { option ->
                            val locked = !isPremium && option != CatCoat.ORANGE_TABBY
                            FilterChip(
                                selected = coat == option,
                                onClick = { if (locked) onOpenPaywall?.invoke() else coat = option },
                                label = { Text(if (locked) "🔒 ${option.label}" else option.label) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(option.base)
                                            .border(1.dp, option.dark, CircleShape)
                                    )
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("The original mecha robot will be shown instead.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(4.dp))
                Text("Weather animation", fontWeight = FontWeight.SemiBold)
                Text(
                    "Which city's live weather plays behind the companion (rain, snow, sun, stars).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = weatherCity != "SECONDARY",
                        onClick = { onWeatherCityChange("PRIMARY") },
                        label = { Text(primaryCityName) }
                    )
                    FilterChip(
                        selected = weatherCity == "SECONDARY",
                        onClick = { onWeatherCityChange("SECONDARY") },
                        label = { Text(secondaryCityName) }
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text("Companion sounds", fontWeight = FontWeight.SemiBold)
                Text(
                    "Soft chirps, purrs and trills for reminders and taps. Automatically silent during meetings, Focus, alarms, Do Not Disturb and night mode.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "ALL" to "All sounds",
                        "REMINDERS" to "Reminders only",
                        "OFF" to "Off"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = soundsMode == key,
                            onClick = { onSoundsChange(key, soundsVolume) },
                            label = { Text(label) }
                        )
                    }
                }
                if (soundsMode != "OFF") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume", fontSize = 12.sp, modifier = Modifier.width(56.dp))
                        Slider(
                            value = soundsVolume,
                            onValueChange = { onSoundsChange(soundsMode, it) },
                            valueRange = 0.05f..1.0f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Display care & screensaver", fontWeight = FontWeight.SemiBold)
                ToggleRow(
                    label = "Adaptive brightness (smooth ambient sensor curve)",
                    checked = adaptiveBrightness,
                    onChange = onAdaptiveBrightnessChange
                )
                ToggleRow(
                    label = "Act as screensaver when screen turns off",
                    checked = screensaverOnScreenOff,
                    onChange = onScreensaverOnScreenOffChange
                )
                ToggleRow(
                    label = "Return to companion after 1 min idle (never while typing)",
                    checked = autoReturnToCompanion,
                    onChange = onAutoReturnToCompanionChange
                )
                ToggleRow("Dim automatically in a dark room", dim) { dim = it }
                ToggleRow("Burn-in protection (tiny pixel shift)", shift) { shift = it }
                ToggleRow("Read the morning briefing aloud", speak) { speak = it }
                if (onOpenSystemScreensaver != null) {
                    OutlinedButton(onClick = onOpenSystemScreensaver, modifier = Modifier.fillMaxWidth()) {
                        Text("🌙  Set Smart Dock as Android System Screensaver")
                    }
                }

                if (onOpenPetTwin != null) {
                    OutlinedButton(onClick = onOpenPetTwin, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isPremium) "🐾  Pet twin: use my own pet" else "🔒  Pet twin: use my own pet")
                    }
                }
                if (onShare != null) {
                    OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Share my buddy") }
                }
                if (onOpenMeetingAlerts != null) {
                    OutlinedButton(onClick = onOpenMeetingAlerts, modifier = Modifier.fillMaxWidth()) { Text("Meeting alerts & privacy") }
                }
                if (onOpenKids != null) {
                    OutlinedButton(onClick = onOpenKids, modifier = Modifier.fillMaxWidth()) { Text("Kids OK-to-wake mode") }
                }
                if (onOpenPaywall != null) {
                    OutlinedButton(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isPremium) "⭐  Premium (unlocked)" else "⭐  Get Premium")
                    }
                }
                if (onOpenWellness != null) {
                    OutlinedButton(onClick = onOpenWellness, modifier = Modifier.fillMaxWidth()) {
                        Text("Wellness & reminders")
                    }
                }
                if (onPreviewWebcam != null) {
                    OutlinedButton(onClick = onPreviewWebcam, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (webcamTestActive) {
                                "🎥  Turn off webcam ring & on-call mode"
                            } else {
                                "🎥  Test webcam ring & on-call mode (stays on until turned off)"
                            }
                        )
                    }
                }
                if (onOpenReliability != null) {
                    OutlinedButton(onClick = onOpenReliability, modifier = Modifier.fillMaxWidth()) {
                        Text("Check alarm reliability")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(useCat, coat, name, dim, shift, speak) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// -------------------------------------------------------------------------------------------
// Dismiss challenge, crash report, sleep sounds
// -------------------------------------------------------------------------------------------

@Composable
private fun ChallengeDialog(question: String, onSubmit: (String) -> Boolean, onSnooze: () -> Unit) {
    var answer by remember(question) { mutableStateOf("") }
    var wrong by remember(question) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { /* must solve or snooze */ },
        title = { Text("Solve to stop the alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$question = ?", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it.filter { c -> c.isDigit() }.take(4); wrong = false },
                    singleLine = true,
                    isError = wrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(if (wrong) "Not quite — try again" else "Answer") }
                )
            }
        },
        confirmButton = { Button(onClick = { wrong = !onSubmit(answer) }) { Text("Stop alarm") } },
        dismissButton = { TextButton(onClick = onSnooze) { Text("Snooze 5 min") } }
    )
}

@Composable
private fun CrashDialog(report: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smart Dock closed unexpectedly") },
        text = {
            Text(
                "Sharing the report helps fix the problem. It contains only technical details " +
                    "(device model, Android version, error), no calendar or personal data.\n\n" +
                    report.lineSequence().take(4).joinToString("\n"),
                fontSize = 12.sp
            )
        },
        confirmButton = { Button(onClick = onShare) { Text("Share report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

private fun shareText(context: Context, subject: String, body: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

@Composable
private fun SleepSoundChip(
    state: SleepSoundPlayer.State,
    focusMode: Boolean,
    onPlay: (SleepSoundPlayer.Sound) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val playing = state.playing
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (expanded && playing == null) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0E1520).copy(alpha = 0.95f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val options = if (focusMode) {
                        listOf(SleepSoundPlayer.Sound.BROWN_NOISE, SleepSoundPlayer.Sound.RAIN, SleepSoundPlayer.Sound.CAFE)
                    } else {
                        listOf(SleepSoundPlayer.Sound.BROWN_NOISE, SleepSoundPlayer.Sound.RAIN, SleepSoundPlayer.Sound.PURR)
                    }
                    options.forEach { sound ->
                        Text(
                            sound.label + if (focusMode) " · until break" else " · 30 min",
                            color = Color(0xFFF5EDE3),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    expanded = false
                                    onPlay(sound)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF0E1520).copy(alpha = 0.85f),
            modifier = Modifier
                .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .clickable { if (playing != null) onStop() else expanded = !expanded }
        ) {
            val remainingMin = if (playing != null) ((state.endsAtMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) else 0
            Text(
                when {
                    playing != null -> "${if (focusMode) "🎧" else "🌙"}  ${playing.label} · $remainingMin min · Stop"
                    focusMode -> "🎧  Focus sounds"
                    else -> "🌙  Sleep sounds"
                },
                color = Color(0xFFC7CBFF),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun MeetingAlertsDialog(
    enabled: Boolean,
    leadMinutes: Int,
    hideTitles: Boolean,
    onSave: (Boolean, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var on by remember { mutableStateOf(enabled) }
    var lead by remember { mutableIntStateOf(leadMinutes) }
    var hide by remember { mutableStateOf(hideTitles) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meeting alerts & privacy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A reminder with a Join button before each meeting from your synced Gmail and Outlook " +
                        "calendars. It works even when the app is closed. Declined, cancelled and all-day events are skipped.",
                    fontSize = 13.sp
                )
                ToggleRow("Meeting alerts", on) { on = it }
                if (on) {
                    Text("Remind me", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10, 15).forEach { m ->
                            FilterChip(selected = lead == m, onClick = { lead = m }, label = { Text("$m min") })
                        }
                    }
                    Text(
                        "Tip: if Google Calendar or Outlook already reminds you, turn their reminders off to avoid double alerts.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Privacy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                ToggleRow("Hide meeting titles on the screensaver and lock screen", hide) { hide = it }
                Text(
                    "Lock-screen notifications never show titles. When the phone is locked, the dock shows only the clock and companion.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(on, lead, hide) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
