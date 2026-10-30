package com.example.ui.components

import android.graphics.RectF
import android.os.Build
import android.view.Surface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.CAMERA_RING_ACTIVE_BRIGHTNESS
import com.example.ui.CAMERA_RING_ACTIVE_STROKE_DP
import com.example.ui.CameraActivityStatus
import com.example.ui.CameraActivityUiState
import com.example.ui.CameraStateViewModel
import com.example.ui.rememberCameraRingComposeTransition
import kotlin.math.abs
import kotlin.math.min

private val WebcamEmerald = Color(0xFF00E676)
private val WebcamCyan = Color(0xFF00F0FF)

internal const val CAMERA_RING_FADE_IN_MS = 280
internal const val CAMERA_RING_SLOW_FADEOUT_MS = 2400

/**
 * Explicit state machine phases for the camera cutout ring:
 * - [OFF]: Camera is inactive and ring is completely off (`brightness = 0.0f`).
 * - [ACTIVE_SOLID]: Camera is active; ring is locked at 100% solid brightness (`brightness = 1.0f`)
 *   with zero dimming or premature fade.
 * - [FADING_OUT]: Camera just turned off; ring smoothly fades from `1.0f` to `0.0f` over
 *   [CAMERA_RING_SLOW_FADEOUT_MS] before entering [OFF].
 */
enum class CameraRingPhase {
    OFF,
    ACTIVE_SOLID,
    FADING_OUT
}

data class CameraRingMachineState(
    val phase: CameraRingPhase = CameraRingPhase.OFF,
    val brightness: Float = 0f
) {
    val isVisible: Boolean get() = phase != CameraRingPhase.OFF && brightness > 0.005f
}

/**
 * Pure, deterministic state machine transition for the camera cutout ring:
 * - Whenever [isCameraActive] is `true`, immediately transitions to [CameraRingPhase.ACTIVE_SOLID]
 *   at full `1.0f` (100%) brightness.
 * - When [isCameraActive] transitions from `true` to `false`, enters [CameraRingPhase.FADING_OUT]
 *   and interpolates brightness smoothly from `1.0f` down to `0.0f` according to [fadeOutProgress]
 *   (`0.0f..1.0f`), transitioning to [CameraRingPhase.OFF] once [fadeOutProgress] reaches `1.0f`.
 * - When [isCameraActive] is `false` while already [CameraRingPhase.OFF], stays [CameraRingPhase.OFF]
 *   at `0.0f` brightness (preventing any cold-start flash).
 */
internal fun transitionCameraRingState(
    current: CameraRingMachineState,
    isCameraActive: Boolean,
    fadeOutProgress: Float = 0f
): CameraRingMachineState {
    if (isCameraActive) {
        return CameraRingMachineState(
            phase = CameraRingPhase.ACTIVE_SOLID,
            brightness = 1.0f
        )
    }
    return when (current.phase) {
        CameraRingPhase.OFF -> CameraRingMachineState(
            phase = CameraRingPhase.OFF,
            brightness = 0.0f
        )
        CameraRingPhase.ACTIVE_SOLID,
        CameraRingPhase.FADING_OUT -> {
            val clampedProgress = fadeOutProgress.coerceIn(0f, 1f)
            val remainingBrightness = (1.0f - clampedProgress).coerceIn(0f, 1f)
            if (clampedProgress >= 1.0f || remainingBrightness <= 0.005f) {
                CameraRingMachineState(
                    phase = CameraRingPhase.OFF,
                    brightness = 0.0f
                )
            } else {
                CameraRingMachineState(
                    phase = CameraRingPhase.FADING_OUT,
                    brightness = remainingBrightness
                )
            }
        }
    }
}

/**
 * Exact window-coordinate bounds of a physical camera cutout.
 */
data class CutoutBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    val isEmpty: Boolean get() = width <= 0.5f || height <= 0.5f
}

