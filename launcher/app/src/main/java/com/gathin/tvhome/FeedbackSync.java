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
    private static final class NoSession extends Exception { }
    private static final class ConnectionChanged extends Exception { }
    interface Listener { void status(int message); }
    private final Context context;
    private final String base;
    private final AtomicFile file;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile boolean enabled, closed, running;
    private volatile int lastMessage;
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
        lastMessage = !enabled ? R.string.reco_sync_off
                : state.optJSONObject("pending") != null && state.optJSONObject("pending").length() > 0 ? R.string.reco_sync_pending
                : state.optBoolean("refresh_pending") ? R.string.reco_sync_watchly_pending : R.string.reco_sync_done;
    }
    boolean enabled() { return enabled; }
    int statusMessage() { return running ? R.string.reco_sync_background : lastMessage; }
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
            // A local hide/clear only cancels unsent work; it must not ask for an account connection.
            if (FeedbackSyncPolicy.actions(choice.optString("decision")).length == 0) {
                enqueue(choice);
                if (pending().length() == 0 && !state.optBoolean("refresh_pending")) {
                    state.remove("last_failure"); lastMessage = R.string.reco_sync_done;
                }
                save(); return;
            }
            // Persist under the consented account before opening the encrypted session: a temporary
            // Keystore/session failure must not lose a choice. checkOwner still gates every account write.
            enqueue(choice); save();
            String key = session(); checkOwner(key); drain(key);
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
        execute(() -> {
            if (pending().length() == 0 && !state.optBoolean("refresh_pending")) return;
            String key = session(); checkOwner(key); drain(key);
        });
    }
    private String session() throws Exception {
        String key = new StremioSession(context).load();
        if (!StremioApi.validKey(key)) throw new NoSession();
        WatchlyCatalog.base(base); return key;
    }
    private String owner(String key) throws Exception {
        byte[] value = MessageDigest.getInstance("SHA-256").digest((base + "\n" + key).getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(value);
    }
    private void checkOwner(String key) throws Exception {
        if (!owner(key).equals(state.optString("account"))) {
            enabled = false; state.put("enabled", false); save(); throw new ConnectionChanged();
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
            if (!key.equals(new StremioSession(context).load())) {
                enabled = false; state.put("enabled", false); save(); throw new ConnectionChanged();
            }
            JSONObject choice = pending().getJSONObject(id);
            try {
                if (choice.getString("action").equals("liked")) StremioApi.like(key, choice.getString("type"), choice.getString("id"));
                else complete(key, choice);
                pending().remove(id); state.put("refresh_pending", true); save(); dirty = true;
            } catch (Exception failure) {
                if (Thread.currentThread().isInterrupted()) throw failure;
                recordFailure(choice.optString("action"), failure);
                failures++;
            }
        }
        if (dirty && !closed && enabled) {
            try { StremioApi.refreshWatchly(base); }
            catch (Exception failure) {
                recordFailure("watchly_refresh", failure);
                report(pending().length() == 0 ? R.string.reco_sync_watchly_pending : R.string.reco_sync_pending);
                return;
            }
            state.put("refresh_pending", false); save();
            StremioApi.warmWatchly(base);
        }
        if (pending().length() == 0) { state.remove("last_failure"); save(); }
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
    private void recordFailure(String phase, Exception failure) {
        // Exception messages/URLs can contain credentials. Keep only bounded diagnostic codes privately.
        try {
            state.put("last_failure", new JSONObject().put("phase", phase)
                    .put("kind", failure.getClass().getSimpleName())
                    .put("code", failure instanceof StremioApi.Failure ? ((StremioApi.Failure) failure).code : 0)
                    .put("at", System.currentTimeMillis()));
            save();
        } catch (Exception ignored) { }
    }
    private void execute(Work work) {
        if (closed) return;
        worker.execute(() -> {
            running = true;
            try { work.run(); } catch (Exception failure) {
                if (!closed && !Thread.currentThread().isInterrupted()) {
                    recordFailure("connection_or_storage", failure);
                    report(failure instanceof NoSession ? R.string.reco_sync_no_session
                            : failure instanceof ConnectionChanged ? R.string.reco_sync_connection_changed : R.string.reco_sync_pending);
                }
            }
            finally { running = false; }
        });
    }
    private void report(int message) { lastMessage = message; main.post(() -> { if (!closed) listener.status(message); }); }
    void close() { closed = true; worker.shutdownNow(); }
}
