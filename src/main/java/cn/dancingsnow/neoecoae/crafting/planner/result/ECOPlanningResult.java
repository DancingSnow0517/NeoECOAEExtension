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
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Immutable planning answer. Its execution plan is interpreted at most once, on first access when needed. */
public final class ECOPlanningResult {
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
    private Set<ResourceLocation> fuzzyPlanningItemIds = Set.of();
    private final UUID planningId;
    private volatile ECOExecutionRequirement executionRequirement;
    private volatile @Nullable ECOExecutionPlan executionPlan;
    private volatile @Nullable String executionPlanError;
    private final Object executionPlanLock = new Object();
    private volatile boolean executionPlanResolved;
    private final @Nullable ExecutionProvenance provenance;

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos, UUID planningId,
            @Nullable ExecutionProvenance provenance) {
        this.status = status;
        this.plan = plan;
        var firings = new java.util.LinkedHashMap<IPatternDetails, PlannerAmount>();
        if (plan != null) plan.patternTimes().forEach((pattern, count) -> firings.put(pattern, PlannerAmount.of(count)));
        this.exactPatternTimes = java.util.Map.copyOf(firings);
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
                built = ECOExecutionPlanBuilder.build(signature, mode, this.components,
                    this.executionComponentOrder, plan.patternTimes(), provenance);
                if (built.phases().isEmpty()) {
                    // An empty phase list is never a valid cycle metadata answer. Surface it explicitly:
                    // for a cycle-expected plan this must end as an explicit FAILED state, not as a
                    // silently stripped schedule that later looks like phaseCount=0 while cycleExpected=true.
                    if (requirement != ECOExecutionRequirement.NONE) error = "EXECUTION_PLAN_EMPTY";
                    built = null;
                }
            } catch (RuntimeException failure) {
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
        theoreticalBytes = bytes == null ? BigInteger.ZERO : bytes.toBigInteger();
    }
    public Set<ResourceLocation> fuzzyPlanningItemIds() { return fuzzyPlanningItemIds; }
    public void setFuzzyPlanningItemIds(Set<ResourceLocation> itemIds) {
        fuzzyPlanningItemIds = itemIds == null ? Set.of() : Set.copyOf(itemIds);
    }
    public UUID planningId() { return planningId; }
    public ECOExecutionRequirement executionRequirement() { return executionRequirement; }
    public @Nullable String executionPlanError() {
        resolveExecutionPlan();
        return executionPlanError;
    }
    public @Nullable ExecutionProvenance provenance() { return provenance; }

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
            .flatMap(p -> p.cycleWitness().stream()).toList();
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
        if (executionPlan == null) return ECOExecutionContract.nativeContract(planningId, signature);
        return new ECOExecutionContract(planningId, signature, executionPlan.mode(), executionPlan, null);
    }
}
