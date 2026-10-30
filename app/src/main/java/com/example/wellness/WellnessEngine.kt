package com.example.wellness

import com.example.data.calendar.UnifiedScheduleItem
import java.util.Calendar

/** Snapshot of everything the engine needs to decide on a nudge. */
data class WellnessContext(
    val now: Long,
    val settings: WellnessSettings,
    val schedule: List<UnifiedScheduleItem>,
    val focusRunning: Boolean,
    val alarmActive: Boolean,
    val nightActive: Boolean,
    val dndActive: Boolean,
    val weatherTempC: Double?,
    val weatherCode: Int?,
    val isDaytime: Boolean,
    /** When the current uninterrupted desk session started (app on screen). */
    val deskSessionStart: Long,
    val lastWater: Long,
    val lastStretch: Long,
    val lastEyeRest: Long,
    val lunchLoggedToday: Boolean,
    val prioritiesSetToday: Boolean
)

/**
 * Decides which (if any) gentle nudge to show. Pure logic, no Android calls, so it's unit-testable.
 *
 * Rules:
 *  - One nudge at a time, at least [MIN_GAP_MS] apart (except the pre-meeting breath).
 *  - Quiet during meetings, Focus sessions, ringing alarms, Do Not Disturb, and at night
 *    (only the wind-down reminder may appear in the evening).
 *  - Desk reminders (water, stretch, eyes, walk, lunch, top 3) only in active hours
 *    (and on weekdays when "weekdays only" is on).
 *  - Snoozed types wait; once-a-day nudges fire at most once per day.
 */
class WellnessEngine {
    private val lastShown = mutableMapOf<NudgeType, Long>()
    private val snoozedUntil = mutableMapOf<NudgeType, Long>()
    private val onceKeys = mutableSetOf<String>()
    private var lastAnyShown = 0L

    fun snooze(type: NudgeType, now: Long, minutes: Int = 10) {
        snoozedUntil[type] = now + minutes * 60_000L
    }

    fun markShown(type: NudgeType, now: Long, onceKey: String? = null) {
        lastShown[type] = now
        lastAnyShown = now
        if (onceKey != null) onceKeys += onceKey
    }

