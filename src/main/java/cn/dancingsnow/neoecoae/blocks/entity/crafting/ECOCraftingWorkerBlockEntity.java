package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathResult;
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
    private static final int MAX_PERSISTED_THREAD_RECORDS = ECOBatchCraftingHelper.MAX_BATCH_SIZE;

    private final List<ECOCraftingThread> craftingThreads = new ArrayList<>();
    private final ECOCraftingFastPathCache fastPathCache = new ECOCraftingFastPathCache();

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
                if (controller.isOverclocked() && !controller.isActiveCooling()) {
                    powerMultiply = controller.getTier().getOverclockedCrafterPowerMultiply();
                }
                int overlockTimes = controller.getEffectiveOverclockTimes();
                TickRateModulation rate = TickRateModulation.IDLE;
                for (ECOCraftingThread thread : craftingThreads) {
                    TickRateModulation r = thread.tick(overlockTimes, powerMultiply, ticksSinceLastCall);
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
        } else {
            return false;
        }
    }

    public boolean pushBatch(ECOBatchCraftingRequest request) {
        if (!NEConfig.ecoAe2FastPathEnabled || NEConfig.postCraftingEvent) {
            fastPathCache.recordDisabled();
            return false;
        }
        if (cluster == null || cluster.getController() == null) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        if (request.batchSize() > getAvailableThreadSlots()
            || request.batchSize() > getControllerAvailableThreadSlots(controller)) {
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
        return fastPathCache;
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
        return Math.max(0, controller.getThreadCount() - controller.getRunningThreadCount());
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
        fastPathCache.clear();
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
        runningThreads = (int) Math.min(ECOBatchCraftingHelper.MAX_BATCH_SIZE, busyThreads);
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
