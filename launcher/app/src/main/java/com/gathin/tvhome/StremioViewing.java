package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Account-local watched evidence, independent of the four-hour recommendation catalog cache. */
final class StremioViewing {
    static final int MAX_MOVIES = 5000, MAX_SERIES = 1000;
    private StremioViewing() { }

    static Set<String> movieIds(JSONObject snapshot) {
        return new HashSet<>(records(snapshot.optJSONArray("movies"), "movie", MAX_MOVIES).keySet());
    }

    static JSONObject update(JSONObject previous, JSONArray library, Map<String, JSONArray> metadata, LocalDate today) throws Exception {
        if (library.length() > 20_000) throw new Exception("Library limit");
        Map<String, JSONObject> movies = records(previous.optJSONArray("movies"), "movie", MAX_MOVIES);
        Map<String, JSONObject> shows = records(previous.optJSONArray("series"), "series", MAX_SERIES);
        for (int i = 0; i < library.length(); i++) {
            JSONObject row = library.optJSONObject(i);
            if (!valid(row)) continue;
            String id = row.optString("_id"); JSONObject state = row.optJSONObject("state");
            if (state == null) continue;
            if ("movie".equals(row.optString("type"))) {
                // Playback position alone is not a completion event. Use Stremio's watched marks.
                if (state.optLong("timesWatched") > 0 || (!state.has("timesWatched") && state.optLong("flaggedWatched") > 0)) {
                    JSONObject record = new JSONObject().put("id", id).put("type", "movie");
                    copyMetadata(row, record);
                    movies.remove(id); movies.put(id, record);
                } else if (state.has("timesWatched")) movies.remove(id);
            } else if (metadata.containsKey(id)) {
                JSONObject summary = SeasonViewing.fromLibrary(row, metadata.get(id), today);
                if (summary == null) continue; // Corrupt or unavailable evidence cannot erase a prior result.
                summary.put("state_signature", signature(row));
                copyMetadata(row, summary);
                shows.remove(id); shows.put(id, summary);
            }
        }
        trim(movies, MAX_MOVIES); trim(shows, MAX_SERIES);
        return new JSONObject().put("schema", 1).put("movies", new JSONArray(movies.values())).put("series", new JSONArray(shows.values()));
    }

    /** Prioritize changed viewing, using the same eight metadata-request slots as Continue. */
    static List<String> lookups(List<JSONObject> library, JSONObject previous, long now) {
        Map<String, JSONObject> known = records(previous.optJSONArray("series"), "series", MAX_SERIES);
        List<JSONObject> candidates = new ArrayList<>();
        for (JSONObject row : library) if (valid(row) && "series".equals(row.optString("type"))) candidates.add(row);
        candidates.sort(Comparator.comparingLong(StremioViewing::changedAt).reversed());
        List<String> result = new ArrayList<>();
        // Reserve half the budget for normal resume/upcoming data, even during a large initial import.
        for (JSONObject row : candidates) {
            String id = row.optString("_id"); JSONObject saved = known.get(id);
            if ((saved == null || !signature(row).equals(saved.optString("state_signature"))) && !result.contains(id)) result.add(id);
            if (result.size() == StremioProgress.MAX_LOOKUPS / 2) break;
        }
        for (String id : StremioProgress.lookups(library, now)) {
            if (!result.contains(id)) result.add(id);
            if (result.size() == StremioProgress.MAX_LOOKUPS) break;
        }
        return result;
    }
    private static long changedAt(JSONObject row) {
        JSONObject state = row.optJSONObject("state");
        return Math.max(StremioProgress.time(row.optString("_mtime")), state == null ? 0 : StremioProgress.time(state.optString("lastWatched")));
    }
    private static boolean valid(JSONObject row) {
        return row != null && row.optString("_id").matches("tt[0-9]+")
                && (row.optString("type").equals("movie") || row.optString("type").equals("series"));
    }
    private static String signature(JSONObject row) {
        JSONObject state = row.optJSONObject("state"); if (state == null) return "";
        // Only a digest of bounded watched/progress fields is persisted, never the raw library row.
        String value = state.optString("watched") + "|" + state.optString("video_id") + "|"
                + state.optLong("timeOffset") + "|" + state.optLong("duration");
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ignored) { return ""; }
    }
    private static Map<String, JSONObject> records(JSONArray values, String type, int limit) {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        if (values != null) for (int i = 0; i < Math.min(values.length(), limit); i++) {
            JSONObject record = values.optJSONObject(i);
            if (record != null && RecommendationTaste.valid(record) && type.equals(record.optString("type"))) result.put(record.optString("id"), record);
        }
        return result;
    }
    private static void trim(Map<String, JSONObject> records, int limit) {
        while (records.size() > limit) records.remove(records.keySet().iterator().next());
    }
    private static void copyMetadata(JSONObject row, JSONObject target) throws Exception {
        CatalogItem item = new CatalogItem(row);
        target.put("genres", new JSONArray(item.allGenres)).put("cast", new JSONArray(item.cast)).put("directors", new JSONArray(item.directors));
    }
/** True when two snapshots carry the same watched evidence. Ignores the account binding and key order. */
static boolean sameEvidence(JSONObject a, JSONObject b) {
    return canonical(a.optJSONArray("movies")).equals(canonical(b.optJSONArray("movies")))
            && canonical(a.optJSONArray("series")).equals(canonical(b.optJSONArray("series")));
}
static String canonical(Object value) { StringBuilder out = new StringBuilder(); canonical(value, out); return out.toString(); }
private static void canonical(Object value, StringBuilder out) {
    if (value instanceof JSONObject) {
        JSONObject object = (JSONObject) value; java.util.TreeSet<String> keys = new java.util.TreeSet<>();
        for (java.util.Iterator<String> it = object.keys(); it.hasNext(); ) keys.add(it.next());
        out.append('{');
        for (String key : keys) { out.append(JSONObject.quote(key)).append(':'); canonical(object.opt(key), out); out.append(','); }
        out.append('}');
    } else if (value instanceof JSONArray) {
        JSONArray array = (JSONArray) value; out.append('[');
        for (int i = 0; i < array.length(); i++) { canonical(array.opt(i), out); out.append(','); }
        out.append(']');
    } else if (value instanceof String) out.append(JSONObject.quote((String) value));
    else out.append(value == null ? "null" : String.valueOf(value));
}
}
