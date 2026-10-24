package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.List;

/** Reuse ranked catalogs until the provider, local choices or release date changes. */
final class CatalogRanking {
    private List<CatalogItem> input, ranked;
    private RecommendationTaste taste;
    private LocalDate date;
    private int source;
    private boolean recentOnly;

    List<CatalogItem> apply(List<CatalogItem> items, int selected, LocalDate today, RecommendationTaste profile) {
        return apply(items, selected, today, profile, true);
    }
    List<CatalogItem> apply(List<CatalogItem> items, int selected, LocalDate today, RecommendationTaste profile, boolean recent) {
        if (input != items || source != selected || taste != profile || !today.equals(date) || recentOnly != recent) {
            ranked = profile.apply(CatalogDiscovery.recommendations(items, selected, today, recent), selected);
            input = items; source = selected; taste = profile; date = today;
            recentOnly = recent;
        }
        return ranked;
    }
}
