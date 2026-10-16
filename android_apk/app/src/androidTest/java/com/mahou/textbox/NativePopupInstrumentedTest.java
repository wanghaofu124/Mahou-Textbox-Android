package com.mahou.textbox;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import androidx.activity.ComponentActivity;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NativePopupInstrumentedTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test public void popupBridgeExposesNativeAndStateContracts() throws Exception {
        assertBridge("setEditing", boolean.class);
        assertBridge("dismissKeyboard");
        assertBridge("setEditorExpanded", boolean.class);
        assertBridge("setPopupOpacity", int.class);
        assertBridge("getPopupOpacity");
        assertBridge("getGeneratorState");
        assertBridge("updateGeneratorState", String.class);
        assertBridge("closePopup");
        assertBridge("minimizePopup");
        assertBridge("requestPopupShare", boolean.class);
        assertBridge("completePopupExport", String.class, String.class, boolean.class);
        assertTrue(ComponentActivity.class.isAssignableFrom(ShareProxyActivity.class));
    }

    @Test public void pngWorkerPreservesOriginalBytesWithoutBitmapRecompression() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff307050);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        bitmap.recycle();
        byte[] png = output.toByteArray();
        String dataUrl = "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        File temporary = null;
        try {
            temporary = worker.submit(() -> PopupShareImage.prepare(context, dataUrl)).get();
            assertArrayEquals(png, readBytes(temporary));
        } finally {
            PopupShareImage.discard(temporary);
            worker.shutdownNow();
        }
    }

    @Test public void invalidPngFailsWithoutReplacingPublishedImage() throws Exception {
        File target = new File(new File(context.getCacheDir(), "share"), "mahou_textbox.png");
        long previousLength = target.exists() ? target.length() : -1;
        try {
            PopupShareImage.prepare(context, "data:image/png;base64,bm90IGFuIGltYWdl");
            fail("Invalid PNG must not be accepted");
        } catch (IOException expected) {
            assertEquals(previousLength, target.exists() ? target.length() : -1);
        }
    }

    @Test public void shareSessionSurvivesServiceAbsenceAndRejectsStaleCompletion() {
        String previous = PopupShareSessionStore.pending(context);
        long previousRequest = PopupShareSessionStore.request(context);
        assertNull("Do not run this test during a live popup share", previous);
        try {
            PopupShareSessionStore.begin(context, "native-popup-test", 7);
            PopupShareSessionStore.complete(context, "stale");
            assertEquals("native-popup-test", PopupShareSessionStore.pending(context));
            assertEquals(7, PopupShareSessionStore.request(context));
            PopupShareSessionStore.complete(context, "native-popup-test");
            assertNull(PopupShareSessionStore.pending(context));
            PopupShareSessionStore.complete(context, "native-popup-test");
            assertEquals(0, PopupShareSessionStore.request(context));
        } finally {
            PopupShareSessionStore.complete(context, "native-popup-test");
            if (previous != null) PopupShareSessionStore.begin(context, previous, previousRequest);
        }
    }

    private void assertBridge(String name, Class<?>... parameters) throws Exception {
        Method method = PopupService.PopupBridge.class.getMethod(name, parameters);
        assertNotNull(method.getAnnotation(JavascriptInterface.class));
    }

    private byte[] readBytes(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
}
