package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Immutable planning answer. Its execution plan is interpreted exactly once during construction. */
public final class ECOPlanningResult {
    private final PlanningStatus status;
    private final @Nullable CraftingPlan plan;
    private final ECOPlanTrace trace;
    private final List<CycleDiagnostic> cycles;
    private final List<ComponentPlanningResult> components;
    private final List<Integer> executionComponentOrder;
    private final long calculationNanos;
    private final UUID planningId;
    private final ECOExecutionRequirement executionRequirement;
    private final @Nullable ECOExecutionPlan executionPlan;
    private final @Nullable String executionPlanError;

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos, UUID planningId) {
        this.status = status;
        this.plan = plan;
        this.trace = trace;
        this.cycles = List.copyOf(cycles);
        this.components = List.copyOf(components);
        this.executionComponentOrder = List.copyOf(executionComponentOrder);
        this.calculationNanos = Math.max(0L, calculationNanos);
        this.planningId = planningId == null ? UUID.randomUUID() : planningId;
        if (status == PlanningStatus.SUCCESS && plan == null) {
            throw new IllegalArgumentException("A successful planning result requires a plan");
        }
        ECOExecutionRequirement requirement = plan == null ? ECOExecutionRequirement.NONE
            : ECOExecutionRequirement.classify(this.components, plan.patternTimes());
        ECOExecutionPlan built = null;
        String error = null;
        if (status == PlanningStatus.SUCCESS && plan != null && requirement != ECOExecutionRequirement.BLOCKED
                && !this.components.isEmpty()) {
            try {
                PlanIdentity.Signature signature = PlanIdentity.of(plan);
                if (signature == null) throw new IllegalStateException("Plan identity unavailable");
                ExecutionMode mode = requirement == ECOExecutionRequirement.ORDERED
                    ? ExecutionMode.ORDERED_CYCLE : ExecutionMode.PHASED_DAG;
                built = ECOExecutionPlanBuilder.build(signature, mode, this.components,
                    this.executionComponentOrder, plan.patternTimes());
                if (built.phases().isEmpty()) built = null;
            } catch (RuntimeException failure) {
                error = "EXECUTION_PLAN_BUILD_FAILED:" + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ":" + failure.getMessage());
            }
        }
        if (requirement == ECOExecutionRequirement.BLOCKED) error = "CYCLE_NOT_SOLVED";
        this.executionRequirement = error == null ? requirement : ECOExecutionRequirement.BLOCKED;
        this.executionPlan = built;
        this.executionPlanError = error;
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos) {
        this(status, plan, trace, cycles, components, executionComponentOrder, calculationNanos, UUID.randomUUID());
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
    public UUID planningId() { return planningId; }
    public ECOExecutionRequirement executionRequirement() { return executionRequirement; }
    public @Nullable String executionPlanError() { return executionPlanError; }

    public boolean shouldUseNativeFallback() {
        return status == PlanningStatus.PARTIAL_UNSUPPORTED || status == PlanningStatus.UNSUPPORTED
            || status == PlanningStatus.INTERNAL_ERROR;
    }

    public ECOExecutionPlan executionPlan() {
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
