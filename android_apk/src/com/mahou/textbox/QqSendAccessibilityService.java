package com.mahou.textbox;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One explicit user request, one generated image, one existing conversation. Never chooses a recipient. */
public final class QqSendAccessibilityService extends AccessibilityService {
    static final String ACTION_RESULT = "com.mahou.textbox.DIRECT_SEND_RESULT";
    static final String ACTION_BEGIN = "com.mahou.textbox.DIRECT_SEND_BEGIN";
    interface SendCallback { void onResult(String problem); }
    private static final String STORE = "mahou_send_temporary_media";
    private static volatile QqSendAccessibilityService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile SendSession session;
    private volatile boolean closed;
    private final Runnable advance = this::step;

    enum Stage { PREPARING, OPEN_ALBUM, OPEN_FOLDERS, FIND_ALBUM, SELECT_IMAGE, VERIFY_SELECTION, WAIT_RETURN }
    static final class SendSession {
        final String id = UUID.randomUUID().toString();
        final String target, conversation;
        final long deadline = SystemClock.elapsedRealtime() + 20000;
        final String album = "魔裁-" + id.substring(0, 8);
        final String filename = "mahou-" + id + ".png";
        Stage stage = Stage.PREPARING;
        Uri media;
        long nextAction;
        int moreClicks, folderScrolls;
        boolean sendClicked;
        Stage logged;
        SendSession(String target, String conversation) { this.target = target; this.conversation = conversation; }
    }

    static boolean isTarget(String name) {
        return "com.tencent.mobileqq".equals(name) || "com.tencent.tim".equals(name)
                || BuildConfig.DEBUG && "com.mahou.imeclient".equals(name);
    }
    static boolean isAvailable() { return instance != null && !instance.closed && Build.VERSION.SDK_INT >= 29; }

