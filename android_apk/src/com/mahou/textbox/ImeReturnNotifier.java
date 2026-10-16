package com.mahou.textbox;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** Visible while the phone's own keyboard is selected, because that keyboard has no return button. */
final class ImeReturnNotifier {
    static final int ID = 6002;
    private static final String CHANNEL = "mahou-ime-return";

    private ImeReturnNotifier() {}

    static void show(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "回到图片输入法", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("切到原来的键盘时，点此通知回到魔裁图片输入法");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(context, 7,
                ImeReturnActivity.pickerIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(ID, new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("回到魔裁图片输入法")
                .setContentText("原来的键盘没有返回按钮，点这里切回来")
                .setContentIntent(open)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build());
    }

    static void hide(Context context) {
        NotificationManagerCompat.from(context).cancel(ID);
    }
}
