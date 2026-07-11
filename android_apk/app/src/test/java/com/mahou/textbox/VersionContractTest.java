package com.mahou.textbox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VersionContractTest {
    @Test
    public void publicReleaseVersionIsPinned() {
        assertEquals("0.4.0", BuildConfig.VERSION_NAME);
        assertEquals(23, BuildConfig.VERSION_CODE);
    }
}
