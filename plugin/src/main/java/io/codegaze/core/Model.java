package io.codegaze.core;

import java.util.List;
import java.util.Map;

/** Immutable snapshots: a gaze event is always mapped against its displayed frame. */
public final class Model {
    private Model() {}
    public record Rect(double x, double y, double width, double height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
    public record Target(String kind, String text, String file, String revision,
                         int startOffset, int endOffset, int line, int column,
                         String symbol, String symbolKind, List<Rect> bounds) {}
    public record Frame(long id, long capturedEpochMs, long capturedMonoNs,
                        int width, int height, String image, String title,
                        String status, List<Target> targets) {}
    public record Sample(String clientId, long sequence, long frameId,
                         double clientMonoMs, Double clientEpochMs,
                         String source, String fallbackReason, boolean valid,
                         Double u, Double v, List<Double> origin, List<Double> direction,
                         Double sensorTime, String sensorTimeBasis) {}
    public record Event(long serverSequence, String sessionId, long receivedEpochMs,
                        long receivedMonoNs, Sample sample, String mappingStatus,
                        Long frameCapturedEpochMs, Long frameAgeMs, Target target) {}
    public record Session(String id, long startedEpochMs, Map<String, Object> metadata) {}
}
