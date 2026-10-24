package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.Inflater;

/** Opt-in, positive-only season rules. Explicit unfinished/unwatched seasons always win. */
final class SeasonViewing {
    static final class Result {
        final Set<Integer> watched = new HashSet<>();
        boolean complete;
        boolean positive() { return !watched.isEmpty(); }
    }
    private SeasonViewing() { }

    static Result evaluate(JSONObject record) {
        Result result = new Result();
        JSONArray values = record.optJSONArray("seasons");
        if (values == null || values.length() > 100) return result;
        TreeMap<Integer, JSONObject> seasons = new TreeMap<>();
        int latest = 0, evidenceThrough = 0, missing = 0, seen = 0;
        boolean blocked = false, active = false, uncertain = false;
        for (int i = 0; i < values.length(); i++) {
            JSONObject season = values.optJSONObject(i);
            if (!valid(season) || seasons.put(season.optInt("season"), season) != null) return new Result();
            int number = season.optInt("season"), total = season.optInt("total"), count = season.optInt("watched");
            String status = season.optString("status");
            boolean explicit = status.equals("watched"), veto = status.equals("unwatched") || status.equals("unfinished");
            if (veto && (season.optInt("aired") > 0 || season.optInt("undated") > 0)) blocked = true;
            if (!veto && ((explicit && season.optInt("future") == 0)
                    || (total > 0 && count * 2 >= total && season.optInt("aired") > 0))) {
                result.watched.add(number); latest = Math.max(latest, number);
            }
            if (!veto && (count > 0 || explicit)) evidenceThrough = Math.max(evidenceThrough, number);
            if (explicit && season.optInt("future") == 0) active = true;
            if (season.optInt("aired") > 0) {
                active = true; missing += Math.max(0, season.optInt("aired") - count); seen += count;
            }
            if (season.optInt("undated") > 0 && !explicit) uncertain = true;
        }
        for (java.util.Map.Entry<Integer, JSONObject> entry : seasons.entrySet()) {
            JSONObject season = entry.getValue(); String status = season.optString("status");
            if (entry.getKey() < latest && !status.equals("unwatched") && !status.equals("unfinished")
                    && season.optInt("future") == 0) result.watched.add(entry.getKey());
        }
        // The tolerated gap is per whole title, never two missing episodes in every season.
        if (active && seen > 0 && missing <= 2 && !blocked && !uncertain) {
            for (java.util.Map.Entry<Integer, JSONObject> entry : seasons.entrySet())
                if (entry.getKey() <= evidenceThrough && entry.getValue().optInt("aired") > 0 && entry.getValue().optInt("future") == 0) result.watched.add(entry.getKey());
        }
        result.complete = active && !blocked && !uncertain;
        for (java.util.Map.Entry<Integer, JSONObject> entry : seasons.entrySet()) {
            JSONObject season = entry.getValue();
            if (season.optInt("aired") > 0 && (!result.watched.contains(entry.getKey()) || season.optInt("future") > 0)) result.complete = false;
        }
        return result;
    }

    private static boolean valid(JSONObject s) {
        if (s == null) return false;
        int number = s.optInt("season", -1), total = s.optInt("total", -1), watched = s.optInt("watched", -1);
        int aired = s.optInt("aired", -1), future = s.optInt("future", 0), undated = s.optInt("undated", 0);
        String state = s.optString("status");
        return number > 0 && number <= 1000 && total >= 0 && total <= 10000 && watched >= 0 && watched <= total
                && aired >= 0 && future >= 0 && undated >= 0 && aired + future + undated == total && watched <= aired + undated
                && (state.isEmpty() || state.equals("watched") || state.equals("unwatched") || state.equals("unfinished"));
    }

    /** Refresh counts, retaining explicit user season decisions from the reviewed baseline. */
    static JSONObject merge(JSONObject reviewed, JSONObject live) {
        try {
            JSONObject merged = new JSONObject(reviewed.toString());
            TreeMap<Integer, JSONObject> seasons = new TreeMap<>();
            for (JSONObject record : new JSONObject[]{reviewed, live}) {
                JSONArray values = record.optJSONArray("seasons");
                if (values == null) continue;
                for (int i = 0; i < Math.min(100, values.length()); i++) {
                    JSONObject next = values.optJSONObject(i); if (!valid(next)) continue;
                    next = new JSONObject(next.toString());
                    JSONObject previous = seasons.get(next.optInt("season"));
                    if (previous != null && !previous.optString("status").isEmpty()) next.put("status", previous.optString("status"));
                    seasons.put(next.optInt("season"), next);
                }
            }
            merged.put("seasons", new JSONArray(seasons.values()));
            return merged;
        } catch (Exception ignored) { return reviewed; }
    }

