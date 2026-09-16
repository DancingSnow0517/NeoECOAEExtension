package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.api.crafting.Ae2LtCraftingIntegration;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Thin ECO adapter over AE2LT's stable crafting integration API. */
public final class ECOOverloadCpuAccountingBridge {
    private ECOOverloadCpuAccountingBridge() {}

    public static Ae2LtCraftingIntegration.OverloadRegistration prepare(Object cpuLogic,
            IPatternDetails originalPattern, UUID jobId, @Nullable AEKey finalOutput) {
        return Ae2LtCraftingIntegration.prepareOverload(cpuLogic, originalPattern, jobId, finalOutput);
    }
}
