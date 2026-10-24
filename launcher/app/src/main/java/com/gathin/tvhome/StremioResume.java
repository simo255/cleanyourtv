package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Bounded foreground-only account reads and QR pairing; no service or playback/history uploads. */
final class StremioResume {
    private final Activity activity;
    private final StremioSession session;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(), pairing = Executors.newSingleThreadExecutor();
    private final Map<String, JSONArray> episodes = new LinkedHashMap<>();
    private final Map<String, Long> fetched = new LinkedHashMap<>();
    private String key;
    private boolean closed;
    private int generation, pairGeneration, pollCount;
    private long lastAttempt, deadline;
    private Future<?> task, pairTask;
    private Runnable listener, poll;
    private AlertDialog dialog;
    boolean loading, failed, expired;
    long syncedAt;
    List<StremioProgress.Item> watching = new ArrayList<>(), upcoming = new ArrayList<>();

    StremioResume(Activity activity) {
        this.activity = activity; session = new StremioSession(activity); key = session.load();
        if (!connected()) return;
        try {
            JSONObject cache = session.cache(key); long now = System.currentTimeMillis();
            watching = StremioProgress.cached(cache.optJSONArray("watching"), false, now);
            upcoming = StremioProgress.cached(cache.optJSONArray("upcoming"), true, now);
            syncedAt = cache.optLong("synced_at");
        } catch (Exception ignored) { }
    }
    void listen(Runnable listener) { this.listener = listener; }
    boolean connected() { return !key.isEmpty(); }
    private void publish() { if (!closed && listener != null) listener.run(); }
    List<StremioProgress.Item> items(boolean future) {
        if (!future) return watching;
        List<StremioProgress.Item> current = new ArrayList<>();
        for (StremioProgress.Item item : upcoming) if (item.release > System.currentTimeMillis()) current.add(item);
        return current;
    }
    void refresh(boolean force) {
        long now = System.currentTimeMillis();
        if (closed || loading || !connected() || (!force && now - lastAttempt < 60_000)) return;
        lastAttempt = now; loading = true; failed = false; publish();
        int expected = generation; String auth = key;
        task = worker.submit(() -> {
            try {
                List<JSONObject> library = StremioProgress.library(StremioApi.library(auth));
                Map<String, JSONArray> metadata = new LinkedHashMap<>();
                for (String id : StremioProgress.lookups(library, now)) {
                    if (Thread.currentThread().isInterrupted()) return;
                    try {
                        if (!episodes.containsKey(id) || now - fetched.get(id) >= 6 * 3_600_000L) {
                            episodes.put(id, StremioApi.episodes(id)); fetched.put(id, now);
                            while (episodes.size() > 32) { String first = episodes.keySet().iterator().next(); episodes.remove(first); fetched.remove(first); }
                        }
                        metadata.put(id, episodes.get(id));
                    } catch (InterruptedException interrupted) { return; }
                    catch (Exception ignored) { if (episodes.containsKey(id)) metadata.put(id, episodes.get(id)); }
                }
                List<StremioProgress.Item> nextWatching = StremioProgress.build(library, metadata, false, now);
                List<StremioProgress.Item> nextUpcoming = StremioProgress.build(library, metadata, true, now);
                JSONObject cache = new JSONObject().put("watching", StremioProgress.json(nextWatching))
                        .put("upcoming", StremioProgress.json(nextUpcoming)).put("synced_at", now);
                main.post(() -> {
                    if (closed || generation != expected) return;
                    watching = nextWatching; upcoming = nextUpcoming; syncedAt = now; loading = false; failed = false;
                    try { session.cache(auth, cache); } catch (Exception ignored) { }
                    publish();
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (closed || generation != expected) return;
                    loading = false; failed = true;
                    if (failure instanceof StremioApi.Failure && (((StremioApi.Failure) failure).code == 1
                            || ((StremioApi.Failure) failure).code == 401)) { disconnect(); expired = true; failed = true; }
                    publish();
                });
            }
        });
    }
    void disconnect() {
        generation++; pause(); if (task != null) task.cancel(true);
        key = ""; session.clear(); watching.clear(); upcoming.clear(); syncedAt = 0; lastAttempt = 0;
        loading = false; failed = false; expired = false; publish();
    }
    void connect() {
        if (closed) return;
        if (connected()) {
            new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog).setTitle(R.string.stremio_disconnect)
                    .setMessage(R.string.stremio_disconnect_detail).setPositiveButton(R.string.stremio_disconnect, (d, w) -> disconnect())
                    .setNegativeButton(R.string.done, null).show();
            return;
        }
        pause();
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER); int pad = dp(18); content.setPadding(pad, pad, pad, pad);
        ImageView image = new ImageView(activity); image.setContentDescription(activity.getString(R.string.stremio_qr_description));
        image.setBackgroundColor(Color.WHITE); image.setPadding(dp(6), dp(6), dp(6), dp(6));
        content.addView(image, new LinearLayout.LayoutParams(dp(190), dp(190)));
        TextView label = new TextView(activity); label.setTextColor(Color.WHITE); label.setTextSize(16); label.setGravity(Gravity.CENTER);
        content.addView(label, new LinearLayout.LayoutParams(-1, -2));
        dialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog).setTitle(R.string.stremio_connect)
                .setView(content).setPositiveButton(R.string.stremio_new_code, null).setNegativeButton(R.string.done, null).create();
        dialog.setOnDismissListener(d -> cancelPair()); dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> startPair(image, label));
        startPair(image, label);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private boolean currentPair(int expected) { return !closed && expected == pairGeneration && dialog != null && dialog.isShowing(); }
    private void startPair(ImageView image, TextView label) {
        cancelPair(); int expected = pairGeneration; deadline = SystemClock.elapsedRealtime() + 180_000; pollCount = 0;
        image.setImageDrawable(null); label.setText(R.string.stremio_pair_loading);
        pairTask = pairing.submit(() -> {
            try {
                JSONObject link = StremioApi.createLink(); String code = link.getString("code"), url = link.getString("link");
                main.post(() -> { if (currentPair(expected)) label.setText(activity.getString(R.string.stremio_pair_instructions, url)); });
                try {
                    byte[] data = StremioApi.qr(link.getString("qrcode"));
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                    if (bounds.outWidth > 0 && bounds.outWidth <= 1024 && bounds.outHeight > 0 && bounds.outHeight <= 1024) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        main.post(() -> { if (currentPair(expected)) image.setImageBitmap(bitmap); });
                    }
                } catch (Exception ignored) { /* The official text link remains usable if its QR image is unavailable. */ }
                main.post(() -> { if (currentPair(expected)) schedulePoll(code, expected, label); });
            } catch (Exception ignored) { main.post(() -> { if (currentPair(expected)) label.setText(R.string.stremio_pair_error); }); }
        });
    }
    private void schedulePoll(String code, int expected, TextView label) {
        if (!currentPair(expected)) return;
        if (++pollCount > 50 || SystemClock.elapsedRealtime() >= deadline) { label.setText(R.string.stremio_pair_expired); return; }
        poll = () -> {
            if (!currentPair(expected)) return;
            pairTask = pairing.submit(() -> {
                try {
                    String auth = StremioApi.readLink(code);
                    main.post(() -> {
                        if (!currentPair(expected)) return;
                        if (auth.isEmpty()) { schedulePoll(code, expected, label); return; }
                        try {
                            session.save(auth); key = auth; expired = false; failed = false; lastAttempt = 0;
                            dialog.dismiss(); publish(); refresh(true);
                        } catch (Exception ignored) { label.setText(R.string.stremio_pair_error); }
                    });
                } catch (Exception ignored) { main.post(() -> { if (currentPair(expected)) label.setText(R.string.stremio_pair_error); }); }
            });
        };
        main.postDelayed(poll, 3000);
    }
    private void cancelPair() {
        pairGeneration++; if (poll != null) main.removeCallbacks(poll); if (pairTask != null) pairTask.cancel(true);
    }
    void pause() { cancelPair(); if (dialog != null) { dialog.dismiss(); dialog = null; } }
    void close() { closed = true; generation++; pause(); listener = null; worker.shutdownNow(); pairing.shutdownNow(); }
}
