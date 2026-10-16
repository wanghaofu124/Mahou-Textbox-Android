package com.mahou.textbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

public final class ProbeInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        // #region agent log
        Log.i("MahouDebug", "probe-install status=" + status);
        // #endregion
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return;
        Intent confirm = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                : intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirm == null) return;
        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
