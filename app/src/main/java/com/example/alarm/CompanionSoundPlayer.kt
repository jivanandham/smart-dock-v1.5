package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Short, gentle companion sound effects (< 1 second) for wellbeing reminders and tap reactions.
 *
 * Design principles:
 * - Quiet by default, short, and warm — closer to a real cat in the room than a notification ringtone.
 * - 4 subtle pitch/timing variations per cue so it never sounds mechanical.
 * - Adapts automatically between Cat (formant-shaped chirps, trills, and purrs) and Robot (soft
 *   R2-style harmonic beeps).
 * - Checks `res/raw/companion_<cue>` first so custom or Pet Twin recordings play when bundled,
 *   falling back to procedural synthesis with zero asset bloat.
 * - Obeys quiet rules: silent when mode is "OFF", whenphone is on silent/vibrate, or when `quiet`
 *   is true (meetings, Focus sessions, ringing alarms, Do Not Disturb, or night mode).
 */
enum class CompanionSoundCue(val isReminder: Boolean, val rawResName: String) {
    // Wellbeing reminders
    WATER_CHIRP(isReminder = true, rawResName = "companion_water_chirp"),
    WATER_LAP(isReminder = false, rawResName = "companion_water_lap"),
    STRETCH_YAWN(isReminder = true, rawResName = "companion_stretch_yawn"),
    EYE_REST_PRRT(isReminder = true, rawResName = "companion_eye_rest_prrt"),
    BREATH_PURR(isReminder = true, rawResName = "companion_breath_purr"),
    LUNCH_MEW(isReminder = true, rawResName = "companion_lunch_mew"),
    WALK_TRILL(isReminder = true, rawResName = "companion_walk_trill"),
    MEETING_CHIRP(isReminder = true, rawResName = "companion_meeting_chirp"),
    END_OF_DAY_MRROW(isReminder = true, rawResName = "companion_end_of_day"),
    WIND_DOWN_YAWN(isReminder = true, rawResName = "companion_wind_down"),

    // Reactions & celebrations
    TAP_HOP(isReminder = false, rawResName = "companion_tap_hop"),
    TAP_SPIN(isReminder = false, rawResName = "companion_tap_spin"),
    TAP_SHAKE(isReminder = false, rawResName = "companion_tap_shake"),
    CELEBRATE_CHIRP(isReminder = false, rawResName = "companion_celebrate"),
    TIMER_DONE(isReminder = true, rawResName = "companion_timer_done"),
    FOCUS_DONE(isReminder = true, rawResName = "companion_focus_done")
}

object CompanionSoundPlayer {
    private const val SAMPLE_RATE = 24_000

    /**
     * Plays [cue] if allowed by the user's [mode] ("ALL", "REMINDERS", "OFF"), system ringer mode,
     * and [quiet] state (meeting, Focus session, alarm ringing, DND, or night mode).
     */
    fun play(
        context: Context,
        cue: CompanionSoundCue,
        useCat: Boolean = true,
        mode: String = "ALL",
        volume: Float = 0.35f,
        quiet: Boolean = false
    ) {
        if (quiet) return
        val normalizedMode = mode.uppercase()
        if (normalizedMode == "OFF") return
        if (normalizedMode == "REMINDERS" && !cue.isReminder) return

        val clampedVolume = volume.coerceIn(0.05f, 1.0f)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return
        }

        // 1. Check for an optional custom raw audio asset (e.g., res/raw/companion_water_chirp.*)
        val rawId = try {
            context.resources.getIdentifier(cue.rawResName, "raw", context.packageName)
        } catch (_: Exception) {
            0
        }
        if (rawId != 0) {
            try {
                val mp = MediaPlayer.create(context.applicationContext, rawId)
                if (mp != null) {
                    mp.setVolume(clampedVolume, clampedVolume)
                    mp.setOnCompletionListener { it.release() }
                    mp.start()
                    return
                }
            } catch (_: Exception) {
                // Fall through to synthesized sound
            }
        }

