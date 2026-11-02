package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** Invocation-local immutable stock and structure; never reuses another order's numeric proof. */
    static final class Prepared {
        private record Branch(long requested, Map<AEKey, Integer> choices) {}
        private final OptimisticSupplyModel model;
        private final PlannerInventorySnapshot inventory;
        private final Map<AEKey, BigInteger> stock = new HashMap<>();
        private final Map<Branch, Boolean> branchProofs = new LinkedHashMap<>();

        private Prepared(OptimisticSupplyModel model, PlannerInventorySnapshot inventory, ECOCancellation cancellation)
                throws InterruptedException {
            this.model = model;
            this.inventory = inventory;
            for (var entry : inventory.toKeyCounter()) {
                cancellation.checkpoint();
                if (entry.getLongValue() > 0) stock.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
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
            if (cached != null) return cached;
            boolean rejected = insufficientOutput(requested, choices, fixed, cancellation) != null;
            if (branchProofs.size() < 256) branchProofs.put(new Branch(requested, Map.copyOf(signature)), rejected);
            return rejected;
        }

        @Nullable PlannerAmount insufficientOutput(long requested, Map<AEKey, Integer> choices, Set<AEKey> fixed,
                ECOCancellation cancellation) throws InterruptedException {
            if (requested <= 0) return null;
            Map<AEKey, BigInteger> finite = new HashMap<>();
            ArrayDeque<AEKey> queue = new ArrayDeque<>(model.keys);
            Set<AEKey> queued = new HashSet<>(model.keys);
            BigInteger target = BigInteger.valueOf(requested);
            int revisions = 0;
            while (!queue.isEmpty() && revisions++ < MAX_REVISIONS) {
                cancellation.checkpoint();
                AEKey key = queue.removeFirst();
                queued.remove(key);
                if (model.emitted.contains(key) || inventory.isUnbounded(key)) continue;
                BigInteger bound = stock.getOrDefault(key, BigInteger.ZERO);
                boolean infinite = false;
                for (var production : model.producers.getOrDefault(key, java.util.List.of())) {
                    cancellation.checkpoint();
                    var recipe = production.recipe();
                    if (!recipe.allowed(choices, fixed)) continue;
                    BigInteger firings = null;
                    for (var input : recipe.inputs()) {
                        BigInteger allowed = input.firings(finite, cancellation);
                        if (allowed != null && (firings == null || allowed.compareTo(firings) < 0)) firings = allowed;
                        if (firings != null && firings.signum() == 0) break;
                    }
                    if (firings == null) infinite = true;
                    else bound = bound.add(firings.multiply(production.amount()));
                }
                if (infinite) continue;
                BigInteger previous = finite.get(key);
                if (previous != null && previous.compareTo(bound) <= 0) continue;
                finite.put(key, bound);
                if (key.equals(model.goal) && bound.compareTo(target) < 0) return PlannerAmount.of(bound);
                for (AEKey dependent : model.dependents.getOrDefault(key, Set.of())) {
                    if (queued.add(dependent)) queue.addLast(dependent);
                }
            }
            return null;
        }
    }
}
