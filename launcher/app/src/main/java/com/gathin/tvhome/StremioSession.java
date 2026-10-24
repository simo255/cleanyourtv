package com.gathin.tvhome;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Separate from Trakt. The bearer key is encrypted with a non-exportable Android Keystore key. */
final class StremioSession {
    private static final String ALIAS = "stremio-resume-v1";
    private final AtomicFile auth, cache, viewing;
    StremioSession(Context context) {
        auth = new AtomicFile(new File(context.getNoBackupFilesDir(), "stremio-session.json"));
        cache = new AtomicFile(new File(context.getNoBackupFilesDir(), "stremio-resume-cache.json"));
        viewing = new AtomicFile(new File(context.getNoBackupFilesDir(), "stremio-viewing-cache.json"));
    }
    static final class Unavailable extends Exception { Unavailable(Throwable cause) { super(cause); } }
    String load() throws Unavailable {
        File file = auth.getBaseFile();
        if (!file.exists()) return "";
        if (file.length() > 16_000) { clear(); return ""; }
        try {
            JSONObject stored = read(auth, 16_000);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(stored.getString("iv"), Base64.NO_WRAP)));
            String value = new String(cipher.doFinal(Base64.decode(stored.getString("data"), Base64.NO_WRAP)), StandardCharsets.UTF_8);
            if (!StremioApi.validKey(value)) { clear(); return ""; }
            return value;
        } catch (Exception failure) {
            if (SessionLoadFailure.definitive(failure)) { clear(); return ""; }
            throw new Unavailable(failure);
        }
    }
    synchronized void save(String value) throws Exception {
        if (!StremioApi.validKey(value)) throw new Exception("Invalid session");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        write(auth, new JSONObject().put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("data", Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP)));
        cache.delete(); viewing.delete();
    }
    JSONObject cache(String value) throws Exception {
        JSONObject stored = read(cache, 256_000);
        if (!fingerprint(value).equals(stored.optString("account"))) throw new Exception("Different session");
        return stored;
    }
    synchronized void cache(String value, JSONObject data) throws Exception { write(cache, bound(value, data)); }
    JSONObject viewing(String value) throws Exception {
        JSONObject stored = read(viewing, 2_000_000);
        if (!fingerprint(value).equals(stored.optString("account"))) throw new Exception("Different session");
        return stored;
    }
    synchronized void viewing(String value, JSONObject data) throws Exception { write(viewing, bound(value, data), 2_000_000); }
    synchronized void clear() { auth.delete(); cache.delete(); viewing.delete(); }
    private static JSONObject bound(String value, JSONObject data) throws Exception {
        JSONObject copy = new JSONObject();
        for (java.util.Iterator<String> keys = data.keys(); keys.hasNext();) {
            String key = keys.next(); copy.put(key, data.opt(key));
        }
        return copy.put("account", fingerprint(value));
    }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private static String fingerprint(String value) throws Exception {
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    private static JSONObject read(AtomicFile file, int limit) throws Exception {
        try (InputStream in = file.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) { if (out.size() + count > limit) throw new Exception("Cache limit"); out.write(buffer, 0, count); }
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }
    private static void write(AtomicFile file, JSONObject value) throws Exception {
        write(file, value, 256_000);
    }
    private static void write(AtomicFile file, JSONObject value, int limit) throws Exception {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) throw new Exception("Cache limit");
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (Exception failure) { if (out != null) file.failWrite(out); throw failure; }
    }
}
