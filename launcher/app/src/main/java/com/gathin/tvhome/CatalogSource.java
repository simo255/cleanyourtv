package com.gathin.tvhome;

/** Stored source IDs are stable across updates, independently of their order in the header. */
final class CatalogSource {
    static final int TRAKT = 0, WATCHLIST = 1, TRENDING = 2, TV = 3, GOOGLE = 4, RECENT = 5, STREMIO = 6, COUNT = 7;
    // GOOGLE (4) remains reserved so existing preferences/caches never change identity.
    static int[] displayOrder() { return new int[]{STREMIO, TRAKT, RECENT, TRENDING}; }
    static int restore(int saved) {
        for (int source : displayOrder()) if (source == saved) return source;
        return TRAKT;
    }
    static boolean networkFeed(int source) { return (source >= TRAKT && source <= TRENDING) || source == RECENT; }
    static boolean publicFeed(int source) { return source == TRENDING || source == RECENT; }
}
