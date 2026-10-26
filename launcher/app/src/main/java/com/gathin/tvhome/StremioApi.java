package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Official link/account protocol, verified against Stremio/stremio-core. */
final class StremioApi {
    private static final String LINK = "https://link.stremio.com/api/v2/";
    static final class Failure extends Exception {
        final int code;
        Failure(int code) { super("Stremio request failed"); this.code = code; }
    }
    private StremioApi() { }
    static boolean officialLink(String value) {
        try {
            URI u = URI.create(value);
            return "https".equals(u.getScheme()) && "link.stremio.com".equals(u.getHost())
                    && u.getUserInfo() == null && u.getPort() == -1 && u.getFragment() == null;
        } catch (Exception ignored) { return false; }
    }
    static JSONObject createLink() throws Exception {
        JSONObject result = object(request(LINK + "create?type=Create", null, 32_000));
        if (!result.optString("code").matches("[A-Za-z0-9_-]{1,128}")
                || !officialLink(result.optString("link")) || !officialLink(result.optString("qrcode"))) throw new Failure(-1);
        return result;
    }
    static String readLink(String code) throws Exception {
        return linkKey(request(LINK + "read?type=Read&code=" + URLEncoder.encode(code, "UTF-8"), null, 32_000));
    }
    static String linkKey(byte[] bytes) throws Exception {
        try {
            JSONObject result = object(bytes);
            String key = result.optString("authKey");
            if (!validKey(key)) throw new Failure(-1);
            return key;
        } catch (Failure failure) { if (failure.code == 101) return ""; throw failure; }
    }
    // Stremio returns an opaque bearer value, including padded Base64 ('=', '+' and '/').
    // It is serialized as JSON, never interpolated into a URL, header or shell command.
    static boolean validKey(String key) {
        if (key == null || key.length() < 10 || key.length() > 4096) return false;
        for (int i = 0; i < key.length(); i++) if (key.charAt(i) < 33 || key.charAt(i) > 126) return false;
        return true;
    }
    static JSONArray library(String key) throws Exception {
        JSONObject response = response(request("https://api.strem.io/api/datastoreGet", new JSONObject()
                .put("authKey", key).put("collection", "libraryItem").put("ids", new JSONArray()).put("all", true).toString(), 6_000_000));
        return response.getJSONArray("result");
    }
    static JSONArray episodes(String id) throws Exception {
        if (!id.matches("tt[0-9]+")) return new JSONArray();
        JSONObject response = new JSONObject(new String(request("https://v3-cinemeta.strem.io/meta/series/" + id + ".json", null, 2_000_000), StandardCharsets.UTF_8));
        return response.getJSONObject("meta").getJSONArray("videos");
    }
    static void putLibrary(String key, JSONObject item) throws Exception {
        response(request("https://api.strem.io/api/datastorePut", new JSONObject().put("authKey", key)
                .put("collection", "libraryItem").put("changes", new JSONArray().put(item)).toString(), 512_000));
    }
    static String rating(String key, String type, String id) throws Exception {
        FeedbackSyncPolicy.validate(type, id);
        JSONObject value = new JSONObject(new String(request("https://likes.stremio.com/api/get_status?authToken="
                + URLEncoder.encode(key, "UTF-8") + "&mediaId=" + id + "&mediaType=" + type, null, 32_000), StandardCharsets.UTF_8));
        if (!value.has("status") || value.has("error")) throw new Failure(-1);
        return value.isNull("status") ? "" : value.getString("status");
    }
    static void like(String key, String type, String id) throws Exception {
        FeedbackSyncPolicy.validate(type, id);
        String current = rating(key, type, id);
        if (current.equals("loved") || current.equals("liked")) return;
        JSONObject value = new JSONObject(new String(request("https://likes.stremio.com/api/send", new JSONObject()
                .put("authToken", key).put("mediaId", id).put("mediaType", type).put("status", "liked").toString(), 32_000), StandardCharsets.UTF_8));
        if (value.has("error")) throw new Failure(-1);
        String after = rating(key, type, id);
        if (!after.equals("liked") && !after.equals("loved")) throw new Failure(-1);
    }
    static void refreshWatchly(String base) throws Exception {
        String origin = WatchlyCatalog.base(base);
        JSONObject result = new JSONObject(new String(request(origin + "dashboard/refresh", "{}", 32_000), StandardCharsets.UTF_8));
        if (!"started".equals(result.optString("status"))) throw new Failure(-1);
    }
    static void warmWatchly(String base) {
        // Refresh clears provider caches, but its scheduled updater may decline an early rebuild.
        // Catalog reads repopulate them without another invalidation; a failure must not undo the write.
        try {
            String origin = WatchlyCatalog.base(base);
            for (String type : new String[]{"movie", "series"})
                request(origin + "catalog/" + type + "/watchly.rec.json", null, 2_000_000);
        } catch (Exception ignored) { }
    }
    static JSONObject response(byte[] bytes) throws Exception {
        JSONObject value = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONObject error = value.optJSONObject("error"); if (error != null) throw new Failure(error.optInt("code", -1));
        if (!value.has("result")) throw new Failure(-1);
        return value;
    }
    private static JSONObject object(byte[] bytes) throws Exception { return response(bytes).getJSONObject("result"); }
    static byte[] qr(String url) throws Exception {
        if (!officialLink(url)) throw new Failure(-1);
        return request(url, null, 256_000);
    }
    private static byte[] request(String address, String body, int limit) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        HttpURLConnection c = (HttpURLConnection) URI.create(address).toURL().openConnection();
        c.setConnectTimeout(7000); c.setReadTimeout(9000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) OnnTvHome/2.18");
        c.setRequestProperty("Accept", "application/json,image/png");
        try {
            if (body != null) {
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            if (status != 200) throw new Failure(status);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (out.size() + count > limit) throw new Failure(-1);
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }
}
