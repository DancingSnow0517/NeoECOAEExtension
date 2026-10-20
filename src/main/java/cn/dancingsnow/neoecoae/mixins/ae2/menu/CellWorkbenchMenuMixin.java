package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.stacks.AEKey;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaLongBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

@Mixin(CellWorkbenchMenu.class)
public class CellWorkbenchMenuMixin {
    @Inject(method = "iterateCellStacks", at = @At("HEAD"), cancellable = true)
    private void neoecoae$partitionEcoCell(ItemStack stack,
            CallbackInfoReturnable<Iterator<? extends AEKey>> cir) {
        var inventory = ECOStorageCellItem.getCellInventory(stack);
        if (inventory instanceof ECOMegaLongBulkStorageCell bulk) {
            // The expanded available stacks contain several variants per chain.
            // Partition only once per stored chain, preserving its configured form.
            cir.setReturnValue(bulk.getStoredChainFilters().iterator());
        }
    }
}
