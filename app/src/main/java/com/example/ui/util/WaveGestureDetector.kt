package com.example.ui.util

/**
 * Detects a deliberate hand wave over the proximity sensor: FAR → NEAR → FAR, where the
 * "near" part lasts between [minNearMs] and [maxNearMs]. This ignores:
 *  - the phone lying face-down or covered (near for longer than [maxNearMs]),
 *  - a reading that starts already "near" (no FAR baseline),
 *  - sensor jitter (near for less than [minNearMs]).
 */
class WaveGestureDetector(
    private val minNearMs: Long = 60L,
    private val maxNearMs: Long = 1_200L,
    private val cooldownMs: Long = 1_500L
) {
    private var sawFar = false
    private var nearSince = -1L
    private var lastWaveAt = -1_000_000L

    fun reset() {
        sawFar = false
        nearSince = -1L
    }

    /** Feed each sensor reading. Returns true exactly when a wave completes. */
    fun onReading(isNear: Boolean, nowMs: Long): Boolean {
        if (!isNear) {
            val wasNear = nearSince >= 0
            val duration = if (wasNear) nowMs - nearSince else 0L
            nearSince = -1L
            val completed = wasNear && sawFar && duration in minNearMs..maxNearMs && nowMs - lastWaveAt > cooldownMs
            sawFar = true
            if (completed) lastWaveAt = nowMs
            return completed
        }
        if (nearSince < 0) nearSince = nowMs
        return false
    }
}
