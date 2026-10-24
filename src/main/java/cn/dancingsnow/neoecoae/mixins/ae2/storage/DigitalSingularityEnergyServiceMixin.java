package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.me.Grid;
import appeng.me.service.EnergyService;
import cn.dancingsnow.neoecoae.integration.biggerae2.BiggerAE2SingularityPowerService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EnergyService.class, remap = false)
public abstract class DigitalSingularityEnergyServiceMixin {
    @Shadow @Final private Grid grid;

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void neoecoae$refreshSingularityPower(CallbackInfo ci) {
        grid.getService(BiggerAE2SingularityPowerService.class).refresh();
    }

    // Cover both the advertised demand and AE2's successful-payment check. Keep the surcharge
    // separate from drainPerTick: subtracting a huge prior bill would lose the ordinary idle drain.
    @ModifyExpressionValue(method = {"getIdlePowerUsage", "onServerEndTick"},
        at = @At(value = "FIELD", target = "Lappeng/me/service/EnergyService;drainPerTick:D",
            opcode = Opcodes.GETFIELD), require = 3, expect = 3, allow = 3)
    private double neoecoae$includeSingularityPower(double ordinaryDrain) {
        return ordinaryDrain + grid.getService(BiggerAE2SingularityPowerService.class).getAdditionalIdleDrain();
    }
}
