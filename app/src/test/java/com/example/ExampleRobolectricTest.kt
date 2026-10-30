package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.NativeCalendarReader
import com.example.data.weather.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name from context and verify provider classification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart Dock", appName)

        assertEquals(
            AccountProviderBadge.GMAIL,
            NativeCalendarReader.classifyProvider("user@gmail.com", "com.google", "Primary")
        )
        assertEquals(
            AccountProviderBadge.OUTLOOK,
            NativeCalendarReader.classifyProvider("work@outlook.com", "com.microsoft.office.outlook", "Calendar")
        )
        assertEquals(
            "Clear Sky",
            WeatherRepository.mapWmoCodeToLabel(0)
        )
    }
}
