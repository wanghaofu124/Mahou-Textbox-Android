package com.mahou.textbox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native port of the mobile.js export canvas: background, role, dialogue text and role name. */
final class StickerRenderer {
    private static final int CANVAS_WIDTH = 2560;
    private static final int CANVAS_HEIGHT = 834;
    private static final int ROLE_TOP = 134;
    private static final int TEXT_LEFT = 728;
    private static final int TEXT_TOP = 355;
    private static final int TEXT_RIGHT = 2339;
    private static final int TEXT_BOTTOM = 800;
    private static final int MIN_FONT_SIZE = 12;
    private static final int MAX_FONT_SIZE = 145;
    private static final int BUILT_IN_BACKGROUND_COUNT = 16;
    private static final int MAX_NAME_BLOCKS = 32;
    private static final int BASE_COLOR = 0xFF111218;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BRACKET_COLOR = 0xFF89B1FB;
    private static final int TEXT_SHADOW = 0xD1000000;
    private static final int FLAGS = Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG
            | Paint.LINEAR_TEXT_FLAG | Paint.FILTER_BITMAP_FLAG;

    private static final class NamePart {
        final String text;
        final int x;
        final int y;
        final int size;
        final int color;

        NamePart(String text, int x, int y, int size, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
        }
    }

    private static final class Role {
        final String key;
        final int emotions;
        final int color;
        final NamePart[] nameParts;

        Role(String key, int emotions, int color, NamePart... nameParts) {
            this.key = key;
            this.emotions = emotions;
            this.color = color;
            this.nameParts = nameParts;
        }
    }

    // Same order as the character table in mobile.js; index 2 (sherri) is the fallback role.
    private static final Role[] ROLES = {
            new Role("ema", 8, 0xFFFD91AF,
                    new NamePart("樱", 759, 73, 186, 0xFFFD91AF),
                    new NamePart("羽", 949, 175, 92, WHITE),
                    new NamePart("艾", 1039, 117, 147, WHITE),
                    new NamePart("玛", 1183, 175, 92, WHITE)),
            new Role("hiro", 6, 0xFFEF4F54,
                    new NamePart("二", 759, 63, 196, 0xFFEF4F54),
                    new NamePart("阶堂", 955, 175, 92, WHITE),
                    new NamePart("希", 1143, 110, 147, WHITE),
                    new NamePart("罗", 1283, 175, 92, WHITE)),
            new Role("sherri", 7, 0xFF89B1FB,
                    new NamePart("橘", 759, 73, 186, 0xFF89B1FB),
                    new NamePart("雪", 943, 110, 147, WHITE),
                    new NamePart("莉", 1093, 175, 92, WHITE)),
            new Role("hanna", 5, 0xFFA9C71E,
                    new NamePart("远", 759, 73, 186, 0xFFA9C71E),
                    new NamePart("野", 945, 175, 92, WHITE),
                    new NamePart("汉", 1042, 117, 147, WHITE),
                    new NamePart("娜", 1186, 175, 92, WHITE)),
            new Role("anan", 9, 0xFF9F91FB,
                    new NamePart("夏", 759, 73, 186, 0xFF9F91FB),
                    new NamePart("目", 949, 175, 92, WHITE),
                    new NamePart("安", 1039, 117, 147, WHITE),
                    new NamePart("安", 1183, 175, 92, WHITE)),
            new Role("yuki", 18, 0xFFC3D1E7,
                    new NamePart("月", 759, 63, 196, 0xFFC3D1E7),
                    new NamePart("代", 948, 175, 92, WHITE),
                    new NamePart("雪", 1053, 117, 147, WHITE)),
            new Role("meruru", 6, 0xFFE3B9AF,
                    new NamePart("冰", 759, 73, 186, 0xFFE3B9AF),
                    new NamePart("上", 945, 175, 92, WHITE),
                    new NamePart("梅", 1042, 117, 147, WHITE),
                    new NamePart("露露", 1186, 175, 92, WHITE)),
            new Role("noa", 6, 0xFF68DFE7,
                    new NamePart("城", 759, 73, 186, 0xFF68DFE7),
                    new NamePart("崎", 945, 175, 92, WHITE),
                    new NamePart("诺", 1042, 117, 147, WHITE),
                    new NamePart("亚", 1186, 175, 92, WHITE)),
            new Role("reia", 7, 0xFFFDB158,
                    new NamePart("莲", 759, 73, 186, 0xFFFDB158),
                    new NamePart("见", 945, 175, 92, WHITE),
                    new NamePart("蕾", 1042, 117, 147, WHITE),
                    new NamePart("雅", 1186, 175, 92, WHITE)),
            new Role("miria", 4, 0xFFEBCF8B,
                    new NamePart("佐", 759, 73, 186, 0xFFEBCF8B),
                    new NamePart("伯", 945, 175, 92, WHITE),
                    new NamePart("米", 1042, 117, 147, WHITE),
                    new NamePart("莉亚", 1186, 175, 92, WHITE)),
            new Role("nanoka", 5, 0xFF838F93,
                    new NamePart("黑", 759, 63, 196, 0xFF838F93),
                    new NamePart("部", 955, 175, 92, WHITE),
                    new NamePart("奈", 1053, 117, 147, WHITE),
                    new NamePart("叶香", 1197, 175, 92, WHITE)),
            new Role("mago", 5, 0xFFB97CEB,
                    new NamePart("宝", 759, 73, 186, 0xFFB97CEB),
                    new NamePart("生", 945, 175, 92, WHITE),
                    new NamePart("玛", 1042, 117, 147, WHITE),
                    new NamePart("格", 1186, 175, 92, WHITE)),
            new Role("alisa", 6, 0xFFEB4B3C,
                    new NamePart("紫", 759, 73, 186, 0xFFEB4B3C),
                    new NamePart("藤", 945, 175, 92, WHITE),
                    new NamePart("亚", 1042, 117, 147, WHITE),
                    new NamePart("里沙", 1186, 175, 92, WHITE)),
            new Role("coco", 5, 0xFFFB724E,
                    new NamePart("泽", 759, 73, 186, 0xFFFB724E),
                    new NamePart("渡", 945, 175, 92, WHITE),
                    new NamePart("可", 1042, 117, 147, WHITE),
                    new NamePart("可", 1186, 175, 92, WHITE))
    };

