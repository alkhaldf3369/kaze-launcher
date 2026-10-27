package dev.kaze.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final java.util.concurrent.ExecutorService BG = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WallpaperView wallpaper;
    private LinearLayout row;
    private HorizontalScrollView rowScroll;
    private FrameLayout allPanel;
    private LinearLayout allGrid;
    private TextView dateView, greet;
    private View content;
    private List<Apps.App> all = new ArrayList<>();
    private final Map<String, Apps.App> byPkg = new HashMap<>();
    private String lastSignature = "";
    private float d;

    private final Runnable tick = new Runnable() {
        @Override public void run() { updateDate(); ui.postDelayed(this, 60_000); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        wallpaper = new WallpaperView(this);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x00000000, 0x00000000, 0xCC07051A}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(dp(56), dp(36), dp(56), dp(28));
        content = col;

        // Top bar: clock + date on the start side, settings gear on the end side.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setShadowLayer(dp(10), 0, 0, 0x88000000);
        clock.setIncludeFontPadding(false);
        clockBox.addView(clock);
        dateView = text(18, 0xE6FFFFFF, "sans-serif-light");
        clockBox.addView(dateView);
        top.addView(clockBox, new LinearLayout.LayoutParams(0, -2, 1));

        TextView gear = text(26, Color.WHITE, "sans-serif");
        gear.setText("⚙");
        gear.setGravity(Gravity.CENTER);
        gear.setFocusable(true);
        GradientDrawable gbg = new GradientDrawable();
        gbg.setShape(GradientDrawable.OVAL);
        gbg.setColor(0x33000000);
        gear.setBackground(gbg);
        gear.setOnFocusChangeListener((v, f) -> {
            gbg.setColor(f ? 0xFFFF6FA8 : 0x33000000);
            v.animate().scaleX(f ? 1.15f : 1f).scaleY(f ? 1.15f : 1f).setDuration(150).start();
        });
        gear.setOnClickListener(v -> openSettings());
        top.addView(gear, new LinearLayout.LayoutParams(dp(56), dp(56)));
        col.addView(top);

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

        greet = text(15, 0xCCFFFFFF, "sans-serif-medium");
        greet.setLetterSpacing(0.05f);
        greet.setPadding(dp(14), 0, dp(14), dp(2));
        col.addView(greet);

        rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipChildren(false);
        rowScroll.setClipToPadding(false);
        rowScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rowScroll.setSmoothScrollingEnabled(true);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(0, dp(10), 0, dp(10));
        rowScroll.addView(row);
        col.addView(rowScroll, new LinearLayout.LayoutParams(-1, -2));

        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        allPanel = buildAllPanel();
        allPanel.setVisibility(View.GONE);
        root.addView(allPanel, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        refreshApps(true);
    }

    private int dp(float v) { return Math.round(v * d); }

    private TextView text(int sp, int color, String face) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        wallpaper.start();
        ui.post(tick);
        refreshApps(false);
        content.setAlpha(0f);
        content.setTranslationY(dp(14));
        content.animate().alpha(1f).translationY(0).setDuration(320).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        wallpaper.stop();
        ui.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Home pressed while already home: close panels, go back to the first tile.
        if (allPanel.getVisibility() == View.VISIBLE) closeAll();
        else if (row.getChildCount() > 0) {
            rowScroll.smoothScrollTo(0, 0);
            row.getChildAt(0).requestFocus();
        }
    }

    private void updateDate() {
        Calendar c = Calendar.getInstance();
        Locale ar = new Locale("ar");
        String pattern = DateFormat.getBestDateTimePattern(ar, "EEEEdMMMM");
        dateView.setText(DateFormat.format(pattern, c));
        int h = c.get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "سهرة حلوة" : h < 12 ? "صباح الخير" : h < 18 ? "مساء الخير" : "مساء النور";
        greet.setText(g + "  ·  تطبيقاتي");
    }

    // ---------------------------------------------------------------- apps

    private void refreshApps(boolean force) {
        BG.execute(() -> {
            List<Apps.App> list = Apps.loadAll(this);
            StringBuilder sig = new StringBuilder();
            for (Apps.App a : list) sig.append(a.pkg).append(';');
            String s = sig.toString();
            ui.post(() -> {
                if (!force && s.equals(lastSignature) && row.getChildCount() > 0) return;
                lastSignature = s;
                all = list;
                byPkg.clear();
                for (Apps.App a : list) byPkg.put(a.pkg, a);
                List<String> r = Apps.row(this);
                if (r == null) { r = Apps.defaultRow(list); Apps.saveRow(this, r); }
                buildRow(r, null);
                buildAllGrid();
            });
        });
    }

    private void buildRow(List<String> ids, String focusId) {
        row.removeAllViews();
        View toFocus = null;
        for (String id : ids) {
            Tile t;
            if (Apps.CINJOY.equals(id)) {
                t = new Tile(this, id, Tile.KIND_CINJOY, "CinJoy", null, null, 200, 112);
            } else {
                Apps.App a = byPkg.get(id);
                if (a == null) continue; // uninstalled
                t = new Tile(this, id, Tile.KIND_APP, a.label, a.banner, a.icon, 200, 112);
            }
            wireRowTile(t);
            row.addView(t);
            if (id.equals(focusId)) toFocus = t;
        }
        Tile more = new Tile(this, "@all", Tile.KIND_ALL, "كل التطبيقات", null, null, 112, 112);
        more.setOnClickListener(v -> openAll());
        row.addView(more);
        View f = toFocus != null ? toFocus : row.getChildAt(0);
        if (f != null) f.post(f::requestFocus);
    }

    private void wireRowTile(Tile t) {
        t.setOnClickListener(v -> open(t.id));
        t.setOnLongClickListener(v -> { rowMenu(t.id); return true; });
        t.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { rowMenu(t.id); return true; }
            return false;
        });
    }

    private void open(String id) {
        if (Apps.CINJOY.equals(id)) {
            startActivity(new Intent(this, BrowserActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }
        Intent i = Apps.launchIntent(this, id);
        if (i == null) { Toast.makeText(this, "ما قدرت أفتح التطبيق", Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(i); } catch (ActivityNotFoundException ignored) { }
    }

    private void rowMenu(String id) {
        List<String> r = Apps.row(this);
        if (r == null) return;
        int pos = r.indexOf(id);
        String[] items = {"قدّمه خطوة", "أخّره خطوة", "شيله من الصف"};
        new AlertDialog.Builder(this, R.style.KazeDialog)
                .setTitle(Apps.CINJOY.equals(id) ? "CinJoy" : (byPkg.containsKey(id) ? byPkg.get(id).label : id))
                .setItems(items, (dlg, which) -> {
                    if (which == 0 && pos > 0) { r.remove(pos); r.add(pos - 1, id); }
                    else if (which == 1 && pos < r.size() - 1) { r.remove(pos); r.add(pos + 1, id); }
                    else if (which == 2) r.remove(pos);
                    Apps.saveRow(this, r);
                    buildRow(r, which == 2 ? null : id);
                    buildAllGrid();
                }).show();
    }

    // ------------------------------------------------------------ all apps

    private FrameLayout buildAllPanel() {
        FrameLayout p = new FrameLayout(this);
        p.setBackgroundColor(0xE60B0820);
        p.setClickable(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(56), dp(36), dp(56), 0);
        TextView title = text(30, Color.WHITE, "sans-serif-light");
        title.setText("كل التطبيقات");
        box.addView(title);
        TextView hint = text(14, 0xAAFFFFFF, "sans-serif");
        hint.setText("اضغط مطوّل على أي تطبيق عشان تضيفه للصف أو تشيله");
        hint.setPadding(0, dp(4), 0, dp(12));
        box.addView(hint);
        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, 0, 0, dp(40));
        sv.setVerticalScrollBarEnabled(false);
        allGrid = new LinearLayout(this);
        allGrid.setOrientation(LinearLayout.VERTICAL);
        allGrid.setClipChildren(false);
        sv.addView(allGrid);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        p.addView(box, new FrameLayout.LayoutParams(-1, -1));
        return p;
    }

    private void buildAllGrid() {
        allGrid.removeAllViews();
        List<String> r = Apps.row(this);
        int perRow = 6;
        LinearLayout line = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % perRow == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setClipChildren(false);
                allGrid.addView(line);
            }
            Apps.App a = all.get(i);
            boolean pinned = r != null && r.contains(a.pkg);
            Tile t = new Tile(this, a.pkg, Tile.KIND_APP, (pinned ? "★ " : "") + a.label, a.banner, a.icon, 176, 99);
            t.setOnClickListener(v -> { closeAll(); open(a.pkg); });
            t.setOnLongClickListener(v -> { togglePin(a.pkg); return true; });
            line.addView(t);
        }
    }

    private void togglePin(String pkg) {
        List<String> r = Apps.row(this);
        if (r == null) r = new ArrayList<>();
        boolean added;
        if (r.contains(pkg)) { r.remove(pkg); added = false; }
        else { r.add(pkg); added = true; }
        Apps.saveRow(this, r);
        Toast.makeText(this, added ? "انضاف للصف ★" : "انشال من الصف", Toast.LENGTH_SHORT).show();
        View focused = allPanel.findFocus();
        int idx = -1;
        if (focused instanceof Tile) idx = indexInGrid((Tile) focused);
        buildRow(r, null);
        buildAllGrid();
        focusGrid(idx);
    }

    private int indexInGrid(Tile t) {
        int n = 0;
        for (int i = 0; i < allGrid.getChildCount(); i++) {
            ViewGroup line = (ViewGroup) allGrid.getChildAt(i);
            for (int j = 0; j < line.getChildCount(); j++, n++) if (line.getChildAt(j) == t) return n;
        }
        return -1;
    }

    private void focusGrid(int idx) {
        if (idx < 0) idx = 0;
        int n = 0;
        for (int i = 0; i < allGrid.getChildCount(); i++) {
            ViewGroup line = (ViewGroup) allGrid.getChildAt(i);
            for (int j = 0; j < line.getChildCount(); j++, n++) {
                if (n == idx) { View v = line.getChildAt(j); v.post(v::requestFocus); return; }
            }
        }
    }

    private void openAll() {
        allPanel.setAlpha(0f);
        allPanel.setVisibility(View.VISIBLE);
        allPanel.animate().alpha(1f).setDuration(220).start();
        focusGrid(0);
    }

    private void closeAll() {
        allPanel.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            allPanel.setVisibility(View.GONE);
            View last = row.getChildAt(row.getChildCount() - 1);
            if (last != null) last.requestFocus();
        }).start();
    }

    // ------------------------------------------------------------ settings

    private void openSettings() {
        String[] items = {"رابط موقع CinJoy", "كيف أضيف خلفياتي؟", "تفعيل زر الهوم", "إعدادات التلفزيون"};
        new AlertDialog.Builder(this, R.style.KazeDialog)
                .setTitle("الإعدادات")
                .setItems(items, (dlg, which) -> {
                    if (which == 0) askCinJoyUrl(this, null);
                    else if (which == 1) {
                        new AlertDialog.Builder(this, R.style.KazeDialog)
                                .setTitle("خلفياتك")
                                .setMessage("حط صورك أو GIF أو فيديوهات قصيرة (mp4) في هذا المجلد على التلفزيون، والواجهة تعرضها وتبدّل بينها كل 3 دقايق:\n\n"
                                        + WallpaperView.folder(this) + "\n\nإذا المجلد فاضي تشتغل الخلفية المتحركة الأصلية.")
                                .setPositiveButton("تمام", null).show();
                    } else if (which == 2) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } else {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                }).show();
    }

    /** Shared with BrowserActivity: asks for the site's address and saves it. */
    static void askCinJoyUrl(Activity a, Runnable done) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setText(Browser.home(a));
        e.setSelection(e.getText().length());
        new AlertDialog.Builder(a, R.style.KazeDialog)
                .setTitle("عنوان موقع CinJoy")
                .setView(e)
                .setPositiveButton("حفظ", (dlg, w) -> {
                    Browser.setHome(a, e.getText().toString());
                    if (done != null) done.run();
                })
                .setNegativeButton("إلغاء", null).show();
    }

    @Override
    public void onBackPressed() {
        if (allPanel.getVisibility() == View.VISIBLE) closeAll();
        // A launcher never closes on Back.
    }
}
