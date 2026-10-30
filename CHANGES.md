# Smart Dock – Improvement Pass

This pass focuses on reliability (alarms, timers, calendar) and on making the AI companion feel
more alive. No database schema changes were made, so existing data is kept.

## 1. Alarms now work with the app closed (new `alarm/` package)

Before: alarms were checked by a 1-second loop inside the ViewModel, so they only fired while
the app was open in the foreground, and no sound was ever played.

Now:
- `AlarmScheduler` registers every enabled alarm with `AlarmManager.setAlarmClock()`
  (exempt from Doze, shows the alarm icon in the status bar). Disabled or deleted alarms are
  cancelled. Falls back to an inexact alarm if exact-alarm permission is missing.
- `AlarmReceiver` fires the alarm, schedules tomorrow's occurrence, and re-schedules everything
  after reboot, app update, clock change or time-zone change.
- `AlarmRingService` (foreground service) plays the system alarm sound on the alarm audio stream
  with a 30-second volume ramp, vibrates, shows a full-screen notification with Snooze / Stop,
  and auto-silences after 10 minutes.
- Snoozes are real one-shot system alarms and are persisted, so they survive the app being killed.
- `AlarmRingBus` mirrors ringing / snoozed / idle state into the UI, including when an alarm
  wakes a dead process.
- The in-app "RING" test button now goes through the same service, so it tests the real thing.
- `MainActivity` shows over the lock screen and turns the screen on only while ringing, and asks
  for notification permission on Android 13+.

New manifest permissions: `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` (≤ API 32),
`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`.

## 2. Timers no longer drift

Countdown and Pandora/Pomodoro timers store an absolute end time and compute the remaining
seconds from it, instead of subtracting 1 per loop iteration. A chime plays when they finish.
The clock tick is aligned to the second boundary so the seconds display never lags.

## 3. Calendar

- Automatic refresh via a `ContentObserver` (debounced) plus a 15-minute safety refresh,
  instead of only refreshing in `onResume()`.
- Event descriptions are read, and Google Meet / Teams / Zoom / Webex links are detected
  (`MeetingLinks`). The meeting spotlight shows a **Join** button.
- Duplicate events (same invite in Gmail and Outlook) are merged; the card shows "Also in: …".
- Cancelled events, declined invites and hidden calendars are skipped.
- All-day events are converted from UTC to local midnight (they could land on the wrong day),
  and they no longer force the companion into Meeting mode all day.
- Query errors are logged and exposed as `DockViewModel.calendarError` instead of silently
  returning an empty list.
- Activity classifier uses whole-word matching on title + location only. Fixes false hits like
  "eat" in "great", "run" in "rundown", "nap" in "snapshot", "sport" in "transport", and stops
  the account e-mail from influencing the result.
- Manual companion overrides (from swiping the stage) expire after 30 minutes or when a new
  calendar event starts, and are no longer wiped on every refresh.

## 4. AI companion animation (`CompanionStage.kt` (formerly `AnkerNanoAnimations.kt`))

- **Squash & stretch fixed**: X and Y now animate together (they ran one after the other),
  with Y as the volume-preserving inverse of X.
- **No more foot sliding**: roaming is driven per frame, and the step cycle comes from distance
  actually travelled, so feet stop when the body stops. Planted feet move opposite to the walking
  direction. The companion pauses at the edges and occasionally stops mid-stage.
- **Blinking**: random blinks every 2–6 s with occasional double blinks.
- **Gaze**: small random eye darts, and the face looks toward where you tap for about a second.
- **Escalating taps**: 1st tap = hop, 2nd within 2 s = spin, 3rd = playful head shake.
- **Frame-rate independent particles** using `withFrameNanos` and elapsed time (same speed on
  60/90/120 Hz screens).
- **Reduced motion**: if the system animator scale is 0 ("Remove animations"), roaming, particles
  and hops are replaced by a gentle pulse.

## 5. UI and other fixes

- Weather icons map all WMO codes (partly cloudy, rain, snow, thunderstorm, clear night) instead
  of only sun/cloud.
- Temperatures are shown as whole numbers for across-the-room readability.
- `DualCityWeather.poitiers/pittsburgh` renamed to `primaryCity/secondaryCity` (the fields hold
  whichever cities the user picks).
