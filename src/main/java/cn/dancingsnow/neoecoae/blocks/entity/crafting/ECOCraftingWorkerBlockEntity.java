package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathLimits;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathResult;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class ECOCraftingWorkerBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingWorkerBlockEntity>
        implements IGridTickable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<ECOCraftingThread> craftingThreads = new ArrayList<>();
    private final ECOCraftingFastPathCache fastPathCache = new ECOCraftingFastPathCache();
    private final IActionSource actionSource;

    @Getter
    private int runningThreads = 0;

    private int nextFreeThreadIndex = 0;

    public ECOCraftingWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.actionSource = IActionSource.ofMachine(this);
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        long startNanos = System.nanoTime();
        ECOCraftingSystemBlockEntity controller = cluster == null ? null : cluster.getController();
        try {
            return doTickingRequest(controller, ticksSinceLastCall);
        } finally {
            if (controller != null) {
                controller.recordPerformanceSample(System.nanoTime() - startNanos);
            }
        }
    }

    private TickRateModulation doTickingRequest(ECOCraftingSystemBlockEntity controller, int ticksSinceLastCall) {
        if (controller != null) {
            int powerMultiply = 1;
            if (controller.isOverclocked() && !controller.isActiveCooling()) {
                powerMultiply = controller.getTier().getOverclockedCrafterPowerMultiply();
            }
            int overlockTimes = controller.getEffectiveOverclockTimes();
            int bonusValue = Math.min(10 + overlockTimes * 10, 100);

            // ── Phase 1 aggregation: extract AE power once for all threads ──
            double totalSafePower = 0.0;
            for (ECOCraftingThread thread : craftingThreads) {
                totalSafePower += thread.computePowerNeed(ticksSinceLastCall, bonusValue, powerMultiply);
            }

            double totalExtracted = 0.0;
            if (totalSafePower > 0.0) {
                IGrid grid = getMainNode().getGrid();
                if (grid != null) {
                    totalExtracted = grid.getEnergyService()
                            .extractAEPower(totalSafePower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                }
            }
            double powerRatio = totalSafePower > 0.0 ? totalExtracted / totalSafePower : 0.0;
            // ── End aggregation ──

            TickRateModulation rate = TickRateModulation.IDLE;
            for (ECOCraftingThread thread : craftingThreads) {
                double threadNeed = thread.computePowerNeed(ticksSinceLastCall, bonusValue, powerMultiply);
                double allocatedPower = threadNeed * powerRatio;
                TickRateModulation r =
                        thread.tickAggregated(overlockTimes, powerMultiply, ticksSinceLastCall, allocatedPower);
                if (r.ordinal() > rate.ordinal()) {
                    rate = r;
                }
            }
            flushCompletedOutputs();
            return rate;
        } else {
            return TickRateModulation.IDLE;
        }
    }

    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table) {
        return pushPattern(pattern, table, null);
    }

    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table, UUID craftingJobId) {
        return pushPattern(ECOExtractedPatternExecution.slow(pattern, table), craftingJobId);
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, UUID craftingJobId) {
        if (cluster == null || cluster.getController() == null) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        if (getRunningThreads() >= controller.getThreadCountPerWorker()) {
            fastPathCache.recordNoThreadReject();
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
                if (thread.pushPattern(execution, controller, craftingJobId)) {
                    nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                    return true;
                }
                // Continue trying other free threads.
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
        return thread.pushPattern(execution, controller, craftingJobId);
    }

    public boolean pushBatch(ECOBatchCraftingRequest request) {
        if (cluster == null || cluster.getController() == null) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        int controllerAvailableSlots = controller.getCurrentBatchSlots();
        if (!ECOFastPathLimits.canAcceptBatch(
                request.batchSize(), getAvailableThreadSlots(), controllerAvailableSlots)) {
            fastPathCache.recordNoThreadReject();
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
                if (thread.pushBatch(request, controller)) {
                    nextFreeThreadIndex = (index + 1) % Math.max(1, craftingThreads.size());
                    return true;
                }
                // Continue trying other free threads.
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
        return thread.pushBatch(request, controller);
    }

    public ECOFastPathResult getVerifiedFastPathResult(ECOExtractedPatternExecution execution) {
        var key = execution.key();
        if (key == null) {
            fastPathCache.recordKeyBuildFailed();
            return null;
        }
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        ECOFastPathResult result = fastPathCache.get(key, tick);
        if (result == null) {
            return null;
        }
        if (result.isNegative()) {
            fastPathCache.recordFallbackSlowPath();
            return null;
        }
        if (!result.matchesExecution(execution)) {
            fastPathCache.recordExpectedMismatch();
            return null;
        }
        return result;
    }

    public ECOCraftingFastPathCache getFastPathCache() {
        return cluster == null ? fastPathCache : cluster.getFastPathCache();
    }

    public boolean isBusy() {
        return getAvailableThreadSlots() <= 0;
    }

    public int getAvailableThreadSlots() {
        if (cluster == null || cluster.getController() == null) {
            return 0;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        return Math.max(0, controller.getThreadCountPerWorker() - getRunningThreads());
    }

    public List<ECOCraftingThread.Snapshot> getThreadSnapshots() {
        List<ECOCraftingThread.Snapshot> snapshots = new ArrayList<>();
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.isFree()) {
                snapshots.add(thread.createSnapshot());
            }
        }
        return List.copyOf(snapshots);
    }

    /**
     * Returns the output ItemStack of the first active (non-free) craft thread,
     * or {@link ItemStack#EMPTY} if this worker is idle.
     */
    public ItemStack getActiveCraftOutput() {
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.isFree()) {
                ECOCraftingThread.Snapshot snapshot = thread.createSnapshot();
                return snapshot.outputItem().copy();
            }
        }
        return ItemStack.EMPTY;
    }

    public ThreadProgressSummary getThreadProgressSummary() {
        int busyThreadCount = 0;
        int occupiedSlots = 0;
        int maxProgress = 0;
        long weightedProgress = 0L;
        for (ECOCraftingThread thread : craftingThreads) {
            if (thread.isFree()) {
                continue;
            }
            int slots = Math.max(1, thread.getOccupiedThreadSlots());
            busyThreadCount++;
            occupiedSlots += slots;
            maxProgress = Math.max(maxProgress, thread.getProgress());
            weightedProgress += (long) thread.getProgress() * slots;
        }
        int averageProgress = occupiedSlots <= 0 ? 0 : Math.round((float) weightedProgress / occupiedSlots);
        return new ThreadProgressSummary(busyThreadCount, occupiedSlots, maxProgress, averageProgress);
    }

    public void onThreadWork(int occupiedThreadSlots) {
        int slots = Math.max(1, occupiedThreadSlots);
        runningThreads += slots;
        if (cluster != null && cluster.getController() != null) {
            cluster.getController().onWorkerThreadCountChanged(slots);
        }
        setChanged();
        wakeTickingDevice();
    }

    @Override
    public void setChanged() {
        if (this.level != null) {
            level.blockEntityChanged(getBlockPos());
        }
    }

    public void onThreadStop(int occupiedThreadSlots) {
        int slots = Math.max(1, occupiedThreadSlots);
        runningThreads -= slots;
        if (runningThreads < 0) {
            LOGGER.warn(
                    "ECO worker runningThreads underflow: worker={} releasedSlots={} correctedToZero=true",
                    getBlockPos(),
                    slots);
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

    public boolean recoverUnfinishedJobInputsToNetwork(UUID craftingJobId, MEStorage storage) {
        boolean recoveredAll = true;
        for (ECOCraftingThread thread : craftingThreads) {
            if (thread.belongsToJob(craftingJobId) && !thread.recoverUnfinishedInputsToNetwork(storage)) {
                recoveredAll = false;
            }
        }
        if (recoveredAll) {
            wakeTickingDevice();
        }
        return recoveredAll;
    }

    public boolean recoverOrphanedWorkToNetwork(Set<UUID> activeJobIds, MEStorage storage) {
        boolean recoveredAll = true;
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.recoverOrphanedWorkToNetwork(activeJobIds, storage)) {
                recoveredAll = false;
            }
        }
        if (recoveredAll) {
            wakeTickingDevice();
        }
        return recoveredAll;
    }

    public boolean recoverAllToNetwork(MEStorage storage) {
        boolean recoveredAll = true;
        for (ECOCraftingThread thread : craftingThreads) {
            if (!thread.isFree() && !thread.recoverInputsToNetwork(storage)) {
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

    private void flushCompletedOutputs() {
        // ── Collect per-thread outputs (keep separate to avoid starvation) ──
        List<ECOCraftingThread> readyThreads = new ArrayList<>();
        List<KeyCounter> perThreadOutputs = new ArrayList<>();
        KeyCounter combinedOutputs = new KeyCounter();
        for (ECOCraftingThread thread : craftingThreads) {
            if (thread.isOutputReady()) {
                KeyCounter threadOutput = thread.collectOutputItems();
                readyThreads.add(thread);
                perThreadOutputs.add(threadOutput);
                for (Object2LongMap.Entry<AEKey> entry : threadOutput) {
                    combinedOutputs.add(entry.getKey(), entry.getLongValue());
                }
            }
        }
        if (readyThreads.isEmpty()) {
            return;
        }

        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }

        // ── Batch-insert all outputs into AE2 storage once ──
        CraftingService craftingService = (CraftingService) grid.getCraftingService();
        MEStorage storage = grid.getStorageService().getInventory();
        KeyCounter totalAcceptedOutputs = new KeyCounter();
        for (Object2LongMap.Entry<AEKey> entry : combinedOutputs) {
            AEKey key = entry.getKey();
            long requested = entry.getLongValue();
            long accepted = craftingService.insertIntoCpus(key, requested, Actionable.MODULATE);
            if (accepted < requested) {
                accepted += storage.insert(key, requested - accepted, Actionable.MODULATE, actionSource);
            }
            if (accepted > 0) {
                totalAcceptedOutputs.add(key, accepted);
            }
        }

        // ── Fair proportional allocation across threads ──
        // Track remainder per key to distribute rounding leftovers
        java.util.Map<AEKey, Long> remainingAccepted = new java.util.HashMap<>();
        for (Object2LongMap.Entry<AEKey> entry : totalAcceptedOutputs) {
            remainingAccepted.put(entry.getKey(), entry.getLongValue());
        }

        for (int i = 0; i < readyThreads.size(); i++) {
            ECOCraftingThread thread = readyThreads.get(i);
            KeyCounter threadOutput = perThreadOutputs.get(i);
            KeyCounter allocation = new KeyCounter();

            for (Object2LongMap.Entry<AEKey> entry : threadOutput) {
                AEKey key = entry.getKey();
                long threadAmount = entry.getLongValue();
                long totalForKey = combinedOutputs.get(key);
                long acceptedForKey = totalAcceptedOutputs.get(key);

                if (totalForKey <= 0 || acceptedForKey <= 0 || threadAmount <= 0) {
                    continue;
                }

                long fairShare;
                if (totalForKey <= acceptedForKey) {
                    // All output for this key was accepted — allocate fully
                    fairShare = threadAmount;
                } else {
                    // Proportional: threadAmount * acceptedForKey / totalForKey (floor)
                    fairShare = threadAmount * acceptedForKey / totalForKey;
                }

                // Clamp to remaining accepted for this key (handles rounding leftovers)
                long remaining = remainingAccepted.getOrDefault(key, 0L);
                if (fairShare > remaining) {
                    fairShare = remaining;
                }

                if (fairShare > 0) {
                    allocation.add(key, fairShare);
                    remainingAccepted.put(key, remaining - fairShare);
                }
            }

            thread.applyOutputFlush(allocation);
        }

        // ── Distribute rounding leftovers ──
        // Integer division in fairShare can leave a remainder that was
        // accepted by AE2 but not allocated to any thread.  Find a thread
        // that still has outputsReady for each leftover key and give it there.
        for (java.util.Map.Entry<AEKey, Long> entry : remainingAccepted.entrySet()) {
            long leftover = entry.getValue();
            if (leftover <= 0) continue;
            AEKey key = entry.getKey();
            for (int i = 0; i < readyThreads.size(); i++) {
                ECOCraftingThread thread = readyThreads.get(i);
                if (!thread.isOutputReady()) continue;
                if (perThreadOutputs.get(i).get(key) > 0) {
                    KeyCounter topUp = new KeyCounter();
                    topUp.add(key, leftover);
                    thread.applyOutputFlush(topUp);
                    break;
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        ListTag threads = new ListTag();
        for (ECOCraftingThread thread : craftingThreads) {
            threads.add(thread.serializeNBT());
        }
        data.put("craftingThreads", threads);
        data.putInt("runningThreads", runningThreads);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        ListTag threads = data.getList("craftingThreads", Tag.TAG_COMPOUND);
        craftingThreads.clear();
        fastPathCache.clear();
        int busyThreads = 0;
        for (int i = 0; i < threads.size(); i++) {
            ECOCraftingThread thread = new ECOCraftingThread(this);
            thread.deserializeNBT(threads.getCompound(i));
            craftingThreads.add(thread);
            if (!thread.isFree()) {
                busyThreads += thread.getOccupiedThreadSlots();
            }
        }
        runningThreads = busyThreads;
        nextFreeThreadIndex = 0;
    }

    public boolean isWorking() {
        return runningThreads > 0;
    }

    public record ThreadProgressSummary(int busyThreadCount, int occupiedSlots, int maxProgress, int averageProgress) {}

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ECOCraftingThread thread : craftingThreads) {
            thread.dropRecoverablesAndClear(drops);
        }
    }
}
