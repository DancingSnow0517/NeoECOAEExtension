package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.crafting.execution.batch.*;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.math.BigInteger;
import java.util.Map;

/** Captures live CPU limits without taking ownership of any material or energy. */
final class ECOBatchDispatchPlanning {
    private static final ECOBatchPlanner PLANNER = new ECOBatchPlanner();

    private ECOBatchDispatchPlanning() {}

    static ECOBatchPlan plan(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            long capacity, long quota, double singlePower, IEnergyService energy, ECOBatchMode mode) {
        return prepare(request, provider).plan(capacity, quota, singlePower, energy, mode);
    }

    static Prepared prepare(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        return new Prepared(request, provider);
    }

    /** Reuses recipe structure within one visit; all mutable resource limits are read for every offer. */
    static final class Prepared {
        private final ECOCraftingDispatchRequest request;
        private final ECOPatternIdentity identity;
        private final Object2LongOpenHashMap<AEKey> inputs = new Object2LongOpenHashMap<>();
        private final Object2LongOpenHashMap<AEKey> waitingPerCraft = new Object2LongOpenHashMap<>();
        private final long arithmetic;

        private Prepared(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
            this.request = request;
            this.identity = ECOPatternIdentity.of(request.pattern(), provider);
            long limit;
            try {
                // These are lookup totals only. Physical counters retain the pattern's sparse slot order.
                for (var counter : request.inputs()) accumulate(counter, inputs);
                accumulate(request.outputs(), waitingPerCraft);
                accumulate(request.remainders(), waitingPerCraft);
                limit = waitingPerCraft.size() > ECOBatchCraftingHelper.MAX_BATCH_STACK_ENTRIES ? 0L
                        : Math.min(arithmeticLimit(inputs), arithmeticLimit(waitingPerCraft));
            } catch (ArithmeticException overflow) {
                limit = 0L;
            }
            arithmetic = limit;
        }

        private static void accumulate(KeyCounter counter, Object2LongOpenHashMap<AEKey> totals) {
            if (counter != null) {
                for (var entry : counter) {
                    long count = entry.getLongValue();
                    if (count <= 0) continue;
                    totals.put(entry.getKey(), Math.addExact(totals.getLong(entry.getKey()), count));
                }
            }
        }

        private static long arithmeticLimit(Object2LongOpenHashMap<AEKey> totals) {
            if (totals.size() > ECOBatchCraftingHelper.MAX_BATCH_STACK_ENTRIES) return 0L;
            long limit = Long.MAX_VALUE;
            for (var it = totals.values().iterator(); it.hasNext();) {
                limit = Math.min(limit, ECOBatchCraftingHelper.maxBatchSizeForAmount(it.nextLong()));
            }
            return limit;
        }

        ECOBatchPlan plan(long capacity, long quota, double singlePower, IEnergyService energy, ECOBatchMode mode) {
            long upper = Math.min(request.allowedCrafts(), Math.min(arithmetic,
                    Math.min(Math.max(0, capacity), Math.max(0, quota))));
            if (mode == ECOBatchMode.SINGLE) upper = Math.min(upper, 1L);
            if (upper <= 0) return null;
            long material = upper;
            var protectedSeeds = request.job().executionRuntime == null ? Map.<AEKey, Long>of()
                    : request.job().executionRuntime.protectedStartupSeed(request.candidate());
            for (var it = inputs.object2LongEntrySet().fastIterator(); it.hasNext();) {
                var input = it.next();
                long seed = protectedSeeds.getOrDefault(input.getKey(), 0L);
                long available;
                if (request.inventory() instanceof ECOExactInventory exact && exact.isEnabled()) {
                    var amount = exact.amount(input.getKey()).subtract(BigInteger.valueOf(seed)).max(BigInteger.ZERO);
                    // Arithmetic already bounds each aggregate debit to long, so excess stock cannot limit it.
                    if (amount.bitLength() > 63) continue;
                    available = amount.longValue();
                } else {
                    available = Math.max(0L, Math.max(0L, request.inventory().list.get(input.getKey())) - seed);
                }
                material = Math.min(material, available / input.getLongValue());
                if (material == 0) return null;
            }
            long waiting = Math.min(upper, ECOCraftingEnergyTransaction.maxSafeCrafts(singlePower));
            if (!request.job().exactOrder) {
                for (var it = waitingPerCraft.object2LongEntrySet().fastIterator(); it.hasNext();) {
                    var output = it.next();
                    long stored = request.job().waitingFor.list.get(output.getKey());
                    if (stored < 0) return null;
                    waiting = Math.min(waiting, (Long.MAX_VALUE - stored) / output.getLongValue());
                }
            }
            long affordable = ECOBatchCraftingHelper.maxAffordableCrafts(singlePower, Math.min(material, waiting),
                    amount -> energy.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG));
            return PLANNER.plan(new ECOBatchPlanRequest(identity, request.allowedCrafts(), material,
                    Math.max(0, capacity), affordable, waiting, Math.max(0, quota), arithmetic,
                    Long.MAX_VALUE, Long.MAX_VALUE, mode));
        }
    }
}
