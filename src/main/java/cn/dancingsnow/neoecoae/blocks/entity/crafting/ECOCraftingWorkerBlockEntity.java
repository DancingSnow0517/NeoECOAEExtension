package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import lombok.Getter;
import net.minecraft.core.BlockPos;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class ECOCraftingWorkerBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingWorkerBlockEntity>
    implements IGridTickable {

    private final List<ECOCraftingThread> craftingThreads = new ArrayList<>();

    @Getter
    private int runningThreads = 0;

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
        return new TickingRequest(1, 10, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
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
        } else {
            return TickRateModulation.IDLE;
        }
    }

    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table) {
        return pushPattern(pattern, table, null);
    }

    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table, UUID craftingJobId) {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (getRunningThreads() >= controller.getThreadCountPerWorker()) {
                return false;
            }
            AtomicBoolean pushed = new AtomicBoolean(false);
            for (ECOCraftingThread t : craftingThreads) {
                if (t.isFree()) {
                    if (pushed.get()) {
                        continue;
                    }
                    if (t.pushPattern(pattern, table, controller, craftingJobId)) {
                        pushed.set(true);
                    }
                }
            }
            if (!pushed.get()) {
                if (craftingThreads.size() < controller.getThreadCountPerWorker()) {
                    ECOCraftingThread thread = new ECOCraftingThread(this);
                    craftingThreads.add(thread);
                    setChanged();
                    markForUpdate();
                    return thread.pushPattern(pattern, table, controller, craftingJobId);
                } else {
                    return false;
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
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

    public void onThreadWork() {
        runningThreads++;
        if (cluster != null && cluster.getController() != null) {
            cluster.getController().onWorkerThreadCountChanged(1);
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

    public void onThreadStop() {
        runningThreads--;
        if (runningThreads < 0) {
            runningThreads = 0;
        }
        if (cluster != null && cluster.getController() != null) {
            cluster.getController().onWorkerThreadCountChanged(-1);
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
        int busyThreads = 0;
        for (int i = 0; i < threads.size(); i++) {
            ECOCraftingThread thread = new ECOCraftingThread(this);
            thread.deserializeNBT(threads.getCompound(i));
            craftingThreads.add(thread);
            if (!thread.isFree()) {
                busyThreads++;
            }
        }
        runningThreads = busyThreads;
    }

    public boolean isWorking() {
        return runningThreads > 0;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ECOCraftingThread thread : craftingThreads) {
            thread.addRecoverableDrops(drops);
        }
    }
}
