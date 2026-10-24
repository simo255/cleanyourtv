package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogSourceTest {
    @Test public void previousSelectionsKeepTheirStoredIdentity() {
        assertEquals(0, CatalogSource.TRAKT);
        assertEquals(1, CatalogSource.WATCHLIST);
        assertEquals(2, CatalogSource.TRENDING);
        assertEquals(3, CatalogSource.TV);
        for (int saved = 0; saved <= 3; saved++) assertEquals(saved, CatalogSource.restore(saved));
        assertEquals(CatalogSource.GOOGLE, CatalogSource.restore(CatalogSource.GOOGLE));
    }
    @Test public void googleIsAdjacentToTraktWithoutChangingTheOtherSourceIds() {
        assertArrayEquals(new int[]{0, 4, 1, 2, 3}, CatalogSource.displayOrder());
        assertEquals(5, CatalogSource.COUNT);
    }
    @Test public void googleAndLocalAppsDoNotUseTheTraktOrPopularNetworkFeeds() {
        assertTrue(CatalogSource.networkFeed(CatalogSource.TRAKT));
        assertTrue(CatalogSource.networkFeed(CatalogSource.WATCHLIST));
        assertTrue(CatalogSource.networkFeed(CatalogSource.TRENDING));
        assertFalse(CatalogSource.networkFeed(CatalogSource.GOOGLE));
        assertFalse(CatalogSource.networkFeed(CatalogSource.TV));
        assertEquals(CatalogSource.TRAKT, CatalogSource.restore(-1));
        assertEquals(CatalogSource.TRAKT, CatalogSource.restore(99));
    }
}
