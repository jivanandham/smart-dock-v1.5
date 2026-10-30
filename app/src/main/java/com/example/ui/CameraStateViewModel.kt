package com.example.ui

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core. Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.hardware.WebcamActivityMonitor
import com.example.data.hardware.WebcamTelemetryState
import com.example.ui.components.CAMERA_RING_SLOW_FADEOUT_MS
import com.example.ui.components.CameraRingMachineState
import com.example.ui.components.CameraRingPhase
import com.example.ui.components.transitionCameraRingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Constant 4.5dp core stroke width maintained while the camera is active. */
const val CAMERA_RING_ACTIVE_STROKE_DP = 4.5f

/** Minimum stroke width (in dp) reached at the tail end of the slow fade-out. */
const val CAMERA_RING_FADEOUT_MIN_STROKE_DP = 3.5f

/** Constant 100% brightness (1.0f) maintained for the entire duration the camera is active. */
const val CAMERA_RING_ACTIVE_BRIGHTNESS = 1.0f

/** Target 0% brightness (0.0f) when the camera is inactive. */
const val CAMERA_RING_INACTIVE_BRIGHTNESS = 0.0f

/**
 * Smooth cubic-bezier easing curve applied to the slow fade-out when the camera is deactivated.
 * Begins decaying gently from 100% brightness and eases smoothly to 0% over [CAMERA_RING_SLOW_FADEOUT_MS].
 */
val CameraRingSlowFadeOutEasing: Easing = CubicBezierEasing(0.22f, 0.0f, 0.12f, 1.0f)

/**
 * High-level camera activity status managed by [CameraStateViewModel]:
 * - [Active]: Camera (or user-initiated webcam test preview) is active; the 4.5dp ring is locked
 *   at a constant 100% solid brightness (`targetBrightness = 1.0f`, `targetStrokeWidthDp = 4.5f`).
 * - [Inactive]: Camera is deactivated; ring transitions smoothly to 0% brightness (`targetBrightness = 0.0f`)
 *   using [CameraRingSlowFadeOutEasing] via a Jetpack Compose transition.
 */
enum class CameraActivityStatus(
    val targetBrightness: Float,
    val targetStrokeWidthDp: Float
) {
    Active(
        targetBrightness = CAMERA_RING_ACTIVE_BRIGHTNESS,
        targetStrokeWidthDp = CAMERA_RING_ACTIVE_STROKE_DP
    ),
    Inactive(
        targetBrightness = CAMERA_RING_INACTIVE_BRIGHTNESS,
        targetStrokeWidthDp = CAMERA_RING_FADEOUT_MIN_STROKE_DP
    )
}

/**
 * Snapshot of centralized camera activity state exposed by [CameraStateViewModel].
 */
data class CameraActivityUiState(
    val status: CameraActivityStatus = CameraActivityStatus.Inactive,
    val targetBrightness: Float = status.targetBrightness,
    val targetStrokeWidthDp: Float = status.targetStrokeWidthDp,
    val isCameraInUse: Boolean = false,
    val isPreviewActive: Boolean = false,
    val activeLensLabel: String = "WEBCAM",
    val activeCameraIds: Set<String> = emptySet()
) {
    val isActive: Boolean get() = status == CameraActivityStatus.Active
    val isPreviewOnly: Boolean get() = isPreviewActive && !isCameraInUse
}

/**
 * Evaluated per-frame values from the Jetpack Compose transition managed by [CameraStateViewModel].
 * Guarantees a constant `4.5dp` stroke width and `100%` (`1.0f`) brightness whenever [status] is
 * [CameraActivityStatus.Active], and applies [CameraRingSlowFadeOutEasing] when transitioning to
 * [CameraActivityStatus.Inactive].
 */
data class CameraRingComposeTransitionState(
    val status: CameraActivityStatus,
    val phase: CameraRingPhase,
    val brightness: Float,
    val strokeWidthDp: Float
) {
    val isVisible: Boolean get() = phase != CameraRingPhase.OFF && brightness > 0.005f
}

/**
 * Pure reducer that computes [CameraActivityUiState] from hardware [WebcamTelemetryState]
 * and manual preview state.
 */
internal fun resolveCameraActivityUiState(
    hardwareState: WebcamTelemetryState,
    isPreviewActive: Boolean
): CameraActivityUiState {
    val active = hardwareState.isCameraInUse || isPreviewActive
    val status = if (active) CameraActivityStatus.Active else CameraActivityStatus.Inactive
    return CameraActivityUiState(
        status = status,
        targetBrightness = status.targetBrightness,
        targetStrokeWidthDp = status.targetStrokeWidthDp,
        isCameraInUse = hardwareState.isCameraInUse,
        isPreviewActive = isPreviewActive,
        activeLensLabel = hardwareState.activeLensLabel,
        activeCameraIds = hardwareState.activeCameraIds
    )
}

/**
 * Process-wide coordinator that owns the single [WebcamActivityMonitor] hardware listener and
 * preview state so [CameraStateViewModel], [DockViewModel], and overlays stay 100% synchronized.
 */
