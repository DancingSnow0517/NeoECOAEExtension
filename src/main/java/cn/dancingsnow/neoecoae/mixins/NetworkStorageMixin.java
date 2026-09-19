package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin {
    @WrapOperation(
            method = "getAvailableStacks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"),
            require = 1)
    private void neoecoae$listContribution(MEStorage storage, KeyCounter output, Operation<Void> original) {
        KeyCounter contribution = new KeyCounter();
        ExactAmountCollector.contribution(storage, contribution, () -> original.call(storage, contribution));
        SaturatingStackAccumulator.addAll(output, contribution);
    }
}
