package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the selected side inside the successful call, before AE2 clears sendDirection. */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderProcessingTargetMixin implements ECOProcessingTargetProvider {
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow private Direction sendDirection;
    @Unique private Target neoecoae$processingTarget;

    @Override public Target neoecoae$getProcessingTarget() { return neoecoae$processingTarget; }

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void neoecoae$resetTarget(IPatternDetails pattern, KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        neoecoae$processingTarget = null;
    }

    @Unique private void neoecoae$recordSide(Direction direction) {
        var be = host.getBlockEntity();
        if (direction != null && be.getLevel() != null) neoecoae$processingTarget = new Target(
            be.getLevel().dimension().location(), be.getBlockPos().relative(direction));
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
        "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z"))
    private boolean neoecoae$recordMachine(ICraftingMachine machine, IPatternDetails pattern, KeyCounter[] inputs,
            Direction side, Operation<Boolean> original) {
        boolean accepted = original.call(machine, pattern, inputs, side);
        if (accepted) neoecoae$recordSide(side.getOpposite());
        return accepted;
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
        "Lappeng/helpers/patternprovider/PatternProviderLogic;sendStacksOut()Z"))
    private boolean neoecoae$recordInventory(PatternProviderLogic logic, Operation<Boolean> original) {
        neoecoae$recordSide(sendDirection);
        return original.call(logic);
    }
}
