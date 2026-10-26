package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opt-in foreground outbox, bound to both connections. No credentials or account writes on the UI thread. */
final class FeedbackSync {
    interface Listener { void status(int message); }
    private final Context context;
    private final String base;
    private final AtomicFile file;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile boolean enabled, closed, running;
    private JSONObject state;
    private long attempted;
    private volatile int revision;
    FeedbackSync(Context context, String base, Listener listener) {
        this.context = context.getApplicationContext(); this.base = base; this.listener = listener;
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "stremio-feedback-outbox.json"));
        try {
            if (file.getBaseFile().length() > 512_000) throw new Exception("Outbox limit");
            try (java.io.InputStream in = file.openRead()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] bytes = new byte[4096]; int n;
                while ((n = in.read(bytes)) != -1) { if (out.size() + n > 512_000) throw new Exception("Outbox limit"); out.write(bytes, 0, n); }
                state = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
            enabled = state.optBoolean("enabled");
        } catch (Exception ignored) { state = new JSONObject(); }
    }
    boolean enabled() { return enabled; }
    void enable(JSONArray choices) {
        int version = ++revision;
        execute(() -> {
            if (version != revision || closed) return;
            String key = session(); String owner = owner(key);
            if (!owner.equals(state.optString("account"))) state = new JSONObject().put("account", owner);
            state.put("enabled", true); enabled = true;
            for (int i = 0; i < choices.length(); i++) enqueue(choices.getJSONObject(i));
            if (version != revision || closed) return;
            save(); drain(key);
        });
    }
    void disable() {
        enabled = false; revision++;
        execute(() -> { state.put("enabled", false).put("pending", new JSONObject()); save(); report(R.string.reco_sync_off); });
    }
    void choice(JSONObject choice) {
        if (!enabled) return;
        revision++;
        execute(() -> {
            String key = session(); checkOwner(key); enqueue(choice); save(); drain(key);
        });
    }
    void cancelPending() {
        revision++;
        execute(() -> { state.put("pending", new JSONObject()); save(); });
    }
    void retry() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (!enabled || running || now - attempted < 60_000) return;
        attempted = now;
        execute(() -> { String key = session(); checkOwner(key); drain(key); });
    }
    private String session() throws Exception {
        String key = new StremioSession(context).load();
        if (!StremioApi.validKey(key)) throw new Exception("Connect Stremio first");
        WatchlyCatalog.base(base); return key;
    }
    private String owner(String key) throws Exception {
        byte[] value = MessageDigest.getInstance("SHA-256").digest((base + "\n" + key).getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(value);
    }
    private void checkOwner(String key) throws Exception {
        if (!owner(key).equals(state.optString("account"))) {
            enabled = false; state.put("enabled", false); save(); throw new Exception("Connection changed");
        }
    }
    private JSONObject pending() throws Exception {
        JSONObject pending = state.optJSONObject("pending");
        if (pending == null) { pending = new JSONObject(); state.put("pending", pending); }
        return pending;
    }
    private void enqueue(JSONObject choice) throws Exception {
        FeedbackSyncPolicy.enqueue(pending(), choice);
    }
    private void drain(String key) throws Exception {
        int version = revision;
        boolean dirty = state.optBoolean("refresh_pending");
        List<String> keys = new ArrayList<>(); Iterator<String> names = pending().keys();
        while (names.hasNext() && keys.size() < 20) keys.add(names.next());
        if (keys.isEmpty() && !dirty) return;
        int failures = 0;
        for (String id : keys) {
            if (closed || !enabled || version != revision || Thread.currentThread().isInterrupted()) return;
            // A sign-out/account switch must not replay work using the former account.
            if (!key.equals(new StremioSession(context).load())) throw new Exception("Session changed");
            JSONObject choice = pending().getJSONObject(id);
            try {
                if (choice.getString("action").equals("liked")) StremioApi.like(key, choice.getString("type"), choice.getString("id"));
                else complete(key, choice);
                pending().remove(id); state.put("refresh_pending", true); save(); dirty = true;
            } catch (Exception failure) {
                if (Thread.currentThread().isInterrupted()) throw failure;
                failures++;
            }
        }
        if (dirty && !closed && enabled) {
            StremioApi.refreshWatchly(base); state.put("refresh_pending", false); save();
            StremioApi.warmWatchly(base);
        }
        report(failures == 0 && pending().length() == 0 ? R.string.reco_sync_done : R.string.reco_sync_pending);
    }
    static void complete(String key, JSONObject choice) throws Exception {
        String id = choice.getString("id");
        JSONObject before = find(StremioApi.library(key), id);
        JSONArray videos = null;
        if (choice.getString("type").equals("series")) try { videos = StremioApi.episodes(id); }
        catch (Exception unavailable) {
            // A removed metadata listing must not endlessly queue an existing explicit watched mark.
            // No new episodes or account changes are invented when the title is already marked watched.
            if (FeedbackSyncPolicy.alreadyWatched(before) && !Thread.currentThread().isInterrupted()) return;
            throw unavailable;
        }
        JSONObject next = FeedbackSyncPolicy.complete(before, choice, videos, Instant.now());
        if (before != null && before.getJSONObject("state").toString().equals(next.getJSONObject("state").toString())) return;
        JSONObject fresh = find(StremioApi.library(key), id);
        if (!(before == null ? fresh == null : fresh != null && before.toString().equals(fresh.toString()))) throw new Exception("Concurrent update");
        StremioApi.putLibrary(key, next);
        JSONObject after = find(StremioApi.library(key), id);
        if (after == null || !next.getJSONObject("state").toString().equals(after.getJSONObject("state").toString())) throw new Exception("Readback failed");
    }
    private static JSONObject find(JSONArray rows, String id) throws Exception {
        JSONObject found = null;
        for (int i = 0; i < rows.length(); i++) if (id.equals(rows.getJSONObject(i).optString("_id"))) {
            if (found != null) throw new Exception("Duplicate title"); found = rows.getJSONObject(i);
        }
        return found;
    }
    private void save() throws Exception {
        byte[] bytes = state.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 512_000) throw new Exception("Outbox limit");
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (Exception failure) { if (out != null) file.failWrite(out); throw failure; }
    }
    private interface Work { void run() throws Exception; }
    private void execute(Work work) {
        if (closed) return;
        worker.execute(() -> {
            running = true;
            try { work.run(); } catch (Exception ignored) { report(R.string.reco_sync_pending); }
            finally { running = false; }
        });
    }
    private void report(int message) { main.post(() -> { if (!closed) listener.status(message); }); }
    void close() { closed = true; worker.shutdownNow(); }
}
