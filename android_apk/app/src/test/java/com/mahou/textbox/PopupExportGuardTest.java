package com.mahou.textbox;

import static org.junit.Assert.*;
import org.junit.Test;

public class PopupExportGuardTest {
    @Test public void duplicateCallbacksAndDuplicateShareRequestsAreRejected() {
        PopupExportGuard guard = new PopupExportGuard();
        long request = guard.beginExport();
        assertTrue(request > 0);
        assertEquals(0, guard.beginExport());
        assertTrue(guard.acceptExport(request));
        assertFalse(guard.acceptExport(request));
        assertEquals(0, guard.beginDirectShare());
        assertTrue(guard.beginExternal(request));
        assertFalse(guard.beginExternal(request));
        assertFalse(guard.fail(request));
        assertTrue(guard.finishExternal(request));
        assertFalse(guard.finishExternal(request));
    }

    @Test public void timedOutExportCannotCompleteOrFailNewRequest() {
        PopupExportGuard guard = new PopupExportGuard();
        long old = guard.beginExport();
        assertTrue(guard.fail(old));
        long next = guard.beginExport();
        assertFalse(guard.acceptExport(old));
        assertFalse(guard.fail(old));
        assertTrue(guard.acceptExport(next));
        assertTrue(guard.beginExternal(next));
    }

    @Test public void expiredWorkerAndCanceledServiceCannotLaunchShare() {
        PopupExportGuard guard = new PopupExportGuard();
        long request = guard.beginDirectShare();
        assertTrue(guard.isWriting(request));
        assertTrue(guard.fail(request));
        assertFalse(guard.isWriting(request));
        assertFalse(guard.beginExternal(request));
        long next = guard.beginDirectShare();
        guard.cancel();
        assertFalse(guard.beginExternal(next));
        assertFalse(guard.finishExternal(next));
        assertTrue(guard.beginDirectShare() > next);
    }

    @Test public void staleReturnDoesNotRestoreAnotherExternalShare() {
        PopupExportGuard guard = new PopupExportGuard();
        long old = guard.beginDirectShare();
        guard.beginExternal(old);
        guard.finishExternal(old);
        long next = guard.beginDirectShare();
        guard.beginExternal(next);
        assertFalse(guard.finishExternal(old));
        assertEquals(0, guard.beginExport());
        assertTrue(guard.finishExternal(next));
    }

    @Test public void serviceRecreationWaitsForMatchingActivityResult() {
        PopupExportGuard recreated = new PopupExportGuard();
        assertTrue(recreated.resumeExternal(23));
        assertEquals(0, recreated.beginExport());
        assertFalse(recreated.finishExternal(22));
        assertTrue(recreated.finishExternal(23));
        assertEquals(24, recreated.beginExport());
    }
}
