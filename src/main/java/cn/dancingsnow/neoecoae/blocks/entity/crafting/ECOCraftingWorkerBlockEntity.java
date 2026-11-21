package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.integration.ae2.keyreuse.ECOKeyFactory;

import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.util.SettingsFrom;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity.VirtualLaneStartResult;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingWorkerBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOCraftingWorkerBlockEntity>
    implements IGridTickable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_PERSISTED_THREAD_RECORDS = 65_536;
    private static final Set<ECOCraftingWorkerBlockEntity> LOADED_SERVER_WORKERS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private final List<ECOCraftingThread> craftingThreads = new ArrayList<>();
    private final Map<UUID, Set<ECOCraftingThread>> threadsByJob = new HashMap<>();
    private boolean registeredForJobs;
    private boolean unloading;

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
    private boolean displayDirty = true;
    private boolean portableRecoveryExported;

    public ECOCraftingWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        boolean wasUnloading = unloading;
        unloading = false;
        super.onReady();
        if (level instanceof ServerLevel && !registeredForJobs) {
            synchronized (LOADED_SERVER_WORKERS) {
                LOADED_SERVER_WORKERS.add(this);
            }
            registeredForJobs = true;
            trackCurrentJobs(true);
        }
        if (wasUnloading && cluster != null) cluster.invalidateDispatchTopology();
        onThreadAvailabilityChanged();
        applyIdlePower(ECOPowerRole.CRAFTING_WORKER, 64, 0);
        reconcileJobTermination();
        refreshDisplayedJob();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterLoadedWorker();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterLoadedWorker();
        super.setRemoved();
    }

    private void unregisterLoadedWorker() {
        if (unloading) return;
        unloading = true;
        if (registeredForJobs) trackCurrentJobs(false);
        registeredForJobs = false;
        synchronized (LOADED_SERVER_WORKERS) {
            LOADED_SERVER_WORKERS.remove(this);
        }
        onThreadAvailabilityChanged();
        if (cluster != null) cluster.invalidateDispatchTopology();
    }

    public boolean isLoadedForDispatch() {
        return !unloading && !isRemoved();
    }

    public static Set<ECOCraftingWorkerBlockEntity> getLoadedServerWorkers() {
        synchronized (LOADED_SERVER_WORKERS) {
            return LOADED_SERVER_WORKERS;
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, !isWorking());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isWorking()) {
            refreshDisplayedJob();
            return TickRateModulation.SLEEP;
        }
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            long startNanos = System.nanoTime();
            try {
                int powerMultiply = 1;
                if (controller.isOverclocked() && !controller.isActiveCooling()) {
                    powerMultiply = controller.getTier().getOverclockedCrafterPowerMultiply();
                }
                int overlockTimes = controller.getExecutionOverclockTimes();
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
                return isWorking() ? rate : TickRateModulation.SLEEP;
            } finally {
                controller.recordPerformanceSample(System.nanoTime() - startNanos);
            }
        } else {
            TickRateModulation rate = TickRateModulation.IDLE;
            for (ECOCraftingThread thread : craftingThreads) {
                TickRateModulation outputRate = thread.tickOutputOnly();
                if (outputRate != null && outputRate.ordinal() > rate.ordinal()) rate = outputRate;
            }
            refreshDisplayedJob();
            return isWorking() ? rate : TickRateModulation.SLEEP;
        }
    }

    public void reconcileJobTermination() {
        for (ECOCraftingThread thread : craftingThreads) thread.reconcileJobTermination();
        if (isWorking()) wakeTickingDevice();
    }

    public void recoverTerminatedJob(UUID craftingJobId) {
        var threads = threadsByJob.get(craftingJobId);
        if (threads == null) return;
        for (var thread : threads) thread.reconcileJobTermination();
        wakeTickingDevice();
    }

    public void terminateRunningJobs() {
        if (!(level instanceof ServerLevel)) return;
        for (UUID jobId : threadsByJob.keySet()) ECOCraftingJobLifecycle.finish(level, jobId, false);
        reconcileJobTermination();
    }

    @Override
    public void breakCluster() {
        // Also covers removal of an already detached worker, where there is no cluster to notify.
        terminateRunningJobs();
        super.breakCluster();
    }

    @Override
    protected void onMainNodeGridChanged() {
        super.onMainNodeGridChanged();
        reconcileJobTermination();
    }

    @Override
    public void updateCluster(@Nullable NECraftingCluster cluster) {
        var previous = this.cluster;
        super.updateCluster(cluster);
        if (previous != cluster) {
            if (previous != null) {
                previous.onWorkerAvailabilityChanged(this);
                previous.invalidateDispatchTopology();
            }
            recountRunningBatches();
            onThreadAvailabilityChanged();
            if (cluster != null) cluster.invalidateDispatchTopology();
            reconcileJobTermination();
        }
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, UUID craftingJobId) {
        if (cluster == null || cluster.getController() == null || getAvailableBatchCapacity() <= 0) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        int threadObjectCapacity = controller.getThreadObjectCapacityForWorker(this);
        return dispatchToThread(threadObjectCapacity,
            thread -> thread.pushPattern(execution, controller, craftingJobId));
    }

    public boolean pushBatch(ECOVerifiedFastPathExecution verified) {
        ECOCraftingFastPathCache cache = getFastPathCache();
        if (NEConfig.postCraftingEvent) {
            return false;
        }
        if (cluster == null || cluster.getController() == null) {
            return false;
        }
        if (!verified.recipe().isIssuedBy(cache)) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        int workerThreadCapacity = controller.getThreadObjectCapacityForWorker(this);
        if (verified.batchSize() > getAvailableThreadSlots()
            || verified.batchSize() > getControllerAvailableThreadSlots(controller)) {
            return false;
        }
        return dispatchToThread(workerThreadCapacity, thread -> thread.pushBatch(verified, controller));
    }

    public boolean pushExactVirtualBatch(
            cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe recipe,
            java.math.BigInteger count, java.util.UUID job) {
        if (NEConfig.postCraftingEvent
                || cluster == null || cluster.getController() == null || isWorking()) return false;
        var controller = cluster.getController();
        if (!controller.isFullVirtualCraftingMode() || !recipe.isIssuedBy(getFastPathCache())) return false;
        return dispatchToThread(controller.getThreadObjectCapacityForWorker(this),
            thread -> thread.pushExactVirtualBatch(recipe, count, job, controller));
    }

    public boolean pushVirtualBatch(ECOVerifiedVirtualExecution verified) {
        ECOCraftingFastPathCache cache = getFastPathCache();
        if (NEConfig.postCraftingEvent) {
            return false;
        }
        if (cluster == null || cluster.getController() == null || isWorking()) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        if (!controller.isFullVirtualCraftingMode() || !verified.recipe().isIssuedBy(cache)) {
            return false;
        }
        return dispatchToThread(controller.getThreadObjectCapacityForWorker(this),
            thread -> thread.pushVirtualBatch(verified, controller));
    }

    /**
     * Owns the common worker-side dispatch lifecycle. The public push methods remain separate because they are
     * integration hook points, but they now share the same round-robin and thread-creation semantics.
     */
    private boolean dispatchToThread(int threadCapacity, Predicate<ECOCraftingThread> submit) {
        if (threadCapacity <= 0) {
            return false;
        }

        int threadCount = craftingThreads.size();
        if (threadCount > 0) {
            int start = Math.floorMod(nextFreeThreadIndex, threadCount);
            for (int offset = 0; offset < threadCount; offset++) {
                int index = (start + offset) % threadCount;
                ECOCraftingThread thread = craftingThreads.get(index);
                if (!thread.isFree() || !submit.test(thread)) {
                    continue;
                }
                nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                refreshDisplayedJob();
                return true;
            }
        }

        if (craftingThreads.size() >= threadCapacity) {
            return false;
        }

        ECOCraftingThread thread = new ECOCraftingThread(this);
        craftingThreads.add(thread);
        nextFreeThreadIndex = craftingThreads.size() % Math.max(1, threadCapacity);
        setChanged();
        markForUpdate();
        boolean accepted = submit.test(thread);
        if (accepted) {
            refreshDisplayedJob();
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

    public void onThreadAvailabilityChanged() {
        if (cluster != null) {
            cluster.onWorkerAvailabilityChanged(this);
        }
    }

    /** Remaining craft count accepted by this physical FX lane's next batch. */
    public int getAvailableBatchCapacity() {
        if (isLoadedForDispatch() && cluster != null && !cluster.isDestroyed() && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (controller.getThreadObjectCapacityForWorker(this) <= 0) {
                return 0;
            }
            // Each physical worker is one execution lane, even if a legacy save retained multiple thread
            // objects. Consult actual custody rather than the derived running counter, but never reuse a
            // free object alongside a busy one: virtual coolant is charged per physical lane.
            for (ECOCraftingThread thread : craftingThreads) {
                if (!thread.isFree()) {
                    return 0;
                }
            }
            return Math.max(0, controller.getThreadCountForWorker(this));
        }
        return 0;
    }

    public boolean isControlledBy(ECOCraftingSystemBlockEntity controller) {
        return cluster != null && cluster.getController() == controller;
    }

    public VirtualLaneStartResult getVirtualCraftingBlockedResult(long currentTick) {
        ECOCraftingSystemBlockEntity controller = cluster == null ? null : cluster.getController();
        if (controller == null || !controller.isFullVirtualCraftingMode()) return VirtualLaneStartResult.STARTED;
        VirtualLaneStartResult result = VirtualLaneStartResult.STARTED;
        for (ECOCraftingThread thread : craftingThreads) {
            VirtualLaneStartResult blocked = thread.getVirtualCraftingBlockedResult(controller, currentTick);
            if (blocked == VirtualLaneStartResult.COOLANT_UNAVAILABLE) return blocked;
            if (blocked == VirtualLaneStartResult.ENERGY_UNAVAILABLE) result = blocked;
        }
        return result;
    }

    public List<ECOCraftingThread.Snapshot> getThreadSnapshots() {
        List<ECOCraftingThread.Snapshot> snapshots = new ArrayList<>();
        for (ECOCraftingThread thread : craftingThreads) {
            ECOCraftingThread.Snapshot snapshot = thread.createSnapshot();
            if (snapshot.busy()) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
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

    public void markDisplayDirty() {
        displayDirty = true;
    }

    /** The first current output, for controller panels on both ordinary and monitor FX cores. */
    public ItemStack getCraftingDisplayItem() {
        return getCraftingDisplayOutput().item();
    }

    public CraftingOutputDisplay getCraftingDisplayOutput() {
        for (ECOCraftingThread thread : craftingThreads) {
            var key = thread.getDisplayedOutputKey();
            if (key == null) continue;
            PlannerAmount amount = PlannerAmount.ZERO;
            for (ECOCraftingThread current : craftingThreads) {
                amount = amount.add(current.getDisplayedItemQuantity(key));
            }
            if (amount.signum() > 0) return new CraftingOutputDisplay(key.toStack(1), amount.toBigInteger());
        }
        return CraftingOutputDisplay.EMPTY;
    }

    private void refreshDisplayedJob() {
        if (!displayDirty) return;
        GenericStack nextDisplay = null;
        if (isMonitor()) {
            for (ECOCraftingThread thread : craftingThreads) {
                var key = thread.getDisplayedOutputKey();
                if (key != null) {
                    long amount = thread.getDisplayedOutputAmount();
                    nextDisplay = displayedJob != null && displayedJob.what().equals(key)
                        && displayedJob.amount() == amount
                        ? displayedJob : new GenericStack(key, amount);
                    break;
                }
            }
        }
        displayDirty = false;
        if (!java.util.Objects.equals(displayedJob, nextDisplay)) {
            displayedJob = nextDisplay;
            markForUpdate();
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        GenericStack previousDisplayedJob = displayedJob;
        displayedJob = ECOKeyFactory.readBuffer(data);
        return changed || !java.util.Objects.equals(previousDisplayedJob, displayedJob);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        GenericStack.writeBuffer(displayedJob, data);
    }

    public void onBatchStarted(ECOCraftingThread thread, @Nullable UUID jobId) {
        runningThreads = Math.addExact(runningThreads, 1);
        trackThread(thread, jobId);
        onThreadAvailabilityChanged();
        setChanged();
        wakeTickingDevice();
    }

    @Override
    public void setChanged() {
        if (this.level != null) {
            level.blockEntityChanged(getBlockPos());
        }
    }

    public void onBatchStopped(ECOCraftingThread thread, @Nullable UUID jobId) {
        untrackThread(thread, jobId);
        if (runningThreads > 0) {
            runningThreads--;
            onThreadAvailabilityChanged();
        } else {
            reconcileBatchCounts();
        }
        setChanged();
    }

    public int recountRunningBatches() {
        int busy = 0;
        for (var thread : craftingThreads) if (!thread.isFree()) busy++;
        return runningThreads = busy;
    }

    public void reconcileBatchCounts() {
        if (cluster != null && !cluster.isDestroyed()) {
            cluster.recountWorkerBatches();
            cluster.invalidateDispatchAvailability();
        } else {
            recountRunningBatches();
        }
    }

    private void trackThread(ECOCraftingThread thread, @Nullable UUID jobId) {
        if (jobId == null) return;
        var threads = threadsByJob.computeIfAbsent(jobId, ignored -> new HashSet<>());
        if (threads.add(thread) && threads.size() == 1 && registeredForJobs) {
            ECOCraftingJobLifecycle.trackWorker(level, jobId, this, true);
        }
    }

    private void untrackThread(ECOCraftingThread thread, @Nullable UUID jobId) {
        if (jobId == null) return;
        var threads = threadsByJob.get(jobId);
        if (threads == null || !threads.remove(thread) || !threads.isEmpty()) return;
        threadsByJob.remove(jobId);
        if (registeredForJobs) ECOCraftingJobLifecycle.trackWorker(level, jobId, this, false);
    }

    private void trackCurrentJobs(boolean holding) {
        for (UUID jobId : threadsByJob.keySet()) ECOCraftingJobLifecycle.trackWorker(level, jobId, this, holding);
    }

    public void releaseCompletedJobOutputs(UUID craftingJobId) {
        var threads = threadsByJob.get(craftingJobId);
        if (threads == null) return;
        for (var thread : threads) thread.releaseCompletedJobOutputs(craftingJobId);
        wakeTickingDevice();
    }

    public boolean recoverJobToNetwork(UUID craftingJobId, MEStorage storage) {
        boolean recoveredAll = true;
        var threads = threadsByJob.get(craftingJobId);
        if (threads == null) return true;
        for (var thread : List.copyOf(threads)) {
            if (thread.belongsToJob(craftingJobId) && !thread.recoverInputsToNetwork(storage)) {
                recoveredAll = false;
            }
        }
        if (isWorking()) wakeTickingDevice();
        return recoveredAll;
    }

    /** Discards every in-flight stack without creating item entities. Intended for the administrative repair command. */
    public ClearResult discardAllCraftingContents() {
        Set<UUID> jobIds = new HashSet<>();
        int busyThreads = 0;
        for (ECOCraftingThread thread : craftingThreads) {
            ECOCraftingThread.Snapshot snapshot = thread.createSnapshot();
            if (!snapshot.busy()) {
                continue;
            }
            busyThreads++;
            if (snapshot.craftingJobId() != null) {
                jobIds.add(snapshot.craftingJobId());
            }
        }
        if (craftingThreads.isEmpty()) {
            return new ClearResult(0, Set.of());
        }

        if (registeredForJobs) trackCurrentJobs(false);
        threadsByJob.clear();
        craftingThreads.clear();
        runningThreads = 0;
        nextFreeThreadIndex = 0;
        displayedJob = null;
        displayDirty = false;
        onThreadAvailabilityChanged();
        setChanged();
        markForUpdate();
        return new ClearResult(busyThreads, jobIds);
    }

    public record ClearResult(int threadCount, Set<UUID> jobIds) {}

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
        markDisplayDirty();
        ListTag threads = data.getList("craftingThreads", Tag.TAG_COMPOUND);
        if (registeredForJobs) trackCurrentJobs(false);
        threadsByJob.clear();
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
                trackThread(thread, thread.getCraftingJobId());
            }
        }
        runningThreads = (int) Math.min(Integer.MAX_VALUE, busyThreads);
        nextFreeThreadIndex = 0;
        onThreadAvailabilityChanged();
        if (registeredForJobs) reconcileJobTermination();
    }

    public boolean isWorking() {
        return runningThreads > 0;
    }

    /** Number of currently executing batches, distinct from the craft count carried by those batches. */
    public int getRunningBatchCount() {
        return Math.max(0, runningThreads);
    }

    /** Queue occupancy counts the crafts inside each batch, rather than the number of thread objects. */
    public long getQueuedCraftCount() {
        long total = 0L;
        for (ECOCraftingThread thread : craftingThreads) {
            total = cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd(total, thread.getCraftCount());
        }
        return total;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (!preparePortableRecoveryThreads() || portableRecoveryExported) {
            return;
        }

        ItemStack recoveryCarrier = null;
        for (ItemStack drop : drops) {
            if (drop.is(getBlockState().getBlock().asItem()) && drop.getCount() == 1) {
                recoveryCarrier = drop;
                break;
            }
        }
        if (recoveryCarrier == null) {
            recoveryCarrier = new ItemStack(getBlockState().getBlock());
            drops.add(recoveryCarrier);
        }
        saveToItem(recoveryCarrier, level.registryAccess());
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != SettingsFrom.DISMANTLE_ITEM || level == null || !preparePortableRecoveryThreads()) {
            return;
        }
        CompoundTag blockEntityData = saveCustomOnly(level.registryAccess());
        net.minecraft.world.level.block.entity.BlockEntity.addEntityType(blockEntityData, getType());
        builder.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(blockEntityData));
        portableRecoveryExported = true;
    }

    private boolean preparePortableRecoveryThreads() {
        boolean hasPortableRecovery = false;
        for (ECOCraftingThread thread : craftingThreads) {
            hasPortableRecovery |= thread.preparePortableRecovery();
        }
        return hasPortableRecovery;
    }
}