    private static final Paint textPaint = new Paint(FLAGS);
    private static final Paint bitmapPaint = new Paint(FLAGS);
    private static Typeface typeface;
    // The key carries the sha256 of every custom asset in use, so an edited asset never hits a stale composite.
    private static String cacheKey = "";
    private static Bitmap cache;

    private StickerRenderer() {}

    /** Draws one image at params.width. The caller owns and must recycle the returned bitmap. */
    static synchronized Bitmap render(Context context, RenderParams params) throws IOException, JSONException {
        if (typeface == null) {
            typeface = Typeface.createFromAsset(context.getAssets(), "font3.ttf");
            textPaint.setTypeface(typeface);
        }
        String backgroundKey = normalizeBackgroundKey(params.backgroundKey);
        String backgroundPath = builtInBackgroundPath(backgroundKey);
        Role role = findRole(params.characterKey);
        JSONObject manifest = backgroundPath == null || role == null
                ? new JSONObject(AssetStore.listAssets(context)) : null;
        String version = "";
        if (backgroundPath == null) {
            JSONObject item = findItem(manifest.getJSONArray("backgrounds"), backgroundKey);
            if (item == null) {
                backgroundKey = "builtin-1";
                backgroundPath = builtInBackgroundPath(backgroundKey);
            } else {
                backgroundPath = item.getString("path");
                version += item.getString("sha256");
            }
        }
        JSONObject custom = null;
        if (role == null) {
            custom = findItem(manifest.getJSONArray("characters"), params.characterKey);
            if (custom == null) role = ROLES[2];
        }
        int emotion = Integer.parseInt(params.emotion);
        String characterKey;
        String rolePath;
        if (role != null) {
            emotion = Math.min(emotion, role.emotions);
            characterKey = role.key;
            rolePath = role.key + "/" + role.key + " (" + emotion + ").png";
        } else {
            List<JSONObject> emotions = ownerEmotions(manifest.getJSONArray("emotions"), custom.getString("id"));
            emotion = Math.min(emotion, Math.max(1, emotions.size()));
            characterKey = custom.getString("id");
            JSONObject image = emotions.isEmpty() ? custom : emotions.get(emotion - 1);
            rolePath = image.getString("path");
            version += custom.getString("sha256") + image.getString("sha256");
        }

        int width = params.width;
        int height = (int) Math.round((double) CANVAS_HEIGHT * width / CANVAS_WIDTH);
        String key = backgroundKey + "|" + characterKey + "|" + emotion + "|" + width + "|" + version;
        if (!key.equals(cacheKey)) {
            Bitmap composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(composite);
            canvas.scale((float) width / CANVAS_WIDTH, (float) width / CANVAS_WIDTH);
            canvas.drawColor(BASE_COLOR);
            Bitmap backgroundImage = decode(context, backgroundPath);
            drawImage(canvas, backgroundImage, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            backgroundImage.recycle();
            Bitmap roleImage = decode(context, rolePath);
            if (custom == null) {
                drawImage(canvas, roleImage, 0, ROLE_TOP, roleImage.getWidth(), roleImage.getHeight());
            } else {
                JSONObject fit = custom.getJSONObject("fit");
                drawImage(canvas, roleImage, fit.getDouble("x"), fit.getDouble("y"),
                        fit.getDouble("w"), fit.getDouble("h"));
            }
            roleImage.recycle();
            if (cache != null) cache.recycle();
            cache = composite;
            cacheKey = key;
        }

        Bitmap output = cache.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(output);
        canvas.scale((float) width / CANVAS_WIDTH, (float) width / CANVAS_WIDTH);
        drawDialogText(canvas, params.text, role != null && "anan".equals(role.key) ? role.color : BRACKET_COLOR);
        if (role != null) {
            for (NamePart part : role.nameParts) {
                drawName(canvas, part.text, part.x, part.y, part.size, part.color, 2);
            }
        } else {
            JSONArray blocks = custom.getJSONArray("nameBlocks");
            for (int i = 0; i < Math.min(MAX_NAME_BLOCKS, blocks.length()); i++) {
                JSONObject block = blocks.getJSONObject(i);
                drawName(canvas, block.getString("text"), block.getDouble("x"), block.getDouble("y"),
                        block.getDouble("size"), Color.parseColor(block.getString("color")), 3);
            }
        }
        return output;
    }

    private static String normalizeBackgroundKey(String value) {
        if (value.isEmpty()) return "builtin-1";
        return value.matches("\\d+") ? "builtin-" + value : value;
    }

    private static String builtInBackgroundPath(String key) {
        for (int number = 1; number <= BUILT_IN_BACKGROUND_COUNT; number++) {
            if (key.equals("builtin-" + number)) return "background/c" + number + ".png";
        }
        return null;
    }

    private static Role findRole(String key) {
        for (Role role : ROLES) {
            if (role.key.equals(key)) return role;
        }
        return null;
    }

    private static JSONObject findItem(JSONArray items, String id) throws JSONException {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (id.equals(item.getString("id"))) return item;
        }
        return null;
    }

