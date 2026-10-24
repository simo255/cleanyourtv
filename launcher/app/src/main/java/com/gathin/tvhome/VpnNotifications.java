package com.gathin.tvhome;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Opt-in local status from the configured VPN app. No notification bodies, IP lookups, disk writes or account data. */
public final class VpnNotifications extends NotificationListenerService {
    private String vpnPackage = "";
    private static final Set<Runnable> observers = new HashSet<>();
    private static VpnNotifications connected;
    private static String country = "";

    static void configure(Context context, boolean enabled) {
        ComponentName component = new ComponentName(context, VpnNotifications.class);
        PackageManager pm = context.getPackageManager();
        int setting = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(component) != setting) {
            pm.setComponentEnabledSetting(component, setting, PackageManager.DONT_KILL_APP);
        }
        if (!enabled) publish("");
    }

    static void watch(Runnable observer) {
        observers.add(observer);
        if (connected != null) {
            connected.vpnPackage = Config.load(connected).vpnApp;
            connected.refresh();
        }
    }

    static void unwatch(Runnable observer) { observers.remove(observer); }
    static String country() { return country; }

    @Override public void onListenerConnected() {
        vpnPackage = Config.load(this).vpnApp;
        connected = this;
        refresh();
    }

    @Override public void onListenerDisconnected() {
        if (connected == this) connected = null;
        publish("");
    }

    @Override public void onDestroy() {
        if (connected == this) { connected = null; publish(""); }
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (vpnPackage.equals(notification.getPackageName())) refresh();
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (vpnPackage.equals(notification.getPackageName())) refresh();
    }

    private void refresh() {
        String next = "";
        long newest = -1;
        try {
            StatusBarNotification[] notifications = getActiveNotifications();
            if (notifications != null) for (StatusBarNotification entry : notifications) {
                // Skip every other app before inspecting its notification. Marketing is never a VPN status.
                if (!vpnPackage.equals(entry.getPackageName()) || !entry.isOngoing()) continue;
                Notification notification = entry.getNotification();
                CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
                String value = VpnCountry.parse(title == null ? null : title.toString(), Locale.getDefault());
                if (entry.getPostTime() >= newest) { next = value; newest = entry.getPostTime(); }
            }
        } catch (SecurityException ignored) {
            // Revoked access removes the location immediately; Android VPN detection still works.
        }
        publish(next);
    }

    private static void publish(String next) {
        if (country.equals(next)) return;
        country = next;
        for (Runnable observer : new ArrayList<>(observers)) observer.run();
    }
}
