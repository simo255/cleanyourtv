package com.gathin.tvhome;

/** Stored source IDs are stable across updates, independently of their order in the header. */
final class CatalogSource {
    static final int TRAKT = 0, WATCHLIST = 1, TRENDING = 2, TV = 3, GOOGLE = 4, COUNT = 5;
    static int[] displayOrder() { return new int[]{TRAKT, GOOGLE, WATCHLIST, TRENDING, TV}; }
    static int restore(int saved) { return saved >= 0 && saved < COUNT ? saved : TRAKT; }
    static boolean networkFeed(int source) { return source >= TRAKT && source <= TRENDING; }
}
