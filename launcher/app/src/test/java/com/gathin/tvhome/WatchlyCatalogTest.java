package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WatchlyCatalogTest {
    private static final String BASE = "https://watchly.elfhosted.com/example_token/";
    @Test public void acceptsOnlyThePrivateElfHostedTokenPath() {
        assertEquals(BASE, WatchlyCatalog.base(BASE + "manifest.json"));
        assertEquals(BASE, WatchlyCatalog.base(BASE));
        for (String bad : new String[]{BASE.replace("https:", "http:"), BASE + "?secret=x", BASE + "#fragment",
                BASE.replace("watchly.elfhosted.com", "watchly.elfhosted.com.evil.example"),
                BASE.replace("watchly.elfhosted.com", "user@watchly.elfhosted.com"),
                BASE.replace("watchly.elfhosted.com", "watchly.elfhosted.com:443"),
                BASE.replace("example_token", "../another"), BASE.replace("example_token", "%2Fother"),
                "https://www.strem.io/trakt/addon/old/", BASE + "\n"}) {
            try { WatchlyCatalog.base(bad); fail("Accepted an unsafe private catalog address"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void doesNotConfuseTopPicksWithRewatchOrOtherCategories() throws Exception {
        JSONObject manifest = new JSONObject().put("catalogs", new JSONArray()
                .put(new JSONObject().put("type", "movie").put("id", "watchly.rewatch"))
                .put(new JSONObject().put("type", "series").put("id", "watchly.rec")));
        assertEquals("watchly.rec", WatchlyCatalog.topPicks(manifest, "series"));
        try { WatchlyCatalog.topPicks(manifest, "movie"); fail("Must not substitute rewatch results"); }
        catch (IllegalArgumentException expected) { }
    }
}
