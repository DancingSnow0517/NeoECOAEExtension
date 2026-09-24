package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternBusUpdateTest {
    @Test
    void decodesOnlyDirtySlotsAfterInitialSnapshot() {
        PatternSlotCache<String> cache = new PatternSlotCache<>(4);
        List<Integer> decoded = new ArrayList<>();
        cache.refresh(4, slot -> {
            decoded.add(slot);
            return "slot" + slot;
        });
        assertEquals(List.of(0, 1, 2, 3), decoded);

        decoded.clear();
        cache.mark(1);
        cache.mark(1);
        cache.mark(3);
        cache.refresh(4, slot -> {
            decoded.add(slot);
            return "changed" + slot;
        });
        assertEquals(List.of(1, 3), decoded);
        assertEquals("slot0", cache.get(0));
        assertEquals("changed1", cache.get(1));
    }

    @Test
    void coalescesMutationsIntoOneDuePublication() {
        var updates = new PatternBusUpdateScheduler.PendingUpdates<Object>();
        Object bus = new Object();
        updates.mark(bus, 11);
        updates.mark(bus, 11);
        updates.mark(bus, 12);
        assertTrue(updates.drainDue(11, ignored -> true).isEmpty());
        assertEquals(List.of(bus), updates.drainDue(12, ignored -> true));
        assertTrue(updates.drainDue(12, ignored -> true).isEmpty());
    }
}
