package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.weather.WeatherRepository
import com.example.data.weather.WeatherStationData
import com.example.ui.theme.ChargingEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KilljoyGold
import com.example.ui.theme.RadianiteCyan
import com.example.ui.theme.ValorantCrimson
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlinx.coroutines.delay

/**
 * Atmospheric weather condition categories for realistic real-time backdrop visualization.
 */
enum class AtmosphericCondition(
    val label: String,
    val iconEmoji: String,
    val accentColor: Color
) {
    SUNNY_CLEAR("Sunny & Clear", "☀️", KilljoyGold),
    STARRY_NIGHT("Starry Night", "🌙", Color(0xFF60A5FA)),
    PARTLY_CLOUDY("Partly Cloudy", "⛅", Color(0xFF38BDF8)),
    OVERCAST_MIST("Overcast & Mist", "🌫️", Color(0xFF94A3B8)),
    RAIN_SHOWERS("Rain Showers", "🌧️", RadianiteCyan),
    THUNDERSTORM("Thunderstorm", "⚡", Color(0xFFA855F7)),
    SNOWFALL("Snowfall", "❄️", Color(0xFFE2E8F0))
}

/**
 * Maps live WMO weather codes and local time-of-day to an [AtmosphericCondition].
 * WMO code 0 ("Clear sky") and 1 ("Mainly clear") both render a clear sky rather than clouds.
 */
fun resolveAtmosphericCondition(
    weatherCode: Int,
    isDaytime: Boolean
): AtmosphericCondition {
    return when (weatherCode) {
        0, 1 -> if (isDaytime) AtmosphericCondition.SUNNY_CLEAR else AtmosphericCondition.STARRY_NIGHT
        2 -> if (isDaytime) AtmosphericCondition.PARTLY_CLOUDY else AtmosphericCondition.STARRY_NIGHT
        3, 45, 48 -> AtmosphericCondition.OVERCAST_MIST
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> AtmosphericCondition.RAIN_SHOWERS
        71, 73, 75, 77, 85, 86 -> AtmosphericCondition.SNOWFALL
        95, 96, 99 -> AtmosphericCondition.THUNDERSTORM
        else -> if (isDaytime) AtmosphericCondition.SUNNY_CLEAR else AtmosphericCondition.STARRY_NIGHT
    }
}

/**
 * Estimates today's local sunrise and sunset as minutes from midnight (0..1439) using the
 * NOAA solar equation for the given [cal] date and [latitude]/[longitude].
 * In Poitiers (46.58°N, 0.34°E) in late October, sunset is around 18:40 CEST / 17:40 CET.
 */
fun estimateSunriseSunsetMinutes(
    cal: Calendar,
    latitude: Double,
    longitude: Double
): Pair<Int, Int> {
    val doy = cal.get(Calendar.DAY_OF_YEAR)
    val gamma = 2.0 * PI / 365.0 * (doy - 1 + 0.5)
    val eqTime = 229.18 * (
        0.000075 +
            0.001868 * cos(gamma) -
            0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) -
            0.040849 * sin(2 * gamma)
        )
    val decl = 0.006918 -
        0.399912 * cos(gamma) +
        0.070257 * sin(gamma) -
        0.006758 * cos(2 * gamma) +
        0.000907 * sin(2 * gamma) -
        0.002697 * cos(3 * gamma) +
        0.00148 * sin(3 * gamma)
    val latRad = Math.toRadians(latitude.coerceIn(-65.0, 65.0))
    val zenith = Math.toRadians(90.833)
    val cosH = ((cos(zenith) / (cos(latRad) * cos(decl))) - tan(latRad) * tan(decl)).coerceIn(-1.0, 1.0)
    val hourAngleDeg = Math.toDegrees(acos(cosH))
    val tzOffsetMinutes = cal.timeZone.getOffset(cal.timeInMillis) / 60_000.0
    val solarNoonMin = 720.0 - 4.0 * longitude - eqTime + tzOffsetMinutes
    val sunriseMin = (solarNoonMin - hourAngleDeg * 4.0).toInt().coerceIn(0, 1439)
    val sunsetMin = (solarNoonMin + hourAngleDeg * 4.0).toInt().coerceIn(0, 1439)
    return sunriseMin to sunsetMin
}

