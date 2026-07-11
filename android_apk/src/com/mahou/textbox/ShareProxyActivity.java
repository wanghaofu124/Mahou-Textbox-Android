package com.mahou.textbox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class ShareProxyActivity extends Activity {
    public static final String ACTION_SHARE_PROXY_CLOSED = "com.mahou.textbox.ACTION_SHARE_PROXY_CLOSED";

    private static final String TAG_SHARE = "MahouShare";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_URI = "uri";
    private static final String MODE_CHOOSER = "chooser";
    private static final String MODE_QQ = "qq";
    private static final String SHARE_FILE_NAME = "mahou_textbox.png";
    private static final String SHARE_MIME = "image/png";

    private boolean launched;
    private boolean pausedAfterLaunch;

    public static Intent chooserIntent(Context context, Uri uri) {
        Intent intent = new Intent(context, ShareProxyActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_CHOOSER);
        intent.putExtra(EXTRA_URI, uri.toString());
        return intent;
    }

    public static Intent qqIntent(Context context, Uri uri) {
        Intent intent = new Intent(context, ShareProxyActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_QQ);
        intent.putExtra(EXTRA_URI, uri.toString());
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String rawUri = getIntent().getStringExtra(EXTRA_URI);
            if (rawUri == null) {
                throw new IllegalArgumentException("Missing share image.");
            }
            Uri uri = Uri.parse(rawUri);
            String mode = getIntent().getStringExtra(EXTRA_MODE);
            if (MODE_QQ.equals(mode)) {
                shareToQqOrFallback(uri);
            } else {
                startActivity(buildShareChooser(buildShareIntent(uri), uri));
            }
            launched = true;
        } catch (Exception error) {
            Log.e(TAG_SHARE, "proxy share failed", error);
            Toast.makeText(this, "分享失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            notifyShareProxyClosed();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launched && pausedAfterLaunch) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        if (launched) {
            pausedAfterLaunch = true;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (launched) {
            notifyShareProxyClosed();
        }
        super.onDestroy();
    }

    private void notifyShareProxyClosed() {
        sendBroadcast(new Intent(ACTION_SHARE_PROXY_CLOSED).setPackage(getPackageName()));
    }

    private void shareToQqOrFallback(Uri uri) {
        try {
            Intent intent = buildShareIntent(uri);
            intent.setPackage("com.tencent.mobileqq");
            grantUriPermission("com.tencent.mobileqq", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException qqMissing) {
            shareToTimOrChooser(uri);
        }
    }

    private void shareToTimOrChooser(Uri uri) {
        try {
            Intent intent = buildShareIntent(uri);
            intent.setPackage("com.tencent.tim");
            grantUriPermission("com.tencent.tim", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException timMissing) {
            startActivity(buildShareChooser(buildShareIntent(uri), uri));
        }
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
}
