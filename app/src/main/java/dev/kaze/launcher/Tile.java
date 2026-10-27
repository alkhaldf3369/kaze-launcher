package dev.kaze.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A rounded glass card for one app. On focus it grows, lifts and gets a
 * glowing pink-to-violet border; the app name fades in beneath it.
 */
public class Tile extends LinearLayout {

    public static final int KIND_APP = 0, KIND_CINJOY = 1, KIND_ALL = 2;

    private final FrameLayout card;
    private final TextView name;
    private final Glow glow;
    private final float density;
    public final String id;

    public Tile(Context c, String id, int kind, String label, Drawable banner, Drawable icon, int wDp, int hDp) {
        super(c);
        this.id = id;
        density = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClipChildren(false);
        setClipToPadding(false);
        int pad = dp(14);
        setPadding(pad, pad, pad, 0);

        card = new FrameLayout(c);
        card.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0x55100A24);
        bg.setStroke(dp(1), 0x33FFFFFF);
        card.setBackground(bg);
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);

        if (kind == KIND_CINJOY) {
            card.addView(new CinJoyArt(c), new FrameLayout.LayoutParams(-1, -1));
        } else if (kind == KIND_ALL) {
            card.addView(new AllArt(c), new FrameLayout.LayoutParams(-1, -1));
        } else if (banner != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout inner = new LinearLayout(c);
            inner.setOrientation(VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            inner.addView(iv, new LayoutParams(dp(hDp * 0.42f), dp(hDp * 0.42f)));
            TextView t = new TextView(c);
            t.setText(label);
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setSingleLine(true);
            t.setPadding(dp(8), dp(6), dp(8), 0);
            inner.addView(t);
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }

        glow = new Glow(c);
        glow.setAlpha(0f);
        FrameLayout holder = new FrameLayout(c);
        holder.setClipChildren(false);
        holder.addView(card, new FrameLayout.LayoutParams(dp(wDp), dp(hDp), Gravity.CENTER));
        holder.addView(glow, new FrameLayout.LayoutParams(dp(wDp) + dp(8), dp(hDp) + dp(8), Gravity.CENTER));
        addView(holder, new LayoutParams(dp(wDp) + dp(8), dp(hDp) + dp(8)));

        name = new TextView(c);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setShadowLayer(dp(6), 0, dp(1), 0xCC000000);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0f);
        name.setPadding(0, dp(12), 0, 0);
        addView(name, new LayoutParams(dp(wDp + 20), -2));
    }

    private int dp(float v) { return Math.round(v * density); }

    @Override
    protected void onFocusChanged(boolean focused, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(focused, dir, prev);
        float s = focused ? 1.12f : 1f;
        animate().scaleX(s).scaleY(s).translationZ(focused ? dp(12) : 0)
                .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0.8f))
                .setDuration(focused ? 260 : 180).start();
        glow.animate().alpha(focused ? 1f : 0f).setDuration(200).start();
        name.animate().alpha(focused ? 1f : 0f).translationY(focused ? 0 : -dp(6)).setDuration(200).start();
        if (focused) glow.spin(); else glow.halt();
    }

    /** Animated gradient border drawn around the card. */
    static class Glow extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private ValueAnimator anim;
        private float phase;
        private final float d;

        Glow(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void spin() {
            halt();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(2600);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        void halt() { if (anim != null) { anim.cancel(); anim = null; } }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float off = phase * w * 2;
            p.setShader(new LinearGradient(-w + off, 0, off, h,
                    new int[]{0xFFFF6FA8, 0xFFB98CFF, 0xFF7FD6FF, 0xFFFF6FA8}, null, Shader.TileMode.MIRROR));
            float sw = p.getStrokeWidth() / 2;
            r.set(sw, sw, w - sw, h - sw);
            c.drawRoundRect(r, 21 * d, 21 * d, p);
        }
    }

    /** Built-in art for the CinJoy tile. */
    static class CinJoyArt extends View {
        private final Paint bg = new Paint(), t = new Paint(Paint.ANTI_ALIAS_FLAG), pl = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tri = new Path();
        CinJoyArt(Context c) {
            super(c);
            t.setColor(Color.WHITE);
            t.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            t.setTextAlign(Paint.Align.CENTER);
            pl.setColor(0xFFFF6FA8);
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            bg.setShader(new LinearGradient(0, 0, w, h, 0xFF2B0F3A, 0xFFB0204F, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, bg);
            float r = h * 0.17f, cx = w * 0.5f, cy = h * 0.36f;
            pl.setColor(0x33FFFFFF);
            c.drawCircle(cx, cy, r * 1.25f, pl);
            tri.reset();
            tri.moveTo(cx - r * 0.45f, cy - r * 0.6f);
            tri.lineTo(cx + r * 0.65f, cy);
            tri.lineTo(cx - r * 0.45f, cy + r * 0.6f);
            tri.close();
            pl.setColor(Color.WHITE);
            c.drawPath(tri, pl);
            t.setTextSize(h * 0.2f);
            c.drawText("CinJoy", cx, h * 0.82f, t);
        }
    }

    /** Art for the "all apps" tile: a 3x3 dot grid. */
    static class AllArt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        AllArt(Context c) { super(c); p.setColor(0xDDFFFFFF); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), g = h * 0.14f, r = h * 0.035f;
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++)
                    c.drawCircle(w / 2 + i * g, h / 2 + j * g, r, p);
        }
    }
}
