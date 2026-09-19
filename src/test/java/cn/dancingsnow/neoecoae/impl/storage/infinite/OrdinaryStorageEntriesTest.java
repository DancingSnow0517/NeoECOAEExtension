package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class OrdinaryStorageEntriesTest {
    @Test
    void missingModAndDamagedEntryDoNotHideHealthyItems() {
        ListTag entries = new ListTag();
        entries.add(entry("stone", 100));
        entries.add(entry("missing", 20));
        entries.add(entry("broken", -1));
        var result = InfiniteStorageEntries.readOrdinary(entries, key -> {
            String id = key.getString("id");
            if (id.equals("missing")) throw new IllegalArgumentException("Mod removed");
            return id;
        });
        assertEquals(2, result.available().size());
        assertEquals("stone", result.available().get(0).key());
        assertNull(result.available().get(1).key());
        assertEquals(entry("broken", -1), result.retained().get(0));
        assertTrue(result.blockedKeys().contains("broken"));
    }

    @Test
    void conflictingAliasesRetainBothQuantitiesWithoutSummingThem() {
        ListTag entries = new ListTag();
        entries.add(entry("old_name", 100));
        entries.add(entry("new_name", 200));
        entries.add(entry("iron", 3));
        var result = InfiniteStorageEntries.readOrdinary(
                entries, key -> key.getString("id").equals("iron") ? "iron" : "same");
        assertEquals(1, result.available().size());
        assertEquals(2, result.retained().size());
        assertTrue(result.blockedKeys().contains("same"));
    }

    private static CompoundTag entry(String id, long amount) {
        CompoundTag key = new CompoundTag();
        key.putString("id", id);
        CompoundTag entry = new CompoundTag();
        entry.put("key", key);
        entry.putLong("amount", amount);
        return entry;
    }
}
