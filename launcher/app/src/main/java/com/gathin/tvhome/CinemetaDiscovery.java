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
    static List<String> paths(int year, int rotation) {
        List<String> paths = new ArrayList<>(), selected = new ArrayList<>();
        // Public calendar rotation, independent of private history or learned interests.
        selected.add(GENRES[Math.floorMod(rotation, 19)]);
        paths.add("year/genre=" + year);
        paths.add("year/genre=" + (year - 1));
        paths.add("year/genre=" + year + "&skip=100");
        paths.add("top/genre=" + selected.get(0));
        paths.add("imdbRating");
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
}
