package com.mahou.textbox;

/** Screen-coordinate geometry, independent of Android's window/inset dispatch. */
final class PopupGeometry {
    static final class Bounds {
        final int left, top, right, bottom;

        Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left + 1, right);
            this.bottom = Math.max(top + 1, bottom);
        }

        int width() { return right - left; }
        int height() { return bottom - top; }

        boolean sameAs(Bounds other) {
            return other != null && left == other.left && top == other.top
                    && right == other.right && bottom == other.bottom;
        }
    }

    static final class Window {
        final int x, y, width, height;

        Window(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        Window moveTo(int x, int y) { return new Window(x, y, width, height); }
    }

    private PopupGeometry() {}

    static Window migrateLegacy(int layoutVersion, Window stored, int compactWidth, int compactHeight) {
        if (layoutVersion >= 1) return stored;
        return new Window(stored.x, stored.y, Math.min(stored.width, compactWidth),
                Math.min(stored.height, compactHeight));
    }

    static Window clamp(Window window, Bounds bounds, int minWidth, int minHeight) {
        int width = bounded(window.width, Math.min(minWidth, bounds.width()), bounds.width());
        int height = bounded(window.height, Math.min(minHeight, bounds.height()), bounds.height());
        return new Window(bounded(window.x, bounds.left, bounds.right - width),
                bounded(window.y, bounds.top, bounds.bottom - height), width, height);
    }

    static Window resize(Window start, int dx, int dy, Bounds bounds, int minWidth, int minHeight) {
        // Keep the top-left anchored. Resizing near an edge must not move the whole panel.
        Window anchor = clamp(start, bounds, minWidth, minHeight);
        int width = bounded(anchor.width + dx, Math.min(minWidth, bounds.right - anchor.x),
                bounds.right - anchor.x);
        int height = bounded(anchor.height + dy, Math.min(minHeight, bounds.bottom - anchor.y),
                bounds.bottom - anchor.y);
        return new Window(anchor.x, anchor.y, width, height);
    }

    static Window snap(Window window, Bounds bounds, int distance) {
        int x = nearEdge(window.x, bounds.left, bounds.right - window.width, distance);
        int y = nearEdge(window.y, bounds.top, bounds.bottom - window.height, distance);
        return window.moveTo(x, y);
    }

    static Window expanded(Bounds bounds, int margin) {
        int horizontal = Math.min(margin, Math.max(0, (bounds.width() - 1) / 2));
        int vertical = Math.min(margin, Math.max(0, (bounds.height() - 1) / 2));
        return new Window(bounds.left + horizontal, bounds.top + vertical,
                bounds.width() - horizontal * 2, bounds.height() - vertical * 2);
    }

    static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static int nearEdge(int value, int first, int last, int distance) {
        int firstDistance = Math.abs(value - first);
        int lastDistance = Math.abs(last - value);
        if (Math.min(firstDistance, lastDistance) > distance) return value;
        return firstDistance <= lastDistance ? first : last;
    }
}