/**
 * Evaluates whether it is currently daytime in the specified station timezone, following
 * actual seasonal/astronomical sunrise and sunset (or Open-Meteo's reported sunrise/sunset).
 */
fun isDaytimeInTimeZone(
    timeZoneId: String,
    currentTimeMillis: Long,
    cityName: String? = null,
    sunriseMinuteOfDay: Int? = null,
    sunsetMinuteOfDay: Int? = null
): Boolean {
    return try {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = currentTimeMillis }
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (sunriseMinuteOfDay != null && sunsetMinuteOfDay != null && sunriseMinuteOfDay < sunsetMinuteOfDay) {
            return nowMinute in sunriseMinuteOfDay until sunsetMinuteOfDay
        }
        val preset = if (!cityName.isNullOrBlank()) {
            WeatherRepository.findCityPreset(cityName)
        } else {
            WeatherRepository.CITY_PRESETS.firstOrNull { it.timeZoneId.equals(timeZoneId, ignoreCase = true) }
                ?: WeatherRepository.POITIERS_PRESET
        }
        val (sunrise, sunset) = estimateSunriseSunsetMinutes(cal, preset.latitude, preset.longitude)
        nowMinute in sunrise until sunset
    } catch (_: Exception) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val (sunrise, sunset) = estimateSunriseSunsetMinutes(
            cal,
            WeatherRepository.POITIERS_PRESET.latitude,
            WeatherRepository.POITIERS_PRESET.longitude
        )
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        nowMinute in sunrise until sunset
    }
}

/**
 * Evaluates whether it is currently daytime for [weatherData] at [currentTimeMillis].
 * Prefers today's Open-Meteo sunrise/sunset when available, then falls back to astronomical sunrise/sunset.
 */
fun isDaytimeForWeather(weatherData: WeatherStationData?, currentTimeMillis: Long): Boolean {
    if (weatherData == null) {
        return isDaytimeInTimeZone(TimeZone.getDefault().id, currentTimeMillis)
    }
    return isDaytimeInTimeZone(
        timeZoneId = weatherData.timeZoneId,
        currentTimeMillis = currentTimeMillis,
        cityName = weatherData.cityName,
        sunriseMinuteOfDay = weatherData.sunriseMinuteOfDay,
        sunsetMinuteOfDay = weatherData.sunsetMinuteOfDay
    )
}

/**
 * High-performance real-time atmospheric weather visualizer canvas.
 * Renders live dynamic conditions like golden solar lens flares, falling angled rain streaks,
 * electric thunderstorm lightning flashes, drifting cloud banks, and fluttering snowflakes
 * behind the AI Toy Companion.
 */
