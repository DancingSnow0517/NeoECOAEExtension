package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.items.storage.CreativeCellItem;
import appeng.me.cells.CreativeCellHandler;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmountSource;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCreativeCellTest {
    private MockedStatic<AEKeyTypes> keyTypes;

    @BeforeEach
    void initializeKeyTypes() {
        keyTypes = mockStatic(AEKeyTypes.class);
        keyTypes.when(AEKeyTypes::getAll).thenReturn(java.util.Set.of(AEKeyType.items(), AEKeyType.fluids()));
    }

    @AfterEach
    void closeKeyTypes() {
        keyTypes.close();
    }

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private static ItemStack creativeStack(List<GenericStack> config) {
        // Only the item shell is mocked; both inventories use AE2's real CellConfig and storage logic.
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(mock(CreativeCellItem.class));
        when(stack.getOrDefault(eq(AEComponents.STORAGE_CELL_CONFIG_INV), anyList())).thenReturn(config);
        when(stack.getHoverName()).thenReturn(Component.literal("Creative test cell"));
        return stack;
    }

    @Test
    void declaresLongMaxWithoutChangingVanillaStorageBehavior() {
        AEKey configured = AEItemKey.of(Items.STONE);
        AEKey other = AEItemKey.of(Items.DIRT);
        ItemStack stack = creativeStack(List.of(new GenericStack(configured, 1)));
        var vanilla = CreativeCellHandler.INSTANCE.getCellInventory(stack, null);
        var eco = ECOCreativeCell.Handler.INSTANCE.getCellInventory(stack, null);
        assertNotNull(vanilla);
        assertNotNull(eco);
        assertEquals(Integer.MAX_VALUE, vanilla.getAvailableStacks().get(configured));
        assertEquals(Long.MAX_VALUE, eco.getAvailableStacks().get(configured));

        clearInvocations(stack); // AE2 normalizes configuration while creating its inventory.
        var source = mock(IActionSource.class);
        for (Actionable mode : Actionable.values()) {
            for (AEKey key : List.of(configured, other)) {
                for (long amount : new long[]{0, 64, (long) Integer.MAX_VALUE + 1, Long.MAX_VALUE}) {
                    assertEquals(vanilla.insert(key, amount, mode, source), eco.insert(key, amount, mode, source));
                    assertEquals(vanilla.extract(key, amount, mode, source), eco.extract(key, amount, mode, source));
                }
                assertEquals(vanilla.isPreferredStorageFor(key, source), eco.isPreferredStorageFor(key, source));
            }
        }
        eco.persist();
        java.math.BigInteger extracted = java.math.BigInteger.ZERO;
        for (int child = 0; child < 3; child++) {
            extracted = extracted.add(java.math.BigInteger.valueOf(
                eco.extract(configured, Long.MAX_VALUE, Actionable.MODULATE, source)));
        }
        assertEquals(java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(3)), extracted);
        assertEquals(Long.MAX_VALUE, eco.getAvailableStacks().get(configured));
        assertEquals(0, eco.getAvailableStacks().get(other));
        assertEquals(vanilla.getStatus(), eco.getStatus());
        assertEquals(vanilla.getIdleDrain(), eco.getIdleDrain());
        assertEquals(vanilla.canFitInsideCell(), eco.canFitInsideCell());
        assertEquals(vanilla.getDescription(), eco.getDescription());
        assertFalse(eco.isInfiniteStorageEligible());
        assertEquals(0, eco.getUsedBytes());
        assertEquals(1, eco.getStoredItemTypes());
        assertEquals(63, eco.getTotalItemTypes());
        verify(stack, never()).set(eq(AEComponents.STORAGE_CELL_CONFIG_INV), anyList());
    }

    @Test
    void duplicateSourcesSaturateWithoutOverridingTerminalAmounts() {
        AEKey key = AEItemKey.of(Items.STONE);
        ItemStack stack = creativeStack(List.of(new GenericStack(key, 1)));
        var first = ECOCreativeCell.Handler.INSTANCE.getCellInventory(stack, null);
        var second = ECOCreativeCell.Handler.INSTANCE.getCellInventory(stack, null);
        assertNotNull(first);
        assertNotNull(second);
        KeyCounter counter = new KeyCounter();
        counter.add(key, 64);
        first.getAvailableStacks(counter);
        second.getAvailableStacks(counter);
        assertEquals(Long.MAX_VALUE, counter.get(key));
        assertEquals(java.util.Set.of(key), ((ECOCreativeCell) first).configuredKeys());
        assertFalse(first instanceof ExactAmountSource);
    }

    @Test
    void emptyCellsRemainEmptyAndOrdinaryItemsAreRejected() {
        var empty = ECOCreativeCell.Handler.INSTANCE.getCellInventory(creativeStack(List.of()), null);
        assertNotNull(empty);
        assertTrue(empty.getAvailableStacks().isEmpty());
        assertTrue(empty.canFitInsideCell());
        assertEquals(0, empty.getStoredItemTypes());
        assertFalse(ECOCreativeCell.Handler.INSTANCE.isCell(new ItemStack(Items.STONE)));
        assertNull(ECOCreativeCell.Handler.INSTANCE.getCellInventory(new ItemStack(Items.STONE), null));
        assertFalse(ECOCreativeCell.Handler.INSTANCE.isCell(ItemStack.EMPTY));
    }
}
