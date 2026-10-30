package com.example.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class CityPreset(
    val name: String,
    val countryCode: String,
    val timeZoneId: String,
    val latitude: Double,
    val longitude: Double,
    val regionLabel: String = ""
)

data class WeatherStationData(
    val cityName: String,
    val countryCode: String,
    val timeZoneId: String,
    val temperatureC: Double,
    val highC: Double,
    val lowC: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val conditionLabel: String,
    val weatherCode: Int,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /** Today's local sunrise as minutes from midnight (0..1439) in [timeZoneId]. */
    val sunriseMinuteOfDay: Int? = null,
    /** Today's local sunset as minutes from midnight (0..1439) in [timeZoneId]. */
    val sunsetMinuteOfDay: Int? = null,
    /** Open-Meteo's live `is_day` flag at the time of reading. */
    val isDay: Boolean? = null
)

data class DualCityWeather(
    val primaryCity: WeatherStationData?,
    val secondaryCity: WeatherStationData?
) {
    /**
     * Weather that drives the animated sky behind the companion.
     * [choice] is "PRIMARY" or "SECONDARY"; falls back to the other city if the chosen one failed to load.
     */
    fun forAnimation(choice: String): WeatherStationData? =
        if (choice == WEATHER_CITY_SECONDARY) secondaryCity ?: primaryCity else primaryCity ?: secondaryCity

    /**
     * Keeps the last real weather reading when a refresh fails (for example, a brief Wi-Fi drop),
     * unless the user changed that slot to a different city.
     */
    fun mergePreservingLast(
        fresh: DualCityWeather,
        primaryCityName: String = primaryCity?.cityName.orEmpty(),
        secondaryCityName: String = secondaryCity?.cityName.orEmpty()
    ): DualCityWeather {
        val keptPrimary = fresh.primaryCity ?: primaryCity?.takeIf {
            primaryCityName.isBlank() || it.cityName.equals(primaryCityName.trim(), ignoreCase = true)
        }
        val keptSecondary = fresh.secondaryCity ?: secondaryCity?.takeIf {
            secondaryCityName.isBlank() || it.cityName.equals(secondaryCityName.trim(), ignoreCase = true)
        }
        return DualCityWeather(primaryCity = keptPrimary, secondaryCity = keptSecondary)
    }

    companion object {
        const val WEATHER_CITY_PRIMARY = "PRIMARY"
        const val WEATHER_CITY_SECONDARY = "SECONDARY"
    }
}

class WeatherRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        val POITIERS_PRESET = CityPreset(
            name = "Poitiers",
            countryCode = "FR",
            timeZoneId = "Europe/Paris",
            latitude = 46.5802,
            longitude = 0.3404,
            regionLabel = "Europe"
        )

        val PITTSBURGH_PRESET = CityPreset(
            name = "Pittsburgh",
            countryCode = "US",
            timeZoneId = "America/New_York",
            latitude = 40.4406,
            longitude = -79.9959,
            regionLabel = "Americas"
        )

        private val dynamicCityCache = ConcurrentHashMap<String, CityPreset>()

        val CITY_PRESETS = listOf(
            // Europe
            POITIERS_PRESET,
            CityPreset("Paris", "FR", "Europe/Paris", 48.8566, 2.3522, "Europe"),
            CityPreset("London", "UK", "Europe/London", 51.5074, -0.1278, "Europe"),
            CityPreset("Berlin", "DE", "Europe/Berlin", 52.5200, 13.4050, "Europe"),
            CityPreset("Madrid", "ES", "Europe/Madrid", 40.4168, -3.7038, "Europe"),
            CityPreset("Rome", "IT", "Europe/Rome", 41.9028, 12.4964, "Europe"),
            CityPreset("Amsterdam", "NL", "Europe/Amsterdam", 52.3676, 4.9041, "Europe"),
            CityPreset("Zurich", "CH", "Europe/Zurich", 47.3769, 8.5417, "Europe"),
            CityPreset("Vienna", "AT", "Europe/Vienna", 48.2082, 16.3738, "Europe"),
            CityPreset("Stockholm", "SE", "Europe/Stockholm", 59.3293, 18.0686, "Europe"),
            CityPreset("Dublin", "IE", "Europe/Dublin", 53.3498, -6.2603, "Europe"),
            CityPreset("Lisbon", "PT", "Europe/Lisbon", 38.7223, -9.1393, "Europe"),
            CityPreset("Athens", "GR", "Europe/Athens", 37.9838, 23.7275, "Europe"),
            CityPreset("Warsaw", "PL", "Europe/Warsaw", 52.2297, 21.0122, "Europe"),
            CityPreset("Prague", "CZ", "Europe/Prague", 50.0755, 14.4378, "Europe"),
            CityPreset("Istanbul", "TR", "Europe/Istanbul", 41.0082, 28.9784, "Europe"),
            CityPreset("Reykjavik", "IS", "Atlantic/Reykjavik", 64.1466, -21.9426, "Europe"),

            // Americas
            PITTSBURGH_PRESET,
            CityPreset("New York", "US", "America/New_York", 40.7128, -74.0060, "Americas"),
            CityPreset("San Francisco", "US", "America/Los_Angeles", 37.7749, -122.4194, "Americas"),
            CityPreset("Chicago", "US", "America/Chicago", 41.8781, -87.6298, "Americas"),
            CityPreset("Los Angeles", "US", "America/Los_Angeles", 34.0522, -118.2437, "Americas"),
            CityPreset("Seattle", "US", "America/Los_Angeles", 47.6062, -122.3321, "Americas"),
            CityPreset("Miami", "US", "America/New_York", 25.7617, -80.1918, "Americas"),
            CityPreset("Austin", "US", "America/Chicago", 30.2672, -97.7431, "Americas"),
            CityPreset("Boston", "US", "America/New_York", 42.3601, -71.0589, "Americas"),
            CityPreset("Toronto", "CA", "America/Toronto", 43.6532, -79.3832, "Americas"),
            CityPreset("Vancouver", "CA", "America/Vancouver", 49.2827, -123.1207, "Americas"),
            CityPreset("Montreal", "CA", "America/Toronto", 45.5017, -73.5673, "Americas"),
            CityPreset("Mexico City", "MX", "America/Mexico_City", 19.4326, -99.1332, "Americas"),
            CityPreset("Sao Paulo", "BR", "America/Sao_Paulo", -23.5505, -46.6333, "Americas"),
            CityPreset("Rio de Janeiro", "BR", "America/Sao_Paulo", -22.9068, -43.1729, "Americas"),
            CityPreset("Buenos Aires", "AR", "America/Argentina/Buenos_Aires", -34.6037, -58.3816, "Americas"),
            CityPreset("Santiago", "CL", "America/Santiago", -33.4489, -70.6693, "Americas"),
            CityPreset("Bogota", "CO", "America/Bogota", 4.7110, -74.0721, "Americas"),
            CityPreset("Lima", "PE", "America/Lima", -12.0464, -77.0428, "Americas"),

            // Asia & Pacific
            CityPreset("Tokyo", "JP", "Asia/Tokyo", 35.6762, 139.6503, "Asia"),
            CityPreset("Osaka", "JP", "Asia/Tokyo", 34.6937, 135.5023, "Asia"),
            CityPreset("Seoul", "KR", "Asia/Seoul", 37.5665, 126.9780, "Asia"),
            CityPreset("Singapore", "SG", "Asia/Singapore", 1.3521, 103.8198, "Asia"),
            CityPreset("Mumbai", "IN", "Asia/Kolkata", 19.0760, 72.8777, "Asia"),
            CityPreset("New Delhi", "IN", "Asia/Kolkata", 28.6139, 77.2090, "Asia"),
            CityPreset("Bengaluru", "IN", "Asia/Kolkata", 12.9716, 77.5946, "Asia"),
            CityPreset("Chennai", "IN", "Asia/Kolkata", 13.0827, 80.2707, "Asia"),
            CityPreset("Hyderabad", "IN", "Asia/Kolkata", 17.3850, 78.4867, "Asia"),
            CityPreset("Bangkok", "TH", "Asia/Bangkok", 13.7563, 100.5018, "Asia"),
            CityPreset("Hong Kong", "HK", "Asia/Hong_Kong", 22.3193, 114.1694, "Asia"),
            CityPreset("Taipei", "TW", "Asia/Taipei", 25.0330, 121.5654, "Asia"),
            CityPreset("Shanghai", "CN", "Asia/Shanghai", 31.2304, 121.4737, "Asia"),
            CityPreset("Beijing", "CN", "Asia/Shanghai", 39.9042, 116.4074, "Asia"),
            CityPreset("Kuala Lumpur", "MY", "Asia/Kuala_Lumpur", 3.1390, 101.6869, "Asia"),
            CityPreset("Jakarta", "ID", "Asia/Jakarta", -6.2088, 106.8456, "Asia"),
            CityPreset("Manila", "PH", "Asia/Manila", 14.5995, 120.9842, "Asia"),

            // Middle East, Africa & Oceania
            CityPreset("Dubai", "AE", "Asia/Dubai", 25.2048, 55.2708, "MEA & Oceania"),
            CityPreset("Abu Dhabi", "AE", "Asia/Dubai", 24.4539, 54.3773, "MEA & Oceania"),
            CityPreset("Doha", "QA", "Asia/Qatar", 25.2854, 51.5310, "MEA & Oceania"),
            CityPreset("Riyadh", "SA", "Asia/Riyadh", 24.7136, 46.6753, "MEA & Oceania"),
            CityPreset("Cairo", "EG", "Africa/Cairo", 30.0444, 31.2357, "MEA & Oceania"),
            CityPreset("Johannesburg", "ZA", "Africa/Johannesburg", -26.2041, 28.0473, "MEA & Oceania"),
            CityPreset("Cape Town", "ZA", "Africa/Johannesburg", -33.9249, 18.4241, "MEA & Oceania"),
            CityPreset("Nairobi", "KE", "Africa/Nairobi", -1.2921, 36.8219, "MEA & Oceania"),
            CityPreset("Lagos", "NG", "Africa/Lagos", 6.5244, 3.3792, "MEA & Oceania"),
            CityPreset("Casablanca", "MA", "Africa/Casablanca", 33.5731, -7.5898, "MEA & Oceania"),
            CityPreset("Sydney", "AU", "Australia/Sydney", -33.8688, 151.2093, "MEA & Oceania"),
            CityPreset("Melbourne", "AU", "Australia/Melbourne", -37.8136, 144.9631, "MEA & Oceania"),
            CityPreset("Auckland", "NZ", "Pacific/Auckland", -36.8485, 174.7633, "MEA & Oceania")
        )

        fun registerCustomCityPreset(preset: CityPreset) {
            dynamicCityCache[preset.name.lowercase()] = preset
        }

        fun findCityPreset(cityName: String, fallback: CityPreset = POITIERS_PRESET): CityPreset {
            val clean = cityName.trim()
            if (clean.isEmpty()) return fallback
            CITY_PRESETS.find { it.name.equals(clean, ignoreCase = true) }?.let { return it }
            dynamicCityCache[clean.lowercase()]?.let { return it }
            return CityPreset(
                name = clean,
                countryCode = fallback.countryCode,
                timeZoneId = fallback.timeZoneId,
                latitude = fallback.latitude,
                longitude = fallback.longitude
            )
        }

        fun mapWmoCodeToLabel(code: Int): String = when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Misty Fog"
            51, 53, 55 -> "Light Drizzle"
            61, 63, 65 -> "Rain Showers"
            71, 73, 75 -> "Snowfall"
            80, 81, 82 -> "Scattered Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Fair Conditions"
        }

        /**
         * Parses an Open-Meteo local ISO timestamp (e.g. "2026-10-29T18:40") into minutes from midnight (0..1439).
         */
        fun parseIsoMinuteOfDay(iso: String?): Int? {
            if (iso.isNullOrBlank()) return null
            val timePart = iso.substringAfter('T', "").takeIf { it.isNotEmpty() } ?: return null
            val parts = timePart.split(':')
            if (parts.size < 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].take(2).toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }
    }

    /**
     * Searches any city in the world using local catalog + Open-Meteo Worldwide Geocoding API.
     */
    suspend fun searchWorldwideCities(query: String): List<CityPreset> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext CITY_PRESETS
        val localMatches = CITY_PRESETS.filter {
            it.name.contains(clean, ignoreCase = true) ||
                it.countryCode.contains(clean, ignoreCase = true) ||
                it.regionLabel.contains(clean, ignoreCase = true)
        }

        val remoteMatches = mutableListOf<CityPreset>()
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=en&format=json"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    if (bodyStr.isNotBlank()) {
                        val root = JSONObject(bodyStr)
                        val results = root.optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val name = item.optString("name", "").trim()
                                val countryCode = item.optString("country_code", "GL").uppercase()
                                val tz = item.optString("timezone", "UTC").ifBlank { "UTC" }
                                val lat = item.optDouble("latitude", 0.0)
                                val lon = item.optDouble("longitude", 0.0)
                                val admin1 = item.optString("admin1", "")
                                if (name.isNotBlank()) {
                                    val preset = CityPreset(
                                        name = name,
                                        countryCode = countryCode,
                                        timeZoneId = tz,
                                        latitude = lat,
                                        longitude = lon,
                                        regionLabel = admin1
                                    )
                                    registerCustomCityPreset(preset)
                                    remoteMatches.add(preset)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        (localMatches + remoteMatches).distinctBy { "${it.name.lowercase()}_${it.countryCode.lowercase()}" }
    }

    private suspend fun resolveOrGeocodeCity(cityName: String, fallback: CityPreset): CityPreset {
        val clean = cityName.trim()
        if (clean.isEmpty()) return fallback
        CITY_PRESETS.find { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        dynamicCityCache[clean.lowercase()]?.let { return it }
        val geocoded = searchWorldwideCities(clean).firstOrNull()
        if (geocoded != null) {
            registerCustomCityPreset(geocoded)
            return geocoded
        }
        return findCityPreset(clean, fallback)
    }

    suspend fun fetchDualPoitiersAndPittsburgh(): DualCityWeather =
        fetchDualCitiesWeather(POITIERS_PRESET.name, PITTSBURGH_PRESET.name)

    suspend fun fetchDualCitiesWeather(
        firstCityName: String,
        secondCityName: String
    ): DualCityWeather = coroutineScope {
        val city1 = async { resolveOrGeocodeCity(firstCityName, POITIERS_PRESET) }
        val city2 = async { resolveOrGeocodeCity(secondCityName, PITTSBURGH_PRESET) }
        val resolved1 = city1.await()
        val resolved2 = city2.await()
        val firstDeferred = async { fetchLiveWeather(resolved1).getOrNull() }
        val secondDeferred = async { fetchLiveWeather(resolved2).getOrNull() }
        DualCityWeather(
            primaryCity = firstDeferred.await(),
            secondaryCity = secondDeferred.await()
        )
    }

    suspend fun fetchLiveWeather(city: CityPreset): Result<WeatherStationData> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${city.latitude}&longitude=${city.longitude}" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Weather HTTP ${response.code}")
                    )
                }
                val bodyStr = response.body?.string()
                    ?: return@withContext Result.failure(IllegalStateException("Empty weather body"))

                val root = JSONObject(bodyStr)
                val resolvedTimezone = root.optString("timezone", city.timeZoneId).ifBlank { city.timeZoneId }
                val current = root.getJSONObject("current")
                val temp = current.optDouble("temperature_2m", 18.0)
                val humidity = current.optInt("relative_humidity_2m", 60)
                val code = current.optInt("weather_code", 1)
                val wind = current.optDouble("wind_speed_10m", 9.0)
                val isDay = if (current.has("is_day")) current.optInt("is_day", 1) == 1 else null

                val daily = root.optJSONObject("daily")
                val maxArr = daily?.optJSONArray("temperature_2m_max")
                val minArr = daily?.optJSONArray("temperature_2m_min")
                val sunriseArr = daily?.optJSONArray("sunrise")
                val sunsetArr = daily?.optJSONArray("sunset")
                val high = if (maxArr != null && maxArr.length() > 0) maxArr.optDouble(0, temp + 3) else temp + 3
                val low = if (minArr != null && minArr.length() > 0) minArr.optDouble(0, temp - 4) else temp - 4
                val sunriseMin = if (sunriseArr != null && sunriseArr.length() > 0) {
                    parseIsoMinuteOfDay(sunriseArr.optString(0))
                } else null
                val sunsetMin = if (sunsetArr != null && sunsetArr.length() > 0) {
                    parseIsoMinuteOfDay(sunsetArr.optString(0))
                } else null

                Result.success(
                    WeatherStationData(
                        cityName = city.name,
                        countryCode = city.countryCode,
                        timeZoneId = resolvedTimezone,
                        temperatureC = temp,
                        highC = high,
                        lowC = low,
                        humidityPercent = humidity,
                        windKmh = wind,
                        conditionLabel = mapWmoCodeToLabel(code),
                        weatherCode = code,
                        sunriseMinuteOfDay = sunriseMin,
                        sunsetMinuteOfDay = sunsetMin,
                        isDay = isDay
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

