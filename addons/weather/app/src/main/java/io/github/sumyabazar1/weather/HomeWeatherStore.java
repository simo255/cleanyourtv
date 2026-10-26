package io.github.sumyabazar1.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A small shared cache. A visible client requests refresh; there is no scheduler or service. */
final class HomeWeatherStore {
    static final Uri URI = Uri.parse("content://io.github.sumyabazar1.weather.home/current");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static boolean loading;
    private static long attemptedAt;
    private static String attemptedPlace = "";
    private HomeWeatherStore() { }
    static SharedPreferences place(Context context) { return context.getSharedPreferences("place", Context.MODE_PRIVATE); }
    static SharedPreferences cache(Context context) { return context.getSharedPreferences("home_weather", Context.MODE_PRIVATE); }
    static String key(SharedPreferences place) {
        return place.getString("name", "") + ":" + place.getLong("lat", 0) + ":" + place.getLong("lon", 0);
    }
    static synchronized void refresh(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences place = place(app), cache = cache(app);
        String key = key(place);
        if (!place.contains("name") || loading) return;
        long now = System.currentTimeMillis();
        long at = key.equals(cache.getString("place_key", "")) && cache.contains("forecast")
                ? cache.getLong("updated_at", 0) : 0;
        if (!WeatherFreshness.due(now, at, key.equals(attemptedPlace) ? attemptedAt : 0)) return;
        loading = true; attemptedAt = now; attemptedPlace = key;
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        IO.execute(() -> {
            try { save(app, key, Forecast.fetch(lat, lon, Forecast.MIN_DAYS)); }
            catch (Exception ignored) { /* Keep the dated reading on network failure. */ }
            finally { synchronized (HomeWeatherStore.class) { loading = false; } }
        });
    }
    static synchronized void save(Context context, String key, Forecast.Report report) {
        if (!key.equals(key(place(context)))) return; // a city changed while the request was in flight
        JSONArray forecast = new JSONArray();
        if (report.today != null) forecast.put(day(report.today));
        for (Forecast.Day day : report.days) forecast.put(day(day));
        cache(context).edit().putString("place_key", key).putInt("temp", report.tempC)
                .putInt("feels", report.feelsC).putInt("code", report.code).putBoolean("day", report.day)
                .putInt("humidity", report.humidity).putInt("wind", report.windKmh)
                .putString("forecast", forecast.toString())
                .putLong("updated_at", System.currentTimeMillis()).apply();
        context.getContentResolver().notifyChange(URI, null);
    }
    private static JSONObject day(Forecast.Day day) {
        JSONObject json = new JSONObject();
        try { json.put("date", day.date.toString()).put("code", day.code).put("max", day.maxC)
                .put("min", day.minC).put("precipitation", day.precipitation); }
        catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        return json;
    }
}
