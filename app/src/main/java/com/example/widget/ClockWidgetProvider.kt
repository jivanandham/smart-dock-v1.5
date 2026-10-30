package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.alarm.AlarmScheduler
import com.example.data.local.DockAlarmEntity
import com.example.data.local.DockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen clock widget: live time and date (TextClock updates itself, no battery cost),
 * the next alarm, and a small sleeping cat. Tapping opens Smart Dock.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                val alarms = DockDatabase.getInstance(context).dockDao().getAllAlarms()
                refreshAll(context, AlarmScheduler.nextAlarmSummary(alarms))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) {
            val mgr = AppWidgetManager.getInstance(context)
            onUpdate(context, mgr, mgr.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java)))
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshAll(context: Context, next: Pair<DockAlarmEntity, Long>?) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val alarmText = if (next != null) {
                val (alarm, at) = next
                "⏰ ${AlarmScheduler.timeText(alarm.hour, alarm.minute)} • in ${
                    AlarmScheduler.formatCountdown(at - System.currentTimeMillis())
                }"
            } else {
                "No alarm set"
            }
            val open = PendingIntent.getActivity(
                context, 20,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_clock).apply {
                    setTextViewText(R.id.widget_next_alarm, alarmText)
                    setOnClickPendingIntent(R.id.widget_root, open)
                }
                mgr.updateAppWidget(id, views)
            }
        }
    }
}
