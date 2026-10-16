package com.mahou.textbox;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;

/** Checks the optional QQ probe and opens the system screens a normal app is allowed to open. */
public final class QqProbeGuide {
    static final String PROBE_PACKAGE = "com.mahou.qqsendprobe";
    private static final String ASSET = "qq-probe.apk";
    private static final String[][] ROOT_MANAGERS = {
            {"com.topjohnwu.magisk", "Magisk"},
            {"com.rifsxd.ksu.manager", "KernelSU"},
            {"me.bmax.apatch", "APatch"}
    };
    private static final String LSPOSED = "org.lsposed.manager";

    private QqProbeGuide() {}

    static String status(Context context) {
        try {
            PackageManager packages = context.getPackageManager();
            String root = "";
            for (String[] manager : ROOT_MANAGERS) if (installed(packages, manager[0])) root = manager[1];
            String qq = version(packages, "com.tencent.mobileqq");
            if (qq.isEmpty()) qq = version(packages, "com.tencent.tim");
            boolean canInstall = Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls();
            return new JSONObject()
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("rootManager", root)
                    .put("lsposed", installed(packages, LSPOSED))
                    .put("qqVersion", qq)
                    .put("qqMatch", "9.3.70".equals(qq))
                    .put("probeInstalled", installed(packages, PROBE_PACKAGE))
                    .put("canInstallPackages", canInstall)
                    .toString();
        } catch (Exception error) {
            return "{}";
        }
    }

    static void install(Context context) {
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return;
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            session = installer.openSession(installer.createSession(params));
            try (InputStream input = context.getAssets().open(ASSET); OutputStream output = session.openWrite("probe", 0, -1)) {
                byte[] buffer = new byte[16384];
                for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
                session.fsync(output);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            Intent callback = new Intent(context, ProbeInstallReceiver.class).setPackage(context.getPackageName());
            session.commit(PendingIntent.getBroadcast(context, 0, callback, flags).getIntentSender());
            session = null;
            // #region agent log
            Log.i("MahouDebug", "probe-install committed");
            // #endregion
        } catch (Exception error) {
            // #region agent log
            Log.i("MahouDebug", "probe-install failed=" + error.getClass().getSimpleName());
            // #endregion
            if (session != null) session.abandon();
        }
    }

    static void openRootManager(Context context) {
        PackageManager packages = context.getPackageManager();
        for (String[] manager : ROOT_MANAGERS) {
            Intent launch = packages.getLaunchIntentForPackage(manager[0]);
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
    }

    static void openLsposed(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(LSPOSED);
        if (launch == null) return;
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void openQq(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.tencent.mobileqq");
        if (launch == null) launch = context.getPackageManager().getLaunchIntentForPackage("com.tencent.tim");
        if (launch == null) return;
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static boolean installed(PackageManager packages, String name) {
        try { packages.getPackageInfo(name, 0); return true; }
        catch (PackageManager.NameNotFoundException error) { return false; }
    }

    private static String version(PackageManager packages, String name) {
        try {
            PackageInfo info = packages.getPackageInfo(name, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException error) { return ""; }
    }
}
