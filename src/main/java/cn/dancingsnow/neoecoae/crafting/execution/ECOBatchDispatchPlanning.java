package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.crafting.execution.batch.*;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import java.math.BigInteger;
import java.util.Map;

/** Captures live CPU limits without taking ownership of any material or energy. */
final class ECOBatchDispatchPlanning {
    private ECOBatchDispatchPlanning() {}

    static ECOBatchPlan plan(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            long capacity, long quota, double singlePower, IEnergyService energy, ECOBatchMode mode) {
        var inputs = ECOFastPathStacks.copyCounters(request.inputs());
        var outputs = ECOFastPathStacks.copyCounter(request.outputs());
        var remainders = ECOFastPathStacks.copyCounter(request.remainders());
        long arithmetic = ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(inputs, outputs, remainders);
        long material = request.allowedCrafts();
        var protectedSeeds = request.job().executionRuntime == null ? Map.<appeng.api.stacks.AEKey, Long>of()
                : request.job().executionRuntime.protectedStartupSeed(request.candidate());
        for (var input : inputs) {
            var available = ECOExactInventory.amount(request.inventory(), input.what())
                    .subtract(BigInteger.valueOf(protectedSeeds.getOrDefault(input.what(), 0L))).max(BigInteger.ZERO);
            material = Math.min(material, available.divide(BigInteger.valueOf(input.amount()))
                    .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact());
        }
        long waiting = ECOCraftingFastPathDispatcher.safeWaitingBatch(request, singlePower);
        long upper = Math.min(request.allowedCrafts(), Math.min(material,
                Math.min(Math.max(0, capacity), Math.min(Math.max(0, quota), Math.min(arithmetic, waiting)))));
        if (mode == ECOBatchMode.SINGLE) upper = Math.min(upper, 1L);
        long affordable = ECOBatchCraftingHelper.maxAffordableCrafts(singlePower, upper,
                amount -> energy.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        return new ECOBatchPlanner().plan(new ECOBatchPlanRequest(ECOPatternIdentity.of(request.pattern(), provider),
                request.allowedCrafts(), material, Math.max(0, capacity), affordable, waiting,
                Math.max(0, quota), arithmetic, Long.MAX_VALUE, Long.MAX_VALUE, mode));
    }
}
