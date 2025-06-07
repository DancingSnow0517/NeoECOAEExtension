package cn.dancingsnow.neoecoae.mixins;

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
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

@Debug(export = true)
@Mixin(CraftingStatusMenu.class)
public class CraftingStatusMenuMixin extends CraftingCPUMenu {

    public CraftingStatusMenuMixin(MenuType<?> menuType, int id, Inventory ip, Object te) {
        super(menuType, id, ip, te);
    }

    @WrapOperation(
        method = "createCpuList",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"
        )
    )
    private boolean wrapAdd(ArrayList instance, Object e, Operation<Boolean> original, @Local ICraftingCPU cpu, @Local int serial) {
        if (cpu instanceof ECOCraftingCPU ecoCPU) {
            IOverlayTextureHolder.of((CraftingStatusMenu.CraftingCpuListEntry) e).neoecoae$setOverlay(ecoCPU.getTier().getCPUOverlayTexture());
        }
        return original.call(instance, e);
    }
}