    fun evaluate(ctx: WellnessContext): Pair<Nudge, String?>? {
        val s = ctx.settings
        if (!s.enabled || ctx.alarmActive || ctx.dndActive || ctx.focusRunning) return null

        val cal = Calendar.getInstance().apply { timeInMillis = ctx.now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minuteOfDay = hour * 60 + cal.get(Calendar.MINUTE)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekday = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
        val day = WellnessDay.key(ctx.now)

        val timed = ctx.schedule.filter { !it.isAllDay && !it.isCompleted }
        val inMeeting = timed.any { it.startTimeMillis <= ctx.now && it.endTimeMillis > ctx.now }
        if (inMeeting) return null

        fun available(type: NudgeType) = (snoozedUntil[type] ?: 0L) <= ctx.now
        fun once(type: NudgeType, extra: String = "") = "${type.name}|$day|$extra"
        fun notDoneToday(type: NudgeType, extra: String = "") = once(type, extra) !in onceKeys
        val gapOk = ctx.now - lastAnyShown >= MIN_GAP_MS

        // 1. Calm breath 3–6 minutes before a meeting (once per meeting).
        if (s.breathingEnabled && !ctx.nightActive && available(NudgeType.PRE_MEETING_BREATH)) {
            val soon = timed.firstOrNull { it.startTimeMillis - ctx.now in (3 * 60_000L)..(6 * 60_000L) }
            if (soon != null && notDoneToday(NudgeType.PRE_MEETING_BREATH, soon.id)) {
                return Nudge(
                    type = NudgeType.PRE_MEETING_BREATH,
                    shownAtMillis = ctx.now,
                    detail = "\"${soon.title}\" starts in a few minutes. A calm breath first?",
                    expiresAtMillis = soon.startTimeMillis.coerceAtLeast(ctx.now + 30_000L)
                ) to once(NudgeType.PRE_MEETING_BREATH, soon.id)
            }
        }
        if (!gapOk) return null

        // 2. Wind-down, the only evening nudge allowed during night mode.
        if (s.windDownEnabled && available(NudgeType.WIND_DOWN) && notDoneToday(NudgeType.WIND_DOWN)) {
            val bed = s.bedtimeHour * 60 + s.bedtimeMinute
            val start = (bed - 60 + 1440) % 1440
            val delta = (minuteOfDay - start + 1440) % 1440
            if (delta in 0..10) return Nudge(NudgeType.WIND_DOWN, ctx.now) to once(NudgeType.WIND_DOWN)
        }
        if (ctx.nightActive) return null

        // 3. Meeting fatigue: over 3 hours of calls today.
        if (s.fatigueEnabled && available(NudgeType.MEETING_FATIGUE) && notDoneToday(NudgeType.MEETING_FATIGUE)) {
            if (meetingMinutesToday(timed, ctx.now) >= 180) {
                return Nudge(NudgeType.MEETING_FATIGUE, ctx.now) to once(NudgeType.MEETING_FATIGUE)
            }
        }

        // 4. End of workday (weekdays), within 30 minutes after the end hour.
        if (s.endOfDayEnabled && weekday && available(NudgeType.END_OF_DAY) && notDoneToday(NudgeType.END_OF_DAY)) {
            val end = s.workEndHour * 60
            if (minuteOfDay in end until end + 30) return Nudge(NudgeType.END_OF_DAY, ctx.now) to once(NudgeType.END_OF_DAY)
        }

        // 5. Friday celebration.
        if (s.celebrationsEnabled && dow == Calendar.FRIDAY && hour >= 17 && notDoneToday(NudgeType.FRIDAY)) {
            return Nudge(NudgeType.FRIDAY, ctx.now) to once(NudgeType.FRIDAY)
        }

        // 6. Birthdays in the calendar today.
        if (s.celebrationsEnabled && notDoneToday(NudgeType.BIRTHDAY)) {
            val bday = birthdayToday(ctx.schedule, ctx.now)
            if (bday != null) return Nudge(NudgeType.BIRTHDAY, ctx.now, "🎉 $bday") to once(NudgeType.BIRTHDAY)
        }

        // Desk reminders below only in active hours.
        val activeHours = hour >= s.workStartHour && hour < s.workEndHour && (!s.weekdaysOnly || weekday)
        if (!activeHours) return null

        // 7. Top 3 for today, once in the morning.
        if (s.top3Enabled && !ctx.prioritiesSetToday && available(NudgeType.TOP3) && notDoneToday(NudgeType.TOP3)) {
            return Nudge(NudgeType.TOP3, ctx.now) to once(NudgeType.TOP3)
        }

        // 8. Lunch (13:30–15:00) if there's no lunch on the calendar and none logged.
        if (s.lunchEnabled && !ctx.lunchLoggedToday && minuteOfDay in (13 * 60 + 30) until 15 * 60 &&
            available(NudgeType.LUNCH) && notDoneToday(NudgeType.LUNCH) && !hasMealEventToday(ctx.schedule, ctx.now)
        ) {
            return Nudge(NudgeType.LUNCH, ctx.now) to once(NudgeType.LUNCH)
        }

        val deskMinutes = (ctx.now - ctx.deskSessionStart) / 60_000L
        val freeFor20 = timed.none { it.startTimeMillis in ctx.now..(ctx.now + 20 * 60_000L) }

        // 9. A walk when it's nice out and the calendar is free (once a day).
        if (s.walkEnabled && ctx.isDaytime && (ctx.weatherCode ?: 99) <= 1 &&
            (ctx.weatherTempC ?: 0.0) in 8.0..30.0 && hour in 10..16 && freeFor20 && deskMinutes >= 90 &&
            available(NudgeType.WALK) && notDoneToday(NudgeType.WALK)
        ) {
            return Nudge(NudgeType.WALK, ctx.now) to once(NudgeType.WALK)
        }

        // 10. Stretch after a long sitting session.
        val sinceStretch = (ctx.now - maxOf(ctx.lastStretch, ctx.deskSessionStart)) / 60_000L
        if (s.stretchEnabled && sinceStretch >= s.stretchIntervalMin && available(NudgeType.STRETCH)) {
            return Nudge(NudgeType.STRETCH, ctx.now) to null
        }

        // 11. Water, more often on hot days.
        val heat = (ctx.weatherTempC ?: 20.0) >= 27.0
        val waterInterval = if (heat) (s.waterIntervalMin * 0.75).toInt() else s.waterIntervalMin
        val sinceWater = (ctx.now - maxOf(ctx.lastWater, ctx.deskSessionStart - 30 * 60_000L)) / 60_000L
        if (s.waterEnabled && sinceWater >= waterInterval && available(NudgeType.WATER)) {
            val detail = if (heat) "It's hot today. Your buddy is having a sip, join in?" else null
            return Nudge(NudgeType.WATER, ctx.now, detail) to null
        }

        // 12. Eye rest (20-20-20).
        val sinceEyes = (ctx.now - maxOf(ctx.lastEyeRest, ctx.deskSessionStart, lastShown[NudgeType.EYE_REST] ?: 0L)) / 60_000L
        if (s.eyeRestEnabled && sinceEyes >= s.eyeRestIntervalMin && available(NudgeType.EYE_REST)) {
            return Nudge(NudgeType.EYE_REST, ctx.now) to null
        }
        return null
    }

    companion object {
        const val MIN_GAP_MS = 10 * 60_000L
        private val MEAL_WORDS = Regex("""\b(lunch|déjeuner|dejeuner|brunch|meal|repas)\b""", RegexOption.IGNORE_CASE)
        private val BIRTHDAY_WORDS = Regex("""\b(birthday|bday|anniversaire)\b""", RegexOption.IGNORE_CASE)

        fun sortByPriority(nudges: List<Nudge>): List<Nudge> =
            nudges.sortedWith(
                compareByDescending<Nudge> { it.type.priority }
                    .thenBy { it.shownAtMillis }
            )

        fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /** Minutes of meetings already attended today (overlap of events with today, up to now). */
        fun meetingMinutesToday(timed: List<UnifiedScheduleItem>, now: Long): Long {
            val dayStart = startOfDay(now)
            return timed.sumOf { ev ->
                val start = maxOf(ev.startTimeMillis, dayStart)
                val end = minOf(ev.endTimeMillis, now)
                if (end > start) (end - start) / 60_000L else 0L
            }
        }

        fun hasMealEventToday(schedule: List<UnifiedScheduleItem>, now: Long): Boolean {
            val dayStart = startOfDay(now)
            val dayEnd = dayStart + 24 * 3_600_000L
            return schedule.any { it.startTimeMillis in dayStart until dayEnd && MEAL_WORDS.containsMatchIn(it.title) }
        }

        fun birthdayToday(schedule: List<UnifiedScheduleItem>, now: Long): String? {
            val dayStart = startOfDay(now)
            val dayEnd = dayStart + 24 * 3_600_000L
            return schedule.firstOrNull {
                it.startTimeMillis < dayEnd && it.endTimeMillis > dayStart && BIRTHDAY_WORDS.containsMatchIn(it.title)
            }?.title
        }

        /** Start offset (minutes from now) of the first free 15-minute slot within the next 4 hours. */
        fun nextFreeSlotOffsetMinutes(schedule: List<UnifiedScheduleItem>, now: Long, lengthMin: Int = 15): Int {
            val timed = schedule.filter { !it.isAllDay && !it.isCompleted && it.endTimeMillis > now }
                .sortedBy { it.startTimeMillis }
            var t = now
            val len = lengthMin * 60_000L
            for (ev in timed) {
                if (ev.startTimeMillis >= t + len) break
                if (ev.endTimeMillis > t) t = ev.endTimeMillis
            }
            return ((t - now) / 60_000L).toInt().coerceIn(0, 240)
        }

        /** Seasonal / birthday hat for the companion: "PARTY", "SANTA", "WITCH" or null. */
        fun companionHat(schedule: List<UnifiedScheduleItem>, now: Long, enabled: Boolean): String? {
            if (!enabled) return null
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val month = cal.get(Calendar.MONTH)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            return when {
                birthdayToday(schedule, now) != null -> "PARTY"
                month == Calendar.DECEMBER && dayOfMonth in 20..26 -> "SANTA"
                month == Calendar.OCTOBER && dayOfMonth == 31 -> "WITCH"
                month == Calendar.JANUARY && dayOfMonth == 1 -> "PARTY"
                else -> null
            }
        }
    }
}