@Composable
fun WeatherAtmosphereVisualizer(
    weatherData: WeatherStationData?,
    currentTimeMillis: Long = System.currentTimeMillis(),
    showLiveBadge: Boolean = false,
    onCycleConditionPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Keep the last real weather reading if a refresh fails (no fake sunshine fallback).
    var lastRealWeather by remember { mutableStateOf<WeatherStationData?>(null) }
    if (weatherData != null) {
        lastRealWeather = weatherData
    }
    val effectiveWeather = weatherData ?: lastRealWeather

    // Re-evaluate once per minute so sunrise/sunset transitions happen right on time in every season.
    val minuteBucket = currentTimeMillis / 60_000L
    val isDaytime = remember(
        effectiveWeather?.cityName,
        effectiveWeather?.timeZoneId,
        effectiveWeather?.sunriseMinuteOfDay,
        effectiveWeather?.sunsetMinuteOfDay,
        minuteBucket
    ) {
        isDaytimeForWeather(effectiveWeather, currentTimeMillis)
    }

    val liveCondition = remember(effectiveWeather?.weatherCode, isDaytime) {
        effectiveWeather?.let { resolveAtmosphericCondition(it.weatherCode, isDaytime) }
            ?: if (isDaytime) AtmosphericCondition.SUNNY_CLEAR else AtmosphericCondition.STARRY_NIGHT
    }

    // Interactive preview override state so users can tap to test/cycle different conditions.
    // Resets automatically back to live weather after 20 seconds.
    var previewConditionIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(previewConditionIndex) {
        if (previewConditionIndex != -1) {
            delay(20_000L)
            previewConditionIndex = -1
        }
    }
    val activeCondition = if (previewConditionIndex in AtmosphericCondition.entries.indices) {
        AtmosphericCondition.entries[previewConditionIndex]
    } else {
        liveCondition
    }

    val infinite = rememberInfiniteTransition(label = "weather_atmosphere")

    // Slow solar / celestial rotation (0..2PI over 20s)
    val solarAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather_solar_angle"
    )

    // Fast rain / lightning loop (0..1f over 800ms)
    val rainPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather_rain_phase"
    )

    // Medium cloud and snow drift cycle (0..1f over 7000ms)
    val cloudDrift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather_cloud_drift"
    )

    // Periodic thunderstorm lightning pulse (0..1f over 3200ms)
    val lightningCycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather_lightning_cycle"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("weather_atmosphere_visualizer")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val u = kotlin.math.min(h / 78f, w / 140f)
            val floorY = h - 6f * u

            when (activeCondition) {
                AtmosphericCondition.SUNNY_CLEAR -> {
                    drawSunnyBackdrop(
                        w = w,
                        h = h,
                        u = u,
                        solarAngle = solarAngle,
                        drift = cloudDrift
                    )
                }
                AtmosphericCondition.STARRY_NIGHT -> {
                    drawStarryNightBackdrop(
                        w = w,
                        h = h,
                        u = u,
                        drift = cloudDrift
                    )
                }
                AtmosphericCondition.PARTLY_CLOUDY -> {
                    drawSunnyBackdrop(w, h, u, solarAngle, cloudDrift, sunAlpha = 0.55f)
                    drawCloudLayer(w, h, u, drift = cloudDrift, isDense = false)
                }
                AtmosphericCondition.OVERCAST_MIST -> {
                    drawOvercastMistBackdrop(w, h, u, drift = cloudDrift, floorY = floorY)
                }
                AtmosphericCondition.RAIN_SHOWERS -> {
                    drawRainBackdrop(
                        w = w,
                        h = h,
                        u = u,
                        rainPhase = rainPhase,
                        floorY = floorY,
                        windKmh = effectiveWeather?.windKmh ?: 12.0
                    )
                }
                AtmosphericCondition.THUNDERSTORM -> {
                    drawThunderstormBackdrop(
                        w = w,
                        h = h,
                        u = u,
                        rainPhase = rainPhase,
                        lightningCycle = lightningCycle,
                        floorY = floorY,
                        windKmh = effectiveWeather?.windKmh ?: 22.0
                    )
                }
                AtmosphericCondition.SNOWFALL -> {
                    drawSnowfallBackdrop(
                        w = w,
                        h = h,
                        u = u,
                        snowPhase = cloudDrift,
                        floorY = floorY
                    )
                }
            }
        }

        // Live Weather Status Badge at Top-Left of animation stage
        if (showLiveBadge) {
            val isCustomPreview = previewConditionIndex != -1
            val cityName = effectiveWeather?.cityName ?: "Local Station"
            val tempDisplay = effectiveWeather?.let { "${it.temperatureC.toInt()}°C" } ?: "--"
            val statusText = if (isCustomPreview) {
                "${activeCondition.iconEmoji} PREVIEW: ${activeCondition.label.uppercase()}"
            } else {
                "${activeCondition.iconEmoji} $cityName • $tempDisplay • ${activeCondition.label.uppercase()}"
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF070F19).copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = activeCondition.accentColor.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable {
                        onCycleConditionPreview?.invoke()
                        previewConditionIndex = (previewConditionIndex + 2) % (AtmosphericCondition.entries.size + 1) - 1
                    }
                    .padding(horizontal = 9.dp, vertical = 3.5.dp)
                    .testTag("weather_atmosphere_badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = statusText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                    color = activeCondition.accentColor
                )
                if (isCustomPreview) {
                    Text(
                        text = "RESET",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.5.sp,
                        color = ValorantCrimson
                    )
                }
            }
        }
    }
}

