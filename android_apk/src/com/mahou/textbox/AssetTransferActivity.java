package com.mahou.textbox;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AssetTransferActivity extends ComponentActivity {
    public static final String ACTION_ASSET_TRANSFER_RESULT = "com.mahou.textbox.ASSET_TRANSFER_RESULT";
    public static final String EXTRA_TARGET = "target";
    public static final String EXTRA_RESULT_JSON = "resultJson";

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_SETTINGS = "settings";
    private static final String MODE_BACKUP = "backup";
    private static final String MODE_RESTORE = "restore";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String target;
    private String settingsJson;

    private final ActivityResultLauncher<String> createDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
                if (uri == null) {
                    sendResult(cancelledResult());
                } else {
                    executor.execute(() -> exportBackup(uri));
                }
            }
    );

    private final ActivityResultLauncher<String[]> openDocument = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) {
                    sendResult(cancelledResult());
                } else {
                    executor.execute(() -> importBackup(uri));
                }
            }
    );

    public static Intent backupIntent(Context context, String target, String settingsJson) {
        return new Intent(context, AssetTransferActivity.class)
                .putExtra(EXTRA_MODE, MODE_BACKUP)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_SETTINGS, settingsJson);
    }

    public static Intent restoreIntent(Context context, String target) {
        return new Intent(context, AssetTransferActivity.class)
                .putExtra(EXTRA_MODE, MODE_RESTORE)
                .putExtra(EXTRA_TARGET, target);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        target = getIntent().getStringExtra(EXTRA_TARGET);
        settingsJson = getIntent().getStringExtra(EXTRA_SETTINGS);
        if (savedInstanceState != null) {
            return;
        }
        if (MODE_BACKUP.equals(getIntent().getStringExtra(EXTRA_MODE))) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
            createDocument.launch("魔裁文本框素材-" + stamp + ".mahou-backup");
        } else {
            openDocument.launch(new String[]{"application/zip", "application/octet-stream", "*/*"});
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void exportBackup(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IllegalStateException("无法创建备份文件");
            }
            sendResult(AssetStore.exportBackup(this, output, settingsJson));
        } catch (Exception error) {
            sendResult(errorResult(error));
        }
    }

    private void importBackup(Uri uri) {
        try {
            long declaredSize = -1;
            try (android.content.res.AssetFileDescriptor descriptor =
                         getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (descriptor != null) {
                    declaredSize = descriptor.getLength();
                }
            }
            if (declaredSize > AssetStore.MAX_BACKUP_BYTES) {
                throw new IllegalArgumentException("备份文件不能超过 220 MB");
            }
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalStateException("无法打开备份文件");
                }
                sendResult(AssetStore.importBackup(this, input));
            }
        } catch (Exception error) {
            sendResult(errorResult(error));
        }
    }

    private String cancelledResult() {
        try {
            return new JSONObject().put("status", "cancelled").put("message", "已取消").toString();
        } catch (Exception ignored) {
            return "{\"status\":\"cancelled\"}";
        }
    }

    private String errorResult(Exception error) {
        try {
            String message = error.getMessage() == null ? "素材操作失败" : error.getMessage();
            return new JSONObject().put("status", "error").put("message", message).toString();
        } catch (Exception ignored) {
            return "{\"status\":\"error\",\"message\":\"素材操作失败\"}";
        }
    }

    private void sendResult(String resultJson) {
        Intent result = new Intent(ACTION_ASSET_TRANSFER_RESULT)
                .setPackage(getPackageName())
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_RESULT_JSON, resultJson);
        sendBroadcast(result);
        runOnUiThread(() -> {
            try {
                JSONObject json = new JSONObject(resultJson);
                String message = json.optString("message", "素材操作完成");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
                Toast.makeText(this, "素材操作完成", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }
}
