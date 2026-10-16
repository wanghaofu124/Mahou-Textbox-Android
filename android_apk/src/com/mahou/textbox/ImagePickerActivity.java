package com.mahou.textbox;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImagePickerActivity extends ComponentActivity {
    public static final String ACTION_IMAGE_PICKED = "com.mahou.textbox.IMAGE_PICKED";
    public static final String EXTRA_IMAGE_URL = "imageUrl";
    // Kept for source compatibility only; no image bytes are sent over Binder.
    @Deprecated
    public static final String EXTRA_DATA_URL = "dataUrl";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_CANCELLED = "cancelled";
    public static final String EXTRA_TARGET = "target";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_OWNER_ID = "ownerId";

    private static final int MAX_IMAGE_EDGE = 1280;
    static final int MAX_SOURCE_BYTES = 20 * 1024 * 1024;
    static final long MAX_IMPORT_BYTES = 8L * 1024 * 1024;
    static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;
    static final long IMPORT_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String target;
    private String kind;
    private String ownerId;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) {
                    sendBroadcast(baseResultIntent().putExtra(EXTRA_CANCELLED, true));
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
            String imageUrl;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalStateException("无法打开所选图片");
                }
                imageUrl = cacheImport(this, input);
            }
            Intent result = imageResultIntent(this, target, kind, ownerId, imageUrl);
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

    static Intent imageResultIntent(Context context, String target, String kind, String ownerId, String url) {
        if (!isImportUrl(url)) {
            throw new IllegalArgumentException("Invalid import URL");
        }
        return new Intent(ACTION_IMAGE_PICKED).setPackage(context.getPackageName())
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_OWNER_ID, ownerId)
                .putExtra(EXTRA_IMAGE_URL, url);
    }

    static synchronized String cacheImport(Context context, InputStream input) throws Exception {
        byte[] sourceBytes = readLimitedBytes(input, MAX_SOURCE_BYTES);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("无法识别图片尺寸");
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_EDGE * 2);
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, decode);
        if (decoded == null) {
            throw new IllegalArgumentException("图片解码失败");
        }

        Bitmap exportBitmap = decoded;
        try {
            Bitmap oriented = applyExifOrientation(exportBitmap, sourceBytes);
            if (oriented != exportBitmap) {
                exportBitmap.recycle();
                exportBitmap = oriented;
            }
            Bitmap scaled = scaleForStorage(exportBitmap);
            if (scaled != exportBitmap) {
                exportBitmap.recycle();
                exportBitmap = scaled;
            }
            // Preserve alpha capability even when sampling removes a small transparent region.
            boolean preserveAlpha = exportBitmap.hasAlpha();
            Bitmap.CompressFormat format = preserveAlpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            File root = importDirectory(context);
            if (!root.isDirectory() && !root.mkdirs()) {
                throw new IOException("Cannot create image import cache");
            }
            cleanupImports(context, System.currentTimeMillis());
            long available = MAX_CACHE_BYTES - generatedCacheBytes(root);
            if (available <= 0) {
                throw new IOException("Image import cache is full; try again later");
            }
            String name = "mahou-import-" + UUID.randomUUID()
                    + (preserveAlpha ? ".png" : ".jpg");
            File file = new File(root, name);
            boolean complete = false;
            try {
                try (BoundedOutputStream output = new BoundedOutputStream(
                        new FileOutputStream(file), Math.min(MAX_IMPORT_BYTES, available))) {
                    if (!exportBitmap.compress(format, preserveAlpha ? 100 : 90, output)) {
                        throw new IllegalStateException("图片编码失败");
                    }
                    output.verifyComplete();
                    output.flush();
                }
                complete = file.length() > 0;
                if (!complete) {
                    throw new IOException("Empty image import");
                }
                return LocalWebContent.IMPORT_BASE_URL + name;
            } finally {
                if (!complete) {
                    file.delete();
                }
            }
        } finally {
            exportBitmap.recycle();
        }
    }

    static File importDirectory(Context context) throws IOException {
        File root = new File(context.getCacheDir().getCanonicalFile(), "imports");
        if (!root.getAbsoluteFile().equals(root.getCanonicalFile())) {
            throw new IOException("Unsafe image import cache");
        }
        return root;
    }

    static boolean isGeneratedImportName(String name) {
        return name != null && name.matches("mahou-import-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)");
    }

    public static boolean isImportUrl(String url) {
        if (url == null || !url.startsWith(LocalWebContent.IMPORT_BASE_URL)) {
            return false;
        }
        return isGeneratedImportName(url.substring(LocalWebContent.IMPORT_BASE_URL.length()));
    }

    static synchronized void cleanupImports(Context context, long now) throws IOException {
        File root = importDirectory(context);
        File[] files = root.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            long modified = file.lastModified();
            if (isSafeImportFile(root, file) && modified > 0
                    && now - modified >= IMPORT_MAX_AGE_MS
                    && !LocalWebContent.isImportActive(file.getName())) {
                file.delete();
            }
        }
    }

    private static long generatedCacheBytes(File root) throws IOException {
        long bytes = 0;
        File[] files = root.listFiles();
        if (files != null) {
            for (File file : files) {
                if (isSafeImportFile(root, file)) {
                    bytes += file.length();
                }
            }
        }
        return bytes;
    }

    private static boolean isSafeImportFile(File root, File file) throws IOException {
        return isGeneratedImportName(file.getName()) && file.isFile()
                && file.getAbsoluteFile().equals(file.getCanonicalFile())
                && root.equals(file.getCanonicalFile().getParentFile());
    }

    private static final class BoundedOutputStream extends FilterOutputStream {
        private final long limit;
        private long written;
        private boolean failed;

        BoundedOutputStream(OutputStream output, long limit) {
            super(output);
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            try {
                out.write(value);
            } catch (IOException error) {
                failed = true;
                throw error;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            reserve(length);
            try {
                out.write(bytes, offset, length);
            } catch (IOException error) {
                failed = true;
                throw error;
            }
        }

        private void reserve(int length) throws IOException {
            if (length > limit - written) {
                failed = true;
                throw new IOException("Image import cache limit exceeded");
            }
            written += length;
        }

        void verifyComplete() throws IOException {
            if (failed || written == 0) {
                throw new IOException("Cannot write complete image import");
            }
        }
    }

    private static Bitmap applyExifOrientation(Bitmap source, byte[] bytes) {
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

    private static byte[] readLimitedBytes(InputStream input, int maxBytes) throws Exception {
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

    private static int calculateInSampleSize(int width, int height, int targetEdge) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / sample > targetEdge) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleForStorage(Bitmap bitmap) {
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
