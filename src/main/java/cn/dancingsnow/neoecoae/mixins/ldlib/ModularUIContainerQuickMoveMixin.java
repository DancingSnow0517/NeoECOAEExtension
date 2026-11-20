package cn.dancingsnow.neoecoae.mixins.ldlib;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes player-inventory Shift-clicks into host-owned drives before LDLib's own slot merge.
 *
 * <p>LDLib calls {@code attemptMergeStack(stack, fromContainer, simulate)} twice: a simulated pass that only
 * shrinks {@code stack} to compute how much can move, then a real pass after removing that amount from the
 * source slot. Both passes must shrink {@code stack} by exactly what the host accepted, and only the real pass
 * may change the host, otherwise items are duplicated.</p>
 */
@Mixin(ModularUIContainer.class)
public abstract class ModularUIContainerQuickMoveMixin {
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE", target =
        "Lcom/lowdragmc/lowdraglib/gui/modular/ModularUIContainer;attemptMergeStack(Lnet/minecraft/world/item/ItemStack;ZZ)Z",
        remap = false))
    private boolean neoecoae$quickMoveIntoHost(ModularUIContainer menu, ItemStack stack, boolean fromContainer,
        boolean simulate, Operation<Boolean> original) {
        boolean merged = false;
        var player = menu.getModularUI().entityPlayer;
        if (!fromContainer && !stack.isEmpty() && player != null && !player.level().isClientSide
            && menu.stillValid(player)) {
            ModularUI ui = ModularUI.from(menu);
            if (ui != null) {
                ItemStack remaining = ui.quickMoveIntoHost(stack.copy(), simulate);
                int accepted = stack.getCount() - (remaining.isEmpty() ? 0 : remaining.getCount());
                if (accepted > 0 && (remaining.isEmpty() || ItemStack.isSameItemSameTags(stack, remaining))) {
                    stack.shrink(accepted);
                    merged = true;
                }
                if (stack.isEmpty()) return true;
            }
        }
        return original.call(menu, stack, fromContainer, simulate) || merged;
    }
}
