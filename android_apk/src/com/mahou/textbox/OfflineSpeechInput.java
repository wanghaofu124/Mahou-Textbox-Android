package com.mahou.textbox;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline Chinese speech input backed by a verified, app-private Vosk model. */
final class OfflineSpeechInput {
    interface Listener {
        void onStatus(String status);
        void onListening();
        void onPartialText(String text);
        void onComplete(String text);
        void onError(String message, Exception error);
    }

    private static final String TAG = "MahouVoice";
    private static final String MODEL_NAME = "vosk-model-small-cn-0.22";
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip";
    private static final String MODEL_SHA256 = "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba";
    private static final long MODEL_ARCHIVE_BYTES = 43_898_754L;
    private static final float SAMPLE_RATE = 16_000f;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile int generation;
    private volatile boolean closed;
    private volatile HttpURLConnection activeConnection;
    private volatile boolean preparing;
    private volatile boolean listening;
    private Future<?> preparationTask;
    private Listener listener;
    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private final StringBuilder finalText = new StringBuilder();

    OfflineSpeechInput(Context context) {
        appContext = context.getApplicationContext();
    }

    boolean isPreparing() {
        return preparing;
    }

    boolean hasInstalledModel() {
        return isInstalled(new File(new File(appContext.getFilesDir(), "speech-models"), MODEL_NAME));
    }

    void start(Listener callback) {
        if (closed || preparing || listening) return;
        listener = callback;
        preparing = true;
        int requestId = ++generation;
        postStatus(requestId, "正在准备本机语音包…");

        preparationTask = worker.submit(() -> {
            File modelDirectory;
            try {
                modelDirectory = ensureModelInstalled(requestId);
            } catch (Exception error) {
                postFailure(requestId, "语音包下载或安装失败，请检查网络和存储空间后重试", error);
                return;
            }

            if (!isCurrent(requestId)) return;
            try {
                if (model == null) model = new Model(modelDirectory.getAbsolutePath());
                Recognizer nextRecognizer = new Recognizer(model, SAMPLE_RATE);
                SpeechService nextService = new SpeechService(nextRecognizer, SAMPLE_RATE);
                mainHandler.post(() -> beginListening(requestId, nextRecognizer, nextService));
            } catch (Exception | LinkageError error) {
                Log.e(TAG, "offline recognizer initialization failed", error);
                postFailure(requestId, "本机语音引擎启动失败，请重试", error instanceof Exception
                        ? (Exception) error : new IOException(error));
            }
        });
    }

