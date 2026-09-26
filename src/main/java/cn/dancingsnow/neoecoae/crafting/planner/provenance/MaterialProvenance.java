package cn.dancingsnow.neoecoae.crafting.planner.provenance;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mutable solve-local ledger for material sources and unconsumed byproduct credit. */
public final class MaterialProvenance {
    private final Map<AEKey, Map<MaterialSource, PlannerAmount>> suppliers = new LinkedHashMap<>();
    private final Map<AEKey, Map<IPatternDetails, PlannerAmount>> creditLedger = new LinkedHashMap<>();
    private final Map<UUID, MaterialDemand> demands = new LinkedHashMap<>();
    private final List<SupplyAllocation> allocations = new ArrayList<>();
    private final Map<UUID, PlannerAmount> allocated = new LinkedHashMap<>();

    public void register(MaterialDemand demand) {
        MaterialDemand previous = demands.putIfAbsent(demand.id(), demand);
        if (previous != null && !previous.equals(demand)) {
            throw new IllegalStateException("Conflicting demand identity: " + demand.id());
        }
    }

    public PlannerAmount remaining(MaterialDemand demand) {
        if (!demand.equals(demands.get(demand.id()))) {
            throw new IllegalStateException("Unregistered demand: " + demand.id());
        }
        return demand.amount().subtract(allocated.getOrDefault(demand.id(), PlannerAmount.ZERO));
    }

    public PlannerAmount pendingAmount(AEKey key) {
        PlannerAmount result = PlannerAmount.ZERO;
        for (MaterialDemand demand : demands.values()) {
            if (demand.key().equals(key)) result = result.add(remaining(demand));
        }
        return result;
    }

    /** Apportions an aggregated numeric draw to the original demands in registration order. */
    public void allocatePending(AEKey key, AEKey material, MaterialSource source, PlannerAmount amount) {
        requirePositive(amount);
        if (pendingAmount(key).compareTo(amount) < 0) {
            throw new IllegalStateException("Supply exceeds pending demand: " + key + " amount=" + amount);
        }
        PlannerAmount left = amount;
        for (MaterialDemand demand : demands.values()) {
            if (!demand.key().equals(key)) continue;
            PlannerAmount take = remaining(demand).min(left);
            if (take.signum() > 0) allocate(demand, material, source, take);
            left = left.subtract(take);
            if (left.isZero()) break;
        }
    }

    public void consumePendingCredit(AEKey key, PlannerAmount amount) {
        requirePositive(amount);
        if (pendingAmount(key).compareTo(amount) < 0) {
            throw new IllegalStateException("Credit exceeds pending demand: " + key);
        }
        MaterialProvenance candidate = copy();
        Map<IPatternDetails, PlannerAmount> consumed = candidate.consumeCredit(key, amount);
        // The diagnostic draw was already recorded. Remove it before allocating the exact portions.
        consumed.forEach((pattern, count) -> {
            MaterialSource source = new MaterialSource.PatternOutput(pattern, false);
            Map<MaterialSource, PlannerAmount> sources = candidate.suppliers.get(key);
            PlannerAmount left = sources.get(source).subtract(count);
            if (left.isZero()) sources.remove(source); else sources.put(source, left);
            candidate.allocatePending(key, key, source, count);
        });
        replaceWith(candidate);
    }

    /** An external solve's root demand belongs to its requesting cycle, not to the player's final output. */
    public MaterialProvenance forCycleBoundary(int componentId) {
        MaterialProvenance copy = copy();
        copy.demands.replaceAll((id, demand) -> demand.kind() == MaterialDemand.Kind.GOAL
            ? new MaterialDemand(id, MaterialDemand.Kind.CYCLE_BOUNDARY, null, -1, componentId,
                demand.key(), demand.amount())
            : demand);
        return copy;
    }

    /** Resolves a deferred numeric promise after the owning cycle has selected its physical work. */
    public void resolveDeferred(AEKey key, IPatternDetails pattern, MaterialSource resolved) {
        for (int i = 0; i < allocations.size(); i++) {
            SupplyAllocation allocation = allocations.get(i);
            if (!allocation.material().equals(key)
                    || !(allocation.source() instanceof MaterialSource.PatternOutput output)
                    || !output.primary() || output.pattern() != pattern) continue;
            Map<MaterialSource, PlannerAmount> sources = suppliers.get(key);
            PlannerAmount left = sources.get(allocation.source()).subtract(allocation.amount());
            if (left.isZero()) sources.remove(allocation.source()); else sources.put(allocation.source(), left);
            sources.merge(resolved, allocation.amount(), PlannerAmount::add);
            allocations.set(i, new SupplyAllocation(allocation.demandId(), key, resolved, allocation.amount()));
        }
    }

    /** A delegated cycle that needs no firings supplies its output from reserved stock. */
    public void resolveStockSatisfiedCycle(int componentId) {
        MaterialSource.CycleOutput cycleOutput = new MaterialSource.CycleOutput(componentId);
        for (int i = 0; i < allocations.size(); i++) {
            SupplyAllocation allocation = allocations.get(i);
            if (!cycleOutput.equals(allocation.source())) continue;
            Map<MaterialSource, PlannerAmount> sources = suppliers.get(allocation.material());
            PlannerAmount attributed = sources == null ? null : sources.get(cycleOutput);
            if (attributed == null || attributed.compareTo(allocation.amount()) < 0) {
                throw new IllegalStateException("Cycle output attribution exceeds its source total: " + allocation);
            }
            PlannerAmount left = attributed.subtract(allocation.amount());
            if (left.isZero()) sources.remove(cycleOutput); else sources.put(cycleOutput, left);
            sources.merge(MaterialSource.Stock.INSTANCE, allocation.amount(), PlannerAmount::add);
            allocations.set(i, new SupplyAllocation(allocation.demandId(), allocation.material(),
                MaterialSource.Stock.INSTANCE, allocation.amount()));
        }
    }

