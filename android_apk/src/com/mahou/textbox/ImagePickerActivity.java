package com.mahou.textbox;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImagePickerActivity extends ComponentActivity {
    public static final String ACTION_IMAGE_PICKED = "com.mahou.textbox.IMAGE_PICKED";
    public static final String EXTRA_DATA_URL = "dataUrl";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_TARGET = "target";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_OWNER_ID = "ownerId";

    private static final int MAX_IMAGE_EDGE = 1280;
    private static final int MAX_SOURCE_BYTES = 20 * 1024 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String target;
    private String kind;
    private String ownerId;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) {
                    Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                executor.execute(() -> processPickedImage(uri));
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        target = getIntent().getStringExtra(EXTRA_TARGET);
        kind = getIntent().getStringExtra(EXTRA_KIND);
        ownerId = getIntent().getStringExtra(EXTRA_OWNER_ID);
        if (savedInstanceState == null) {
            picker.launch(new String[]{"image/png", "image/jpeg", "image/webp"});
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void processPickedImage(Uri uri) {
        try {
            String dataUrl = readAndEncode(uri);
            Intent result = baseResultIntent();
            result.putExtra(EXTRA_DATA_URL, dataUrl);
            sendBroadcast(result);
            runOnUiThread(this::finish);
        } catch (Exception error) {
            Intent result = baseResultIntent();
            result.putExtra(EXTRA_ERROR, safeMessage(error, "导入图片失败"));
            sendBroadcast(result);
            runOnUiThread(() -> {
                Toast.makeText(this, "导入失败：" + safeMessage(error, "无法读取图片"), Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    private Intent baseResultIntent() {
        Intent result = new Intent(ACTION_IMAGE_PICKED).setPackage(getPackageName());
        result.putExtra(EXTRA_TARGET, target);
        result.putExtra(EXTRA_KIND, kind);
        result.putExtra(EXTRA_OWNER_ID, ownerId);
        return result;
    }

    private String readAndEncode(Uri uri) throws Exception {
        byte[] sourceBytes;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalStateException("无法打开所选图片");
            }
            sourceBytes = readLimitedBytes(input, MAX_SOURCE_BYTES);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("无法识别图片尺寸");
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_EDGE * 2);
        Bitmap decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, decode);
        if (decoded == null) {
            throw new IllegalArgumentException("图片解码失败");
        }

        Bitmap oriented = applyExifOrientation(decoded, sourceBytes);
        if (oriented != decoded) {
            decoded.recycle();
        }
        Bitmap exportBitmap = scaleForStorage(oriented);
        if (exportBitmap != oriented) {
            oriented.recycle();
        }

        try {
            boolean preserveAlpha = hasTransparentPixels(exportBitmap);
            Bitmap.CompressFormat format = preserveAlpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (!exportBitmap.compress(format, preserveAlpha ? 100 : 90, output)) {
                    throw new IllegalStateException("图片编码失败");
                }
                String mime = preserveAlpha ? "image/png" : "image/jpeg";
                return "data:" + mime + ";base64,"
                        + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            }
        } finally {
            exportBitmap.recycle();
        }
    }

    private Bitmap applyExifOrientation(Bitmap source, byte[] bytes) {
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(bytes));
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.setScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.setScale(1f, -1f);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.setRotate(90f);
                    matrix.postScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.setRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.setRotate(-90f);
                    matrix.postScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(-90f);
                    break;
                default:
                    return source;
            }
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (Exception ignored) {
            return source;
        }
    }

    private byte[] readLimitedBytes(InputStream input, int maxBytes) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("图片不能超过 20 MB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private int calculateInSampleSize(int width, int height, int targetEdge) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / (sample * 2) >= targetEdge) {
            sample *= 2;
        }
        return sample;
    }

    private boolean hasTransparentPixels(Bitmap bitmap) {
        if (!bitmap.hasAlpha()) {
            return false;
        }
        int width = bitmap.getWidth();
        int[] row = new int[width];
        for (int y = 0; y < bitmap.getHeight(); y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int pixel : row) {
                if ((pixel >>> 24) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private Bitmap scaleForStorage(Bitmap bitmap) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= MAX_IMAGE_EDGE) {
            return bitmap;
        }
        float scale = (float) MAX_IMAGE_EDGE / longest;
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)),
                true
        );
    }

    private String safeMessage(Exception error, String fallback) {
        return error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? fallback
                : error.getMessage();
    }
}
