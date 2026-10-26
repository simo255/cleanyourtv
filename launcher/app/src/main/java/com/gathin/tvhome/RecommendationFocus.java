package com.gathin.tvhome;

import java.util.List;

/** Keep navigation tied to titles when feedback changes their ranking or removes them. */
final class RecommendationFocus {
    private RecommendationFocus() { }

    static String key(CatalogItem item) { return item.type + ":" + item.id; }

    static int position(List<CatalogItem> items, String key, int fallback) {
        for (int i = 0; i < items.size(); i++) if (key(items.get(i)).equals(key)) return i;
        return items.isEmpty() ? -1 : Math.max(0, Math.min(fallback, items.size() - 1));
    }

    static String after(List<CatalogItem> items, String ratedKey) {
        int position = position(items, ratedKey, -1);
        if (position < 0) return "";
        if (position + 1 < items.size()) return key(items.get(position + 1));
        return position > 0 ? key(items.get(position - 1)) : "";
    }
}
