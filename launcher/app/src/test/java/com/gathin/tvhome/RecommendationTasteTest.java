package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RecommendationTasteTest {
    @Test public void watchedAndLikedHidesTheTitleAndCountsItsPreferenceOnce() throws Exception {
        JSONObject record = entry("tt1", "Crime");
        JSONArray combined = new JSONArray().put(new JSONObject(record.toString()).put("decision", RecommendationTaste.SEEN_LIKED));
        RecommendationTaste both = new RecommendationTaste(new JSONArray(), combined);
        RecommendationTaste liked = new RecommendationTaste(new JSONArray(), new JSONArray()
                .put(new JSONObject(record.toString()).put("decision", RecommendationTaste.MORE)));
        assertTrue(both.apply(Arrays.asList(item("tt1", "Crime")), CatalogSource.WATCHLY).isEmpty());
        assertEquals(liked.score(item("tt9", "Crime")), both.score(item("tt9", "Crime")), 0);
        RecommendationTaste imported = new RecommendationTaste(new JSONArray()
                .put(new JSONObject(record.toString()).put("completed", true)), combined);
        assertEquals(both.score(item("tt9", "Crime")), imported.score(item("tt9", "Crime")), 0);
    }
    private JSONObject entry(String id, String genre) throws Exception {
        return new JSONObject().put("id", id).put("type", "series").put("name", id).put("genre", genre);
    }
    private CatalogItem item(String id, String genre) throws Exception { return new CatalogItem(entry(id, genre)); }
    @Test public void watchlyAllowsNearbyRefinementButDoesNotReplaceItsTopPickWithADistantMatch() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray(), new JSONArray()
                .put(entry("tt90", "Crime").put("decision", "more")));
        List<CatalogItem> input = new java.util.ArrayList<>();
        for (int i = 1; i < 20; i++) input.add(item("tt" + i, "Comedy"));
        CatalogItem match = item("tt20", "Crime"); input.add(match);
        List<CatalogItem> watchly = taste.apply(input, CatalogSource.WATCHLY);
        assertSame(input.get(0), watchly.get(0));
        assertTrue(watchly.indexOf(match) < 19);
        assertSame(match, taste.apply(input, CatalogSource.RECENT).get(0));
        assertSame(match, taste.apply(Arrays.asList(input.get(0), match), CatalogSource.WATCHLY).get(0));
        assertEquals(new java.util.HashSet<>(input), new java.util.HashSet<>(watchly));
        assertSame(match, input.get(19));
    }
    @Test public void completedViewingIsPositiveAndAnIncompleteRecordIsEntirelyNeutral() throws Exception {
        JSONArray complete = new JSONArray().put(entry("tt1", "Crime").put("completed", true))
                .put(entry("tt2", "Comedy").put("completed", false));
        RecommendationTaste taste = new RecommendationTaste(complete, new JSONArray());
        CatalogItem comedy = item("tt3", "Comedy"), crime = item("tt4", "Crime");
        assertTrue(taste.score(crime) > 0); assertEquals(0, taste.score(comedy), 0);
        assertEquals("tt4", taste.apply(Arrays.asList(comedy, crime), CatalogSource.WATCHLY).get(0).id);
    }
    @Test public void incompleteViewingDoesNotInferADislikeOrChangeOrdering() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray().put(entry("tt1", "Crime").put("completed", false)), new JSONArray());
        List<CatalogItem> input = Arrays.asList(item("tt3", "Comedy"), item("tt4", "Crime"));
        assertFalse(taste.learned()); assertEquals(input, taste.apply(input, CatalogSource.WATCHLY));
    }
    @Test public void manualRejectionHidesOnlyTheChosenTitleWithoutPenalizingItsGenre() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray(), new JSONArray().put(entry("tt1", "Crime").put("decision", "hide")));
        CatalogItem another = item("tt2", "Crime");
        assertEquals(0, taste.score(another), 0);
        assertEquals(Arrays.asList(another), taste.apply(Arrays.asList(item("tt1", "Crime"), another), CatalogSource.GOOGLE));
    }
    @Test public void googleAndTrendingKeepTheirOrderAndWatchlistIsNeverPrunedByFeedback() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray().put(entry("tt1", "Crime").put("completed", true)),
                new JSONArray().put(entry("tt3", "Comedy").put("decision", "hide")));
        List<CatalogItem> input = Arrays.asList(item("tt2", "Comedy"), item("tt4", "Crime"));
        assertEquals(input, taste.apply(input, CatalogSource.GOOGLE)); assertEquals(input, taste.apply(input, CatalogSource.TRENDING));
        List<CatalogItem> watchlist = Arrays.asList(item("tt1", "Crime"), item("tt3", "Comedy"));
        assertEquals(watchlist, taste.apply(watchlist, CatalogSource.WATCHLIST));
    }
    @Test public void completedSeriesChoiceAddsPositiveAffinityAndCanBeResetWithoutDeletingImportedPositives() throws Exception {
        JSONArray imported = new JSONArray().put(entry("tt1", "Crime").put("completed", true));
        JSONArray manual = new JSONArray().put(entry("tt2", "Comedy").put("decision", "completed"));
        RecommendationTaste taste = new RecommendationTaste(imported, manual);
        assertTrue(taste.score(item("tt9", "Comedy")) > 0);
        assertTrue(taste.apply(Arrays.asList(item("tt2", "Comedy")), CatalogSource.RECENT).isEmpty());
        RecommendationTaste reset = new RecommendationTaste(imported, new JSONArray());
        assertEquals(0, reset.score(item("tt9", "Comedy")), 0); assertTrue(reset.score(item("tt8", "Crime")) > 0);
    }
    @Test public void importedCompletedTitlesAreExcludedFromPersonalDiscoveryButNotGoogle() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray().put(entry("tt1", "Crime").put("completed", true)), new JSONArray());
        List<CatalogItem> input = Arrays.asList(item("tt1", "Crime"));
        assertTrue(taste.apply(input, CatalogSource.WATCHLY).isEmpty()); assertEquals(input, taste.apply(input, CatalogSource.GOOGLE));
    }
    @Test public void frenchAndEnglishGenresMatchAndRepeatedGenreRunsGetAnotherRelevantOption() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray().put(entry("tt1", "Drama").put("completed", true))
                .put(entry("tt2", "Comedy").put("completed", true)), new JSONArray());
        assertTrue(taste.score(item("tt9", "Drame")) > 0);
        List<CatalogItem> input = Arrays.asList(item("tt3", "Drama"), item("tt4", "Drama"), item("tt5", "Drama"), item("tt6", "Comedy"));
        assertEquals("tt6", taste.apply(input, CatalogSource.RECENT).get(2).id);
        assertEquals(4, input.size());
    }
}
