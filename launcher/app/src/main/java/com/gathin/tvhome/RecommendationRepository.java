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

/** Independent read-only feeds. Private Watchly URLs never leave the ElfHosted origin. */
final class RecommendationRepository {
    static final int FOR_YOU = CatalogSource.WATCHLY, WATCHLIST = CatalogSource.WATCHLIST,
            TRENDING = CatalogSource.TRENDING, RECENT = CatalogSource.RECENT;
    static final int EMPTY_PRIVATE = -2;
    static final long REFRESH_MS = CatalogRefresh.INTERVAL_MS;
    private static final String POPULAR_BASE = "https://v3-cinemeta.strem.io/";
    private static final String POPULAR_ACCOUNT = "cinemeta-popular-v1";
    static final class Feed {
        final File cache;
        List<CatalogItem> movies = new ArrayList<>(), shows = new ArrayList<>();
        final CatalogRanking movieRanking = new CatalogRanking(), showRanking = new CatalogRanking();
        final CatalogRefresh refresh = new CatalogRefresh();
        long syncedAt, lastAttempt;
        boolean loading;
        int error;
        Feed(File cache) { this.cache = cache; }
    }
    interface Listener { void changed(int source); }
    private final File posterDir;
    private final RecommendationFeedback feedback;
    private final FeedbackSync feedbackSync;
    private final JSONArray completed;
    private final JSONArray reviewed, positives;
    private JSONArray observed = new JSONArray();
    private JSONArray observedPositives = new JSONArray();
    private final Set<String> observedMovies = new HashSet<>();
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
    private volatile int posterWidth = 300, posterHeight = 450;
    private long posterBytes = -1;
    void posterTarget(int widthPx, int heightPx) { posterWidth = widthPx; posterHeight = heightPx; }
    void trimMemory() { bitmaps.evictAll(); }
    private RecommendationTaste rankedTaste;
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(PosterSampling.cacheBytes(Runtime.getRuntime().maxMemory())) {
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
            base = WatchlyCatalog.base(config.getString("addon_base"));
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
        feedbackSync = new FeedbackSync(context, base, message -> {
            if (message == R.string.reco_sync_done && !closed) refresh(FOR_YOU, true);
        });
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
                feed.movies = CatalogItem.parse(cache.getJSONArray("movies"), "movie", source == FOR_YOU, watchedMovies);
                feed.shows = CatalogItem.parse(cache.getJSONArray("shows"), "series", false, watchedShows);
                feed.syncedAt = cache.optLong("synced_at");
            } catch (Exception ignored) { }
        }
    }
    boolean configured() { return !base.isEmpty(); }
    boolean enhanced() { return enhanced; }
    boolean recentOnly() { return !enhanced || preferences.getBoolean("recent_only", false); }
    void togglePeriod() { preferences.edit().putBoolean("recent_only", !recentOnly()).apply(); }
    void observe(JSONObject values) {
        if (!enhanced) return;
        observed = values.optJSONArray("series");
        observedPositives = values.optJSONArray("movies");
        observedMovies.clear();
        observedMovies.addAll(StremioViewing.movieIds(values));
        rankedTaste = null;
    }
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
                Set<String> movies = new HashSet<>(watchedMovies); movies.addAll(observedMovies);
                java.util.Map<String, JSONObject> movieSignals = DiscoveryTaste.records(positives);
                for (java.util.Map.Entry<String, JSONObject> entry : DiscoveryTaste.records(observedPositives).entrySet()) movieSignals.putIfAbsent(entry.getKey(), entry.getValue());
                rankedTaste = new RecommendationTaste(completed, feedback.entries(), new JSONArray(currentViewing().values()), new JSONArray(movieSignals.values()), movies);
            }
        }
        return rankedTaste;
    }
    boolean learned() { return taste().learned(); }
    private java.util.Map<String, JSONObject> currentViewing() {
        java.util.Map<String, JSONObject> seasons = DiscoveryTaste.records(reviewed);
        for (java.util.Map.Entry<String, JSONObject> entry : DiscoveryTaste.records(observed).entrySet()) {
            JSONObject previous = seasons.get(entry.getKey());
            seasons.put(entry.getKey(), previous == null ? entry.getValue() : SeasonViewing.mergeProgress(previous, entry.getValue()));
        }
        return seasons;
    }
    boolean choose(CatalogItem item, String decision) {
        JSONArray seasons = null;
        if (enhanced && "series".equals(item.type) && RecommendationTaste.completion(decision)) try {
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
        if (saved) {
            rankedTaste = null;
            try { feedbackSync.choice(new JSONObject().put("id", item.id).put("type", item.type)
                    .put("name", item.name).put("decision", decision).put("seasons", seasons)); } catch (Exception ignored) { }
        }
        return saved;
    }
    boolean feedbackSyncEnabled() { return feedbackSync.enabled(); }
    int feedbackSyncMessage() { return feedbackSync.statusMessage(); }
    void toggleFeedbackSync() {
        if (feedbackSync.enabled()) feedbackSync.disable(); else feedbackSync.enable(feedback.entries());
    }
    void retryFeedbackSync() { feedbackSync.retry(); }
    boolean resetFeedback() {
        boolean saved = feedback.reset();
        if (saved) { rankedTaste = null; feedbackSync.cancelPending(); }
        return saved;
    }
    static boolean hasTmdb(Feed feed) {
        for (CatalogItem item : feed.movies) if ("TMDb".equals(item.provider)) return true;
        for (CatalogItem item : feed.shows) if ("TMDb".equals(item.provider)) return true;
        return false;
    }
    String account(int source) { return source == RECENT ? (enhanced ? "cinemeta-discovery-v2-" : "cinemeta-recent-v3-") + java.time.LocalDate.now().getYear() : source == TRENDING ? POPULAR_ACCOUNT : fingerprint; }
    void listen(Listener listener) { this.listener = listener; }
    private void publish(int source) { if (!closed && listener != null) listener.changed(source); }

    void refresh(int source, boolean force) {
        if (!CatalogSource.networkFeed(source)) return;
        Feed feed = feeds[source]; long now = System.currentTimeMillis();
        if (closed || (!CatalogSource.publicFeed(source) && !configured()) || feed.loading) return;
        if (!feed.refresh.due(force, now, android.os.SystemClock.elapsedRealtime(), feed.syncedAt)) return;
        feed.loading = true; feed.lastAttempt = now; feed.error = 0; publish(source);
        Set<String> movieSeen = new HashSet<>(watchedMovies), showSeen = new HashSet<>(watchedShows);
        List<CatalogItem> previousMovies = new ArrayList<>(feed.movies), previousShows = new ArrayList<>(feed.shows);
        RecommendationTaste refreshTaste = source == FOR_YOU ? taste() : null;
        // Only the selected source is fetched. Switching source queues another bounded operation.
        (CatalogSource.publicFeed(source) ? publicWorker : worker).execute(() -> {
            try {
                String origin = CatalogSource.publicFeed(source) ? POPULAR_BASE : base;
                JSONObject manifest = source == FOR_YOU ? new JSONObject(new String(download(base + "manifest.json",
                        512_000, 0), StandardCharsets.UTF_8)) : null;
                JSONArray rawMovies = source == RECENT || source == FOR_YOU ? new JSONArray() : catalog(origin, "movie", "top");
                JSONArray rawShows = source == RECENT || source == FOR_YOU ? new JSONArray() : catalog(origin, "series", "top");
                List<CatalogItem> movies = source == FOR_YOU ? watchly("movie", manifest, movieSeen, refreshTaste)
                        : source == RECENT ? recent("movie", previousMovies)
                        : CatalogItem.parse(rawMovies, "movie", source == FOR_YOU, movieSeen);
                List<CatalogItem> shows = source == FOR_YOU ? watchly("series", manifest, showSeen, refreshTaste)
                        : source == RECENT ? recent("series", previousShows)
                        : CatalogItem.parse(rawShows, "series", false, showSeen);
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
                    feed.refresh.succeeded(android.os.SystemClock.elapsedRealtime());
                    feed.error = 0; feed.loading = false; publish(source);
                });
            } catch (Exception failure) {
                final int status = failure instanceof StatusException ? ((StatusException) failure).status : -1;
                main.post(() -> { if (!closed) {
                    feed.refresh.failed(android.os.SystemClock.elapsedRealtime(), status);
                    feed.loading = false; feed.error = status; publish(source);
                } });
            }
        });
    }
    private JSONArray catalog(String origin, String type, String name) throws Exception {
        JSONObject response = new JSONObject(new String(download(origin + "catalog/" + type + "/" + name + ".json",
                2_000_000, POPULAR_BASE.equals(origin) ? 1 : 0), StandardCharsets.UTF_8));
        return CatalogItem.metas(response);
    }
    private List<CatalogItem> watchly(String type, JSONObject manifest, Set<String> seen,
            RecommendationTaste profile) throws Exception {
        boolean excludeWatched = "movie".equals(type);
        List<CatalogItem> items = CatalogItem.parse(catalog(base, type, WatchlyCatalog.topPicks(manifest, type)),
                type, excludeWatched, seen);
        java.time.LocalDate today = java.time.LocalDate.now();
        long deadline = android.os.SystemClock.elapsedRealtime() + 60_000;
        for (String id : WatchlyCatalog.supplements(manifest, type)) {
            if (!WatchlyCatalog.needsMore(items, today, profile)) break;
            if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (android.os.SystemClock.elapsedRealtime() >= deadline) break;
            try {
                items = WatchlyCatalog.append(items, CatalogItem.parse(catalog(base, type, id),
                        type, excludeWatched, seen));
            } catch (Exception ignored) { /* A secondary row must not discard successful Top Picks. */ }
        }
        return items;
    }
    /** Public, bounded requests; successful discovery refreshes retain some older candidates. */
    private List<CatalogItem> recent(String type, List<CatalogItem> previous) throws Exception {
        if (enhanced) {
            List<List<CatalogItem>> pages = new ArrayList<>();
            long deadline = android.os.SystemClock.elapsedRealtime() + 60_000;
            Exception failure = null;
            java.time.LocalDate today = java.time.LocalDate.now();
            for (String path : CinemetaDiscovery.paths(type, today.getYear(), today.getDayOfYear())) {
                if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (android.os.SystemClock.elapsedRealtime() >= deadline) break;
                try { pages.add(CatalogItem.parse(catalog(POPULAR_BASE, type, path), type, false, java.util.Collections.emptySet())); }
                catch (Exception e) { failure = e; }
            }
            if (pages.isEmpty()) throw failure == null ? new Exception() : failure;
            return CinemetaDiscovery.retain(CinemetaDiscovery.merge(pages), previous);
        }
        List<CatalogItem> items = new ArrayList<>();
        int currentYear = java.time.LocalDate.now().getYear();
        for (int year : new int[]{currentYear, currentYear - 1, currentYear - 2}) {
            JSONArray metas = catalog(POPULAR_BASE, type, "year/genre=" + year);
            items.addAll(CatalogItem.parse(metas, type, false, java.util.Collections.emptySet()));
        }
        items.addAll(tmdb.items(type, java.time.LocalDate.now()));
        return CatalogDiscovery.recent(items, java.time.LocalDate.now());
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
        if (closed) return;
        PosterRequest previous = view.getTag() instanceof PosterRequest ? (PosterRequest) view.getTag() : null;
        if (!address.isEmpty() && previous != null
                && previous.reusable(address, posterGeneration, view.getDrawable() != null)) return;
        PosterRequest request = new PosterRequest(address, posterGeneration);
        view.setTag(request);
        if (address.isEmpty()) { view.setImageDrawable(null); return; }
        Bitmap ready = bitmaps.get(address);
        if (ready != null) { view.setImageBitmap(ready); return; }
        // Only a genuinely different poster needs the placeholder, never a metadata-only refresh.
        view.setImageDrawable(null);
        images.execute(() -> loadPoster(request, view, false));
    }
    private void loadPoster(PosterRequest request, ImageView view, boolean network) {
        String address = request.address;
        int generation = request.generation;
        try {
            if (closed || generation != posterGeneration) return;
            File file = new File(posterDir, hash(address));
            byte[] data = null;
            if (!network) {
                synchronized (posterDir) {
                    if (file.isFile()) try (InputStream input = new java.io.FileInputStream(file)) {
                        data = PosterData.read(input);
                    } catch (Exception invalidCache) { discardPoster(file); }
                }
            } else {
                if (address.startsWith("content://")) {
                    try (InputStream input = resolver.openInputStream(Uri.parse(address))) {
                        data = PosterData.read(input);
                    }
                } else data = download(address, PosterData.MAX_BYTES, CatalogEndpoint.publicPoster(URI.create(address)) ? 2 : 0);
            }
            if (closed || generation != posterGeneration) return;
            Bitmap bitmap = data == null ? null : decodePoster(data, posterWidth, posterHeight);
            if (bitmap == null) {
                // One replacement attempt, on the network pool. Invalid responses never enter the cache.
                if (!network) {
                    discardPoster(file);
                    downloads.execute(() -> loadPoster(request, view, true));
                } else request.failed();
                return;
            }
            if (closed || generation != posterGeneration) { bitmap.recycle(); return; }
            if (network) {
                // A full disk must not prevent a valid image from appearing this session.
                try { savePoster(file, data); } catch (Exception ignored) { }
            } else file.setLastModified(System.currentTimeMillis());
            bitmaps.put(address, bitmap);
            main.post(() -> { if (!closed && generation == posterGeneration && view.getTag() == request) view.setImageBitmap(bitmap); });
        } catch (Exception ignored) { request.failed(); }
    }
    private static Bitmap decodePoster(byte[] data, int width, int height) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = PosterSampling.sampleSize(bounds.outWidth, bounds.outHeight, width, height);
            int sampledWidth = bounds.outWidth / options.inSampleSize;
            if (width > 0 && sampledWidth > width) {
                options.inScaled = true; options.inDensity = sampledWidth; options.inTargetDensity = width;
            }
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (RuntimeException invalidImage) { return null; }
    }
    private void discardPoster(File file) {
        synchronized (posterDir) {
            long size = file.length();
            if (file.delete() && posterBytes >= 0) posterBytes -= size;
        }
    }
    private void savePoster(File file, byte[] data) throws Exception {
        synchronized (posterDir) {
            if (posterBytes < 0) {
                posterBytes = 0; File[] initial = posterDir.listFiles();
                if (initial != null) for (File f : initial) posterBytes += f.length();
            }
            long previous = file.isFile() ? file.length() : 0;
            writeAtomic(file, data); posterBytes += data.length - previous;
            if (posterBytes > 12_000_000) {
                File[] files = posterDir.listFiles();
                if (files != null) {
                    java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
                    long[] sizes = new long[files.length]; long total = 0;
                    for (int i = 0; i < files.length; i++) { sizes[i] = files[i].length(); total += sizes[i]; }
                    int count = PosterSampling.evictions(sizes, total, 9_000_000);
                    for (int i = 0; i < count; i++) files[i].delete();
                    posterBytes = 0; for (File f : files) posterBytes += f.length();
                }
            }
        }
    }
    private static String hash(String text) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(); for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return result.toString();
    }
    void close() { closed = true; listener = null; feedbackSync.close(); worker.shutdownNow(); publicWorker.shutdownNow(); images.shutdownNow(); downloads.shutdownNow(); bitmaps.evictAll(); }
}
