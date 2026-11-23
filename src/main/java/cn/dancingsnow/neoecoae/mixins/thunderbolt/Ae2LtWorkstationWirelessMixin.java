package cn.dancingsnow.neoecoae.mixins.thunderbolt;

import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The shared workstation entity accepts a direct wireless link only in standalone mode. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.grid.wirelesslink.MultiblockLinkReadiness", remap = false)
public abstract class Ae2LtWorkstationWirelessMixin {
    @Inject(method = "canKeepVirtualConnection(Lappeng/api/networking/IGridNode;)Z",
        at = @At("HEAD"), cancellable = true)
    private static void neoecoae$standaloneWorkstation(IGridNode node, CallbackInfoReturnable<Boolean> callback) {
        if (node.getOwner() instanceof ECOLargeIntegratedWorkingStationBlockEntity workstation) {
            callback.setReturnValue(!workstation.isFormed());
        }
    }
}
