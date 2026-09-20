package io.codegaze.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FrameStore {
    private final int limit;
    private final Map<Long, Model.Frame> frames = new LinkedHashMap<>();
    public FrameStore(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Frame limit must be positive");
        this.limit = limit;
    }
    public synchronized void add(Model.Frame frame) {
        frames.put(frame.id(), frame);
        while (frames.size() > limit) frames.remove(frames.keySet().iterator().next());
    }
    public synchronized Model.Frame get(long id) { return frames.get(id); }
    public synchronized void clear() { frames.clear(); }
}
