package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionContract;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;

/** Ordinary ECO DAGs can use external CPUs; cycles and exact orders still require ECO runtime. */
public final class ECOExternalCpuSupport {
    private ECOExternalCpuSupport() {}
    public static boolean accepts(ICraftingCPU cpu, ICraftingPlan plan) {
        boolean supported = cpu instanceof CraftingCPUCluster
                || cpu != null && cpu.getClass().getName().equals("net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU");
        return supported && supportsPlan(plan);
    }
    public static boolean supportsPlan(ICraftingPlan plan) {
        if (plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan) return false;
        if (!ECOPlanningResultRegistry.isECOOwnedPlan(plan)) return true;
        var result = ECOPlanningResultRegistry.find(plan);
        if (result == null || java.util.stream.Stream.of(result.exactPatternTimes().values(),
                result.exactUsedItems().values(), result.exactEmittedItems().values(),
                result.exactMissingItems().values()).flatMap(java.util.Collection::stream)
                .anyMatch(amount -> !amount.fitsLong())) return false;
        var contract = ECOPlanningResultRegistry.resolveContract(plan, null);
        if (!supportsContract(result, contract)) return false;
        // Native CPUs cannot execute the explicit item-ID override captured by an ECO processing plan.
        return plan.patternTimes().keySet().stream().noneMatch(pattern ->
            java.util.Arrays.stream(pattern.getInputs()).anyMatch(input -> {
                var possible = input.getPossibleInputs();
                return possible != null && possible.length > 0 && possible[0] != null
                    && cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching
                        .enabled(pattern, possible[0].what(), contract.fuzzyPlanningItemIds());
            }));
    }

    private static boolean supportsContract(ECOPlanningResult result, ECOExecutionContract contract) {
        if (contract == null) return false;
        if (contract.mode() == ExecutionMode.NATIVE) return true;
        if (contract.mode() != ExecutionMode.PHASED_DAG
                || result.executionRequirement() != ECOExecutionRequirement.NONE) return false;

        // ECO also builds phases for ordinary DAGs. Keep that metadata for ECO CPUs without
        // requiring an external CPU to run our scheduler when AE2's normal task vector suffices.
        var execution = contract.executionPlan();
        return execution != null && execution.mode() == ExecutionMode.PHASED_DAG
                && !execution.tasks().isEmpty()
                && execution.phases().stream().allMatch(phase -> phase.type() == ECOExecutionSchedule.Type.DAG)
                && execution.tasks().stream().allMatch(task -> task.kind() == ECOExecutionPlan.TaskKind.DAG
                    && usesNativeInputs(task));
    }

    private static boolean usesNativeInputs(ECOExecutionPlan.TaskSpec task) {
        var inputs = task.pattern().getInputs();
        if (inputs == null) return false;
        for (var allocation : task.inputAllocations()) {
            if (allocation.slot() >= inputs.length || inputs[allocation.slot()] == null
                    || allocation.runs().size() != 1) return false;
            var input = inputs[allocation.slot()];
            var possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0 || possible[0] == null) return false;
            var run = allocation.runs().getFirst();
            // Per-craft input choices or nonstandard amounts need ECO's input resolver.
            if (!run.key().equals(possible[0].what()) || run.crafts() != task.totalCount()) return false;
            try {
                if (run.amount() != Math.multiplyExact(possible[0].amount(), input.getMultiplier())) return false;
            } catch (ArithmeticException overflow) {
                return false;
            }
        }
        return true;
    }
}
