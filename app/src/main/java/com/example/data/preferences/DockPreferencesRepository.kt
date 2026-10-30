package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.weather.WeatherRepository
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dockDataStore by preferencesDataStore(name = "smart_dock_prefs")

data class DockSettings(
    val clockStyle: String = "NANO", // "NANO", "FLIP", "MATRIX", "CHRONO"
    val agentTheme: String = "VALORANT", // "VALORANT", "JETT", "KILLJOY", "OMEN"
    val forceLandscapeMode: Boolean = false,
    val use24HourFormat: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val nightRedMode: Boolean = false,
    val nightTimeOnlyEnabled: Boolean = false,
    val nightStartHour: Int = 22,
    val nightStartMinute: Int = 0,
    val nightEndHour: Int = 7,
    val nightEndMinute: Int = 0,
    val showPortHubTelemetry: Boolean = false,
    val showAgendaTicker: Boolean = true,
    val brightnessDimmer: Float = 1.0f, // 0.25f to 1.0f
    val localLlmEndpoint: String = "http://127.0.0.1:8080/v1/chat/completions",
    val localLlmModelName: String = "gemma-2-2b-it",
    val cityName: String = "Poitiers",
    val cityLat: Double = 46.5802,
    val cityLon: Double = 0.3404,
    val secondaryCityName: String = "Pittsburgh",
    val secondaryCityLat: Double = 40.4406,
    val secondaryCityLon: Double = -79.9959,
    val sunriseSimulationEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    // --- Production additions ---
    val onboardingDone: Boolean = false,
    val useCatCompanion: Boolean = true,
    val catCoat: String = "ORANGE_TABBY", // ORANGE_TABBY, GREY_TABBY, BLACK, CREAM
    val catName: String = "Mochi",
    val autoDimInDark: Boolean = true,
    val burnInProtection: Boolean = true,
    val speakMorningBriefing: Boolean = false,
    /** Which city's weather drives the animated sky: "PRIMARY" or "SECONDARY". */
    val weatherAnimationCity: String = "PRIMARY",
    // Kids OK-to-wake mode
    val kidsModeEnabled: Boolean = false,
    val kidsBedHour: Int = 19,
    val kidsBedMinute: Int = 30,
    val kidsWakeHour: Int = 7,
    val kidsWakeMinute: Int = 0,
    // Companion sounds: "ALL", "REMINDERS", or "OFF"
    val companionSoundsMode: String = "ALL",
    val companionSoundsVolume: Float = 0.35f,
    // Screensaver, 15s auto-return & smooth adaptive brightness
    val adaptiveBrightnessEnabled: Boolean = true,
    val screensaverOnScreenOff: Boolean = false,
    /** Reliable system reminders before meetings, with a Join button. */
    val meetingAlertsEnabled: Boolean = true,
    /** Minutes before the start (1, 5, 10 or 15). */
    val meetingAlertLeadMinutes: Int = 5,
    /** Hide meeting titles on the screensaver, the locked view and lock-screen notifications. */
    val hideMeetingTitles: Boolean = false,
    val autoReturnToCompanionEnabled: Boolean = true
) {
    /** "SLEEP" between bedtime and wake time, "WAKE" for 30 minutes after wake time, otherwise null. */
    fun kidsPhase(now: Long): String? {
        if (!kidsModeEnabled) return null
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val bed = kidsBedHour * 60 + kidsBedMinute
        val wake = kidsWakeHour * 60 + kidsWakeMinute
        val sleeping = if (bed > wake) m >= bed || m < wake else m in bed until wake
        if (sleeping) return "SLEEP"
        val sinceWake = (m - wake + 1440) % 1440
        return if (sinceWake < 30) "WAKE" else null
    }

    fun isNightTimeOnlyActive(currentTimeMillis: Long): Boolean {
        if (!nightTimeOnlyEnabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = (nightStartHour.coerceIn(0, 23) * 60) + nightStartMinute.coerceIn(0, 59)
        val endMins = (nightEndHour.coerceIn(0, 23) * 60) + nightEndMinute.coerceIn(0, 59)

        return when {
            startMins == endMins -> true
            startMins < endMins -> nowMins in startMins until endMins
            else -> nowMins >= startMins || nowMins < endMins
        }
    }
}

class DockPreferencesRepository(private val context: Context) {

    private object Keys {
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val AGENT_THEME = stringPreferencesKey("agent_theme")
        val FORCE_LANDSCAPE = booleanPreferencesKey("force_landscape_mode")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val KEEP_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val NIGHT_RED_MODE = booleanPreferencesKey("night_red_mode")
        val NIGHT_TIME_ONLY_ENABLED = booleanPreferencesKey("night_time_only_enabled")
        val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        val NIGHT_START_MINUTE = intPreferencesKey("night_start_minute")
        val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
        val NIGHT_END_MINUTE = intPreferencesKey("night_end_minute")
        val SHOW_PORT_TELEMETRY = booleanPreferencesKey("show_port_telemetry_v2")
        val SHOW_AGENDA_TICKER = booleanPreferencesKey("show_agenda_ticker")
        val BRIGHTNESS_DIMMER = floatPreferencesKey("brightness_dimmer")
        val LLM_ENDPOINT = stringPreferencesKey("local_llm_endpoint")
        val LLM_MODEL = stringPreferencesKey("local_llm_model")
        val CITY_NAME = stringPreferencesKey("city_name")
        val CITY_LAT = doublePreferencesKey("city_lat")
        val CITY_LON = doublePreferencesKey("city_lon")
        val SECONDARY_CITY_NAME = stringPreferencesKey("secondary_city_name")
        val SECONDARY_CITY_LAT = doublePreferencesKey("secondary_city_lat")
        val SECONDARY_CITY_LON = doublePreferencesKey("secondary_city_lon")
        val SUNRISE_SIMULATION_ENABLED = booleanPreferencesKey("sunrise_simulation_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val USE_CAT = booleanPreferencesKey("use_cat_companion")
        val CAT_COAT = stringPreferencesKey("cat_coat")
        val CAT_NAME = stringPreferencesKey("cat_name")
        val AUTO_DIM = booleanPreferencesKey("auto_dim_in_dark")
        val BURN_IN = booleanPreferencesKey("burn_in_protection")
        val SPEAK_BRIEFING = booleanPreferencesKey("speak_morning_briefing")
        val WEATHER_ANIMATION_CITY = stringPreferencesKey("weather_animation_city")
        val KIDS_ENABLED = booleanPreferencesKey("kids_mode_enabled")
        val KIDS_BED_H = intPreferencesKey("kids_bed_hour")
        val KIDS_BED_M = intPreferencesKey("kids_bed_minute")
        val KIDS_WAKE_H = intPreferencesKey("kids_wake_hour")
        val KIDS_WAKE_M = intPreferencesKey("kids_wake_minute")
        val COMPANION_SOUNDS_MODE = stringPreferencesKey("companion_sounds_mode")
        val COMPANION_SOUNDS_VOLUME = floatPreferencesKey("companion_sounds_volume")
        val ADAPTIVE_BRIGHTNESS = booleanPreferencesKey("adaptive_brightness_enabled")
        val SCREENSAVER_ON_SCREEN_OFF = booleanPreferencesKey("screensaver_on_screen_off")
        val MEETING_ALERTS = booleanPreferencesKey("meeting_alerts_enabled")
        val MEETING_ALERT_LEAD = intPreferencesKey("meeting_alert_lead_minutes")
        val HIDE_MEETING_TITLES = booleanPreferencesKey("hide_meeting_titles")
        val AUTO_RETURN_COMPANION = booleanPreferencesKey("auto_return_to_companion_enabled")
    }

    val settingsFlow: Flow<DockSettings> = context.dockDataStore.data.map { prefs ->
        DockSettings(
            clockStyle = prefs[Keys.CLOCK_STYLE] ?: "NANO",
            agentTheme = prefs[Keys.AGENT_THEME] ?: "VALORANT",
            forceLandscapeMode = prefs[Keys.FORCE_LANDSCAPE] ?: false,
            use24HourFormat = prefs[Keys.USE_24_HOUR] ?: false,
            keepScreenAwake = prefs[Keys.KEEP_AWAKE] ?: true,
            nightRedMode = prefs[Keys.NIGHT_RED_MODE] ?: false,
            nightTimeOnlyEnabled = prefs[Keys.NIGHT_TIME_ONLY_ENABLED] ?: false,
            nightStartHour = (prefs[Keys.NIGHT_START_HOUR] ?: 22).coerceIn(0, 23),
            nightStartMinute = (prefs[Keys.NIGHT_START_MINUTE] ?: 0).coerceIn(0, 59),
            nightEndHour = (prefs[Keys.NIGHT_END_HOUR] ?: 7).coerceIn(0, 23),
            nightEndMinute = (prefs[Keys.NIGHT_END_MINUTE] ?: 0).coerceIn(0, 59),
            showPortHubTelemetry = prefs[Keys.SHOW_PORT_TELEMETRY] ?: false,
            showAgendaTicker = prefs[Keys.SHOW_AGENDA_TICKER] ?: true,
            brightnessDimmer = (prefs[Keys.BRIGHTNESS_DIMMER] ?: 1.0f).coerceIn(0.25f, 1.0f),
            localLlmEndpoint = prefs[Keys.LLM_ENDPOINT] ?: "http://127.0.0.1:8080/v1/chat/completions",
            localLlmModelName = prefs[Keys.LLM_MODEL] ?: "gemma-2-2b-it",
            cityName = prefs[Keys.CITY_NAME] ?: "Poitiers",
            cityLat = prefs[Keys.CITY_LAT] ?: 46.5802,
            cityLon = prefs[Keys.CITY_LON] ?: 0.3404,
            secondaryCityName = prefs[Keys.SECONDARY_CITY_NAME] ?: "Pittsburgh",
            secondaryCityLat = prefs[Keys.SECONDARY_CITY_LAT] ?: 40.4406,
            secondaryCityLon = prefs[Keys.SECONDARY_CITY_LON] ?: -79.9959,
            sunriseSimulationEnabled = prefs[Keys.SUNRISE_SIMULATION_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            useCatCompanion = prefs[Keys.USE_CAT] ?: true,
            catCoat = prefs[Keys.CAT_COAT] ?: "ORANGE_TABBY",
            catName = prefs[Keys.CAT_NAME] ?: "Mochi",
            autoDimInDark = prefs[Keys.AUTO_DIM] ?: true,
            burnInProtection = prefs[Keys.BURN_IN] ?: true,
            speakMorningBriefing = prefs[Keys.SPEAK_BRIEFING] ?: false,
            weatherAnimationCity = prefs[Keys.WEATHER_ANIMATION_CITY] ?: "PRIMARY",
            kidsModeEnabled = prefs[Keys.KIDS_ENABLED] ?: false,
            kidsBedHour = (prefs[Keys.KIDS_BED_H] ?: 19).coerceIn(0, 23),
            kidsBedMinute = (prefs[Keys.KIDS_BED_M] ?: 30).coerceIn(0, 59),
            kidsWakeHour = (prefs[Keys.KIDS_WAKE_H] ?: 7).coerceIn(0, 23),
            kidsWakeMinute = (prefs[Keys.KIDS_WAKE_M] ?: 0).coerceIn(0, 59),
            companionSoundsMode = prefs[Keys.COMPANION_SOUNDS_MODE] ?: "ALL",
            companionSoundsVolume = (prefs[Keys.COMPANION_SOUNDS_VOLUME] ?: 0.35f).coerceIn(0.05f, 1.0f),
            adaptiveBrightnessEnabled = prefs[Keys.ADAPTIVE_BRIGHTNESS] ?: true,
            // Off by default: showing over the lock screen is opt-in and always restricted (see LockedDockOverlay).
            screensaverOnScreenOff = prefs[Keys.SCREENSAVER_ON_SCREEN_OFF] ?: false,
            meetingAlertsEnabled = prefs[Keys.MEETING_ALERTS] ?: true,
            meetingAlertLeadMinutes = (prefs[Keys.MEETING_ALERT_LEAD] ?: 5).coerceIn(1, 30),
            hideMeetingTitles = prefs[Keys.HIDE_MEETING_TITLES] ?: false,
            autoReturnToCompanionEnabled = prefs[Keys.AUTO_RETURN_COMPANION] ?: true
        )
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dockDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setCompanion(useCat: Boolean, coat: String, name: String) {
        context.dockDataStore.edit {
            it[Keys.USE_CAT] = useCat
            it[Keys.CAT_COAT] = coat
            it[Keys.CAT_NAME] = name.trim().take(24).ifBlank { "Mochi" }
        }
    }

    suspend fun setKidsMode(enabled: Boolean, bedHour: Int, bedMinute: Int, wakeHour: Int, wakeMinute: Int) {
        context.dockDataStore.edit {
            it[Keys.KIDS_ENABLED] = enabled
            it[Keys.KIDS_BED_H] = bedHour
            it[Keys.KIDS_BED_M] = bedMinute
            it[Keys.KIDS_WAKE_H] = wakeHour
            it[Keys.KIDS_WAKE_M] = wakeMinute
        }
    }

    suspend fun setWeatherAnimationCity(choice: String) {
        context.dockDataStore.edit {
            it[Keys.WEATHER_ANIMATION_CITY] = if (choice == "SECONDARY") "SECONDARY" else "PRIMARY"
        }
    }

    suspend fun setCompanionSounds(mode: String, volume: Float) {
        val normalized = when (mode.uppercase()) {
            "OFF" -> "OFF"
            "REMINDERS" -> "REMINDERS"
            else -> "ALL"
        }
        context.dockDataStore.edit {
            it[Keys.COMPANION_SOUNDS_MODE] = normalized
            it[Keys.COMPANION_SOUNDS_VOLUME] = volume.coerceIn(0.05f, 1.0f)
        }
    }

    suspend fun setDisplayCare(autoDim: Boolean, burnIn: Boolean, speakBriefing: Boolean) {
        context.dockDataStore.edit {
            it[Keys.AUTO_DIM] = autoDim
            it[Keys.BURN_IN] = burnIn
            it[Keys.SPEAK_BRIEFING] = speakBriefing
        }
    }

    suspend fun setAdaptiveBrightnessEnabled(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.ADAPTIVE_BRIGHTNESS] = enabled }
    }

    suspend fun setMeetingAlerts(enabled: Boolean, leadMinutes: Int, hideTitles: Boolean) {
        context.dockDataStore.edit {
            it[Keys.MEETING_ALERTS] = enabled
            it[Keys.MEETING_ALERT_LEAD] = leadMinutes.coerceIn(1, 30)
            it[Keys.HIDE_MEETING_TITLES] = hideTitles
        }
    }

    suspend fun setScreensaverOnScreenOff(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.SCREENSAVER_ON_SCREEN_OFF] = enabled }
    }

    suspend fun setAutoReturnToCompanionEnabled(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.AUTO_RETURN_COMPANION] = enabled }
    }

    suspend fun setSunriseSimulationEnabled(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.SUNRISE_SIMULATION_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setClockStyle(style: String) {
        context.dockDataStore.edit { it[Keys.CLOCK_STYLE] = style }
    }

    suspend fun setAgentTheme(theme: String) {
        context.dockDataStore.edit { it[Keys.AGENT_THEME] = theme }
    }

    suspend fun setForceLandscapeMode(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.FORCE_LANDSCAPE] = enabled }
    }

    suspend fun setUse24HourFormat(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.USE_24_HOUR] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.KEEP_AWAKE] = enabled }
    }

    suspend fun setNightRedMode(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.NIGHT_RED_MODE] = enabled }
    }

    suspend fun setNightTimeOnlyEnabled(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.NIGHT_TIME_ONLY_ENABLED] = enabled }
    }

    suspend fun setNightTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.dockDataStore.edit {
            it[Keys.NIGHT_START_HOUR] = (startHour % 24 + 24) % 24
            it[Keys.NIGHT_START_MINUTE] = (startMinute % 60 + 60) % 60
            it[Keys.NIGHT_END_HOUR] = (endHour % 24 + 24) % 24
            it[Keys.NIGHT_END_MINUTE] = (endMinute % 60 + 60) % 60
        }
    }

    suspend fun setShowPortHubTelemetry(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.SHOW_PORT_TELEMETRY] = enabled }
    }

    suspend fun setShowAgendaTicker(enabled: Boolean) {
        context.dockDataStore.edit { it[Keys.SHOW_AGENDA_TICKER] = enabled }
    }

    suspend fun setBrightnessDimmer(value: Float) {
        context.dockDataStore.edit { it[Keys.BRIGHTNESS_DIMMER] = value.coerceIn(0.25f, 1.0f) }
    }

    suspend fun setLocalLlmConfig(endpoint: String, modelName: String) {
        context.dockDataStore.edit {
            it[Keys.LLM_ENDPOINT] = endpoint.trim()
            it[Keys.LLM_MODEL] = modelName.trim()
        }
    }

    suspend fun setWeatherCity(cityName: String) {
        val preset = WeatherRepository.findCityPreset(cityName, WeatherRepository.POITIERS_PRESET)
        context.dockDataStore.edit {
            it[Keys.CITY_NAME] = preset.name
            it[Keys.CITY_LAT] = preset.latitude
            it[Keys.CITY_LON] = preset.longitude
        }
    }

    suspend fun setSecondaryWeatherCity(cityName: String) {
        val preset = WeatherRepository.findCityPreset(cityName, WeatherRepository.PITTSBURGH_PRESET)
        context.dockDataStore.edit {
            it[Keys.SECONDARY_CITY_NAME] = preset.name
            it[Keys.SECONDARY_CITY_LAT] = preset.latitude
            it[Keys.SECONDARY_CITY_LON] = preset.longitude
        }
    }

    suspend fun setDualWeatherCities(primaryCityName: String, secondaryCityName: String) {
        val primary = WeatherRepository.findCityPreset(primaryCityName, WeatherRepository.POITIERS_PRESET)
        val secondary = WeatherRepository.findCityPreset(secondaryCityName, WeatherRepository.PITTSBURGH_PRESET)
        context.dockDataStore.edit {
            it[Keys.CITY_NAME] = primary.name
            it[Keys.CITY_LAT] = primary.latitude
            it[Keys.CITY_LON] = primary.longitude
            it[Keys.SECONDARY_CITY_NAME] = secondary.name
            it[Keys.SECONDARY_CITY_LAT] = secondary.latitude
            it[Keys.SECONDARY_CITY_LON] = secondary.longitude
        }
    }
}
