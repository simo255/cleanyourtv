package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Trakt, public discovery and app-published TV channels share one remote-friendly shelf. */
final class RecommendationsShelf {
    private static final int TV = CatalogSource.TV, GOOGLE = CatalogSource.GOOGLE;
    private final Activity activity;
    private final FrameLayout host;
    private final RecommendationRepository repository;
    private final TvCatalog tv;
    private final Map<String, TvProgram> tvPrograms = new HashMap<>();
    private final Runnable focusDock;
    private final TextView status, movieTab, showTab, refreshTab;
    private final TextView[] sources = new TextView[CatalogSource.COUNT];
    private final List<TextView> controls = new ArrayList<>();
    private final RecyclerView list;
    private final Posters adapter = new Posters();
    private final Typeface font;
    private boolean showSeries, dockVisible = true, enabled;
    private boolean fitsAboveDock = true;
    private int source, pendingFocus = -1;
    private String syncStatus = "";
    private boolean catalogReady;
    private boolean catalogEmpty, tvResume, googleSeries;
    private String tvChannel;
    private List<CatalogItem> movies = new ArrayList<>(), shows = new ArrayList<>();
    private final int[] sourceNames = {R.string.reco_for_you, R.string.reco_watchlist, R.string.reco_trending, R.string.reco_tv, R.string.reco_google, R.string.reco_recent};

