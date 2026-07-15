package org.jrs82.fsclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Small 5-bar WiFi signal meter for the header.
 *
 * <p>{@link #setLevel(int)} (0..5) sets the number of lit bars. The color of the
 * lit bars slides with the signal level from red (weak) through yellow to
 * green (strong) — i.e. the whole scale is dynamic. Unlit bars are drawn in
 * dim gray.
 */
public class WifiSignalView extends View {

    private static final int BAR_COUNT = 5;
    private static final int COLOR_OFF = 0xFF3A3A3A;   // unlit bar (dim)
    // Color stops from weakest to strongest.
    private static final int COLOR_LOW = 0xFFEE4444;   // red (level 1)
    private static final int COLOR_MID = 0xFFE6C200;   // yellow (level 3)
    private static final int COLOR_HIGH = 0xFF66DD66;  // green (level 5)

    private int level = 0;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public WifiSignalView(Context c) { super(c); }
    public WifiSignalView(Context c, AttributeSet a) { super(c, a); }
    public WifiSignalView(Context c, AttributeSet a, int d) { super(c, a, d); }

    /** @param l number of lit bars, clamped to 0..5. */
    public void setLevel(int l) {
        int clamped = Math.max(0, Math.min(BAR_COUNT, l));
        if (clamped != level) {
            level = clamped;
            invalidate();
        }
    }

    public int getLevel() { return level; }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int barW = dp(3.5f);
        int gap = dp(2.5f);
        int desiredW = getPaddingLeft() + getPaddingRight()
                + BAR_COUNT * barW + (BAR_COUNT - 1) * gap;
        int desiredH = getPaddingTop() + getPaddingBottom() + dp(18);
        setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec));
    }

    /** Gradient color for level 1..5: red → yellow → green. */
    private int colorForLevel(int lvl) {
        float t = (lvl - 1) / (float) (BAR_COUNT - 1); // 0..1
        if (t <= 0.5f) {
            return lerp(COLOR_LOW, COLOR_MID, t / 0.5f);
        }
        return lerp(COLOR_MID, COLOR_HIGH, (t - 0.5f) / 0.5f);
    }

    private static int lerp(int from, int to, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int usableW = getWidth() - left - getPaddingRight();
        int usableH = getHeight() - top - getPaddingBottom();
        if (usableW <= 0 || usableH <= 0) return;

        float gap = usableW * 0.14f;
        float barW = (usableW - gap * (BAR_COUNT - 1)) / BAR_COUNT;
        float radius = barW * 0.35f;
        float bottom = top + usableH;
        int litColor = level > 0 ? colorForLevel(level) : COLOR_OFF;

        for (int i = 0; i < BAR_COUNT; i++) {
            float frac = (i + 1) / (float) BAR_COUNT;   // 0.2 .. 1.0 of the height
            float h = usableH * frac;
            float x = left + i * (barW + gap);
            rect.set(x, bottom - h, x + barW, bottom);
            paint.setColor(i < level ? litColor : COLOR_OFF);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
