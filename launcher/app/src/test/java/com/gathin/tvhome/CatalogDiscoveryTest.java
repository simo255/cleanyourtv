package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CatalogDiscoveryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private CatalogItem item(String id, String year, String released) throws Exception {
        return new CatalogItem(new JSONObject().put("id", id).put("type", "movie").put("name", id)
                .put("releaseInfo", year).put("released", released));
    }
    @Test public void watchlyRejectsOlderSuggestionsAndFutureReleases() throws Exception {
        List<CatalogItem> input = Arrays.asList(item("tt1", "2008", "2008-01-01"), item("tt2", "2026", ""),
                item("tt3", "2027", ""), item("tt4", "2026", "2026-12-01"));
        for (boolean recent : new boolean[]{true, false}) {
            List<CatalogItem> result = CatalogDiscovery.recommendations(input, CatalogSource.WATCHLY, TODAY, recent);
            assertEquals(1, result.size()); assertEquals("tt2", result.get(0).id);
        }
    }
    @Test public void fixedRecommendationsKeep2024Through2026EvenWithAllYearsSaved() throws Exception {
        for (String type : new String[]{"movie", "series"}) {
            List<CatalogItem> input = Arrays.asList(
                    new CatalogItem(item("tt1", "2023–2026", "").json().put("type", type)),
                    new CatalogItem(item("tt2", "2024–", "").json().put("type", type)),
                    new CatalogItem(item("tt3", "2025", "").json().put("type", type)),
                    new CatalogItem(item("tt4", "2026", "").json().put("type", type)),
                    new CatalogItem(item("tt5", "2027", "").json().put("type", type)),
                    new CatalogItem(item("tt6", "", "").json().put("type", type)),
                    new CatalogItem(item("tt7", "2026", "2026-12-01").json().put("type", type)));
            for (int source : new int[]{CatalogSource.WATCHLY, CatalogSource.TRENDING}) {
                for (boolean recent : new boolean[]{true, false}) {
                    List<CatalogItem> result = CatalogDiscovery.recommendations(input, source, TODAY, recent);
                    assertEquals(Arrays.asList(input.get(1), input.get(2), input.get(3)), result);
                }
                List<CatalogItem> nextYear = CatalogDiscovery.recommendations(input, source, LocalDate.of(2027, 1, 1), false);
                assertFalse(nextYear.contains(input.get(1)));
                assertTrue(nextYear.contains(input.get(4)));
            }
        }
    }
    @Test public void recentCatalogExcludesUpcomingUnverifiedAndOlderReleasesAndDeduplicates() throws Exception {
        CatalogItem older = item("tt100", "2023", "2023-12-31T00:00:00Z");
        CatalogItem valid = item("tt101", "2026", "2026-10-20T00:00:00Z");
        CatalogItem future = item("tt102", "2026", "2026-12-20T00:00:00Z");
        CatalogItem undated = item("tt103", "2025", "");
        List<CatalogItem> result = CatalogDiscovery.recent(Arrays.asList(older, valid, future, undated, valid), TODAY);
        assertEquals(1, result.size()); assertEquals("tt101", result.get(0).id);
    }
    @Test public void newestFirstUsesDatesRatherThanProviderOrderAndKeepsBothYearsAvailable() throws Exception {
        CatalogItem a = item("tt1", "2025", "2025-12-01T00:00:00Z");
        CatalogItem b = item("tt2", "2026", "2026-10-01T00:00:00Z");
        CatalogItem c = item("tt3", "2026", "2026-02-01T00:00:00Z");
        List<CatalogItem> recent = CatalogDiscovery.recent(Arrays.asList(a, c, b), TODAY);
        assertEquals("tt2", recent.get(0).id); assertEquals("tt3", recent.get(1).id);
        assertEquals("tt1", recent.get(2).id);
    }
    @Test public void recentDiscoveryFollowsTheCurrentYearAndDoesNotMistakeFinalSeasonForPremiere() throws Exception {
        CatalogItem oldShow = item("tt1", "2016–2026", "2016-01-01T00:00:00Z");
        CatalogItem first = item("tt2", "2024–", "2024-02-01T00:00:00Z");
        CatalogItem second = item("tt3", "2026", "2026-09-01T00:00:00Z");
        CatalogItem newer = item("tt4", "2027", "2027-01-01T00:00:00Z");
        List<CatalogItem> original = Arrays.asList(oldShow, first, second, newer);
        List<CatalogItem> result = CatalogDiscovery.recent(original, LocalDate.of(2027, 3, 1));
        assertEquals(2, result.size()); assertSame(newer, result.get(0)); assertSame(second, result.get(1));
        assertEquals(4, original.size()); assertSame(oldShow, original.get(0));
    }
    @Test public void malformedDatesAndYearsAreNotPresentedAsVerifiedNewReleases() throws Exception {
        assertTrue(CatalogDiscovery.recent(Arrays.asList(item("tt1", "2026", "2026-99-40"),
                item("tt2", "unknown", "2026-01-01")), TODAY).isEmpty());
    }
    @Test public void newMetadataSurvivesCacheRoundTrip() throws Exception {
        CatalogItem before = new CatalogItem(item("tt1", "2026", "2026-01-01T00:00:00Z").json()
                .put("description", "A summary").put("imdbRating", "8.2")
                .put("genres", new org.json.JSONArray().put("Drama").put("Crime")));
        CatalogItem after = new CatalogItem(before.json());
        assertEquals(before.released, after.released); assertEquals("8.2", after.rating);
        assertEquals("Drama · Crime", after.genres); assertEquals("A summary", after.description);
    }
    @Test public void everyRecommendationSourceAutomaticallyKeepsCurrentAndTwoPreviousYearsInProviderOrder() throws Exception {
        CatalogItem old = item("tt1", "2023", "");
        CatalogItem first = item("tt2", "2025–", "");
        CatalogItem second = item("tt3", "2026", "");
        CatalogItem futureYear = item("tt4", "2027", "");
        CatalogItem unknown = item("tt5", "", "");
        CatalogItem futureRelease = item("tt6", "2026", "2026-12-01T00:00:00Z");
        List<CatalogItem> input = Arrays.asList(old, first, second, futureYear, unknown, futureRelease);
        for (int source : new int[]{CatalogSource.GOOGLE, CatalogSource.TRENDING, CatalogSource.RECENT}) {
            List<CatalogItem> result = CatalogDiscovery.recommendations(input, source, TODAY);
            assertEquals(2, result.size()); assertSame(first, result.get(0)); assertSame(second, result.get(1));
        }
        assertEquals(6, input.size());
    }
    @Test public void watchlistAndResumeMembershipAreNotRestrictedByRecommendationYears() throws Exception {
        List<CatalogItem> input = Arrays.asList(item("tt1", "2016–2026", ""), item("tt2", "", ""));
        for (int source : new int[]{CatalogSource.WATCHLIST, CatalogSource.TV}) {
            List<CatalogItem> result = CatalogDiscovery.recommendations(input, source, TODAY);
            assertEquals(input, result); result.clear(); assertEquals(2, input.size());
        }
    }
    @Test public void cachedRecommendationsUseTheNewWindowWhenTheCalendarYearChanges() throws Exception {
        List<CatalogItem> input = Arrays.asList(item("tt1", "2024", ""), item("tt2", "2026", ""), item("tt3", "2027", ""));
        List<CatalogItem> result = CatalogDiscovery.recommendations(input, CatalogSource.GOOGLE, LocalDate.of(2027, 1, 1));
        assertEquals(2, result.size()); assertEquals("tt2", result.get(0).id); assertEquals("tt3", result.get(1).id);
    }
}
