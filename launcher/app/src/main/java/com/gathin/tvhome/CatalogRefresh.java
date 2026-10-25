package com.gathin.tvhome;

/** Wall time ages persisted catalogs; monotonic time schedules this process's requests. */
final class CatalogRefresh {
    static final long INTERVAL_MS = 4 * 60 * 60_000L;
    private final AccountRefresh retries = new AccountRefresh();
    private boolean failed;
    private long succeededAt = -1;

    boolean due(boolean force, long wallTime, long elapsedTime, long cachedAt) {
        if (force) return true;
        if (failed) return retries.due(AccountRefresh.Trigger.TICK, elapsedTime);
        if (succeededAt >= 0) return elapsedTime - succeededAt >= INTERVAL_MS;
        // A missing cache or a clock corrected behind a saved timestamp must not suppress a fetch.
        return cachedAt <= 0 || wallTime < cachedAt || wallTime - cachedAt >= INTERVAL_MS;
    }

    void succeeded(long elapsedTime) {
        succeededAt = elapsedTime;
        failed = false;
        retries.succeeded();
    }

    void failed(long elapsedTime, int status) {
        failed = true;
        retries.failed(elapsedTime, status);
    }
}
