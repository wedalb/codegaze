# CodeGaze

**See your live IntelliJ editor in VR. Record where a measured eye or head ray meets the code. Export the session.**

[![Build and test](https://github.com/wedalb/codegaze/actions/workflows/ci.yml/badge.svg)](https://github.com/wedalb/codegaze/actions/workflows/ci.yml)
[Download releases](https://github.com/wedalb/codegaze/releases) · [Architecture](docs/design.md) · [Data format](docs/data-format.md) · [Contributing](CONTRIBUTING.md)

CodeGaze is an open-source research preview: an IntelliJ plugin, a portable OpenXR viewer, and a browser viewer. It maps an estimated point on a virtual monitor to a code token, its source occurrence and, when available, its resolved symbol. Every record preserves its tracking source and timestamps.

> **What is verified:** automated geometry, tracking-policy, mapping, export and IntelliJ PSI tests, plus build checks. **What still needs hardware validation:** headset/runtime interoperability and measured gaze accuracy. An exact token under an estimated coordinate is not proof that a participant looked at that token. Head direction is not eye tracking.

## What it does

- Displays the active IntelliJ editor on a floating virtual monitor, including syntax highlighting and scrolling.
- Prefers eye gaze through OpenXR's standard eye-gaze extension when valid; otherwise uses the centre of the headset view.
- Labels every sample as `eye`, `head` or `simulated`, with fallback reason and validity.
- Maps against the frame actually submitted to the viewer, preserving unsaved document revisions and source offsets.
- Uses IntelliJ PSI to distinguish variable/method references when language support can resolve them.
- Saves continuously to local disk and exports CSV, JSONL samples and frame metadata in one ZIP.
- Includes a desktop mouse simulator and a synthetic demo for supervisors and contributors.

This is a **virtual editor monitor**, not an OS-level extended-display driver. Version 0.1 shares one active editor; other IDE panels and completion popups are not semantically mapped. Keep popups dismissed during a study. The editor image updates at about 5 Hz; VR head movement is rendered independently at the headset runtime's refresh rate. This is an initial research implementation, not a low-latency desktop-streaming replacement.

## Compatibility

| Setup | Viewer | Tracking route | Status |
|---|---|---|---|
| Windows + Beyond 2e + SteamVR | Native OpenXR | Eye if exposed by runtime and Bigscreen software; head fallback | Requires headset acceptance test |
| Windows + Quest via Link/Air Link or another PC VR connection | Native OpenXR | Head; eye only if hardware/runtime exposes it | Requires headset acceptance test |
| Windows + another OpenXR-compatible headset | Native OpenXR | Capability-detected eye/head | Requires headset acceptance test |
| Mac or Windows + Quest Browser over USB forwarding | Browser WebXR | Head direction | Requires headset/browser acceptance test |
| Windows/macOS/Linux without a headset | Browser simulator | Explicitly simulated mouse points | Automated data/geometry checks |
| Linux + an available OpenXR runtime | Native OpenXR | Capability-detected eye/head | Experimental; requires runtime validation |
| macOS native viewer | Desktop preview | No supported native VR route claimed | Preview only |

“Headset independent” means using standard OpenXR/WebXR interfaces and detecting capabilities. It cannot overcome missing vendor drivers, unavailable eye-gaze APIs or browser limitations. Beyond requires a supported Windows PC; Quest 3 has no built-in eye tracking. The native app uses Godot's OpenGL compatibility renderer, so the selected runtime must support OpenXR OpenGL graphics bindings.

## Install the IntelliJ plugin

1. Install **IntelliJ IDEA 2024.3 or later**. The build and automated integration tests target 2024.3.5 / build 243. Other versions need verification.
2. Download `CodeGaze-0.1.0.zip` from the release assets. This is the plugin; do not extract it.
3. In IntelliJ, open **Settings → Plugins → gear icon → Install Plugin from Disk…** and choose that ZIP. Restart if requested.
4. Open a project and a source file. Open **View → Tool Windows → CodeGaze**.
5. Select **Start sharing**, then **Open viewer & recorder**. The pairing key is passed locally to the browser.
6. Keep the IDE window visible and unminimized. Your normal keyboard/mouse remain connected to IntelliJ.

If port 8742 is occupied, stop another CodeGaze project/demo instance first. Only one project can host the service on that port at a time.

## Use Beyond 2e / SteamVR / another PC headset

1. Install and configure your headset's drivers and **SteamVR** (or a compatible OpenXR runtime). Set the intended runtime as the active OpenXR runtime in its settings.
2. For Beyond 2e, install Bigscreen's eye tracking software, complete its calibration, and ensure gaze is exposed to OpenXR. [Bigscreen instructions](https://store.bigscreenvr.com/en-gb/blogs/beyond/how-eyetracking-works-in-bigscreen-beyond-2e).
3. Download and extract `CodeGaze-Windows-x86_64.zip`; run `CodeGaze.exe`.
4. Copy the local server URL and pairing key from the IntelliJ CodeGaze tool window into the viewer. Select **Connect**.
5. Put on the headset. A virtual screen displays your editor. Press **R** in the viewer to reposition it in front of you. Focus IntelliJ when you want to type.
6. In the browser recorder, enter an anonymous participant label and select **Start recording**. The native client samples automatically while the session is active.
7. Check the native status: `eye` or `head`, and `valid` or `lost`. **Force head direction** lets you run a head-only condition explicitly.
8. Stop recording in the browser, then **Download session**. Stop sharing in IntelliJ when finished.

The native desktop settings panel is for setup; it is not a controller-operated VR menu. Releases are unsigned research binaries. No vendor connection or calibration has been fabricated by the software.

## Use Quest with a Mac or Windows through its browser

This route uses **head direction**, including on headsets with eye sensors. It does not need SteamVR or a gaming GPU on the computer.

1. Install the IntelliJ plugin and start sharing as above.
2. Enable developer mode on your Quest, install [Android platform tools](https://developer.android.com/tools/releases/platform-tools), connect the headset by USB, and approve USB debugging in the headset.
3. Run `adb devices` and confirm your headset is authorized.
4. Run `adb reverse tcp:8742 tcp:8742`.
5. In **Quest Browser**, open `http://localhost:8742`. Enter the pairing key from IntelliJ.
6. Select **Enter VR · head direction** and approve the browser's immersive-session request. Start recording before entering VR, or use the computer's browser recorder while the headset is in VR.
7. The virtual monitor appears in front of you. A controller select/trigger recenters it. Continue using the computer's physical keyboard and mouse.
8. Exit VR, stop recording and download the session. Remove forwarding with `adb reverse --remove tcp:8742` when finished.

WebXR requires a secure context; `localhost` is treated as trustworthy. A plain LAN-IP HTTP URL is not equivalent. USB forwarding is the documented first-release route; wireless HTTPS deployment is not bundled. Browser/runtime support must be validated on the actual device.

## Try it without a headset

With the plugin running, open the browser recorder, enable **Mouse simulator**, start recording and move over code. Synthetic samples are always labelled `simulated`.

For a standalone demonstration with **synthetic code**, install Python 3.10+, download the source archive and run:

```sh
python3 tools/demo_server.py
```

Open the URL printed by the command. No IDE, headset, third-party Python package or cloud account is needed. Stop the demo before starting the real plugin on the same port. The demo exercises the viewer and export shape; it does not validate IntelliJ capture or real eye tracking.

## Export and analysis

The session ZIP contains:

| File | Purpose |
|---|---|
| `session.json` | Participant label, session ID, start time and software/clock notes |
| `samples.csv` | Analysis-friendly rows with source, timestamps, UV, token, revision, offsets and mapping status |
| `samples.jsonl` | Full samples, original rays and mapped targets |
| `frames.jsonl` | Referenced content-frame metadata and visible token rectangles; no screenshot images |

`line` and `column` are 1-based. Offsets are 0-based UTF-16, with an exclusive end. Document revisions are SHA-256 hashes. Sample timestamps are **software sampling and server receipt times**; raw sensor acquisition time is not available through this integration and remains null. Client/server monotonic clocks are distinct. See [data format](docs/data-format.md).

To derive **token dwell intervals**, extract the session ZIP and run:

```sh
python3 tools/analyze.py samples.jsonl --output dwell.csv
```

This groups consecutive samples on the same source occurrence and tracking source. It is not a validated eye-fixation detector. Gaps, source changes, lost tracking and document changes split intervals.

## Before collecting study data

Follow the [hardware acceptance procedure](docs/validation.md). Validate known identifiers at the intended font size and screen distance; test scroll, wrapping, folding, edits and tracking loss. Measure actual error and data loss. Treat small adjacent identifiers as ambiguous when calibration does not support distinguishing them. Do not equate gaze with comprehension.

The plugin retains recordings in `codegaze-recordings` beneath the IDE's system directory. The viewer shows the active recording directory through `/api/status`. Recordings include source snippets and file names: keep them out of the public repository. There is no automatic upload or analytics.

## Build and contribute

See [CONTRIBUTING.md](CONTRIBUTING.md) for development, tests, release builds and adding providers. The code is MIT licensed; native releases include Godot, with [third-party notices](THIRD_PARTY_NOTICES.md).
