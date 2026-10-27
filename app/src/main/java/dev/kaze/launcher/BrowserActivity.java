package dev.kaze.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;

/**
 * Remote-friendly browser for the CinJoy site.
 * - Arrows move a pointer (it speeds up while held), OK clicks, the pointer
 *   scrolls the page when pushed against the top or bottom edge.
 * - Back goes back; holding Back (or Menu) opens the browser menu.
 * - Ads: known ad hosts are never loaded, pop-up windows are dropped, and
 *   the page is not allowed to jump to another site on its own.
 * - Remembers the last page and reopens it next time.
 */
public class BrowserActivity extends Activity {

    private WebView web;
    private CursorView cursor;
    private FrameLayout full;
    private TextView toast;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private float d;
    private String blockedUrl;

    // Pointer movement state
    private boolean up, down, left, right;
    private long moveStart;
    private long lastMove;
    private boolean looping;

    private static final String CLEANUP_JS =
            "(function(){if(window.__kaze)return;window.__kaze=1;" +
            "window.open=function(){return null};" +
            "function sweep(){var vw=innerWidth,vh=innerHeight;" +
            "document.querySelectorAll('body *').forEach(function(e){" +
            "var s=getComputedStyle(e);if(s.position!=='fixed'&&s.position!=='absolute')return;" +
            "var r=e.getBoundingClientRect();if(r.width<vw*0.8||r.height<vh*0.8)return;" +
            "if(e.querySelector('video,iframe')||e.tagName==='VIDEO'||e.tagName==='IFRAME')return;" +
            "var z=parseInt(s.zIndex)||0;var clear=parseFloat(s.opacity)<0.2||" +
            "((s.backgroundColor==='rgba(0, 0, 0, 0)'||s.backgroundColor==='transparent')&&!e.innerText.trim());" +
            "if(z>=100&&clear)e.remove();});}" +
            "sweep();var n=0,t=setInterval(function(){sweep();if(++n>20)clearInterval(t)},1500);})();";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0820);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0820);
        web.setFocusable(false); // keys go to the pointer, not to page focus
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        String ua = s.getUserAgentString().replace("; wv", "").replaceAll("Version/[\\d.]+ ", "");
        s.setUserAgentString(ua);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        full = new FrameLayout(this);
        full.setBackgroundColor(Color.BLACK);
        full.setVisibility(View.GONE);
        root.addView(full, new FrameLayout.LayoutParams(-1, -1));

        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(-1, -1));

        toast = new TextView(this);
        toast.setTextColor(Color.WHITE);
        toast.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        toast.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable tb = new GradientDrawable();
        tb.setCornerRadius(dp(22));
        tb.setColor(0xCC140F2E);
        tb.setStroke(dp(1), 0x66FF6FA8);
        toast.setBackground(tb);
        toast.setAlpha(0f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tlp.bottomMargin = dp(28);
        root.addView(toast, tlp);

        setContentView(root);
        root.post(() -> cursor.moveTo(root.getWidth() / 2f, root.getHeight() / 2f));

        if (Browser.home(this).isEmpty()) {
            MainActivity.askCinJoyUrl(this, this::startLoading);
        } else {
            startLoading();
        }
        say("الأسهم تحرك المؤشر · OK ضغط · اضغط مطوّل على رجوع للقائمة");
    }

    private int dp(float v) { return Math.round(v * d); }

    private void startLoading() {
        String last = Browser.last(this);
        String url = last != null ? last : Browser.home(this);
        if (url.isEmpty()) { finish(); return; }
        web.loadUrl(url);
    }

    private void say(String msg) {
        toast.setText(msg);
        toast.animate().cancel();
        toast.animate().alpha(1f).setStartDelay(0).setDuration(200).withEndAction(() ->
                toast.animate().alpha(0f).setStartDelay(3000).setDuration(400).start()).start();
    }

    // ------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean downEv = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: up = downEv; startLoop(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: down = downEv; startLoop(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: left = downEv; startLoop(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: right = downEv; startLoop(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (downEv && e.getRepeatCount() == 0) click();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (downEv) js("var v=document.querySelector('video');if(v){v.paused?v.play():v.pause()}");
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (downEv) js("var v=document.querySelector('video');if(v)v.currentTime+=10");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (downEv) js("var v=document.querySelector('video');if(v)v.currentTime-=10");
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (!downEv) menu();
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) { e.startTracking(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) { menu(); return true; }
        return super.onKeyLongPress(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && e.isTracking() && !e.isCanceled()) {
            if (customView != null) web.getWebChromeClient().onHideCustomView();
            else if (web.canGoBack()) web.goBack();
            else finish();
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private void js(String code) {
        web.evaluateJavascript("(function(){" + code + "})()", null);
    }

    // ---------------------------------------------------------- pointer

    private void startLoop() {
        boolean any = up || down || left || right;
        if (!any) { looping = false; return; }
        if (looping) return;
        looping = true;
        moveStart = SystemClock.uptimeMillis();
        lastMove = 0;
        cursor.animate().cancel();
        cursor.setAlpha(1f);
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long t) {
            if (!looping) { scheduleHide(); return; }
            long now = SystemClock.uptimeMillis();
            float dt = lastMove == 0 ? 1f : Math.min(3f, (now - lastMove) / 16.6f);
            lastMove = now;
            float held = (now - moveStart) / 1000f;
            float speed = dp(4) + Math.min(1f, held / 0.9f) * dp(20);
            float dx = 0, dy = 0;
            if (left) dx -= speed * dt;
            if (right) dx += speed * dt;
            if (up) dy -= speed * dt;
            if (down) dy += speed * dt;

            float edge = dp(30);
            View target = customView != null ? full : web;
            if (dy < 0 && cursor.y <= edge) scroll(target, -dy);
            else if (dy > 0 && cursor.y >= cursor.getHeight() - edge) scroll(target, -dy);
            cursor.moveTo(cursor.x + dx, cursor.y + dy);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void scheduleHide() {
        cursor.animate().alpha(0.25f).setStartDelay(4000).setDuration(600).start();
    }

    /** Sends a mouse-wheel event at the pointer so inner scroll areas work too. */
    private void scroll(View target, float amount) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = cursor.x;
        pc[0].y = cursor.y;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, amount / dp(40));
        MotionEvent ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        target.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    private void click() {
        cursor.animate().cancel();
        cursor.setAlpha(1f);
        cursor.pulse();
        View target = customView != null ? full : web;
        long t = SystemClock.uptimeMillis();
        MotionEvent dn = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursor.x, cursor.y, 0);
        dn.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent upEv = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cursor.x, cursor.y, 0);
        upEv.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(dn);
        target.dispatchTouchEvent(upEv);
        dn.recycle();
        upEv.recycle();
        scheduleHide();
    }

    // ------------------------------------------------------------- menu

    private void menu() {
        String[] items = blockedUrl != null
                ? new String[]{"تحديث الصفحة", "الصفحة الرئيسية للموقع", "تغيير عنوان الموقع", "افتح الرابط اللي انمنع", "رجوع للواجهة"}
                : new String[]{"تحديث الصفحة", "الصفحة الرئيسية للموقع", "تغيير عنوان الموقع", "رجوع للواجهة"};
        new AlertDialog.Builder(this, R.style.KazeDialog)
                .setTitle("CinJoy")
                .setItems(items, (dlg, w) -> {
                    String it = items[w];
                    if (it.startsWith("تحديث")) web.reload();
                    else if (it.startsWith("الصفحة الرئيسية")) web.loadUrl(Browser.home(this));
                    else if (it.startsWith("تغيير")) MainActivity.askCinJoyUrl(this, () -> web.loadUrl(Browser.home(this)));
                    else if (it.startsWith("افتح")) { String u = blockedUrl; blockedUrl = null; allowOnce = u; web.loadUrl(u); }
                    else finish();
                }).show();
    }

    private String allowOnce;

    // ----------------------------------------------------------- clients

    private static final WebResourceResponse EMPTY() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            String host = r.getUrl().getHost();
            if (Browser.isAd(BrowserActivity.this, host)) return EMPTY();
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            if (!r.isForMainFrame()) return false;
            Uri u = r.getUrl();
            String url = u.toString();
            String scheme = u.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) return true; // intent://, market:// ...
            if (Browser.isAd(BrowserActivity.this, u.getHost())) { say("انمنع إعلان"); return true; }
            if (url.equals(allowOnce)) { allowOnce = null; return false; }
            if (r.isRedirect()) return false;
            String cur = v.getUrl();
            String b = Browser.base(u.getHost());
            if (b.equals(Browser.baseOf(cur)) || b.equals(Browser.baseOf(Browser.home(BrowserActivity.this)))) return false;
            blockedUrl = url;
            say("منعت تحويل لموقع ثاني · القائمة ← افتح الرابط لو تبيه");
            return true;
        }

        @Override
        public void onPageStarted(WebView v, String url, Bitmap fav) {
            v.evaluateJavascript(CLEANUP_JS, null);
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            v.evaluateJavascript(CLEANUP_JS, null);
            CookieManager.getInstance().flush();
        }

        @Override
        public void doUpdateVisitedHistory(WebView v, String url, boolean reload) {
            if (url != null && url.startsWith("http")) Browser.setLast(BrowserActivity.this, url);
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public boolean onCreateWindow(WebView v, boolean dialog, boolean userGesture, Message msg) {
            if (!userGesture) return false; // silent pop-up: drop it
            // A link with target=_blank: catch its address, open it here only if it's the same site.
            WebView tmp = new WebView(BrowserActivity.this);
            tmp.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView t, WebResourceRequest r) {
                    String url = r.getUrl().toString();
                    String b = Browser.base(r.getUrl().getHost());
                    if (!Browser.isAd(BrowserActivity.this, r.getUrl().getHost())
                            && (b.equals(Browser.baseOf(web.getUrl())) || b.equals(Browser.baseOf(Browser.home(BrowserActivity.this))))) {
                        web.loadUrl(url);
                    } else {
                        say("انمنعت نافذة منبثقة");
                    }
                    t.destroy();
                    return true;
                }
            });
            WebView.WebViewTransport tr = (WebView.WebViewTransport) msg.obj;
            tr.setWebView(tmp);
            msg.sendToTarget();
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback cb) {
            if (customView != null) { cb.onCustomViewHidden(); return; }
            customView = view;
            customCb = cb;
            full.addView(view, new FrameLayout.LayoutParams(-1, -1));
            full.setVisibility(View.VISIBLE);
            web.setVisibility(View.INVISIBLE);
            scheduleHide();
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            full.removeView(customView);
            full.setVisibility(View.GONE);
            web.setVisibility(View.VISIBLE);
            customView = null;
            if (customCb != null) customCb.onCustomViewHidden();
            customCb = null;
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        @Override
        public void onPermissionRequest(PermissionRequest r) { r.deny(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
