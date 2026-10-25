package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Current year plus two previous years; saved lists and resume entries stay complete. */
final class CatalogDiscovery {
    private CatalogDiscovery() { }
    static int year(CatalogItem item) {
        String value = item.year.trim();
        if (value.length() < 4 || !value.substring(0, 4).matches("[0-9]{4}")) return 0;
        return Integer.parseInt(value.substring(0, 4)); // series premiere, not the final year of a range
    }
    static LocalDate released(CatalogItem item) {
        if (item.released.length() < 10) return null;
        try { return LocalDate.parse(item.released.substring(0, 10)); }
        catch (DateTimeParseException ignored) { return null; }
    }
    static List<CatalogItem> recommendations(List<CatalogItem> items, int source, LocalDate today) {
        return recommendations(items, source, today, true);
    }
    static List<CatalogItem> recommendations(List<CatalogItem> items, int source, LocalDate today, boolean recentOnly) {
        if (source != CatalogSource.WATCHLY && source != CatalogSource.GOOGLE
                && source != CatalogSource.TRENDING && source != CatalogSource.RECENT) return new ArrayList<>(items);
        List<CatalogItem> result = new ArrayList<>();
        for (CatalogItem item : items) {
            int year = year(item);
            LocalDate date = released(item);
            // Watchly owns its configured year range; local Discover can opt into all years.
            boolean allYears = source == CatalogSource.WATCHLY || (source == CatalogSource.RECENT && !recentOnly);
            if (year > 0 && (allYears || year >= today.getYear() - 2) && year <= today.getYear()
                    && (date == null || !date.isAfter(today))) result.add(item);
        }
        return result;
    }
    static List<CatalogItem> recent(List<CatalogItem> items, LocalDate today) {
        List<CatalogItem> result = new ArrayList<>(); Set<String> added = new HashSet<>();
        for (CatalogItem item : recommendations(items, CatalogSource.RECENT, today)) {
            LocalDate date = released(item);
            if (date != null && added.add(item.id)) result.add(item);
        }
        result.sort((a, b) -> released(b).compareTo(released(a)));
        return result;
    }
}
