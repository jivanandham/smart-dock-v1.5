package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun weatherAtmosphere_resolvesRainCorrectly() {
    val condition = com.example.ui.components.resolveAtmosphericCondition(61, isDaytime = true)
    assertEquals(com.example.ui.components.AtmosphericCondition.RAIN_SHOWERS, condition)
  }

  @Test
  fun weatherAtmosphere_resolvesSunAndNightCorrectly() {
    val daySun = com.example.ui.components.resolveAtmosphericCondition(0, isDaytime = true)
    val nightClear = com.example.ui.components.resolveAtmosphericCondition(0, isDaytime = false)
    assertEquals(com.example.ui.components.AtmosphericCondition.SUNNY_CLEAR, daySun)
    assertEquals(com.example.ui.components.AtmosphericCondition.STARRY_NIGHT, nightClear)
  }

  @Test
  fun weatherAtmosphere_resolvesThunderstormAndSnow() {
    val storm = com.example.ui.components.resolveAtmosphericCondition(95, isDaytime = true)
    val snow = com.example.ui.components.resolveAtmosphericCondition(71, isDaytime = true)
    assertEquals(com.example.ui.components.AtmosphericCondition.THUNDERSTORM, storm)
    assertEquals(com.example.ui.components.AtmosphericCondition.SNOWFALL, snow)
  }
}
