package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed request and candidate budgets using only catalog extras in Cinemeta's manifest. */
final class CinemetaDiscovery {
    static final int MAX_ITEMS = 300;
    private static final String[] GENRES = {"Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
            "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Mystery", "Romance", "Sci-Fi",
            "Sport", "Thriller", "War", "Western", "Reality-TV", "Talk-Show", "Game-Show"};
    // The first 19 genres are supported by Cinemeta's movie catalog.
    private static final int MOVIE_GENRES = 19;
    static List<String> paths(String type, int year, int rotation) {
        List<String> paths = new ArrayList<>(), selected = new ArrayList<>();
        // Public calendar rotation, independent of private history or learned interests.
        int count = "series".equals(type) ? GENRES.length : MOVIE_GENRES;
        selected.add(GENRES[Math.floorMod(rotation, count)]);
        paths.add("year/genre=" + year);
        paths.add("year/genre=" + (year - 1));
        paths.add("year/genre=" + (year - 2));
        paths.add("year/genre=" + year + "&skip=100");
        paths.add("top/genre=" + selected.get(0));
        paths.add("imdbRating");
        // Broaden older discoveries without disclosing tastes in outgoing requests.
        String olderYear = "year/genre=" + (year - 2 - Math.floorMod(rotation, 19));
        if (!paths.contains(olderYear)) paths.add(olderYear);
        paths.add("top/genre=" + GENRES[Math.floorMod(rotation + count / 2, count)]);
        return paths;
    }
    static List<CatalogItem> merge(List<List<CatalogItem>> pages) {
        List<CatalogItem> result = new ArrayList<>(); Set<String> added = new HashSet<>();
        // Round-robin preserves some candidates from each catalog within the memory cap.
        for (int row = 0; row < MAX_ITEMS && result.size() < MAX_ITEMS; row++) for (List<CatalogItem> page : pages) {
            if (row >= page.size() || result.size() == MAX_ITEMS) continue;
            CatalogItem item = page.get(row);
            if (added.add(RecommendationTaste.key(item.type, item.id))) result.add(item);
        }
        return result;
    }

    static List<CatalogItem> retain(List<CatalogItem> fresh, List<CatalogItem> previous) {
        // Three fresh slots to one older slot; finite memory and deterministic refreshes.
        List<CatalogItem> result = new ArrayList<>(); Set<String> added = new HashSet<>();
        Set<String> freshIds = new HashSet<>();
        for (CatalogItem item : fresh) freshIds.add(RecommendationTaste.key(item.type, item.id));
        int current = 0, old = 0;
        while (result.size() < MAX_ITEMS && (current < fresh.size() || old < previous.size())) {
            for (int i = 0; i < 3 && current < fresh.size() && result.size() < MAX_ITEMS; i++)
                add(result, added, fresh.get(current++));
            while (old < previous.size() && freshIds.contains(RecommendationTaste.key(previous.get(old).type, previous.get(old).id))) old++;
            if (old < previous.size() && result.size() < MAX_ITEMS) add(result, added, previous.get(old++));
        }
        return result;
    }
    private static void add(List<CatalogItem> result, Set<String> added, CatalogItem item) {
        if (added.add(RecommendationTaste.key(item.type, item.id))) result.add(item);
    }
}
