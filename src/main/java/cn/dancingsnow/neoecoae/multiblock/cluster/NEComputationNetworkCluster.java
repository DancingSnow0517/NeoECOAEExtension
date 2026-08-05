package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Logical C-series exchange cluster. Physical clusters still own their AE2
 * nodes, drives and threading cores; this object aggregates their published
 * capacity and routes a job to one physical host on the requesting grid.
 */
public final class NEComputationNetworkCluster {
    private static final int ULTIMATE_C9_HOST_COUNT = 8;
    private static final int ULTIMATE_C9_MIN_THREADING_CORES = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(NEComputationNetworkCluster.class);
    private static final Comparator<NEComputationCluster> CLUSTER_ORDER = Comparator.comparing(
        cluster -> cluster.getController() == null
            ? Long.MAX_VALUE
            : cluster.getController().getBlockPos().asLong()
    );

    private final ServerLevel level;
    private List<NEComputationCluster> physicalClusters = List.of();
    private List<ECOComputationSystemBlockEntity> controllers = List.of();
    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;
    /** Synthetic CPUs must retain object identity between AE2 service refreshes. */
    private final Map<IGrid, ECOCraftingCPU> fakeCpus = new IdentityHashMap<>();
    private long revision;

    public NEComputationNetworkCluster(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public void configure(Collection<NEComputationCluster> source) {
        Map<IGrid, IGridNode> changedGrids = collectGridNodes(physicalClusters);
        List<NEComputationCluster> clusters = source.stream()
            .filter(cluster -> cluster != null && !cluster.isDestroyed() && cluster.getController() != null)
            .sorted(CLUSTER_ORDER)
            .toList();
        physicalClusters = List.copyOf(clusters);

        List<ECOComputationSystemBlockEntity> nextControllers = new ArrayList<>();
        for (NEComputationCluster cluster : clusters) {
            nextControllers.add(cluster.getController());
        }
        controllers = List.copyOf(nextControllers);
        selectionMode = controllers.isEmpty()
            ? CpuSelectionMode.ANY
            : controllers.getFirst().getLocalSelectionMode();
        for (ECOComputationSystemBlockEntity controller : controllers) {
            controller.setLocalSelectionMode(selectionMode);
            controller.onNetworkStateChanged();
        }
        changedGrids.putAll(collectGridNodes(physicalClusters));
        postCpuChange(changedGrids);
        revision++;
    }

    public void clear() {
        Map<IGrid, IGridNode> changedGrids = collectGridNodes(physicalClusters);
        for (ECOComputationSystemBlockEntity controller : controllers) {
            controller.onNetworkStateChanged();
        }
        physicalClusters = List.of();
        controllers = List.of();
        selectionMode = CpuSelectionMode.ANY;
        fakeCpus.clear();
        postCpuChange(changedGrids);
        revision++;
    }

    public List<NEComputationCluster> getPhysicalClusters() {
        return physicalClusters;
    }

    public List<ECOComputationSystemBlockEntity> getControllers() {
        return controllers;
    }

    public int getMemberCount() {
        return controllers.size();
    }

    public boolean isFastPlanningEnabled() {
        return !controllers.isEmpty() && controllers.stream()
            .allMatch(ECOComputationSystemBlockEntity::isFastPlanningEnabled);
    }

    /** Each host contributes its local capacity multiplied by its switch tier. */
    public int getMaxThreads() {
        return saturatingInt(sumMultiplied(NEComputationCluster::getLocalMaxThreads));
    }

    public int getCPUAccelerators() {
        if (hasUltimateAggregateCapacity()) {
            return NEConfig.ECO_CPU_PUSH_TICK_LIMIT_MAX;
        }
        return Math.min(
            NEConfig.ECO_CPU_PUSH_TICK_LIMIT_MAX,
            saturatingInt(sumMultiplied(NEComputationCluster::getLocalCPUAccelerators))
        );
    }

    public long getTotalStorage() {
        if (hasUltimateAggregateCapacity()) {
            return Long.MAX_VALUE;
        }
        return sumMultiplied(NEComputationCluster::getLocalTotalStorage);
    }

    public long getAvailableStorage() {
        if (hasUltimateAggregateCapacity()) {
            return Math.max(0L, Long.MAX_VALUE - getActiveJobBytes());
        }
        return Math.max(0L, getTotalStorage() - getActiveJobBytes());
    }

    public long getAvailableStorageForGrid(@Nullable IGrid grid) {
        if (hasUltimateAggregateCapacity() && hasActiveHostOnGrid(grid)) {
            return Math.max(0L, Long.MAX_VALUE - getActiveJobBytes());
        }
        long matchingStorage = 0L;
        long matchingActiveJobBytes = 0L;
        for (NEComputationCluster cluster : physicalClusters) {
            if (!cluster.isLocallyActive()) {
                continue;
            }
            IGridNode node = cluster.getLocalNode();
            if (grid != null && (node == null || node.getGrid() != grid)) {
                continue;
            }
            matchingStorage = saturatingAdd(
                matchingStorage,
                saturatingMultiply(cluster.getNetworkMultiplier(), cluster.getLocalTotalStorage())
            );
            matchingActiveJobBytes = saturatingAdd(matchingActiveJobBytes, cluster.getLocalActiveJobBytes());
        }
        return Math.max(0L, matchingStorage - matchingActiveJobBytes);
    }

    public boolean hasUltimateAggregateCapacity() {
        int eligibleHosts = 0;
        for (NEComputationCluster cluster : physicalClusters) {
            ECOComputationSystemBlockEntity controller = cluster.getController();
            if (controller == null
                || controller.getTier().getTier() < ECOTier.L9.getTier()
                || cluster.getThreadingCores().size() < ULTIMATE_C9_MIN_THREADING_CORES
                || cluster.getNetworkMultiplier() < 8
                || !cluster.hasFullComputationDrives()) {
                continue;
            }
            if (++eligibleHosts >= ULTIMATE_C9_HOST_COUNT) {
                return true;
            }
        }
        return false;
    }

    public void onHostCapacityChanged() {
        postCpuChange();
        revision++;
    }

    private boolean hasActiveHostOnGrid(@Nullable IGrid grid) {
        for (NEComputationCluster cluster : physicalClusters) {
            if (!cluster.isLocallyActive()) {
                continue;
            }
            IGridNode node = cluster.getLocalNode();
            if (grid == null || node != null && node.getGrid() == grid) {
                return true;
            }
        }
        return false;
    }

    private long getActiveJobBytes() {
        return sum(NEComputationCluster::getLocalActiveJobBytes);
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        List<ECOCraftingCPU> result = new ArrayList<>();
        for (NEComputationCluster cluster : physicalClusters) {
            result.addAll(cluster.getLocalActiveCPUs());
        }
        return List.copyOf(result);
    }

    public List<ECOCraftingCPU> getActiveCPUs(@Nullable IGrid grid) {
        if (grid == null) {
            return List.of();
        }
        List<ECOCraftingCPU> result = new ArrayList<>();
        for (NEComputationCluster cluster : physicalClusters) {
            if (!cluster.isLocallyActive()) {
                continue;
            }
            for (ECOCraftingCPU cpu : cluster.getLocalActiveCPUs()) {
                if (cpu.getGrid() == grid) {
                    result.add(cpu);
                }
            }
        }
        return List.copyOf(result);
    }

    public boolean isActive() {
        for (NEComputationCluster cluster : physicalClusters) {
            if (cluster.isLocallyActive()) {
                return true;
            }
        }
        return false;
    }

    public boolean canBeAutoSelectedFor(IActionSource actionSource) {
        return switch (selectionMode) {
            case ANY -> true;
            case PLAYER_ONLY -> actionSource.player().isPresent();
            case MACHINE_ONLY -> actionSource.player().isEmpty();
        };
    }

    public CpuSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(CpuSelectionMode mode) {
        CpuSelectionMode next = mode == null ? CpuSelectionMode.ANY : mode;
        selectionMode = next;
        for (NEComputationCluster cluster : physicalClusters) {
            cluster.setLocalSelectionMode(next);
        }
        postCpuChange();
    }

    public void cycleSelectionMode() {
        setSelectionMode(switch (selectionMode) {
            case ANY -> CpuSelectionMode.PLAYER_ONLY;
            case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
            case MACHINE_ONLY -> CpuSelectionMode.ANY;
        });
    }

    public ICraftingSubmitResult submitJob(
        IGrid grid,
        ICraftingPlan job,
        IActionSource source,
        ICraftingRequester requestingMachine
    ) {
        if (job.simulation()) {
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        }
        long aggregateAvailableBytes = getAvailableStorageForGrid(grid);
        boolean aggregateCapacitySufficient = aggregateAvailableBytes >= job.bytes();
        NEComputationCluster selected = null;
        for (NEComputationCluster cluster : physicalClusters) {
            if (!cluster.isLocallyActive() || !cluster.canBeAutoSelectedFor(source)) {
                continue;
            }
            IGridNode node = cluster.getLocalNode();
            if (node == null
                || node.getGrid() != grid
                || cluster.getLocalActiveCPUs().size() >= cluster.getLocalMaxThreads()) {
                continue;
            }
            if (selected == null
                || cluster.getLocalActiveCPUs().size() < selected.getLocalActiveCPUs().size()
                || cluster.getLocalActiveCPUs().size() == selected.getLocalActiveCPUs().size()
                    && cluster.getLocalTotalStorage() > selected.getLocalTotalStorage()) {
                selected = cluster;
            }
        }
        if (!aggregateCapacitySufficient || selected == null) {
            LOGGER.warn(
                "ECO crafting submission rejected: requiredBytes={} publishedAvailableBytes={} grid={} source={} "
                    + "selectionMode={} candidates={}",
                job.bytes(),
                aggregateAvailableBytes,
                Integer.toHexString(System.identityHashCode(grid)),
                source,
                selectionMode,
                describeSubmitCandidates(grid, source, aggregateCapacitySufficient)
            );
            return appeng.crafting.execution.CraftingSubmitResult.NO_CPU_FOUND;
        }
        ICraftingSubmitResult result = selected.submitJobLocal(grid, job, source, requestingMachine, true);
        if (!result.successful()) {
            LOGGER.warn(
                "ECO crafting submission failed after selecting host: requiredBytes={} host={} effectiveAvailableBytes={} "
                    + "threadSlots={}/{} result={}",
                job.bytes(),
                describeHost(selected),
                selected.getEffectiveAvailableStorage(),
                selected.getLocalActiveCPUs().size(),
                selected.getLocalMaxThreads(),
                result
            );
        }
        return result;
    }

    private String describeSubmitCandidates(
        IGrid grid,
        IActionSource source,
        boolean aggregateCapacitySufficient
    ) {
        StringJoiner candidates = new StringJoiner(", ", "[", "]");
        for (NEComputationCluster cluster : physicalClusters) {
            boolean active = cluster.isLocallyActive();
            boolean selectionAllowed = cluster.canBeAutoSelectedFor(source);
            IGridNode node = cluster.getLocalNode();
            boolean gridMatches = node != null && node.getGrid() == grid;
            long localCapacityBytes = saturatingMultiply(cluster.getNetworkMultiplier(), cluster.getLocalTotalStorage());
            int activeThreadSlots = cluster.getLocalActiveCPUs().size();
            int maxThreadSlots = cluster.getLocalMaxThreads();
            String rejectReason = !active
                ? "offline"
                : !selectionAllowed
                    ? "selection-mode"
                    : !gridMatches
                        ? "grid-mismatch"
                        : !aggregateCapacitySufficient
                            ? "insufficient-aggregate-capacity"
                            : activeThreadSlots >= maxThreadSlots
                                ? "no-free-thread-slot"
                                : "eligible";
            candidates.add(String.format(
                java.util.Locale.ROOT,
                "{host=%s, active=%s, gridMatches=%s, selectionAllowed=%s, localCapacityBytes=%d, "
                    + "threadSlots=%d/%d, reason=%s}",
                describeHost(cluster),
                active,
                gridMatches,
                selectionAllowed,
                localCapacityBytes,
                activeThreadSlots,
                maxThreadSlots,
                rejectReason
            ));
        }
        return candidates.toString();
    }

    private static String describeHost(NEComputationCluster cluster) {
        ECOComputationSystemBlockEntity controller = cluster.getController();
        return controller == null ? "<missing-controller>" : controller.getBlockPos().toShortString();
    }

    public @Nullable ECOCraftingCPU getFakeCPU() {
        return getFakeCPU(null);
    }

    public @Nullable ECOCraftingCPU getFakeCPU(@Nullable IGrid grid) {
        ECOCraftingCPU cached = fakeCpus.get(grid);
        NEComputationCluster selected = findUsableCluster(grid, cached);
        if (selected == null) {
            fakeCpus.remove(grid);
            return null;
        }
        long availableStorage = grid == null
            ? getAvailableStorage()
            : getAvailableStorageForGrid(grid);
        if (cached == null || cached.getCluster() != selected) {
            cached = new ECOCraftingCPU(
                selected,
                availableStorage,
                selected.getController() == null ? ECOTier.L4 : selected.getController().getTier()
            );
            fakeCpus.put(grid, cached);
        } else {
            cached.updateFakeStorage(availableStorage);
        }
        return cached;
    }

    public void pruneInactiveCPUs() {
        for (NEComputationCluster cluster : physicalClusters) {
            cluster.pruneInactiveCPUsLocal();
        }
    }

    public void deactivate(ICraftingPlan plan) {
        for (NEComputationCluster cluster : physicalClusters) {
            if (cluster.hasLocalPlan(plan)) {
                cluster.deactivateLocal(plan);
                return;
            }
        }
    }

    public void cancelJob(ICraftingPlan plan) {
        for (NEComputationCluster cluster : physicalClusters) {
            if (cluster.hasLocalPlan(plan)) {
                cluster.cancelJobLocal(plan);
                return;
            }
        }
    }

    public long getRevision() {
        return revision;
    }

    private long sumMultiplied(java.util.function.ToLongFunction<NEComputationCluster> getter) {
        long total = 0L;
        for (NEComputationCluster cluster : physicalClusters) {
            total = saturatingAdd(
                total,
                saturatingMultiply(cluster.getNetworkMultiplier(), getter.applyAsLong(cluster))
            );
        }
        return total;
    }

    private long sum(java.util.function.ToLongFunction<NEComputationCluster> getter) {
        long total = 0L;
        for (NEComputationCluster cluster : physicalClusters) {
            total = saturatingAdd(total, getter.applyAsLong(cluster));
        }
        return total;
    }

    private static int saturatingInt(long value) {
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private @Nullable NEComputationCluster findUsableCluster(
        @Nullable IGrid grid,
        @Nullable ECOCraftingCPU cached
    ) {
        if (cached != null && isUsableCluster(cached.getCluster(), grid)) {
            return cached.getCluster();
        }
        for (NEComputationCluster cluster : physicalClusters) {
            if (isUsableCluster(cluster, grid)) {
                return cluster;
            }
        }
        return null;
    }

    private static boolean isUsableCluster(NEComputationCluster cluster, @Nullable IGrid grid) {
        if (!cluster.isLocallyActive()) {
            return false;
        }
        IGridNode node = cluster.getLocalNode();
        return grid == null || node != null && node.getGrid() == grid;
    }

    private void postCpuChange() {
        postCpuChange(collectGridNodes(physicalClusters));
    }

    private void postCpuChange(Map<IGrid, IGridNode> gridNodes) {
        for (NEComputationCluster cluster : physicalClusters) {
            IGridNode node = cluster.getLocalNode();
            if (node != null && node.getGrid() != null) {
                gridNodes.putIfAbsent(node.getGrid(), node);
            }
        }
        for (Map.Entry<IGrid, IGridNode> entry : gridNodes.entrySet()) {
            entry.getKey().postEvent(new appeng.api.networking.events.GridCraftingCpuChange(entry.getValue()));
        }
    }

    private static Map<IGrid, IGridNode> collectGridNodes(Collection<NEComputationCluster> clusters) {
        Map<IGrid, IGridNode> gridNodes = new HashMap<>();
        for (NEComputationCluster cluster : clusters) {
            IGridNode node = cluster.getLocalNode();
            if (node != null && node.getGrid() != null) {
                gridNodes.putIfAbsent(node.getGrid(), node);
            }
        }
        return gridNodes;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return Math.max(0L, left);
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

}
