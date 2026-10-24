package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Small D-pad friendly menus. Hiding an app changes only this launcher's presentation. */
final class LauncherMenus {
    private static final int BLUETOOTH_PERMISSION_REQUEST = 7001;
    private final Activity activity;
    private final LauncherPreferences preferences;
    private final AppAdapter adapter;
    private final Consumer<String> refresh;
    private final Runnable inputs;
    private final String vpnApp;
    private Runnable recommendations;

    void setRecommendations(Runnable recommendations) { this.recommendations = recommendations; }

    LauncherMenus(Activity activity, LauncherPreferences preferences, AppAdapter adapter,
                  Consumer<String> refresh, Runnable inputs, String vpnApp) {
        this.activity = activity;
        this.preferences = preferences;
        this.adapter = adapter;
        this.refresh = refresh;
        this.inputs = inputs;
        this.vpnApp = vpnApp;
    }

    private String text(int id) { return activity.getString(id); }

    private void list(String title, List<String> labels, Consumer<Integer> action) {
        new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> action.accept(which))
                .setPositiveButton(R.string.done, null)
                .show();
    }

    void showQuickMenu() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (recommendations != null) add(labels, actions, R.string.reco_menu, recommendations);
        add(labels, actions, R.string.edit_dock, this::showDockEditor);
        add(labels, actions, R.string.hidden_apps_menu, this::showHiddenApps);
        add(labels, actions, R.string.quick_network, () -> openFirst(
                tvIntent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_WIFI_SETTINGS)));
        add(labels, actions, R.string.quick_bluetooth, this::openBluetooth);
        add(labels, actions, R.string.quick_audio, () -> openFirst(
                tvIntent(Settings.ACTION_SOUND_SETTINGS), new Intent(Settings.ACTION_SOUND_SETTINGS)));
        add(labels, actions, R.string.quick_apps, () -> openFirst(
                tvIntent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        if (!vpnApp.isEmpty()) add(labels, actions, R.string.quick_vpn, () -> openFirst(
                activity.getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp),
                activity.getPackageManager().getLaunchIntentForPackage(vpnApp)));
        add(labels, actions, R.string.inputs, inputs);
        add(labels, actions, R.string.settings, () -> openFirst(new Intent(Settings.ACTION_SETTINGS)));
        list(text(R.string.quick_menu), labels, which -> actions.get(which).run());
    }

    void showDockEditor() {
        List<AppAdapter.Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String pkg : preferences.dock()) {
            AppAdapter.Entry entry = find(pkg);
            if (entry != null && !preferences.hidden().contains(pkg)) {
                entries.add(entry);
                labels.add((entries.size()) + "  ·  " + entry.label);
            }
        }
        labels.add(text(R.string.add_to_dock));
        labels.add(text(R.string.hidden_apps_menu));
        String title = activity.getString(R.string.dock_editor_title, entries.size(), preferences.capacity);
        list(title, labels, which -> {
            if (which < entries.size()) showAppActions(entries.get(which), true);
            else if (which == entries.size()) showAddApps();
            else showHiddenApps();
        });
    }

    private AppAdapter.Entry find(String pkg) {
        for (AppAdapter.Entry entry : adapter.allApps()) if (entry.pkg.equals(pkg)) return entry;
        return null;
    }

    private void showAddApps() {
        List<AppAdapter.Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> dock = preferences.dock();
        Set<String> hidden = preferences.hidden();
        for (AppAdapter.Entry entry : adapter.allApps()) {
            if (dock.contains(entry.pkg)) continue;
            entries.add(entry);
            labels.add(entry.label + (hidden.contains(entry.pkg) ? " · " + text(R.string.hidden_state) : ""));
        }
        if (entries.isEmpty()) {
            Toast.makeText(activity, R.string.no_more_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        list(text(R.string.add_to_dock), labels, which -> {
            AppAdapter.Entry entry = entries.get(which);
            if (pin(entry)) showDockEditor();
        });
    }

    void showHiddenApps() {
        List<AppAdapter.Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Set<String> hidden = preferences.hidden();
        for (AppAdapter.Entry entry : adapter.allApps()) {
            if (!hidden.contains(entry.pkg)) continue;
            entries.add(entry);
            labels.add(entry.label);
        }
        if (entries.isEmpty()) {
            new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog)
                    .setTitle(R.string.hidden_apps_menu).setMessage(R.string.no_hidden_apps)
                    .setPositiveButton(R.string.done, null).show();
            return;
        }
        list(text(R.string.hidden_apps_menu), labels, which -> showAppActions(entries.get(which), false));
    }

    void showAppActions(AppAdapter.Entry entry, boolean returnToEditor) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<String> dock = preferences.dock();
        int position = dock.indexOf(entry.pkg);
        if (position > 0) add(labels, actions, R.string.move_left, () -> changed(entry, () -> preferences.move(entry.pkg, -1), returnToEditor));
        if (position >= 0 && position < dock.size() - 1)
            add(labels, actions, R.string.move_right, () -> changed(entry, () -> preferences.move(entry.pkg, 1), returnToEditor));
        if (position >= 0) add(labels, actions, R.string.remove_from_dock, () -> changed(entry, () -> preferences.unpin(entry.pkg), returnToEditor));
        else add(labels, actions, R.string.add_to_dock, () -> { if (pin(entry) && returnToEditor) showDockEditor(); });
        boolean hidden = preferences.hidden().contains(entry.pkg);
        add(labels, actions, hidden ? R.string.show_app : R.string.hide_app,
                () -> changed(entry, () -> preferences.setHidden(entry.pkg, !hidden), returnToEditor));
        add(labels, actions, R.string.app_info, () -> openFirst(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + entry.pkg))));
        list(entry.label, labels, which -> actions.get(which).run());
    }

    private boolean pin(AppAdapter.Entry entry) {
        if (!preferences.pin(entry.pkg)) {
            new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog)
                    .setTitle(R.string.dock_full_title)
                    .setMessage(activity.getString(R.string.dock_full_message, preferences.capacity))
                    .setPositiveButton(R.string.edit_dock, (dialog, which) -> showDockEditor())
                    .setNegativeButton(R.string.cancel, null).show();
            return false;
        }
        refresh.accept(entry.component.flattenToShortString());
        return true;
    }

    private void changed(AppAdapter.Entry entry, Runnable change, boolean returnToEditor) {
        change.run();
        refresh.accept(entry.component.flattenToShortString());
        if (returnToEditor) showDockEditor();
    }

    private void add(List<String> labels, List<Runnable> actions, int label, Runnable action) {
        labels.add(text(label));
        actions.add(action);
    }

    private Intent tvIntent(String action) { return new Intent(action).setPackage("com.android.tv.settings"); }

    private void openBluetooth() {
        if (Build.VERSION.SDK_INT >= 31
                && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION_REQUEST);
            return;
        }
        openFirst(tvIntent("com.google.android.intent.action.CONNECT_INPUT"), new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    void onPermissionsResult(int requestCode, int[] results) {
        if (requestCode != BLUETOOTH_PERMISSION_REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openBluetooth();
        else Toast.makeText(activity, R.string.bluetooth_permission_needed, Toast.LENGTH_LONG).show();
    }

    private void openFirst(Intent... choices) {
        for (Intent intent : choices) {
            if (intent == null) continue;
            try {
                activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
    }
}
