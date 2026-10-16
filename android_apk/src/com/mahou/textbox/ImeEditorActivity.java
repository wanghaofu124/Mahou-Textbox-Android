package com.mahou.textbox;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.speech.RecognizerIntent;
import org.json.JSONObject;
import java.util.ArrayList;

/** Translucent caption sheet. Typing stays in 魔裁 so returning never needs the IME picker. */
public class ImeEditorActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 8021;
    private static final int REQUEST_SYSTEM_SPEECH = 8022;
    private WebView webView;
    private boolean returning;
    private boolean builtInKeyboard;

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
        webView.loadUrl(LocalWebContent.ORIGIN + "/assets/ime-editor.html");
    }

    @Override protected void onDestroy() {
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SYSTEM_SPEECH) return;
        ArrayList<String> matches = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        String text = resultCode == RESULT_OK && matches != null && !matches.isEmpty() ? matches.get(0).trim() : "";
        updateVoicePreview("", false);
        if (text.isEmpty()) {
            showEditorNotice(resultCode == RESULT_CANCELED ? "语音输入已取消" : "没有听清，请再试一次", 3000);
            return;
        }
        if (webView != null) {
            webView.evaluateJavascript("window.insertRecognizedText&&window.insertRecognizedText(" + JSONObject.quote(text) + ");", null);
        }
        showEditorNotice("语音已输入", 1800);
    }

    private void startVoiceInput() {
        runOnUiThread(() -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                return;
            }
            startSystemSpeechActivity();
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSystemSpeechActivity();
        } else {
            updateVoicePreview("", false);
            showEditorNotice("需要麦克风权限才能使用语音输入", 3500);
        }
    }

    private void startSystemSpeechActivity() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出台词");
        try {
            updateVoicePreview("", true);
            showEditorNotice("正在打开手机语音输入…", 10000);
            startActivityForResult(intent, REQUEST_SYSTEM_SPEECH);
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            updateVoicePreview("", false);
            showEditorNotice("这台手机没有可用的语音识别服务", 3500);
        }
    }

    private void showEditorNotice(String message, int durationMs) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript("window.showAppNotice&&window.showAppNotice(" + JSONObject.quote(message) + "," + durationMs + ");", null);
        });
    }

    private void updateVoicePreview(String text, boolean listening) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript("window.setImeVoicePreview&&window.setImeVoicePreview("
                    + JSONObject.quote(text == null ? "" : text) + "," + listening + ");", null);
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
