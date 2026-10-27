package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.api.storage.INativeMEStorageRestrictedItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeMEStorageCellFilterTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void independentAddonItemIsRejectedWithoutExtendingEcoCell() throws Exception {
        var item = mock(Item.class, withSettings().extraInterfaces(INativeMEStorageRestrictedItem.class)
            .defaultAnswer(CALLS_REAL_METHODS));
        assertFalse(item instanceof ECOStorageCellItem);
        var result = filter(stack(item));
        assertTrue(result.isCancelled());
        assertFalse(result.getReturnValue());
    }

    @Test
    void itemCanRestrictIndividualStacksAndOtherwiseKeepAe2Validation() throws Exception {
        var item = mock(Item.class, withSettings().extraInterfaces(INativeMEStorageRestrictedItem.class));
        var restricted = stack(item);
        var unrestricted = stack(item);
        when(((INativeMEStorageRestrictedItem) item).isNativeMEStorageRestricted(restricted)).thenReturn(true);
        assertTrue(filter(restricted).isCancelled());
        assertFalse(filter(unrestricted).isCancelled());
    }

    @Test
    void ordinaryItemsAndEmptyStacksLeaveAe2ValidationInPlace() throws Exception {
        assertFalse(filter(stack(mock(Item.class))).isCancelled());
        assertFalse(filter(ItemStack.EMPTY).isCancelled());
    }

    @Test
    void existingEcoCellClassStillUsesTheRestrictionContract() throws Exception {
        assertTrue(INativeMEStorageRestrictedItem.class.isAssignableFrom(ECOStorageCellItem.class));
        var result = filter(stack(mock(ECOStorageCellItem.class, CALLS_REAL_METHODS)));
        assertTrue(result.isCancelled());
        assertFalse(result.getReturnValue());
    }

    private static ItemStack stack(Item item) {
        var stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        return stack;
    }

    private static CallbackInfoReturnable<Boolean> filter(ItemStack stack) throws Exception {
        var method = NativeMEStorageCellFilterMixin.class.getDeclaredMethod("neoecoae$rejectEcoStorageCells",
            InternalInventory.class, int.class, ItemStack.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        var result = new CallbackInfoReturnable<Boolean>("allowInsert", true, true);
        method.invoke(new NativeMEStorageCellFilterMixin() {}, null, 0, stack, result);
        return result;
    }
}
