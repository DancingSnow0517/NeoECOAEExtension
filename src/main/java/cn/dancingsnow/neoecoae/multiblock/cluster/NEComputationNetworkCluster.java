package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.util.NEMath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Logical pooling of 2-8 network-switch-equipped computation hosts sharing a frequency and ME grid.
 * This object owns no AE2 grid-node identity of its own - every member still owns its own real
 * hardware, jobs, and grid presence. It only supplies the pooled admission numbers that make the
 * group read, to AE2 and to players, as a single aggregated host.
 */
public class NEComputationNetworkCluster {
    private final List<NEComputationCluster> members = new ArrayList<>();

    public void configure(List<NEComputationCluster> newMembers) {
        this.members.clear();
        this.members.addAll(newMembers);
    }

    public List<NEComputationCluster> getMembers() {
        return List.copyOf(members);
    }

    public boolean isLeader(NEComputationCluster member) {
        return !members.isEmpty() && members.getFirst() == member;
    }

    private long sumMultiplied(ToLongFunction<NEComputationCluster> getter) {
        long total = 0;
        for (NEComputationCluster member : members) {
            long multiplier = members.size() > 1 ? member.getNetworkMultiplier() : 1;
            total = NEMath.saturatingAdd(total, NEMath.saturatingMultiply(getter.applyAsLong(member), multiplier));
        }
        return total;
    }

    /**
     * Exactly 8 members, all at max tier, max structural build length, and high-energy switches -
     * the unconditional endgame override. No cooling-controller precondition.
     */
    public boolean isEndgameEligible() {
        if (members.size() != 8) {
            return false;
        }
        for (NEComputationCluster member : members) {
            ECOComputationSystemBlockEntity controller = member.getController();
            if (controller == null) {
                return false;
            }
            if (controller.getTier().getTier() != ECOTier.L9.getTier()) {
                return false;
            }
            if (controller.getSelectedBuildLength() != controller.getMaxBuildLength()) {
                return false;
            }
            if (!controller.hasHighEnergyNetworkSwitch()) {
                return false;
            }
        }
        return true;
    }

    public int getTotalThreads() {
        if (isEndgameEligible()) {
            return Integer.MAX_VALUE;
        }
        long total = sumMultiplied(NEComputationCluster::getOwnMaxThreads);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getTotalParallelism() {
        if (isEndgameEligible()) {
            return Integer.MAX_VALUE;
        }
        long total = sumMultiplied(NEComputationCluster::getCPUAccelerators);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public long getTotalStorageCapacity() {
        if (isEndgameEligible()) {
            return Long.MAX_VALUE;
        }
        return sumMultiplied(NEComputationCluster::getTotalStorage);
    }

    public long getPooledAvailableStorage() {
        long capacity = getTotalStorageCapacity();
        long usedBytes = getReservedStorage();
        return capacity - Math.min(capacity, usedBytes);
    }

    long getReservedStorage() {
        long usedBytes = 0;
        for (NEComputationCluster member : members) {
            usedBytes = NEMath.saturatingAdd(usedBytes, member.getOwnUsedStorage());
        }
        return usedBytes;
    }

    boolean reservationsFit() {
        return getReservedStorage() <= getTotalStorageCapacity();
    }

    public ICraftingSubmitResult submitJob(
        IGrid grid,
        ICraftingPlan job,
        IActionSource src,
        ICraftingRequester requestingMachine
    ) {
        job = ECOPlanningResultRegistry.resolveSubmissionPlan(job);
        boolean anyActive = false;
        for (NEComputationCluster member : members) {
            if (member.isActive()) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (job.bytes() > getPooledAvailableStorage()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        ICraftingSubmitResult lastResult = null;
        for (NEComputationCluster member : members) {
            ICraftingSubmitResult result = member.trySpawnLocalJob(grid, job, src, requestingMachine);
            if (result != null) {
                if (result.successful()) {
                    return result;
                }
                lastResult = result;
            }
        }
        return lastResult == null ? CraftingSubmitResult.NO_CPU_FOUND : lastResult;
    }
}
