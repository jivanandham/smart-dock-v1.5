package com.example

import com.example.alarm.AlarmScheduler
import com.example.data.local.DockAlarmEntity
import com.example.ui.components.repeatSummary
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRepeatTest {

    // Thursday 29 October 2026, 07:30
    private val thursdayMorning = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 29, 7, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayOfWeek(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)

    @Test
    fun weekdaysAlarm_onFridayEvening_ringsMonday() {
        val fridayEvening = thursdayMorning + 36 * 3_600_000L // Friday 19:30
        val next = AlarmScheduler.nextTriggerMillis(7, 0, DockAlarmEntity.WEEKDAYS_MASK, null, fridayEvening)
        assertNotNull(next)
        assertEquals(Calendar.MONDAY, dayOfWeek(next!!))
    }

    @Test
    fun weekendAlarm_ringsSaturday() {
        val next = AlarmScheduler.nextTriggerMillis(9, 0, DockAlarmEntity.WEEKEND_MASK, null, thursdayMorning)
        assertEquals(Calendar.SATURDAY, dayOfWeek(next!!))
    }

    @Test
    fun skipNext_movesToFollowingOccurrence() {
        val first = AlarmScheduler.nextTriggerMillis(8, 0, DockAlarmEntity.ALL_DAYS_MASK, null, thursdayMorning)!!
        val skipped = AlarmScheduler.nextTriggerMillis(8, 0, DockAlarmEntity.ALL_DAYS_MASK, first, thursdayMorning)!!
        assertEquals(24 * 3_600_000L, skipped - first)
    }

    @Test
    fun oneTimeAlarm_ringsNextMatchingTime() {
        val next = AlarmScheduler.nextTriggerMillis(6, 0, 0, null, thursdayMorning)!!
        assertTrue(next > thursdayMorning)
        assertEquals(Calendar.FRIDAY, dayOfWeek(next))
    }

    @Test
    fun dayBit_mapsMondayToZeroAndSundayToSix() {
        assertEquals(0, AlarmScheduler.dayBit(Calendar.MONDAY))
        assertEquals(6, AlarmScheduler.dayBit(Calendar.SUNDAY))
    }

    @Test
    fun repeatSummary_readsNaturally() {
        assertEquals("Every day", repeatSummary(DockAlarmEntity.ALL_DAYS_MASK))
        assertEquals("Weekdays", repeatSummary(DockAlarmEntity.WEEKDAYS_MASK))
        assertEquals("Once", repeatSummary(0))
        assertEquals("Mon Wed", repeatSummary(0b101))
    }

    @Test
    fun countdown_formatsHoursAndMinutes() {
        assertEquals("7 h 23 min", AlarmScheduler.formatCountdown((7 * 60 + 23) * 60_000L))
        assertEquals("5 min", AlarmScheduler.formatCountdown(5 * 60_000L + 30_000L))
    }
}
