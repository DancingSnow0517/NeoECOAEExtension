package cn.dancingsnow.neoecoae.items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOBulkStorageCellItemTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void expansionUsesCellLimitsAndPreservesInactiveMarkerSlots() {
        var item = mock(ECOBulkStorageCellItem.class, CALLS_REAL_METHODS);
        doReturn(5).when(item).getTotalTypes();
        doReturn(25).when(item).upgradedTypeLimit();
        var stack = new ItemStack(Items.PAPER);
        doReturn(false).when(item).hasExpansionCard(stack);
        {
            assertEquals(5, item.activeSlots(stack));
            assertEquals(5, item.getConfigInventory(stack).size());
            doReturn(true).when(item).hasExpansionCard(stack);
            assertEquals(25, item.activeSlots(stack));
            assertEquals(25, item.getConfigInventory(stack).size());
            var key = AEItemKey.of(Items.IRON_INGOT);
            var saved = new ArrayList<GenericStack>(Collections.nCopies(25, null));
            saved.set(24, new GenericStack(key, 0));
            cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem.writeConfiguredStacks(stack, saved);
            doReturn(false).when(item).hasExpansionCard(stack);
            assertEquals(5, item.getConfigInventory(stack).size());
            assertEquals(key, cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem.configuredStacks(stack).get(24).what());
            doReturn(true).when(item).hasExpansionCard(stack);
            assertEquals(key, item.getConfigInventory(stack).getKey(24));
        }
    }
}
