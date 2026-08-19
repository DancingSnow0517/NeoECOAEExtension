package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Logical F-series exchange cluster. Physical multiblock clusters remain the
 * owners of AE2 nodes and coolant tanks; this object only joins their control
 * and scheduling state.
 */
public final class NECraftingNetworkCluster {
    public static final int VIRTUAL_CRAFTING_REQUIRED_HOSTS = 8;
    private static final Comparator<NECraftingCluster> CLUSTER_ORDER = Comparator.comparing(
            cluster -> cluster.getController() == null
                    ? Long.MAX_VALUE
                    : cluster.getController().getBlockPos().asLong());

    private final ServerLevel level;
    private List<NECraftingCluster> physicalClusters = List.of();
    private List<ECOCraftingSystemBlockEntity> controllers = List.of();
    private List<ECOCraftingWorkerBlockEntity> workers = List.of();
    private List<ECOCraftingPatternBusBlockEntity> patternBuses = List.of();
    private List<cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity> parallelCores = List
            .of();
    private int nextCoolantControllerIndex;
    // Verified recipe metadata belongs to the logical exchange, not one physical F-series host.
    private final ECOCraftingFastPathCache fastPathCache = new ECOCraftingFastPathCache();
    private boolean overclocked;
    private boolean activeCooling;
    private long revision;

    public NECraftingNetworkCluster(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public void configure(Collection<NECraftingCluster> source) {
        List<NECraftingCluster> clusters = source.stream()
                .filter(cluster -> cluster != null && !cluster.isDestroyed() && cluster.getController() != null)
                .sorted(CLUSTER_ORDER)
                .toList();
        boolean topologyChanged = !sameIdentityList(physicalClusters, clusters);
        this.physicalClusters = List.copyOf(clusters);

        List<ECOCraftingSystemBlockEntity> nextControllers = new ArrayList<>();
        Set<ECOCraftingWorkerBlockEntity> nextWorkers = new LinkedHashSet<>();
        Set<ECOCraftingPatternBusBlockEntity> nextPatternBuses = new LinkedHashSet<>();
        Set<cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity> nextParallelCores = new LinkedHashSet<>();
        for (NECraftingCluster cluster : clusters) {
            nextControllers.add(cluster.getController());
            nextWorkers.addAll(cluster.getWorkers());
            nextPatternBuses.addAll(cluster.getPatternBuses());
            nextParallelCores.addAll(cluster.getParallelCores());
        }
        this.controllers = List.copyOf(nextControllers);
        this.workers = nextWorkers.stream()
                .sorted(Comparator.comparing(worker -> worker.getBlockPos().asLong()))
                .toList();
        this.patternBuses = nextPatternBuses.stream()
                .sorted(Comparator.comparing(bus -> bus.getBlockPos().asLong()))
                .toList();
        this.parallelCores = nextParallelCores.stream()
                .sorted(Comparator.comparing(core -> core.getBlockPos().asLong()))
                .toList();
        if (topologyChanged) {
            // The manager reuses a partition object across rebuilds. Its verified recipe results
            // must not survive a split/merge into a different physical member set.
            fastPathCache.clear();
        }
        if (controllers.isEmpty()) {
            overclocked = false;
            activeCooling = false;
        } else {
            overclocked = controllers.getFirst().isLocalOverclocked();
            activeCooling = controllers.getFirst().isLocalActiveCooling();
            for (ECOCraftingSystemBlockEntity controller : controllers) {
                controller.setLocalOverclocked(overclocked);
                controller.setLocalActiveCooling(activeCooling);
            }
        }
        revision++;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.onNetworkStateChanged();
        }
    }