/**
 * Resolves the tight physical camera cutout bounds in window coordinates across any Android phone
 * and screen layout/orientation:
 *
 * 1. If an exact hardware cutout path bounding box ([exactPathBounds]) is available (Android 12+
 *    `DisplayCutout.getCutoutPath()`), uses it directly because it traces the actual punch-hole or
 *    pill lens without the bezel gap between the screen edge and the hole.
 * 2. Otherwise, if [hardwareBoundingRects] from `DisplayCutout.getBoundingRects()` is non-empty,
 *    tightens edge-anchored cutout rects (where Android extends the rect from the screen edge `0`
 *    to the inner bottom of a circular punch-hole for status-bar safe-inset calculation) so the
 *    bounds hug the circular hole itself rather than the status-bar bezel gap above it, while
 *    preserving wide pill/island cutouts.
 * 3. If no hardware `DisplayCutout` is reported (e.g., on an emulator), places a Pixel 6a-sized
 *    punch-hole circle (`defaultDiameterPx`, `defaultCenterInsetPx`) on the camera edge matching
 *    [displayRotation] (`ROTATION_0`, `ROTATION_90`, `ROTATION_180`, `ROTATION_270`).
 */
internal fun resolveCameraCutoutBounds(
    windowWidthPx: Float,
    windowHeightPx: Float,
    displayRotation: Int,
    exactPathBounds: CutoutBounds? = null,
    hardwareBoundingRects: List<CutoutBounds> = emptyList(),
    defaultDiameterPx: Float,
    defaultCenterInsetPx: Float
): CutoutBounds {
    if (exactPathBounds != null && !exactPathBounds.isEmpty) {
        return tightenEdgeAnchoredPunchHole(
            rect = exactPathBounds,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx
        )
    }

    val validRects = hardwareBoundingRects.filter { !it.isEmpty }
    if (validRects.isNotEmpty()) {
        // Choose the primary front-camera cutout rect (prefer the edge corresponding to current rotation
        // if multiple cutouts exist on the device).
        val chosen = selectPrimaryCameraCutoutRect(
            rects = validRects,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            displayRotation = displayRotation
        )
        return tightenEdgeAnchoredPunchHole(
            rect = chosen,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx
        )
    }

    // Fallback when running on a cutout-free emulator: position according to display rotation
    val radius = (defaultDiameterPx * 0.5f).coerceAtLeast(1f)
    val inset = defaultCenterInsetPx.coerceAtLeast(radius)
    val isLandscape = windowWidthPx > windowHeightPx

    val cx: Float
    val cy: Float
    when (displayRotation) {
        Surface.ROTATION_90 -> {
            cx = inset
            cy = windowHeightPx * 0.5f
        }
        Surface.ROTATION_270 -> {
            cx = (windowWidthPx - inset).coerceAtLeast(radius)
            cy = windowHeightPx * 0.5f
        }
        Surface.ROTATION_180 -> {
            cx = windowWidthPx * 0.5f
            cy = (windowHeightPx - inset).coerceAtLeast(radius)
        }
        else -> {
            if (isLandscape) {
                cx = inset
                cy = windowHeightPx * 0.5f
            } else {
                cx = windowWidthPx * 0.5f
                cy = inset
            }
        }
    }

    return CutoutBounds(
        left = cx - radius,
        top = cy - radius,
        right = cx + radius,
        bottom = cy + radius
    )
}

private fun selectPrimaryCameraCutoutRect(
    rects: List<CutoutBounds>,
    windowWidthPx: Float,
    windowHeightPx: Float,
    displayRotation: Int
): CutoutBounds {
    if (rects.size == 1) return rects.first()
    return when (displayRotation) {
        Surface.ROTATION_90 -> rects.minByOrNull { it.left } ?: rects.first()
        Surface.ROTATION_270 -> rects.maxByOrNull { it.right } ?: rects.first()
        Surface.ROTATION_180 -> rects.maxByOrNull { it.bottom } ?: rects.first()
        else -> rects.minByOrNull { it.top } ?: rects.first()
    }
}

