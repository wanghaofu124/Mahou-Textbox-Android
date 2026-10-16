package com.mahou.textbox;

/** Saved alongside the ActivityResultRegistry; configuration changes are not share returns. */
final class ShareProxyLifecycle {
    private boolean launched, completed;

    ShareProxyLifecycle(boolean launched, boolean completed) {
        this.launched = launched;
        this.completed = completed;
    }

    boolean beginLaunch() {
        if (launched || completed) return false;
        launched = true;
        return true;
    }

    boolean complete() {
        if (completed) return false;
        completed = true;
        return true;
    }

    boolean isLaunched() { return launched; }
    boolean isCompleted() { return completed; }
}
