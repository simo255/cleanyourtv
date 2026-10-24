package com.gathin.tvhome;

import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/** Independent read-only feeds. Private Trakt URLs never leave the official add-on origin. */
final class RecommendationRepository {
    static final int FOR_YOU = CatalogSource.TRAKT, WATCHLIST = CatalogSource.WATCHLIST,
            TRENDING = CatalogSource.TRENDING, RECENT = CatalogSource.RECENT;
    static final long REFRESH_MS = 4 * 60 * 60_000L;
    private static final String POPULAR_BASE = "https://v3-cinemeta.strem.io/";
    private static final String POPULAR_ACCOUNT = "cinemeta-popular-v1";
    static final class Feed {
        final File cache;
        List<CatalogItem> movies = new ArrayList<>(), shows = new ArrayList<>();
        final CatalogRanking movieRanking = new CatalogRanking(), showRanking = new CatalogRanking();
        long syncedAt, lastAttempt;
        boolean loading;
        int error;
        Feed(File cache) { this.cache = cache; }
    }
    interface Listener { void changed(int source); }
    private final File posterDir;
    private final RecommendationFeedback feedback;
    private final JSONArray completed;
    private final JSONArray reviewed, positives;
    private JSONArray observed = new JSONArray();
    private final boolean enhanced;
    private final android.content.SharedPreferences preferences;
    private final TmdbCatalog tmdb;
    private final ContentResolver resolver;
    private final Feed[] feeds = new Feed[CatalogSource.COUNT];
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService publicWorker = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor images = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    private final ThreadPoolExecutor downloads = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    private volatile int posterGeneration;
    private RecommendationTaste rankedTaste;
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private final Set<String> watchedMovies = new HashSet<>(), watchedShows = new HashSet<>();
    private String base = "", fingerprint = "";
    private volatile boolean closed;
    private Listener listener;

