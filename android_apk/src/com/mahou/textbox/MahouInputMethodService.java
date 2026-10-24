package com.mahou.textbox;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.content.ContentValues;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.FileInputStream;
import java.io.OutputStream;
import androidx.core.app.NotificationManagerCompat;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MahouInputMethodService extends InputMethodService {
    private static MahouInputMethodService active;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final long OFFER_KEEP_MS = 90_000L;
    private WebView webView;
    private FrameLayout inputRoot;
    private int inputViewHeight;
    private int inputViewBackground;
    private long webViewCreatedAt;
    private int webViewRecoveryCount;
    private int healthCheckGeneration;
    private boolean imePageReady;
    private final Runnable sweepAlbum = () -> worker.execute(this::sweep);
    private boolean destroyed, inputActive, exportBusy, switchingKeyboard;
    private String captionEditorTarget = "";
    private volatile boolean pendingCaptionSend;
    private int captionSendWaits;
    private final Runnable continueCaptionSend = new Runnable() {
        @Override public void run() {
            if (!pendingCaptionSend || destroyed) return;
            EditorInfo info = getCurrentInputEditorInfo();
            String target = info == null || info.packageName == null ? "" : info.packageName;
            if (!inputActive || !isQqTarget(target) || !captionEditorTarget.equals(target)) {
                if (++captionSendWaits >= 100) {
                    pendingCaptionSend = false;
                    captionSendWaits = 0;
                    notice("未能返回原聊天输入框，请重新点“发到 QQ”", 3600);
                } else main.postDelayed(this, 120);
                return;
            }
            pendingCaptionSend = false;
            captionSendWaits = 0;
            evaluate("window.sendCurrentImageToQQ&&window.sendCurrentImageToQQ();");
        }
    };
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AssetStore.ACTION_ASSETS_CHANGED.equals(intent.getAction()))
                evaluate("window.receiveAssetsChanged&&window.receiveAssetsChanged();");
            else if (GeneratorStateStore.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                if (isInputViewShown()) deliverState();
            }
            else if (InputMethodSupport.ACTION_MODE_CHANGED.equals(intent.getAction())) {
                requestHideSelf(0);
                deliverTarget();
            } else deliverState();
        }
    };

    private static boolean isQqTarget(String packageName) {
        return "com.tencent.mobileqq".equals(packageName) || "com.tencent.tim".equals(packageName);
    }

    static void leaveForTextKeyboard() {
        MahouInputMethodService service = active;
        if (service != null) service.main.post(() -> service.previousKeyboard(true));
    }

    static boolean queueCaptionSendAfterEditor() {
        MahouInputMethodService service = active;
        if (service == null || service.destroyed || !isQqTarget(service.captionEditorTarget)) return false;
        service.pendingCaptionSend = true;
        service.captionSendWaits = 0;
        service.main.removeCallbacks(service.continueCaptionSend);
        service.main.postDelayed(service.continueCaptionSend, 180);
        return true;
    }

    @Override public void onCreate() {
        super.onCreate();
        active = this;
        IntentFilter filter = new IntentFilter(GeneratorStateStore.ACTION_STATE_CHANGED);
        filter.addAction(AssetStore.ACTION_ASSETS_CHANGED);
        filter.addAction(InputMethodSupport.ACTION_MODE_CHANGED);
        ContextCompat.registerReceiver(this, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        androidx.core.app.NotificationManagerCompat.from(this).cancel(6001);
    }

    @Override @SuppressLint("SetJavaScriptEnabled") public View onCreateInputView() {
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
        }
        FrameLayout root = new FrameLayout(this);
        int height = Math.min(dp(410), Math.round(getResources().getDisplayMetrics().heightPixels * .50f));
        inputRoot = root;
        inputViewHeight = height;
        root.setLayoutParams(new FrameLayout.LayoutParams(-1, height));
        root.setMinimumHeight(height);
        int background = ThemeChrome.resolve(ThemeChrome.loadThemeKey(getSharedPreferences("mahou_popup_prefs", MODE_PRIVATE))).webBg;
        inputViewBackground = background;
        root.setBackgroundColor(background);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, 0, bars.right, bars.bottom);
            if (webView != null) webView.setLayoutParams(new FrameLayout.LayoutParams(-1, Math.max(dp(240), inputViewHeight - bars.bottom)));
            return WindowInsetsCompat.CONSUMED;
        });
        attachImeWebView(root);
        return root;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void attachImeWebView(FrameLayout root) {
        WebView view = new WebView(this);
        webView = view;
        imePageReady = false;
        webViewCreatedAt = android.os.SystemClock.uptimeMillis();
        view.setBackgroundColor(inputViewBackground);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        view.setInitialScale(0);
        view.setHorizontalScrollBarEnabled(false);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 26) {
            view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }
        view.addOnLayoutChangeListener((changed, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> applyImeViewport());
        view.addJavascriptInterface(new ImeBridge(), "AndroidBridge");
        view.setWebViewClient(LocalWebContent.createClient(this, () -> {
            if (webView != view || destroyed) return;
            imePageReady = true;
            webViewRecoveryCount = 0;
            appliedViewport = 0;
            applyImeViewport();
            deliverState();
            deliverTarget();
        }, new LocalWebContent.WebViewLifecycleListener() {
            @Override public void onMainFrameError(String url, int errorCode, String description) {
                Log.e("MahouIme", "main page error " + errorCode + " " + description + " url=" + url);
            }

            @Override public boolean onRenderProcessGone(WebView failed, boolean didCrash) {
                Log.e("MahouIme", "renderer exited; crashed=" + didCrash);
                main.post(() -> recoverImeWebView(failed, "renderer-exit"));
                return true;
            }
        }));
        root.addView(view, new FrameLayout.LayoutParams(-1, inputViewHeight));
        view.loadUrl(LocalWebContent.ORIGIN + "/assets/ime.html?ime=1");
    }

    private void recoverImeWebView(WebView failed, String reason) {
        if (destroyed || inputRoot == null || webView != failed) return;
        if (webViewRecoveryCount >= 2) {
            Log.e("MahouIme", "recovery limit reached after " + reason);
            return;
        }
        ++webViewRecoveryCount;
        ++healthCheckGeneration;
        Log.w("MahouIme", "rebuilding input page after " + reason);
        if (failed.getParent() instanceof ViewGroup) ((ViewGroup) failed.getParent()).removeView(failed);
        failed.removeJavascriptInterface("AndroidBridge");
        failed.stopLoading();
        failed.destroy();
        webView = null;
        appliedViewport = 0;
        attachImeWebView(inputRoot);
        if (isInputViewShown()) webView.requestFocus();
    }

    private void checkImeWebViewHealth() {
        WebView current = webView;
        if (destroyed || current == null) return;
        int generation = ++healthCheckGeneration;
        main.postDelayed(() -> {
            if (destroyed || webView != current || generation != healthCheckGeneration) return;
            if (!imePageReady) {
                if (android.os.SystemClock.uptimeMillis() - webViewCreatedAt >= 3500) {
                    recoverImeWebView(current, "page-load-timeout");
                } else checkImeWebViewHealth();
                return;
            }
            current.evaluateJavascript("(function(){var e=document.querySelector('.workspace');if(!e)return false;var r=e.getBoundingClientRect();return r.width>0&&r.height>0&&getComputedStyle(e).display!=='none';})()", value -> {
                if (destroyed || webView != current || generation != healthCheckGeneration) return;
                if (!"true".equals(value)) recoverImeWebView(current, "page-health-check");
                else refreshImeView();
            });
        }, 1200);
    }

    private void refreshImeView() {
        if (inputRoot != null) {
            inputRoot.setVisibility(View.VISIBLE);
            inputRoot.setAlpha(1f);
            inputRoot.requestLayout();
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
            webView.setAlpha(1f);
            webView.requestLayout();
            webView.invalidate();
            webView.postInvalidateOnAnimation();
            webView.requestFocus();
        }
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onWindowShown() {
        super.onWindowShown();
        if (webView != null) {
            refreshImeView();
            checkImeWebViewHealth();
        }
    }

    private int appliedViewport;

    private void applyImeViewport() {
        if (webView == null) return;
        int pixels = webView.getWidth();
        if (pixels <= 0 || pixels == appliedViewport) return;
        appliedViewport = pixels;
        int css = Math.max(1, Math.round(pixels / getResources().getDisplayMetrics().density));
        webView.evaluateJavascript("(function(){var w=" + css
                + ";var m=document.querySelector('meta[name=viewport]');if(m)m.setAttribute('content','width='+w+',initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover');"
                + "document.documentElement.style.width=w+'px';document.documentElement.style.maxWidth=w+'px';"
                + "document.body.style.width=w+'px';document.body.style.maxWidth=w+'px';})()", null);
    }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        inputActive = true;
        deliverTarget();
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        inputActive = true;
        ImeReturnChip.hide(this);
        ImeReturnNotifier.hide(this);
        deliverState();
        deliverTarget();
        refreshImeView();
        checkImeWebViewHealth();
    }

    @Override public void onFinishInput() {
        inputActive = false;
        evaluate("window.onInputEnded&&window.onInputEnded();");
        super.onFinishInput();
    }

    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacks(sweepAlbum);
        main.removeCallbacks(continueCaptionSend);
        sweep();
        worker.shutdownNow();
        try { unregisterReceiver(changes); } catch (IllegalArgumentException ignored) {}
        ImeReturnChip.hide(this);
        ImeReturnNotifier.hide(this);
        if (active == this) active = null;
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        inputRoot = null;
        super.onDestroy();
    }

    private boolean sensitive(EditorInfo info) {
        if (info == null) return true;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        return type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private void deliverTarget() {
        try {
            EditorInfo info = getCurrentInputEditorInfo();
            String target = info == null ? "" : info.packageName;
            JSONObject result = new JSONObject().put("active", inputActive).put("sensitive", sensitive(info))
                    .put("canSend", inputActive && !sensitive(info) && isQqTarget(target));
            evaluate("window.receiveInputTarget&&window.receiveInputTarget(" + JSONObject.quote(result.toString()) + ");");
        } catch (Exception ignored) {}
    }

    private void deliverState() {
        evaluate("window.receiveGeneratorState&&window.receiveGeneratorState(" + JSONObject.quote(GeneratorStateStore.read(this)) + ");");
    }

    private void evaluate(String script) {
        if (!destroyed && webView != null) webView.evaluateJavascript(script, null);
    }

    private void notice(String text) { notice(text, 1800); }

    private void notice(String text, int durationMs) {
        if (destroyed) return;
        if (isInputViewShown()) evaluate("typeof showAppNotice==='function'&&showAppNotice(" + JSONObject.quote(text) + "," + durationMs + ");");
        else Toast.makeText(this, text, durationMs >= 3000 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    private void updateImeSendState(String state, String message) {
        evaluate("window.setImeSendState&&window.setImeSendState(" + JSONObject.quote(state) + "," + JSONObject.quote(message) + ");");
    }

    private void readDraft() {
        if (!inputActive || sensitive(getCurrentInputEditorInfo())) { notice("安全输入框不读取草稿"); return; }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) { notice("请先点一下聊天输入框"); return; }
        try {
            String text = "";
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = 181;
            ExtractedText extracted = connection.getExtractedText(request, 0);
            if (extracted != null && extracted.text != null) text = extracted.text.toString();
            if (text.trim().isEmpty()) {
                CharSequence before = connection.getTextBeforeCursor(181, 0);
                CharSequence after = connection.getTextAfterCursor(181, 0);
                text = (before == null ? "" : before.toString()) + (after == null ? "" : after.toString());
            }
            if (text.trim().isEmpty()) { notice("当前输入框是空的"); return; }
            if (text.length() > 180) { notice("草稿超过 180 字，请缩短后再读取"); return; }
            GeneratorStateStore.update(this, new JSONObject().put("text", text).toString());
            deliverState();
            notice("已读取到图片台词，聊天草稿保留");
        } catch (Exception error) { notice("当前输入框无法读取草稿"); }
    }

    private void send(String dataUrl) {
        EditorInfo info = getCurrentInputEditorInfo();
        String target = info == null || info.packageName == null ? "" : info.packageName;
        if (!inputActive || sensitive(info) || !isQqTarget(target)) {
            updateImeSendState("error", "请先打开要发送图片的 QQ 或 TIM 聊天");
            notice("请先打开要发送图片的 QQ 或 TIM 聊天");
            return;
        }
        if (destroyed) return;
        if (exportBusy) {
            updateImeSendState("error", "上一张还在处理，请稍候");
            notice("上一张还在处理");
            return;
        }
        exportBusy = true;
        updateImeSendState("busy", "正在准备图片…");
        worker.execute(() -> {
            File temporary = null;
            Uri media = null;
            try {
                temporary = PopupShareImage.prepare(getApplicationContext(), dataUrl);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(temporary.getPath(), bounds);
                sweep();
                long now = System.currentTimeMillis();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "mahou-" + now + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.DATE_TAKEN, now);
                values.put(MediaStore.Images.Media.WIDTH, bounds.outWidth);
                values.put(MediaStore.Images.Media.HEIGHT, bounds.outHeight);
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mahou");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                media = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (media == null) throw new IOException("insert");
                try (InputStream input = new FileInputStream(temporary); OutputStream output = getContentResolver().openOutputStream(media)) {
                    if (output == null) throw new IOException("open");
                    byte[] buffer = new byte[16384];
                    for (int count; (count = input.read(buffer)) != -1; ) output.write(buffer, 0, count);
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(media, ready, null, null);
                }
                PopupShareImage.discard(temporary);
                main.post(() -> {
                    exportBusy = false;
                    if (destroyed) return;
                    main.removeCallbacks(sweepAlbum);
                    main.postDelayed(sweepAlbum, OFFER_KEEP_MS);
                    String message = "图片已保存到相册。点 QQ 输入框右侧“+”→“相册”，选择最新的魔裁图片发送。";
                    updateImeSendState("ready", message);
                    notice(message, 5200);
                });
            } catch (Exception | OutOfMemoryError error) {
                PopupShareImage.discard(temporary);
                main.post(() -> {
                    exportBusy = false;
                    updateImeSendState("error", "图片没能交给 QQ 识别，请重试。");
                    notice("图片没能交给 QQ 识别，请重试");
                });
                if (media != null) getContentResolver().delete(media, null, null);
            }
        });
    }

    private void sweep() {
        if (Build.VERSION.SDK_INT < 29) return;
        String[] projection = { MediaStore.Images.Media._ID };
        String selection = MediaStore.Images.Media.DISPLAY_NAME + " LIKE ? AND " + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = { "mahou-%", Environment.DIRECTORY_PICTURES + "/Mahou%" };
        try (android.database.Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            if (cursor == null) return;
            int id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext()) {
                getContentResolver().delete(android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(id)), null, null);
            }
        } catch (RuntimeException ignored) {}
    }

    private void copy(String dataUrl) {
        if (destroyed) return;
        if (exportBusy) { notice("上一张还在处理"); return; }
        exportBusy = true;
        worker.execute(() -> {
            File temporary = null;
            try {
                temporary = PopupShareImage.prepare(getApplicationContext(), dataUrl);
                File directory = new File(getCacheDir(), "ime-share");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
                File[] old = directory.listFiles();
                long total = 0;
                if (old != null) for (File file : old) {
                    if (file.lastModified() < System.currentTimeMillis() - 86400000L) file.delete();
                    else total += file.length();
                }
                if (total > 64L * 1024 * 1024) throw new IOException();
                File image = new File(directory, UUID.randomUUID() + ".png");
                if (!temporary.renameTo(image)) throw new IOException();
                Uri uri = Uri.parse("content://com.mahou.textbox.share/ime/" + image.getName());
                main.post(() -> {
                    exportBusy = false;
                    if (destroyed) return;
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "魔裁文本框图片", uri));
                        notice("图片已复制");
                    }
                });
            } catch (Exception | OutOfMemoryError error) {
                PopupShareImage.discard(temporary);
                main.post(() -> { exportBusy = false; notice("图片复制失败，请重试"); });
            }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void openCaptionEditor() {
        if (destroyed) return;
        EditorInfo info = getCurrentInputEditorInfo();
        captionEditorTarget = info == null || info.packageName == null ? "" : info.packageName;
        pendingCaptionSend = false;
        captionSendWaits = 0;
        boolean builtInKeyboard = InputMethodSupport.useBuiltInTextKeyboard(this);
        startActivity(ImeEditorActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
        if (builtInKeyboard) {
            requestHideSelf(0);
        } else {
            // Keep the existing phone-keyboard flow as the default.
            handOffToTextKeyboard(false);
        }
    }

    private void previousKeyboard() { previousKeyboard(false); }

    private void previousKeyboard(boolean force) {
        if (switchingKeyboard || destroyed) return;
        boolean chip = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean notes = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (!force && !chip && !notes) {
            startActivity(ImeReturnActivity.prepareIntent(this));
            return;
        }
        if (chip) ImeReturnChip.show(this);
        if (notes) ImeReturnNotifier.show(this);
        handOffToTextKeyboard(true);
        Toast.makeText(this, chip
                ? "已切到原键盘。点右上角「回魔裁」，再选魔裁图片输入法。"
                : (notes
                    ? "已切到原键盘。点通知「回到魔裁」，再选魔裁图片输入法。"
                    : "已切到原键盘。点屏幕下方键盘图标，选回魔裁图片输入法。"),
                Toast.LENGTH_LONG).show();
    }

    private void handOffToTextKeyboard(boolean allowDirectSwitch) {
        if (switchingKeyboard || destroyed) return;
        switchingKeyboard = true;
        String keyboard = InputMethodSupport.textKeyboard(this);
        try {
            if (Build.VERSION.SDK_INT >= 28) switchToPreviousInputMethod();
            else {
                InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (manager != null) manager.switchToLastInputMethod(getWindow().getWindow().getAttributes().token);
            }
        } catch (RuntimeException ignored) {}
        main.postDelayed(() -> {
            switchingKeyboard = false;
            if (destroyed) return;
            String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (current != null && !current.startsWith(getPackageName() + "/")) InputMethodSupport.rememberTextKeyboard(this, current);
            else if (allowDirectSwitch && keyboard != null) {
                try { switchInputMethod(keyboard); } catch (RuntimeException ignored) {}
            }
        }, 300);
    }

    public final class ImeBridge {
        @JavascriptInterface public String getGeneratorState() { return GeneratorStateStore.read(MahouInputMethodService.this); }
        @JavascriptInterface public String updateGeneratorState(String patch) {
            try { return GeneratorStateStore.update(MahouInputMethodService.this, patch); }
            catch (Exception error) { return "{\"error\":\"无法保存台词\"}"; }
        }
        @JavascriptInterface public String listAssets() {
            try { return AssetStore.listAssets(MahouInputMethodService.this); }
            catch (Exception error) { return "{\"backgrounds\":[],\"characters\":[],\"emotions\":[]}"; }
        }
        @JavascriptInterface public String getAppInfo() {
            try { return AssetStore.getAppInfo(MahouInputMethodService.this); }
            catch (Exception error) { return "{}"; }
        }
        @JavascriptInterface public void importCurrentDraft() { main.post(MahouInputMethodService.this::readDraft); }
        @JavascriptInterface public void requestInputTarget() { main.post(MahouInputMethodService.this::deliverTarget); }
        @JavascriptInterface public void shareToQQ(String dataUrl) { main.post(() -> send(dataUrl)); }
        @JavascriptInterface public void shareImage(String dataUrl) { main.post(() -> copy(dataUrl)); }
        @JavascriptInterface public void hidePanel() { main.post(() -> { if (!destroyed) requestHideSelf(0); }); }
        @JavascriptInterface public void previousKeyboard() { main.post(MahouInputMethodService.this::previousKeyboard); }
        @JavascriptInterface public void openCaptionEditor() { main.post(MahouInputMethodService.this::openCaptionEditor); }
        @JavascriptInterface public void showToast(String text) { main.post(() -> notice(text)); }
        @JavascriptInterface public void openMainEditor() {
            main.post(() -> {
                startActivity(new Intent(MahouInputMethodService.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                requestHideSelf(0);
            });
        }
        @JavascriptInterface public String getPopupPreference(String key) {
            if (!"uiTheme".equals(key) && !"qqExportSize".equals(key) && !"textKeyboard".equals(key)) return "";
            return getSharedPreferences("mahou_popup_prefs", MODE_PRIVATE).getString(key, "");
        }
        @JavascriptInterface public void setPopupPreference(String key, String value) {
            if ((!"uiTheme".equals(key) && !"qqExportSize".equals(key) && !"textKeyboard".equals(key)) || value == null || value.length() > 64) return;
            getSharedPreferences("mahou_popup_prefs", MODE_PRIVATE).edit().putString(key, value).apply();
        }
    }
}
