package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.zIndex
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.hardware.BatteryTelemetry
import com.example.data.weather.DualCityWeather
import com.example.data.weather.WeatherRepository
import com.example.data.weather.WeatherStationData
import com.example.ui.theme.ChargingEmerald
import com.example.ui.theme.DockAmber
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KilljoyGold
import com.example.ui.theme.RadianiteCyan
import com.example.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Anker Nano 10-in-1 (VALORANT Limited Edition) Smart Display Screen.
 * - Portrait mode: AI Toy Stage on top, Date & Time in the middle, Dual-City Weather row at the bottom.
 * - Landscape mode: City 1 Weather on the Left End, AI Toy Animation running on top of Date & Time in the Center,
 *   and City 2 Weather on the Right End.
 */
@Composable
fun StandByClockCard(
    currentTimeMillis: Long,
    clockStyle: String,
    agentThemeName: String,
    use24Hour: Boolean,
    forceLandscapeMode: Boolean,
    isNightTimeOnly: Boolean = false,
    primaryCityName: String = "Poitiers",
    secondaryCityName: String = "Pittsburgh",
    showToyInsideCard: Boolean = true,
    fillAvailableHeight: Boolean = false,
    showPortHubTelemetry: Boolean,
    battery: BatteryTelemetry,
    dualCityWeather: DualCityWeather,
    schedule: List<UnifiedScheduleItem>,
    isAgentThinking: Boolean,
    manualToyActivity: AiToyActivity?,
    onSelectManualToyActivity: (AiToyActivity?) -> Unit,
    onQuickAddCalendarStatus: (AiToyActivity) -> Unit,
    chronoState: ActiveChronoHudState = ActiveChronoHudState(),
    onSnoozeChrono: (AiToyActivity) -> Unit = {},
    onStopChrono: (AiToyActivity) -> Unit = {},
    onStartQuickChrono: (AiToyActivity) -> Unit = {},
    hapticsEnabled: Boolean = true,
    onRefreshWeather: () -> Unit,
    onCycleClockStyle: () -> Unit,
    onToggleLandscapeMode: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var turboPreview by rememberSaveable { mutableStateOf(false) }

    val primaryPreset = WeatherRepository.findCityPreset(primaryCityName, WeatherRepository.POITIERS_PRESET)
    val secondaryPreset = WeatherRepository.findCityPreset(secondaryCityName, WeatherRepository.PITTSBURGH_PRESET)
    val primaryCountry = dualCityWeather.primaryCity?.countryCode?.ifBlank { primaryPreset.countryCode } ?: primaryPreset.countryCode
    val secondaryCountry = dualCityWeather.secondaryCity?.countryCode?.ifBlank { secondaryPreset.countryCode } ?: secondaryPreset.countryCode
    val primaryCityLabel = "${primaryPreset.name.uppercase(Locale.getDefault())}, $primaryCountry"
    val secondaryCityLabel = "${secondaryPreset.name.uppercase(Locale.getDefault())}, $secondaryCountry"

    val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
    val hour12 = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val displayHour = if (use24Hour) "%02d".format(hour24) else "%02d".format(hour12)
    val displayMinute = "%02d".format(calendar.get(Calendar.MINUTE))
    val displaySecond = "%02d".format(calendar.get(Calendar.SECOND))
    val secondsFraction = calendar.get(Calendar.SECOND) / 60f
    val amPm = if (use24Hour) "24H" else if (hour24 < 12) "AM" else "PM"
    val fullDateFormatted = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        .format(Date(currentTimeMillis)).uppercase(Locale.getDefault())

    // 0 = normal clock, 1 = night-time-only full screen; animated in between (see NightModeTransition.kt).
    val nightProgress = rememberNightProgress(isNightTimeOnly)
    val dockViewModel = com.example.ui.LocalDockViewModel.current

    Box(
        modifier = (if (fillAvailableHeight || nightProgress > 0.001f) modifier.fillMaxSize() else modifier.fillMaxWidth())
            .testTag("standby_clock_card")
    ) {
        if (nightProgress > 0.001f) {
        Box(modifier = Modifier.fillMaxSize().zIndex(1f).nightClockLayer(nightProgress)) {
            // NIGHT TIME-ONLY FULL SCREEN MODE (20-FT HIGH-CONTRAST DISPLAY):
            // Hides AI Toy animations, Primary & Secondary weather, date, and telemetry bar,
            // displaying ONLY the giant, ultra-bold clock centered in full screen.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF03060A))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("night_time_only_fullscreen"),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = clockStyle,
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { it / 5 }) togetherWith
                            (fadeOut(tween(180)) + slideOutVertically { -it / 5 })
                    },
                    label = "night_time_only_clock_mode"
                ) { targetStyle ->
                    when (targetStyle) {
                        "MATRIX" -> TensorMatrixClockDisplay(
                            hour = displayHour,
                            minute = displayMinute,
                            second = displaySecond,
                            amPm = amPm,
                            isAgentThinking = isAgentThinking,
                            isFullScreenTimeOnly = true,
                            onCycleMode = onCycleClockStyle
                        )
                        "CHRONO" -> ChronoDialClockDisplay(
                            hour24 = hour24,
                            minute = calendar.get(Calendar.MINUTE),
                            second = calendar.get(Calendar.SECOND),
                            displayHour = displayHour,
                            displayMinute = displayMinute,
                            amPm = amPm,
                            batteryPercent = battery.levelPercent,
                            isFullScreenTimeOnly = true,
                            onCycleMode = onCycleClockStyle
                        )
                        else -> FlipClockDisplay(
                            hour = displayHour,
                            minute = displayMinute,
                            second = displaySecond,
                            amPm = amPm,
                            secondsFraction = secondsFraction,
                            isFullScreenTimeOnly = true,
                            onCycleMode = onCycleClockStyle
                        )
                    }
                }
            }
        } // end night layer
        }
        if (nightProgress < 0.999f) {
        Box(
            modifier = (if (fillAvailableHeight || nightProgress > 0.001f) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .nightDayLayer(nightProgress)
        ) {
        if (fillAvailableHeight) {
            // LANDSCAPE MODE:
            // Left End = Primary City Weather | Center = Animation on top of Date & Time | Right End = Secondary City Weather
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0E1722),
                                Color(0xFF060A10)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("dual_city_weather_row"),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT END: Primary City Weather
                LandscapeEndWeatherCard(
                    cityLabel = primaryCityLabel,
                    timeZoneId = dualCityWeather.primaryCity?.timeZoneId ?: primaryPreset.timeZoneId,
                    currentTimeMillis = currentTimeMillis,
                    use24Hour = use24Hour,
                    weather = dualCityWeather.primaryCity,
                    accentColor = RadianiteCyan,
                    onClick = onRefreshWeather,
                    modifier = Modifier
                        .weight(0.24f)
                        .fillMaxHeight()
                        .testTag("weather_card_poitiers")
                )

                // CENTER: AI Toy Animation running on top of large Date & Time
                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.52f)
                    ) {
                        if (showToyInsideCard) {
                            InteractiveAiToyTopStage(
                                currentTimeMillis = currentTimeMillis,
                                schedule = schedule,
                                isAgentThinking = isAgentThinking,
                                manualActivity = manualToyActivity,
                                onSelectManualActivity = onSelectManualToyActivity,
                                onQuickAddCalendarStatus = onQuickAddCalendarStatus,
                                chronoState = chronoState,
                                onSnoozeChrono = onSnoozeChrono,
                                onStopChrono = onStopChrono,
                                onStartQuickChrono = onStartQuickChrono,
                                fillAvailableHeight = true,
                                hapticsEnabled = hapticsEnabled,
                                currentWeather = dualCityWeather.forAnimation(com.example.ui.LocalWeatherAnimationCity.current),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.48f)
                            .onGloballyPositioned { coords ->
                                dockViewModel?.updateClockProtectedBounds(coords.boundsInWindow())
                            }
                            .testTag("protected_clock_bounds_landscape"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = fullDateFormatted,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCycleClockStyle() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("cycle_clock_mode_button")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        AnimatedContent(
                            targetState = clockStyle,
                            transitionSpec = {
                                (fadeIn(tween(240)) + slideInVertically(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                ) { it / 5 }) togetherWith
                                    (fadeOut(tween(180)) + slideOutVertically { -it / 5 })
                            },
                            label = "anker_smart_screen_mode_landscape"
                        ) { targetStyle ->
                            when (targetStyle) {
                                "MATRIX" -> TensorMatrixClockDisplay(
                                    hour = displayHour,
                                    minute = displayMinute,
                                    second = displaySecond,
                                    amPm = amPm,
                                    isAgentThinking = isAgentThinking,
                                    onCycleMode = onCycleClockStyle
                                )
                                "CHRONO" -> ChronoDialClockDisplay(
                                    hour24 = hour24,
                                    minute = calendar.get(Calendar.MINUTE),
                                    second = calendar.get(Calendar.SECOND),
                                    displayHour = displayHour,
                                    displayMinute = displayMinute,
                                    amPm = amPm,
                                    batteryPercent = battery.levelPercent,
                                    onCycleMode = onCycleClockStyle
                                )
                                else -> FlipClockDisplay(
                                    hour = displayHour,
                                    minute = displayMinute,
                                    second = displaySecond,
                                    amPm = amPm,
                                    secondsFraction = secondsFraction,
                                    onCycleMode = onCycleClockStyle
                                )
                            }
                        }
                    }
                }

                // RIGHT END: Secondary City Weather
                LandscapeEndWeatherCard(
                    cityLabel = secondaryCityLabel,
                    timeZoneId = dualCityWeather.secondaryCity?.timeZoneId ?: secondaryPreset.timeZoneId,
                    currentTimeMillis = currentTimeMillis,
                    use24Hour = use24Hour,
                    weather = dualCityWeather.secondaryCity,
                    accentColor = KilljoyGold,
                    onClick = onRefreshWeather,
                    modifier = Modifier
                        .weight(0.24f)
                        .fillMaxHeight()
                        .testTag("weather_card_pittsburgh")
                )
            }
        } else {
            // PORTRAIT MODE:
            // Vertically centered AI Toy Animation, Date & Time, and Dual-City Weather Row
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0E1722),
                                Color(0xFF060A10)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    if (showToyInsideCard) {
                        InteractiveAiToyTopStage(
                            currentTimeMillis = currentTimeMillis,
                            schedule = schedule,
                            isAgentThinking = isAgentThinking,
                            manualActivity = manualToyActivity,
                            onSelectManualActivity = onSelectManualToyActivity,
                            onQuickAddCalendarStatus = onQuickAddCalendarStatus,
                            chronoState = chronoState,
                            onSnoozeChrono = onSnoozeChrono,
                            onStopChrono = onStopChrono,
                            onStartQuickChrono = onStartQuickChrono,
                            fillAvailableHeight = true,
                            hapticsEnabled = hapticsEnabled,
                            currentWeather = dualCityWeather.forAnimation(com.example.ui.LocalWeatherAnimationCity.current),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .onGloballyPositioned { coords ->
                            dockViewModel?.updateClockProtectedBounds(coords.boundsInWindow())
                        }
                        .testTag("protected_clock_bounds_portrait"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = fullDateFormatted,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCycleClockStyle() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .testTag("cycle_clock_mode_button")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedContent(
                        targetState = clockStyle,
                        transitionSpec = {
                            (fadeIn(tween(240)) + slideInVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) { it / 5 }) togetherWith
                                (fadeOut(tween(180)) + slideOutVertically { -it / 5 })
                        },
                        label = "anker_smart_screen_mode"
                    ) { targetStyle ->
                        when (targetStyle) {
                            "MATRIX" -> TensorMatrixClockDisplay(
                                hour = displayHour,
                                minute = displayMinute,
                                second = displaySecond,
                                amPm = amPm,
                                isAgentThinking = isAgentThinking,
                                onCycleMode = onCycleClockStyle
                            )
                            "CHRONO" -> ChronoDialClockDisplay(
                                hour24 = hour24,
                                minute = calendar.get(Calendar.MINUTE),
                                second = calendar.get(Calendar.SECOND),
                                displayHour = displayHour,
                                displayMinute = displayMinute,
                                amPm = amPm,
                                batteryPercent = battery.levelPercent,
                                onCycleMode = onCycleClockStyle
                            )
                            else -> FlipClockDisplay(
                                hour = displayHour,
                                minute = displayMinute,
                                second = displaySecond,
                                amPm = amPm,
                                secondsFraction = secondsFraction,
                                onCycleMode = onCycleClockStyle
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                DualCityWeatherRow(
                    currentTimeMillis = currentTimeMillis,
                    use24Hour = use24Hour,
                    primaryCityLabel = primaryCityLabel,
                    primaryTimeZoneId = dualCityWeather.primaryCity?.timeZoneId ?: primaryPreset.timeZoneId,
                    secondaryCityLabel = secondaryCityLabel,
                    secondaryTimeZoneId = dualCityWeather.secondaryCity?.timeZoneId ?: secondaryPreset.timeZoneId,
                    primaryCity = dualCityWeather.primaryCity,
                    secondaryCity = dualCityWeather.secondaryCity,
                    onRefresh = onRefreshWeather
                )

                if (showPortHubTelemetry) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AnkerPowerFlowTelemetryBar(
                        battery = battery,
                        turboPreview = turboPreview,
                        onToggleTurboPreview = { turboPreview = !turboPreview }
                    )
                }
            }
        }
        } // end day layer
        } // end if day visible

        // Dusk / dawn veil and greeting, above both layers.
        NightTransitionVeil(
            p = nightProgress,
            towardNight = isNightTimeOnly,
            modifier = Modifier.matchParentSize().zIndex(2f)
        )
        NightGreetingCaption(
            isNight = isNightTimeOnly,
            catName = com.example.ui.LocalCompanionAppearance.current.let { if (it.useCat) it.name else null },
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(3f)
        )
    }
}

