package com.mahou.textbox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VersionContractTest {
    @Test
    public void publicReleaseVersionIsPinned() {
        assertEquals("0.7.26", BuildConfig.VERSION_NAME);
        assertEquals(57, BuildConfig.VERSION_CODE);
    }
}
