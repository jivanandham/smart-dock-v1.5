# Implementation Plan: High-Tech Mecha AI Toy Companion & Fluid Physics Animations

## User Review & Confirmed Design Choices
Based on your input, the AI Toy Companion stage will be upgraded with the following specifications:
1. **Visual Style**: High-tech mecha chassis with glowing neon accents (cyan/amber/magenta circuit traces), articulated robotic limb joints (elbows, knees, ball sockets), illuminated visor with active HUD scanlines, and an energy reactor core.
2. **Animation & Motion**: Fluid physics-based locomotion with inverse-kinematic walking cadence, spring-damped vertical inertia, multi-phase idle breathing (expansion/contraction of torso and audio antennae), and momentum tilting.
3. **Interactivity & Sensory Feedback**: Interactive tap detection triggering a playful booster hop/backflip, floating holographic heart and spark emotion particles, paired with a tactile micro-haptic purr vibration.

---

## 1. Overview & Core Concept
The AI Toy Companion is the living centerpiece of the Anker Nano smart dock experience. Currently, the companion uses a simplified flat cartoon robot silhouette. This upgrade revamps the top stage with:
- **Articulated Cyber-Chassis**: Distinct mechanical plates, brushed metallic gradients (`Brush.linearGradient`), reinforced joint hubs, and glowing neon conduits.
- **Dynamic Visor Display**: An animated LED/OLED faceplate with expressive cyber optics (blinking, scanlines, expression shifting per activity state).
- **Physics-Informed Motion**: Replacing rigid sinusoids with smooth harmonic oscillations, spring-like vertical bobs that compress upon landing, and cadence-matched limb swing.
- **Micro-Haptic & Particle Tap Interactions**: When tapped, the toy reacts instantly with an energized bounce, pulsing its neon accents, launching floating heart/spark particles, and triggering a gentle cascading micro-haptic vibration sequence.

---

## 2. Visual Design & Activity Expressions

### 2.1 Cyber-Mecha Anatomy
- **Chassis Finish**: Dual-tone cyber plating (Deep Titanium `#181F2C` base with reinforced Carbon Trim `#2A3447` and polished Chamfers).
- **Neon Glow Accents**:
  - *Standard / Roaming*: Cyan / Neon Teal glow (`#00F0FF`, `#00C2D1`).
  - *Working / Focus / Meeting*: Electric Violet / Azure (`#7B61FF`, `#3B82F6`).
  - *Eating / Gym / Active*: Energy Amber / Solar Orange (`#FF9900`, `#FF5500`).
  - *Napping / Sleeping*: Low-frequency Bioluminescent Indigo (`#4338CA`, `#6366F1`) in dim standby mode.
  - *Alarm / Alert*: Vivid Crimson & Hyper-Gold alert flash (`#FF2A6D`, `#FFD166`).
- **Articulated Joints**: Segmented arms and legs featuring circular servo joint caps, dual-pivoting knees/elbows with metallic pivot pins, and magnetic hovering thruster pods for flight/jump modes.
- **HUD Visor & Eye Display**: A rounded pill visor containing vector LED elements, animated horizontal scanlines running at 60fps, and adaptive eye shapes (e.g. curved cyber eyes for happy, focused narrow bars for working, dynamic pulsing waveforms during audio/alarms).

### 2.2 Activity-Specific Enhancements
| Activity Mode | Mechanical Presentation | Primary Animation |
| :--- | :--- | :--- |
| **ROAMING** | Free-roam servo strides, antenna scanning | Dynamic step stride, side-to-side torso bank, foot thruster dust sparks |
| **WORKING / CODING** | Deployed holographic mini-terminals | Fast mechanical keyboard typing, holographic projector beam, HUD data stream |
| **BUSY / MEETING** | Presenting posture with animated cyber-tablet | Gesturing robotic hands, live sound-wave visualizer on visor |
| **EATING / CHARGING** | High-energy induction battery socket & battery pod | Sipping glowing neon power-cord juice, battery level gauge filling on chest |
| **GYM / TRAINING** | Pneumatic piston dumbbells with hydraulic steam vents | Arm curling with piston extension/compression, exhaust puffs, glowing reactor |
| **NAPPING / SLEEPING**| Standby chassis lock, low-power pulse | Slow 0.25 Hz reactor glow, rhythmic chest drop, drifting neon 'z Z' particles |
| **ALARM / TIMER** | Full alert mode, emergency beacon overdrive | Energetic siren hop, pulsating beacon antenna, flashing visor hazard chevrons |

---

## 3. Key Product Decisions & Architecture

