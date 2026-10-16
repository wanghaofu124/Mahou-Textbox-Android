package com.mahou.textbox;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONObject;

final class InputMethodSupport {
    static final String ACTION_MODE_CHANGED = "com.mahou.textbox.INPUT_MODE_CHANGED";
    private static final String PREFS = "mahou_popup_prefs";

    private InputMethodSupport() {}

    static boolean isSelected(Context context) {
        String current = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName selected = ComponentName.unflattenFromString(current == null ? "" : current);
        return new ComponentName(context, MahouInputMethodService.class).equals(selected);
    }

    static boolean isImageMode(Context context) {
        return !"popup".equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("chatMode", "keyboard"));
    }

    static boolean useBuiltInTextKeyboard(Context context) {
        return "builtin".equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("textKeyboard", "system"));
    }

    static void activateImageMode(Context context) {
        context.stopService(new Intent(context, PopupService.class));
        setMode(context, "keyboard");
    }

    static void activatePopupMode(Context context) { setMode(context, "popup"); }

    private static void setMode(Context context, String mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("chatMode", mode).remove("textKeyboardHandoff")
                .remove("textEntryUntil").remove("keyboardReturnDeadline").apply();
        NotificationManagerCompat.from(context).cancel(6001);
        context.sendBroadcast(new Intent(ACTION_MODE_CHANGED).setPackage(context.getPackageName()));
    }

    static String status(Context context) {
        JSONObject result = new JSONObject();
        try {
            InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            boolean enabled = false;
            if (manager != null) for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
                if (context.getPackageName().equals(info.getPackageName())
                        && MahouInputMethodService.class.getName().equals(info.getServiceName())) enabled = true;
            }
            result.put("enabled", enabled).put("selected", isSelected(context))
                    .put("mode", isImageMode(context) ? "keyboard" : "popup")
                    .put("directSendEnabled", QqSendAccessibilityService.isEnabled(context));
        } catch (Exception ignored) {}
        return result.toString();
    }

    static String textKeyboard(Context context) {
        InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager == null) return null;
        String previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("previousTextInputMethod", "");
        String fallback = null;
        for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
            if (context.getPackageName().equals(info.getPackageName())) continue;
            if (info.getId().equals(previous)) return previous;
            if (fallback == null) fallback = info.getId();
        }
        return fallback;
    }

    static void rememberTextKeyboard(Context context, String id) {
        if (id == null || id.startsWith(context.getPackageName() + "/")) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("previousTextInputMethod", id).apply();
    }

    static boolean selectThisIme(Context context) {
        if (isSelected(context)) return true;
        String id = new ComponentName(context, MahouInputMethodService.class).flattenToShortString();
        try {
            return Settings.Secure.putString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, id) && isSelected(context);
        } catch (SecurityException ignored) { return false; }
    }

    static void openSettings(Context context) {
        context.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // The picker is only used for initial selection or an explicit change by the user.
    static void showPicker(Context context) {
        InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showInputMethodPicker();
    }
}
