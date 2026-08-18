package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStage;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingWorkerBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingWorkerBlockEntity>
    implements IGridTickable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_PERSISTED_THREAD_RECORDS = 65_536;

    private final List<ECOCraftingThread> craftingThreads = new ArrayList<>();
    private final ECOCraftingFastPathCache detachedFastPathCache = new ECOCraftingFastPathCache();

    @Getter
    private int runningThreads = 0;

    private int nextFreeThreadIndex = 0;

    public ECOCraftingWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            long startNanos = System.nanoTime();
            try {
                int powerMultiply = 1;
                if (controller.isLocalOverclocked() && !controller.isLocalActiveCooling()) {
                    powerMultiply = controller.getTier().getOverclockedCrafterPowerMultiply();
                }
                powerMultiply = (int)Math.min(
                    Integer.MAX_VALUE,
                    (long)powerMultiply * cluster.getNetworkPowerMultiplier()
                );
                int overlockTimes = controller.getEffectiveOverclockTimesForLocalTasks();
                boolean fullNetworkPowerMode = cluster.getNetworkMultiplier() > 1;
                boolean networkPowerPrepaid = fullNetworkPowerMode
                    && controller.tryPayFullNetworkPowerForCurrentTick();
                TickRateModulation rate = TickRateModulation.IDLE;
                for (ECOCraftingThread thread : craftingThreads) {
                    TickRateModulation r = thread.tick(
                        overlockTimes,
                        powerMultiply,
                        ticksSinceLastCall,
                        fullNetworkPowerMode,
                        networkPowerPrepaid
                    );
                    if (r.ordinal() > rate.ordinal()) {
                        rate = r;
                    }
                }
                setChanged();
                return rate;
            } finally {
                controller.recordPerformanceSample(System.nanoTime() - startNanos);
            }
        } else {
            return TickRateModulation.IDLE;
        }
    }

    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table) {
        return pushPattern(pattern, table, null);
    }

    public boolean pushPattern(
        IMolecularAssemblerSupportedPattern pattern,
        KeyCounter[] table,
        UUID craftingJobId
    ) {
        return pushPattern(ECOExtractedPatternExecution.slow(pattern, table), craftingJobId);
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, UUID craftingJobId) {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (getRunningThreads() >= controller.getThreadCountPerWorker()) {
                getFastPathCache().recordNoThreadReject();
                return false;
            }
            ECOCraftingSystemBlockEntity.CraftingLane lane = controller.findAvailableCraftingLane(1);
            if (lane == null) {
                getFastPathCache().recordNoThreadReject();
                return false;
            }

            int threadCount = craftingThreads.size();
            if (threadCount > 0) {
                int start = Math.floorMod(nextFreeThreadIndex, threadCount);
                for (int offset = 0; offset < threadCount; offset++) {
                    int index = (start + offset) % threadCount;
                    ECOCraftingThread thread = craftingThreads.get(index);
                    if (!thread.isFree()) {
                        continue;
                    }
                    if (thread.pushPattern(execution, controller, craftingJobId, lane.index())) {
                        nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                        return true;
                    }
                }
            }

            if (craftingThreads.size() >= controller.getThreadCountPerWorker()) {
                return false;
            }

            ECOCraftingThread thread = new ECOCraftingThread(this);
            craftingThreads.add(thread);
            nextFreeThreadIndex = craftingThreads.size() % Math.max(1, controller.getThreadCountPerWorker());
            setChanged();
            markForUpdate();
            return thread.pushPattern(execution, controller, craftingJobId, lane.index());
        } else {
            return false;
        }
    }

    public boolean pushBatch(ECOBatchCraftingRequest request, ECOFastPathResult verifiedResult) {
        if (!NEConfig.ecoAe2FastPathEnabled || NEConfig.postCraftingEvent) {
            getFastPathCache().recordDisabled();
            ECOFastPathDiagnostics.logBatchFailure(request,
                NEConfig.postCraftingEvent ? ECOFastPathFallbackReason.POST_CRAFTING_EVENT
                    : ECOFastPathFallbackReason.FAST_PATH_DISABLED,
                ECOFastPathStage.ELIGIBILITY, getBlockPos(), currentTick(),
                "worker_gate enabled=" + NEConfig.ecoAe2FastPathEnabled
                    + " postCraftingEvent=" + NEConfig.postCraftingEvent);
            return false;
        }
        if (cluster == null || cluster.getController() == null) {
            ECOFastPathDiagnostics.logBatchFailure(request, ECOFastPathFallbackReason.WORKER_REJECTED,
                ECOFastPathStage.WORKER_ACCEPT, getBlockPos(), currentTick(),
                "worker_has_no_active_cluster_or_controller");
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        int requiredLaneCapacity = controller.isVirtualCraftingMode()
            ? 1
            : Math.toIntExact(request.batchSize());
        ECOCraftingSystemBlockEntity.CraftingLane lane = controller.findAvailableCraftingLane(requiredLaneCapacity);
        if (lane == null || getAvailableThreadSlots() <= 0 || getControllerAvailableThreadSlots(controller) <= 0) {
            getFastPathCache().recordNoThreadReject();
            ECOFastPathDiagnostics.logBatchFailure(request, ECOFastPathFallbackReason.NO_THREAD_SLOT,
                ECOFastPathStage.WORKER_ACCEPT, getBlockPos(), currentTick(),
                "laneAvailable=" + (lane != null) + " workerSlots=" + getAvailableThreadSlots()
                    + " controllerSlots=" + getControllerAvailableThreadSlots(controller));
            return false;
        }

        int threadCount = craftingThreads.size();
        if (threadCount > 0) {
            int start = Math.floorMod(nextFreeThreadIndex, threadCount);
            for (int offset = 0; offset < threadCount; offset++) {
                int index = (start + offset) % threadCount;
                ECOCraftingThread thread = craftingThreads.get(index);
                if (!thread.isFree()) {
                    continue;
                }
                if (thread.pushBatch(request, controller, verifiedResult, lane.index())) {
                    nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                    return true;
                }
            }
        }

        if (craftingThreads.size() >= controller.getThreadCountPerWorker()) {
            ECOFastPathDiagnostics.logBatchFailure(request, ECOFastPathFallbackReason.NO_THREAD_SLOT,
                ECOFastPathStage.WORKER_ACCEPT, getBlockPos(), currentTick(),
                "all_existing_threads_rejected_and_thread_count_reached_limit="
                    + controller.getThreadCountPerWorker());
            return false;
        }

        ECOCraftingThread thread = new ECOCraftingThread(this);
        craftingThreads.add(thread);
        nextFreeThreadIndex = craftingThreads.size() % Math.max(1, controller.getThreadCountPerWorker());
        setChanged();
        markForUpdate();
        boolean accepted = thread.pushBatch(request, controller, verifiedResult, lane.index());
        if (!accepted) {
            ECOFastPathDiagnostics.logBatchFailure(request, ECOFastPathFallbackReason.WORKER_REJECTED,
                ECOFastPathStage.WORKER_ACCEPT, getBlockPos(), currentTick(),
                "new_thread_rejected_batch lane=" + lane.index());
        }
        return accepted;
    }

    public ECOFastPathResult getVerifiedFastPathResult(ECOExtractedPatternExecution execution) {
        var key = execution.key();
        if (key == null) {
            getFastPathCache().recordKeyBuildFailed();
            ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.KEY_BUILD_FAILED,
                ECOFastPathStage.CACHE_LOOKUP, getBlockPos(), currentTick(), "execution_key_is_null");
            return null;
        }
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        ECOFastPathResult result = getFastPathCache().get(key, tick);
        if (result == null) {
            return null;
        }
        if (result.isNegative()) {
            getFastPathCache().recordFallbackSlowPath();
            ECOFastPathDiagnostics.logFailure(execution,
                result.negativeReason() == null
                    ? ECOFastPathFallbackReason.NEGATIVE_CACHE
                    : result.negativeReason(),
                ECOFastPathStage.CACHE_LOOKUP, getBlockPos(), tick, "negative_cache_result");
            return null;
        }
        if (!result.matchesExecution(execution)) {
            getFastPathCache().recordExpectedMismatch();
            ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.CACHE_ENTRY_MISMATCH,
                ECOFastPathStage.CACHE_VERIFY, getBlockPos(), tick,
                "cached_result_does_not_match_execution");
            return null;
        }
        return result;
    }

    public ECOCraftingFastPathCache getFastPathCache() {
        ECOCraftingSystemBlockEntity controller = cluster == null ? null : cluster.getController();
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getFastPathCache();
        }
        return controller == null ? detachedFastPathCache : controller.getFastPathCache();
    }

    private static long currentTick() {
        return appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
    }

    public boolean isBusy() {
        return getAvailableThreadSlots() <= 0;
    }

    public int getAvailableThreadSlots() {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            return Math.max(0, controller.getThreadCountPerWorker() - getRunningThreads());
        }
        return 0;
    }

    public int getBatchOccupiedThreadSlots(int craftCount) {
        return craftCount > 0 ? 1 : 0;
    }

    public List<Integer> getAssignedLaneIndices() {
        List<Integer> indices = new ArrayList<>();
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.isFree() && thread.getAssignedLaneIndex() >= 0) {
                indices.add(thread.getAssignedLaneIndex());
            }
        }
        return List.copyOf(indices);
    }

    public int getUnassignedBusyTaskCount() {
        int count = 0;
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.isFree() && thread.getAssignedLaneIndex() < 0) {
                count++;
            }
        }
        return count;
    }

    /** Number of logical crafting tasks, independent of batch slot usage. */
    public int getRunningTaskCount() {
        int count = 0;
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.isFree()) {
                count++;
            }
        }
        return count;
    }

    public boolean isControlledBy(ECOCraftingSystemBlockEntity controller) {
        return cluster != null && cluster.getController() == controller;
    }

    public List<ECOCraftingThread.Snapshot> getThreadSnapshots() {
        List<ECOCraftingThread.Snapshot> snapshots = new ArrayList<>();
        for (ECOCraftingThread thread : craftingThreads) {
            ECOCraftingThread.Snapshot snapshot = thread.createSnapshot();
            if (snapshot.busy()) {
                snapshots.add(snapshot);
            }
        }
        return List.copyOf(snapshots);
    }

    public boolean hasBusyOutput(ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        for (ECOCraftingThread thread : craftingThreads) {
            if (thread.hasOutput(output)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack getActiveCraftOutput() {
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.isFree()) {
                ItemStack output = thread.getOutputItem();
                if (!output.isEmpty()) {
                    return output;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private int getControllerAvailableThreadSlots(ECOCraftingSystemBlockEntity controller) {
        return Math.max(0, controller.getLocalThreadCount() - controller.getLocalRunningThreadCount());
    }

    public void onThreadWork() {
        onThreadWork(1);
    }

    public void onThreadWork(int occupiedThreadSlots) {
        int slots = Math.max(1, occupiedThreadSlots);
        int previousRunningThreads = runningThreads;
        runningThreads = Math.addExact(runningThreads, slots);
        ECOCraftingSystemBlockEntity controller = cluster == null ? null : cluster.getController();
        boolean controllerUpdateAttempted = controller != null;
        try {
            if (controller != null) {
                controller.onWorkerThreadCountChanged(slots);
            }
            setChanged();
            wakeTickingDevice();
        } catch (RuntimeException | Error e) {
            runningThreads = previousRunningThreads;
            if (controllerUpdateAttempted) {
                try {
                    controller.onWorkerThreadCountChanged(-slots);
                } catch (RuntimeException | Error rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
            }
            throw e;
        }
    }

    @Override
    public void setChanged() {
        if (this.level != null) {
            level.blockEntityChanged(getBlockPos());
        }
    }

    public void onThreadStop() {
        onThreadStop(1);
    }

    public void onThreadStop(int occupiedThreadSlots) {
        int slots = Math.max(1, occupiedThreadSlots);
        runningThreads -= slots;
        if (runningThreads < 0) {
            LOGGER.warn(
                "ECO worker runningThreads underflow: worker={} slots={} before correction",
                getBlockPos(),
                slots
            );
            runningThreads = 0;
        }
        if (cluster != null && cluster.getController() != null) {
            cluster.getController().onWorkerThreadCountChanged(-slots);
        }
        setChanged();
    }

    public boolean recoverJobToNetwork(UUID craftingJobId, MEStorage storage) {
        boolean recoveredAll = true;
        for (ECOCraftingThread thread : craftingThreads) {
            if (thread.belongsToJob(craftingJobId) && !thread.recoverInputsToNetwork(storage)) {
                recoveredAll = false;
            }
        }
        if (recoveredAll) {
            wakeTickingDevice();
        }
        return recoveredAll;
    }

    public void releaseJobOutputsToNetwork(UUID craftingJobId) {
        for (ECOCraftingThread thread : craftingThreads) {
            thread.releaseJobOutputsToNetwork(craftingJobId);
        }
        wakeTickingDevice();
    }

    private void wakeTickingDevice() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        ListTag threads = new ListTag();
        for (ECOCraftingThread thread : craftingThreads) {
            threads.add(thread.serializeNBT(registries));
        }
        data.put("craftingThreads", threads);
        data.putInt("runningThreads", runningThreads);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        ListTag threads = data.getList("craftingThreads", Tag.TAG_COMPOUND);
        craftingThreads.clear();
        if (cluster == null || cluster.getController() == null) {
            detachedFastPathCache.clear();
        }
        if (threads.size() > MAX_PERSISTED_THREAD_RECORDS) {
            LOGGER.error(
                "ECO worker persisted too many crafting threads; excess records will be ignored: worker={} count={}",
                getBlockPos(),
                threads.size()
            );
        }
        long busyThreads = 0L;
        for (int i = 0; i < Math.min(threads.size(), MAX_PERSISTED_THREAD_RECORDS); i++) {
            ECOCraftingThread thread = new ECOCraftingThread(this);
            thread.deserializeNBT(registries, threads.getCompound(i));
            craftingThreads.add(thread);
            if (!thread.isFree()) {
                busyThreads += thread.getOccupiedThreadSlots();
            }
        }
        runningThreads = (int) Math.min(Integer.MAX_VALUE, busyThreads);
        nextFreeThreadIndex = 0;
    }

    public boolean isWorking() {
        return runningThreads > 0;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ECOCraftingThread thread : craftingThreads) {
            thread.dropRecoverablesAndClear(drops);
        }
    }
}
