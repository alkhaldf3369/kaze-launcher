package dev.kaze.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background layer. Shows the user's own pictures, animated GIF/WebP files
 * or short videos from the app's "wallpapers" folder, cross-fading between
 * them on a timer. With an empty folder it shows the built-in SakuraView.
 */
public class WallpaperView extends FrameLayout {

    public static final long SWITCH_MS = 3 * 60 * 1000L;
    private static final long FADE_MS = 1200;

    private final SakuraView sakura;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<File> media = new ArrayList<>();
    private View current;
    private int index = -1;
    private boolean running;

    private final Runnable next = new Runnable() {
        @Override public void run() {
            showNext();
            if (running && media.size() > 1) ui.postDelayed(this, SWITCH_MS);
        }
    };

    public WallpaperView(Context c) {
        super(c);
        sakura = new SakuraView(c);
        addView(sakura, new LayoutParams(-1, -1));
    }

    public static File folder(Context c) {
        File f = c.getExternalFilesDir("wallpapers");
        if (f != null && !f.exists()) f.mkdirs();
        return f;
    }

    /** Re-scans the folder. Call from onResume. */
    public void start() {
        running = true;
        List<File> found = scan();
        boolean changed = !found.equals(media);
        if (changed) {
            media.clear();
            media.addAll(found);
            Collections.shuffle(media);
            index = -1;
        }
        ui.removeCallbacks(next);
        if (media.isEmpty()) {
            removeCurrent();
            sakura.setVisibility(VISIBLE);
            sakura.setRunning(true);
            return;
        }
        sakura.setRunning(false);
        sakura.setVisibility(GONE);
        if (changed || current == null) showNext();
        else resumeCurrent();
        if (media.size() > 1) ui.postDelayed(next, SWITCH_MS);
    }

    public void stop() {
        running = false;
        ui.removeCallbacks(next);
        sakura.setRunning(false);
        if (current instanceof VideoView) ((VideoView) current).pause();
        if (current instanceof ImageView) {
            Drawable d = ((ImageView) current).getDrawable();
            if (d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).stop();
        }
    }

    private void resumeCurrent() {
        if (current instanceof VideoView) ((VideoView) current).start();
        if (current instanceof ImageView) {
            Drawable d = ((ImageView) current).getDrawable();
            if (d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
        }
    }

    private List<File> scan() {
        List<File> out = new ArrayList<>();
        File dir = folder(getContext());
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
                    || n.endsWith(".gif") || n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv")) {
                out.add(f);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static boolean isVideo(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv");
    }

    private static boolean isAnimated(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".gif") || n.endsWith(".webp");
    }

    private void showNext() {
        if (media.isEmpty()) return;
        index = (index + 1) % media.size();
        File f = media.get(index);
        if (isVideo(f)) showVideo(f);
        else loadImage(f);
    }

    private void showVideo(File f) {
        VideoView v = new VideoView(getContext());
        v.setAlpha(0f);
        v.setVideoURI(Uri.fromFile(f));
        v.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            v.start();
            fadeIn(v);
        });
        v.setOnErrorListener((mp, what, extra) -> { removeView(v); return true; });
        FrameLayout.LayoutParams lp = new LayoutParams(-1, -1);
        addView(v, lp);
    }

    private void loadImage(File f) {
        int tw = Math.max(1280, getWidth()), th = Math.max(720, getHeight());
        io.execute(() -> {
            Drawable d = null;
            try {
                if (isAnimated(f) && Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.Source src = ImageDecoder.createSource(f);
                    d = ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                        int sample = Math.max(1, Math.min(info.getSize().getWidth() / tw, info.getSize().getHeight() / th));
                        if (sample > 1) decoder.setTargetSampleSize(sample);
                    });
                } else {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getPath(), o);
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= tw && o.outHeight / (sample * 2) >= th) sample *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
                    if (b != null) d = new android.graphics.drawable.BitmapDrawable(getResources(), b);
                }
            } catch (Throwable ignored) { }
            final Drawable result = d;
            ui.post(() -> {
                if (result == null) return;
                ImageView iv = new ImageView(getContext());
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageDrawable(result);
                iv.setAlpha(0f);
                addView(iv, new LayoutParams(-1, -1));
                if (result instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) result).start();
                // Slow "Ken Burns" drift keeps still pictures alive.
                if (!(result instanceof AnimatedImageDrawable)) {
                    iv.setScaleX(1.0f); iv.setScaleY(1.0f);
                    iv.animate().scaleX(1.08f).scaleY(1.08f).setDuration(SWITCH_MS).start();
                }
                fadeIn(iv);
            });
        });
    }

    private void fadeIn(View v) {
        View old = current;
        current = v;
        v.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            if (old != null && old != sakura) {
                if (old instanceof VideoView) ((VideoView) old).stopPlayback();
                removeView(old);
            }
        }).start();
    }

    private void removeCurrent() {
        if (current != null) {
            if (current instanceof VideoView) ((VideoView) current).stopPlayback();
            removeView(current);
            current = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @SuppressWarnings("unused")
    private static ViewGroup.LayoutParams full() { return new ViewGroup.LayoutParams(-1, -1); }
}