    static boolean isEnabled(Context context) {
        if (Build.VERSION.SDK_INT < 29 || context == null) return false;
        if (isAvailable()) return true;
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) return false;
        ComponentName component = new ComponentName(context, QqSendAccessibilityService.class);
        String full = component.flattenToString();
        String brief = component.flattenToShortString();
        for (String item : enabled.split(":")) if (full.equals(item) || brief.equals(item)) return true;
        return false;
    }

    static String status(Context context) {
        try {
            QqSendAccessibilityService service = instance;
            return new JSONObject().put("enabled", isEnabled(context)).put("connected", isAvailable())
                    .put("supported", Build.VERSION.SDK_INT >= 29)
                    .put("busy", service != null && service.session != null).toString();
        } catch (Exception ignored) { return "{}"; }
    }

    static void openSettings(Context context) {
        Intent settings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (context instanceof Activity) {
            new AlertDialog.Builder(context).setTitle("QQ 辅助发送")
                    .setMessage("开启后，只有你点发送时才会读取当前 QQ/TIM 界面，打开本次生成图片的临时相册并点击发送。不会选择群或好友，不上传聊天内容。你可以随时在系统无障碍设置中关闭。")
                    .setNegativeButton("取消", null).setPositiveButton("去开启", (dialog, which) -> context.startActivity(settings)).show();
        } else {
            Toast.makeText(context, "请开启“魔裁 QQ 辅助发送”，返回当前聊天后再点发送", Toast.LENGTH_LONG).show();
            context.startActivity(settings);
        }
    }

    static void openChat(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.tencent.mobileqq");
        if (launch == null) launch = context.getPackageManager().getLaunchIntentForPackage("com.tencent.tim");
        if (launch == null) { Toast.makeText(context, "尚未安装 QQ 或 TIM", Toast.LENGTH_SHORT).show(); return; }
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Toast.makeText(context, "在要发送的聊天里打开魔裁，点发送即可", Toast.LENGTH_LONG).show();
    }

    // The keyboard and this service run in different processes. A static instance in the keyboard is always empty.
    static void request(Context context, File image, String target, SendCallback callback) {
        if (Build.VERSION.SDK_INT < 29) { callback.onResult("QQ 辅助发送需要 Android 10 或更新版本"); return; }
        if (image == null || !image.isFile() || image.length() == 0 || image.length() > 12 * 1024 * 1024) {
            callback.onResult("图片数据无效，请重新生成"); return;
        }
        if (!isEnabled(context)) { callback.onResult("请先在设置中开启“QQ 辅助发送”"); return; }
        QqSendAccessibilityService service = instance;
        if (service != null && !service.closed) { callback.onResult(service.start(image, target)); return; }
        Intent intent = new Intent(ACTION_BEGIN).setPackage(context.getPackageName());
        intent.putExtra("path", image.getAbsolutePath());
        intent.putExtra("target", target == null ? "" : target);
        context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent done) {
                int code = getResultCode();
                String problem = code == 1 ? null : code == 2 ? getResultData() : "辅助发送正在连接，请稍候再点一次发送";
                // #region agent log
                Log.i("MahouDebug", "assist request pid=" + android.os.Process.myPid() + " code=" + code);
                // #endregion
                callback.onResult(problem);
            }
        }, null, 0, null, null);
    }

    private final BroadcastReceiver beginRequest = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_BEGIN.equals(intent.getAction())) return;
            String path = intent.getStringExtra("path");
            String problem = start(path == null ? null : new File(path), intent.getStringExtra("target"));
            if (problem == null) setResultCode(1);
            else { setResultCode(2); setResultData(problem); }
            // #region agent log
            Log.i("MahouDebug", "assist accept pid=" + android.os.Process.myPid() + " ok=" + (problem == null));
            // #endregion
        }
    };

    private String start(File image, String target) {
        if (session != null) return "上一张图片正在发送，请稍候";
        if (!owned(image)) return "图片数据无效，请重新生成";
        AccessibilityNodeInfo root = chatRoot();
        if (root == null) return "未找到当前 QQ 聊天，请先打开聊天";
        try {
            String actual = string(root.getPackageName());
            // #region agent log
            Log.i("MahouDebug", "assist window=" + actual + " target=" + target);
            // #endregion
            if (!isTarget(actual) || target != null && !target.isEmpty() && !target.equals(actual))
                return "当前窗口不是要发送的 QQ 聊天";
            String conversation = conversation(root);
            if (conversation == null) return "无法确认当前会话，请打开 QQ 群聊或好友聊天后重试";
            SendSession pending = new SendSession(actual, conversation);
            session = pending;
            report("working", "正在发送到“" + conversation + "”");
            worker.execute(() -> prepare(pending, image));
            main.postDelayed(advance, 250);
            return null;
        } finally { root.recycle(); }
    }

    static void cancel() {
        QqSendAccessibilityService service = instance;
        if (service != null) service.finish("cancelled", "发送已停止", false);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        closed = false;
        try { unregisterReceiver(beginRequest); } catch (RuntimeException ignored) {}
        ContextCompat.registerReceiver(this, beginRequest, new IntentFilter(ACTION_BEGIN), ContextCompat.RECEIVER_NOT_EXPORTED);
        // #region agent log
        Log.i("MahouDebug", "assist connected pid=" + android.os.Process.myPid());
        // #endregion
        if (BuildConfig.DEBUG) {
            AccessibilityServiceInfo info = getServiceInfo();
            info.packageNames = new String[]{"com.tencent.mobileqq", "com.tencent.tim", "com.mahou.imeclient"};
            setServiceInfo(info);
        }
        worker.execute(() -> {
            // Only URIs previously created and recorded by this app can be removed.
            for (String uri : getSharedPreferences(STORE, MODE_PRIVATE).getAll().keySet()) deleteMedia(Uri.parse(uri));
        });
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (session == null) return;
        main.removeCallbacks(advance);
        main.postDelayed(advance, 120);
    }
    @Override public void onInterrupt() {
        // #region agent log
        Log.i("MahouDebug", "assist interrupt session=" + (session != null));
        // #endregion
    }
    @Override public void onDestroy() {
        closed = true;
        try { unregisterReceiver(beginRequest); } catch (RuntimeException ignored) {}
        // #region agent log
        Log.i("MahouDebug", "assist destroyed pid=" + android.os.Process.myPid());
        // #endregion
        finish("cancelled", "辅助发送已关闭", false);
        instance = null;
        main.removeCallbacksAndMessages(null);
        worker.shutdown();
        super.onDestroy();
    }

    private void prepare(SendSession pending, File source) {
        Uri media = null;
        try {
            if (closed || session != pending) return;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, pending.filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + pending.album + "/");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            media = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (media == null) throw new java.io.IOException();
            getSharedPreferences(STORE, MODE_PRIVATE).edit().putLong(media.toString(), System.currentTimeMillis()).commit();
            try (FileInputStream input = new FileInputStream(source); OutputStream output = getContentResolver().openOutputStream(media)) {
                if (output == null) throw new java.io.IOException();
                byte[] buffer = new byte[16384];
                for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
            }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(media, values, null, null);
            Uri complete = media;
            main.post(() -> {
                if (closed || session != pending) { worker.execute(() -> deleteMedia(complete)); return; }
                pending.media = complete;
                pending.stage = Stage.OPEN_ALBUM;
                step();
            });
        } catch (Exception | OutOfMemoryError error) {
            if (media != null) deleteMedia(media);
            main.post(() -> { if (session == pending) finish("error", "无法准备发送图片，请检查存储空间", false); });
        }
    }

    private void step() {
        main.removeCallbacks(advance);
        SendSession current = session;
        if (current == null || closed) return;
        try {
            if (!advanceSession(current)) return;
        } catch (RuntimeException error) {
            // #region agent log
            Log.i("MahouDebug", "assist step-error=" + error.getClass().getSimpleName());
            // #endregion
            finish("error", "这一次发送已停止，辅助发送仍开着", false);
            return;
        }
        if (session == current && !closed) main.postDelayed(advance, 250);
    }

    private boolean advanceSession(SendSession current) {
        if (SystemClock.elapsedRealtime() > current.deadline) {
            finish("error", current.sendClicked ? "已点 QQ 发送，请查看聊天结果；没有重复发送" : "当前 QQ 页面未能识别，已停止，没有发送", current.sendClicked);
            return false;
        }
        AccessibilityNodeInfo root = chatRoot();
        try {
            if (root == null) return true;
            String pkg = string(root.getPackageName());
            boolean inChatApp = current.target.equals(pkg);
            if (hasLabel(root, "选择联系人", "选择好友", "选择会话", "发送给", "选择群聊")) {
                finish("error", "出现会话选择页面，已停止发送", false); return false;
            }
            if (current.stage == Stage.PREPARING || SystemClock.elapsedRealtime() < current.nextAction) return true;
            if (current.logged != current.stage) {
                current.logged = current.stage;
                // #region agent log
                Log.i("MahouDebug", "assist stage=" + current.stage + " pkg=" + pkg);
                // #endregion
            }
            String chat = inChatApp ? conversation(root) : null;
            if (differentChat(current.conversation, chat)) {
                finish("cancelled", "聊天对象已变化，已停止发送", current.sendClicked); return false;
            }
            if (!inChatApp && current.stage == Stage.OPEN_ALBUM) return true;
            switch (current.stage) {
                case OPEN_ALBUM:
                    if (chat == null) break;
                    AccessibilityNodeInfo photo = action(root, true, "图片", "相册", "照片", "发送图片", "打开相册");
                    if (photo != null) {
                        if (press(photo)) current.stage = Stage.OPEN_FOLDERS;
                    } else if (current.moreClicks < 1) {
                        AccessibilityNodeInfo more = action(root, true, "更多", "更多功能", "加号");
                        if (more != null && press(more)) current.moreClicks++;
                    }
                    break;
                case OPEN_FOLDERS:
                    if (hasLabel(root, current.album)) { current.stage = Stage.FIND_ALBUM; break; }
                    AccessibilityNodeInfo albums = action(root, false, "所有照片", "全部照片", "最近照片", "图片和视频", "最近项目", "相机胶卷", "所有图片", "相册");
                    if (albums != null && press(albums)) current.stage = Stage.FIND_ALBUM;
                    break;
                case FIND_ALBUM:
                    AccessibilityNodeInfo folder = albumNode(root, current.album);
                    if (folder != null && press(folder)) { current.stage = Stage.SELECT_IMAGE; break; }
                    if (current.folderScrolls < 3) {
                        AccessibilityNodeInfo scroll = unique(root, node -> node.isScrollable());
                        if (scroll != null) {
                            boolean moved = scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                            scroll.recycle();
                            if (moved) current.folderScrolls++;
                            else current.folderScrolls = 3;
                        }
                    }
                    break;
                case SELECT_IMAGE:
                    if (!hasLabel(root, current.album)) break;
                    AccessibilityNodeInfo checkbox = imageChoice(root, current.filename);
                    if (checkbox != null && press(checkbox)) current.stage = Stage.VERIFY_SELECTION;
                    break;
                case VERIFY_SELECTION:
                    if (!hasLabel(root, current.album)) break;
                    AccessibilityNodeInfo send = unique(root, node -> label(node).matches("发送\\s*[（(]\\s*1\\s*[)）]"));
                    if (send != null) {
                        // Advance before the irreversible click; no callback or timeout can send twice.
                        current.stage = Stage.WAIT_RETURN;
                        current.sendClicked = press(send);
                        if (!current.sendClicked) finish("error", "QQ 发送按钮未响应，没有重试", false);
                    }
                    break;
                case WAIT_RETURN:
                    if (inChatApp && chat != null && !differentChat(current.conversation, chat))
                        finish("submitted", "已在“" + chat + "”点击 QQ 发送", true);
                    break;
                default: break;
            }
            current.nextAction = SystemClock.elapsedRealtime() + 350;
            return true;
        } finally { if (root != null) root.recycle(); }
    }

    private static boolean differentChat(String expected, String seen) {
        return seen != null && expected != null && !seen.equals(expected)
                && !"当前聊天".equals(seen) && !"当前聊天".equals(expected);
    }

    private AccessibilityNodeInfo chatRoot() {
        List<AccessibilityWindowInfo> windows = getWindows();
        AccessibilityNodeInfo chosen = null;
        boolean chosenHasInput = false;
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                if (!isTarget(string(root.getPackageName()))) { root.recycle(); continue; }
                AccessibilityNodeInfo edit = findEditable(root);
                boolean hasInput = edit != null;
                if (edit != null) edit.recycle();
                if (chosen == null || (hasInput && !chosenHasInput) || (hasInput == chosenHasInput && window.getLayer() > 0 && !chosenHasInput)) {
                    if (chosen != null && chosen != root) chosen.recycle();
                    chosen = AccessibilityNodeInfo.obtain(root);
                    chosenHasInput = hasInput;
                }
                root.recycle();
            }
        } finally {
            for (AccessibilityWindowInfo window : windows) window.recycle();
        }
        if (chosen != null) return chosen;
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && isTarget(string(active.getPackageName()))) return active;
        if (active != null) active.recycle();
        return null;
    }

    private boolean owned(File image) {
        if (image == null || !image.isFile()) return false;
        try {
            String path = image.getCanonicalPath();
            return path.startsWith(getCacheDir().getCanonicalPath()) || path.startsWith(getFilesDir().getCanonicalPath());
        } catch (java.io.IOException error) { return false; }
    }

    private interface Match { boolean test(AccessibilityNodeInfo node); }
    private List<AccessibilityNodeInfo> find(AccessibilityNodeInfo root, Match match) {
        List<AccessibilityNodeInfo> found = new ArrayList<>();
        visit(root, match, found, 0, new int[]{0});
        return found;
    }
    private void visit(AccessibilityNodeInfo node, Match match, List<AccessibilityNodeInfo> result, int depth, int[] count) {
        visit(node, match, result, depth, count, true);
    }
    private void visit(AccessibilityNodeInfo node, Match match, List<AccessibilityNodeInfo> result, int depth, int[] count, boolean requireVisible) {
        if (node == null || depth > 35 || ++count[0] > 1500) return;
        if ((!requireVisible || node.isVisibleToUser()) && match.test(node)) result.add(AccessibilityNodeInfo.obtain(node));
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { visit(child, match, result, depth + 1, count, requireVisible); child.recycle(); }
        }
    }
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> found = new ArrayList<>();
        visit(root, node -> node.isEditable() && !node.isPassword(), found, 0, new int[]{0}, false);
        if (found.isEmpty()) return null;
        for (int i = 1; i < found.size(); i++) found.get(i).recycle();
        return found.get(0);
    }
    private AccessibilityNodeInfo unique(AccessibilityNodeInfo root, Match match) {
        List<AccessibilityNodeInfo> nodes = find(root, match);
        if (nodes.size() == 1) return nodes.get(0);
        for (AccessibilityNodeInfo node : nodes) node.recycle();
        return null;
    }
    private boolean hasLabel(AccessibilityNodeInfo root, String... labels) {
        List<AccessibilityNodeInfo> nodes = find(root, node -> matches(label(node), labels));
        boolean result = !nodes.isEmpty();
        for (AccessibilityNodeInfo node : nodes) node.recycle();
        return result;
    }
    private static boolean matches(String actual, String... labels) {
        for (String label : labels) if (actual.equals(label)) return true;
        return false;
    }
    private AccessibilityNodeInfo action(AccessibilityNodeInfo root, boolean lower, String... labels) {
        Rect window = new Rect(); root.getBoundsInScreen(window);
        List<AccessibilityNodeInfo> nodes = find(root, node -> {
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            return node.isEnabled() && matches(label(node), labels)
                    && (!lower || bounds.centerY() > window.top + window.height() / 2);
        });
        return prefer(nodes, true);
    }
    private AccessibilityNodeInfo albumNode(AccessibilityNodeInfo root, String album) {
        return prefer(find(root, node -> label(node).equals(album) || label(node).matches(java.util.regex.Pattern.quote(album) + "\\s*[（(]?1[)）]?")), true);
    }
    private AccessibilityNodeInfo imageChoice(AccessibilityNodeInfo root, String filename) {
        List<AccessibilityNodeInfo> named = find(root, node -> label(node).contains(filename));
        if (!named.isEmpty()) return prefer(named, true);
        List<AccessibilityNodeInfo> boxes = find(root, node -> node.isCheckable() && !node.isChecked()
                && !label(node).contains("原图") && !label(node).contains("全选"));
        // A folder with several photos is not this generated image. Only a single choice is safe.
        if (boxes.size() == 1) return boxes.get(0);
        // #region agent log
        Log.i("MahouDebug", "assist choices=" + boxes.size());
        // #endregion
        for (AccessibilityNodeInfo node : boxes) node.recycle();
        return null;
    }
    private AccessibilityNodeInfo prefer(List<AccessibilityNodeInfo> nodes, boolean lower) {
        AccessibilityNodeInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AccessibilityNodeInfo node : nodes) {
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            int score = (node.isClickable() ? 100000 : 0) + bounds.width() * bounds.height() + (lower ? bounds.centerY() : 0);
            if (best == null || score > bestScore) {
                if (best != null) best.recycle();
                best = node;
                bestScore = score;
            } else node.recycle();
        }
        return best;
    }
    private String conversation(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo input = findEditable(root);
        if (input == null) return null;
        input.recycle();
        Rect bounds = new Rect(); root.getBoundsInScreen(bounds);
        List<AccessibilityNodeInfo> titles = find(root, node -> {
            String text = string(node.getText()).trim();
            if (text.isEmpty() || text.length() > 120 || banner(text)) return false;
            Rect rect = new Rect(); node.getBoundsInScreen(rect);
            return rect.centerY() < bounds.top + dp(150)
                    && rect.centerX() > bounds.left + dp(36) && rect.centerX() < bounds.right - dp(36)
                    && rect.width() > dp(48);
        });
        AccessibilityNodeInfo best = null;
        int bestTop = Integer.MAX_VALUE;
        boolean bestIsTitle = false;
        for (AccessibilityNodeInfo title : titles) {
            Rect rect = new Rect(); title.getBoundsInScreen(rect);
            boolean titleId = string(title.getViewIdResourceName()).contains("title");
            if (best == null || (titleId && !bestIsTitle) || (titleId == bestIsTitle && rect.top < bestTop)) {
                if (best != null) best.recycle();
                best = title;
                bestTop = rect.top;
                bestIsTitle = titleId;
            } else title.recycle();
        }
        if (best == null) return "当前聊天";
        String result = string(best.getText()).trim();
        best.recycle();
        return result.isEmpty() ? "当前聊天" : result;
    }

    private static boolean banner(String text) {
        if (matches(text, "QQ", "TIM", "消息", "联系人", "搜索", "返回", "群聊", "完成", "发送")) return true;
        if (text.matches("[0-9:： .%]+")) return true;
        return text.contains("群待办") || text.contains("公告") || text.contains("投票") || text.contains("|");
    }
    private static String label(AccessibilityNodeInfo node) {
        String text = string(node.getText()).trim();
        return text.isEmpty() ? string(node.getContentDescription()).trim() : text;
    }
    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }
    private boolean press(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (click(node)) return true;
        if (Build.VERSION.SDK_INT < 24 || rect.width() < 8 || rect.height() < 8) return false;
        Path path = new Path();
        path.moveTo(rect.centerX(), rect.centerY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60)).build();
        boolean sent = dispatchGesture(gesture, null, null);
        // #region agent log
        Log.i("MahouDebug", "assist gesture=" + sent);
        // #endregion
        return sent;
    }
    private static boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        try {
            for (int i = 0; i < 4 && current != null; i++) {
                if (current.isEnabled() && current.isClickable()) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo parent = current.getParent(); current.recycle(); current = parent;
            }
            return false;
        } finally { if (current != null) current.recycle(); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void finish(String state, String message, boolean sent) {
        SendSession old = session;
        if (old == null) return;
        session = null;
        main.removeCallbacks(advance);
        // #region agent log
        Log.i("MahouDebug", "assist finish state=" + state + " sent=" + sent);
        // #endregion
        report(state, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (old.media != null) {
            Uri media = old.media;
            if (sent && !closed) main.postDelayed(() -> worker.execute(() -> deleteMedia(media)), 120000);
            else worker.execute(() -> deleteMedia(media));
        }
    }
    private void report(String state, String message) {
        try {
            String value = new JSONObject().put("state", state).put("message", message).toString();
            sendBroadcast(new Intent(ACTION_RESULT).setPackage(getPackageName()).putExtra("result", value));
        } catch (Exception ignored) {}
    }
    private void deleteMedia(Uri uri) {
        if (!"content".equals(uri.getScheme()) || !"media".equals(uri.getAuthority())) return;
        try {
            getContentResolver().delete(uri, null, null);
            getSharedPreferences(STORE, MODE_PRIVATE).edit().remove(uri.toString()).apply();
        } catch (Exception ignored) {}
    }
}
