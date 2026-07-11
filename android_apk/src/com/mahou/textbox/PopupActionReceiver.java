package com.mahou.textbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class PopupActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (PopupService.ACTION_STOP_POPUP.equals(intent.getAction())) {
            context.stopService(new Intent(context, PopupService.class));
            return;
        }
        if (PopupService.ACTION_RESTORE_POPUP.equals(intent.getAction())) {
            Intent service = new Intent(context, PopupService.class)
                    .setAction(PopupService.ACTION_RESTORE_POPUP);
            ContextCompat.startForegroundService(context, service);
        }
    }
}
