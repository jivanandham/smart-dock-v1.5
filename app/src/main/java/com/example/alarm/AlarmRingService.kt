package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.DockDatabase
import kotlinx.coroutines.launch

/**
 * Foreground service that actually rings: plays the alarm sound with a gradual volume ramp,
 * vibrates, and shows a full-screen notification with Snooze and Stop actions.
 *
 * Starting a foreground service from the background is allowed here because the trigger comes
 * from an AlarmManager.setAlarmClock() alarm.
 */
class AlarmRingService : Service() {

    companion object {
        private const val TAG = "AlarmRingService"

        const val ACTION_RING = "com.example.alarm.ACTION_RING"
        const val ACTION_SNOOZE = "com.example.alarm.ACTION_SNOOZE"
        const val ACTION_STOP = "com.example.alarm.ACTION_STOP"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_OPENED_FROM_ALARM = "extra_opened_from_alarm"

        private const val CHANNEL_ID = "dock_alarm_ringing"
        private const val NOTIFICATION_ID = 4201
        private const val RAMP_DURATION_MS = 30_000L
        private const val RAMP_STEP_MS = 1_000L
        private const val AUTO_SILENCE_MS = 10 * 60_000L
        private const val START_VOLUME = 0.08f

        fun startRinging(
            context: Context,
            alarmId: Long,
            label: String,
            timeText: String,
            vibrateOnly: Boolean = false,
            challenge: Boolean = false,
            soundUri: String? = null
        ) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_RING
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_LABEL, label)
                putExtra(AlarmScheduler.EXTRA_TIME_TEXT, timeText)
                putExtra(AlarmScheduler.EXTRA_VIBRATE_ONLY, vibrateOnly)
                putExtra(AlarmScheduler.EXTRA_CHALLENGE, challenge)
                putExtra(AlarmScheduler.EXTRA_SOUND_URI, soundUri)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start alarm service", e)
                // At least reflect the ringing state in the UI if the app is open.
                AlarmRingBus.update(AlarmRingStatus.Ringing(alarmId, label, timeText, challenge))
            }
        }

        fun snooze(
            context: Context,
            alarmId: Long,
            label: String,
            timeText: String,
            minutes: Int = 5
        ) {
            sendCommand(context, ACTION_SNOOZE) {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_LABEL, label)
                putExtra(AlarmScheduler.EXTRA_TIME_TEXT, timeText)
                putExtra(EXTRA_SNOOZE_MINUTES, minutes)
            }
        }

        fun stop(context: Context, alarmId: Long) {
            sendCommand(context, ACTION_STOP) {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            }
        }

        private fun sendCommand(context: Context, action: String, extras: Intent.() -> Unit) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                this.action = action
                extras()
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Service could not be started (e.g. app in background). Handle state directly.
                Log.w(TAG, "startService failed for $action, applying directly", e)
                handleCommandWithoutService(context.applicationContext, intent)
            }
        }

        private fun handleCommandWithoutService(context: Context, intent: Intent) {
            val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
            when (intent.action) {
                ACTION_SNOOZE -> {
                    val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL).orEmpty()
                    val timeText = intent.getStringExtra(AlarmScheduler.EXTRA_TIME_TEXT).orEmpty()
                    val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
                    applySnooze(context, alarmId, label, timeText, minutes)
                }
                ACTION_STOP -> applyStop(context, alarmId)
            }
        }

        private fun applySnooze(context: Context, alarmId: Long, label: String, timeText: String, minutes: Int) {
            val until = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
            AlarmScheduler.scheduleSnooze(context, alarmId, label, timeText, until)
            AlarmRingBus.update(AlarmRingStatus.Snoozed(alarmId, label, timeText, until))
            if (alarmId > 0) {
                AlarmRingBus.ioScope.launch {
                    DockDatabase.getInstance(context).dockDao()
                        .updateAlarmSnooze(alarmId, until, System.currentTimeMillis() / 60_000L)
                }
            }
        }

        private fun applyStop(context: Context, alarmId: Long) {
            AlarmScheduler.cancelSnooze(context, alarmId)
            AlarmRingBus.update(AlarmRingStatus.Idle)
            if (alarmId > 0) {
                AlarmRingBus.ioScope.launch {
                    DockDatabase.getInstance(context).dockDao()
                        .updateAlarmSnooze(alarmId, null, System.currentTimeMillis() / 60_000L)
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentVolume = START_VOLUME

    private var currentAlarmId: Long = -1L
    private var currentLabel: String = ""
    private var currentTimeText: String = ""
    private var currentVibrateOnly = false
    private var currentChallenge = false
    private var currentSoundUri: String? = null

    private val rampRunnable = object : Runnable {
        override fun run() {
            val step = (1f - START_VOLUME) / (RAMP_DURATION_MS / RAMP_STEP_MS).toFloat()
            currentVolume = (currentVolume + step).coerceAtMost(1f)
            try {
                player?.setVolume(currentVolume, currentVolume)
            } catch (_: IllegalStateException) {
            }
            if (currentVolume < 1f) handler.postDelayed(this, RAMP_STEP_MS)
        }
    }

    private val autoSilenceRunnable = Runnable {
        Log.i(TAG, "Auto-silencing alarm after timeout")
        applyStop(applicationContext, currentAlarmId)
        stopRingingAndSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STOP
        when (action) {
            ACTION_RING -> {
                currentAlarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
                currentLabel = intent?.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: "Smart Dock Alarm"
                currentTimeText = intent?.getStringExtra(AlarmScheduler.EXTRA_TIME_TEXT).orEmpty()
                currentVibrateOnly = intent?.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE_ONLY, false) ?: false
                currentChallenge = intent?.getBooleanExtra(AlarmScheduler.EXTRA_CHALLENGE, false) ?: false
                currentSoundUri = intent?.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI)

                // Publish state first so an activity launched by the full-screen intent sees it.
                AlarmRingBus.update(
                    AlarmRingStatus.Ringing(currentAlarmId, currentLabel, currentTimeText, currentChallenge)
                )
                startInForeground(buildNotification())
                if (!currentVibrateOnly) startSound()
                startVibration()
                handler.removeCallbacks(autoSilenceRunnable)
                handler.postDelayed(autoSilenceRunnable, AUTO_SILENCE_MS)
            }

            ACTION_SNOOZE -> {
                val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, currentAlarmId) ?: currentAlarmId
                val label = intent?.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: currentLabel
                val timeText = intent?.getStringExtra(AlarmScheduler.EXTRA_TIME_TEXT) ?: currentTimeText
                val minutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 5) ?: 5
                applySnooze(applicationContext, id, label, timeText, minutes)
                // Keep the alarm's options for the snoozed ring.
                AlarmScheduler.scheduleSnooze(
                    applicationContext, id, label, timeText,
                    System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L,
                    currentVibrateOnly, currentChallenge, currentSoundUri
                )
                stopRingingAndSelf()
            }

            else -> {
                val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, currentAlarmId) ?: currentAlarmId
                applyStop(applicationContext, id)
                stopRingingAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseAudioAndVibration()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun alarmSoundUri(): Uri? {
        currentSoundUri?.let { custom ->
            try {
                return Uri.parse(custom)
            } catch (_: Exception) {
            }
        }
        return try {
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        } catch (_: Exception) {
            Settings.System.DEFAULT_ALARM_ALERT_URI
        }
    }

    private fun startSound() {
        releasePlayerOnly()
        val uri = alarmSoundUri() ?: return
        try {
            currentVolume = START_VOLUME
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                setVolume(currentVolume, currentVolume)
                prepare()
                start()
            }
            handler.removeCallbacks(rampRunnable)
            handler.postDelayed(rampRunnable, RAMP_STEP_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to play alarm sound", e)
            releasePlayerOnly()
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = vib
        if (vib?.hasVibrator() != true) return
        val pattern = longArrayOf(0, 700, 500)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ringing alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows the ringing alarm with snooze and stop controls"
                setSound(null, null) // The service plays the sound itself (with a volume ramp).
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPENED_FROM_ALARM, true)
        }
        val fullScreen = PendingIntent.getActivity(
            this, 10, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeAction = PendingIntent.getService(
            this, 11,
            Intent(this, AlarmRingService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = PendingIntent.getService(
            this, 12,
            Intent(this, AlarmRingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (currentTimeText.isNotBlank()) "Alarm • $currentTimeText" else "Alarm"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(currentLabel)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Snooze 5 min", snoozeAction)
            .apply {
                // With a dismiss challenge, stopping is only possible inside the app.
                if (currentChallenge) {
                    setContentText("$currentLabel • Open Smart Dock to solve and dismiss")
                    addAction(0, "Open", fullScreen)
                } else {
                    addAction(0, "Stop", stopAction)
                }
            }
            .build()
    }

    private fun stopRingingAndSelf() {
        releaseAudioAndVibration()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayerOnly() {
        handler.removeCallbacks(rampRunnable)
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
    }

    private fun releaseAudioAndVibration() {
        handler.removeCallbacks(autoSilenceRunnable)
        releasePlayerOnly()
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
    }
}