/**
 * Full-height vertical weather pillar placed at the Left End and Right End
 * in Landscape Dock Mode.
 */
@Composable
private fun LandscapeEndWeatherCard(
    cityLabel: String,
    timeZoneId: String,
    currentTimeMillis: Long,
    use24Hour: Boolean,
    weather: WeatherStationData?,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    val cityTimeStr = SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(timeZoneId)
    }.format(Date(currentTimeMillis))

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: City Label & Local Time
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cityLabel,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cityTimeStr,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Middle: Big Weather Icon & Large Temperature
        if (weather != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = weatherIconFor(weather, currentTimeMillis),
                    contentDescription = weather.conditionLabel,
                    tint = accentColor,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${kotlin.math.round(weather.temperatureC).toInt()}°C",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Bottom: High / Low & Condition + Humidity
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "H:${"%.0f".format(weather.highC)}°  L:${"%.0f".format(weather.lowC)}°",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${weather.conditionLabel} • ${weather.humidityPercent}% RH",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Syncing…",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DualCityWeatherRow(
    currentTimeMillis: Long,
    use24Hour: Boolean,
    primaryCityLabel: String,
    primaryTimeZoneId: String,
    secondaryCityLabel: String,
    secondaryTimeZoneId: String,
    primaryCity: WeatherStationData?,
    secondaryCity: WeatherStationData?,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dual_city_weather_row"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CityWeatherMiniCard(
            cityLabel = primaryCityLabel,
            timeZoneId = primaryTimeZoneId,
            currentTimeMillis = currentTimeMillis,
            use24Hour = use24Hour,
            weather = primaryCity,
            accentColor = RadianiteCyan,
            onClick = onRefresh,
            modifier = Modifier
                .weight(1f)
                .testTag("weather_card_poitiers")
        )

        CityWeatherMiniCard(
            cityLabel = secondaryCityLabel,
            timeZoneId = secondaryTimeZoneId,
            currentTimeMillis = currentTimeMillis,
            use24Hour = use24Hour,
            weather = secondaryCity,
            accentColor = KilljoyGold,
            onClick = onRefresh,
            modifier = Modifier
                .weight(1f)
                .testTag("weather_card_pittsburgh")
        )
    }
}