    RecommendationRepository(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
        posterDir = new File(context.getCacheDir(), "posters"); posterDir.mkdirs();
        feeds[FOR_YOU] = new Feed(new File(context.getFilesDir(), "recommendations-cache.json"));
        feeds[WATCHLIST] = new Feed(new File(context.getFilesDir(), "watchlist-cache.json"));
        feeds[TRENDING] = new Feed(new File(context.getFilesDir(), "trending-cache.json"));
        feeds[RECENT] = new Feed(new File(context.getFilesDir(), "recent-cache.json"));
        JSONObject options = new JSONObject();
        try {
            JSONObject config = readJson(new File(context.getFilesDir(), "recommendations-config.json"));
            base = config.getString("addon_base");
            URI uri = URI.create(base);
            if (!"https".equals(uri.getScheme()) || !"www.strem.io".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || !uri.getPath().matches("/trakt/addon/[A-Za-z0-9_-]+/")) throw new Exception();
            fingerprint = hash(base);
            addIds(watchedMovies, config.optJSONArray("watched_movie_ids"));
            addIds(watchedShows, config.optJSONArray("watched_series_ids"));
            options = config;
        } catch (Exception ignored) { base = ""; fingerprint = ""; }
        JSONObject profile = options.optJSONObject("taste_profile");
        completed = profile != null && profile.optInt("schema") == 1 && profile.optJSONArray("completed") != null
                ? profile.optJSONArray("completed") : new JSONArray();
        reviewed = profile != null && profile.optInt("schema") == 1 ? profile.optJSONArray("viewing") : null;
        positives = profile != null && profile.optInt("schema") == 1 ? profile.optJSONArray("positives") : null;
        JSONObject tuning = options.optJSONObject("recommendation_options");
        enhanced = tuning != null && tuning.optBoolean("enhanced", false);
        preferences = context.getSharedPreferences("discovery", 0);
        feedback = new RecommendationFeedback(new File(context.getFilesDir(), "recommendation-feedback.json"), fingerprint);
        tmdb = new TmdbCatalog(options.optString("tmdb_read_token"), new File(context.getCacheDir(), "tmdb"));
        for (int source = 0; source < feeds.length; source++) {
            if (feeds[source] == null || (!CatalogSource.publicFeed(source) && !configured())) continue;
            Feed feed = feeds[source];
            try {
                JSONObject cache = readJson(feed.cache);
                if (!CatalogItem.cacheMatches(cache, account(source), source)) continue;
                if (source == FOR_YOU) {
                    addIds(watchedMovies, cache.optJSONArray("watched_movie_ids"));
                    addIds(watchedShows, cache.optJSONArray("watched_series_ids"));
                }
                feed.movies = CatalogItem.parse(cache.getJSONArray("movies"), "movie", source == FOR_YOU && !enhanced, watchedMovies);
                feed.shows = CatalogItem.parse(cache.getJSONArray("shows"), "series", false, watchedShows);
                feed.syncedAt = cache.optLong("synced_at");
            } catch (Exception ignored) { }
        }
    }
    boolean configured() { return !base.isEmpty(); }
    boolean enhanced() { return enhanced; }
    boolean recentOnly() { return !enhanced || preferences.getBoolean("recent_only", true); }
    void togglePeriod() { preferences.edit().putBoolean("recent_only", !recentOnly()).apply(); }
    void observe(JSONArray values) { if (enhanced) { observed = values; rankedTaste = null; } }
    Feed feed(int source) {
        Feed stored = feeds[source], feed = new Feed(stored.cache);
        // Reapply on reads too: saved catalogs cannot retain the old window across New Year's Day.
        RecommendationTaste taste = taste();
        java.time.LocalDate today = java.time.LocalDate.now();
        feed.movies = stored.movieRanking.apply(stored.movies, source, today, taste, recentOnly());
        feed.shows = stored.showRanking.apply(stored.shows, source, today, taste, recentOnly());
        feed.syncedAt = stored.syncedAt; feed.lastAttempt = stored.lastAttempt; feed.loading = stored.loading; feed.error = stored.error;
        return feed;
    }
    private RecommendationTaste taste() {
        if (rankedTaste == null) {
            if (!enhanced) rankedTaste = new RecommendationTaste(completed, feedback.entries());
            else {
                rankedTaste = new RecommendationTaste(completed, feedback.entries(), new JSONArray(currentViewing().values()), positives, watchedMovies);
            }
        }
        return rankedTaste;
    }
    boolean learned() { return taste().learned(); }
    private java.util.Map<String, JSONObject> currentViewing() {
        java.util.Map<String, JSONObject> seasons = DiscoveryTaste.records(reviewed);
        for (java.util.Map.Entry<String, JSONObject> entry : DiscoveryTaste.records(observed).entrySet()) {
            JSONObject previous = seasons.get(entry.getKey());
            seasons.put(entry.getKey(), previous == null ? entry.getValue() : SeasonViewing.merge(previous, entry.getValue()));
        }
        return seasons;
    }
    boolean choose(CatalogItem item, String decision) {
        JSONArray seasons = null;
        if (enhanced && RecommendationTaste.COMPLETED.equals(decision)) try {
            JSONObject state = currentViewing().get(RecommendationTaste.key(item.type, item.id));
            if (state != null && state.optJSONArray("seasons") != null) {
                seasons = new JSONArray();
                JSONArray values = state.getJSONArray("seasons");
                for (int i = 0; i < values.length(); i++) {
                    JSONObject season = new JSONObject(values.getJSONObject(i).toString());
                    if (season.optInt("aired") > 0 && season.optInt("future") == 0) seasons.put(season.put("status", "watched"));
                }
            }
        } catch (Exception ignored) { seasons = null; }
        boolean saved = feedback.choose(item, decision, seasons);
        if (saved) rankedTaste = null;
        return saved;
    }
    boolean resetFeedback() {
        boolean saved = feedback.reset();
        if (saved) rankedTaste = null;
        return saved;
    }
    static boolean hasTmdb(Feed feed) {
        for (CatalogItem item : feed.movies) if ("TMDb".equals(item.provider)) return true;
        for (CatalogItem item : feed.shows) if ("TMDb".equals(item.provider)) return true;
        return false;
    }
    String account(int source) { return source == RECENT ? (enhanced ? "cinemeta-discovery-v1-" : "cinemeta-recent-v2-") + java.time.LocalDate.now().getYear() : source == TRENDING ? POPULAR_ACCOUNT : fingerprint; }
    void listen(Listener listener) { this.listener = listener; }
    private void publish(int source) { if (!closed && listener != null) listener.changed(source); }

