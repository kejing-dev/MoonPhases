# Moon Phases · 月相课

A PICO OS 6 Spatial SDK app (Kotlin + ECS, BOM 6.1.9). You are the Earth: a sun lamp 4 m ahead, the
Moon at arm's length in front of you. Turn left on the spot and the eight phases appear one by one,
then two eclipses and a five-question quiz. Fully offline, no permissions.

- Package / applicationId: `io.embodit.moonphases`, version 0.1.0
- Container: the default Stage, **Progressive**, immersion 100 / 50 / 100 (`AndroidManifest.xml`); no WindowContainer
- Design package: `spatial-app-factory/apps/moon-phases/design-package.json` (archetype C, 16 states)

## Build

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew.bat assembleDebug --no-daemon
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. `gradle.properties` keeps the build JVM at 2 GB and the
daemon off (several projects build on this machine at once).

## Layout of the code (`app/src/main/java/io/embodit/moonphases`)

```
Main.kt                 DefaultStage { MoonTheme { MoonScreen() } }
platform/               SpatialApplication, LaunchActivity, HeadTracker (HMDTrackingProvider), Chime (ToneGenerator), MoonLog
domain/                 MoonMath (headings, quaternions, spring), Lesson (steps, quiz, numbers), UiState (states, derived values)
ui/MoonViewModel.kt     the state machine; yaw0 (the Sun's heading) and the floor origin live here so a pause survives
ui/MoonScreen.kt        SpatialView + the pre-built attachment panels (card, phase label, two edge labels, settings)
ui/card/                LessonCard (content per state), SettingsPanel, Diagrams (phase icons, top view: Canvas)
ui/theme/               PicoTheme with the design's accents, Noto Sans / Noto Sans SC, in-app language (str())
scene/MoonScene.kt      ECS: Sun (core + glow + corona + the one directional light), Moon (PBR, procedural texture),
                        orbit ring, floor ring (8 notches), stars, hint arrow, panel holders; onFrame() every frame
scene/SceneKit.kt       SDK helpers, shared resources, programmatic meshes and textures
debug/                  DebugCommandReceiver + DebugHost (the QA broadcast)
```

Conventions: headings in degrees, 0 = -Z, positive = turning left (counter-clockwise from above).
θ = wrap360(moonYaw − yaw0). All 2D UI is SpatialUI inside PicoTheme; no Material imports (factory gate).

## Debug broadcast (QA screenshots)

Debug builds only. One command per broadcast; log tag `MoonPhases` answers every command (`adb logcat -s MoonPhases`).

```bash
adb -s <serial> shell am broadcast -a io.embodit.moonphases.DEBUG -p io.embodit.moonphases --es cmd "goto S03 step=3"
adb -s <serial> shell am broadcast -a io.embodit.moonphases.DEBUG -p io.embodit.moonphases --es cmd "theta 40"
```

| Command | What it does |
| --- | --- |
| `goto <S00..S13> [step=n] [q=n] [page=n] [flat=0\|1] [correct=0\|1] [score=n]` | jump to a base state with its parameters (`step` 1..8 lesson, 9/10 eclipse; `q` 1..5 picks S09/S10 by question type) |
| `goto S14` / `goto S15` | settings overlay / restart confirmation over the current base state |
| `theta <deg>` | moves the Sun so that the Moon, which stays in front of the camera, shows this phase angle (0 new, 90 first quarter, 180 full, 270 third quarter) |
| `flat 0\|1` | orbit tilted / flat (eclipses only happen flat) |
| `settings 0\|1` | settings overlay |
| `mod M1=1` `mod M2=1` `mod M4=1` `mod M7=1` (`=0` clears) | force the overshoot reading / the hint arrow / lost tracking / the "Sun is this way" sign. M5 (low immersion) is the system crown, M6 is `hold` |
| `lang zh\|en` | language |
| `hold 0\|1` | the Moon held (M6): frozen, blue ring, judging paused |

### Screenshot list (design package `qaCapture`)

| State | Commands |
| --- | --- |
| S00 Loading | launch, capture within 0.3 s (may be skipped) |
| S01 Ready | `goto S01` |
| S02 Meet | `goto S02 page=1` / `page=2` / `page=3` |
| S03 Seek | `goto S03 step=3` then `theta 40` |
| S03 + M1 | `goto S03 step=3` then `theta 110` (or `mod M1=1`) |
| S03 + M2 | `goto S03 step=2` then `mod M2=1` |
| S04 Got it | `goto S04 step=3` |
| S05 Lap | `goto S05` |
| S06 Eclipse seek | `goto S06 flat=0` / `goto S06 flat=1 step=9` |
| S07 Solar | `goto S07` then `theta 0` |
| S08 Lunar | `goto S08` then `theta 180` |
| S09 Quiz turn | `goto S09 q=1` |
| S10 Quiz pick | `goto S10 q=2` |
| S11 Feedback | `goto S11 q=4 correct=0` |
| S12 Finish | `goto S12 score=4` |
| S13 Free | `goto S13` then `theta 100` |
| S14 Settings | `goto S03 step=2` then `settings 1` |
| S15 Restart confirm | `goto S15` |
| M4 Tracking lost | `mod M4=1` |
| M6 Moon held | `hold 1` |
| M7 Sun edge | `mod M7=1` |
| 8 phases | `goto S13` then `theta 0`, `45`, … `315`: the Moon's lit side must match the textbook (first quarter lit on the right, third on the left) |

Turning the camera in the emulator turns the Moon with it (it follows head yaw with a 0.12 s spring);
`theta` moves the Sun instead, so the Moon stays in front of the camera for comparable screenshots.

## Emulator findings (2026-10-26, emulator-5560, Pico_Emulator_6_1_fresh)

- `AttachmentPanel(id, size = IntSize(...))` takes the size in **pixels**, not dp: a 520 x 340 request came out
  about 200 dp wide and the content was cropped. Leave `size` out and give the content a fixed `Modifier.size`.
- A panel entity parented under a **disabled** holder never shows, also not after the holder is enabled
  (Spatial Ruler finding 1): holders stay enabled; a hidden panel is stowed 40 m below at scale 0.001.
- Start-up: 11 resource creations are queued and paced (2 per frame at > 25 fps, 1 per 2.5 s on the idle
  emulator); each took 8-90 ms, the procedural Moon texture ~1 s on the CPU. Two clean launches, 60 s each: no ANR.
- `createBox` renders twice the asked size on the emulator: the floor notches use a unit cube from a MeshModel.
- The emulator room's floor is at head − 1.25 m (head 1.65, floor 0.40): `floorHeight()` uses that on an x86 build.
- Camera turning (right-button drag with `camera_rotation_button` on) turns the head yaw; the Moon followed and
  the judge fired at θ = 90.6 (`STATE matched -> S04`). `theta` moves the Sun instead, for comparable screenshots.
- Settings pop-up: beside the card (left), not above it, where it covered the Moon (deviation recorded).
- 30 open/close cycles of the settings panel and the phase sign: no black glass, no crash.

## Known gaps

- Never run on a headset. Check there first: the Moon's dark side under the directional light alone (immersion 100),
  pinch-and-hold on the Moon (N7; a short tap holds it 3 s as the fallback), the card following a full body turn.
- The lap animation of S05 (notches flashing one after another) is not animated: all eight light up at once.
- The hint arrow (M2) also appears 10 s after a `goto` without turning, as designed; QA shots taken late show it.