    private static List<JSONObject> ownerEmotions(JSONArray items, String ownerId) throws JSONException {
        List<JSONObject> emotions = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (ownerId.equals(item.getString("ownerId"))) emotions.add(item);
        }
        Collections.sort(emotions, (a, b) -> Long.compare(a.optLong("order"), b.optLong("order")));
        return emotions;
    }

    /** Absolute paths come from AssetStore (custom assets); anything else is an APK asset. */
    private static Bitmap decode(Context context, String path) throws IOException {
        Bitmap bitmap;
        if (path.startsWith("/")) {
            bitmap = BitmapFactory.decodeFile(path);
        } else {
            try (InputStream input = context.getAssets().open(path)) {
                bitmap = BitmapFactory.decodeStream(input);
            }
        }
        if (bitmap == null) throw new IOException("素材无法加载：" + path);
        return bitmap;
    }

    private static void drawImage(Canvas canvas, Bitmap bitmap, double x, double y, double w, double h) {
        canvas.drawBitmap(bitmap, null, new RectF((float) x, (float) y, (float) (x + w), (float) (y + h)), bitmapPaint);
    }

    private static void drawDialogText(Canvas canvas, String value, int bracketColor) {
        String text = trim(value);
        if (text.isEmpty()) text = " ";
        int maxWidth = TEXT_RIGHT - TEXT_LEFT;
        int maxHeight = TEXT_BOTTOM - TEXT_TOP;
        int size = fitFontSize(text, maxWidth, maxHeight);
        List<String> lines = wrapText(text, size, maxWidth);
        int lineHeight = lineHeight(size);
        float y = TEXT_TOP;
        for (String line : lines) {
            drawSegmentedText(canvas, line, TEXT_LEFT, y, bracketColor);
            y += lineHeight;
            if (y > TEXT_BOTTOM) break;
        }
    }

    private static int lineHeight(int size) {
        return (int) Math.round(size * 1.22);
    }

    private static int fitFontSize(String text, int maxWidth, int maxHeight) {
        int low = MIN_FONT_SIZE;
        int high = MAX_FONT_SIZE;
        int best = MIN_FONT_SIZE;
        while (low <= high) {
            int mid = (low + high) / 2;
            List<String> lines = wrapText(text, mid, maxWidth);
            int lineHeight = lineHeight(mid);
            float width = 0;
            for (String line : lines) width = Math.max(width, textPaint.measureText(line));
            if (width <= maxWidth && Math.max(lineHeight, lines.size() * lineHeight) <= maxHeight) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static List<String> wrapText(String text, int size, float maxWidth) {
        textPaint.setTextSize(size);
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\r?\\n", -1)) {
            wrapParagraph(paragraph, maxWidth, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, float maxWidth, List<String> lines) {
        List<String> units = new ArrayList<>();
        boolean hasSpace = paragraph.indexOf(' ') >= 0;
        if (hasSpace) {
            int start = 0;
            for (int i = 1; i <= paragraph.length(); i++) {
                if (i == paragraph.length() || isSpace(paragraph.charAt(i)) != isSpace(paragraph.charAt(start))) {
                    units.add(paragraph.substring(start, i));
                    start = i;
                }
            }
        } else {
            for (int i = 0; i < paragraph.length(); ) {
                int end = i + Character.charCount(paragraph.codePointAt(i));
                units.add(paragraph.substring(i, end));
                i = end;
            }
        }
        String buffer = "";
        int before = lines.size();
        for (String unit : units) {
            String trial = buffer + unit;
            if (textPaint.measureText(trial) <= maxWidth) {
                buffer = trial;
                continue;
            }
            if (!trim(buffer).isEmpty() || !hasSpace) lines.add(trimEnd(buffer));
            buffer = trimStart(unit);
            while (textPaint.measureText(buffer) > maxWidth && buffer.length() > 1) {
                String slice = "";
                for (int i = 0; i < buffer.length(); i += Character.charCount(buffer.codePointAt(i))) {
                    String next = slice + buffer.substring(i, i + Character.charCount(buffer.codePointAt(i)));
                    if (textPaint.measureText(next) > maxWidth) break;
                    slice = next;
                }
                lines.add(slice);
                buffer = buffer.substring(slice.length());
            }
        }
        if (!buffer.isEmpty()) lines.add(trimEnd(buffer));
        if (lines.size() == before) lines.add("");
    }

    private static void drawSegmentedText(Canvas canvas, String line, float x, float y, int bracketColor) {
        // Needs device calibration: mobile.js uses textBaseline "top"; this assumes ascent is the em top.
        float baseline = y - textPaint.ascent();
        boolean inBracket = false;
        float cursor = x;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            String character = line.substring(i, i + Character.charCount(codePoint));
            i += character.length();
            if (codePoint == '[' || codePoint == '【') inBracket = true;
            textPaint.setColor(TEXT_SHADOW);
            canvas.drawText(character, cursor + 4, baseline + 4, textPaint);
            textPaint.setColor(inBracket ? bracketColor : WHITE);
            canvas.drawText(character, cursor, baseline, textPaint);
            cursor += textPaint.measureText(character);
            if (codePoint == ']' || codePoint == '】') inBracket = false;
        }
    }

    private static void drawName(Canvas canvas, String text, double x, double y, double size, int color, int shadow) {
        if (text.isEmpty()) return;
        textPaint.setTextSize((float) size);
        // Needs device calibration, same baseline assumption as drawSegmentedText.
        float baseline = (float) y - textPaint.ascent();
        textPaint.setColor(Color.BLACK);
        canvas.drawText(text, (float) x + shadow, baseline + shadow, textPaint);
        textPaint.setColor(color);
        canvas.drawText(text, (float) x, baseline, textPaint);
    }

    // JavaScript's whitespace set, which String.trim() and Character.isWhitespace() do not match.
    private static boolean isSpace(char c) {
        return c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == 0xFEFF || Character.isSpaceChar(c);
    }

    private static String trimStart(String value) {
        int start = 0;
        while (start < value.length() && isSpace(value.charAt(start))) start++;
        return value.substring(start);
    }

    private static String trimEnd(String value) {
        int end = value.length();
        while (end > 0 && isSpace(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private static String trim(String value) {
        return trimStart(trimEnd(value));
    }
}
