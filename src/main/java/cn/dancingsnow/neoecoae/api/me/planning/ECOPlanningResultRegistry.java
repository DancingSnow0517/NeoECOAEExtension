package cn.dancingsnow.neoecoae.api.me.planning;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOForcedCraftingPlan;
import appeng.api.networking.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.Signature;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionContract;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
    private static final Object2ObjectLinkedOpenHashMap<Signature, Entry> RESULTS = new Object2ObjectLinkedOpenHashMap<>(64);
    private static final Map<ICraftingPlan, ExactEntry> EXACT_RESULTS = new IdentityHashMap<>();
    private static final ThreadLocal<SubmissionAlias> ACTIVE_SUBMISSION_ALIAS = new ThreadLocal<>();

    private ECOPlanningResultRegistry() {
    }

    /** Strict resolver used by confirmation and CPU code. It never transfers metadata by output alone. */
    public static @Nullable ECOExecutionContract resolveContract(ICraftingPlan plan,
            @Nullable ECOPlanningResult attached) {
        if (plan == null) return null;
        ICraftingPlan source = metadataSource(plan);
        ECOPlanningResult result = attached;
        if (result == null || result.plan() == null || !PlanIdentity.matches(source, result.plan())) {
            result = findExact(source);
            if (result == null) result = find(source);
        }
        if (result == null || result.plan() == null || !PlanIdentity.matches(source, result.plan())) return null;
        try {
            ECOExecutionContract contract = submissionContract(result);
            if (!PlanIdentity.matches(contract.planSignature(), source)) {
                Signature submittedSignature = PlanIdentity.of(plan);
                return submittedSignature == null ? null : new ECOExecutionContract(contract.planningId(),
                    submittedSignature, ExecutionMode.BLOCKED, null, "PLAN_IDENTITY_CHANGED",
                    contract.fuzzyPlanningItemIds());
            }
            if (source == plan) return contract;
            Signature submittedSignature = PlanIdentity.of(plan);
            if (submittedSignature == null) return null;
            // Only ECO's final wrapper is allowed to change waiting/missing accounting while retaining provenance.
            return new ECOExecutionContract(contract.planningId(), submittedSignature, contract.mode(),
                contract.executionPlan(), contract.error(), contract.fuzzyPlanningItemIds());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ICraftingPlan metadataSource(ICraftingPlan plan) {
        while (plan instanceof ECOForcedCraftingPlan forced) plan = forced.metadataSource();
        return plan;
    }

    private static ECOExecutionContract submissionContract(ECOPlanningResult result) {
        ECOExecutionContract contract = result.executionContract();
        // Diagnostic results can omit phase construction. That does not make an unfinished cycle native-safe.
        if (result.status() != PlanningStatus.SUCCESS && result.requiresCycleExecution()
                && contract.executable()) {
            return new ECOExecutionContract(contract.planningId(), contract.planSignature(), ExecutionMode.BLOCKED,
                null, "PLANNING_NOT_SUCCESS:" + result.status(), contract.fuzzyPlanningItemIds());
        }
        return contract;
    }

    /** Store only metadata whose source result and registered plan have the same complete identity. */
    public static void register(ICraftingPlan plan, ECOPlanningResult result) {
        long now = System.nanoTime();
        if (plan != null && result != null && result.plan() == plan) {
            synchronized (RESULTS) {
                removeExpired(now);
                EXACT_RESULTS.put(plan, new ExactEntry(result, now));
                trimEntries();
            }
        }

        RegistrationInspection inspection = inspectRegistration(plan, result);
        boolean validSchedule = inspection.reason() == null;
        boolean failClosedMetadata = inspection.canStoreFailClosedMetadata();
        if (!validSchedule && !failClosedMetadata) {
            return;
        }
        Signature signature = PlanIdentity.of(plan);
        if (signature == null) {
            return;
        }

        RecoveryState recoveryState = validSchedule
            ? RecoveryState.VALID_SCHEDULE
            : RecoveryState.MISSING_OR_INVALID_SCHEDULE;
        // Keep pure DAG construction lazy; validated cycle metadata already has an execution contract.
        ECOExecutionContract contract = inspection.cycleExpected() ? submissionContract(result) : null;
        synchronized (RESULTS) {
            removeExpired(now);
            // A complete plan signature is the execution identity. Keeping multiple planning IDs for the same
            // identity made a second identical calculation poison recovery for both submissions. Replace the
            // value atomically; the submitted pattern objects are rebound below before execution.
            RESULTS.putAndMoveToLast(signature, new Entry(result, signature, plan.patternTimes(),
                    contract, inspection.cycleExpected(), recoveryState,
                    inspection.reason(), now));
            trimEntries();
        }
    }

    /** True independently of whether schedule construction/propagation succeeded. */
    public static boolean cycleExpected(@Nullable ECOPlanningResult result) {
        return result != null && result.requiresCycleExecution();
    }

    /**
     * A fallback plan is cycle-sensitive only when the diagnostic result is itself the exact same plan. A shared
     * final output is not enough to transfer a cycle expectation between planner candidates.
     */
    public static boolean cycleSafetyRequired(ICraftingPlan publicPlan, @Nullable ECOPlanningResult result) {
        if (publicPlan == null || result == null || result.plan() == null
                || !PlanIdentity.matches(metadataSource(publicPlan), result.plan())) return false;
        return result.requiresCycleExecution();
    }

    public static List<String> describePlanningResult(@Nullable ECOPlanningResult result) {
        return componentSummary(result);
    }

    public static @Nullable ECOPlanningResult find(ICraftingPlan plan) {
        Signature signature = PlanIdentity.of(plan == null ? null : metadataSource(plan));
        if (signature == null) return null;
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            Entry entry = RESULTS.getAndMoveToLast(signature);
            return entry == null ? null : entry.result();
        }
    }

    /** Exact-object provenance, retained even when a diagnostic plan is not eligible for execution metadata. */
    public static @Nullable ECOPlanningResult findExact(@Nullable ICraftingPlan plan) {
        if (plan == null) return null;
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            ExactEntry entry = EXACT_RESULTS.get(metadataSource(plan));
            return entry == null ? null : entry.result();
        }
    }

    /**
     * Returns true only for a plan produced by the explicit ECO planning route.
     * The complete plan identity is required, so a native/foreign plan with the
     * same output cannot accidentally inherit ECO ownership.
     */
    public static boolean isECOOwnedPlan(@Nullable ICraftingPlan plan) {
        if (plan == null) return false;
        ECOPlanningResult exact = findExact(plan);
        if (exact != null && exact.plan() == metadataSource(plan)) return true;
        ECOPlanningResult registered = find(plan);
        return registered != null && registered.plan() != null
            && PlanIdentity.matches(metadataSource(plan), registered.plan());
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
        if (confirmedSignature == null || !PlanIdentity.matches(metadataSource(confirmedPlan), result.plan())) return null;

        ECOExecutionPlan executionPlan;
        try {
            executionPlan = result.executionPlan();
        } catch (RuntimeException scheduleFailure) {
            executionPlan = null;
        }
        boolean expected = cycleExpected(result);
        if (!expected && executionPlan == null) return null;
        ECOExecutionContract contract = resolveContract(confirmedPlan, result);
        return contract == null ? null : new SubmissionAlias(confirmedSignature, contract,
            result.plan().patternTimes());
    }

    /** @deprecated Prefer {@link #resolveContract(ICraftingPlan, ECOPlanningResult)} at submission. */
    @Deprecated
    public static @Nullable SubmissionMetadata activeSubmissionMetadata(ICraftingPlan plan) {
        SubmissionAlias alias = ACTIVE_SUBMISSION_ALIAS.get();
        if (alias == null || !PlanIdentity.matches(alias.confirmedSignature(), plan)) return null;
        ECOExecutionContract contract = alias.contract();
        boolean cycleExpected = contract.mode() == ExecutionMode.ORDERED_CYCLE
            || contract.mode() == ExecutionMode.DYNAMIC_CYCLE || contract.mode() == ExecutionMode.BLOCKED;
        return new SubmissionMetadata(contract.executionPlan(), cycleExpected, contract.planningId(), true, "ECO");
    }

    /**
     * Resolves and binds the execution plan at the submission boundary. The returned plan uses the pattern objects
     * from the submitted AE2 plan, so provider lookup and input extraction operate on the same objects that the CPU
     * received. A submission alias is consumed only for this synchronous call; runtime state must live in the job.
     */
    public static @Nullable ECOExecutionPlan resolveExecutionPlan(ICraftingPlan plan) {
        if (plan == null) return null;
        SubmissionAlias alias = ACTIVE_SUBMISSION_ALIAS.get();
        if (alias != null && PlanIdentity.matches(alias.confirmedSignature(), plan)) {
            return rebind(alias.contract().executionPlan(), alias.sourceTasks(), plan.patternTimes());
        }
        RecoveredExecutionMetadata recovered = recoverExecutionMetadata(plan);
        return recovered == null ? null : recovered.executionPlan();
    }

    /**
     * Returns whether an optional submission optimizer must leave this exact ECO plan untouched.
     *
     * <p>An optimizer may only be bypassed while the synchronous confirmation alias still proves
     * the complete submitted task vector. A same-output candidate, a rebuilt plan with different counts, or a
     * submission outside the confirmation scope is never protected.</p>
     */
    public static boolean shouldPreserveSubmissionPlan(@Nullable ICraftingPlan plan) {
        SubmissionAlias alias = ACTIVE_SUBMISSION_ALIAS.get();
        return alias != null && PlanIdentity.matches(alias.confirmedSignature(), plan);
    }

    /** Recover/rebind only strict metadata; no same-output or production-scaled candidate search remains. */
    public static @Nullable RecoveredExecutionMetadata recoverExecutionMetadata(ICraftingPlan plan) {
        if (plan == null) return null;
        Signature signature = PlanIdentity.of(metadataSource(plan));
        if (signature == null) return null;
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            Entry entry = RESULTS.getAndMoveToLast(signature);
            if (entry == null) return null;

            ECOExecutionPlan sourcePlan = entry.contract() == null ? null : entry.contract().executionPlan();
            if (sourcePlan == null && !entry.cycleExpected()) {
                try {
                    sourcePlan = entry.result().executionPlan();
                } catch (RuntimeException ignored) {
                    sourcePlan = null;
                }
            }
            ECOExecutionPlan rebound = rebind(sourcePlan, entry.tasks(), plan.patternTimes());
            return recovered(entry, rebound, "strict-plan-identity");
        }
    }

    private static RecoveredExecutionMetadata recovered(Entry entry,
            @Nullable ECOExecutionPlan executionPlan, String matchMode) {
        return new RecoveredExecutionMetadata(executionPlan, entry.cycleExpected(), entry.recoveryState(),
            entry.rejectionReason(), entry.result().planningId(), matchMode);
    }

    /** @deprecated Use {@link #recoverExecutionMetadata(ICraftingPlan)} to retain rejection diagnostics. */
    @Deprecated
    public static @Nullable RecoveredSchedule recoverSchedule(ICraftingPlan plan) {
        RecoveredExecutionMetadata metadata = recoverExecutionMetadata(plan);
        return metadata == null || metadata.executionPlan() == null ? null
            : new RecoveredSchedule(metadata.executionPlan().schedule(), metadata.matchMode());
    }

    public static int registeredScheduleCount() {
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            return (int) RESULTS.values().stream()
                    .filter(entry -> entry.recoveryState() == RecoveryState.VALID_SCHEDULE).count();
        }
    }

    public static int registeredMetadataCount() {
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            return RESULTS.size();
        }
    }

    public static void clear() {
        synchronized (RESULTS) {
            RESULTS.clear();
            EXACT_RESULTS.clear();
        }
        ACTIVE_SUBMISSION_ALIAS.remove();
    }

    public static String mismatchDiagnostic(ICraftingPlan plan) {
        Signature submittedSignature = PlanIdentity.of(plan);
        if (submittedSignature == null) return "submitted-signature-unavailable";
        synchronized (RESULTS) {
            removeExpired(System.nanoTime());
            List<String> candidates = new ArrayList<>();
            int index = 0;
            for (Entry entry : RESULTS.values()) {
                boolean sameOutput = entry.signature().sameFinalOutput(submittedSignature);
                boolean strict = entry.signature().equals(submittedSignature);
                candidates.add("candidate=" + index++ + ",planningId=" + entry.result().planningId()
                    + ",sameFinalOutput=" + sameOutput + ",strictMatch=" + strict
                    + ",registered=" + PlanIdentity.describe(entry.signature())
                    + ",submitted=" + PlanIdentity.describe(submittedSignature));
            }
            return candidates.isEmpty() ? "strict-candidates=0" : String.join("; ", candidates);
        }
    }

    private static void removeExpired(long now) {
        RESULTS.entrySet().removeIf(entry -> now - entry.getValue().createdNanos() > MAX_AGE_NANOS);
        EXACT_RESULTS.entrySet().removeIf(entry -> now - entry.getValue().createdNanos() > MAX_AGE_NANOS);
    }

    private static void trimEntries() {
        while (RESULTS.size() > MAX_ENTRIES) {
            RESULTS.removeFirst();
        }
        while (EXACT_RESULTS.size() > MAX_ENTRIES) {
            EXACT_RESULTS.remove(EXACT_RESULTS.keySet().iterator().next());
        }
    }

    private static @Nullable ECOExecutionPlan rebind(@Nullable ECOExecutionPlan sourcePlan,
            Map<IPatternDetails, Long> sourceTasks, Map<IPatternDetails, Long> submittedTasks) {
        if (sourcePlan == null) return null;
        Map<IPatternDetails, IPatternDetails> mapping = taskMapping(sourceTasks, submittedTasks);
        if (mapping == null) return null;
        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>();
        for (var task : sourcePlan.tasks()) {
            IPatternDetails target = mappedPattern(task.pattern(), mapping);
            if (target == null) return null;
            tasks.add(new ECOExecutionPlan.TaskSpec(task.id(), task.identity(), target,
                ECOExecutionPlan.PatternRuntimeInfo.from(target), task.totalCount(), task.phaseIndex(), task.kind(),
                task.inputAllocations()));
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
            new ECOExecutionSchedule(schedulePhases, sourcePlan.schedule().dependencies()));
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
        // A pure DAG result can defer schedule construction until submission. Cycle metadata is still validated
        // here because a cycle-expected result must never be published without a trustworthy phase contract.
        if (reason == null && cycleExpected) {
            try {
                executionPlan = result.executionPlan();
                ECOExecutionSchedule schedule = executionPlan.schedule();
                if (schedule.phases().isEmpty()) reason = "SCHEDULE_EMPTY";
                else if (cycleExpected && schedule.phases().stream().noneMatch(phase ->
                        phase.type() != ECOExecutionSchedule.Type.DAG)) reason = "NO_CYCLE_PHASE";
                else if (cycleExpected && schedule.phases().stream()
                        .filter(phase -> phase.type() != ECOExecutionSchedule.Type.DAG)
                        .allMatch(phase -> phase.patternSet().isEmpty())) reason = "EMPTY_CYCLE_PATTERN_SET";
            } catch (RuntimeException scheduleFailure) {
                reason = "SCHEDULE_BUILD_FAILED:" + scheduleFailure.getClass().getSimpleName();
            }
        }
        return new RegistrationInspection(status, planSimulation, resultPlanSimulation, strictPlanMatch,
            executionPlan, cycleExpected, reason);
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

    /** @deprecated Compatibility projection of the scoped execution contract. */
    @Deprecated
    public record SubmissionMetadata(@Nullable ECOExecutionPlan executionPlan, boolean cycleExpected,
            @Nullable UUID planningId, boolean metadataMatch, String selectedPlanner) {
        public SubmissionMetadata(@Nullable ECOExecutionPlan executionPlan, boolean cycleExpected) {
            this(executionPlan, cycleExpected, null, true, "ECO");
        }
        public @Nullable ECOExecutionSchedule executionSchedule() {
            return executionPlan == null ? null : executionPlan.schedule();
        }
    }

    private record SubmissionAlias(Signature confirmedSignature, ECOExecutionContract contract,
            Map<IPatternDetails, Long> sourceTasks) {
    }

    private record Entry(ECOPlanningResult result, Signature signature, Map<IPatternDetails, Long> tasks,
            @Nullable ECOExecutionContract contract, boolean cycleExpected, RecoveryState recoveryState,
            @Nullable String rejectionReason, long createdNanos) {
    }

    private record ExactEntry(ECOPlanningResult result, long createdNanos) {
    }

    private record RegistrationInspection(@Nullable PlanningStatus status, boolean planSimulation,
            boolean resultPlanSimulation, boolean strictPlanMatch, @Nullable ECOExecutionPlan executionPlan,
            boolean cycleExpected, @Nullable String reason) {
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

    /** @deprecated Compatibility projection; use {@link RecoveredExecutionMetadata}. */
    @Deprecated
    public record RecoveredSchedule(ECOExecutionSchedule schedule, String matchMode) {
    }
}
