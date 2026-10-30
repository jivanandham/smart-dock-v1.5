package com.example.wellness

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * Every gentle nudge the desk companion can give. [cue] tells the companion which little
 * scene to act out (it "does it first"), [action] is the main button label.
 */
enum class NudgeType(
    val title: String,
    val message: String,
    val action: String?,
    val cue: String?,
    val logType: String?,
    val emoji: String,
    val shortSummaryWord: String,
    val priority: Int,
    val defaultTtlMillis: Long
) {
    WATER(
        title = "Water break",
        message = "Your buddy is having a sip. Join in?",
        action = "Done",
        cue = "WATER",
        logType = "WATER",
        emoji = "💧",
        shortSummaryWord = "water",
        priority = 80,
        defaultTtlMillis = 15 * 60_000L
    ),
    STRETCH(
        title = "Stretch break",
        message = "You've been sitting a while. A one-minute stretch?",
        action = "Done",
        cue = "STRETCH",
        logType = "STRETCH",
        emoji = "🧘",
        shortSummaryWord = "stretch",
        priority = 75,
        defaultTtlMillis = 15 * 60_000L
    ),
    EYE_REST(
        title = "Rest your eyes",
        message = "Look at something far away for 20 seconds.",
        action = "Done",
        cue = "EYE_REST",
        logType = "EYE_REST",
        emoji = "👀",
        shortSummaryWord = "eye rest",
        priority = 78,
        defaultTtlMillis = 15 * 60_000L
    ),
    PRE_MEETING_BREATH(
        title = "Meeting soon",
        message = "A calm breath before your call?",
        action = "Breathe",
        cue = "BREATHE",
        logType = "BREATH",
        emoji = "📅",
        shortSummaryWord = "meeting prep",
        priority = 100,
        defaultTtlMillis = 10 * 60_000L
    ),
    BREATHE(
        title = "Breathing moment",
        message = "Lots of calls today. Take a minute to breathe.",
        action = "Breathe",
        cue = "BREATHE",
        logType = "BREATH",
        emoji = "🌬️",
        shortSummaryWord = "breathing",
        priority = 72,
        defaultTtlMillis = 15 * 60_000L
    ),
    WALK(
        title = "Nice weather outside",
        message = "Your calendar is free. A 10-minute walk?",
        action = "Done",
        cue = "WALK",
        logType = "WALK",
        emoji = "🌤️",
        shortSummaryWord = "walk",
        priority = 60,
        defaultTtlMillis = 30 * 60_000L
    ),
    LUNCH(
        title = "Lunch break",
        message = "No lunch on your calendar yet. Time to step away and eat?",
        action = "Done",
        cue = "LUNCH",
        logType = "LUNCH",
        emoji = "🍱",
        shortSummaryWord = "lunch",
        priority = 68,
        defaultTtlMillis = 30 * 60_000L
    ),
    MEETING_FATIGUE(
        title = "Meeting marathon",
        message = "Over three hours of calls today. Want to block a real break?",
        action = "Block 15m",
        cue = "FATIGUE",
        logType = null,
        emoji = "☕",
        shortSummaryWord = "break",
        priority = 65,
        defaultTtlMillis = 30 * 60_000L
    ),
    WIND_DOWN(
        title = "Time to wind down",
        message = "Bedtime in an hour. Maybe start winding down.",
        action = "Okay",
        cue = "WIND_DOWN",
        logType = null,
        emoji = "🌙",
        shortSummaryWord = "wind-down",
        priority = 48,
        defaultTtlMillis = 60 * 60_000L
    ),
    END_OF_DAY(
        title = "Time to log off",
        message = "Your workday is over. Great job today!",
        action = "Done",
        cue = "END_OF_DAY",
        logType = null,
        emoji = "✨",
        shortSummaryWord = "log off",
        priority = 50,
        defaultTtlMillis = 60 * 60_000L
    ),
    TOP3(
        title = "Your top 3 for today",
        message = "What are the three things that matter most today?",
        action = "Set top 3",
        cue = "TOP3",
        logType = null,
        emoji = "🎯",
        shortSummaryWord = "top 3",
        priority = 55,
        defaultTtlMillis = 60 * 60_000L
    ),
    MORNING_BRIEFING(
        title = "Morning briefing",
        message = "Good morning! Here is your day ahead.",
        action = "Got it",
        cue = null,
        logType = null,
        emoji = "☀️",
        shortSummaryWord = "morning briefing",
        priority = 95,
        defaultTtlMillis = 60 * 60_000L
    ),
    FRIDAY(
        title = "Happy Friday!",
        message = "The weekend is almost here.",
        action = null,
        cue = null,
        logType = null,
        emoji = "🎉",
        shortSummaryWord = "Friday",
        priority = 40,
        defaultTtlMillis = 60 * 60_000L
    ),
    BIRTHDAY(
        title = "Birthday today",
        message = "",
        action = null,
        cue = null,
        logType = null,
        emoji = "🎂",
        shortSummaryWord = "birthday",
        priority = 45,
        defaultTtlMillis = 60 * 60_000L
    );

    /** Low-key nudges that also post a system notification when the app isn't on screen. */
    val notifyWhenAway: Boolean
        get() = this == WATER || this == PRE_MEETING_BREATH || this == WIND_DOWN || this == END_OF_DAY

    /**
     * Friendly companion speech-bubble headline (e.g., "Mrrp? Water break 💧").
     */
    fun companionHeadline(useCat: Boolean = true): String {
        val prefix = if (useCat) {
            when (this) {
                WATER -> "Mrrp?"
                STRETCH -> "Purr~"
                EYE_REST -> "Prrt?"
                PRE_MEETING_BREATH, BREATHE -> "Purr..."
                WALK, LUNCH -> "Mew!"
                WIND_DOWN -> "Yawn~"
                END_OF_DAY, MORNING_BRIEFING, FRIDAY, BIRTHDAY -> "Meow!"
                else -> "Mrrp?"
            }
        } else {
            when (this) {
                WATER, EYE_REST -> "Beep?"
                STRETCH, WALK -> "Whirr~"
                PRE_MEETING_BREATH, BREATHE -> "Humm..."
                else -> "Chirp!"
            }
        }
        return "$prefix $title $emoji"
    }
}

