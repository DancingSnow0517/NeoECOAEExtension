package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Short-lived side channel for planning metadata when a planning accelerator copies a CraftingPlan.
 * The copy retains the plan contents but cannot retain fields added to the original object by a mixin.
 */
public final class ECOPlanningResultRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");
    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_AGE_NANOS = Duration.ofMinutes(10).toNanos();
    private static final Map<PlanSignature, Entry> RESULTS = new LinkedHashMap<>(64, 0.75f, true);
    /**
     * Exact signature of the plan retained by CraftConfirmMenu -> cycle-aware ECO plan shown with it.
     *
     * <p>Some planner/confirmation integrations rebuild the public CraftingPlan before CPU submission. More
     * importantly, a multi-planner may retain one candidate's task map while the ECO diagnostics shown beside
     * it came from the cycle-aware candidate. Matching the submitted task map against the ECO task map cannot
     * recover from that situation: they are deliberately different plans. This alias is recorded only at the
     * user's confirmation boundary, so it identifies the plan that was actually approved without guessing
     * between every same-output candidate produced in the background. The binding exists only while the
     * confirmation menu is synchronously inside {@code ICraftingService.submitJob}; a global signature map
     * would let equal plans from different players overwrite each other and would lose the binding after a
     * rejected submission was retried.
     */
    private static final ThreadLocal<SubmissionAlias> ACTIVE_SUBMISSION_ALIAS = new ThreadLocal<>();

    private ECOPlanningResultRegistry() {
    }

    public static void register(ICraftingPlan plan, ECOPlanningResult result) {
        // Only an executable success can be restored at CPU submission. A simulated/partial candidate could
        // otherwise overwrite a valid entry from another attempt with the same public plan signature.
        if (plan == null || result == null || result.plan() == null
                || result.status() != cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.SUCCESS
                || plan.simulation() || result.plan().simulation()
                || !sameFinalOutput(plan, result.plan())
                || !ECOPhaseScheduler.requiresComponentScheduling(result.executionSchedule())) return;
        PlanSignature signature = signatureOf(plan);
        if (signature == null) return;
        synchronized (RESULTS) {
            long now = System.nanoTime();
            removeExpired(now);
            RESULTS.put(signature, new Entry(result, Map.copyOf(plan.patternTimes()),
                counterContents(plan.usedItems()), now));
            while (RESULTS.size() > MAX_ENTRIES) {
                RESULTS.remove(RESULTS.keySet().iterator().next());
            }
        }
    }

    public static @Nullable ECOPlanningResult find(ICraftingPlan plan) {
        PlanSignature signature = signatureOf(plan);
        if (signature == null) return null;
        synchronized (RESULTS) {
            long now = System.nanoTime();
            removeExpired(now);
            Entry entry = RESULTS.get(signature);
            return entry == null ? null : entry.result();
        }
    }

    /**
     * Runs one confirmation-menu submission with an exact binding from the displayed plan to its complete ECO
     * executable plan. The previous binding is restored for nested calls and all paths, including exceptions.
     */
    public static <T> T withSubmissionAlias(ICraftingPlan confirmedPlan, @Nullable ECOPlanningResult result,
            Supplier<T> submission) {
        Objects.requireNonNull(submission, "submission");
        SubmissionAlias previous = ACTIVE_SUBMISSION_ALIAS.get();
        SubmissionAlias current = createSubmissionAlias(confirmedPlan, result);
        if (current == null) {
            ACTIVE_SUBMISSION_ALIAS.remove();
        } else {
            ACTIVE_SUBMISSION_ALIAS.set(current);
        }
        try {
            return submission.get();
        } finally {
            if (previous == null) {
                ACTIVE_SUBMISSION_ALIAS.remove();
            } else {
                ACTIVE_SUBMISSION_ALIAS.set(previous);
            }
        }
    }

    private static @Nullable SubmissionAlias createSubmissionAlias(ICraftingPlan confirmedPlan,
            @Nullable ECOPlanningResult result) {
        if (confirmedPlan == null || result == null || result.plan() == null
                || result.status() != cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.SUCCESS
                || result.plan().simulation()
                || !ECOPhaseScheduler.requiresComponentScheduling(result.executionSchedule())
                || !sameFinalOutput(confirmedPlan, result.plan())) {
            return null;
        }
        PlanSignature signature = signatureOf(confirmedPlan);
        return signature == null ? null : new SubmissionAlias(signature, result.plan());
    }

    /**
     * Restores the complete cycle-aware plan approved in CraftConfirmMenu. This happens before initial item
     * extraction and before the ECO CPU stores the plan, so tasks, used items, bytes and execution metadata all
     * come from one internally consistent planner result.
     *
     * <p>There is deliberately no global same-output or fuzzy fallback here. The final output check is sufficient
     * only because the alias is scoped to this exact synchronous confirmation call; background candidates and
     * submissions on other threads cannot observe it. This also covers integrations that transform the task map
     * inside {@code submitJob}, after the confirmation menu established the binding.
     */
    public static ICraftingPlan resolveSubmissionPlan(ICraftingPlan submittedPlan) {
        PlanSignature signature = signatureOf(submittedPlan);
        if (signature == null) return submittedPlan;
        SubmissionAlias alias = ACTIVE_SUBMISSION_ALIAS.get();
        if (alias == null || alias.executablePlan().simulation()
                || !sameFinalOutput(submittedPlan, alias.executablePlan())) {
            return submittedPlan;
        }
        ICraftingPlan executable = alias.executablePlan();
        if (executable != submittedPlan) {
            LOGGER.info(
                "[ECO-EXEC] restored confirmed cycle plan finalOutput={} submittedKinds={} "
                    + "submittedExecutions={} executableKinds={} executableExecutions={} signatureChanged={}",
                submittedPlan.finalOutput(), submittedPlan.patternTimes().size(),
                executionCount(submittedPlan.patternTimes()), executable.patternTimes().size(),
                executionCount(executable.patternTimes()), !alias.confirmedSignature().equals(signature));
        }
        return executable;
    }

    /**
     * Recovers and rebinds a schedule to the exact pattern instances received by the CPU. Planning accelerators
     * may replace ordinary details with patched details after the ECO planner has returned its plan.
     */
    public static @Nullable RecoveredSchedule recoverSchedule(ICraftingPlan plan) {
        PlanSignature signature = signatureOf(plan);
        if (signature == null) return null;
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            Entry exact = RESULTS.get(signature);
            if (exact != null) {
                return new RecoveredSchedule(exact.result().executionSchedule(), "exact-definition");
            }

            List<RecoveredSchedule> compatible = new ArrayList<>();
            for (var candidate : RESULTS.entrySet()) {
                if (!candidate.getKey().sameOutput(signature)) continue;
                ECOExecutionSchedule rebound = rebind(candidate.getValue(), plan.patternTimes());
                if (rebound != null) compatible.add(new RecoveredSchedule(rebound, "rebound-output-signature"));
            }
            return compatible.size() == 1 ? compatible.getFirst() : null;
        }
    }

    public static int registeredScheduleCount() {
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            return RESULTS.size();
        }
    }

    public static String mismatchDiagnostic(ICraftingPlan plan) {
        PlanSignature submittedSignature = signatureOf(plan);
        if (submittedSignature == null) return "submitted-signature-unavailable";
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            List<String> candidates = new ArrayList<>();
            int index = 0;
            for (var registered : RESULTS.entrySet()) {
                if (!registered.getKey().sameOutput(submittedSignature)) continue;
                candidates.add(describeMismatch(index++, registered.getValue(), plan));
            }
            return candidates.isEmpty() ? "same-output-candidates=0" : String.join("; ", candidates);
        }
    }

    private static void removeExpired(long now) {
        RESULTS.entrySet().removeIf(entry -> now - entry.getValue().createdNanos() > MAX_AGE_NANOS);
    }

    private static boolean sameFinalOutput(ICraftingPlan left, ICraftingPlan right) {
        return left.finalOutput() != null && right.finalOutput() != null
            && left.finalOutput().amount() == right.finalOutput().amount()
            && left.finalOutput().what().equals(right.finalOutput().what());
    }

    private static long executionCount(Map<IPatternDetails, Long> tasks) {
        long total = 0L;
        for (long value : tasks.values()) {
            if (value <= 0L) continue;
            if (Long.MAX_VALUE - total < value) return Long.MAX_VALUE;
            total += value;
        }
        return total;
    }

    private static @Nullable PlanSignature signatureOf(ICraftingPlan plan) {
        if (plan == null || plan.finalOutput() == null) return null;
        Map<Object, Long> patternTimes = new HashMap<>();
        for (var entry : plan.patternTimes().entrySet()) {
            // Real encoded patterns have a stable AEItemKey definition. The details fallback also keeps this
            // usable for synthetic/test plans and for in-process wrappers that preserve the pattern instances.
            Object identity = entry.getKey().getDefinition();
            if (identity == null) identity = entry.getKey();
            patternTimes.merge(identity, entry.getValue(), Long::sum);
        }
        // Accelerated submission normalizes emittedItems (observed as non-empty while planning and empty at
        // CPU submission). Emitted items are not executable tasks and do not affect component/witness order,
        // so intentionally keep them out of this recovery identity.
        return new PlanSignature(plan.finalOutput().what(), plan.finalOutput().amount(), Map.copyOf(patternTimes));
    }

    private static @Nullable ECOExecutionSchedule rebind(Entry entry,
            Map<IPatternDetails, Long> submittedTasks) {
        if (entry.tasks().size() != submittedTasks.size()) return null;
        Map<IPatternDetails, IPatternDetails> mapping = new HashMap<>();
        Set<IPatternDetails> usedTargets = new HashSet<>();
        for (var sourceTask : entry.tasks().entrySet()) {
            List<IPatternDetails> matches = submittedTasks.entrySet().stream()
                .filter(target -> !usedTargets.contains(target.getKey()))
                .filter(target -> sourceTask.getValue().equals(target.getValue()))
                .filter(target -> ECOPhaseScheduler.samePattern(sourceTask.getKey(), target.getKey()))
                .map(Map.Entry::getKey)
                .toList();
            if (matches.size() != 1) {
                PatternOutputSignature outputs = outputSignature(sourceTask.getKey());
                matches = submittedTasks.entrySet().stream()
                    .filter(target -> !usedTargets.contains(target.getKey()))
                    .filter(target -> sourceTask.getValue().equals(target.getValue()))
                    .filter(target -> outputs.equals(outputSignature(target.getKey())))
                    .map(Map.Entry::getKey)
                    .toList();
            }
            // Planning accelerators can replace an external DAG producer with a larger-batch alternate after
            // planning. Match that replacement only when its total production is identical. Never scale a
            // cycle-owned pattern: witness entries represent individual firings and must remain exact.
            if (matches.size() != 1 && !isCycleOwned(entry.result().executionSchedule(), sourceTask.getKey())) {
                PatternProductionSignature production = productionSignature(
                    sourceTask.getKey(), sourceTask.getValue());
                matches = submittedTasks.entrySet().stream()
                    .filter(target -> !usedTargets.contains(target.getKey()))
                    .filter(target -> production.equals(productionSignature(target.getKey(), target.getValue())))
                    .map(Map.Entry::getKey)
                    .toList();
            }
            if (matches.size() != 1) return null;
            mapping.put(sourceTask.getKey(), matches.getFirst());
            usedTargets.add(matches.getFirst());
        }
        if (usedTargets.size() != submittedTasks.size()) return null;

        List<ECOExecutionSchedule.ComponentExecutionPhase> phases = new ArrayList<>();
        for (var phase : entry.result().executionSchedule().phases()) {
            LinkedHashSet<IPatternDetails> patterns = new LinkedHashSet<>();
            for (IPatternDetails source : phase.patternSet()) {
                IPatternDetails target = mappedPattern(source, mapping);
                if (target == null) return null;
                patterns.add(target);
            }
            List<IPatternDetails> witness = new ArrayList<>();
            for (IPatternDetails source : phase.cycleWitness()) {
                IPatternDetails target = mappedPattern(source, mapping);
                if (target == null) return null;
                witness.add(target);
            }
            phases.add(new ECOExecutionSchedule.ComponentExecutionPhase(
                phase.componentId(), phase.type(), patterns, witness));
        }
        return new ECOExecutionSchedule(phases);
    }

    private static boolean isCycleOwned(ECOExecutionSchedule schedule, IPatternDetails pattern) {
        return schedule.phases().stream()
            .filter(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE)
            .flatMap(phase -> phase.patternSet().stream())
            .anyMatch(member -> ECOPhaseScheduler.samePattern(member, pattern));
    }

    private static @Nullable IPatternDetails mappedPattern(IPatternDetails source,
            Map<IPatternDetails, IPatternDetails> mapping) {
        IPatternDetails direct = mapping.get(source);
        if (direct != null) return direct;
        List<IPatternDetails> matches = mapping.entrySet().stream()
            .filter(entry -> ECOPhaseScheduler.samePattern(source, entry.getKey()))
            .map(Map.Entry::getValue)
            .distinct()
            .toList();
        if (matches.size() == 1) return matches.getFirst();
        PatternOutputSignature outputs = outputSignature(source);
        matches = mapping.entrySet().stream()
            .filter(entry -> outputs.equals(outputSignature(entry.getKey())))
            .map(Map.Entry::getValue)
            .distinct()
            .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static PatternOutputSignature outputSignature(IPatternDetails pattern) {
        Map<AEKey, Long> outputs = new HashMap<>();
        for (var output : pattern.getOutputs()) {
            if (output != null && output.what() != null) {
                outputs.merge(output.what(), output.amount(), Long::sum);
            }
        }
        return new PatternOutputSignature(Map.copyOf(outputs));
    }

    private static PatternProductionSignature productionSignature(IPatternDetails pattern, long executions) {
        Map<AEKey, BigInteger> totals = new HashMap<>();
        BigInteger multiplier = BigInteger.valueOf(executions);
        for (var output : pattern.getOutputs()) {
            if (output != null && output.what() != null) {
                totals.merge(output.what(), BigInteger.valueOf(output.amount()).multiply(multiplier), BigInteger::add);
            }
        }
        return new PatternProductionSignature(Map.copyOf(totals));
    }

    private static String describeMismatch(int index, Entry entry, ICraftingPlan submittedPlan) {
        Map<IPatternDetails, Long> registered = entry.tasks();
        Map<IPatternDetails, Long> submitted = submittedPlan.patternTimes();
        Map<AEKey, Long> submittedUsed = counterContents(submittedPlan.usedItems());
        long registeredExecutions = registered.values().stream().mapToLong(Long::longValue).sum();
        long submittedExecutions = submitted.values().stream().mapToLong(Long::longValue).sum();
        int definitionMatches = 0;
        int outputMatches = 0;
        int amountMatches = 0;
        List<String> samples = new ArrayList<>();
        for (var source : registered.entrySet()) {
            List<Map.Entry<IPatternDetails, Long>> byDefinition = submitted.entrySet().stream()
                .filter(target -> ECOPhaseScheduler.samePattern(source.getKey(), target.getKey()))
                .toList();
            if (!byDefinition.isEmpty()) definitionMatches++;
            PatternOutputSignature outputs = outputSignature(source.getKey());
            List<Map.Entry<IPatternDetails, Long>> byOutput = submitted.entrySet().stream()
                .filter(target -> outputs.equals(outputSignature(target.getKey())))
                .toList();
            if (!byOutput.isEmpty()) outputMatches++;
            if (byOutput.stream().anyMatch(target -> source.getValue().equals(target.getValue()))) amountMatches++;
            if ((byOutput.size() != 1 || byOutput.stream().noneMatch(target ->
                    source.getValue().equals(target.getValue()))) && samples.size() < 3) {
                List<Long> submittedAmounts = byOutput.stream().map(Map.Entry::getValue).toList();
                samples.add("outputs=" + outputs.outputs() + ",registered=" + source.getValue()
                    + ",submittedSameOutput=" + submittedAmounts + ",matches=" + byOutput.size()
                    + ",registeredUsed=" + usedFor(outputs.outputs().keySet(), entry.usedItems())
                    + ",submittedUsed=" + usedFor(outputs.outputs().keySet(), submittedUsed));
            }
        }
        List<String> submittedOnly = submitted.entrySet().stream()
            .filter(target -> registered.entrySet().stream().noneMatch(source ->
                ECOPhaseScheduler.samePattern(source.getKey(), target.getKey())
                    || outputSignature(source.getKey()).equals(outputSignature(target.getKey()))
                    || (!isCycleOwned(entry.result().executionSchedule(), source.getKey())
                        && productionSignature(source.getKey(), source.getValue()).equals(
                            productionSignature(target.getKey(), target.getValue())))))
            .limit(3)
            .map(target -> "outputs=" + outputSignature(target.getKey()).outputs() + ",executions="
                + target.getValue())
            .toList();
        return "candidate=" + index + ",registeredKinds=" + registered.size() + ",submittedKinds="
            + submitted.size() + ",registeredExecutions=" + registeredExecutions + ",submittedExecutions="
            + submittedExecutions + ",definitionMatches=" + definitionMatches + ",outputMatches="
            + outputMatches + ",amountMatches=" + amountMatches + ",samples=" + samples
            + ",submittedOnly=" + submittedOnly;
    }

    private static Map<AEKey, Long> counterContents(KeyCounter counter) {
        Map<AEKey, Long> result = new HashMap<>();
        for (var value : counter) result.put(value.getKey(), value.getLongValue());
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> usedFor(Set<AEKey> keys, Map<AEKey, Long> used) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (AEKey key : keys) result.put(key, used.getOrDefault(key, 0L));
        return result;
    }

    private record PlanSignature(AEKey finalWhat, long finalAmount, Map<Object, Long> patternTimes) {
        boolean sameOutput(PlanSignature other) {
            return finalAmount == other.finalAmount && finalWhat.equals(other.finalWhat);
        }
    }

    private record PatternOutputSignature(Map<AEKey, Long> outputs) {
    }

    private record PatternProductionSignature(Map<AEKey, BigInteger> totals) {
    }

    private record Entry(ECOPlanningResult result, Map<IPatternDetails, Long> tasks,
            Map<AEKey, Long> usedItems, long createdNanos) {
    }

    private record SubmissionAlias(PlanSignature confirmedSignature, ICraftingPlan executablePlan) {
    }

    public record RecoveredSchedule(ECOExecutionSchedule schedule, String matchMode) {
    }
}
