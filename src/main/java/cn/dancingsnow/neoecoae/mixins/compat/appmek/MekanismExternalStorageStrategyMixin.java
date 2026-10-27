package cn.dancingsnow.neoecoae.mixins.compat.appmek;

import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.slurry.ISlurryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Rejects misadvertised capabilities before the facade can call a typed chemical bridge method. */
@Pseudo
@Mixin(targets = "me.ramidzkh.mekae2.ae2.stack.MekanismExternalStorageStrategy", remap = false)
public abstract class MekanismExternalStorageStrategyMixin {
    @ModifyArg(method = "createWrapper", at = @At(value = "INVOKE",
            target = "Lme/ramidzkh/mekae2/ae2/stack/ChemicalHandlerFacade;<init>([Lmekanism/api/chemical/IChemicalHandler;ZLjava/lang/Runnable;)V"),
            index = 0)
    private IChemicalHandler<?, ?>[] neoecoae$validateHandlerTypes(IChemicalHandler<?, ?>[] handlers) {
        // Source Block 1.2.1 advertises its IGasHandler for every chemical capability.
        // A rejected slot must stay empty, so both simulation and commit report zero
        // and AE2 can continue routing without losing the resource being returned.
        IChemicalHandler<?, ?>[] checked = handlers.clone();
        if (!(checked[MekanismKey.GAS] instanceof IGasHandler)) checked[MekanismKey.GAS] = null;
        if (!(checked[MekanismKey.INFUSION] instanceof IInfusionHandler)) checked[MekanismKey.INFUSION] = null;
        if (!(checked[MekanismKey.PIGMENT] instanceof IPigmentHandler)) checked[MekanismKey.PIGMENT] = null;
        if (!(checked[MekanismKey.SLURRY] instanceof ISlurryHandler)) checked[MekanismKey.SLURRY] = null;
        return checked;
    }
}
