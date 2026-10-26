package com.gathin.tvhome;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private, account-bound choices. Does not write to Stremio or Trakt. */
final class RecommendationFeedback {
    private final AtomicFile file;
    private final String account;
    private JSONArray entries = new JSONArray();
    RecommendationFeedback(File path, String account) {
        file = new AtomicFile(path); this.account = account.isEmpty() ? "public" : account;
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (InputStream in = file.openRead()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) { if (data.size() + count > 256_000) return; data.write(buffer, 0, count); }
            }
            JSONObject stored = new JSONObject(new String(data.toByteArray(), StandardCharsets.UTF_8));
            if (this.account.equals(stored.optString("account")) && stored.getJSONArray("entries").length() <= 300) entries = stored.getJSONArray("entries");
        } catch (Exception ignored) { }
    }
    synchronized JSONArray entries() {
        try { return new JSONArray(entries.toString()); } catch (Exception ignored) { return new JSONArray(); }
    }
    synchronized boolean choose(CatalogItem item, String decision) {
        return choose(item, decision, null);
    }
    synchronized boolean choose(CatalogItem item, String decision, JSONArray seasons) {
        if (!decision.isEmpty() && !RecommendationTaste.MORE.equals(decision)
                && !RecommendationTaste.HIDE.equals(decision) && !RecommendationTaste.SEEN.equals(decision)
                && !RecommendationTaste.COMPLETED.equals(decision) && !RecommendationTaste.SEEN_LIKED.equals(decision)) return false;
        try {
            JSONArray next = new JSONArray();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject previous = entries.optJSONObject(i);
                if (previous != null && !(item.id.equals(previous.optString("id")) && item.type.equals(previous.optString("type")))) next.put(previous);
            }
            if (!decision.isEmpty()) next.put(new JSONObject().put("id", item.id).put("type", item.type)
                    .put("name", item.name.substring(0, Math.min(120, item.name.length())))
                    .put("genre", item.genres.substring(0, Math.min(500, item.genres.length())))
                    .put("genres", new JSONArray(item.allGenres)).put("cast", new JSONArray(item.cast))
                    .put("directors", new JSONArray(item.directors)).put("decision", decision).put("seasons", seasons));
            while (next.length() > 300) next.remove(0);
            return save(next);
        } catch (Exception ignored) { return false; }
    }
    synchronized boolean reset() { return save(new JSONArray()); }
    private boolean save(JSONArray next) {
        FileOutputStream out = null;
        try {
            byte[] data = new JSONObject().put("account", account).put("entries", next).toString().getBytes(StandardCharsets.UTF_8);
            if (data.length > 256_000) return false;
            out = file.startWrite(); out.write(data); file.finishWrite(out); entries = next; return true;
        } catch (Exception ignored) { if (out != null) file.failWrite(out); return false; }
    }
}
