package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.function.Consumer;

/** A compact remote-friendly side panel; Back always returns to the underlying selection. */
final class CinemaMenu {
    static void show(Activity activity, String title, String subtitle, List<String> labels, Consumer<Integer> action) {
        float density = activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp = value -> Math.round(value * density);
        Dialog dialog = new Dialog(activity, R.style.Theme_TvHome_Dialog_Nocturne);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp.applyAsInt(22), dp.applyAsInt(22), dp.applyAsInt(22), dp.applyAsInt(16));
        panel.setBackground(HomeAppearance.surface(dp.applyAsInt(14), 0xFF131920, dp.applyAsInt(1), 0xFF303944));
        TextView heading = text(activity, title, 22, HomeAppearance.TEXT);
        heading.setFontVariationSettings("'wght' 550");
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (!subtitle.isEmpty()) {
            TextView caption = text(activity, subtitle, 12, HomeAppearance.MUTED);
            caption.setMaxLines(3);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp.applyAsInt(10);
            panel.addView(caption, lp);
        }
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true); scroll.setScrollbarFadingEnabled(false); scroll.setFocusable(false);
        LinearLayout rows = new LinearLayout(activity); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams body = new LinearLayout.LayoutParams(-1, 0, 1); body.topMargin = dp.applyAsInt(18);
        panel.addView(scroll, body);
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            TextView row = text(activity, labels.get(i), 15, HomeAppearance.TEXT);
            HomeAppearance.control(row, false); row.setSingleLine(false); row.setMaxLines(2);
            row.setFocusable(true); row.setFocusableInTouchMode(true); row.setClickable(true); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp.applyAsInt(12), dp.applyAsInt(6), dp.applyAsInt(12), dp.applyAsInt(6));
            rows.addView(row, new LinearLayout.LayoutParams(-1, dp.applyAsInt(44)));
            row.setOnClickListener(v -> { dialog.dismiss(); action.accept(index); });
        }
        TextView done = text(activity, activity.getString(R.string.done), 13, HomeAppearance.MUTED);
        HomeAppearance.control(done, false); done.setGravity(Gravity.CENTER);
        done.setFocusable(true); done.setFocusableInTouchMode(true); done.setClickable(true); done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(-1, dp.applyAsInt(34)); footer.topMargin = dp.applyAsInt(12);
        panel.addView(done, footer);
        dialog.setContentView(panel);
        dialog.setOnShowListener(ignored -> { if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus(); else done.requestFocus(); });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.48f);
        }
        dialog.show();
        if (window != null) {
            int maxHeight = activity.getResources().getDisplayMetrics().heightPixels - dp.applyAsInt(48);
            window.setLayout(dp.applyAsInt(352), Math.min(maxHeight, dp.applyAsInt(130 + (subtitle.isEmpty() ? 0 : 46) + labels.size() * 44)));
            window.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            WindowManager.LayoutParams attributes = window.getAttributes(); attributes.x = dp.applyAsInt(24); window.setAttributes(attributes);
        }
    }

    private static TextView text(Activity activity, String value, int size, int color) {
        TextView text = new TextView(activity); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(activity.getResources().getFont(R.font.outfit)); text.setFontVariationSettings("'wght' 450");
        text.setIncludeFontPadding(false); return text;
    }
    private CinemaMenu() { }
}
