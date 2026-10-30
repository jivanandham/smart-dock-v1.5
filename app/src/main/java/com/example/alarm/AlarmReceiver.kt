package com.example.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.DockDatabase
import kotlinx.coroutines.launch

/**
 * Receives scheduled alarm broadcasts and system events that invalidate the schedule
 * (reboot, app update, clock or time-zone change, exact-alarm permission change).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: "Smart Dock Alarm"
                val timeText = intent.getStringExtra(AlarmScheduler.EXTRA_TIME_TEXT).orEmpty()
                val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
                val oneTime = intent.getBooleanExtra(AlarmScheduler.EXTRA_ONE_TIME, false)

                AlarmRingService.startRinging(
                    context = appContext,
                    alarmId = alarmId,
                    label = label,
                    timeText = timeText,
                    vibrateOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE_ONLY, false),
                    challenge = intent.getBooleanExtra(AlarmScheduler.EXTRA_CHALLENGE, false),
                    soundUri = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI)
                )

                // Clear a consumed snooze, turn off one-time alarms, schedule the next occurrence.
                rescheduleFromDatabase(
                    appContext,
                    clearSnoozeForId = if (isSnooze) alarmId else null,
                    disableId = if (oneTime && !isSnooze) alarmId else null,
                    afterMillis = System.currentTimeMillis() + 60_000L
                )
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                rescheduleFromDatabase(appContext)
            }
        }
    }

    private fun rescheduleFromDatabase(
        context: Context,
        clearSnoozeForId: Long? = null,
        disableId: Long? = null,
        afterMillis: Long = System.currentTimeMillis()
    ) {
        val pending = goAsync()
        AlarmRingBus.ioScope.launch {
            try {
                val dao = DockDatabase.getInstance(context).dockDao()
                if (clearSnoozeForId != null && clearSnoozeForId > 0) {
                    dao.updateAlarmSnooze(clearSnoozeForId, null, System.currentTimeMillis() / 60_000L)
                }
                if (disableId != null && disableId > 0) dao.disableAlarm(disableId)
                AlarmScheduler.rescheduleAll(context, dao.getAllAlarms(), afterMillis)
                if (clearSnoozeForId == null && disableId == null) {
                    // Boot / update / clock change: meeting reminders were cleared too.
                    MeetingAlertScheduler.rescheduleFromCalendar(context)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to reschedule alarms", e)
            } finally {
                pending.finish()
            }
        }
    }
}
