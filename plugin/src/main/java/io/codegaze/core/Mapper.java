package io.codegaze.core;

import java.util.Set;
import java.util.List;

public final class Mapper {
    public static final Set<String> SOURCES = Set.of("eye", "head", "simulated");
    private Mapper() {}
    public record Result(String status, Model.Target target) {}

    public static void validate(Model.Sample s) {
        if (s == null || s.clientId() == null || s.clientId().length() > 128 || s.clientId().isBlank())
            throw new IllegalArgumentException("A clientId is required");
        if (!SOURCES.contains(s.source())) throw new IllegalArgumentException("Unknown tracking source");
        if (s.sequence() < 0 || s.frameId() < 0 || !Double.isFinite(s.clientMonoMs()) || s.clientMonoMs() < 0)
            throw new IllegalArgumentException("Invalid sequence, frame or time");
        if (s.clientEpochMs() != null && !Double.isFinite(s.clientEpochMs())) throw new IllegalArgumentException("Invalid epoch time");
        if (s.sensorTime() != null && !Double.isFinite(s.sensorTime())) throw new IllegalArgumentException("Invalid sensor time");
        if ((s.u() == null) != (s.v() == null)) throw new IllegalArgumentException("UV must be a pair");
        if (s.u() != null && (!Double.isFinite(s.u()) || !Double.isFinite(s.v())))
            throw new IllegalArgumentException("Invalid screen coordinates");
        validateVector(s.origin()); validateVector(s.direction());
    }
    private static void validateVector(List<Double> values) {
        if (values != null && (values.size() != 3 || values.stream().anyMatch(v -> v == null || !Double.isFinite(v))))
            throw new IllegalArgumentException("Expected a finite three-dimensional vector");
    }
    public static Result map(Model.Frame frame, Model.Sample sample) {
        if (!sample.valid()) return new Result("tracking_lost", null);
        if (frame == null) return new Result("frame_expired", null);
        if (!"ready".equals(frame.status())) return new Result("frame_unavailable", null);
        if (sample.u() == null || sample.v() == null || sample.u() < 0 || sample.u() >= 1 || sample.v() < 0 || sample.v() >= 1)
            return new Result("off_screen", null);
        double x = sample.u() * frame.width(), y = sample.v() * frame.height();
        Model.Target found = null;
        for (Model.Target target : frame.targets()) {
            if (target.bounds().stream().anyMatch(r -> r.contains(x, y))) {
                if (found != null) return new Result("ambiguous", null);
                found = target;
            }
        }
        return new Result(found == null ? "no_token" : "mapped", found);
    }
}