```
+----------------------------------------------------------------------------------+
|                             InteractiveAiToyTopStage                             |
|                                                                                  |
|   +-----------------------+     +-----------------------+                        |
|   | Tap Gesture Detector  |     | Animatable Physics    |                        |
|   | - pointerInput(Unit)  |---->| - hopOffsetY (Spring) |                        |
|   | - microHapticsPurr()  |     | - tapRotation (Inert) |                        |
|   +-----------------------+     | - idleBreathScale     |                        |
|                                 +-----------------------+                        |
|                                             |                                    |
|                                             v                                    |
|                 +-----------------------------------------------+                |
|                 |          Canvas Draw Pipeline (60 FPS)        |                |
|                 |  1. Shadow & Ground Projection Ring           |                |
|                 |  2. Reactor Core & Ambient Neon Aura          |                |
|                 |  3. Articulated Legs & Servo Pivot Joints     |                |
|                 |  4. Mecha Torso Plates & Circuit Conduits     |                |
|                 |  5. Articulated Arms, Hands & Activity Props  |                |
|                 |  6. Neck Collar & Head Chassis                |                |
|                 |  7. LED Visor, Cyber Eyes & Scanlines         |                |
|                 |  8. Antennas / Audio Ear Pods                 |                |
|                 |  9. Emotion Particles (Hearts, Sparks, Rings) |                |
|                 +-----------------------------------------------+                |
+----------------------------------------------------------------------------------+
```

### 3.1 Vector Canvas vs. Heavy Assets
- **Decision**: Keep the entire rendering logic in native Jetpack Compose `Canvas` with hardware acceleration.
- **Rationale**: 
  - Zero APK size bloat (no heavy webp or Lottie files).
  - Perfectly scalable to any resolution (dock landscape, tablet expanded, or compact).
  - Enables sub-frame physics interpolation (e.g. dynamic jump heights based on tap velocity, smooth cross-fading when activity changes).

### 3.2 Physics Simulation & Spring Kinematics
- **Vertical Damping**: We use `Animatable` with `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)` for tap hops. Landing delivers a slight body squash (`scaleY: 0.92`, `scaleX: 1.08`) before restoring to resting shape.
- **Harmonic Walking Cadence**: Forward and backward limb swings are modulated by sine-cosine phase offsets (`sin(walkPhase)` and `cos(walkPhase)`) with knee flexion computed so the feet clear the ground naturally rather than sliding.
- **Emotion Particle Emitter**: Tap events spawn a transient pool of lightweight vector particles (`HeartParticle`, `NeonSparkParticle`) that float upward with randomized trajectories and fade out over 1.2s.

### 3.3 Micro-Haptic Purr Coupling
- Uses the established Android dock haptic feedback pipeline (`Vibrator` / `CombinedVibration` / `VibrationEffect` with fallback to `HapticFeedbackConstants`).
- When tapped, triggers a custom 3-pulse tactile waveform:
  - Phase 1: Crisp click (50ms)
  - Phase 2: Gentle rumble harmonic (80ms at low amplitude)
  - Phase 3: Satisfying release pulse (30ms)

---

## 4. Implementation Steps & Verification Plan

### Step 1: Particle & Physics Models
- Add lightweight immutable state classes in `AnkerNanoAnimations.kt`:
  - `data class ToyEmotionParticle(id: Long, x: Float, y: Float, vx: Float, vy: Float, scale: Float, alpha: Float, isHeart: Boolean)`
  - Tap interaction state manager with coroutine-driven particle lifecycles.

### Step 2: Articulated Mecha Anatomy Drawing Helpers
- Refactor the canvas rendering inside `InteractiveAiToyTopStage`:
  - `drawMechaLeg(...)`: Dual-segment leg with knee joint hub, hydraulic cylinder line, and mechanical foot pad.
  - `drawMechaArm(...)`: Upper arm, articulated elbow hinge, and mechanical gripper hand with activity props.
  - `drawMechaTorso(...)`: Curved bevel armor plates, neon conduit lines (`Path` with stroke glow), and central pulsating reactor gem.
  - `drawMechaHead(...)`: Rounded cyber-helm, side ear sensors/thrusters, antenna beacon, and visor with real-time scanline sweep.

### Step 3: Interactive Tap & Spring Motion Engine
- Attach `pointerInput(Unit) { detectTapGestures { ... } }` to the stage container.
- Launch concurrent animations:
  - Jump/hop spring (`hopOffsetY` animating from 0 to -35dp and rebounding).
  - Mecha tilt angle (`hopTilt` slight playful roll).
  - Particle spawn (2-4 neon hearts and energy sparks).
  - Haptic purr feedback via dock haptic service.

### Step 4: Verification & Polish
- Run `compile_applet` to ensure clean build with zero regressions.
- Run `gradle :app:testDebugUnitTest` to guarantee all existing Robolectric and unit tests pass.
- Verify smooth rendering across all 10 activities (`ROAMING`, `WORKING`, `BUSY_MEETING`, `EATING`, `GYM`, `NAPPING`, `SLEEPING`, `ALARM`, `TIMER`, `PANDORA_TIMER`).
