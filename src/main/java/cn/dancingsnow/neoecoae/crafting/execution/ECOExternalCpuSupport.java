package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;

/** Ordinary ECO plans can use native CPUs; phased and exact-order plans still require ECO runtime. */
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
        return contract != null && contract.mode() == ExecutionMode.NATIVE;
    }
}
