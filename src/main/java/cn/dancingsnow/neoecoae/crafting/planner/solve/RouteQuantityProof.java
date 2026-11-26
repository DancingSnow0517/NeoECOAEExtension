package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.trace.QuantityBoundTrace;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Optimistic, exact-quantity proofs; unknown capacity always means infinity, never zero. */
final class RouteQuantityProof {
    private static final int MAX_REVISIONS = 65_536;
    private static final int MAX_BRANCH_PROOFS = 256;

    private RouteQuantityProof() {}

    static @Nullable PlannerAmount insufficientOutput(CompiledNetwork network, PlannerInventorySnapshot inventory,
            long requested, ECOCancellation cancellation) throws InterruptedException {
        Prepared prepared = prepare(network, inventory, cancellation);
        return prepared == null ? null : prepared.insufficientOutput(requested, Map.of(), Set.of(), cancellation);
    }

    static @Nullable Prepared prepare(CompiledNetwork network, PlannerInventorySnapshot inventory,
            ECOCancellation cancellation) throws InterruptedException {
        OptimisticSupplyModel model = OptimisticSupplyModel.compile(network, cancellation);
        if (model == null) return null;
        cancellation.checkpoint();
        return new Prepared(model, inventory);
    }

    static @Nullable Prepared prepareBounded(CompiledNetwork network, PlannerInventorySnapshot inventory,
            ECOCancellation cancellation) throws InterruptedException {
        try {
            return prepare(network, inventory, allowance(cancellation, 200_000L, 50_000_000L));
        } catch (ECOPlanningBudget.Exhausted unknown) {
            return null;
        }
    }

    private static ECOPlanningBudget allowance(ECOCancellation parent, long work, long nanos) {
        return parent instanceof ECOPlanningBudget budget ? budget.slice(work, nanos)
            : new ECOPlanningBudget(parent, work, nanos, System::nanoTime);
    }

    /** Invocation-local immutable stock and structure; never reuses another order's numeric proof. */
    static final class Prepared {
        private record Branch(long requested, Map<AEKey, Integer> choices) {}
        private final OptimisticSupplyModel model;
        private final PlannerInventorySnapshot inventory;
        private final Map<Branch, Boolean> branchProofs = new Object2ObjectOpenHashMap<>(MAX_BRANCH_PROOFS);
        private long proofNanos;
        private long checkpoints;
        private int calls;
        private int cacheHits;
        private int limited;
        private String explanation = "";
        private @Nullable QuantityBoundTrace evidence;

        @Nullable QuantityBoundTrace evidence() { return evidence; }

        String metrics() {
            return "proofCalls=" + calls + " proofCacheHits=" + cacheHits + " proofLimited=" + limited
                + " proofCheckpoints=" + checkpoints + " branchProofMs=" + proofNanos / 1_000_000.0D;
        }

        String explanation() { return explanation; }

        private Prepared(OptimisticSupplyModel model, PlannerInventorySnapshot inventory) {
            this.model = model;
            this.inventory = inventory;
        }

        private PlannerAmount positiveStock(AEKey key) {
            return inventory.amountOf(key).max(PlannerAmount.ZERO);
        }

        boolean rejectsBranch(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation) throws InterruptedException {
            cancellation.checkpoint();
            var signature = new Object2IntOpenHashMap<AEKey>(Math.min(model.variableKeys.size(), fixed.size()));
            for (AEKey key : model.variableKeys) {
                if (fixed.contains(key)) signature.put(key, choices.getOrDefault(key, 0).intValue());
            }
            Branch branch = new Branch(requested, signature);
            Boolean cached = branchProofs.get(branch);
            if (cached != null) { cacheHits++; return cached; }
            boolean rejected = insufficientOutput(requested, choices, fixed, cancellation) != null;
            if (branchProofs.size() < MAX_BRANCH_PROOFS) branchProofs.put(new Branch(requested, Map.copyOf(signature)), rejected);
            return rejected;
        }

        @Nullable PlannerAmount insufficientOutput(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation) throws InterruptedException {
            return insufficientOutput(requested, choices, fixed, cancellation, false);
        }

        @Nullable PlannerAmount insufficientOutput(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation, boolean captureProof) throws InterruptedException {
            boolean global = fixed.isEmpty();
            evidence = null;
            ECOPlanningBudget budget = allowance(cancellation, global ? 200_000L : 25_000L,
                global ? 50_000_000L : 10_000_000L);
            long started = System.nanoTime();
            calls++;
            try {
                return propagate(requested, choices, fixed, budget, global && captureProof);
            } catch (ECOPlanningBudget.Exhausted unknown) {
                limited++;
                return null;
            } finally {
                proofNanos += System.nanoTime() - started;
                checkpoints += budget.work();
            }
        }

        private record Limit(PlannerAmount firings, OptimisticSupplyModel.Production production,
                             OptimisticSupplyModel.InputGroup input) {}