data class Nudge(
    val type: NudgeType,
    val shownAtMillis: Long,
    val detail: String? = null,
    val expiresAtMillis: Long = shownAtMillis + type.defaultTtlMillis
) {
    val message: String get() = detail ?: type.message

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis

    fun shouldAutoShrink(nowMillis: Long, expandedSinceMillis: Long = shownAtMillis): Boolean =
        nowMillis - expandedSinceMillis >= AUTO_SHRINK_AFTER_MS

    companion object {
        /** First 20 seconds: full bubble with buttons. After that: shrinks to floating icon. */
        const val AUTO_SHRINK_AFTER_MS = 20_000L
    }
}

/**
 * A reminder that auto-expired while the user was away or didn't respond.
 */
data class MissedNudge(
    val type: NudgeType,
    val message: String,
    val shownAtMillis: Long,
    val expiredAtMillis: Long
)

/**
 * Formats the "while you were away" single-line summary (e.g.,
 * `"2 reminders while you were away: water, stretch"`).
 */
fun formatWhileAwaySummary(missed: List<MissedNudge>): String? {
    if (missed.isEmpty()) return null
    val count = missed.size
    val noun = if (count == 1) "reminder" else "reminders"
    val topics = missed
        .map { it.type.shortSummaryWord }
        .distinct()
        .take(4)
        .joinToString(", ")
    return "$count $noun while you were away: $topics"
}

