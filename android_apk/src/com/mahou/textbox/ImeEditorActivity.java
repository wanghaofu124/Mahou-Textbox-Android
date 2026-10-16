package com.mahou.textbox;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.json.JSONObject;

/** Translucent caption sheet. Typing stays in 魔裁 so returning never needs the IME picker. */
public class ImeEditorActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 8021;
    private WebView webView;
    private boolean returning;
    private boolean builtInKeyboard;
    private boolean voicePreparing;
    private boolean voiceListening;
    private OfflineSpeechInput voiceInput;

    @Override @SuppressLint("SetJavaScriptEnabled") protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        builtInKeyboard = InputMethodSupport.useBuiltInTextKeyboard(this);
        int softInputState = builtInKeyboard
                ? android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                : android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | softInputState);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(true);
        webView.setBackgroundColor(0);
        webView.addJavascriptInterface(new EditorBridge(), "AndroidBridge");
        webView.setWebViewClient(LocalWebContent.createClient(this, () ->
                webView.evaluateJavascript("window.initializeImeEditor&&window.initializeImeEditor();", null)));
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        voiceInput = new OfflineSpeechInput(this);
        webView.loadUrl(LocalWebContent.ORIGIN + "/assets/ime-editor.html");
    }

    @Override protected void onDestroy() {
        if (voiceInput != null) {
            voiceInput.close();
            voiceInput = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        finishIfImageKeyboardSelected();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) finishIfImageKeyboardSelected();
    }

    private void finishIfImageKeyboardSelected() {
        if (returning && InputMethodSupport.isSelected(this)) finish();
    }

    private void closeEditor() {
        boolean selected = InputMethodSupport.selectThisIme(this);
        Log.i("MahouDebug", "editor-close direct=" + selected);
        if (selected) {
            finish();
            return;
        }
        returning = true;
        InputMethodSupport.showPicker(this);
    }

    private void startVoiceInput() {
        runOnUiThread(() -> {
            if (voicePreparing && voiceInput != null && voiceInput.isPreparing()) {
                voiceInput.cancelPreparation();
                voicePreparing = false;
                updateVoicePreview("", false, false);
                showEditorNotice("已取消语音包准备", 2200);
                return;
            }
            if (voiceListening) {
                if (voiceInput != null) {
                    updateVoicePreview("正在整理识别结果…", true, false);
                    voiceInput.stop();
                    showEditorNotice("正在整理识别结果…", 3500);
                }
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                updateVoicePreview("", false, false);
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                return;
            }
            startOfflineSpeechRecognition();
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startOfflineSpeechRecognition();
        } else {
            updateVoicePreview("", false, false);
            showEditorNotice("需要麦克风权限才能使用语音输入", 3500);
        }
    }

    private void startOfflineSpeechRecognition() {
        if (voiceInput == null) return;
        if (!voiceInput.hasInstalledModel()) {
            showEditorNotice("首次使用会下载约44MB中文语音包，安装后占用约68MB", 6000);
        }
        voicePreparing = true;
        voiceListening = false;
        updateVoicePreview("正在准备本机语音包…", false, true);
        voiceInput.start(new OfflineSpeechInput.Listener() {
            @Override public void onStatus(String status) {
                voicePreparing = true;
                voiceListening = false;
                updateVoicePreview(status, false, true);
            }

            @Override public void onListening() {
                voicePreparing = false;
                voiceListening = true;
                updateVoicePreview("", true, false);
                showEditorNotice("离线语音已就绪，请说话；再点麦克风结束", 6000);
            }

            @Override public void onPartialText(String text) {
                updateVoicePreview(text, true, false);
            }

            @Override public void onComplete(String text) {
                voicePreparing = false;
                voiceListening = false;
                updateVoicePreview("", false, false);
                if (text == null || text.trim().isEmpty()) {
                    showEditorNotice("没有听清，请再试一次", 3000);
                    return;
                }
                if (webView != null) {
                    webView.evaluateJavascript("window.insertRecognizedText&&window.insertRecognizedText("
                            + JSONObject.quote(text.trim()) + ");", null);
                }
                showEditorNotice("语音已输入", 1800);
            }

            @Override public void onError(String message, Exception error) {
                voicePreparing = false;
                voiceListening = false;
                updateVoicePreview("", false, false);
                Log.w("MahouVoice", message, error);
                showEditorNotice(message, 5000);
            }
        });
    }

    private void showEditorNotice(String message, int durationMs) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript("window.showAppNotice&&window.showAppNotice(" + JSONObject.quote(message) + "," + durationMs + ");", null);
        });
    }

    private void updateVoicePreview(String text, boolean listening, boolean preparing) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript("window.setImeVoicePreview&&window.setImeVoicePreview("
                    + JSONObject.quote(text == null ? "" : text) + "," + listening + "," + preparing + ");", null);
        });
    }

    private final class EditorBridge {
        @JavascriptInterface public String getGeneratorState() { return GeneratorStateStore.read(ImeEditorActivity.this); }
        @JavascriptInterface public String updateGeneratorState(String patch) {
            try { return GeneratorStateStore.update(ImeEditorActivity.this, patch); }
            catch (Exception error) { return "{\"error\":\"无法保存台词\"}"; }
        }
        @JavascriptInterface public String listAssets() {
            try { return AssetStore.listAssets(ImeEditorActivity.this); }
            catch (Exception error) { return "{\"backgrounds\":[],\"characters\":[],\"emotions\":[]}"; }
        }
        @JavascriptInterface public String getAppInfo() {
            try { return AssetStore.getAppInfo(ImeEditorActivity.this); }
            catch (Exception error) { return "{}"; }
        }
        @JavascriptInterface public String getPopupPreference(String key) {
            if (!"uiTheme".equals(key) && !"qqExportSize".equals(key) && !"textKeyboard".equals(key)) return "";
            return getSharedPreferences("mahou_popup_prefs", MODE_PRIVATE).getString(key, "");
        }
        @JavascriptInterface public boolean useBuiltInTextKeyboard() { return builtInKeyboard; }
        @JavascriptInterface public void startVoiceInput() { ImeEditorActivity.this.startVoiceInput(); }
        @JavascriptInterface public void finishImeEditing() { runOnUiThread(ImeEditorActivity.this::closeEditor); }
        @JavascriptInterface public void closeImeEditing() { runOnUiThread(ImeEditorActivity.this::closeEditor); }
        @JavascriptInterface public boolean sendImeEditingToQQ() {
            if (!MahouInputMethodService.queueCaptionSendAfterEditor()) return false;
            runOnUiThread(ImeEditorActivity.this::closeEditor);
            return true;
        }
        @JavascriptInterface public void showToast(String text) {}
    }

    static Intent intent(android.content.Context context) {
        return new Intent(context, ImeEditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