- `usesCleartextTraffic="true"` replaced by a network security config that only allows plain
  HTTP to `127.0.0.1`, `localhost` and the emulator host `10.0.2.2` (for the local LLM bridge).
  If you point the LLM bridge at another device on your LAN over HTTP, add its IP to
  `res/xml/network_security_config.xml`.

## 6. Tests

`app/src/test/java/com/example/CompanionLogicTest.kt` covers the classifier, join-link detection,
de-duplication and next-alarm time calculation.

## Not changed (worth a look later)

- Theme names reuse Riot Games / Valorant names (Killjoy, Omen, Valorant Crimson). Fine for
  personal use; rename before publishing.
- `DockRepository.ensureDefaultAccountsAndAlarmsSeeded()` inserts demo accounts
  (alex.personal@gmail.com etc.). Remove before publishing.
- The Gemini capability is declared but the agent only uses the local LLM bridge.
- Night Mode does not yet slow the companion down; the stage composable doesn't receive the
  night-mode flag.

## 7. New companion: realistic orange tabby cat (`OrangeCatCompanion.kt`)

The robot has been replaced by an orange tabby cat drawn entirely with vector paths and
gradients (no image assets, scales to any screen). **Long-press the stage to switch between the
cat and the original robot.** To change the default, pass `useCatCompanion = false` to
`InteractiveAiToyTopStage`.

Look: shaded orange fur with lighter back and darker belly, tabby stripes on the flanks, the
classic "M" marking on the forehead, cheek stripes, cream muzzle, chin and chest bib, pink nose
and inner ears with fur tufts, white whiskers, amber almond eyes with vertical slit pupils and
catchlights, a ringed tail with a darker tip, and soft fur tufts along the back.

Behaviour per activity:
- Roaming: walks with a real diagonal gait (paws planted, no sliding), tail held up in a curve;
  when it stops it looks around and swishes its tail.
- Working: sits at a laptop and taps the keyboard with one paw, face lit by the screen.
- Meeting: sits upright wearing a tiny headset, head tilts, an ear twitches, short meows.
- Eating: crouches over a blue food bowl and chews.
- Gym: play-hunts a ball of yarn — butt wiggle, pounce, the ball rolls away.
- Napping: "loaf" pose with sleepy slit eyes.
- Sleeping: curled up with the tail over its nose, slow deep breathing, floating Zzz.
- Alarm: startled — arched back, puffed bristled tail, ears flattened, wide pupils, meowing.
- Timer: watches an hourglass and occasionally bats at it.
- Pandora focus: calm slow blinks, gentle purr vibration, orbiting focus orbs.

The cat reuses the existing systems: random blinks, gaze toward taps, escalating tap reactions
(hop, spin, head shake), squash-and-stretch anchored at the paws, hearts on tap, and the
reduced-motion setting.

---

# Production pass (v1.1)

## Release blockers fixed
- **No demo data**: the app no longer seeds fake Gmail/Outlook accounts or two *enabled*
  alarms (07:30 and 09:00) that would have woken new users.
- **Trademark-safe names**: "Pandora" focus timer → "Focus"; themes renamed to Crimson Protocol,
  Radiant Cyan, Solar Gold, Void Violet (internal keys unchanged, so saved settings keep working).
  The optional "open the Pandora app" shortcut is kept since it links to their real app.
- **R8 shrinking** enabled for release builds, with keep rules for data models.
- **Crash capture**: `SmartDockApp` + `CrashReporter` save the last crash and offer to share it on
  the next launch. For Play-level reporting, add Firebase Crashlytics (needs your own
  `google-services.json`).
- **CI**: `.github/workflows/android.yml` runs unit tests and builds a debug APK on every push.

## Alarm reliability check + onboarding
- First launch: pick the cat's coat and name, then a checklist for exact alarms, notifications,
  full-screen alarms, battery "Unrestricted", calendar, and screensaver, each with a one-tap fix.
- Brand-specific tip with a link to dontkillmyapp.com on Samsung, Xiaomi, OnePlus, Huawei, Oppo, Vivo, Realme.
- A red "Alarms may not ring · Fix" chip appears whenever a critical permission is missing.

## Screensaver (Android Daydream)
`SmartDockDreamService`: Settings › Display › Screen saver › **Smart Dock**. Shows the cat, a large
clock, date, next alarm countdown and next meeting automatically while charging. Red and dimmed
at night, with burn-in shift.

## Always-on display care
- **Burn-in protection**: whole UI shifts a few pixels every 2 minutes (toggle in companion settings).
- **Dark-room auto dim**: ambient light sensor drops brightness to near-minimum when the lights go
  off and restores it when they come back on.

