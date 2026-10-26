package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class DiscoveryTasteTest {
    @Test public void combinedChoiceHidesMoviesAndSeriesAndDoesNotDuplicateImportedEvidence() throws Exception {
        for (String type : new String[]{"movie", "series"}) {
            JSONObject record = record("tt1", type, "Crime");
            JSONArray combined = new JSONArray().put(new JSONObject(record.toString()).put("decision", RecommendationTaste.SEEN_LIKED));
            RecommendationTaste both = taste(new JSONArray(), combined, new JSONArray());
            RecommendationTaste liked = taste(new JSONArray(), new JSONArray()
                    .put(new JSONObject(record.toString()).put("decision", RecommendationTaste.MORE)), new JSONArray());
            CatalogItem same = new CatalogItem(record), another = new CatalogItem(record("tt9", type, "Crime"));
            assertTrue(both.apply(Arrays.asList(same), CatalogSource.WATCHLY).isEmpty());
            assertTrue(both.apply(Arrays.asList(same), CatalogSource.RECENT).isEmpty());
            assertEquals(liked.score(another), both.score(another), 0);
            RecommendationTaste imported = taste(new JSONArray().put(new JSONObject(record.toString()).put("completed", true)), combined, new JSONArray());
            assertEquals(both.score(another), imported.score(another), 0);
        }
    }
    @Test public void combinedSeriesChoiceOverridesUnfinishedLocalExclusionButNotItsLikeWeight() throws Exception {
        JSONObject unfinished = record("tt1", "series", "Crime").put("seasons", new JSONArray()
                .put(SeasonViewingTest.season(1, 8, 3, "unfinished")));
        JSONObject choice = record("tt1", "series", "Crime").put("decision", RecommendationTaste.SEEN_LIKED)
                .put("seasons", new JSONArray().put(SeasonViewingTest.season(1, 8, 8, "watched")));
        RecommendationTaste both = taste(new JSONArray(), new JSONArray().put(choice), new JSONArray().put(unfinished));
        assertTrue(both.learned());
        assertTrue(both.apply(Arrays.asList(new CatalogItem(record("tt1", "series", "Crime"))), CatalogSource.WATCHLY).isEmpty());
    }
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
        assertEquals(1, taste.apply(Arrays.asList(new CatalogItem(viewed)), CatalogSource.WATCHLY).size());
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
        for (int source : new int[]{CatalogSource.WATCHLY, CatalogSource.RECENT, CatalogSource.TRENDING}) {
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
        List<CatalogItem> result = duplicate.apply(Arrays.asList(candidate), CatalogSource.WATCHLY);
        assertEquals(Arrays.asList(candidate), result);
    }
    @Test public void knownMovieMetadataIsPositiveButAnUnknownMovieIsNot() throws Exception {
        RecommendationTaste taste = new RecommendationTaste(new JSONArray(), new JSONArray(), new JSONArray(),
                new JSONArray().put(record("tt90", "movie", "Crime")).put(record("tt91", "movie", "Comedy")), Collections.singleton("tt90"));
        assertTrue(taste.score(new CatalogItem(record("tt2", "movie", "Crime"))) > 0);
        assertEquals(0, taste.score(new CatalogItem(record("tt3", "movie", "Comedy"))), 0);
    }
    @Test public void diversityOnlyPromotesTitlesWithAPositiveContentScore() throws Exception {
        RecommendationTaste profile = taste(new JSONArray(), new JSONArray().put(record("tt99", "movie", "Crime").put("decision", "more")), new JSONArray());
        CatalogItem a = new CatalogItem(record("tt1", "movie", "Crime")), b = new CatalogItem(record("tt2", "movie", "Crime")),
                c = new CatalogItem(record("tt3", "movie", "Crime")), neutral = new CatalogItem(record("tt4", "movie", "Comedy"));
        assertTrue(profile.score(a) > 0);
        assertEquals(0, profile.score(neutral), 0);
        assertEquals(Arrays.asList(a, b, c, neutral), profile.apply(Arrays.asList(a, b, c, neutral), CatalogSource.WATCHLY));
    }
    @Test public void sparseEvidenceRespectsProviderButEstablishedTasteCanPromoteADistantMatch() throws Exception {
        JSONArray sparse = new JSONArray().put(record("tt90", "movie", "Crime").put("completed", true));
        JSONArray established = new JSONArray();
        for (int i = 90; i < 120; i++) established.put(record("tt" + i, "movie", "Crime").put("completed", true));
        List<CatalogItem> candidates = new java.util.ArrayList<>();
        for (int i = 1; i < 20; i++) candidates.add(new CatalogItem(record("tt" + i, "movie", "Comedy")));
        CatalogItem match = new CatalogItem(record("tt20", "movie", "Crime")); candidates.add(match);
        assertSame(candidates.get(0), taste(sparse, new JSONArray(), new JSONArray()).apply(candidates, CatalogSource.WATCHLY).get(0));
        assertSame(match, taste(established, new JSONArray(), new JSONArray()).apply(candidates, CatalogSource.WATCHLY).get(0));
    }
    @Test public void metadataFreeHistoryAndOtherMediaDoNotInflateConfidence() throws Exception {
        JSONArray sparse = new JSONArray().put(record("tt90", "movie", "Crime").put("completed", true));
        JSONArray noisy = new JSONArray(sparse.toString());
        for (int i = 100; i < 150; i++) {
            noisy.put(record("tt" + i, "movie", "").put("completed", true));
            noisy.put(record("tt" + i, "series", "Crime").put("completed", true));
        }
        List<CatalogItem> items = Arrays.asList(new CatalogItem(record("tt1", "movie", "Comedy")),
                new CatalogItem(record("tt2", "movie", "Comedy")), new CatalogItem(record("tt3", "movie", "Crime")));
        assertEquals(taste(sparse, new JSONArray(), new JSONArray()).apply(items, CatalogSource.WATCHLY),
                taste(noisy, new JSONArray(), new JSONArray()).apply(items, CatalogSource.WATCHLY));
    }
    @Test public void firstScreenDiversifiesFullGenreSetsEvenWhenLeadingGenreMatches() throws Exception {
        JSONArray choices = new JSONArray().put(record("tt90", "movie", "Crime").put("decision", "more"))
                .put(record("tt91", "movie", "Comedy").put("decision", "more"));
        RecommendationTaste profile = taste(new JSONArray(), choices, new JSONArray());
        CatalogItem a = new CatalogItem(record("tt1", "movie", "Drama").put("genres", new JSONArray().put("Drama").put("Crime")));
        CatalogItem b = new CatalogItem(record("tt2", "movie", "Drama").put("genres", new JSONArray().put("Drama").put("Crime")));
        CatalogItem c = new CatalogItem(record("tt3", "movie", "Drama").put("genres", new JSONArray().put("Drama").put("Comedy")));
        List<CatalogItem> input = Arrays.asList(a, b, c);
        assertEquals(Arrays.asList(a, c, b), profile.apply(input, CatalogSource.WATCHLY));
        assertEquals(Arrays.asList(a, b, c), input);
        assertEquals(input, profile.apply(input, CatalogSource.TRENDING));
    }
    @Test public void firstScreenUsesCastAndDirectorVarietyWithoutChangingMembership() throws Exception {
        RecommendationTaste profile = taste(new JSONArray(), new JSONArray()
                .put(record("tt90", "movie", "Crime").put("decision", "more")), new JSONArray());
        CatalogItem a = new CatalogItem(record("tt1", "movie", "Crime").put("cast", new JSONArray().put("Actor A")).put("director", "Director A"));
        CatalogItem b = new CatalogItem(record("tt2", "movie", "Crime").put("cast", new JSONArray().put("Actor A")).put("director", "Director A"));
        CatalogItem c = new CatalogItem(record("tt3", "movie", "Crime").put("cast", new JSONArray().put("Actor B")).put("director", "Director B"));
        assertEquals(Arrays.asList(a, c, b), profile.apply(Arrays.asList(a, b, c), CatalogSource.WATCHLY));
    }
    @Test public void largeCatalogIsDeterministicAndDoesNotLoseOrDuplicateTitles() throws Exception {
        RecommendationTaste profile = taste(new JSONArray(), new JSONArray()
                .put(record("tt900", "movie", "Crime").put("decision", "more")), new JSONArray());
        List<CatalogItem> input = new java.util.ArrayList<>();
        for (int i = 1000; i < 1300; i++) input.add(new CatalogItem(record("tt" + i, "movie", i % 4 == 0 ? "Comedy" : "Crime")));
        List<CatalogItem> output = profile.apply(input, CatalogSource.WATCHLY);
        assertEquals(300, output.size()); assertEquals(new java.util.HashSet<>(input), new java.util.HashSet<>(output));
        assertEquals(output, profile.apply(input, CatalogSource.WATCHLY));
    }
}
