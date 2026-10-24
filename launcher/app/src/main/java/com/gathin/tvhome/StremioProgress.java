package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only resume data. Never feeds completion, dislike or release-year recommendation filters. */
final class StremioProgress {
    static final int PROGRESS = 0, NEXT = 1, NEW = 2, UPCOMING = 3;
    static final int MAX_LOOKUPS = 8, MAX_ITEMS = 30;
    private static final long DAY = 86_400_000L;
    static final class Item {
        final String id, type, name, poster, video;
        final long position, duration, engaged, release;
        final int kind;
        Item(JSONObject o) {
            id = o.optString("id"); type = o.optString("type"); name = o.optString("name");
            poster = o.optString("poster"); video = o.optString("video");
            position = Math.max(0, o.optLong("position")); duration = Math.max(0, o.optLong("duration"));
            engaged = o.optLong("engaged"); release = o.optLong("release"); kind = o.optInt("kind");
        }
        boolean valid() { return identity(id) && (type.equals("movie") || type.equals("series"))
                && !name.trim().isEmpty() && (video.isEmpty() || identity(video)) && kind >= 0 && kind <= UPCOMING; }
        String key() { return type + ":" + id; }
        int percent() { return duration == 0 || position <= 1 ? -1 : (int) Math.min(100, 100.0 * position / duration); }
        long minutesLeft() { return duration <= position || position <= 1 ? 0 : (duration - position + 59_999) / 60_000; }
        String episode() {
            String[] parts = video.split(":");
            if (parts.length < 3 || !parts[parts.length - 2].matches("[0-9]+") || !parts[parts.length - 1].matches("[0-9]+")) return "";
            return "S" + parts[parts.length - 2] + " · E" + parts[parts.length - 1];
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("type", type).put("name", name).put("poster", poster)
                    .put("video", video).put("position", position).put("duration", duration)
                    .put("engaged", engaged).put("release", release).put("kind", kind);
        }
        Item episode(JSONObject episode, int kind) throws Exception {
            return new Item(json().put("video", episode.getString("id")).put("position", 0).put("duration", 0)
                    .put("release", time(episode.optString("released"))).put("kind", kind));
        }
    }
    private StremioProgress() { }
    static boolean identity(String id) { return id != null && id.matches("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}"); }
    static long time(String value) { try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignored) { return 0; } }
    static List<JSONObject> library(JSONArray rows) throws Exception {
        if (rows.length() > 20_000) throw new Exception("Library limit");
        List<JSONObject> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            JSONObject state = row.optJSONObject("state"); if (state == null) state = new JSONObject();
            Item item = new Item(new JSONObject().put("id", row.optString("_id")).put("type", row.optString("type"))
                    .put("name", row.optString("name")).put("poster", row.optString("poster"))
                    .put("video", state.optString("video_id")).put("position", state.optLong("timeOffset"))
                    .put("duration", state.optLong("duration")).put("engaged", time(state.optString("lastWatched"))));
            if (item.valid() && (!row.optBoolean("removed") || row.optBoolean("temp")) && seen.add(item.key())) result.add(row);
        }
        result.sort((a, b) -> Long.compare(engaged(b), engaged(a)));
        return result;
    }
    private static long engaged(JSONObject row) {
        JSONObject state = row.optJSONObject("state"); return state == null ? 0 : time(state.optString("lastWatched"));
    }
    /** Only IMDb series can use Cinemeta; at most eight recent/followed shows per refresh. */
    static List<String> lookups(List<JSONObject> library, long now) {
        List<String> ids = new ArrayList<>();
        for (JSONObject row : library) {
            if (!row.optString("type").equals("series") || !row.optString("_id").matches("tt[0-9]+")) continue;
            if (row.optBoolean("temp") && (engaged(row) <= 0 || now - engaged(row) > 120 * DAY)) continue;
            ids.add(row.optString("_id")); if (ids.size() == MAX_LOOKUPS) break;
        }
        return ids;
    }
    static List<Item> build(List<JSONObject> library, Map<String, JSONArray> metadata, boolean upcoming, long now) throws Exception {
        List<Item> result = new ArrayList<>();
        for (JSONObject row : library) {
            JSONObject s = row.optJSONObject("state"); if (s == null) s = new JSONObject();
            Item item = new Item(new JSONObject().put("id", row.optString("_id")).put("type", row.optString("type"))
                    .put("name", row.optString("name")).put("poster", row.optString("poster"))
                    .put("video", s.optString("video_id")).put("position", s.optLong("timeOffset"))
                    .put("duration", s.optLong("duration")).put("engaged", engaged(row)));
            List<JSONObject> episodes = episodes(metadata.get(item.id));
            if (upcoming) {
                if (!item.type.equals("series") || (row.optBoolean("temp") && (item.engaged <= 0 || now - item.engaged > 120 * DAY))) continue;
                JSONObject next = null;
                for (JSONObject e : episodes) if (time(e.optString("released")) > now
                        && (next == null || time(e.optString("released")) < time(next.optString("released")))) next = e;
                if (next != null) result.add(item.episode(next, UPCOMING));
            } else if (item.position > 0) {
                // Stremio uses 1 ms to point at the next episode. Never infer completion from a 90% threshold.
                if (item.type.equals("series") && item.position == 1) {
                    JSONObject next = null; for (JSONObject e : episodes) if (item.video.equals(e.optString("id"))) next = e;
                    if (next == null) result.add(new Item(item.json().put("kind", NEXT)));
                    else if (time(next.optString("released")) > 0 && time(next.optString("released")) <= now) result.add(item.episode(next, NEXT));
                } else if (item.duration == 0 || item.position < item.duration) result.add(item);
            } else if (item.type.equals("series") && item.engaged > 0 && now - item.engaged < 60 * DAY) {
                // Offer only the episode immediately after the current pointer, when newly released.
                for (int i = 0; i + 1 < episodes.size(); i++) if (item.video.equals(episodes.get(i).optString("id"))) {
                    JSONObject next = episodes.get(i + 1); long release = time(next.optString("released"));
                    if (release > item.engaged && release <= now) result.add(item.episode(next, NEW));
                    break;
                }
            }
        }
        result.sort(upcoming ? Comparator.comparingLong(i -> i.release)
                : (a, b) -> Long.compare(b.kind == NEW ? b.release : b.engaged, a.kind == NEW ? a.release : a.engaged));
        return new ArrayList<>(result.subList(0, Math.min(MAX_ITEMS, result.size())));
    }
    private static List<JSONObject> episodes(JSONArray values) {
        List<JSONObject> result = new ArrayList<>();
        if (values != null) for (int i = 0; i < Math.min(10_000, values.length()); i++) {
            JSONObject e = values.optJSONObject(i);
            if (e != null && identity(e.optString("id")) && e.optInt("season") > 0 && e.optInt("episode", e.optInt("number")) > 0) result.add(e);
        }
        result.sort(Comparator.comparingInt((JSONObject e) -> e.optInt("season")).thenComparingInt(e -> e.optInt("episode", e.optInt("number"))));
        return result;
    }
    static JSONArray json(List<Item> values) throws Exception { JSONArray out = new JSONArray(); for (Item i : values) out.put(i.json()); return out; }
    static List<Item> cached(JSONArray values, boolean upcoming, long now) {
        List<Item> result = new ArrayList<>();
        if (values != null) for (int i = 0; i < Math.min(MAX_ITEMS, values.length()); i++) {
            JSONObject value = values.optJSONObject(i); if (value == null) continue;
            Item item = new Item(value);
            if (item.valid() && (upcoming ? item.kind == UPCOMING && item.release > now : item.kind != UPCOMING)) result.add(item);
        }
        return result;
    }
}
