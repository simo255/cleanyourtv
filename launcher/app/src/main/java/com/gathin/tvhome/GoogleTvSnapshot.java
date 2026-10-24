package com.gathin.tvhome;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An explicit, dated import of the visible Google TV ranking, separate from live Trakt data. */
final class GoogleTvSnapshot {
    final long capturedAt;
    private final List<CatalogItem> movies, shows;

    GoogleTvSnapshot(JSONObject data, String account) throws Exception {
        if (data.optInt("schema") != 1 || !"google-tv-ui".equals(data.optString("provider"))
                || account.isEmpty() || !account.equals(data.optString("account"))
                || data.optLong("captured_at") <= 0) throw new Exception("Invalid snapshot");
        capturedAt = data.getLong("captured_at");
        movies = CatalogItem.parse(data.getJSONArray("movies"), "movie", false, Collections.emptySet());
        shows = CatalogItem.parse(data.getJSONArray("shows"), "series", false, Collections.emptySet());
        if (movies.isEmpty() && shows.isEmpty()) throw new Exception("Empty snapshot");
    }

    /** Google order and membership are independent of Trakt recommendations and watched exclusions. */
    List<CatalogItem> items(boolean series) { return new ArrayList<>(series ? shows : movies); }
}
