package com.example

import com.example.data.preferences.DockSettings
import com.example.pettwin.PetTwin
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PremiumTwinKidsTest {

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 3, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun petTwin_jsonRoundTrip() {
        val twin = PetTwin(
            enabled = true, species = "cat", baseColor = 0xFF2A2724.toInt(), secondaryColor = 0xFFE08A3C.toInt(),
            eyeColor = 0xFF9DB84A.toInt(), stripes = 0.2f, whiteChest = true, whiteSocks = true,
            tuxedo = false, darkPoints = false, patches = true, name = "Pixel"
        )
        assertEquals(twin, PetTwin.fromJson(twin.toJson()))
        assertNull(PetTwin.fromJson("not json"))
    }

    @Test
    fun petTwin_patchesNeedASecondColour() {
        assertFalse(PetTwin(patches = true, secondaryColor = null).markings.patches)
        assertTrue(PetTwin(patches = true, secondaryColor = 0xFF000000.toInt()).markings.patches)
    }

    @Test
    fun kidsPhase_sleepThenWakeWindow() {
        val s = DockSettings(kidsModeEnabled = true, kidsBedHour = 19, kidsBedMinute = 30, kidsWakeHour = 7, kidsWakeMinute = 0)
        assertEquals("SLEEP", s.kidsPhase(at(20)))
        assertEquals("SLEEP", s.kidsPhase(at(6, 59)))
        assertEquals("WAKE", s.kidsPhase(at(7, 10)))
        assertNull(s.kidsPhase(at(8)))
        assertNull(s.kidsPhase(at(15)))
        assertNull(DockSettings(kidsModeEnabled = false).kidsPhase(at(20)))
    }
}
