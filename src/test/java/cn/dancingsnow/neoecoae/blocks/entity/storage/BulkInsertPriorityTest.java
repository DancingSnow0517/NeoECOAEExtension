package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BulkInsertPriorityTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void markedBulkReceivesItemsBeforeOrdinaryStorageInBothActionModes() {
        for (Actionable mode : Actionable.values()) {
            assertRouting(mode, 64L);
        }
    }

    @Test
    void fullOrUnmatchedBulkFallsBackWithoutLosingItems() {
        for (Actionable mode : Actionable.values()) {
            assertRouting(mode, 0L);
            assertRouting(mode, 20L);
        }
    }

    private static void assertRouting(Actionable mode, long bulkAccepted) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var ordinary = mock(IECOStorageCell.class);
        var bulk = mock(IECOStorageCell.class);
        when(bulk.prioritizesMarkedInserts()).thenReturn(true);
        when(bulk.insert(key, 64L, mode, null)).thenReturn(bulkAccepted);
        when(ordinary.insert(key, 64L - bulkAccepted, mode, null)).thenReturn(64L - bulkAccepted);
        var storage = new ECOStorageInterfaceTransfer.CombinedStorage(
            List.of(ordinary, bulk), Component.literal("test"));

        assertEquals(64L, storage.insert(key, 64L, mode, null));
        var order = inOrder(bulk, ordinary);
        order.verify(bulk).insert(key, 64L, mode, null);
        if (bulkAccepted < 64L) {
            order.verify(ordinary).insert(key, 64L - bulkAccepted, mode, null);
        } else {
            verify(ordinary, never()).insert(any(), anyLong(), any(), any());
        }
    }
}
