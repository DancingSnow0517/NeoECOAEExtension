package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import com.google.common.collect.ImmutableSet;
import java.util.WeakHashMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingStatusMenu.class, remap = false)
public abstract class CraftingStatusMenuMixin extends CraftingCPUMenu {
    @Shadow
    @Final
    private WeakHashMap<ICraftingCPU, Integer> cpuSerialMap;

    @Shadow
    @Final
    private ImmutableSet<ICraftingCPU> lastCpuSet;

    public CraftingStatusMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "createCpuList", at = @At("RETURN"))
    private void neoecoae$setCpuOverlays(CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuList> cir) {
        // createCpuList sorts entries after constructing them. Resolve the overlay from the
        // stable CPU serial after that sort so unrelated CPUs cannot shift the badge to another row.
        for (var entry : cir.getReturnValue().cpus()) {
            for (var cpu : lastCpuSet) {
                if (cpuSerialMap.getOrDefault(cpu, -1) != entry.serial()) {
                    continue;
                }
                var overlay =
                        cpu instanceof ECOCraftingCPU ecoCpu ? ecoCpu.getTier().getCPUOverlayTexture() : null;
                IOverlayTextureHolder.of(entry).neoecoae$setOverlay(overlay);
                break;
            }
        }
    }
}
