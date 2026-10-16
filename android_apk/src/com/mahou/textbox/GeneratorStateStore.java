package com.mahou.textbox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Iterator;

/** Shared by the main editor and overlay WebViews in the app process. */
public final class GeneratorStateStore {
    public static final String ACTION_STATE_CHANGED = "com.mahou.textbox.GENERATOR_STATE_CHANGED";
    public static final String EXTRA_STATE_JSON = "stateJson";

    private static final String PREFS_NAME = "mahou_generator_state";
    private static final String[] FIELDS = {"text", "character", "emotion", "background"};
    private static final int MAX_PATCH_CHARS = 128 * 1024;
    private static final int MAX_FIELD_CHARS = 16 * 1024;

    private GeneratorStateStore() {
    }

    // Reading does not seed the store: revision zero lets JS migrate legacy storage once.
    public static synchronized String read(Context context) {
        return readState(preferences(context)).toString();
    }

    public static synchronized String update(Context context, String patchJson) {
        JSONObject patch = parsePatch(patchJson);
        SharedPreferences preferences = preferences(context);
        JSONObject state = readState(preferences);
        if (patch.length() == 0) {
            return state.toString();
        }
        long revision = state.optLong("revision");
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("Generator revision exhausted");
        }
        try {
            SharedPreferences.Editor editor = preferences.edit();
            for (String field : FIELDS) {
                if (patch.has(field)) {
                    String value = patch.getString(field);
                    state.put(field, value);
                    editor.putString(field, value);
                }
            }
            state.put("revision", revision + 1);
            editor.putLong("revision", revision + 1);
            // apply() updates SharedPreferences in memory immediately and writes to disk
            // asynchronously. Text edits call update() for every keystroke, so commit()
            // would block the WebView bridge thread on disk I/O while the user is typing.
            editor.apply();
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid generator patch", error);
        }
        String json = state.toString();
        // A package-scoped broadcast also reaches receivers in this same process.
        context.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_STATE_JSON, json));
        return json;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static JSONObject readState(SharedPreferences preferences) {
        JSONObject state = new JSONObject();
        try {
            for (String field : FIELDS) {
                state.put(field, preferences.getString(field, ""));
            }
            state.put("revision", Math.max(0L, preferences.getLong("revision", 0L)));
        } catch (JSONException error) {
            throw new IllegalStateException("Cannot read generator state", error);
        }
        return state;
    }

    private static JSONObject parsePatch(String json) {
        if (json == null || json.length() > MAX_PATCH_CHARS) {
            throw new IllegalArgumentException("Invalid generator patch size");
        }
        try {
            JSONTokener tokens = new JSONTokener(json);
            Object parsed = tokens.nextValue();
            if (!(parsed instanceof JSONObject) || tokens.nextClean() != 0) {
                throw new IllegalArgumentException("Generator patch must be a JSON object");
            }
            JSONObject patch = (JSONObject) parsed;
            Iterator<String> keys = patch.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                boolean allowed = false;
                for (String field : FIELDS) {
                    if (field.equals(key)) {
                        allowed = true;
                        break;
                    }
                }
                Object value = patch.get(key);
                if (!allowed || !(value instanceof String)
                        || ((String) value).length() > MAX_FIELD_CHARS) {
                    throw new IllegalArgumentException("Invalid generator field: " + key);
                }
            }
            return patch;
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid generator JSON", error);
        }
    }
}