class CameraActivityCoordinator internal constructor(
    private val hardwareStateFlow: StateFlow<WebcamTelemetryState>,
    private val externalMonitor: WebcamActivityMonitor? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val _isPreviewActive = MutableStateFlow(false)
    private var previewJob: Job? = null

    val webcamState: StateFlow<WebcamTelemetryState> = hardwareStateFlow
    val isWebcamPreview: StateFlow<Boolean> = _isPreviewActive.asStateFlow()

    private val _uiState = MutableStateFlow(
        resolveCameraActivityUiState(hardwareStateFlow.value, _isPreviewActive.value)
    )
    val uiState: StateFlow<CameraActivityUiState> = _uiState.asStateFlow()

    val activityStatus: StateFlow<CameraActivityStatus> = _uiState
        .map { it.status }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = _uiState.value.status
        )

    val isWebcamActive: StateFlow<Boolean> = _uiState
        .map { it.isActive }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = _uiState.value.isActive
        )

    init {
        scope.launch {
            combine(hardwareStateFlow, _isPreviewActive) { hw, preview ->
                resolveCameraActivityUiState(hw, preview)
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun triggerWebcamPreview(durationMs: Long = 0L) {
        previewJob?.cancel()
        _isPreviewActive.value = true
        _uiState.value = resolveCameraActivityUiState(hardwareStateFlow.value, true)
        if (durationMs > 0L) {
            previewJob = scope.launch {
                delay(durationMs)
                _isPreviewActive.value = false
                _uiState.value = resolveCameraActivityUiState(hardwareStateFlow.value, false)
            }
        }
    }

    fun toggleWebcamPreview() {
        if (_isPreviewActive.value) {
            stopWebcamPreview()
        } else {
            triggerWebcamPreview(durationMs = 0L)
        }
    }

    fun stopWebcamPreview() {
        previewJob?.cancel()
        _isPreviewActive.value = false
        _uiState.value = resolveCameraActivityUiState(hardwareStateFlow.value, false)
    }

    companion object {
        @Volatile
        private var instance: CameraActivityCoordinator? = null

        fun getInstance(context: Context): CameraActivityCoordinator {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val monitor = WebcamActivityMonitor(context.applicationContext)
                    CameraActivityCoordinator(
                        hardwareStateFlow = monitor.state,
                        externalMonitor = monitor
                    ).also { instance = it }
                }
            }
        }
    }
}

/**
 * Centralized ViewModel for tracking hardware camera / webcam activity and managing the
 * [StateFlow] and Jetpack Compose transition between [CameraActivityStatus.Active]
 * (constant 4.5dp stroke at 100% brightness) and [CameraActivityStatus.Inactive]
 * (smooth fade-out to 0% brightness with [CameraRingSlowFadeOutEasing]).
 */
