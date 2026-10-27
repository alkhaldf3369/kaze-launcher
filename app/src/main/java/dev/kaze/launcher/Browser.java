package dev.kaze.launcher;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Saved addresses and the ad-host block list for the built-in browser. */
final class Browser {

    private static final String KEY_HOME = "cinjoy_home";
    private static final String KEY_LAST = "cinjoy_last";
    private static Set<String> blocked;

    private Browser() {}

    static String home(Context c) {
        return Apps.prefs(c).getString(KEY_HOME, "https://cinjoy.pk");
    }

    static void setHome(Context c, String url) {
        url = url.trim();
        if (url.isEmpty()) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        Apps.prefs(c).edit().putString(KEY_HOME, url).remove(KEY_LAST).apply();
    }

    static String last(Context c) {
        return Apps.prefs(c).getString(KEY_LAST, null);
    }

    static void setLast(Context c, String url) {
        Apps.prefs(c).edit().putString(KEY_LAST, url).apply();
    }

    /** "watch.cinjoy.example.com" -> "example.com" (good enough for this use). */
    static String base(String host) {
        if (host == null) return "";
        host = host.toLowerCase(Locale.ROOT);
        String[] p = host.split("\\.");
        if (p.length <= 2) return host;
        String tld = p[p.length - 1], sld = p[p.length - 2];
        // Two-part suffixes like co.uk / com.sa
        if (sld.length() <= 3 && tld.length() == 2 && p.length >= 3) {
            return p[p.length - 3] + "." + sld + "." + tld;
        }
        return sld + "." + tld;
    }

    static String baseOf(String url) {
        try { return base(Uri.parse(url).getHost()); } catch (Throwable t) { return ""; }
    }

    static synchronized boolean isAd(Context c, String host) {
        if (host == null) return false;
        if (blocked == null) {
            blocked = new HashSet<>(800);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("adhosts.txt")))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) blocked.add(line.toLowerCase(Locale.ROOT));
                }
            } catch (Exception ignored) { }
        }
        String h = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (blocked.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0) return false;
            h = h.substring(dot + 1);
            if (h.indexOf('.') < 0) return false;
        }
    }
}
