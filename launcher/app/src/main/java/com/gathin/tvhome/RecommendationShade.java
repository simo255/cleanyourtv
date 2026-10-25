package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Edge-to-edge readability gradient that fades away before the poster row. */
final class RecommendationShade extends View {
    private final Paint paint = new Paint();

    RecommendationShade(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        paint.setShader(new LinearGradient(0, 0, 0, Math.max(1, height),
                new int[]{0x00000000, 0x22000000, 0x88000000, 0x88000000, 0x00000000, 0x00000000},
                new float[]{0f, .18f, .30f, .50f, .62f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
