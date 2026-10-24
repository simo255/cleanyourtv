package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import static org.junit.Assert.*;

public class StremioViewingTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private JSONObject row(String id, String type, JSONObject state) throws Exception {
        return new JSONObject().put("_id", id).put("name", "Example").put("type", type).put("state", state);
    }
    private JSONObject state(int plays, int flagged) throws Exception {
        return new JSONObject().put("timesWatched", plays).put("flaggedWatched", flagged);
    }
    private JSONObject update(JSONObject previous, JSONObject... rows) throws Exception {
        return StremioViewing.update(previous, new JSONArray(Arrays.asList(rows)), Collections.emptyMap(), TODAY);
    }
    @Test public void completedMoviesAreDetectedEvenOutsideTheResumeList() throws Exception {
        JSONObject snapshot = update(new JSONObject(), row("tt1", "movie", state(1, 0)).put("removed", true),
                row("tt2", "movie", new JSONObject().put("flaggedWatched", 1)), row("tt3", "movie", state(0, 0).put("duration", 100).put("timeOffset", 95)));
        assertEquals(2, StremioViewing.movieIds(snapshot).size());
        assertTrue(StremioViewing.movieIds(snapshot).contains("tt1")); assertTrue(StremioViewing.movieIds(snapshot).contains("tt2"));
        assertFalse(StremioViewing.movieIds(snapshot).contains("tt3"));
    }
    @Test public void persistedHistorySurvivesMissingRowsButAnExplicitUnwatchCanRemoveAutomaticEvidence() throws Exception {
        JSONObject snapshot = update(new JSONObject(), row("tt1", "movie", state(1, 0)));
        snapshot = update(new JSONObject(snapshot.toString()));
        assertTrue(StremioViewing.movieIds(snapshot).contains("tt1"));
        snapshot = update(snapshot, row("tt1", "movie", new JSONObject()));
        assertTrue(StremioViewing.movieIds(snapshot).contains("tt1"));
        snapshot = update(snapshot, row("tt1", "movie", state(0, 1)));
        assertTrue(StremioViewing.movieIds(snapshot).isEmpty());
    }
    @Test public void privateAccountFieldsNeverEnterTheViewingSnapshot() throws Exception {
        JSONObject raw = row("tt1", "movie", state(1, 0).put("authKey", "secret"))
                .put("authKey", "secret").put("email", "private@example.invalid").put("genres", new JSONArray().put("Crime"));
        JSONObject snapshot = update(new JSONObject(), raw);
        assertFalse(snapshot.toString().contains("secret")); assertFalse(snapshot.toString().contains("email"));
        assertEquals("Crime", snapshot.getJSONArray("movies").getJSONObject(0).getJSONArray("genres").getString(0));
    }
    @Test public void liveCompletionFiltersEveryRecommendationFeedWithoutNewCatalogData() throws Exception {
        JSONObject snapshot = update(new JSONObject(), row("tt1", "movie", state(1, 0)));
        RecommendationTaste taste = new RecommendationTaste(new JSONArray(), new JSONArray(), snapshot.getJSONArray("series"),
                snapshot.getJSONArray("movies"), StremioViewing.movieIds(snapshot));
        List<CatalogItem> cached = Arrays.asList(new CatalogItem(new JSONObject().put("id", "tt1").put("type", "movie")),
                new CatalogItem(new JSONObject().put("id", "tt2").put("type", "movie")));
        for (int source : new int[]{CatalogSource.TRAKT, CatalogSource.RECENT, CatalogSource.TRENDING}) {
            List<CatalogItem> result = taste.apply(cached, source); assertEquals(1, result.size()); assertEquals("tt2", result.get(0).id);
        }
        assertEquals(2, taste.apply(cached, CatalogSource.WATCHLIST).size());
    }
    private JSONArray episodes() throws Exception {
        JSONArray result = new JSONArray();
        for (int ep = 1; ep <= 6; ep++) result.put(new JSONObject().put("id", "tt1:1:" + ep).put("season", 1)
                .put("episode", ep).put("released", "2026-01-01T00:00:00Z"));
        return result;
    }
    private String bitmap(int bits) {
        Deflater compressor = new Deflater(); compressor.setInput(new byte[]{(byte) bits}); compressor.finish();
        byte[] buffer = new byte[100]; int length = compressor.deflate(buffer); compressor.end();
        return "tt1:1:1:1:" + Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, length));
    }
    @Test public void seriesEpisodeEvidencePersistsAndMetadataFailuresDoNotEraseIt() throws Exception {
        JSONObject raw = row("tt1", "series", new JSONObject().put("watched", bitmap(63)));
        Map<String, JSONArray> metadata = Collections.singletonMap("tt1", episodes());
        JSONObject snapshot = StremioViewing.update(new JSONObject(), new JSONArray().put(raw), metadata, TODAY);
        assertTrue(SeasonViewing.evaluate(snapshot.getJSONArray("series").getJSONObject(0)).complete);
        snapshot = StremioViewing.update(new JSONObject(snapshot.toString()), new JSONArray().put(raw), Collections.emptyMap(), TODAY);
        assertTrue(SeasonViewing.evaluate(snapshot.getJSONArray("series").getJSONObject(0)).complete);
        raw.getJSONObject("state").put("watched", "corrupt");
        snapshot = StremioViewing.update(snapshot, new JSONArray().put(raw), metadata, TODAY);
        assertTrue(SeasonViewing.evaluate(snapshot.getJSONArray("series").getJSONObject(0)).complete);
    }
    @Test public void anotherAiredSeasonPreventsWholeSeriesCompletion() throws Exception {
        JSONObject raw = row("tt1", "series", new JSONObject().put("watched", bitmap(63)));
        JSONArray episodes = episodes().put(new JSONObject().put("id", "tt1:2:1").put("season", 2).put("episode", 1).put("released", "2026-10-23"));
        JSONObject snapshot = StremioViewing.update(new JSONObject(), new JSONArray().put(raw), Collections.singletonMap("tt1", episodes), TODAY);
        SeasonViewing.Result result = SeasonViewing.evaluate(snapshot.getJSONArray("series").getJSONObject(0));
        assertTrue(result.positive()); assertFalse(result.complete); assertFalse(result.watched.contains(2));
    }
    @Test public void changedShowsGetMetadataPriorityWithoutIncreasingTheEightSlotBudget() throws Exception {
        List<JSONObject> library = new ArrayList<>();
        for (int i = 1; i <= 20; i++) library.add(row("tt" + i, "series", new JSONObject().put("lastWatched", "2026-10-01T00:00:00Z")));
        JSONObject fresh = library.get(19).put("_mtime", "2026-10-24T00:00:00Z");
        fresh.getJSONObject("state").put("video_id", "tt20:1:6").put("timeOffset", 900).put("duration", 1000);
        List<String> lookups = StremioViewing.lookups(library, new JSONObject(), 1_793_000_000_000L);
        assertEquals(8, lookups.size()); assertEquals("tt20", lookups.get(0));
        assertEquals(8, lookups.stream().distinct().count());
    }
    @Test public void titleIdsAndTypesCannotBecomeAccountState() throws Exception {
        JSONObject snapshot = update(new JSONObject(), row("bad", "movie", state(1, 0)), row("tt2", "channel", state(1, 0)));
        assertTrue(StremioViewing.movieIds(snapshot).isEmpty());
        assertEquals(0, snapshot.getJSONArray("series").length());
    }
}
