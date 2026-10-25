package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional public candidates. Only the user's own TMDB read token goes to the TMDB API. */
final class TmdbCatalog {
    private final String token;
    private final File directory;
    TmdbCatalog(String token, File directory) {
        this.token = validToken(token) ? token : ""; this.directory = directory;
    }
    static boolean validToken(String value) { return value != null && value.length() >= 20 && value.length() <= 4096 && value.matches("[A-Za-z0-9._~-]+"); }
    boolean configured() { return !token.isEmpty(); }
    List<CatalogItem> items(String type, LocalDate today) {
        List<CatalogItem> result = new ArrayList<>();
        if (!configured()) return result;
        String media = "series".equals(type) ? "tv" : "movie";
        String language = "fr".equals(Locale.getDefault().getLanguage()) ? "fr-CA" : "en-US";
        long deadline = System.currentTimeMillis() + 15_000;
        try {
            JSONObject response = get(discover(media, today, language));
            JSONArray entries = response.getJSONArray("results");
            // Mapping to IMDb is required for the Stremio detail link. Cache those public mappings for a week.
            for (int i = 0; i < Math.min(10, entries.length()) && System.currentTimeMillis() < deadline; i++) {
                JSONObject entry = entries.optJSONObject(i);
                long id = entry == null ? 0 : entry.optLong("id");
                if (id <= 0) continue;
                directory.mkdirs(); File file = new File(directory, media + "-" + id + "-" + language + ".json");
                CatalogItem item = null;
                if (file.isFile() && file.length() <= 64_000 && System.currentTimeMillis() - file.lastModified() < 7 * 86_400_000L) {
                    try { item = new CatalogItem(new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))); }
                    catch (Exception ignored) { }
                }
                if (item == null) {
                    try {
                        item = fromDetails(type, get(URI.create("https://api.themoviedb.org/3/" + media + "/" + id
                                + "?append_to_response=external_ids&language=" + language)));
                        if (item != null) Files.write(file.toPath(), item.json().toString().getBytes(StandardCharsets.UTF_8));
                    } catch (Exception ignored) { }
                }
                if (item != null && item.id.matches("tt[0-9]+")) result.add(item);
            }
            trimCache();
        } catch (Exception ignored) { /* Existing Cinemeta candidates remain available. Never log token or headers. */ }
        return result;
    }
    static URI discover(String media, LocalDate today, String language) {
        String date = "tv".equals(media) ? "first_air_date" : "primary_release_date";
        return URI.create("https://api.themoviedb.org/3/discover/" + media + "?include_adult=false&sort_by=popularity.desc"
                + "&" + date + ".gte=" + (today.getYear() - 2) + "-01-01&" + date + ".lte=" + today
                + "&vote_count.gte=10&page=1&language=" + language);
    }
    static CatalogItem fromDetails(String type, JSONObject data) throws Exception {
        JSONObject external = data.optJSONObject("external_ids");
        String imdb = data.optString("imdb_id", external == null ? "" : external.optString("imdb_id"));
        String date = data.optString("series".equals(type) ? "first_air_date" : "release_date");
        String name = data.optString("series".equals(type) ? "name" : "title");
        if (!imdb.matches("tt[0-9]+") || date.length() != 10 || name.trim().isEmpty()) return null;
        try { LocalDate.parse(date); } catch (Exception ignored) { return null; }
        String path = data.optString("poster_path");
        String poster = path.matches("/[A-Za-z0-9_-]+\\.(jpg|png|webp)") ? "https://image.tmdb.org/t/p/w342" + path : "";
        JSONArray names = new JSONArray(), genres = data.optJSONArray("genres");
        if (genres != null) for (int i = 0; i < Math.min(8, genres.length()); i++) {
            JSONObject genre = genres.optJSONObject(i); if (genre != null) names.put(genre.optString("name"));
        }
        return new CatalogItem(new JSONObject().put("id", imdb).put("type", type)
                .put("name", name)
                .put("year", date.substring(0, 4)).put("released", date + "T00:00:00Z")
                .put("poster", poster).put("description", data.optString("overview")).put("genres", names).put("provider", "TMDb"));
    }
    private JSONObject get(URI uri) throws Exception {
        if (!CatalogEndpoint.tmdbApi(uri)) throw new Exception("Invalid TMDB endpoint");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3_000); connection.setReadTimeout(5_000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new Exception("TMDB unavailable");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) { if (out.size() + count > 1_000_000) throw new Exception("Response too large"); out.write(buffer, 0, count); }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    private void trimCache() {
        File[] files = directory.listFiles(); if (files == null) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - 300; i++) files[i].delete();
    }
}
