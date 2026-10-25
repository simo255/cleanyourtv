package io.github.simo255.mediastandby;

/** Monotonic times only: a dream starts after Android has already counted user inactivity. */
final class IdlePolicy {
    private final long enteredAt;
    private final long silenceTimeout;
    private long lastPlayingAt = -1;

    IdlePolicy(long enteredAt, long silenceTimeout) {
        this.enteredAt = enteredAt;
        this.silenceTimeout = Math.max(10_000L, silenceTimeout);
    }

    boolean shouldFinish(long now, boolean playing) {
        if (playing) {
            lastPlayingAt = now;
            return false;
        }
        // Allow the audio engine to settle when Android attaches the dream.
        return lastPlayingAt < 0 ? now - enteredAt >= 2_000L
                : now - lastPlayingAt >= silenceTimeout;
    }
}
