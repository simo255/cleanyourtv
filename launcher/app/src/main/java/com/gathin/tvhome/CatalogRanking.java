package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.List;

/** Reuse ranked catalogs until the provider, local choices or release date changes. */
final class CatalogRanking {
    private List<CatalogItem> input, ranked;
    private RecommendationTaste taste;
    private LocalDate date;
    private int source;

    List<CatalogItem> apply(List<CatalogItem> items, int selected, LocalDate today, RecommendationTaste profile) {
        if (input != items || source != selected || taste != profile || !today.equals(date)) {
            ranked = profile.apply(CatalogDiscovery.recommendations(items, selected, today), selected);
            input = items; source = selected; taste = profile; date = today;
        }
        return ranked;
    }
}
