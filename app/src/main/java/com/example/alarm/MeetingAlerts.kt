package com.example.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.calendar.MeetingLinks
import com.example.data.calendar.NativeCalendarReader
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.preferences.DockPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reliable meeting reminders, scheduled with AlarmManager like alarms, so they fire even when
 * the app is closed or the phone is asleep. Re-scheduled whenever the calendar or the settings
 * change, and after reboot.
 *
 * Skips all-day, completed, cancelled and declined events (the calendar reader already drops
 * cancelled/declined ones). Duplicate invites across Gmail/Outlook are merged upstream.
 */
object MeetingAlertScheduler {
    private const val TAG = "MeetingAlerts"
    private const val PREFS = "meeting_alerts"
    private const val KEY_CODES = "scheduled_codes"
    private const val CODE_BASE = 3_000_000
    const val ACTION_MEETING_ALERT = "com.example.alarm.ACTION_MEETING_ALERT"
    const val EXTRA_TITLE = "meeting_title"
    const val EXTRA_START = "meeting_start"
    const val EXTRA_JOIN = "meeting_join_url"
    const val EXTRA_LEAD = "meeting_lead_minutes"
    const val EXTRA_HIDE_TITLE = "meeting_hide_title"
    private const val LOOKAHEAD_MS = 36 * 3_600_000L

    fun requestCode(eventId: String): Int = CODE_BASE + (eventId.hashCode() and 0x7fffffff) % 900_000

    /** Pure: which events need an alert, and when. */
    fun planAlerts(
        events: List<UnifiedScheduleItem>,
        leadMinutes: Int,
        now: Long
    ): List<Pair<UnifiedScheduleItem, Long>> = events
        .asSequence()
        .filter { !it.isAllDay && !it.isCompleted }
        .map { it to (it.startTimeMillis - leadMinutes * 60_000L) }
        .filter { (ev, at) -> at > now && ev.startTimeMillis - now < LOOKAHEAD_MS }
        .distinctBy { it.first.id }
        .toList()

    fun reschedule(
        context: Context,
        events: List<UnifiedScheduleItem>,
        enabled: Boolean,
        leadMinutes: Int,
        hideTitles: Boolean,
        now: Long = System.currentTimeMillis()
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        val plan = if (enabled) planAlerts(events, leadMinutes, now) else emptyList()
        val wanted = plan.map { requestCode(it.first.id) }.toSet()

        // Cancel alerts for events that were removed, moved out of range, or when disabled.
        (previous - wanted).forEach { code ->
            PendingIntent.getBroadcast(
                context, code, Intent(context, MeetingAlertReceiver::class.java).setAction(ACTION_MEETING_ALERT),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { am.cancel(it); it.cancel() }
        }

        plan.forEach { (ev, at) ->
            val intent = Intent(context, MeetingAlertReceiver::class.java).apply {
                action = ACTION_MEETING_ALERT
                putExtra(EXTRA_TITLE, ev.title)
                putExtra(EXTRA_START, ev.startTimeMillis)
                putExtra(EXTRA_JOIN, ev.joinUrl ?: MeetingLinks.find(ev.location, ev.description))
                putExtra(EXTRA_LEAD, leadMinutes)
                putExtra(EXTRA_HIDE_TITLE, hideTitles)
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode(ev.id), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (AlarmScheduler.canScheduleExact(context)) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        prefs.edit().putStringSet(KEY_CODES, wanted.map { it.toString() }.toSet()).apply()
    }

    /** Used after reboot / app update when no ViewModel is running. */
    suspend fun rescheduleFromCalendar(context: Context) {
        try {
            val settings = DockPreferencesRepository(context).settingsFlow.first()
            val events = NativeCalendarReader(context).queryUpcomingSystemEvents(36)
            reschedule(context, events, settings.meetingAlertsEnabled, settings.meetingAlertLeadMinutes, settings.hideMeetingTitles)
        } catch (e: Exception) {
            Log.w(TAG, "Could not reschedule meeting alerts", e)
        }
    }
}

class MeetingAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MeetingAlertScheduler.ACTION_MEETING_ALERT) return
        val title = intent.getStringExtra(MeetingAlertScheduler.EXTRA_TITLE) ?: "Meeting"
        val start = intent.getLongExtra(MeetingAlertScheduler.EXTRA_START, 0L)
        val join = intent.getStringExtra(MeetingAlertScheduler.EXTRA_JOIN)
        val hide = intent.getBooleanExtra(MeetingAlertScheduler.EXTRA_HIDE_TITLE, false)
        val minutes = ((start - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        val whenText = if (minutes <= 0) "now" else "in $minutes min"
        post(context, title, whenText, join, hide, start)
        // The companion reacts on screen too (it looks at the clock / wears its headset).
        AlarmRingBus.ioScope.launch { MeetingAlertBus.emit(title, start, join) }
    }

    private fun post(context: Context, title: String, whenText: String, join: String?, hide: Boolean, start: Long) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Meeting alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders before your calendar meetings, with a Join button"
                }
            )
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 40, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val shownTitle = if (hide) "Meeting" else title
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("$shownTitle $whenText")
            .setContentText(if (join != null) "Tap Join to open ${MeetingLinks.platformLabel(join)}" else "Starting soon")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter((start - System.currentTimeMillis() + 15 * 60_000L).coerceAtLeast(60_000L))
            // On the lock screen, never show the meeting title.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentTitle("Meeting $whenText")
                    .build()
            )
        if (join != null) {
            val joinIntent = PendingIntent.getActivity(
                context, (start % 100_000).toInt() + 41,
                Intent(Intent.ACTION_VIEW, Uri.parse(join)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Join", joinIntent)
        }
        try {
            nm.notify(NOTIFICATION_BASE + (start / 60_000L % 1000).toInt(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val CHANNEL = "meeting_alerts"
        private const val NOTIFICATION_BASE = 6000
    }
}

/** In-app signal so the companion can react when a meeting alert fires. */
object MeetingAlertBus {
    data class Alert(val title: String, val startMillis: Long, val joinUrl: String?)
    val alerts = kotlinx.coroutines.flow.MutableSharedFlow<Alert>(extraBufferCapacity = 4)
    fun emit(title: String, start: Long, join: String?) {
        alerts.tryEmit(Alert(title, start, join))
    }
}
