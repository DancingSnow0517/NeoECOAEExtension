package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.ExecutionProvenance;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.math.BigInteger;
import net.minecraft.resources.Identifier;
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
    private long calculationNanos;
    private boolean liteCraftingMode;
    private PlannerAmount theoreticalBytes = PlannerAmount.ZERO;
    private java.util.Map<IPatternDetails, PlannerAmount> exactPatternTimes;
    private java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactUsedItems = java.util.Map.of();
    private java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactEmittedItems = java.util.Map.of();
    private java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactMissingItems = java.util.Map.of();
    private Set<Identifier> fuzzyPlanningItemIds = Set.of();
    private final UUID planningId;
    private volatile ECOExecutionRequirement executionRequirement;
    private volatile @Nullable ECOExecutionPlan executionPlan;
    private volatile @Nullable String executionPlanError;
    private final Object executionPlanLock = new Object();
    private volatile boolean executionPlanResolved;
    private final @Nullable ExecutionProvenance provenance;
    private volatile @Nullable cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningImprovements improvements;

    public @Nullable cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningImprovements improvements() {
        return improvements;
    }

    public void setImprovements(cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningImprovements improvements) {
        this.improvements = improvements;
    }
    private volatile @Nullable cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan exactPlan;
    private volatile @Nullable String exactPlanError;
    private volatile boolean exactPlanResolved;

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos, UUID planningId,
            @Nullable ExecutionProvenance provenance) {
        this.status = status;
        this.plan = plan;
        var firings = new java.util.LinkedHashMap<IPatternDetails, PlannerAmount>();
        if (plan != null) plan.patternTimes().forEach((pattern, count) -> firings.put(pattern, PlannerAmount.of(count)));
        this.exactPatternTimes = firings;
        if (plan != null) {
            var used = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, PlannerAmount>();
            var emitted = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, PlannerAmount>();
            var missing = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, PlannerAmount>();
            for (var entry : plan.usedItems()) used.put(entry.getKey(), PlannerAmount.of(entry.getLongValue()));
            for (var entry : plan.emittedItems()) emitted.put(entry.getKey(), PlannerAmount.of(entry.getLongValue()));
            for (var entry : plan.missingItems()) missing.put(entry.getKey(), PlannerAmount.of(entry.getLongValue()));
            setExactMaterials(used, emitted, missing);
        }
        this.trace = trace;
        this.cycles = List.copyOf(cycles);
        this.components = List.copyOf(components);
        this.executionComponentOrder = List.copyOf(executionComponentOrder);
        this.calculationNanos = Math.max(0L, calculationNanos);
        this.planningId = planningId == null ? UUID.randomUUID() : planningId;
        this.provenance = provenance;
        if (status == PlanningStatus.SUCCESS && plan == null) {
            throw new IllegalArgumentException("A successful planning result requires a plan");
        }
        ECOExecutionRequirement requirement = plan == null ? ECOExecutionRequirement.NONE
            : ECOExecutionRequirement.classify(this.components, plan.patternTimes());
        this.executionRequirement = requirement;
        this.executionPlan = null;
        this.executionPlanError = requirement == ECOExecutionRequirement.BLOCKED
            ? "CYCLE_NOT_SOLVED" : null;
        // Pure DAG results are intentionally resolved lazily. They are common and do not need an execution plan
        // while the planner is still constructing/publishing its numeric result. Cycle metadata remains fail-closed.
        this.executionPlanResolved = status != PlanningStatus.SUCCESS
            || requirement == ECOExecutionRequirement.BLOCKED || this.components.isEmpty();
    }

    private void resolveExecutionPlan() {
        if (executionPlanResolved) return;
        synchronized (executionPlanLock) {
            if (executionPlanResolved) return;
            ECOExecutionRequirement requirement = executionRequirement;
            ECOExecutionPlan built = null;
            String error = null;
            try {
                PlanIdentity.Signature signature = PlanIdentity.of(plan);
                if (signature == null) throw new IllegalStateException("Plan identity unavailable");
                ExecutionMode mode = requirement == ECOExecutionRequirement.DYNAMIC
                    ? ExecutionMode.DYNAMIC_CYCLE
                    : requirement == ECOExecutionRequirement.ORDERED
                        ? ExecutionMode.ORDERED_CYCLE : ExecutionMode.PHASED_DAG;
                built = ECOExecutionPlanBuilder.buildExact(signature, mode, this.components,
                    this.executionComponentOrder, exactPatternTimes, exactUsedItems, provenance);
                if (built.phases().isEmpty()) {
                    // An empty phase list is never a valid cycle metadata answer. Surface it explicitly:
                    // for a cycle-expected plan this must end as an explicit FAILED state, not as a
                    // silently stripped schedule that later looks like phaseCount=0 while cycleExpected=true.
                    if (requirement != ECOExecutionRequirement.NONE) error = "EXECUTION_PLAN_EMPTY";
                    built = null;
                }
            } catch (RuntimeException failure) {
                // The UI diagnostic is length-limited; keep the complete demand/allocation in the log.
                LOGGER.error("[ECO-EXECUTION-PLAN] Failed to build execution plan planningId={}", planningId, failure);
                error = "EXECUTION_PLAN_BUILD_FAILED:" + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ":" + failure.getMessage());
            }
            this.executionPlan = built;
            this.executionPlanError = error;
            if (error != null) this.executionRequirement = ECOExecutionRequirement.BLOCKED;
            this.executionPlanResolved = true;
        }
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos) {
        this(status, plan, trace, cycles, components, executionComponentOrder, calculationNanos, UUID.randomUUID(), null);
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
    public boolean liteCraftingMode() { return liteCraftingMode; }
    public void setLiteCraftingMode(boolean enabled) { liteCraftingMode = enabled; }
    /** Record total elapsed time before final publication without rebuilding the validated execution plan. */
    public void finishCalculation(long elapsedNanos) { calculationNanos = Math.max(calculationNanos, elapsedNanos); }
    public BigInteger theoreticalBytes() { return theoreticalBytes.toBigInteger(); }
    /** Final committed firing vector for reports, including counts that cannot fit an AE2 plan. */
    public java.util.Map<IPatternDetails, PlannerAmount> exactPatternTimes() { return exactPatternTimes; }
    public void setExactPatternTimes(java.util.Map<IPatternDetails, PlannerAmount> counts) {
        exactPatternTimes = java.util.Map.copyOf(counts);
    }
    public void setExactMaterials(java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> used,
            java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> emitted,
            java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> missing) {
        exactUsedItems = java.util.Map.copyOf(used);
        exactEmittedItems = java.util.Map.copyOf(emitted);
        exactMissingItems = java.util.Map.copyOf(missing);
    }
    public java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactUsedItems() { return exactUsedItems; }
    public java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactEmittedItems() { return exactEmittedItems; }
    public java.util.Map<appeng.api.stacks.AEKey, PlannerAmount> exactMissingItems() { return exactMissingItems; }
    public void setTheoreticalBytes(PlannerAmount bytes) {
        theoreticalBytes = bytes == null ? PlannerAmount.ZERO : bytes;
    }
    public Set<Identifier> fuzzyPlanningItemIds() { return fuzzyPlanningItemIds; }
    public void setFuzzyPlanningItemIds(Set<Identifier> itemIds) {
        fuzzyPlanningItemIds = itemIds == null ? Set.of() : itemIds;
    }
    public UUID planningId() { return planningId; }
    public ECOExecutionRequirement executionRequirement() { return executionRequirement; }
    public @Nullable String executionPlanError() {
        resolveExecutionPlan();
        return executionPlanError;
    }
    public @Nullable ExecutionProvenance provenance() { return provenance; }

    /** Independent of the AE2 projection and of whether alternative routes were exhausted. */
    public boolean provenInfeasible() {
        return trace.diagnostics().stream().anyMatch(diagnostic -> switch (diagnostic.code()) {
            case ROUTE_QUANTITY_BOUND, ROUTE_PROVEN_UNREACHABLE, MATERIAL_BALANCE_INFEASIBLE,
                PLAN_MATERIAL_CLOSURE_INVALID -> true;
            default -> false;
        });
    }

    public boolean routeSearchIncomplete() {
        return trace.diagnostics().stream().anyMatch(diagnostic -> switch (diagnostic.code()) {
            case ROUTE_SEARCH_BUDGET_EXHAUSTED, ROUTE_OPTIMIZATION_BUDGET -> true;
            default -> false;
        });
    }

    /** Validate the existing exact adapter once, before offering its result for immediate submission. */
    public @Nullable cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan exactPlan() {
        if (status != PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) return null;
        if (!exactPlanResolved) synchronized (executionPlanLock) {
            if (!exactPlanResolved) {
                try {
                    exactPlan = new cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan(this, false);
                } catch (RuntimeException invalid) {
                    exactPlanError = invalid.getClass().getSimpleName() + ":" + invalid.getMessage();
                }
                exactPlanResolved = true;
            }
        }
        return exactPlan;
    }

    public @Nullable String exactPlanError() {
        exactPlan();
        return exactPlanError;
    }

    public boolean executable() {
        if (plan == null || provenInfeasible()) return false;
        return status == PlanningStatus.SUCCESS && !plan.simulation() && executionPlanError() == null
            || status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE && exactPlan() != null;
    }

    public boolean shouldUseNativeFallback() {
        return status == PlanningStatus.PARTIAL_UNSUPPORTED || status == PlanningStatus.UNSUPPORTED
            || status == PlanningStatus.INTERNAL_ERROR;
    }

    public ECOExecutionPlan executionPlan() {
        resolveExecutionPlan();
        if (executionPlan == null) throw new IllegalStateException(executionPlanError == null
            ? "This result has no phased execution plan" : executionPlanError);
        return executionPlan;
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
        resolveExecutionPlan();
        PlanIdentity.Signature signature = PlanIdentity.of(plan);
        if (signature == null) throw new IllegalStateException("Plan identity unavailable");
        if (executionRequirement == ECOExecutionRequirement.BLOCKED) {
            return new ECOExecutionContract(planningId, signature, ExecutionMode.BLOCKED, null,
                executionPlanError == null ? "CYCLE_METADATA_MISSING" : executionPlanError);
        }
        return new ECOExecutionContract(planningId, signature,
            executionPlan == null ? ExecutionMode.NATIVE : executionPlan.mode(), executionPlan, null,
            fuzzyPlanningItemIds);
    }
}
