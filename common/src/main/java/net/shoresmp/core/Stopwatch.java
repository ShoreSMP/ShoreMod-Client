package net.shoresmp.core;

import java.util.function.LongSupplier;

/** A monotonic session timer independent of tick rate, local time and frame rate. */
public final class Stopwatch {
    private enum State { STOPPED, RUNNING, PAUSED }
    private final LongSupplier clock;
    private State state = State.STOPPED;
    private long accumulated, started;

    public Stopwatch(LongSupplier clock) { this.clock = clock; }

    public void startStop() {
        long now = clock.getAsLong();
        if (state == State.STOPPED) {
            accumulated = 0;
            started = now;
            state = State.RUNNING;
        } else {
            if (state == State.RUNNING) accumulated += now - started;
            state = State.STOPPED;
        }
    }

    public void pauseResume() {
        if (state == State.STOPPED) return;
        long now = clock.getAsLong();
        if (state == State.RUNNING) {
            accumulated += now - started;
            state = State.PAUSED;
        } else {
            started = now;
            state = State.RUNNING;
        }
    }

    public void reset() { accumulated = 0; state = State.STOPPED; }

    public long milliseconds() {
        long elapsed = accumulated + (state == State.RUNNING ? clock.getAsLong() - started : 0);
        return elapsed / 1_000_000;
    }

    public static String display(long millis, boolean precise) {
        long seconds = millis / 1000, hours = seconds / 3600;
        int minutes = (int)(seconds / 60 % 60), remainder = (int)(seconds % 60);
        StringBuilder text = new StringBuilder(14);
        if (hours > 0) text.append(hours).append(':');
        if (minutes < 10) text.append('0');
        text.append(minutes).append(':');
        if (remainder < 10) text.append('0');
        text.append(remainder);
        if (precise) {
            int fraction = (int)(millis % 1000);
            text.append('.').append((char)('0' + fraction / 100))
                .append((char)('0' + fraction / 10 % 10)).append((char)('0' + fraction % 10));
        }
        return text.toString();
    }
}