/**
 * On many punch-hole Android phones (including Pixel series), `DisplayCutout.boundingRects`
 * starts at the outer screen edge (`top = 0` in portrait, `left = 0` in landscape-90, etc.)
 * and extends inward to the inner edge of the circular punch-hole so the system knows how tall
 * the status-bar safe inset must be. That makes `perpendicularDepth > parallelSize`.
 *
 * When a cutout rect is anchored to a screen edge and its perpendicular depth is greater than its
 * width along that edge, the physical circular punch-hole has diameter `parallelSize` and sits at
 * the inner tip of the bounding rect.
 */
internal fun tightenEdgeAnchoredPunchHole(
    rect: CutoutBounds,
    windowWidthPx: Float,
    windowHeightPx: Float
): CutoutBounds {
    val edgeTolerancePx = 3f
    val touchesTop = rect.top <= edgeTolerancePx
    val touchesBottom = windowHeightPx > 0f && rect.bottom >= windowHeightPx - edgeTolerancePx
    val touchesLeft = rect.left <= edgeTolerancePx
    val touchesRight = windowWidthPx > 0f && rect.right >= windowWidthPx - edgeTolerancePx

    val w = rect.width
    val h = rect.height

    return when {
        // Top-edge punch-hole where bounding rect includes the top bezel gap above the hole
        touchesTop && !touchesBottom && h > w * 1.08f -> {
            CutoutBounds(
                left = rect.left,
                top = rect.bottom - w,
                right = rect.right,
                bottom = rect.bottom
            )
        }
        // Bottom-edge punch-hole (upside-down portrait)
        touchesBottom && !touchesTop && h > w * 1.08f -> {
            CutoutBounds(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.top + w
            )
        }
        // Left-edge punch-hole (landscape 90°) where bounding rect includes the left bezel gap
        touchesLeft && !touchesRight && w > h * 1.08f -> {
            CutoutBounds(
                left = rect.right - h,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom
            )
        }
        // Right-edge punch-hole (reverse landscape 270°) where bounding rect includes the right bezel gap
        touchesRight && !touchesLeft && w > h * 1.08f -> {
            CutoutBounds(
                left = rect.left,
                top = rect.top,
                right = rect.left + h,
                bottom = rect.bottom
            )
        }
        else -> rect
    }
}

/**
 * Overload accepting [CameraStateViewModel] directly so the overlay observes both the centralized
 * [CameraActivityUiState] and the [CameraStateViewModel.rememberRingComposeTransition] transition.
 */
@Composable
fun CameraCutoutRingOverlay(
    viewModel: CameraStateViewModel,
    onDismissPreview: (() -> Unit)? = { viewModel.stopWebcamPreview() },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ringTransition = viewModel.rememberRingComposeTransition()
    CameraCutoutRingOverlay(
        isActive = uiState.isActive,
        activityStatus = ringTransition.status,
        lensLabel = uiState.activeLensLabel,
        isPreview = uiState.isPreviewOnly,
        onDismissPreview = onDismissPreview,
        modifier = modifier
    )
}

/**
 * Overload accepting [CameraActivityUiState] directly from `CameraStateViewModel`.
 */
@Composable
fun CameraCutoutRingOverlay(
    cameraUiState: CameraActivityUiState,
    onDismissPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CameraCutoutRingOverlay(
        isActive = cameraUiState.isActive,
        activityStatus = cameraUiState.status,
        lensLabel = cameraUiState.activeLensLabel,
        isPreview = cameraUiState.isPreviewOnly,
        onDismissPreview = onDismissPreview,
        modifier = modifier
    )
}

/**
 * Crisp, tight emerald-and-cyan ring that hugs the exact physical camera cutout boundary on
 * Pixel 6a and any Android phone across all orientations and screen layouts.
 *
 * - Driven by [rememberCameraRingComposeTransition] in `CameraStateViewModel`:
 *   - While [activityStatus] is [CameraActivityStatus.Active], the `4.5dp` stroke ring maintains
 *     a constant `100%` solid brightness (`1.0f`).
 *   - When deactivated ([CameraActivityStatus.Inactive]), applies `CameraRingSlowFadeOutEasing`
 *     to smoothly fade from `100%` to `0%` over [CAMERA_RING_SLOW_FADEOUT_MS] (2.4 s).
 */
