package com.gathin.tvhome;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An explicit, dated import of the visible Google TV ranking, separate from live Trakt data. */
final class GoogleTvSnapshot {
    final long capturedAt;
    final List<CatalogItem> movies, shows;
    private final Set<String> keys = new HashSet<>();

    GoogleTvSnapshot(JSONObject data, String account) throws Exception {
        if (data.optInt("schema") != 1 || !"google-tv-ui".equals(data.optString("provider"))
                || account.isEmpty() || !account.equals(data.optString("account"))
                || data.optLong("captured_at") <= 0) throw new Exception("Invalid snapshot");
        capturedAt = data.getLong("captured_at");
        movies = CatalogItem.parse(data.getJSONArray("movies"), "movie", false, Collections.emptySet());
        shows = CatalogItem.parse(data.getJSONArray("shows"), "series", false, Collections.emptySet());
        if (movies.isEmpty() && shows.isEmpty()) throw new Exception("Empty snapshot");
        for (CatalogItem item : movies) keys.add(item.type + ":" + item.id);
        for (CatalogItem item : shows) keys.add(item.type + ":" + item.id);
    }
    boolean contains(CatalogItem item) { return keys.contains(item.type + ":" + item.id); }

    /** Preserve each provider's order, prefer Google's copy of overlaps, and apply known exclusions to both. */
    static List<CatalogItem> mix(List<CatalogItem> google, List<CatalogItem> trakt, Set<String> watched) {
        List<CatalogItem> result = new ArrayList<>(); Set<String> added = new HashSet<>();
        Set<String> googleKeys = new HashSet<>();
        for (CatalogItem item : google) googleKeys.add(item.type + ":" + item.id);
        int g = 0, t = 0;
        while (g < google.size() || t < trakt.size()) {
            while (g < google.size()) {
                CatalogItem item = google.get(g++);
                if (!watched.contains(item.id) && added.add(item.type + ":" + item.id)) { result.add(item); break; }
            }
            while (t < trakt.size()) {
                CatalogItem item = trakt.get(t++); String key = item.type + ":" + item.id;
                if (!watched.contains(item.id) && !googleKeys.contains(key) && added.add(key)) { result.add(item); break; }
            }
        }
        return result;
    }
}
