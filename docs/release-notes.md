CodeGaze's first public research preview brings a live IntelliJ editor into a virtual monitor and records estimated code-token targets with timestamps.

### Downloads

- **CodeGaze-0.1.0.zip** — installable IntelliJ plugin. Use Settings → Plugins → gear → Install Plugin from Disk. Do not extract this ZIP.
- **CodeGaze-Windows-x86_64.zip** — native OpenXR viewer for SteamVR and compatible runtimes.
- **CodeGaze-Linux-x86_64.zip** — experimental native viewer; requires an available OpenXR runtime.
- **CodeGaze-macOS-universal.zip** — native desktop preview; no macOS headset-driver support is claimed. For Quest with a Mac, use the plugin's WebXR viewer over USB forwarding.
- **CodeGaze-source.zip** — source, contribution guide, synthetic demo and analysis tools.
- **SHA256SUMS.txt** — checksums for the downloadable ZIPs.

### Included

- Eye-gaze capability detection and centre-of-view head-pose fallback, explicitly labelled per sample.
- Frame-bound token mapping, source offsets, unsaved-document hashes and PSI symbol resolution where available.
- Browser session controls, a mouse simulator and a WebXR head viewer.
- Local CSV/JSONL recording and ZIP export, plus a token dwell analysis CLI.
- Installation, contribution, architecture, data-format and hardware-acceptance guides.

### Validation and limits

This release is built from a commit that passes its GitHub checks. Tests exercise geometry, tracking fallback, recording/export, IntelliJ source resolution and editor rendering. A synthetic browser recording/export flow was also exercised manually.

**No physical headset acceptance test has been performed in the implementation environment.** Device/runtime interoperability and target accuracy still require validation on your hardware. This is a research preview, not a validated measurement instrument.

The virtual monitor shares the active editor, not the entire OS desktop. Other IDE panels/popups are not mapped. Editor images update at approximately 5 Hz and observations at approximately 30 Hz. Sensor acquisition timestamps and calibrated uncertainty are not supplied; software observation/receipt timestamps are retained instead. Native binaries are unsigned. Head direction does not measure eye motion.

Follow the README's installation guide and docs/validation.md before collecting study data.