    void refresh(int source, boolean force) {
        if (!CatalogSource.networkFeed(source)) return;
        Feed feed = feeds[source]; long now = System.currentTimeMillis();
        if (closed || (!CatalogSource.publicFeed(source) && !configured()) || feed.loading) return;
        if (!force && (now - feed.syncedAt < REFRESH_MS || now - feed.lastAttempt < 60_000)) return;
        feed.loading = true; feed.lastAttempt = now; feed.error = 0; publish(source);
        Set<String> movieSeen = new HashSet<>(watchedMovies), showSeen = new HashSet<>(watchedShows);
        boolean knownHistory = !movieSeen.isEmpty() || !showSeen.isEmpty();
        // Only the selected source is fetched. Switching source queues another bounded operation.
        (CatalogSource.publicFeed(source) ? publicWorker : worker).execute(() -> {
            try {
                if (!CatalogSource.publicFeed(source)) {
                    int movieHistory = readHistory("movie", movieSeen), showHistory = readHistory("series", showSeen);
                    // Invalid private IDs may return HTTP 200 with empty catalogs. Keep prior private caches.
                    if (movieHistory + showHistory == 0 && knownHistory)
                        throw new StatusException(401);
                }
                String endpoint = source == FOR_YOU ? "recommendations" : source == WATCHLIST ? "watchlist" : "top";
                String origin = CatalogSource.publicFeed(source) ? POPULAR_BASE : base;
                List<CatalogItem> movies = source == RECENT ? recent("movie")
                        : CatalogItem.parse(catalog(origin, "movie", endpoint), "movie", source == FOR_YOU && !enhanced, movieSeen);
                List<CatalogItem> shows = source == RECENT ? recent("series")
                        : CatalogItem.parse(catalog(origin, "series", endpoint), "series", false, showSeen);
                long at = System.currentTimeMillis();
                JSONObject cache = new JSONObject().put("account", account(source)).put("source", source).put("synced_at", at)
                        .put("movies", CatalogItem.json(movies)).put("shows", CatalogItem.json(shows));
                if (source == FOR_YOU) cache.put("watched_movie_ids", new JSONArray(movieSeen)).put("watched_series_ids", new JSONArray(showSeen));
                writeAtomic(feed.cache, cache.toString().getBytes(StandardCharsets.UTF_8));
                main.post(() -> {
                    if (closed) return;
                    if (source == FOR_YOU) {
                        watchedMovies.clear(); watchedMovies.addAll(movieSeen);
                        watchedShows.clear(); watchedShows.addAll(showSeen);
                    }
                    rankedTaste = null;
                    feed.movies = movies; feed.shows = shows; feed.syncedAt = at;
                    feed.error = 0; feed.loading = false; publish(source);
                });
            } catch (Exception failure) {
                final int status = failure instanceof StatusException ? ((StatusException) failure).status : -1;
                main.post(() -> { if (!closed) { feed.loading = false; feed.error = status; publish(source); } });
            }
        });
    }
    private JSONArray catalog(String origin, String type, String name) throws Exception {
        JSONObject response = new JSONObject(new String(download(origin + "catalog/" + type + "/" + name + ".json",
                2_000_000, POPULAR_BASE.equals(origin) ? 1 : 0), StandardCharsets.UTF_8));
        return CatalogItem.metas(response);
    }
    /** Four public, bounded requests; the year feed supplies titles missing from the popular top page. */
    private List<CatalogItem> recent(String type) throws Exception {
        if (enhanced) {
            List<List<CatalogItem>> pages = new ArrayList<>();
            long deadline = android.os.SystemClock.elapsedRealtime() + 60_000;
            Exception failure = null;
            java.time.LocalDate today = java.time.LocalDate.now();
            for (String path : CinemetaDiscovery.paths(today.getYear(), today.getDayOfYear())) {
                if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (android.os.SystemClock.elapsedRealtime() >= deadline) break;
                try { pages.add(CatalogItem.parse(catalog(POPULAR_BASE, type, path), type, false, java.util.Collections.emptySet())); }
                catch (Exception e) { failure = e; }
            }
            if (pages.isEmpty()) throw failure == null ? new Exception() : failure;
            return CinemetaDiscovery.merge(pages);
        }
        List<CatalogItem> items = new ArrayList<>();
        int currentYear = java.time.LocalDate.now().getYear();
        for (int year : new int[]{currentYear, currentYear - 1}) {
            JSONArray metas = catalog(POPULAR_BASE, type, "year/genre=" + year);
            items.addAll(CatalogItem.parse(metas, type, false, java.util.Collections.emptySet()));
        }
        items.addAll(tmdb.items(type, java.time.LocalDate.now()));
        return CatalogDiscovery.recent(items, java.time.LocalDate.now());
    }
    private int readHistory(String type, Set<String> seen) throws Exception {
        JSONArray metas = catalog(base, type, "history");
        for (int i = 0; i < metas.length(); i++) {
            String id = metas.getJSONObject(i).optString("id");
            if (id.matches("tt[0-9]+")) seen.add(id);
        }
        return metas.length();
    }
    private static void addIds(Set<String> ids, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) { String id = values.optString(i); if (id.matches("tt[0-9]+")) ids.add(id); }
    }
    private static JSONObject readJson(File file) throws Exception {
        if (file.length() > 2_000_000) throw new Exception();
        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }
    private static void writeAtomic(File file, byte[] bytes) throws Exception {
        android.util.AtomicFile atomic = new android.util.AtomicFile(file);
        FileOutputStream stream = atomic.startWrite();
        try { stream.write(bytes); atomic.finishWrite(stream); }
        catch (Exception e) { atomic.failWrite(stream); throw e; }
    }
    private static final class StatusException extends Exception {
        final int status;
        StatusException(int status) { this.status = status; }
    }
    // 0: no redirects (private catalogs); 1: public Cinemeta; 2: public MetaHub artwork.
    private static byte[] download(String address, int maxBytes, int redirectPolicy) throws Exception {
        for (int redirects = 0; redirects <= 2; redirects++) {
            URI uri = URI.create(address);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new Exception();
            if (redirectPolicy == 1 && !CatalogEndpoint.publicCinemeta(uri)) throw new Exception();
            if (redirectPolicy == 2 && !CatalogEndpoint.publicPoster(uri)) throw new Exception();
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(12_000); connection.setReadTimeout(18_000);
            connection.setRequestProperty("User-Agent", "OnnTvHome/2.9");
            connection.setInstanceFollowRedirects(false);
            try {
                int status = connection.getResponseCode();
                if (redirectPolicy != 0 && redirects < 2 && (status == 301 || status == 302 || status == 303 || status == 307 || status == 308)) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new Exception();
                    address = uri.resolve(location).toString();
                    continue;
                }
                if (status != 200) throw new StatusException(status);
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int count;
                    while ((count = input.read(buf)) != -1) { if (out.size() + count > maxBytes) throw new Exception(); out.write(buf, 0, count); }
                    return out.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new Exception();
    }
    void beginPosterBatch() {
        posterGeneration++;
        images.getQueue().clear(); downloads.getQueue().clear();
    }
    void poster(String address, ImageView view) {
        view.setTag(address); view.setImageDrawable(null);
        if (closed || address.isEmpty()) return;
        Bitmap ready = bitmaps.get(address);
        if (ready != null) { view.setImageBitmap(ready); return; }
        int generation = posterGeneration;
        images.execute(() -> loadPoster(address, view, generation, false));
    }
    private void loadPoster(String address, ImageView view, int generation, boolean network) {
        try {
            if (closed || generation != posterGeneration) return;
            File file = new File(posterDir, hash(address));
            byte[] data;
            if (file.isFile()) data = Files.readAllBytes(file.toPath());
            else {
                // Cached posters never queue behind a slow remote image.
                if (!network) {
                    downloads.execute(() -> loadPoster(address, view, generation, true));
                    return;
                }
                if (address.startsWith("content://")) {
                    try (InputStream input = resolver.openInputStream(Uri.parse(address)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        if (input == null) throw new Exception();
                        byte[] buffer = new byte[8192]; int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (out.size() + count > 3_000_000) throw new Exception();
                            out.write(buffer, 0, count);
                        }
                        data = out.toByteArray();
                    }
                } else data = download(address, 3_000_000, CatalogEndpoint.publicPoster(URI.create(address)) ? 2 : 0);
                synchronized (posterDir) {
                    writeAtomic(file, data);
                    File[] files = posterDir.listFiles();
                    if (files != null) {
                        long total = 0; for (File f : files) total += f.length();
                        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
                        for (File f : files) { if (total <= 12_000_000) break; long bytes = f.length(); if (f.delete()) total -= bytes; }
                    }
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > 300 || bounds.outHeight / options.inSampleSize > 450) options.inSampleSize *= 2;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (bitmap == null || closed) return;
            bitmaps.put(address, bitmap);
            main.post(() -> { if (!closed && generation == posterGeneration && address.equals(view.getTag())) view.setImageBitmap(bitmap); });
        } catch (Exception ignored) { }
    }
    private static String hash(String text) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(); for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return result.toString();
    }
    void close() { closed = true; listener = null; worker.shutdownNow(); publicWorker.shutdownNow(); images.shutdownNow(); downloads.shutdownNow(); bitmaps.evictAll(); }
}
