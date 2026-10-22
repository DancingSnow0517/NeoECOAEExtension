package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.core.localization.Tooltips;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Backports AE2 15.4.11's byte unit table fix when the broken 15.4.10 table is present. */
@Mixin(value = Tooltips.class, remap = false)
public abstract class TooltipsByteUnitsMixin {
    @Shadow @Final @Mutable public static long[] BYTE_NUMS;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void neoecoae$completeByteUnits(CallbackInfo ci) {
        // Match the known upstream defect; preserve already corrected tables, including forks.
        if (BYTE_NUMS.length == 4
                && BYTE_NUMS[0] == (1L << 10)
                && BYTE_NUMS[1] == (1L << 20)
                && BYTE_NUMS[2] == (1L << 30)
                && BYTE_NUMS[3] == (1L << 30)) {
            BYTE_NUMS = new long[] {1L << 10, 1L << 20, 1L << 30, 1L << 40, 1L << 50, 1L << 60};
        }
    }
}
