package com.mahou.textbox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public class AssetStoreSafetyInstrumentedTest {
    private static final int MANIFEST_LIMIT = 5 * 1024 * 1024;
    private static final String PNG_URL = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+XwU2WQAAAABJRU5ErkJggg==";
    private Context context;
    private File files;
    private final List<Intent> notifications = new ArrayList<>();

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        files = new File(app.getCacheDir(), "asset-safety-" + System.nanoTime());
        assertTrue(files.mkdirs());
        notifications.clear();
        context = new ContextWrapper(app) {
            @Override
            public File getFilesDir() {
                return files;
            }

            @Override
            public void sendBroadcast(Intent intent) {
                notifications.add(intent);
            }
        };
    }

    @After
    public void tearDown() {
        delete(files);
    }

    @Test
    public void malformedPresentAssetArraysDoNotChangeCatalog() throws Exception {
        seedBackground();
        byte[] before = catalogBytes();
        for (String key : new String[]{"backgrounds", "characters", "emotions"}) {
            for (Object bad : new Object[]{new JSONObject(), JSONObject.NULL, "not-an-array"}) {
                JSONObject backup = emptyBackup().put(key, bad);
                reject(() -> restore(backup));
                assertArrayEquals(before, catalogBytes());
            }
        }
    }

    @Test
    public void malformedNameBlocksAndFitCannotOverwriteCharacter() throws Exception {
        seedCharacter();
        byte[] before = catalogBytes();
        Object[] badBlocks = {new JSONObject(), JSONObject.NULL, new JSONArray().put("not-a-block"),
                new JSONArray().put(new JSONObject().put("text", repeat(81))),
                new JSONArray().put(new JSONObject().put("size", 401)),
                new JSONArray().put(new JSONObject().put("size", 19)),
                new JSONArray().put(new JSONObject().put("x", 2561)),
                new JSONArray().put(new JSONObject().put("color", "red")),
                new JSONArray().put(new JSONObject().put("id", "same"))
                        .put(new JSONObject().put("id", "same"))};
        for (Object bad : badBlocks) {
            JSONObject backup = backupWith("character", "hero");
            first(backup, "characters").put("nameBlocks", bad);
            reject(() -> restore(backup));
            assertArrayEquals(before, catalogBytes());
        }
        Object[] badFit = {JSONObject.NULL, new JSONArray(), new JSONObject().put("x", "Infinity"),
                new JSONObject().put("y", -1), new JSONObject().put("w", 0), new JSONObject().put("h", 835)};
        for (Object bad : badFit) {
            JSONObject backup = backupWith("character", "hero");
            first(backup, "characters").put("fit", bad);
            reject(() -> restore(backup));
            assertArrayEquals(before, catalogBytes());
        }
        assertEquals(1, catalog().getJSONArray("emotions").length());
    }

    @Test
    public void tooManyNameBlocksAndInvalidLabelsAreRejected() throws Exception {
        seedCharacter();
        byte[] before = catalogBytes();
        JSONArray blocks = new JSONArray();
        for (int i = 0; i < 33; i++) blocks.put(new JSONObject().put("id", "block-" + i));
        reject(() -> AssetStore.updateAsset(context, "hero", new JSONObject().put("nameBlocks", blocks).toString()));
        for (Object bad : new Object[]{repeat(81), 7, JSONObject.NULL}) {
            JSONObject backup = backupWith("character", "hero");
            first(backup, "characters").put("label", bad);
            reject(() -> restore(backup));
        }
        assertArrayEquals(before, catalogBytes());
    }

    @Test
    public void nonFiniteNumericMetadataIsRejectedWithoutMutation() throws Exception {
        seedCharacter();
        byte[] before = catalogBytes();
        for (String patch : new String[]{"{\"fit\":{\"x\":1e309}}", "{\"order\":1e309}",
                "{\"nameBlocks\":[{\"size\":1e309}]}"}) {
            reject(() -> AssetStore.updateAsset(context, "hero", patch));
            assertArrayEquals(before, catalogBytes());
        }
    }

    @Test
    public void duplicateIdsAndOrphanEmotionsAreRejectedAtomically() throws Exception {
        seedCharacter();
        byte[] before = catalogBytes();
        JSONObject duplicate = backupWith("character", "duplicate");
        duplicate.getJSONArray("characters").put(new JSONObject(first(duplicate, "characters").toString()));
        reject(() -> restore(duplicate));
        JSONObject crossKind = backupWith("character", "duplicate");
        crossKind.getJSONArray("backgrounds").put(first(backupWith("background", "duplicate"), "backgrounds"));
        reject(() -> restore(crossKind));
        JSONObject orphan = backupWith("emotion", "new-emotion");
        first(orphan, "emotions").put("ownerId", "missing-character");
        reject(() -> restore(orphan));
        reject(() -> AssetStore.updateAsset(context, "hero-emotion", "{\"ownerId\":\"missing-character\"}"));
        reject(() -> AssetStore.updateAsset(context, "hero", "{\"kind\":\"background\"}"));
        assertArrayEquals(before, catalogBytes());
    }

    @Test
    public void oversizeMetadataIsRejectedBeforeReplacingImageOrCatalog() throws Exception {
        seedBackground();
        byte[] before = catalogBytes();
        JSONObject item = catalog().getJSONArray("backgrounds").getJSONObject(0);
        byte[] image = read(new File(item.getString("path")));
        String large = new JSONObject().put("id", "keep").put("extension", repeat(MANIFEST_LIMIT)).toString();
        reject(() -> AssetStore.saveAsset(context, "background", large, PNG_URL));
        reject(() -> AssetStore.updateAsset(context, "keep", large));
        assertArrayEquals(before, catalogBytes());
        assertArrayEquals(image, read(new File(item.getString("path"))));
    }

    @Test
    public void aggregateManifestLimitRejectsSaveAndUpdateWithoutMutation() throws Exception {
        seedBackground();
        AssetStore.updateAsset(context, "keep", new JSONObject().put("extension", repeat(4 * 1024 * 1024)).toString());
        byte[] before = catalogBytes();
        String second = new JSONObject().put("id", "second").put("extension", repeat(2 * 1024 * 1024)).toString();
        reject(() -> AssetStore.saveAsset(context, "background", second, PNG_URL));
        assertArrayEquals(before, catalogBytes());
        assertFalse(new File(files, "assets/backgrounds/second.png").exists());
        seedSmallSecondBackground();
        byte[] twoItems = catalogBytes();
        reject(() -> AssetStore.updateAsset(context, "second", new JSONObject().put("extension", repeat(2 * 1024 * 1024)).toString()));
        assertArrayEquals(twoItems, catalogBytes());
    }

    @Test
    public void normalizedImportCannotExceedManifestLimit() throws Exception {
        seedBackground();
        byte[] before = catalogBytes();
        JSONObject backup = backupWith("background", "new-background");
        int padding = MANIFEST_LIMIT - backup.toString().getBytes(StandardCharsets.UTF_8).length - 128;
        first(backup, "backgrounds").put("extension", repeat(padding));
        assertTrue(backup.toString().getBytes(StandardCharsets.UTF_8).length <= MANIFEST_LIMIT);
        reject(() -> restore(backup));
        assertArrayEquals(before, catalogBytes());
        assertFalse(new File(files, "assets/backgrounds/new-background.png").exists());
    }

    @Test
    public void exportRejectsThousandAssetsBeforeWritingAnyZipBytes() throws Exception {
        seedBackground();
        JSONObject template = catalog().getJSONArray("backgrounds").getJSONObject(0);
        JSONObject manifest = catalog();
        JSONArray items = new JSONArray();
        for (int i = 0; i < 1000; i++) items.put(new JSONObject(template.toString()).put("id", "asset-" + i));
        manifest.put("backgrounds", items);
        write(new File(files, "assets/manifest.json"), manifest.toString().getBytes(StandardCharsets.UTF_8));
        byte[] before = catalogBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        reject(() -> AssetStore.exportBackup(context, output, "{}"));
        assertEquals(0, output.size());
        assertArrayEquals(before, catalogBytes());
        items.remove(999);
        write(new File(files, "assets/manifest.json"), manifest.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream accepted = new ByteArrayOutputStream();
        JSONObject result = new JSONObject(AssetStore.exportBackup(context, accepted, "{}"));
        assertEquals(999, result.getInt("exportedCount"));
        assertTrue(accepted.size() > 0);
    }

    @Test
    public void exportRejectsOversizeImageAndIndexBeforeWriting() throws Exception {
        seedBackground();
        ByteArrayOutputStream indexOutput = new ByteArrayOutputStream();
        reject(() -> AssetStore.exportBackup(context, indexOutput,
                new JSONObject().put("extension", repeat(MANIFEST_LIMIT)).toString()));
        assertEquals(0, indexOutput.size());
        File image = new File(catalog().getJSONArray("backgrounds").getJSONObject(0).getString("path"));
        try (RandomAccessFile file = new RandomAccessFile(image, "rw")) {
            file.setLength(AssetStore.MAX_SINGLE_ASSET_BYTES + 1);
        }
        ByteArrayOutputStream imageOutput = new ByteArrayOutputStream();
        reject(() -> AssetStore.exportBackup(context, imageOutput, "{}"));
        assertEquals(0, imageOutput.size());
    }

    @Test
    public void compressedOutputBudgetCountsBulkWritesExactlyOnce() throws Exception {
        Class<?> type = Class.forName("com.mahou.textbox.AssetStore$LimitedOutputStream");
        Constructor<?> constructor = type.getDeclaredConstructor(OutputStream.class, long.class);
        constructor.setAccessible(true);
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OutputStream limited = (OutputStream) constructor.newInstance(target, 8L);
        limited.write(new byte[8]);
        reject(() -> limited.write(1));
        assertEquals(8, target.size());
    }

    @Test
    public void exportRejectsAggregateBytesEvenWhenEveryImageFits() throws Exception {
        seedBackground();
        JSONObject manifest = catalog();
        JSONObject template = manifest.getJSONArray("backgrounds").getJSONObject(0);
        try (RandomAccessFile image = new RandomAccessFile(template.getString("path"), "rw")) {
            image.setLength(AssetStore.MAX_SINGLE_ASSET_BYTES);
        }
        JSONArray items = new JSONArray();
        for (int i = 0; i < 11; i++) items.put(new JSONObject(template.toString()).put("id", "asset-" + i));
        manifest.put("backgrounds", items);
        write(new File(files, "assets/manifest.json"), manifest.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        reject(() -> AssetStore.exportBackup(context, output, "{}"));
        assertEquals(0, output.size());
    }

    @Test
    public void committedMutationsNotifyOnceAndRejectedOperationsStayQuiet() throws Exception {
        seedBackground();
        notifications.clear();
        seedSmallSecondBackground();
        assertEquals(1, notifications.size());
        AssetStore.updateAsset(context, "second", "{\"label\":\"renamed\"}");
        assertEquals(2, notifications.size());
        seedCharacter();
        assertEquals(3, notifications.size());
        assertTrue(AssetStore.deleteAsset(context, "background", "second"));
        assertEquals(4, notifications.size());
        restore(backupWith("background", "keep"));
        assertEquals(5, notifications.size());
        assertFalse(AssetStore.deleteAsset(context, "background", "missing"));
        reject(() -> AssetStore.updateAsset(context, "hero-emotion", "{\"ownerId\":\"missing\"}"));
        reject(() -> AssetStore.saveCharacterBundle(context, "{\"id\":\"hero\"}",
                "{\"id\":\"hero-emotion\",\"ownerId\":\"wrong\"}", PNG_URL));
        assertEquals(5, notifications.size());
        for (Intent intent : notifications) {
            assertEquals(AssetStore.ACTION_ASSETS_CHANGED, intent.getAction());
            assertEquals(context.getPackageName(), intent.getPackage());
        }
    }

    @Test
    public void sameIdRestorePreservesEmotionsAndCascadeDeletion() throws Exception {
        seedCharacter();
        JSONObject backup = backupWith("character", "hero");
        first(backup, "characters").put("label", "replacement");
        JSONObject result = restore(backup);
        assertEquals(1, result.getInt("overwrittenCount"));
        assertEquals("replacement", catalog().getJSONArray("characters").getJSONObject(0).getString("label"));
        assertEquals(1, catalog().getJSONArray("emotions").length());
        assertTrue(AssetStore.deleteAsset(context, "character", "hero"));
        assertEquals(0, catalog().getJSONArray("characters").length());
        assertEquals(0, catalog().getJSONArray("emotions").length());
        assertFalse(new File(files, "assets/emotions/hero-emotion.png").exists());
    }

    @Test
    public void rejectedBundleDoesNotDeleteExistingCharacterAndEmotions() throws Exception {
        seedCharacter();
        byte[] before = catalogBytes();
        reject(() -> AssetStore.saveCharacterBundle(context, "{\"id\":\"hero\",\"label\":\"replacement\"}",
                "{\"id\":\"hero-emotion\",\"ownerId\":\"someone-else\"}", PNG_URL));
        assertArrayEquals(before, catalogBytes());
        assertEquals(1, catalog().getJSONArray("emotions").length());
    }

    @Test
    public void failedRollbackRetainsOriginalTreeAndNextReadRecoversIt() throws Exception {
        seedBackground();
        byte[] before = catalogBytes();
        File current = new File(files, "assets");
        File merged = new NeverRenameFile(files, "assets-merge-failure");
        File rollback = new NeverRenameFile(files, "assets-rollback-failure");
        assertTrue(merged.mkdir());
        Method commit = AssetStore.class.getDeclaredMethod("commitImportedTree", File.class, File.class, File.class);
        commit.setAccessible(true);
        try {
            commit.invoke(null, current, merged, rollback);
            fail("Expected failed installation and rollback");
        } catch (InvocationTargetException error) {
            assertTrue(error.getCause() instanceof IOException);
        }
        assertFalse(current.exists());
        assertEquals(1, notifications.size());
        assertArrayEquals(before, read(new File(rollback, "manifest.json")));
        assertEquals("keep", catalog().getJSONArray("backgrounds").getJSONObject(0).getString("id"));
        assertFalse(rollback.exists());
        assertArrayEquals(before, catalogBytes());
    }

    @Test
    public void legacyV1AndV2MissingFieldsMigrateAndAcceptBoundedLegacySize() throws Exception {
        for (int version : new int[]{1, 2}) {
            delete(new File(files, "assets"));
            File image = new File(files, "assets/characters/legacy.jpg");
            write(image, png());
            JSONObject manifest = new JSONObject().put("version", version)
                    .put("characters", new JSONArray().put(new JSONObject().put("id", "legacy")
                            .put("custom", true).put("path", image.getAbsolutePath()).put("src", "file://" + image)));
            write(new File(files, "assets/manifest.json"), manifest.toString().getBytes(StandardCharsets.UTF_8));
            JSONObject migrated = catalog();
            assertEquals(3, migrated.getInt("version"));
            assertTrue(migrated.getJSONArray("characters").getJSONObject(0).getString("path").endsWith("legacy.png"));
            JSONObject updated = new JSONObject(AssetStore.updateAsset(context, "legacy", "{}"));
            assertEquals(720, updated.getJSONObject("fit").getInt("w"));
            assertEquals(1, updated.getJSONArray("nameBlocks").length());
            JSONArray blocks = new JSONArray().put(new JSONObject().put("text", repeat(80)).put("size", 400));
            AssetStore.updateAsset(context, "legacy", new JSONObject().put("nameBlocks", blocks).toString());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            AssetStore.exportBackup(context, output, "{\"lineText\":\"roundtrip\"}");
            delete(new File(files, "assets"));
            JSONObject result = new JSONObject(AssetStore.importBackup(context, new ByteArrayInputStream(output.toByteArray())));
            assertEquals("roundtrip", result.getJSONObject("settings").getString("lineText"));
            assertEquals(400, catalog().getJSONArray("characters").getJSONObject(0)
                    .getJSONArray("nameBlocks").getJSONObject(0).getInt("size"));
        }
    }

    private void seedBackground() throws Exception {
        AssetStore.saveAsset(context, "background", "{\"id\":\"keep\",\"label\":\"original\"}", PNG_URL);
    }

    private void seedSmallSecondBackground() throws Exception {
        AssetStore.saveAsset(context, "background", "{\"id\":\"second\"}", PNG_URL);
    }

    private void seedCharacter() throws Exception {
        AssetStore.saveCharacterBundle(context, "{\"id\":\"hero\",\"label\":\"original\"}",
                "{\"id\":\"hero-emotion\",\"ownerId\":\"hero\"}", PNG_URL);
    }

    private JSONObject catalog() throws Exception {
        return new JSONObject(AssetStore.listAssets(context));
    }

    private byte[] catalogBytes() throws IOException {
        return read(new File(files, "assets/manifest.json"));
    }

    private JSONObject emptyBackup() throws Exception {
        return new JSONObject().put("formatVersion", 1).put("settings", new JSONObject())
                .put("backgrounds", new JSONArray()).put("characters", new JSONArray()).put("emotions", new JSONArray());
    }

    private JSONObject backupWith(String kind, String id) throws Exception {
        JSONObject backup = emptyBackup();
        JSONObject item = new JSONObject().put("id", id).put("label", "imported")
                .put("file", "assets/" + kind + "/" + id + ".png").put("sha256", hash(png()));
        backup.getJSONArray(kind + "s").put(item);
        return backup;
    }

    private JSONObject first(JSONObject backup, String array) throws Exception {
        return backup.getJSONArray(array).getJSONObject(0);
    }

    private JSONObject restore(JSONObject backup) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Set<String> entries = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String key : new String[]{"backgrounds", "characters", "emotions"}) {
                JSONArray array = backup.optJSONArray(key);
                if (array == null) continue;
                for (int i = 0; i < array.length(); i++) {
                    String path = array.getJSONObject(i).getString("file");
                    if (!entries.add(path)) continue;
                    zip.putNextEntry(new ZipEntry(path));
                    zip.write(png());
                    zip.closeEntry();
                }
            }
            zip.putNextEntry(new ZipEntry("backup.json"));
            zip.write(backup.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new JSONObject(AssetStore.importBackup(context, new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static void reject(ThrowingAction action) throws Exception {
        try {
            action.run();
            fail("Expected rejected input");
        } catch (IOException | JSONException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    private static byte[] png() {
        return Base64.decode(PNG_URL.substring(PNG_URL.indexOf(',') + 1), Base64.DEFAULT);
    }

    private static String hash(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            text.append(String.format(java.util.Locale.US, "%02x", value));
        }
        return text.toString();
    }

    private static String repeat(int count) {
        char[] text = new char[count];
        java.util.Arrays.fill(text, 'x');
        return new String(text);
    }

    private static byte[] read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create test directory");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        assertTrue("Cannot remove test file: " + file, file.delete());
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static final class NeverRenameFile extends File {
        NeverRenameFile(File parent, String name) {
            super(parent, name);
        }

        @Override
        public boolean renameTo(File destination) {
            return false;
        }
    }
}
