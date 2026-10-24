package com.gathin.tvhome;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class GoogleTvSnapshotTest {
    private CatalogItem movie(String id) throws Exception {
        return new CatalogItem(new JSONObject().put("id", id).put("type", "movie").put("name", id));
    }
    private JSONObject snapshot() throws Exception {
        return new JSONObject().put("schema", 1).put("provider", "google-tv-ui").put("account", "account-a")
                .put("captured_at", 12345).put("movies", new JSONArray().put(movie("tt1").json())).put("shows", new JSONArray());
    }
    @Test public void importIsBoundToAccountAndRequiresExplicitSourceAndCaptureTime() throws Exception {
        GoogleTvSnapshot parsed = new GoogleTvSnapshot(snapshot(), "account-a");
        assertEquals(12345, parsed.capturedAt); assertTrue(parsed.contains(movie("tt1")));
        for (JSONObject bad : new JSONObject[]{snapshot().put("provider", "cinemeta"), snapshot().put("captured_at", 0), snapshot().put("schema", 2)}) {
            try { new GoogleTvSnapshot(bad, "account-a"); fail(); } catch (Exception expected) { }
        }
        try { new GoogleTvSnapshot(snapshot(), "account-b"); fail(); } catch (Exception expected) { }
    }
    @Test public void alternateProvidersWhileKeepingEachProvidersOrder() throws Exception {
        CatalogItem a=movie("tt1"), b=movie("tt2"), c=movie("tt3"), d=movie("tt4");
        assertEquals(Arrays.asList(a,c,b,d), GoogleTvSnapshot.mix(Arrays.asList(a,b),Arrays.asList(c,d),Collections.emptySet()));
    }
    @Test public void overlappingIdsKeepGoogleCopyAtItsOriginalRankWithoutDuplicates() throws Exception {
        CatalogItem a=movie("tt1"), googleCopy=movie("tt2"), traktCopy=movie("tt2"), c=movie("tt3");
        List<CatalogItem> result=GoogleTvSnapshot.mix(Arrays.asList(a,googleCopy),Arrays.asList(traktCopy,c),Collections.emptySet());
        assertEquals(Arrays.asList(a,c,googleCopy),result);
        assertSame(googleCopy,result.get(2));
    }
    @Test public void knownWatchedExclusionsApplyToBothSources() throws Exception {
        CatalogItem a=movie("tt1"), b=movie("tt2");
        assertEquals(Collections.singletonList(b), GoogleTvSnapshot.mix(Collections.singletonList(a),Arrays.asList(a,b),Collections.singleton("tt1")));
    }
    @Test public void missingGoogleDataPreservesTraktOrderAndEmptyInvalidImportIsRejected() throws Exception {
        CatalogItem a=movie("tt1"), b=movie("tt2");
        assertEquals(Arrays.asList(a,b), GoogleTvSnapshot.mix(Collections.emptyList(),Arrays.asList(a,b),Collections.emptySet()));
        try { new GoogleTvSnapshot(snapshot().put("movies",new JSONArray()),"account-a");fail(); } catch (Exception expected) { }
    }
}
