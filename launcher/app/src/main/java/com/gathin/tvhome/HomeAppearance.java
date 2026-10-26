package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Optional Nocturne presentation; no changes to accounts, content or wallpaper settings. */
final class HomeAppearance {
    static final int INK = 0xFF090C10, SURFACE = 0xFF181E25, TEXT = 0xFFF6F2EA;
    static final int MUTED = 0xFFADB5BF, ACCENT = 0xFF76B7FF;

    static GradientDrawable surface(float radius, int color, int stroke, int edge) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(radius); drawable.setColor(color);
        if (stroke > 0) drawable.setStroke(stroke, edge);
        return drawable;
    }

    static void control(TextView view, boolean navigation) {
        float d = view.getResources().getDisplayMetrics().density;
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, surface(6 * d, TEXT, 0, 0));
        if (navigation) {
            LayerDrawable selected = new LayerDrawable(new android.graphics.drawable.Drawable[]{
                    surface(0, 0, 0, 0), surface(d, ACCENT, 0, 0)});
            selected.setLayerGravity(1, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            selected.setLayerHeight(1, Math.round(2 * d));
            selected.setLayerInset(1, Math.round(12 * d), 0, Math.round(12 * d), 0);
            background.addState(new int[]{android.R.attr.state_selected}, selected);
        } else background.addState(new int[]{android.R.attr.state_selected}, surface(6 * d, SURFACE, 1, 0xFF46515D));
        background.addState(new int[]{}, surface(6 * d, 0, 0, 0));
        view.setBackground(background);
        ColorStateList colors = new ColorStateList(new int[][]{
                {android.R.attr.state_focused}, {android.R.attr.state_selected}, {}},
                new int[]{INK, navigation ? ACCENT : TEXT, MUTED});
        view.setTextColor(colors); view.setCompoundDrawableTintList(colors);
        view.setSingleLine(true); view.setIncludeFontPadding(false);
        view.setLetterSpacing(0);
    }

    /** Compact circular header controls; a VPN country label keeps its readable pill. */
    static void roundHeader(Activity activity) {
        float d = activity.getResources().getDisplayMetrics().density;
        for (int id : new int[]{R.id.btnWallpaper, R.id.btnInputs, R.id.btnSettings, R.id.btnReorder, R.id.btnLibraryTop}) {
            TextView button = activity.findViewById(id);
            if (button.getText().length() > 0) button.setContentDescription(button.getText());
            button.setText("");
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(0); button.setMinimumWidth(0);
            button.setCompoundDrawablePadding(0);
            android.graphics.drawable.Drawable icon = button.getCompoundDrawablesRelative()[0];
            if (icon != null) {
                icon.setBounds(0, 0, Math.round(20 * d), Math.round(20 * d));
                button.setCompoundDrawablesRelative(icon, null, null, null);
            }
            button.setPaddingRelative(Math.round(8 * d), 0, Math.round(8 * d), 0);
            android.view.ViewGroup.LayoutParams params = button.getLayoutParams();
            params.width = params.height = Math.round(36 * d);
            button.setLayoutParams(params);
            StateListDrawable background = new StateListDrawable();
            background.addState(new int[]{android.R.attr.state_focused}, surface(18 * d, TEXT, Math.round(d), TEXT));
            background.addState(new int[]{android.R.attr.state_pressed}, surface(18 * d, 0xAA46515D, Math.round(d), MUTED));
            background.addState(new int[]{android.R.attr.state_selected}, surface(18 * d, 0x6646515D, Math.round(d), 0x66FFFFFF));
            background.addState(new int[]{}, surface(18 * d, 0x443D4853, Math.round(d), 0x38FFFFFF));
            button.setBackground(background);
        }
    }

    static View apply(Activity activity, int backgroundColor) {
        float d = activity.getResources().getDisplayMetrics().density;
        TextView clock = activity.findViewById(R.id.clock);
        clock.setTextSize(30); clock.setFontVariationSettings("'wght' 500");
        clock.setTextColor(TEXT);
        TextView greeting = activity.findViewById(R.id.greeting);
        greeting.setVisibility(View.GONE);
        TextView date = activity.findViewById(R.id.date);
        date.setTextSize(15); date.setLetterSpacing(0.02f); date.setTextColor(MUTED);
        date.setAllCaps(false);
        TextView weather = activity.findViewById(R.id.homeWeather);
        weather.setTextSize(14);
        weather.setTextColor(MUTED);
        weather.setBackground(null);
        weather.setSingleLine(false);
        weather.setMaxLines(3);
        weather.setFocusable(false); weather.setClickable(false);
        weather.setPadding(0, Math.round(4 * d), 0, 0);
        for (int id : new int[]{R.id.btnWallpaper, R.id.btnInputs, R.id.btnWifi, R.id.btnSettings, R.id.btnReorder, R.id.btnLibraryTop}) {
            TextView button = activity.findViewById(id);
            control(button, false);
            button.setTextSize(13);
            button.getLayoutParams().height = Math.round(32 * d);
            button.setPadding(Math.round(9 * d), 0, Math.round(9 * d), 0);
            if (id == R.id.btnInputs || id == R.id.btnSettings) {
                button.setContentDescription(button.getText()); button.setText("");
                button.setCompoundDrawablePadding(0);
            }
        }
        View shelf = activity.findViewById(R.id.recommendationShelf);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) shelf.getLayoutParams();
        params.topMargin = Math.round(70 * d);
        shelf.setLayoutParams(params);
        // Weather and translated dates may grow the header. Never let the catalog cover them.
        View block = activity.findViewById(R.id.leftBlock);
        block.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int[] headerPosition = new int[2], rootPosition = new int[2];
            block.getLocationOnScreen(headerPosition);
            activity.findViewById(R.id.root).getLocationOnScreen(rootPosition);
            int margin = Math.max(Math.round(70 * d), headerPosition[1] - rootPosition[1] + block.getHeight() + Math.round(12 * d));
            // Weather can disappear during this layout pass. Resize the shelf afterwards,
            // so its MATCH_PARENT children are measured with the new margin before drawing.
            shelf.post(() -> {
                FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) shelf.getLayoutParams();
                if (layout.topMargin != margin) { layout.topMargin = margin; shelf.setLayoutParams(layout); }
            });
        });
        // The full app grid is reached with Down. Do not leave half a row peeking below Home.
        TextView bottomMask = new TextView(activity);
        bottomMask.setText(R.string.cinema_library_hint);
        bottomMask.setTextColor(MUTED); bottomMask.setTextSize(10);
        bottomMask.setTypeface(activity.getResources().getFont(R.font.outfit));
        bottomMask.setFontVariationSettings("'wght' 450");
        bottomMask.setGravity(Gravity.RIGHT | Gravity.TOP);
        bottomMask.setPadding(0, 0, Math.round(58 * d), 0);
        bottomMask.setBackgroundColor(backgroundColor);
        bottomMask.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ((FrameLayout) activity.findViewById(R.id.root)).addView(bottomMask,
                new FrameLayout.LayoutParams(-1, Math.round(30 * d), Gravity.BOTTOM));
        return bottomMask;
    }

    private HomeAppearance() { }
}
