package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.*;

public class FeedbackSyncPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-25T18:00:00Z");
    private JSONObject choice(String type) throws Exception {
        return new JSONObject().put("id", "tt12345").put("type", type).put("name", "Example");
    }
    private JSONObject episode(int season, int number, String date) throws Exception {
        return new JSONObject().put("id", "tt12345:" + season + ":" + number)
                .put("season", season).put("episode", number).put("released", date);
    }
    @Test public void completionPreservesResumeDatesAndRewatchCount() throws Exception {
        JSONObject before = new JSONObject().put("_id", "tt12345").put("type", "movie").put("removed", false)
                .put("state", new JSONObject().put("timeOffset", 700).put("duration", 1200).put("timesWatched", 4)
                    .put("video_id", "tt12345").put("lastWatched", "2026-01-01T00:00:00Z"));
        JSONObject after = FeedbackSyncPolicy.complete(before, choice("movie"), null, NOW);
        JSONObject state = after.getJSONObject("state");
        assertEquals(700, state.getInt("timeOffset")); assertEquals(4, state.getInt("timesWatched"));
        assertEquals("2026-01-01T00:00:00Z", state.getString("lastWatched"));
        assertFalse(after.getBoolean("removed")); assertEquals(1, state.getInt("flaggedWatched"));
        assertFalse(before.getJSONObject("state").has("flaggedWatched"));
    }
    @Test public void newWatchedTitleDoesNotAddToSavedLibraryOrInventPlayback() throws Exception {
        JSONObject after = FeedbackSyncPolicy.complete(null, choice("movie"), null, NOW);
        assertTrue(after.getBoolean("removed")); assertTrue(after.getBoolean("temp"));
        assertFalse(after.getJSONObject("state").has("lastWatched"));
        assertFalse(after.getJSONObject("state").has("timeOffset"));
    }
    @Test public void onlyAiredRegularEpisodesAreMarked() throws Exception {
        JSONArray videos = new JSONArray().put(episode(1, 2, "2027-01-01T00:00:00Z"))
                .put(episode(0, 1, "2020-01-01T00:00:00Z"))
                .put(episode(1, 1, "2026-10-24T00:00:00Z"))
                .put(episode(1, 3, "")).put(episode(1, 4, "2026-10-25T20:00:00Z"));
        JSONObject after = FeedbackSyncPolicy.complete(null, choice("series"), videos, NOW);
        assertEquals(new HashSet<>(Arrays.asList("tt12345:1:1")), SeasonViewing.watchedIds(after.getJSONObject("state").getString("watched"),
                Arrays.asList("tt12345:0:1", "tt12345:1:1", "tt12345:1:2", "tt12345:1:3", "tt12345:1:4")));
    }
    @Test public void unfinishedSeasonIsNeverPromotedByBackfill() throws Exception {
        JSONObject choice = choice("series").put("seasons", new JSONArray()
                .put(new JSONObject().put("season", 1).put("status", "watched"))
                .put(new JSONObject().put("season", 2).put("status", "unfinished")));
        JSONArray videos = new JSONArray().put(episode(1, 1, "2020-01-01T00:00:00Z"))
                .put(episode(2, 1, "2021-01-01T00:00:00Z"));
        JSONObject state = FeedbackSyncPolicy.complete(null, choice, videos, NOW).getJSONObject("state");
        assertFalse(state.has("flaggedWatched"));
        assertEquals(new HashSet<>(Arrays.asList("tt12345:1:1")), SeasonViewing.watchedIds(state.getString("watched"), Arrays.asList("tt12345:1:1", "tt12345:2:1")));
    }
    @Test(expected = Exception.class) public void corruptHistoryIsNotOverwritten() throws Exception {
        JSONObject before = new JSONObject().put("_id", "tt12345").put("type", "series")
                .put("state", new JSONObject().put("watched", "corrupt"));
        FeedbackSyncPolicy.complete(before, choice("series"), new JSONArray().put(episode(1, 1, "2020-01-01T00:00:00Z")), NOW);
    }
    @Test public void hidingAndClearingHaveNoRemoteRating() {
        assertEquals("", FeedbackSyncPolicy.action("hide")); assertEquals("", FeedbackSyncPolicy.action(""));
        assertEquals("liked", FeedbackSyncPolicy.action("more")); assertEquals("watched", FeedbackSyncPolicy.action("completed"));
    }
    @Test public void missingMetadataFallbackRequiresBothExplicitWatchedFields() throws Exception {
        assertFalse(FeedbackSyncPolicy.alreadyWatched(null));
        JSONObject row = new JSONObject().put("state", new JSONObject().put("timesWatched", 1));
        assertFalse(FeedbackSyncPolicy.alreadyWatched(row));
        row.getJSONObject("state").put("flaggedWatched", 1);
        assertTrue(FeedbackSyncPolicy.alreadyWatched(row));
        row.getJSONObject("state").put("timesWatched", 0).put("timeOffset", 9999);
        assertFalse(FeedbackSyncPolicy.alreadyWatched(row));
    }
    @Test(expected = Exception.class) public void unknownEpisodeMetadataDoesNotMarkAWholeSeries() throws Exception {
        FeedbackSyncPolicy.complete(null, choice("series"), new JSONArray().put(episode(1, 1, "")), NOW);
    }
}
