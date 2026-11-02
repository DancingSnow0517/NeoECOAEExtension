package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Proof-only relaxation: recipes and slots receive independent stock, alternatives may mix,
 * reusable/special slots impose no quantity limit, and opaque recipes have unlimited output.
 * These optimistic contracts never authorize execution or cache another order's numeric result. */
final class OptimisticSupplyModel {
    record Alternative(AEKey key, BigInteger units) {}
    record InputGroup(List<Alternative> alternatives, BigInteger multiplier) {
        boolean available(Set<AEKey> stock) {
            return alternatives.isEmpty() || alternatives.stream().anyMatch(a -> stock.contains(a.key()));
        }
        @Nullable BigInteger firings(Map<AEKey, BigInteger> finite, ECOCancellation cancellation)
                throws InterruptedException {
            if (alternatives.isEmpty()) return null;
            BigInteger units = BigInteger.ZERO;
            for (Alternative alternative : alternatives) {
                cancellation.checkpoint();
                BigInteger capacity = finite.get(alternative.key());
                if (capacity == null) return null;
                units = units.add(capacity.divide(alternative.units()));
            }
            return units.divide(multiplier);
        }
    }
    record Recipe(Map<AEKey, Integer> owners, List<InputGroup> inputs, Map<AEKey, BigInteger> outputs) {
        boolean allowed(Map<AEKey, Integer> choices, Set<AEKey> fixed) {
            for (var owner : owners.entrySet()) {
                if (owner.getValue() < 0 || !fixed.contains(owner.getKey())
                        || choices.getOrDefault(owner.getKey(), 0).equals(owner.getValue())) return true;
            }
            return false;
        }
    }
    record Production(Recipe recipe, BigInteger amount) {}

    final AEKey goal;
    final Set<AEKey> keys;
    final Set<AEKey> emitted;
    final Set<AEKey> variableKeys;
    final List<Recipe> recipes;
    final Map<AEKey, List<Production>> producers;
    final Map<AEKey, Set<AEKey>> dependents;

    private OptimisticSupplyModel(CompiledNetwork network, Set<AEKey> keys, List<Recipe> recipes,
            Map<AEKey, List<Production>> producers, Map<AEKey, Set<AEKey>> dependents) {
        this.goal = network.goal(); this.keys = keys; this.emitted = network.emittable();
        this.variableKeys = new java.util.HashSet<>();
        network.fastProducers().forEach((key, choices) -> { if (choices.size() > 1) variableKeys.add(key); });
        this.recipes = recipes; this.producers = producers; this.dependents = dependents;
    }

    static @Nullable OptimisticSupplyModel compile(CompiledNetwork network, ECOCancellation cancellation)
            throws InterruptedException {
        Set<AEKey> keys = new LinkedHashSet<>(network.keys());
        keys.add(network.goal());
        List<Recipe> recipes = new ArrayList<>();
        Map<AEKey, List<Production>> producers = new HashMap<>();
        Map<AEKey, Set<AEKey>> dependents = new HashMap<>();
        Map<CompiledPattern, Map<AEKey, Integer>> indexed = new java.util.LinkedHashMap<>();
        for (var entry : network.producers().entrySet()) {
            List<CompiledPattern> fast = network.fastProducersOf(entry.getKey());
            for (CompiledPattern pattern : entry.getValue()) {
                cancellation.checkpoint();
                indexed.computeIfAbsent(pattern, ignored -> new java.util.LinkedHashMap<>())
                    .put(entry.getKey(), pattern.fastSupported() ? fast.indexOf(pattern) : -1);
            }
        }
        for (var indexedEntry : indexed.entrySet()) {
            CompiledPattern pattern = indexedEntry.getKey();
            cancellation.checkpoint();
            Map<AEKey, BigInteger> outputs = new HashMap<>();
            for (GenericStack output : pattern.grossOutputs()) {
                if (output != null && output.what() != null && output.amount() > 0)
                    outputs.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
            }
            List<InputGroup> inputs = new ArrayList<>();
            PatternSemantics semantics = pattern.semantics();
            // A normalized substitution contract has a complete output/return boundary. A truly
            // malformed or fuzzy contract may hide other returns, so it cannot support a proof.
            if (semantics == null || !semantics.supported()
                    || semantics.matchingMode() == PatternSemantics.MatchingMode.UNKNOWN
                    || semantics.matchingMode() == PatternSemantics.MatchingMode.FUZZY) return null;
            boolean known = pattern.fastSupported() && semantics != null && semantics.supported()
                && semantics.executionRestriction() == PatternSemantics.ExecutionRestriction.NONE
                && (semantics.matchingMode() == PatternSemantics.MatchingMode.EXACT
                    || semantics.matchingMode() == PatternSemantics.MatchingMode.SUBSTITUTION);
            for (var input : pattern.inputs()) {
                cancellation.checkpoint();
                if (input.ignoresComponents()) return null;
                boolean relaxed = !known || !input.fastSupported() || input.ignoresComponents()
                    || pattern.specialAnalysis().excludesFromCycleGraph(input) || input.reusable();
                if (!relaxed && semantics.matchingMode() == PatternSemantics.MatchingMode.EXACT) {
                    if (input.amountPerPattern().signum() > 0) inputs.add(new InputGroup(
                        List.of(new Alternative(input.key(), input.amountPerPattern().toBigInteger())), BigInteger.ONE));
                    continue;
                }
                List<Alternative> alternatives = new ArrayList<>();
                long multiplier = 1;
                if (input.source() != null) {
                    try {
                        GenericStack[] possible = input.source().getPossibleInputs();
                        multiplier = input.source().getMultiplier();
                        if (possible == null || possible.length == 0 || multiplier <= 0) return null;
                        // Duplicated candidates may have different unit costs. Keeping all of them
                        // overestimates supply and avoids depending on their iteration order.
                        for (GenericStack candidate : possible) {
                            cancellation.checkpoint();
                            if (candidate == null || candidate.what() == null || candidate.amount() <= 0) return null;
                            alternatives.add(new Alternative(candidate.what(), BigInteger.valueOf(candidate.amount())));
                            AEKey returned = input.source().getRemainingKey(candidate.what());
                            if (returned != null) {
                                outputs.merge(returned, BigInteger.valueOf(multiplier), BigInteger::add);
                                if (returned.equals(candidate.what())) relaxed = true;
                            }
                        }
                    } catch (RuntimeException unreadable) {
                        // An unreadable return could supply an otherwise independent branch.
                        return null;
                    }
                } else {
                    if (semantics.matchingMode() == PatternSemantics.MatchingMode.SUBSTITUTION) return null;
                    relaxed = true;
                }
                if (!relaxed) inputs.add(new InputGroup(List.copyOf(alternatives), BigInteger.valueOf(multiplier)));
            }
            if (!known) inputs.clear();
            Recipe recipe = new Recipe(Map.copyOf(indexedEntry.getValue()), List.copyOf(inputs), Map.copyOf(outputs));
            recipes.add(recipe);
            keys.addAll(outputs.keySet());
            for (InputGroup input : inputs) for (Alternative alternative : input.alternatives()) {
                keys.add(alternative.key());
                dependents.computeIfAbsent(alternative.key(), ignored -> new LinkedHashSet<>()).addAll(outputs.keySet());
            }
            outputs.forEach((key, amount) -> producers.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new Production(recipe, amount)));
        }
        return new OptimisticSupplyModel(network, keys, recipes, producers, dependents);
    }
}
