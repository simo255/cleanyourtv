package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecommendationFocusTest {
    private CatalogItem item(String id, String type) throws Exception {
        return new CatalogItem(new JSONObject().put("id", id).put("type", type).put("name", id));
    }

    @Test public void evaluationFollowsTheNextTitleEvenIfRankingChanges() throws Exception {
        CatalogItem first = item("tt1", "series"), rated = item("tt2", "series"), next = item("tt3", "series");
        String target = RecommendationFocus.after(Arrays.asList(first, rated, next), RecommendationFocus.key(rated));
        assertEquals(0, RecommendationFocus.position(Arrays.asList(next, first, rated), target, 1));
        assertEquals(1, RecommendationFocus.position(Arrays.asList(first, next), target, 1));
    }

    @Test public void missingNextTitleKeepsTheNearbySlotInsteadOfJumpingToTheStart() throws Exception {
        List<CatalogItem> remaining = Arrays.asList(item("tt1", "series"), item("tt4", "series"), item("tt5", "series"));
        assertEquals(2, RecommendationFocus.position(remaining, "series:tt3", 2));
        assertEquals(2, RecommendationFocus.position(remaining, "series:tt3", 10));
    }

    @Test public void evaluatingLastTitleFallsBackToItsPredecessor() throws Exception {
        List<CatalogItem> items = Arrays.asList(item("tt1", "movie"), item("tt2", "movie"));
        assertEquals("movie:tt1", RecommendationFocus.after(items, "movie:tt2"));
        assertEquals("", RecommendationFocus.after(Collections.singletonList(items.get(0)), "movie:tt1"));
        assertEquals(-1, RecommendationFocus.position(Collections.emptyList(), "movie:tt1", 4));
    }

    @Test public void returningFromAnAppFindsTheSameTitleByIdentityAndType() throws Exception {
        List<CatalogItem> items = Arrays.asList(item("tt1", "movie"), item("tt2", "series"), item("tt1", "series"));
        assertEquals(2, RecommendationFocus.position(items, "series:tt1", 0));
        assertEquals(0, RecommendationFocus.position(items, "movie:tt1", 2));
    }
}