/** All wellness preferences (stored as one JSON blob). */
data class WellnessSettings(
    val enabled: Boolean = true,
    val waterEnabled: Boolean = true,
    val waterIntervalMin: Int = 60,
    val waterGoal: Int = 8,
    val stretchEnabled: Boolean = true,
    val stretchIntervalMin: Int = 50,
    val eyeRestEnabled: Boolean = true,
    val eyeRestIntervalMin: Int = 20,
    val breathingEnabled: Boolean = true,
    val walkEnabled: Boolean = true,
    val lunchEnabled: Boolean = true,
    val fatigueEnabled: Boolean = true,
    val windDownEnabled: Boolean = true,
    val bedtimeHour: Int = 23,
    val bedtimeMinute: Int = 0,
    val endOfDayEnabled: Boolean = true,
    val workStartHour: Int = 9,
    val workEndHour: Int = 18,
    val weekdaysOnly: Boolean = true,
    val top3Enabled: Boolean = true,
    val celebrationsEnabled: Boolean = true
) {
    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("waterEnabled", waterEnabled); put("waterIntervalMin", waterIntervalMin)
        put("waterGoal", waterGoal); put("stretchEnabled", stretchEnabled); put("stretchIntervalMin", stretchIntervalMin)
        put("eyeRestEnabled", eyeRestEnabled); put("eyeRestIntervalMin", eyeRestIntervalMin)
        put("breathingEnabled", breathingEnabled); put("walkEnabled", walkEnabled); put("lunchEnabled", lunchEnabled)
        put("fatigueEnabled", fatigueEnabled); put("windDownEnabled", windDownEnabled)
        put("bedtimeHour", bedtimeHour); put("bedtimeMinute", bedtimeMinute); put("endOfDayEnabled", endOfDayEnabled)
        put("workStartHour", workStartHour); put("workEndHour", workEndHour); put("weekdaysOnly", weekdaysOnly)
        put("top3Enabled", top3Enabled); put("celebrationsEnabled", celebrationsEnabled)
    }.toString()

    companion object {
        fun fromJson(json: String?): WellnessSettings {
            if (json.isNullOrBlank()) return WellnessSettings()
            return try {
                val o = JSONObject(json)
                val d = WellnessSettings()
                WellnessSettings(
                    enabled = o.optBoolean("enabled", d.enabled),
                    waterEnabled = o.optBoolean("waterEnabled", d.waterEnabled),
                    waterIntervalMin = o.optInt("waterIntervalMin", d.waterIntervalMin).coerceIn(15, 240),
                    waterGoal = o.optInt("waterGoal", d.waterGoal).coerceIn(1, 20),
                    stretchEnabled = o.optBoolean("stretchEnabled", d.stretchEnabled),
                    stretchIntervalMin = o.optInt("stretchIntervalMin", d.stretchIntervalMin).coerceIn(15, 180),
                    eyeRestEnabled = o.optBoolean("eyeRestEnabled", d.eyeRestEnabled),
                    eyeRestIntervalMin = o.optInt("eyeRestIntervalMin", d.eyeRestIntervalMin).coerceIn(10, 120),
                    breathingEnabled = o.optBoolean("breathingEnabled", d.breathingEnabled),
                    walkEnabled = o.optBoolean("walkEnabled", d.walkEnabled),
                    lunchEnabled = o.optBoolean("lunchEnabled", d.lunchEnabled),
                    fatigueEnabled = o.optBoolean("fatigueEnabled", d.fatigueEnabled),
                    windDownEnabled = o.optBoolean("windDownEnabled", d.windDownEnabled),
                    bedtimeHour = o.optInt("bedtimeHour", d.bedtimeHour).coerceIn(0, 23),
                    bedtimeMinute = o.optInt("bedtimeMinute", d.bedtimeMinute).coerceIn(0, 59),
                    endOfDayEnabled = o.optBoolean("endOfDayEnabled", d.endOfDayEnabled),
                    workStartHour = o.optInt("workStartHour", d.workStartHour).coerceIn(0, 23),
                    workEndHour = o.optInt("workEndHour", d.workEndHour).coerceIn(1, 24),
                    weekdaysOnly = o.optBoolean("weekdaysOnly", d.weekdaysOnly),
                    top3Enabled = o.optBoolean("top3Enabled", d.top3Enabled),
                    celebrationsEnabled = o.optBoolean("celebrationsEnabled", d.celebrationsEnabled)
                )
            } catch (_: Exception) {
                WellnessSettings()
            }
        }
    }
}

/** Today + this week, for the chip, the companion's mood and the weekly recap. */
data class WellnessStats(
    val waterToday: Int = 0,
    val breaksToday: Int = 0,
    val waterGoal: Int = 8,
    val waterWeek: Int = 0,
    val breaksWeek: Int = 0,
    val waterStreakDays: Int = 0,
    val top3Done: Int = 0,
    val top3Total: Int = 0
) {
    /** 0..1. Self-care makes the companion livelier; it never becomes sad. */
    val moodScore: Float
        get() = (0.5f * (waterToday / waterGoal.coerceAtLeast(1).toFloat()).coerceAtMost(1f) +
            0.3f * (breaksToday / 4f).coerceAtMost(1f) +
            0.2f * (if (top3Total > 0) top3Done / top3Total.toFloat() else 0f)).coerceIn(0f, 1f)

    val moodLabel: String
        get() = when {
            moodScore >= 0.75f -> "full of energy"
            moodScore >= 0.4f -> "cheerful"
            else -> "relaxed"
        }
}

object WellnessDay {
    fun key(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
}
