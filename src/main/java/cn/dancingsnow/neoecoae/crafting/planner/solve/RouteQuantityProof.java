package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Optimistic, exact-quantity proofs; unknown capacity always means infinity, never zero. */
final class RouteQuantityProof {
    private static final int MAX_REVISIONS = 65_536;

    private RouteQuantityProof() {}

    static @Nullable PlannerAmount insufficientOutput(CompiledNetwork network, PlannerInventorySnapshot inventory,
            long requested, ECOCancellation cancellation) throws InterruptedException {
        Prepared prepared = prepare(network, inventory, cancellation);
        return prepared == null ? null : prepared.insufficientOutput(requested, Map.of(), Set.of(), cancellation);
    }

    static @Nullable Prepared prepare(CompiledNetwork network, PlannerInventorySnapshot inventory,
            ECOCancellation cancellation) throws InterruptedException {
        OptimisticSupplyModel model = OptimisticSupplyModel.compile(network, cancellation);
        return model == null ? null : new Prepared(model, inventory, cancellation);
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
        private final Map<AEKey, PlannerAmount> stock = new HashMap<>();
        private final Map<Branch, Boolean> branchProofs = new LinkedHashMap<>();
        private long proofNanos;
        private long checkpoints;
        private int calls;
        private int cacheHits;
        private int limited;
        private String explanation = "";

        String metrics() {
            return "proofCalls=" + calls + " proofCacheHits=" + cacheHits + " proofLimited=" + limited
                + " proofCheckpoints=" + checkpoints + " branchProofMs=" + proofNanos / 1_000_000.0D;
        }

        String explanation() { return explanation; }

        private Prepared(OptimisticSupplyModel model, PlannerInventorySnapshot inventory, ECOCancellation cancellation)
                throws InterruptedException {
            this.model = model;
            this.inventory = inventory;
            for (var entry : inventory.amounts().entrySet()) {
                cancellation.checkpoint();
                if (entry.getValue().signum() > 0) stock.put(entry.getKey(), entry.getValue());
            }
        }

        boolean rejectsBranch(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation) throws InterruptedException {
            cancellation.checkpoint();
            Map<AEKey, Integer> signature = new HashMap<>();
            for (AEKey key : model.variableKeys) {
                if (fixed.contains(key)) signature.put(key, choices.getOrDefault(key, 0));
            }
            Branch branch = new Branch(requested, signature);
            Boolean cached = branchProofs.get(branch);
            if (cached != null) { cacheHits++; return cached; }
            boolean rejected = insufficientOutput(requested, choices, fixed, cancellation) != null;
            if (branchProofs.size() < 256) branchProofs.put(new Branch(requested, Map.copyOf(signature)), rejected);
            return rejected;
        }

        @Nullable PlannerAmount insufficientOutput(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation) throws InterruptedException {
            boolean global = fixed.isEmpty();
            ECOPlanningBudget budget = allowance(cancellation, global ? 200_000L : 25_000L,
                global ? 50_000_000L : 10_000_000L);
            long started = System.nanoTime();
            calls++;
            try {
                return propagate(requested, choices, fixed, budget);
            } catch (ECOPlanningBudget.Exhausted unknown) {
                limited++;
                return null;
            } finally {
                proofNanos += System.nanoTime() - started;
                checkpoints += budget.work();
            }
        }

        private record Limit(PlannerAmount firings, PlannerAmount output, OptimisticSupplyModel.InputGroup input) {}

        private @Nullable PlannerAmount propagate(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation) throws InterruptedException {
            if (requested <= 0) return null;
            Map<AEKey, PlannerAmount> finite = new HashMap<>();
            Map<AEKey, List<Limit>> reasons = fixed.isEmpty() ? new HashMap<>() : null;
            ArrayDeque<AEKey> queue = new ArrayDeque<>(model.keys);
            Set<AEKey> queued = new HashSet<>(model.keys);
            PlannerAmount target = PlannerAmount.of(requested);
            int revisions = 0;
            while (!queue.isEmpty() && revisions++ < MAX_REVISIONS) {
                cancellation.checkpoint();
                AEKey key = queue.removeFirst();
                queued.remove(key);
                if (model.emitted.contains(key) || inventory.isUnbounded(key)) continue;
                PlannerAmount bound = stock.getOrDefault(key, PlannerAmount.ZERO);
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
                        if (limits != null) limits.add(new Limit(firings, production.amount(), limitingInput));
                    }
                }
                if (infinite) continue;
                PlannerAmount previous = finite.get(key);
                if (previous != null && previous.compareTo(bound) <= 0) continue;
                finite.put(key, bound);
                if (reasons != null) reasons.put(key, limits);
                if (key.equals(model.goal) && bound.compareTo(target) < 0) {
                    if (reasons != null) explanation = explain(finite, reasons);
                    return bound;
                }
                for (AEKey dependent : model.dependents.getOrDefault(key, Set.of())) {
                    if (queued.add(dependent)) queue.addLast(dependent);
                }
            }
            return null;
        }

        private String explain(Map<AEKey, PlannerAmount> finite, Map<AEKey, List<Limit>> reasons) {
            StringBuilder text = new StringBuilder("supplyUpperBounds=[");
            ArrayDeque<AEKey> pending = new ArrayDeque<>();
            pending.add(model.goal);
            Set<AEKey> visited = new HashSet<>();
            while (!pending.isEmpty() && visited.size() < 16) {
                AEKey key = pending.removeFirst();
                if (!visited.add(key)) continue;
                if (visited.size() > 1) text.append("; ");
                text.append(key).append(" <= ").append(finite.get(key))
                    .append(" stock=").append(stock.getOrDefault(key, PlannerAmount.ZERO));
                for (Limit limit : reasons.getOrDefault(key, List.of())) {
                    text.append(" + firings<=").append(limit.firings()).append(" * ").append(limit.output())
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
