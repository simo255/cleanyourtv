package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONArray;

/** Shell-invoked device check, only in the test APK. Credentials never enter results or logs. */
public final class StremioConnectionCheck extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (arguments != null && "feedback_outbox".equals(arguments.getString("mode"))) {
                checkFeedbackOutbox();
                result.putBoolean("choice_survives_unavailable_session", true);
                result.putBoolean("local_clear_needs_no_session", true);
                result.putBoolean("empty_queue_needs_no_session", true);
                result.putString("stage", "success"); finish(Activity.RESULT_OK, result); return;
            }
            if (arguments != null && "keystore".equals(arguments.getString("mode"))) {
                java.io.File directory = new java.io.File(getTargetContext().getCacheDir(), "stremio-session-check");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new Exception("Test storage unavailable");
                android.content.Context context = new android.content.ContextWrapper(getTargetContext()) {
                    @Override public java.io.File getNoBackupFilesDir() { return directory; }
                };
                String sample = "AbC+/0123456789012345678901234567890123456789=";
                StremioSession check = new StremioSession(context);
                try {
                    check.save(sample);
                    if (!sample.equals(new StremioSession(context).load())) throw new Exception("Round trip failed");
                    String stored = new String(java.nio.file.Files.readAllBytes(new java.io.File(directory, "stremio-session.json").toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    if (stored.contains(sample)) throw new Exception("Unencrypted value");
                } finally { check.clear(); }
                result.putBoolean("encrypted_session_round_trip", true);
                result.putString("stage", "success"); finish(Activity.RESULT_OK, result); return;
            }
            result.putString("stage", "read_link");
            StremioSession session = new StremioSession(getTargetContext());
            String code = arguments == null ? "" : arguments.getString("linkCode", "");
            String auth = code.isEmpty() ? session.load() : StremioApi.readLink(code);
            if (!StremioApi.validKey(auth)) throw new Exception("Session unavailable");
            // Validate that the already-approved credential works before replacing private storage.
            result.putString("stage", "read_library");
            JSONArray library = StremioApi.library(auth);
            result.putString("stage", "encrypt_session");
            if (!code.isEmpty()) session.save(auth);
            result.putString("stage", "decrypt_session");
            if (!auth.equals(new StremioSession(getTargetContext()).load())) throw new Exception("Round trip failed");
            result.putString("stage", "success");
            result.putBoolean("encrypted_session_round_trip", true);
            result.putBoolean("official_library_read", true);
            result.putInt("library_items", library.length());
            result.putInt("resume_items_without_enrichment", StremioProgress.build(StremioProgress.library(library),
                    java.util.Collections.emptyMap(), false, System.currentTimeMillis()).size());
            finish(Activity.RESULT_OK, result);
        } catch (Exception failure) {
            result.putString("failure_type", failure.getClass().getSimpleName());
            if (failure instanceof StremioApi.Failure) result.putInt("failure_code", ((StremioApi.Failure) failure).code);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void checkFeedbackOutbox() throws Exception {
        java.io.File directory = new java.io.File(getTargetContext().getCacheDir(), "feedback-outbox-check-" + System.nanoTime());
        if (!directory.mkdir()) throw new Exception("Test storage unavailable");
        android.content.Context context = new android.content.ContextWrapper(getTargetContext()) {
            @Override public java.io.File getNoBackupFilesDir() { return directory; }
            @Override public android.content.Context getApplicationContext() { return this; }
        };
        java.io.File outbox = new java.io.File(directory, "stremio-feedback-outbox.json");
        java.nio.file.Files.write(outbox.toPath(), new org.json.JSONObject().put("enabled", true)
                .put("account", "isolated-test-account").put("pending", new org.json.JSONObject())
                .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.util.concurrent.BlockingQueue<Integer> messages = new java.util.concurrent.LinkedBlockingQueue<>();
        FeedbackSync sync = new FeedbackSync(context, "https://watchly.elfhosted.com/isolated_test/", messages::offer);
        try {
            org.json.JSONObject choice = new org.json.JSONObject().put("type", "movie").put("id", "tt12345")
                    .put("name", "Private isolated test").put("decision", RecommendationTaste.SEEN_LIKED);
            sync.choice(choice);
            Integer message = messages.poll(5, java.util.concurrent.TimeUnit.SECONDS);
            if (message == null || message != R.string.reco_sync_no_session) throw new Exception("Missing session not identified");
            org.json.JSONObject saved = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(outbox.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            if (saved.getJSONObject("pending").length() != 2 || !saved.getString("account").equals("isolated-test-account"))
                throw new Exception("Choice was lost before opening session");
            sync.choice(choice.put("decision", "hide"));
            long deadline = android.os.SystemClock.elapsedRealtime() + 3000;
            do {
                Thread.sleep(50);
                saved = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(outbox.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            } while (saved.getJSONObject("pending").length() != 0 && android.os.SystemClock.elapsedRealtime() < deadline);
            if (saved.getJSONObject("pending").length() != 0) throw new Exception("Local clear failed without session");
            sync.retry();
            if (messages.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS) != null) throw new Exception("Spurious connection warning for local/empty work");
        } finally {
            sync.close();
            java.io.File[] files = directory.listFiles();
            if (files != null) for (java.io.File file : files) file.delete();
            directory.delete();
        }
    }
}