## Alarms
- **Repeat days** (M T W T F S S), one-time alarms (all days off), **skip next**, **vibrate only**,
  **math challenge to stop** (notification then offers Snooze/Open instead of Stop), and a
  **custom sound** via the system ringtone picker.
- "Rings in 7 h 23 min" under every alarm.
- Database migration 2 → 3 keeps existing alarms (they become "every day").
- **Voice assistants / other apps**: handles `SET_ALARM`, `SET_TIMER`, `SHOW_ALARMS`, so
  "set an alarm for 7" or "set a 10 minute timer" can target Smart Dock.
- **Morning briefing** after dismissing an alarm: the cat greets you with the weather and your
  first meeting; optionally read aloud (text-to-speech).
- **Sleep sounds** (evening chip, bottom-right): brown noise, rain or a purr, generated live,
  30-minute timer with gentle fade-out.

## Companion
- **Coats**: orange tabby, grey tabby, black, cream (each with matching eyes, nose and stripes).
- **Name your cat** (shown in the briefing and accessibility label).
- **Idle moments** when it stops walking: yawning, grooming a paw, stretching, looking around.
- **Long-press the cat** to open companion settings (coat, name, cat/robot, display care).
- TalkBack description on the stage ("Mochi the cat, sleep time…").

## Home-screen widget
Live clock and date (system TextClock, no battery cost), next alarm, and a sleeping cat icon.

## Localization
French translations for the app's string resources (`values-fr`). Most screen text is still
hard-coded in Kotlin; moving it into `strings.xml` is the next step for full translation.

## Tests
`AlarmRepeatTest`: weekdays/weekends, skip next, one-time alarms, day mapping, summaries, countdown.

## Still needs you (can't be done in code)
- Build and run on real devices; overnight alarm test on Samsung, Xiaomi/OnePlus and Pixel.
- Play Console: exact-alarm and full-screen-intent declarations, foreground-service type
  declaration, Data Safety form, privacy policy URL, closed testing.
- Firebase project for Crashlytics.
- A professional Rive cat (optional upgrade over the code-drawn cat).
- Splitting the very large UI files and moving all text to string resources.

## Night mode transition (v1.2)
Switching between the normal clock and the night-time-only full-screen clock (on schedule or
via the toggle) is now an animated dusk/dawn sequence instead of an instant cut
(`NightModeTransition.kt`):
- **To night (2.6 s):** the day layout softly blurs, dims, shrinks and sinks; a deep indigo dusk
  rises from the bottom with a horizon glow, a crescent moon drifts up and stars appear; then the
  giant night clock focuses in from a slight blur and settles into place.
- **To day (2 s):** the exact mirror, with a warm dawn and a rising sun.
- A very dim greeting appears for 5 seconds afterwards: "Good night · Mochi is asleep" or
  "Good morning". It never shows on app start, only on an actual switch.
- Layers exist only while visible, so there's no extra rendering cost once the switch ends.
- Blur is used on Android 12+; older versions get the same motion without blur.
- With "Remove animations" turned on in system settings, the switch is instant.

## Weather animation city (v1.3)
- New setting **Weather animation** (long-press the cat → Companion): choose which city's live
  weather plays behind the companion: primary or secondary. Falls back to the other city if the
  chosen one fails to load. Also offered during onboarding.
- Day/night sky (sun vs. stars) follows the chosen city's local time.
- **Screensaver** now fetches weather for both cities every 30 minutes, and shows the animated sky
  for the chosen city behind the cat. The date line shows only the date.

## Desk companion wellness (v1.4)
A complete, optional wellness system (`wellness/` package, `WellnessOverlays.kt`). The companion
acts out every reminder first, so it feels friendly rather than nagging.

**Reminders (each can be turned off; intervals and hours adjustable)**
- Drink water: the cat sips from a little glass; tap "I drank a glass". Daily goal, interval,
  more frequent on hot days (from the live weather), never during meetings.
