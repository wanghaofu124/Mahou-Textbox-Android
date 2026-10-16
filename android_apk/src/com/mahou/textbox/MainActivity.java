package com.mahou.textbox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends ComponentActivity {
    public static final String EXTRA_TEST_ACTION = "mahou_test_action";
    public static final String EXTRA_TEST_THEME_STEPS = "mahou_test_theme_steps";
    public static final String EXTRA_TEST_QQ_STEPS = "mahou_test_qq_steps";

    private static final String TAG_SHARE = "MahouShare";
    private static final String TAG_TEST = "MahouTest";
    private static final String SHARE_AUTHORITY = "com.mahou.textbox.share";
    private static final String SHARE_FILE_NAME = "mahou_textbox.png";
    private static final String SHARE_MIME = "image/png";
    private static final String PREFS_NAME = "mahou_popup_prefs";
    private static final String ACTION_ASSETS_CHANGED = "com.mahou.textbox.ASSETS_CHANGED";

    private WebView webView;
    private AccessibleActionLayer accessibleActionLayer;
    private boolean startPopupAfterNotificationRequest;
    private boolean editing;
    private boolean imeVisible;
    private boolean activityResumed;
    private long deliveredStateRevision = -1L;
    private final ExecutorService exportWorker = Executors.newSingleThreadExecutor();
    private boolean exportBusy;
    private volatile boolean destroyed;

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (!granted) {
                    Toast.makeText(
                            this,
                            "通知权限未开启，悬浮窗仍可使用，但后台运行提示可能不明显。",
                            Toast.LENGTH_LONG
                    ).show();
                }
                if (startPopupAfterNotificationRequest) {
                    startPopupAfterNotificationRequest = false;
                    startPopupService();
                }
            }
    );
    private final BroadcastReceiver imageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ImagePickerActivity.ACTION_IMAGE_PICKED.equals(intent.getAction())) {
                return;
            }
            String target = intent.getStringExtra(ImagePickerActivity.EXTRA_TARGET);
            if (target != null && !"main".equals(target)) {
                return;
            }
            if (intent.getBooleanExtra(ImagePickerActivity.EXTRA_CANCELLED, false)) return;
            String error = intent.getStringExtra(ImagePickerActivity.EXTRA_ERROR);
            if (error != null) {
                Toast.makeText(MainActivity.this, "导入失败：" + error, Toast.LENGTH_LONG).show();
                return;
            }
            String imageUrl = intent.getStringExtra(ImagePickerActivity.EXTRA_IMAGE_URL);
            String kind = intent.getStringExtra(ImagePickerActivity.EXTRA_KIND);
            String ownerId = intent.getStringExtra(ImagePickerActivity.EXTRA_OWNER_ID);
            deliverPickedAsset(kind == null ? "background" : kind, ownerId, imageUrl);
        }
    };

    private final BroadcastReceiver generatorStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (GeneratorStateStore.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                if (activityResumed) deliverGeneratorState(intent.getStringExtra(GeneratorStateStore.EXTRA_STATE_JSON));
            } else if (ACTION_ASSETS_CHANGED.equals(intent.getAction()) && webView != null) {
                webView.evaluateJavascript("window.receiveAssetsChanged&&window.receiveAssetsChanged();", null);
            }
        }
    };

    private final BroadcastReceiver assetTransferReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AssetTransferActivity.ACTION_ASSET_TRANSFER_RESULT.equals(intent.getAction())) {
                return;
            }
            String target = intent.getStringExtra(AssetTransferActivity.EXTRA_TARGET);
            if (target != null && !"main".equals(target)) {
                return;
            }
            deliverAssetTransferResult(intent.getStringExtra(AssetTransferActivity.EXTRA_RESULT_JSON));
        }
    };

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        accessibleActionLayer = new AccessibleActionLayer(this, new AccessibleActionLayer.ActionHandler() {
            @Override
            public void onAction(String action) {
                triggerWebAction(action);
            }
        });
        accessibleActionLayer.setVisibility(View.GONE);
        accessibleActionLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.addView(accessibleActionLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        applyWindowChromeTheme(ThemeChrome.loadThemeKey(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        ));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

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
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBlockNetworkLoads(true);

        if (BuildConfig.DEBUG) {
            webView.setWebChromeClient(new WebChromeClient());
        }
        webView.setWebViewClient(LocalWebContent.createClient(this, () -> {
            deliveredStateRevision = -1L;
            if (activityResumed) deliverGeneratorState(GeneratorStateStore.read(this));
            scheduleTestIntentIfNeeded();
        }));
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        registerImageReceiver();
        registerAssetTransferReceiver();
        IntentFilter dataChanges = new IntentFilter(GeneratorStateStore.ACTION_STATE_CHANGED);
        dataChanges.addAction(ACTION_ASSETS_CHANGED);
        ContextCompat.registerReceiver(this, generatorStateReceiver, dataChanges,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        webView.loadUrl(LocalWebContent.START_URL);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        scheduleTestIntentIfNeeded();
    }

    private boolean isDebugBuild() {
        return BuildConfig.DEBUG;
    }

    private void scheduleTestIntentIfNeeded() {
        if (!isDebugBuild() || webView == null) {
            return;
        }
        Intent intent = getIntent();
        if (intent == null || intent.getStringExtra(EXTRA_TEST_ACTION) == null) {
            return;
        }
        final String action = intent.getStringExtra(EXTRA_TEST_ACTION);
        final int themeSteps = intent.getIntExtra(EXTRA_TEST_THEME_STEPS, 1);
        final int qqSteps = intent.getIntExtra(EXTRA_TEST_QQ_STEPS, 1);
        intent.removeExtra(EXTRA_TEST_ACTION);
        webView.postDelayed(new Runnable() {
            @Override
            public void run() {
                runDebugTestAction(action, themeSteps, qqSteps);
            }
        }, 800);
    }

    private void runDebugTestAction(String action, int themeSteps, int qqSteps) {
        if (!BuildConfig.DEBUG || webView == null) {
            return;
        }
        if ("qq".equals(action)) {
            triggerWebAction("qq");
            return;
        }
        if ("share".equals(action)) {
            triggerWebAction("share");
            return;
        }
        if ("assetManager".equals(action)) {
            triggerWebAction("assetManager");
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(
                            "(function(){var el=document.querySelector('.asset-modal.open,.asset-manager.open,[role=\"dialog\"]');return !!el;})()",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    Log.i(TAG_TEST, "asset_overlay_open=" + "true".equals(value));
                                }
                            }
                    );
                }
            }, 500);
            return;
        }
        if ("popup".equals(action)) {
            triggerWebAction("popup");
            return;
        }
        if ("storage_png_probe".equals(action)) {
            try {
                String png = "data:image/png;base64,"
                        + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+XwU2WQAAAABJRU5ErkJggg==";
                String saved = AssetStore.saveAsset(
                        this,
                        "background",
                        "{\"id\":\"debug-transparent-probe\",\"name\":\"透明测试\",\"custom\":true}",
                        png
                );
                JSONObject item = new JSONObject(saved);
                Log.i(TAG_TEST, "storage_png_path=" + item.optString("path"));
                Log.i(TAG_TEST, "storage_png_mime=" + item.optString("mime"));
                AssetStore.deleteAsset(this, "background", "debug-transparent-probe");
            } catch (Exception error) {
                Log.e(TAG_TEST, "storage_png_probe_failed", error);
            }
            return;
        }
        if ("theme_step".equals(action)) {
            String script = "(function(){if(window.__testThemeStep){for(var i=0;i<"
                    + Math.max(1, themeSteps)
                    + ";i++){window.__testThemeStep(1);}}})()";
            webView.evaluateJavascript(script, null);
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    logThemeStateForTest();
                }
            }, 500);
            return;
        }
        if ("theme_read".equals(action)) {
            logThemeStateForTest();
            return;
        }
        if ("qq_size_step".equals(action)) {
            String script = "(function(){if(window.__testQqExportStep){for(var i=0;i<"
                    + Math.max(1, qqSteps)
                    + ";i++){window.__testQqExportStep(1);}}})()";
            webView.evaluateJavascript(script, null);
            return;
        }
        if ("qq_export_normal".equals(action)) {
            webView.evaluateJavascript(
                    "window.__testSetQqExport&&window.__testSetQqExport('normal');",
                    null
            );
            return;
        }
        if ("qq_export_xlarge".equals(action)) {
            applyQqExportPresetForTest("xlarge");
            return;
        }
        if ("qq_export_xlarge_and_share".equals(action)) {
            applyQqExportPresetForTest("xlarge");
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    triggerWebAction("qq");
                }
            }, 1200);
            return;
        }
        if ("share_fixed_test".equals(action)) {
            applyQqExportPresetForTest("xlarge");
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    triggerWebAction("share");
                }
            }, 2500);
            return;
        }
        if ("android_back".equals(action)) {
            webView.evaluateJavascript(
                    "(function(){try{return !!(window.handleAndroidBack&&window.handleAndroidBack());}catch(e){return false;}})();",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            Log.i(TAG_TEST, "android_back_handled=" + normalizeJsResult(value));
                        }
                    }
            );
        }
    }

    private void logThemeStateForTest() {
        String prefTheme = ThemeChrome.loadThemeKey(getSharedPreferences(PREFS_NAME, MODE_PRIVATE));
        Log.i(TAG_TEST, "pref_theme=" + prefTheme);
        webView.evaluateJavascript(
                "(function(){return document.documentElement.dataset.theme||'dark';})();",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.i(TAG_TEST, "dom_theme=" + normalizeJsResult(value));
                    }
                }
        );
    }

    private void applyQqExportPresetForTest(final String presetKey) {
        webView.evaluateJavascript(
                "window.__testSetQqExport&&window.__testSetQqExport(" + JSONObject.quote(presetKey) + ");",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        webView.evaluateJavascript(
                                "(function(){return window.__testGetQqExportKey?window.__testGetQqExportKey():'';})();",
                                new ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String keyValue) {
                                        Log.i(TAG_TEST, "qq_export_size=" + normalizeJsResult(keyValue));
                                    }
                                }
                        );
                    }
                }
        );
    }

    private String normalizeJsResult(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (webView != null) {
            deliverGeneratorState(GeneratorStateStore.read(this));
            webView.evaluateJavascript("window.refreshInputMethodStatus&&window.refreshInputMethodStatus();", null);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        exportWorker.shutdownNow();
        try {
            unregisterReceiver(imageReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(assetTransferReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(generatorStateReceiver);
        } catch (Exception ignored) {
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void handleBackPressed() {
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return !!(window.handleAndroidBack&&window.handleAndroidBack());}catch(e){return false;}})();",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (parseJsBool(value)) {
                            return;
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (editing || imeVisible) {
                                    dismissKeyboard();
                                    return;
                                }
                                if (webView != null && webView.canGoBack()) {
                                    webView.goBack();
                                } else {
                                    finish();
                                }
                            }
                        });
                    }
                }
        );
    }

    private static boolean parseJsBool(String value) {
        if (value == null || "null".equals(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return "true".equals(trimmed);
    }

    private void triggerWebAction(String action) {
        if (webView == null || action == null) {
            return;
        }
        String targetId;
        if (action.startsWith("web:")) {
            targetId = action.substring(4);
        } else if ("popup".equals(action)) {
            targetId = "popupButton";
        } else if ("random".equals(action)) {
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
        webView.evaluateJavascript("(function(){var el=document.getElementById("
                + JSONObject.quote(targetId)
                + "); if(el){ el.click(); }})()", null);
    }

    private void dismissKeyboard() {
        editing = false;
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript(
                "(function(){var el=document.activeElement;if(el&&el.blur){el.blur();}})();", null);
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(webView);
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.ime());
        }
        InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (input != null) {
            input.hideSoftInputFromWindow(webView.getWindowToken(), 0);
        }
    }

    private void deliverGeneratorState(String json) {
        if (webView == null || json == null) {
            return;
        }
        try {
            long revision = new JSONObject(json).getLong("revision");
            if (revision <= deliveredStateRevision) {
                return;
            }
            deliveredStateRevision = revision;
            webView.evaluateJavascript("window.receiveGeneratorState&&window.receiveGeneratorState("
                    + JSONObject.quote(json) + ");", null);
        } catch (org.json.JSONException ignored) {
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public String getInputMethodStatus() { return InputMethodSupport.status(MainActivity.this); }
        @JavascriptInterface public String getDirectSendStatus() { return QqSendAccessibilityService.status(MainActivity.this); }
        @JavascriptInterface public void openDirectSendSettings() { runOnUiThread(() -> Toast.makeText(MainActivity.this, "辅助发送已停用", Toast.LENGTH_SHORT).show()); }
        @JavascriptInterface public void openInputMethodSettings() { runOnUiThread(() -> prepareKeyboardAction("settings")); }
        @JavascriptInterface public void showInputMethodPicker() { runOnUiThread(() -> prepareKeyboardAction("picker")); }
        @JavascriptInterface public void useImageMode() {
            runOnUiThread(() -> {
                InputMethodSupport.activateImageMode(MainActivity.this);
                if (!InputMethodSupport.isSelected(MainActivity.this)) prepareKeyboardAction("picker");
                else if (webView != null) webView.evaluateJavascript("window.refreshInputMethodStatus&&window.refreshInputMethodStatus();", null);
            });
        }
        @JavascriptInterface
        public String getGeneratorState() {
            return GeneratorStateStore.read(MainActivity.this);
        }

        @JavascriptInterface
        public String updateGeneratorState(String patchJson) {
            try {
                return GeneratorStateStore.update(MainActivity.this, patchJson);
            } catch (Exception error) {
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public void dismissKeyboard() {
            runOnUiThread(MainActivity.this::dismissKeyboard);
        }

        @JavascriptInterface
        public void setEditing(boolean value) {
            runOnUiThread(() -> editing = value);
        }

        @JavascriptInterface
        public void showToast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void shareImage(final String dataUrl) {
            runOnUiThread(() -> exportAndShare(dataUrl, false));
        }

        @JavascriptInterface
        public void shareToQQ(final String dataUrl) {
            runOnUiThread(() -> QqSendAccessibilityService.openChat(MainActivity.this));
        }

        @JavascriptInterface
        public void startPopupMode() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openPopupMode();
                }
            });
        }

        @JavascriptInterface
        public void pickBackgroundImage() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(MainActivity.this, ImagePickerActivity.class);
                    intent.putExtra(ImagePickerActivity.EXTRA_TARGET, "main");
                    intent.putExtra(ImagePickerActivity.EXTRA_KIND, "background");
                    startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void pickAssetImage(final String kind, final String ownerId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(MainActivity.this, ImagePickerActivity.class);
                    intent.putExtra(ImagePickerActivity.EXTRA_TARGET, "main");
                    intent.putExtra(ImagePickerActivity.EXTRA_KIND, kind);
                    intent.putExtra(ImagePickerActivity.EXTRA_OWNER_ID, ownerId);
                    startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public String listAssets() {
            try {
                return AssetStore.listAssets(MainActivity.this);
            } catch (Exception error) {
                if (BuildConfig.DEBUG) Log.e("MahouAssets", "listAssets failed", error);
                return "{\"backgrounds\":[],\"characters\":[],\"emotions\":[]}";
            }
        }

        @JavascriptInterface
        public String saveAsset(String kind, String metadataJson, String imageDataUrl) {
            try {
                return AssetStore.saveAsset(MainActivity.this, kind, metadataJson, imageDataUrl);
            } catch (Exception error) {
                if (BuildConfig.DEBUG) Log.e("MahouAssets", "saveAsset failed", error);
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public String saveCharacterBundle(String characterJson, String emotionJson, String imageDataUrl) {
            try {
                return AssetStore.saveCharacterBundle(
                        MainActivity.this,
                        characterJson,
                        emotionJson,
                        imageDataUrl
                );
            } catch (Exception error) {
                if (BuildConfig.DEBUG) Log.e("MahouAssets", "saveCharacterBundle failed", error);
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public String updateAsset(String id, String metadataJson) {
            try {
                return AssetStore.updateAsset(MainActivity.this, id, metadataJson);
            } catch (Exception error) {
                if (BuildConfig.DEBUG) Log.e("MahouAssets", "updateAsset failed", error);
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public boolean deleteAsset(String kind, String id) {
            try {
                return AssetStore.deleteAsset(MainActivity.this, kind, id);
            } catch (Exception error) {
                if (BuildConfig.DEBUG) Log.e("MahouAssets", "deleteAsset failed", error);
                return false;
            }
        }

        @JavascriptInterface
        public void startAssetBackup(final String settingsJson) {
            runOnUiThread(() -> startActivity(
                    AssetTransferActivity.backupIntent(MainActivity.this, "main", settingsJson)
            ));
        }

        @JavascriptInterface
        public void startAssetRestore() {
            runOnUiThread(() -> startActivity(
                    AssetTransferActivity.restoreIntent(MainActivity.this, "main")
            ));
        }

        @JavascriptInterface
        public String getAppInfo() {
            try {
                return AssetStore.getAppInfo(MainActivity.this);
            } catch (Exception error) {
                return errorJson(error);
            }
        }

        @JavascriptInterface
        public void reportAccessibleActions(final String layoutJson) {
            runOnUiThread(() -> {
                if (accessibleActionLayer != null && webView != null) {
                    boolean nativeTree = webView.getAccessibilityNodeProvider() != null;
                    accessibleActionLayer.setVisibility(nativeTree ? View.GONE : View.VISIBLE);
                    accessibleActionLayer.setImportantForAccessibility(nativeTree
                            ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                    if (nativeTree) {
                        return;
                    }
                    accessibleActionLayer.updateActions(layoutJson);
                }
            });
        }

        @JavascriptInterface
        public void setPopupPreference(String key, String value) {
            if (!isAllowedPreference(key) || value == null || value.length() > 64) {
                return;
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public String getPopupPreference(String key) {
            if (!isAllowedPreference(key)) {
                return "";
            }
            return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(key, "");
        }

        @JavascriptInterface
        public void applyNativeChromeTheme(final String themeKey) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    applyWindowChromeTheme(themeKey);
                }
            });
        }
    }

    private static boolean isAllowedPreference(String key) {
        return "uiTheme".equals(key) || "qqExportSize".equals(key) || "textKeyboard".equals(key);
    }

    private void applyWindowChromeTheme(String themeKey) {
        ThemeChrome.Palette palette = ThemeChrome.resolve(themeKey);
        getWindow().setStatusBarColor(palette.statusBar);
        getWindow().setNavigationBarColor(palette.statusBar);
        applyLightSystemBars(palette.lightStatusBar);
        if (webView != null) {
            webView.setBackgroundColor(palette.webBg);
        }
        if (isDebugBuild()) {
            Log.i(TAG_TEST, "status_bar_color=0x" + Integer.toHexString(palette.statusBar));
            Log.i(TAG_TEST, "light_status_bar=" + palette.lightStatusBar);
            Log.i(TAG_TEST, "light_navigation_bar=" + palette.lightStatusBar);
        }
    }

    private void applyLightSystemBars(boolean light) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int appearance = light ? mask : 0;
                controller.setSystemBarsAppearance(appearance, mask);
            }
            return;
        }
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (light) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);
    }

    private void registerImageReceiver() {
        IntentFilter filter = new IntentFilter(ImagePickerActivity.ACTION_IMAGE_PICKED);
        ContextCompat.registerReceiver(this, imageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void registerAssetTransferReceiver() {
        IntentFilter filter = new IntentFilter(AssetTransferActivity.ACTION_ASSET_TRANSFER_RESULT);
        ContextCompat.registerReceiver(this, assetTransferReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void deliverPickedAsset(String kind, String ownerId, String imageUrl) {
        if (!ImagePickerActivity.isImportUrl(imageUrl) || webView == null) {
            return;
        }
        String script = "window.receivePickedAsset && window.receivePickedAsset("
                + JSONObject.quote(kind)
                + ","
                + JSONObject.quote(ownerId == null ? "" : ownerId)
                + ","
                + JSONObject.quote(imageUrl)
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

    private void prepareKeyboardAction(String action) {
        dismissKeyboard();
        InputMethodSupport.activateImageMode(this);
        performKeyboardAction(action);
    }

    private void performKeyboardAction(String action) {
        if ("settings".equals(action)) InputMethodSupport.openSettings(this);
        else InputMethodSupport.showPicker(this);
        if (webView != null) webView.evaluateJavascript("window.refreshInputMethodStatus&&window.refreshInputMethodStatus();", null);
    }

    private void openPopupMode() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(permission);
            Toast.makeText(this, "\u8bf7\u5141\u8bb8\u201c\u663e\u793a\u5728\u5176\u4ed6\u5e94\u7528\u4e0a\u5c42\u201d\uff0c\u7136\u540e\u56de\u6765\u518d\u70b9\u5f39\u7a97\u6a21\u5f0f\u3002", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            startPopupAfterNotificationRequest = true;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        startPopupService();
    }

    private void startPopupService() {
        dismissKeyboard();
        InputMethodSupport.activatePopupMode(this);
        ContextCompat.startForegroundService(this, new Intent(this, PopupService.class)
                .setAction(PopupService.ACTION_RESTORE_POPUP));
        Toast.makeText(this, "弹窗已开启，可在 QQ 上方使用。", Toast.LENGTH_SHORT).show();
    }

    private void exportAndShare(String dataUrl, boolean preferQQ) {
        if (destroyed || exportBusy) return;
        exportBusy = true;
        dismissKeyboard();
        exportWorker.execute(() -> {
            File temporary = null;
            try {
                temporary = PopupShareImage.prepare(getApplicationContext(), dataUrl);
                final File prepared = temporary;
                runOnUiThread(() -> {
                    exportBusy = false;
                    if (destroyed) { PopupShareImage.discard(prepared); return; }
                    try {
                        PopupShareImage.publish(prepared);
                        Uri uri = Uri.parse("content://" + SHARE_AUTHORITY + "/shared.png");
                        startActivity(preferQQ ? ShareProxyActivity.qqIntent(this, uri)
                                : ShareProxyActivity.chooserIntent(this, uri));
                    } catch (Exception error) {
                        PopupShareImage.discard(prepared);
                        Toast.makeText(this, "分享失败，请重试。", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception | OutOfMemoryError error) {
                PopupShareImage.discard(temporary);
                runOnUiThread(() -> {
                    exportBusy = false;
                    if (!destroyed) Toast.makeText(this, "无法生成分享图片，请重试。", Toast.LENGTH_LONG).show();
                });
            }
        });
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