        // 2. Synthesize a short, warm sound on a background thread
        val variant = Random.nextInt(4)
        thread(name = "CompanionSound", isDaemon = true) {
            try {
                val samples = synthesizeCue(cue, useCat, variant, clampedVolume)
                playPcm(samples)
            } catch (_: Throwable) {
                // Best-effort ambient audio
            }
        }
    }

    internal fun synthesizeCue(
        cue: CompanionSoundCue,
        useCat: Boolean,
        variant: Int = 0,
        volume: Float = 0.35f
    ): ShortArray {
        // Subtle +/- 5% pitch & duration humanization across the 4 variants
        val pitchFactor = when (variant % 4) {
            0 -> 1.00
            1 -> 1.04
            2 -> 0.96
            else -> 1.02
        }
        val durFactor = when (variant % 4) {
            0 -> 1.00
            1 -> 0.94
            2 -> 1.06
            else -> 0.98
        }

        return if (useCat) {
            synthesizeCatCue(cue, pitchFactor, durFactor, volume)
        } else {
            synthesizeRobotCue(cue, pitchFactor, durFactor, volume)
        }
    }

    private fun synthesizeCatCue(
        cue: CompanionSoundCue,
        pitch: Double,
        dur: Double,
        volume: Float
    ): ShortArray = when (cue) {
        CompanionSoundCue.WATER_CHIRP -> {
            // Soft questioning "mrrp?": rising chirp with gentle trill
            catVoiceGlide(
                durationMs = (240 * dur).toInt(),
                fStart = 420.0 * pitch,
                fMid = 510.0 * pitch,
                fEnd = 690.0 * pitch,
                trillHz = 28.0,
                trillDepth = 0.25,
                volume = volume
            )
        }
        CompanionSoundCue.WATER_LAP -> {
            // Two soft little "lap-lap" droplets
            concat(
                catVoiceGlide((95 * dur).toInt(), 360.0 * pitch, 520.0 * pitch, 460.0 * pitch, 18.0, 0.2, volume * 0.8f),
                silence(45),
                catVoiceGlide((105 * dur).toInt(), 390.0 * pitch, 560.0 * pitch, 490.0 * pitch, 18.0, 0.2, volume * 0.85f)
            )
        }
        CompanionSoundCue.STRETCH_YAWN -> {
            // Long lazy "mmmrraaow" stretch-yawn
            catVoiceGlide(
                durationMs = (540 * dur).toInt(),
                fStart = 340.0 * pitch,
                fMid = 530.0 * pitch,
                fEnd = 290.0 * pitch,
                trillHz = 22.0,
                trillDepth = 0.18,
                volume = volume * 0.9f
            )
        }
        CompanionSoundCue.EYE_REST_PRRT -> {
            // Quiet curious "prrt"
            catVoiceGlide(
                durationMs = (190 * dur).toInt(),
                fStart = 460.0 * pitch,
                fMid = 580.0 * pitch,
                fEnd = 540.0 * pitch,
                trillHz = 34.0,
                trillDepth = 0.35,
                volume = volume * 0.85f
            )
        }
        CompanionSoundCue.BREATH_PURR -> {
            // Slow gentle purr that rises and falls
            purrWave(durationMs = (680 * dur).toInt(), baseHz = 26.0 * pitch, carrierHz = 190.0 * pitch, volume = volume * 0.85f)
        }
        CompanionSoundCue.LUNCH_MEW -> {
            // Hopeful little "mew?" followed by a soft ceramic bowl clink
            concat(
                catVoiceGlide((230 * dur).toInt(), 480.0 * pitch, 640.0 * pitch, 590.0 * pitch, 14.0, 0.12, volume),
                silence(40),
                bellClink((110 * dur).toInt(), 1480.0 * pitch, volume * 0.55f)
            )
        }
        CompanionSoundCue.WALK_TRILL -> {
            // Happy rolling "brrrp" trill
            catVoiceGlide(
                durationMs = (310 * dur).toInt(),
                fStart = 430.0 * pitch,
                fMid = 620.0 * pitch,
                fEnd = 740.0 * pitch,
                trillHz = 36.0,
                trillDepth = 0.42,
                volume = volume
            )
        }
        CompanionSoundCue.MEETING_CHIRP -> {
            // Two polite soft chirps
            concat(
                catVoiceGlide((125 * dur).toInt(), 490.0 * pitch, 610.0 * pitch, 640.0 * pitch, 24.0, 0.2, volume * 0.8f),
                silence(55),
                catVoiceGlide((135 * dur).toInt(), 520.0 * pitch, 660.0 * pitch, 690.0 * pitch, 24.0, 0.2, volume * 0.85f)
            )
        }
        CompanionSoundCue.END_OF_DAY_MRROW -> {
            // Contented descending "mrrow" sigh
            catVoiceGlide(
                durationMs = (420 * dur).toInt(),
                fStart = 470.0 * pitch,
                fMid = 430.0 * pitch,
                fEnd = 310.0 * pitch,
                trillHz = 20.0,
                trillDepth = 0.16,
                volume = volume * 0.85f
            )
        }
        CompanionSoundCue.WIND_DOWN_YAWN -> {
            // Sleepy yawn into a brief purr
            concat(
                catVoiceGlide((340 * dur).toInt(), 380.0 * pitch, 440.0 * pitch, 270.0 * pitch, 16.0, 0.12, volume * 0.75f),
                purrWave((280 * dur).toInt(), 25.0 * pitch, 180.0 * pitch, volume * 0.65f)
            )
        }
        CompanionSoundCue.TAP_HOP -> {
            // 1st tap: cheerful short "mew"
            catVoiceGlide((175 * dur).toInt(), 510.0 * pitch, 670.0 * pitch, 620.0 * pitch, 20.0, 0.15, volume)
        }
        CompanionSoundCue.TAP_SPIN -> {
            // 2nd tap: playful "mrrp!"
            catVoiceGlide((230 * dur).toInt(), 460.0 * pitch, 690.0 * pitch, 790.0 * pitch, 35.0, 0.36, volume)
        }
        CompanionSoundCue.TAP_SHAKE -> {
            // 3rd tap: comical low "mrrr" grumble
            catVoiceGlide((250 * dur).toInt(), 330.0 * pitch, 300.0 * pitch, 250.0 * pitch, 38.0, 0.45, volume * 0.9f)
        }
        CompanionSoundCue.CELEBRATE_CHIRP -> {
            // Happy chirp + tiny sparkle chime
            concat(
                catVoiceGlide((170 * dur).toInt(), 520.0 * pitch, 720.0 * pitch, 780.0 * pitch, 30.0, 0.28, volume),
                silence(25),
                bellClink((140 * dur).toInt(), 1320.0 * pitch, volume * 0.6f)
            )
        }
        CompanionSoundCue.TIMER_DONE -> {
            // Friendly double "mew!"
            concat(
                catVoiceGlide((150 * dur).toInt(), 520.0 * pitch, 680.0 * pitch, 650.0 * pitch, 22.0, 0.18, volume),
                silence(45),
                catVoiceGlide((190 * dur).toInt(), 560.0 * pitch, 760.0 * pitch, 720.0 * pitch, 22.0, 0.18, volume)
            )
        }
        CompanionSoundCue.FOCUS_DONE -> {
            // Soft proud "prrt"
            catVoiceGlide((220 * dur).toInt(), 440.0 * pitch, 600.0 * pitch, 680.0 * pitch, 32.0, 0.32, volume * 0.9f)
        }
    }

    private fun synthesizeRobotCue(
        cue: CompanionSoundCue,
        pitch: Double,
        dur: Double,
        volume: Float
    ): ShortArray = when (cue) {
        CompanionSoundCue.WATER_CHIRP,
        CompanionSoundCue.TAP_HOP -> robotBeepGlide((150 * dur).toInt(), 580.0 * pitch, 860.0 * pitch, volume)
        CompanionSoundCue.TAP_SPIN,
        CompanionSoundCue.WALK_TRILL,
        CompanionSoundCue.CELEBRATE_CHIRP -> concat(
            robotBeepGlide((110 * dur).toInt(), 640.0 * pitch, 920.0 * pitch, volume),
            silence(30),
            robotBeepGlide((130 * dur).toInt(), 880.0 * pitch, 1180.0 * pitch, volume)
        )
        CompanionSoundCue.TAP_SHAKE -> robotBeepGlide((210 * dur).toInt(), 420.0 * pitch, 280.0 * pitch, volume)
        else -> robotBeepGlide((180 * dur).toInt(), 520.0 * pitch, 740.0 * pitch, volume * 0.85f)
    }

    private fun catVoiceGlide(
        durationMs: Int,
        fStart: Double,
        fMid: Double,
        fEnd: Double,
        trillHz: Double,
        trillDepth: Double,
        volume: Float
    ): ShortArray {
        val total = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
        val out = ShortArray(total)
        var phase = 0.0
        val peakAmplitude = (Short.MAX_VALUE * 0.28f * volume.coerceIn(0f, 1f)).toInt()
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / total
            // Quadratic pitch contour through start -> mid -> end
            val baseFreq = if (progress < 0.5) {
                val u = progress * 2.0
                fStart + (fMid - fStart) * u
            } else {
                val u = (progress - 0.5) * 2.0
                fMid + (fEnd - fMid) * u
            }
            val trill = 1.0 + 0.03 * sin(2.0 * PI * trillHz * t)
            val freq = baseFreq * trill
            phase += 2.0 * PI * freq / SAMPLE_RATE

            // Warm feline timbre: fundamental + soft 2nd and 3rd harmonics
            val wave = 0.68 * sin(phase) + 0.24 * sin(phase * 2.0) + 0.08 * sin(phase * 3.0)
            // Amplitude trill + smooth attack/release envelope
            val ampMod = 1.0 - trillDepth * 0.5 * (1.0 - sin(2.0 * PI * trillHz * t))
            val env = sin(PI * progress).let { it * it }
            out[i] = (wave * ampMod * env * peakAmplitude).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun purrWave(
        durationMs: Int,
        baseHz: Double,
        carrierHz: Double,
        volume: Float
    ): ShortArray {
        val total = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
        val out = ShortArray(total)
        val peakAmplitude = (Short.MAX_VALUE * 0.22f * volume.coerceIn(0f, 1f)).toInt()
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / total
            val pulse = 0.5 * (1.0 + sin(2.0 * PI * baseHz * t))
            val carrier = 0.75 * sin(2.0 * PI * carrierHz * t) + 0.25 * sin(2.0 * PI * (carrierHz * 1.5) * t)
            val env = sin(PI * progress)
            out[i] = (carrier * pulse * env * peakAmplitude).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun bellClink(durationMs: Int, freqHz: Double, volume: Float): ShortArray {
        val total = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
        val out = ShortArray(total)
        val peakAmplitude = (Short.MAX_VALUE * 0.22f * volume.coerceIn(0f, 1f)).toInt()
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 22.0)
            val wave = 0.75 * sin(2.0 * PI * freqHz * t) + 0.25 * sin(2.0 * PI * freqHz * 2.4 * t)
            out[i] = (wave * env * peakAmplitude).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun robotBeepGlide(
        durationMs: Int,
        fStart: Double,
        fEnd: Double,
        volume: Float
    ): ShortArray {
        val total = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
        val out = ShortArray(total)
        var phase = 0.0
        val peakAmplitude = (Short.MAX_VALUE * 0.24f * volume.coerceIn(0f, 1f)).toInt()
        for (i in 0 until total) {
            val progress = i.toDouble() / total
            val freq = fStart + (fEnd - fStart) * progress
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val wave = 0.8 * sin(phase) + 0.2 * sin(phase * 2.0)
            val env = sin(PI * progress)
            out[i] = (wave * env * peakAmplitude).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun silence(durationMs: Int): ShortArray =
        ShortArray((SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1))

    private fun concat(vararg chunks: ShortArray): ShortArray {
        val total = chunks.sumOf { it.size }
        val result = ShortArray(total)
        var pos = 0
        for (c in chunks) {
            c.copyInto(result, pos)
            pos += c.size
        }
        return result
    }

    private fun playPcm(samples: ShortArray) {
        val byteCount = samples.size * 2
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(byteCount)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        val durationMs = (samples.size * 1000L / SAMPLE_RATE) + 80L
        Thread.sleep(durationMs)
        try {
            track.stop()
        } catch (_: Exception) {
        }
        track.release()
    }
}
