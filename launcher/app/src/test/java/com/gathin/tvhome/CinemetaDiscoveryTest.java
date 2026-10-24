package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CinemetaDiscoveryTest {
    @Test public void requestPlanIsBoundedAndDependsOnlyOnPublicCalendar() {
        List<String> paths = CinemetaDiscovery.paths(2026, 297);
        assertEquals(5, paths.size()); assertEquals("year/genre=2026", paths.get(0));
        assertEquals("year/genre=2026&skip=100", paths.get(2)); assertEquals("imdbRating", paths.get(4));
        assertEquals(paths, CinemetaDiscovery.paths(2026, 297));
        assertNotEquals(paths.get(3), CinemetaDiscovery.paths(2026, 298).get(3));
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
}
