package net.shoresmp.core;

import java.util.*;

/** Time-bounded AND byte-bounded compressed frame storage. All timestamps use monotonic nanos. */
public final class FrameRing {
    public record Frame(long nanos, byte[] jpeg, int width, int height) {}
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final long durationNanos, maxBytes;
    private long bytes;
    public FrameRing(long durationNanos, long maxBytes) { this.durationNanos = durationNanos; this.maxBytes = maxBytes; }
    public synchronized void add(Frame frame) {
        if (!frames.isEmpty() && (frames.getLast().width() != frame.width() || frames.getLast().height() != frame.height())) clear();
        if (!frames.isEmpty() && frame.nanos() <= frames.getLast().nanos()) return;
        frames.addLast(frame); bytes += frame.jpeg().length;
        while (!frames.isEmpty() && (bytes > maxBytes || frame.nanos() - frames.getFirst().nanos() > durationNanos)) {
            bytes -= frames.removeFirst().jpeg().length;
        }
    }
    public synchronized List<Frame> snapshot(long endNanos) {
        return frames.stream().filter(f -> f.nanos() <= endNanos && endNanos - f.nanos() <= durationNanos).toList();
    }
    public synchronized void clear() { frames.clear(); bytes = 0; }
}