/**
 * Draws bright golden solar lens flares, rotating sun rays, and floating warm light motes.
 */
private fun DrawScope.drawSunnyBackdrop(
    w: Float,
    h: Float,
    u: Float,
    solarAngle: Float,
    drift: Float,
    sunAlpha: Float = 0.85f
) {
    val sunX = w * 0.82f
    val sunY = h * 0.22f

    // Ambient warm sky aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(KilljoyGold.copy(alpha = 0.28f * sunAlpha), Color.Transparent),
            center = Offset(sunX, sunY),
            radius = w * 0.45f
        ),
        radius = w * 0.45f,
        center = Offset(sunX, sunY)
    )

    // Rotating radiant sun rays
    val rayCount = 10
    val maxRayLen = 28f * u
    for (i in 0 until rayCount) {
        val angle = solarAngle + (i * (2f * PI / rayCount)).toFloat()
        val rayCos = cos(angle)
        val raySin = sin(angle)
        val rayLen = if (i % 2 == 0) maxRayLen else maxRayLen * 0.65f
        drawLine(
            color = KilljoyGold.copy(alpha = (0.35f * sunAlpha)),
            start = Offset(sunX + rayCos * 7f * u, sunY + raySin * 7f * u),
            end = Offset(sunX + rayCos * (7f * u + rayLen), sunY + raySin * (7f * u + rayLen)),
            strokeWidth = 2.4f * u,
            cap = StrokeCap.Round
        )
    }

    // Solar Core Disc
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = sunAlpha), KilljoyGold.copy(alpha = sunAlpha)),
            center = Offset(sunX, sunY),
            radius = 7.5f * u
        ),
        radius = 7.5f * u,
        center = Offset(sunX, sunY)
    )

    // Floating warm golden light dust motes drifting upward
    for (m in 0..7) {
        val moteProg = (drift + m * 0.125f) % 1f
        val mx = (w * 0.15f) + (m * (w * 0.11f)) + sin(moteProg * 6f + m) * 6f * u
        val my = (h * 0.75f) - (moteProg * h * 0.65f)
        val moteAlpha = (sin(moteProg * PI.toFloat()) * 0.65f * sunAlpha).coerceIn(0f, 1f)
        drawCircle(
            color = KilljoyGold.copy(alpha = moteAlpha),
            radius = (1.5f + (m % 3) * 0.6f) * u,
            center = Offset(mx, my)
        )
    }
}

/**
 * Draws serene starry night sky with twinkling constellations and celestial lunar crescent.
 */
private fun DrawScope.drawStarryNightBackdrop(
    w: Float,
    h: Float,
    u: Float,
    drift: Float
) {
    val moonX = w * 0.84f
    val moonY = h * 0.24f

    // Soft celestial lunar glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF60A5FA).copy(alpha = 0.24f), Color.Transparent),
            center = Offset(moonX, moonY),
            radius = 26f * u
        ),
        radius = 26f * u,
        center = Offset(moonX, moonY)
    )

    // Crescent Moon
    drawCircle(
        color = KilljoyGold.copy(alpha = 0.90f),
        radius = 8.5f * u,
        center = Offset(moonX, moonY)
    )
    drawCircle(
        color = Color(0xFF05080E),
        radius = 6.8f * u,
        center = Offset(moonX + 3.2f * u, moonY - 2.2f * u)
    )

    // Twinkling Star Field
    val starCoords = listOf(
        Offset(w * 0.12f, h * 0.18f),
        Offset(w * 0.22f, h * 0.32f),
        Offset(w * 0.35f, h * 0.14f),
        Offset(w * 0.48f, h * 0.26f),
        Offset(w * 0.62f, h * 0.15f),
        Offset(w * 0.72f, h * 0.36f),
        Offset(w * 0.18f, h * 0.48f),
        Offset(w * 0.28f, h * 0.62f),
        Offset(w * 0.58f, h * 0.52f),
        Offset(w * 0.78f, h * 0.58f)
    )

    for ((index, pos) in starCoords.withIndex()) {
        val twinkle = (sin((drift * 2f * PI + index).toFloat()) * 0.5f + 0.5f).coerceIn(0.2f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = twinkle * 0.85f),
            radius = (1.4f + (index % 2) * 0.8f) * u,
            center = pos
        )
    }
}

