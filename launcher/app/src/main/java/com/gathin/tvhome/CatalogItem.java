package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared Stremio catalog parsing; list membership and personalized exclusions stay distinct. */
final class CatalogItem {
    final String id, type, name, year, poster, description, released, rating, genres, provider;

    CatalogItem(JSONObject json) {
        id = json.optString("id", json.optString("imdb_id"));
        type = json.optString("type"); name = json.optString("name");
        year = json.optString("year", json.optString("releaseInfo"));
        poster = present(json, "poster");
        description = present(json, "description");
        provider = json.optString("provider");
        released = present(json, "released"); rating = present(json, "imdbRating");
        JSONArray tags = json.optJSONArray("genres");
        StringBuilder labels = new StringBuilder();
        if (tags != null) for (int i = 0; i < Math.min(3, tags.length()); i++) {
            String tag = tags.optString(i).trim();
            if (!tag.isEmpty()) { if (labels.length() > 0) labels.append(" · "); labels.append(tag); }
        }
        genres = tags == null ? present(json, "genre") : labels.toString();
    }

    private static String present(JSONObject json, String key) {
        String value = json.optString(key, "").trim();
        return value.equalsIgnoreCase("null") ? "" : value;
    }

    JSONObject json() throws Exception {
        return new JSONObject().put("id", id).put("type", type).put("name", name)
                .put("year", year).put("poster", poster).put("description", description)
                .put("released", released).put("imdbRating", rating).put("genre", genres).put("provider", provider);
    }

    static JSONArray metas(JSONObject response) throws Exception {
        if (response.has("error")) throw new Exception("Invalid catalog");
        return response.getJSONArray("metas");
    }

    static List<CatalogItem> parse(JSONArray values, String type, boolean excludeWatched, Set<String> watched) throws Exception {
        List<CatalogItem> result = new ArrayList<>(); Set<String> added = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            CatalogItem item = new CatalogItem(value);
            if (type.equals(item.type) && item.id.matches("tt[0-9]+") && !item.name.trim().isEmpty()
                    && (!excludeWatched || !watched.contains(item.id)) && added.add(item.id)) result.add(item);
        }
        return result;
    }

    static JSONArray json(List<CatalogItem> items) throws Exception {
        JSONArray array = new JSONArray(); for (CatalogItem item : items) array.put(item.json()); return array;
    }

    static boolean cacheMatches(JSONObject cache, String account, int source) {
        // Only the legacy For-you cache predates the explicit source field.
        return account.equals(cache.optString("account")) && cache.optInt("source", 0) == source;
    }
}
