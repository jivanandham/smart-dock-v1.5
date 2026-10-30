package com.example

import com.example.alarm.MeetingAlertScheduler
import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.UnifiedScheduleItem
import com.example.ui.util.WaveGestureDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyAndMeetingAlertsTest {

    @Test
    fun wave_farNearFar_isDetected() {
        val d = WaveGestureDetector()
        assertFalse(d.onReading(false, 0))      // baseline: far
        assertFalse(d.onReading(true, 1_000))   // hand over the sensor
        assertTrue(d.onReading(false, 1_300))   // hand gone after 300 ms -> wave
    }

    @Test
    fun wave_faceDownOrCovered_isIgnored() {
        val d = WaveGestureDetector()
        assertFalse(d.onReading(false, 0))
        assertFalse(d.onReading(true, 1_000))
        assertFalse(d.onReading(false, 4_000))  // covered for 3 s: not a wave
    }

    @Test
    fun wave_startingNear_needsAFarBaseline() {
        val d = WaveGestureDetector()
        assertFalse(d.onReading(true, 0))       // phone already lying face-down
        assertFalse(d.onReading(false, 300))    // lifted: no snooze
    }

    @Test
    fun wave_cooldownPreventsDoubleSnooze() {
        val d = WaveGestureDetector()
        d.onReading(false, 0); d.onReading(true, 100)
        assertTrue(d.onReading(false, 400))
        d.onReading(true, 600)
        assertFalse(d.onReading(false, 900))    // within 1.5 s cooldown
    }

    private fun ev(id: String, startInMin: Int, allDay: Boolean = false, done: Boolean = false, now: Long) =
        UnifiedScheduleItem(
            id = id, title = id, accountName = "me", calendarDisplayName = "c",
            providerBadge = AccountProviderBadge.GMAIL,
            startTimeMillis = now + startInMin * 60_000L, endTimeMillis = now + (startInMin + 30) * 60_000L,
            location = "", isAllDay = allDay, isCompleted = done
        )

    @Test
    fun meetingAlerts_planOnlyFutureTimedEvents() {
        val now = 1_000_000_000L
        val events = listOf(
            ev("soon", 3, now = now),          // alert time (start - 5) already passed
            ev("later", 30, now = now),        // alert at +25 min
            ev("allday", 60, allDay = true, now = now),
            ev("done", 90, done = true, now = now),
            ev("far", 60 * 48, now = now),     // beyond the 36 h look-ahead
            ev("later", 30, now = now)         // duplicate id
        )
        val plan = MeetingAlertScheduler.planAlerts(events, leadMinutes = 5, now = now)
        assertEquals(listOf("later"), plan.map { it.first.id })
        assertEquals(now + 25 * 60_000L, plan.first().second)
    }
}
