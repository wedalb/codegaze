package io.codegaze.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Writes continuously to disk; exports do not need to retain samples in memory. */
public final class Recorder implements AutoCloseable {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private final Path base;
    private Model.Session session;
    private Path directory;
    private BufferedWriter events, csv, frames;
    private long count;
    private final Map<String, Long> lastSequences = new HashMap<>();
    private final Set<Long> savedFrames = new HashSet<>();
    private String lastError;

    public Recorder(Path base) { this.base = base; }
    public synchronized Model.Session start(Map<String, Object> metadata) throws IOException {
        if (events != null) throw new IllegalStateException("A recording is already active");
        String id = UUID.randomUUID().toString();
        directory = base.resolve(id + ".codegaze-session");
        Files.createDirectories(directory);
        session = new Model.Session(id, System.currentTimeMillis(), Map.copyOf(metadata));
        Files.writeString(directory.resolve("session.json"), JSON.toJson(session), StandardCharsets.UTF_8);
        events = Files.newBufferedWriter(directory.resolve("samples.jsonl"), StandardCharsets.UTF_8);
        csv = Files.newBufferedWriter(directory.resolve("samples.csv"), StandardCharsets.UTF_8);
        frames = Files.newBufferedWriter(directory.resolve("frames.jsonl"), StandardCharsets.UTF_8);
        csv.write(Csv.row("session_id", "server_sequence", "received_utc_ms", "received_monotonic_ns",
                "client_id", "client_sequence", "client_monotonic_ms", "client_utc_ms", "source",
                "fallback_reason", "valid", "frame_id", "frame_captured_utc_ms", "frame_age_ms",
                "u", "v", "mapping_status", "file", "document_revision", "token", "token_kind",
                "line_1based", "column_1based", "start_offset_utf16", "end_offset_utf16_exclusive",
                "symbol", "symbol_kind", "sensor_time", "sensor_time_basis"));
        count = 0; lastError = null; lastSequences.clear(); savedFrames.clear();
        return session;
    }
    public synchronized Model.Event record(Model.Sample sample, Model.Frame frame) throws IOException {
        Mapper.validate(sample);
        if (events == null) throw new IllegalStateException("Start recording before sending samples");
        Long last = lastSequences.get(sample.clientId());
        if (last != null && sample.sequence() <= last) throw new IllegalArgumentException("Duplicate or out-of-order sample");
        Mapper.Result result = Mapper.map(frame, sample);
        long now = System.currentTimeMillis();
        Model.Event event = new Model.Event(++count, session.id(), now, System.nanoTime(), sample,
                result.status(), frame == null ? null : frame.capturedEpochMs(),
                frame == null ? null : Math.max(0, now - frame.capturedEpochMs()), result.target());
        try {
            if (frame != null && savedFrames.add(frame.id())) {
                // Images are deliberately not persisted; token geometry and revisions are retained.
                Model.Frame metadata = new Model.Frame(frame.id(), frame.capturedEpochMs(), frame.capturedMonoNs(),
                        frame.width(), frame.height(), null, frame.title(), frame.status(), frame.targets());
                frames.write(JSON.toJson(metadata)); frames.newLine(); frames.flush();
            }
            Model.Target t = event.target();
            csv.write(Csv.row(session.id(), event.serverSequence(), now, event.receivedMonoNs(),
                    sample.clientId(), sample.sequence(), sample.clientMonoMs(), sample.clientEpochMs(), sample.source(),
                    sample.fallbackReason(), sample.valid(), sample.frameId(), event.frameCapturedEpochMs(), event.frameAgeMs(),
                    sample.u(), sample.v(), event.mappingStatus(), t == null ? null : t.file(), t == null ? null : t.revision(),
                    t == null ? null : t.text(), t == null ? null : t.kind(), t == null ? null : t.line(), t == null ? null : t.column(),
                    t == null ? null : t.startOffset(), t == null ? null : t.endOffset(), t == null ? null : t.symbol(),
                    t == null ? null : t.symbolKind(), sample.sensorTime(), sample.sensorTimeBasis()));
            events.write(JSON.toJson(event)); events.newLine();
            csv.flush(); events.flush();
            lastSequences.put(sample.clientId(), sample.sequence());
            return event;
        } catch (IOException error) {
            lastError = error.getMessage(); stop(); throw error;
        }
    }
    public synchronized void stop() throws IOException {
        IOException failure = null;
        for (Writer writer : new Writer[]{events, csv, frames}) if (writer != null) {
            try { writer.close(); } catch (IOException e) { failure = e; }
        }
        events = null; csv = null; frames = null;
        if (failure != null) throw failure;
    }
    public synchronized Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recording", events != null); out.put("samples", count);
        out.put("sessionId", session == null ? null : session.id());
        out.put("directory", directory == null ? null : directory.toString());
        out.put("error", lastError);
        return out;
    }
    public synchronized byte[] export() throws IOException {
        if (directory == null) throw new IllegalStateException("No session to export");
        if (events != null) throw new IllegalStateException("Stop recording before exporting");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : List.of("session.json", "samples.jsonl", "samples.csv", "frames.jsonl")) {
                zip.putNextEntry(new ZipEntry(name)); Files.copy(directory.resolve(name), zip); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    @Override public void close() throws IOException { stop(); }
}
