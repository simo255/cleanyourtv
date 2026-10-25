package io.github.simo255.mediastandby;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 64);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(Color.rgb(12, 17, 28));
        TextView title = new TextView(this);
        title.setText(R.string.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        page.addView(title);
        TextView explanation = new TextView(this);
        explanation.setText(R.string.explanation);
        explanation.setTextColor(Color.rgb(181, 192, 211));
        explanation.setTextSize(20);
        explanation.setPadding(0, 24, 0, 32);
        page.addView(explanation);
        Button settings = new Button(this);
        settings.setText(R.string.settings);
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch (ActivityNotFoundException ignored) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        page.addView(settings, new LinearLayout.LayoutParams(-2, -2));
        setContentView(page);
        settings.requestFocus();
    }
}
