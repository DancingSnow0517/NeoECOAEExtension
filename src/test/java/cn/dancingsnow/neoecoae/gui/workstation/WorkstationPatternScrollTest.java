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
        assertTrue(scroll.cellY(45) > 72 && scroll.cellY(45) < 90,
            "The sixth row must enter gradually while the first row is still visible");
        assertTrue(scroll.isVisible(0));
        assertTrue(scroll.isVisible(45));
        assertFalse(scroll.isVisible(54));
    }

    @Test
    void animationHasTheSamePositionAtThirtySixtyAndOneHundredFortyFourFramesPerSecond() {
        double expected = advance(30);
        assertEquals(expected, advance(60), 1e-9);
        assertEquals(expected, advance(144), 1e-9);
        assertTrue(expected > 50 && expected < 54);
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
        assertEquals(54, scroll.maximum());
        scroll.jumpTo(9.5);
        assertEquals(-9.5, scroll.cellY(0));
        assertEquals(80.5, scroll.cellY(45));
        scroll.advance(1);
        assertEquals(9.5, scroll.offset(), "Thumb dragging must follow the pointer without easing lag");
        scroll.scrollBy(10_000);
        scroll.advance(1);
        assertEquals(54, scroll.offset());
        assertFalse(scroll.isVisible(26));
        assertTrue(scroll.isVisible(27));
        assertTrue(scroll.isVisible(71));
        scroll.scrollBy(-10_000);
        scroll.advance(1);
        assertEquals(0, scroll.offset());
        assertFalse(scroll.isVisible(45));
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
        scroll.jumpTo(54);
        scroll.scrollBy(18);
        scroll.setSlotCount(9);
        scroll.advance(1);
        assertEquals(0, scroll.maximum());
        assertEquals(0, scroll.offset());
        scroll.scrollBy(100);
        scroll.advance(1);
        assertEquals(0, scroll.offset());
        scroll.setSlotCount(36);
        assertEquals(0, scroll.maximum(), "The four initially unlocked rows fit within the new viewport");
        scroll.setSlotCount(45);
        assertEquals(0, scroll.maximum(), "Five rows fit without scrolling");
        scroll.setSlotCount(46);
        assertEquals(18, scroll.maximum(), "An incomplete row must still be reachable");
    }

    private static WorkstationPatternScroll scroll(int slots) {
        var scroll = new WorkstationPatternScroll();
        scroll.setSlotCount(slots);
        return scroll;
    }

    private static double advance(int framesPerSecond) {
        var scroll = scroll(72);
        scroll.scrollBy(54);
        for (int frame = 0; frame < framesPerSecond / 6; frame++) scroll.advance(1.0 / framesPerSecond);
        return scroll.offset();
    }
}
