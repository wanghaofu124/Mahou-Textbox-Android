package com.mahou.textbox;

/** Main-thread request ownership: an expired export can never launch a later share. */
final class PopupExportGuard {
    private enum Stage { IDLE, EXPORTING, WRITING, EXTERNAL }
    private long sequence;
    private long request;
    private Stage stage = Stage.IDLE;

    long beginExport() {
        if (stage != Stage.IDLE) return 0;
        request = ++sequence;
        stage = Stage.EXPORTING;
        return request;
    }

    long beginDirectShare() {
        long id = beginExport();
        if (id != 0) acceptExport(id);
        return id;
    }

    boolean acceptExport(long id) {
        if (!matches(id, Stage.EXPORTING)) return false;
        stage = Stage.WRITING;
        return true;
    }

    boolean beginExternal(long id) {
        if (!matches(id, Stage.WRITING)) return false;
        stage = Stage.EXTERNAL;
        return true;
    }

    boolean fail(long id) {
        if (id == 0 || request != id || stage == Stage.IDLE || stage == Stage.EXTERNAL) return false;
        stage = Stage.IDLE;
        return true;
    }

    boolean finishExternal(long id) {
        if (!matches(id, Stage.EXTERNAL)) return false;
        stage = Stage.IDLE;
        return true;
    }

    boolean isWriting(long id) { return matches(id, Stage.WRITING); }
    void cancel() { ++sequence; request = 0; stage = Stage.IDLE; }

    boolean resumeExternal(long id) {
        if (id <= 0 || stage != Stage.IDLE) return false;
        sequence = Math.max(sequence, id);
        request = id;
        stage = Stage.EXTERNAL;
        return true;
    }

    private boolean matches(long id, Stage expected) {
        return id != 0 && request == id && stage == expected;
    }
}
