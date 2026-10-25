package cn.dancingsnow.neoecoae.gui.computation;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComputationScrollPageTest {
    private final AtomicLong time = new AtomicLong();
    private final ComputationScrollPage<Integer> page = new ComputationScrollPage<>(9, 1, time::get);

    @Test void rowsMoveContinuouslyAndHitTestingFollowsTheRenderedPosition() {
        page.accept(0, 0, values(0, 18), false);
        page.advance();
        page.accept(3, 0, values(0, 21), false);
        assertEquals(0, page.advance());
        time.set(80_000_000);
        assertEquals(1.5, page.advance());
        assertEquals(1, page.hit(0, 0, 23, 22));
        assertEquals(2, page.hit(12, 0, 23, 22));
        assertNull(page.hit(10.75, 0, 23, 22), "the moving separator is not a row");
        time.set(160_000_000);
        // Input continues using the last drawn frame until the renderer advances.
        assertEquals(1, page.hit(0, 0, 23, 22));
        assertEquals(3, page.advance());
        assertEquals(3, page.hit(0, 0, 23, 22));
    }

    @Test void reversingMidAnimationStartsAtTheCurrentPositionAndRefreshesDoNotRestartIt() {
        page.accept(0, 0, values(0, 18), false);
        page.accept(3, 0, values(0, 21), false);
        time.set(80_000_000);
        assertEquals(1.5, page.advance());
        page.accept(0, 0, values(0, 18), false);
        assertEquals(1.5, page.advance());
        time.set(160_000_000);
        page.accept(0, 0, values(0, 18), false);
        assertEquals(0.75, page.advance());
        time.set(240_000_000);
        assertEquals(0, page.advance());
    }

    @Test void aReplyBetweenFramesDoesNotMoveTheClickableRowsBeforeTheyAreDrawn() {
        page.accept(0, 0, values(0, 18), false);
        page.advance();
        page.accept(3, 0, values(0, 21), false);
        time.set(80_000_000);
        page.accept(0, 0, values(0, 18), false);
        assertEquals(0, page.hit(0, 0, 23, 22));
        assertEquals(1.5, page.advance());
        assertEquals(1, page.hit(0, 0, 23, 22));
    }

    @Test void largeThumbJumpsStayInsideTheBoundedReceivedWindow() {
        page.accept(0, 0, values(0, 18), false);
        page.accept(1000, 991, values(991, 1018), false);
        assertEquals(991, page.advance());
        for (int frame = 0; frame <= 10; frame++) {
            time.set(frame * 16_000_000L);
            double position = page.advance();
            assertTrue(position >= 991 && position <= 1000);
            double firstFullRow = (Math.ceil(position) - position) * 23 + 1;
            assertNotNull(page.hit(firstFullRow, 0, 23, 22));
            assertNotNull(page.hit(firstFullRow + 7 * 23, 0, 23, 22));
        }
    }

    @Test void gridColumnsAndPartialFinalRowsKeepTheirOwnEntries() {
        var grid = new ComputationScrollPage<Integer>(9, 3, time::get);
        grid.accept(0, 0, values(0, 54), false);
        grid.accept(3, 0, values(0, 57), false);
        time.set(80_000_000);
        assertEquals(0.5, grid.advance());
        assertEquals(1, grid.hit(0, 1, 23, 22));
        assertEquals(5, grid.hit(12, 2, 23, 22));
        assertNull(grid.hit(206, 0, 23, 22));
        grid.accept(3, 0, values(0, 28), true);
        assertEquals(1, grid.advance());
        assertEquals(27, grid.hit(185, 0, 23, 22));
        assertNull(grid.hit(185, 1, 23, 22));
    }

    @Test void changingCpuOrReopeningSnapsToTheNewPage() {
        page.accept(0, 0, values(0, 18), false);
        page.accept(3, 0, values(0, 21), false);
        time.set(80_000_000);
        page.accept(0, 0, List.of(99), true);
        assertEquals(0, page.advance());
        assertEquals(99, page.hit(0, 0, 23, 22));
        page.reset();
        page.accept(12, 3, values(3, 30), false);
        assertEquals(12, page.advance());
    }

    private static List<Integer> values(int start, int end) {
        return IntStream.range(start, end).boxed().toList();
    }
}