@Composable
private fun CityWeatherMiniCard(
    cityLabel: String,
    timeZoneId: String,
    currentTimeMillis: Long,
    use24Hour: Boolean,
    weather: WeatherStationData?,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    val cityTimeStr = SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(timeZoneId)
    }.format(Date(currentTimeMillis))

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = cityLabel,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = accentColor
            )
            Text(
                text = cityTimeStr,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (weather != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = weatherIconFor(weather, currentTimeMillis),
                        contentDescription = weather.conditionLabel,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${kotlin.math.round(weather.temperatureC).toInt()}°C",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "H:${"%.0f".format(weather.highC)}° L:${"%.0f".format(weather.lowC)}°",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${weather.conditionLabel} • ${weather.humidityPercent}% RH",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Syncing weather…",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FlipClockDisplay(
    hour: String,
    minute: String,
    second: String,
    amPm: String,
    secondsFraction: Float,
    isFullScreenTimeOnly: Boolean = false,
    onCycleMode: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCycleMode() },
        contentAlignment = Alignment.Center
    ) {
        val isWideLayout = maxWidth >= 500.dp

        if (isFullScreenTimeOnly) {
            // 20-FOOT FULL-SCREEN TIME-ONLY DISPLAY:
            // Ultra-large, ultra-bold numerals filling the screen so time is effortless to read from 20+ ft away.
            val digitFontSizeSp = when {
                maxWidth >= 680.dp -> 168
                isWideLayout -> 144
                else -> 118
            }
            val strokePx = if (isWideLayout) 7.5f else 6f

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlipDigitCard(
                        digits = hour,
                        fontSizeSp = digitFontSizeSp,
                        strokeWidthPx = strokePx,
                        verticalPaddingDp = if (isWideLayout) 18 else 14,
                        modifier = Modifier.weight(1f)
                    )

                    UltraBoldClockText(
                        text = ":",
                        fontSizeSp = (digitFontSizeSp * 0.78f).toInt(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidthPx = strokePx * 0.85f,
                        letterSpacingSp = 0f
                    )

                    FlipDigitCard(
                        digits = minute,
                        fontSizeSp = digitFontSizeSp,
                        strokeWidthPx = strokePx,
                        verticalPaddingDp = if (isWideLayout) 18 else 14,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = amPm,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF060A10),
                        modifier = Modifier.border(
                            width = 2.dp,
                            color = RadianiteCyan.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    ) {
                        Text(
                            text = ":$second",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            color = RadianiteCyan,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            // STANDARD MODE (ALSO BIGGER & BOLDER FOR 20-FT VISIBILITY):
            val normalFontSizeSp = if (isWideLayout) 84 else 82
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlipDigitCard(
                        digits = hour,
                        fontSizeSp = normalFontSizeSp,
                        strokeWidthPx = 4.5f,
                        verticalPaddingDp = 8,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    FlipDigitCard(
                        digits = minute,
                        fontSizeSp = normalFontSizeSp,
                        strokeWidthPx = 4.5f,
                        verticalPaddingDp = 8,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = amPm,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF060A10),
                            modifier = Modifier.border(
                                width = 1.4.dp,
                                color = RadianiteCyan.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = ":$second",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 21.sp,
                                color = RadianiteCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipDigitCard(
    digits: String,
    fontSizeSp: Int = 82,
    strokeWidthPx: Float = 4.5f,
    verticalPaddingDp: Int = 8,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF060A11))
            .border(
                width = 1.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(vertical = verticalPaddingDp.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedFlipString(
            text = digits,
            fontSizeSp = fontSizeSp,
            strokeWidthPx = strokeWidthPx
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(Color.Black.copy(alpha = 0.92f))
        )
    }
}

@Composable
private fun AnimatedFlipString(
    text: String,
    fontSizeSp: Int,
    strokeWidthPx: Float = 4.5f
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { height -> height / 2 } + fadeIn(tween(180)))
                .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(150)))
                .using(SizeTransform(clip = false))
        },
        label = "animated_flip_digits"
    ) { value ->
        UltraBoldClockText(
            text = value,
            fontSizeSp = fontSizeSp,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = SpaceGroteskFontFamily,
            strokeWidthPx = strokeWidthPx,
            letterSpacingSp = -2.5f
        )
    }
}

/**
 * Renders ultra-bold, heavy-weight clock numerals with a rounded stroke reinforcement
 * and subtle luminance glow so the time can be read clearly from 20+ feet away.
 */
@Composable
private fun UltraBoldClockText(
    text: String,
    fontSizeSp: Int,
    color: Color,
    fontFamily: FontFamily = SpaceGroteskFontFamily,
    strokeWidthPx: Float = 4.5f,
    letterSpacingSp: Float = -2f,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Black,
            fontSize = fontSizeSp.sp,
            letterSpacing = letterSpacingSp.sp,
            color = color,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round, cap = StrokeCap.Round),
                shadow = Shadow(
                    color = color.copy(alpha = 0.42f),
                    offset = Offset(0f, 0f),
                    blurRadius = 16f
                )
            )
        )
        Text(
            text = text,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Black,
            fontSize = fontSizeSp.sp,
            letterSpacing = letterSpacingSp.sp,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TensorMatrixClockDisplay(
    hour: String,
    minute: String,
    second: String,
    amPm: String,
    isAgentThinking: Boolean,
    isFullScreenTimeOnly: Boolean = false,
    onCycleMode: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tensor_pulse")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAgentThinking) 800 else 2400,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF060A10))
            .border(
                width = if (isFullScreenTimeOnly) 1.8.dp else 1.2.dp,
                color = secondaryColor.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onCycleMode() }
            .padding(if (isFullScreenTimeOnly) 20.dp else 14.dp)
    ) {
        val isWide = maxWidth >= 500.dp
        val mainFontSp = when {
            isFullScreenTimeOnly && isWide -> 142
            isFullScreenTimeOnly -> 98
            isWide -> 72
            else -> 66
        }
        val secFontSp = if (isFullScreenTimeOnly) 36 else 24

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isFullScreenTimeOnly) Arrangement.Center else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    UltraBoldClockText(
                        text = "$hour:$minute",
                        fontSizeSp = mainFontSp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = JetBrainsMonoFontFamily,
                        strokeWidthPx = if (isFullScreenTimeOnly) 6f else 4f,
                        letterSpacingSp = -3f
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ".$second",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = secFontSp.sp,
                        color = secondaryColor,
                        modifier = Modifier.padding(bottom = if (isFullScreenTimeOnly) 14.dp else 8.dp)
                    )
                }
                if (!isFullScreenTimeOnly) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAgentThinking) "AGENT ACTIVE" else "240HZ • $amPm",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                    }
                }
            }

            if (isFullScreenTimeOnly) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = amPm,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = primaryColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFullScreenTimeOnly) 52.dp else 32.dp)
            ) {
                val barCount = 34
                val spacing = size.width / barCount
                val centerY = size.height / 2f
                for (i in 0 until barCount) {
                    val normalized = i.toFloat() / barCount
                    val wave = sin(phase + i * 0.42f) * cos(phase * 0.6f + i * 0.25f)
                    val barHeight = (size.height * 0.2f) +
                        (size.height * 0.75f * abs(wave)).coerceAtMost(size.height * 0.92f)
                    val barColor = if (normalized < 0.45f) primaryColor else secondaryColor
                    drawLine(
                        color = barColor.copy(alpha = 0.9f),
                        start = Offset(x = i * spacing + spacing / 2f, y = centerY - barHeight / 2f),
                        end = Offset(x = i * spacing + spacing / 2f, y = centerY + barHeight / 2f),
                        strokeWidth = (spacing * 0.54f).coerceAtLeast(4f),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun ChronoDialClockDisplay(
    hour24: Int,
    minute: Int,
    second: Int,
    displayHour: String,
    displayMinute: String,
    amPm: String,
    batteryPercent: Int,
    isFullScreenTimeOnly: Boolean = false,
    onCycleMode: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val infiniteOrbit = rememberInfiniteTransition(label = "prime_dial_orbit")
    val orbitAngle by infiniteOrbit.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    val dialSize = if (isFullScreenTimeOnly) 230.dp else 156.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCycleMode() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(dialSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = min(size.width, size.height) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = outlineColor.copy(alpha = 0.25f),
                    radius = radius - 4f,
                    center = center,
                    style = Stroke(width = if (isFullScreenTimeOnly) 8f else 5f)
                )
                drawArc(
                    color = ChargingEmerald,
                    startAngle = -90f,
                    sweepAngle = (batteryPercent / 100f) * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - (radius - 4f), center.y - (radius - 4f)),
                    size = Size((radius - 4f) * 2f, (radius - 4f) * 2f),
                    style = Stroke(width = if (isFullScreenTimeOnly) 8f else 5f, cap = StrokeCap.Round)
                )

                val innerRingR = radius - 16f
                drawCircle(
                    color = outlineColor.copy(alpha = 0.35f),
                    radius = innerRingR,
                    center = center,
                    style = Stroke(width = if (isFullScreenTimeOnly) 9f else 6f)
                )
                val sweepAngle = (second / 60f) * 360f
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRingR, center.y - innerRingR),
                    size = Size(innerRingR * 2f, innerRingR * 2f),
                    style = Stroke(width = if (isFullScreenTimeOnly) 9f else 6f, cap = StrokeCap.Round)
                )

                val orbitRad = Math.toRadians(orbitAngle.toDouble())
                drawCircle(
                    color = DockAmber,
                    radius = if (isFullScreenTimeOnly) 6f else 4f,
                    center = Offset(
                        x = center.x + ((radius - 4f) * cos(orbitRad)).toFloat(),
                        y = center.y + ((radius - 4f) * sin(orbitRad)).toFloat()
                    )
                )

                for (h in 0 until 12) {
                    val angleRad = Math.toRadians((h * 30.0) - 90.0)
                    val tickInner = innerRingR - 14f
                    val tickOuter = innerRingR - 5f
                    drawLine(
                        color = if (h % 3 == 0) primaryColor else outlineColor,
                        start = Offset(
                            x = center.x + (tickInner * cos(angleRad)).toFloat(),
                            y = center.y + (tickInner * sin(angleRad)).toFloat()
                        ),
                        end = Offset(
                            x = center.x + (tickOuter * cos(angleRad)).toFloat(),
                            y = center.y + (tickOuter * sin(angleRad)).toFloat()
                        ),
                        strokeWidth = if (h % 3 == 0) 5f else 2.5f,
                        cap = StrokeCap.Round
                    )
                }

                val hourAngle = Math.toRadians(((hour24 % 12 + minute / 60.0) * 30.0) - 90.0)
                drawLine(
                    color = primaryColor,
                    start = center,
                    end = Offset(
                        x = center.x + ((radius * 0.38f) * cos(hourAngle)).toFloat(),
                        y = center.y + ((radius * 0.38f) * sin(hourAngle)).toFloat()
                    ),
                    strokeWidth = if (isFullScreenTimeOnly) 10f else 7f,
                    cap = StrokeCap.Round
                )

                val minAngle = Math.toRadians((minute * 6.0) - 90.0)
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.95f),
                    start = center,
                    end = Offset(
                        x = center.x + ((radius * 0.56f) * cos(minAngle)).toFloat(),
                        y = center.y + ((radius * 0.56f) * sin(minAngle)).toFloat()
                    ),
                    strokeWidth = if (isFullScreenTimeOnly) 7f else 5f,
                    cap = StrokeCap.Round
                )

                drawCircle(color = secondaryColor, radius = if (isFullScreenTimeOnly) 8f else 5.5f, center = center)
            }

            if (!isFullScreenTimeOnly) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF080D16).copy(alpha = 0.94f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .border(
                            width = 1.4.dp,
                            color = primaryColor.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Text(
                        text = "$displayHour:$displayMinute $amPm",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (isFullScreenTimeOnly) {
            Spacer(modifier = Modifier.height(14.dp))
            UltraBoldClockText(
                text = "$displayHour:$displayMinute",
                fontSizeSp = 88,
                color = onSurfaceColor,
                fontFamily = SpaceGroteskFontFamily,
                strokeWidthPx = 5.5f,
                letterSpacingSp = -2.5f
            )
            Text(
                text = "$amPm • :${"%02d".format(second)}",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                color = primaryColor
            )
        }
    }
}

/**
 * Maps WMO weather codes (Open-Meteo) to an icon, with a moon for clear nights in the city's
 * own time zone. Previously only "sun" (codes 0-2) or "cloud" were shown, so partly cloudy,
 * rain, snow and storms all looked the same.
 */
private fun weatherIconFor(
    weather: WeatherStationData,
    currentTimeMillis: Long
): androidx.compose.ui.graphics.vector.ImageVector {
    val isNight = !isDaytimeForWeather(weather, currentTimeMillis)
    return when (weather.weatherCode) {
        0, 1 -> if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny
        2 -> if (isNight) Icons.Default.NightsStay else Icons.Default.FilterDrama
        3, 45, 48 -> Icons.Default.Cloud
        in 51..67, in 80..82 -> Icons.Default.Umbrella
        in 71..77, 85, 86 -> Icons.Default.AcUnit
        in 95..99 -> Icons.Default.Thunderstorm
        else -> Icons.Default.Cloud
    }
}
