package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver.CircuitSummary;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.RepeatLayout;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.KeyAmount;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.PatternAmount;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.Recursion;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.RecursionLayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Projects an existing compact witness into growth checkpoints; never searches for another recipe order. */
final class CycleRecursionPreview {
    static final int MAX_LAYERS = 64;
    private static final long MAX_DETAIL_WORK = 250_000L;

    private CycleRecursionPreview() {}

    static Recursion create(@Nullable CycleSolveResult solve, List<KeyAmount> requiredOutputs,
            Map<IPatternDetails, Integer> patternIds) {
        if (solve == null || !solve.hasExactExecutionCounts()) return Recursion.EMPTY;
        List<PatternRun> runs = solve.executionPlan();
        Map<IPatternDetails, CompiledPattern> patterns = new LinkedHashMap<>();
        Map<IPatternDetails, PlannerAmount> counts = new LinkedHashMap<>();
        PlannerAmount[] multipliers = RepeatLayout.plannerMultipliers(runs);
        for (int i = 0; i < runs.size(); i++) {
            PatternRun run = runs.get(i);
            patterns.putIfAbsent(run.details(), run.pattern());
            counts.merge(run.details(), run.plannerCount().multiply(multipliers[i]), PlannerAmount::add);
        }
        counts.values().removeIf(PlannerAmount::isZero);
        if (!counts.equals(solve.exactPatternTimes()) || !patternIds.keySet().containsAll(counts.keySet())) {
            return Recursion.EMPTY;
        }
        Map<AEKey, Integer> keyIndex = new LinkedHashMap<>();
        solve.exactRequiredSeed().keySet().forEach(key -> keyIndex.computeIfAbsent(key, ignored -> keyIndex.size()));
        for (CompiledPattern pattern : patterns.values()) {
            pattern.inputs().forEach(input -> keyIndex.computeIfAbsent(input.key(), ignored -> keyIndex.size()));
            pattern.grossOutputs().forEach(output -> keyIndex.computeIfAbsent(output.what(), ignored -> keyIndex.size()));
        }
        requiredOutputs.forEach(value -> keyIndex.computeIfAbsent(value.key(), ignored -> keyIndex.size()));
        solve.exactExternalDemand().keySet().forEach(key -> keyIndex.computeIfAbsent(key, ignored -> keyIndex.size()));
        List<AEKey> keys = List.copyOf(keyIndex.keySet());
        List<IPatternDetails> patternOrder = List.copyOf(patterns.keySet());
        Map<IPatternDetails, Integer> patternIndex = new LinkedHashMap<>();
        for (int i = 0; i < patternOrder.size(); i++) patternIndex.put(patternOrder.get(i), i);
        PlannerAmount[] remainingCounts = zeroes(patternOrder.size());
        PlannerAmount[] remainingDelta = zeroes(keys.size());
        for (int i = 0; i < patternOrder.size(); i++) {
            var pattern = patterns.get(patternOrder.get(i));
            PlannerAmount count = counts.getOrDefault(pattern.details(), PlannerAmount.ZERO);
            remainingCounts[i] = count;
            pattern.inputs().forEach(input -> {
                int key = keyIndex.get(input.key());
                remainingDelta[key] = remainingDelta[key].subtract(input.amountPerPattern().multiply(count));
            });
            pattern.grossOutputs().forEach(output -> {
                int key = keyIndex.get(output.what());
                remainingDelta[key] = remainingDelta[key].add(count.multiply(output.amount()));
            });
        }
        PlannerAmount[] remainingExternal = zeroes(keys.size());
        PlannerAmount[] stock = zeroes(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            AEKey key = keys.get(i);
            remainingExternal[i] = solve.exactExternalDemand().getOrDefault(key, PlannerAmount.ZERO).max(PlannerAmount.ZERO);
            stock[i] = solve.exactRequiredSeed().getOrDefault(key, PlannerAmount.ZERO);
            // Include the existing output stock that the verified witness actually started with. In particular,
            // a stock-satisfied cycle must not be drawn as producing its order from an empty seed.
            PlannerAmount ending = solve.exactDeliverableOutputs().get(key);
            if (ending != null) stock[i] = stock[i].max(ending.subtract(remainingDelta[i]).subtract(remainingExternal[i]));
        }
        for (KeyAmount output : requiredOutputs) {
            int key = keyIndex.get(output.key());
            if (stock[key].add(remainingDelta[key]).add(remainingExternal[key]).compareTo(output.plannerAmount()) < 0) {
                return Recursion.EMPTY;
            }
        }
        List<KeyAmount> initial = amounts(keys, stock);
        List<RecursionLayer> layers = new ArrayList<>();
        int maxLayers = Math.min(MAX_LAYERS, Math.max(2, 8192 / Math.max(1, keys.size() + patterns.size())));
        long work = 0;
        for (Block block : blocks(runs)) {
            work += (long) (block.end() - block.start()) * (keys.size() + patternOrder.size());
            if (work > MAX_DETAIL_WORK) break;
            CircuitSummary unit = summarize(runs, block, keyIndex, patternIndex);
            PlannerAmount remaining = block.repetitions();
            boolean first = true;
            while (remaining.signum() > 0 && layers.size() < maxLayers - 1) {
                PlannerAmount batch = null;
                for (int i = 0; i < keys.size(); i++) {
                    // Reusable catalysts do not grow; only replenished inputs determine a growth checkpoint.
                    if (unit.required()[i].signum() > 0 && unit.delta()[i].signum() > 0
                            && !solve.exactExternalDemand().containsKey(keys.get(i))) {
                        PlannerAmount capacity = stock[i].divide(unit.required()[i]).max(PlannerAmount.ONE);
                        batch = batch == null ? capacity : batch.min(capacity);
                    }
                }
                boolean folded = batch == null && !first && remaining.compareTo(PlannerAmount.ONE) > 0;
                if (batch == null) batch = first ? PlannerAmount.ONE : remaining;
                batch = batch.min(remaining);
                CircuitSummary step = unit.repeated(batch);
                PlannerAmount[] added = zeroes(keys.size());
                for (int i = 0; i < keys.size(); i++) {
                    added[i] = step.required()[i].subtract(stock[i]).max(PlannerAmount.ZERO);
                    if (added[i].compareTo(remainingExternal[i]) > 0) return Recursion.EMPTY;
                    remainingExternal[i] = remainingExternal[i].subtract(added[i]);
                    stock[i] = stock[i].add(added[i]).add(step.delta()[i]);
                    remainingDelta[i] = remainingDelta[i].subtract(step.delta()[i]);
                }
                for (int i = 0; i < remainingCounts.length; i++) {
                    remainingCounts[i] = remainingCounts[i].subtract(step.counts()[i]);
                }
                layers.add(new RecursionLayer(amounts(keys, added), amounts(keys, stock),
                    firings(patternOrder, patternIds, step.counts()), folded));
                remaining = remaining.subtract(batch);
                first = false;
            }
            if (layers.size() >= maxLayers - 1) break;
        }
        if (Arrays.stream(remainingCounts).anyMatch(value -> value.signum() > 0)
                || Arrays.stream(remainingExternal).anyMatch(value -> value.signum() > 0)) {
            // The entire suffix was already verified by the solver. Combine it algebraically instead of
            // expanding billions of firings or hiding the final connection to the requested output.
            for (int i = 0; i < stock.length; i++) {
                stock[i] = stock[i].add(remainingExternal[i]).add(remainingDelta[i]);
                if (stock[i].signum() < 0) return Recursion.EMPTY;
            }
            layers.add(new RecursionLayer(amounts(keys, remainingExternal), amounts(keys, stock),
                firings(patternOrder, patternIds, remainingCounts), true));
        }
        return new Recursion(initial, List.copyOf(layers), true);
    }

