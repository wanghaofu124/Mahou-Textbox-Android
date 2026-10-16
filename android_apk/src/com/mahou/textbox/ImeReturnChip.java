package com.mahou.textbox;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/** Shown only after the user explicitly leaves for a text keyboard. */
final class ImeReturnChip {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static TextView chip;
    private static WindowManager windows;

    private ImeReturnChip() {}

    static void show(Context context) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "已切到文字键盘。点系统键盘图标，选回“魔裁图片输入法”。", Toast.LENGTH_LONG).show();
            return;
        }
        Context app = context.getApplicationContext();
        MAIN.post(() -> showOnMain(app));
    }

    static void hide(Context context) { MAIN.post(ImeReturnChip::hideOnMain); }

    private static void showOnMain(Context app) {
        if (chip != null) return;
        WindowManager manager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) return;
        float density = app.getResources().getDisplayMetrics().density;
        TextView view = new TextView(app);
        view.setText("回魔裁");
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        int pad = Math.round(14 * density);
        view.setPadding(pad, pad, pad, pad);
        view.setContentDescription("回到魔裁图片输入法");
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF21B1B1B);
        background.setCornerRadius(22 * density);
        view.setBackground(background);
        view.setOnClickListener(clicked -> {
            if (InputMethodSupport.selectThisIme(app)) { hideOnMain(); return; }
            app.startActivity(ImeReturnActivity.pickerIntent(app));
        });
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = Math.round(12 * density);
        params.y = Math.round(48 * density);
        try {
            manager.addView(view, params);
            chip = view;
            windows = manager;
        } catch (RuntimeException ignored) {
            chip = null;
            windows = null;
            Toast.makeText(app, "已切到文字键盘。点系统键盘图标，选回“魔裁图片输入法”。", Toast.LENGTH_LONG).show();
        }
    }

    private static void hideOnMain() {
        if (chip == null || windows == null) return;
        try { windows.removeView(chip); } catch (RuntimeException ignored) {}
        chip = null;
        windows = null;
    }
}
