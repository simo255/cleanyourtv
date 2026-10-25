package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;
import java.util.Date;
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
    private boolean active, observed, closed;

    HomeWeather(Activity activity, TextView view) {
        this.activity = activity; this.view = view;
        view.setText(R.string.home_weather_open);
        view.setContentDescription(activity.getString(R.string.home_weather_open));
        view.setVisibility(View.VISIBLE);
        view.setOnClickListener(v -> {
            try { activity.startActivity(new Intent().setClassName("io.github.sumyabazar1.weather", "io.github.sumyabazar1.weather.WeatherActivity")); }
            catch (Exception ignored) { android.widget.Toast.makeText(activity,
                    R.string.home_weather_unavailable, android.widget.Toast.LENGTH_LONG).show(); }
        });
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
                        label = activity.getString(R.string.home_weather_reading, city, temp) + "  ·  "
                                + (stale ? activity.getString(R.string.home_weather_saved, time) : condition);
                        detail = activity.getString(R.string.home_weather_detail, city, temp, condition, time);
                        if (stale) detail += " · " + activity.getString(R.string.home_weather_saved, time);
                    }
                }
            } catch (Exception ignored) { /* Optional add-on absent, not configured or signed with a different key. */ }
            finally { reading.set(false); }
            final String text = label, description = detail;
            main.post(() -> {
                if (!active || closed) return;
                view.setText(text.isEmpty() ? activity.getString(R.string.home_weather_open) : text);
                view.setContentDescription(description.isEmpty()
                        ? activity.getString(R.string.home_weather_open) : description);
                view.setVisibility(View.VISIBLE);
            });
        });
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
