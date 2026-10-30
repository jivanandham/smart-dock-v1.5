package com.example.ui.util

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Premium Haptic Feedback Helper for Smart Dock interactions.
 * Delivers crisp, subtle physical vibrations for page gestures, schedule toggles,
 * alarm controls, and chronometer actions.
 */
class DockHapticFeedback(
    private val context: Context,
    private val composeHaptics: HapticFeedback,
    var isEnabled: Boolean = true
) {
    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Subtle micro-tick for page swipe snapping, tab switching, and carousel scrolling.
     */
    fun tick() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(10L)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Throwable) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Standard tactile click for button presses, quick schedule presets, and chips.
     */
    fun click() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(18L)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Dual micro-pulse for toggle switches and mode activations.
     */
    fun toggle() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 14, 40, 14), intArrayOf(0, 160, 0, 190), -1)
                )
            } else if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(25L)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Confirmation buzz for alarm saving, setting persistence, and successful actions.
     */
    fun success() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 20, 60, 35), intArrayOf(0, 180, 0, 240), -1)
                )
            } else if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(40L)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Heavy tactile alert for alarm ringing or timer completion.
     */
    fun alert() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(60L)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Tactile micro-haptic purr for companion robot pet interactions.
     * Generates a cascading, playful harmonic vibration waveform.
     */
    fun purr() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 15, 30, 18, 30, 24),
                        intArrayOf(0, 120, 0, 160, 0, 210),
                        -1
                    )
                )
            } else if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(28L)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Throwable) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Light single tap that feels like a tiny paw for a companion chirp.
     */
    fun chirp() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(14L, 140))
            } else {
                tick()
            }
        } catch (_: Throwable) {
            tick()
        }
    }

    /**
     * Quick double-tap flutter for celebratory trills and spins.
     */
    fun trill() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 12, 26, 16),
                        intArrayOf(0, 150, 0, 200),
                        -1
                    )
                )
            } else {
                purr()
            }
        } catch (_: Throwable) {
            purr()
        }
    }

    /**
     * Slow lub-dub pulse for breathing exercises so users can follow with their eyes closed.
     */
    fun heartbeat() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 18, 90, 24),
                        intArrayOf(0, 110, 0, 155),
                        -1
                    )
                )
            } else {
                tick()
            }
        } catch (_: Throwable) {
            tick()
        }
    }
}

/**
 * Provides a remembered [DockHapticFeedback] instance bound to the local context and compose haptics.
 */
@Composable
fun rememberDockHaptics(enabled: Boolean = true): DockHapticFeedback {
    val context = LocalContext.current.applicationContext
    val composeHaptics = LocalHapticFeedback.current
    return remember(context, composeHaptics, enabled) {
        DockHapticFeedback(context, composeHaptics, enabled)
    }
}
