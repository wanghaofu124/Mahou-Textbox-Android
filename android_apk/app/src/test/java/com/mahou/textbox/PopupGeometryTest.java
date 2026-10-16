package com.mahou.textbox;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Random;

public class PopupGeometryTest {
    private final PopupGeometry.Bounds portrait = new PopupGeometry.Bounds(0, 24, 412, 868);

    @Test public void compactDefaultAndMinimumLeaveControlsReachable() {
        PopupGeometry.Window normal = PopupGeometry.clamp(window(12, 64, 360, 400), portrait, 280, 300);
        assertEquals(360, normal.width);
        assertEquals(400, normal.height);
        PopupGeometry.Window tiny = PopupGeometry.clamp(window(10, 40, 10, 10), portrait, 280, 300);
        assertEquals(280, tiny.width);
        assertEquals(300, tiny.height);
    }

    @Test public void smallerThanMinimumScreenUsesAvailableSpaceNotOffscreenChrome() {
        PopupGeometry.Bounds keyboard = new PopupGeometry.Bounds(20, 30, 260, 270);
        PopupGeometry.Window result = PopupGeometry.clamp(window(500, 600, 360, 400), keyboard, 280, 300);
        assertEquals(20, result.x);
        assertEquals(30, result.y);
        assertEquals(240, result.width);
        assertEquals(240, result.height);
    }

    @Test public void resizeAtEdgesDoesNotTranslatePanel() {
        PopupGeometry.Window result = PopupGeometry.resize(window(52, 400, 360, 400), 1000, 1000,
                portrait, 280, 300);
        assertEquals(52, result.x);
        assertEquals(400, result.y);
        assertEquals(360, result.width);
        assertEquals(468, result.height);
    }

    @Test public void releaseSnapsOnlyWithinSixteenPixels() {
        PopupGeometry.Window middle = PopupGeometry.snap(window(25, 160, 280, 300), portrait, 16);
        assertEquals(25, middle.x);
        assertEquals(160, middle.y);
        PopupGeometry.Window near = PopupGeometry.snap(window(130, 25, 280, 300), portrait, 16);
        assertEquals(132, near.x);
        assertEquals(24, near.y);
    }

    @Test public void oldScreenWideDockIsMigratedOnlyOnce() {
        PopupGeometry.Window legacy = window(30, 80, 900, 800);
        PopupGeometry.Window converted = PopupGeometry.migrateLegacy(0, legacy, 360, 400);
        assertEquals(360, converted.width);
        assertEquals(400, converted.height);
        assertEquals(30, converted.x);
        assertSame(legacy, PopupGeometry.migrateLegacy(1, legacy, 360, 400));
    }

    @Test public void randomizedRotationAndInsetClampsAlwaysRemainWithinSafeBounds() {
        Random random = new Random(501);
        for (int i = 0; i < 2000; i++) {
            int left = random.nextInt(50), top = random.nextInt(60);
            PopupGeometry.Bounds bounds = new PopupGeometry.Bounds(left, top,
                    left + 1 + random.nextInt(1400), top + 1 + random.nextInt(1400));
            PopupGeometry.Window result = PopupGeometry.clamp(window(random.nextInt(2000) - 500,
                    random.nextInt(2000) - 500, random.nextInt(2000), random.nextInt(2000)), bounds, 280, 300);
            assertTrue(result.x >= bounds.left && result.y >= bounds.top);
            assertTrue(result.x + result.width <= bounds.right && result.y + result.height <= bounds.bottom);
            assertTrue(result.width > 0 && result.height > 0);
        }
    }

    private PopupGeometry.Window window(int x, int y, int width, int height) {
        return new PopupGeometry.Window(x, y, width, height);
    }
}
