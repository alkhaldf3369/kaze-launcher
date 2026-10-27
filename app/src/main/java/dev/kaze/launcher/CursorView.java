package dev.kaze.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A soft glowing pointer drawn on top of the web page. */
public class CursorView extends View {

    public float x, y;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float d;
    private float press;

    public CursorView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        fill.setColor(0xFFFFFFFF);
        edge.setColor(0xFFFF6FA8);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2 * d);
        edge.setStrokeJoin(Paint.Join.ROUND);
        halo.setColor(0x55FF6FA8);
        float s = 1.25f * d;
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 22 * s);
        arrow.lineTo(6 * s, 16.5f * s);
        arrow.lineTo(10 * s, 25 * s);
        arrow.lineTo(13.5f * s, 23.5f * s);
        arrow.lineTo(9.5f * s, 15 * s);
        arrow.lineTo(17 * s, 15 * s);
        arrow.close();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void moveTo(float nx, float ny) {
        x = Math.max(0, Math.min(getWidth() - 1, nx));
        y = Math.max(0, Math.min(getHeight() - 1, ny));
        invalidate();
    }

    /** Short ripple when OK is pressed. */
    public void pulse() {
        press = 1f;
        postOnAnimation(new Runnable() {
            @Override public void run() {
                press -= 0.08f;
                invalidate();
                if (press > 0) postOnAnimation(this);
            }
        });
    }

    @Override
    protected void onDraw(Canvas c) {
        if (press > 0) {
            halo.setAlpha((int) (140 * press));
            c.drawCircle(x, y, (1.3f - press) * 34 * d, halo);
        }
        c.save();
        c.translate(x, y);
        c.drawPath(arrow, fill);
        c.drawPath(arrow, edge);
        c.restore();
    }
}
