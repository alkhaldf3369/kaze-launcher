package dev.kaze.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Original animated anime-style night scene: gradient sky, glowing moon,
 * twinkling stars, layered mountains with a torii gate, drifting sakura
 * petals and floating fireflies. Drawn with plain Canvas calls and capped
 * at 30 fps so it stays light on TV hardware.
 */
public class SakuraView extends View {

    private static final long FRAME_MS = 33;

    private final Random rnd = new Random(7);
    private final Paint sky = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint far = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint near = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fly = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mist = new Paint();

    private final Path farHills = new Path();
    private final Path nearHills = new Path();
    private final Path torii = new Path();
    private final Path petalShape = new Path();

    private float[] sx, sy, sr, sp;          // stars
    private float[] px, py, pv, pw, pr, prs, ps, pa; // petals
    private float[] fx, fy, fp;              // fireflies

    private int w, h;
    private boolean running;
    private long start = SystemClock.uptimeMillis();
    private long last;

    public SakuraView(Context c) {
        super(c);
        star.setColor(Color.WHITE);
        far.setColor(0xFF2A1F52);
        near.setColor(0xFF120C28);
        petal.setStyle(Paint.Style.FILL);
        fly.setColor(0xFFFFE9A8);
        moon.setColor(0xFFFFF4E0);

        petalShape.moveTo(0, -1f);
        petalShape.cubicTo(0.9f, -0.9f, 0.9f, 0.6f, 0, 1f);
        petalShape.cubicTo(-0.9f, 0.6f, -0.9f, -0.9f, 0, -1f);
        petalShape.close();
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw; h = nh;
        if (w == 0 || h == 0) return;

        sky.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0B0A24, 0xFF231A4D, 0xFF5B2B6E, 0xFFC4577F},
                new float[]{0f, 0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        mist.setShader(new LinearGradient(0, h * 0.6f, 0, h,
                new int[]{0x00FFB3CB, 0x33FFB3CB, 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        float mx = w * 0.78f, my = h * 0.24f, mr = h * 0.09f;
        glow.setShader(new RadialGradient(mx, my, mr * 4.5f,
                new int[]{0x66FFE7C4, 0x22FF9FC0, 0x00000000},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));

        int ns = 140;
        sx = new float[ns]; sy = new float[ns]; sr = new float[ns]; sp = new float[ns];
        for (int i = 0; i < ns; i++) {
            sx[i] = rnd.nextFloat() * w;
            sy[i] = rnd.nextFloat() * h * 0.62f;
            sr[i] = 0.6f + rnd.nextFloat() * 1.8f;
            sp[i] = rnd.nextFloat() * 6.28f;
        }

        int np = 46;
        px = new float[np]; py = new float[np]; pv = new float[np]; pw = new float[np];
        pr = new float[np]; prs = new float[np]; ps = new float[np]; pa = new float[np];
        for (int i = 0; i < np; i++) resetPetal(i, true);

        int nf = 18;
        fx = new float[nf]; fy = new float[nf]; fp = new float[nf];
        for (int i = 0; i < nf; i++) {
            fx[i] = rnd.nextFloat() * w;
            fy[i] = h * (0.62f + rnd.nextFloat() * 0.33f);
            fp[i] = rnd.nextFloat() * 6.28f;
        }

        buildHills();
    }

    private void buildHills() {
        farHills.reset();
        farHills.moveTo(0, h);
        farHills.lineTo(0, h * 0.70f);
        farHills.cubicTo(w * 0.12f, h * 0.60f, w * 0.22f, h * 0.66f, w * 0.32f, h * 0.62f);
        farHills.cubicTo(w * 0.45f, h * 0.55f, w * 0.55f, h * 0.68f, w * 0.68f, h * 0.63f);
        farHills.cubicTo(w * 0.80f, h * 0.58f, w * 0.90f, h * 0.66f, w, h * 0.62f);
        farHills.lineTo(w, h);
        farHills.close();

        nearHills.reset();
        nearHills.moveTo(0, h);
        nearHills.lineTo(0, h * 0.82f);
        nearHills.cubicTo(w * 0.15f, h * 0.76f, w * 0.30f, h * 0.84f, w * 0.46f, h * 0.80f);
        nearHills.cubicTo(w * 0.62f, h * 0.76f, w * 0.80f, h * 0.86f, w, h * 0.78f);
        nearHills.lineTo(w, h);
        nearHills.close();

        // Torii gate silhouette standing on the far ridge.
        float gx = w * 0.20f, base = h * 0.655f, gh = h * 0.15f, gw = h * 0.16f;
        float post = gw * 0.08f;
        torii.reset();
        torii.addRect(gx - gw / 2 + post, base - gh, gx - gw / 2 + post * 2, base, Path.Direction.CW);
        torii.addRect(gx + gw / 2 - post * 2, base - gh, gx + gw / 2 - post, base, Path.Direction.CW);
        torii.addRect(gx - gw / 2 + post * 0.5f, base - gh * 0.78f, gx + gw / 2 - post * 0.5f, base - gh * 0.71f, Path.Direction.CW);
        Path top = new Path();
        top.moveTo(gx - gw * 0.62f, base - gh * 1.02f);
        top.quadTo(gx, base - gh * 0.92f, gx + gw * 0.62f, base - gh * 1.02f);
        top.lineTo(gx + gw * 0.58f, base - gh * 0.90f);
        top.quadTo(gx, base - gh * 0.82f, gx - gw * 0.58f, base - gh * 0.90f);
        top.close();
        torii.addPath(top);
    }

    private void resetPetal(int i, boolean anywhere) {
        px[i] = anywhere ? rnd.nextFloat() * w : -20 - rnd.nextFloat() * w * 0.3f;
        py[i] = anywhere ? rnd.nextFloat() * h : -20 - rnd.nextFloat() * h * 0.5f;
        float depth = 0.4f + rnd.nextFloat() * 0.6f;
        pv[i] = (0.6f + rnd.nextFloat() * 0.8f) * depth * h / 900f;
        pw[i] = (0.5f + rnd.nextFloat()) * depth * w / 1600f;
        pr[i] = rnd.nextFloat() * 360f;
        prs[i] = (rnd.nextFloat() - 0.5f) * 4f;
        ps[i] = (5f + rnd.nextFloat() * 6f) * depth * h / 1080f;
        pa[i] = 0.45f + depth * 0.5f;
    }

    public void setRunning(boolean r) {
        running = r;
        if (r) { last = 0; postInvalidateOnAnimation(); }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (w == 0) return;
        long now = SystemClock.uptimeMillis();
        float t = (now - start) / 1000f;
        float dt = last == 0 ? 1f : Math.min(3f, (now - last) / 16.6f);
        last = now;

        c.drawRect(0, 0, w, h, sky);

        for (int i = 0; i < sx.length; i++) {
            float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.3f + sp[i]));
            star.setAlpha((int) (a * 255));
            c.drawCircle(sx[i], sy[i], sr[i], star);
        }

        float mx = w * 0.78f, my = h * 0.24f + (float) Math.sin(t * 0.2f) * h * 0.004f;
        c.drawCircle(mx, my, h * 0.4f, glow);
        c.drawCircle(mx, my, h * 0.09f, moon);

        c.drawPath(farHills, far);
        c.drawPath(torii, far);
        c.drawRect(0, h * 0.6f, w, h, mist);
        c.drawPath(nearHills, near);

        for (int i = 0; i < fx.length; i++) {
            float a = 0.5f + 0.5f * (float) Math.sin(t * 2f + fp[i]);
            float x = fx[i] + (float) Math.sin(t * 0.4f + fp[i]) * w * 0.02f;
            float y = fy[i] + (float) Math.cos(t * 0.5f + fp[i]) * h * 0.015f;
            fly.setAlpha((int) (40 * a));
            c.drawCircle(x, y, h * 0.012f, fly);
            fly.setAlpha((int) (220 * a));
            c.drawCircle(x, y, h * 0.0035f, fly);
        }

        for (int i = 0; i < px.length; i++) {
            py[i] += pv[i] * dt;
            px[i] += (pw[i] + (float) Math.sin(t * 0.8f + i) * pw[i] * 0.8f) * dt;
            pr[i] += prs[i] * dt;
            if (py[i] > h + 20 || px[i] > w + 20) resetPetal(i, false);

            float flip = (float) Math.cos(t * 1.5f + i);
            int col = i % 3 == 0 ? 0xFFFFC2D6 : (i % 3 == 1 ? 0xFFFFA3C1 : 0xFFFFE0EA);
            petal.setColor(col);
            petal.setAlpha((int) (pa[i] * 255));
            c.save();
            c.translate(px[i], py[i]);
            c.rotate(pr[i]);
            c.scale(ps[i] * Math.max(0.25f, Math.abs(flip)), ps[i] * 0.7f);
            c.drawPath(petalShape, petal);
            c.restore();
        }

        if (running) {
            long delay = Math.max(0, FRAME_MS - (SystemClock.uptimeMillis() - now));
            postInvalidateDelayed(delay);
        }
    }
}
