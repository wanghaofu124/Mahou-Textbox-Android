package com.mahou.textbox;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/** Everything StickerRenderer needs to draw one image. */
final class RenderParams {
    final String text;
    final String characterKey;
    final String emotion;
    final String backgroundKey;
    final int width;

    RenderParams(String text, String characterKey, String emotion, String backgroundKey, int width) {
        this.text = text;
        this.characterKey = characterKey;
        this.emotion = emotion;
        this.backgroundKey = backgroundKey;
        this.width = width;
    }

    static RenderParams fromState(Context context, int width) throws JSONException {
        JSONObject state = new JSONObject(GeneratorStateStore.read(context));
        return new RenderParams(
                state.getString("text"),
                state.getString("character"),
                state.getString("emotion"),
                state.getString("background"),
                width);
    }
}