class CameraStateViewModel internal constructor(
    private val coordinator: CameraActivityCoordinator,
    private val mutableHardwareFlowForTesting: MutableStateFlow<WebcamTelemetryState>? = null
) : ViewModel() {

    /**
     * Secondary constructor for unit testing without an Android [Context].
     */
    constructor(
        initialTelemetry: WebcamTelemetryState = WebcamTelemetryState(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    ) : this(
        coordinator = MutableStateFlow(initialTelemetry).let { flow ->
            CameraActivityCoordinator(
                hardwareStateFlow = flow,
                externalMonitor = null,
                scope = scope
            )
        }
    )

    /** Centralized UI state containing [CameraActivityStatus.Active] / [CameraActivityStatus.Inactive], target brightness, and 4.5dp stroke target. */
    val uiState: StateFlow<CameraActivityUiState> = coordinator.uiState

    /** StateFlow managing the transition between [CameraActivityStatus.Active] (1.0f) and [CameraActivityStatus.Inactive] (0.0f). */
    val activityStatus: StateFlow<CameraActivityStatus> = coordinator.activityStatus

    /** Raw hardware webcam telemetry state (active camera IDs and lens facing label). */
    val webcamState: StateFlow<WebcamTelemetryState> = coordinator.webcamState

    /** True when the user has manually toggled the webcam indicator preview on. */
    val isWebcamPreview: StateFlow<Boolean> = coordinator.isWebcamPreview

    /** True whenever the hardware camera is open OR the manual preview is active. */
    val isWebcamActive: StateFlow<Boolean> = coordinator.isWebcamActive

    fun triggerWebcamPreview(durationMs: Long = 0L) {
        coordinator.triggerWebcamPreview(durationMs)
    }

    fun toggleWebcamPreview() {
        coordinator.toggleWebcamPreview()
    }

    fun stopWebcamPreview() {
        coordinator.stopWebcamPreview()
    }

    internal fun updateHardwareTelemetryForTest(telemetry: WebcamTelemetryState) {
        mutableHardwareFlowForTesting?.value = telemetry
    }

    /**
     * Collects [activityStatus] from this [CameraStateViewModel] and drives the Jetpack Compose
     * transition that keeps the 4.5dp ring at a constant 100% brightness while active and applies
     * [CameraRingSlowFadeOutEasing] for the slow fade-out when deactivated.
     */
    @Composable
    fun rememberRingComposeTransition(): CameraRingComposeTransitionState {
        val status by activityStatus.collectAsStateWithLifecycle()
        return rememberCameraRingComposeTransition(activityStatus = status)
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraStateViewModel(
                coordinator = CameraActivityCoordinator.getInstance(appContext)
            ) as T
        }
    }

    companion object {
        /**
         * Transition spec for ring brightness:
         * - Locks immediately (`snap()`) to 100% brightness (`1.0f`) when transitioning to [CameraActivityStatus.Active].
         * - Applies [CameraRingSlowFadeOutEasing] over [CAMERA_RING_SLOW_FADEOUT_MS] (`2400 ms`) when
         *   transitioning to [CameraActivityStatus.Inactive].
         */
        fun brightnessTransitionSpec(
            targetState: CameraActivityStatus
        ): FiniteAnimationSpec<Float> =
            if (targetState == CameraActivityStatus.Active) {
                snap()
            } else {
                tween(
                    durationMillis = CAMERA_RING_SLOW_FADEOUT_MS,
                    easing = CameraRingSlowFadeOutEasing
                )
            }

        /**
         * Transition spec for the 4.5dp ring stroke width:
         * - Locks immediately (`snap()`) to `4.5dp` ([CAMERA_RING_ACTIVE_STROKE_DP]) when [CameraActivityStatus.Active].
         * - Eases smoothly with [CameraRingSlowFadeOutEasing] over [CAMERA_RING_SLOW_FADEOUT_MS] when deactivated.
         */
        fun strokeWidthTransitionSpec(
            targetState: CameraActivityStatus
        ): FiniteAnimationSpec<Float> =
            if (targetState == CameraActivityStatus.Active) {
                snap()
            } else {
                tween(
                    durationMillis = CAMERA_RING_SLOW_FADEOUT_MS,
                    easing = CameraRingSlowFadeOutEasing
                )
            }

        internal fun createForTesting(
            initialTelemetry: WebcamTelemetryState = WebcamTelemetryState(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        ): CameraStateViewModel {
            val hardwareFlow = MutableStateFlow(initialTelemetry)
            val coordinator = CameraActivityCoordinator(
                hardwareStateFlow = hardwareFlow,
                externalMonitor = null,
                scope = scope
            )
            return CameraStateViewModel(
                coordinator = coordinator,
                mutableHardwareFlowForTesting = hardwareFlow
            )
        }
    }
}

/**
 * Jetpack Compose transition driver for the camera cutout ring:
 * - Maintains a constant `4.5dp` stroke width ([CAMERA_RING_ACTIVE_STROKE_DP]) and constant `100%`
 *   brightness ([CAMERA_RING_ACTIVE_BRIGHTNESS] = `1.0f`) throughout active camera use.
 * - Applies [CameraRingSlowFadeOutEasing] over [CAMERA_RING_SLOW_FADEOUT_MS] (`2400 ms`) for a
 *   smooth fade-out when the camera is deactivated.
 */
@Composable
fun rememberCameraRingComposeTransition(
    activityStatus: CameraActivityStatus
): CameraRingComposeTransitionState {
    val transition: Transition<CameraActivityStatus> = updateTransition(
        targetState = activityStatus,
        label = "camera_activity_transition"
    )

    val animatedBrightness by transition.animateFloat(
        transitionSpec = { CameraStateViewModel.brightnessTransitionSpec(targetState) },
        label = "camera_ring_brightness"
    ) { state ->
        state.targetBrightness
    }

    val animatedStrokeDp by transition.animateFloat(
        transitionSpec = { CameraStateViewModel.strokeWidthTransitionSpec(targetState) },
        label = "camera_ring_stroke_dp"
    ) { state ->
        state.targetStrokeWidthDp
    }

    val isCurrentlyActive = activityStatus == CameraActivityStatus.Active
    val fadeOutProgress = (1.0f - animatedBrightness).coerceIn(0f, 1f)
    val basePhase = when {
        isCurrentlyActive -> CameraRingPhase.ACTIVE_SOLID
        transition.currentState == CameraActivityStatus.Active || animatedBrightness > 0.005f -> CameraRingPhase.FADING_OUT
        else -> CameraRingPhase.OFF
    }

    val machineState = transitionCameraRingState(
        current = CameraRingMachineState(
            phase = basePhase,
            brightness = if (isCurrentlyActive) CAMERA_RING_ACTIVE_BRIGHTNESS else animatedBrightness.coerceIn(0f, 1f)
        ),
        isCameraActive = isCurrentlyActive,
        fadeOutProgress = fadeOutProgress
    )

    val effectiveStrokeDp = if (machineState.phase == CameraRingPhase.ACTIVE_SOLID) {
        CAMERA_RING_ACTIVE_STROKE_DP
    } else {
        animatedStrokeDp.coerceIn(CAMERA_RING_FADEOUT_MIN_STROKE_DP, CAMERA_RING_ACTIVE_STROKE_DP)
    }

    return CameraRingComposeTransitionState(
        status = activityStatus,
        phase = machineState.phase,
        brightness = machineState.brightness,
        strokeWidthDp = effectiveStrokeDp
    )
}
