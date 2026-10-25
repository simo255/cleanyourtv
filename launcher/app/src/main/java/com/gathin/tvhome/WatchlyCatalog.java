package com.gathin.tvhome;

import java.net.URI;
import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps the private Watchly token on the selected ElfHosted origin. */
final class WatchlyCatalog {
    static String base(String address) {
        if (address == null || !address.matches("[\\x21-\\x7e]+")) throw new IllegalArgumentException();
        URI uri = URI.create(address);
        if (!"https".equals(uri.getScheme()) || !"watchly.elfhosted.com".equals(uri.getRawAuthority())
                || uri.getRawQuery() != null || uri.getRawFragment() != null) throw new IllegalArgumentException();
        String path = uri.getRawPath();
        if (path.endsWith("/manifest.json")) path = path.substring(0, path.length() - "manifest.json".length());
        if (!path.matches("/[A-Za-z0-9_-]{1,64}/")) throw new IllegalArgumentException();
        return "https://watchly.elfhosted.com" + path;
    }

    static String topPicks(JSONObject manifest, String type) {
        JSONArray catalogs = manifest.optJSONArray("catalogs");
        if (catalogs != null) for (int i = 0; i < catalogs.length(); i++) {
            JSONObject catalog = catalogs.optJSONObject(i);
            if (catalog != null && type.equals(catalog.optString("type"))
                    && "watchly.rec".equals(catalog.optString("id"))) return "watchly.rec";
        }
        throw new IllegalArgumentException("Enable Watchly Top Picks for this category");
    }

    private WatchlyCatalog() { }
}
