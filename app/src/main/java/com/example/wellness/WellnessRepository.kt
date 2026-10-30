package com.example.wellness

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.DailyPriorityEntity
import com.example.data.local.DockDao
import com.example.data.local.WellnessLogEntity
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wellnessStore by preferencesDataStore(name = "wellness_prefs")

/** Wellness settings (DataStore) and logs/priorities (Room). Everything stays on the device. */
class WellnessRepository(private val context: Context, private val dao: DockDao) {

    private val settingsKey = stringPreferencesKey("wellness_settings_json")

    val settingsFlow: Flow<WellnessSettings> =
        context.wellnessStore.data.map { WellnessSettings.fromJson(it[settingsKey]) }

    suspend fun saveSettings(settings: WellnessSettings) {
        context.wellnessStore.edit { it[settingsKey] = settings.toJson() }
    }

    suspend fun log(type: String, now: Long = System.currentTimeMillis()) {
        dao.insertWellnessLog(WellnessLogEntity(type = type, atMillis = now, dayKey = WellnessDay.key(now)))
    }

    suspend fun removeLatestLog(types: Collection<String>, now: Long = System.currentTimeMillis()): Int {
        if (types.isEmpty()) return 0
        return dao.deleteLatestWellnessLogForTypes(
            dayKey = WellnessDay.key(now),
            types = types.toList()
        )
    }

    /** Logs from the last 60 days (enough for streaks and the weekly recap). */
    fun recentLogs(now: Long = System.currentTimeMillis()): Flow<List<WellnessLogEntity>> =
        dao.observeWellnessLogsSince(WellnessEngine.startOfDay(now) - 60L * 24 * 3_600_000L)

    fun prioritiesToday(now: Long = System.currentTimeMillis()): Flow<List<DailyPriorityEntity>> =
        dao.observePriorities(WellnessDay.key(now))

    suspend fun savePriorities(texts: List<String>, now: Long = System.currentTimeMillis()) {
        val day = WellnessDay.key(now)
        dao.clearPriorities(day)
        texts.map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEachIndexed { i, text ->
            dao.upsertPriority(DailyPriorityEntity(dayKey = day, position = i, text = text))
        }
    }

    suspend fun setPriorityDone(id: Long, done: Boolean) = dao.setPriorityDone(id, done)

    companion object {
        val BREAK_TYPES = setOf("STRETCH", "EYE_REST", "WALK", "BREATH", "LUNCH")

        fun computeStats(
            logs: List<WellnessLogEntity>,
            priorities: List<DailyPriorityEntity>,
            waterGoal: Int,
            now: Long = System.currentTimeMillis()
        ): WellnessStats {
            val today = WellnessDay.key(now)
            val weekStart = WellnessEngine.startOfDay(now) - 6L * 24 * 3_600_000L
            val todayLogs = logs.filter { it.dayKey == today }
            val weekLogs = logs.filter { it.atMillis >= weekStart }

            // Streak: consecutive days (ending today or yesterday) where the water goal was met.
            val waterByDay = logs.filter { it.type == "WATER" }.groupingBy { it.dayKey }.eachCount()
            var streak = 0
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            if ((waterByDay[today] ?: 0) < waterGoal) cal.add(Calendar.DAY_OF_YEAR, -1)
            while ((waterByDay[WellnessDay.key(cal.timeInMillis)] ?: 0) >= waterGoal) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            return WellnessStats(
                waterToday = todayLogs.count { it.type == "WATER" },
                breaksToday = todayLogs.count { it.type in BREAK_TYPES },
                waterGoal = waterGoal,
                waterWeek = weekLogs.count { it.type == "WATER" },
                breaksWeek = weekLogs.count { it.type in BREAK_TYPES },
                waterStreakDays = streak,
                top3Done = priorities.count { it.done },
                top3Total = priorities.size
            )
        }
    }
}
