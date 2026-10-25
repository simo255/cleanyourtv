package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Reduced motion (config.xml reduced_motion): the one spring and the one time limit every animation uses then. */
final class Motion {
    static final int FOCUS_MS = 150;
    static final int PAGE_MS = 180;

    /** Cancel an interrupted entrance without leaving an invisible or displaced page behind. */
    static void reset(android.view.View view) {
        view.animate().withEndAction(null).cancel();
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
    }

    /** Move only the incoming content; the wallpaper and header remain still. */
    static void enter(android.view.View view, float distance, boolean animate) {
        reset(view);
        if (!animate || !view.isShown()) return;
        view.setTranslationY(distance);
        view.animate().translationY(0f).setDuration(PAGE_MS)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
    }

    /** The longest any reduced-motion animation runs, in milliseconds. */
    static final int REDUCED_MS = 90;
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    static final float REDUCED_STIFFNESS = 15000f;
    /** The spring stops once it is within this many pixels of its target. */
    static final float REDUCED_MIN_CHANGE = 2f;

    private Motion() {
    }

    static void reduce(SpringAnimation spring) {
        spring.setSpring(new SpringForce().setStiffness(REDUCED_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE);
    }
}
