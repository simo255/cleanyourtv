package io.github.sumyabazar1.weather;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherFreshnessTest {
    @Test public void usesCacheUntilThirtyMinutesAndRetriesFailuresAfterFiveMinutes() {
        long now = 10_000_000L;
        assertFalse(WeatherFreshness.due(now, now - 60_000, 0));
        assertTrue(WeatherFreshness.due(now, now - Forecast.REFRESH_MS, 0));
        assertFalse(WeatherFreshness.due(now, 0, now - 60_000));
        assertTrue(WeatherFreshness.due(now, 0, now - Forecast.RETRY_MS));
    }
    @Test public void unsetAndFutureTimestampsDoNotFreezeRefresh() {
        assertTrue(WeatherFreshness.due(10_000, 0, 0));
        assertTrue(WeatherFreshness.due(10_000, 20_000, 20_000));
    }
}
