package com.mahou.textbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Parcel;
import android.system.Os;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.WebViewClientCompat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ImageImportInstrumentedTest {
    private Context context;
    private File testCache;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        testCache = new File(app.getCacheDir(), "image-import-test-" + UUID.randomUUID());
        assertTrue(testCache.mkdirs());
        context = new ContextWrapper(app) {
            @Override
            public File getCacheDir() {
                return testCache;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        File canonical = testCache.getCanonicalFile();
        assertTrue(canonical.getName().startsWith("image-import-test-"));
        deleteTestFiles(canonical);
    }

    @Test
    public void largeTransparentPngUsesSmallUrlBroadcastAndLocalPngResponse() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1600, 1280, Bitmap.Config.ARGB_8888);
        byte[] source;
        try {
            Random random = new Random(42);
            int[] row = new int[bitmap.getWidth()];
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < row.length; x++) {
                    row[x] = 0x80000000 | (random.nextInt() & 0x00ffffff);
                }
                bitmap.setPixels(row, 0, row.length, 0, y, row.length, 1);
            }
            source = encode(bitmap, Bitmap.CompressFormat.PNG);
        } finally {
            bitmap.recycle();
        }
        assertTrue(source.length > 1024 * 1024);
        assertTrue(source.length < ImagePickerActivity.MAX_SOURCE_BYTES);
        String url = ImagePickerActivity.cacheImport(context, new ByteArrayInputStream(source));
        assertTrue(ImagePickerActivity.isImportUrl(url));
        File cached = importFile(url);
        assertTrue(cached.length() > 1024 * 1024);
        assertTrue(cached.length() <= ImagePickerActivity.MAX_IMPORT_BYTES);

        Intent result = ImagePickerActivity.imageResultIntent(context, "main", "emotion", "alisa", url);
        assertFalse(result.hasExtra(ImagePickerActivity.EXTRA_DATA_URL));
        Parcel parcel = Parcel.obtain();
        try {
            result.writeToParcel(parcel, 0);
            assertTrue("Binder payload must remain small", parcel.dataSize() < 2048);
        } finally {
            parcel.recycle();
        }
        BlockingQueue<Intent> received = new LinkedBlockingQueue<>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                received.add(intent);
            }
        };
        ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(ImagePickerActivity.ACTION_IMAGE_PICKED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        try {
            context.sendBroadcast(result);
            Intent delivered = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(delivered);
            assertEquals(url, delivered.getStringExtra(ImagePickerActivity.EXTRA_IMAGE_URL));
            assertEquals("main", delivered.getStringExtra(ImagePickerActivity.EXTRA_TARGET));
            assertEquals("emotion", delivered.getStringExtra(ImagePickerActivity.EXTRA_KIND));
            assertEquals("alisa", delivered.getStringExtra(ImagePickerActivity.EXTRA_OWNER_ID));
        } finally {
            context.unregisterReceiver(receiver);
        }

        WebResourceResponse response = LocalWebContent.createClient(context, null)
                .shouldInterceptRequest(null, url);
        assertEquals(200, response.getStatusCode());
        assertEquals("image/png", response.getMimeType());
        Bitmap decoded;
        try (InputStream input = response.getData()) {
            decoded = android.graphics.BitmapFactory.decodeStream(input);
        }
        assertNotNull(decoded);
        try {
            assertEquals(1280, decoded.getWidth());
            assertEquals(1024, decoded.getHeight());
            assertTrue(decoded.hasAlpha());
            assertTrue(Color.alpha(decoded.getPixel(0, 0)) < 255);
        } finally {
            decoded.recycle();
        }
    }

    @Test
    public void exifRotationIsAppliedBeforeCachingJpeg() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888);
        File source = new File(testCache, "orientation-source.jpg");
        try (FileOutputStream output = new FileOutputStream(source)) {
            bitmap.eraseColor(Color.RED);
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output));
        } finally {
            bitmap.recycle();
        }
        ExifInterface exif = new ExifInterface(source);
        exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
        exif.saveAttributes();
        String url;
        try (InputStream input = new FileInputStream(source)) {
            url = ImagePickerActivity.cacheImport(context, input);
        }
        assertTrue(url.endsWith(".jpg"));
        WebResourceResponse response = LocalWebContent.createClient(context, null)
                .shouldInterceptRequest(null, url);
        assertEquals("image/jpeg", response.getMimeType());
        Bitmap decoded;
        try (InputStream input = response.getData()) {
            decoded = android.graphics.BitmapFactory.decodeStream(input);
        }
        assertNotNull(decoded);
        try {
            assertEquals(80, decoded.getWidth());
            assertEquals(120, decoded.getHeight());
        } finally {
            decoded.recycle();
        }
    }

    @Test
    public void oversizedSourceAndFullCacheAreRejectedWithoutPartialFile() throws Exception {
        try {
            ImagePickerActivity.cacheImport(context, new InputStream() {
                private int remaining = ImagePickerActivity.MAX_SOURCE_BYTES + 1;

                @Override
                public int read() {
                    return remaining-- > 0 ? 0 : -1;
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    if (remaining <= 0) return -1;
                    int count = Math.min(length, remaining);
                    java.util.Arrays.fill(bytes, offset, offset + count, (byte) 0);
                    remaining -= count;
                    return count;
                }
            });
            fail("Source larger than 20 MB accepted");
        } catch (IllegalArgumentException expected) {
            assertFalse(ImagePickerActivity.importDirectory(context).exists());
        }
        File full = generatedFile();
        try (RandomAccessFile file = new RandomAccessFile(full, "rw")) {
            file.setLength(ImagePickerActivity.MAX_CACHE_BYTES);
        }
        try {
            ImagePickerActivity.cacheImport(context, new ByteArrayInputStream(smallPng()));
            fail("Full cache accepted another image");
        } catch (java.io.IOException expected) {
            assertEquals(1, ImagePickerActivity.importDirectory(context).listFiles().length);
            assertEquals(ImagePickerActivity.MAX_CACHE_BYTES, full.length());
        }
        try (RandomAccessFile file = new RandomAccessFile(full, "rw")) {
            file.setLength(ImagePickerActivity.MAX_CACHE_BYTES - 16);
        }
        try {
            ImagePickerActivity.cacheImport(context, new ByteArrayInputStream(smallPng()));
            fail("Image that cannot fit in remaining cache accepted");
        } catch (java.io.IOException | IllegalStateException expected) {
            assertEquals("Partial import must be removed", 1,
                    ImagePickerActivity.importDirectory(context).listFiles().length);
        }
    }

    @Test
    public void cleanupOnlyDeletesOldGeneratedInactiveFiles() throws Exception {
        File stale = generatedFile();
        File active = generatedFile();
        File recent = generatedFile();
        File user = new File(stale.getParentFile(), "user-photo.png");
        write(user, smallPng());
        long now = System.currentTimeMillis();
        long old = now - ImagePickerActivity.IMPORT_MAX_AGE_MS - 1000;
        assertTrue(stale.setLastModified(old));
        assertTrue(active.setLastModified(old));
        assertTrue(user.setLastModified(old));

        AtomicReference<WebView> view = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                view.set(new WebView(ApplicationProvider.getApplicationContext())));
        WebViewClientCompat client = LocalWebContent.createClient(context, null);
        try {
            WebResourceResponse response = client.shouldInterceptRequest(view.get(),
                    LocalWebContent.IMPORT_BASE_URL + active.getName());
            response.getData().close();
            ImagePickerActivity.cleanupImports(context, now);
            assertFalse(stale.exists());
            assertTrue(active.exists());
            assertTrue(recent.exists());
            assertTrue(user.exists());

            client.onPageStarted(view.get(), LocalWebContent.START_URL, null);
            ImagePickerActivity.cleanupImports(context, now);
            assertFalse(active.exists());
            assertTrue(recent.exists());
            assertTrue(user.exists());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> view.get().destroy());
        }
    }

    @Test
    public void importPathsTraversalSymlinksAndExternalRequestsAreBlocked() throws Exception {
        WebViewClientCompat client = LocalWebContent.createClient(context, null);
        for (String url : new String[]{"https://example.com/image.png", "http://appassets.androidplatform.net/assets/mobile.html",
                LocalWebContent.ORIGIN + "/imports/user-photo.png",
                LocalWebContent.ORIGIN + "/imports/%2e%2e/private.png",
                LocalWebContent.ORIGIN + "/custom/../private.png",
                LocalWebContent.ORIGIN + "/assets/%2e%2e/mobile.html",
                "https://user@appassets.androidplatform.net/assets/mobile.html",
                "https://appassets.androidplatform.net:444/assets/mobile.html"}) {
            WebResourceResponse blocked = client.shouldInterceptRequest(null, url);
            assertEquals(url, 403, blocked.getStatusCode());
            blocked.getData().close();
            assertTrue(client.shouldOverrideUrlLoading(null, url));
        }
        File target = new File(testCache, "private.png");
        write(target, smallPng());
        File link = new File(ImagePickerActivity.importDirectory(context),
                "mahou-import-" + UUID.randomUUID() + ".png");
        assertTrue(link.getParentFile().mkdirs());
        Os.symlink(target.getAbsolutePath(), link.getAbsolutePath());
        WebResourceResponse blocked = client.shouldInterceptRequest(null,
                LocalWebContent.IMPORT_BASE_URL + link.getName());
        assertEquals(404, blocked.getStatusCode());
        blocked.getData().close();
        ImagePickerActivity.cleanupImports(context, System.currentTimeMillis() + ImagePickerActivity.IMPORT_MAX_AGE_MS);
        assertTrue(target.exists());
        assertTrue(link.delete());
        assertFalse(client.shouldOverrideUrlLoading(null, LocalWebContent.DOCK_URL));
    }

    @Test
    public void bothLocalEditorsNotifyPageReadyWithQueryParameters() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        WebViewClientCompat client = LocalWebContent.createClient(context, calls::incrementAndGet);
        client.onPageFinished(null, LocalWebContent.START_URL + "?popup=1");
        client.onPageFinished(null, LocalWebContent.DOCK_URL + "?dock=1");
        client.onPageFinished(null, "https://example.com/assets/mobile.html");
        assertEquals(2, calls.get());
    }

    private byte[] smallPng() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(Color.TRANSPARENT);
            return encode(bitmap, Bitmap.CompressFormat.PNG);
        } finally {
            bitmap.recycle();
        }
    }

    private byte[] encode(Bitmap bitmap, Bitmap.CompressFormat format) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(bitmap.compress(format, 100, output));
            return output.toByteArray();
        }
    }

    private File generatedFile() throws Exception {
        File root = ImagePickerActivity.importDirectory(context);
        if (!root.isDirectory()) assertTrue(root.mkdirs());
        File file = new File(root, "mahou-import-" + UUID.randomUUID() + ".png");
        write(file, smallPng());
        return file;
    }

    private File importFile(String url) throws Exception {
        return new File(ImagePickerActivity.importDirectory(context),
                url.substring(LocalWebContent.IMPORT_BASE_URL.length()));
    }

    private void write(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }

    private void deleteTestFiles(File file) throws Exception {
        assertTrue(file.getCanonicalPath().startsWith(testCache.getCanonicalPath()));
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTestFiles(child);
        }
        assertTrue(file.delete());
    }
}
