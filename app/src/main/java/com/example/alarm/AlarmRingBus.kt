package com.example.alarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live status of the system-level alarm, shared between [AlarmRingService] and the UI. */
sealed interface AlarmRingStatus {
    data object Idle : AlarmRingStatus

    data class Ringing(
        val alarmId: Long,
        val label: String,
        val timeText: String,
        /** True when the user must solve a math problem before the alarm can be stopped. */
        val requiresChallenge: Boolean = false
    ) : AlarmRingStatus

    data class Snoozed(
        val alarmId: Long,
        val label: String,
        val timeText: String,
        val untilMillis: Long
    ) : AlarmRingStatus
}

/**
 * Process-wide bridge between the alarm service and the ViewModel.
 *
 * A StateFlow (not an event stream) is used on purpose: when an alarm wakes a dead process,
 * the service publishes "Ringing" before any ViewModel exists. The ViewModel created afterwards
 * still receives the current value and shows the ringing UI.
 */
object AlarmRingBus {
    private val _status = MutableStateFlow<AlarmRingStatus>(AlarmRingStatus.Idle)
    val status: StateFlow<AlarmRingStatus> = _status.asStateFlow()

    /** Long-lived scope for small DB writes that must outlive a stopped service. */
    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun update(newStatus: AlarmRingStatus) {
        _status.value = newStatus
    }
}