    void cancelPreparation() {
        if (!preparing) return;
        preparing = false;
        ++generation;
        Future<?> task = preparationTask;
        preparationTask = null;
        if (task != null) task.cancel(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    void stop() {
        if (!listening || speechService == null) return;
        speechService.stop();
    }

    void close() {
        if (closed) return;
        closed = true;
        preparing = false;
        listening = false;
        ++generation;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        Future<?> task = preparationTask;
        if (task != null) task.cancel(true);
        releaseCurrentSession(true);
        worker.execute(() -> {
            if (model != null) {
                model.close();
                model = null;
            }
        });
        worker.shutdown();
    }

    private void beginListening(int requestId, Recognizer nextRecognizer, SpeechService nextService) {
        if (!isCurrent(requestId)) {
            closeSession(nextService, nextRecognizer, true);
            return;
        }
        preparationTask = null;
        preparing = false;
        listening = true;
        recognizer = nextRecognizer;
        speechService = nextService;
        finalText.setLength(0);
        if (listener != null) listener.onListening();

        boolean started;
        try {
            started = nextService.startListening(new RecognitionListener() {
            @Override public void onPartialResult(String hypothesis) {
                if (!isCurrent(requestId) || !listening || listener == null) return;
                String partial = jsonText(hypothesis, "partial");
                listener.onPartialText(finalText.toString() + partial);
            }

            @Override public void onResult(String hypothesis) {
                if (!isCurrent(requestId) || !listening) return;
                appendResult(jsonText(hypothesis, "text"));
                if (listener != null) listener.onPartialText(finalText.toString());
            }

            @Override public void onFinalResult(String hypothesis) {
                if (!isCurrent(requestId) || !listening) return;
                appendResult(jsonText(hypothesis, "text"));
                String text = finalText.toString().trim();
                finishSession();
                if (listener != null) listener.onComplete(text);
            }

            @Override public void onError(Exception error) {
                if (!isCurrent(requestId) || !listening) return;
                finishSession();
                Log.e(TAG, "offline speech capture failed", error);
                if (listener != null) listener.onError("麦克风无法启动，请检查权限或是否被占用", error);
            }

            @Override public void onTimeout() {
                if (!isCurrent(requestId) || !listening) return;
                String text = finalText.toString().trim();
                finishSession();
                if (listener != null) listener.onComplete(text);
            }
            });
        } catch (RuntimeException error) {
            finishSession();
            Log.e(TAG, "offline speech capture could not start", error);
            if (listener != null) listener.onError("本机语音识别启动失败，请检查麦克风权限或占用情况", error);
            return;
        }

        if (!started) {
            finishSession();
            if (listener != null) listener.onError("本机语音识别未能启动，请再试一次", new IOException("SpeechService did not start"));
        }
    }

    private void finishSession() {
        listening = false;
        SpeechService service = speechService;
        Recognizer activeRecognizer = recognizer;
        speechService = null;
        recognizer = null;
        closeSession(service, activeRecognizer, false);
    }

    private void releaseCurrentSession(boolean cancel) {
        SpeechService service = speechService;
        Recognizer activeRecognizer = recognizer;
        speechService = null;
        recognizer = null;
        closeSession(service, activeRecognizer, cancel);
    }

    private static void closeSession(SpeechService service, Recognizer activeRecognizer, boolean cancel) {
        if (service != null) {
            if (cancel) service.cancel();
            service.shutdown();
        }
        if (activeRecognizer != null) activeRecognizer.close();
    }

    private void appendResult(String value) {
        if (value == null || value.isEmpty()) return;
        if (finalText.length() > 0 && isLatinWordCharacter(finalText.charAt(finalText.length() - 1))
                && isLatinWordCharacter(value.charAt(0))) {
            finalText.append(' ');
        }
        finalText.append(value);
    }

    private static boolean isLatinWordCharacter(char value) {
        return value < 128 && Character.isLetterOrDigit(value);
    }

    private static String jsonText(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private File ensureModelInstalled(int requestId) throws Exception {
        File modelsRoot = new File(appContext.getFilesDir(), "speech-models");
        File installed = new File(modelsRoot, MODEL_NAME);
        if (isInstalled(installed)) return installed;

        if (!modelsRoot.isDirectory() && !modelsRoot.mkdirs()) throw new IOException("Unable to create model directory");
        deleteTree(installed);
        File archive = new File(appContext.getCacheDir(), MODEL_NAME + ".zip.part");
        File extracted = new File(modelsRoot, MODEL_NAME + ".installing");
        archive.delete();
        deleteTree(extracted);

        try {
            downloadModel(requestId, archive);
            postStatus(requestId, "正在校验语音包…");
            if (archive.length() != MODEL_ARCHIVE_BYTES || !MODEL_SHA256.equals(sha256(archive))) {
                archive.delete();
                throw new IOException("语音包校验失败");
            }
            if (!isCurrent(requestId)) throw new InterruptedIOException("Download cancelled");
            postStatus(requestId, "正在安装本机语音包…");
            extractModel(archive, extracted);
            if (!hasModelFiles(extracted)) throw new IOException("语音包文件不完整");
            File marker = new File(extracted, ".mahou-model-sha256");
            try (FileOutputStream output = new FileOutputStream(marker)) {
                output.write(MODEL_SHA256.getBytes(StandardCharsets.US_ASCII));
            }
            if (!isCurrent(requestId)) throw new InterruptedIOException("Download cancelled");
            if (installed.exists()) deleteTree(installed);
            if (!extracted.renameTo(installed)) throw new IOException("Unable to finalize model directory");
            return installed;
        } finally {
            archive.delete();
            deleteTree(extracted);
        }
    }

    private void downloadModel(int requestId, File archive) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        activeConnection = connection;
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "MahouTextbox/0.7.27");
        try {
            int response = connection.getResponseCode();
            if (response != HttpURLConnection.HTTP_OK) throw new IOException("Model server returned HTTP " + response);
            int contentLength = connection.getContentLength();
            if (contentLength > 0 && contentLength != MODEL_ARCHIVE_BYTES) throw new IOException("Unexpected model size");
            postStatus(requestId, "正在下载中文语音包：0%");
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream fileOutput = new FileOutputStream(archive);
                 BufferedOutputStream output = new BufferedOutputStream(fileOutput)) {
                byte[] buffer = new byte[32 * 1024];
                long downloaded = 0;
                int previousPercent = -1;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (!isCurrent(requestId) || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Model download cancelled");
                    }
                    output.write(buffer, 0, read);
                    downloaded += read;
                    if (downloaded > MODEL_ARCHIVE_BYTES) throw new IOException("Unexpected model size");
                    int percent = (int) (downloaded * 100 / MODEL_ARCHIVE_BYTES);
                    if (percent != previousPercent) {
                        previousPercent = percent;
                        postStatus(requestId, "正在下载中文语音包：" + percent + "%");
                    }
                }
                output.flush();
                if (downloaded != MODEL_ARCHIVE_BYTES) throw new IOException("Incomplete model download");
            }
        } finally {
            if (activeConnection == connection) activeConnection = null;
            connection.disconnect();
        }
    }

    private static void extractModel(File archive, File destination) throws IOException {
        deleteTree(destination);
        if (!destination.mkdirs()) throw new IOException("Unable to create extraction directory");
        String prefix = MODEL_NAME + "/";
        String rootPath = destination.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[32 * 1024];
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith(prefix)) throw new IOException("Unexpected model archive entry");
                String relativePath = name.substring(prefix.length());
                if (relativePath.isEmpty()) continue;
                File outputFile = new File(destination, relativePath);
                if (!outputFile.getCanonicalPath().startsWith(rootPath)) throw new IOException("Invalid model archive path");
                if (entry.isDirectory()) {
                    if (!outputFile.isDirectory() && !outputFile.mkdirs()) throw new IOException("Unable to create model folder");
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Unable to create model folder");
                    try (FileOutputStream fileOutput = new FileOutputStream(outputFile);
                         BufferedOutputStream output = new BufferedOutputStream(fileOutput)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    }
                }
                input.closeEntry();
            }
        }
    }

    private static boolean isInstalled(File modelDirectory) {
        File marker = new File(modelDirectory, ".mahou-model-sha256");
        if (!marker.isFile() || !hasModelFiles(modelDirectory)) return false;
        try (InputStream input = new FileInputStream(marker)) {
            byte[] bytes = new byte[MODEL_SHA256.length()];
            int read = input.read(bytes);
            return read == bytes.length && MODEL_SHA256.equals(new String(bytes, StandardCharsets.US_ASCII));
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean hasModelFiles(File modelDirectory) {
        return new File(modelDirectory, "am/final.mdl").isFile()
                && new File(modelDirectory, "conf/model.conf").isFile()
                && new File(modelDirectory, "graph/Gr.fst").isFile();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder value = new StringBuilder();
        for (byte item : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private boolean isCurrent(int requestId) {
        return !closed && requestId == generation;
    }

    private void postStatus(int requestId, String status) {
        mainHandler.post(() -> {
            if (isCurrent(requestId) && preparing && listener != null) listener.onStatus(status);
        });
    }

    private void postFailure(int requestId, String message, Exception error) {
        if (!isCurrent(requestId)) return;
        Log.e(TAG, message, error);
        mainHandler.post(() -> {
            if (!isCurrent(requestId)) return;
            preparing = false;
            preparationTask = null;
            if (listener != null) listener.onError(message, error);
        });
    }
}
