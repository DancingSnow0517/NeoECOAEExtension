package cn.dancingsnow.neoecoae.mixins.ldlib;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModularUIContainer.class)
public abstract class ModularUIContainerQuickMoveMixin {
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE", target =
        "Lcom/lowdragmc/lowdraglib/gui/modular/ModularUIContainer;attemptMergeStack(Lnet/minecraft/world/item/ItemStack;ZZ)Z",
        remap = false))
    private boolean neoecoae$quickMoveIntoHost(ModularUIContainer menu, ItemStack stack, boolean intoPlayer,
        boolean reverse, Operation<Boolean> original) {
        var player = menu.getModularUI().entityPlayer;
        if (!intoPlayer && player != null && menu.stillValid(player)) {
            ModularUI ui = ModularUI.from(menu);
            if (ui != null) {
                ItemStack remaining = ui.quickMoveIntoHost(stack);
                if (remaining.isEmpty()) return true;
                if (remaining != stack && ItemStack.isSameItemSameTags(stack, remaining)
                    && remaining.getCount() < stack.getCount()) {
                    stack.shrink(stack.getCount() - remaining.getCount());
                }
            }
        }
        return original.call(menu, stack, intoPlayer, reverse);
    }
}
