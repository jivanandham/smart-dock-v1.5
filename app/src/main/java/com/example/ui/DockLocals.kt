package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import com.example.ui.components.CatCoat
import kotlinx.coroutines.delay

/** How the companion should look, provided once at the top of the UI (app and screensaver). */
data class CompanionAppearance(
    val useCat: Boolean = true,
    val coat: CatCoat = CatCoat.ORANGE_TABBY,
    val name: String = "Mochi",
    /** Pet twin override (null = use the preset coat). */
    val palette: com.example.ui.components.CatPalette? = null,
    val markings: com.example.ui.components.CatMarkings = com.example.ui.components.CatMarkings()
)

/** True when Premium is unlocked. */
val LocalIsPremium = compositionLocalOf { false }

val LocalCompanionAppearance = compositionLocalOf { CompanionAppearance() }

/** "PRIMARY" or "SECONDARY": which city's weather drives the animated sky behind the companion. */
val LocalWeatherAnimationCity = compositionLocalOf { "PRIMARY" }

/** Gives deep components (e.g. alarm rows, the companion stage) access to actions without prop drilling. */
val LocalDockViewModel = staticCompositionLocalOf<DockViewModel?> { null }

/** Centralized ViewModel managing hardware camera activity ('Active' 100% brightness vs 'Inactive' fading to 0%). */
val LocalCameraStateViewModel = staticCompositionLocalOf<CameraStateViewModel?> { null }

/**
 * OLED burn-in protection: shifts content by a few pixels in a slow loop (new position every
 * 2 minutes, never more than [maxPx] from the origin). Invisible to the user, but static pixels
 * no longer sit in exactly the same place for hours.
 */
@Composable
fun rememberBurnInOffset(enabled: Boolean, maxPx: Int = 6): IntOffset {
    val offset by produceState(IntOffset.Zero, enabled) {
        if (!enabled) {
            value = IntOffset.Zero
            return@produceState
        }
        // Lissajous-like path so the shift covers an area instead of a line.
        val xs = intArrayOf(0, 3, 6, 4, 1, -2, -5, -6, -3, 0, 2, 5)
        val ys = intArrayOf(0, -2, 1, 4, 6, 3, 0, -3, -6, -4, -1, 2)
        var i = (System.currentTimeMillis() / 120_000L % xs.size).toInt()
        while (true) {
            value = IntOffset(xs[i].coerceIn(-maxPx, maxPx), ys[i].coerceIn(-maxPx, maxPx))
            delay(120_000L)
            i = (i + 1) % xs.size
        }
    }
    return offset
}
