package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleCircuitSummary;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternContractReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class ECOOrderedCycleBatching {
    private static final int MAX_GROUP_WIDTH = 64;

    private ECOOrderedCycleBatching() {}

    static List<PatternRun> normalize(List<PatternRun> runs) {
        List<PatternRun> normalized = null;
        int copied = 0;
        for (int end = 0; end < runs.size(); end++) {
            PatternRun last = runs.get(end);
            int width = last.repeatWidth();
            if (width < 2 || width > MAX_GROUP_WIDTH || last.plannerRepetitions().compareTo(PlannerAmount.ONE) <= 0) continue;
            int start = end - width + 1;
            var replacement = merge(runs.subList(start, end + 1), last.plannerRepetitions());
            if (replacement == null) continue;
            if (normalized == null) normalized = new ArrayList<>(runs.size());
            normalized.addAll(runs.subList(copied, start));
            normalized.addAll(replacement);
            copied = end + 1;
        }
        if (normalized == null) return runs;
        normalized.addAll(runs.subList(copied, runs.size()));
        return normalized;
    }

    private static @Nullable List<PatternRun> merge(List<PatternRun> group, PlannerAmount laps) {
        Map<PlanIdentity.PatternIdentity, PatternRun> merged = new LinkedHashMap<>();
        for (PatternRun run : group) {
            var identity = PlanIdentity.patternIdentityFor(run.details());
            if (identity == null || run.plannerCount().signum() <= 0) return null;
            var previous = merged.get(identity);
            if (previous != null && previous.pattern() != run.pattern()) return null;
            merged.put(identity, previous == null ? run
                : new PatternRun(run.pattern(), previous.plannerCount().add(run.plannerCount())));
        }
        if (merged.size() == group.size()) return null;

        Map<AEKey, Integer> keys = new LinkedHashMap<>();
        for (PatternRun run : merged.values()) {
            run.pattern().inputs().forEach(input -> keys.computeIfAbsent(input.key(), key -> keys.size()));
            run.pattern().outputs().forEach(output -> keys.computeIfAbsent(output.what(), key -> keys.size()));
        }
        Map<CompiledPattern, Recipe> recipes = new HashMap<>();
        List<Batch> pending = new ArrayList<>(merged.size());
        for (PatternRun run : merged.values()) {
            Recipe recipe = verifiedRecipe(run.pattern(), keys, pending.size());
            if (recipe == null) return null;
            recipes.put(run.pattern(), recipe);
            pending.add(new Batch(run, recipe.summarize(merged.size(), run.plannerCount())));
        }

        var original = CycleCircuitSummary.empty(keys.size(), merged.size());
        for (PatternRun run : group) {
            original = original.then(recipes.get(run.pattern()).summarize(merged.size(), run.plannerCount()));
        }
        var reordered = CycleCircuitSummary.empty(keys.size(), merged.size());
        List<PatternRun> order = new ArrayList<>(pending.size());
        PlannerAmount warmup = PlannerAmount.ZERO;
        while (!pending.isEmpty()) {
            int best = -1;
            PlannerAmount fewestWarmup = laps.subtract(1L);
            for (int index = 0; index < pending.size(); index++) {
                var needed = warmupFor(original, reordered, pending.get(index).flow(), warmup);
                if (needed != null && needed.compareTo(fewestWarmup) < 0) {
                    best = index;
                    fewestWarmup = needed;
                    if (needed.equals(warmup)) break;
                }
            }
            if (best < 0) return null;
            Batch next = pending.remove(best);
            order.add(next.run());
            reordered = reordered.then(next.flow());
            warmup = fewestWarmup;
        }

        PlannerAmount groupedLaps = laps.subtract(warmup);
        var baseline = original.repeated(laps);
        var candidate = reordered.repeated(groupedLaps);
        if (warmup.signum() > 0) candidate = original.repeated(warmup).then(candidate);
        if (!Arrays.equals(candidate.counts(), baseline.counts()) || !Arrays.equals(candidate.delta(), baseline.delta())) return null;
        for (int key = 0; key < keys.size(); key++) {
            if (candidate.required()[key].compareTo(baseline.required()[key]) > 0) return null;
        }

        List<PatternRun> replacement = new ArrayList<>();
        if (warmup.signum() > 0) appendGroup(replacement, group, warmup);
        appendGroup(replacement, order, groupedLaps);
        return replacement;
    }

    private static @Nullable PlannerAmount warmupFor(CycleCircuitSummary original, CycleCircuitSummary prefix,
            CycleCircuitSummary next, PlannerAmount warmup) {
        for (int key = 0; key < original.required().length; key++) {
            var needed = next.required()[key].subtract(prefix.delta()[key]).subtract(original.required()[key]);
            if (needed.signum() <= 0) continue;
            var growth = original.delta()[key];
            if (growth.signum() <= 0) return null;
            warmup = warmup.max(needed.ceilDiv(growth));
        }
        return warmup;
    }

    private static void appendGroup(List<PatternRun> destination, List<PatternRun> group, PlannerAmount laps) {
        for (int index = 0; index < group.size(); index++) {
            var run = group.get(index);
            destination.add(index == group.size() - 1 && !laps.equals(PlannerAmount.ONE)
                ? new PatternRun(run.pattern(), run.plannerCount(), group.size(), laps)
                : new PatternRun(run.pattern(), run.plannerCount()));
        }
    }

    private static @Nullable Recipe verifiedRecipe(CompiledPattern pattern, Map<AEKey, Integer> keys, int index) {
        if (!pattern.semantics().cycleSafeForStaticPlanning() || pattern.specialAnalysis().special()
                || !pattern.semantics().returnedOutputs().isEmpty()) return null;
        var contract = PatternContractReader.readFixedProcessingInputs(pattern.details());
        if (!contract.wellFormed() || contract.inputs().size() != pattern.inputs().size()
                || !contract.outputs().equals(pattern.outputs()) || contract.outputs().isEmpty()) return null;

        var consumed = CycleCircuitSummary.zeroes(keys.size());
        var produced = CycleCircuitSummary.zeroes(keys.size());
        for (int slot = 0; slot < pattern.inputs().size(); slot++) {
            var input = pattern.inputs().get(slot);
            var declared = contract.inputs().get(slot);
            var amount = PlannerAmount.of(declared.primary().amount()).multiply(declared.multiplier());
            if (input.remainderKey() != null || !input.key().equals(declared.primary().what())
                    || !input.amountPerPattern().equals(amount)) return null;
            int key = keys.get(input.key());
            consumed[key] = consumed[key].add(amount);
        }
        for (var output : contract.outputs()) {
            int key = keys.get(output.what());
            if (output.amount() <= 0 || consumed[key].signum() > 0) return null;
            produced[key] = produced[key].add(output.amount());
        }
        return new Recipe(consumed, produced, index);
    }

    private record Batch(PatternRun run, CycleCircuitSummary flow) {}

    private record Recipe(PlannerAmount[] consumed, PlannerAmount[] produced, int index) {
        CycleCircuitSummary summarize(int transitions, PlannerAmount count) {
            return CycleCircuitSummary.run(consumed, produced, transitions, index, count);
        }
    }
}
