package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogSourceTest {
    @Test public void previousSelectionsKeepTheirStoredIdentity() {
        assertEquals(0, CatalogSource.TRAKT);
        assertEquals(1, CatalogSource.WATCHLIST);
        assertEquals(2, CatalogSource.TRENDING);
        assertEquals(3, CatalogSource.TV);
        for (int saved : new int[]{CatalogSource.TRAKT, CatalogSource.GOOGLE, CatalogSource.RECENT, CatalogSource.TRENDING})
            assertEquals(saved, CatalogSource.restore(saved));
    }
    @Test public void googleIsAdjacentToTraktWithoutChangingTheOtherSourceIds() {
        assertArrayEquals(new int[]{0, 4, 5, 2}, CatalogSource.displayOrder());
        assertEquals(6, CatalogSource.COUNT);
    }
    @Test public void removedSectionsFallBackToTraktWhenRestoringSavedPreferences() {
        assertEquals(CatalogSource.TRAKT, CatalogSource.restore(CatalogSource.WATCHLIST));
        assertEquals(CatalogSource.TRAKT, CatalogSource.restore(CatalogSource.TV));
    }
    @Test public void googleAndLocalAppsDoNotUseTheTraktOrPopularNetworkFeeds() {
        assertTrue(CatalogSource.networkFeed(CatalogSource.TRAKT));
        assertTrue(CatalogSource.networkFeed(CatalogSource.WATCHLIST));
        assertTrue(CatalogSource.networkFeed(CatalogSource.TRENDING));
        assertTrue(CatalogSource.networkFeed(CatalogSource.RECENT));
        assertTrue(CatalogSource.publicFeed(CatalogSource.RECENT));
        assertFalse(CatalogSource.publicFeed(CatalogSource.TRAKT));
        assertFalse(CatalogSource.networkFeed(CatalogSource.GOOGLE));
        assertFalse(CatalogSource.networkFeed(CatalogSource.TV));
        assertEquals(CatalogSource.TRAKT, CatalogSource.restore(-1));
        assertEquals(CatalogSource.TRAKT, CatalogSource.restore(99));
    }
}
