package dev.kaze.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Installed apps plus the user's pinned row, stored in SharedPreferences. */
public final class Apps {

    public static final String CINJOY = "@cinjoy";
    private static final String PREFS = "kaze";
    private static final String KEY_ROW = "row";

    public static final class App {
        public final String pkg;
        public final String label;
        public final Drawable banner; // may be null
        public final Drawable icon;
        App(String pkg, String label, Drawable banner, Drawable icon) {
            this.pkg = pkg; this.label = label; this.banner = banner; this.icon = icon;
        }
    }

    private static final Map<String, App> cache = new HashMap<>();

    private Apps() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** All launchable apps, TV apps first by name. Runs on a background thread. */
    public static List<App> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, ActivityInfo> found = new LinkedHashMap<>();
        Map<String, Boolean> tv = new HashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(c.getPackageName())) continue;
                if (!found.containsKey(p)) {
                    found.put(p, ri.activityInfo);
                    tv.put(p, cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER));
                }
            }
        }
        List<App> out = new ArrayList<>();
        for (Map.Entry<String, ActivityInfo> e : found.entrySet()) {
            App a = cache.get(e.getKey());
            if (a == null) {
                ActivityInfo ai = e.getValue();
                Drawable banner = null;
                try { banner = ai.loadBanner(pm); } catch (Throwable ignored) { }
                if (banner == null) try { banner = ai.applicationInfo.loadBanner(pm); } catch (Throwable ignored) { }
                Drawable icon = ai.loadIcon(pm);
                CharSequence l = ai.applicationInfo.loadLabel(pm);
                a = new App(e.getKey(), l == null ? e.getKey() : l.toString(), banner, icon);
                cache.put(e.getKey(), a);
            }
            out.add(a);
        }
        Collator col = Collator.getInstance(Locale.getDefault());
        out.sort((a, b) -> {
            boolean ta = Boolean.TRUE.equals(tv.get(a.pkg)), tb = Boolean.TRUE.equals(tv.get(b.pkg));
            if (ta != tb) return ta ? -1 : 1;
            return col.compare(a.label, b.label);
        });
        return out;
    }

    public static void forget(String pkg) { cache.remove(pkg); }

    public static List<String> row(Context c) {
        String s = prefs(c).getString(KEY_ROW, null);
        if (s == null) return null;
        if (s.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split(",")));
    }

    public static void saveRow(Context c, List<String> row) {
        prefs(c).edit().putString(KEY_ROW, TextUtils.join(",", row)).apply();
    }

    /** First run: CinJoy plus the first few TV apps. */
    public static List<String> defaultRow(List<App> all) {
        List<String> r = new ArrayList<>();
        r.add(CINJOY);
        for (App a : all) {
            if (r.size() >= 8) break;
            if (a.pkg.contains("settings") || a.pkg.contains("launcher")) continue;
            r.add(a.pkg);
        }
        return r;
    }

    public static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    public static ComponentName self(Context c) {
        return new ComponentName(c, MainActivity.class);
    }
}
