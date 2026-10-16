package com.mahou.textbox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VersionContractTest {
    @Test
    public void publicReleaseVersionIsPinned() {
        assertEquals("0.7.25", BuildConfig.VERSION_NAME);
        assertEquals(56, BuildConfig.VERSION_CODE);
    }
}
