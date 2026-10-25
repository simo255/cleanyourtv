package io.github.simo255.mediastandby;

import android.graphics.Color;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

/** Runs only while selected by Android as the active screensaver. No privileged app permissions. */
public final class MediaDream extends DreamService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout clockGroup;
    private TextView clock;
    private TextView status;
    private IdlePolicy policy;
    private AudioManager audio;
    private boolean running;
    private final Runnable tick = this::tick;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        clockGroup = new LinearLayout(this);
        clockGroup.setOrientation(LinearLayout.VERTICAL);
        clockGroup.setGravity(Gravity.CENTER);
        clock = new TextView(this);
        clock.setTextSize(68);
        clock.setTextColor(Color.rgb(186, 201, 230));
        status = new TextView(this);
        status.setTextSize(18);
        status.setTextColor(Color.rgb(125, 139, 164));
        clockGroup.addView(clock);
        clockGroup.addView(status);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(clockGroup, params);
        setContentView(root);
        audio = getSystemService(AudioManager.class);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        long timeout = Settings.System.getLong(getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 600_000L);
        policy = new IdlePolicy(SystemClock.elapsedRealtime(), timeout);
        running = true;
        handler.post(tick);
    }

    private void tick() {
        if (!running) return;
        boolean playing = true;
        try {
            if (audio != null) playing = audio.isMusicActive();
        } catch (RuntimeException ignored) {
            // A transient audio-service failure must not end playback by putting the box to sleep.
        }
        long now = SystemClock.elapsedRealtime();
        if (policy.shouldFinish(now, playing)) {
            running = false;
            finish(); // Android decides whether to sleep; no forced lock or wake action.
            return;
        }
        clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        status.setText(playing ? R.string.playing : R.string.paused);
        // Drift slowly around the centre so the clock does not stay on the same pixels.
        float seconds = now / 1000f;
        clockGroup.setTranslationX((float) Math.sin(seconds / 29f) * root.getWidth() * .16f);
        clockGroup.setTranslationY((float) Math.cos(seconds / 37f) * root.getHeight() * .14f);
        handler.postDelayed(tick, 1000L);
    }

    @Override public void onDreamingStopped() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
