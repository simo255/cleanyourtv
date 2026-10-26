package com.cleanyourtv.samsungvolume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Provisioning is restricted in the manifest to the privileged ADB settings caller. */
public final class SetupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("com.cleanyourtv.samsungvolume.MODE".equals(intent.getAction())) {
            context.getSharedPreferences("samsung_volume", Context.MODE_PRIVATE).edit()
                .putBoolean("volume_fallback", intent.getBooleanExtra("volume_fallback", false))
                .putBoolean("raw_monitor", intent.getBooleanExtra("raw_monitor", true)).apply();
            return;
        }
        if (!"com.cleanyourtv.samsungvolume.CONFIGURE".equals(intent.getAction())) return;
        String host = intent.getStringExtra("host");
        String pin = intent.getStringExtra("cert_pin");
        String token = intent.getStringExtra("token");
        if (host == null || pin == null) return;
        SamsungRemote.get(context).configure(host, pin, token == null ? "" : token);
        context.getSharedPreferences("samsung_volume", Context.MODE_PRIVATE).edit()
            .putBoolean("volume_fallback", false).putBoolean("raw_monitor", true).putBoolean("enabled", true).apply();
        SamsungRemote.get(context).connect();
    }
}
