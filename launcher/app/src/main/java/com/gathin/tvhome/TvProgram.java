package com.gathin.tvhome;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** App-owned TV entries retain their original links; no inferred IMDb matching or account upload. */
final class TvProgram {
    final String key, owner, channelKey, name, poster, intent, app, channel;
    final long position, duration, engagement;

    TvProgram(JSONObject row, String table, String app, String channel) {
        owner = row.optString("package_name");
        key = "tv:" + table + ":" + owner + ":" + row.optLong("_id", -1);
        channelKey = owner + "/" + row.optLong("channel_id", -1);
        name = row.optString("title"); poster = row.optString("poster_art_uri");
        intent = row.optString("intent_uri"); this.app = app; this.channel = channel;
        position = Math.max(0, row.optLong("last_playback_position_millis"));
        duration = Math.max(0, row.optLong("duration_millis"));
        engagement = Math.max(0, row.optLong("last_engagement_time_utc_millis"));
    }

    boolean valid() {
        return owner.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") && !key.endsWith(":-1")
                && !name.trim().isEmpty() && !intent.trim().isEmpty();
    }
    int percent() { return duration == 0 ? -1 : (int) Math.min(100, 100.0 * position / duration); }
    boolean inProgress() { return duration > 0 && position > 0 && position < duration; }
    String identity() { return owner + "\n" + intent; }

    /** Fairly mix channels instead of letting the first large provider fill the whole shelf. */
    static List<TvProgram> channels(List<TvProgram> all, String selected) {
        LinkedHashMap<String, List<TvProgram>> groups = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (TvProgram item : all) if (item.valid() && (selected.isEmpty() || selected.equals(item.channelKey)) && seen.add(item.identity()))
            groups.computeIfAbsent(item.channelKey, key -> new ArrayList<>()).add(item);
        List<TvProgram> result = new ArrayList<>();
        for (int index = 0; ; index++) {
            boolean more = false;
            for (List<TvProgram> group : groups.values()) if (index < group.size()) { result.add(group.get(index)); more = true; }
            if (!more) return result;
        }
    }

    static List<TvProgram> resume(List<TvProgram> watchNext, List<TvProgram> previews) {
        List<TvProgram> candidates = new ArrayList<>(watchNext);
        for (TvProgram item : previews) if (item.inProgress()) candidates.add(item);
        candidates.sort((a, b) -> Long.compare(b.engagement, a.engagement));
        List<TvProgram> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (TvProgram item : candidates) if (item.valid() && seen.add(item.identity())) result.add(item);
        return result;
    }
}
