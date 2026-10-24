package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads public, searchable TV rows with the ordinary runtime permission. Never writes TvProvider. */
final class TvCatalog {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final int REQUEST_PERMISSION = 2108;
    static final int NEED_PERMISSION = 1, UNAVAILABLE = 2;
    static final class Channel {
        final String key, label;
        Channel(String key, String label) { this.key = key; this.label = label; }
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private Runnable listener;
    private boolean loading, closed;
    private long lastRead;
    long syncedAt;
    int error;
    List<TvProgram> previews = new ArrayList<>(), next = new ArrayList<>();
    List<Channel> channels = new ArrayList<>();

    TvCatalog(Context context) {
        this.context = context.getApplicationContext();
        observer = new ContentObserver(main) { @Override public void onChange(boolean selfChange) { lastRead = 0; } };
        try { context.getContentResolver().registerContentObserver(Uri.parse("content://android.media.tv"), true, observer); }
        catch (Exception ignored) { }
    }
    void listen(Runnable listener) { this.listener = listener; }
    private void publish() { if (!closed && listener != null) listener.run(); }
    boolean permitted() { return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED; }
    boolean loading() { return loading; }
    List<TvProgram> items(boolean resume, String selected) {
        return resume ? TvProgram.resume(next, previews) : TvProgram.channels(previews, selected);
    }
    void refresh(boolean force) {
        if (closed || loading) return;
        if (!permitted()) {
            previews = new ArrayList<>(); next = new ArrayList<>(); channels = new ArrayList<>();
            error = NEED_PERMISSION; syncedAt = 0; publish(); return;
        }
        if (!force && System.currentTimeMillis() - lastRead < 60_000) return;
        loading = true; error = 0; publish();
        Set<String> hidden = new HashSet<>(context.getSharedPreferences("launcher_layout", 0)
                .getStringSet("hidden_apps", Collections.emptySet()));
        Collections.addAll(hidden, context.getResources().getStringArray(R.array.hidden_apps));
        worker.execute(() -> {
            try {
                Map<String, String> appNames = new HashMap<>(), channelNames = new HashMap<>();
                List<Channel> available = new ArrayList<>();
                List<JSONObject> rows = query("channel", new String[]{"_id", "package_name", "display_name", "type"}, null);
                for (JSONObject row : rows) {
                    String owner = row.optString("package_name");
                    String app = appName(owner, hidden, appNames);
                    if (app == null || !"TYPE_PREVIEW".equals(row.optString("type"))) continue;
                    String key = owner + "/" + row.optLong("_id"), name = row.optString("display_name");
                    channelNames.put(key, name); available.add(new Channel(key, app + " \u00B7 " + name));
                }
                String[] common = {"_id", "package_name", "title", "poster_art_uri", "intent_uri", "browsable",
                        "last_playback_position_millis", "duration_millis"};
                List<String> previewColumns = new ArrayList<>(); Collections.addAll(previewColumns, common);
                previewColumns.add("channel_id"); previewColumns.add("weight");
                List<String> nextColumns = new ArrayList<>(); Collections.addAll(nextColumns, common);
                nextColumns.add("last_engagement_time_utc_millis");
                List<TvProgram> programs = programs(query("preview_program", previewColumns.toArray(new String[0]), "weight DESC"),
                        "preview", hidden, appNames, channelNames);
                List<TvProgram> watchNext = programs(query("watch_next_program", nextColumns.toArray(new String[0]), "last_engagement_time_utc_millis DESC"),
                        "next", hidden, appNames, channelNames);
                Set<String> populated = new HashSet<>(); for (TvProgram item : programs) populated.add(item.channelKey);
                available.removeIf(channel -> !populated.contains(channel.key));
                available.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
                main.post(() -> {
                    if (closed) return;
                    previews = programs; next = watchNext; channels = available;
                    lastRead = syncedAt = System.currentTimeMillis(); loading = false; error = 0; publish();
                });
            } catch (Exception ignored) {
                main.post(() -> { if (!closed) { loading = false; error = UNAVAILABLE; lastRead = System.currentTimeMillis(); publish(); } });
            }
        });
    }
    private String appName(String owner, Set<String> hidden, Map<String, String> names) {
        if (hidden.contains(owner)) return null;
        if (!names.containsKey(owner)) {
            try {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo app = pm.getApplicationInfo(owner, 0);
                names.put(owner, app.enabled && pm.getLeanbackLaunchIntentForPackage(owner) != null ? pm.getApplicationLabel(app).toString() : null);
            } catch (Exception ignored) { names.put(owner, null); }
        }
        return names.get(owner);
    }
    private List<TvProgram> programs(List<JSONObject> rows, String table, Set<String> hidden, Map<String, String> apps, Map<String, String> names) {
        List<TvProgram> result = new ArrayList<>();
        for (JSONObject row : rows) {
            String owner = row.optString("package_name"), app = appName(owner, hidden, apps);
            if (app == null || row.optInt("browsable") != 1) continue;
            String channel = names.getOrDefault(owner + "/" + row.optLong("channel_id", -1), "");
            TvProgram item = new TvProgram(row, table, app, channel);
            if (item.valid()) result.add(item);
        }
        return result;
    }
    private List<JSONObject> query(String table, String[] columns, String order) throws Exception {
        List<JSONObject> rows = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(Uri.parse("content://android.media.tv/" + table), columns, null, null, order)) {
            if (cursor == null) throw new Exception();
            while (cursor.moveToNext() && rows.size() < 1500) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < cursor.getColumnCount(); i++) if (!cursor.isNull(i)) {
                    if (cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER) row.put(cursor.getColumnName(i), cursor.getLong(i));
                    else if (cursor.getType(i) == Cursor.FIELD_TYPE_STRING) row.put(cursor.getColumnName(i), cursor.getString(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }
    static Intent intent(TvProgram program) throws Exception {
        Intent intent = Intent.parseUri(program.intent, Intent.URI_INTENT_SCHEME);
        if (intent.getComponent() != null && !program.owner.equals(intent.getComponent().getPackageName())) throw new Exception();
        intent.setSelector(null); intent.setPackage(program.owner); intent.setClipData(null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.getAction() == null) intent.setAction(Intent.ACTION_VIEW);
        if (intent.getCategories() != null) for (String category : new HashSet<>(intent.getCategories())) intent.removeCategory(category);
        return intent;
    }
    void close() {
        closed = true; listener = null; worker.shutdownNow();
        try { context.getContentResolver().unregisterContentObserver(observer); } catch (Exception ignored) { }
    }
}
