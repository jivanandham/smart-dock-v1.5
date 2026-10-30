package com.example.alarm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Generates calming sleep sounds in real time (no audio files, no licensing issues) and fades
 * them out after a timer. Sounds: brown noise, soft rain, and a low cat purr.
 */
class SleepSoundPlayer {

    enum class Sound(val label: String) { BROWN_NOISE("Brown noise"), RAIN("Rain"), PURR("Purr"), CAFE("Café") }

    data class State(val playing: Sound? = null, val endsAtMillis: Long = 0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var fadeOutRequested = false
    @Volatile private var generation = 0
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun play(sound: Sound, minutes: Int = 30, volume: Float = 0.5f) {
        stop(immediate = true)
        fadeOutRequested = false
        val gen = ++generation
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = State(sound, endsAt)
        job = scope.launch { render(gen, sound, endsAt, volume.coerceIn(0.05f, 1f)) }
    }

    /** Fades out over ~3 s, or stops at once when [immediate]. */
    fun stop(immediate: Boolean = false) {
        if (immediate) {
            generation++
            job?.cancel()
            job = null
            _state.value = State()
        } else {
            fadeOutRequested = true
        }
    }

    private fun render(gen: Int, sound: Sound, endsAt: Long, volume: Float) {
        val rate = 22_050
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e("SleepSoundPlayer", "AudioTrack unavailable", e)
            if (gen == generation) _state.value = State()
            return
        }
        val buffer = ShortArray(1024)
        var brown = 0.0
        var lowpass = 0.0
        var phase = 0.0
        var murmur = 0.0
        var clinkPhase = 0.0
        var clinkAmp = 0.0
        var clinkFreq = 2200.0
        var gain = 0.0          // fades in from silence
        val fadeMs = 3_000.0
        val fadeOutWindow = 60_000L // final minute fades out gently
        track.play()
        try {
            while (scope.isActive && gen == generation) {
                val now = System.currentTimeMillis()
                val remaining = endsAt - now
                if (remaining <= 0) break
                val timerFactor = if (remaining < fadeOutWindow) remaining / fadeOutWindow.toDouble() else 1.0
                for (i in buffer.indices) {
                    val target = if (fadeOutRequested) 0.0 else 1.0
                    gain += (target - gain) / (rate * fadeMs / 1000.0)
                    val white = Random.nextDouble(-1.0, 1.0)
                    val sample = when (sound) {
                        Sound.BROWN_NOISE -> {
                            brown = (brown + white * 0.02).coerceIn(-1.0, 1.0) * 0.998
                            brown * 3.0
                        }
                        Sound.RAIN -> {
                            lowpass += (white - lowpass) * 0.35
                            val drop = if (Random.nextDouble() < 0.0006) Random.nextDouble(0.4, 1.0) else 0.0
                            lowpass * 0.55 + drop * white
                        }
                        Sound.CAFE -> {
                            // Soft crowd murmur (band-limited noise with slow swells) plus rare cup clinks
                            lowpass += (white - lowpass) * 0.08
                            murmur += (lowpass - murmur) * 0.3
                            val swell = 0.7 + 0.3 * sin(now / 1000.0 * 2 * PI / 7.0)
                            if (clinkAmp < 0.001 && Random.nextDouble() < 0.00004) {
                                clinkAmp = Random.nextDouble(0.15, 0.35)
                                clinkFreq = Random.nextDouble(1800.0, 3200.0)
                            }
                            clinkPhase += 2 * PI * clinkFreq / rate
                            val clink = sin(clinkPhase) * clinkAmp
                            clinkAmp *= 0.9993
                            (lowpass - murmur) * 2.2 * swell + clink
                        }
                        Sound.PURR -> {
                            // ~25 Hz amplitude-modulated low rumble, breathing every ~3 s
                            phase += 2 * PI * 25.0 / rate
                            val breath = 0.6 + 0.4 * sin(now / 1000.0 * 2 * PI / 3.0)
                            lowpass += (white - lowpass) * 0.05
                            lowpass * (0.5 + 0.5 * sin(phase)) * 4.0 * breath
                        }
                    }
                    val v = (sample * volume * gain * timerFactor).coerceIn(-1.0, 1.0)
                    buffer[i] = (v * Short.MAX_VALUE * 0.8).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
                if (fadeOutRequested && gain < 0.01) break
            }
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
            if (gen == generation) _state.value = State()
        }
    }
}
