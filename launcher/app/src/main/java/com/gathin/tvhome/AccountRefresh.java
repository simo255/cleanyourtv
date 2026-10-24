package com.gathin.tvhome;

/** When a foreground Stremio library read is due. Times are SystemClock.elapsedRealtime() values. */
final class AccountRefresh {
    enum Trigger { FORCE, FOREGROUND, TICK }
    static final long FOREGROUND_GAP_MS = 60_000L, PERIODIC_MS = 15 * 60_000L;
    static final long BACKOFF_BASE_MS = 2 * 60_000L, BACKOFF_MAX_MS = 30 * 60_000L, RATE_LIMIT_MS = 10 * 60_000L;
    private long lastAttempt = Long.MIN_VALUE / 2, retryAt;
    private int failures;
    boolean due(Trigger trigger, long now) {
        if (trigger == Trigger.FORCE) return true;
        if (failures > 0) return now >= retryAt;
        return now - lastAttempt >= (trigger == Trigger.TICK ? PERIODIC_MS : FOREGROUND_GAP_MS);
    }
    void started(long now) { lastAttempt = now; }
    void succeeded() { failures = 0; retryAt = 0; }
    void failed(long now, int code) {
        failures = Math.min(failures + 1, 16);
        long delay = Math.min(BACKOFF_MAX_MS, BACKOFF_BASE_MS << Math.min(failures - 1, 4));
        if (code == 429 || code == 503) delay = Math.max(delay, RATE_LIMIT_MS);
        retryAt = now + delay;
    }
    void reset() { lastAttempt = Long.MIN_VALUE / 2; retryAt = 0; failures = 0; }
}