- Stretch breaks after 45–60 min of sitting, with a 1-minute guided routine (6 gentle steps).
- Eye rest (20-20-20): the cat gazes out of a window.
- Breathing: 3–6 min before a meeting and after many calls; guided box breathing (4 rounds).
- A walk on sunny days when the calendar has a free 20-minute gap.
- Lunch break if no lunch is on the calendar by 13:30.
- Meeting marathon: after 3+ hours of calls, offers to block the next free 15 minutes.
- Top 3 for today: morning prompt, sticky-note scene, ticking one off makes the companion celebrate.
- End of workday (the cat packs a little bag) and wind-down an hour before bedtime (it yawns in its loaf).
- Celebrations: Friday at 5 pm, birthdays from the calendar (party hat), Santa hat 20–26 Dec,
  witch hat on 31 Oct, party hat on New Year's Day.

**Rules so it never becomes annoying**
- One nudge at a time, at least 10 minutes apart; "Later" snoozes 10 minutes.
- Automatically quiet during meetings, Focus sessions, ringing alarms, Do Not Disturb and at night
  (only the wind-down reminder appears in the evening). Desk reminders only in active hours,
  weekdays only by default.
- Shown on the companion first; a system notification only when the app isn't on screen
  (water, pre-meeting breath, wind-down, end of day).
- Desk time resets after 15 minutes away from the app.
- Everything is stored on the phone (new Room tables `wellness_log` and `daily_priorities`,
  migration 3 → 4). Described as healthy-habit nudges, not medical advice.

**Focus**
- During a Focus session the cat shows a tiny "do not disturb" sign, all reminders pause, and a
  chip offers focus sounds (brown noise, rain, and a new generated café ambience).

**Progress and motivation**
- Bottom-left chip: water glasses and Top 3 progress; tap for the Wellness sheet with today's
  summary, quick actions (+1 glass, stretch, breathe), Top 3 checklist, weekly recap
  (breaks and glasses) and water-goal streak, plus all settings.
- Companion mood ("relaxed", "cheerful", "full of energy") rises with self-care. It never becomes sad.
- Also reachable from Companion settings (long-press the cat) → "Wellness & reminders".

**Tests**: `WellnessEngineTest` covers timing, quiet rules, pre-meeting breath, lunch detection,
meeting minutes, free-slot finding, settings round trip, streaks and mood.

## Roadmap build (v1.5)

### Brand in one place
- `gradle.properties`: `brand.appId` and `brand.appName`. The app name everywhere (launcher,
  widget, screensaver) comes from there. **Set your own `brand.appId` before the first Play
  upload**; it can never change afterwards.
- Own release key: copy `keystore.properties.example` → `keystore.properties` (git-ignored).
  When present, release builds are signed with your key; otherwise the AI Studio setup is used.

### Premium (Google Play Billing 7)
- `premium/PremiumManager.kt`: monthly / yearly subscription (product `premium`, base plans
  `monthly`, `yearly`) and lifetime (`premium_lifetime`). Create these IDs in Play Console.
- Paywall with live Play prices (fallback labels until products exist), restore purchases,
  pending-payment handling, offline cache. **Debug builds** have a "Debug: unlock Premium" button
  for testing.
- Premium unlocks: all coats, pet twin, companion screensaver, sleep & focus sounds, spoken
  briefing, guided stretch/breathing. Always free: clock, alarms, orange tabby, calendar, weather,
  basic wellness reminders (logging still works without the guided sessions).

### Pet twin
- Long-press the cat → "Pet twin". Clear consent, pick a photo; Gemini returns colours and
  markings (if `GEMINI_API_KEY` is enabled in `.env`), otherwise colours are sampled on the
  phone. The photo is never stored.
- Live animated preview; editor for main fur, patch colour (calico/tortie), eye colour, stripes,
  white chest, white socks, tuxedo face, colour-point (Siamese-style).
- Dogs: recognised, drawn as a cat in the dog's colours until the dog companion ships.
- "Share my buddy": screenshot + share sheet (FileProvider).

### Animation v2
- Activity changes no longer slide the whole stage: the companion cross-fades into its new
  activity with a little squash and a soft "poof" ring.
- Ambient lighting on the companion only: warm dawn, golden hour, cool moonlight, rainy blue,
  storm grey.
- Energy "brain": lively mornings, an after-lunch dip, sleepy evenings, lifted by the user's
  self-care mood. Low energy = slower walking, more stops, more yawns, heavier eyelids.
- Tail spring physics: the tail lags behind the walking direction, swings when tapped, then settles.

### Kids OK-to-wake mode
- Long-press the cat → "Kids OK-to-wake mode". Between bedtime and wake time: black screen,
  sleeping companion, dim red "stay in bed" message. At wake time: green screen, companion
  bouncing, "Good morning! You can get up". Parents tap 5 times to reach the app.

