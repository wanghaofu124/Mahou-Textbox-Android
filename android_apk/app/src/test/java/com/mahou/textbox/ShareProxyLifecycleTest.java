package com.mahou.textbox;

import static org.junit.Assert.*;
import org.junit.Test;

public class ShareProxyLifecycleTest {
    @Test public void rotationAndProcessRecreationNeverRelaunchPendingShare() {
        ShareProxyLifecycle original = new ShareProxyLifecycle(false, false);
        assertTrue(original.beginLaunch());
        ShareProxyLifecycle recreated = new ShareProxyLifecycle(original.isLaunched(), original.isCompleted());
        assertFalse(recreated.beginLaunch());
        assertFalse(recreated.isCompleted());
        assertTrue(recreated.complete());
    }

    @Test public void resultCancellationAndFailureCompleteExactlyOnce() {
        ShareProxyLifecycle lifecycle = new ShareProxyLifecycle(false, false);
        lifecycle.beginLaunch();
        assertTrue(lifecycle.complete());
        assertFalse(lifecycle.complete());
        assertFalse(lifecycle.beginLaunch());
    }

    @Test public void completedProxyRemainsCompletedAfterRecreation() {
        ShareProxyLifecycle lifecycle = new ShareProxyLifecycle(true, true);
        assertFalse(lifecycle.beginLaunch());
        assertFalse(lifecycle.complete());
    }
}
