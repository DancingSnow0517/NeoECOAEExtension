package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import java.util.Collection;
import java.util.stream.Stream;

/** External CPUs accept only plans that can execute with their native long-based job ledger. */
public final class ECOExternalCpuSupport {
    private ECOExternalCpuSupport() {}

    public static boolean accepts(ICraftingCPU cpu, ICraftingPlan plan) {
        return (cpu instanceof CraftingCPUCluster
                        || cpu != null
                                && cpu.getClass()
                                        .getName()
                                        .equals("net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU"))
                && supportsPlan(plan);
    }

    public static boolean supportsPlan(ICraftingPlan plan) {
        if (plan == null || plan instanceof ECOExactCraftingPlan) return false;
        if (!ECOPlanningResultRegistry.isECOOwnedPlan(plan)) return true;

        ECOPlanningResult attached = plan instanceof ECOCraftingPlanDiagnostics diagnostics
                ? diagnostics.neoecoae$getPlanningResult()
                : null;
        if (attached != null && (attached.plan() == null || !PlanIdentity.matches(plan, attached.plan()))) {
            attached = null;
        }
        ECOPlanningResult result = attached != null ? attached : ECOPlanningResultRegistry.find(plan);
        if (result == null
                || Stream.of(
                                result.exactPatternTimes().values(),
                                result.exactUsedItems().values(),
                                result.exactEmittedItems().values(),
                                result.exactMissingItems().values())
                        .flatMap(Collection::stream)
                        .anyMatch(amount -> !amount.fitsLong())) {
            return false;
        }
        var contract = ECOPlanningResultRegistry.resolveContract(plan, attached);
        return contract != null && contract.mode() == ExecutionMode.NATIVE;
    }

    /** Ownership survives removal of a diagnostic attachment, so missing ECO metadata fails closed. */
    public interface OwnedPlan {
        boolean neoecoae$isECOOwnedPlan();
    }
}