    /** The material may be a concrete component-sensitive alternative to the demand's key. */
    public void allocate(MaterialDemand demand, AEKey material, MaterialSource source, PlannerAmount amount) {
        SupplyAllocation allocation = new SupplyAllocation(demand.id(), material, source, amount);
        if (remaining(demand).compareTo(amount) < 0) {
            throw new IllegalStateException("Demand over-allocation: " + demand.id());
        }
        supplied(material, source, amount);
        allocations.add(allocation);
        allocated.merge(demand.id(), amount, PlannerAmount::add);
    }

    /** Consumes credit and assigns it to one demand atomically. Unused credit creates no allocation. */
    public void consumeCredit(MaterialDemand demand, AEKey material, PlannerAmount amount) {
        requirePositive(amount);
        if (remaining(demand).compareTo(amount) < 0) {
            throw new IllegalStateException("Demand over-allocation: " + demand.id());
        }
        MaterialProvenance candidate = copy();
        Map<IPatternDetails, PlannerAmount> consumed = candidate.consumeCredit(material, amount);
        // consumeCredit already updated the diagnostic source totals.
        consumed.forEach((pattern, count) -> {
            candidate.allocations.add(new SupplyAllocation(demand.id(), material,
                new MaterialSource.PatternOutput(pattern, false), count));
            candidate.allocated.merge(demand.id(), count, PlannerAmount::add);
        });
        replaceWith(candidate);
    }

    public void credit(AEKey key, IPatternDetails pattern, PlannerAmount amount) {
        requirePositive(amount);
        creditLedger.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
            .merge(pattern, amount, PlannerAmount::add);
    }

    /** Consumes exactly {@code amount} in insertion order, or fails before changing the ledger. */
    public Map<IPatternDetails, PlannerAmount> consumeCredit(AEKey key, PlannerAmount amount) {
        requirePositive(amount);
        Map<IPatternDetails, PlannerAmount> credits = creditLedger.get(key);
        PlannerAmount available = PlannerAmount.ZERO;
        if (credits != null) {
            for (PlannerAmount credit : credits.values()) available = available.add(credit);
        }
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException("Crafted credit and provenance ledger diverged for " + key);
        }
        Map<IPatternDetails, PlannerAmount> consumed = new LinkedHashMap<>();
        PlannerAmount remaining = amount;
        var iterator = credits.entrySet().iterator();
        while (iterator.hasNext() && remaining.signum() > 0) {
            var entry = iterator.next();
            PlannerAmount drawn = remaining.min(entry.getValue());
            if (drawn.signum() <= 0) continue;
            consumed.put(entry.getKey(), drawn);
            supplied(key, new MaterialSource.PatternOutput(entry.getKey(), false), drawn);
            remaining = remaining.subtract(drawn);
            PlannerAmount left = entry.getValue().subtract(drawn);
            if (left.isZero()) iterator.remove(); else entry.setValue(left);
        }
        if (credits.isEmpty()) creditLedger.remove(key);
        if (remaining.signum() > 0) {
            throw new IllegalStateException("Crafted credit and provenance ledger diverged for " + key);
        }
        return Map.copyOf(consumed);
    }

    public void supplied(AEKey key, MaterialSource source, PlannerAmount amount) {
        requirePositive(amount);
        suppliers.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
            .merge(source, amount, PlannerAmount::add);
    }

    public MaterialProvenance copy() {
        MaterialProvenance copy = new MaterialProvenance();
        copy.mergeFrom(this);
        return copy;
    }

    public void mergeFrom(MaterialProvenance other) {
        mergeSuppliersFrom(other);
        other.creditLedger.forEach((key, credits) -> credits.forEach((pattern, amount) ->
            creditLedger.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .merge(pattern, amount, PlannerAmount::add)));
    }

    public void mergeSuppliersFrom(MaterialProvenance other) {
        // Validate before mutating either source totals or demand allocations.
        for (MaterialDemand demand : other.demands.values()) {
            MaterialDemand previous = demands.get(demand.id());
            if (previous != null && !previous.equals(demand)) {
                throw new IllegalStateException("Conflicting demand identity: " + demand.id());
            }
            PlannerAmount combined = allocated.getOrDefault(demand.id(), PlannerAmount.ZERO)
                .add(other.allocated.getOrDefault(demand.id(), PlannerAmount.ZERO));
            if (combined.compareTo(demand.amount()) > 0) {
                throw new IllegalStateException("Demand over-allocation during merge: " + demand.id());
            }
        }
        demands.putAll(other.demands);
        allocations.addAll(other.allocations);
        other.allocated.forEach((id, amount) -> allocated.merge(id, amount, PlannerAmount::add));
        other.suppliers.forEach((key, sources) -> sources.forEach((source, amount) ->
            suppliers.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .merge(source, amount, PlannerAmount::add)));
    }

    public void replaceWith(MaterialProvenance other) {
        if (other == this) return;
        suppliers.clear();
        creditLedger.clear();
        demands.clear();
        allocations.clear();
        allocated.clear();
        mergeFrom(other);
    }

    public ExecutionProvenance freeze() {
        return new ExecutionProvenance(suppliers, demands, allocations);
    }

    private static void requirePositive(PlannerAmount amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}