### Tests
`PremiumTwinKidsTest`: pet twin JSON, calico rule, kids sleep/wake windows.

## Roadmap status

**Done in code:** alarms & reliability check, screensaver, widget, burn-in & dark-room dimming,
calendar with Join, wellness system, Premium + paywall, pet twin (cat), animation v2 (cross-fade,
lighting, energy, tail physics), kids mode, brand config, own signing, CI, crash capture.

**Needs you / outside the code:**
- Real-device build + overnight alarm tests; send compile errors.
- Final name & trademark; then set `brand.appId` / `brand.appName`.
- Play Console: account, products (`premium`, `premium_lifetime`), declarations, Data Safety,
  privacy policy, closed test.
- Firebase project if you want Crashlytics (needs your `google-services.json`).
- Open-Meteo commercial plan once you sell.
- A Rive animator for the studio-quality companion (and dog/fox/bunny/owl species).
- iOS version (separate Kotlin/Compose Multiplatform project).

**Next code steps I'd suggest:** dog companion (new skeleton), true walk-over transitions,
moving all screen text into string resources + full French, splitting the largest UI files.

---

## v1.6 – Real-time weather animation fixes, companion sounds & senses, and wave-to-snooze

### 1. Real-time weather animation fixes
- **Live updates:** Weather now refreshes automatically every 15 minutes in both the main app and the screensaver, and refreshes when returning to the app if the current reading is missing or more than 10 minutes old.
- **Real sunrise & sunset:** Sun vs. stars now follows the actual sunrise and sunset in the chosen city (`sunrise`/`sunset` from Open-Meteo with astronomical solar calculation fallback for the city's coordinates and timezone), checked every minute so the sky turns starry right at sunset in every season (e.g., ~18:40 CEST in late October in Poitiers instead of waiting until 20:00).
- **No more fake sunshine:** If a weather refresh fails (e.g., temporary Wi-Fi drop), the last real weather reading is preserved instead of falling back to a sunny sky.
- **Preview resets itself:** Tapping the weather badge to preview other conditions now automatically resets back to live weather after 20 seconds.
- **Corrected "Mainly clear":** WMO code `1` ("mainly clear") now displays a clear sky (`SUNNY_CLEAR` / `STARRY_NIGHT`) instead of clouds.
- **Unit tests:** Added tests in `WeatherCityChoiceTest` confirming late-October autumn sunset in Poitiers, WMO code `1` clear-sky mapping, and preserving the last real weather reading on failed refreshes.

### 2. Companion sounds, haptics & wave-to-snooze
- **Gentle companion sounds (`CompanionSoundPlayer`):** Short (< 1 s), quiet synthesized cat chirps, trills, yawns, and purrs (plus robot harmonic beeps when Robot companion is selected) with 4 subtle pitch/timing variations per cue and optional `res/raw/companion_<cue>` custom recording overrides.
  - Wellbeing reminders: water break (`mrrp?`) + lapping sound on logging water, stretch yawn, eye rest (`prrt`), breathing purr, lunch (`mew?` + bowl clink), walk trill (`brrrp`), meeting soon (two soft chirps), end-of-day sigh (`mrrow`), and wind-down yawn.
  - Reactions & celebrations: escalating 3-step tap reactions (hop `mew`, spin `mrrp!`, head shake `mrrr`), Top 3 priority ticked off, daily water goal reached, Friday/birthday celebrations, timer done, and Focus session done.
- **Quiet rules & controls:** Automatically silent during meetings, Focus sessions, ringing alarms, Do Not Disturb, night mode, and when the phone is on silent or vibrate. Simple controls in Companion settings (`All sounds` / `Reminders only` / `Off` + volume slider).
- **Matching haptics (`DockHapticFeedback`):** Added `chirp()`, `trill()`, and `heartbeat()` vibration patterns paired with companion taps and reactions.
- **Wave to snooze (`MainActivity`):** Waving a hand over the phone's proximity sensor while an alarm or timer is ringing snoozes it hands-free.

---

## v1.7 – Pixel 6a Mac Mini Webcam Ring, Screensaver on Screen-Off, 15s Idle Return & Smooth Adaptive Brightness

### 1. Pixel 6a Webcam Active Ring & Companion On-Call Mode
- **Zero-permission camera monitoring (`WebcamActivityMonitor`):** Uses `CameraManager.AvailabilityCallback` to detect whenever any camera lens on the Pixel 6a turns on (e.g., Pixel USB webcam mode for a Mac mini).
- **Exact-fit punch-hole cutout ring (`CameraCutoutRingOverlay`):** Renders only a crisp, tight (`2.2.dp`) animated Emerald Green (`#00E676`) and Radianite Cyan (`#00F0FF`) sweep-gradient ring directly on the physical camera cutout boundary (using Android 12+ `DisplayCutout.cutoutPath` and edge-unanchored `DisplayCutout.boundingRects` across `ROTATION_0`, `ROTATION_90`, `ROTATION_180`, and `ROTATION_270` with `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`), with the floating badge removed.
- **Automatic companion On-Call state:** Switches the companion into `AiToyActivity.BUSY_MEETING` (`"ON CALL • WEBCAM"`) wearing its headset while the webcam is active, and includes a 15-second preview button in Companion settings.

### 2. Automatic Screensaver on Screen-Off
- **Dual screensaver coverage:** Listens for `Intent.ACTION_SCREEN_OFF` and `Intent.ACTION_POWER_CONNECTED` in `MainActivity` with lock-screen display (`setShowWhenLocked(true)`) so pressing the power button or letting the screen turn off immediately transitions Smart Dock into its dimmed, burn-in-safe Companion & Clock screensaver over the lock screen, alongside Android's system `SmartDockDreamService` Daydream screensaver.
- **Companion settings shortcut:** Added a toggle (`Act as screensaver when screen turns off`) and a one-tap shortcut to Android System Screensaver settings.

### 3. 15-Second Idle Auto-Return to Companion
- **Automatic return (`shouldAutoReturnToCompanion`):** Tracks all touch gestures across the screen (`PointerEventPass.Initial`) and automatically animates back to the main Companion & Clock page (and closes open sheets/dialogs) after 15 seconds of inactivity on Meeting Schedules, Alarm/Timer/Focus, or Settings.

### 4. Smooth Adaptive Brightness
- **Logarithmic lux curve & EMA smoothing (`computeAdaptiveBrightnessForLux`, `smoothAdaptiveBrightness`):** Continuously maps ambient light sensor readings (`Sensor.TYPE_LIGHT`) from pitch-dark (`0 lux` → `4%`) to bright desk/daylight (`1000+ lux` → `100%`) scaled by the user's brightness ceiling and smoothed via an exponential moving average.

---

## v1.8 – Clock-Safe Companion Speech Bubble Reminders & Persistent Camera Ring with Slow Fade-Off

### 1. Persistent Camera Cutout Ring & Slow Power-Down
- **Permanently illuminated while camera is on (`WebcamActivityMonitor` & `CameraCutoutRingOverlay`):** Tracks both logical and physical sub-camera availability with a stream-reconfiguration grace window (`1200 ms`) so resolution switches never drop the ring mid-call, and converts the manual test button into a persistent ON/OFF toggle.
- **Slow 2.4-second fade-off (`CAMERA_RING_SLOW_FADEOUT_MS`):** When the camera turns off, the emerald-and-cyan cutout ring slowly powers down and fades out over `2.4 seconds` instead of snapping off abruptly.

### 2. Clock-Safe Companion Speech Bubble Reminders
- **Protected clock space (`StandByClockCard` & `computeClockSafeStagePlacement`):** The clock container reports its real-time window bounds (`boundsInWindow`), and reminders are strictly placed above `clockBounds.top` in the companion stage—automatically shrinking to a compact icon if vertical space is tight so the clock digits are never covered.
- **Companion speech bubble delivery (`WellnessOverlays`):** Replaces the bottom-center nudge card and top-center morning briefing card with a translucent speech bubble originating from the companion (e.g., `"Mrrp? Water break 💧"` with compact `Done` and `Later` buttons).
- **20-second auto-shrink & relevance expiration (`Nudge` & `DockViewModel`):** Full bubble displays for 20 seconds, then shrinks to a gently pulsing floating icon near the companion (tap the icon or the companion to reopen). Reminders auto-expire when no longer relevant (`15 min` for water/stretch/eye-rest, exact meeting start time for pre-meeting breath, `30 min` for walk/lunch, `60 min` for end-of-day/wind-down/morning briefing).
- **Priority queue (`+N` badge), swipe gestures, "While you were away" summary & night mode dim dot:** Shows the highest-priority reminder with a `"+N"` count badge (tap to view queued items), supports horizontal swipe to dismiss and upward swipe to snooze for 10 minutes, summarizes auto-expired reminders (`"2 reminders while you were away: water, stretch"`) + lists them in the Wellness sheet, and collapses reminders to a very dim dot in night mode.

---

## v1.9 – Continuous 100% Camera Cutout Glow, Unobstructed Poitiers Card & Companion-Scaled Water Bottle

### 1. Continuous 100% Camera Cutout Ring & Logical/Physical Callback Filtering
- **No false startup flash (`WebcamActivityMonitor.resolveEffectiveActiveCameraIds`):** Filters out inactive physical sub-sensor callbacks (`onPhysicalCameraUnavailable` on multi-sensor logical cameras like Pixel 6a camera `"0"`) when the parent logical camera is closed, preventing a brief flash-and-fade on app launch while keeping the camera state continuously active for the entire duration any logical camera is open.
- **Un-dimmed 100% glow (`CameraCutoutRingOverlay`):** Renders a dual-layer luminous halo + full-opacity (`alpha = ringPower`) emerald-and-cyan core ring that stays at 100% brightness while active and only fades out over `2.4 s` when the camera turns off, with no floating `CAM` / `CAM ON` badge on the companion stage.

### 2. Unobstructed "Poitiers" Primary City Weather Card & Companion-Scaled Water Bottle
- **Removed top-left overlay chip (`WellnessOverlays`):** Removed the `Alignment.TopStart` pill that sat over the "Poitiers" weather card header in landscape mode.
- **Companion-scaled desk water bottle & break badge (`AnkerNanoAnimations`):** Added a responsive, `u`-scaled desk water bottle prop on the companion stage floor beside the companion with an animated water fill level, meniscus wave, graduation ticks, and interactive stage controls (`💧 X/Y` to log `+1` glass with a companion celebration hop, and `🧘 Z` to open the Wellness sheet).

---

## v1.10 – Centralized `CameraStateViewModel`, Compose Transition Camera Ring State Machine (`4.5 dp`) & Water/Break `−` / `+` Controls

### 1. Centralized `CameraStateViewModel` & Compose `updateTransition` Camera Cutout Ring (`CameraStateViewModel`, `CameraCutoutRingOverlay`)
- **Centralized camera activity tracking (`CameraStateViewModel` & `CameraActivityCoordinator`):**
  - Centralizes hardware `WebcamActivityMonitor` telemetry and manual preview state in a single process-wide coordinator shared by `CameraStateViewModel` and `DockViewModel`.
  - Exposes `activityStatus: StateFlow<CameraActivityStatus>` and `uiState: StateFlow<CameraActivityUiState>` to manage transitions between `CameraActivityStatus.Active` (`targetBrightness = 1.0f` / 100% brightness) and `CameraActivityStatus.Inactive` (`targetBrightness = 0.0f` / fading to 0%).
- **Jetpack Compose `updateTransition` + deterministic state machine (`CameraRingPhase`, `CameraRingMachineState`, `transitionCameraRingState`):**
  - `CameraCutoutRingOverlay` uses `updateTransition(targetState = activityStatus, label = "camera_activity_transition")` with `transition.animateFloat`:
    - `Active` (`ACTIVE_SOLID`): Uses `snap()` to immediately lock at **100% solid brightness (`1.0f`)** and full `4.5 dp` core stroke while the camera is active.
    - `Inactive` (`FADING_OUT` → `OFF`): Uses `tween(durationMillis = CAMERA_RING_SLOW_FADEOUT_MS, easing = FastOutSlowInEasing)` to smoothly fade brightness from `1.0f` down to `0.0f` over `2.4 seconds` (`2400 ms`) before entering `OFF`.
- **Bolder stroke & brighter dual-layer halo:** Upgraded the active camera cutout ring core stroke to `4.5 dp` with a brighter mid-layer emerald halo (`1.85×` stroke width at `55%` alpha) and soft outer cyan bloom (`2.8×` stroke width at `28%` alpha), expanding outward from the cutout perimeter so the inner edge stays flush with the hardware lens boundary.

### 2. Increase & Decrease Controls for Water and Breaks & Smooth Water Bottle Fill Animation (`DockDatabase`, `WellnessRepository`, `DockViewModel`, `AnkerNanoAnimations`, `WellnessOverlays`)
- **Room DAO & Repository decrement support (`deleteLatestWellnessLogForTypes` / `removeLatestLog`):** Deletes the most recent `WATER` or break (`STRETCH`, `EYE_REST`, `WALK`, `BREATH`, `LUNCH`) entry for today's `dayKey` when decrementing, clamped at `0`.
- **Companion stage `−` / `+` pills (`AnkerNanoAnimations`):** Added `−` and `+` buttons to both the water pill (`[−] 💧 X/Y [+]`) and break pill (`[−] 🧘 Z [+]`) on the companion stage; the desk water bottle prop immediately animates its fill level up or down to match.
- **Smooth desk water bottle fill animation (`rememberWaterBottleFillAnimationState`, `WaterBottleSmoothFillEasing`, `drawCompanionWaterBottleProp`):**
  - Smoothly interpolates the liquid fill level (`680 ms` cubic-bezier ease-out `WaterBottleSmoothFillEasing`) relative to `waterToday / waterGoal` without overshoot clipping.
  - Dynamically boosts dual-wave meniscus sloshing (`computeWaterBottleSloshIntensity`) and rising hydration micro-bubbles while the water level is actively rising or draining.
  - Renders goal-relative graduation ticks (`computeWaterBottleGoalTicks`) along the bottle wall that light up as each glass milestone is reached, and smoothly cross-fades the liquid, cap, and bottle rim from hydration cyan to emerald green when the daily goal is met.
- **Wellness sheet `−1` / `+1` steppers (`WellnessOverlays`):** Added `−1 💧` / `+1 💧` and `−1 🧘` / `+1 🧘` stepper buttons inside the Wellness sheet's "Today" section alongside `Guided Stretch` and `Guided Breathe`.






---

## v1.8 – Safety fixes, reliable meeting alerts, privacy mode

### Critical fixes
- **Lock screen is safe again.** "Act as screensaver when screen turns off" is now **off by
  default**, never turns the screen on by itself, and while the phone is locked the dock shows
  only `LockedDockOverlay`: clock, date, next alarm and the companion. No meeting titles, notes,
  assistant, settings or purchases. Any tap asks to unlock (`requestDismissKeyguard`). A ringing
  alarm can still be snoozed or stopped from the lock screen; alarms with a math challenge ask
  to unlock first. Only a ringing alarm may turn the screen on.
- **Auto-return never loses text.** It waits 60 s (was 15 s), never runs while a dialog, sheet or
  the keyboard is open, and no longer closes dialogs.
- **Wave-to-snooze needs a real wave.** `WaveGestureDetector` requires far → near → far with the
  hand over the sensor for 60 ms–1.2 s, plus a 1.5 s cooldown. Face-down or covered phones and
  readings that start "near" are ignored. The proximity sensor is registered only while an
  alarm or timer is ringing. Waving snoozes; stopping still needs a tap.

### Improvements
- **Reliable meeting alerts** (`alarm/MeetingAlerts.kt`): system-scheduled reminders 1, 5, 10 or
  15 minutes before each meeting, firing even when the app is closed; re-scheduled when the
  calendar or settings change and after reboot. High-priority notification with a **Join**
  button (Meet/Teams/Zoom/Webex), auto-removed 15 min after the start. Skips all-day, completed,
  declined and cancelled events; duplicates merged. Long-press the cat → "Meeting alerts & privacy".
- **Privacy mode:** option to hide meeting titles on the screensaver and locked view.
  Lock-screen notifications never show titles (private notification with a public version).
- **Low-power companion:** at night (22:00–06:00) or in battery saver, the companion's animation
  "brain" updates at about 20 fps instead of every frame.
- **Cleaner code naming:** `AnkerNanoAnimations.kt` renamed to `CompanionStage.kt`.
- Wording: the webcam feature refers to Pixel's USB webcam mode (not Apple's "Continuity").

### Tests
- `CompanionLogicTest`: auto-return 60 s and never with dialog/keyboard open.
- `SafetyAndMeetingAlertsTest`: wave detection (valid wave, covered/face-down, no baseline,
  cooldown) and meeting-alert planning (future timed events only, look-ahead, duplicates).
- Weather daylight is covered by the existing `WeatherCityChoiceTest` (late-October Poitiers sunset).

### Not done in this pass (bigger refactors)
- Splitting `CompanionStage.kt` (~3,150 lines), `DockDialogs.kt` and `DockViewModel.kt` into
  smaller files, moving all screen text into string resources, and screenshot UI tests. These
  are best done in Android Studio with a compiler, one file at a time.
