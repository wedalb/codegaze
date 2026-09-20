# Validation and hardware acceptance

## Automated checks

The CI workflow builds the IntelliJ plugin, runs Java mapping/recording tests and IntelliJ PSI integration tests, checks browser geometry/source selection, tests the synthetic HTTP workflow and dwell analysis, imports/tests Godot scripts and exports native binaries. See the exact release commit's workflow for outcomes; a successful build does not validate hardware.

## Manual acceptance before research use

Record OS, IDE build, CodeGaze version, GPU/driver, headset model/firmware, runtime name/version, eye software version, refresh rate, font, screen distance and size. Use anonymous participant IDs.

1. Install the release plugin and start sharing a short Java file. Confirm the viewer matches the active file. Validate syntax, lines and gutter; minimize/restore behavior should not be used during a study.
2. Start a mouse simulation session. Point at known identifiers, whitespace, gutter and outside the monitor. Export and check token ranges, source=simulated, and no_token/off_screen outcomes.
3. Repeat with horizontal/vertical scroll, soft wrapping, folded code and unsaved edits. Verify hashes change with edits and old displayed frames preserve old mappings. If your language/inlay configuration fails, disable it or do not use that condition until fixed.
4. Connect the native client with the headset runtime active. Confirm the scene remains stable during head motion. Recenter the virtual monitor.
5. Force head mode. Align the centre of the view with known targets and verify source=head and head_forced. Remove/occlude the headset as appropriate and verify tracking_lost rather than stale coordinates.
6. On an eye-equipped device, enable/calibrate vendor tracking. With head stationary, move the eyes among widely separated targets. Verify exported source=eye and changing hits. Turn off eye tracking or obstruct the tracker and check head fallback with the correct reason. Never infer eye functionality only from headset model.
7. Measure hit rates and spatial error on a randomized grid of identifiers at the intended font size/distance. Repeat at screen edges and after refitting the headset. Report errors and ambiguous neighbouring tokens; do not assert exact-variable accuracy from successful geometry tests.
8. Record at least 10 minutes while reading/editing. Inspect sequence gaps, invalid/expired samples, frame ages, delivery errors, UI responsiveness and disk usage. Stop, export and inspect CSV/JSONL. Confirm no real samples are labelled simulated and no head samples are labelled eye.
9. Repeat separately for WebXR on the headset browser. Verify USB port forwarding, immersive entry, recentering and head-only labeling. Do not assume success in the desktop browser implies headset-browser compatibility.

## Known first-release limitations

- No physical headset validation has been completed by the implementation environment.
- Approximately 5 Hz editor images, 30 Hz software observation; not a full-rate remote desktop or raw eye-sensor recorder.
- Active editor only. Other IDE panels/popups are not captured/mapped. Complex inlays, bidi text and custom editors require validation.
- No automatic uncertainty ellipse or participant-specific calibration model. Vendor calibration and your own accuracy study are necessary.
- No OS-level virtual display, wireless browser deployment, built-in Android APK, native macOS VR driver or remote input injection.
- Native client uses OpenXR OpenGL binding; a runtime that exposes only other graphics bindings needs a renderer/provider adaptation.
- Exported source snippets and relative paths can still be sensitive. Review before sharing datasets.