    RecommendationsShelf(Activity activity, FrameLayout host, Runnable focusDock) {
        this.activity = activity; this.host = host; this.focusDock = focusDock;
        font = activity.getResources().getFont(R.font.outfit);
        repository = new RecommendationRepository(activity);
        tv = new TvCatalog(activity);
        enabled = activity.getSharedPreferences("recommendations", 0).getBoolean("enabled", true);
        showSeries = activity.getSharedPreferences("recommendations", 0).getBoolean("series", false);
        googleSeries = activity.getSharedPreferences("recommendations", 0).getBoolean("google_series", false);
        tvResume = activity.getSharedPreferences("recommendations", 0).getBoolean("tv_resume", false);
        tvChannel = activity.getSharedPreferences("recommendations", 0).getString("tv_channel", "");
        source = CatalogSource.restore(activity.getSharedPreferences("recommendations", 0).getInt("source", 0));
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        host.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        int[] sourceIds = {R.id.recommendation_for_you, R.id.recommendation_watchlist, R.id.recommendation_trending, R.id.recommendation_tv, R.id.recommendation_google, R.id.recommendation_recent};
        for (int i : CatalogSource.displayOrder()) {
            final int index = i;
            sources[i] = button(sourceNames[i]); sources[i].setId(sourceIds[i]);
            sources[i].setOnClickListener(v -> source(index, true));
            header.addView(sources[i]);
        }
        header.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        movieTab = button(R.string.reco_movies); showTab = button(R.string.reco_shows);
        movieTab.setId(R.id.recommendation_movies); showTab.setId(R.id.recommendation_shows);
        movieTab.setOnClickListener(v -> { if (source == TV) channelMenu(); else category(false); });
        showTab.setOnClickListener(v -> category(true));
        header.addView(movieTab); header.addView(showTab);
        refreshTab = button(R.string.reco_refresh); refreshTab.setId(R.id.recommendation_refresh);
        refreshTab.setOnClickListener(v -> refresh(true, true)); header.addView(refreshTab);
        HorizontalScrollView headerScroll = new HorizontalScrollView(activity);
        headerScroll.setFillViewport(true); headerScroll.setHorizontalScrollBarEnabled(false);
        headerScroll.setFocusable(false);
        headerScroll.addView(header, new ViewGroup.LayoutParams(-2, -2));
        panel.addView(headerScroll, new LinearLayout.LayoutParams(-1, -2));
        status = text("", 11, 0xFFA6ADB8); status.setId(R.id.recommendation_status);
        panel.addView(status, new LinearLayout.LayoutParams(-1, dp(23)));
        list = new RecyclerView(activity); list.setId(R.id.recommendation_list);
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        list.setItemAnimator(null); list.setClipToPadding(false); list.setClipChildren(false);
        list.setPadding(dp(4), dp(6), dp(8), dp(5)); list.setAdapter(adapter);
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View view) {
                if (list.getChildAdapterPosition(view) == pendingFocus) list.post(RecommendationsShelf.this::focusPending);
            }
            @Override public void onChildViewDetachedFromWindow(@NonNull View view) { }
        });
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        repository.listen(changedSource -> { if (changedSource == source) render(); });
        tv.listen(() -> { if (source == TV) render(); });
        source(source, false);
    }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setTypeface(font);
        if (Build.VERSION.SDK_INT >= 28) view.setFontVariationSettings("'wght' 450");
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private TextView button(int label) {
        TextView view = text(activity.getString(label), 12, Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(32)); params.rightMargin = dp(8);
        view.setLayoutParams(params); view.setPadding(dp(12), 0, dp(12), 0);
        view.setFocusable(true); view.setClickable(true); view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.pill_bg); view.setTextColor(buttonColors(Color.WHITE));
        controls.add(view);
        view.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (adapter.getItemCount() == 0) focusDock.run(); else focusCard(0);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) { activity.findViewById(R.id.btnInputs).requestFocus(); return true; }
            int index = controls.indexOf(v);
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) { controls.get(Math.max(0, index - 1)).requestFocus(); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { controls.get(Math.min(controls.size() - 1, index + 1)).requestFocus(); return true; }
            return false;
        });
        return view;
    }
    private void source(int selected, boolean refresh) {
        source = selected; pendingFocus = -1;
        activity.getSharedPreferences("recommendations", 0).edit().putInt("source", source).apply();
        for (int i : CatalogSource.displayOrder()) sources[i].setTextColor(buttonColors(i == source ? 0xFF9CB7FF : 0xFFA6ADB8));
        paintTabs(); render(); list.scrollToPosition(0);
        if (refresh) refresh(false, true);
    }
    private void category(boolean series) {
        pendingFocus = -1;
        if (source == TV) {
            tvResume = series;
            activity.getSharedPreferences("recommendations", 0).edit().putBoolean("tv_resume", series).apply();
        } else if (source == GOOGLE) {
            googleSeries = series;
            activity.getSharedPreferences("recommendations", 0).edit().putBoolean("google_series", series).apply();
        } else {
            showSeries = series;
            activity.getSharedPreferences("recommendations", 0).edit().putBoolean("series", series).apply();
        }
        paintTabs();
        render(); list.scrollToPosition(0);
    }
    private boolean secondary() { return source == TV ? tvResume : source == GOOGLE ? googleSeries : showSeries; }
    private void paintTabs() {
        movieTab.setText(source == TV ? R.string.reco_tv_channels : R.string.reco_movies);
        showTab.setText(source == TV ? R.string.reco_tv_resume : R.string.reco_shows);
        refreshTab.setText(source == GOOGLE ? R.string.reco_google_reload : R.string.reco_refresh);
        movieTab.setTextColor(buttonColors(secondary() ? 0xFFA6ADB8 : 0xFF9CB7FF));
        showTab.setTextColor(buttonColors(secondary() ? 0xFF9CB7FF : 0xFFA6ADB8));
    }
    private void refresh(boolean force, boolean interactive) {
        if (source != TV) { repository.refresh(source, force); return; }
        if (!tv.permitted() && interactive) activity.requestPermissions(new String[]{TvCatalog.PERMISSION}, TvCatalog.REQUEST_PERMISSION);
        tv.refresh(force);
    }
    private void channelMenu() {
        if (!tv.permitted()) { refresh(true, true); return; }
        List<TvCatalog.Channel> available = new ArrayList<>(tv.channels);
        String[] labels = new String[available.size() + 1]; labels[0] = activity.getString(R.string.reco_tv_all);
        int selected = 0;
        for (int i = 0; i < available.size(); i++) {
            labels[i + 1] = available.get(i).label;
            if (available.get(i).key.equals(tvChannel)) selected = i + 1;
        }
        new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog).setTitle(R.string.reco_tv_channels)
                .setSingleChoiceItems(labels, selected, (dialog, choice) -> {
                    tvChannel = choice == 0 ? "" : available.get(choice - 1).key;
                    activity.getSharedPreferences("recommendations", 0).edit().putString("tv_channel", tvChannel).apply();
                    category(false); dialog.dismiss(); focusCard(0);
                }).setNegativeButton(R.string.done, null).show();
    }
    private void render() {
        if (source == TV) { renderTv(); return; }
        if (source == GOOGLE) { renderGoogle(); return; }
        RecommendationRepository.Feed feed = repository.feed(source);
        movies = feed.movies;
        shows = feed.shows;
        String provider = activity.getString(source == CatalogSource.RECENT ? (RecommendationRepository.hasTmdb(feed) ? R.string.reco_provider_recent_tmdb : R.string.reco_provider_recent) : source == 0 ? R.string.reco_provider_for_you
                : source == 1 ? R.string.reco_provider_watchlist : R.string.reco_provider_trending);
        if (repository.learned() && (source == CatalogSource.TRAKT || source == CatalogSource.RECENT)) provider += " · " + activity.getString(R.string.reco_taste_label);
        if (!CatalogSource.publicFeed(source) && !repository.configured()) syncStatus = activity.getString(R.string.reco_setup);
        else if (feed.loading) syncStatus = activity.getString(R.string.reco_feed_loading, provider);
        else if (feed.error != 0) syncStatus = activity.getString(feed.syncedAt > 0 ? R.string.reco_feed_cached : R.string.reco_feed_error, provider);
        else if (feed.syncedAt > 0) syncStatus = activity.getString(R.string.reco_feed_synced, provider,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(feed.syncedAt)));
        else syncStatus = provider;
        catalogReady = feed.syncedAt > 0 && feed.error == 0 && !feed.loading;
        catalogEmpty = (showSeries ? shows : movies).isEmpty();
        if (!list.hasFocus()) updateStatus();
        adapter.replace(showSeries ? shows : movies); updateVisibility();
    }
    private void renderGoogle() {
        movies = repository.googleItems(false); shows = repository.googleItems(true);
        if (repository.googleLoading()) syncStatus = activity.getString(R.string.reco_feed_loading, activity.getString(R.string.reco_google));
        else if (!repository.hasGoogle()) syncStatus = activity.getString(R.string.reco_google_setup);
        else syncStatus = activity.getString(R.string.reco_google_status,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(repository.googleCapturedAt())));
        catalogReady = repository.hasGoogle() && !repository.googleLoading();
        catalogEmpty = (googleSeries ? shows : movies).isEmpty();
        if (!list.hasFocus()) updateStatus();
        adapter.replace(googleSeries ? shows : movies); updateVisibility();
    }
    private void renderTv() {
        List<CatalogItem> cards = new ArrayList<>(); tvPrograms.clear();
        for (TvProgram item : tv.items(tvResume, tvChannel)) {
            String detail = item.app + (item.channel.isEmpty() ? "" : " \u00B7 " + item.channel);
            if (tvResume && item.percent() >= 0) detail += " \u00B7 " + item.percent() + "%";
            try {
                cards.add(new CatalogItem(new JSONObject().put("id", item.key).put("type", "tv").put("name", item.name)
                        .put("year", detail).put("poster", item.poster)));
                tvPrograms.put(item.key, item);
            } catch (Exception ignored) { }
        }
        String provider = activity.getString(R.string.reco_tv_provider);
        if (!tv.permitted()) syncStatus = activity.getString(R.string.reco_tv_permission);
        else if (tv.loading()) syncStatus = activity.getString(R.string.reco_feed_loading, provider);
        else if (tv.error != 0) syncStatus = activity.getString(tv.syncedAt > 0 ? R.string.reco_feed_cached : R.string.reco_feed_error, provider);
        else syncStatus = activity.getString(R.string.reco_tv_status, provider, cards.size());
        catalogReady = tv.syncedAt > 0 && tv.error == 0 && !tv.loading(); catalogEmpty = cards.isEmpty();
        if (!list.hasFocus()) updateStatus();
        adapter.replace(cards); updateVisibility();
    }
    private void updateStatus() {
        status.setText(catalogReady && catalogEmpty
                ? activity.getString(source == TV ? R.string.reco_tv_empty : source == GOOGLE ? R.string.reco_google_empty
                    : source == 1 ? R.string.reco_watchlist_empty : R.string.reco_empty) : syncStatus);
    }
    boolean visible() { return (repository.configured() || source >= 2) && enabled && dockVisible && fitsAboveDock; }
    void fitAboveDock(float dockTop) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) host.getLayoutParams();
        int available = Math.round(dockTop) - params.topMargin - dp(18);
        fitsAboveDock = available >= dp(160);
        int height = Math.max(dp(160), Math.min(dp(232), available));
        if (params.height != height) { params.height = height; host.setLayoutParams(params); }
        updateVisibility();
    }
    private ColorStateList buttonColors(int idle) {
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}}, new int[]{0xFF111111, idle});
    }
    void dockVisible(boolean visible) { dockVisible = visible; updateVisibility(); }
    private void updateVisibility() { host.setVisibility(visible() ? View.VISIBLE : View.GONE); }
    void resume() { if (enabled) refresh(false, false); }
    void close() { repository.close(); tv.close(); }
    boolean focused() { return host.hasFocus(); }
    boolean focusCard(int preferred) {
        if (!visible()) return false;
        if (adapter.getItemCount() == 0) { sources[source].requestFocus(); return true; }
        int pos = Math.max(0, Math.min(preferred, adapter.getItemCount() - 1)); pendingFocus = pos;
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(pos);
        if (holder != null) focusPending();
        else {
            ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(pos, dp(4));
            list.post(this::focusPending);
        }
        return true;
    }
    private void focusPending() {
        if (!visible() || pendingFocus < 0) return;
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(pendingFocus);
        if (holder != null) { holder.itemView.requestFocus(); pendingFocus = -1; }
    }
    void menu() {
        int[] order = CatalogSource.displayOrder();
        List<String> labels = new ArrayList<>();
        for (int selected : order) labels.add(activity.getString(sourceNames[selected]));
        labels.add(movieTab.getText().toString()); labels.add(showTab.getText().toString());
        labels.add(refreshTab.getText().toString()); labels.add(activity.getString(enabled ? R.string.reco_hide : R.string.reco_show));
        labels.add(activity.getString(R.string.reco_reset_feedback));
        new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog).setTitle(R.string.reco_menu)
                .setItems(labels.toArray(new String[0]), (dialog, choice) -> {
                    if (choice == order.length + 4) {
                        if (!repository.resetFeedback()) Toast.makeText(activity, R.string.reco_feedback_error, Toast.LENGTH_SHORT).show();
                        render();
                    }
                    else if (choice == order.length + 2) refresh(true, true);
                    else if (choice == order.length + 3) {
                        enabled = !enabled; activity.getSharedPreferences("recommendations", 0).edit().putBoolean("enabled", enabled).apply();
                        updateVisibility(); if (enabled) refresh(false, true); focusDock.run();
                    } else {
                        enabled = true; activity.getSharedPreferences("recommendations", 0).edit().putBoolean("enabled", true).apply();
                        if (choice < order.length) source(order[choice], true);
                        else if (source == TV && choice == order.length) { channelMenu(); return; }
                        else category(choice == order.length + 1);
                        dockVisible(true); focusDock.run(); focusCard(0);
                    }
                }).setPositiveButton(R.string.done, null).show();
    }
    private void feedbackMenu(CatalogItem item) {
        int[] names = {R.string.reco_more_like_this, R.string.reco_not_interested,
                "series".equals(item.type) ? R.string.reco_finished_series : R.string.reco_seen, R.string.reco_clear_choice};
        String[] labels = new String[names.length]; for (int i = 0; i < names.length; i++) labels[i] = activity.getString(names[i]);
        new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog).setTitle(item.name)
                .setItems(labels, (dialog, choice) -> {
                    if (choice == 0 && RecommendationTaste.tags(item.genres).isEmpty()) {
                        Toast.makeText(activity, R.string.reco_not_enough_info, Toast.LENGTH_LONG).show(); return;
                    }
                    String[] decisions = {RecommendationTaste.MORE, RecommendationTaste.HIDE,
                            "series".equals(item.type) ? RecommendationTaste.COMPLETED : RecommendationTaste.SEEN, ""};
                    if (!repository.choose(item, decisions[choice])) Toast.makeText(activity, R.string.reco_feedback_error, Toast.LENGTH_SHORT).show();
                    pendingFocus = -1; render(); focusCard(0);
                }).setNegativeButton(R.string.done, null).show();
    }
    private void open(CatalogItem title) {
        if ("tv".equals(title.type)) {
            try { activity.startActivity(TvCatalog.intent(tvPrograms.get(title.id))); }
            catch (Exception ignored) { Toast.makeText(activity, R.string.reco_open_error, Toast.LENGTH_LONG).show(); }
            return;
        }
        Uri uri = new Uri.Builder().scheme("stremio").authority("").appendPath("detail").appendPath(title.type).appendPath(title.id).build();
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage("com.stremio.one")); }
        catch (Exception ignored) { Toast.makeText(activity, R.string.reco_open_error, Toast.LENGTH_LONG).show(); }
    }
    private final class Posters extends RecyclerView.Adapter<Poster> {
        private final List<CatalogItem> values = new ArrayList<>();
        private int renderedSource = -1;
        Posters() { setHasStableIds(true); }
        void replace(List<CatalogItem> next) {
            List<CatalogItem> limited = next.subList(0, Math.min(100, next.size()));
            boolean same = renderedSource == source && values.size() == limited.size();
            if (same) for (int i = 0; i < values.size(); i++) {
                CatalogItem a = values.get(i), b = limited.get(i);
                if (!a.id.equals(b.id) || !a.type.equals(b.type) || !a.name.equals(b.name)
                        || !a.year.equals(b.year) || !a.poster.equals(b.poster)) { same = false; break; }
            }
            if (same) return;
            renderedSource = source;
            values.clear(); values.addAll(limited); notifyDataSetChanged();
        }
        @Override public long getItemId(int position) { return (values.get(position).type + values.get(position).id).hashCode(); }
        @Override public int getItemCount() { return values.size(); }
        @NonNull @Override public Poster onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new Poster(); }
        @Override public void onBindViewHolder(@NonNull Poster holder, int position) {
            CatalogItem title = values.get(position);
            holder.title.setText(title.name); holder.year.setText(caption(title));
            ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
            int width = dp("tv".equals(title.type) ? 156 : 100);
            if (params.width != width) { params.width = width; holder.itemView.setLayoutParams(params); }
            holder.itemView.setContentDescription(title.name + " \u00B7 " + caption(title));
            holder.itemView.setOnClickListener(v -> open(title)); repository.poster(title.poster, holder.image);
            holder.itemView.setOnLongClickListener("tv".equals(title.type) ? null : v -> { feedbackMenu(title); return true; });
            holder.itemView.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) { focusDock.run(); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_UP) { (secondary() ? showTab : movieTab).requestFocus(); return true; }
                int current = holder.getBindingAdapterPosition();
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int from = pendingFocus >= 0 ? pendingFocus : current;
                    focusCard(from + (key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
                    return true;
                }
                return false;
            });
        }
    }
    private final class Poster extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, year;
        Poster() {
            super(new LinearLayout(activity)); LinearLayout card = (LinearLayout) itemView;
            card.setId(R.id.recommendation_card); card.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(dp(100), -1); params.rightMargin = dp(15);
            card.setLayoutParams(params); card.setPadding(dp(3), dp(3), dp(3), dp(3));
            card.setFocusable(true); card.setClickable(true);
            image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xFF20232A); card.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
            title = text("", 11, Color.WHITE); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(title, new LinearLayout.LayoutParams(-1, dp(20)));
            year = text("", 10, 0xFF9BA3B3); year.setSingleLine(true); year.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(year, new LinearLayout.LayoutParams(-1, dp(16)));
            card.setOnFocusChangeListener((v, focused) -> {
                GradientDrawable background = new GradientDrawable(); background.setCornerRadius(dp(7));
                background.setColor(focused ? 0xFF202C43 : 0x00141416);
                if (focused) background.setStroke(dp(2), 0xFFB1C6FF);
                card.setBackground(background);
                if (focused) {
                    int position = getBindingAdapterPosition();
                    if (position >= 0 && position < adapter.values.size()) {
                        CatalogItem selected = adapter.values.get(position);
                        status.setText(selected.name + " \u00B7 " + caption(selected));
                    }
                } else updateStatus();
            });
        }
    }
    private String caption(CatalogItem item) {
        if (source == GOOGLE) return "Google TV · " + item.year;
        if (source == CatalogSource.TRAKT) return "Trakt · " + item.year;
        if ("TMDb".equals(item.provider)) return "TMDb · " + item.year;
        return item.year;
    }
}
