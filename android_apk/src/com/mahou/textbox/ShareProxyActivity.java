package com.mahou.textbox;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class ShareProxyActivity extends ComponentActivity {
    public static final String ACTION_SHARE_PROXY_CLOSED = "com.mahou.textbox.ACTION_SHARE_PROXY_CLOSED";
    public static final String ACTION_SHARE_PROXY_OPENING = "com.mahou.textbox.ACTION_SHARE_PROXY_OPENING";
    public static final String EXTRA_SHARE_SESSION = "popupShareSession";

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_URI = "uri";
    private static final String MODE_CHOOSER = "chooser";
    private static final String MODE_QQ = "qq";
    private static final String STATE_LAUNCHED = "shareLaunched";
    private static final String STATE_COMPLETED = "shareCompleted";
    private static final String SHARE_FILE_NAME = "mahou_textbox.png";
    private ShareProxyLifecycle shareLifecycle;

    // Always register in the same order, including when restored with a pending result.
    private final ActivityResultLauncher<Intent> shareLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> completeShare());

    public static Intent chooserIntent(Context context, Uri uri) {
        return proxyIntent(context, uri, MODE_CHOOSER);
    }

    public static Intent qqIntent(Context context, Uri uri) {
        return proxyIntent(context, uri, MODE_QQ);
    }

    private static Intent proxyIntent(Context context, Uri uri, String mode) {
        return new Intent(context, ShareProxyActivity.class)
                .putExtra(EXTRA_MODE, mode).putExtra(EXTRA_URI, uri.toString());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shareLifecycle = new ShareProxyLifecycle(
                savedInstanceState != null && savedInstanceState.getBoolean(STATE_LAUNCHED),
                savedInstanceState != null && savedInstanceState.getBoolean(STATE_COMPLETED));
        if (shareLifecycle.isCompleted()) {
            finish();
            return;
        }
        if (!shareLifecycle.beginLaunch()) return;
        if (!getIntent().hasExtra(EXTRA_SHARE_SESSION)) {
            getIntent().putExtra(EXTRA_SHARE_SESSION, "main:" + java.util.UUID.randomUUID());
        }
        sendBroadcast(new Intent(ACTION_SHARE_PROXY_OPENING).setPackage(getPackageName())
                .putExtra(EXTRA_SHARE_SESSION, getIntent().getStringExtra(EXTRA_SHARE_SESSION)));
        try {
            String rawUri = getIntent().getStringExtra(EXTRA_URI);
            if (rawUri == null) throw new IllegalArgumentException("Missing share image.");
            Uri uri = Uri.parse(rawUri);
            if (!"content".equals(uri.getScheme()) || !"com.mahou.textbox.share".equals(uri.getAuthority())) {
                throw new IllegalArgumentException("Invalid share image.");
            }
            if (MODE_QQ.equals(getIntent().getStringExtra(EXTRA_MODE))) shareToQqOrFallback(uri);
            else shareLauncher.launch(buildShareChooser(uri));
        } catch (RuntimeException error) {
            Log.e("MahouShare", "Proxy share failed", error);
            Toast.makeText(this, "分享失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            completeShare();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_LAUNCHED, shareLifecycle.isLaunched());
        outState.putBoolean(STATE_COMPLETED, shareLifecycle.isCompleted());
        super.onSaveInstanceState(outState);
    }

    private void completeShare() {
        if (shareLifecycle == null || !shareLifecycle.complete()) return;
        PopupShareSessionStore.complete(this, getIntent().getStringExtra(EXTRA_SHARE_SESSION));
        sendBroadcast(new Intent(ACTION_SHARE_PROXY_CLOSED).setPackage(getPackageName())
                .putExtra(EXTRA_SHARE_SESSION, getIntent().getStringExtra(EXTRA_SHARE_SESSION)));
        finish();
    }

    private void shareToQqOrFallback(Uri uri) {
        try {
            shareLauncher.launch(buildShareIntent(uri).setPackage("com.tencent.mobileqq"));
        } catch (ActivityNotFoundException qqMissing) {
            try {
                shareLauncher.launch(buildShareIntent(uri).setPackage("com.tencent.tim"));
            } catch (ActivityNotFoundException timMissing) {
                shareLauncher.launch(buildShareChooser(uri));
            }
        }
    }

    private Intent buildShareIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(getContentResolver(), SHARE_FILE_NAME, uri));
        return intent;
    }

    private Intent buildShareChooser(Uri uri) {
        Intent chooser = Intent.createChooser(buildShareIntent(uri), "分享魔裁文本框");
        chooser.putExtra(Intent.EXTRA_STREAM, uri);
        chooser.setClipData(ClipData.newUri(getContentResolver(), SHARE_FILE_NAME, uri));
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooser;
    }
}
