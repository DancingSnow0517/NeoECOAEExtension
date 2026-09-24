package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete exact order, carrying bounded AE2 projections only at the API boundary. */
public final class ECOExactCraftingPlan implements ICraftingPlan {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private final ICraftingPlan projection;
    private final BigInteger storage;
    private final Map<IPatternDetails, BigInteger> tasks;
    private final Map<AEKey, BigInteger> deferredStock;
    private final Map<AEKey, BigInteger> deferredEmitted;
    private final ECOExecutionPlan execution;

    public static boolean needsSmallerBatch(ECOPlanningResult result) {
        boolean wideTask = result.exactPatternTimes().values().stream()
                .anyMatch(count -> count.toBigInteger().compareTo(MAX) > 0);
        if (!wideTask) return false;
        long taskKinds = result.exactPatternTimes().values().stream().filter(count -> count.signum() > 0).count();
        return taskKinds != 1 || result.components().stream()
                .anyMatch(component -> component.type() == ComponentPlanningResult.Type.CYCLIC);
    }

    public ECOExactCraftingPlan(ECOPlanningResult result, boolean forced) {
        if (!ECOBigOrderAdmission.allows(result, forced)) throw new IllegalArgumentException("Incomplete exact plan");
        storage = result.theoreticalBytes();
        Map<IPatternDetails, Long> counts = new LinkedHashMap<>();
        Map<IPatternDetails, BigInteger> exact = new LinkedHashMap<>();
        result.exactPatternTimes().forEach((pattern, amount) -> {
            if (amount.signum() > 0) {
                exact.put(pattern, amount.toBigInteger());
                counts.put(pattern, bounded(amount.toBigInteger()));
            }
        });
        tasks = Map.copyOf(exact);
        Map<AEKey, PlannerAmount> stockAmounts = new LinkedHashMap<>(result.exactUsedItems());
        for (var component : result.components()) {
            component.stockReservations().forEach((key, amount) -> {
                if (amount != null && amount > 0)
                    stockAmounts.merge(key, PlannerAmount.of(amount), PlannerAmount::max);
            });
        }
        Map<AEKey, BigInteger> stock = new LinkedHashMap<>();
        Map<AEKey, BigInteger> emitted = new LinkedHashMap<>();
        KeyCounter initial = split(stockAmounts, stock);
        KeyCounter initialEmitted = split(result.exactEmittedItems(), emitted);
        if (forced) result.exactMissingItems().forEach((key, amount) ->
                stock.merge(key, amount.toBigInteger(), BigInteger::add));
        deferredStock = Map.copyOf(stock);
        deferredEmitted = Map.copyOf(emitted);
        projection = new CraftingPlan(result.plan().finalOutput(), 0, false,
                result.plan().multiplePaths(), initial, initialEmitted, new KeyCounter(), Map.copyOf(counts));
        boolean wideTask = exact.values().stream().anyMatch(count -> count.compareTo(MAX) > 0);
        if (wideTask) {
            if (needsSmallerBatch(result))
                throw new IllegalArgumentException("Exact task count exceeds 1.20.1 runtime limit");
            execution = null;
        } else {
            ECOPlanningResult interpreted = new ECOPlanningResult(PlanningStatus.SUCCESS,
                    (CraftingPlan) projection, result.trace(), result.cycles(), result.components(),
                    result.executionComponentOrder(), result.calculationNanos(), result.planningId(), result.provenance());
            if (interpreted.executionPlanError() != null)
                throw new IllegalArgumentException(interpreted.executionPlanError());
            execution = interpreted.executionContract().executionPlan();
        }
    }

    private static KeyCounter split(Map<AEKey, PlannerAmount> amounts, Map<AEKey, BigInteger> deferred) {
        KeyCounter initial = new KeyCounter();
        amounts.forEach((key, amount) -> {
            if (amount.signum() <= 0) return;
            if (amount.fitsLong()) initial.set(key, amount.longValueExact());
            else {
                initial.set(key, Long.MAX_VALUE);
                deferred.put(key, amount.toBigInteger().subtract(MAX));
            }
        });
        return initial;
    }

    public static long bounded(BigInteger amount) {
        return amount.min(MAX).longValueExact();
    }

    public BigInteger theoreticalBytes() { return storage; }
    public Map<IPatternDetails, BigInteger> exactTasks() { return tasks; }
    public Map<AEKey, BigInteger> deferredStock() { return deferredStock; }
    public Map<AEKey, BigInteger> deferredEmitted() { return deferredEmitted; }
    public ECOExecutionPlan execution() { return execution; }
    @Override public GenericStack finalOutput() { return projection.finalOutput(); }
    @Override public long bytes() { return 0; }
    @Override public boolean simulation() { return false; }
    @Override public boolean multiplePaths() { return projection.multiplePaths(); }
    @Override public KeyCounter usedItems() { return projection.usedItems(); }
    @Override public KeyCounter emittedItems() { return projection.emittedItems(); }
    @Override public KeyCounter missingItems() { return projection.missingItems(); }
    @Override public Map<IPatternDetails, Long> patternTimes() { return projection.patternTimes(); }
}
