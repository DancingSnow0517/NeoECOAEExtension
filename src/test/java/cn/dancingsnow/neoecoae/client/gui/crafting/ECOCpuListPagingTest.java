package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.config.CpuSelectionMode;
import appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPage;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ECOCpuListPagingTest {
    private final List<CraftingCpuListEntry> cpus = IntStream.rangeClosed(1, 1000)
        .mapToObj(i -> new CraftingCpuListEntry(i, 1024, 1, null, CpuSelectionMode.ANY, null, 0, 0)).toList();

    @Test void largeNetworksSendOnlySixRowsAndTheOffscreenSelectedCpu() {
        var page = ECOCpuListPage.sample(cpus, 500, 12, 1);
        assertEquals(1000, page.total());
        assertEquals(cpus.subList(500, 506), page.cpus());
        assertSame(cpus.getFirst(), page.selected());
        assertEquals(12, page.sequence());
    }

    @Test void shrinkingAndEmptyNetworksClampOffsetsAndDropMissingSelections() {
        assertEquals(994, ECOCpuListPage.sample(cpus, Integer.MAX_VALUE, 1, 1000).offset());
        var small = ECOCpuListPage.sample(cpus.subList(0, 3), 994, 2, 1000);
        assertEquals(0, small.offset());
        assertEquals(3, small.cpus().size());
        assertNull(small.selected());
        var empty = ECOCpuListPage.sample(List.of(), 99, 3, 1);
        assertEquals(0, empty.total());
        assertEquals(0, empty.offset());
        assertTrue(empty.cpus().isEmpty());
    }

    @Test void rapidScrollingKeepsOldRowsUntilTheLatestReply() {
        var state = new ECOCpuListClientState();
        var initial = ECOCpuListPage.sample(cpus, 0, 0, 1);
        state.accept(initial);
        assertEquals(1, state.scrollTo(2).sequence());
        assertEquals(2, state.scrollTo(4).sequence());
        assertFalse(state.accept(ECOCpuListPage.sample(cpus, 2, 1, 1)));
        assertSame(initial, state.visible());
        assertFalse(state.canSelect());
        assertTrue(state.accept(ECOCpuListPage.sample(cpus, 4, 2, 1)));
        assertEquals(5, state.visible().cpus().getFirst().serial());
        assertTrue(state.canSelect());
    }

    @Test void returningToTheSameOffsetStillRequiresTheLatestSequenceBeforeClicking() {
        var state = new ECOCpuListClientState();
        var initial = ECOCpuListPage.sample(cpus, 0, 0, 1);
        state.accept(initial);
        state.scrollTo(2);
        state.scrollTo(0);
        assertFalse(state.accept(initial));
        assertFalse(state.accept(ECOCpuListPage.sample(cpus, 0, 1, 1)));
        assertFalse(state.canSelect());
        assertTrue(state.accept(ECOCpuListPage.sample(cpus, 0, 2, 1)));
        assertTrue(state.canSelect());
    }

    @Test void serverClampingAndRecreatedWidgetsKeepTheirSequencesInSync() {
        var state = new ECOCpuListClientState();
        state.accept(ECOCpuListPage.sample(cpus, 994, 50, 1));
        assertEquals(51, state.scrollTo(992).sequence());
        state.accept(ECOCpuListPage.sample(cpus.subList(0, 3), 992, 51, 1));
        assertEquals(0, state.offset());
        assertTrue(state.canSelect());
        assertNull(state.scrollTo(Integer.MAX_VALUE));
    }

    @Test void unchangedSnapshotsDoNotUndoLocalInput() {
        var state = new ECOCpuListClientState();
        var initial = ECOCpuListPage.sample(cpus, 0, 0, 1);
        assertTrue(state.accept(initial));
        assertFalse(state.accept(initial));
        state.scrollTo(4);
        assertFalse(state.accept(ECOCpuListPage.sample(cpus, 0, 0, 1)));
        assertEquals(4, state.offset());
    }
}
