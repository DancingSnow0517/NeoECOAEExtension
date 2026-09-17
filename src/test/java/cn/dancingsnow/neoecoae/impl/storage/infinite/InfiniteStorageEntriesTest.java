package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class InfiniteStorageEntriesTest {
    @Test
    void invalidQuantityDoesNotHideUnrelatedItemsAndIsRetainedVerbatim() {
        ListTag entries = entries(entry("stone", 64), entry("broken", -1), entry("iron", 12));
        var result = InfiniteStorageEntries.read(entries, key -> key.getString("id"));
        assertEquals(
                List.of("stone", "iron"),
                result.available().stream()
                        .map(InfiniteStorageEntries.Entry::key)
                        .toList());
        assertEquals(List.of(entries.getCompound(1)), result.retained());
        assertEquals(java.util.Set.of("broken"), result.blockedKeys());
        assertEquals(HugeAmount.of(64), result.available().get(0).amount());
    }

    @Test
    void decoderFailureOnlyMakesThatKeyUnresolved() {
        var result = InfiniteStorageEntries.read(entries(entry("stone", 64), entry("missing", 12)), key -> {
            if (key.getString("id").equals("missing")) throw new IllegalArgumentException("Missing mod");
            return key.getString("id");
        });
        assertEquals("stone", result.available().get(0).key());
        assertNull(result.available().get(1).key());
        assertEquals(HugeAmount.of(12), result.available().get(1).amount());
        assertEquals("missing", result.available().get(1).encodedKey().getString("id"));
    }

    @Test
    void allConflictingCopiesAreIsolatedInsteadOfChoosingOrSummingOne() {
        CompoundTag first = entry("stone", 64);
        CompoundTag second = entry("stone", 128);
        var result = InfiniteStorageEntries.read(entries(first, entry("iron", 12), second), key -> key.getString("id"));
        assertEquals(
                List.of("iron"),
                result.available().stream()
                        .map(InfiniteStorageEntries.Entry::key)
                        .toList());
        assertEquals(List.of(first, second), result.retained());
        assertEquals(java.util.Set.of("stone"), result.blockedKeys());
    }

    @Test
    void aliasesResolvingToSameKeyAreAlsoIsolated() {
        var result = InfiniteStorageEntries.read(entries(entry("old", 64), entry("new", 12)), key -> "same-item");
        assertTrue(result.available().isEmpty());
        assertEquals(2, result.retained().size());
    }

    @Test
    void retainedEntriesStayIsolatedAfterHealthyContentsChange() {
        CompoundTag broken = entry("broken", -1);
        var result = InfiniteStorageEntries.read(entries(entry("stone", 64), broken), key -> key.getString("id"));
        ListTag saved = entries(entry("stone", 32));
        result.retained().forEach(saved::add);
        var reloaded = InfiniteStorageEntries.read(saved, key -> key.getString("id"));
        assertEquals(HugeAmount.of(32), reloaded.available().get(0).amount());
        assertEquals(List.of(broken), reloaded.retained());
    }

    private static CompoundTag entry(String id, long amount) {
        CompoundTag key = new CompoundTag();
        key.putString("id", id);
        CompoundTag entry = new CompoundTag();
        entry.put("key", key);
        entry.putLong("amount_long", amount);
        return entry;
    }

    private static ListTag entries(CompoundTag... records) {
        ListTag list = new ListTag();
        for (CompoundTag record : records) list.add(record);
        return list;
    }
}
