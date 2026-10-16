package com.mahou.textbox;

/** Persistent full/mini placement is never replaced by transient editor or IME geometry. */
final class PopupPlacement {
    private final int minWidth, minHeight, miniSize, editorMargin;
    private PopupGeometry.Window full, mini, temporary;
    private PopupGeometry.Bounds safe, usable;
    private boolean minimized, editing, expanded;

    PopupPlacement(PopupGeometry.Window full, PopupGeometry.Window mini, boolean minimized,
            int minWidth, int minHeight, int miniSize, int editorMargin) {
        this.full = full;
        this.mini = mini;
        this.minimized = minimized;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.miniSize = miniSize;
        this.editorMargin = editorMargin;
    }

    void setBounds(PopupGeometry.Bounds safe, PopupGeometry.Bounds usable) {
        boolean usableChanged = !usable.sameAs(this.usable);
        this.safe = safe;
        this.usable = usable;
        full = PopupGeometry.clamp(full, safe, minWidth, minHeight);
        mini = PopupGeometry.clamp(mini, safe, miniSize, miniSize);
        if (expanded && usableChanged) temporary = PopupGeometry.expanded(usable, editorMargin);
    }

    PopupGeometry.Window visible() {
        if (minimized) return PopupGeometry.clamp(mini, usable, miniSize, miniSize);
        PopupGeometry.Window window = temporary == null ? full : temporary;
        if (editing && !expanded) {
            window = new PopupGeometry.Window(window.x, window.y, window.width,
                    Math.min(window.height, minHeight));
        }
        return PopupGeometry.clamp(window, usable, minWidth, minHeight);
    }

    void move(PopupGeometry.Window window) {
        if (minimized) mini = PopupGeometry.clamp(window, usable, miniSize, miniSize);
        else if (editing || expanded) temporary = PopupGeometry.clamp(window, usable, minWidth, minHeight);
        else full = PopupGeometry.clamp(window, usable, minWidth, minHeight);
    }

    void dragTo(int x, int y) {
        PopupGeometry.Window moved = PopupGeometry.clamp(visible().moveTo(x, y), usable,
                minimized ? miniSize : minWidth, minimized ? miniSize : minHeight);
        if (minimized) mini = new PopupGeometry.Window(moved.x, moved.y, mini.width, mini.height);
        else if (editing || expanded) temporary = moved;
        else full = new PopupGeometry.Window(moved.x, moved.y, full.width, full.height);
    }

    void setEditing(boolean value) {
        if (editing == value) return;
        editing = value;
        if (!expanded) temporary = null;
    }

    void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        temporary = value ? PopupGeometry.expanded(usable, editorMargin) : null;
    }

    void setMinimized(boolean value) {
        minimized = value;
        if (value) {
            editing = false;
            if (!expanded) temporary = null;
        }
    }

    PopupGeometry.Window full() { return full; }
    PopupGeometry.Window mini() { return mini; }
    PopupGeometry.Bounds usable() { return usable; }
    boolean isMinimized() { return minimized; }
    boolean isExpanded() { return expanded; }
    boolean isEditing() { return editing; }
}
