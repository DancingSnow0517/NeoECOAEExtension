package cn.dancingsnow.neoecoae.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatternCatalogTest {
    @Test
    void slotIndexTracksDuplicatesAndCapacity() {
        var index = new PatternCatalog.BusIndex<String>(3);
        assertEquals(3, index.emptySlots());
        index.set(0, "stone");
        index.set(1, "stone");
        assertEquals(1, index.emptySlots());
        index.set(0, null);
        assertTrue(index.contains("stone"));
        index.set(1, "iron");
        assertFalse(index.contains("stone"));
        assertTrue(index.contains("iron"));
        assertEquals(2, index.emptySlots());
    }

    @Test
    void rebuiltIndexUsesNewPageCapacity() {
        var oldIndex = new PatternCatalog.BusIndex<String>(2);
        oldIndex.set(0, "stone");
        var expanded = new PatternCatalog.BusIndex<String>(4);
        expanded.set(0, "stone");
        assertEquals(3, expanded.emptySlots());
        assertTrue(expanded.contains("stone"));
        assertEquals(1, oldIndex.emptySlots());
    }
}
