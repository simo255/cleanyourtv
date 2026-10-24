package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Completed viewing is positive. Incomplete/unknown viewing contributes no negative signal. */
final class RecommendationTaste {
    static final String MORE = "more", HIDE = "hide", SEEN = "seen", COMPLETED = "completed";
    private final Map<String, Double> affinity = new HashMap<>();
    private final Set<String> completed = new HashSet<>(), hidden = new HashSet<>();

    RecommendationTaste(JSONArray finished, JSONArray choices) {
        Set<String> learned = new HashSet<>();
        if (finished != null) for (int i = 0; i < Math.min(1000, finished.length()); i++) {
            JSONObject record = finished.optJSONObject(i);
            if (record == null || !record.optBoolean("completed", false) || !valid(record)) continue;
            String key = key(record.optString("type"), record.optString("id"));
            completed.add(key);
            if (learned.add(key)) learn(tags(record), 1);
        }
        if (choices != null) for (int i = 0; i < Math.min(300, choices.length()); i++) {
            JSONObject record = choices.optJSONObject(i);
            if (record == null || !valid(record)) continue;
            String decision = record.optString("decision"), key = key(record.optString("type"), record.optString("id"));
            if (MORE.equals(decision)) learn(tags(record), 2);
            if (COMPLETED.equals(decision)) learn(tags(record), 1);
            if (HIDE.equals(decision) || SEEN.equals(decision) || COMPLETED.equals(decision)) hidden.add(key);
        }
    }
    static String key(String type, String id) { return type + ":" + id; }
    static boolean valid(JSONObject item) {
        return item.optString("id").matches("tt[0-9]+")
                && ("series".equals(item.optString("type")) || "movie".equals(item.optString("type")));
    }
    private static List<String> tags(JSONObject record) {
        JSONArray genres = record.optJSONArray("genres");
        if (genres == null) return tags(record.optString("genre"));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(12, genres.length()); i++) result.addAll(tags(genres.optString(i)));
        return new ArrayList<>(new LinkedHashSet<>(result));
    }
    static List<String> tags(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String raw : value.split("[·,&/|]")) {
            String tag = raw.trim().toLowerCase(Locale.ROOT);
            switch (tag) {
                case "drame": tag = "drama"; break;
                case "comédie": tag = "comedy"; break;
                case "mystère": tag = "mystery"; break;
                case "aventure": tag = "adventure"; break;
                case "documentaire": tag = "documentary"; break;
                case "famille": tag = "family"; break;
                case "horreur": tag = "horror"; break;
                case "fantastique": tag = "fantasy"; break;
                case "guerre": tag = "war"; break;
                case "histoire": tag = "history"; break;
                default: break;
            }
            if (tag.equals("science fiction") || tag.equals("science-fiction") || tag.equals("sci-fi")) tag = "science fiction";
            if (tag.equals("politics")) tag = "war";
            if (!tag.isEmpty() && tag.length() <= 60) result.add(tag);
        }
        return new ArrayList<>(result);
    }
    private void learn(List<String> genres, double weight) {
        if (genres.isEmpty()) return;
        for (String genre : genres) affinity.put(genre, affinity.getOrDefault(genre, 0.0) + weight / genres.size());
    }
    double score(CatalogItem item) {
        List<String> genres = tags(item.genres); double score = 0;
        for (String genre : genres) score += affinity.getOrDefault(genre, 0.0);
        return genres.isEmpty() ? 0 : score / Math.sqrt(genres.size());
    }
    List<CatalogItem> apply(List<CatalogItem> items, int source) {
        if (source == CatalogSource.TV || source == CatalogSource.WATCHLIST) return new ArrayList<>(items);
        boolean personalized = source == CatalogSource.TRAKT || source == CatalogSource.RECENT;
        List<CatalogItem> result = new ArrayList<>();
        for (CatalogItem item : items) {
            String key = key(item.type, item.id);
            if (!hidden.contains(key) && (!personalized || !completed.contains(key))) result.add(item);
        }
        if (!personalized || affinity.isEmpty()) return result;
        // Sorting compares each title repeatedly. Parse its genres and score only once.
        Map<CatalogItem, Double> scores = new HashMap<>();
        Map<CatalogItem, String> primaryGenres = new HashMap<>();
        for (CatalogItem item : result) {
            scores.put(item, score(item));
            primaryGenres.put(item, primary(item));
        }
        // Java's stable sort keeps provider order when the learned scores tie.
        result.sort(Comparator.comparingDouble((CatalogItem item) -> scores.get(item)).reversed());
        // Avoid three identical leading genres in a row when another relevant option is nearby.
        for (int i = 2; i < result.size(); i++) {
            String first = primaryGenres.get(result.get(i - 1));
            if (first.isEmpty() || !first.equals(primaryGenres.get(result.get(i - 2))) || !first.equals(primaryGenres.get(result.get(i)))) continue;
            for (int j = i + 1; j < Math.min(result.size(), i + 6); j++) {
                if (!first.equals(primaryGenres.get(result.get(j))) && scores.get(result.get(j)) > 0) {
                    result.add(i, result.remove(j)); break;
                }
            }
        }
        return result;
    }
    private String primary(CatalogItem item) { List<String> genres = tags(item.genres); return genres.isEmpty() ? "" : genres.get(0); }
    boolean learned() { return !affinity.isEmpty(); }
}
