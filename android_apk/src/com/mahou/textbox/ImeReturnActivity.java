package com.mahou.textbox;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Stays off the main app task. Either asks once for a return notification,
 * or shows the system keyboard list so the picture keyboard can be chosen again.
 */
public class ImeReturnActivity extends Activity {
    static final String EXTRA_PREPARE = "prepare";
    private static final int REQUEST_NOTIFICATION = 7;
    private boolean prepareMode;
    private boolean awaitingPermission;
    private boolean pickerShown;
    private boolean pickerArmed;
    private boolean pickerPaused;
    private ContentObserver imeSetting;

    static Intent prepareIntent(Context context) {
        return new Intent(context, ImeReturnActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_PREPARE, true);
    }

    static Intent pickerIntent(Context context) {
        return new Intent(context, ImeReturnActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        prepareMode = getIntent().getBooleanExtra(EXTRA_PREPARE, false);
        if (prepareMode) {
            prepare();
            return;
        }
        watchForImageKeyboard();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        prepareMode = intent.getBooleanExtra(EXTRA_PREPARE, false);
        pickerShown = false;
        pickerArmed = false;
        pickerPaused = false;
        if (prepareMode) prepare();
        else watchForImageKeyboard();
    }

    private void prepare() {
        if (androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            finishPrepare(true);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            awaitingPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
            return;
        }
        finishPrepare(false);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_NOTIFICATION) return;
        awaitingPermission = false;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        finishPrepare(granted || androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled());
    }

    private void finishPrepare(boolean canNotify) {
        if (canNotify) ImeReturnNotifier.show(this);
        else Toast.makeText(this, "没有通知时，切回来请点屏幕下方的键盘图标，选「魔裁图片输入法」。", Toast.LENGTH_LONG).show();
        MahouInputMethodService.leaveForTextKeyboard();
        finish();
    }

    private void watchForImageKeyboard() {
        if (imeSetting != null) return;
        imeSetting = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                if (InputMethodSupport.isSelected(ImeReturnActivity.this)) closeAfterReturn();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, imeSetting);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prepareMode || awaitingPermission) return;
        if (InputMethodSupport.isSelected(this)) {
            closeAfterReturn();
            return;
        }
        if (!pickerShown) {
            pickerShown = true;
            // #region agent log
            Log.i("MahouDebug", "return picker");
            // #endregion
            InputMethodSupport.showPicker(this);
            getWindow().getDecorView().post(() -> pickerArmed = true);
            return;
        }
        if (pickerArmed && pickerPaused) finish();
    }

    @Override protected void onPause() {
        super.onPause();
        if (pickerArmed) pickerPaused = true;
    }

    private void closeAfterReturn() {
        ImeReturnNotifier.hide(this);
        ImeReturnChip.hide(this);
        finish();
    }

    @Override protected void onDestroy() {
        if (imeSetting != null) getContentResolver().unregisterContentObserver(imeSetting);
        super.onDestroy();
    }
}
