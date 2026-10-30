package com.example

import com.example.alarm.CompanionSoundCue
import com.example.alarm.CompanionSoundPlayer
import com.example.data.weather.DualCityWeather
import com.example.data.weather.WeatherRepository
import com.example.data.weather.WeatherStationData
import com.example.ui.components.AtmosphericCondition
import com.example.ui.components.isDaytimeForWeather
import com.example.ui.components.isDaytimeInTimeZone
import com.example.ui.components.resolveAtmosphericCondition
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCityChoiceTest {
    private fun station(
        name: String,
        timeZoneId: String = "UTC",
        weatherCode: Int = 0,
        sunriseMinute: Int? = null,
        sunsetMinute: Int? = null,
        isDay: Boolean? = null
    ) = WeatherStationData(
        cityName = name,
        countryCode = "",
        timeZoneId = timeZoneId,
        temperatureC = 10.0,
        highC = 12.0,
        lowC = 5.0,
        humidityPercent = 50,
        windKmh = 5.0,
        conditionLabel = WeatherRepository.mapWmoCodeToLabel(weatherCode),
        weatherCode = weatherCode,
        sunriseMinuteOfDay = sunriseMinute,
        sunsetMinuteOfDay = sunsetMinute,
        isDay = isDay
    )

    @Test
    fun choosesRequestedCity_andFallsBackWhenMissing() {
        val both = DualCityWeather(station("Poitiers"), station("Pittsburgh"))
        assertEquals("Poitiers", both.forAnimation("PRIMARY")?.cityName)
        assertEquals("Pittsburgh", both.forAnimation("SECONDARY")?.cityName)

        val onlyPrimary = DualCityWeather(station("Poitiers"), null)
        assertEquals("Poitiers", onlyPrimary.forAnimation("SECONDARY")?.cityName)

        assertNull(DualCityWeather(null, null).forAnimation("PRIMARY"))
    }

    @Test
    fun failedRefresh_keepsLastRealWeatherInsteadOfFakeSunshine() {
        val last = DualCityWeather(
            primaryCity = station("Poitiers", weatherCode = 61),
            secondaryCity = station("Pittsburgh", weatherCode = 3)
        )
        val failedRefresh = DualCityWeather(null, null)

        val merged = last.mergePreservingLast(
            fresh = failedRefresh,
            primaryCityName = "Poitiers",
            secondaryCityName = "Pittsburgh"
        )
        assertEquals(61, merged.primaryCity?.weatherCode)
        assertEquals(3, merged.secondaryCity?.weatherCode)

        // If the user switches to a different city and the first fetch fails, do not keep the old city's data
        val switchedCity = last.mergePreservingLast(
            fresh = failedRefresh,
            primaryCityName = "Tokyo",
            secondaryCityName = "Pittsburgh"
        )
        assertNull(switchedCity.primaryCity)
        assertEquals(3, switchedCity.secondaryCity?.weatherCode)
    }

    @Test
    fun mainlyClearCode1_showsClearSkyInsteadOfClouds() {
        assertEquals(
            AtmosphericCondition.SUNNY_CLEAR,
            resolveAtmosphericCondition(weatherCode = 1, isDaytime = true)
        )
        assertEquals(
            AtmosphericCondition.STARRY_NIGHT,
            resolveAtmosphericCondition(weatherCode = 1, isDaytime = false)
        )
        assertEquals(
            AtmosphericCondition.PARTLY_CLOUDY,
            resolveAtmosphericCondition(weatherCode = 2, isDaytime = true)
        )
    }

    @Test
    fun poitiersLateOctoberSunset_turnsStarryBefore20h() {
        val parisTz = TimeZone.getTimeZone("Europe/Paris")
        // Late October (Oct 24) in Poitiers: sunset is around 18:40 CEST.
        val afternoon = Calendar.getInstance(parisTz).apply {
            set(2026, Calendar.OCTOBER, 24, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val afterAutumnSunsetBefore20h = Calendar.getInstance(parisTz).apply {
            set(2026, Calendar.OCTOBER, 24, 19, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Astronomical fallback (even without Open-Meteo sunrise/sunset fields)
        assertTrue(isDaytimeInTimeZone("Europe/Paris", afternoon))
        assertFalse(isDaytimeInTimeZone("Europe/Paris", afterAutumnSunsetBefore20h))

        // Exact Open-Meteo sunrise (08:27 = 507) & sunset (18:40 = 1120)
        val sunriseMin = WeatherRepository.parseIsoMinuteOfDay("2026-10-24T08:27")
        val sunsetMin = WeatherRepository.parseIsoMinuteOfDay("2026-10-24T18:40")
        assertEquals(8 * 60 + 27, sunriseMin)
        assertEquals(18 * 60 + 40, sunsetMin)

        val poitiersWeather = station(
            name = "Poitiers",
            timeZoneId = "Europe/Paris",
            weatherCode = 1,
            sunriseMinute = sunriseMin,
            sunsetMinute = sunsetMin
        )

        val justBeforeSunset = Calendar.getInstance(parisTz).apply {
            set(2026, Calendar.OCTOBER, 24, 18, 35, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val rightAtSunset = Calendar.getInstance(parisTz).apply {
            set(2026, Calendar.OCTOBER, 24, 18, 40, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertTrue(isDaytimeForWeather(poitiersWeather, justBeforeSunset))
        assertFalse(isDaytimeForWeather(poitiersWeather, rightAtSunset))
        assertEquals(
            AtmosphericCondition.STARRY_NIGHT,
            resolveAtmosphericCondition(
                poitiersWeather.weatherCode,
                isDaytimeForWeather(poitiersWeather, rightAtSunset)
            )
        )
    }

    @Test
    fun companionSoundPlayer_synthesizesCatAndRobotVariations() {
        for (cue in CompanionSoundCue.entries) {
            val catSamples = CompanionSoundPlayer.synthesizeCue(cue, useCat = true, variant = 0, volume = 0.35f)
            val robotSamples = CompanionSoundPlayer.synthesizeCue(cue, useCat = false, variant = 2, volume = 0.35f)
            assertTrue("Expected non-empty cat audio for $cue", catSamples.isNotEmpty())
            assertTrue("Expected non-empty robot audio for $cue", robotSamples.isNotEmpty())
        }
    }
}
