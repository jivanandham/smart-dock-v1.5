package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmScheduler
import com.example.data.local.DockAlarmEntity
import com.example.ui.LocalDockViewModel

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val DayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val OptionAccent = Color(0xFFF6B86E)

/**
 * Per-alarm options under each alarm row: repeat days, next-ring countdown, skip next,
 * vibrate only, dismiss challenge and custom sound.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmOptionsRow(alarm: DockAlarmEntity, modifier: Modifier = Modifier) {
    val vm = LocalDockViewModel.current ?: return
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val next = if (alarm.isEnabled) AlarmScheduler.nextTriggerFor(alarm, now) else null
    val skipped = (alarm.skipUntilMillis ?: 0L) > now

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.let { data ->
                @Suppress("DEPRECATION")
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            vm.updateAlarmOptions(alarm, soundUri = uri?.toString())
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DayLetters.forEachIndexed { index, letter ->
                val on = (alarm.repeatDaysMask and (1 shl index)) != 0
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (on) OptionAccent.copy(alpha = 0.22f) else Color.Transparent)
                        .border(1.dp, if (on) OptionAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                        .clickable { vm.updateAlarmOptions(alarm, repeatDaysMask = alarm.repeatDaysMask xor (1 shl index)) }
                        .semantics {
                            contentDescription = DayNames[index]
                            selected = on
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(letter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) OptionAccent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            text = when {
                !alarm.isEnabled -> "Off"
                next == null -> "No upcoming day selected"
                else -> buildString {
                    append(repeatSummary(alarm.repeatDaysMask))
                    append(" · rings in ")
                    append(AlarmScheduler.formatCountdown(next - now))
                    if (skipped) append(" (next one skipped)")
                }
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionPill(if (skipped) "Un-skip" else "Skip next", skipped) { vm.toggleSkipNext(alarm) }
            OptionPill("Vibrate only", alarm.vibrateOnly) { vm.updateAlarmOptions(alarm, vibrateOnly = !alarm.vibrateOnly) }
            OptionPill("Math to stop", alarm.dismissChallenge) { vm.updateAlarmOptions(alarm, dismissChallenge = !alarm.dismissChallenge) }
            OptionPill(if (alarm.soundUri != null) "Custom sound" else "Default sound", alarm.soundUri != null) {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                    alarm.soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                }
                try {
                    soundPicker.launch(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, "No sound picker available", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun OptionPill(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (active) OptionAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) OptionAccent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (active) OptionAccent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

fun repeatSummary(mask: Int): String = when (mask) {
    0 -> "Once"
    DockAlarmEntity.ALL_DAYS_MASK -> "Every day"
    DockAlarmEntity.WEEKDAYS_MASK -> "Weekdays"
    DockAlarmEntity.WEEKEND_MASK -> "Weekends"
    else -> (0..6).filter { (mask and (1 shl it)) != 0 }.joinToString(" ") { DayNames[it].take(3) }
}
