package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Typography-only title details; all artwork stays in the poster strip below. */
final class CinemaPreview extends LinearLayout {
    private final TextView title, meta, summary;
    private boolean animate;
    private String selected = "";

    void motion(boolean enabled, boolean reduced) { animate = enabled && !reduced; }

    CinemaPreview(Context context) {
        super(context);
        setOrientation(VERTICAL); setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(3), dp(4), dp(12), dp(4));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        title = label(31, HomeAppearance.TEXT); title.setMaxLines(1);
        title.setFontVariationSettings("'wght' 550"); title.setLetterSpacing(-0.02f);
        title.setAutoSizeTextTypeUniformWithConfiguration(24, 31, 1, TypedValue.COMPLEX_UNIT_SP);
        LayoutParams heading = new LayoutParams(-1, dp(38)); heading.topMargin = dp(3); heading.bottomMargin = dp(3);
        addView(title, heading);
        meta = label(13, HomeAppearance.MUTED); meta.setMaxLines(1);
        addView(meta, new LayoutParams(-1, -2));
        summary = label(14, HomeAppearance.MUTED); summary.setMaxLines(2); summary.setMinLines(2);
        summary.setLineSpacing(dp(2), 1f);
        LayoutParams body = new LayoutParams(-1, -2); body.topMargin = dp(6);
        addView(summary, body);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(int size, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false);
        view.setTypeface(getResources().getFont(R.font.outfit), Typeface.NORMAL);
        view.setFontVariationSettings("'wght' 450");
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    void show(CatalogItem item, String detail) {
        String key = item.type + ":" + item.id;
        boolean changed = !selected.equals(key);
        selected = key;
        if (!item.name.contentEquals(title.getText())) title.setText(item.name);
        String facts = detail;
        if (!item.rating.isEmpty()) facts += (facts.isEmpty() ? "" : "   ·   ") + "★ " + item.rating;
        String genres = item.genreLabel();
        if (!genres.isEmpty()) facts += (facts.isEmpty() ? "" : "   ·   ") + genres;
        if (!facts.contentEquals(meta.getText())) meta.setText(facts);
        meta.setVisibility(facts.isEmpty() ? INVISIBLE : VISIBLE);
        if (!item.description.contentEquals(summary.getText())) summary.setText(item.description);
        summary.setVisibility(item.description.isEmpty() ? INVISIBLE : VISIBLE);
        setVisibility(VISIBLE);
        if (changed) Motion.enter(this, dp(4), animate);
    }

    void clear() { Motion.reset(this); selected = ""; setVisibility(INVISIBLE); }

    /** A small text shadow complements the apps-first page's soft wallpaper gradient. */
    void wallpaperContrast() {
        for (TextView text : new TextView[]{title, meta, summary})
            text.setShadowLayer(dp(2), 0, dp(1), 0xE6000000);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int lines = height >= dp(160) ? 3 : 2;
        if (summary.getMaxLines() != lines) summary.setMaxLines(lines);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

}
