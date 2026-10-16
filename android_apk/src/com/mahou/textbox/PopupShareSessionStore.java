package com.mahou.textbox;

import android.content.Context;
import android.content.SharedPreferences;

/** The result Activity can complete the session even while the overlay service is not alive. */
final class PopupShareSessionStore {
    private static final String SESSION = "pending_share_session";
    private static final String REQUEST = "pending_share_request";

    private PopupShareSessionStore() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("mahou_popup_prefs", Context.MODE_PRIVATE);
    }

    static void begin(Context context, String session, long request) {
        preferences(context).edit().putString(SESSION, session).putLong(REQUEST, request).apply();
    }

    static String pending(Context context) { return preferences(context).getString(SESSION, null); }
    static long request(Context context) { return preferences(context).getLong(REQUEST, 0); }

    static void complete(Context context, String session) {
        if (session != null && session.equals(pending(context))) {
            preferences(context).edit().remove(SESSION).remove(REQUEST).apply();
        }
    }
}
