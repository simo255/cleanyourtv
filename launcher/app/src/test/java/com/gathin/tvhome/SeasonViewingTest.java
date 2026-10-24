package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import java.util.Base64;
import java.util.zip.Deflater;
import static org.junit.Assert.*;

public class SeasonViewingTest {
    static JSONObject season(int number, int total, int watched, String status) throws Exception {
        return new JSONObject().put("season", number).put("total", total).put("aired", total)
                .put("watched", watched).put("future", 0).put("undated", 0).put("status", status);
    }
    static JSONObject title(JSONObject... seasons) throws Exception {
        return new JSONObject().put("id", "tt1").put("type", "series").put("seasons", new JSONArray(seasons));
    }
    @Test public void halfASeasonInfersOnlyEarlierSeasons() throws Exception {
        SeasonViewing.Result result = SeasonViewing.evaluate(title(season(1, 8, 0, ""), season(2, 8, 4, ""), season(3, 8, 0, "")));
        assertTrue(result.watched.contains(1)); assertTrue(result.watched.contains(2));
        assertFalse(result.watched.contains(3)); assertFalse(result.complete);
    }
    @Test public void explicitUnfinishedAndUnwatchedOverrideCountsAndEarlierSeasonInference() throws Exception {
        SeasonViewing.Result result = SeasonViewing.evaluate(title(season(1, 8, 8, "unfinished"), season(2, 8, 8, "unwatched"), season(3, 8, 4, "")));
        assertEquals(1, result.watched.size()); assertTrue(result.watched.contains(3)); assertFalse(result.complete);
        assertFalse(SeasonViewing.evaluate(title(season(1, 8, 8, "unfinished"))).positive());
    }
    @Test public void twoEpisodeToleranceIsPerTitleNotPerSeason() throws Exception {
        assertTrue(SeasonViewing.evaluate(title(season(1, 3, 1, ""))).complete);
        assertFalse(SeasonViewing.evaluate(title(season(1, 3, 1, ""), season(2, 3, 1, ""))).positive());
        SeasonViewing.Result later = SeasonViewing.evaluate(title(season(1, 8, 8, ""), season(2, 2, 0, "")));
        assertFalse(later.watched.contains(2)); assertFalse(later.complete);
    }
    @Test public void futureSeasonDoesNotEraseCurrentCompletionOrBecomeWatched() throws Exception {
        JSONObject future = season(2, 8, 0, "unwatched").put("aired", 0).put("future", 8);
        SeasonViewing.Result result = SeasonViewing.evaluate(title(season(1, 8, 8, "watched"), future));
        assertTrue(result.complete); assertFalse(result.watched.contains(2));
        future.put("aired", 1).put("future", 7);
        assertFalse(SeasonViewing.evaluate(title(season(1, 8, 8, "watched"), future)).complete);
    }
    @Test public void unknownAndInvalidCountsCannotProduceCompletion() throws Exception {
        assertFalse(SeasonViewing.evaluate(title(season(1, 8, 4, "").put("aired", 7).put("undated", 1))).complete);
        assertFalse(SeasonViewing.evaluate(title(season(1, 8, 9, ""))).positive());
        assertFalse(SeasonViewing.evaluate(title(season(1, 8, 4, ""), season(1, 8, 4, ""))).positive());
        JSONObject confirmed = season(1, 8, 0, "watched").put("aired", 0).put("undated", 8);
        assertTrue(SeasonViewing.evaluate(title(confirmed)).complete);
    }
    @Test public void mergeRefreshesCountsButPreservesReviewAndNewSeasons() throws Exception {
        JSONObject merged = SeasonViewing.merge(title(season(1, 8, 0, "unfinished")), title(season(1, 8, 8, ""), season(2, 8, 4, "")));
        SeasonViewing.Result result = SeasonViewing.evaluate(merged);
        assertFalse(result.watched.contains(1)); assertTrue(result.watched.contains(2)); assertFalse(result.complete);
    }
    @Test public void newWatchedEvidenceSupersedesAnOlderUnfinishedSnapshotButUnchangedCountsDoNot() throws Exception {
        JSONObject reviewed = title(season(1, 8, 2, "unfinished"));
        assertFalse(SeasonViewing.evaluate(SeasonViewing.mergeProgress(reviewed, title(season(1, 8, 2, "")))).positive());
        assertTrue(SeasonViewing.evaluate(SeasonViewing.mergeProgress(reviewed, title(season(1, 8, 8, "")))).complete);
        JSONObject watched = title(season(1, 8, 2, "watched"));
        assertTrue(SeasonViewing.evaluate(SeasonViewing.mergeProgress(watched, title(season(1, 8, 0, "")))).complete);
        reviewed.put("reviewed_at", 1000);
        assertFalse(SeasonViewing.evaluate(SeasonViewing.mergeProgress(reviewed, title(season(1, 8, 8, "")).put("updated_at", 999))).complete);
        assertTrue(SeasonViewing.evaluate(SeasonViewing.mergeProgress(reviewed, title(season(1, 8, 8, "")).put("updated_at", 1001))).complete);
    }
    private JSONArray videos() throws Exception {
        JSONArray values = new JSONArray();
        for (int i = 1; i <= 8; i++) values.put(new JSONObject().put("id", "tt1:1:" + i).put("season", 1).put("episode", i).put("released", "2026-01-01T00:00:00Z"));
        return values;
    }
    private JSONObject row(JSONObject state) throws Exception { return new JSONObject().put("_id", "tt1").put("type", "series").put("state", state); }
    @Test public void onlyExactPlaybackAt75PercentCountsAndSentinelIsIgnored() throws Exception {
        JSONObject state = new JSONObject().put("duration", 1000).put("timeOffset", 750).put("video_id", "tt1:1:4");
        JSONObject summary = SeasonViewing.fromLibrary(row(state), videos(), LocalDate.of(2026, 10, 24));
        assertEquals(1, summary.getJSONArray("seasons").getJSONObject(0).getInt("watched"));
        state.put("timeOffset", 749);
        assertEquals(0, SeasonViewing.fromLibrary(row(state), videos(), LocalDate.of(2026, 10, 24)).getJSONArray("seasons").getJSONObject(0).getInt("watched"));
        state.put("timeOffset", 1).put("duration", 1);
        assertEquals(0, SeasonViewing.fromLibrary(row(state), videos(), LocalDate.of(2026, 10, 24)).getJSONArray("seasons").getJSONObject(0).getInt("watched"));
    }
    @Test public void bitmapMustMapEveryBitAndCorruptionStaysNeutral() throws Exception {
        Deflater compressor = new Deflater(); compressor.setInput(new byte[]{15}); compressor.finish();
        byte[] bytes = new byte[100]; int size = compressor.deflate(bytes); compressor.end();
        String packed = Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(bytes, size));
        JSONObject state = new JSONObject().put("watched", "tt1:1:4:4:" + packed);
        JSONObject summary = SeasonViewing.fromLibrary(row(state), videos(), LocalDate.of(2026, 10, 24));
        assertNotNull(summary); assertTrue(SeasonViewing.evaluate(summary).complete);
        state.put("watched", "tt1:1:4:100:" + packed);
        assertNull(SeasonViewing.fromLibrary(row(state), videos(), LocalDate.of(2026, 10, 24)));
        state.put("watched", "corrupt");
        assertNull(SeasonViewing.fromLibrary(row(state), videos(), LocalDate.of(2026, 10, 24)));
    }
}
