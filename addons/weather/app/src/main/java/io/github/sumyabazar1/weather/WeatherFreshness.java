package io.github.sumyabazar1.weather;

/** Clock changes must neither freeze a cached reading nor produce a tight retry loop. */
final class WeatherFreshness {
    private WeatherFreshness() { }
    static boolean due(long now, long updated, long lastAttempt) {
        boolean fresh = updated > 0 && now >= updated && now - updated < Forecast.REFRESH_MS;
        boolean retry = lastAttempt > 0 && now >= lastAttempt && now - lastAttempt < Forecast.RETRY_MS;
        return !fresh && !retry;
    }
}
