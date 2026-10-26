package com.cleanyourtv.samsungvolume;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small remote-friendly status/control screen; never displays private pairing data. */
public final class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView status;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean enabled = getSharedPreferences("samsung_volume", MODE_PRIVATE).getBoolean("enabled", false);
            status.setText(!enabled ? "Relais en pause" : !SamsungRemote.get(MainActivity.this).ready()
                ? "Connexion à votre téléviseur…" : !LocalAdbVolumeKeys.get(MainActivity.this).ready()
                ? "Télé connectée · autorisez le débogage local sur la box"
                : "Connecté · seul le volume Samsung est commandé");
            handler.postDelayed(this, 1500);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(72, 35, 72, 30);
        layout.setBackgroundColor(Color.rgb(14, 23, 40));
        TextView title = new TextView(this);
        title.setText("Le volume de votre télé, avec la télécommande onn");
        title.setTextSize(26); title.setTextColor(Color.WHITE);
        layout.addView(title);
        status = new TextView(this); status.setTextSize(19); status.setTextColor(Color.rgb(130, 183, 255));
        layout.addView(status);
        TextView details = new TextView(this);
        details.setText("Les touches +, − et Muet règlent directement la Samsung.\nLa box et la télé doivent être sur le même réseau.\nL’allumage conserve le réglage HDMI-CEC existant.");
        details.setTextSize(17); details.setTextColor(Color.LTGRAY); details.setPadding(0, 14, 0, 14);
        layout.addView(details);
        LinearLayout tests = new LinearLayout(this);
        addButton(tests, "Volume −", v -> SamsungRemote.get(this).key("KEY_VOLDOWN"));
        addButton(tests, "Volume +", v -> SamsungRemote.get(this).key("KEY_VOLUP"));
        addButton(tests, "Muet", v -> SamsungRemote.get(this).key("KEY_MUTE"));
        layout.addView(tests);
        addButton(layout, "Réglages d’accessibilité", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(layout, "Activer / mettre en pause le relais", v -> {
            boolean enabled = getSharedPreferences("samsung_volume", MODE_PRIVATE).getBoolean("enabled", false);
            getSharedPreferences("samsung_volume", MODE_PRIVATE).edit().putBoolean("enabled", !enabled).apply();
            if (enabled) SamsungRemote.get(this).disconnect(); else SamsungRemote.get(this).connect();
        });
        setContentView(layout);
        if (getSharedPreferences("samsung_volume", MODE_PRIVATE).getBoolean("enabled", false)) SamsungRemote.get(this).connect();
    }
    private void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(listener);
        parent.addView(button);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
