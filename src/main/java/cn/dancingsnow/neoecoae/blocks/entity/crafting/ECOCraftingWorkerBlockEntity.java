package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingWorkerBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOCraftingWorkerBlockEntity>
    implements IGridTickable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_PERSISTED_THREAD_RECORDS = 65_536;

    private final List<ECOCraftingThread> craftingThreads = new ArrayList<>();

    /**
     * Only used while this worker has no cluster - a detached worker can never craft, but statistics calls
     * still need a sink. The real cache lives on the crafting cluster (or, when a Network Switch group is
     * formed, on that group) so verified recipes are shared instead of re-verified per worker.
     */
    @Nullable
    private ECOCraftingFastPathCache detachedFastPathCache;

    @Getter
    private int runningThreads = 0;

    private int nextFreeThreadIndex = 0;

    @Getter
    @Nullable
    private GenericStack displayedJob;

    public ECOCraftingWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
        refreshDisplayedJob();
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
                if (controller.isOverclocked() && !controller.isActiveCooling()) {
                    powerMultiply = controller.getTier().getOverclockedCrafterPowerMultiply();
                }
                int overlockTimes = controller.getEffectiveOverclockTimes();
                TickRateModulation rate = TickRateModulation.IDLE;
                for (ECOCraftingThread thread : craftingThreads) {
                    TickRateModulation r = thread.tick(controller, overlockTimes, powerMultiply, ticksSinceLastCall);
                    if (r.ordinal() > rate.ordinal()) {
                        rate = r;
                    }
                }
                // Thread state transitions call setChanged() at the mutation point. Avoid marking the block entity
                // dirty on every tick when no persistent field changed; monitor rendering is the only reason to
                // refresh the derived display here.
                if (isMonitor()) {
                    refreshDisplayedJob();
                }
                return rate;
            } finally {
                controller.recordPerformanceSample(System.nanoTime() - startNanos);
            }
        } else {
            return TickRateModulation.IDLE;
        }
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, UUID craftingJobId) {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (isWorking()) {
                getFastPathCache().recordNoThreadReject();
                return false;
            }
            int threadObjectCapacity = controller.getThreadObjectCapacityForWorker(this);

            int threadCount = craftingThreads.size();
            if (threadCount > 0) {
                int start = Math.floorMod(nextFreeThreadIndex, threadCount);
                for (int offset = 0; offset < threadCount; offset++) {
                    int index = (start + offset) % threadCount;
                    ECOCraftingThread thread = craftingThreads.get(index);
                    if (!thread.isFree()) {
                        continue;
                    }
                    if (thread.pushPattern(execution, controller, craftingJobId)) {
                        nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                        refreshDisplayedJob();
                        return true;
                    }
                }
            }

            if (craftingThreads.size() >= threadObjectCapacity) {
                return false;
            }

            ECOCraftingThread thread = new ECOCraftingThread(this);
            craftingThreads.add(thread);
            nextFreeThreadIndex = craftingThreads.size() % Math.max(1, threadObjectCapacity);
            setChanged();
            markForUpdate();
            boolean accepted = thread.pushPattern(execution, controller, craftingJobId);
            if (accepted) {
                refreshDisplayedJob();
            }
            return accepted;
        } else {
            return false;
        }
    }

    public boolean pushBatch(ECOVerifiedFastPathExecution verified) {
        ECOCraftingFastPathCache cache = getFastPathCache();
        if (!NEConfig.ecoAe2FastPathEnabled || NEConfig.postCraftingEvent) {
            cache.recordDisabled();
            return false;
        }
        if (cluster == null || cluster.getController() == null) {
            return false;
        }
        if (!verified.recipe().isIssuedBy(cache)) {
            cache.recordExpectedMismatch();
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        int workerThreadCapacity = controller.getThreadObjectCapacityForWorker(this);
        if (verified.batchSize() > getAvailableThreadSlots()
            || verified.batchSize() > getControllerAvailableThreadSlots(controller)) {
            cache.recordNoThreadReject();
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
                if (thread.pushBatch(verified, controller)) {
                    nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                    refreshDisplayedJob();
                    return true;
                }
            }
        }

        if (craftingThreads.size() >= workerThreadCapacity) {
            return false;
        }

        ECOCraftingThread thread = new ECOCraftingThread(this);
        craftingThreads.add(thread);
        nextFreeThreadIndex = craftingThreads.size() % Math.max(1, workerThreadCapacity);
        setChanged();
        markForUpdate();
        boolean accepted = thread.pushBatch(verified, controller);
        if (accepted) {
            refreshDisplayedJob();
        }
        return accepted;
    }

    public boolean pushVirtualBatch(ECOVerifiedVirtualExecution verified) {
        ECOCraftingFastPathCache cache = getFastPathCache();
        if (!NEConfig.ecoAe2FastPathEnabled || NEConfig.postCraftingEvent) {
            cache.recordDisabled();
            return false;
        }
        if (cluster == null || cluster.getController() == null || isWorking()) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        if (!controller.isFullVirtualCraftingMode() || !verified.recipe().isIssuedBy(cache)) {
            cache.recordExpectedMismatch();
            return false;
        }
        for (int index = 0; index < craftingThreads.size(); index++) {
            ECOCraftingThread thread = craftingThreads.get(index);
            if (thread.isFree() && thread.pushVirtualBatch(verified, controller)) {
                nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                refreshDisplayedJob();
                return true;
            }
        }
        if (craftingThreads.size() >= controller.getThreadObjectCapacityForWorker(this)) {
            return false;
        }
        ECOCraftingThread thread = new ECOCraftingThread(this);
        craftingThreads.add(thread);
        boolean accepted = thread.pushVirtualBatch(verified, controller);
        if (accepted) {
            refreshDisplayedJob();
            setChanged();
            markForUpdate();
        }
        return accepted;
    }

    /**
     * Fast-path knowledge for this worker: the crafting cluster's cache, or the Network Switch group's shared
     * cache while a group is formed. A worker never owns verified recipes on its own, so a recipe verified by
     * any worker of the group is immediately usable by all of them.
     */
    public ECOCraftingFastPathCache getFastPathCache() {
        cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster owner = cluster;
        if (owner != null) {
            return owner.getFastPathCache();
        }
        if (detachedFastPathCache == null) {
            detachedFastPathCache = new ECOCraftingFastPathCache(ECOCraftingFastPathCache.MIN_CACHE_SIZE);
        }
        return detachedFastPathCache;
    }

    public boolean isBusy() {
        return getAvailableThreadSlots() <= 0;
    }

    public int getAvailableThreadSlots() {
        return getAvailableBatchCapacity();
    }

    /** Remaining craft count accepted by this physical FX lane's next batch. */
    public int getAvailableBatchCapacity() {
        if (cluster != null && cluster.getController() != null) {
            if (isWorking()) {
                return 0;
            }
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            return Math.max(0, controller.getThreadCountForWorker(this));
        }
        return 0;
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

    private int getControllerAvailableThreadSlots(ECOCraftingSystemBlockEntity controller) {
        return controller.getDispatchThreadCapacity();
    }

    public int getCapacityMultiplier() {
        return 1;
    }

    public boolean isMonitor() {
        return getBlockState().is(NEBlocks.FX_MONITOR_CORE.get());
    }

    private void refreshDisplayedJob() {
        GenericStack nextDisplay = null;
        if (isMonitor()) {
            for (ECOCraftingThread thread : craftingThreads) {
                ECOCraftingThread.Snapshot snapshot = thread.createSnapshot();
                if (!snapshot.busy() || snapshot.outputItem().isEmpty()) {
                    continue;
                }
                GenericStack item = GenericStack.fromItemStack(snapshot.outputItem().copyWithCount(1));
                if (item != null) {
                    nextDisplay = new GenericStack(item.what(), Math.max(1L, snapshot.outputAmount()));
                    break;
                }
            }
        }
        if (!java.util.Objects.equals(displayedJob, nextDisplay)) {
            displayedJob = nextDisplay;
            markForUpdate();
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        GenericStack previousDisplayedJob = displayedJob;
        displayedJob = GenericStack.readBuffer(data);
        return changed || !java.util.Objects.equals(previousDisplayedJob, displayedJob);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        GenericStack.writeBuffer(displayedJob, data);
    }

    public void onBatchStarted() {
        int previousRunningThreads = runningThreads;
        runningThreads = Math.addExact(runningThreads, 1);
        ECOCraftingSystemBlockEntity controller = cluster == null ? null : cluster.getController();
        boolean controllerUpdateAttempted = controller != null;
        try {
            if (controller != null) {
                controller.recalculateRunningThreadCountFromWorkers();
            }
            setChanged();
            wakeTickingDevice();
        } catch (RuntimeException | Error e) {
            // Error is included so worker and controller thread counts are rolled back before it escapes.
            runningThreads = previousRunningThreads;
            if (controllerUpdateAttempted) {
                try {
                    controller.recalculateRunningThreadCountFromWorkers();
                } catch (RuntimeException | Error rollbackFailure) {
                    // Preserve a rollback failure without hiding the original runtime failure or Error.
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

    public void onBatchStopped() {
        runningThreads -= 1;
        if (runningThreads < 0) {
            LOGGER.warn(
                "ECO worker running batch count underflow: worker={} before correction",
                getBlockPos()
            );
            runningThreads = 0;
        }
        if (cluster != null && cluster.getController() != null) {
            cluster.getController().recalculateRunningThreadCountFromWorkers();
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
        // The fast-path cache is no longer per worker, so a worker load must not wipe knowledge its whole
        // cluster shares. Staleness is already impossible: every key carries the reload generation and the
        // dimension, and losing the cluster drops the cache with it.
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
                busyThreads++;
            }
        }
        runningThreads = (int) Math.min(Integer.MAX_VALUE, busyThreads);
        nextFreeThreadIndex = 0;
        if (cluster != null && cluster.getController() != null) {
            cluster.getController().recalculateRunningThreadCountFromWorkers();
        }
    }

    public boolean isWorking() {
        return runningThreads > 0;
    }

    /** Number of currently executing batches, distinct from the craft count carried by those batches. */
    public int getRunningBatchCount() {
        return Math.max(0, runningThreads);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ECOCraftingThread thread : craftingThreads) {
            thread.dropRecoverablesAndClear(drops);
        }
    }
}
