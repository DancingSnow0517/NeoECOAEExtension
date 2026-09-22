package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Prevents storage totals from disappearing when an unbounded stack is combined with another inventory. */
@Mixin(NetworkStorage.class)
public class NetworkStorageMixin {
    @WrapOperation(
        method = "extract",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J")
    )
    private long neoecoae$filterCreativeInput(MEStorage storage, appeng.api.stacks.AEKey key, long amount,
            appeng.api.config.Actionable mode, appeng.api.networking.security.IActionSource source,
            Operation<Long> original) {
        return cn.dancingsnow.neoecoae.impl.storage.transfer.ECOCreativeExtractionFilter.extractSource(
            storage, key, mode, () -> original.call(storage, key, amount, mode, source));
    }

    @WrapOperation(
        method = "getAvailableStacks",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"
        )
    )
    private void neoecoae$getAvailableStacksSaturated(
        MEStorage storage, KeyCounter output, Operation<Void> original
    ) {
        // Each mounted inventory writes into an isolated counter. Merging it ourselves makes
        // Long.MAX_VALUE + a normal disk amount saturate instead of wrapping negative, regardless
        // of mount order (the infinite resource matrix may be visited first or last).
        KeyCounter contribution = new KeyCounter();
        original.call(storage, contribution);
        ExactAmountCollector.observe(storage, contribution);
        SaturatingStackAccumulator.addAll(output, contribution);
    }
}
