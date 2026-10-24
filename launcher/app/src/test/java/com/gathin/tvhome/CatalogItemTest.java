package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class CatalogItemTest {
    @Test public void genreArraysDisplayWithoutJsonSyntaxIncludingSavedCatalogs() throws Exception {
        for (String type : new String[]{"movie", "series"}) {
            CatalogItem item = new CatalogItem(movie("tt123").put("type", type)
                    .put("genre", new JSONArray().put("Drama").put("Mystery")));
            assertEquals("Drama · Mystery", item.genreLabel());
            assertEquals("Drama · Mystery", new CatalogItem(item.json()).genreLabel());
            assertEquals("[\"Drama\",\"Mystery\"]", item.genres);
        }
        assertEquals("Drama", new CatalogItem(movie("tt123").put("genre", "Drama")).genreLabel());
        assertEquals("", new CatalogItem(movie("tt123").put("genre", "[]")).genreLabel());
    }

    private JSONObject movie(String id) throws Exception {
        return new JSONObject().put("id", id).put("type", "movie").put("name", "Example");
    }

    @Test public void watchedExclusionsDoNotChangeWatchlistOrPopularMembership() throws Exception {
        JSONArray input = new JSONArray().put(movie("tt123")).put(movie("tt456"));
        assertEquals(1, CatalogItem.parse(input, "movie", true, Collections.singleton("tt123")).size());
        List<CatalogItem> watchlist = CatalogItem.parse(input, "movie", false, Collections.singleton("tt123"));
        assertEquals(2, watchlist.size());
        assertEquals("tt123", watchlist.get(0).id);
    }

    @Test public void providerOrderIsRetainedWhileDuplicatesAndInvalidEntriesAreRemoved() throws Exception {
        JSONArray input = new JSONArray().put(movie("tt456")).put(movie("tt123")).put(movie("tt456"))
                .put(movie("not-an-imdb-id")).put(movie("tt789").put("type", "series"))
                .put(JSONObject.NULL).put(movie("tt999").put("name", " "));
        List<CatalogItem> result = CatalogItem.parse(input, "movie", false, Collections.emptySet());
        assertEquals(2, result.size());
        assertEquals("tt456", result.get(0).id);
        assertEquals("tt123", result.get(1).id);
    }

    @Test public void emptyWatchlistIsValidButErrorPayloadCannotReplaceCache() throws Exception {
        assertEquals(0, CatalogItem.metas(new JSONObject().put("metas", new JSONArray())).length());
        for (JSONObject bad : new JSONObject[]{new JSONObject(), new JSONObject().put("metas", "wrong"),
                new JSONObject().put("metas", new JSONArray()).put("error", "expired")}) {
            try { CatalogItem.metas(bad); fail("Bad response accepted"); } catch (Exception expected) { }
        }
    }

    @Test public void cacheCannotCrossSourcesOrAccountsAndLegacyRecommendationsStillLoad() throws Exception {
        JSONObject old = new JSONObject().put("account", "account-a");
        assertTrue(CatalogItem.cacheMatches(old, "account-a", 0));
        assertFalse(CatalogItem.cacheMatches(old, "account-a", 1));
        JSONObject watchlist = new JSONObject().put("account", "account-a").put("source", 1);
        assertTrue(CatalogItem.cacheMatches(watchlist, "account-a", 1));
        assertFalse(CatalogItem.cacheMatches(watchlist, "account-b", 1));
        assertFalse(CatalogItem.cacheMatches(watchlist, "account-a", 2));
    }

    @Test public void cinemetaFallbackFieldsAndCacheRoundTripPreserveDetails() throws Exception {
        CatalogItem item = new CatalogItem(new JSONObject().put("imdb_id", "tt123").put("type", "series")
                .put("name", "Series").put("releaseInfo", "2020–2026").put("poster", "https://example.test/poster.jpg"));
        CatalogItem restored = new CatalogItem(item.json());
        assertEquals("tt123", restored.id);
        assertEquals("2020–2026", restored.year);
        assertEquals(item.poster, restored.poster);
    }

    @Test public void nullMetadataIsAbsent() throws Exception {
        CatalogItem item = new CatalogItem(movie("tt123").put("imdbRating", JSONObject.NULL)
                .put("description", JSONObject.NULL).put("genre", "null").put("poster", JSONObject.NULL));
        assertEquals("", item.rating); assertEquals("", item.description);
        assertEquals("", item.genres); assertEquals("", item.poster);
        assertEquals("", new CatalogItem(item.json()).rating);
    }
}
