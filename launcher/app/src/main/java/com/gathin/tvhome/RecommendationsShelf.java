package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
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
    private static final long TAB_FOCUS_REFRESH_MS = 700;
    private final Runnable focusRefresh = () -> refresh(AccountRefresh.Trigger.FOREGROUND, false);
    private static final int TV = CatalogSource.TV;
    private final Activity activity;
    private final FrameLayout host;
    private final RecommendationRepository repository;
    private final TvCatalog tv;
    private final StremioResume stremio;
    private final Map<String, StremioProgress.Item> resumeItems = new HashMap<>();
    private final Map<String, TvProgram> tvPrograms = new HashMap<>();
    private final Runnable focusDock, focusTop;
    private final TextView status, movieTab, showTab, refreshTab, accountTab;
    private final TextView[] sources = new TextView[CatalogSource.COUNT];
    private final List<TextView> controls = new ArrayList<>();
    private final RecyclerView list;
    private final Posters adapter = new Posters();
    private final Typeface font;
    private final boolean refined;
    private final int dialogTheme;
    private final LinearLayout emptyPanel;
    private final TextView emptyMessage;
    private final CinemaPreview preview;
    private int lastFocusedCard;
    private boolean showSeries, dockVisible = true, enabled;
    private boolean fitsAboveDock = true;
    private float lastDockTop;
    private int source, pendingFocus = -1;
    private String syncStatus = "";
    private boolean catalogReady;
    private boolean catalogEmpty, tvResume, stremioUpcoming;
    private String tvChannel;
    private List<CatalogItem> movies = new ArrayList<>(), shows = new ArrayList<>();
    private final int[] sourceNames = {R.string.reco_for_you, R.string.reco_watchlist, R.string.reco_trending, R.string.reco_tv, 0, R.string.reco_recent, R.string.stremio_resume};

    RecommendationsShelf(Activity activity, FrameLayout host, Runnable focusDock, Runnable focusTop, boolean refined) {
        this.activity = activity; this.host = host; this.focusDock = focusDock; this.focusTop = focusTop;
        this.refined = refined;
        dialogTheme = refined ? R.style.Theme_TvHome_Dialog_Nocturne : R.style.Theme_TvHome_Dialog;
        if (refined) host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (lastDockTop > 0 && (t != ot || b != ob)) fitAboveDock(lastDockTop);
        });
        font = activity.getResources().getFont(R.font.outfit);
        repository = new RecommendationRepository(activity);
        repository.posterTarget(dp(136), dp(204));
        tv = new TvCatalog(activity);
        stremio = new StremioResume(activity);
        if (repository.enhanced()) {
            sourceNames[CatalogSource.RECENT] = R.string.reco_discover;
            stremio.observeViewing(repository::observe);
        }
        enabled = activity.getSharedPreferences("recommendations", 0).getBoolean("enabled", true);
        showSeries = activity.getSharedPreferences("recommendations", 0).getBoolean("series", false);
        tvResume = activity.getSharedPreferences("recommendations", 0).getBoolean("tv_resume", false);
        stremioUpcoming = activity.getSharedPreferences("recommendations", 0).getBoolean("stremio_upcoming", false);
        tvChannel = activity.getSharedPreferences("recommendations", 0).getString("tv_channel", "");
        source = CatalogSource.restore(activity.getSharedPreferences("recommendations", 0).getInt("source", 0));
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        host.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        int[] sourceIds = {R.id.recommendation_for_you, R.id.recommendation_watchlist, R.id.recommendation_trending, R.id.recommendation_tv, 0, R.id.recommendation_recent, R.id.recommendation_stremio};
        for (int i : CatalogSource.displayOrder()) {
            final int index = i;
            sources[i] = button(sourceNames[i]); sources[i].setId(sourceIds[i]);
            if (refined) {
                HomeAppearance.control(sources[i], true); sources[i].setTextSize(15);
                if (i == CatalogSource.TRAKT) sources[i].setText(R.string.cinema_for_you);
            }
            sources[i].setOnClickListener(v -> source(index, true));
            header.addView(sources[i]);
        }
        LinearLayout filters = header;
        filters.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        movieTab = button(R.string.reco_movies); showTab = button(R.string.reco_shows);
        movieTab.setId(R.id.recommendation_movies); showTab.setId(R.id.recommendation_shows);
        movieTab.setOnClickListener(v -> { if (source == TV) channelMenu(); else category(false); });
        showTab.setOnClickListener(v -> category(true));
        filters.addView(movieTab); filters.addView(showTab);
        refreshTab = button(R.string.reco_refresh); refreshTab.setId(R.id.recommendation_refresh);
        refreshTab.setOnClickListener(v -> refresh(AccountRefresh.Trigger.FORCE, true)); filters.addView(refreshTab);
        accountTab = button(R.string.stremio_connect); accountTab.setId(R.id.stremio_account);
        accountTab.setOnClickListener(v -> stremio.connect());
        if (!refined) filters.addView(accountTab);
        if (refined) refreshTab.setVisibility(View.GONE);
        HorizontalScrollView headerScroll = new HorizontalScrollView(activity);
        headerScroll.setFillViewport(true); headerScroll.setHorizontalScrollBarEnabled(false);
        headerScroll.setFocusable(false);
        headerScroll.addView(header, new ViewGroup.LayoutParams(-2, -2));
        panel.addView(headerScroll, new LinearLayout.LayoutParams(-1, -2));
        status = text("", refined ? 14 : 11, refined ? HomeAppearance.MUTED : 0xFFA6ADB8); status.setId(R.id.recommendation_status);
        if (refined) {
            status.setSingleLine(true); status.setEllipsize(TextUtils.TruncateAt.END);
            status.setPadding(dp(4), 0, dp(4), 0);
        }
        panel.addView(status, new LinearLayout.LayoutParams(-1, dp(refined ? 32 : 23)));
        if (refined) status.setVisibility(View.GONE);
        list = new RecyclerView(activity); list.setId(R.id.recommendation_list);
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        // A full strip exceeds RecyclerView's default pool of five cards. Keep enough
        // holders to switch catalogs without recreating the remaining visible cards.
        list.getRecycledViewPool().setMaxRecycledViews(0, 16);
        list.setItemAnimator(null); list.setClipToPadding(false); list.setClipChildren(false);
        list.setPadding(dp(4), dp(6), dp(8), dp(5)); list.setAdapter(adapter);
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View view) {
                if (list.getChildAdapterPosition(view) == pendingFocus) list.post(RecommendationsShelf.this::focusPending);
            }
            @Override public void onChildViewDetachedFromWindow(@NonNull View view) { }
        });
        FrameLayout content = new FrameLayout(activity);
        preview = refined ? new CinemaPreview(activity) : null;
        if (refined) {
            LinearLayout cinema = new LinearLayout(activity); cinema.setOrientation(LinearLayout.VERTICAL);
            cinema.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
            cinema.addView(list, new LinearLayout.LayoutParams(-1, dp(140)));
            content.addView(cinema, new FrameLayout.LayoutParams(-1, -1));
        } else content.addView(list, new FrameLayout.LayoutParams(-1, -1));
        emptyPanel = new LinearLayout(activity); emptyPanel.setOrientation(LinearLayout.VERTICAL);
        emptyPanel.setGravity(Gravity.CENTER); emptyPanel.setPadding(dp(32), dp(12), dp(32), dp(12));
        emptyMessage = text("", 18, HomeAppearance.MUTED); emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setMaxLines(4); emptyMessage.setEllipsize(TextUtils.TruncateAt.END);
        emptyPanel.addView(emptyMessage, new LinearLayout.LayoutParams(-1, -2));
        if (refined) {
            LinearLayout.LayoutParams connectParams = (LinearLayout.LayoutParams) accountTab.getLayoutParams();
            connectParams.topMargin = dp(18);
            emptyPanel.addView(accountTab, connectParams);
        }
        emptyPanel.setVisibility(View.GONE);
        content.addView(emptyPanel, new FrameLayout.LayoutParams(-1, -1));
        panel.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        repository.listen(changedSource -> { if (changedSource == source) render(); });
        tv.listen(() -> { if (source == TV) render(); });
        stremio.listen(() -> {
            if (source == CatalogSource.STREMIO) { paintTabs(); render(); }
            else if (repository.enhanced() && CatalogSource.networkFeed(source)) render();
        });
        if (refined) {
            for (int index : CatalogSource.displayOrder()) activateOnFocus(sources[index], () -> {
                if (source == index) return;
                source(index, false);
                // Wait for the user to settle on a tab before starting a network read.
                host.removeCallbacks(focusRefresh);
                host.postDelayed(focusRefresh, TAB_FOCUS_REFRESH_MS);
            });
        }
        source(source, false);
    }
    private void activateOnFocus(TextView tab, Runnable activate) {
        tab.setOnFocusChangeListener((v, focused) -> { if (focused) activate.run(); });
        tab.setOnHoverListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                activate.run();
                v.requestFocus();
            }
            return false;
        });
    }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setTypeface(font);
        if (Build.VERSION.SDK_INT >= 28) view.setFontVariationSettings("'wght' 450");
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private TextView button(int label) {
        TextView view = text(activity.getString(label), refined ? 14 : 12, Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(refined ? 38 : 32)); params.rightMargin = dp(8);
        view.setLayoutParams(params); view.setPadding(dp(12), 0, dp(12), 0);
        view.setFocusable(true); view.setClickable(true); view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.pill_bg); view.setTextColor(buttonColors(Color.WHITE));
        if (refined) HomeAppearance.control(view, false);
        controls.add(view);
        view.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (adapter.getItemCount() == 0) {
                    if (refined && v != accountTab && accountTab.isShown()) accountTab.requestFocus();
                    else focusDock.run();
                } else focusCard(0);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (refined && v == accountTab) sources[source].requestFocus();
                else focusTop.run();
                return true;
            }
            int index = controls.indexOf(v);
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int step = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                for (int next = index + step; next >= 0 && next < controls.size(); next += step)
                    if (controls.get(next).getVisibility() == View.VISIBLE) {
                        if (!refined || controls.get(next) != accountTab) controls.get(next).requestFocus();
                        break;
                    }
                return true;
            }
            return false;
        });
        return view;
    }
    private void source(int selected, boolean refresh) {
        if (source == CatalogSource.STREMIO && selected != source) stremio.pause();
        source = selected; pendingFocus = -1; lastFocusedCard = 0;
        activity.getSharedPreferences("recommendations", 0).edit().putInt("source", source).apply();
        for (int i : CatalogSource.displayOrder()) {
            if (refined) sources[i].setSelected(i == source);
            else sources[i].setTextColor(buttonColors(i == source ? 0xFF9CB7FF : 0xFFA6ADB8));
        }
        paintTabs(); render(); list.scrollToPosition(0);
        if (refresh) { host.removeCallbacks(focusRefresh); refresh(AccountRefresh.Trigger.FOREGROUND, true); }
    }
    private void category(boolean series) {
        pendingFocus = -1; lastFocusedCard = 0;
        if (source == TV) {
            tvResume = series;
            activity.getSharedPreferences("recommendations", 0).edit().putBoolean("tv_resume", series).apply();
        } else if (source == CatalogSource.STREMIO) {
            stremioUpcoming = series;
            activity.getSharedPreferences("recommendations", 0).edit().putBoolean("stremio_upcoming", series).apply();
        } else {
            showSeries = series;
            activity.getSharedPreferences("recommendations", 0).edit().putBoolean("series", series).apply();
        }
        paintTabs();
        render(); list.scrollToPosition(0);
    }
    private boolean secondary() { return source == TV ? tvResume : source == CatalogSource.STREMIO ? stremioUpcoming : showSeries; }
    private void paintTabs() {
        movieTab.setText(source == TV ? R.string.reco_tv_channels : source == CatalogSource.STREMIO ? R.string.stremio_in_progress : R.string.reco_movies);
        showTab.setText(source == TV ? R.string.reco_tv_resume : source == CatalogSource.STREMIO ? R.string.stremio_upcoming : R.string.reco_shows);
        accountTab.setVisibility(source == CatalogSource.STREMIO && (!refined || !stremio.connected()) ? View.VISIBLE : View.GONE);
        accountTab.setText(stremio.connected() ? R.string.stremio_disconnect : R.string.stremio_connect);
        refreshTab.setText(R.string.reco_refresh);
        if (refined) { movieTab.setSelected(!secondary()); showTab.setSelected(secondary()); }
        else {
            movieTab.setTextColor(buttonColors(secondary() ? 0xFFA6ADB8 : 0xFF9CB7FF));
            showTab.setTextColor(buttonColors(secondary() ? 0xFF9CB7FF : 0xFFA6ADB8));
        }
    }
    private void refresh(AccountRefresh.Trigger trigger, boolean interactive) {
        boolean force = trigger == AccountRefresh.Trigger.FORCE;
        if (source == CatalogSource.STREMIO) {
            if (interactive && !stremio.connected() && !stremio.sessionPending) stremio.connect(); else stremio.refresh(trigger);
            return;
        }
        if (source != TV) {
            if (repository.enhanced()) stremio.refresh(trigger);
            repository.refresh(source, force); return;
        }
        if (!tv.permitted() && interactive) activity.requestPermissions(new String[]{TvCatalog.PERMISSION}, TvCatalog.REQUEST_PERMISSION);
        tv.refresh(force);
    }
    private void channelMenu() {
        if (!tv.permitted()) { refresh(AccountRefresh.Trigger.FORCE, true); return; }
        List<TvCatalog.Channel> available = new ArrayList<>(tv.channels);
        String[] labels = new String[available.size() + 1]; labels[0] = activity.getString(R.string.reco_tv_all);
        int selected = 0;
        for (int i = 0; i < available.size(); i++) {
            labels[i + 1] = available.get(i).label;
            if (available.get(i).key.equals(tvChannel)) selected = i + 1;
        }
        new AlertDialog.Builder(activity, dialogTheme).setTitle(R.string.reco_tv_channels)
                .setSingleChoiceItems(labels, selected, (dialog, choice) -> {
                    tvChannel = choice == 0 ? "" : available.get(choice - 1).key;
                    activity.getSharedPreferences("recommendations", 0).edit().putString("tv_channel", tvChannel).apply();
                    category(false); dialog.dismiss(); focusCard(0);
                }).setNegativeButton(R.string.done, null).show();
    }
    private void render() {
        if (source == CatalogSource.STREMIO) { renderStremio(); return; }
        if (source == TV) { renderTv(); return; }
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
    private void renderStremio() {
        List<CatalogItem> cards = new ArrayList<>(); resumeItems.clear();
        for (StremioProgress.Item item : stremio.items(stremioUpcoming)) {
            try {
                String detail = item.episode();
                if (item.kind == StremioProgress.UPCOMING) detail += " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(item.release));
                else if (item.kind == StremioProgress.NEXT || item.kind == StremioProgress.NEW)
                    detail += " · " + activity.getString(item.kind == StremioProgress.NEW ? R.string.stremio_new_episode : R.string.stremio_next_episode);
                else if (item.minutesLeft() > 0) detail += " · " + activity.getString(R.string.stremio_minutes_left, item.minutesLeft());
                if (detail.startsWith(" · ")) detail = detail.substring(3);
                CatalogItem card = new CatalogItem(new JSONObject().put("id", item.id).put("type", item.type)
                        .put("name", item.name).put("poster", item.poster).put("year", detail));
                cards.add(card); resumeItems.put(item.key(), item);
            } catch (Exception ignored) { }
        }
        if (stremio.sessionPending) syncStatus = activity.getString(R.string.reco_feed_loading, "Stremio");
        else if (!stremio.connected()) syncStatus = activity.getString(stremio.expired ? R.string.stremio_session_expired : R.string.stremio_setup);
        else if (stremio.loading) syncStatus = activity.getString(R.string.reco_feed_loading, "Stremio");
        else if (stremio.failed) syncStatus = activity.getString(stremio.syncedAt > 0 ? R.string.reco_feed_cached : R.string.reco_feed_error, "Stremio");
        else if (stremio.syncedAt > 0) syncStatus = activity.getString(R.string.reco_feed_synced, "Stremio",
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(stremio.syncedAt)));
        else syncStatus = "Stremio";
        catalogReady = stremio.connected() && stremio.syncedAt > 0 && !stremio.loading && !stremio.failed;
        catalogEmpty = cards.isEmpty();
        if (!list.hasFocus()) updateStatus(); adapter.replace(cards); updateVisibility();
    }
    private void updateStatus() {
        status.setText(catalogReady && catalogEmpty
                ? activity.getString(source == CatalogSource.STREMIO ? (stremioUpcoming ? R.string.stremio_upcoming_empty : R.string.stremio_resume_empty)
                    : source == TV ? R.string.reco_tv_empty
                    : source == 1 ? R.string.reco_watchlist_empty : R.string.reco_empty) : syncStatus);
        emptyMessage.setText(status.getText());
        emptyPanel.setVisibility(refined && catalogEmpty ? View.VISIBLE : View.GONE);
    }
    boolean visible() { return (repository.configured() || source >= 2) && enabled && dockVisible && fitsAboveDock; }
    void fitAboveDock(float dockTop) {
        lastDockTop = dockTop;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) host.getLayoutParams();
        int available = Math.round(dockTop) - params.topMargin - dp(refined ? 22 : 18);
        fitsAboveDock = available >= dp(refined ? 240 : 160);
        int height = Math.max(dp(refined ? 240 : 160), Math.min(dp(refined ? 340 : 232), available));
        if (params.height != height) { params.height = height; host.setLayoutParams(params); }
        updateVisibility();
    }
    private ColorStateList buttonColors(int idle) {
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}}, new int[]{0xFF111111, idle});
    }
    void dockVisible(boolean visible) { dockVisible = visible; updateVisibility(); }
    private void updateVisibility() { host.setVisibility(visible() ? View.VISIBLE : View.GONE); }
    void resume() { if (enabled) refresh(AccountRefresh.Trigger.FOREGROUND, false); }
    void trimMemory() { repository.trimMemory(); }
    void tick() { if (enabled) refresh(AccountRefresh.Trigger.TICK, false); }
    void pause() { host.removeCallbacks(focusRefresh); stremio.pause(); }
    void close() { host.removeCallbacks(focusRefresh); repository.close(); tv.close(); stremio.close(); }
    boolean focused() { return host.hasFocus(); }
    boolean focusPreviousCard() { return focusCard(lastFocusedCard); }
    boolean focusCard(int preferred) {
        if (!visible()) return false;
        if (adapter.getItemCount() == 0) {
            if (refined && accountTab.isShown()) accountTab.requestFocus(); else sources[source].requestFocus();
            return true;
        }
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
        if (refined) {
            List<String> options = new ArrayList<>();
            options.add(activity.getString(R.string.reco_refresh));
            options.add(activity.getString(enabled ? R.string.reco_hide : R.string.reco_show));
            options.add(activity.getString(R.string.reco_reset_feedback));
            options.add(activity.getString(stremio.connected() ? R.string.stremio_disconnect : R.string.stremio_connect));
            if (repository.enhanced()) options.add(activity.getString(repository.recentOnly() ? R.string.reco_all_years : R.string.reco_recent_years));
            CinemaMenu.show(activity, activity.getString(R.string.reco_menu), syncStatus, options, choice -> {
                if (choice == 0) refresh(AccountRefresh.Trigger.FORCE, true);
                else if (choice == 1) {
                    enabled = !enabled; activity.getSharedPreferences("recommendations", 0).edit().putBoolean("enabled", enabled).apply();
                    updateVisibility(); if (enabled) refresh(AccountRefresh.Trigger.FOREGROUND, true); focusDock.run();
                } else if (choice == 2) {
                    if (!repository.resetFeedback()) Toast.makeText(activity, R.string.reco_feedback_error, Toast.LENGTH_SHORT).show();
                    render();
                } else if (choice == 4) {
                    repository.togglePeriod(); render();
                } else {
                    enabled = true; activity.getSharedPreferences("recommendations", 0).edit().putBoolean("enabled", true).apply();
                    source(CatalogSource.STREMIO, false); dockVisible(true); stremio.connect();
                }
            });
            return;
        }
        int[] order = CatalogSource.displayOrder();
        List<String> labels = new ArrayList<>();
        for (int selected : order) labels.add(activity.getString(sourceNames[selected]));
        labels.add(movieTab.getText().toString()); labels.add(showTab.getText().toString());
        labels.add(refreshTab.getText().toString()); labels.add(activity.getString(enabled ? R.string.reco_hide : R.string.reco_show));
        labels.add(activity.getString(R.string.reco_reset_feedback));
        labels.add(activity.getString(stremio.connected() ? R.string.stremio_disconnect : R.string.stremio_connect));
        if (repository.enhanced()) labels.add(activity.getString(repository.recentOnly() ? R.string.reco_all_years : R.string.reco_recent_years));
        new AlertDialog.Builder(activity, dialogTheme).setTitle(R.string.reco_menu)
                .setItems(labels.toArray(new String[0]), (dialog, choice) -> {
                    if (choice == order.length + 6) {
                        repository.togglePeriod(); render();
                    }
                    else if (choice == order.length + 5) {
                        enabled = true; activity.getSharedPreferences("recommendations", 0).edit().putBoolean("enabled", true).apply();
                        source(CatalogSource.STREMIO, false); dockVisible(true); stremio.connect();
                    }
                    else if (choice == order.length + 4) {
                        if (!repository.resetFeedback()) Toast.makeText(activity, R.string.reco_feedback_error, Toast.LENGTH_SHORT).show();
                        render();
                    }
                    else if (choice == order.length + 2) refresh(AccountRefresh.Trigger.FORCE, true);
                    else if (choice == order.length + 3) {
                        enabled = !enabled; activity.getSharedPreferences("recommendations", 0).edit().putBoolean("enabled", enabled).apply();
                        updateVisibility(); if (enabled) refresh(AccountRefresh.Trigger.FOREGROUND, true); focusDock.run();
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
        new AlertDialog.Builder(activity, dialogTheme).setTitle(item.name)
                .setItems(labels, (dialog, choice) -> {
                    if (choice == 0 && RecommendationTaste.tags(item.genres).isEmpty()
                            && (!repository.enhanced() || (item.allGenres.isEmpty() && item.cast.isEmpty() && item.directors.isEmpty()))) {
                        Toast.makeText(activity, R.string.reco_not_enough_info, Toast.LENGTH_LONG).show(); return;
                    }
                    String[] decisions = {RecommendationTaste.MORE, RecommendationTaste.HIDE,
                            "series".equals(item.type) ? RecommendationTaste.COMPLETED : RecommendationTaste.SEEN, ""};
                    if (!repository.choose(item, decisions[choice])) Toast.makeText(activity, R.string.reco_feedback_error, Toast.LENGTH_SHORT).show();
                    pendingFocus = -1; render(); focusCard(0);
                }).setNegativeButton(R.string.done, null).show();
    }
    private void open(CatalogItem title) {
        if (source == CatalogSource.STREMIO) {
            StremioProgress.Item item = resumeItems.get(title.type + ":" + title.id);
            if (item == null) return;
            if (item.kind == StremioProgress.UPCOMING) { Toast.makeText(activity, R.string.stremio_not_released, Toast.LENGTH_SHORT).show(); return; }
            Uri.Builder link = new Uri.Builder().scheme("stremio").authority("").appendPath("detail").appendPath(item.type).appendPath(item.id);
            if (!item.video.isEmpty()) link.appendPath(item.video);
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, link.build()).setPackage("com.stremio.one")); }
            catch (Exception ignored) { Toast.makeText(activity, R.string.reco_open_error, Toast.LENGTH_LONG).show(); }
            return;
        }
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
        private String resumeSignature = "";
        Posters() { setHasStableIds(true); }
        void replace(List<CatalogItem> next) {
            List<CatalogItem> limited = next.subList(0, Math.min(100, next.size()));
            StringBuilder signature = new StringBuilder();
            if (source == CatalogSource.STREMIO) for (StremioProgress.Item item : stremio.items(stremioUpcoming))
                signature.append(item.key()).append('/').append(item.video).append('/').append(item.position).append('/').append(item.duration).append(';');
            boolean same = renderedSource == source && values.size() == limited.size() && resumeSignature.equals(signature.toString());
            if (same) for (int i = 0; i < values.size(); i++) {
                CatalogItem a = values.get(i), b = limited.get(i);
                if (!a.id.equals(b.id) || !a.type.equals(b.type) || !a.name.equals(b.name)
                        || !a.year.equals(b.year) || !a.poster.equals(b.poster)
                        || !a.description.equals(b.description)
                        || !a.rating.equals(b.rating) || !a.genres.equals(b.genres)) { same = false; break; }
            }
            if (same) return;
            resumeSignature = signature.toString();
            renderedSource = source;
            repository.beginPosterBatch();
            values.clear(); values.addAll(limited); notifyDataSetChanged();
            if (preview != null) {
                if (values.isEmpty()) preview.clear();
                else showPreview(Math.min(lastFocusedCard, values.size() - 1));
            }
        }
        @Override public long getItemId(int position) { return (values.get(position).type + values.get(position).id).hashCode(); }
        @Override public int getItemCount() { return values.size(); }
        @NonNull @Override public Poster onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new Poster(); }
        @Override public void onBindViewHolder(@NonNull Poster holder, int position) {
            CatalogItem title = values.get(position);
            holder.title.setText(title.name); holder.year.setText(caption(title));
            if (refined) holder.itemView.setSelected(position == lastFocusedCard);
            StremioProgress.Item resume = source == CatalogSource.STREMIO ? resumeItems.get(title.type + ":" + title.id) : null;
            holder.progress.setVisibility(resume != null && resume.percent() >= 0 ? View.VISIBLE : View.GONE);
            if (resume != null) holder.progress.setProgress(Math.max(0, resume.percent()));
            ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
            int width = dp("tv".equals(title.type) ? 156 : refined ? 92 : source == CatalogSource.STREMIO ? 136 : 100);
            if (params.width != width) { params.width = width; holder.itemView.setLayoutParams(params); }
            holder.itemView.setContentDescription(title.name + " \u00B7 " + caption(title));
            holder.itemView.setOnClickListener(v -> open(title)); repository.poster(title.poster, holder.image);
            holder.itemView.setOnLongClickListener("tv".equals(title.type) || source == CatalogSource.STREMIO ? null : v -> { feedbackMenu(title); return true; });
            holder.itemView.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) { focusDock.run(); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_UP) { (refined ? sources[source] : secondary() ? showTab : movieTab).requestFocus(); return true; }
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
        final ProgressBar progress;
        Poster() {
            super(new LinearLayout(activity)); LinearLayout card = (LinearLayout) itemView;
            card.setId(R.id.recommendation_card); card.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(dp(100), -1); params.rightMargin = dp(refined ? 12 : 15);
            card.setLayoutParams(params); card.setPadding(dp(3), dp(3), dp(3), dp(3));
            card.setFocusable(true); card.setClickable(true);
            image = new ImageView(activity); image.setScaleType(refined ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xFF20232A); card.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
            if (refined) {
                image.setBackground(HomeAppearance.surface(dp(4), HomeAppearance.SURFACE, 0, 0));
                image.setClipToOutline(true);
                StateListDrawable selection = new StateListDrawable();
                selection.addState(new int[]{android.R.attr.state_focused}, HomeAppearance.surface(dp(6), 0, dp(2), HomeAppearance.TEXT));
                selection.addState(new int[]{android.R.attr.state_selected}, HomeAppearance.surface(dp(6), 0, dp(1), HomeAppearance.ACCENT));
                selection.addState(new int[]{}, HomeAppearance.surface(dp(6), 0, 0, 0));
                card.setBackground(selection);
            }
            progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
            progress.setProgressTintList(ColorStateList.valueOf(0xFF9CB7FF)); progress.setVisibility(View.GONE);
            card.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
            title = text("", refined ? 14 : 11, Color.WHITE); title.setMaxLines(refined ? 2 : 1); title.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(title, new LinearLayout.LayoutParams(-1, dp(refined ? 36 : 20)));
            year = text("", refined ? 12 : 10, refined ? HomeAppearance.MUTED : 0xFF9BA3B3); year.setMaxLines(refined ? 2 : 1); year.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(year, new LinearLayout.LayoutParams(-1, dp(refined ? 28 : 16)));
            if (refined) { title.setVisibility(View.GONE); year.setVisibility(View.GONE); }
            card.setOnFocusChangeListener((v, focused) -> {
                GradientDrawable background = new GradientDrawable(); background.setCornerRadius(dp(7));
                background.setColor(focused ? 0xFF202C43 : 0x00141416);
                if (focused) background.setStroke(dp(2), 0xFFB1C6FF);
                if (!refined) card.setBackground(background);
                if (focused) {
                    int position = getBindingAdapterPosition();
                    if (position >= 0 && position < adapter.values.size()) {
                        CatalogItem selected = adapter.values.get(position);
                        lastFocusedCard = position;
                        if (refined) showPreview(position);
                        else status.setText(selected.name + " \u00B7 " + caption(selected));
                    }
                } else updateStatus();
            });
        }
    }
    private void showPreview(int position) {
        if (preview == null || position < 0 || position >= adapter.values.size()) return;
        CatalogItem item = adapter.values.get(position);
        for (int i = 0; i < list.getChildCount(); i++) {
            View card = list.getChildAt(i);
            card.setSelected(list.getChildAdapterPosition(card) == position);
        }
        preview.show(item, caption(item));
    }
    private String caption(CatalogItem item) {
        if (source == CatalogSource.TRAKT) return "Trakt · " + item.year;
        if ("TMDb".equals(item.provider)) return "TMDb · " + item.year;
        return item.year;
    }
}
