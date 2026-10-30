package com.example.data.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WebcamTelemetryState(
    val isCameraInUse: Boolean = false,
    val activeCameraIds: Set<String> = emptySet(),
    val activeLensLabel: String = "WEBCAM"
)

/**
 * Zero-permission system camera monitor using [CameraManager.AvailabilityCallback].
 *
 * Whenever the Pixel 6a is used as a webcam for a Mac Mini (via Android 14+ USB Webcam
 * `DeviceAsWebcam`, Camo, DroidCam, or any video call app), Android notifies all registered
 * [CameraManager.AvailabilityCallback] listeners via [onCameraUnavailable] /
 * [onPhysicalCameraUnavailable] when the camera lens opens and [onCameraAvailable] /
 * [onPhysicalCameraAvailable] when it closes.
 *
 * Includes a short release grace window ([RELEASE_DEBOUNCE_MS]) so brief stream or resolution
 * reconfigurations during a live webcam session never drop the active camera state, while
 * turning on immediately (0 ms) as soon as the camera opens.
 */
class WebcamActivityMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraManager: CameraManager? = try {
        appContext.getSystemService(CameraManager::class.java)
    } catch (_: Throwable) {
        null
    }

    private val activeLogicalIds = LinkedHashSet<String>()
    private val activePhysicalIds = LinkedHashSet<String>()
    private val facingCache = HashMap<String, Int>()

    private val _state = MutableStateFlow(WebcamTelemetryState())
    val state: StateFlow<WebcamTelemetryState> = _state.asStateFlow()

    private val delayedReleaseRunnable = Runnable {
        synchronized(activeLogicalIds) {
            val effective = resolveEffectiveActiveCameraIds(activeLogicalIds, activePhysicalIds)
            if (effective.isEmpty()) {
                publishLocked()
            }
        }
    }

    private val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            synchronized(activeLogicalIds) {
                activeLogicalIds.remove(cameraId)
                // Clear any physical sub-camera keys belonging to this logical camera
                activePhysicalIds.removeAll { it.startsWith("$cameraId:") }
                scheduleOrPublishLocked()
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            synchronized(activeLogicalIds) {
                mainHandler.removeCallbacks(delayedReleaseRunnable)
                if (cameraId !in facingCache) {
                    resolveLensFacing(cameraId)?.let { facingCache[cameraId] = it }
                }
                activeLogicalIds.add(cameraId)
                publishLocked()
            }
        }

        override fun onPhysicalCameraAvailable(cameraId: String, physicalCameraId: String) {
            synchronized(activeLogicalIds) {
                activePhysicalIds.remove("$cameraId:$physicalCameraId")
                // Note: If the parent logical camera is still in activeLogicalIds, the camera
                // remains actively in use without interruption.
                scheduleOrPublishLocked()
            }
        }

        override fun onPhysicalCameraUnavailable(cameraId: String, physicalCameraId: String) {
            synchronized(activeLogicalIds) {
                // On Pixel 6a and Android 11+ logical multi-cameras, inactive physical sub-sensors
                // can report unavailable during initial registration even when the parent logical
                // camera is closed (available). Only track physical sub-camera unavailability when
                // its parent logical camera is also unavailable (open).
                if (cameraId in activeLogicalIds) {
                    mainHandler.removeCallbacks(delayedReleaseRunnable)
                    if (cameraId !in facingCache) {
                        resolveLensFacing(cameraId)?.let { facingCache[cameraId] = it }
                    }
                    activePhysicalIds.add("$cameraId:$physicalCameraId")
                    publishLocked()
                }
            }
        }
    }

    init {
        try {
            cameraManager?.registerAvailabilityCallback(callback, mainHandler)
        } catch (_: Throwable) {
            // Safe fallback on environments without camera service
        }
    }

    private fun resolveLensFacing(cameraId: String): Int? = try {
        cameraManager
            ?.getCameraCharacteristics(cameraId)
            ?.get(CameraCharacteristics.LENS_FACING)
    } catch (_: Throwable) {
        null
    }

    private fun scheduleOrPublishLocked() {
        mainHandler.removeCallbacks(delayedReleaseRunnable)
        val effective = resolveEffectiveActiveCameraIds(activeLogicalIds, activePhysicalIds)
        if (effective.isNotEmpty()) {
            publishLocked()
        } else {
            // Wait briefly before declaring all cameras closed so stream handshakes don't flicker
            mainHandler.postDelayed(delayedReleaseRunnable, RELEASE_DEBOUNCE_MS)
        }
    }

    private fun publishLocked() {
        val effective = resolveEffectiveActiveCameraIds(activeLogicalIds, activePhysicalIds)
        _state.value = computeState(effective, facingCache)
    }

    fun release() {
        mainHandler.removeCallbacks(delayedReleaseRunnable)
        try {
            cameraManager?.unregisterAvailabilityCallback(callback)
        } catch (_: Throwable) {
        }
    }

    companion object {
        internal const val RELEASE_DEBOUNCE_MS = 1200L

        /**
         * Combines active logical camera IDs with physical sub-camera keys whose parent logical
         * camera is currently active. Prevents false startup flashes from inactive physical
         * sub-sensors on logical multi-camera devices (such as Pixel 6a camera "0" -> "2", "3"),
         * while keeping the camera marked active continuously as long as the logical camera is open.
         */
        fun resolveEffectiveActiveCameraIds(
            activeLogicalIds: Set<String>,
            activePhysicalKeys: Set<String> = emptySet()
        ): Set<String> {
            if (activeLogicalIds.isEmpty()) return emptySet()
            return LinkedHashSet<String>().apply {
                addAll(activeLogicalIds)
                activePhysicalKeys.forEach { key ->
                    val parentId = key.substringBefore(':')
                    if (parentId in activeLogicalIds) {
                        add(parentId)
                    }
                }
            }
        }

        /**
         * Pure state reducer used both at runtime and in JVM unit tests.
         */
        fun computeState(
            activeIds: Set<String>,
            facingByCameraId: Map<String, Int> = emptyMap()
        ): WebcamTelemetryState {
            if (activeIds.isEmpty()) {
                return WebcamTelemetryState(
                    isCameraInUse = false,
                    activeCameraIds = emptySet(),
                    activeLensLabel = "WEBCAM"
                )
            }
            val firstId = activeIds.first()
            val label = when (facingByCameraId[firstId]) {
                CameraCharacteristics.LENS_FACING_FRONT -> "WEBCAM • FRONT"
                CameraCharacteristics.LENS_FACING_BACK -> "WEBCAM • REAR"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "WEBCAM • USB"
                else -> "WEBCAM"
            }
            return WebcamTelemetryState(
                isCameraInUse = true,
                activeCameraIds = activeIds,
                activeLensLabel = label
            )
        }
    }
}
