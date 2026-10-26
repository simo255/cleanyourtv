package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.io.ByteArrayOutputStream;

/** Positive account changes only; completion never rewrites the resume pointer or watch dates. */
final class FeedbackSyncPolicy {
    private FeedbackSyncPolicy() { }
    static void validate(String type, String id) throws Exception {
        if (!(type.equals("movie") || type.equals("series")) || !id.matches("tt[0-9]+")) throw new Exception("Unsupported title");
    }
    static String action(String decision) {
        if (RecommendationTaste.MORE.equals(decision)) return "liked";
        if (RecommendationTaste.SEEN.equals(decision) || RecommendationTaste.COMPLETED.equals(decision)) return "watched";
        return "";
    }
    static String[] actions(String decision) {
        if (RecommendationTaste.SEEN_LIKED.equals(decision)) return new String[]{"watched", "liked"};
        String action = action(decision);
        return action.isEmpty() ? new String[0] : new String[]{action};
    }
    /** Two independent durable entries let a failed like retry without losing a successful watched mark. */
    static void enqueue(JSONObject pending, JSONObject choice) throws Exception {
        String type = choice.getString("type"), id = choice.getString("id"); validate(type, id);
        String prefix = type + ":" + id + ":";
        String[] actions = actions(choice.optString("decision"));
        if (actions.length == 0) {
            pending.remove(prefix + "liked"); pending.remove(prefix + "watched"); return;
        }
        int added = 0;
        for (String action : actions) if (!pending.has(prefix + action)) added++;
        if (pending.length() + added > 600) throw new Exception("Outbox full");
        for (String action : actions) pending.put(prefix + action, new JSONObject(choice.toString()).put("action", action));
    }
    static boolean alreadyWatched(JSONObject row) {
        JSONObject state = row == null ? null : row.optJSONObject("state");
        return state != null && state.optInt("flaggedWatched") > 0 && state.optInt("timesWatched") > 0;
    }
    static JSONObject complete(JSONObject before, JSONObject choice, JSONArray videos, Instant now) throws Exception {
        String id = choice.getString("id"), type = choice.getString("type"); validate(type, id);
        JSONObject next;
        if (before == null) {
            next = new JSONObject().put("_id", id).put("type", type).put("name", choice.getString("name"))
                    .put("removed", true).put("temp", true).put("_ctime", now.toString()).put("state", new JSONObject());
        } else {
            if (!id.equals(before.optString("_id")) || !type.equals(before.optString("type"))) throw new Exception("Title mismatch");
            next = new JSONObject(before.toString());
        }
        JSONObject state = next.optJSONObject("state");
        if (state == null) throw new Exception("Invalid state");
        boolean whole = true;
        if (type.equals("series")) {
            if (videos == null || videos.length() == 0 || videos.length() > 20_000) throw new Exception("Episode metadata unavailable");
            List<JSONObject> sorted = new ArrayList<>();
            for (int i = 0; i < videos.length(); i++) sorted.add(videos.getJSONObject(i));
            sorted.sort(Comparator.comparingInt((JSONObject v) -> v.optInt("season", -1))
                    .thenComparingInt(v -> v.optInt("episode", v.optInt("number", -1))).thenComparing(v -> v.optString("released")));
            List<String> ids = new ArrayList<>();
            for (JSONObject v : sorted) {
                int sn = v.optInt("season", -1), en = v.optInt("episode", v.optInt("number", -1));
                String vid = v.getString("id");
                if (sn < 0 || en < 0 || !vid.equals(id + ":" + sn + ":" + en)) throw new Exception("Unmapped episode");
                ids.add(vid);
            }
            if (new HashSet<>(ids).size() != ids.size()) throw new Exception("Duplicate episodes");
            Set<String> seen = SeasonViewing.watchedIds(state.optString("watched"), ids);
            JSONArray seasons = choice.optJSONArray("seasons");
            Set<Integer> allowed = new HashSet<>();
            if (seasons != null) for (int i = 0; i < seasons.length(); i++) {
                JSONObject season = seasons.getJSONObject(i);
                if ("watched".equals(season.optString("status"))) allowed.add(season.getInt("season"));
            }
            int marked = 0;
            for (JSONObject v : sorted) {
                // Specials and unknown/future release dates are never invented as watched.
                if (v.optInt("season") <= 0 || v.optInt("episode", v.optInt("number")) <= 0) continue;
                try {
                    if (!Instant.parse(v.optString("released")).isAfter(now)) {
                        if (seasons == null || allowed.contains(v.optInt("season"))) { seen.add(v.getString("id")); marked++; }
                        else whole = false;
                    }
                } catch (java.time.format.DateTimeParseException ignored) { }
            }
            if (marked == 0) throw new Exception("No dated aired episodes");
            byte[] raw = new byte[(ids.size() + 7) / 8];
            for (int i = 0; i < ids.size(); i++) if (seen.contains(ids.get(i))) raw[i / 8] |= (byte) (1 << (i % 8));
            Deflater zip = new Deflater(); ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                zip.setInput(raw); zip.finish(); byte[] buffer = new byte[1024];
                while (!zip.finished()) { int count = zip.deflate(buffer); out.write(buffer, 0, count); }
            } finally { zip.end(); }
            String bitmap = ids.get(ids.size() - 1) + ":" + ids.size() + ":" + Base64.getEncoder().encodeToString(out.toByteArray());
            if (!SeasonViewing.watchedIds(bitmap, ids).equals(seen)) throw new Exception("Episode verification failed");
            state.put("watched", bitmap);
        }
        if (whole) {
            state.put("flaggedWatched", Math.max(1, state.optInt("flaggedWatched")));
            state.put("timesWatched", Math.max(1, state.optInt("timesWatched")));
        }
        return next.put("_mtime", now.toString());
    }
}