    public void clear() {
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.onNetworkStateChanged();
        }
        physicalClusters = List.of();
        controllers = List.of();
        workers = List.of();
        patternBuses = List.of();
        parallelCores = List.of();
        fastPathCache.clear();
        revision++;
    }

    private static boolean sameIdentityList(List<?> first, List<?> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index) != second.get(index)) {
                return false;
            }
        }
        return true;
    }

    public List<NECraftingCluster> getPhysicalClusters() {
        return physicalClusters;
    }

    public List<ECOCraftingSystemBlockEntity> getControllers() {
        return controllers;
    }

    public List<ECOCraftingWorkerBlockEntity> getWorkers() {
        return workers;
    }

    public ECOCraftingFastPathCache getFastPathCache() {
        return fastPathCache;
    }

    public List<ECOCraftingPatternBusBlockEntity> getPatternBuses() {
        return patternBuses;
    }

    public List<cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity> getParallelCores() {
        return parallelCores;
    }

    public long getRevision() {
        return revision;
    }

    public int getMemberCount() {
        return controllers.size();
    }

    /**
     * Virtual crafting is the endgame reward for a complete high-energy F9
     * exchange network. Every host must be built to the configured maximum
     * length, which gives it the full number of FX task slots.
     */
    public boolean isVirtualCraftingEligible() {
        if (!activeCooling
                || physicalClusters.size() != VIRTUAL_CRAFTING_REQUIRED_HOSTS
                || controllers.size() != VIRTUAL_CRAFTING_REQUIRED_HOSTS) {
            return false;
        }

        int requiredWorkersPerHost = Math.max(1, NEConfig.craftingSystemMaxLength - 4);
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller == null
                    || controller.getTier().getTier() != ECOTier.L9.getTier()
                    || !physicalCluster.isHighEnergyNetworkMode()
                    || physicalCluster.getWorkers().size() != requiredWorkersPerHost
                    || controller.getLocalThreadCount()
                            != requiredWorkersPerHost * VIRTUAL_CRAFTING_REQUIRED_HOSTS) {
                return false;
            }
        }
        return true;
    }

    public void onCoolingAvailabilityChanged() {
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.refreshExchangeThreadCount();
        }
    }

    /**
     * Independent FX task threads. Exchange membership sets threads per worker;
     * x2/x8 sets batch per thread.
     */
    public int getEffectiveValue() {
        long total = 0L;
        for (NECraftingCluster cluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (controller != null) {
                total = saturatingAdd(total, controller.getLocalThreadCount());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getThreadCount() {
        return getEffectiveValue();
    }

    public int getRunningThreadCount() {
        long total = 0L;
        for (NECraftingCluster cluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (controller != null) {
                total = saturatingAdd(total, controller.getLocalRunningThreadCount());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getAvailableThreads() {
        return Math.max(0, getThreadCount() - getRunningThreadCount());
    }

    public int getOverflowThreads() {
        return Math.max(0, getThreadCount() - getAvailableThreads());
    }

    public int getEffectiveOverclockTimes() {
        if (controllers.isEmpty()) {
            return 0;
        }
        int effective = Integer.MAX_VALUE;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            effective = Math.min(effective, controller.getEffectiveOverclockTimesForLocalTasks());
        }
        return effective == Integer.MAX_VALUE ? 0 : effective;
    }

    public int getCoolingMaxOverclock() {
        if (!activeCooling) {
            return -1;
        }
        int maximum = -1;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            maximum = Math.max(maximum, controller.getLocalCoolingMaxOverclock());
        }
        return maximum;
    }

    public int getCoolantAmount() {
        long total = 0L;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            total = saturatingAdd(total, controller.getCoolant());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getCoolantCapacity() {
        return (int) Math.min(
                Integer.MAX_VALUE,
                saturatingMultiply(ECOCraftingSystemBlockEntity.MAX_COOLANT, controllers.size()));
    }

    public FluidStack getDisplayedCoolantFluid() {
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            FluidStack fluid = controller.getCurrentCoolantFluid();
            if (!fluid.isEmpty()) {
                return fluid.copy();
            }
        }
        return FluidStack.EMPTY;
    }

    public void clearCoolant() {
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.clearLocalCoolant();
        }
        onCoolingAvailabilityChanged();
    }

    public long getMaxEnergyUsage() {
        long total = 0L;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            total = saturatingAdd(total, controller.getLocalMaxEnergyUsage());
        }
        return total;
    }

    public boolean hasCoolingForNetworkMultiplier(int multiplier) {
        if (!activeCooling || multiplier <= 1) {
            return multiplier <= 1;
        }
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            if (controller.hasLocalCoolingForNetworkMultiplier(multiplier)) {
                return true;
            }
        }
        return false;
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!activeCooling || requestedCrafts <= 0 || coolantPerCraft <= 0) {
            return Math.max(0, requestedCrafts);
        }
        long total = 0L;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            total = saturatingAdd(total, controller.getLocalCraftingCoolantCraftLimit(
                    coolantPerCraft, requiredOverclock, requestedCrafts));
            if (total >= requestedCrafts) {
                return requestedCrafts;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        if (!activeCooling || amount <= 0) {
            return true;
        }
        if (getCraftingCoolantCraftLimit(1, requiredOverclock, amount) < amount) {
            return false;
        }
        if (controllers.isEmpty()) {
            return false;
        }
        int remaining = amount;
        int start = Math.floorMod(nextCoolantControllerIndex, controllers.size());
        for (int offset = 0; offset < controllers.size() && remaining > 0; offset++) {
            ECOCraftingSystemBlockEntity controller = controllers.get((start + offset) % controllers.size());
            int participantsLeft = controllers.size() - offset;
            int fairShare = (remaining + participantsLeft - 1) / participantsLeft;
            int available = controller.getLocalCraftingCoolantCraftLimit(1, requiredOverclock, fairShare);
            int consumed = Math.min(fairShare, available);
            if (consumed > 0 && !controller.tryConsumeLocalCoolant(consumed, requiredOverclock)) {
                return false;
            }
            remaining -= consumed;
        }
        nextCoolantControllerIndex = (start + 1) % controllers.size();
        return remaining == 0;
    }

    public int getAvailableThreadSlots(@Nullable IGrid grid) {
        long available = 0L;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller == null || !hasMatchingWorker(physicalCluster, grid)) {
                continue;
            }
            available = saturatingAdd(available, Math.max(
                    0,
                    controller.getLocalThreadCount() - controller.getLocalRunningThreadCount()));
        }
        return (int) Math.min(Integer.MAX_VALUE, available);
    }

    /** Independent task slots visible from the requested AE grid. */
    public int getRecipeSlotCount(@Nullable IGrid grid) {
        long slots = 0L;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller != null && hasMatchingWorker(physicalCluster, grid)) {
                slots = saturatingAdd(slots, controller.getLocalThreadCount());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, slots);
    }

    public int getOccupiedRecipeSlots(@Nullable IGrid grid) {
        return getOccupiedCraftingSlots(grid);
    }

    /** Independent crafting task slots exposed to the requested AE grid. */
    public int getCraftingSlotCount(@Nullable IGrid grid) {
        long slots = 0L;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller != null && hasMatchingWorker(physicalCluster, grid)) {
                slots = saturatingAdd(slots, controller.getLocalThreadCount());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, slots);
    }

    /** Independent task slots currently occupied on the requested AE grid. */
    public int getOccupiedCraftingSlots(@Nullable IGrid grid) {
        long occupied = 0L;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller != null && hasMatchingWorker(physicalCluster, grid)) {
                occupied = saturatingAdd(occupied, controller.getLocalRunningThreadCount());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, occupied);
    }

    /** Parallel-core capacity that cannot be backed by workers on visible hosts. */
    public int getStructuralOverflow(@Nullable IGrid grid) {
        long overflow = 0L;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller != null && hasMatchingWorker(physicalCluster, grid)) {
                overflow = saturatingAdd(overflow, controller.getLocalOverflowThreads());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, overflow);
    }

    public int getMaxBatchPerThread(@Nullable IGrid grid) {
        int maxBatch = 0;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller != null && hasMatchingWorker(physicalCluster, grid)) {
                maxBatch = Math.max(maxBatch, controller.getLocalMaxBatchPerThread());
            }
        }
        return maxBatch;
    }

    /**
     * Runtime per-host batch data for the whole logical cluster. Each host
     * contributes
     * its actual thread count and actual per-slot batch size so the UI can show
     * what
     * every host really provides instead of only the cluster maximum.
     */
    public List<HostBatchInfo> getHostBatchInfos() {
        List<HostBatchInfo> result = new ArrayList<>(controllers.size());
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller != null) {
                result.add(new HostBatchInfo(
                        physicalCluster.isHighEnergyNetworkMode(),
                        controller.getLocalThreadCount(),
                        controller.isVirtualCraftingMode()
                                ? Long.MAX_VALUE
                                : controller.getLocalMaxBatchPerThread()));
            }
        }
        return List.copyOf(result);
    }

    /** Per-host runtime values shown in the host UI tooltip. */
    public record HostBatchInfo(boolean highEnergy, int threadCount, long maxBatchPerThread) {
    }

    private static boolean hasMatchingWorker(NECraftingCluster physicalCluster, @Nullable IGrid grid) {
        for (ECOCraftingWorkerBlockEntity worker : physicalCluster.getWorkers()) {
            if (grid == null || worker.getMainNode().getGrid() == grid) {
                return true;
            }
        }
        return false;
    }

    private int getAvailableLogicalSlots(NECraftingCluster physicalCluster) {
        ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
        return controller == null ? 0
                : Math.max(
                        0,
                        controller.getLocalThreadCount() - controller.getLocalRunningThreadCount());
    }

    private int getAvailableLogicalSlots(ECOCraftingWorkerBlockEntity worker) {
        NECraftingCluster physical = worker.getCluster();
        return physical == null ? 0 : getAvailableLogicalSlots(physical);
    }

    public boolean isOverclocked() {
        return overclocked;
    }

    public boolean isActiveCooling() {
        return activeCooling;
    }

    public void setOverclocked(boolean value) {
        overclocked = value;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.setLocalOverclocked(value);
        }
    }

    public void setActiveCooling(boolean value) {
        activeCooling = value;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.setLocalActiveCooling(value);
        }
    }

    public List<IPatternDetails> getMergedPatterns() {
        Map<PatternSignature, IPatternDetails> merged = new LinkedHashMap<>();
        for (ECOCraftingPatternBusBlockEntity patternBus : patternBuses) {
            for (IPatternDetails pattern : patternBus.getLocalAvailablePatterns()) {
                merged.putIfAbsent(PatternSignature.of(pattern), pattern);
            }
        }
        return List.copyOf(merged.values());
    }

    public boolean tryPushPattern(
            @Nullable IGrid grid,
            ECOExtractedPatternExecution execution,
            @Nullable UUID craftingJobId) {
        if (workers.isEmpty()) {
            return false;
        }
        if (getAvailableThreadSlots(grid) <= 0) {
            return false;
        }
        int clusterStart = 0;
        for (int clusterOffset = 0; clusterOffset < physicalClusters.size(); clusterOffset++) {
            int clusterIndex = (clusterStart + clusterOffset) % physicalClusters.size();
            NECraftingCluster physical = physicalClusters.get(clusterIndex);
            List<ECOCraftingWorkerBlockEntity> localWorkers = physical.getWorkers();
            if (getAvailableLogicalSlots(physical) <= 0 || localWorkers.isEmpty()) {
                continue;
            }
            int workerStart = 0;
            for (int workerOffset = 0; workerOffset < localWorkers.size(); workerOffset++) {
                int workerIndex = (workerStart + workerOffset) % localWorkers.size();
                ECOCraftingWorkerBlockEntity worker = localWorkers.get(workerIndex);
                if (grid != null && worker.getMainNode().getGrid() != grid) {
                    continue;
                }
                if (worker.pushPattern(execution, craftingJobId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean tryPushBatch(
            @Nullable IGrid grid,
            ECOBatchCraftingRequest request,
            @Nullable ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer) {
        if (workers.isEmpty() || offer == null) {
            return false;
        }
        // A batch occupies one logical host thread. Its physical worker slots
        // are checked separately below, so a batch may be larger than the
        // number of logical hosts in the network.
        if (getAvailableThreadSlots(grid) <= 0) {
            return false;
        }
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        if (!workers.contains(worker) || (grid != null && worker.getMainNode().getGrid() != grid)) {
            return false;
        }
        NECraftingCluster physical = worker.getCluster();
        ECOCraftingSystemBlockEntity controller = physical == null ? null : physical.getController();
        if (controller != null
                && offer.maxBatchSize() >= request.batchSize()
                && getAvailableLogicalSlots(worker) > 0
                && worker.getAvailableThreadSlots() > 0
                && worker.pushBatch(request, offer.result())) {
            return true;
        }
        // The selected offer is only a capacity snapshot. Retry other compatible workers after
        // a safe pre-ownership rejection so one stale worker cannot starve the network.
        for (ECOCraftingWorkerBlockEntity candidate : workers) {
            if (candidate == worker || (grid != null && candidate.getMainNode().getGrid() != grid)
                    || candidate.getAvailableThreadSlots() <= 0) {
                continue;
            }
            NECraftingCluster candidatePhysical = candidate.getCluster();
            ECOCraftingSystemBlockEntity candidateController = candidatePhysical == null
                    ? null : candidatePhysical.getController();
            if (candidateController == null || getAvailableLogicalSlots(candidate) <= 0) {
                continue;
            }
            ECOFastPathResult result = candidate.getFastPathCache().peek(request.key());
            if (result != null && !result.isNegative()
                    && result.matchesBatchRequest(request)
                    && candidate.pushBatch(request, result)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public ECOCraftingPatternBusBlockEntity.BatchFastPathOffer findBatchFastPathOffer(
            @Nullable IGrid grid,
            ECOFastPathKey key,
            @Nullable ECOExtractedPatternExecution execution,
            @Nullable ECOBatchCraftingRequest request,
            @Nullable UUID craftingJobId,
            long requestedBatchSize) {
        if (requestedBatchSize <= 0 || workers.isEmpty()) {
            return null;
        }
        if (getAvailableThreadSlots(grid) <= 0) {
            return null;
        }
        int clusterStart = 0;
        for (int clusterOffset = 0; clusterOffset < physicalClusters.size(); clusterOffset++) {
            NECraftingCluster physical = physicalClusters.get((clusterStart + clusterOffset) % physicalClusters.size());
            ECOCraftingSystemBlockEntity controller = physical.getController();
            List<ECOCraftingWorkerBlockEntity> localWorkers = physical.getWorkers();
            if (controller == null || getAvailableLogicalSlots(physical) <= 0 || localWorkers.isEmpty()) {
                continue;
            }
            long availableBatchSize = controller.isVirtualCraftingMode()
                    ? Long.MAX_VALUE
                    : controller.getLargestAvailableCraftingBatchSize();
            if (availableBatchSize <= 0) {
                continue;
            }
            int workerStart = 0;
            ECOCraftingPatternBusBlockEntity.BatchFastPathOffer bestOffer = null;
            for (int workerOffset = 0; workerOffset < localWorkers.size(); workerOffset++) {
                ECOCraftingWorkerBlockEntity worker = localWorkers
                        .get((workerStart + workerOffset) % localWorkers.size());
                if ((grid != null && worker.getMainNode().getGrid() != grid) || worker.getAvailableThreadSlots() <= 0) {
                    continue;
                }
                ECOFastPathResult result = execution == null
                        ? worker.getFastPathCache().peek(key)
                        : worker.getVerifiedFastPathResult(execution);
                if (result == null || result.isNegative()) {
                    continue;
                }
                if (request != null && !result.matchesBatchRequest(request)) {
                    worker.getFastPathCache().recordExpectedMismatch();
                    continue;
                }
                long maxBatchSize = Math.min(requestedBatchSize, availableBatchSize);
                bestOffer = new ECOCraftingPatternBusBlockEntity.BatchFastPathOffer(worker, result, maxBatchSize);
                break;
            }
            if (bestOffer != null) {
                return bestOffer;
            }
        }
        return null;
    }

    public boolean isBusy(@Nullable IGrid grid) {
        if (getAvailableThreadSlots(grid) <= 0) {
            return true;
        }
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (grid != null && worker.getMainNode().getGrid() != grid) {
                continue;
            }
            if (worker.getAvailableThreadSlots() > 0 || !worker.isBusy()) {
                return false;
            }
        }
        return true;
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

    private record PatternSignature(
            @Nullable AEItemKey definition,
            Class<?> patternType,
            List<InputSignature> inputs,
            List<GenericStack> outputs,
            boolean supportsExternalPush,
            @Nullable IPatternDetails fallbackIdentity) {
        private static PatternSignature of(IPatternDetails pattern) {
            try {
                List<InputSignature> inputs = Arrays.stream(pattern.getInputs())
                        .map(input -> new InputSignature(
                                List.copyOf(Arrays.asList(input.getPossibleInputs())),
                                input.getMultiplier()))
                        .toList();
                return new PatternSignature(
                        pattern.getDefinition(),
                        pattern.getClass(),
                        inputs,
                        List.copyOf(pattern.getOutputs()),
                        pattern.supportsPushInputsToExternalInventory(),
                        null);
            } catch (RuntimeException failure) {
                // Dynamic third-party patterns may not expose a stable shape.
                // Keep each such instance distinct instead of dropping it.
                return new PatternSignature(
                        null,
                        pattern.getClass(),
                        List.of(),
                        List.of(),
                        false,
                        pattern);
            }
        }
    }

    private record InputSignature(List<GenericStack> possibleInputs, long multiplier) {
    }
}
