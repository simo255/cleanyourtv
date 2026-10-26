package com.gathin.tvhome;

import android.app.Activity;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;
import java.util.Date;
import java.time.LocalDate;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional local weather client. The separate add-on owns city selection, networking and cache. */
final class HomeWeather {
    private static final Uri URI = Uri.parse("content://io.github.sumyabazar1.weather.home/current");
    private final Activity activity;
    private final TextView view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean reading = new AtomicBoolean();
    private final Runnable tick = () -> { read(); schedule(); };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { read(); }
    };
    private boolean active, observed, closed, visible = true;

    HomeWeather(Activity activity, TextView view) {
        this.activity = activity; this.view = view;
        view.setText(R.string.home_weather_pending);
        view.setContentDescription(activity.getString(R.string.home_weather_pending));
        view.setVisibility(View.VISIBLE);
    }
    void visible(boolean shown) {
        visible = shown;
        view.setVisibility(shown ? View.VISIBLE : View.GONE);
    }
    void resume() {
        if (closed || active) return;
        active = true;
        try { activity.getContentResolver().registerContentObserver(URI, false, observer); observed = true; }
        catch (Exception ignored) { observed = false; }
        read(); schedule();
    }
    private void schedule() { if (active) main.postDelayed(tick, 60_000); }
    private void read() {
        if (!active || closed || !reading.compareAndSet(false, true)) return;
        io.execute(() -> {
            String label = "", detail = "";
            try (Cursor cursor = activity.getContentResolver().query(URI, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String city = clean(cursor.getString(cursor.getColumnIndexOrThrow("city")));
                    String condition = clean(cursor.getString(cursor.getColumnIndexOrThrow("condition")));
                    int temp = cursor.getInt(cursor.getColumnIndexOrThrow("temp"));
                    long at = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
                    if (!city.isEmpty() && temp >= -100 && temp <= 70 && at > 0 && at <= System.currentTimeMillis() + 300_000) {
                        String time = DateFormat.getTimeFormat(activity).format(new Date(at));
                        boolean stale = System.currentTimeMillis() - at >= 30 * 60_000L;
                        label = activity.getString(R.string.home_weather_current, temp);
                        int feelsColumn = cursor.getColumnIndex("feels");
                        if (feelsColumn >= 0 && !cursor.isNull(feelsColumn)) {
                            int feels = cursor.getInt(feelsColumn);
                            if (feels >= -100 && feels <= 70)
                                label += "   ·   " + activity.getString(R.string.home_weather_feels_like, feels);
                        }
                        if (!condition.isEmpty()) label += "   ·   " + condition;
                        if (stale) label += "   ·   " + activity.getString(R.string.home_weather_saved, time);
                        detail = label;
                        int forecastColumn = cursor.getColumnIndex("forecast");
                        if (forecastColumn >= 0) {
                            String forecast = cursor.getString(forecastColumn);
                            LocalDate today = LocalDate.now();
                            label += "\n" + forecastLine(R.string.home_weather_today, WeatherBand.day(forecast, today))
                                    + "\n" + forecastLine(R.string.home_weather_tomorrow, WeatherBand.day(forecast, today.plusDays(1)));
                            detail = label;
                        }
                    }
                }
            } catch (Exception ignored) { /* Optional add-on absent, not configured or signed with a different key. */ }
            finally { reading.set(false); }
            final String text = label, description = detail;
            main.post(() -> {
                if (!active || closed) return;
                view.setText(text.isEmpty() ? activity.getString(R.string.home_weather_pending) : text);
                view.setContentDescription(description.isEmpty()
                        ? activity.getString(R.string.home_weather_pending) : description);
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
            });
        });
    }
    private String forecastLine(int label, JSONObject day) {
        String name = activity.getString(label);
        if (day == null) return name + "   ·   " + activity.getString(R.string.home_weather_forecast_missing);
        String result = name + "   " + WeatherBand.icon(day.optInt("code", 3)) + "   "
                + day.optInt("max") + "° / " + day.optInt("min") + "°   ·   " + clean(day.optString("condition"));
        int precipitation = day.optInt("precipitation", -1);
        if (precipitation >= 0 && precipitation <= 100)
            result += " · " + activity.getString(R.string.home_weather_precipitation, precipitation);
        return result;
    }
    private static String clean(String text) {
        if (text == null) return "";
        return text.substring(0, Math.min(80, text.length())).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
    }
    void pause() {
        active = false; main.removeCallbacks(tick);
        if (observed) { activity.getContentResolver().unregisterContentObserver(observer); observed = false; }
    }
    void close() { pause(); closed = true; io.shutdownNow(); }
}
