# Design and boundaries

CodeGaze records a measured pointing ray against a live IntelliJ editor shown on a virtual monitor. The editor continues to run locally with its normal keyboard and mouse. The virtual monitor is a captured editor surface, not an OS virtual display driver.

## Components

- IntelliJ plugin (Java 21): captures the active editor, calculates visible token rectangles from IntelliJ's own layout, resolves symbols using PSI, serves authenticated loopback HTTP, persists sessions.
- Native Godot/OpenXR client: reads the standard eye-gaze action where available. Otherwise it projects the centre head ray. Lost head tracking produces invalid samples. Rendering stays at the runtime's refresh rate while editor textures update about five times per second.
- Browser client: desktop simulator and WebXR head-pose viewer. WebXR in this application does not expose eye gaze. Quest can connect to the loopback service over Android USB port forwarding, including from macOS.
- Analysis CLI: derives token dwell intervals from exported samples, preserving source and document revision. Dwell is not a validated physiological fixation classifier.

## Geometry and data integrity

A token rectangle comes from the same EDT/read-action snapshot as its displayed pixels. Tokens have half-open UTF-16 character ranges, 1-based source line/column, a SHA-256 document revision, and a definition reference when IntelliJ can resolve it. Logical pixel coordinates avoid mixing desktop DPI scaling with the image's own dimensions.

Each viewer holds a content-frame ID alongside its texture. Samples cite that ID. The server maps against the corresponding immutable snapshot, never the current editor. A bounded history of 120 frames prevents unbounded image storage. Expired frame IDs are recorded as frame_expired. No nearest-token snapping is performed. Overlapping rectangles yield ambiguous; whitespace yields no_token. Folded-away source is not assigned to visible pixels.

Capture and live token rectangles currently cover the active editor, its source text and gutter background. Other IDE panels, completion popups and tooltips are not mapped. This release should be used with popups dismissed. Complex inlays, bidirectional scripts and unusual custom editor renderers need dedicated validation before research use. Very large tokens over 4096 UTF-16 units and frames with over 6000 highlighter tokens are bounded for responsiveness and may be unmapped.

## Clocks

Both clients record software sampling time on their monotonic clock and their wall clock. The server adds its own monotonic receipt time and UTC milliseconds; each content frame has capture times. These are separate clock domains: never subtract client and server monotonic values. The native Godot layer used here does not expose the OpenXR eye-pose acquisition time. sensorTime is therefore null, not invented. Content-frame IDs identify submitted textures, not measured photon presentation times. Sample frequency is approximately 30 Hz, not a claim about the eye sensor's native frequency.

## Source selection

Prefer eye if supported and valid; otherwise head if valid. Record eye_tracking_unavailable, eye_tracking_lost or head_forced as the reason. A head ray is the centre view direction and cannot measure independent eye movement. No runtime means desktop preview only in the native client. The browser simulator always records simulated.

## Security and storage

Sharing starts only from the IDE tool window. HTTP binds to 127.0.0.1, uses a random per-plugin-instance pairing key, validates Host and Origin, denies cross-origin requests and limits request size. The pairing key is delivered to the browser in a URL fragment and cleared from the address bar. Browser code and fonts are local; no analytics or cloud service is used.

Recordings contain visible source tokens, project-relative file names and symbol references. They remain in IntelliJ's system directory and in user-downloaded exports. Images are not persisted. Stop sharing to shut down the server. Never commit research recordings to a public repository. For remote transport use a properly secured tunnel; the service intentionally does not bind to a public network interface.

## Validation limits

Automated tests cover geometric projection, mode fallback, frame-bound mapping, invalid/off-screen samples, duplicate rejection, source resolution, recording and export. A headset acceptance test is still required for each runtime/device combination. No exact-variable accuracy guarantee is made. Calibrate using the vendor's tools, measure target hit rates at the intended font size and distance, and preserve ambiguous outcomes in analysis.
