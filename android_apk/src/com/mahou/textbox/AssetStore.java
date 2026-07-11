package com.mahou.textbox;

import android.content.Context;
import android.util.Base64;

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
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class AssetStore {
    public static final long MAX_BACKUP_BYTES = 220L * 1024L * 1024L;
    public static final long MAX_TOTAL_ASSET_BYTES = 200L * 1024L * 1024L;
    public static final long MAX_SINGLE_ASSET_BYTES = 20L * 1024L * 1024L;

    private static final int MANIFEST_VERSION = 3;
    private static final int BACKUP_VERSION = 1;
    private static final int MAX_BACKUP_ENTRIES = 1000;
    private static final long MAX_MANIFEST_BYTES = 5L * 1024L * 1024L;
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
        JSONObject metadata = new JSONObject(metadataJson);
        String id = requireId(metadata.optString("id"));
        byte[] imageBytes = decodeDataUrl(imageDataUrl);
        if (imageBytes.length == 0) {
            throw new IOException("图片内容为空");
        }
        if (imageBytes.length > MAX_SINGLE_ASSET_BYTES) {
            throw new IOException("单张素材不能超过 20 MB");
        }

        ImageFormat format = requireFormat(imageBytes);
        JSONObject manifest = readManifest(context);
        JSONArray array = manifest.getJSONArray(arrayName(kind));
        int existingIndex = findIndex(array, id);
        File root = rootDir(context);
        File outputDir = new File(root, directoryName(kind));
        ensureDirectory(outputDir);
        File outputFile = new File(outputDir, safeFileName(id) + format.extension);

        long replacedBytes = 0;
        File previousFile = null;
        if (existingIndex >= 0) {
            previousFile = fileFromItem(array.optJSONObject(existingIndex));
            if (previousFile != null && previousFile.isFile()) {
                replacedBytes = previousFile.length();
            }
        }
        long projected = totalAssetBytes(root) - replacedBytes + imageBytes.length;
        if (projected > MAX_TOTAL_ASSET_BYTES) {
            throw new IOException("自定义素材总容量不能超过 200 MB");
        }

        writeFileAtomically(outputFile, imageBytes);
        if (previousFile != null && !sameFile(previousFile, outputFile) && isInsideRoot(root, previousFile)) {
            deleteQuietly(previousFile);
        }

        populateFileMetadata(metadata, kind, outputFile, format, imageBytes.length, sha256(imageBytes));
        if (existingIndex >= 0) {
            array.put(existingIndex, metadata);
        } else {
            array.put(metadata);
        }
        writeManifest(context, manifest);
        return metadata.toString();
    }

    public static synchronized String saveCharacterBundle(
            Context context,
            String characterJson,
            String emotionJson,
            String imageDataUrl
    ) throws IOException, JSONException {
        JSONObject character = new JSONObject(characterJson);
        String characterId = requireId(character.optString("id"));
        boolean characterSaved = false;
        try {
            String savedCharacter = saveAsset(context, "character", characterJson, imageDataUrl);
            characterSaved = true;
            String savedEmotion = saveAsset(context, "emotion", emotionJson, imageDataUrl);
            return new JSONObject()
                    .put("character", new JSONObject(savedCharacter))
                    .put("emotion", new JSONObject(savedEmotion))
                    .toString();
        } catch (Exception error) {
            if (characterSaved) {
                deleteAsset(context, "character", characterId);
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            if (error instanceof JSONException) {
                throw (JSONException) error;
            }
            throw new IOException("角色素材保存失败", error);
        }
    }

    public static synchronized boolean deleteAsset(Context context, String kind, String id)
            throws IOException, JSONException {
        JSONObject manifest = readManifest(context);
        JSONArray array = manifest.getJSONArray(arrayName(kind));
        int index = findIndex(array, id);
        if (index < 0) {
            return false;
        }
        JSONObject item = array.optJSONObject(index);
        if (item == null || !item.optBoolean("custom", false)) {
            return false;
        }

        File root = rootDir(context);
        deleteItemFile(root, item);
        array.remove(index);
        if ("character".equals(kind)) {
            JSONArray emotions = manifest.getJSONArray("emotions");
            for (int i = emotions.length() - 1; i >= 0; i--) {
                JSONObject emotion = emotions.optJSONObject(i);
                if (emotion != null && id.equals(emotion.optString("ownerId"))) {
                    deleteItemFile(root, emotion);
                    emotions.remove(i);
                }
            }
        }
        writeManifest(context, manifest);
        return true;
    }

    public static synchronized String updateAsset(Context context, String id, String metadataJson)
            throws IOException, JSONException {
        JSONObject patch = new JSONObject(metadataJson);
        JSONObject manifest = readManifest(context);
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
            writeManifest(context, manifest);
            return current.toString();
        }
        throw new IOException("未找到要更新的素材");
    }

    public static synchronized String exportBackup(Context context, OutputStream output, String settingsJson)
            throws IOException, JSONException {
        JSONObject manifest = readManifest(context);
        JSONObject backup = new JSONObject()
                .put("formatVersion", BACKUP_VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdAt", isoTimestamp())
                .put("settings", parseObjectOrEmpty(settingsJson));

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            int exported = 0;
            for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
                JSONArray source = manifest.getJSONArray(name);
                JSONArray target = new JSONArray();
                String kind = singularName(name);
                for (int i = 0; i < source.length(); i++) {
                    JSONObject item = source.optJSONObject(i);
                    if (item == null || !item.optBoolean("custom", false)) {
                        continue;
                    }
                    File file = fileFromItem(item);
                    if (file == null || !file.isFile() || !isInsideRoot(rootDir(context), file)) {
                        throw new IOException("素材文件缺失：" + item.optString("label", item.optString("id")));
                    }
                    ImageFormat format = requireFormat(readHeader(file));
                    String entryName = "assets/" + kind + "/" + safeFileName(item.optString("id")) + format.extension;
                    JSONObject copy = new JSONObject(item.toString());
                    copy.remove("path");
                    copy.remove("src");
                    copy.put("file", entryName);
                    copy.put("mime", format.mime);
                    copy.put("size", file.length());
                    copy.put("sha256", sha256(file));
                    target.put(copy);
                    addFileToZip(zip, entryName, file);
                    exported++;
                }
                backup.put(name, target);
            }
            byte[] index = backup.toString().getBytes(StandardCharsets.UTF_8);
            zip.putNextEntry(new ZipEntry("backup.json"));
            zip.write(index);
            zip.closeEntry();
            zip.finish();
            return new JSONObject()
                    .put("status", "success")
                    .put("exportedCount", exported)
                    .put("message", "已备份 " + exported + " 个自定义素材")
                    .toString();
        }
    }

    public static synchronized String importBackup(Context context, InputStream input)
            throws IOException, JSONException {
        File filesDir = context.getFilesDir();
        File extracted = new File(filesDir, "assets-import-" + System.currentTimeMillis());
        File merged = new File(filesDir, "assets-merge-" + System.currentTimeMillis());
        File rollback = new File(filesDir, "assets-rollback-" + System.currentTimeMillis());
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

            File currentRoot = rootDir(context);
            JSONObject mergedManifest = new JSONObject(readManifest(context).toString());
            long currentBytes = totalAssetBytes(currentRoot);
            long replacedBytes = 0;
            long importedBytes = 0;
            int importedCount = 0;
            int overwrittenCount = 0;

            ensureDirectory(merged);
            copyAssetTree(currentRoot, merged);
            for (String name : new String[]{"backgrounds", "characters", "emotions"}) {
                JSONArray incoming = backup.optJSONArray(name);
                if (incoming == null) {
                    incoming = new JSONArray();
                }
                JSONArray destination = mergedManifest.getJSONArray(name);
                String kind = singularName(name);
                File destinationDir = new File(merged, name);
                ensureDirectory(destinationDir);
                for (int i = 0; i < incoming.length(); i++) {
                    JSONObject item = incoming.optJSONObject(i);
                    if (item == null) {
                        throw new IOException("备份素材索引无效");
                    }
                    String id = requireId(item.optString("id"));
                    String entryName = item.optString("file", "");
                    if (!isSafeEntry(entryName) || !entryName.startsWith("assets/" + kind + "/")) {
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
                    if (!actualHash.equalsIgnoreCase(item.optString("sha256", ""))) {
                        throw new IOException("素材校验失败：" + id);
                    }

                    int existing = findIndex(destination, id);
                    File previousMergedFile = null;
                    if (existing >= 0) {
                        File old = fileFromItem(destination.optJSONObject(existing));
                        if (old != null && old.isFile()) {
                            replacedBytes += old.length();
                            previousMergedFile = new File(destinationDir, old.getName());
                        }
                        overwrittenCount++;
                    }
                    importedBytes += staged.length();
                    importedCount++;

                    File outputFile = new File(destinationDir, safeFileName(id) + format.extension);
                    copyFile(staged, outputFile);
                    if (previousMergedFile != null && !sameFile(previousMergedFile, outputFile)) {
                        deleteQuietly(previousMergedFile);
                    }
                    JSONObject normalized = new JSONObject(item.toString());
                    normalized.remove("file");
                    populateFileMetadata(
                            normalized,
                            kind,
                            new File(new File(currentRoot, name), outputFile.getName()),
                            format,
                            outputFile.length(),
                            actualHash
                    );
                    if (existing >= 0) {
                        destination.put(existing, normalized);
                    } else {
                        destination.put(normalized);
                    }
                }
            }

            if (currentBytes - replacedBytes + importedBytes > MAX_TOTAL_ASSET_BYTES) {
                throw new IOException("恢复后素材总量将超过 200 MB");
            }
            mergedManifest.put("version", MANIFEST_VERSION);
            writeManifestFile(merged, mergedManifest);
            commitImportedTree(currentRoot, merged, rollback);

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
            deleteRecursively(merged);
            deleteRecursively(rollback);
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

    private static boolean migrateManifest(Context context, JSONObject manifest)
            throws IOException, JSONException {
        boolean changed = manifest.optInt("version", 1) != MANIFEST_VERSION;
        File root = rootDir(context);
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
                    if (!source.renameTo(target)) {
                        copyFile(source, target);
                        if (!source.delete()) {
                            deleteQuietly(target);
                            continue;
                        }
                    }
                    source = target;
                    changed = true;
                }
                String hash = sha256(source);
                if (!format.mime.equals(item.optString("mime"))
                        || item.optLong("size", -1) != source.length()
                        || !hash.equalsIgnoreCase(item.optString("sha256", ""))
                        || !srcFor(kind, source).equals(item.optString("src"))) {
                    populateFileMetadata(item, kind, source, format, source.length(), hash);
                    changed = true;
                }
            }
        }
        manifest.put("version", MANIFEST_VERSION);
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
        deleteRecursively(rollback);
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
        ensureDirectory(root);
        File target = new File(root, MANIFEST_FILE);
        File temp = new File(root, MANIFEST_FILE + ".tmp");
        File backup = new File(root, MANIFEST_FILE + ".bak");
        writeBytesAndSync(temp, manifest.toString().getBytes(StandardCharsets.UTF_8));
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

    private static void writeFileAtomically(File target, byte[] bytes) throws IOException {
        ensureDirectory(target.getParentFile());
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        writeBytesAndSync(temp, bytes);
        if (target.exists() && !target.delete()) {
            deleteQuietly(temp);
            throw new IOException("无法替换素材文件");
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

    private static void deleteItemFile(File root, JSONObject item) throws IOException {
        File file = fileFromItem(item);
        if (file != null && isInsideRoot(root, file)) {
            deleteQuietly(file);
        }
    }

    private static File rootDir(Context context) throws IOException {
        File root = new File(context.getFilesDir(), ROOT_DIR);
        ensureDirectory(root);
        return root;
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
