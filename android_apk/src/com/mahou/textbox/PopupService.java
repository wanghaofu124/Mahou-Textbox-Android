package com.mahou.textbox;

import android.app.Service;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PopupService extends Service {
    public static final String ACTION_RESTORE_POPUP = "com.mahou.textbox.RESTORE_POPUP";
    public static final String ACTION_STOP_POPUP = "com.mahou.textbox.STOP_POPUP";

    private static final String NOTIFICATION_CHANNEL_ID = "mahou_popup_active";
    private static final int NOTIFICATION_ID = 4001;
    private static final String SHARE_AUTHORITY = "com.mahou.textbox.share";
    private static final String PREFS_NAME = "mahou_popup_prefs";
    private static final int MIN_ALPHA_PERCENT = 70;
    private static final int MAX_ALPHA_PERCENT = 100;

    private static final String KEY_X = "popup_x";
    private static final String KEY_Y = "popup_y";
    private static final String KEY_WIDTH = "popup_width";
    private static final String KEY_HEIGHT = "popup_height";
    private static final String KEY_ALPHA = "popup_alpha";
    private static final String KEY_MINIMIZED = "popup_minimized";
    private static final String KEY_LAYOUT_VERSION = "popup_layout_version";
    private static final String KEY_FULL_X = "popup_full_x";
    private static final String KEY_FULL_Y = "popup_full_y";
    private static final String KEY_MINI_X = "popup_mini_x";
    private static final String KEY_MINI_Y = "popup_mini_y";
    private static final String ACTION_GENERATOR_STATE_CHANGED = "com.mahou.textbox.GENERATOR_STATE_CHANGED";
    private static final String ACTION_ASSETS_CHANGED = "com.mahou.textbox.ASSETS_CHANGED";
    private static final long EXPORT_TIMEOUT_MS = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService exportWorker = Executors.newSingleThreadExecutor();
    private final PopupExportGuard exportGuard = new PopupExportGuard();
    private final String shareSessionPrefix = UUID.randomUUID().toString();
    private WindowManager windowManager;
    private PopupRoot popupRoot;
    private LinearLayout fullView;
    private LinearLayout miniView;
    private WindowManager.LayoutParams params;
    private WebView webView;
    private LinearLayout popupTitleBar;
    private LinearLayout popupResizeBar;
    private LinearLayout opacityControls;
    private SeekBar opacitySeek;
    private TextView popupAlphaLabel;
    private SharedPreferences prefs;
    private PopupPlacement placement;
    private PopupGeometry.Bounds safeBounds;
    private volatile int alphaPercent;
    private volatile boolean destroyed;
    private boolean imeVisible;
    private boolean backPending;
    private boolean framePending;
    private int keyboardGeneration;
    private String pendingShareSession;
    private long pendingShareRequest;
    private long directSendRequest;
    private String lastNativeState;
    private boolean externalAssetActivity;
    private Runnable exportTimeoutRunnable;
    private AccessibleActionLayer accessibleActionLayer;
    private final Choreographer.FrameCallback layoutFrame = frameTimeNanos -> {
        framePending = false;
        updatePopupLayout();
    };

    private final BroadcastReceiver generatorStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_GENERATOR_STATE_CHANGED.equals(intent.getAction())) {
                if (isPopupContentVisible()) deliverGeneratorState(intent.getStringExtra("stateJson"));
            }
        }
    };

    private final BroadcastReceiver assetsChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!destroyed && webView != null && ACTION_ASSETS_CHANGED.equals(intent.getAction())) {
                webView.evaluateJavascript("window.receiveAssetsChanged&&window.receiveAssetsChanged();", null);
            }
        }
    };

    private final BroadcastReceiver imageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ImagePickerActivity.ACTION_IMAGE_PICKED.equals(intent.getAction())) {
                return;
            }
            String target = intent.getStringExtra(ImagePickerActivity.EXTRA_TARGET);
            if (target != null && !"popup".equals(target)) {
                return;
            }
            restoreAfterAssetActivity();
            if (intent.getBooleanExtra(ImagePickerActivity.EXTRA_CANCELLED, false)) return;
            String error = intent.getStringExtra(ImagePickerActivity.EXTRA_ERROR);
            if (error != null) {
                Toast.makeText(PopupService.this, "导入失败：" + error, Toast.LENGTH_LONG).show();
                return;
            }
            String dataUrl = intent.getStringExtra(ImagePickerActivity.EXTRA_DATA_URL);
            String imageUrl = intent.getStringExtra(ImagePickerActivity.EXTRA_IMAGE_URL);
            String kind = intent.getStringExtra(ImagePickerActivity.EXTRA_KIND);
            String ownerId = intent.getStringExtra(ImagePickerActivity.EXTRA_OWNER_ID);
            deliverPickedAsset(kind == null ? "background" : kind, ownerId, dataUrl, imageUrl);
        }
    };

    private final BroadcastReceiver shareReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (QqSendAccessibilityService.ACTION_RESULT.equals(intent.getAction())) {
                try {
                    JSONObject result = new JSONObject(intent.getStringExtra("result"));
                    if (!"working".equals(result.optString("state")) && directSendRequest != 0) {
                        exportGuard.finishExternal(directSendRequest);
                        directSendRequest = 0;
                        if (!destroyed && popupRoot != null) popupRoot.setVisibility(View.VISIBLE);
                        if (isPopupContentVisible()) deliverGeneratorState(GeneratorStateStore.read(PopupService.this));
                    }
                } catch (Exception ignored) {}
                return;
            }
            if (ShareProxyActivity.ACTION_SHARE_PROXY_OPENING.equals(intent.getAction())) {
                String session = intent.getStringExtra(ShareProxyActivity.EXTRA_SHARE_SESSION);
                if (pendingShareSession != null || destroyed || session == null) return;
                long request = exportGuard.beginDirectShare();
                if (request == 0 || !exportGuard.beginExternal(request)) return;
                dismissKeyboard();
                pendingShareSession = session;
                pendingShareRequest = request;
                PopupShareSessionStore.begin(PopupService.this, session, request);
                if (popupRoot != null) popupRoot.setVisibility(View.GONE);
                return;
            }
            if (!ShareProxyActivity.ACTION_SHARE_PROXY_CLOSED.equals(intent.getAction())) {
                return;
            }
            restoreAfterShareIntent(intent.getStringExtra(ShareProxyActivity.EXTRA_SHARE_SESSION));
        }
    };

    private final BroadcastReceiver assetTransferReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AssetTransferActivity.ACTION_ASSET_TRANSFER_RESULT.equals(intent.getAction())) {
                return;
            }
            String target = intent.getStringExtra(AssetTransferActivity.EXTRA_TARGET);
            if (target != null && !"popup".equals(target)) {
                return;
            }
            restoreAfterAssetActivity();
            deliverAssetTransferResult(intent.getStringExtra(AssetTransferActivity.EXTRA_RESULT_JSON));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        pendingShareSession = PopupShareSessionStore.pending(this);
        if (pendingShareSession != null) {
            pendingShareRequest = PopupShareSessionStore.request(this);
            if (!exportGuard.resumeExternal(pendingShareRequest)) {
                PopupShareSessionStore.complete(this, pendingShareSession);
                pendingShareSession = null;
            }
        }
        startPersistentNotification();
        if (InputMethodSupport.isImageMode(this)) { stopSelf(); return; }
        registerImageReceiver();
        registerShareReceiver();
        registerAssetTransferReceiver();
        ContextCompat.registerReceiver(this, generatorStateReceiver,
                new IntentFilter(ACTION_GENERATOR_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, assetsChangedReceiver,
                new IntentFilter(ACTION_ASSETS_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        showPopup();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (InputMethodSupport.isImageMode(this)) { stopSelf(); return START_NOT_STICKY; }
        if (popupRoot == null) {
            showPopup();
        }
        if (intent != null && ACTION_RESTORE_POPUP.equals(intent.getAction())) {
            // A notification restore must not cover an external share target still in use.
            if (pendingShareSession != null || directSendRequest != 0) return START_STICKY;
            externalAssetActivity = false;
            if (popupRoot != null) {
                popupRoot.setVisibility(View.VISIBLE);
            }
            setMinimized(false);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        exportGuard.cancel();
        cancelExportTimeout();
        exportWorker.shutdownNow();
        PopupShareSessionStore.complete(this, pendingShareSession);
        if (framePending) Choreographer.getInstance().removeFrameCallback(layoutFrame);
        setEditing(false);
        try {
            unregisterReceiver(imageReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(shareReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(assetTransferReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(generatorStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(assetsChangedReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (windowManager != null && popupRoot != null) {
            popupRoot.removeBackCallback();
            if (popupRoot.isAttachedToWindow()) windowManager.removeViewImmediate(popupRoot);
            popupRoot = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.stopLoading();
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (params == null) {
            return;
        }
        refreshWindowBounds(null);
        updatePopupLayout();
        saveWindowState();
    }

    private void startPersistentNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.popup_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("悬浮聊天生成器运行期间显示");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        int immutable = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openApp = PendingIntent.getActivity(
                this,
                10,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable
        );
        PendingIntent restore = PendingIntent.getBroadcast(
                this,
                11,
                new Intent(this, PopupActionReceiver.class).setAction(ACTION_RESTORE_POPUP),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable
        );
        PendingIntent stop = PendingIntent.getBroadcast(
                this,
                12,
                new Intent(this, PopupActionReceiver.class).setAction(ACTION_STOP_POPUP),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.popup_notification_title))
                .setContentText(getString(R.string.popup_notification_text))
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, getString(R.string.popup_restore), restore)
                .addAction(0, getString(R.string.popup_stop), stop);
        int serviceType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), serviceType);
    }

    private void showPopup() {
        if (destroyed || popupRoot != null) return;
        if (InputMethodSupport.isImageMode(this)) { stopSelf(); return; }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "没有悬浮窗权限，请重新授权。", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        refreshWindowBounds(null);
        loadWindowState();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(360), dp(400), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.alpha = 1f;
        params.softInputMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);

        popupRoot = new PopupRoot(this);
        popupRoot.setBackgroundColor(Color.TRANSPARENT);
        fullView = buildFullView();
        miniView = buildMiniView();
        popupRoot.addView(fullView, new FrameLayout.LayoutParams(-1, -1));
        popupRoot.addView(miniView, new FrameLayout.LayoutParams(-1, -1));
        ViewCompat.setOnApplyWindowInsetsListener(popupRoot, (view, insets) -> {
            refreshWindowBounds(insets);
            return insets;
        });
        popupRoot.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!destroyed && popupRoot != null) refreshWindowBounds(ViewCompat.getRootWindowInsets(popupRoot));
        });
        applyMode();
        if (pendingShareSession != null) popupRoot.setVisibility(View.GONE);
        try {
            windowManager.addView(popupRoot, params);
            applyPopupChromeTheme(ThemeChrome.loadThemeKey(prefs));
            ViewCompat.requestApplyInsets(popupRoot);
        } catch (RuntimeException error) {
            Log.e("MahouPopup", "Cannot attach overlay", error);
            stopSelf();
        }
    }

    private void applyPopupChromeTheme(String themeKey) {
        ThemeChrome.Palette palette = ThemeChrome.resolve(themeKey);
        // The content's ancestors stay transparent; only native chrome has a solid fill.
        if (popupTitleBar != null) popupTitleBar.setBackgroundColor(palette.panel);
        if (popupResizeBar != null) popupResizeBar.setBackgroundColor(palette.panel);
        if (opacityControls != null) opacityControls.setBackgroundColor(palette.panel);
        if (miniView != null) miniView.setBackgroundColor(Color.TRANSPARENT);
        if (webView != null) webView.setBackgroundColor(palette.webBg);
        tintChrome(popupTitleBar, palette.titleText);
        tintChrome(popupResizeBar, palette.titleText);
        tintChrome(opacityControls, palette.titleText);
        tintChrome(miniView, palette.panelButtonText);
        if (miniView != null && miniView.getChildCount() != 0) {
            miniView.getChildAt(0).setBackground(rippleBackground(palette.panel, true));
        }
    }

    private void tintChrome(ViewGroup container, int color) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof ImageView) ((ImageView) child).setImageTintList(ColorStateList.valueOf(color));
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
            if (child instanceof ViewGroup) tintChrome((ViewGroup) child, color);
        }
    }

    private RippleDrawable rippleBackground(int color, boolean round) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(color);
        fill.setCornerRadius(dp(round ? 28 : 4));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(round ? 28 : 4));
        return new RippleDrawable(ColorStateList.valueOf(0x44808080), fill, mask);
    }

    private ImageButton iconButton(int icon, String label) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(rippleBackground(Color.TRANSPARENT, false));
        button.setContentDescription(label);
        ViewCompat.setTooltipText(button, label);
        return button;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private LinearLayout buildFullView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);

        popupTitleBar = new LinearLayout(this);
        popupTitleBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout drag = new LinearLayout(this);
        drag.setGravity(Gravity.CENTER_VERTICAL);
        drag.setPadding(dp(8), 0, 0, 0);
        drag.setContentDescription("拖动悬浮面板");
        ImageView grip = new ImageView(this);
        grip.setImageResource(R.drawable.ic_popup_drag);
        grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        drag.addView(grip, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView title = new TextView(this);
        title.setText("魔裁文本框");
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        drag.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        popupTitleBar.addView(drag, new LinearLayout.LayoutParams(0, dp(48), 1));

        ImageButton settings = iconButton(R.drawable.ic_popup_settings, "生成设置");
        settings.setContentDescription("生成设置，长按调节内容透明度");
        settings.setOnClickListener(view -> openGeneratorSettings());
        settings.setOnLongClickListener(view -> {
            dismissKeyboard();
            opacityControls.setVisibility(opacityControls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        });
        ViewCompat.setTooltipText(settings, "生成设置；长按调节透明度");
        popupTitleBar.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton minimize = iconButton(R.drawable.ic_popup_minimize, "收起悬浮面板");
        minimize.setOnClickListener(view -> setMinimized(true));
        popupTitleBar.addView(minimize, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton close = iconButton(R.drawable.ic_popup_close, "关闭悬浮面板");
        close.setOnClickListener(view -> stopSelf());
        popupTitleBar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(popupTitleBar, new LinearLayout.LayoutParams(-1, dp(48)));

        opacityControls = new LinearLayout(this);
        opacityControls.setGravity(Gravity.CENTER_VERTICAL);
        opacityControls.setPadding(dp(12), 0, dp(8), 0);
        popupAlphaLabel = new TextView(this);
        popupAlphaLabel.setTextSize(12);
        popupAlphaLabel.setSingleLine(true);
        opacityControls.addView(popupAlphaLabel, new LinearLayout.LayoutParams(dp(100), -2));
        opacitySeek = new SeekBar(this);
        opacitySeek.setContentDescription("面板内容透明度，百分之七十到一百");
        opacitySeek.setMax(MAX_ALPHA_PERCENT - MIN_ALPHA_PERCENT);
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) setAlphaPercent(MIN_ALPHA_PERCENT + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { saveWindowState(); }
        });
        opacityControls.addView(opacitySeek, new LinearLayout.LayoutParams(0, dp(48), 1));
        opacityControls.setVisibility(View.GONE);
        root.addView(opacityControls, new LinearLayout.LayoutParams(-1, dp(48)));

        webView = new WebView(this) {
            @Override
            public void onWindowFocusChanged(boolean hasWindowFocus) {
                super.onWindowFocusChanged(hasWindowFocus);
                if (hasWindowFocus && placement.isEditing()) requestKeyboard();
            }
        };
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        WebSettings settingsWeb = webView.getSettings();
        settingsWeb.setJavaScriptEnabled(true);
        settingsWeb.setDomStorageEnabled(true);
        settingsWeb.setAllowFileAccess(false);
        settingsWeb.setAllowContentAccess(false);
        settingsWeb.setAllowFileAccessFromFileURLs(false);
        settingsWeb.setAllowUniversalAccessFromFileURLs(false);
        settingsWeb.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settingsWeb.setBuiltInZoomControls(false);
        settingsWeb.setDisplayZoomControls(false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress == 100 && !destroyed) {
                    deliverGeneratorState(GeneratorStateStore.read(PopupService.this));
                    lastNativeState = null;
                    publishNativeState();
                }
            }
        });
        webView.setWebViewClient(LocalWebContent.createClient(this, null));
        webView.addJavascriptInterface(new PopupBridge(), "AndroidBridge");
        webView.loadUrl(LocalWebContent.DOCK_URL + "?dock=1");
        applyContentAlpha();
        FrameLayout webFrame = new FrameLayout(this);
        webFrame.setBackgroundColor(Color.TRANSPARENT);
        webFrame.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        accessibleActionLayer = new AccessibleActionLayer(this, this::triggerWebAction);
        accessibleActionLayer.setVisibility(View.GONE);
        accessibleActionLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        webFrame.addView(accessibleActionLayer, new FrameLayout.LayoutParams(-1, -1));
        root.addView(webFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        popupResizeBar = new LinearLayout(this);
        popupResizeBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ImageButton resize = iconButton(R.drawable.ic_popup_resize, "拖动调整面板大小");
        popupResizeBar.addView(resize, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(popupResizeBar, new LinearLayout.LayoutParams(-1, dp(48)));
        attachGesture(drag, false);
        attachGesture(resize, true);
        return root;
    }

    private LinearLayout buildMiniView() {
        LinearLayout mini = new LinearLayout(this);
        ImageButton restore = iconButton(R.drawable.ic_popup_restore, "恢复悬浮面板，可拖动位置");
        restore.setPadding(dp(14), dp(14), dp(14), dp(14));
        restore.setOnClickListener(view -> setMinimized(false));
        restore.setOnLongClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, restore);
            menu.getMenu().add("发到 QQ").setOnMenuItemClickListener(item -> { requestExport(true); return true; });
            menu.getMenu().add("分享/保存").setOnMenuItemClickListener(item -> { requestExport(false); return true; });
            menu.getMenu().add("关闭悬浮面板").setOnMenuItemClickListener(item -> {
                stopSelf();
                return true;
            });
            menu.show();
            return true;
        });
        mini.addView(restore, new LinearLayout.LayoutParams(-1, -1));
        attachGesture(restore, false);
        return mini;
    }

    private void openGeneratorSettings() {
        dismissKeyboard();
        if (webView == null) return;
        webView.evaluateJavascript(
                "(function(){if(typeof window.openGeneratorSettings==='function'){window.openGeneratorSettings();return true;}return false;})()",
                handled -> {
                    if (!destroyed && "false".equals(handled)) opacityControls.setVisibility(View.VISIBLE);
                });
    }

    private void launchAssetActivity(Intent intent) {
        dismissKeyboard();
        externalAssetActivity = true;
        popupRoot.setVisibility(View.GONE);
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException error) {
            restoreAfterAssetActivity();
            Toast.makeText(this, "无法打开文件选择器，请重试。", Toast.LENGTH_LONG).show();
        }
    }

    private void restoreAfterAssetActivity() {
        if (!externalAssetActivity || destroyed) return;
        externalAssetActivity = false;
        if (popupRoot != null && pendingShareSession == null) popupRoot.setVisibility(View.VISIBLE);
        refreshWindowBounds(null);
        updatePopupLayout();
        if (isPopupContentVisible()) deliverGeneratorState(GeneratorStateStore.read(this));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachGesture(View handle, boolean resize) {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            private PopupGeometry.Window start;
            private PopupGeometry.Bounds gestureBounds;
            private float downX, downY;
            private boolean moving, longPressed;
            private final Runnable longPress = () -> {
                if (!destroyed && !moving && handle.isPressed()) {
                    longPressed = handle.performLongClick();
                    if (longPressed) handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            };

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (placement == null || destroyed) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        refreshWindowBounds(ViewCompat.getRootWindowInsets(popupRoot));
                        start = placement.visible();
                        gestureBounds = placement.usable();
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moving = longPressed = false;
                        view.setPressed(true);
                        if (!resize) mainHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (longPressed) return true;
                        if (!gestureBounds.sameAs(placement.usable())) {
                            gestureBounds = placement.usable();
                            start = placement.visible();
                            downX = event.getRawX();
                            downY = event.getRawY();
                        }
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (!moving && Math.hypot(dx, dy) > slop) {
                            moving = true;
                            mainHandler.removeCallbacks(longPress);
                            view.setPressed(false);
                            view.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                                    ? HapticFeedbackConstants.GESTURE_START : HapticFeedbackConstants.CLOCK_TICK);
                            // Rebase at the slop boundary so the first frame cannot jump.
                            start = placement.visible();
                            downX = event.getRawX();
                            downY = event.getRawY();
                            dx = dy = 0;
                        }
                        if (moving) {
                            PopupGeometry.Window next = resize
                                    ? PopupGeometry.resize(start, Math.round(dx), Math.round(dy),
                                            placement.usable(), dp(280), dp(300))
                                    : start.moveTo(start.x + Math.round(dx), start.y + Math.round(dy));
                            if (resize) placement.move(next);
                            else placement.dragTo(next.x, next.y);
                            scheduleLayout();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(longPress);
                        view.setPressed(false);
                        if (moving) {
                            if (!resize && event.getActionMasked() == MotionEvent.ACTION_UP) {
                                PopupGeometry.Window snapped = PopupGeometry.snap(placement.visible(), placement.usable(), dp(16));
                                placement.dragTo(snapped.x, snapped.y);
                            }
                            flushLayout();
                            saveWindowState();
                            view.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                                    ? HapticFeedbackConstants.GESTURE_END : HapticFeedbackConstants.CLOCK_TICK);
                        } else if (!longPressed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                            view.performClick();
                        }
                        moving = false;
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void scheduleLayout() {
        if (destroyed || framePending || popupRoot == null) return;
        framePending = true;
        Choreographer.getInstance().postFrameCallback(layoutFrame);
    }

    private void flushLayout() {
        if (framePending) Choreographer.getInstance().removeFrameCallback(layoutFrame);
        framePending = false;
        updatePopupLayout();
    }

    private void updatePopupLayout() {
        if (destroyed || params == null || placement == null || popupRoot == null) return;
        PopupGeometry.Window window = placement.visible();
        params.x = window.x;
        params.y = window.y;
        params.width = window.width;
        params.height = window.height;
        if (popupRoot.isAttachedToWindow()) windowManager.updateViewLayout(popupRoot, params);
        publishNativeState();
    }

    @SuppressWarnings("deprecation")
    private void refreshWindowBounds(WindowInsetsCompat rootInsets) {
        if (windowManager == null || destroyed) return;
        int width, height;
        Insets bars;
        int keyboardBottom = Integer.MAX_VALUE;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowMetrics metrics = windowManager.getMaximumWindowMetrics();
            Rect bounds = metrics.getBounds();
            width = bounds.width();
            height = bounds.height();
            WindowInsetsCompat insets = WindowInsetsCompat.toWindowInsetsCompat(metrics.getWindowInsets());
            bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            // Display-wide insets, not popup-local IME intersections, prevent resize feedback loops.
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            if (ime > bars.bottom) keyboardBottom = height - ime;
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            width = metrics.widthPixels;
            height = metrics.heightPixels;
            bars = rootInsets == null ? Insets.of(0, systemDimension("status_bar_height"), 0,
                    systemDimension("navigation_bar_height"))
                    : rootInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
        }
        PopupGeometry.Bounds safe = new PopupGeometry.Bounds(bars.left, bars.top,
                width - bars.right, height - bars.bottom);
        if (popupRoot != null && popupRoot.isShown()) {
            Rect visible = new Rect();
            popupRoot.getWindowVisibleDisplayFrame(visible);
            // The display frame is also available on pre-30 devices and observes other apps' IMEs.
            if (visible.width() >= width / 2 && visible.height() > dp(80)
                    && height - visible.bottom > Math.max(bars.bottom + dp(40), dp(100))) {
                keyboardBottom = Math.min(keyboardBottom, visible.bottom);
            }
        }
        PopupGeometry.Bounds usable = new PopupGeometry.Bounds(safe.left, safe.top, safe.right,
                Math.min(safe.bottom, Math.max(safe.top + 1, keyboardBottom)));
        boolean changed = safeBounds == null || !safe.sameAs(safeBounds)
                || placement != null && !usable.sameAs(placement.usable());
        safeBounds = safe;
        imeVisible = usable.bottom < safe.bottom;
        if (placement != null) {
            placement.setBounds(safe, usable);
            if (changed) {
                scheduleLayout();
                saveWindowState();
            }
        }
    }

    private int systemDimension(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id == 0 ? 0 : getResources().getDimensionPixelSize(id);
    }

    private void setEditing(boolean value) {
        if (placement == null || params == null) return;
        if (value && (destroyed || placement.isMinimized() || pendingShareSession != null)) return;
        boolean changed = placement.isEditing() != value;
        if (!changed) return;
        placement.setEditing(value);
        ++keyboardGeneration;
        updateInteractionFlags();
        if (!value) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && popupRoot != null) imm.hideSoftInputFromWindow(popupRoot.getWindowToken(), 0);
            if (webView != null && !destroyed) {
                webView.evaluateJavascript(
                        "(function(){var a=document.activeElement;if(a&&(a.tagName==='TEXTAREA'||a.tagName==='INPUT'||a.isContentEditable))a.blur();})()",
                        null);
                webView.clearFocus();
            }
        }
        flushLayout();
        if (value && webView != null) {
            webView.requestFocus();
            requestKeyboard();
        }
        publishNativeState();
    }

    private void requestKeyboard() {
        final int generation = keyboardGeneration;
        if (webView == null) return;
        webView.post(() -> {
            if (destroyed || !placement.isEditing() || generation != keyboardGeneration || webView == null
                    || !webView.hasWindowFocus()) return;
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void dismissKeyboard() {
        setEditing(false);
    }

    private void updateInteractionFlags() {
        int passive = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (!placement.isMinimized() && (placement.isEditing() || placement.isExpanded())) {
            params.flags &= ~passive;
        } else {
            params.flags |= passive;
        }
    }

    private void setEditorExpanded(boolean expanded) {
        if (placement == null || destroyed || placement.isMinimized()) return;
        placement.setExpanded(expanded);
        updateInteractionFlags();
        scheduleLayout();
        publishNativeState();
    }

    private void handleAndroidBack() {
        if (backPending || destroyed) return;
        backPending = true;
        if (webView == null) {
            backPending = false;
            dismissKeyboard();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return !!(window.handleAndroidBack&&window.handleAndroidBack());}catch(e){return false;}})()",
                handled -> {
                    backPending = false;
                    if (destroyed) return;
                    boolean editing = placement.isEditing() || imeVisible;
                    // Web overlays get first refusal, before IME dismissal or native collapse.
                    if (editing) dismissKeyboard();
                    if ("true".equals(handled)) return;
                    if (opacityControls.getVisibility() == View.VISIBLE) opacityControls.setVisibility(View.GONE);
                    else if (!editing) setMinimized(true);
                });
    }

    private void publishNativeState() {
        if (webView == null || placement == null || destroyed) return;
        try {
            JSONObject state = new JSONObject();
            state.put("imeVisible", imeVisible);
            state.put("editing", placement.isEditing());
            state.put("alpha", alphaPercent);
            state.put("mode", placement.isMinimized() ? "mini" : "full");
            state.put("editorExpanded", placement.isExpanded());
            String json = state.toString();
            if (json.equals(lastNativeState)) return;
            lastNativeState = json;
            webView.evaluateJavascript("window.setNativePopupState&&window.setNativePopupState("
                    + JSONObject.quote(json) + ");", null);
        } catch (Exception error) {
            if (BuildConfig.DEBUG) Log.d("MahouPopup", "Cannot publish popup state", error);
        }
    }

    private void requestExport(boolean preferQQ) {
        if (webView == null || destroyed) return;
        long request = exportGuard.beginExport();
        if (request == 0) return;
        scheduleExportTimeout(request);
        webView.evaluateJavascript(
                "(async function(){try{var data=await window.exportImageDataUrl("
                        + preferQQ + ");AndroidBridge.completePopupExport("
                        + JSONObject.quote(Long.toString(request)) + ",data," + preferQQ
                        + ");}catch(e){AndroidBridge.popupExportFailed("
                        + JSONObject.quote(Long.toString(request)) + ",String(e&&e.message||e));}})();", null);
    }

    private void scheduleExportTimeout(long request) {
        cancelExportTimeout();
        exportTimeoutRunnable = () -> failExport(request, "图片导出超时，请重试。");
        mainHandler.postDelayed(exportTimeoutRunnable, EXPORT_TIMEOUT_MS);
    }

    private void cancelExportTimeout() {
        if (exportTimeoutRunnable != null) mainHandler.removeCallbacks(exportTimeoutRunnable);
        exportTimeoutRunnable = null;
    }

    private void failExport(long request, String message) {
        if (destroyed || !exportGuard.fail(request)) return;
        cancelExportTimeout();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void acceptDirectShare(String dataUrl, boolean preferQQ) {
        if (destroyed) return;
        long request = exportGuard.beginDirectShare();
        if (request == 0) return;
        scheduleExportTimeout(request);
        writeAndShare(request, dataUrl, preferQQ);
    }

    private void writeAndShare(long request, String dataUrl, boolean preferQQ) {
        exportWorker.execute(() -> {
            File temporary = null;
            try {
                temporary = PopupShareImage.prepare(getApplicationContext(), dataUrl);
                if (destroyed || Thread.currentThread().isInterrupted()) {
                    PopupShareImage.discard(temporary);
                    return;
                }
                final File completed = temporary;
                mainHandler.post(() -> {
                    if (destroyed || !exportGuard.isWriting(request)) {
                        PopupShareImage.discard(completed);
                        return;
                    }
                    try {
                        PopupShareImage.publish(completed);
                        launchShare(request, preferQQ);
                    } catch (Exception error) {
                        PopupShareImage.discard(completed);
                        failExport(request, "分享失败：" + error.getMessage());
                    }
                });
            } catch (Exception error) {
                PopupShareImage.discard(temporary);
                mainHandler.post(() -> failExport(request, "分享失败：" + error.getMessage()));
            } catch (OutOfMemoryError error) {
                PopupShareImage.discard(temporary);
                mainHandler.post(() -> failExport(request, "图片过大，无法分享。请重试。"));
            }
        });
    }

    private void launchShare(long request, boolean preferQQ) {
        if (!exportGuard.beginExternal(request)) return;
        cancelExportTimeout();
        dismissKeyboard();
        pendingShareRequest = request;
        pendingShareSession = shareSessionPrefix + ":" + request;
        PopupShareSessionStore.begin(this, pendingShareSession, request);
        Intent proxy = preferQQ ? ShareProxyActivity.qqIntent(this, shareUri())
                : ShareProxyActivity.chooserIntent(this, shareUri());
        proxy.putExtra(ShareProxyActivity.EXTRA_SHARE_SESSION, pendingShareSession);
        proxy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        popupRoot.setVisibility(View.GONE);
        try {
            startActivity(proxy);
        } catch (RuntimeException error) {
            String session = pendingShareSession;
            restoreAfterShareIntent(session);
            Toast.makeText(this, "分享失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Uri shareUri() {
        return Uri.parse("content://" + SHARE_AUTHORITY + "/shared.png");
    }

    private void restoreAfterShareIntent(String session) {
        if (destroyed || session == null || !session.equals(pendingShareSession)
                || !exportGuard.finishExternal(pendingShareRequest)) return;
        PopupShareSessionStore.complete(this, session);
        pendingShareSession = null;
        pendingShareRequest = 0;
        if (popupRoot != null) popupRoot.setVisibility(View.VISIBLE);
        refreshWindowBounds(null);
        updatePopupLayout();
        if (isPopupContentVisible()) deliverGeneratorState(GeneratorStateStore.read(this));
    }

    private final class PopupRoot extends FrameLayout {
        private Object backDispatcher;
        private Object backCallback;

        PopupRoot(Context context) { super(context); }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            boolean outside = event.getActionMasked() == MotionEvent.ACTION_OUTSIDE
                    || event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && (event.getX() < 0 || event.getY() < 0 || event.getX() >= getWidth() || event.getY() >= getHeight());
            if (outside) {
                // An edge swipe belongs to system Back; don't drop focus before its callback.
                float x = event.getRawX();
                if (safeBounds != null && x > safeBounds.left + dp(24)
                        && x < safeBounds.right - dp(24)) dismissKeyboard();
                return false;
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (consumeBack(event)) return true;
            return super.dispatchKeyEventPreIme(event);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (consumeBack(event)) return true;
            return super.dispatchKeyEvent(event);
        }

        private boolean consumeBack(KeyEvent event) {
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) handleAndroidBack();
            return true;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (Build.VERSION.SDK_INT >= 33) post(this::installBackCallback);
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            if (Build.VERSION.SDK_INT >= 33) {
                removeBackCallback();
                if (hasWindowFocus) post(this::installBackCallback);
            }
        }

        @androidx.annotation.RequiresApi(33)
        private void installBackCallback() {
            if (destroyed || placement == null || placement.isMinimized()
                    || (!placement.isEditing() && !placement.isExpanded()) || !hasWindowFocus()) return;
            android.window.OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
            if (dispatcher == null || backCallback != null) return;
            android.window.OnBackInvokedCallback callback = PopupService.this::handleAndroidBack;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            backDispatcher = dispatcher;
            backCallback = callback;
        }

        void removeBackCallback() {
            if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
                ((android.window.OnBackInvokedDispatcher) backDispatcher)
                        .unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
                backDispatcher = backCallback = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            removeBackCallback();
            super.onDetachedFromWindow();
        }
    }

    private void triggerWebAction(String action) {
        if (action == null || destroyed) return;
        if ("minimize".equals(action)) { setMinimized(true); return; }
        if ("close".equals(action)) { stopSelf(); return; }
        if ("settings".equals(action)) { openGeneratorSettings(); return; }
        if ("qq".equals(action) || "share".equals(action)) {
            requestExport("qq".equals(action));
            return;
        }
        String targetId;
        if (action.startsWith("web:")) targetId = action.substring(4);
        else if ("random".equals(action)) targetId = "randomButton";
        else if ("addBackground".equals(action)) targetId = "addBackgroundButton";
        else if ("assetManager".equals(action)) targetId = "assetManagerButton";
        else if ("about".equals(action)) targetId = "aboutButton";
        else return;
        if (webView != null) {
            webView.evaluateJavascript("(function(){var el=document.getElementById("
                    + JSONObject.quote(targetId)
                    + ");if(el&&!el.disabled){el.click();}})();", null);
        }
    }

    private void postAlive(Runnable action) {
        if (!destroyed) mainHandler.post(() -> { if (!destroyed) action.run(); });
    }

    private long parseRequestId(String requestId) {
        try { return Long.parseLong(requestId); }
        catch (NumberFormatException error) { return 0; }
    }

    public class PopupBridge {
        @JavascriptInterface public String getDirectSendStatus() { return QqSendAccessibilityService.status(PopupService.this); }
        @JavascriptInterface public void openDirectSendSettings() { postAlive(() -> Toast.makeText(PopupService.this, "辅助发送已停用", Toast.LENGTH_SHORT).show()); }
        @JavascriptInterface public void cancelDirectSend() { postAlive(QqSendAccessibilityService::cancel); }
        @JavascriptInterface
        public void showToast(final String message) {
            postAlive(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(PopupService.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void shareImage(final String dataUrl) {
            postAlive(() -> acceptDirectShare(dataUrl, false));
        }

        @JavascriptInterface
        public void shareToQQ(final String dataUrl) {
            postAlive(() -> acceptDirectShare(dataUrl, true));
        }

        @JavascriptInterface
        public void requestPopupShare(boolean preferQQ) {
            postAlive(() -> requestExport(preferQQ));
        }

        @JavascriptInterface
        public void completePopupExport(String requestId, String dataUrl, boolean preferQQ) {
            postAlive(() -> {
                long request = parseRequestId(requestId);
                if (exportGuard.acceptExport(request)) writeAndShare(request, dataUrl, preferQQ);
            });
        }

        @JavascriptInterface
        public void popupExportFailed(String requestId, String message) {
            postAlive(() -> failExport(parseRequestId(requestId), "图片导出失败：" + message));
        }

        @JavascriptInterface
        public void miniExportFailed(final String message) {
            // Compatibility only: an untagged late failure cannot cancel a newer export.
            showToast("图片导出失败：" + message);
        }

        @JavascriptInterface
        public void setEditing(boolean editing) {
            postAlive(() -> PopupService.this.setEditing(editing));
        }

        @JavascriptInterface
        public void dismissKeyboard() {
            postAlive(PopupService.this::dismissKeyboard);
        }

        @JavascriptInterface
        public void setEditorExpanded(boolean expanded) {
            postAlive(() -> PopupService.this.setEditorExpanded(expanded));
        }

        @JavascriptInterface
        public void setPopupOpacity(int percent) {
            postAlive(() -> setAlphaPercent(percent));
        }

        @JavascriptInterface
        public int getPopupOpacity() { return alphaPercent; }

        @JavascriptInterface
        public String getGeneratorState() {
            try { return GeneratorStateStore.read(PopupService.this); }
            catch (Exception error) { return errorJson(error); }
        }

        @JavascriptInterface
        public String updateGeneratorState(String patchJson) {
            try { return GeneratorStateStore.update(PopupService.this, patchJson); }
            catch (Exception error) { return errorJson(error); }
        }

        @JavascriptInterface
        public void closePopup() { postAlive(PopupService.this::stopSelf); }

        @JavascriptInterface
        public void minimizePopup() { postAlive(() -> setMinimized(true)); }

        @JavascriptInterface
        public void startPopupMode() {
            postAlive(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(PopupService.this, "\u5f39\u7a97\u5df2\u7ecf\u6253\u5f00\u3002", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void pickBackgroundImage() {
            postAlive(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(PopupService.this, ImagePickerActivity.class);
                    intent.putExtra(ImagePickerActivity.EXTRA_TARGET, "popup");
                    intent.putExtra(ImagePickerActivity.EXTRA_KIND, "background");
                    launchAssetActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void pickAssetImage(final String kind, final String ownerId) {
            postAlive(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(PopupService.this, ImagePickerActivity.class);
                    intent.putExtra(ImagePickerActivity.EXTRA_TARGET, "popup");
                    intent.putExtra(ImagePickerActivity.EXTRA_KIND, kind);
                    intent.putExtra(ImagePickerActivity.EXTRA_OWNER_ID, ownerId);
                    launchAssetActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public String listAssets() {
            try {
                return AssetStore.listAssets(PopupService.this);
            } catch (Exception error) {
                Log.e("MahouAssets", "listAssets failed", error);
                return "{\"backgrounds\":[],\"characters\":[],\"emotions\":[]}";
            }
        }

        @JavascriptInterface
        public String saveAsset(String kind, String metadataJson, String imageDataUrl) {
            try {
                return AssetStore.saveAsset(PopupService.this, kind, metadataJson, imageDataUrl);
            } catch (Exception error) {
                Log.e("MahouAssets", "saveAsset failed", error);
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public String saveCharacterBundle(String characterJson, String emotionJson, String imageDataUrl) {
            try {
                return AssetStore.saveCharacterBundle(
                        PopupService.this,
                        characterJson,
                        emotionJson,
                        imageDataUrl
                );
            } catch (Exception error) {
                Log.e("MahouAssets", "saveCharacterBundle failed", error);
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public String updateAsset(String id, String metadataJson) {
            try {
                return AssetStore.updateAsset(PopupService.this, id, metadataJson);
            } catch (Exception error) {
                Log.e("MahouAssets", "updateAsset failed", error);
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public boolean deleteAsset(String kind, String id) {
            try {
                return AssetStore.deleteAsset(PopupService.this, kind, id);
            } catch (Exception error) {
                Log.e("MahouAssets", "deleteAsset failed", error);
                return false;
            }
        }

        @JavascriptInterface
        public void startAssetBackup(final String settingsJson) {
            postAlive(() -> {
                Intent intent = AssetTransferActivity.backupIntent(
                        PopupService.this,
                        "popup",
                        settingsJson
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launchAssetActivity(intent);
            });
        }

        @JavascriptInterface
        public void startAssetRestore() {
            postAlive(() -> {
                Intent intent = AssetTransferActivity.restoreIntent(PopupService.this, "popup")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launchAssetActivity(intent);
            });
        }

        @JavascriptInterface
        public String getAppInfo() {
            try {
                return AssetStore.getAppInfo(PopupService.this);
            } catch (Exception error) {
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public void reportAccessibleActions(final String layoutJson) {
            postAlive(() -> {
                if (accessibleActionLayer != null && webView != null) {
                    boolean nativeTree = webView.getAccessibilityNodeProvider() != null;
                    accessibleActionLayer.setVisibility(nativeTree ? View.GONE : View.VISIBLE);
                    accessibleActionLayer.setImportantForAccessibility(nativeTree
                            ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                    if (!nativeTree) accessibleActionLayer.updateActions(layoutJson);
                }
            });
        }

        @JavascriptInterface
        public void setPopupPreference(String key, String value) {
            if ((!"uiTheme".equals(key) && !"qqExportSize".equals(key) && !"textKeyboard".equals(key)) || value == null || value.length() > 64) return;
            prefs.edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public String getPopupPreference(String key) {
            if (!"uiTheme".equals(key) && !"qqExportSize".equals(key) && !"textKeyboard".equals(key)) return "";
            return prefs.getString(key, "");
        }

        @JavascriptInterface
        public void applyNativeChromeTheme(final String themeKey) {
            postAlive(new Runnable() {
                @Override
                public void run() {
                    applyPopupChromeTheme(themeKey);
                }
            });
        }
    }


    private void registerImageReceiver() {
        IntentFilter filter = new IntentFilter(ImagePickerActivity.ACTION_IMAGE_PICKED);
        ContextCompat.registerReceiver(this, imageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void registerShareReceiver() {
        IntentFilter filter = new IntentFilter(ShareProxyActivity.ACTION_SHARE_PROXY_CLOSED);
        filter.addAction(ShareProxyActivity.ACTION_SHARE_PROXY_OPENING);
        filter.addAction(QqSendAccessibilityService.ACTION_RESULT);
        ContextCompat.registerReceiver(this, shareReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void registerAssetTransferReceiver() {
        IntentFilter filter = new IntentFilter(AssetTransferActivity.ACTION_ASSET_TRANSFER_RESULT);
        ContextCompat.registerReceiver(this, assetTransferReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void deliverPickedAsset(String kind, String ownerId, String dataUrl, String imageUrl) {
        if (destroyed || webView == null) return;
        String source = dataUrl != null && !dataUrl.isEmpty() ? dataUrl : trustedPickedUrl(imageUrl);
        if (source == null) return;
        webView.evaluateJavascript("window.receivePickedAsset&&window.receivePickedAsset("
                + JSONObject.quote(kind) + "," + JSONObject.quote(ownerId == null ? "" : ownerId)
                + "," + JSONObject.quote(source) + ");", null);
    }

    private String trustedPickedUrl(String imageUrl) {
        if (imageUrl == null) return null;
        Uri uri = Uri.parse(imageUrl);
        return "https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null
                && uri.getPath() != null && uri.getPath().startsWith("/") ? imageUrl : null;
    }

    private void deliverGeneratorState(String stateJson) {
        if (destroyed || webView == null || stateJson == null) return;
        webView.evaluateJavascript("window.receiveGeneratorState&&window.receiveGeneratorState("
                + JSONObject.quote(stateJson) + ");", null);
    }

    private boolean isPopupContentVisible() {
        return !destroyed && webView != null && popupRoot != null
                && popupRoot.getVisibility() == View.VISIBLE && placement != null
                && !placement.isMinimized() && pendingShareSession == null && !externalAssetActivity;
    }

    private void deliverAssetTransferResult(String resultJson) {
        if (resultJson == null || webView == null) {
            return;
        }
        webView.evaluateJavascript(
                "window.receiveAssetTransferResult&&window.receiveAssetTransferResult("
                        + JSONObject.quote(resultJson) + ");",
                null
        );
    }

    private void setMinimized(boolean value) {
        if (placement == null || destroyed || pendingShareSession != null) return;
        if (value) dismissKeyboard();
        placement.setMinimized(value);
        updateInteractionFlags();
        if (opacityControls != null) opacityControls.setVisibility(View.GONE);
        applyMode();
        if (!value) deliverGeneratorState(GeneratorStateStore.read(this));
        saveWindowState();
    }

    private void applyMode() {
        if (fullView == null || miniView == null || params == null || placement == null) return;
        fullView.setVisibility(placement.isMinimized() ? View.GONE : View.VISIBLE);
        miniView.setVisibility(placement.isMinimized() ? View.VISIBLE : View.GONE);
        updatePopupLayout();
    }

    private void loadWindowState() {
        int layoutVersion = prefs.getInt(KEY_LAYOUT_VERSION, 0);
        boolean migrate = layoutVersion < 2;
        int oldX = prefs.getInt(KEY_X, dp(12));
        int oldY = prefs.getInt(KEY_Y, safeBounds.top + dp(40));
        int width = prefs.getInt(KEY_WIDTH, dp(360));
        int height = prefs.getInt(KEY_HEIGHT, dp(400));
        if (layoutVersion < 3 && height == dp(480)) height = dp(400);
        // 0.4 saved screen-wide dock dimensions and one shared position. Convert only once.
        PopupGeometry.Window full = PopupGeometry.migrateLegacy(migrate ? 0 : 1, new PopupGeometry.Window(
                migrate ? oldX : prefs.getInt(KEY_FULL_X, oldX),
                migrate ? oldY : prefs.getInt(KEY_FULL_Y, oldY), migrate ? dp(360) : width,
                migrate ? dp(400) : height), dp(360), dp(400));
        PopupGeometry.Window mini = new PopupGeometry.Window(
                migrate ? oldX : prefs.getInt(KEY_MINI_X, oldX),
                migrate ? oldY : prefs.getInt(KEY_MINI_Y, oldY), dp(56), dp(56));
        placement = new PopupPlacement(full, mini, prefs.getBoolean(KEY_MINIMIZED, false),
                dp(280), dp(300), dp(56), dp(8));
        placement.setBounds(safeBounds, safeBounds);
        alphaPercent = PopupGeometry.bounded(prefs.getInt(KEY_ALPHA, MAX_ALPHA_PERCENT),
                MIN_ALPHA_PERCENT, MAX_ALPHA_PERCENT);
        saveWindowState();
    }

    private void saveWindowState() {
        if (placement == null || prefs == null) return;
        PopupGeometry.Window full = placement.full();
        PopupGeometry.Window mini = placement.mini();
        prefs.edit().putInt(KEY_LAYOUT_VERSION, 3)
                .putInt(KEY_FULL_X, full.x).putInt(KEY_FULL_Y, full.y)
                .putInt(KEY_MINI_X, mini.x).putInt(KEY_MINI_Y, mini.y)
                .putInt(KEY_WIDTH, full.width).putInt(KEY_HEIGHT, full.height)
                .putInt(KEY_ALPHA, alphaPercent)
                .putBoolean(KEY_MINIMIZED, placement.isMinimized()).apply();
    }

    private void setAlphaPercent(int value) {
        alphaPercent = PopupGeometry.bounded(value, MIN_ALPHA_PERCENT, MAX_ALPHA_PERCENT);
        applyContentAlpha();
        prefs.edit().putInt(KEY_ALPHA, alphaPercent).apply();
        publishNativeState();
    }

    private void applyContentAlpha() {
        if (webView != null) webView.setAlpha(alphaPercent / 100f);
        if (popupAlphaLabel != null) {
            popupAlphaLabel.setText(alphaPercent + "%");
            popupAlphaLabel.setContentDescription("内容透明度百分之" + alphaPercent);
        }
        if (opacitySeek != null) opacitySeek.setProgress(alphaPercent - MIN_ALPHA_PERCENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String errorJson(Exception error) {
        try {
            JSONObject json = new JSONObject();
            json.put("error", error.getMessage());
            return json.toString();
        } catch (Exception ignored) {
            return "{\"error\":\"unknown\"}";
        }
    }

}
