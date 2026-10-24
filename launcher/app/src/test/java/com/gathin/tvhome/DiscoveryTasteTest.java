package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class DiscoveryTasteTest {
    private JSONObject record(String id, String type, String genre) throws Exception {
        return new JSONObject().put("id", id).put("type", type).put("name", id).put("genres", new JSONArray().put(genre));
    }
    private RecommendationTaste taste(JSONArray complete, JSONArray choices, JSONArray viewing) {
        return new RecommendationTaste(complete, choices, viewing, new JSONArray(), Collections.singleton("tt90"));
    }
    @Test public void movieAndSeriesProfilesStaySeparateAndIncompleteIsNeutral() throws Exception {
        RecommendationTaste taste = taste(new JSONArray().put(record("tt1", "series", "Crime").put("completed", true)), new JSONArray(), new JSONArray());
        assertTrue(taste.score(new CatalogItem(record("tt2", "series", "Crime"))) > 0);
        assertEquals(0, taste.score(new CatalogItem(record("tt2", "movie", "Crime"))), 0);
        JSONObject unfinished = record("tt1", "series", "Crime").put("seasons", new JSONArray().put(SeasonViewingTest.season(1, 8, 7, "unfinished")));
        assertFalse(taste(new JSONArray(), new JSONArray(), new JSONArray().put(unfinished)).learned());
    }
    @Test public void missingLiveHistoryDoesNotErasePriorPositiveTasteButExplicitReviewCanCorrectIt() throws Exception {
        JSONArray completed = new JSONArray().put(record("tt1", "series", "Crime").put("completed", true));
        JSONObject unknown = record("tt1", "series", "Crime").put("seasons", new JSONArray().put(SeasonViewingTest.season(1, 8, 0, "")));
        RecommendationTaste taste = taste(completed, new JSONArray(), new JSONArray().put(unknown));
        assertTrue(taste.learned());
        unknown.getJSONArray("seasons").getJSONObject(0).put("status", "unfinished");
        assertFalse(taste(completed, new JSONArray(), new JSONArray().put(unknown)).learned());
    }
    @Test public void completedSeasonLearnsWithoutHidingSeriesWithLaterUnwatchedSeason() throws Exception {
        JSONObject viewed = record("tt1", "series", "Crime").put("seasons", new JSONArray()
                .put(SeasonViewingTest.season(1, 8, 8, "watched")).put(SeasonViewingTest.season(2, 8, 0, "unwatched")));
        JSONArray old = new JSONArray().put(record("tt1", "series", "Crime").put("completed", true));
        RecommendationTaste taste = taste(old, new JSONArray().put(record("tt1", "series", "Crime").put("decision", "completed")), new JSONArray().put(viewed));
        assertTrue(taste.learned());
        assertEquals(1, taste.apply(Arrays.asList(new CatalogItem(viewed)), CatalogSource.TRAKT).size());
    }
    @Test public void newManualCompletionOverridesReviewedSeasonsButNotANewSeason() throws Exception {
        JSONObject reviewed = record("tt1", "series", "Crime").put("seasons", new JSONArray().put(SeasonViewingTest.season(1, 8, 0, "unfinished")));
        JSONObject choice = record("tt1", "series", "Crime").put("decision", "completed")
                .put("seasons", new JSONArray().put(SeasonViewingTest.season(1, 8, 0, "watched")));
        RecommendationTaste taste = taste(new JSONArray(), new JSONArray().put(choice), new JSONArray().put(reviewed));
        assertTrue(taste.apply(Arrays.asList(new CatalogItem(reviewed)), CatalogSource.RECENT).isEmpty());
        reviewed.getJSONArray("seasons").put(SeasonViewingTest.season(2, 8, 0, ""));
        taste = taste(new JSONArray(), new JSONArray().put(choice), new JSONArray().put(reviewed));
        assertEquals(1, taste.apply(Arrays.asList(new CatalogItem(reviewed)), CatalogSource.RECENT).size());
    }
    @Test public void watchedExclusionsAreSharedButWatchlistAndGoogleRemainIntact() throws Exception {
        RecommendationTaste taste = taste(new JSONArray().put(record("tt1", "series", "Crime").put("completed", true)), new JSONArray(), new JSONArray());
        List<CatalogItem> input = Arrays.asList(new CatalogItem(record("tt90", "movie", "Crime")), new CatalogItem(record("tt1", "series", "Crime")), new CatalogItem(record("tt2", "series", "Comedy")));
        for (int source : new int[]{CatalogSource.TRAKT, CatalogSource.RECENT, CatalogSource.TRENDING}) {
            List<CatalogItem> result = taste.apply(input, source); assertEquals(1, result.size()); assertEquals("tt2", result.get(0).id);
        }
        assertEquals(input, taste.apply(input, CatalogSource.WATCHLIST)); assertEquals(input, taste.apply(input, CatalogSource.GOOGLE));
    }
    @Test public void metadataSignalsUseCastAndDirectorsAndSurviveCacheRoundTrip() throws Exception {
        JSONObject completed = record("tt1", "movie", "Drama").put("completed", true)
                .put("cast", new JSONArray().put("Example Actor")).put("directors", new JSONArray().put("Example Director"));
        RecommendationTaste taste = taste(new JSONArray().put(completed), new JSONArray(), new JSONArray());
        CatalogItem match = new CatalogItem(record("tt2", "movie", "Comedy").put("cast", new JSONArray().put("example actor")));
        assertTrue(taste.score(new CatalogItem(match.json())) > 0);
        CatalogItem director = new CatalogItem(record("tt3", "movie", "Comedy").put("director", "Example Director"));
        assertTrue(taste.score(director) > 0);
    }
    @Test public void duplicateCompletionDoesNotDoubleCountAndProviderMembershipIsPreserved() throws Exception {
        JSONObject completed = record("tt1", "series", "Crime").put("completed", true);
        RecommendationTaste imported = taste(new JSONArray().put(completed), new JSONArray(), new JSONArray());
        RecommendationTaste duplicate = taste(new JSONArray().put(completed), new JSONArray().put(record("tt1", "series", "Crime").put("decision", "completed")), new JSONArray());
        CatalogItem candidate = new CatalogItem(record("tt2", "series", "Crime"));
        assertEquals(imported.score(candidate), duplicate.score(candidate), 0);
        List<CatalogItem> result = duplicate.apply(Arrays.asList(candidate), CatalogSource.TRAKT);
        assertEquals(Arrays.asList(candidate), result);
    }
    @Test public void knownMovieMetadataIsPositiveButAnUnknownMovieIsNot() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray(), new JSONArray(), new JSONArray(),
                new JSONArray().put(record("tt90", "movie", "Crime")).put(record("tt91", "movie", "Comedy")), Collections.singleton("tt90"));
        assertTrue(taste.score(new CatalogItem(record("tt2", "movie", "Crime"))) > 0);
        assertEquals(0, taste.score(new CatalogItem(record("tt3", "movie", "Comedy"))), 0);
    }
}
