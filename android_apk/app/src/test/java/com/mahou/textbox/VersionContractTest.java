package com.mahou.textbox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VersionContractTest {
    @Test
    public void publicReleaseVersionIsPinned() {
        assertEquals("0.7.27", BuildConfig.VERSION_NAME);
        assertEquals(58, BuildConfig.VERSION_CODE);
    }
}
