package cn.dancingsnow.neoecoae.mixins.compat.gregtech;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechProcessingDispatch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Keep GT's pattern lookup, worker assignment and notifications native while expanding sparse inputs fully. */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine", remap = false)
public abstract class GTMEPatternBufferMixin implements ECOGregTechProcessingDispatch.BatchInputProvider {
    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine$InternalSlot;"
                    + "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)V"))
    private void neoecoae$pushCompleteBatch(@Coerce Object slot, IPatternDetails pattern, KeyCounter[] inputs,
            Operation<Void> original) {
        original.call(slot, ECOGregTechProcessingDispatch.bufferExecutionPattern(pattern, inputs), inputs);
    }
}
