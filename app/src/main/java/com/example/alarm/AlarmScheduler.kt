package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.local.DockAlarmEntity
import java.util.Calendar

/**
 * Schedules dock alarms with the system [AlarmManager] so they fire even when the app is closed,
 * the screen is off, or the device is in Doze.
 *
 * Alarms are daily (hour + minute). After each one fires, [AlarmReceiver] reschedules it for the
 * next day. Snoozes are scheduled as separate one-shot alarms.
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    const val ACTION_FIRE = "com.example.alarm.ACTION_FIRE"
    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_LABEL = "extra_alarm_label"
    const val EXTRA_TIME_TEXT = "extra_alarm_time_text"
    const val EXTRA_IS_SNOOZE = "extra_alarm_is_snooze"
    const val EXTRA_VIBRATE_ONLY = "extra_alarm_vibrate_only"
    const val EXTRA_CHALLENGE = "extra_alarm_challenge"
    const val EXTRA_SOUND_URI = "extra_alarm_sound_uri"
    const val EXTRA_ONE_TIME = "extra_alarm_one_time"

    private const val PREFS = "dock_alarm_scheduler"
    private const val KEY_SCHEDULED_IDS = "scheduled_ids"
    private const val SNOOZE_CODE_OFFSET = 1_000_000

    fun timeText(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /** Next wall-clock time for [hour]:[minute], strictly after [afterMillis]. */
    fun nextTriggerMillis(hour: Int, minute: Int, afterMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = afterMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= afterMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Monday = 0 … Sunday = 6, matching [DockAlarmEntity.repeatDaysMask] bits. */
    fun dayBit(calendarDayOfWeek: Int): Int = (calendarDayOfWeek + 5) % 7

    /**
     * Next occurrence that respects repeat days and "skip next".
     * A mask of 0 is a one-time alarm (next matching clock time on any day).
     */
    fun nextTriggerMillis(
        hour: Int,
        minute: Int,
        repeatDaysMask: Int,
        skipUntilMillis: Long?,
        afterMillis: Long = System.currentTimeMillis()
    ): Long? {
        val cal = Calendar.getInstance().apply {
            timeInMillis = afterMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (dayOffset in 0..14) {
            if (dayOffset > 0) cal.add(Calendar.DAY_OF_YEAR, 1)
            val candidate = cal.timeInMillis
            if (candidate <= afterMillis) continue
            if (skipUntilMillis != null && candidate <= skipUntilMillis) continue
            val allowed = repeatDaysMask == 0 ||
                (repeatDaysMask and (1 shl dayBit(cal.get(Calendar.DAY_OF_WEEK)))) != 0
            if (allowed) return candidate
        }
        return null
    }

    fun nextTriggerFor(alarm: DockAlarmEntity, afterMillis: Long = System.currentTimeMillis()): Long? =
        nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask, alarm.skipUntilMillis, afterMillis)

    /** "in 7 h 23 min" style text for the next enabled alarm, or null if none. */
    fun nextAlarmSummary(alarms: List<DockAlarmEntity>, now: Long = System.currentTimeMillis()): Pair<DockAlarmEntity, Long>? {
        return alarms.filter { it.isEnabled }
            .mapNotNull { a -> nextTriggerFor(a, now)?.let { a to it } }
            .minByOrNull { it.second }
    }

    fun formatCountdown(millis: Long): String {
        val totalMin = (millis / 60_000L).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h} h ${m} min" else "${m} min"
    }

    /**
     * Brings the system schedule in line with [alarms]: schedules every enabled alarm (and any
     * pending snooze) and cancels alarms that were disabled or deleted since the last call.
     *
     * [afterMillis] is pushed slightly forward when called right after an alarm fires so the
     * same minute is never scheduled twice.
     */
    fun rescheduleAll(
        context: Context,
        alarms: List<DockAlarmEntity>,
        afterMillis: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previouslyScheduled = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        val enabled = alarms.filter { it.isEnabled }
        val enabledIds = enabled.map { it.id }.toSet()

        // Cancel alarms that were deleted or turned off.
        (previouslyScheduled - enabledIds).forEach { id ->
            cancel(context, id)
            cancelSnooze(context, id)
        }
        alarms.filter { !it.isEnabled }.forEach { cancelSnooze(context, it.id) }

        enabled.forEach { alarm ->
            val trigger = nextTriggerFor(alarm, afterMillis)
            if (trigger == null) {
                cancel(context, alarm.id)
            } else {
                scheduleExact(context, alarm.id.toInt(), trigger, alarm, isSnooze = false)
            }
            val snoozeUntil = alarm.snoozedUntilMillis
            if (snoozeUntil != null && snoozeUntil > System.currentTimeMillis()) {
                scheduleExact(context, snoozeCode(alarm.id), snoozeUntil, alarm, isSnooze = true)
            }
        }

        prefs.edit().putStringSet(KEY_SCHEDULED_IDS, enabledIds.map { it.toString() }.toSet()).apply()
        try {
            com.example.widget.ClockWidgetProvider.refreshAll(context, nextAlarmSummary(alarms))
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh failed", e)
        }
    }

    fun scheduleSnooze(
        context: Context,
        alarmId: Long,
        label: String,
        timeText: String,
        atMillis: Long,
        vibrateOnly: Boolean = false,
        challenge: Boolean = false,
        soundUri: String? = null
    ) {
        val parts = timeText.split(":")
        val stub = DockAlarmEntity(
            id = alarmId,
            hour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            label = label,
            vibrateOnly = vibrateOnly,
            dismissChallenge = challenge,
            soundUri = soundUri
        )
        scheduleExact(context, snoozeCode(alarmId), atMillis, stub, isSnooze = true)
    }

    fun cancelSnooze(context: Context, alarmId: Long) {
        cancelByCode(context, snoozeCode(alarmId))
    }

    fun cancel(context: Context, alarmId: Long) {
        cancelByCode(context, alarmId.toInt())
    }

    /** True when the app may schedule exact alarms (always true below Android 12). */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun snoozeCode(alarmId: Long): Int = SNOOZE_CODE_OFFSET + alarmId.toInt()

    private fun fireIntent(context: Context, alarm: DockAlarmEntity, isSnooze: Boolean): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_LABEL, alarm.label)
            putExtra(EXTRA_TIME_TEXT, timeText(alarm.hour, alarm.minute))
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_VIBRATE_ONLY, alarm.vibrateOnly)
            putExtra(EXTRA_CHALLENGE, alarm.dismissChallenge)
            putExtra(EXTRA_SOUND_URI, alarm.soundUri)
            putExtra(EXTRA_ONE_TIME, alarm.repeatDaysMask == 0)
        }

    private fun scheduleExact(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        alarm: DockAlarmEntity,
        isSnooze: Boolean
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            fireIntent(context, alarm, isSnooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Shown when the user taps the alarm icon in the status bar.
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (canScheduleExact(context)) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), operation)
            } else {
                // Fallback: may be delayed by a few minutes in Doze, but still fires.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                Log.w(TAG, "Exact alarm permission missing; using inexact fallback for alarm ${alarm.id}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to schedule alarm ${alarm.id}", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun cancelByCode(context: Context, requestCode: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(existing)
        existing.cancel()
    }
}
