package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NEStoragePagingTest {
    @Test
    void emptyAndSinglePageCollectionsUseFirstPage() {
        var empty = NEStoragePaging.page(List.of(), 12);
        assertEquals(0, empty.pageIndex());
        assertEquals(1, empty.pageCount());
        assertEquals(0, empty.totalCount());
        assertEquals(List.of(), empty.entries());

        var fullPage = NEStoragePaging.page(values(32), 3);
        assertEquals(0, fullPage.pageIndex());
        assertEquals(1, fullPage.pageCount());
        assertEquals(values(32), fullPage.entries());
    }

    @Test
    void pageBoundariesCoverEveryEntryWithoutOverlap() {
        var first = NEStoragePaging.page(values(129), 0);
        var middle = NEStoragePaging.page(values(129), 2);
        var last = NEStoragePaging.page(values(129), 4);

        assertEquals(values(32), first.entries());
        assertEquals(IntStream.range(64, 96).boxed().toList(), middle.entries());
        assertEquals(List.of(128), last.entries());
        assertEquals(5, last.pageCount());
        assertEquals(129, last.totalCount());
    }

    @Test
    void requestedPageIsClampedAfterContentsShrink() {
        assertEquals(0, NEStoragePaging.page(values(33), -1).pageIndex());
        assertEquals(1, NEStoragePaging.page(values(33), 99).pageIndex());
        assertEquals(0, NEStoragePaging.page(values(12), 4).pageIndex());
    }

    private static List<Integer> values(int count) {
        return IntStream.range(0, count).boxed().toList();
    }
}
