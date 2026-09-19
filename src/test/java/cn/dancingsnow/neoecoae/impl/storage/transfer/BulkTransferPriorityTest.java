package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BulkTransferPriorityTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void markedBulkPrecedesExistingOrdinaryStockAndIsNotReservedTwice() throws Exception {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var ordinary = mock(ECOStorageShard.class);
        var bulk = mock(ECOStorageShard.class);
        var ordinaryCell = mock(ECOStorageCell.class);
        var bulkCell = mock(ECOStorageCell.class);
        when(ordinary.storage()).thenReturn(ordinaryCell);
        when(bulk.storage()).thenReturn(bulkCell);
        when(bulk.index()).thenReturn(1);
        when(bulk.drivePosition()).thenReturn(1L);
        when(bulkCell.prioritizesMarkedInserts()).thenReturn(true);
        when(bulkCell.isPreferredStorageFor(key, null)).thenReturn(true);
        when(ordinary.stored(key, null)).thenReturn(100L);
        when(bulk.stored(key, null)).thenReturn(10L);
        when(bulk.insert(key, 64L, Actionable.SIMULATE, null)).thenReturn(20L);
        when(ordinary.insert(key, 44L, Actionable.SIMULATE, null)).thenReturn(44L);
        var constructor = ECOFiniteStorageDomain.class.getDeclaredConstructor(
            List.class, ECOStorageInterfaceMode.class, Component.class, UUID.class);
        constructor.setAccessible(true);
        var domain = constructor.newInstance(List.of(ordinary, bulk), ECOStorageInterfaceMode.INPUT,
            Component.literal("test"), UUID.randomUUID());
        domain.reconcileKey(key, null);

        var plan = domain.reserveInsert(key, 64L, null).plan();
        assertEquals(64L, plan.amount());
        assertEquals(List.of(new ECOStorageAllocation(1, 20L), new ECOStorageAllocation(0, 44L)),
            plan.allocations());
        verify(bulk).insert(key, 64L, Actionable.SIMULATE, null);
        verify(bulk, never()).insert(key, 44L, Actionable.SIMULATE, null);
    }
}
