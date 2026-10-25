package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogRefreshTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test public void missingExpiredAndFutureCachesFetchImmediately() {
        CatalogRefresh policy = new CatalogRefresh();
        assertTrue(policy.due(false, NOW, 0, 0));
        assertTrue(policy.due(false, NOW, 0, NOW - CatalogRefresh.INTERVAL_MS));
        assertTrue(policy.due(false, NOW, 0, NOW + 60_000));
    }

    @Test public void persistedCatalogIsFreshForTwelveHours() {
        CatalogRefresh policy = new CatalogRefresh();
        assertFalse(policy.due(false, NOW, 0, NOW));
        assertFalse(policy.due(false, NOW, 0, NOW - 12 * 60 * 60_000L + 1));
        assertTrue(policy.due(false, NOW, 0, NOW - 12 * 60 * 60_000L));
    }

    @Test public void successfulRefreshUsesElapsedTimeEvenWhenWallClockChanges() {
        CatalogRefresh policy = new CatalogRefresh();
        policy.succeeded(0);
        assertFalse(policy.due(false, NOW + 10 * CatalogRefresh.INTERVAL_MS, 60_000, NOW));
        assertFalse(policy.due(false, NOW - 60_000, CatalogRefresh.INTERVAL_MS - 1, NOW));
        assertTrue(policy.due(false, NOW - 60_000, CatalogRefresh.INTERVAL_MS, NOW));
    }

    @Test public void failuresBackOffFromTwoToThirtyMinutes() {
        CatalogRefresh policy = new CatalogRefresh();
        long elapsed = 0;
        for (long delay : new long[]{120_000, 240_000, 480_000, 960_000, 1_800_000, 1_800_000}) {
            policy.failed(elapsed, -1);
            assertFalse(policy.due(false, NOW, elapsed + delay - 1, 0));
            assertTrue(policy.due(false, NOW, elapsed + delay, 0));
            assertTrue(policy.due(true, NOW, elapsed, 0));
            elapsed += delay;
        }
    }

    @Test public void serverRateLimitsWaitAtLeastTenMinutes() {
        for (int status : new int[]{429, 503}) {
            CatalogRefresh policy = new CatalogRefresh();
            policy.failed(100, status);
            assertFalse(policy.due(false, NOW, 600_099, 0));
            assertTrue(policy.due(false, NOW, 600_100, 0));
        }
    }

    @Test public void successfulRecoveryResetsFailuresAndRestoresTwelveHourInterval() {
        CatalogRefresh policy = new CatalogRefresh();
        policy.failed(0, 429);
        policy.succeeded(100);
        assertFalse(policy.due(false, NOW, 600_100, NOW));
        assertTrue(policy.due(false, NOW, 100 + CatalogRefresh.INTERVAL_MS, NOW));
        policy.failed(100 + CatalogRefresh.INTERVAL_MS, -1);
        assertTrue(policy.due(false, NOW, 120_100 + CatalogRefresh.INTERVAL_MS, NOW));
    }

    @Test public void manualRefreshBypassesFreshCacheAndFailedManualRefreshGetsBackoff() {
        CatalogRefresh policy = new CatalogRefresh();
        policy.succeeded(0);
        assertTrue(policy.due(true, NOW, 1, NOW));
        policy.failed(1, -1);
        assertFalse(policy.due(false, NOW, 120_000, NOW));
        assertTrue(policy.due(false, NOW, 120_001, NOW));
    }

    @Test public void failureInOneFeedDoesNotDelayAnother() {
        CatalogRefresh privateFeed = new CatalogRefresh(), publicFeed = new CatalogRefresh();
        privateFeed.failed(0, 429);
        assertFalse(privateFeed.due(false, NOW, 1, 0));
        assertTrue(publicFeed.due(false, NOW, 1, 0));
    }
}
