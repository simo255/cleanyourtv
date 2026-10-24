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
}
