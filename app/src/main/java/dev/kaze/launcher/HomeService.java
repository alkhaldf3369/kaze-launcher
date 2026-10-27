package dev.kaze.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the Home button open Kaze on Google TV, where the stock launcher
 * cannot be replaced. When the system launcher comes to the front after a
 * short Home press, Kaze is brought up instead. Holding Home is left alone,
 * so the Google TV dashboard still opens. It never reads screen content.
 */
public class HomeService extends AccessibilityService {

    private long homeDown;
    private long longHomeAt;

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) homeDown = e.getEventTime();
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() > 0) longHomeAt = SystemClock.uptimeMillis();
            if (e.getAction() == KeyEvent.ACTION_UP && e.getEventTime() - homeDown > 500) longHomeAt = SystemClock.uptimeMillis();
        }
        return false; // never swallow keys
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (p.equals(getPackageName()) || !isStockLauncher(p)) return;
        if (SystemClock.uptimeMillis() - longHomeAt < 4000) return; // user held Home on purpose
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private static boolean isStockLauncher(String p) {
        return p.equals("com.google.android.apps.tv.launcherx")
                || p.equals("com.google.android.tvlauncher")
                || p.equals("com.google.android.leanbacklauncher")
                || p.equals("com.android.tv.launcher")
                || (p.contains("launcher") && (p.contains(".tv") || p.contains("tv.")));
    }

    @Override
    public void onInterrupt() { }
}