@Composable
fun CameraCutoutRingOverlay(
    isActive: Boolean,
    lensLabel: String = "WEBCAM",
    isPreview: Boolean = false,
    onDismissPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activityStatus: CameraActivityStatus = if (isActive) CameraActivityStatus.Active else CameraActivityStatus.Inactive
) {
    val ringTransition = rememberCameraRingComposeTransition(activityStatus = activityStatus)
    val ringPower = ringTransition.brightness

    if (ringTransition.isVisible) {
        val view = LocalView.current
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current

        // Trigger recomposition whenever WindowInsets or display orientation/layout changes
        var insetsVersion by remember { mutableIntStateOf(0) }
        var overlayWindowOffset by remember { mutableStateOf(Offset.Zero) }

        DisposableEffect(view, configuration) {
            val listener = androidx.core.view.OnApplyWindowInsetsListener { _, insets ->
                insetsVersion++
                insets
            }
            ViewCompat.setOnApplyWindowInsetsListener(view, listener)
            ViewCompat.requestApplyInsets(view)
            onDispose {
                ViewCompat.setOnApplyWindowInsetsListener(view, null)
            }
        }

        val infinite = rememberInfiniteTransition(label = "webcam_cutout_ring")
        val sweepAngleDeg by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_rotation"
        )

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInWindow()
                    if (abs(pos.x - overlayWindowOffset.x) > 0.5f ||
                        abs(pos.y - overlayWindowOffset.y) > 0.5f
                    ) {
                        overlayWindowOffset = pos
                    }
                }
                .semantics {
                    contentDescription = when (ringTransition.phase) {
                        CameraRingPhase.ACTIVE_SOLID -> "Camera active cutout ring ($lensLabel)"
                        CameraRingPhase.FADING_OUT -> "Camera cutout ring fading out"
                        CameraRingPhase.OFF -> "Camera cutout ring off"
                    }
                }
                .testTag("webcam_cutout_ring_overlay")
        ) {
            // Read insetsVersion and configuration so Compose tracks changes across rotations
            @Suppress("UNUSED_EXPRESSION")
            insetsVersion
            @Suppress("UNUSED_EXPRESSION")
            configuration.orientation

            val windowWidthPx = if (view.width > 0) view.width.toFloat() else constraints.maxWidth.toFloat()
            val windowHeightPx = if (view.height > 0) view.height.toFloat() else constraints.maxHeight.toFloat()
            val displayRotation = view.display?.rotation ?: Surface.ROTATION_0

            val displayCutout = view.rootWindowInsets?.displayCutout

            // 1. Prefer Android 12+ exact cutout Path bounds when available
            val exactPathBounds: CutoutBounds? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && displayCutout != null) {
                val cutoutPath = displayCutout.cutoutPath
                if (cutoutPath != null && !cutoutPath.isEmpty) {
                    val pathRect = RectF()
                    cutoutPath.computeBounds(pathRect, true)
                    CutoutBounds(
                        left = pathRect.left,
                        top = pathRect.top,
                        right = pathRect.right,
                        bottom = pathRect.bottom
                    ).takeIf { !it.isEmpty }
                } else {
                    null
                }
            } else {
                null
            }

            // 2. Fallback to DisplayCutout boundingRects
            val hardwareRects: List<CutoutBounds> = displayCutout
                ?.boundingRects
                ?.mapNotNull { rect ->
                    if (rect.isEmpty) null
                    else CutoutBounds(
                        left = rect.left.toFloat(),
                        top = rect.top.toFloat(),
                        right = rect.right.toFloat(),
                        bottom = rect.bottom.toFloat()
                    )
                }
                .orEmpty()

            // Pixel 6a physical punch-hole dimensions (~10.5dp diameter, centered ~14dp from screen edge)
            val defaultDiameterPx = with(density) { 10.5.dp.toPx() }
            val defaultCenterInsetPx = with(density) { 14.dp.toPx() }

            val windowCutoutBounds = resolveCameraCutoutBounds(
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                displayRotation = displayRotation,
                exactPathBounds = exactPathBounds,
                hardwareBoundingRects = hardwareRects,
                defaultDiameterPx = defaultDiameterPx,
                defaultCenterInsetPx = defaultCenterInsetPx
            )

            // Convert from window coordinates to this Canvas's local coordinates
            val localBounds = CutoutBounds(
                left = windowCutoutBounds.left - overlayWindowOffset.x,
                top = windowCutoutBounds.top - overlayWindowOffset.y,
                right = windowCutoutBounds.right - overlayWindowOffset.x,
                bottom = windowCutoutBounds.bottom - overlayWindowOffset.y
            )

            // Medium-bold 4.5dp ring hugging the exact camera cutout boundary at constant 100% brightness while active
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = if (ringTransition.phase == CameraRingPhase.ACTIVE_SOLID) {
                    CAMERA_RING_ACTIVE_STROKE_DP.dp.toPx()
                } else {
                    ringTransition.strokeWidthDp.dp.toPx()
                }
                // Expand by half the stroke width so the inner edge of the stroke sits flush on the cutout edge
                val halfStroke = strokeWidthPx * 0.5f
                val ringLeft = localBounds.left - halfStroke
                val ringTop = localBounds.top - halfStroke
                val ringWidth = localBounds.width + strokeWidthPx
                val ringHeight = localBounds.height + strokeWidthPx
                val cornerRadiusPx = min(ringWidth, ringHeight) * 0.5f
                val holeCenter = Offset(localBounds.centerX, localBounds.centerY)
                // Locked at 1.0f (100% brightness) continuously in ACTIVE_SOLID; smoothly eases during FADING_OUT
                val alpha = if (ringTransition.phase == CameraRingPhase.ACTIVE_SOLID) {
                    CAMERA_RING_ACTIVE_BRIGHTNESS
                } else {
                    ringPower.coerceIn(0f, 1f)
                }

                // Soft outer ambient bloom around the camera cutout
                val outerBloomStrokePx = strokeWidthPx * 2.8f
                val outerBloomHalf = outerBloomStrokePx * 0.5f
                val outerBloomWidth = localBounds.width + outerBloomStrokePx
                val outerBloomHeight = localBounds.height + outerBloomStrokePx
                val outerBloomCorner = min(outerBloomWidth, outerBloomHeight) * 0.5f
                drawRoundRect(
                    color = WebcamCyan.copy(alpha = (0.28f * alpha).coerceIn(0f, 1f)),
                    topLeft = Offset(localBounds.left - outerBloomHalf, localBounds.top - outerBloomHalf),
                    size = Size(outerBloomWidth, outerBloomHeight),
                    cornerRadius = CornerRadius(outerBloomCorner, outerBloomCorner),
                    style = Stroke(width = outerBloomStrokePx, cap = StrokeCap.Round)
                )

                // Brighter mid-layer luminous emerald halo
                val haloStrokePx = strokeWidthPx * 1.85f
                val haloHalf = haloStrokePx * 0.5f
                val haloWidth = localBounds.width + haloStrokePx
                val haloHeight = localBounds.height + haloStrokePx
                val haloCorner = min(haloWidth, haloHeight) * 0.5f
                drawRoundRect(
                    color = WebcamEmerald.copy(alpha = (0.55f * alpha).coerceIn(0f, 1f)),
                    topLeft = Offset(localBounds.left - haloHalf, localBounds.top - haloHalf),
                    size = Size(haloWidth, haloHeight),
                    cornerRadius = CornerRadius(haloCorner, haloCorner),
                    style = Stroke(width = haloStrokePx, cap = StrokeCap.Round)
                )

                // Solid 100% brightness 4.5dp core ring with rotating emerald-cyan highlight
                rotate(degrees = sweepAngleDeg, pivot = holeCenter) {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                WebcamEmerald.copy(alpha = alpha),
                                WebcamCyan.copy(alpha = alpha),
                                Color(0xFF6EE7B7).copy(alpha = alpha),
                                WebcamCyan.copy(alpha = alpha),
                                WebcamEmerald.copy(alpha = alpha)
                            ),
                            center = holeCenter
                        ),
                        topLeft = Offset(ringLeft, ringTop),
                        size = Size(ringWidth, ringHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
