package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

@Mixin(CraftingStatusMenu.class)
public abstract class CraftingStatusMenuMixin120 extends CraftingCPUMenu {
    public CraftingStatusMenuMixin120(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @WrapOperation(
        method = "createCpuList",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z")
    )
    private boolean neoecoae$setCpuOverlay(
        ArrayList<CraftingStatusMenu.CraftingCpuListEntry> entries,
        Object entry,
        Operation<Boolean> original,
        @Local ICraftingCPU cpu
    ) {
        if (entry instanceof CraftingStatusMenu.CraftingCpuListEntry listEntry && cpu instanceof ECOCraftingCPU ecoCpu) {
            IOverlayTextureHolder.of(listEntry).neoecoae$setOverlay(ecoCpu.getTier().getCPUOverlayTexture());
        }
        return original.call(entries, entry);
    }
}
