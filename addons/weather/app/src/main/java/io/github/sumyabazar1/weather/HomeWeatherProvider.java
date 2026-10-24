package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Read-only, signature-protected local weather summary. Never exposes coordinates or preferences. */
public final class HomeWeatherProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!HomeWeatherStore.URI.equals(uri)) throw new IllegalArgumentException("Unknown weather path");
        HomeWeatherStore.refresh(getContext());
        SharedPreferences place = HomeWeatherStore.place(getContext()), cache = HomeWeatherStore.cache(getContext());
        MatrixCursor result = new MatrixCursor(new String[]{"city", "temp", "feels", "condition", "updated_at"}, 1);
        if (place.contains("name") && HomeWeatherStore.key(place).equals(cache.getString("place_key", ""))) {
            result.addRow(new Object[]{place.getString("name", ""), cache.getInt("temp", 0), cache.getInt("feels", 0),
                    getContext().getString(Forecast.sky(cache.getInt("code", 3), cache.getBoolean("day", true))),
                    cache.getLong("updated_at", 0)});
        }
        result.setNotificationUri(getContext().getContentResolver(), HomeWeatherStore.URI);
        return result;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.tvhome.weather"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException("Read only"); }
}
