package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogRankingTest {
    private final LocalDate today = LocalDate.of(2026, 10, 24);
    private final RecommendationTaste neutral = new RecommendationTaste(new JSONArray(), new JSONArray());
    private CatalogItem item(String id, String released) throws Exception {
        return new CatalogItem(new JSONObject().put("id", id).put("type", "movie")
                .put("name", id).put("year", released.substring(0, 4)).put("released", released));
    }
    @Test public void repeatedTabVisitsReuseRankingButNewProviderDataReplacesIt() throws Exception {
        CatalogRanking cache = new CatalogRanking();
        List<CatalogItem> first = Arrays.asList(item("tt1", "2026-01-01"));
        List<CatalogItem> ranked = cache.apply(first, CatalogSource.TRAKT, today, neutral);
        assertSame(ranked, cache.apply(first, CatalogSource.TRAKT, today, neutral));
        List<CatalogItem> next = Arrays.asList(item("tt2", "2026-01-02"));
        assertEquals("tt2", cache.apply(next, CatalogSource.TRAKT, today, neutral).get(0).id);
    }
    @Test public void feedbackAndResetImmediatelyChangeCachedResults() throws Exception {
        CatalogRanking cache = new CatalogRanking();
        List<CatalogItem> items = Arrays.asList(item("tt1", "2026-01-01"));
        assertEquals(1, cache.apply(items, CatalogSource.TRAKT, today, neutral).size());
        RecommendationTaste hidden = new RecommendationTaste(new JSONArray(), new JSONArray().put(
                new JSONObject().put("id", "tt1").put("type", "movie").put("decision", "hide")));
        assertTrue(cache.apply(items, CatalogSource.TRAKT, today, hidden).isEmpty());
        assertEquals(1, cache.apply(items, CatalogSource.TRAKT, today, neutral).size());
    }
    @Test public void releaseDatesAndYearBoundaryInvalidateCachedResults() throws Exception {
        CatalogRanking cache = new CatalogRanking();
        List<CatalogItem> items = Arrays.asList(item("tt1", "2025-01-01"), item("tt2", "2026-10-25"));
        assertEquals(1, cache.apply(items, CatalogSource.RECENT, today, neutral).size());
        assertEquals(2, cache.apply(items, CatalogSource.RECENT, today.plusDays(1), neutral).size());
        List<CatalogItem> nextYear = cache.apply(items, CatalogSource.RECENT, LocalDate.of(2027, 1, 1), neutral);
        assertEquals(1, nextYear.size()); assertEquals("tt2", nextYear.get(0).id);
    }
}
