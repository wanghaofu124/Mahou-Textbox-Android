package com.mahou.textbox;

import android.app.Service;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

public class PopupService extends Service {
    public static final String ACTION_RESTORE_POPUP = "com.mahou.textbox.RESTORE_POPUP";
    public static final String ACTION_STOP_POPUP = "com.mahou.textbox.STOP_POPUP";

    private static final String TAG_SHARE = "MahouShare";
    private static final String NOTIFICATION_CHANNEL_ID = "mahou_popup_active";
    private static final int NOTIFICATION_ID = 4001;
    private static final String SHARE_AUTHORITY = "com.mahou.textbox.share";
    private static final String SHARE_FILE_NAME = "mahou_textbox.png";
    private static final String SHARE_MIME = "image/png";
    private static final String PREFS_NAME = "mahou_popup_prefs";
    private static final int MIN_ALPHA_PERCENT = 70;
    private static final int MAX_ALPHA_PERCENT = 100;

    private static final String KEY_X = "popup_x";
    private static final String KEY_Y = "popup_y";
    private static final String KEY_WIDTH = "popup_width";
    private static final String KEY_HEIGHT = "popup_height";
    private static final String KEY_ALPHA = "popup_alpha";
    private static final String KEY_MINIMIZED = "popup_minimized";
    private static final long EXPORT_TIMEOUT_MS = 20000;
    private static final long SHARE_RESTORE_FALLBACK_MS = 30000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private FrameLayout popupRoot;
    private LinearLayout fullView;
    private LinearLayout miniView;
    private WindowManager.LayoutParams params;
    private WebView webView;
    private LinearLayout popupChromeRoot;
    private LinearLayout popupTitleBar;
    private LinearLayout popupResizeBar;
    private LinearLayout popupMiniView;
    private TextView popupTitleText;
    private TextView popupAlphaLabel;
    private TextView popupResizeText;
    private TextView popupMiniDragText;
    private SharedPreferences prefs;
    private int normalWidth;
    private int normalHeight;
    private int alphaPercent;
    private boolean minimized;
    private boolean minimizeAfterShareIntent;
    private boolean pendingShareRestore;
    private boolean exportingFromMini;
    private Runnable exportTimeoutRunnable;
    private Runnable shareRestoreFallbackRunnable;
    private AccessibleActionLayer accessibleActionLayer;
    private ShareState shareState = ShareState.IDLE;

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
            String error = intent.getStringExtra(ImagePickerActivity.EXTRA_ERROR);
            if (error != null) {
                Toast.makeText(PopupService.this, "导入失败：" + error, Toast.LENGTH_LONG).show();
                return;
            }
            String dataUrl = intent.getStringExtra(ImagePickerActivity.EXTRA_DATA_URL);
            String kind = intent.getStringExtra(ImagePickerActivity.EXTRA_KIND);
            String ownerId = intent.getStringExtra(ImagePickerActivity.EXTRA_OWNER_ID);
            deliverPickedAsset(kind == null ? "background" : kind, ownerId, dataUrl);
        }
    };

    private final BroadcastReceiver shareReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ShareProxyActivity.ACTION_SHARE_PROXY_CLOSED.equals(intent.getAction())) {
                return;
            }
            restoreAfterShareIntent();
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
            deliverAssetTransferResult(intent.getStringExtra(AssetTransferActivity.EXTRA_RESULT_JSON));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        startPersistentNotification();
        registerImageReceiver();
        registerShareReceiver();
        registerAssetTransferReceiver();
        showPopup();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (popupRoot == null) {
            showPopup();
        }
        if (intent != null && ACTION_RESTORE_POPUP.equals(intent.getAction())) {
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
        if (windowManager != null && popupRoot != null) {
            windowManager.removeView(popupRoot);
            popupRoot = null;
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
        normalWidth = clampWidth(normalWidth);
        normalHeight = clampHeight(normalHeight);
        params.width = minimized ? miniWidth() : normalWidth;
        params.height = minimized ? miniHeight() : normalHeight;
        updatePopupLayout(true);
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
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "\u6ca1\u6709\u60ac\u6d6e\u7a97\u6743\u9650\uff0c\u8bf7\u91cd\u65b0\u6388\u6743\u3002", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadWindowState();

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                minimized ? miniWidth() : normalWidth,
                minimized ? miniHeight() : normalHeight,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(KEY_X, dp(12));
        params.y = prefs.getInt(KEY_Y, dp(64));
        params.alpha = 1f;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        clampPosition();

        popupRoot = new FrameLayout(this);
        popupRoot.setBackgroundColor(Color.TRANSPARENT);

        fullView = buildFullView();
        miniView = buildMiniView();
        popupRoot.addView(fullView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        popupRoot.addView(miniView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        applyMode();

        windowManager.addView(popupRoot, params);
        applyPopupChromeTheme(ThemeChrome.loadThemeKey(prefs));
        if (webView != null) {
            webView.requestFocus(View.FOCUS_DOWN);
        }
    }

    private void applyPopupChromeTheme(String themeKey) {
        ThemeChrome.Palette palette = ThemeChrome.resolve(themeKey);
        if (popupChromeRoot != null) {
            popupChromeRoot.setBackgroundColor(palette.windowBg);
        }
        if (popupTitleBar != null) {
            popupTitleBar.setBackgroundColor(palette.panel);
        }
        if (popupResizeBar != null) {
            popupResizeBar.setBackgroundColor(palette.windowBg);
        }
        if (popupMiniView != null) {
            popupMiniView.setBackgroundColor(palette.panel);
        }
        if (webView != null) {
            webView.setBackgroundColor(palette.webBg);
        }
        if (popupTitleText != null) {
            popupTitleText.setTextColor(palette.titleText);
        }
        if (popupAlphaLabel != null) {
            popupAlphaLabel.setTextColor(palette.titleText);
        }
        if (popupResizeText != null) {
            popupResizeText.setTextColor(palette.titleText);
        }
        if (popupMiniDragText != null) {
            popupMiniDragText.setTextColor(palette.titleText);
        }
        applyPanelButtonColors(popupTitleBar, palette.panelButtonText);
        applyPanelButtonColors(popupMiniView, palette.panelButtonText);
    }

    private void applyPanelButtonColors(ViewGroup container, int textColor) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof Button) {
                ((Button) child).setTextColor(textColor);
            } else if (child instanceof ViewGroup) {
                applyPanelButtonColors((ViewGroup) child, textColor);
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private LinearLayout buildFullView() {
        LinearLayout root = new LinearLayout(this);
        popupChromeRoot = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(23, 24, 29));

        LinearLayout titleBar = new LinearLayout(this);
        popupTitleBar = titleBar;
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(10), 0, dp(6), 0);
        titleBar.setBackgroundColor(Color.rgb(36, 38, 49));

        TextView title = new TextView(this);
        popupTitleText = title;
        title.setText("\u9b54\u88c1\u6587\u672c\u6846 - \u62d6\u52a8");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setSingleLine(true);
        title.setContentDescription("\u62d6\u52a8\u60ac\u6d6e\u7a97");
        titleBar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        TextView alphaLabel = new TextView(this);
        popupAlphaLabel = alphaLabel;
        alphaLabel.setText("\u900f\u660e");
        alphaLabel.setTextColor(Color.WHITE);
        alphaLabel.setGravity(Gravity.CENTER);
        alphaLabel.setTextSize(12);
        alphaLabel.setContentDescription("\u900f\u660e\u5ea6");
        titleBar.addView(alphaLabel, new LinearLayout.LayoutParams(dp(42), dp(44)));

        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setContentDescription("\u8c03\u6574\u60ac\u6d6e\u7a97\u900f\u660e\u5ea6");
        alphaSeek.setMax(MAX_ALPHA_PERCENT - MIN_ALPHA_PERCENT);
        alphaSeek.setProgress(alphaPercent - MIN_ALPHA_PERCENT);
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                setAlphaPercent(MIN_ALPHA_PERCENT + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveWindowState();
            }
        });
        titleBar.addView(alphaSeek, new LinearLayout.LayoutParams(dp(84), dp(44)));

        Button minimize = compactButton("\u2212");
        minimize.setContentDescription("\u6700\u5c0f\u5316\u60ac\u6d6e\u7a97");
        minimize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMinimized(true);
            }
        });
        titleBar.addView(minimize, new LinearLayout.LayoutParams(dp(40), dp(36)));

        Button close = compactButton("\u00d7");
        close.setContentDescription("\u5173\u95ed\u60ac\u6d6e\u7a97");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopSelf();
            }
        });
        titleBar.addView(close, new LinearLayout.LayoutParams(dp(40), dp(36)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(23, 24, 29));
        applyContentAlpha();
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (BuildConfig.DEBUG) {
            webView.setWebChromeClient(new WebChromeClient());
        }
        webView.setWebViewClient(LocalWebContent.createClient(this, null));
        webView.addJavascriptInterface(new PopupBridge(), "AndroidBridge");
        webView.loadUrl(LocalWebContent.START_URL + "?popup=1");

        LinearLayout resizeBar = new LinearLayout(this);
        popupResizeBar = resizeBar;
        resizeBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resizeBar.setPadding(0, 0, dp(4), 0);
        resizeBar.setBackgroundColor(Color.rgb(23, 24, 29));
        TextView resize = new TextView(this);
        popupResizeText = resize;
        resize.setText("\u2198");
        resize.setTextColor(Color.WHITE);
        resize.setGravity(Gravity.CENTER);
        resize.setTextSize(18);
        resize.setContentDescription("\u62d6\u52a8\u7f29\u653e\u60ac\u6d6e\u7a97");
        resizeBar.addView(resize, new LinearLayout.LayoutParams(dp(42), dp(26)));

        root.addView(titleBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        FrameLayout webFrame = new FrameLayout(this);
        webFrame.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        accessibleActionLayer = new AccessibleActionLayer(this, new AccessibleActionLayer.ActionHandler() {
            @Override
            public void onAction(String action) {
                triggerWebAction(action);
            }
        });
        webFrame.addView(accessibleActionLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        root.addView(webFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        root.addView(resizeBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26)
        ));

        attachDrag(title);
        attachResize(resize);
        return root;
    }

    private LinearLayout buildMiniView() {
        LinearLayout mini = new LinearLayout(this);
        popupMiniView = mini;
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(4), dp(4), dp(4), dp(4));
        mini.setBackgroundColor(Color.rgb(36, 38, 49));

        TextView drag = new TextView(this);
        popupMiniDragText = drag;
        drag.setText("\u2630");
        drag.setTextColor(Color.WHITE);
        drag.setGravity(Gravity.CENTER);
        drag.setTextSize(18);
        mini.addView(drag, new LinearLayout.LayoutParams(dp(26), dp(40)));

        Button restore = compactButton("\u21ba");
        restore.setContentDescription("\u6062\u590d\u5b8c\u6574\u60ac\u6d6e\u7a97");
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMinimized(false);
            }
        });
        mini.addView(restore, new LinearLayout.LayoutParams(dp(34), dp(40)));

        Button qq = compactButton("QQ");
        qq.setContentDescription("\u53d1\u5230 QQ");
        qq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportFromMini(true);
            }
        });
        mini.addView(qq, new LinearLayout.LayoutParams(dp(34), dp(40)));

        Button share = compactButton("\u21e7");
        share.setContentDescription("\u5206\u4eab\u6216\u4fdd\u5b58");
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportFromMini(false);
            }
        });
        mini.addView(share, new LinearLayout.LayoutParams(dp(34), dp(40)));

        Button close = compactButton("\u00d7");
        close.setContentDescription("\u5173\u95ed\u60ac\u6d6e\u7a97");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopSelf();
            }
        });
        mini.addView(close, new LinearLayout.LayoutParams(dp(34), dp(40)));

        attachDrag(drag);
        return mini;
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void exportFromMini(final boolean qqPreferred) {
        if (BuildConfig.DEBUG) {
            Log.i("MahouTest", "popup_mini_export_start qq=" + qqPreferred);
        }
        minimizeAfterShareIntent = true;
        if (popupRoot == null || webView == null) {
            Toast.makeText(this, "生成器仍在加载中。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (exportingFromMini || shareState != ShareState.IDLE) {
            return;
        }
        Toast.makeText(this, "正在生成图片…", Toast.LENGTH_SHORT).show();
        exportingFromMini = true;
        shareState = ShareState.EXPORTING;
        scheduleExportTimeout();
        exportAndShare(qqPreferred);
    }

    private void updatePopupLayout(boolean clamp) {
        if (windowManager == null || popupRoot == null || params == null) {
            return;
        }
        if (clamp) {
            clampPosition();
        }
        if (popupRoot.getWindowToken() != null) {
            windowManager.updateViewLayout(popupRoot, params);
        }
    }

    private void scheduleExportTimeout() {
        cancelExportTimeout();
        exportTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!exportingFromMini) {
                    return;
                }
                Toast.makeText(PopupService.this, "图片导出超时。", Toast.LENGTH_LONG).show();
                clearExportFromMiniLock();
            }
        };
        mainHandler.postDelayed(exportTimeoutRunnable, EXPORT_TIMEOUT_MS);
    }

    private void cancelExportTimeout() {
        if (exportTimeoutRunnable != null) {
            mainHandler.removeCallbacks(exportTimeoutRunnable);
            exportTimeoutRunnable = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + Math.round(event.getRawX() - downX);
                        params.y = startY + Math.round(event.getRawY() - downY);
                        clampPosition();
                        windowManager.updateViewLayout(popupRoot, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        saveWindowState();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachResize(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int startWidth;
            private int startHeight;
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startWidth = params.width;
                        startHeight = params.height;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        normalWidth = clampWidth(startWidth + Math.round(event.getRawX() - downX));
                        normalHeight = clampHeight(startHeight + Math.round(event.getRawY() - downY));
                        params.width = normalWidth;
                        params.height = normalHeight;
                        clampPosition();
                        windowManager.updateViewLayout(popupRoot, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        saveWindowState();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void exportAndShare(final boolean preferQQ) {
        if (webView == null) {
            Toast.makeText(this, "生成器仍在加载中。", Toast.LENGTH_SHORT).show();
            clearExportFromMiniLock();
            return;
        }
        String bridgeMethod = preferQQ ? "shareToQQ" : "shareImage";
        webView.evaluateJavascript(
                "(async function(){try{var data=await window.exportImageDataUrl("
                        + (preferQQ ? "true" : "false")
                        + ");AndroidBridge."
                        + bridgeMethod
                        + "(data);}catch(e){AndroidBridge.miniExportFailed(String(e&&e.message||e));}})();",
                null
        );
    }

    private void clearExportFromMiniLock() {
        exportingFromMini = false;
        if (shareState == ShareState.EXPORTING) {
            shareState = ShareState.IDLE;
        }
    }

    private void prepareForExternalAction() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && popupRoot != null) {
                imm.hideSoftInputFromWindow(popupRoot.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
        pendingShareRestore = true;
        shareState = ShareState.EXTERNAL_SHARE;
        if (popupRoot != null) {
            popupRoot.setVisibility(View.GONE);
        }
    }

    private void scheduleShareRestoreFallback() {
        cancelShareRestoreFallback();
        shareRestoreFallbackRunnable = new Runnable() {
            @Override
            public void run() {
                shareRestoreFallbackRunnable = null;
                restoreAfterShareIntent();
            }
        };
        mainHandler.postDelayed(shareRestoreFallbackRunnable, SHARE_RESTORE_FALLBACK_MS);
    }

    private void cancelShareRestoreFallback() {
        if (shareRestoreFallbackRunnable != null) {
            mainHandler.removeCallbacks(shareRestoreFallbackRunnable);
            shareRestoreFallbackRunnable = null;
        }
    }

    private void triggerWebAction(String action) {
        if ("minimize".equals(action)) {
            setMinimized(true);
            return;
        }
        if ("close".equals(action)) {
            stopSelf();
            return;
        }
        String targetId;
        if ("random".equals(action)) {
            targetId = "randomButton";
        } else if ("addBackground".equals(action)) {
            targetId = "addBackgroundButton";
        } else if ("assetManager".equals(action)) {
            targetId = "assetManagerButton";
        } else if ("qq".equals(action)) {
            targetId = "qqButton";
        } else if ("share".equals(action)) {
            targetId = "shareButton";
        } else if ("about".equals(action)) {
            targetId = "aboutButton";
        } else {
            return;
        }
        if (webView != null) {
            webView.evaluateJavascript("(function(){var el=document.getElementById("
                    + JSONObject.quote(targetId)
                    + "); if(el){ el.click(); }})()", null);
        }
    }

    public class PopupBridge {
        @JavascriptInterface
        public void showToast(final String message) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(PopupService.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void shareImage(final String dataUrl) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    cancelExportTimeout();
                    clearExportFromMiniLock();
                    shareDataUrl(dataUrl);
                }
            });
        }

        @JavascriptInterface
        public void shareToQQ(final String dataUrl) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    cancelExportTimeout();
                    clearExportFromMiniLock();
                    shareDataUrlToQQ(dataUrl);
                }
            });
        }

        @JavascriptInterface
        public void miniExportFailed(final String message) {
            mainHandler.post(() -> {
                cancelExportTimeout();
                clearExportFromMiniLock();
                String detail = message == null || message.isEmpty() ? "未知错误" : message;
                Toast.makeText(PopupService.this, "图片导出失败：" + detail, Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void startPopupMode() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(PopupService.this, "\u5f39\u7a97\u5df2\u7ecf\u6253\u5f00\u3002", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void pickBackgroundImage() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    setMinimized(true);
                    Intent intent = new Intent(PopupService.this, ImagePickerActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(ImagePickerActivity.EXTRA_TARGET, "popup");
                    intent.putExtra(ImagePickerActivity.EXTRA_KIND, "background");
                    startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void pickAssetImage(final String kind, final String ownerId) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    setMinimized(true);
                    Intent intent = new Intent(PopupService.this, ImagePickerActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(ImagePickerActivity.EXTRA_TARGET, "popup");
                    intent.putExtra(ImagePickerActivity.EXTRA_KIND, kind);
                    intent.putExtra(ImagePickerActivity.EXTRA_OWNER_ID, ownerId);
                    startActivity(intent);
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
            mainHandler.post(() -> {
                setMinimized(true);
                Intent intent = AssetTransferActivity.backupIntent(
                        PopupService.this,
                        "popup",
                        settingsJson
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
        }

        @JavascriptInterface
        public void startAssetRestore() {
            mainHandler.post(() -> {
                setMinimized(true);
                Intent intent = AssetTransferActivity.restoreIntent(PopupService.this, "popup")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
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
            mainHandler.post(() -> {
                if (accessibleActionLayer != null) {
                    accessibleActionLayer.updateActions(layoutJson);
                }
            });
        }

        @JavascriptInterface
        public void setPopupPreference(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public String getPopupPreference(String key) {
            return prefs.getString(key, "");
        }

        @JavascriptInterface
        public void applyNativeChromeTheme(final String themeKey) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    applyPopupChromeTheme(themeKey);
                }
            });
        }
    }

    private void shareDataUrl(String dataUrl) {
        try {
            Uri uri = saveShareImage(dataUrl);
            Intent proxy = ShareProxyActivity.chooserIntent(this, uri);
            proxy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            prepareForExternalAction();
            startActivity(proxy);
            scheduleShareRestoreFallback();
        } catch (Exception error) {
            restoreAfterShareIntent();
            Log.e(TAG_SHARE, "popup shareImage failed", error);
            Toast.makeText(PopupService.this, "分享失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareDataUrlToQQ(String dataUrl) {
        try {
            Uri uri = saveShareImage(dataUrl);
            Intent proxy = ShareProxyActivity.qqIntent(this, uri);
            proxy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            prepareForExternalAction();
            startActivity(proxy);
            scheduleShareRestoreFallback();
        } catch (ActivityNotFoundException notFound) {
            shareToTimOrChooser(dataUrl);
        } catch (Exception error) {
            restoreAfterShareIntent();
            Log.e(TAG_SHARE, "popup shareToQQ failed", error);
            Toast.makeText(PopupService.this, "发到 QQ 失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareToTimOrChooser(String dataUrl) {
        try {
            Uri uri = saveShareImage(dataUrl);
            Intent intent = buildShareIntent(uri);
            intent.setPackage("com.tencent.tim");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            grantUriPermission("com.tencent.tim", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            prepareForExternalAction();
            startActivity(intent);
            scheduleShareRestoreFallback();
        } catch (ActivityNotFoundException notFound) {
            shareDataUrl(dataUrl);
        } catch (Exception error) {
            restoreAfterShareIntent();
            Log.e(TAG_SHARE, "popup shareToTim failed", error);
            Toast.makeText(PopupService.this, "QQ/TIM share target was not found.", Toast.LENGTH_LONG).show();
        }
    }

    private void restoreAfterShareIntent() {
        if (!pendingShareRestore && shareState != ShareState.EXTERNAL_SHARE) {
            return;
        }
        cancelShareRestoreFallback();
        if (popupRoot != null) {
            popupRoot.setVisibility(View.VISIBLE);
        }
        pendingShareRestore = false;
        shareState = ShareState.RESTORED;
        if (minimizeAfterShareIntent) {
            minimizeAfterShareIntent = false;
            setMinimized(true);
        }
        if (webView != null) {
            webView.requestFocus();
        }
        shareState = ShareState.IDLE;
    }

    private Uri saveShareImage(String dataUrl) throws Exception {
        int commaIndex = dataUrl.indexOf(',');
        String base64 = commaIndex >= 0 ? dataUrl.substring(commaIndex + 1) : dataUrl;
        byte[] image = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
        if (bitmap == null) {
            throw new IllegalArgumentException("Cannot decode generated image.");
        }
        Log.i(TAG_SHARE, "share png size: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        File dir = new File(getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create share cache directory.");
        }

        File outFile = new File(dir, SHARE_FILE_NAME);
        FileOutputStream output = new FileOutputStream(outFile);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("Cannot encode PNG share image.");
            }
        } finally {
            output.close();
            bitmap.recycle();
        }

        return Uri.parse("content://" + SHARE_AUTHORITY + "/shared.png");
    }

    private Intent buildShareIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(SHARE_MIME);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newUri(getContentResolver(), SHARE_FILE_NAME, uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private Intent buildShareChooser(Intent intent, Uri uri) {
        Intent chooser = Intent.createChooser(intent, "Share Mahou textbox");
        chooser.putExtra(Intent.EXTRA_STREAM, uri);
        chooser.setClipData(ClipData.newUri(getContentResolver(), SHARE_FILE_NAME, uri));
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooser;
    }

    private void registerImageReceiver() {
        IntentFilter filter = new IntentFilter(ImagePickerActivity.ACTION_IMAGE_PICKED);
        ContextCompat.registerReceiver(this, imageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void registerShareReceiver() {
        IntentFilter filter = new IntentFilter(ShareProxyActivity.ACTION_SHARE_PROXY_CLOSED);
        ContextCompat.registerReceiver(this, shareReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void registerAssetTransferReceiver() {
        IntentFilter filter = new IntentFilter(AssetTransferActivity.ACTION_ASSET_TRANSFER_RESULT);
        ContextCompat.registerReceiver(this, assetTransferReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void deliverPickedAsset(String kind, String ownerId, String dataUrl) {
        if (dataUrl == null || webView == null) {
            return;
        }
        String script = "window.receivePickedAsset && window.receivePickedAsset("
                + JSONObject.quote(kind)
                + ","
                + JSONObject.quote(ownerId == null ? "" : ownerId)
                + ","
                + JSONObject.quote(dataUrl)
                + ");";
        webView.evaluateJavascript(script, null);
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
        minimized = value;
        applyMode();
        saveWindowState();
    }

    private void applyMode() {
        if (fullView == null || miniView == null || params == null) {
            return;
        }
        fullView.setVisibility(minimized ? View.GONE : View.VISIBLE);
        miniView.setVisibility(minimized ? View.VISIBLE : View.GONE);
        params.width = minimized ? miniWidth() : normalWidth;
        params.height = minimized ? miniHeight() : normalHeight;
        updatePopupLayout(true);
    }

    private void loadWindowState() {
        normalWidth = clampWidth(prefs.getInt(KEY_WIDTH, dp(360)));
        normalHeight = clampHeight(prefs.getInt(KEY_HEIGHT, dp(580)));
        int storedAlpha = prefs.getInt(KEY_ALPHA, MAX_ALPHA_PERCENT);
        alphaPercent = Math.max(MIN_ALPHA_PERCENT, Math.min(MAX_ALPHA_PERCENT, storedAlpha));
        if (storedAlpha != alphaPercent) {
            prefs.edit().putInt(KEY_ALPHA, alphaPercent).apply();
        }
        minimized = prefs.getBoolean(KEY_MINIMIZED, false);
    }

    private void saveWindowState() {
        if (params == null || prefs == null) {
            return;
        }
        prefs.edit()
                .putInt(KEY_X, params.x)
                .putInt(KEY_Y, params.y)
                .putInt(KEY_WIDTH, normalWidth)
                .putInt(KEY_HEIGHT, normalHeight)
                .putInt(KEY_ALPHA, alphaPercent)
                .putBoolean(KEY_MINIMIZED, minimized)
                .apply();
    }

    private void setAlphaPercent(int value) {
        alphaPercent = Math.max(MIN_ALPHA_PERCENT, Math.min(MAX_ALPHA_PERCENT, value));
        applyContentAlpha();
        if (params != null) {
            params.alpha = 1f;
            if (windowManager != null && popupRoot != null) {
                windowManager.updateViewLayout(popupRoot, params);
            }
        }
    }

    private void applyContentAlpha() {
        if (webView != null) {
            webView.setAlpha(alphaPercent / 100f);
        }
    }

    private int clampWidth(int width) {
        return Math.max(dp(260), Math.min(width, screenWidth() - dp(16)));
    }

    private int clampHeight(int height) {
        return Math.max(dp(220), Math.min(height, screenHeight() - dp(48)));
    }

    private void clampPosition() {
        int maxX = Math.max(0, screenWidth() - params.width);
        int maxY = Math.max(0, screenHeight() - params.height);
        params.x = Math.max(0, Math.min(params.x, maxX));
        params.y = Math.max(0, Math.min(params.y, maxY));
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int miniWidth() {
        return dp(170);
    }

    private int miniHeight() {
        return dp(48);
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

    private enum ShareState {
        IDLE,
        EXPORTING,
        EXTERNAL_SHARE,
        RESTORED
    }
}
