package com.mahou.textbox;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class AssetStore {
    public static final String ACTION_ASSETS_CHANGED = "com.mahou.textbox.ASSETS_CHANGED";
    public static final long MAX_BACKUP_BYTES = 220L * 1024L * 1024L;
    public static final long MAX_TOTAL_ASSET_BYTES = 200L * 1024L * 1024L;
    public static final long MAX_SINGLE_ASSET_BYTES = 20L * 1024L * 1024L;

    private static final int MANIFEST_VERSION = 3;
    private static final int BACKUP_VERSION = 1;
    private static final int MAX_BACKUP_ENTRIES = 1000;
    private static final long MAX_MANIFEST_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_LABEL_LENGTH = 80;
    private static final int MAX_NAME_BLOCKS = 32;
    private static final int MAX_NAME_TEXT_LENGTH = 80;
    private static final String ROOT_DIR = "assets";
    private static final String MANIFEST_FILE = "manifest.json";

    private AssetStore() {
    }

    public static synchronized String listAssets(Context context) throws IOException, JSONException {
        JSONObject manifest = readManifest(context);
        manifest.put("usedBytes", totalAssetBytes(rootDir(context)));
        manifest.put("maxBytes", MAX_TOTAL_ASSET_BYTES);
        return manifest.toString();
    }

    public static synchronized String getAppInfo(Context context) throws IOException, JSONException {
        return new JSONObject()
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("buildType", BuildConfig.BUILD_TYPE)
                .put("usedBytes", totalAssetBytes(rootDir(context)))
                .put("maxBytes", MAX_TOTAL_ASSET_BYTES)
                .toString();
    }

    public static synchronized String saveAsset(
            Context context,
            String kind,
            String metadataJson,
            String imageDataUrl
    ) throws IOException, JSONException {
        JSONObject metadata = parseMetadata(metadataJson);
        normalizeMetadata(metadata, kind);
        byte[] imageBytes = decodeDataUrl(imageDataUrl);
        if (imageBytes.length == 0) {
            throw new IOException("图片内容为空");
        }
        if (imageBytes.length > MAX_SINGLE_ASSET_BYTES) {
            throw new IOException("单张素材不能超过 20 MB");
        }

        ImageFormat format = requireFormat(imageBytes);
        JSONObject manifest = readManifestSnapshot(context);
        File root = new File(context.getFilesDir(), ROOT_DIR);
        List<AssetChange> changes = new ArrayList<>();
        changes.add(planAsset(manifest, root, kind, metadata, format,
                imageBytes.length, sha256(imageBytes), null, imageBytes));
        commitChanges(context, root, manifest, changes, new ArrayList<>());
        return metadata.toString();
    }

    public static synchronized String saveCharacterBundle(
            Context context,
            String characterJson,
            String emotionJson,
            String imageDataUrl
    ) throws IOException, JSONException {
        JSONObject character = parseMetadata(characterJson);
        JSONObject emotion = parseMetadata(emotionJson);
        normalizeMetadata(character, "character");
        normalizeMetadata(emotion, "emotion");
        if (!character.getString("id").equals(emotion.getString("ownerId"))) {
            throw new IOException("表情必须属于所保存的角色");
        }
        byte[] imageBytes = decodeDataUrl(imageDataUrl);
        if (imageBytes.length == 0 || imageBytes.length > MAX_SINGLE_ASSET_BYTES) {
            throw new IOException("图片内容为空或超过 20 MB");
        }
        ImageFormat format = requireFormat(imageBytes);
        String hash = sha256(imageBytes);
        JSONObject manifest = readManifestSnapshot(context);
        File root = new File(context.getFilesDir(), ROOT_DIR);
        List<AssetChange> changes = new ArrayList<>();
        changes.add(planAsset(manifest, root, "character", character, format,
                imageBytes.length, hash, null, imageBytes));
        changes.add(planAsset(manifest, root, "emotion", emotion, format,
                imageBytes.length, hash, null, imageBytes));
        commitChanges(context, root, manifest, changes, new ArrayList<>());
        return new JSONObject().put("character", character).put("emotion", emotion).toString();
    }

    public static synchronized boolean deleteAsset(Context context, String kind, String id)
            throws IOException, JSONException {
        JSONObject manifest = readManifestSnapshot(context);
        JSONArray array = manifest.getJSONArray(arrayName(kind));
        int index = findIndex(array, id);
        if (index < 0) {
            return false;
        }
        JSONObject item = array.optJSONObject(index);
        if (item == null || !item.optBoolean("custom", false)) {
            return false;
        }

        File root = new File(context.getFilesDir(), ROOT_DIR);
        List<File> removed = new ArrayList<>();
        removed.add(fileFromItem(item));
        array.remove(index);
        if ("character".equals(kind)) {
            JSONArray emotions = manifest.getJSONArray("emotions");
            for (int i = emotions.length() - 1; i >= 0; i--) {
                JSONObject emotion = emotions.optJSONObject(i);
                if (emotion != null && id.equals(emotion.optString("ownerId"))) {
                    removed.add(fileFromItem(emotion));
                    emotions.remove(i);
                }
            }
        }
        commitChanges(context, root, manifest, new ArrayList<>(), removed);
        return true;
    }

    public static synchronized String updateAsset(Context context, String id, String metadataJson)
            throws IOException, JSONException {
        JSONObject patch = parseMetadata(metadataJson);
        JSONObject manifest = readManifestSnapshot(context);
        for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
            JSONArray array = manifest.getJSONArray(name);
            int index = findIndex(array, id);
            if (index < 0) {
                continue;
            }
            JSONObject current = array.getJSONObject(index);
            merge(current, patch);
            current.put("id", id);
            array.put(index, current);
            validateCatalog(manifest);
            manifestBytes(manifest);
            writeManifest(context, manifest);
            notifyAssetsChanged(context);
            return current.toString();
        }
        throw new IOException("未找到要更新的素材");
    }

    public static synchronized String exportBackup(Context context, OutputStream output, String settingsJson)
            throws IOException, JSONException {
        JSONObject manifest = readManifestSnapshot(context);
        validateCatalog(manifest);
        manifestBytes(manifest);
        JSONObject backup = new JSONObject()
                .put("formatVersion", BACKUP_VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdAt", isoTimestamp())
                .put("settings", parseObjectOrEmpty(settingsJson));

        File root = new File(context.getFilesDir(), ROOT_DIR);
        JSONObject restored = emptyManifest();
        List<File> files = new ArrayList<>();
        List<String> entries = new ArrayList<>();
        long totalBytes = 0;
        for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
            JSONArray source = manifest.getJSONArray(name);
            JSONArray target = new JSONArray();
            String kind = singularName(name);
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null || !item.optBoolean("custom", false)) {
                    continue;
                }
                if (files.size() + 2 > MAX_BACKUP_ENTRIES) {
                    throw new IOException("备份条目不能超过 1000 个（含索引）");
                }
                File file = fileFromItem(item);
                if (file == null || !file.isFile() || !isInsideRoot(root, file)) {
                    throw new IOException("素材文件缺失：" + item.optString("label", item.optString("id")));
                }
                totalBytes += file.length();
                if (file.length() > MAX_SINGLE_ASSET_BYTES || totalBytes > MAX_TOTAL_ASSET_BYTES) {
                    throw new IOException("备份素材超过单张 20 MB 或总量 200 MB 限制");
                }
                ImageFormat format = requireFormat(readHeader(file));
                String entryName = "assets/" + kind + "/" + safeFileName(item.optString("id")) + format.extension;
                if (!isSafeEntry(entryName)) throw new IOException("素材无法使用安全备份路径");
                JSONObject copy = new JSONObject(item.toString());
                copy.remove("path");
                copy.remove("src");
                copy.put("file", entryName);
                copy.put("mime", format.mime);
                copy.put("size", file.length());
                copy.put("sha256", sha256(file));
                target.put(copy);
                files.add(file);
                entries.add(entryName);
                JSONObject normalized = new JSONObject(copy.toString());
                normalized.remove("file");
                populateFileMetadata(normalized, kind,
                        new File(new File(root, name), safeFileName(item.getString("id")) + format.extension),
                        format, file.length(), copy.getString("sha256"));
                restored.getJSONArray(name).put(normalized);
            }
            backup.put(name, target);
        }
        validateCatalog(restored);
        manifestBytes(restored);
        byte[] index = manifestBytes(backup);
        try (ZipOutputStream zip = new ZipOutputStream(new LimitedOutputStream(output, MAX_BACKUP_BYTES))) {
            for (int i = 0; i < files.size(); i++) {
                addFileToZip(zip, entries.get(i), files.get(i));
            }
            zip.putNextEntry(new ZipEntry("backup.json"));
            zip.write(index);
            zip.closeEntry();
            zip.finish();
            return new JSONObject()
                    .put("status", "success")
                    .put("exportedCount", files.size())
                    .put("message", "已备份 " + files.size() + " 个自定义素材")
                    .toString();
        }
    }

    public static synchronized String importBackup(Context context, InputStream input)
            throws IOException, JSONException {
        File filesDir = context.getFilesDir();
        File extracted = new File(filesDir, "assets-import-" + System.currentTimeMillis());
        ensureDirectory(extracted);
        try {
            extractBackup(input, extracted);
            File backupFile = new File(extracted, "backup.json");
            if (!backupFile.isFile()) {
                throw new IOException("备份中缺少 backup.json");
            }
            JSONObject backup = new JSONObject(new String(
                    readAllBytes(backupFile, MAX_MANIFEST_BYTES),
                    StandardCharsets.UTF_8
            ));
            if (backup.optInt("formatVersion", -1) != BACKUP_VERSION) {
                throw new IOException("不支持的备份格式版本");
            }

            ensureImportArrays(backup);
            if (backup.has("settings") && backup.optJSONObject("settings") == null) {
                throw new IOException("备份设置必须是对象");
            }
            File currentRoot = new File(filesDir, ROOT_DIR);
            JSONObject mergedManifest = readManifestSnapshot(context);
            int importedCount = 0;
            int overwrittenCount = 0;
            Set<String> incomingIds = new HashSet<>();
            Set<String> incomingFiles = new HashSet<>();
            List<AssetChange> changes = new ArrayList<>();

            for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
                JSONArray incoming = backup.getJSONArray(name);
                JSONArray destination = mergedManifest.getJSONArray(name);
                String kind = singularName(name);
                for (int i = 0; i < incoming.length(); i++) {
                    JSONObject item = incoming.optJSONObject(i);
                    if (item == null) {
                        throw new IOException("备份素材索引无效");
                    }
                    JSONObject normalized = new JSONObject(item.toString());
                    normalizeMetadata(normalized, kind);
                    String id = normalized.getString("id");
                    if (!incomingIds.add(id)) {
                        throw new IOException("备份包含重复素材 ID：" + id);
                    }
                    String entryName = requiredString(item, "file");
                    if (!isSafeEntry(entryName) || !entryName.startsWith("assets/" + kind + "/")
                            || !incomingFiles.add(entryName)) {
                        throw new IOException("备份素材路径无效");
                    }
                    File staged = new File(extracted, entryName).getCanonicalFile();
                    if (!staged.isFile() || !isInsideRoot(extracted, staged)) {
                        throw new IOException("备份素材文件缺失：" + id);
                    }
                    if (staged.length() > MAX_SINGLE_ASSET_BYTES) {
                        throw new IOException("单张素材不能超过 20 MB");
                    }
                    ImageFormat format = requireFormat(readHeader(staged));
                    String actualHash = sha256(staged);
                    if (!actualHash.equalsIgnoreCase(requiredString(item, "sha256"))) {
                        throw new IOException("素材校验失败：" + id);
                    }

                    int existing = findIndex(destination, id);
                    if (existing >= 0) {
                        overwrittenCount++;
                    }
                    importedCount++;
                    normalized.remove("file");
                    changes.add(planAsset(mergedManifest, currentRoot, kind, normalized, format,
                            staged.length(), actualHash, staged, null));
                }
            }

            mergedManifest.put("version", MANIFEST_VERSION);
            commitChanges(context, currentRoot, mergedManifest, changes, new ArrayList<>());

            JSONObject result = new JSONObject()
                    .put("status", "success")
                    .put("importedCount", importedCount)
                    .put("overwrittenCount", overwrittenCount)
                    .put("settings", backup.optJSONObject("settings") == null
                            ? new JSONObject()
                            : backup.optJSONObject("settings"))
                    .put("message", "已恢复 " + importedCount + " 个素材");
            return result.toString();
        } finally {
            deleteRecursively(extracted);
        }
    }

    private static JSONObject readManifest(Context context) throws IOException, JSONException {
        File root = rootDir(context);
        File manifestFile = new File(root, MANIFEST_FILE);
        if (!manifestFile.isFile()) {
            JSONObject empty = emptyManifest();
            writeManifest(context, empty);
            return empty;
        }
        JSONObject manifest;
        if (manifestFile.length() > MAX_MANIFEST_BYTES) {
            throw new IOException("素材索引超过 5 MB，原文件已保留");
        }
        try {
            manifest = new JSONObject(new String(
                    readAllBytes(manifestFile, MAX_MANIFEST_BYTES),
                    StandardCharsets.UTF_8
            ));
            ensureArrays(manifest);
        } catch (Exception error) {
            preserveCorruptManifest(manifestFile);
            manifest = emptyManifest();
            writeManifest(context, manifest);
            return manifest;
        }
        if (migrateManifest(context, manifest)) {
            writeManifest(context, manifest);
        }
        return manifest;
    }

    private static JSONObject readManifestSnapshot(Context context) throws IOException, JSONException {
        File root = new File(context.getFilesDir(), ROOT_DIR);
        if (!root.exists() && findRecoveryTree(context) != null) {
            throw new IOException("素材恢复事务待回滚，请先重新读取素材");
        }
        File file = new File(root, MANIFEST_FILE);
        if (!file.isFile()) return emptyManifest();
        JSONObject manifest = new JSONObject(new String(readAllBytes(file, MAX_MANIFEST_BYTES), StandardCharsets.UTF_8));
        ensureImportArrays(manifest);
        return manifest;
    }

    private static JSONObject parseMetadata(String json) throws IOException, JSONException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) {
            throw new IOException("素材元数据不能超过 5 MB");
        }
        return new JSONObject(json);
    }

    private static byte[] manifestBytes(JSONObject manifest) throws IOException {
        byte[] bytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MANIFEST_BYTES) throw new IOException("素材索引不能超过 5 MB");
        return bytes;
    }

    private static String requiredString(JSONObject object, String key) throws IOException {
        Object value = object.opt(key);
        if (!(value instanceof String)) throw new IOException("素材字段必须是字符串：" + key);
        return (String) value;
    }

    private static String boundedString(JSONObject object, String key, String fallback, int max) throws IOException {
        String value = object.has(key) ? requiredString(object, key) : fallback;
        if (value.codePointCount(0, value.length()) > max) {
            throw new IOException("素材字段过长：" + key);
        }
        return value;
    }

    private static double boundedNumber(JSONObject object, String key, double fallback, double min, double max)
            throws IOException {
        Object value = object.has(key) ? object.opt(key) : fallback;
        if (!(value instanceof Number)) throw new IOException("素材字段必须是数字：" + key);
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number) || number < min || number > max) {
            throw new IOException("素材数字字段越界：" + key);
        }
        return number;
    }

    private static void normalizeMetadata(JSONObject item, String kind) throws IOException, JSONException {
        arrayName(kind);
        String id = requireId(requiredString(item, "id"));
        item.put("id", id);
        if (item.has("kind") && !kind.equals(requiredString(item, "kind"))) {
            throw new IOException("素材类型与所属数组不一致");
        }
        String label = boundedString(item, "label", id.substring(0, Math.min(id.length(), MAX_LABEL_LENGTH)), MAX_LABEL_LENGTH);
        item.put("label", label);
        item.put("order", boundedNumber(item, "order", 0, 0, Long.MAX_VALUE));
        String owner = boundedString(item, "ownerId", "", 128);
        if ("emotion".equals(kind)) {
            owner = requireId(owner);
        } else if (!owner.isEmpty()) {
            throw new IOException("只有表情素材可以设置所属角色");
        }
        item.put("ownerId", owner);
        if (!"character".equals(kind)) {
            if (item.has("fit") || item.has("nameBlocks")) {
                throw new IOException("只有角色素材可以设置定位和姓名字块");
            }
            return;
        }
        JSONObject fit = item.has("fit") ? item.optJSONObject("fit") : new JSONObject();
        if (fit == null) throw new IOException("角色定位必须是对象");
        fit.put("x", boundedNumber(fit, "x", 0, 0, 2560));
        fit.put("y", boundedNumber(fit, "y", 134, 0, 834));
        fit.put("w", boundedNumber(fit, "w", 720, 1, 2560));
        fit.put("h", boundedNumber(fit, "h", 700, 1, 834));
        item.put("fit", fit);
        JSONArray blocks = item.has("nameBlocks") ? item.optJSONArray("nameBlocks")
                : new JSONArray().put(new JSONObject().put("text", label));
        if (blocks == null || blocks.length() > MAX_NAME_BLOCKS) {
            throw new IOException("姓名字块必须是数组且不能超过 32 个");
        }
        Set<String> blockIds = new HashSet<>();
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) throw new IOException("姓名字块必须是对象");
            String blockId = requireId(boundedString(block, "id", "name-" + i, 128));
            if (!blockIds.add(blockId)) throw new IOException("姓名字块 ID 重复");
            block.put("id", blockId);
            block.put("text", boundedString(block, "text", "", MAX_NAME_TEXT_LENGTH));
            block.put("x", boundedNumber(block, "x", 759, 0, 2560));
            block.put("y", boundedNumber(block, "y", 73, 0, 834));
            block.put("size", boundedNumber(block, "size", 150, 20, 400));
            String color = boundedString(block, "color", "#ffffff", 7);
            if (!color.matches("#[A-Fa-f0-9]{6}")) throw new IOException("姓名字块颜色无效");
            block.put("color", color);
        }
        item.put("nameBlocks", blocks);
    }

    private static void ensureImportArrays(JSONObject manifest) throws IOException, JSONException {
        for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
            if (!manifest.has(name)) manifest.put(name, new JSONArray());
            else if (manifest.optJSONArray(name) == null) throw new IOException("素材列表必须是数组：" + name);
        }
    }

    private static void validateCatalog(JSONObject manifest) throws IOException, JSONException {
        ensureImportArrays(manifest);
        Set<String> ids = new HashSet<>();
        Set<String> owners = new HashSet<>();
        for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
            JSONArray array = manifest.getJSONArray(name);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) throw new IOException("素材索引必须是对象");
                normalizeMetadata(item, singularName(name));
                String id = item.getString("id");
                if (!ids.add(id)) throw new IOException("素材 ID 重复：" + id);
                if ("characters".equals(name)) owners.add(id);
            }
        }
        JSONArray emotions = manifest.getJSONArray("emotions");
        for (int i = 0; i < emotions.length(); i++) {
            if (!owners.contains(emotions.getJSONObject(i).getString("ownerId"))) {
                throw new IOException("表情所属角色不存在");
            }
        }
    }

    private static AssetChange planAsset(JSONObject manifest, File root, String kind, JSONObject metadata,
            ImageFormat format, long size, String hash, File source, byte[] bytes) throws IOException, JSONException {
        JSONArray array = manifest.getJSONArray(arrayName(kind));
        String id = metadata.getString("id");
        int existing = findIndex(array, id);
        File previous = existing < 0 ? null : fileFromItem(array.optJSONObject(existing));
        File output = new File(new File(root, directoryName(kind)), safeFileName(id) + format.extension);
        populateFileMetadata(metadata, kind, output, format, size, hash);
        if (existing < 0) array.put(metadata);
        else array.put(existing, metadata);
        return new AssetChange(output, previous, source, bytes, size);
    }

    private static File stagedFile(File root, File stagedRoot, File file) throws IOException {
        if (!isInsideRoot(root, file)) throw new IOException("素材文件路径越界");
        return new File(stagedRoot, file.getCanonicalPath().substring(root.getCanonicalPath().length() + 1));
    }

    private static void commitChanges(Context context, File root, JSONObject manifest,
            List<AssetChange> changes, List<File> removed) throws IOException, JSONException {
        validateCatalog(manifest);
        manifest.put("version", MANIFEST_VERSION);
        manifestBytes(manifest);
        long projected = totalAssetBytes(root);
        for (AssetChange change : changes) {
            if (change.previous != null) {
                stagedFile(root, root, change.previous);
                projected -= change.previous.length();
            }
            projected += change.size;
        }
        for (File file : removed) {
            if (file != null) {
                stagedFile(root, root, file);
                projected -= file.length();
            }
        }
        if (projected > MAX_TOTAL_ASSET_BYTES) throw new IOException("素材总量不能超过 200 MB");
        File staged = new File(context.getFilesDir(), "assets-merge-" + System.nanoTime());
        File rollback = new File(context.getFilesDir(), "assets-rollback-" + System.nanoTime());
        try {
            copyAssetTree(root, staged);
            for (AssetChange change : changes) {
                File target = stagedFile(root, staged, change.output);
                ensureDirectory(target.getParentFile());
                if (change.source == null) writeBytesAndSync(target, change.bytes);
                else copyFile(change.source, target);
                if (change.previous != null && !sameFile(change.previous, change.output)) {
                    deleteQuietly(stagedFile(root, staged, change.previous));
                }
            }
            for (File file : removed) {
                if (file != null) deleteQuietly(stagedFile(root, staged, file));
            }
            if (totalAssetBytes(staged) > MAX_TOTAL_ASSET_BYTES) throw new IOException("素材总量不能超过 200 MB");
            writeManifestFile(staged, manifest);
            commitImportedTree(root, staged, rollback);
            notifyAssetsChanged(context);
        } finally {
            deleteRecursively(staged);
            // commitImportedTree alone owns rollback cleanup; it may contain the only original copy.
        }
    }

    private static void notifyAssetsChanged(Context context) {
        try {
            context.sendBroadcast(new Intent(ACTION_ASSETS_CHANGED).setPackage(context.getPackageName()));
        } catch (RuntimeException error) {
            // Observer failures must not turn an already committed transaction into a reported failure.
            Log.w("MahouAssets", "Asset change notification failed", error);
        }
    }

    private static boolean migrateManifest(Context context, JSONObject manifest)
            throws IOException, JSONException {
        boolean changed = manifest.optInt("version", 1) != MANIFEST_VERSION;
        File root = rootDir(context);
        List<File[]> moves = new ArrayList<>();
        for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
            JSONArray array = manifest.getJSONArray(name);
            String kind = singularName(name);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                File source = fileFromItem(item);
                if (item == null || source == null || !source.isFile() || !isInsideRoot(root, source)) {
                    continue;
                }
                ImageFormat format = detectFormat(readHeader(source));
                if (format == ImageFormat.UNKNOWN) {
                    continue;
                }
                String expectedName = safeFileName(requireId(item.optString("id"))) + format.extension;
                File target = new File(source.getParentFile(), expectedName);
                if (!source.getName().equals(expectedName)) {
                    if (target.exists() && !sameFile(source, target)) {
                        target = uniqueMigrationTarget(source.getParentFile(), safeFileName(item.optString("id")), format.extension);
                    }
                    moves.add(new File[]{source, target});
                    changed = true;
                }
                String hash = sha256(source);
                if (!format.mime.equals(item.optString("mime"))
                        || item.optLong("size", -1) != source.length()
                        || !hash.equalsIgnoreCase(item.optString("sha256", ""))
                        || !srcFor(kind, target).equals(item.optString("src"))
                        || !target.getAbsolutePath().equals(item.optString("path"))) {
                    populateFileMetadata(item, kind, target, format, source.length(), hash);
                    changed = true;
                }
            }
        }
        manifest.put("version", MANIFEST_VERSION);
        manifestBytes(manifest);
        for (File[] move : moves) {
            if (!move[0].renameTo(move[1])) {
                copyFile(move[0], move[1]);
                if (!move[0].delete()) throw new IOException("无法迁移素材文件");
            }
        }
        return changed;
    }

    private static void extractBackup(InputStream rawInput, File outputRoot) throws IOException {
        CountingInputStream counting = new CountingInputStream(rawInput, MAX_BACKUP_BYTES);
        Set<String> names = new HashSet<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(counting)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                entries++;
                if (entries > MAX_BACKUP_ENTRIES) {
                    throw new IOException("备份条目不能超过 1000 个");
                }
                String name = entry.getName();
                if (!isSafeEntry(name) || !names.add(name)) {
                    throw new IOException("备份中包含无效或重复路径");
                }
                long entryLimit = "backup.json".equals(name) ? MAX_MANIFEST_BYTES : MAX_SINGLE_ASSET_BYTES;
                File output = new File(outputRoot, name).getCanonicalFile();
                if (!isInsideRoot(outputRoot, output)) {
                    throw new IOException("备份路径越界");
                }
                ensureDirectory(output.getParentFile());
                long written = copyLimited(zip, output, entryLimit);
                total += written;
                if (total > MAX_TOTAL_ASSET_BYTES + MAX_MANIFEST_BYTES) {
                    throw new IOException("备份解压后不能超过 200 MB");
                }
                zip.closeEntry();
            }
        }
    }

    private static void commitImportedTree(File current, File merged, File rollback) throws IOException {
        if (rollback.exists()) throw new IOException("素材回滚目录已存在，原目录已保留");
        if (!current.exists()) {
            if (!merged.renameTo(current)) throw new IOException("无法安装素材恢复事务");
            return;
        }
        if (!current.renameTo(rollback)) {
            throw new IOException("无法准备素材恢复事务");
        }
        if (!merged.renameTo(current)) {
            if (!rollback.renameTo(current)) {
                throw new IOException("素材恢复失败，且无法自动回滚");
            }
            throw new IOException("素材恢复失败，原数据已回滚");
        }
        deleteRecursively(rollback);
    }

    private static void copyAssetTree(File sourceRoot, File targetRoot) throws IOException {
        ensureDirectory(targetRoot);
        for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
            File source = new File(sourceRoot, name);
            File target = new File(targetRoot, name);
            if (source.isDirectory()) {
                copyDirectory(source, target);
            } else {
                ensureDirectory(target);
            }
        }
    }

    private static void copyDirectory(File source, File target) throws IOException {
        ensureDirectory(target);
        File[] children = source.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File destination = new File(target, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, destination);
            } else if (!child.getName().endsWith(".tmp")) {
                copyFile(child, destination);
            }
        }
    }

    private static void writeManifest(Context context, JSONObject manifest) throws IOException {
        writeManifestFile(rootDir(context), manifest);
    }

    private static void writeManifestFile(File root, JSONObject manifest) throws IOException {
        byte[] bytes = manifestBytes(manifest);
        ensureDirectory(root);
        File target = new File(root, MANIFEST_FILE);
        File temp = new File(root, MANIFEST_FILE + ".tmp");
        File backup = new File(root, MANIFEST_FILE + ".bak");
        writeBytesAndSync(temp, bytes);
        if (target.isFile()) {
            copyFile(target, backup);
        }
        if (target.exists() && !target.delete()) {
            deleteQuietly(temp);
            throw new IOException("无法替换素材索引");
        }
        if (!temp.renameTo(target)) {
            copyFile(temp, target);
            deleteQuietly(temp);
        }
    }

    private static void populateFileMetadata(
            JSONObject metadata,
            String kind,
            File file,
            ImageFormat format,
            long size,
            String hash
    ) throws JSONException {
        metadata.put("kind", kind);
        metadata.put("path", file.getAbsolutePath());
        metadata.put("src", srcFor(kind, file));
        metadata.put("mime", format.mime);
        metadata.put("size", size);
        metadata.put("sha256", hash);
        metadata.put("custom", true);
    }

    private static String srcFor(String kind, File file) {
        return LocalWebContent.CUSTOM_BASE_URL + directoryNameUnchecked(kind) + "/" + file.getName();
    }

    private static void addFileToZip(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    private static long copyLimited(InputStream input, File output, long limit) throws IOException {
        long total = 0;
        try (FileOutputStream stream = new FileOutputStream(output, false)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IOException("备份中的单个文件过大");
                }
                stream.write(buffer, 0, read);
            }
            stream.flush();
            stream.getFD().sync();
        }
        return total;
    }

    private static byte[] decodeDataUrl(String dataUrl) throws IOException {
        if (dataUrl == null) {
            throw new IOException("图片内容为空");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || comma == dataUrl.length() - 1) {
            throw new IOException("图片数据格式无效");
        }
        try {
            return Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("图片数据无法解码", error);
        }
    }

    private static JSONObject emptyManifest() throws JSONException {
        return new JSONObject()
                .put("version", MANIFEST_VERSION)
                .put("backgrounds", new JSONArray())
                .put("characters", new JSONArray())
                .put("emotions", new JSONArray());
    }

    private static void ensureArrays(JSONObject manifest) throws JSONException {
        for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
            if (!manifest.has(name) || manifest.optJSONArray(name) == null) {
                manifest.put(name, new JSONArray());
            }
        }
    }

    private static String arrayName(String kind) throws IOException {
        switch (kind) {
            case "background": return "backgrounds";
            case "character": return "characters";
            case "emotion": return "emotions";
            default: throw new IOException("素材类型无效");
        }
    }

    private static String directoryName(String kind) throws IOException {
        return arrayName(kind);
    }

    private static String directoryNameUnchecked(String kind) {
        if ("background".equals(kind)) return "backgrounds";
        if ("character".equals(kind)) return "characters";
        return "emotions";
    }

    private static String singularName(String arrayName) {
        if ("backgrounds".equals(arrayName)) return "background";
        if ("characters".equals(arrayName)) return "character";
        return "emotion";
    }

    private static String requireId(String id) throws IOException {
        String clean = id == null ? "" : id.trim();
        if (clean.isEmpty() || clean.length() > 128 || !clean.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("素材 ID 无效");
        }
        return clean;
    }

    private static int findIndex(JSONArray array, String id) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                return i;
            }
        }
        return -1;
    }

    private static void merge(JSONObject target, JSONObject patch) throws JSONException {
        Iterator<String> keys = patch.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!"id".equals(key) && !"path".equals(key) && !"src".equals(key)
                    && !"mime".equals(key) && !"size".equals(key) && !"sha256".equals(key)
                    && !"custom".equals(key)) {
                target.put(key, patch.get(key));
            }
        }
    }

    private static File fileFromItem(JSONObject item) {
        if (item == null) return null;
        String path = item.optString("path", "");
        return path.isEmpty() ? null : new File(path);
    }

    private static File rootDir(Context context) throws IOException {
        File root = new File(context.getFilesDir(), ROOT_DIR);
        if (!root.exists()) {
            File recovery = findRecoveryTree(context);
            if (recovery != null && !recovery.renameTo(root)) {
                throw new IOException("无法自动回滚，原素材保留在：" + recovery.getAbsolutePath());
            }
        }
        ensureDirectory(root);
        return root;
    }

    private static File findRecoveryTree(Context context) throws IOException {
        File[] candidates = context.getFilesDir().listFiles((dir, name) -> name.startsWith("assets-rollback-"));
        File recovery = null;
        if (candidates != null) {
            for (File candidate : candidates) {
                if (!new File(candidate, MANIFEST_FILE).isFile()) continue;
                if (recovery != null) throw new IOException("存在多个素材恢复目录，请保留并人工恢复");
                recovery = candidate;
            }
        }
        return recovery;
    }

    private static long totalAssetBytes(File root) {
        return directoryBytes(new File(root, "backgrounds"))
                + directoryBytes(new File(root, "characters"))
                + directoryBytes(new File(root, "emotions"));
    }

    private static long directoryBytes(File file) {
        if (!file.exists()) return 0;
        if (file.isFile()) return file.getName().endsWith(".tmp") ? 0 : file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += directoryBytes(child);
        }
        return total;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory == null) return;
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建素材目录");
        }
    }

    private static void writeBytesAndSync(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static byte[] readAllBytes(File file, long limit) throws IOException {
        if (file.length() > limit) throw new IOException("文件过大");
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("文件过大");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] readHeader(File file) throws IOException {
        byte[] header = new byte[12];
        try (FileInputStream input = new FileInputStream(file)) {
            int total = 0;
            while (total < header.length) {
                int read = input.read(header, total, header.length - total);
                if (read < 0) break;
                total += read;
            }
            if (total == header.length) return header;
            byte[] result = new byte[total];
            System.arraycopy(header, 0, result, 0, total);
            return result;
        }
    }

    private static ImageFormat requireFormat(byte[] bytes) throws IOException {
        ImageFormat format = detectFormat(bytes);
        if (format == ImageFormat.UNKNOWN) {
            throw new IOException("仅支持 PNG 或 JPEG 图片");
        }
        return format;
    }

    private static ImageFormat detectFormat(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E
                && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A
                && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return ImageFormat.PNG;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return ImageFormat.JPEG;
        }
        return ImageFormat.UNKNOWN;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(bytes));
        } catch (Exception error) {
            throw new IOException("无法计算素材校验值", error);
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } catch (Exception error) {
            throw new IOException("无法计算素材校验值", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value));
        return result.toString();
    }

    private static String safeFileName(String id) {
        String safe = id.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isEmpty() ? String.valueOf(System.currentTimeMillis()) : safe;
    }

    private static boolean isSafeEntry(String name) {
        return name != null && !name.isEmpty() && !name.startsWith("/") && !name.contains("\\")
                && !name.contains("../") && !name.contains("/..")
                && ("backup.json".equals(name) || name.matches("assets/(background|character|emotion)/[A-Za-z0-9._-]+\\.(png|jpg)"));
    }

    private static JSONObject parseObjectOrEmpty(String json) {
        try {
            return json == null || json.trim().isEmpty() ? new JSONObject() : new JSONObject(json);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String isoTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static File uniqueMigrationTarget(File parent, String base, String extension) {
        File target = new File(parent, base + extension);
        int suffix = 1;
        while (target.exists()) {
            target = new File(parent, base + "-migrated-" + suffix + extension);
            suffix++;
        }
        return target;
    }

    private static boolean sameFile(File first, File second) throws IOException {
        return first.getCanonicalFile().equals(second.getCanonicalFile());
    }

    private static boolean isInsideRoot(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath() + File.separator;
        return file.getCanonicalPath().startsWith(rootPath);
    }

    private static void copyFile(File source, File target) throws IOException {
        ensureDirectory(target.getParentFile());
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void preserveCorruptManifest(File manifest) {
        File backup = new File(manifest.getParentFile(), "manifest.corrupt-" + System.currentTimeMillis() + ".json");
        try {
            copyFile(manifest, backup);
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) file.delete();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private enum ImageFormat {
        PNG(".png", "image/png"),
        JPEG(".jpg", "image/jpeg"),
        UNKNOWN("", "");

        final String extension;
        final String mime;

        ImageFormat(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }
    }

    private static final class AssetChange {
        final File output;
        final File previous;
        final File source;
        final byte[] bytes;
        final long size;

        AssetChange(File output, File previous, File source, byte[] bytes, long size) {
            this.output = output;
            this.previous = previous;
            this.source = source;
            this.bytes = bytes;
            this.size = size;
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream output;
        private final long limit;
        private long count;

        LimitedOutputStream(OutputStream output, long limit) {
            this.output = output;
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            reserve(length);
            output.write(bytes, offset, length);
        }

        private void reserve(int length) throws IOException {
            if (length > limit - count) throw new IOException("备份文件不能超过 220 MB");
            count += length;
        }

        @Override
        public void flush() throws IOException {
            output.flush();
        }

        @Override
        public void close() throws IOException {
            output.close();
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        CountingInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) add(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) add(read);
            return read;
        }

        private void add(int value) throws IOException {
            count += value;
            if (count > limit) throw new IOException("备份文件不能超过 220 MB");
        }
    }
}
