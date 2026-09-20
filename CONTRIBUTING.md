# Contributing to CodeGaze

Contributions that improve measurement correctness, headset interoperability, editor mapping and reproducible testing are welcome. Open an issue describing the observed behavior and a small reproduction before a substantial redesign.

## Development environment

- JDK 21 (the Gradle wrapper downloads Gradle 8.13).
- Node.js 20+ for browser-core tests; the browser app itself has no npm dependencies.
- Python 3.10+ for the demo and analysis tools; no third-party Python packages are required.
- Godot **4.4.1 standard**, with matching export templates, for the native client.
- A compatible headset/runtime is required only for hardware acceptance tests.

```sh
git clone https://github.com/wedalb/codegaze.git
cd codegaze
./gradlew :plugin:test :plugin:buildPlugin :plugin:verifyPluginStructure -Djava.awt.headless=false
node --test tests/*.test.mjs
python3 -m unittest discover -s tests -p 'test_*.py' -v
godot --headless --xr-mode off --path native --editor --import --quit
godot --headless --xr-mode off --path native --script test_tracking.gd
python3 tools/test_native_client.py
```

On Windows use `gradlew.bat` and `python`. Configure JAVA_HOME to point to JDK 21 if needed. The first plugin build downloads a complete IntelliJ distribution and needs several GB of disk space.

The editor-painting integration test needs a display. On a Linux CI server, prefix the Gradle command with `xvfb-run -a`, as the workflow does. With headless mode enabled, that UI test is skipped; do not count it as hardware or rendering validation.

## Run during development

```sh
./gradlew :plugin:runIde
```

This opens an isolated IntelliJ sandbox with the plugin. Open a sample project and select View → Tool Windows → CodeGaze. To work on the browser UI without the IDE, run `python3 tools/demo_server.py`; it serves the web directory directly with synthetic frames. Stop the demo before launching the actual plugin server on port 8742.

Open the native directory in Godot, or run `godot --path native`. On a machine without a VR runtime, use `--xr-mode off` for desktop preview. The native app does not manufacture tracking samples when no runtime is available.

## Layout

- plugin/src/main/java/io/codegaze/ide: IDE capture, token/PSI mapping, tool window, authenticated local HTTP.
- plugin/src/main/java/io/codegaze/core: immutable frames, mapping, recording and CSV.
- web: recorder UI, desktop simulator, WebXR head viewer, tested geometry.
- native: Godot OpenXR client and eye/head policy.
- tools: synthetic demo, dwell analysis, CI setup.
- tests and plugin/src/test: regression and integration tests.
- docs: architecture, data format and hardware acceptance.

## Contribution expectations

1. Create a focused branch and describe the concrete behavior being changed.
2. Preserve source labels, explicit validity, frame IDs and clock meanings. Never silently relabel head pose as eye gaze or guess source locations from expired frames.
3. Add a regression test for a meaningful bug or new provider behavior. For hardware changes, report the actual headset/runtime and attach an anonymized acceptance result.
4. Keep screenshots, real recordings, pairing keys and proprietary source out of commits. Use the synthetic example for public demonstrations.
5. Run the checks above. Include limitations and any untested behavior in the pull request.

Java integration tests should resolve source symbols through the actual IntelliJ test framework. Geometry tests must cover orientation, edges and misses. Language mapping changes should include wrapping/folding/inlay cases when relevant.

## Adding a headset or provider

Prefer standard OpenXR. Confirm support for XR_EXT_eye_gaze_interaction at runtime; validate each pose before using it. If a vendor-specific SDK is necessary, isolate it behind a provider and emit the same sample contract. Preserve unavailable sensor timestamps as null. Hardware with no exposed eye data must remain head-only. WebXR uses a viewer-centre ray and currently makes no eye-tracking claim.

## Releases

The Build and test workflow builds plugin/native ZIP artifacts from each commit. A release must point to a passing commit. Push a version tag such as v0.1.0 only after reviewing the passing commit. The tag workflow rebuilds and tests, packages source and SHA-256 checksums, and publishes a GitHub prerelease using docs/release-notes.md. Do not publish a hardware-validated claim without the device acceptance record. Keep release versions in plugin/build.gradle.kts, plugin service metadata, native/project.godot, export_presets.cfg, and viewer labels consistent.

License: MIT. By contributing, you agree to license your contribution under the repository's MIT license.
