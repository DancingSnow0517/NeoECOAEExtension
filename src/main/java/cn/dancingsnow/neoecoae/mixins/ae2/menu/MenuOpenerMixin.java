package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.gui.computation.ECOCraftingStatusUI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every AE2 crafting status request passes here; a working ECO host shows only the ECO page instead. */
@Mixin(MenuOpener.class)
public abstract class MenuOpenerMixin {
    @Inject(
        method = "open(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/world/entity/player/Player;Lappeng/menu/locator/MenuHostLocator;Z)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void neoecoae$openEcoCraftingStatus(MenuType<?> type, Player player, MenuHostLocator locator,
            boolean fromSubMenu, CallbackInfoReturnable<Boolean> cir) {
        if (type == CraftingStatusMenu.TYPE && player instanceof ServerPlayer serverPlayer
                && ECOCraftingStatusUI.tryOpen(serverPlayer, locator)) {
            cir.setReturnValue(true);
        }
    }
}
