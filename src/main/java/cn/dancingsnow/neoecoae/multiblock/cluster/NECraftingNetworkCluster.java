package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
    private static final Comparator<NECraftingCluster> CLUSTER_ORDER = Comparator.comparing(
        cluster -> cluster.getController() == null
            ? Long.MAX_VALUE
            : cluster.getController().getBlockPos().asLong()
    );

    private final ServerLevel level;
    private List<NECraftingCluster> physicalClusters = List.of();
    private List<ECOCraftingSystemBlockEntity> controllers = List.of();
    private List<ECOCraftingWorkerBlockEntity> workers = List.of();
    private List<ECOCraftingPatternBusBlockEntity> patternBuses = List.of();
    private List<cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity> parallelCores = List.of();
    private int nextWorkerIndex;
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
        nextWorkerIndex = Math.floorMod(nextWorkerIndex, Math.max(1, workers.size()));
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
        nextWorkerIndex = 0;
        revision++;
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

    /** Each host's effective value is multiplied by its switch tier. */
    public int getEffectiveValue() {
        long total = 0L;
        for (NECraftingCluster cluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (controller != null) {
                total = saturatingAdd(
                    total,
                    saturatingMultiply(cluster.getNetworkMultiplier(), controller.getLocalThreadCount())
                );
            }
        }
        return (int)Math.min(Integer.MAX_VALUE, total);
    }

    /** The design gives each formed host exactly one logical crafting thread. */
    public int getThreadCount() {
        return getMemberCount();
    }

    public int getRunningThreadCount() {
        long total = 0L;
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            total = saturatingAdd(total, worker.getRunningTaskCount());
        }
        return (int)Math.min(Integer.MAX_VALUE, total);
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
            effective = Math.min(effective, controller.getLocalEffectiveOverclockTimes());
        }
        return effective == Integer.MAX_VALUE ? 0 : effective;
    }

    public long getMaxEnergyUsage() {
        long total = 0L;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            total = saturatingAdd(total, controller.getLocalMaxEnergyUsage());
        }
        return total;
    }

    public int getAvailableThreadSlots(@Nullable IGrid grid) {
        int available = 0;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller == null) {
                continue;
            }
            boolean hasMatchingWorker = hasMatchingWorker(physicalCluster, grid);
            int runningTasks = 0;
            for (ECOCraftingWorkerBlockEntity worker : physicalCluster.getWorkers()) {
                runningTasks += worker.getRunningTaskCount();
            }
            if (hasMatchingWorker && runningTasks < 1) {
                available++;
            }
        }
        return available;
    }

    /** Number of physical host slots visible from the requested AE grid. */
    public int getRecipeSlotCount(@Nullable IGrid grid) {
        int slots = 0;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            if (physicalCluster.getController() != null && hasMatchingWorker(physicalCluster, grid)) {
                slots++;
            }
        }
        return slots;
    }

    public int getOccupiedRecipeSlots(@Nullable IGrid grid) {
        return Math.max(0, getRecipeSlotCount(grid) - getAvailableThreadSlots(grid));
    }

    /** Parallel-core crafting slots exposed to the requested AE grid. */
    public int getCraftingSlotCount(@Nullable IGrid grid) {
        long slots = 0L;
        for (NECraftingCluster physicalCluster : physicalClusters) {
            ECOCraftingSystemBlockEntity controller = physicalCluster.getController();
            if (controller != null && hasMatchingWorker(physicalCluster, grid)) {
                slots = saturatingAdd(
                    slots,
                    saturatingMultiply(physicalCluster.getNetworkMultiplier(), controller.getLocalThreadCount())
                );
            }
        }
        return (int)Math.min(Integer.MAX_VALUE, slots);
    }

    /** Physical batch slots currently occupied on the requested AE grid. */
    public int getOccupiedCraftingSlots(@Nullable IGrid grid) {
        long occupied = 0L;
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (grid == null || worker.getMainNode().getGrid() == grid) {
                occupied = saturatingAdd(occupied, worker.getRunningThreads());
            }
        }
        return (int)Math.min(Integer.MAX_VALUE, occupied);
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
        return (int)Math.min(Integer.MAX_VALUE, overflow);
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
        int runningTasks = 0;
        for (ECOCraftingWorkerBlockEntity worker : physicalCluster.getWorkers()) {
            runningTasks += worker.getRunningTaskCount();
        }
        return Math.max(0, 1 - runningTasks);
    }

    /*
     * Physical worker slots are still used by local workers for batch work,
     * but a network task consumes one logical host thread.
     */
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
            for (IPatternDetails pattern : patternBus.getAvailablePatterns()) {
                merged.putIfAbsent(PatternSignature.of(pattern), pattern);
            }
        }
        return List.copyOf(merged.values());
    }

    public boolean tryPushPattern(
        ECOCraftingPatternBusBlockEntity source,
        ECOExtractedPatternExecution execution,
        @Nullable UUID craftingJobId
    ) {
        if (workers.isEmpty() || isOutputBusy(execution)) {
            return false;
        }
        IGrid grid = source.getGrid();
        if (getAvailableThreadSlots(grid) <= 0) {
            return false;
        }
        int start = Math.floorMod(nextWorkerIndex, workers.size());
        for (int offset = 0; offset < workers.size(); offset++) {
            int index = (start + offset) % workers.size();
            ECOCraftingWorkerBlockEntity worker = workers.get(index);
            if (grid != null && worker.getMainNode().getGrid() != grid) {
                continue;
            }
            NECraftingCluster physical = worker.getCluster();
            ECOCraftingSystemBlockEntity controller = physical == null ? null : physical.getController();
            if (controller != null
                && physical != null
                && getAvailableLogicalSlots(physical) > 0
                && worker.pushPattern(execution, craftingJobId)) {
                nextWorkerIndex = (index + 1) % workers.size();
                return true;
            }
        }
        return false;
    }

    public boolean tryPushBatch(
        ECOCraftingPatternBusBlockEntity source,
        ECOBatchCraftingRequest request,
        @Nullable ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer
    ) {
        if (workers.isEmpty() || offer == null || isOutputBusy(request)) {
            return false;
        }
        IGrid grid = source.getGrid();
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
            && worker.getAvailableThreadSlots() >= request.batchSize()
            && worker.pushBatch(request, offer.result())) {
            int index = workers.indexOf(worker);
            nextWorkerIndex = index < 0 ? nextWorkerIndex : (index + 1) % workers.size();
            return true;
        }
        return false;
    }

    @Nullable
    public ECOCraftingPatternBusBlockEntity.BatchFastPathOffer findBatchFastPathOffer(
        ECOCraftingPatternBusBlockEntity source,
        ECOFastPathKey key,
        @Nullable ECOExtractedPatternExecution execution,
        @Nullable ECOBatchCraftingRequest request,
        int requestedBatchSize
    ) {
        if (requestedBatchSize <= 0 || workers.isEmpty()) {
            return null;
        }
        IGrid grid = source.getGrid();
        if (getAvailableThreadSlots(grid) <= 0) {
            return null;
        }
        int start = Math.floorMod(nextWorkerIndex, workers.size());
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer bestOffer = null;
        for (int offset = 0; offset < workers.size(); offset++) {
            int index = (start + offset) % workers.size();
            ECOCraftingWorkerBlockEntity worker = workers.get(index);
            if (grid != null && worker.getMainNode().getGrid() != grid) {
                continue;
            }
            int availableSlots = worker.getAvailableThreadSlots();
            if (availableSlots <= 0) {
                continue;
            }
            // Logical capacity decides whether this host may accept the
            // task; the worker's physical capacity decides the batch size.
            if (getAvailableLogicalSlots(worker) <= 0) {
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
            int maxBatchSize = Math.max(
                0,
                Math.min(requestedBatchSize, availableSlots)
            );
            if (maxBatchSize > 0 && (bestOffer == null || maxBatchSize > bestOffer.maxBatchSize())) {
                bestOffer = new ECOCraftingPatternBusBlockEntity.BatchFastPathOffer(worker, result, maxBatchSize);
                if (maxBatchSize >= requestedBatchSize) {
                    break;
                }
            }
        }
        return bestOffer;
    }

    public boolean isBusy(ECOCraftingPatternBusBlockEntity source) {
        IGrid grid = source.getGrid();
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

    private boolean isOutputBusy(ECOExtractedPatternExecution execution) {
        for (GenericStack output : execution.expectedOutputs()) {
            if (output.what() instanceof AEItemKey itemKey && isOutputBusy(itemKey.toStack(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean isOutputBusy(ECOBatchCraftingRequest request) {
        for (GenericStack output : request.outputsPerCraft()) {
            if (output.what() instanceof AEItemKey itemKey && isOutputBusy(itemKey.toStack(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean isOutputBusy(ItemStack output) {
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (worker.hasBusyOutput(output)) {
                return true;
            }
        }
        return false;
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
        @Nullable IPatternDetails fallbackIdentity
    ) {
        private static PatternSignature of(IPatternDetails pattern) {
            try {
                List<InputSignature> inputs = Arrays.stream(pattern.getInputs())
                    .map(input -> new InputSignature(
                        List.copyOf(Arrays.asList(input.getPossibleInputs())),
                        input.getMultiplier()
                    ))
                    .toList();
                return new PatternSignature(
                    pattern.getDefinition(),
                    pattern.getClass(),
                    inputs,
                    List.copyOf(pattern.getOutputs()),
                    pattern.supportsPushInputsToExternalInventory(),
                    null
                );
            } catch (RuntimeException failure) {
                // Dynamic third-party patterns may not expose a stable shape.
                // Keep each such instance distinct instead of dropping it.
                return new PatternSignature(
                    null,
                    pattern.getClass(),
                    List.of(),
                    List.of(),
                    false,
                    pattern
                );
            }
        }
    }

    private record InputSignature(List<GenericStack> possibleInputs, long multiplier) {
    }
}
