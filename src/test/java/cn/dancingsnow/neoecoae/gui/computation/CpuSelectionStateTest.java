package cn.dancingsnow.neoecoae.gui.computation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CpuSelectionStateTest {
    // Distinct CPUs can share all their display fields; their actual identity must still distinguish them.
    private record Cpu(String name, boolean busy) {}
    private final CpuSelectionState<Cpu> selection = new CpuSelectionState<>(8);
    private void update(List<Cpu> cpus) { selection.update(cpus, Cpu::busy, Cpu::name); }

    @Test
    void removalAndReorderingDoNotMoveSelectionToAnotherCpu() {
        Cpu first = new Cpu(null, true), selected = new Cpu(null, true), next = new Cpu(null, true);
        update(List.of(first, selected));
        int serial = selection.serial(selected);
        assertTrue(selection.select(serial));
        update(List.of(next, selected));
        assertSame(selected, selection.selected());
        assertEquals(serial, selection.selectedSerial());
        assertNotEquals(serial, selection.serial(next));
        assertEquals(List.of(selected, next), selection.page());
    }

    @Test
    void choosesBusyCpuInitiallyAndAfterSelectedCpuDisappears() {
        Cpu idle = new Cpu("A", false), busy = new Cpu("B", true), other = new Cpu("C", true);
        update(List.of(idle, busy, other));
        assertSame(busy, selection.selected());
        update(List.of(idle, other));
        assertSame(other, selection.selected());
        update(List.of(idle));
        assertSame(idle, selection.selected());
    }

    @Test
    void unknownAndExpiredSelectionRequestsCannotSelectReplacementCpu() {
        Cpu old = new Cpu(null, true), replacement = new Cpu(null, true);
        update(List.of(old));
        int expired = selection.selectedSerial();
        update(List.of(replacement));
        assertFalse(selection.select(expired));
        assertFalse(selection.select(Integer.MAX_VALUE));
        assertSame(replacement, selection.selected());
    }

    @Test
    void pagesLargeListsAndClampsScrollWhenCpusDisappear() {
        List<Cpu> cpus = new ArrayList<>();
        for (int i = 0; i < 1000; i++) cpus.add(new Cpu(null, true));
        update(cpus);
        selection.scroll(Integer.MAX_VALUE);
        assertEquals(992, selection.offset());
        assertEquals(8, selection.page().size());
        assertSame(cpus.getLast(), selection.page().getLast());
        Collections.reverse(cpus);
        update(cpus);
        assertSame(cpus.getFirst(), selection.page().getLast());
        update(cpus.subList(0, 3));
        assertEquals(0, selection.offset());
        assertEquals(3, selection.page().size());
        update(List.of());
        assertEquals(-1, selection.selectedSerial());
        assertTrue(selection.page().isEmpty());
    }

    @Test
    void sameCpuAppearingTwiceOnlyProducesOneRow() {
        Cpu cpu = new Cpu(null, false);
        update(List.of(cpu, cpu));
        assertEquals(1, selection.size());
        assertSame(cpu, selection.selected());
    }
}
