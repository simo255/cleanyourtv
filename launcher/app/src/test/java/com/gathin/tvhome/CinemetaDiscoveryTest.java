package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CinemetaDiscoveryTest {
    @Test public void requestPlanIsBoundedAndDependsOnlyOnPublicCalendar() {
        List<String> paths = CinemetaDiscovery.paths("movie", 2026, 297);
        assertEquals(8, paths.size()); assertEquals("year/genre=2026", paths.get(0));
        assertEquals("year/genre=2026&skip=100", paths.get(3)); assertEquals("imdbRating", paths.get(5));
        assertEquals(paths, CinemetaDiscovery.paths("movie", 2026, 297));
        assertNotEquals(paths.get(4), CinemetaDiscovery.paths("movie", 2026, 298).get(4));
        assertNotEquals(paths.get(4), paths.get(7));
        assertEquals("year/genre=2012", paths.get(6));
    }
    @Test public void recentThreeYearsAreRequestedFirstWithoutDuplicatePages() {
        for (int rotation = 0; rotation < 365; rotation++) {
            List<String> paths = CinemetaDiscovery.paths("series", 2026, rotation);
            assertEquals(Arrays.asList("year/genre=2026", "year/genre=2025", "year/genre=2024"), paths.subList(0, 3));
            assertTrue(paths.size() <= 8);
            assertEquals(paths.size(), new java.util.HashSet<>(paths).size());
        }
    }
    @Test public void allPagesContributeWithinTheCapAndDuplicatesAreRemoved() throws Exception {
        List<CatalogItem> a = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            a.add(new CatalogItem(new JSONObject().put("id", "tt" + i).put("type", "movie")));
            b.add(new CatalogItem(new JSONObject().put("id", "tt" + (i + 1000)).put("type", "movie")));
        }
        List<CatalogItem> result = CinemetaDiscovery.merge(Arrays.asList(a, b, a));
        assertEquals(300, result.size()); assertEquals("tt0", result.get(0).id); assertEquals("tt1000", result.get(1).id);
        assertEquals(300, result.stream().map(i -> i.id).distinct().count());
    }
    @Test public void seriesRotationReachesTvOnlyGenres() {
        java.util.Set<String> paths = new java.util.HashSet<>();
        for (int i = 0; i < 22; i++) paths.add(CinemetaDiscovery.paths("series", 2026, i).get(4));
        for (String genre : new String[]{"Reality-TV", "Talk-Show", "Game-Show"}) assertTrue(paths.contains("top/genre=" + genre));
    }
    @Test public void movieRotationNeverRequestsTvOnlyGenres() {
        for (int i = 0; i <= 100; i++) {
            for (String path : CinemetaDiscovery.paths("movie", 2026, i))
                for (String genre : new String[]{"Reality-TV", "Talk-Show", "Game-Show"}) assertFalse(path.contains(genre));
        }
    }
    private CatalogItem item(int id, String name) throws Exception {
        return new CatalogItem(new JSONObject().put("id", "tt" + id).put("type", "movie").put("name", name));
    }
    @Test public void refreshedMetadataWinsAndOlderDiscoveriesSurviveWithinTheCap() throws Exception {
        List<CatalogItem> fresh = new ArrayList<>(), previous = new ArrayList<>();
        for (int i = 0; i < 300; i++) { fresh.add(item(i, "fresh")); previous.add(item(i + 200, "old")); }
        List<CatalogItem> result = CinemetaDiscovery.retain(fresh, previous);
        assertEquals(300, result.size());
        assertEquals(300, result.stream().map(i -> i.id).distinct().count());
        assertEquals(75, result.stream().filter(i -> i.name.equals("old")).count());
        assertTrue(result.stream().filter(i -> Integer.parseInt(i.id.substring(2)) < 300).allMatch(i -> i.name.equals("fresh")));
        assertEquals("tt300", result.get(3).id);
    }
    @Test public void partialRefreshRetainsMissingPagesAndEmptyHistoryNeedsNoSpecialCase() throws Exception {
        CatalogItem updated = item(1, "updated"), oldCopy = item(1, "stale"), other = item(2, "retained");
        assertEquals(Arrays.asList(updated, other), CinemetaDiscovery.retain(Arrays.asList(updated), Arrays.asList(oldCopy, other)));
        assertEquals(Arrays.asList(updated), CinemetaDiscovery.retain(Arrays.asList(updated), java.util.Collections.emptyList()));
    }
}