/**
 * Draws rolling volumetric cloud wave banks drifting across the horizon.
 */
private fun DrawScope.drawCloudLayer(
    w: Float,
    h: Float,
    u: Float,
    drift: Float,
    isDense: Boolean
) {
    val cloudColor = if (isDense) Color(0xFF334155).copy(alpha = 0.42f) else Color(0xFF64748B).copy(alpha = 0.26f)
    val offsetX = (drift * w * 0.6f)

    for (c in 0..2) {
        val cx = ((c * w * 0.4f) + offsetX) % (w * 1.3f) - (w * 0.15f)
        val cy = h * 0.28f + (c * 6f * u)
        val path = Path().apply {
            moveTo(cx, cy)
            quadraticTo(cx + 18f * u, cy - 14f * u, cx + 36f * u, cy)
            quadraticTo(cx + 54f * u, cy - 18f * u, cx + 72f * u, cy)
            quadraticTo(cx + 86f * u, cy - 8f * u, cx + 96f * u, cy + 8f * u)
            lineTo(cx, cy + 8f * u)
            close()
        }
        drawPath(path = path, color = cloudColor)
    }
}

/**
 * Draws atmospheric overcast fog with low-hanging horizontal mist layers.
 */
private fun DrawScope.drawOvercastMistBackdrop(
    w: Float,
    h: Float,
    u: Float,
    drift: Float,
    floorY: Float
) {
    drawCloudLayer(w, h, u, drift, isDense = true)

    // Low-lying fog haze drifting right above floor line
    for (f in 0..2) {
        val fogY = floorY - (4f + f * 5f) * u
        val shift = sin((drift * 2f * PI + f).toFloat()) * 18f * u
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF94A3B8).copy(alpha = 0.28f - f * 0.08f),
                    Color(0xFF94A3B8).copy(alpha = 0.28f - f * 0.08f),
                    Color.Transparent
                )
            ),
            start = Offset(shift, fogY),
            end = Offset(w + shift, fogY),
            strokeWidth = 3.5f * u,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Draws dynamic angled rain streaks and animated floor ripple splashes.
 */
private fun DrawScope.drawRainBackdrop(
    w: Float,
    h: Float,
    u: Float,
    rainPhase: Float,
    floorY: Float,
    windKmh: Double
) {
    val slantX = (windKmh.toFloat() * 0.35f * u).coerceIn(-12f * u, 14f * u)
    val dropCount = 32

    // Falling rain streaks
    for (i in 0 until dropCount) {
        val dropProg = (rainPhase + i * (1f / dropCount)) % 1f
        val startX = (i * (w / dropCount)) + (dropProg * slantX * 2.2f)
        val startY = dropProg * (floorY - 2f * u)
        val dropLen = (12f + (i % 4) * 3f) * u

        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    RadianiteCyan.copy(alpha = 0.05f),
                    RadianiteCyan.copy(alpha = 0.70f)
                ),
                startY = startY,
                endY = startY + dropLen
            ),
            start = Offset(startX, startY),
            end = Offset(startX + slantX * 0.45f, startY + dropLen),
            strokeWidth = 1.4f * u,
            cap = StrokeCap.Round
        )

        // Ground splash ripples on impact near floorY
        if (dropProg > 0.88f) {
            val splashProg = (dropProg - 0.88f) / 0.12f
            val splashRadius = (3.5f + splashProg * 6.5f) * u
            val splashAlpha = (1f - splashProg) * 0.65f
            drawOval(
                color = RadianiteCyan.copy(alpha = splashAlpha),
                topLeft = Offset(startX - splashRadius, floorY - 1.8f * u),
                size = Size(splashRadius * 2f, 2.4f * u),
                style = Stroke(width = 1.1f * u)
            )
        }
    }
}

