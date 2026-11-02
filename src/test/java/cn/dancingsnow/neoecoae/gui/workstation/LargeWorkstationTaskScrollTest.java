package cn.dancingsnow.neoecoae.gui.workstation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LargeWorkstationTaskScrollTest {
    @Test
    void wheelExposesPartialRowsAndPreservesFractionalInput() {
        var scroll = scroll(512);
        scroll.scrollBy(0.25);
        scroll.scrollBy(0.25);
        scroll.advance(1.0 / 60);
        assertTrue(scroll.offset() > 0 && scroll.offset() < 13);
        assertNotEquals(Math.rint(scroll.offset()), scroll.offset());
        assertTrue(scroll.rowShift() < 0);
        assertEquals(4, scroll.lastVisibleRow());
        scroll.advance(1);
        assertEquals(13, scroll.offset());
    }

    @Test
    void easingIsIndependentOfFrameRate() {
        double expected = advanceAt(30);
        assertEquals(expected, advanceAt(60), 1e-9);
        assertEquals(expected, advanceAt(144), 1e-9);
    }

    @Test
    void delayedSnapshotsDoNotResetTheTargetOrExposeMissingRows() {
        var scroll = scroll(512);
        scroll.scrollBy(12);
        scroll.advance(1);
        assertEquals(104, scroll.offset(), "Stop at the end of the received seven-row window");
        assertEquals(6, scroll.requestedRow());
        assertTrue(scroll.receive(512, 4, 7));
        scroll.advance(1);
        assertEquals(208, scroll.offset());
        assertTrue(scroll.receive(512, 8, 7));
        scroll.advance(1);
        assertEquals(312, scroll.offset(), "Keep the wheel target across multiple server replies");
    }

    @Test
    void reversingDirectionRejectsAReplyThatNoLongerCoversTheView() {
        var scroll = scroll(512);
        scroll.scrollBy(4);
        scroll.advance(1);
        scroll.scrollBy(-4);
        scroll.advance(1);
        assertEquals(0, scroll.offset());
        assertFalse(scroll.receive(512, 2, 7));
        assertEquals(0, scroll.rowShift());
        assertEquals(0, scroll.requestedRow());
    }

    @Test
    void thumbRetainsSubpixelPositionsAndWaitsForDistantRows() {
        var scroll = scroll(512);
        scroll.jumpTo(9.5);
        assertEquals(9.5, scroll.offset());
        scroll.jumpTo(500 * 26 + 9.5);
        assertEquals(9.5, scroll.offset());
        assertEquals(500 * 26 + 9.5, scroll.thumbOffset());
        assertEquals(500, scroll.requestedRow());
        assertTrue(scroll.receive(512, 498, 7));
        assertEquals(500 * 26 + 9.5, scroll.offset());
        scroll.jumpTo(1e9);
        assertTrue(scroll.receive(512, 507, 5));
        assertEquals(509 * 26, scroll.offset());
        assertEquals(512, scroll.lastVisibleRow());
    }

    @Test
    void shrinkingOrEmptyQueuesClampBothVisibleAndPendingPositions() {
        var scroll = scroll(512);
        scroll.scrollBy(100);
        scroll.advance(1);
        assertTrue(scroll.receive(2, 0, 2));
        scroll.advance(1);
        assertEquals(0, scroll.offset());
        assertEquals(0, scroll.maximum());
        assertEquals(0, scroll.requestedRow());
        assertEquals(2, scroll.lastVisibleRow());
        assertTrue(scroll.receive(0, 0, 0));
        scroll.scrollBy(-10);
        scroll.advance(1);
        assertEquals(0, scroll.offset());
    }

    @Test
    void pressingReturnCanFreezeTheTaskAtItsCurrentPosition() {
        var scroll = scroll(20);
        scroll.scrollBy(1);
        scroll.advance(1.0 / 60);
        double pressedAt = scroll.offset();
        scroll.stop();
        scroll.advance(1);
        assertEquals(pressedAt, scroll.offset());
    }

    private static LargeWorkstationTaskScroll scroll(int tasks) {
        var scroll = new LargeWorkstationTaskScroll();
        assertTrue(scroll.receive(tasks, 0, Math.min(tasks, LargeWorkstationUI.SNAPSHOT_SIZE)));
        return scroll;
    }

    private static double advanceAt(int fps) {
        var scroll = scroll(512);
        scroll.scrollBy(1);
        for (int frame = 0; frame < fps / 6; frame++) scroll.advance(1.0 / fps);
        return scroll.offset();
    }
}
