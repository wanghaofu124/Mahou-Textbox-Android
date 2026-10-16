package com.mahou.textbox;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import org.json.JSONObject;
import java.util.ArrayList;

/** Translucent caption sheet. Typing stays in 魔裁 so returning never needs the IME picker. */
public class ImeEditorActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 8021;
    private WebView webView;
    private boolean returning;
    private boolean builtInKeyboard;
    private SpeechRecognizer speechRecognizer;
    private String voicePreviewText = "";

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
        destroySpeechRecognizer();
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

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition();
        } else {
            updateVoicePreview("", false);
            showEditorNotice("需要麦克风权限才能使用语音输入", 3500);
        }
    }

    private void startVoiceInput() {
        runOnUiThread(() -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                return;
            }
            startSpeechRecognition();
        });
    }

    private void startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateVoicePreview("", false);
            showEditorNotice("这台手机没有可用的语音识别服务", 3500);
            return;
        }
        destroySpeechRecognizer();
        try {
            // Let the device's configured recognition service choose its best mode; forcing
            // on-device recognition can sharply reduce Chinese recognition quality.
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    voicePreviewText = "";
                    updateVoicePreview("", true);
                    showEditorNotice("正在听，请说出台词", 30000);
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    updateVoicePreview(voicePreviewText.isEmpty() ? "正在识别…" : voicePreviewText, false);
                    showEditorNotice("正在识别…", 10000);
                }
                @Override public void onError(int error) {
                    updateVoicePreview("", false);
                    destroySpeechRecognizer();
                    showEditorNotice(speechErrorMessage(error), 3500);
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = matches == null || matches.isEmpty() ? "" : matches.get(0).trim();
                    updateVoicePreview("", false);
                    destroySpeechRecognizer();
                    if (text.isEmpty()) {
                        showEditorNotice("没有听清，请再试一次", 3000);
                        return;
                    }
                    if (webView != null) {
                        webView.evaluateJavascript("window.insertRecognizedText&&window.insertRecognizedText(" + JSONObject.quote(text) + ");", null);
                    }
                    showEditorNotice("语音已输入", 1800);
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    voicePreviewText = partial == null || partial.isEmpty() ? "" : partial.get(0).trim();
                    updateVoicePreview(voicePreviewText, true);
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出台词");
            speechRecognizer.startListening(intent);
        } catch (RuntimeException error) {
            destroySpeechRecognizer();
            updateVoicePreview("", false);
            showEditorNotice("无法启动语音识别，请检查手机语音服务", 3500);
        }
    }

    private String speechErrorMessage(int error) {
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return "没有听清，请再试一次";
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) return "语音识别网络不可用";
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return "没有麦克风权限，请在设置中允许";
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) return "语音识别正忙，请稍后再试";
        return "语音识别失败，请再试一次";
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

    private void destroySpeechRecognizer() {
        SpeechRecognizer recognizer = speechRecognizer;
        speechRecognizer = null;
        if (recognizer != null) recognizer.destroy();
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
