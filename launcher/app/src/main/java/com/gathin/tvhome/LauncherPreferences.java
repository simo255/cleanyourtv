package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** User choices stay local and survive launcher updates and device restarts. */
final class LauncherPreferences {
    private final SharedPreferences preferences;
    private final List<String> defaults;
    final int capacity;

    LauncherPreferences(Context context, int capacity, List<String> dockDefaults) {
        this.capacity = capacity;
        defaults = new ArrayList<>(dockDefaults);
        preferences = context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE);
    }

    List<String> dock() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(preferences.getString("dock_order", new JSONArray(defaults).toString()));
            for (int i = 0; i < stored.length() && result.size() < capacity; i++) {
                String pkg = stored.optString(i, "");
                if (!pkg.isEmpty() && !result.contains(pkg)) result.add(pkg);
            }
        } catch (JSONException e) {
            result.addAll(defaults.subList(0, Math.min(capacity, defaults.size())));
        }
        return result;
    }

    Set<String> hidden() {
        return new HashSet<>(preferences.getStringSet("hidden_apps", Collections.emptySet()));
    }

    private void save(List<String> dock, Set<String> hidden) {
        preferences.edit().putString("dock_order", new JSONArray(dock).toString())
                .putStringSet("hidden_apps", hidden).apply();
    }

    boolean pin(String pkg) {
        List<String> dock = dock();
        if (!dock.contains(pkg) && dock.size() >= capacity) return false;
        if (!dock.contains(pkg)) dock.add(pkg);
        Set<String> hidden = hidden();
        hidden.remove(pkg);
        save(dock, hidden);
        return true;
    }

    void unpin(String pkg) {
        List<String> dock = dock();
        dock.remove(pkg);
        save(dock, hidden());
    }

    /** Share dock edits made by upstream's reorder mode with the existing fork editor. */
    void replaceDock(List<String> packages) {
        List<String> dock = new ArrayList<>();
        Set<String> hidden = hidden();
        for (String pkg : packages) {
            if (dock.size() >= capacity) break;
            if (pkg != null && !pkg.isEmpty() && !dock.contains(pkg)) {
                dock.add(pkg);
                hidden.remove(pkg);
            }
        }
        save(dock, hidden);
    }

    void move(String pkg, int direction) {
        List<String> dock = dock();
        int from = dock.indexOf(pkg), to = from + direction;
        if (from >= 0 && to >= 0 && to < dock.size()) {
            Collections.swap(dock, from, to);
            save(dock, hidden());
        }
    }

    void setHidden(String pkg, boolean hide) {
        Set<String> hidden = hidden();
        List<String> dock = dock();
        if (hide) {
            hidden.add(pkg);
            dock.remove(pkg);
        } else hidden.remove(pkg);
        save(dock, hidden);
    }
}
