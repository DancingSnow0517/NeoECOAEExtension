package cn.dancingsnow.neoecoae.mixins.minecraft;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECORemoteMachineReach;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A machine opened from the crafting status page stays usable while that one menu is open, wherever the player is.
 * 1.20.1 has no shared block-interaction reach check, so the exemption applies where the server closes stale menus.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRemoteMachineReachMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean neoecoae$remoteMachine(AbstractContainerMenu menu, Player player, Operation<Boolean> original) {
        return original.call(menu, player) || ECORemoteMachineReach.allowsMenu(player, menu);
    }
}
