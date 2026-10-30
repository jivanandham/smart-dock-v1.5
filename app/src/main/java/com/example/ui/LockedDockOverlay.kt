package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarm.AlarmRingBus
import com.example.alarm.AlarmRingStatus
import com.example.alarm.AlarmScheduler
import com.example.ui.components.AiToyActivity
import com.example.ui.components.InteractiveAiToyTopStage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * What the dock shows while the phone is LOCKED (screensaver-on-screen-off or a ringing alarm):
 * a read-only clock, date, next alarm and the companion. No meeting titles, notes, assistant or
 * settings. Any tap asks the user to unlock first. A ringing alarm can still be snoozed or stopped,
 * like the system Clock app; alarms with a math challenge ask for unlock first.
 */
@Composable
fun LockedDockOverlay(viewModel: DockViewModel, onRequestUnlock: () -> Unit) {
    val now by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val schedule by viewModel.unifiedSchedule.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val dual by viewModel.dualCityWeather.collectAsStateWithLifecycle()
    val alarmStatus by AlarmRingBus.status.collectAsStateWithLifecycle()
    val ringing = alarmStatus as? AlarmRingStatus.Ringing

    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val night = hour >= 22 || hour < 7
    val timeColor = if (night) Color(0xFFB3261E) else Color(0xFFF5EDE3)
    val subColor = if (night) Color(0xFF7A1D18) else Color(0xFFF6B86E)
    val timeText = SimpleDateFormat(if (settings.use24HourFormat) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val dateText = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now))
    val next = AlarmScheduler.nextAlarmSummary(alarms, now)
    val shift = rememberBurnInOffset(settings.burnInProtection, maxPx = 10)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallow every touch so nothing behind this view can be used while locked.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onRequestUnlock()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().offset { shift }.padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // The companion still reacts to the time of day and alarms, but no titles are shown.
                InteractiveAiToyTopStage(
                    currentTimeMillis = now,
                    schedule = schedule,
                    isAgentThinking = false,
                    manualActivity = if (ringing != null) AiToyActivity.ALARM else null,
                    onSelectManualActivity = {},
                    onQuickAddCalendarStatus = {},
                    fillAvailableHeight = true,
                    hapticsEnabled = false,
                    currentWeather = dual.forAnimation(settings.weatherAnimationCity),
                    modifier = Modifier.fillMaxSize()
                )
                // Transparent shield on top of the stage (blocks its long-press settings).
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { onRequestUnlock() }
                )
            }
            Text(timeText, color = timeColor, fontSize = 110.sp, fontWeight = FontWeight.Light, lineHeight = 110.sp)
            Text(dateText, color = subColor, fontSize = 18.sp)
            if (next != null && ringing == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Alarm ${AlarmScheduler.timeText(next.first.hour, next.first.minute)} · in ${AlarmScheduler.formatCountdown(next.second - now)}",
                    color = subColor.copy(alpha = 0.8f), fontSize = 14.sp
                )
            }
            if (ringing != null) {
                Spacer(Modifier.height(14.dp))
                Text(ringing.label, color = Color(0xFFF5EDE3), fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { viewModel.snoozeChrono(AiToyActivity.ALARM) }) { Text("Snooze 5 min") }
                    Button(onClick = {
                        if (ringing.requiresChallenge) onRequestUnlock() else viewModel.stopChrono(AiToyActivity.ALARM)
                    }) { Text(if (ringing.requiresChallenge) "Unlock to stop" else "Stop") }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text("Tap to unlock", color = subColor.copy(alpha = 0.45f), fontSize = 12.sp)
            }
        }
    }
}
