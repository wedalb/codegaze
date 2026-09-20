# Data format — protocol 1

A session export is a ZIP containing session.json, samples.csv, samples.jsonl and frames.jsonl. JSONL is the authoritative representation; CSV is a flattened view and prefixes spreadsheet-formula-like strings with an apostrophe. Token text is exact in JSONL.

## Sample event

- serverSequence: session-local increasing receipt sequence.
- sessionId: UUID of the recording.
- receivedEpochMs / receivedMonoNs: server wall clock and monotonic receipt time.
- sample.clientId / sequence: identifies a producer and detects duplicate/out-of-order submissions. Gaps are observable and should be reported in analysis.
- sample.clientMonoMs / clientEpochMs: software observation time on the client's monotonic and wall clocks. Browser WebXR uses its animation-frame timestamp; the native client uses Godot's monotonic clock.
- sample.source: eye, head, or simulated. Never pool these without an explicit analysis decision.
- sample.fallbackReason: eye_tracking_unavailable, eye_tracking_lost, head_forced, webxr_head_only or mouse_simulator; null for native eye gaze.
- sample.valid: validity of the chosen source. Off-screen is distinct from lost tracking.
- sample.frameId: the captured editor texture referenced by the observation.
- sample.u / v: normalized coordinates from the image top-left; null if the ray misses. The valid domain is [0,1) on each axis.
- sample.origin / direction: three-dimensional ray in the client's tracking/world coordinate space. Null for the mouse simulator. Coordinates are not portable across recenter operations; use UV for source mapping.
- sample.sensorTime: null in v0.1. No sensor acquisition-time guarantee is made.
- frameCapturedEpochMs / frameAgeMs: server capture time and age at receipt. This is not measured display latency.
- mappingStatus: mapped, no_token, off_screen, tracking_lost, ambiguous, frame_expired, or frame_unavailable.
- target: null when unmapped; otherwise the exact token at the estimated coordinate in that frame.

## Target

A target includes text, lexical kind, project-relative file name, SHA-256 document revision, 0-based UTF-16 startOffset/endOffset (end exclusive), 1-based line/column, optional symbol definition reference and symbolKind, and one or more image rectangles. Symbol references identify a definition by file, source offset and name within the recorded snapshot context; they are not stable identifiers across arbitrary edits/refactors. Resolution may be unavailable while indexing or for unsupported language constructs.

Frames retain visible token text and rectangles but not screenshot images or complete source documents. To reproduce full source context, retain the project commit plus an appropriate record of unsaved edits separately under your study's data management process.

## Clock handling

Client and server monotonic clocks have different origins. Do not subtract them. Use clientMonoMs for within-client dwell durations. Use server receipt timestamps for arrival ordering. Wall clocks can change. Cross-client precise alignment requires additional clock synchronization that v0.1 does not implement. Polling at 30 Hz is not raw eye-camera acquisition and should not be used to claim high-frequency saccade measurements.

## Dwell analysis

The analysis CLI groups adjacent mapped samples with the same client, source, file revision and token range. A gap over 100 ms, missing sequence number, invalid sample, different token, different source, document change or backwards timestamp splits the group. The default minimum dwell duration is 100 ms. These parameters are analysis choices, not validated physiological thresholds.