/**
 * Draws heavy storm rain with electric lightning flash illumination and branching arcs.
 */
private fun DrawScope.drawThunderstormBackdrop(
    w: Float,
    h: Float,
    u: Float,
    rainPhase: Float,
    lightningCycle: Float,
    floorY: Float,
    windKmh: Double
) {
    // Heavy rain backdrop
    drawRainBackdrop(w, h, u, rainPhase, floorY, windKmh + 10.0)

    // Lightning Flash Trigger window (brief sharp pulse)
    val isFlashing = lightningCycle in 0.42f..0.48f || lightningCycle in 0.52f..0.56f
    if (isFlashing) {
        val flashAlpha = if (lightningCycle < 0.48f) 0.32f else 0.22f

        // Ambient sky flash
        drawRect(
            color = Color(0xFF67E8F9).copy(alpha = flashAlpha),
            size = Size(w, h)
        )

        // Electrical Branching Lightning Bolt Arc
        val boltStartX = w * 0.68f
        val boltPath = Path().apply {
            moveTo(boltStartX, 0f)
            lineTo(boltStartX - 8f * u, h * 0.22f)
            lineTo(boltStartX + 12f * u, h * 0.38f)
            lineTo(boltStartX - 6f * u, h * 0.58f)
            lineTo(boltStartX + 8f * u, floorY - 4f * u)
        }
        // Outer violet glow
        drawPath(
            path = boltPath,
            color = Color(0xFFA855F7).copy(alpha = 0.85f),
            style = Stroke(width = 4.2f * u, cap = StrokeCap.Round)
        )
        // White core bolt
        drawPath(
            path = boltPath,
            color = Color.White,
            style = Stroke(width = 2.2f * u, cap = StrokeCap.Round)
        )

        // Ground strike spark ring
        drawCircle(
            color = Color.White,
            radius = 4.5f * u,
            center = Offset(boltStartX + 8f * u, floorY - 4f * u)
        )
    }
}

/**
 * Draws fluttering snowflakes with sinusoidal horizontal drift and ground frost dusting.
 */
private fun DrawScope.drawSnowfallBackdrop(
    w: Float,
    h: Float,
    u: Float,
    snowPhase: Float,
    floorY: Float
) {
    val flakeCount = 26

    for (f in 0 until flakeCount) {
        val flakeProg = (snowPhase * 1.8f + f * (1f / flakeCount)) % 1f
        val baseX = (f * (w / flakeCount))
        val wobbleX = baseX + sin((flakeProg * 4f * PI + f).toFloat()) * 9f * u
        val flakeY = flakeProg * (floorY - 2f * u)
        val flakeRadius = (1.6f + (f % 4) * 0.8f) * u
        val flakeAlpha = (0.35f + (f % 3) * 0.22f).coerceIn(0.2f, 0.95f)

        // Snowflake core
        drawCircle(
            color = Color.White.copy(alpha = flakeAlpha),
            radius = flakeRadius,
            center = Offset(wobbleX, flakeY)
        )

        // Cross-arm glint on larger flakes
        if (f % 3 == 0) {
            val armLen = flakeRadius * 1.6f
            drawLine(
                color = Color.White.copy(alpha = flakeAlpha * 0.75f),
                start = Offset(wobbleX - armLen, flakeY),
                end = Offset(wobbleX + armLen, flakeY),
                strokeWidth = 1f * u
            )
            drawLine(
                color = Color.White.copy(alpha = flakeAlpha * 0.75f),
                start = Offset(wobbleX, flakeY - armLen),
                end = Offset(wobbleX, flakeY + armLen),
                strokeWidth = 1f * u
            )
        }
    }

    // Frost accumulation dusting on floor line
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.50f),
                Color.Transparent
            )
        ),
        start = Offset(w * 0.1f, floorY - 0.8f * u),
        end = Offset(w * 0.9f, floorY - 0.8f * u),
        strokeWidth = 1.6f * u,
        cap = StrokeCap.Round
    )
}
