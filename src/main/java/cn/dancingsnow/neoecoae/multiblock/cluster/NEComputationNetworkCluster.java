package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates the physical computation hosts attached to the same AE2 grid.
 * Jobs remain owned by their selected physical host, which keeps persistence
 * and cancellation semantics identical to a standalone controller.
 */
public final class NEComputationNetworkCluster {
    private static final int INFINITE_CAPACITY_HOSTS = 8;
    private static final Comparator<NEComputationCluster> HOST_ORDER =
            Comparator.comparingLong(cluster -> cluster.getController() == null
                    ? Long.MAX_VALUE
                    : cluster.getController().getBlockPos().asLong());

    private List<NEComputationCluster> members = List.of();
    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;
    private int nextHostIndex;

    public void configure(Collection<NEComputationCluster> source) {
        List<NEComputationCluster> next = source.stream()
                .filter(cluster -> cluster != null && !cluster.isDestroyed() && cluster.getController() != null)
                .sorted(HOST_ORDER)
                .toList();
        members = List.copyOf(next);
        selectionMode =
                members.isEmpty() ? CpuSelectionMode.ANY : members.get(0).getLocalSelectionMode();
        for (NEComputationCluster member : members) {
            member.setLocalSelectionMode(selectionMode);
            member.getController().markComputationStatsDirty();
        }
        nextHostIndex = Math.floorMod(nextHostIndex, Math.max(1, members.size()));
        onHostCapacityChanged();
    }

    public void clear() {
        for (NEComputationCluster member : members) {
            member.getController().markComputationStatsDirty();
            // The controller remains an AE2 CPU after logical membership is
            // removed; publish the local CPU list so the grid drops the old
            // aggregate and discovers the physical host again.
            member.notifyLocalGridChange();
        }
        members = List.of();
        nextHostIndex = 0;
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isInfiniteCapacity() {
        return members.size() == INFINITE_CAPACITY_HOSTS
                && members.stream().allMatch(NEComputationCluster::isHighEnergyNetworkMode);
    }

    public int getCPUAccelerators() {
        long total = 0;
        for (NEComputationCluster member : members) {
            total = saturatingAdd(total, multiplied(member.getLocalCPUAccelerators(), member.getNetworkMultiplier()));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getMaxThreads() {
        long total = 0;
        for (NEComputationCluster member : members) {
            total = saturatingAdd(total, multiplied(member.getLocalMaxThreads(), member.getNetworkMultiplier()));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public long getTotalStorageBytes() {
        if (isInfiniteCapacity()) {
            return Long.MAX_VALUE;
        }
        long total = 0;
        for (NEComputationCluster member : members) {
            total = saturatingAdd(total, multiplied(member.getLocalTotalStorageBytes(), member.getNetworkMultiplier()));
        }
        return total;
    }

    public long getAvailableStorage() {
        if (isInfiniteCapacity()) {
            return Long.MAX_VALUE;
        }
        long total = 0;
        long used = 0;
        for (NEComputationCluster member : members) {
            total = saturatingAdd(total, multiplied(member.getLocalTotalStorageBytes(), member.getNetworkMultiplier()));
            used = saturatingAdd(used, Math.max(0L, member.getLocalActiveJobBytes()));
        }
        return total <= used ? 0L : total - used;
    }

    public boolean isActive() {
        return members.stream().anyMatch(NEComputationCluster::isLocallyActive);
    }

    public boolean hasFreeThread() {
        return members.stream().anyMatch(NEComputationCluster::hasLocalFreeThread);
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        List<ECOCraftingCPU> result = new ArrayList<>();
        for (NEComputationCluster member : members) {
            result.addAll(member.getLocalActiveCPUs());
        }
        return result;
    }

    /** Returns only CPUs whose owning controller is on the requesting AE2 grid. */
    public List<ECOCraftingCPU> getActiveCPUs(IGrid grid) {
        if (grid == null) {
            return List.of();
        }
        List<ECOCraftingCPU> result = new ArrayList<>();
        for (NEComputationCluster member : members) {
            if (!member.isLocallyActive()) {
                continue;
            }
            for (ECOCraftingCPU cpu : member.getLocalActiveCPUs()) {
                try {
                    if (cpu.getGrid() == grid) {
                        result.add(cpu);
                    }
                } catch (RuntimeException ignored) {
                    // A CPU can lose its controller node while the grid is splitting.
                }
            }
        }
        return result;
    }

    /** Returns whether at least one active physical host in this logical network is on the grid. */
    public boolean hasActiveHostOnGrid(IGrid grid) {
        if (grid == null) {
            return false;
        }
        for (NEComputationCluster member : members) {
            if (!member.isLocallyActive()) {
                continue;
            }
            IGridNode node = member.getNode();
            try {
                if (node != null && node.getGrid() == grid) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // The node may be destroyed while AE2 is splitting the grid.
            }
        }
        return false;
    }

    public int getActiveCpuCount() {
        int total = 0;
        for (NEComputationCluster member : members) {
            total = Math.addExact(total, Math.min(Integer.MAX_VALUE - total, member.getLocalActiveCpuCount()));
        }
        return total;
    }

    public CpuSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(CpuSelectionMode mode) {
        selectionMode = mode;
        for (NEComputationCluster member : members) {
            member.setLocalSelectionMode(mode);
        }
        onHostCapacityChanged();
    }

    public boolean canBeAutoSelectedFor(IActionSource source) {
        return switch (selectionMode) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public ICraftingSubmitResult submitJob(
            IGrid grid, ICraftingPlan job, IActionSource source, ICraftingRequester requester) {
        if (members.isEmpty() || !isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (getAvailableStorage() < job.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        int start = Math.floorMod(nextHostIndex, members.size());
        for (int offset = 0; offset < members.size(); offset++) {
            int index = (start + offset) % members.size();
            NEComputationCluster member = members.get(index);
            if (!member.isLocallyActive() || !member.hasLocalFreeThread()) {
                continue;
            }
            IGridNode node = member.getNode();
            if (grid != null && (node == null || node.getGrid() != grid)) {
                continue;
            }
            ICraftingSubmitResult result = member.submitLocalJob(grid, job, source, requester, true);
            if (result.successful()) {
                nextHostIndex = (index + 1) % members.size();
                return result;
            }
        }
        return CraftingSubmitResult.CPU_BUSY;
    }

    public void onHostCapacityChanged() {
        for (NEComputationCluster member : members) {
            ECOComputationSystemBlockEntity controller = member.getController();
            controller.markComputationStatsDirty();
            member.notifyLocalGridChange();
        }
    }

    private static long multiplied(long value, int multiplier) {
        if (value <= 0 || multiplier <= 0) {
            return 0;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
