package cn.dancingsnow.neoecoae.gui.workstation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkstationPatternScrollTest {
    @Test
    void wheelMovesThroughFractionalPixelsAndExposesTwoPartialEdgeRows() {
        var scroll = scroll(72);
        scroll.scrollBy(18);
        scroll.advance(1.0 / 60);
        assertTrue(scroll.offset() > 0 && scroll.offset() < 18);
        assertNotEquals(Math.rint(scroll.offset()), scroll.offset(), "Animation must retain subpixel precision");
        assertTrue(scroll.cellY(0) < 0, "The first row must slide through the top edge");
        assertTrue(scroll.cellY(36) > 54 && scroll.cellY(36) < 72,
            "The fifth row must enter gradually while the first row is still visible");
        assertTrue(scroll.isVisible(0));
        assertTrue(scroll.isVisible(36));
        assertFalse(scroll.isVisible(45));
    }

    @Test
    void animationHasTheSamePositionAtThirtySixtyAndOneHundredFortyFourFramesPerSecond() {
        double expected = advance(30);
        assertEquals(expected, advance(60), 1e-9);
        assertEquals(expected, advance(144), 1e-9);
        assertTrue(expected > 50 && expected < 72);
    }

    @Test
    void fractionalWheelInputAccumulatesAndCanReverseBeforeAnimationCompletes() {
        var scroll = scroll(72);
        scroll.scrollBy(0.25 * 18);
        scroll.scrollBy(0.25 * 18);
        scroll.advance(1);
        assertEquals(9, scroll.offset(), 0.01);
        scroll.scrollBy(-2.5);
        scroll.advance(1);
        assertEquals(6.5, scroll.offset(), 0.01);
    }

    @Test
    void thumbCanStopBetweenRowsAndBothEndsClampWithoutOvershoot() {
        var scroll = scroll(72);
        assertEquals(72, scroll.maximum());
        scroll.jumpTo(9.5);
        assertEquals(-9.5, scroll.cellY(0));
        assertEquals(62.5, scroll.cellY(36));
        scroll.advance(1);
        assertEquals(9.5, scroll.offset(), "Thumb dragging must follow the pointer without easing lag");
        scroll.scrollBy(10_000);
        scroll.advance(1);
        assertEquals(72, scroll.offset());
        assertFalse(scroll.isVisible(35));
        assertTrue(scroll.isVisible(36));
        assertTrue(scroll.isVisible(71));
        scroll.scrollBy(-10_000);
        scroll.advance(1);
        assertEquals(0, scroll.offset());
        assertFalse(scroll.isVisible(36));
    }

    @Test
    void pressingASlotStopsPendingAnimationSoTheClickedPhysicalSlotDoesNotMove() {
        var scroll = scroll(72);
        scroll.scrollBy(54);
        scroll.advance(1.0 / 60);
        double pressedAt = scroll.offset();
        scroll.stop();
        scroll.advance(1);
        assertEquals(pressedAt, scroll.offset());
    }

    @Test
    void shortInventoriesCannotScrollAndResizingClampsBothCurrentAndPendingOffsets() {
        var scroll = scroll(72);
        scroll.jumpTo(72);
        scroll.scrollBy(18);
        scroll.setSlotCount(9);
        scroll.advance(1);
        assertEquals(0, scroll.maximum());
        assertEquals(0, scroll.offset());
        scroll.scrollBy(100);
        scroll.advance(1);
        assertEquals(0, scroll.offset());
        scroll.setSlotCount(37);
        assertEquals(18, scroll.maximum(), "An incomplete row must still be reachable");
    }

    private static WorkstationPatternScroll scroll(int slots) {
        var scroll = new WorkstationPatternScroll();
        scroll.setSlotCount(slots);
        return scroll;
    }

    private static double advance(int framesPerSecond) {
        var scroll = scroll(72);
        scroll.scrollBy(72);
        for (int frame = 0; frame < framesPerSecond / 6; frame++) scroll.advance(1.0 / framesPerSecond);
        return scroll.offset();
    }
}
