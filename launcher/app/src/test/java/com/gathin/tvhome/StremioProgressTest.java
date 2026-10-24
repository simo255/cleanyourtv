package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class StremioProgressTest {
    private static final long NOW = StremioProgress.time("2026-10-24T12:00:00Z");
    private JSONObject row(String id, String type, long position, long duration) throws Exception {
        return new JSONObject().put("_id", id).put("type", type).put("name", "Example")
                .put("state", new JSONObject().put("video_id", type.equals("series") ? id + ":1:1" : id)
                        .put("timeOffset", position).put("duration", duration).put("lastWatched", "2026-10-23T12:00:00Z"));
    }
    private JSONArray episodes(String secondRelease) throws Exception {
        return new JSONArray().put(new JSONObject().put("id", "tt123:1:2").put("season", 1).put("episode", 2).put("released", secondRelease))
                .put(new JSONObject().put("id", "tt123:1:1").put("season", 1).put("episode", 1).put("released", "2026-10-20T12:00:00Z"));
    }
    private List<StremioProgress.Item> build(JSONObject row, JSONArray episodes, boolean upcoming) throws Exception {
        return StremioProgress.build(StremioProgress.library(new JSONArray().put(row)), Map.of("tt123", episodes), upcoming, NOW);
    }
    @Test public void partialPlaybackKeepsTheExactEpisodeEvenDuringCredits() throws Exception {
        JSONObject series = row("tt123", "series", 95_000, 100_000);
        StremioProgress.Item item = build(series, episodes("2026-10-22T12:00:00Z"), false).get(0);
        assertEquals("tt123:1:1", item.video); assertEquals(StremioProgress.PROGRESS, item.kind);
        assertEquals(95, item.percent()); assertEquals(1, item.minutesLeft()); assertEquals("S1 · E1", item.episode());
        assertEquals(95_000, series.getJSONObject("state").getLong("timeOffset"));
    }
    @Test public void stremiosOneMillisecondPointerDoesNotSkipAnotherEpisode() throws Exception {
        StremioProgress.Item item = build(row("tt123", "series", 1, 0), episodes("2026-10-22T12:00:00Z"), false).get(0);
        assertEquals("tt123:1:1", item.video); assertEquals(StremioProgress.NEXT, item.kind); assertEquals(-1, item.percent());
    }
    @Test public void aKnownFutureEpisodeIsNotOfferedAsPlayable() throws Exception {
        JSONObject row = row("tt123", "series", 1, 0); row.getJSONObject("state").put("video_id", "tt123:1:2");
        assertTrue(build(row, episodes("2026-10-25T12:00:00Z"), false).isEmpty());
        assertEquals(StremioProgress.UPCOMING, build(row, episodes("2026-10-25T12:00:00Z"), true).get(0).kind);
    }
    @Test public void aNewEpisodeAppearsOnlyAfterItsRelease() throws Exception {
        JSONObject row = row("tt123", "series", 0, 0);
        assertTrue(build(row, episodes("2026-10-25T12:00:00Z"), false).isEmpty());
        StremioProgress.Item next = build(row, episodes("2026-10-24T01:00:00Z"), false).get(0);
        assertEquals(StremioProgress.NEW, next.kind); assertEquals("tt123:1:2", next.video);
        assertTrue(build(row, episodes(""), false).isEmpty());
    }
    @Test public void oldTitlesStayInResumeAndFinishedMoviesAreNotResumed() throws Exception {
        JSONObject row = row("tt123", "movie", 30_000, 100_000).put("releaseInfo", "1999");
        assertEquals(1, build(row, new JSONArray(), false).size());
        row.getJSONObject("state").put("timeOffset", 100_000);
        assertTrue(build(row, new JSONArray(), false).isEmpty());
    }
    @Test public void removedLibraryItemsAreExcludedExceptTemporaryPlayback() throws Exception {
        JSONObject row = row("tt123", "series", 100, 0).put("removed", true);
        assertTrue(StremioProgress.library(new JSONArray().put(row)).isEmpty());
        row.put("temp", true); assertEquals(1, StremioProgress.library(new JSONArray().put(row)).size());
    }
    @Test public void malformedIdsAndDuplicateLibraryRowsCannotEnterTheShelf() throws Exception {
        JSONObject valid = row("kitsu:123", "series", 100, 0);
        JSONArray rows = new JSONArray().put(valid).put(valid).put(row("../escape", "movie", 100, 0))
                .put(row("tt123", "other", 100, 0)).put(new JSONObject());
        assertEquals(1, StremioProgress.library(rows).size());
        assertEquals("kitsu:123", StremioProgress.library(rows).get(0).getString("_id"));
    }
    @Test public void missingMetadataPreservesPlaybackAndDoesNotInventAnUpcomingEpisode() throws Exception {
        JSONObject row = row("tt123", "series", 10_000, 100_000);
        assertEquals(1, build(row, new JSONArray(), false).size());
        assertTrue(build(row, new JSONArray(), true).isEmpty());
    }
    @Test public void episodeLookupsAreBoundedAndDoNotFetchUnrelatedOrStaleTemporaryShows() throws Exception {
        JSONArray rows = new JSONArray();
        for (int i = 0; i < 50; i++) rows.put(row("tt" + (100 + i), "series", 100, 0));
        assertEquals(8, StremioProgress.lookups(StremioProgress.library(rows), NOW).size());
        JSONObject old = row("tt123", "series", 0, 0).put("temp", true);
        old.getJSONObject("state").put("lastWatched", "2025-01-01T00:00:00Z");
        assertTrue(StremioProgress.lookups(StremioProgress.library(new JSONArray().put(old)), NOW).isEmpty());
        assertTrue(build(old, episodes("2026-10-25T00:00:00Z"), true).isEmpty());
    }
    @Test public void cacheRoundTripKeepsProgressAndExpiresPastUpcomingEpisodes() throws Exception {
        List<StremioProgress.Item> live = build(row("tt123", "series", 50_000, 100_000), new JSONArray(), false);
        assertEquals(50, StremioProgress.cached(StremioProgress.json(live), false, NOW).get(0).percent());
        List<StremioProgress.Item> future = build(row("tt123", "series", 0, 0), episodes("2026-10-25T00:00:00Z"), true);
        assertTrue(StremioProgress.cached(StremioProgress.json(future), true, NOW + 2 * 86_400_000L).isEmpty());
    }
    @Test public void officialLoginLinksRejectLookalikesRedirectTargetsAndUserInfo() {
        assertTrue(StremioApi.officialLink("https://link.stremio.com/ABCD"));
        for (String value : new String[]{"http://link.stremio.com/A", "https://link.stremio.com.evil.example/A",
                "https://user@link.stremio.com/A", "https://link.stremio.com:444/A", "https://example.com/qr"})
            assertFalse(value, StremioApi.officialLink(value));
    }
    @Test public void apiErrorsAndMalformedResponsesNeverMasqueradeAsAnEmptyLibrary() throws Exception {
        for (String body : new String[]{"{\"error\":{\"code\":1}}", "{\"success\":false}"}) {
            try { StremioApi.response(body.getBytes(StandardCharsets.UTF_8)); fail("Expected failure"); }
            catch (StremioApi.Failure expected) { assertFalse(expected.getMessage().contains("authKey")); }
        }
        assertEquals(0, StremioApi.response("{\"result\":[]}".getBytes(StandardCharsets.UTF_8)).getJSONArray("result").length());
        assertFalse(StremioApi.validKey("invalid\nheader"));
    }
    @Test public void linkDataNotReadyIsPendingButExpiredOrInvalidSessionsAreErrors() throws Exception {
        assertEquals("", StremioApi.linkKey("{\"error\":{\"code\":101,\"message\":\"Link code data not found\"}}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("example_auth_key", StremioApi.linkKey("{\"result\":{\"authKey\":\"example_auth_key\"}}".getBytes(StandardCharsets.UTF_8)));
        for (int code : new int[]{1, 102, 403}) {
            try { StremioApi.linkKey(("{\"error\":{\"code\":" + code + "}}").getBytes(StandardCharsets.UTF_8)); fail("Expected error"); }
            catch (StremioApi.Failure expected) { assertEquals(code, expected.code); }
        }
    }
    @Test public void paddedOpaqueAuthKeysSurviveLoginWithoutBeingTrimmedOrDecoded() throws Exception {
        String padded = "AbC+/0123456789012345678901234567890123456789=";
        assertTrue(StremioApi.validKey(padded));
        byte[] response = new JSONObject().put("result", new JSONObject().put("success", true).put("authKey", padded))
                .toString().getBytes(StandardCharsets.UTF_8);
        assertEquals(padded, StremioApi.linkKey(response));
        for (String invalid : new String[]{"", "short", " leading-space", "trailing-space ", "embedded space", "newline\nvalue", "control\u0000value", "a".repeat(4097)})
            assertFalse(StremioApi.validKey(invalid));
    }
}