    /** A later increase in watched episodes can supersede an imported unfinished/unwatched snapshot. */
    static JSONObject mergeProgress(JSONObject reviewed, JSONObject live) {
        JSONObject merged = merge(reviewed, live);
        if (reviewed.optLong("reviewed_at") > 0 && live.optLong("updated_at") <= reviewed.optLong("reviewed_at")) return merged;
        try {
            TreeMap<Integer, JSONObject> baseline = new TreeMap<>(), current = new TreeMap<>();
            for (JSONObject item : new JSONObject[]{reviewed, live}) {
                JSONArray values = item.optJSONArray("seasons"); if (values == null) continue;
                for (int i = 0; i < values.length(); i++) {
                    JSONObject value = values.optJSONObject(i); if (!valid(value)) continue;
                    (item == reviewed ? baseline : current).put(value.optInt("season"), value);
                }
            }
            JSONArray seasons = merged.optJSONArray("seasons"); if (seasons == null) return merged;
            for (int i = 0; i < seasons.length(); i++) {
                JSONObject season = seasons.getJSONObject(i), before = baseline.get(season.optInt("season")), after = current.get(season.optInt("season"));
                if (before == null || after == null) continue;
                String status = before.optString("status");
                if ((status.equals("unfinished") || status.equals("unwatched")) && after.optInt("watched") > before.optInt("watched")) season.remove("status");
            }
        } catch (Exception ignored) { return merge(reviewed, live); }
        return merged;
    }

    /** Decode only identified episodes, including the one explicit playback pointer at >=75%. */
    static JSONObject fromLibrary(JSONObject row, JSONArray videos, LocalDate today) {
        if (!row.optString("_id").matches("tt[0-9]+") || !"series".equals(row.optString("type"))
                || videos == null || videos.length() > 10000) return null;
        try {
            List<JSONObject> ordered = new ArrayList<>();
            for (int i = 0; i < videos.length(); i++) {
                JSONObject video = videos.optJSONObject(i); if (video == null) return null; ordered.add(video);
            }
            ordered.sort(Comparator.comparingInt((JSONObject v) -> v.optInt("season", -1))
                    .thenComparingInt(v -> v.optInt("episode", v.optInt("number", -1))).thenComparing(v -> v.optString("released")));
            List<String> ids = new ArrayList<>(); for (JSONObject v : ordered) ids.add(v.optString("id"));
            if (new HashSet<>(ids).size() != ids.size()) return null;
            JSONObject state = row.optJSONObject("state"); if (state == null) return null;
            Set<String> watched = watchedIds(state.optString("watched"), ids);
            long duration = state.optLong("duration"), position = state.optLong("timeOffset");
            String pointer = state.optString("video_id");
            if (duration > 0 && position > 1 && (double) position / duration >= .75 && ids.contains(pointer)) watched.add(pointer);
            TreeMap<Integer, JSONObject> seasons = new TreeMap<>();
            for (JSONObject video : ordered) {
                int sn = video.optInt("season", -1), en = video.optInt("episode", video.optInt("number", -1));
                if (sn == 0 || en == 0) continue;
                if (sn < 0 || en < 0 || !video.optString("id").equals(row.optString("_id") + ":" + sn + ":" + en)) return null;
                JSONObject season = seasons.get(sn);
                if (season == null) { season = new JSONObject().put("season", sn).put("total", 0).put("watched", 0).put("aired", 0).put("future", 0).put("undated", 0); seasons.put(sn, season); }
                season.put("total", season.getInt("total") + 1);
                String bucket = "undated";
                try { bucket = LocalDate.parse(video.optString("released").substring(0, 10)).isAfter(today) ? "future" : "aired"; }
                catch (Exception ignored) { }
                season.put(bucket, season.getInt(bucket) + 1);
                if (!bucket.equals("future") && watched.contains(video.optString("id"))) season.put("watched", season.getInt("watched") + 1);
            }
            return new JSONObject().put("id", row.optString("_id")).put("type", "series").put("seasons", new JSONArray(seasons.values()))
                    .put("updated_at", Math.max(StremioProgress.time(row.optString("_mtime")), StremioProgress.time(state.optString("lastWatched"))))
                    .put("genres", new JSONArray(CatalogItem.strings(row.optJSONArray("genres"), 12)));
        } catch (Exception ignored) { return null; }
    }

    private static Set<String> watchedIds(String field, List<String> ids) throws Exception {
        if (field.isEmpty()) return new HashSet<>();
        int last = field.lastIndexOf(':'), middle = field.lastIndexOf(':', last - 1);
        if (middle < 0 || field.length() > 100000) throw new Exception("Invalid watched state");
        String anchor = field.substring(0, middle), length = field.substring(middle + 1, last);
        if (!length.matches("[0-9]{1,8}") || !ids.contains(anchor)) throw new Exception("Invalid anchor");
        Inflater inflater = new Inflater(); byte[] raw = new byte[65537]; int size;
        try {
            inflater.setInput(Base64.getDecoder().decode(field.substring(last + 1)));
            size = inflater.inflate(raw);
            if (!inflater.finished() || inflater.getRemaining() != 0 || size > 65536) throw new Exception("Invalid bitmap");
        } finally { inflater.end(); }
        int offset = Integer.parseInt(length) - ids.indexOf(anchor) - 1, count = 0;
        for (int i = 0; i < size; i++) count += Integer.bitCount(raw[i] & 255);
        Set<String> result = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            int bit = i + offset;
            if (bit >= 0 && bit < size * 8 && ((raw[bit / 8] & 255) & (1 << (bit % 8))) != 0) result.add(ids.get(i));
        }
        if (result.size() != count) throw new Exception("Unmapped watched episodes");
        return result;
    }
}
