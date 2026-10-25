package com.gathin.tvhome;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import java.net.URI;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class TmdbCatalogTest {
    @Test public void bearerTokenCannotBeRedirectedToPrivateOrUntrustedEndpoints() {
        assertTrue(CatalogEndpoint.tmdbApi(URI.create("https://api.themoviedb.org/3/discover/tv?page=1")));
        for (String url : new String[]{"https://www.strem.io/trakt/addon/example/", "http://api.themoviedb.org/3/discover/tv",
                "https://api.themoviedb.org.evil.test/3/discover/tv", "https://user@api.themoviedb.org/3/discover/tv",
                "https://api.themoviedb.org:443/3/discover/tv", "https://api.themoviedb.org/3/discover/tv#private",
                "https://api.themoviedb.org/3/account/1"}) assertFalse(url, CatalogEndpoint.tmdbApi(URI.create(url)));
    }
    @Test public void discoveryQueriesApplyDatesAndVoteFloorWithoutSendingWatchHistoryOrCredentialsInTheUrl() {
        String url = TmdbCatalog.discover("tv", LocalDate.of(2027, 1, 2), "fr-CA").toString();
        assertTrue(url.contains("first_air_date.gte=2025-01-01")); assertTrue(url.contains("first_air_date.lte=2027-01-02"));
        assertTrue(url.contains("vote_count.gte=10")); assertFalse(url.contains("api_key"));
        assertFalse(url.contains("watched")); assertFalse(url.contains("history"));
    }
    @Test public void imdbMappingIsRequiredAndTmdbScoresAreNeverMislabelledAsImdbRatings() throws Exception {
        JSONObject details = new JSONObject().put("name", "Example").put("first_air_date", "2026-01-02")
                .put("external_ids", new JSONObject().put("imdb_id", "tt123"))
                .put("poster_path", "/test-poster.jpg").put("vote_average", 8.5)
                .put("genres", new JSONArray().put(new JSONObject().put("name", "Drama")));
        CatalogItem item = TmdbCatalog.fromDetails("series", details);
        assertEquals("tt123", item.id); assertEquals("2026", item.year); assertEquals("TMDb", item.provider);
        assertEquals("Drama", item.genres); assertEquals("", item.rating);
        assertTrue(item.poster.startsWith("https://image.tmdb.org/"));
        details.remove("external_ids"); assertNull(TmdbCatalog.fromDetails("series", details));
    }
    @Test public void malformedTokensAndArtworkCannotInjectHeadersOrChangeArtworkHosts() throws Exception {
        assertFalse(TmdbCatalog.validToken("token\r\nHeader: value")); assertFalse(TmdbCatalog.validToken(""));
        JSONObject data = new JSONObject().put("title", "Movie").put("release_date", "2025-12-01")
                .put("imdb_id", "tt123").put("poster_path", "//evil.test/track.jpg");
        assertEquals("", TmdbCatalog.fromDetails("movie", data).poster);
    }
}
