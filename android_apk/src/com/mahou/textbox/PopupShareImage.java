package com.mahou.textbox;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Decode/validate/write only on the export worker; PNG bytes do not need recompression. */
final class PopupShareImage {
    private static final int MAX_ENCODED_LENGTH = 24 * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;

    private PopupShareImage() {}

    static File prepare(Context context, String dataUrl) throws IOException {
        if (dataUrl == null || dataUrl.isEmpty() || dataUrl.length() > MAX_ENCODED_LENGTH) {
            throw new IOException("Invalid or oversized PNG export.");
        }
        String prefix = "data:image/png;base64,";
        if (dataUrl.startsWith("data:") && !dataUrl.startsWith(prefix)) {
            throw new IOException("The generated image must be a PNG.");
        }
        byte[] png;
        try {
            png = Base64.decode(dataUrl.startsWith(prefix) ? dataUrl.substring(prefix.length()) : dataUrl,
                    Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid PNG encoding.", error);
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
        if (!"image/png".equals(bounds.outMimeType) || bounds.outWidth <= 0 || bounds.outHeight <= 0
                || (long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) {
            throw new IOException("Invalid or oversized PNG image.");
        }
        if (BuildConfig.DEBUG) {
            Log.d("MahouShare", "popup PNG size: " + bounds.outWidth + "x" + bounds.outHeight);
        }
        File directory = new File(context.getCacheDir(), "share");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create share cache directory.");
        }
        File temporary = File.createTempFile("popup-export-", ".png", directory);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(png);
        } catch (IOException error) {
            discard(temporary);
            throw error;
        }
        return temporary;
    }

    static void publish(File temporary) throws IOException {
        File target = new File(temporary.getParentFile(), "mahou_textbox.png");
        if (!temporary.renameTo(target)) throw new IOException("Cannot publish share image.");
    }

    static void discard(File temporary) {
        if (temporary != null && temporary.exists() && !temporary.delete() && BuildConfig.DEBUG) {
            Log.d("MahouShare", "Could not remove a temporary export.");
        }
    }
}