    private record Block(int start, int end, PlannerAmount repetitions, boolean singleFiring) {}

    private static List<Block> blocks(List<PatternRun> runs) {
        Map<Integer, Integer> ends = new LinkedHashMap<>();
        for (int end = 0; end < runs.size(); end++) {
            if (runs.get(end).plannerRepetitions().compareTo(PlannerAmount.ONE) > 0) {
                ends.put(end - runs.get(end).repeatWidth() + 1, end);
            }
        }
        List<Block> result = new ArrayList<>();
        for (int start = 0; start < runs.size();) {
            Integer end = ends.get(start);
            if (end == null) {
                if (runs.get(start).plannerCount().signum() > 0) {
                    result.add(new Block(start, start + 1, runs.get(start).plannerCount(), true));
                }
                start++;
            } else {
                result.add(new Block(start, end + 1, runs.get(end).plannerRepetitions(), false));
                start = end + 1;
            }
        }
        return result;
    }

    private static CircuitSummary summarize(List<PatternRun> runs, Block block, Map<AEKey, Integer> keys,
            Map<IPatternDetails, Integer> patterns) {
        CircuitSummary summary = new CircuitSummary(zeroes(keys.size()), zeroes(keys.size()),
            zeroes(keys.size()), zeroes(patterns.size()));
        for (int i = block.start(); i < block.end(); i++) {
            PatternRun run = runs.get(i);
            PlannerAmount[] consumed = zeroes(keys.size()), produced = zeroes(keys.size());
            run.pattern().inputs().forEach(input -> {
                int key = keys.get(input.key());
                consumed[key] = consumed[key].add(input.amountPerPattern());
            });
            run.pattern().grossOutputs().forEach(output -> {
                int key = keys.get(output.what());
                produced[key] = produced[key].add(output.amount());
            });
            summary = summary.then(CircuitSummary.run(consumed, produced, patterns.size(), patterns.get(run.details()),
                block.singleFiring() ? PlannerAmount.ONE : run.plannerCount()));
        }
        return summary;
    }

    private static PlannerAmount[] zeroes(int size) {
        PlannerAmount[] result = new PlannerAmount[size];
        Arrays.fill(result, PlannerAmount.ZERO);
        return result;
    }

    private static List<KeyAmount> amounts(List<AEKey> keys, PlannerAmount[] values) {
        List<KeyAmount> result = new ArrayList<>();
        for (int i = 0; i < values.length; i++) if (values[i].signum() > 0) result.add(new KeyAmount(keys.get(i), values[i]));
        return List.copyOf(result);
    }

    private static List<PatternAmount> firings(List<IPatternDetails> patterns, Map<IPatternDetails, Integer> ids,
            PlannerAmount[] values) {
        List<PatternAmount> result = new ArrayList<>();
        for (int i = 0; i < values.length; i++) if (values[i].signum() > 0) {
            result.add(new PatternAmount(ids.get(patterns.get(i)), values[i]));
        }
        return List.copyOf(result);
    }
}
