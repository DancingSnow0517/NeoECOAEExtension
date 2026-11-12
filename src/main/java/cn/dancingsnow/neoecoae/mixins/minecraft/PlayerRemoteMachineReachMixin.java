package cn.dancingsnow.neoecoae.mixins.minecraft;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECORemoteMachineReach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A machine opened from the crafting status page stays usable while that one menu is open, wherever the player is. */
@Mixin(Player.class)
public abstract class PlayerRemoteMachineReachMixin {
    @Inject(method = "isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z", at = @At("HEAD"), cancellable = true)
    private void neoecoae$remoteMachine(BlockPos pos, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (ECORemoteMachineReach.allows((Player) (Object) this, pos)) cir.setReturnValue(true);
    }
}
