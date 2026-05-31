package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.crafting.CraftingCPUMenu;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges ECOCraftingCPU into AE2's {@link CraftingCPUMenu} so the right-side
 * crafting status detail panel shows entries for ECO CPUs.
 */
@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin120 extends AEBaseMenu {

    public CraftingCPUMenuMixin120(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Unique
    private ECOCraftingCPU neoecoae$cpu = null;

    @Inject(method = { "cancelCrafting" }, at = { @At("TAIL") })
    public void neoecoae$onCancelCrafting(CallbackInfo ci) {
        if (!this.isClientSide() && this.neoecoae$cpu != null) {
            this.neoecoae$cpu.cancelJob();
        }
    }
}