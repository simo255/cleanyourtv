package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic, opt-in content ranking. No model, negative inference or cross-type learning. */
final class DiscoveryTaste {
    private final Map<String, Map<String, Double>> profiles = new HashMap<>();
    private final Set<String> excluded = new HashSet<>();

    DiscoveryTaste(JSONArray finished, JSONArray choices, JSONArray viewing, JSONArray positives, Set<String> movies) {
        Map<String, JSONObject> reviewed = records(viewing);
        Map<String, JSONObject> signals = new LinkedHashMap<>();
        for (JSONObject item : records(positives).values())
            if ("movie".equals(item.optString("type")) && movies.contains(item.optString("id"))) signals.put(key(item), item);
        for (JSONObject item : records(finished).values()) {
            if (item.optBoolean("completed")) { signals.putIfAbsent(key(item), item); excluded.add(key(item)); }
        }
        for (JSONObject item : records(choices).values())
            if (RecommendationTaste.COMPLETED.equals(item.optString("decision"))) signals.putIfAbsent(key(item), item);
        for (String id : movies) excluded.add("movie:" + id);
        for (JSONObject choice : records(choices).values()) {
            JSONObject current = reviewed.get(key(choice));
            if (current != null && RecommendationTaste.COMPLETED.equals(choice.optString("decision")) && choice.optJSONArray("seasons") != null) {
                JSONObject merged = SeasonViewing.merge(choice, current);
                try {
                    for (String field : new String[]{"genres", "cast", "directors"})
                        if (current.has(field)) merged.put(field, current.get(field));
                } catch (Exception ignored) { }
                reviewed.put(key(choice), merged);
            }
        }
        for (JSONObject item : reviewed.values()) {
            SeasonViewing.Result state = SeasonViewing.evaluate(item);
            if (state.positive()) signals.put(key(item), withMetadata(item, signals.get(key(item))));
            else if (explicitVeto(item)) signals.remove(key(item));
            if (state.complete) excluded.add(key(item)); else excluded.remove(key(item));
        }
        Set<String> stronger = new HashSet<>();
        for (JSONObject item : records(choices).values()) {
            String decision = item.optString("decision"), key = key(item);
            if (RecommendationTaste.HIDE.equals(decision) || RecommendationTaste.SEEN.equals(decision)) excluded.add(key);
            if (RecommendationTaste.SEEN.equals(decision) && "movie".equals(item.optString("type"))) signals.putIfAbsent(key, item);
            if (RecommendationTaste.COMPLETED.equals(decision) && !reviewed.containsKey(key)) {
                signals.putIfAbsent(key, item); excluded.add(key);
            }
            if (RecommendationTaste.MORE.equals(decision)) { signals.putIfAbsent(key, item); stronger.add(key); }
        }
        for (JSONObject record : signals.values()) {
            CatalogItem item = new CatalogItem(record);
            Map<String, Double> profile = profiles.computeIfAbsent(item.type, unused -> new HashMap<>());
            double weight = stronger.contains(key(record)) ? 2 : 1;
            learn(profile, genres(item), "g:", weight);
            learn(profile, item.cast, "a:", weight * .6);
            learn(profile, item.directors, "d:", weight * .8);
        }
    }
    static Map<String, JSONObject> records(JSONArray values) {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        if (values != null) for (int i = 0; i < Math.min(1000, values.length()); i++) {
            JSONObject record = values.optJSONObject(i);
            if (record != null && RecommendationTaste.valid(record)) result.putIfAbsent(key(record), record);
        }
        return result;
    }
    private static String key(JSONObject value) { return RecommendationTaste.key(value.optString("type"), value.optString("id")); }
    private static boolean explicitVeto(JSONObject item) {
        JSONArray seasons = item.optJSONArray("seasons");
        if (seasons != null) for (int i = 0; i < seasons.length(); i++) {
            JSONObject season = seasons.optJSONObject(i); if (season == null) continue;
            String status = season.optString("status");
            if ((status.equals("unwatched") || status.equals("unfinished"))
                    && (season.optInt("aired") > 0 || season.optInt("undated") > 0)) return true;
        }
        return false;
    }
    private static JSONObject withMetadata(JSONObject item, JSONObject fallback) {
        if (fallback == null) return item;
        try {
            JSONObject result = new JSONObject(item.toString());
            CatalogItem known = new CatalogItem(fallback), current = new CatalogItem(item);
            if (current.allGenres.isEmpty()) result.put("genres", new JSONArray(known.allGenres));
            if (current.cast.isEmpty()) result.put("cast", new JSONArray(known.cast));
            if (current.directors.isEmpty()) result.put("directors", new JSONArray(known.directors));
            return result;
        } catch (Exception ignored) { return item; }
    }
    private static String normalized(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static List<String> genres(CatalogItem item) { return RecommendationTaste.tags(String.join("|", item.allGenres)); }
    private static void learn(Map<String, Double> profile, List<String> values, String prefix, double weight) {
        for (String value : values) profile.merge(prefix + normalized(value), weight / values.size(), Double::sum);
    }
    double score(CatalogItem item) {
        Map<String, Double> profile = profiles.get(item.type); if (profile == null) return 0;
        return overlap(profile, genres(item), "g:") + overlap(profile, item.cast, "a:") + overlap(profile, item.directors, "d:");
    }
    private static double overlap(Map<String, Double> profile, List<String> values, String prefix) {
        double score = 0;
        for (String value : values) score += Math.log1p(profile.getOrDefault(prefix + normalized(value), 0.0));
        return values.isEmpty() ? 0 : score / Math.sqrt(values.size());
    }
    boolean learned() { for (Map<String, Double> profile : profiles.values()) if (!profile.isEmpty()) return true; return false; }
    List<CatalogItem> apply(List<CatalogItem> items, int source) {
        if (source == CatalogSource.TV || source == CatalogSource.WATCHLIST || source == CatalogSource.GOOGLE) return new ArrayList<>(items);
        List<CatalogItem> result = new ArrayList<>(); Set<String> added = new HashSet<>();
        for (CatalogItem item : items) if (!excluded.contains(RecommendationTaste.key(item.type, item.id))
                && added.add(RecommendationTaste.key(item.type, item.id))) result.add(item);
        if (source != CatalogSource.TRAKT && source != CatalogSource.RECENT) return result;
        Map<CatalogItem, Double> scores = new HashMap<>(), raw = new HashMap<>();
        Map<CatalogItem, String> primaries = new HashMap<>(); double maximum = 0;
        for (CatalogItem item : result) {
            double score = score(item); raw.put(item, score); scores.put(item, score);
            primaries.put(item, primary(item)); maximum = Math.max(maximum, score);
        }
        if (maximum == 0) return result;
        // Retain provider order as a substantial prior; no candidate is added to a private feed.
        for (int i = 0; i < result.size(); i++) {
            CatalogItem item = result.get(i); scores.put(item, .65 * scores.get(item) / maximum + .35 / (1 + i / 20.0));
        }
        result.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        for (int i = 2; i < result.size(); i++) {
            String genre = primaries.get(result.get(i));
            if (genre.isEmpty() || !genre.equals(primaries.get(result.get(i - 1))) || !genre.equals(primaries.get(result.get(i - 2)))) continue;
            for (int j = i + 1; j < Math.min(result.size(), i + 6); j++) {
                if (!genre.equals(primaries.get(result.get(j))) && raw.get(result.get(j)) > 0) { result.add(i, result.remove(j)); break; }
            }
        }
        return result;
    }
    private static String primary(CatalogItem item) { List<String> genres = genres(item); return genres.isEmpty() ? "" : genres.get(0); }
}
