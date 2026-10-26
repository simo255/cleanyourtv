package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WatchlyCatalogTest {
    private static final String BASE = "https://watchly.elfhosted.com/example_token/";
    @Test public void acceptsOnlyThePrivateElfHostedTokenPath() {
        assertEquals(BASE, WatchlyCatalog.base(BASE + "manifest.json"));
        assertEquals(BASE, WatchlyCatalog.base(BASE));
        for (String bad : new String[]{BASE.replace("https:", "http:"), BASE + "?secret=x", BASE + "#fragment",
                BASE.replace("watchly.elfhosted.com", "watchly.elfhosted.com.evil.example"),
                BASE.replace("watchly.elfhosted.com", "user@watchly.elfhosted.com"),
                BASE.replace("watchly.elfhosted.com", "watchly.elfhosted.com:443"),
                BASE.replace("example_token", "../another"), BASE.replace("example_token", "%2Fother"),
                "https://www.strem.io/trakt/addon/old/", BASE + "\n"}) {
            try { WatchlyCatalog.base(bad); fail("Accepted an unsafe private catalog address"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void doesNotConfuseTopPicksWithRewatchOrOtherCategories() throws Exception {
        JSONObject manifest = new JSONObject().put("catalogs", new JSONArray()
                .put(new JSONObject().put("type", "movie").put("id", "watchly.rewatch"))
                .put(new JSONObject().put("type", "series").put("id", "watchly.rec")));
        assertEquals("watchly.rec", WatchlyCatalog.topPicks(manifest, "series"));
        try { WatchlyCatalog.topPicks(manifest, "movie"); fail("Must not substitute rewatch results"); }
        catch (IllegalArgumentException expected) { }
    }

    private JSONObject row(String type, String id) throws Exception {
        return new JSONObject().put("type", type).put("id", id);
    }

    @Test public void supplementsUseOnlyEnabledSafePersonalizedRowsForTheSelectedType() throws Exception {
        JSONObject manifest = new JSONObject().put("catalogs", new JSONArray()
                .put(row("movie", "watchly.rec")).put(row("movie", "watchly.rewatch"))
                .put(row("series", "watchly.theme.1")).put(row("movie", "watchly.item.1"))
                .put(row("movie", "watchly.item.1")).put(row("movie", "watchly.theme.1"))
                .put(row("movie", "watchly.theme.2").put("extra", new JSONArray()
                        .put(new JSONObject().put("name", "search").put("isRequired", true))))
                .put(row("movie", "watchly.theme.3/../../manifest"))
                .put(row("movie", "watchly.all.loved")).put(row("movie", "watchly.creators"))
                .put(row("movie", "watchly.liked.all")));
        assertEquals(Arrays.asList("watchly.item.1", "watchly.theme.1", "watchly.all.loved", "watchly.creators"),
                WatchlyCatalog.supplements(manifest, "movie"));
        assertEquals(Arrays.asList("watchly.theme.1"), WatchlyCatalog.supplements(manifest, "series"));
        assertTrue(WatchlyCatalog.supplements(new JSONObject(), "movie").isEmpty());
    }

    private CatalogItem item(int id, int year) throws Exception {
        return new CatalogItem(row("series", "tt" + id).put("name", "Title " + id).put("releaseInfo", "" + year));
    }

    @Test public void refillCountsVisiblePicksAfterDateCompletionAndHiddenExclusions() throws Exception {
        LocalDate today = LocalDate.of(2026, 10, 25);
        JSONArray choices = new JSONArray().put(row("series", "tt1").put("decision", "hide"))
                .put(row("series", "tt2").put("decision", "completed"));
        RecommendationTaste taste = new RecommendationTaste(new JSONArray(), choices);
        List<CatalogItem> items = new ArrayList<>();
        for (int i = 1; i <= 21; i++) items.add(item(i, 2025));
        items.add(item(100, 2023)); items.add(item(101, 2027));
        items.add(new CatalogItem(item(102, 2026).json().put("released", "2026-12-01")));
        assertTrue(WatchlyCatalog.needsMore(items, today, taste)); // Only 19 can be displayed.
        items.add(item(22, 2024));
        assertFalse(WatchlyCatalog.needsMore(items, today, taste));
    }

    @Test public void supplementsKeepTopPicksMetadataAndDeduplicateAcrossRows() throws Exception {
        CatalogItem top = item(1, 2025), second = item(2, 2026);
        List<CatalogItem> primary = Arrays.asList(top);
        List<CatalogItem> result = WatchlyCatalog.append(primary, Arrays.asList(item(1, 2024), second, second));
        assertEquals(Arrays.asList(top, second), result);
        assertSame(top, result.get(0)); assertEquals(1, primary.size());
    }
}
