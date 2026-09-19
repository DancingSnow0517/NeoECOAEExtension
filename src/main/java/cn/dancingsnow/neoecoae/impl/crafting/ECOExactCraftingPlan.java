package cn.dancingsnow.neoecoae.impl.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** One complete order. Long projections are adapter values, never the authoritative task counters. */
public final class ECOExactCraftingPlan implements ICraftingPlan {
    private final ICraftingPlan projection;
    private final BigInteger storage;
    private final Map<IPatternDetails, BigInteger> tasks;
    private final Map<AEKey, BigInteger> deferredStock;
    private final Map<AEKey, BigInteger> deferredEmitted;
    private final ECOExecutionPlan execution;

    public ECOExactCraftingPlan(ECOPlanningResult result, boolean forced) {
        if (!cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission.allows(result, forced))
            throw new IllegalArgumentException("Incomplete exact plan");
        storage = result.theoreticalBytes();
        var counts = new LinkedHashMap<IPatternDetails, Long>();
        var exact = new LinkedHashMap<IPatternDetails, BigInteger>();
        result.exactPatternTimes().forEach((pattern, amount) -> {
            if (amount.signum() > 0) {
                exact.put(pattern, amount.toBigInteger());
                counts.put(pattern, bounded(amount.toBigInteger()));
            }
        });
        tasks = Map.copyOf(exact);
        var stockAmounts = new LinkedHashMap<AEKey, PlannerAmount>();
        stockAmounts.putAll(result.exactUsedItems());
        // Cycle solvers record startup reservations on the component. They are part of the same
        // order-wide material ledger even when the normal used-items projection is empty.
        for (var component : result.components()) {
            component.stockReservations().forEach((key, amount) -> {
                if (amount == null || amount <= 0) return;
                stockAmounts.merge(key, PlannerAmount.of(amount), PlannerAmount::max);
            });
        }
        var stock = new LinkedHashMap<AEKey, BigInteger>();
        var emitted = new LinkedHashMap<AEKey, BigInteger>();
        KeyCounter initial = split(stockAmounts, stock);
        KeyCounter initialEmitted = split(result.exactEmittedItems(), emitted);
        if (forced) result.exactMissingItems().forEach((key, amount) ->
            stock.merge(key, amount.toBigInteger(), BigInteger::add));
        deferredStock = Map.copyOf(stock);
        deferredEmitted = Map.copyOf(emitted);
        projection = new appeng.crafting.CraftingPlan(result.plan().finalOutput(), 0,
            false, result.plan().multiplePaths(), initial, initialEmitted, new KeyCounter(), Map.copyOf(counts));
        // Interpret the full selected graph once. Cycle witnesses and seed protections remain mandatory.
        var interpreted = new ECOPlanningResult(PlanningStatus.SUCCESS,
            (appeng.crafting.CraftingPlan) projection, result.trace(), result.cycles(), result.components(),
            result.executionComponentOrder(), result.calculationNanos(), result.planningId(), result.provenance());
        var contract = interpreted.executionContract();
        if (interpreted.executionPlanError() != null)
            throw new IllegalArgumentException(interpreted.executionPlanError());
        execution = contract.executionPlan();
    }

    /** Summary-only restored CPU reservation; the live execution ledger is restored separately. */
    public ECOExactCraftingPlan(ICraftingPlan projection, BigInteger storage) {
        this.projection = projection;
        this.storage = storage;
        this.tasks = Map.of(); this.deferredStock = Map.of(); this.deferredEmitted = Map.of();
        this.execution = null;
    }

    private static KeyCounter split(Map<AEKey, PlannerAmount> amounts, Map<AEKey, BigInteger> deferred) {
        var initial = new KeyCounter();
        amounts.forEach((key, amount) -> {
            if (amount.signum() <= 0) return;
            if (amount.fitsLong()) {
                initial.set(key, amount.longValueExact());
            } else {
                // The initial window is physically extracted at admission. Defer only the remainder,
                // otherwise refilling would debit that first window a second time.
                initial.set(key, Long.MAX_VALUE);
                deferred.put(key, amount.toBigInteger().subtract(BigInteger.valueOf(Long.MAX_VALUE)));
            }
        });
        return initial;
    }

    public static long bounded(BigInteger amount) { return amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(); }
    public BigInteger theoreticalBytes() { return storage; }
    public Map<IPatternDetails, BigInteger> exactTasks() { return tasks; }
    public Map<AEKey, BigInteger> deferredStock() { return deferredStock; }
    public Map<AEKey, BigInteger> deferredEmitted() { return deferredEmitted; }
    public ECOExecutionPlan execution() { return execution; }
    @Override public GenericStack finalOutput() { return projection.finalOutput(); }
    // Explicit large orders retain their existing exemption from AE2 byte reservations.
    @Override public long bytes() { return 0; }
    @Override public boolean simulation() { return false; }
    @Override public boolean multiplePaths() { return projection.multiplePaths(); }
    @Override public KeyCounter usedItems() { return projection.usedItems(); }
    @Override public KeyCounter emittedItems() { return projection.emittedItems(); }
    @Override public KeyCounter missingItems() { return projection.missingItems(); }
    @Override public Map<IPatternDetails, Long> patternTimes() { return projection.patternTimes(); }
}
