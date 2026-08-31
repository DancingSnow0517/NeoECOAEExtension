package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.Signature;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionContract;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Short-lived side channel for planning metadata when a planner or confirmation integration copies a
 * {@link ICraftingPlan}. The executable plan is always the object received by submission; this registry only
 * recovers metadata that is proved to describe that exact executable task vector.
 */
public final class ECOPlanningResultRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");
    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_AGE_NANOS = Duration.ofMinutes(10).toNanos();
    private static final Map<Signature, List<Entry>> RESULTS = new LinkedHashMap<>(64, 0.75f, true);
    private static final ThreadLocal<SubmissionAlias> ACTIVE_SUBMISSION_ALIAS = new ThreadLocal<>();

    private ECOPlanningResultRegistry() {
    }

    /** Strict resolver used by confirmation and CPU code. It never transfers metadata by output alone. */
    public static @Nullable ECOExecutionContract resolveContract(ICraftingPlan plan,
            @Nullable ECOPlanningResult attached) {
        if (plan == null) return null;
        if (attached != null && attached.plan() != null && PlanIdentity.matches(plan, attached.plan())) {
            try { return attached.executionContract(); }
            catch (RuntimeException ignored) { return null; }
        }
        ECOPlanningResult registered = find(plan);
        if (registered == null || registered.plan() == null || !PlanIdentity.matches(plan, registered.plan())) return null;
        try { return registered.executionContract(); }
        catch (RuntimeException ignored) { return null; }
    }

    /** Store only metadata whose source result and registered plan have the same complete identity. */
    public static void register(ICraftingPlan plan, ECOPlanningResult result) {
        RegistrationInspection inspection = inspectRegistration(plan, result);
        boolean validSchedule = inspection.reason() == null;
        boolean failClosedMetadata = inspection.canStoreFailClosedMetadata();
        if (!validSchedule && !failClosedMetadata) {
            logRegistration(false, inspection, plan, result, null, null);
            return;
        }
        Signature signature = PlanIdentity.of(plan);
        if (signature == null) {
            logRegistration(false, inspection.withReason("SIGNATURE_UNAVAILABLE"), plan, result, null, null);
            return;
        }

        RecoveryState recoveryState = validSchedule
            ? RecoveryState.VALID_SCHEDULE
            : RecoveryState.MISSING_OR_INVALID_SCHEDULE;
        UUID planningId = result.planningId();
        long now = System.nanoTime();
        synchronized (RESULTS) {
            removeExpired(now);
            List<Entry> entries = RESULTS.computeIfAbsent(signature, ignored -> new ArrayList<>());
            // Multiple aliases of one ECO planning result are one candidate. Different planning IDs remain
            // independent even if they happen to have byte-for-byte equal plans.
            entries.removeIf(entry -> entry.planningId().equals(planningId));
            entries.add(new Entry(result, signature, Map.copyOf(plan.patternTimes()),
                inspection.executionPlan(), inspection.cycleExpected(),
                recoveryState, inspection.reason(), planningId, now));
            trimEntries();
        }
        logRegistration(validSchedule, inspection, plan, result, planningId, recoveryState);
    }

    /** True independently of whether schedule construction/propagation succeeded. */
    public static boolean cycleExpected(@Nullable ECOPlanningResult result) {
        return result != null && result.executionRequirement() != ECOExecutionRequirement.NONE;
    }

    /**
     * A fallback plan is cycle-sensitive only when the diagnostic result is itself the exact same plan. A shared
     * final output is not enough to transfer a cycle expectation between planner candidates.
     */
    public static boolean cycleSafetyRequired(ICraftingPlan publicPlan, @Nullable ECOPlanningResult result) {
        if (publicPlan == null || result == null || result.plan() == null
                || !PlanIdentity.matches(publicPlan, result.plan())) return false;
        return result.executionRequirement() != ECOExecutionRequirement.NONE;
    }

    public static List<String> describePlanningResult(@Nullable ECOPlanningResult result) {
        return componentSummary(result);
    }

    public static @Nullable ECOPlanningResult find(ICraftingPlan plan) {
        Signature signature = PlanIdentity.of(plan);
        if (signature == null) return null;
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            return uniqueEntry(RESULTS.get(signature));
        }
    }

    /**
     * Emits one low-frequency candidate decision report for the final plan. The output-only comparison here is
     * diagnostic text only; it is never used to attach metadata or choose a plan.
     */
    public static void logCandidateSelection(@Nullable ICraftingPlan submittedPlan,
            @Nullable ECOPlanningResult selected) {
        Signature submittedSignature = PlanIdentity.of(submittedPlan);
        if (submittedSignature == null) return;
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            boolean emitted = false;
            for (List<Entry> entries : RESULTS.values()) {
                for (Entry entry : entries) {
                    if (!entry.signature().sameFinalOutput(submittedSignature)) continue;
                    boolean strict = entry.signature().equals(submittedSignature);
                    boolean isSelected = selected != null && entry.result() == selected && strict;
                    LOGGER.debug("[ECO-CANDIDATE] planningId={} finalOutput={} signature={} executions={} "
                            + "selected={} rejected={} reason={}", entry.planningId(), submittedPlan.finalOutput(),
                        PlanIdentity.describe(entry.signature()), entry.signature().executionCount(), isSelected,
                        !isSelected, strict ? isSelected ? "" : "strict-candidate-not-selected" : "strict-plan-mismatch");
                    emitted = true;
                }
            }
            if (!emitted) {
                LOGGER.debug("[ECO-CANDIDATE] planningId={} finalOutput={} signature={} executions={} selected={} "
                        + "rejected={} reason={}", selected == null ? null : selected.planningId(),
                    submittedPlan.finalOutput(), PlanIdentity.describe(submittedSignature),
                    submittedSignature.executionCount(), selected != null, selected == null,
                    selected == null ? "no-registered-candidate" : "selected-result-not-registered");
            }
        }
    }

    /**
     * Binds metadata to one synchronous confirmation submission. The binding contains no executable plan and can
     * therefore never replace the plan passed by the confirmation path.
     */
    public static <T> T withSubmissionAlias(ICraftingPlan confirmedPlan, @Nullable ECOPlanningResult result,
            Supplier<T> submission) {
        Objects.requireNonNull(submission, "submission");
        SubmissionAlias previous = ACTIVE_SUBMISSION_ALIAS.get();
        SubmissionAlias current = createSubmissionAlias(confirmedPlan, result);
        if (current == null) ACTIVE_SUBMISSION_ALIAS.remove();
        else ACTIVE_SUBMISSION_ALIAS.set(current);
        try {
            return submission.get();
        } finally {
            if (previous == null) ACTIVE_SUBMISSION_ALIAS.remove();
            else ACTIVE_SUBMISSION_ALIAS.set(previous);
        }
    }

    private static @Nullable SubmissionAlias createSubmissionAlias(ICraftingPlan confirmedPlan,
            @Nullable ECOPlanningResult result) {
        if (confirmedPlan == null || result == null || result.plan() == null
                || result.status() != PlanningStatus.SUCCESS || result.plan().simulation()) return null;
        Signature confirmedSignature = PlanIdentity.of(confirmedPlan);
        if (confirmedSignature == null || !PlanIdentity.matches(confirmedSignature, result.plan())) return null;

        ECOExecutionPlan executionPlan;
        try {
            executionPlan = result.executionPlan();
        } catch (RuntimeException scheduleFailure) {
            LOGGER.error("[ECO-SUBMISSION] failed to build confirmed metadata schedule; "
                + "preserving cycleExpected for fail-closed dispatch", scheduleFailure);
            executionPlan = null;
        }
        boolean expected = cycleExpected(result);
        if (!expected && executionPlan == null) return null;
        return new SubmissionAlias(confirmedSignature, result.planningId(), executionPlan, expected, "ECO");
    }

    /** Metadata is visible only if the submitted plan proves the same complete identity as the confirmation plan. */
    public static @Nullable SubmissionMetadata activeSubmissionMetadata(ICraftingPlan plan) {
        SubmissionAlias alias = ACTIVE_SUBMISSION_ALIAS.get();
        if (alias == null || !PlanIdentity.matches(alias.confirmedSignature(), plan)) return null;
        return new SubmissionMetadata(alias.executionPlan(), alias.cycleExpected(), alias.planningId(), true,
            alias.selectedPlanner());
    }

    /**
     * Returns whether an optional submission optimizer must leave this exact ECO plan untouched.
     *
     * <p>This is deliberately stricter than {@link #activeSubmissionMetadata(ICraftingPlan)}'s caller-facing
     * metadata contract: an optimizer may only be bypassed while the synchronous confirmation alias still proves
     * the complete submitted task vector. A same-output candidate, a rebuilt plan with different counts, or a
     * submission outside the confirmation scope is never protected.</p>
     */
    public static boolean shouldPreserveSubmissionPlan(@Nullable ICraftingPlan plan) {
        SubmissionAlias alias = ACTIVE_SUBMISSION_ALIAS.get();
        boolean preserve = alias != null && PlanIdentity.matches(alias.confirmedSignature(), plan);
        if (preserve) {
            LOGGER.debug("[ECO-SUBMISSION] preserving strictly bound plan from external task-vector rewrite "
                    + "planningId={} signature={}", alias.planningId(), PlanIdentity.describe(alias.confirmedSignature()));
        }
        return preserve;
    }

    /**
     * The submitted plan is authoritative. This method is retained as a compatibility hook for cluster/CPU
     * integrations, but it deliberately returns the input object on every path.
     */
    public static ICraftingPlan resolveSubmissionPlan(ICraftingPlan submittedPlan) {
        Signature submittedSignature = PlanIdentity.of(submittedPlan);
        SubmissionAlias alias = ACTIVE_SUBMISSION_ALIAS.get();
        boolean metadataMatch = alias != null && PlanIdentity.matches(alias.confirmedSignature(), submittedPlan);
        long submittedExecutions = PlanIdentity.executionCount(
            submittedPlan == null ? null : submittedPlan.patternTimes());
        LOGGER.info("[ECO-SUBMISSION] selectedPlanner={} submittedSignature={} submittedExecutions={} "
                + "metadataPlanningId={} metadataMatch={} planReplaced=false cpuExecutions={}",
            alias == null ? "unknown" : alias.selectedPlanner(), PlanIdentity.describe(submittedSignature),
            submittedExecutions, alias == null ? null : alias.planningId(), metadataMatch, submittedExecutions);
        return submittedPlan;
    }

    /** Recover/rebind only strict metadata; no same-output or production-scaled candidate search remains. */
    public static @Nullable RecoveredExecutionMetadata recoverExecutionMetadata(ICraftingPlan plan) {
        Signature signature = PlanIdentity.of(plan);
        if (signature == null) return null;
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            List<Entry> entries = RESULTS.get(signature);
            Entry entry = uniqueEntryObject(entries);
            if (entry == null) return null;

            ECOExecutionPlan rebound = rebind(entry, plan.patternTimes());
            return recovered(entry, rebound, "strict-plan-identity");
        }
    }

    public static @Nullable RecoveredSchedule recoverSchedule(ICraftingPlan plan) {
        RecoveredExecutionMetadata metadata = recoverExecutionMetadata(plan);
        return metadata == null || metadata.executionPlan() == null
            ? null : new RecoveredSchedule(metadata.executionPlan().schedule(), metadata.matchMode());
    }

    private static RecoveredExecutionMetadata recovered(Entry entry,
            @Nullable ECOExecutionPlan executionPlan, String matchMode) {
        return new RecoveredExecutionMetadata(executionPlan, entry.cycleExpected(), entry.recoveryState(),
            entry.rejectionReason(), entry.planningId(), matchMode);
    }

    public static int registeredScheduleCount() {
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            return (int) RESULTS.values().stream().flatMap(List::stream)
                .filter(entry -> entry.recoveryState() == RecoveryState.VALID_SCHEDULE).count();
        }
    }

    public static int registeredMetadataCount() {
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            return RESULTS.values().stream().mapToInt(List::size).sum();
        }
    }

    public static String mismatchDiagnostic(ICraftingPlan plan) {
        Signature submittedSignature = PlanIdentity.of(plan);
        if (submittedSignature == null) return "submitted-signature-unavailable";
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            List<String> candidates = new ArrayList<>();
            int index = 0;
            for (List<Entry> entries : RESULTS.values()) {
                for (Entry entry : entries) {
                    boolean sameOutput = entry.signature().sameFinalOutput(submittedSignature);
                    boolean strict = entry.signature().equals(submittedSignature);
                    candidates.add("candidate=" + index++ + ",planningId=" + entry.planningId()
                        + ",sameFinalOutput=" + sameOutput + ",strictMatch=" + strict
                        + ",registered=" + PlanIdentity.describe(entry.signature())
                        + ",submitted=" + PlanIdentity.describe(submittedSignature));
                }
            }
            return candidates.isEmpty() ? "strict-candidates=0" : String.join("; ", candidates);
        }
    }

    private static void removeExpired(long now) {
        RESULTS.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(value -> now - value.createdNanos() > MAX_AGE_NANOS);
            return entry.getValue().isEmpty();
        });
    }

    private static void trimEntries() {
        while (RESULTS.values().stream().mapToInt(List::size).sum() > MAX_ENTRIES) {
            var oldestBucket = RESULTS.entrySet().iterator().next();
            oldestBucket.getValue().clear();
            RESULTS.remove(oldestBucket.getKey());
        }
    }

    private static @Nullable ECOPlanningResult uniqueEntry(@Nullable List<Entry> entries) {
        Entry entry = uniqueEntryObject(entries);
        return entry == null ? null : entry.result();
    }

    private static @Nullable Entry uniqueEntryObject(@Nullable List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        UUID planningId = entries.getFirst().planningId();
        return entries.stream().allMatch(entry -> entry.planningId().equals(planningId)) ? entries.getFirst() : null;
    }

    private static @Nullable ECOExecutionPlan rebind(Entry entry,
            Map<IPatternDetails, Long> submittedTasks) {
        ECOExecutionPlan sourcePlan = entry.executionPlan();
        if (sourcePlan == null) return null;
        Map<IPatternDetails, IPatternDetails> mapping = taskMapping(entry.tasks(), submittedTasks);
        if (mapping == null) return null;
        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>();
        for (var task : sourcePlan.tasks()) {
            IPatternDetails target = mappedPattern(task.pattern(), mapping);
            if (target == null) return null;
            tasks.add(new ECOExecutionPlan.TaskSpec(task.id(), task.identity(), target,
                ECOExecutionPlan.PatternRuntimeInfo.from(target), task.totalCount(), task.phaseIndex(), task.kind()));
        }
        List<ECOExecutionSchedule.ComponentExecutionPhase> schedulePhases = new ArrayList<>();
        for (var phase : sourcePlan.schedule().phases()) {
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
            schedulePhases.add(new ECOExecutionSchedule.ComponentExecutionPhase(
                phase.componentId(), phase.type(), patterns, witness));
        }
        return new ECOExecutionPlan(sourcePlan.signature(), sourcePlan.mode(), tasks, sourcePlan.phases(),
            new ECOExecutionSchedule(schedulePhases));
    }

    private static @Nullable Map<IPatternDetails, IPatternDetails> taskMapping(
            Map<IPatternDetails, Long> sourceTasks, Map<IPatternDetails, Long> submittedTasks) {
        if (sourceTasks.size() != submittedTasks.size()
                || !PlanIdentity.sameTaskCounts(sourceTasks, submittedTasks)) return null;
        Map<IPatternDetails, IPatternDetails> mapping = new HashMap<>();
        Set<IPatternDetails> usedTargets = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (var sourceTask : sourceTasks.entrySet()) {
            List<IPatternDetails> matches = submittedTasks.entrySet().stream()
                .filter(target -> !usedTargets.contains(target.getKey()))
                .filter(target -> sourceTask.getValue().equals(target.getValue()))
                .filter(target -> ECOPhaseScheduler.samePattern(sourceTask.getKey(), target.getKey()))
                .map(Map.Entry::getKey)
                .toList();
            if (matches.size() != 1) return null;
            mapping.put(sourceTask.getKey(), matches.getFirst());
            usedTargets.add(matches.getFirst());
        }
        return usedTargets.size() == submittedTasks.size() ? mapping : null;
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
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static RegistrationInspection inspectRegistration(@Nullable ICraftingPlan plan,
            @Nullable ECOPlanningResult result) {
        PlanningStatus status = result == null ? null : result.status();
        boolean planSimulation = plan != null && plan.simulation();
        boolean resultPlanSimulation = result != null && result.plan() != null && result.plan().simulation();
        boolean strictPlanMatch = plan != null && result != null && result.plan() != null
            && PlanIdentity.matches(plan, result.plan());
        ECOExecutionPlan executionPlan = null;
        String reason = null;
        if (plan == null) reason = "PLAN_NULL";
        else if (result == null) reason = "RESULT_NULL";
        else if (result.plan() == null) reason = "RESULT_PLAN_NULL";
        else if (!strictPlanMatch) reason = "PLAN_IDENTITY_MISMATCH";
        else if (status != PlanningStatus.SUCCESS) reason = "STATUS_NOT_SUCCESS";
        else if (planSimulation) reason = "PLAN_SIMULATION";
        else if (resultPlanSimulation) reason = "RESULT_PLAN_SIMULATION";
        boolean cycleExpected = cycleExpected(result);
        if (reason == null) {
            try {
                executionPlan = result.executionPlan();
                ECOExecutionSchedule schedule = executionPlan.schedule();
                if (schedule.phases().isEmpty()) reason = "SCHEDULE_EMPTY";
                else if (cycleExpected && schedule.phases().stream().noneMatch(phase ->
                        phase.type() == ECOExecutionSchedule.Type.CYCLE)) reason = "NO_CYCLE_PHASE";
                else if (cycleExpected && schedule.phases().stream()
                        .filter(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE)
                        .allMatch(phase -> phase.patternSet().isEmpty())) reason = "EMPTY_CYCLE_PATTERN_SET";
            } catch (RuntimeException scheduleFailure) {
                reason = "SCHEDULE_BUILD_FAILED:" + scheduleFailure.getClass().getSimpleName();
            }
        }
        return new RegistrationInspection(status, planSimulation, resultPlanSimulation, strictPlanMatch,
            executionPlan, cycleExpected, reason);
    }

    private static void logRegistration(boolean registered, RegistrationInspection inspection,
            @Nullable ICraftingPlan plan, @Nullable ECOPlanningResult result,
            @Nullable UUID planningId, @Nullable RecoveryState recoveryState) {
        ECOExecutionSchedule schedule = inspection.executionPlan() == null ? null : inspection.executionPlan().schedule();
        int phaseCount = schedule == null ? 0 : schedule.phases().size();
        long cyclePhaseCount = schedule == null ? 0 : schedule.phases().stream()
            .filter(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE).count();
        String message = "[ECO-METADATA] status={} strictPlanMatch={} cycleExpected={} phaseCount={} "
            + "cyclePhaseCount={} registered={} recoveryState={} reason={} planningId={} signature={}";
        Signature signature = PlanIdentity.of(plan);
        if (registered || inspection.cycleExpected()) {
            LOGGER.info(message, inspection.status(), inspection.strictPlanMatch(), inspection.cycleExpected(),
                phaseCount, cyclePhaseCount, registered, recoveryState,
                registered ? "REGISTERED" : inspection.reason(), planningId, PlanIdentity.describe(signature));
        } else {
            LOGGER.debug(message, inspection.status(), inspection.strictPlanMatch(), inspection.cycleExpected(),
                phaseCount, cyclePhaseCount, false, recoveryState, inspection.reason(), planningId,
                PlanIdentity.describe(signature));
        }
    }

    private static List<String> componentSummary(@Nullable ECOPlanningResult result) {
        if (result == null) return List.of();
        return result.components().stream().map(component -> {
            long cycleFirings = component.cycleResult() == null ? 0L
                : PlanIdentity.executionCount(component.cycleResult().patternTimes());
            return component.componentId() + ":" + component.type() + "/" + component.status()
                + "(cycleStatus=" + component.cycleStatus()
                + ",cycleResult=" + (component.cycleResult() == null ? null : component.cycleResult().status())
                + ",cycleFirings=" + cycleFirings + ",patterns=" + component.patterns().size()
                + ",executionPatterns=" + component.executionPatterns().size() + ")";
        }).toList();
    }

    public record SubmissionMetadata(@Nullable ECOExecutionPlan executionPlan, boolean cycleExpected,
            @Nullable UUID planningId, boolean metadataMatch, String selectedPlanner) {
        public SubmissionMetadata(@Nullable ECOExecutionPlan executionPlan, boolean cycleExpected) {
            this(executionPlan, cycleExpected, null, true, "ECO");
        }
        public @Nullable ECOExecutionSchedule executionSchedule() {
            return executionPlan == null ? null : executionPlan.schedule();
        }
    }

    private record SubmissionAlias(Signature confirmedSignature, UUID planningId,
            @Nullable ECOExecutionPlan executionPlan, boolean cycleExpected, String selectedPlanner) {
    }

    private record Entry(ECOPlanningResult result, Signature signature, Map<IPatternDetails, Long> tasks,
            @Nullable ECOExecutionPlan executionPlan, boolean cycleExpected, RecoveryState recoveryState,
            @Nullable String rejectionReason, UUID planningId, long createdNanos) {
    }

    private record RegistrationInspection(@Nullable PlanningStatus status, boolean planSimulation,
            boolean resultPlanSimulation, boolean strictPlanMatch, @Nullable ECOExecutionPlan executionPlan,
            boolean cycleExpected, @Nullable String reason) {
        RegistrationInspection withReason(String replacement) {
            return new RegistrationInspection(status, planSimulation, resultPlanSimulation, strictPlanMatch,
                executionPlan, cycleExpected, replacement);
        }

        boolean canStoreFailClosedMetadata() {
            if (!cycleExpected || reason == null || !strictPlanMatch) return false;
            return reason.equals("SCHEDULE_NULL") || reason.equals("SCHEDULE_EMPTY")
                || reason.equals("NO_CYCLE_PHASE") || reason.equals("EMPTY_CYCLE_PATTERN_SET")
                || reason.equals("STATUS_NOT_SUCCESS") || reason.startsWith("SCHEDULE_BUILD_FAILED:");
        }
    }

    public enum RecoveryState {
        VALID_SCHEDULE,
        MISSING_OR_INVALID_SCHEDULE
    }

    public record RecoveredExecutionMetadata(@Nullable ECOExecutionPlan executionPlan, boolean cycleExpected,
            RecoveryState state, @Nullable String rejectionReason, UUID planningId, String matchMode) {
        public @Nullable ECOExecutionSchedule schedule() {
            return executionPlan == null ? null : executionPlan.schedule();
        }
    }

    public record RecoveredSchedule(ECOExecutionSchedule schedule, String matchMode) {
    }
}
