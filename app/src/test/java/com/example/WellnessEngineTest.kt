package com.example

import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.local.DailyPriorityEntity
import com.example.data.local.WellnessLogEntity
import com.example.wellness.NudgeType
import com.example.wellness.WellnessContext
import com.example.wellness.WellnessDay
import com.example.wellness.WellnessEngine
import com.example.wellness.WellnessRepository
import com.example.wellness.WellnessSettings
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WellnessEngineTest {

    /** Tuesday 3 November 2026 at the given time. */
    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 3, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun event(title: String, start: Long, minutes: Int) = UnifiedScheduleItem(
        id = title + start, title = title, accountName = "me", calendarDisplayName = "c",
        providerBadge = AccountProviderBadge.GMAIL, startTimeMillis = start,
        endTimeMillis = start + minutes * 60_000L, location = ""
    )

    private fun ctx(
        now: Long,
        schedule: List<UnifiedScheduleItem> = emptyList(),
        deskStart: Long = now - 120 * 60_000L,
        lastWater: Long = now,
        lastStretch: Long = now,
        lastEye: Long = now,
        focus: Boolean = false,
        night: Boolean = false,
        settings: WellnessSettings = WellnessSettings(),
        priorities: Boolean = true,
        lunchLogged: Boolean = true
    ) = WellnessContext(
        now = now, settings = settings, schedule = schedule, focusRunning = focus, alarmActive = false,
        nightActive = night, dndActive = false, weatherTempC = 15.0, weatherCode = 3, isDaytime = true,
        deskSessionStart = deskStart, lastWater = lastWater, lastStretch = lastStretch, lastEyeRest = lastEye,
        lunchLoggedToday = lunchLogged, prioritiesSetToday = priorities
    )

    @Test
    fun stretch_afterLongSitting() {
        val now = at(11)
        val nudge = WellnessEngine().evaluate(ctx(now, lastStretch = now - 60 * 60_000L))
        assertEquals(NudgeType.STRETCH, nudge?.first?.type)
    }

    @Test
    fun water_whenIntervalPassed() {
        val now = at(11)
        val nudge = WellnessEngine().evaluate(ctx(now, lastWater = now - 70 * 60_000L))
        assertEquals(NudgeType.WATER, nudge?.first?.type)
    }

    @Test
    fun quietDuringMeetingsFocusAndNight() {
        val now = at(11)
        val meeting = event("Standup", now - 5 * 60_000L, 30)
        val due = ctx(now, lastWater = 0L, lastStretch = 0L)
        assertNull(WellnessEngine().evaluate(due.copy(schedule = listOf(meeting))))
        assertNull(WellnessEngine().evaluate(due.copy(focusRunning = true)))
        assertNull(WellnessEngine().evaluate(due.copy(nightActive = true)))
    }

    @Test
    fun breathBeforeMeeting_oncePerMeeting() {
        val now = at(10, 55)
        val engine = WellnessEngine()
        val meeting = event("Design review", at(11), 30)
        val first = engine.evaluate(ctx(now, schedule = listOf(meeting)))
        assertEquals(NudgeType.PRE_MEETING_BREATH, first?.first?.type)
        engine.markShown(first!!.first.type, now, first.second)
        assertNull(engine.evaluate(ctx(now + 60_000L, schedule = listOf(meeting))))
    }

    @Test
    fun noDeskRemindersOutsideActiveHours() {
        val now = at(7)
        assertNull(WellnessEngine().evaluate(ctx(now, lastWater = 0L, lastStretch = 0L)))
    }

    @Test
    fun top3_inTheMorningWhenNotSet() {
        val now = at(9, 15)
        assertEquals(NudgeType.TOP3, WellnessEngine().evaluate(ctx(now, priorities = false))?.first?.type)
    }

    @Test
    fun lunch_onlyWithoutMealInCalendar() {
        val now = at(13, 45)
        assertEquals(NudgeType.LUNCH, WellnessEngine().evaluate(ctx(now, lunchLogged = false))?.first?.type)
        val withLunch = listOf(event("Lunch with Sam", at(12, 30), 60))
        assertTrue(WellnessEngine().evaluate(ctx(now, schedule = withLunch, lunchLogged = false))?.first?.type != NudgeType.LUNCH)
    }

    @Test
    fun meetingMinutesAndFreeSlot() {
        val now = at(15)
        val events = listOf(event("A", at(9), 90), event("B", at(11), 60), event("C", at(13), 60))
        assertEquals(210L, WellnessEngine.meetingMinutesToday(events, now))
        val upcoming = listOf(event("D", now + 5 * 60_000L, 30))
        assertEquals(35, WellnessEngine.nextFreeSlotOffsetMinutes(upcoming, now))
    }

    @Test
    fun settings_roundTripJson() {
        val s = WellnessSettings(waterGoal = 10, stretchIntervalMin = 40, weekdaysOnly = false)
        assertEquals(s, WellnessSettings.fromJson(s.toJson()))
    }

    @Test
    fun stats_streakAndMood() {
        val now = at(16)
        val logs = mutableListOf<WellnessLogEntity>()
        for (dayBack in 0..2) {
            val t = now - dayBack * 24 * 3_600_000L
            repeat(8) { logs += WellnessLogEntity(type = "WATER", atMillis = t - it * 60_000L, dayKey = WellnessDay.key(t)) }
        }
        logs += WellnessLogEntity(type = "STRETCH", atMillis = now, dayKey = WellnessDay.key(now))
        val prios = listOf(DailyPriorityEntity(dayKey = WellnessDay.key(now), position = 0, text = "x", done = true))
        val stats = WellnessRepository.computeStats(logs, prios, waterGoal = 8, now = now)
        assertEquals(8, stats.waterToday)
        assertEquals(3, stats.waterStreakDays)
        assertEquals(1, stats.breaksToday)
        assertTrue(stats.moodScore > 0.5f)
    }

    @Test
    fun waterReminder_fadesAfter15Minutes_andEndOfDayAfter1Hour() {
        val now = at(11)
        val waterNudge = com.example.wellness.Nudge(NudgeType.WATER, now)
        assertEquals(now + 15 * 60_000L, waterNudge.expiresAtMillis)
        assertTrue(!waterNudge.isExpired(now + 14 * 60_000L))
        assertTrue(waterNudge.isExpired(now + 15 * 60_000L))

        val eodNudge = com.example.wellness.Nudge(NudgeType.END_OF_DAY, now)
        assertEquals(now + 60 * 60_000L, eodNudge.expiresAtMillis)
        assertTrue(eodNudge.isExpired(now + 60 * 60_000L))
    }

    @Test
    fun preMeetingReminder_expiresAtExactMeetingStart() {
        val now = at(10, 55)
        val meetingStart = at(11, 0)
        val meeting = event("Architecture Sync", meetingStart, 30)
        val pair = WellnessEngine().evaluate(ctx(now, schedule = listOf(meeting)))
        val nudge = pair!!.first
        assertEquals(NudgeType.PRE_MEETING_BREATH, nudge.type)
        assertEquals(meetingStart, nudge.expiresAtMillis)
        assertTrue(!nudge.isExpired(meetingStart - 1_000L))
        assertTrue(nudge.isExpired(meetingStart))
    }

    @Test
    fun bubbleShrinksAfter20Seconds_andHasCompanionHeadline() {
        val now = at(14)
        val waterNudge = com.example.wellness.Nudge(NudgeType.WATER, now)
        assertEquals("Mrrp? Water break 💧", waterNudge.type.companionHeadline(useCat = true))
        assertTrue(!waterNudge.shouldAutoShrink(now + 19_999L))
        assertTrue(waterNudge.shouldAutoShrink(now + 20_000L))
    }

    @Test
    fun priorityQueue_ordersMostImportantFirst() {
        val now = at(11)
        val low = com.example.wellness.Nudge(NudgeType.END_OF_DAY, now)
        val medium = com.example.wellness.Nudge(NudgeType.STRETCH, now)
        val high = com.example.wellness.Nudge(NudgeType.WATER, now)
        val urgent = com.example.wellness.Nudge(NudgeType.PRE_MEETING_BREATH, now)

        val sorted = WellnessEngine.sortByPriority(listOf(low, medium, high, urgent))
        assertEquals(
            listOf(
                NudgeType.PRE_MEETING_BREATH,
                NudgeType.WATER,
                NudgeType.STRETCH,
                NudgeType.END_OF_DAY
            ),
            sorted.map { it.type }
        )
    }

    @Test
    fun whileYouWereAwaySummary_formatsMissedNudges() {
        val now = at(15)
        val missed = listOf(
            com.example.wellness.MissedNudge(NudgeType.WATER, "Drink water", now - 20 * 60_000L, now - 5 * 60_000L),
            com.example.wellness.MissedNudge(NudgeType.STRETCH, "Stretch", now - 18 * 60_000L, now - 3 * 60_000L)
        )
        assertEquals(
            "2 reminders while you were away: water, stretch",
            com.example.wellness.formatWhileAwaySummary(missed)
        )
    }

    @Test
    fun clockSafeStagePlacement_protectsClockDigitsAndShrinksOnTightScreens() {
        // Normal landscape dock: clock starts at y = 520px out of 1080px height
        val normalClockRect = androidx.compose.ui.geometry.Rect(100f, 520f, 1800f, 1020f)
        val normalPlacement = com.example.ui.computeClockSafeStagePlacement(
            windowHeightPx = 1080f,
            clockBounds = normalClockRect,
            minFullBubbleHeightPx = 160f
        )
        assertEquals(520f, normalPlacement.safeStageHeightPx, 0.01f)
        assertTrue(normalPlacement.canFitFullBubble)

        // Small/split screen where clock starts at y = 110px (< 160px needed for full bubble)
        val tightClockRect = androidx.compose.ui.geometry.Rect(20f, 110f, 600f, 400f)
        val tightPlacement = com.example.ui.computeClockSafeStagePlacement(
            windowHeightPx = 420f,
            clockBounds = tightClockRect,
            minFullBubbleHeightPx = 160f
        )
        assertEquals(110f, tightPlacement.safeStageHeightPx, 0.01f)
        assertTrue(!tightPlacement.canFitFullBubble)
    }

    @Test
    fun cameraRing_slowFadeOutDurationConfigured() {
        assertTrue(com.example.ui.components.CAMERA_RING_SLOW_FADEOUT_MS >= 2000)
        assertTrue(
            com.example.ui.components.CAMERA_RING_SLOW_FADEOUT_MS >
                com.example.ui.components.CAMERA_RING_FADE_IN_MS * 5
        )
    }
}