        private @Nullable PlannerAmount propagate(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation, boolean captureProof) throws InterruptedException {
            if (requested <= 0) return null;
            Map<AEKey, PlannerAmount> finite = new Object2ObjectOpenHashMap<>(model.keys.size());
            Map<AEKey, List<Limit>> reasons = fixed.isEmpty() ? new Object2ObjectOpenHashMap<>(model.keys.size()) : null;
            Map<AEKey, QuantityBoundTrace.Node> versions = captureProof ? new Object2ObjectOpenHashMap<>() : null;
            ArrayDeque<AEKey> queue = new ArrayDeque<>(model.keys);
            Set<AEKey> queued = new ObjectOpenHashSet<>(model.keys);
            PlannerAmount target = PlannerAmount.of(requested);
            int revisions = 0;
            while (!queue.isEmpty() && revisions++ < MAX_REVISIONS) {
                cancellation.checkpoint();
                AEKey key = queue.removeFirst();
                queued.remove(key);
                if (model.emitted.contains(key) || inventory.isUnbounded(key)) continue;
                PlannerAmount bound = positiveStock(key);
                List<Limit> limits = reasons == null ? null : new ArrayList<>();
                boolean infinite = false;
                for (var production : model.producers.getOrDefault(key, java.util.List.of())) {
                    cancellation.checkpoint();
                    var recipe = production.recipe();
                    if (!recipe.allowed(choices, fixed)) continue;
                    PlannerAmount firings = null;
                    OptimisticSupplyModel.InputGroup limitingInput = null;
                    for (var input : recipe.inputs()) {
                        PlannerAmount allowed = input.firings(finite, cancellation);
                        if (allowed != null && (firings == null || allowed.compareTo(firings) < 0)) {
                            firings = allowed;
                            limitingInput = input;
                        }
                        if (firings != null && firings.signum() == 0) break;
                    }
                    if (firings == null) infinite = true;
                    else {
                        bound = bound.add(firings.multiply(production.amount()));
                        if (limits != null) limits.add(new Limit(firings, production, limitingInput));
                    }
                }
                if (infinite) continue;
                PlannerAmount previous = finite.get(key);
                if (previous != null && previous.compareTo(bound) <= 0) continue;
                finite.put(key, bound);
                if (reasons != null) reasons.put(key, limits);
                if (versions != null) {
                    versions.put(key, recordBound(revisions, key, bound, limits, versions));
                }
                if (key.equals(model.goal) && bound.compareTo(target) < 0) {
                    if (versions != null) evidence = new QuantityBoundTrace(requested, versions.get(key));
                    if (reasons != null) explanation = explain(finite, reasons);
                    return bound;
                }
                for (AEKey dependent : model.dependents.getOrDefault(key, Set.of())) {
                    if (queued.add(dependent)) queue.addLast(dependent);
                }
            }
            return null;
        }

        /** Freeze just the finite input revisions that established this bound (before updating the key itself). */
        private QuantityBoundTrace.Node recordBound(int revision, AEKey key, PlannerAmount bound, List<Limit> limits,
                Map<AEKey, QuantityBoundTrace.Node> versions) {
            var routes = new ArrayList<QuantityBoundTrace.Route>(limits.size());
            for (Limit limit : limits) {
                var input = limit.input();
                var alternatives = new ArrayList<QuantityBoundTrace.Input>(input.alternatives().size());
                for (var alt : input.alternatives()) {
                    alternatives.add(new QuantityBoundTrace.Input(versions.get(alt.key()), alt.units()));
                }
                routes.add(new QuantityBoundTrace.Route(limit.production().recipe().pattern(), limit.production().amount(),
                    limit.firings(), input.slot(), input.multiplier(), alternatives));
            }
            return new QuantityBoundTrace.Node(revision, key, positiveStock(key), bound, routes);
        }

        private String explain(Map<AEKey, PlannerAmount> finite, Map<AEKey, List<Limit>> reasons) {
            StringBuilder text = new StringBuilder("supplyUpperBounds=[");
            ArrayDeque<AEKey> pending = new ArrayDeque<>();
            pending.add(model.goal);
            Set<AEKey> visited = new ObjectOpenHashSet<>(16);
            while (!pending.isEmpty() && visited.size() < 16) {
                AEKey key = pending.removeFirst();
                if (!visited.add(key)) continue;
                if (visited.size() > 1) text.append("; ");
                text.append(key).append(" <= ").append(finite.get(key))
                    .append(" stock=").append(positiveStock(key));
                for (Limit limit : reasons.getOrDefault(key, List.of())) {
                    text.append(" + firings<=").append(limit.firings()).append(" * ").append(limit.production().amount())
                        .append(" limitedBy=").append(limit.input().alternatives())
                        .append(" multiplier=").append(limit.input().multiplier());
                    for (var input : limit.input().alternatives()) if (!visited.contains(input.key())) pending.add(input.key());
                }
            }
            if (!pending.isEmpty()) text.append("; further dependencies omitted");
            return text.append("]; optimistic bound, not an executable maximum").toString();
        }
    }
}
