package com.mahou.textbox;

import static org.junit.Assert.*;
import org.junit.Test;

public class PopupPlacementTest {
    private final PopupGeometry.Bounds safe = new PopupGeometry.Bounds(0, 24, 412, 868);
    private final PopupGeometry.Bounds keyboard = new PopupGeometry.Bounds(0, 24, 412, 520);

    private PopupPlacement placement() {
        PopupPlacement placement = new PopupPlacement(new PopupGeometry.Window(20, 400, 360, 400),
                new PopupGeometry.Window(330, 100, 56, 56), false, 280, 300, 56, 8);
        placement.setBounds(safe, safe);
        return placement;
    }

    @Test public void fullAndMiniCoordinatesAreIndependent() {
        PopupPlacement placement = placement();
        placement.setMinimized(true);
        placement.move(new PopupGeometry.Window(5, 70, 56, 56));
        placement.setMinimized(false);
        assertEquals(20, placement.visible().x);
        assertEquals(400, placement.visible().y);
        placement.move(new PopupGeometry.Window(40, 80, 360, 400));
        placement.setMinimized(true);
        assertEquals(5, placement.visible().x);
        assertEquals(70, placement.visible().y);
    }

    @Test public void imeMoveAndResizeNeverOverwriteSavedFullGeometry() {
        PopupPlacement placement = placement();
        placement.setEditing(true);
        placement.setBounds(safe, keyboard);
        assertTrue(placement.visible().y + placement.visible().height <= 520);
        placement.move(new PopupGeometry.Window(0, 30, 300, 320));
        assertEquals(400, placement.full().y);
        assertEquals(360, placement.full().width);
        placement.setEditing(false);
        placement.setBounds(safe, safe);
        assertEquals(20, placement.visible().x);
        assertEquals(400, placement.visible().y);
        assertEquals(400, placement.visible().height);
    }

    @Test public void closingLastModalWhileImeVisibleRestoresOnlyToKeyboardSafeSize() {
        PopupPlacement placement = placement();
        placement.setExpanded(true);
        placement.setEditing(true);
        placement.setBounds(safe, keyboard);
        assertEquals(396, placement.visible().width);
        assertEquals(480, placement.visible().height);
        placement.setExpanded(false);
        assertEquals(360, placement.visible().width);
        assertTrue(placement.visible().y + placement.visible().height <= 520);
        assertEquals(400, placement.full().y);
        placement.setEditing(false);
        placement.setBounds(safe, safe);
        assertEquals(400, placement.visible().y);
    }

    @Test public void repeatedModalNotificationsDoNotReplaceRestoreGeometry() {
        PopupPlacement placement = placement();
        placement.setExpanded(true);
        placement.setExpanded(true);
        placement.move(new PopupGeometry.Window(1, 24, 400, 700));
        placement.setExpanded(false);
        placement.setExpanded(false);
        assertEquals(20, placement.visible().x);
        assertEquals(400, placement.visible().height);
    }

    @Test public void miniImeClampDoesNotOverwriteMiniSavedPosition() {
        PopupPlacement placement = placement();
        placement.setMinimized(true);
        placement.move(new PopupGeometry.Window(300, 790, 56, 56));
        placement.setBounds(safe, keyboard);
        assertEquals(464, placement.visible().y);
        assertEquals(790, placement.mini().y);
        placement.setBounds(safe, safe);
        assertEquals(790, placement.visible().y);
    }

    @Test public void rotationClampsBothModesAndModalRestoreGeometry() {
        PopupPlacement placement = placement();
        placement.setExpanded(true);
        PopupGeometry.Bounds landscape = new PopupGeometry.Bounds(32, 24, 868, 388);
        placement.setBounds(landscape, landscape);
        assertEquals(820, placement.visible().width);
        placement.setExpanded(false);
        assertTrue(placement.visible().y + placement.visible().height <= 388);
        placement.setMinimized(true);
        assertTrue(placement.visible().x >= 32);
        assertEquals(56, placement.visible().width);
    }

    @Test public void minimizingOpenModalKeepsItsTransientExpansionOnRestore() {
        PopupPlacement placement = placement();
        placement.setExpanded(true);
        placement.setMinimized(true);
        assertEquals(56, placement.visible().width);
        placement.setMinimized(false);
        assertEquals(396, placement.visible().width);
        placement.setExpanded(false);
        assertEquals(360, placement.visible().width);
    }

    @Test public void draggingPassivePanelAroundAnotherAppsImeDoesNotResizeSavedPanel() {
        PopupPlacement placement = placement();
        PopupGeometry.Bounds smallUsable = new PopupGeometry.Bounds(0, 24, 412, 324);
        placement.setBounds(safe, smallUsable);
        assertEquals(300, placement.visible().height);
        placement.dragTo(35, 24);
        assertEquals(400, placement.full().height);
        assertEquals(35, placement.full().x);
        placement.setBounds(safe, safe);
        assertEquals(400, placement.visible().height);
    }
}
