package com.gathin.tvhome;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps the private Watchly token on the selected ElfHosted origin. */
final class WatchlyCatalog {
    static final int MIN_VISIBLE = 20;

    /** Only enabled personalized rows, with bounded requests and no rewatch fallback. */
    static List<String> supplements(JSONObject manifest, String type) {
        List<String> result = new ArrayList<>();
        JSONArray catalogs = manifest.optJSONArray("catalogs");
        if (catalogs != null) for (int i = 0; i < catalogs.length() && result.size() < 4; i++) {
            JSONObject catalog = catalogs.optJSONObject(i);
            if (catalog == null || !type.equals(catalog.optString("type"))) continue;
            String id = catalog.optString("id");
            if (id.matches("watchly\\.(item|theme|creators)(\\.[0-9]{1,4})?")
                    || id.equals("watchly.all.loved") || id.equals("watchly.liked.all")) {
                boolean required = false;
                JSONArray extras = catalog.optJSONArray("extra");
                if (extras != null) for (int j = 0; j < extras.length(); j++) {
                    JSONObject extra = extras.optJSONObject(j);
                    if (extra != null && extra.optBoolean("isRequired")) required = true;
                }
                if (!required && !result.contains(id)) result.add(id);
            }
        }
        return result;
    }

    static boolean needsMore(List<CatalogItem> items, LocalDate today, RecommendationTaste taste) {
        return taste.apply(CatalogDiscovery.recommendations(items, CatalogSource.WATCHLY, today),
                CatalogSource.WATCHLY).size() < MIN_VISIBLE;
    }

    static List<CatalogItem> append(List<CatalogItem> primary, List<CatalogItem> extra) {
        List<CatalogItem> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (List<CatalogItem> page : java.util.Arrays.asList(primary, extra)) for (CatalogItem item : page) {
            if (added.add(RecommendationTaste.key(item.type, item.id))) result.add(item);
        }
        return result;
    }

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
