# Thicker Camera Cutout Ring & Water/Break Increase/Decrease Controls

Enhances the active camera cutout ring so it renders with a bolder ~4.5 dp core stroke and brighter luminous halo while the camera is on, and adds `-` and `+` buttons to both the companion stage water/break pills and the Wellness sheet so you can easily increment or decrement your daily water and break counts.

## User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed preferences from our design check-in:

- **Water & Break `-` / `+` Controls**: Added directly to the companion stage water (`💧 X/Y`) and break (`🧘 Z`) pills as well as inside the Wellness sheet, allowing instant `+1` and `-1` adjustments (clamped at `0` so counts never go negative).
- **Camera Cutout Ring Thickness**: Upgraded to a medium-bold **4.5 dp** core stroke with a brighter, wider dual-layer emerald-and-cyan halo while active, fading out smoothly over `2.4 s` when the camera turns off.

## 1. Overview & Core Concept

- **What It Does**:
  1. Makes the physical camera cutout ring substantially more visible across the desk when the camera is on (`4.5 dp` core stroke + luminous outer halo) while keeping its inner edge flush with the hardware lens boundary.
  2. Lets you both increase (`+`) and decrease (`-`) today's water glass count and break count directly from the companion stage pills and from the Wellness sheet—making it effortless to log a glass/break or undo an accidental tap.
- **Target Audience / Persona**: StandBy desk-dock users who want clear at-a-glance camera activity feedback and frictionless two-way control over their daily hydration and break counters.
- **Key Value**: Mistakes or extra taps can be corrected in a single tap without resetting data, and the companion desk water bottle prop immediately reflects both increases and decreases in water level.

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Thicker Active Camera Ring**: When any camera turns on, the cutout ring illuminates at 100% brightness with a `4.5 dp` rotating emerald-and-cyan core stroke and a multi-layer outer glow halo (`~10 dp` soft outer bloom + `~6.5 dp` mid halo). When the camera closes, it powers down smoothly over `2.4 s`.
  2. **Stage Water Pill (`- 💧 X/Y +`)**:
     - Tapping `-` removes the most recent water log from today (when `waterToday > 0`), plays a subtle click haptic, and smoothly lowers the water level in the companion's desk water bottle prop.
     - Tapping the center label (`💧 X/Y`) or `+` logs `+1` glass of water, plays the water sound cue, raises the bottle fill level, and triggers a happy companion hop.
  3. **Stage Break Pill (`- 🧘 Z +`)**:
     - Tapping `-` removes the most recent break log from today (when `breaksToday > 0`).
     - Tapping `+` logs `+1` break (`STRETCH`) with a companion celebration hop.
     - Tapping the center label (`🧘 Z`) opens the full Wellness sheet.
  4. **Wellness Sheet Stepper Row**: Inside the Wellness sheet's "Today" section, dedicated `-` / `+` stepper controls for both **Water** and **Breaks** let you adjust today's counts alongside the existing guided Stretch and Breathe actions.
- **Visual Identity & Theme**:
  - *Color Palette*: Sky-cyan (`#38BDF8`) and Emerald (`#00E676`) for hydration and camera active glow; crisp monospace typography (`JetBrainsMono`) on dark translucent pills (`#0A131E` at 84% alpha).
  - *Interactive Feedback*: Enabled `-` buttons highlight cleanly when count `> 0` and dim to 35% opacity when count is `0` so zero-state bounds are immediately clear.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Removing the Latest Today Entry on Decrement (`-1`)**
  - *Chosen Approach*: Decrementing water deletes the most recent `WATER` log entry for today's `dayKey`; decrementing breaks deletes the most recent break log entry (`STRETCH`, `EYE_REST`, `WALK`, `BREATH`, `LUNCH`) for today's `dayKey`.
  - *Why*: Preserves accurate timestamps for earlier logs and keeps weekly recaps and hydration streaks automatically consistent without requiring a separate counter table.
- **Decision 2: Outward Ring Expansion from Cutout Edge**
  - *Chosen Approach*: As the active stroke width increases to `4.5 dp`, the ring expands outward from the cutout perimeter (`halfStroke = strokeWidthPx * 0.5f`) rather than inward.
  - *Why*: Ensures the thicker ring frames the physical camera hole cleanly without encroaching inside the lens cutout area.

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Room Database (DockDao)                          │
│  • insertWellnessLog(log)                                               │
│  • deleteLatestWellnessLogForTypes(dayKey, types)  <── NEW (-1 support) │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    WellnessRepository & DockViewModel                   │
│  • logWellness("WATER" / "STRETCH")  ──► +1 Water / +1 Break            │
│  • decrementWaterToday()             ──► -1 Water (if waterToday > 0)   │
│  • decrementBreakToday()             ──► -1 Break (if breaksToday > 0)  │
└───────────────┬───────────────────────────────────┬─────────────────────┘
                │                                   │
                ▼                                   ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│   Companion Stage (Top Stage)     │ │          Wellness Sheet           │
│ • Desk Water Bottle Prop (0..100%)│ │ • Water Stepper:  [ - ] 💧 [ + ]  │
│ • Water Pill: [ - ] 💧 X/Y [ + ]  │ │ • Break Stepper:  [ - ] 🧘 [ + ]  │
│ • Break Pill: [ - ] 🧘 Z   [ + ]  │ │ • Guided Stretch & Breathe        │
└───────────────────────────────────┘ └───────────────────────────────────┘
```

- **Data Model & State**:
  - `DockDao`: Add `@Query("DELETE FROM wellness_log WHERE id = (SELECT id FROM wellness_log WHERE dayKey = :dayKey AND type IN (:types) ORDER BY atMillis DESC, id DESC LIMIT 1)")` to atomically remove the newest matching entry for today.
  - `WellnessRepository`: Expose `removeLatestLog(types: Set<String>, now: Long)` calling the DAO query.
  - `DockViewModel`: Expose `decrementWater()` and `decrementBreak()` which invoke `wellnessRepository.removeLatestLog(...)` for `"WATER"` or `WellnessRepository.BREAK_TYPES`.
- **Camera Cutout Ring (`CameraCutoutRingOverlay`)**:
  - Increase active core stroke to `4.5.dp.toPx()` (scaling smoothly with `ringPower`), paired with a brighter mid-halo (`strokeWidthPx * 1.85f` at `0.55f * alpha`) and soft outer bloom (`strokeWidthPx * 2.8f` at `0.28f * alpha`).
