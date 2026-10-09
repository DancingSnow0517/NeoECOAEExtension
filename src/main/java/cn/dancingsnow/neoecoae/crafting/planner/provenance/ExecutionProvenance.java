package cn.dancingsnow.neoecoae.crafting.planner.provenance;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;

/** Material attribution passed from numeric planning to phase construction. */
public record ExecutionProvenance(Map<AEKey, Map<MaterialSource, PlannerAmount>> suppliers,
        Map<UUID, MaterialDemand> demands, List<SupplyAllocation> allocations) {
    public static final ExecutionProvenance ABSENT = new ExecutionProvenance(Map.of());

    /** Compatibility representation; it cannot establish demand-level completeness. */
    public ExecutionProvenance(Map<AEKey, Map<MaterialSource, PlannerAmount>> suppliers) {
        this(suppliers, Map.of(), List.of());
    }

    public ExecutionProvenance {
        Map<UUID, PlannerAmount> totals = new LinkedHashMap<>();
        for (var entry : demands.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) {
                throw new IllegalArgumentException("Demand identity does not match its ledger key");
            }
        }
        for (SupplyAllocation allocation : allocations) {
            MaterialDemand demand = demands.get(allocation.demandId());
            if (demand == null) throw new IllegalArgumentException("Allocation has no demand");
            PlannerAmount total = totals.merge(demand.id(), allocation.amount(), PlannerAmount::add);
            if (total.compareTo(demand.amount()) > 0) {
                throw new IllegalArgumentException("Demand is over-allocated: " + demand.id());
            }
        }
    }

    public Set<MaterialSource> suppliersOf(AEKey key) {
        return suppliers.getOrDefault(key, Map.of()).keySet();
    }

    public Map<MaterialSource, PlannerAmount> supplierAmountsOf(AEKey key) {
        return suppliers.getOrDefault(key, Map.of());
    }

    public boolean covers(AEKey key) {
        return !suppliersOf(key).isEmpty();
    }

    public List<SupplyAllocation> allocationsFor(MaterialDemand demand) {
        if (!demand.equals(demands.get(demand.id()))) {
            throw new IllegalArgumentException("Unknown demand: " + demand.id());
        }
        return allocations.stream().filter(a -> a.demandId().equals(demand.id()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    public List<SupplyAllocation> allocationsFor(IPatternDetails consumer) {
        return allocations.stream().filter(a -> {
            MaterialDemand demand = demands.get(a.demandId());
            return demand.kind() == MaterialDemand.Kind.INPUT
                && PlanIdentity.samePattern(demand.consumer(), consumer);
        }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    /** Actual recipe allocations only; unused byproducts and stock create no dependency. */
    public boolean hasPatternCycle(Set<IPatternDetails> tasks, ECOCancellation cancellation)
            throws InterruptedException {
        Map<IPatternDetails, Set<IPatternDetails>> outgoing = new LinkedHashMap<>();
        Map<IPatternDetails, Integer> indegree = new LinkedHashMap<>();
        tasks.forEach(pattern -> indegree.put(pattern, 0));
        for (SupplyAllocation allocation : allocations) {
            cancellation.checkpoint();
            MaterialDemand demand = demands.get(allocation.demandId());
            if (demand.kind() != MaterialDemand.Kind.INPUT
                    || !(allocation.source() instanceof MaterialSource.PatternOutput output)
                    || !tasks.contains(output.pattern()) || !tasks.contains(demand.consumer())) continue;
            if (outgoing.computeIfAbsent(output.pattern(), ignored -> new java.util.LinkedHashSet<>())
                    .add(demand.consumer())) {
                indegree.merge(demand.consumer(), 1, Integer::sum);
            }
        }
        var ready = new java.util.ArrayDeque<IPatternDetails>();
        indegree.forEach((pattern, count) -> { if (count == 0) ready.add(pattern); });
        int visited = 0;
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            IPatternDetails producer = ready.removeFirst();
            visited++;
            for (IPatternDetails consumer : outgoing.getOrDefault(producer, Set.of())) {
                if (indegree.merge(consumer, -1, Integer::sum) == 0) ready.addLast(consumer);
            }
        }
        return visited != tasks.size();
    }

    /** Completeness is exact and independent of the legacy per-material diagnostic totals. */
    public void requireComplete() {
        if (demands.isEmpty()) throw new IllegalStateException("Missing demand attribution");
        Map<UUID, PlannerAmount> totals = new LinkedHashMap<>();
        Map<AEKey, Map<MaterialSource, PlannerAmount>> attributed = new LinkedHashMap<>();
        allocations.forEach(a -> totals.merge(a.demandId(), a.amount(), PlannerAmount::add));
        allocations.forEach(a -> attributed.computeIfAbsent(a.material(), ignored -> new LinkedHashMap<>())
            .merge(a.source(), a.amount(), PlannerAmount::add));
        for (MaterialDemand demand : demands.values()) {
            if (!demand.amount().equals(totals.getOrDefault(demand.id(), PlannerAmount.ZERO))) {
                throw new IllegalStateException("Incomplete demand attribution: " + demand.id()
                    + " consumer=" + demand.consumer() + " material=" + demand.key());
            }
        }
        if (!attributed.equals(suppliers)) {
            throw new IllegalStateException("Material source totals do not match demand allocations");
        }
    }
}
