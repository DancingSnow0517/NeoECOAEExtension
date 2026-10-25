package cn.dancingsnow.neoecoae.gui.computation;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComputationScrollPageTest {
    private final ComputationScrollPage<Integer> page = new ComputationScrollPage<>(9, 1);

    @Test void scrollingSwitchesWholeRowsAndLeavesTheFixedSeparatorsUnclickable() {
        page.accept(0, 0, values(0, 18));
        assertEquals(0, page.advance());
        page.accept(3, 0, values(0, 21));
        assertEquals(3, page.advance());
        assertEquals(0, page.row(3));
        assertEquals(3, page.hit(0, 0, 23, 22));
        assertEquals(3, page.hit(21.9, 0, 23, 22));
        assertNull(page.hit(22, 0, 23, 22));
        assertEquals(4, page.hit(23, 0, 23, 22));
        for (int frame = 0; frame < 10; frame++) assertEquals(3, page.advance());
    }

    @Test void reversingOrRefreshingAReceivedPageNeverInterpolatesRows() {
        page.accept(3, 0, values(0, 21));
        assertEquals(3, page.advance());
        page.accept(0, 0, values(0, 18));
        assertEquals(0, page.advance());
        page.accept(0, 0, values(0, 18));
        assertEquals(0, page.advance());
        assertEquals(0, page.hit(0, 0, 23, 22));
    }

    @Test void aReplyBetweenFramesKeepsThePreviouslyDrawnRowsClickableUntilTheNextFrame() {
        page.accept(0, 0, values(0, 18));
        page.advance();
        page.accept(1000, 991, values(991, 1018));
        assertEquals(0, page.hit(0, 0, 23, 22));
        assertEquals(0, page.get(0));
        assertEquals(1000, page.advance());
        assertEquals(1000, page.hit(0, 0, 23, 22));
        assertEquals(1008, page.hit(8 * 23, 0, 23, 22));
    }

    @Test void gridColumnsAndPartialFinalRowsKeepTheirOwnEntries() {
        var grid = new ComputationScrollPage<Integer>(9, 3);
        grid.accept(0, 0, values(0, 54));
        grid.advance();
        grid.accept(3, 0, values(0, 57));
        assertEquals(1, grid.advance());
        assertEquals(4, grid.hit(0, 1, 23, 22));
        assertEquals(5, grid.hit(21, 2, 23, 22));
        assertNull(grid.hit(22, 0, 23, 22));
        assertNull(grid.hit(206, 0, 23, 22));
        grid.accept(3, 0, values(0, 28));
        assertEquals(1, grid.advance());
        assertEquals(27, grid.hit(185, 0, 23, 22));
        assertNull(grid.hit(185, 1, 23, 22));
    }

    @Test void resettingAndReceivingAnotherCpuDoesNotReuseOldEntries() {
        page.accept(3, 0, values(0, 21));
        page.advance();
        page.reset();
        assertNull(page.hit(0, 0, 23, 22));
        page.accept(0, 0, List.of(99));
        assertEquals(0, page.advance());
        assertEquals(99, page.hit(0, 0, 23, 22));
        assertEquals(1, page.size());
    }

    private static List<Integer> values(int start, int end) {
        return IntStream.range(start, end).boxed().toList();
    }
}
