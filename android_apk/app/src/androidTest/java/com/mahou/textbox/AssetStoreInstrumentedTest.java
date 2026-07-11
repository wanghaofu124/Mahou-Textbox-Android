package com.mahou.textbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public class AssetStoreInstrumentedTest {
    private static final String PNG_DATA_URL = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+XwU2WQAAAABJRU5ErkJggg==";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearAssets();
    }

    @After
    public void tearDown() {
        clearAssets();
    }

    @Test
    public void transparentPngIsStoredWithV3Metadata() throws Exception {
        AssetStore.saveAsset(
                context,
                "background",
                "{\"id\":\"transparent-test\",\"label\":\"透明测试\"}",
                PNG_DATA_URL
        );
        JSONObject manifest = new JSONObject(AssetStore.listAssets(context));
        JSONObject item = manifest.getJSONArray("backgrounds").getJSONObject(0);
        assertEquals(3, manifest.getInt("version"));
        assertEquals("image/png", item.getString("mime"));
        assertTrue(item.getString("path").endsWith(".png"));
        assertTrue(item.getString("src").startsWith(LocalWebContent.CUSTOM_BASE_URL));
        assertEquals(64, item.getString("sha256").length());
    }

    @Test
    public void oldPngStoredAsJpgMigratesWithoutDataLoss() throws Exception {
        File root = new File(context.getFilesDir(), "assets");
        File backgrounds = new File(root, "backgrounds");
        assertTrue(backgrounds.mkdirs());
        byte[] png = Base64.decode(PNG_DATA_URL.substring(PNG_DATA_URL.indexOf(',') + 1), Base64.DEFAULT);
        File legacy = new File(backgrounds, "legacy.jpg");
        try (FileOutputStream output = new FileOutputStream(legacy)) {
            output.write(png);
        }
        JSONObject old = new JSONObject()
                .put("version", 1)
                .put("backgrounds", new JSONArray().put(new JSONObject()
                        .put("id", "legacy")
                        .put("label", "旧素材")
                        .put("custom", true)
                        .put("path", legacy.getAbsolutePath())
                        .put("src", "file://" + legacy.getAbsolutePath())))
                .put("characters", new JSONArray())
                .put("emotions", new JSONArray());
        write(new File(root, "manifest.json"), old.toString().getBytes(StandardCharsets.UTF_8));

        JSONObject migrated = new JSONObject(AssetStore.listAssets(context));
        JSONObject item = migrated.getJSONArray("backgrounds").getJSONObject(0);
        assertEquals(3, migrated.getInt("version"));
        assertTrue(item.getString("path").endsWith("legacy.png"));
        assertFalse(legacy.exists());
        assertTrue(new File(item.getString("path")).isFile());
    }

    @Test
    public void deletingCharacterAlsoDeletesItsEmotions() throws Exception {
        AssetStore.saveCharacterBundle(
                context,
                "{\"id\":\"char-test\",\"label\":\"角色\"}",
                "{\"id\":\"emotion-test\",\"ownerId\":\"char-test\",\"label\":\"表情\"}",
                PNG_DATA_URL
        );
        assertTrue(AssetStore.deleteAsset(context, "character", "char-test"));
        JSONObject manifest = new JSONObject(AssetStore.listAssets(context));
        assertEquals(0, manifest.getJSONArray("characters").length());
        assertEquals(0, manifest.getJSONArray("emotions").length());
    }

    @Test
    public void backupRoundTripRestoresAssetsAndSettings() throws Exception {
        AssetStore.saveAsset(
                context,
                "background",
                "{\"id\":\"roundtrip\",\"label\":\"往返测试\"}",
                PNG_DATA_URL
        );
        ByteArrayOutputStream backup = new ByteArrayOutputStream();
        JSONObject exported = new JSONObject(AssetStore.exportBackup(
                context,
                backup,
                "{\"uiTheme\":\"light\",\"lineText\":\"测试台词\"}"
        ));
        assertEquals("success", exported.getString("status"));
        AssetStore.deleteAsset(context, "background", "roundtrip");

        JSONObject imported = new JSONObject(AssetStore.importBackup(
                context,
                new ByteArrayInputStream(backup.toByteArray())
        ));
        assertEquals("success", imported.getString("status"));
        assertEquals("light", imported.getJSONObject("settings").getString("uiTheme"));
        JSONObject manifest = new JSONObject(AssetStore.listAssets(context));
        assertEquals(1, manifest.getJSONArray("backgrounds").length());
        assertTrue(new File(manifest.getJSONArray("backgrounds").getJSONObject(0).getString("path")).isFile());
    }

    @Test
    public void zipSlipIsRejectedWithoutChangingExistingAssets() throws Exception {
        AssetStore.saveAsset(
                context,
                "background",
                "{\"id\":\"keep-me\",\"label\":\"保留\"}",
                PNG_DATA_URL
        );
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../escape.png"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        try {
            AssetStore.importBackup(context, new ByteArrayInputStream(bytes.toByteArray()));
            fail("Zip Slip backup should be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
        JSONObject manifest = new JSONObject(AssetStore.listAssets(context));
        assertEquals("keep-me", manifest.getJSONArray("backgrounds").getJSONObject(0).getString("id"));
    }

    @Test
    public void corruptManifestIsBackedUpAndReplaced() throws Exception {
        File root = new File(context.getFilesDir(), "assets");
        assertTrue(root.mkdirs());
        write(new File(root, "manifest.json"), "{broken".getBytes(StandardCharsets.UTF_8));
        JSONObject manifest = new JSONObject(AssetStore.listAssets(context));
        assertEquals(3, manifest.getInt("version"));
        File[] backups = root.listFiles((dir, name) -> name.startsWith("manifest.corrupt-"));
        assertTrue(backups != null && backups.length == 1);
    }

    private void write(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }

    private void clearAssets() {
        deleteRecursively(new File(context.getFilesDir(), "assets"));
        File[] siblings = context.getFilesDir().listFiles((dir, name) ->
                name.startsWith("assets-import-") || name.startsWith("assets-merge-") || name.startsWith("assets-rollback-"));
        if (siblings != null) for (File sibling : siblings) deleteRecursively(sibling);
    }

    private void deleteRecursively(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        assertTrue(file.delete());
    }
}
