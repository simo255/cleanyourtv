package com.gathin.tvhome;

import android.os.SystemClock;
import androidx.recyclerview.widget.RecyclerView;

/** Prepare a bounded number of empty app holders only while Home is quiet. No artwork is decoded. */
final class IdleTilePool implements Runnable {
    private static final long QUIET_MS = 400;
    private final RecyclerView grid;
    private final RecyclerView.Adapter<?> adapter;
    private final int limit;
    private boolean active;
    private long lastInput = SystemClock.uptimeMillis();

    IdleTilePool(RecyclerView grid, RecyclerView.Adapter<?> adapter, int limit) {
        this.grid = grid;
        this.adapter = adapter;
        this.limit = limit;
        grid.getRecycledViewPool().setMaxRecycledViews(0, limit);
        grid.setItemViewCacheSize(limit);
    }

    void start() {
        active = true;
        grid.removeCallbacks(this);
        grid.postDelayed(this, QUIET_MS);
    }

    void interacted() { lastInput = SystemClock.uptimeMillis(); }

    void pause() { active = false; grid.removeCallbacks(this); }

    @Override public void run() {
        if (!active || !grid.isAttachedToWindow() || !grid.hasWindowFocus()) return;
        int target = Math.min(limit, Math.max(0, adapter.getItemCount() - grid.getChildCount()));
        if (grid.getRecycledViewPool().getRecycledViewCount(0) >= target) return;
        long quiet = SystemClock.uptimeMillis() - lastInput;
        if (quiet < QUIET_MS || grid.isComputingLayout() || grid.isLayoutRequested()
                || grid.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            grid.postDelayed(this, Math.max(32, QUIET_MS - quiet));
            return;
        }
        // At most one inflation per idle turn, never a whole row during a D-pad frame.
        grid.getRecycledViewPool().putRecycledView(adapter.createViewHolder(grid, 0));
        grid.postDelayed(this, 32);
    }
}
