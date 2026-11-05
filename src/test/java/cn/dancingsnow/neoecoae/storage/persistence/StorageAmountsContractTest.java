package cn.dancingsnow.neoecoae.storage.persistence;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StorageAmountsContractTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void existingReadOnlyViewTracksChangesWithoutOwningAnotherLedger() {
        var values = new StorageAmounts();
        var view = values.visibleAmounts();
        var key = AEItemKey.of(Items.STONE);
        values.add(key, 7);
        assertEquals(7, view.getLong(key));
        assertThrows(UnsupportedOperationException.class, () -> view.put(key, 3));
        assertThrows(UnsupportedOperationException.class, () -> view.object2LongEntrySet().iterator().next().setValue(3));
        values.add(key, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, view.getLong(key));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7)), values.totalExact());
        values.setLong(key, 0);
        assertTrue(view.isEmpty());
        assertEquals(BigInteger.ZERO, values.totalExact());
    }

    @Test void noOpAssignmentsAndValueChangesPreserveStructureRevisionSemantics() {
        var values = new StorageAmounts();
        var key = AEItemKey.of(Items.STONE);
        values.setLong(key, 0);
        assertEquals(0, values.contentRevision());
        assertEquals(0, values.structureRevision());
        values.setLong(key, 5);
        long content = values.contentRevision(), structure = values.structureRevision();
        values.setLong(AEItemKey.of(Items.STONE), 5);
        assertEquals(content, values.contentRevision());
        values.add(key, 4);
        assertTrue(values.contentRevision() > content);
        assertEquals(structure, values.structureRevision());
        values.clear();
        assertTrue(values.structureRevision() > structure);
        assertEquals(0, values.totalVisible());
        assertTrue(values.visibleAmounts().isEmpty());
    }

    @Test void equalityIncludesExactOverflowAndIgnoresDeletedHistoryEntries() {
        var first = new StorageAmounts();
        var second = new StorageAmounts();
        first.enableChangeLog(4);
        second.enableChangeLog(4);
        var key = AEItemKey.of(Items.STONE);
        BigInteger huge = BigInteger.ONE.shiftLeft(130);
        first.set(key, HugeAmount.of(huge));
        second.set(key, HugeAmount.of(huge.add(BigInteger.ONE)));
        assertEquals(first.visibleAmounts(), second.visibleAmounts());
        assertFalse(first.sameContents(second));
        first.add(key, 1);
        assertTrue(first.sameContents(second));
        first.setLong(key, 0);
        second.clear();
        assertTrue(first.sameContents(second));
        assertTrue(first.visibleAmounts().isEmpty());
    }
}
