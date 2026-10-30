package com.example

import com.example.alarm.AlarmScheduler
import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.MeetingLinks
import com.example.data.calendar.NativeCalendarReader
import com.example.data.calendar.UnifiedScheduleItem
import com.example.ui.components.AiToyActivity
import com.example.ui.components.classifyScheduleEventActivity
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLogicTest {

    private fun event(title: String, location: String = "", account: String = "me@gmail.com", start: Long = 0L) =
        UnifiedScheduleItem(
            id = title + account + start,
            title = title,
            accountName = account,
            calendarDisplayName = "Cal",
            providerBadge = AccountProviderBadge.GMAIL,
            startTimeMillis = start,
            endTimeMillis = start + 3_600_000L,
            location = location
        )

    @Test
    fun classifier_matchesWholeWordsOnly() {
        assertEquals(AiToyActivity.EATING, classifyScheduleEventActivity(event("Lunch with Sam")))
        assertEquals(AiToyActivity.GYM, classifyScheduleEventActivity(event("Morning run")))
        assertEquals(AiToyActivity.NAPPING, classifyScheduleEventActivity(event("Quick nap")))
        // Substrings that used to trigger the wrong activity:
        assertEquals(AiToyActivity.BUSY_MEETING, classifyScheduleEventActivity(event("Great sync")))
        assertEquals(AiToyActivity.BUSY_MEETING, classifyScheduleEventActivity(event("Quarterly rundown")))
        assertEquals(AiToyActivity.BUSY_MEETING, classifyScheduleEventActivity(event("Snapshot review")))
        assertEquals(AiToyActivity.BUSY_MEETING, classifyScheduleEventActivity(event("Transport planning")))
    }

    @Test
    fun classifier_ignoresAccountName() {
        assertEquals(
            AiToyActivity.BUSY_MEETING,
            classifyScheduleEventActivity(event("Design review", account = "foodie.runner@gmail.com"))
        )
    }

    @Test
    fun meetingLinks_detectsCommonProviders() {
        val meet = MeetingLinks.find("", "Join: https://meet.google.com/abc-defg-hij")
        assertEquals("https://meet.google.com/abc-defg-hij", meet)
        assertEquals("Google Meet", MeetingLinks.platformLabel(meet!!))

        val zoom = MeetingLinks.find("https://us02web.zoom.us/j/123456789?pwd=xyz", null)
        assertTrue(zoom!!.startsWith("https://us02web.zoom.us/j/123456789"))
        assertEquals("Zoom", MeetingLinks.platformLabel(zoom))

        val teams = MeetingLinks.find(null, "<https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc%40thread.v2/0>")
        assertEquals("Teams", MeetingLinks.platformLabel(teams!!))

        assertNull(MeetingLinks.find("Room 4B", "Agenda attached"))
    }

    @Test
    fun deduplicate_mergesSameInviteAcrossAccounts() {
        val a = event("Standup", account = "me@gmail.com", start = 1_000L)
        val b = event("Standup", account = "me@outlook.com", start = 1_000L)
        val c = event("Retro", account = "me@gmail.com", start = 5_000L)
        val merged = NativeCalendarReader.deduplicate(listOf(a, b, c))
        assertEquals(2, merged.size)
        assertEquals(listOf("me@outlook.com"), merged.first { it.title == "Standup" }.alsoInAccounts)
    }

    @Test
    fun alarmScheduler_nextTriggerIsAlwaysInTheFuture() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 29, 7, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val later = AlarmScheduler.nextTriggerMillis(8, 0, now)
        assertEquals(30 * 60_000L, later - now)

        // Same minute as "now" must roll over to tomorrow, never fire twice.
        val sameMinute = AlarmScheduler.nextTriggerMillis(7, 30, now)
        assertTrue(sameMinute > now)
        val earlier = AlarmScheduler.nextTriggerMillis(6, 0, now)
        assertTrue(earlier > now)
    }

    @Test
    fun webcamActive_switchesCompanionToBusyOnCallAndLabelsLens() {
        val frontState = com.example.data.hardware.WebcamActivityMonitor.computeState(
            activeIds = setOf("1"),
            facingByCameraId = mapOf("1" to android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT)
        )
        assertTrue(frontState.isCameraInUse)
        assertEquals("WEBCAM • FRONT", frontState.activeLensLabel)

        val idleState = com.example.data.hardware.WebcamActivityMonitor.computeState(emptySet())
        org.junit.Assert.assertFalse(idleState.isCameraInUse)

        val afternoonMillis = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 30, 15, 0, 0)
        }.timeInMillis

        val (activityWhenOnCamera, labelWhenOnCamera) = com.example.ui.components.resolveToyActivity(
            manualOverride = null,
            isAgentThinking = false,
            currentTimeMillis = afternoonMillis,
            schedule = emptyList(),
            isCameraInUse = true
        )
        assertEquals(AiToyActivity.BUSY_MEETING, activityWhenOnCamera)
        assertEquals("ON CALL • WEBCAM", labelWhenOnCamera)

        // Ringing alarm still takes top priority over webcam state
        val (alarmActivity, _) = com.example.ui.components.resolveToyActivity(
            manualOverride = null,
            isAgentThinking = false,
            currentTimeMillis = afternoonMillis,
            schedule = emptyList(),
            chronoState = com.example.ui.components.ActiveChronoHudState(isAlarmRinging = true, ringingAlarmTimeText = "07:00"),
            isCameraInUse = true
        )
        assertEquals(AiToyActivity.ALARM, alarmActivity)
    }

    @Test
    fun idleAutoReturn_afterOneMinute_neverWhileTypingOrDialogOpen() {
        val t0 = 100_000L
        val timeout = com.example.ui.AUTO_RETURN_TIMEOUT_MS
        // Companion page never auto-returns
        org.junit.Assert.assertFalse(
            com.example.ui.shouldAutoReturnToCompanion(0, false, t0 + 5 * timeout, t0)
        )
        for (page in listOf(1, 2, 3)) {
            org.junit.Assert.assertFalse(
                com.example.ui.shouldAutoReturnToCompanion(page, false, t0 + timeout - 100L, t0)
            )
            assertTrue(com.example.ui.shouldAutoReturnToCompanion(page, false, t0 + timeout, t0))
            // An open dialog or the keyboard always blocks the return (no lost text)
            org.junit.Assert.assertFalse(
                com.example.ui.shouldAutoReturnToCompanion(page, true, t0 + 10 * timeout, t0)
            )
            org.junit.Assert.assertFalse(
                com.example.ui.shouldAutoReturnToCompanion(page, false, t0 + 10 * timeout, t0, isKeyboardVisible = true)
            )
        }
        assertEquals(60_000L, timeout)
    }

    @Test
    fun adaptiveBrightness_followsSmoothLogarithmicCurveAndRespectsCeiling() {
        val pitchDark = com.example.ui.computeAdaptiveBrightnessForLux(0f, userScale = 1.0f)
        val dimRoom = com.example.ui.computeAdaptiveBrightnessForLux(15f, userScale = 1.0f)
        val deskLight = com.example.ui.computeAdaptiveBrightnessForLux(200f, userScale = 1.0f)
        val daylight = com.example.ui.computeAdaptiveBrightnessForLux(2000f, userScale = 1.0f)

        assertTrue(pitchDark in 0.03f..0.06f)
        assertTrue(dimRoom > pitchDark)
        assertTrue(deskLight > dimRoom)
        assertTrue(daylight > deskLight)
        assertTrue(daylight <= 1.0f)

        // User scale ceiling limits maximum brightness
        val scaledDaylight = com.example.ui.computeAdaptiveBrightnessForLux(2000f, userScale = 0.5f)
        assertTrue(scaledDaylight <= 0.5f)

        // Exponential moving average smoothing steps gracefully toward target
        val smoothed = com.example.ui.smoothAdaptiveBrightness(
            currentBrightness = 0.10f,
            targetBrightness = 0.90f,
            alpha = 0.25f
        )
        assertEquals(0.30f, smoothed, 0.001f)
    }

    @Test
    fun cameraCutoutBounds_fitsExactLensInAllOrientationsAndDevices() {
        // 1. Top-edge punch-hole where Android's boundingRect starts at top=0 (bezel gap included)
        // Hole diameter is 30px (left=525..555), safeInsetTop is 88px (top=0..88).
        val topAnchoredRect = com.example.ui.components.CutoutBounds(
            left = 525f,
            top = 0f,
            right = 555f,
            bottom = 88f
        )
        val portraitBounds = com.example.ui.components.resolveCameraCutoutBounds(
            windowWidthPx = 1080f,
            windowHeightPx = 2400f,
            displayRotation = android.view.Surface.ROTATION_0,
            hardwareBoundingRects = listOf(topAnchoredRect),
            defaultDiameterPx = 30f,
            defaultCenterInsetPx = 42f
        )
        // Must tighten to a 30x30 circle at the inner tip (top=58..88) instead of a 88px status-bar circle
        assertEquals(30f, portraitBounds.width, 0.01f)
        assertEquals(30f, portraitBounds.height, 0.01f)
        assertEquals(540f, portraitBounds.centerX, 0.01f)
        assertEquals(73f, portraitBounds.centerY, 0.01f)

        // 2. Landscape 90° left-edge punch-hole where boundingRect starts at left=0..88, height=30
        val leftAnchoredRect = com.example.ui.components.CutoutBounds(
            left = 0f,
            top = 525f,
            right = 88f,
            bottom = 555f
        )
        val landscape90Bounds = com.example.ui.components.resolveCameraCutoutBounds(
            windowWidthPx = 2400f,
            windowHeightPx = 1080f,
            displayRotation = android.view.Surface.ROTATION_90,
            hardwareBoundingRects = listOf(leftAnchoredRect),
            defaultDiameterPx = 30f,
            defaultCenterInsetPx = 42f
        )
        assertEquals(30f, landscape90Bounds.width, 0.01f)
        assertEquals(30f, landscape90Bounds.height, 0.01f)
        assertEquals(73f, landscape90Bounds.centerX, 0.01f)
        assertEquals(540f, landscape90Bounds.centerY, 0.01f)

        // 3. Reverse Landscape 270° right-edge punch-hole where boundingRect touches right=2400
        val rightAnchoredRect = com.example.ui.components.CutoutBounds(
            left = 2312f,
            top = 525f,
            right = 2400f,
            bottom = 555f
        )
        val landscape270Bounds = com.example.ui.components.resolveCameraCutoutBounds(
            windowWidthPx = 2400f,
            windowHeightPx = 1080f,
            displayRotation = android.view.Surface.ROTATION_270,
            hardwareBoundingRects = listOf(rightAnchoredRect),
            defaultDiameterPx = 30f,
            defaultCenterInsetPx = 42f
        )
        assertEquals(30f, landscape270Bounds.width, 0.01f)
        assertEquals(30f, landscape270Bounds.height, 0.01f)
        assertEquals(2327f, landscape270Bounds.centerX, 0.01f)
        assertEquals(540f, landscape270Bounds.centerY, 0.01f)

        // 4. Corner punch-hole or detached cutoutPath on another phone is preserved at its exact coordinates
        val cornerPathBounds = com.example.ui.components.CutoutBounds(
            left = 48f,
            top = 36f,
            right = 80f,
            bottom = 68f
        )
        val cornerBounds = com.example.ui.components.resolveCameraCutoutBounds(
            windowWidthPx = 1080f,
            windowHeightPx = 2400f,
            displayRotation = android.view.Surface.ROTATION_0,
            exactPathBounds = cornerPathBounds,
            defaultDiameterPx = 30f,
            defaultCenterInsetPx = 42f
        )
        assertEquals(64f, cornerBounds.centerX, 0.01f)
        assertEquals(52f, cornerBounds.centerY, 0.01f)
        assertEquals(32f, cornerBounds.width, 0.01f)
    }

    @Test
    fun webcamMonitor_ignoresOfflinePhysicalSubSensorsWhenLogicalCameraClosed_andStaysOnWhileLogicalOpen() {
        // When logical camera "0" is available (closed), an inactive physical sub-sensor "0:3"
        // callback on Pixel 6a must NOT cause a false startup flash.
        val closedWithPhysicalNoise = com.example.data.hardware.WebcamActivityMonitor.resolveEffectiveActiveCameraIds(
            activeLogicalIds = emptySet(),
            activePhysicalKeys = setOf("0:3")
        )
        assertTrue(closedWithPhysicalNoise.isEmpty())
        org.junit.Assert.assertFalse(
            com.example.data.hardware.WebcamActivityMonitor.computeState(closedWithPhysicalNoise).isCameraInUse
        )

        // When logical camera "0" or "1" is unavailable (open), it stays continuously active
        // regardless of physical sub-sensor transitions.
        val openLogical = com.example.data.hardware.WebcamActivityMonitor.resolveEffectiveActiveCameraIds(
            activeLogicalIds = setOf("1"),
            activePhysicalKeys = emptySet()
        )
        assertEquals(setOf("1"), openLogical)
        assertTrue(com.example.data.hardware.WebcamActivityMonitor.computeState(openLogical).isCameraInUse)
    }

    @Test
    fun companionWaterBottleFillFraction_scalesAndClampsCleanly() {
        assertEquals(0f, com.example.ui.components.computeWaterBottleFillFraction(0, 8), 0.001f)
        assertEquals(0.375f, com.example.ui.components.computeWaterBottleFillFraction(3, 8), 0.001f)
        assertEquals(1.0f, com.example.ui.components.computeWaterBottleFillFraction(8, 8), 0.001f)
        assertEquals(1.0f, com.example.ui.components.computeWaterBottleFillFraction(11, 8), 0.001f)
        assertEquals(0.6f, com.example.ui.components.computeWaterBottleFillFraction(3, 5), 0.001f)

        // Slosh intensity is 0 when settled and increases smoothly while filling or draining
        assertEquals(0f, com.example.ui.components.computeWaterBottleSloshIntensity(0.5f, 0.5f), 0.001f)
        val oneGlassSlosh = com.example.ui.components.computeWaterBottleSloshIntensity(0.375f, 0.500f)
        assertTrue(oneGlassSlosh in 0.5f..1.0f)

        // Goal graduation ticks reflect the user's daily goal
        val ticksFor6 = com.example.ui.components.computeWaterBottleGoalTicks(6)
        assertEquals(5, ticksFor6.size)
        assertEquals(0.5f, ticksFor6[2], 0.001f)

        // Smooth fill easing curve interpolates monotonically from 0f to 1f
        val e0 = com.example.ui.components.WaterBottleSmoothFillEasing.transform(0f)
        val e25 = com.example.ui.components.WaterBottleSmoothFillEasing.transform(0.25f)
        val e50 = com.example.ui.components.WaterBottleSmoothFillEasing.transform(0.50f)
        val e100 = com.example.ui.components.WaterBottleSmoothFillEasing.transform(1f)
        assertEquals(0f, e0, 0.0001f)
        assertTrue(e25 > e0 && e50 > e25 && e100 >= e50)
        assertEquals(1f, e100, 0.0001f)
    }

    @Test
    fun cameraRingStateMachine_locksAt100PercentWhenActiveAndFadesOutSmoothlyWhenTurnedOff() {
        // 1. Cold start with camera inactive stays OFF at 0% brightness (no startup flash)
        val initialOff = com.example.ui.components.transitionCameraRingState(
            current = com.example.ui.components.CameraRingMachineState(),
            isCameraActive = false,
            fadeOutProgress = 0f
        )
        assertEquals(com.example.ui.components.CameraRingPhase.OFF, initialOff.phase)
        assertEquals(0f, initialOff.brightness, 0.0001f)
        org.junit.Assert.assertFalse(initialOff.isVisible)

        // 2. Turning camera ON immediately locks into ACTIVE_SOLID at 100% brightness (1.0f)
        val activeState = com.example.ui.components.transitionCameraRingState(
            current = initialOff,
            isCameraActive = true,
            fadeOutProgress = 0f
        )
        assertEquals(com.example.ui.components.CameraRingPhase.ACTIVE_SOLID, activeState.phase)
        assertEquals(1.0f, activeState.brightness, 0.0001f)
        assertTrue(activeState.isVisible)

        // Remains at 100% solid brightness continuously while camera stays active
        val stillActiveState = com.example.ui.components.transitionCameraRingState(
            current = activeState,
            isCameraActive = true,
            fadeOutProgress = 0.5f
        )
        assertEquals(com.example.ui.components.CameraRingPhase.ACTIVE_SOLID, stillActiveState.phase)
        assertEquals(1.0f, stillActiveState.brightness, 0.0001f)

        // 3. Turning camera OFF enters FADING_OUT and smoothly decays from 1.0f to 0.0f
        val fade25 = com.example.ui.components.transitionCameraRingState(
            current = activeState,
            isCameraActive = false,
            fadeOutProgress = 0.25f
        )
        assertEquals(com.example.ui.components.CameraRingPhase.FADING_OUT, fade25.phase)
        assertEquals(0.75f, fade25.brightness, 0.0001f)
        assertTrue(fade25.isVisible)

        val fade60 = com.example.ui.components.transitionCameraRingState(
            current = fade25,
            isCameraActive = false,
            fadeOutProgress = 0.60f
        )
        assertEquals(com.example.ui.components.CameraRingPhase.FADING_OUT, fade60.phase)
        assertEquals(0.40f, fade60.brightness, 0.0001f)
        assertTrue(fade60.isVisible)

        // 4. Re-activating the camera mid-fade immediately snaps back to ACTIVE_SOLID at 100% brightness
        val reactivatedMidFade = com.example.ui.components.transitionCameraRingState(
            current = fade60,
            isCameraActive = true,
            fadeOutProgress = 0.60f
        )
        assertEquals(com.example.ui.components.CameraRingPhase.ACTIVE_SOLID, reactivatedMidFade.phase)
        assertEquals(1.0f, reactivatedMidFade.brightness, 0.0001f)

        // 5. Completing fade-out (progress = 1.0f) transitions cleanly to OFF at 0% brightness
        val fadeComplete = com.example.ui.components.transitionCameraRingState(
            current = fade60,
            isCameraActive = false,
            fadeOutProgress = 1.0f
        )
        assertEquals(com.example.ui.components.CameraRingPhase.OFF, fadeComplete.phase)
        assertEquals(0.0f, fadeComplete.brightness, 0.0001f)
        org.junit.Assert.assertFalse(fadeComplete.isVisible)
    }

    @Test
    fun cameraStateViewModel_managesStateFlowTransitionBetweenActiveAndInactive() {
        val viewModel = com.example.ui.CameraStateViewModel.createForTesting()

        // Initially Inactive (0% target brightness)
        assertEquals(com.example.ui.CameraActivityStatus.Inactive, viewModel.activityStatus.value)
        assertEquals(0.0f, viewModel.uiState.value.targetBrightness, 0.0001f)
        assertEquals(com.example.ui.CAMERA_RING_FADEOUT_MIN_STROKE_DP, viewModel.uiState.value.targetStrokeWidthDp, 0.0001f)
        org.junit.Assert.assertFalse(viewModel.isWebcamActive.value)

        // Hardware camera opens -> transitions StateFlow to Active (constant 100% target brightness & 4.5dp stroke)
        val frontCameraTelemetry = com.example.data.hardware.WebcamActivityMonitor.computeState(
            activeIds = setOf("1"),
            facingByCameraId = mapOf("1" to android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT)
        )
        viewModel.updateHardwareTelemetryForTest(frontCameraTelemetry)

        assertEquals(com.example.ui.CameraActivityStatus.Active, viewModel.activityStatus.value)
        assertEquals(1.0f, viewModel.uiState.value.targetBrightness, 0.0001f)
        assertEquals(4.5f, viewModel.uiState.value.targetStrokeWidthDp, 0.0001f)
        assertTrue(viewModel.uiState.value.isCameraInUse)
        assertEquals("WEBCAM • FRONT", viewModel.uiState.value.activeLensLabel)
        assertTrue(viewModel.isWebcamActive.value)

        // Hardware camera closes -> transitions StateFlow to Inactive (0% target brightness)
        viewModel.updateHardwareTelemetryForTest(com.example.data.hardware.WebcamTelemetryState())
        assertEquals(com.example.ui.CameraActivityStatus.Inactive, viewModel.activityStatus.value)
        assertEquals(0.0f, viewModel.uiState.value.targetBrightness, 0.0001f)
        org.junit.Assert.assertFalse(viewModel.isWebcamActive.value)

        // Manual preview toggle also transitions StateFlow between Active (100%, 4.5dp) and Inactive (0%)
        viewModel.toggleWebcamPreview()
        assertEquals(com.example.ui.CameraActivityStatus.Active, viewModel.activityStatus.value)
        assertEquals(1.0f, viewModel.uiState.value.targetBrightness, 0.0001f)
        assertEquals(4.5f, viewModel.uiState.value.targetStrokeWidthDp, 0.0001f)
        assertTrue(viewModel.uiState.value.isPreviewOnly)

        viewModel.stopWebcamPreview()
        assertEquals(com.example.ui.CameraActivityStatus.Inactive, viewModel.activityStatus.value)
        assertEquals(0.0f, viewModel.uiState.value.targetBrightness, 0.0001f)

        // Verify CameraRingSlowFadeOutEasing curve eases smoothly and monotonically from 0f to 1f
        val ease0 = com.example.ui.CameraRingSlowFadeOutEasing.transform(0.0f)
        val ease25 = com.example.ui.CameraRingSlowFadeOutEasing.transform(0.25f)
        val ease50 = com.example.ui.CameraRingSlowFadeOutEasing.transform(0.50f)
        val ease75 = com.example.ui.CameraRingSlowFadeOutEasing.transform(0.75f)
        val ease100 = com.example.ui.CameraRingSlowFadeOutEasing.transform(1.0f)
        assertEquals(0.0f, ease0, 0.0001f)
        assertTrue(ease25 in 0.01f..0.99f && ease25 > ease0)
        assertTrue(ease50 > ease25)
        assertTrue(ease75 > ease50)
        assertEquals(1.0f, ease100, 0.0001f)
    }
}




