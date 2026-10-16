package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.ExecutionProvenance;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.UUID;
import java.util.Set;
import java.math.BigInteger;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Planning answer. Its execution plan is interpreted at most once, on first access when needed. */
public final class ECOPlanningResult {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ECOPlanningResult.class);
    private final PlanningStatus status;
    private final @Nullable CraftingPlan plan;
    private final ECOPlanTrace trace;
    private final List<CycleDiagnostic> cycles;
    private final List<ComponentPlanningResult> components;
    private final List<Integer> executionComponentOrder;
    private final long calculationNanos;
    private BigInteger theoreticalBytes = BigInteger.ZERO;
    private java.util.Map<IPatternDetails, PlannerAmount> exactPatternTimes;
    private java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactUsedItems = java.util.Map.of();
    private java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactEmittedItems = java.util.Map.of();
    private java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactMissingItems = java.util.Map.of();
    private volatile Set<ResourceLocation> fuzzyPlanningItemIds = Set.of();
    private final UUID planningId;
    // Keep the diagnostic classification separate: unsuccessful cycle results retain their cycle
    // expectation even though their compatibility contract intentionally remains NATIVE.
    private final ExecutionMode classifiedMode;
    private final Object executionPlanLock = new Object();
    private volatile @Nullable ECOExecutionContract executionContract;
    // A strict contract requires a signature. Retain identity failures separately so diagnostics can
    // still report a resolved BLOCKED answer when no valid contract can be constructed.
    private volatile @Nullable String resolutionError;
    private final @Nullable ExecutionProvenance provenance;

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos, UUID planningId,
            @Nullable ExecutionProvenance provenance) {
        this.status = status;
        this.plan = plan;
        var firings = new java.util.LinkedHashMap<IPatternDetails, PlannerAmount>();
        if (plan != null) plan.patternTimes().forEach((pattern, count) -> firings.put(pattern, PlannerAmount.of(count)));
        this.exactPatternTimes = firings;
        this.trace = trace;
        this.cycles = cycles;
        this.components = components;
        this.executionComponentOrder = executionComponentOrder;
        this.calculationNanos = Math.max(0L, calculationNanos);
        this.planningId = planningId == null ? UUID.randomUUID() : planningId;
        this.provenance = provenance;
        if (status == PlanningStatus.SUCCESS && plan == null) {
            throw new IllegalArgumentException("A successful planning result requires a plan");
        }
        ExecutionMode mode = plan == null ? ExecutionMode.NATIVE
            : classifyExecutionMode(this.components, plan.patternTimes());
        this.classifiedMode = mode == ExecutionMode.NATIVE && status == PlanningStatus.SUCCESS
            && !this.components.isEmpty() ? ExecutionMode.PHASED_DAG : mode;
    }

    private ECOExecutionContract buildExecutionContract() {
        PlanIdentity.Signature signature;
        try {
            signature = PlanIdentity.of(plan);
            if (signature == null) throw new IllegalStateException("Plan identity unavailable");
        } catch (RuntimeException failure) {
            if (needsExecutionPlan() && resolutionError == null) {
                resolutionError = executionPlanBuildError(failure);
            }
            throw failure;
        }
        ECOExecutionPlan built = null;
        String error = resolutionError != null ? resolutionError
            : classifiedMode == ExecutionMode.BLOCKED ? "CYCLE_NOT_SOLVED" : null;
        if (needsExecutionPlan() && error == null) {
            try {
                built = ECOExecutionPlanBuilder.build(signature, classifiedMode, this.components,
                    this.executionComponentOrder, plan.patternTimes(), provenance);
                if (built.phases().isEmpty()) {
                    // Empty cycle metadata remains a hard failure; an empty DAG uses native execution.
                    if (requiresCycleExecution()) error = "EXECUTION_PLAN_EMPTY";
                    built = null;
                }
            } catch (RuntimeException failure) {
                error = executionPlanBuildError(failure);
            }
        }
        return new ECOExecutionContract(planningId, signature,
            error != null ? ExecutionMode.BLOCKED : built == null ? ExecutionMode.NATIVE : built.mode(),
            error == null ? built : null, error, fuzzyPlanningItemIds);
    }

    private boolean needsExecutionPlan() {
        return status == PlanningStatus.SUCCESS && classifiedMode != ExecutionMode.BLOCKED
            && !components.isEmpty();
    }

    private String executionPlanBuildError(RuntimeException failure) {
        LOGGER.error("[ECO-EXECUTION-PLAN] Failed to build execution plan planningId={}", planningId, failure);
        return "EXECUTION_PLAN_BUILD_FAILED:" + failure.getClass().getSimpleName()
            + (failure.getMessage() == null ? "" : ":" + failure.getMessage());
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos, UUID planningId) {
        this(status, plan, trace, cycles, components, executionComponentOrder, calculationNanos, planningId, null);
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos) {
        this(status, plan, trace, cycles, components, executionComponentOrder, calculationNanos, UUID.randomUUID());
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos,
            @Nullable ExecutionProvenance provenance) {
        this(status, plan, trace, cycles, components, executionComponentOrder, calculationNanos,
            UUID.randomUUID(), provenance);
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, long calculationNanos) {
        this(status, plan, trace, cycles, List.of(), List.of(), calculationNanos);
    }

    public PlanningStatus status() { return status; }
    public @Nullable CraftingPlan plan() { return plan; }
    public ECOPlanTrace trace() { return trace; }
    public List<CycleDiagnostic> cycles() { return cycles; }
    public List<ComponentPlanningResult> components() { return components; }
    public List<Integer> executionComponentOrder() { return executionComponentOrder; }
    public long calculationNanos() { return calculationNanos; }
    public BigInteger theoreticalBytes() { return theoreticalBytes; }
    /** Final committed firing vector for reports, including counts that cannot fit an AE2 plan. */
    public java.util.Map<IPatternDetails, PlannerAmount> exactPatternTimes() { return exactPatternTimes; }
    public void setExactPatternTimes(java.util.Map<IPatternDetails, PlannerAmount> counts) {
        exactPatternTimes = counts;
    }
    public void setExactMaterials(java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> used,
            java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> emitted,
            java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> missing) {
        exactUsedItems = used;
        exactEmittedItems = emitted;
        exactMissingItems = missing;
    }
    public java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactUsedItems() { return exactUsedItems; }
    public java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactEmittedItems() { return exactEmittedItems; }
    public java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactMissingItems() { return exactMissingItems; }
    public void setTheoreticalBytes(PlannerAmount bytes) {
        theoreticalBytes = bytes == null ? BigInteger.ZERO : bytes.toBigInteger();
    }
    public Set<ResourceLocation> fuzzyPlanningItemIds() { return fuzzyPlanningItemIds; }
    public void setFuzzyPlanningItemIds(Set<ResourceLocation> itemIds) {
        Set<ResourceLocation> snapshot = itemIds == null ? Set.of() : Set.copyOf(itemIds);
        synchronized (executionPlanLock) {
            fuzzyPlanningItemIds = snapshot;
            ECOExecutionContract cached = executionContract;
            if (cached != null && !cached.fuzzyPlanningItemIds().equals(snapshot)) {
                executionContract = new ECOExecutionContract(cached.planningId(), cached.planSignature(),
                    cached.mode(), cached.executionPlan(), cached.error(), snapshot);
            }
        }
    }
    public UUID planningId() { return planningId; }
    /** Diagnostic classification without forcing plan construction. See executionContract() for submission. */
    public ExecutionMode executionMode() {
        ECOExecutionContract cached = executionContract;
        return resolutionError != null || cached != null && cached.mode() == ExecutionMode.BLOCKED
            ? ExecutionMode.BLOCKED : classifiedMode;
    }
    public boolean requiresCycleExecution() {
        ExecutionMode mode = executionMode();
        return mode == ExecutionMode.ORDERED_CYCLE || mode == ExecutionMode.DYNAMIC_CYCLE
            || mode == ExecutionMode.BLOCKED;
    }
    /** @deprecated Use executionMode() or requiresCycleExecution() for diagnostics. */
    @Deprecated
    public ECOExecutionRequirement executionRequirement() {
        return ECOExecutionRequirement.from(executionMode());
    }
    public @Nullable String executionPlanError() {
        if (classifiedMode == ExecutionMode.BLOCKED) return "CYCLE_NOT_SOLVED";
        if (!needsExecutionPlan()) return null;
        ECOExecutionContract cached = executionContract;
        if (cached != null) return cached.error();
        String failure = resolutionError;
        if (failure != null) return failure;
        synchronized (executionPlanLock) {
            if (resolutionError != null) return resolutionError;
            try {
                return executionContract().error();
            } catch (RuntimeException unavailableIdentity) {
                if (resolutionError != null) return resolutionError;
                throw unavailableIdentity;
            }
        }
    }
    public @Nullable ExecutionProvenance provenance() { return provenance; }

    public boolean shouldUseNativeFallback() {
        return status == PlanningStatus.PARTIAL_UNSUPPORTED || status == PlanningStatus.UNSUPPORTED
            || status == PlanningStatus.INTERNAL_ERROR;
    }

    public ECOExecutionPlan executionPlan() {
        String error = executionPlanError();
        if (error != null) throw new IllegalStateException(error);
        ECOExecutionContract cached = executionContract;
        if (cached == null || cached.executionPlan() == null) {
            throw new IllegalStateException("This result has no phased execution plan");
        }
        return cached.executionPlan();
    }

    public ECOExecutionSchedule executionSchedule() { return executionPlan().schedule(); }

    /** Expanded compatibility projection; compressed cycles deliberately do not expand here. */
    public List<IPatternDetails> cycleWitness() {
        return executionSchedule().phases().stream().filter(p -> p.type() == ECOExecutionSchedule.Type.CYCLE)
            .flatMap(p -> p.cycleWitness().stream())
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    public ECOExecutionContract executionContract() {
        if (plan == null) throw new IllegalStateException("Cannot create an execution contract without a plan");
        ECOExecutionContract cached = executionContract;
        if (cached != null) return cached;
        synchronized (executionPlanLock) {
            cached = executionContract;
            if (cached == null) {
                cached = buildExecutionContract();
                executionContract = cached;
            }
            return cached;
        }
    }

    static ExecutionMode classifyExecutionMode(List<ComponentPlanningResult> components,
            Map<IPatternDetails, Long> plannedTasks) {
        Map<PlanIdentity.PatternIdentity, Long> positivePlannedCounts = new HashMap<>();
        Set<PlanIdentity.PatternIdentity> overflowingIdentities = new HashSet<>();
        Map<IPatternDetails, Long> fallbackCounts = new IdentityHashMap<>();
        Set<IPatternDetails> overflowingFallbacks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : plannedTasks.entrySet()) {
            Long count = entry.getValue();
            if (count == null || count <= 0L) continue;
            PlanIdentity.PatternIdentity identity = safePatternIdentity(entry.getKey());
            if (identity == null) {
                if (overflowingFallbacks.contains(entry.getKey())) continue;
                Long existing = fallbackCounts.get(entry.getKey());
                if (existing == null) {
                    fallbackCounts.put(entry.getKey(), count);
                } else {
                    try {
                        fallbackCounts.put(entry.getKey(), Math.addExact(existing, count));
                    } catch (ArithmeticException overflow) {
                        overflowingFallbacks.add(entry.getKey());
                    }
                }
            } else if (overflowingIdentities.contains(identity)) {
                continue;
            } else {
                Long existing = positivePlannedCounts.get(identity);
                if (existing == null) {
                    positivePlannedCounts.put(identity, count);
                } else {
                    try {
                        positivePlannedCounts.put(identity, Math.addExact(existing, count));
                    } catch (ArithmeticException overflow) {
                        overflowingIdentities.add(identity);
                    }
                }
            }
        }
        boolean ordered = false;
        boolean dynamic = false;
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            boolean plannedMember = component.executionPatterns().stream().anyMatch(pattern ->
                plannedCount(pattern, positivePlannedCounts, overflowingIdentities,
                    fallbackCounts, overflowingFallbacks) > 0);
            switch (component.cycleDisposition()) {
                case BLOCKED -> { return ExecutionMode.BLOCKED; }
                case NOT_REQUIRED, STOCK_SATISFIED -> {
                    if (plannedMember) return ExecutionMode.BLOCKED;
                }
                case ORDERED_EXECUTION -> {
                    if (!plannedMember || component.cycleStatus() != CyclePlanningStatus.SOLVED
                            || component.cycleResult() == null || !component.cycleResult().status().solved()) {
                        return ExecutionMode.BLOCKED;
                    }
                    ordered = true;
                }
                case DYNAMIC_EXECUTION -> {
                    if (!plannedMember || component.cycleStatus() != CyclePlanningStatus.SOLVED
                            || component.cycleResult() == null || !component.cycleResult().status().solved()
                            || !component.cycleResult().hasExactExecutionCounts()) {
                        return ExecutionMode.BLOCKED;
                    }
                    dynamic = true;
                }
            }
        }
        return dynamic ? ExecutionMode.DYNAMIC_CYCLE : ordered ? ExecutionMode.ORDERED_CYCLE : ExecutionMode.NATIVE;
    }

    private static long plannedCount(IPatternDetails pattern,
            Map<PlanIdentity.PatternIdentity, Long> positivePlannedCounts,
            Set<PlanIdentity.PatternIdentity> overflowingIdentities,
            Map<IPatternDetails, Long> fallbackCounts,
            Set<IPatternDetails> overflowingFallbacks) {
        PlanIdentity.PatternIdentity identity = safePatternIdentity(pattern);
        if (identity != null) {
            if (overflowingIdentities.contains(identity)) {
                throw new ArithmeticException("long overflow");
            }
            return positivePlannedCounts.getOrDefault(identity, 0L);
        }
        if (overflowingFallbacks.contains(pattern)) throw new ArithmeticException("long overflow");
        return fallbackCounts.getOrDefault(pattern, 0L);
    }

    private static PlanIdentity.PatternIdentity safePatternIdentity(IPatternDetails pattern) {
        if (pattern == null) return null;
        try {
            PlanIdentity.PatternIdentity identity = PlanIdentity.patternIdentityFor(pattern);
            return identity != null && identity.kind() != null && identity.value() != null ? identity : null;
        } catch (RuntimeException rejected) {
            return null;
        }
    }
}
