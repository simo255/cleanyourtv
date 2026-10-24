package com.gathin.tvhome;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class GoogleTvSnapshotTest {
    private JSONObject item(String id, String type) throws Exception {
        return new JSONObject().put("id", id).put("type", type).put("name", id);
    }
    private JSONObject snapshot() throws Exception {
        return new JSONObject().put("schema", 1).put("provider", "google-tv-ui").put("account", "account-a")
                .put("captured_at", 12345).put("movies", new JSONArray().put(item("tt1", "movie"))).put("shows", new JSONArray());
    }
    @Test public void importIsBoundToAccountAndRequiresExplicitSourceAndCaptureTime() throws Exception {
        GoogleTvSnapshot parsed = new GoogleTvSnapshot(snapshot(), "account-a");
        assertEquals(12345, parsed.capturedAt); assertEquals("tt1", parsed.items(false).get(0).id);
        for (JSONObject bad : new JSONObject[]{snapshot().put("provider", "cinemeta"), snapshot().put("captured_at", 0), snapshot().put("schema", 2)}) {
            try { new GoogleTvSnapshot(bad, "account-a"); fail(); } catch (Exception expected) { }
        }
        try { new GoogleTvSnapshot(snapshot(), "account-b"); fail(); } catch (Exception expected) { }
    }
    @Test public void googleKeepsItsOwnOrderAndDoesNotAcceptTraktExclusions() throws Exception {
        JSONObject data = snapshot().put("movies", new JSONArray().put(item("tt3", "movie")).put(item("tt1", "movie")))
                .put("watched_movie_ids", new JSONArray().put("tt1"));
        List<CatalogItem> result = new GoogleTvSnapshot(data, "account-a").items(false);
        assertEquals(2, result.size());
        assertEquals("tt3", result.get(0).id);
        assertEquals("tt1", result.get(1).id);
    }
    @Test public void moviesAndShowsStayInTheirOwnCategories() throws Exception {
        GoogleTvSnapshot parsed = new GoogleTvSnapshot(snapshot().put("shows", new JSONArray().put(item("tt2", "series"))), "account-a");
        assertEquals("tt1", parsed.items(false).get(0).id);
        assertEquals("tt2", parsed.items(true).get(0).id);
    }
    @Test public void renderedListsCannotMutateTheStoredSnapshot() throws Exception {
        GoogleTvSnapshot parsed = new GoogleTvSnapshot(snapshot(), "account-a");
        parsed.items(false).clear();
        assertEquals(1, parsed.items(false).size());
        assertTrue(parsed.items(true).isEmpty());
    }
    @Test public void emptyImportIsRejectedWithoutFallingBackToTrakt() throws Exception {
        try { new GoogleTvSnapshot(snapshot().put("movies", new JSONArray()), "account-a"); fail(); }
        catch (Exception expected) { }
    }
}
