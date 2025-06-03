package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
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
        return new TickingRequest(1, 10, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            int powerMultiply = 1;
            if (controller.isOverclocked() && !controller.isActiveCooling()) {
                powerMultiply = controller.getTier().getOverclockedCrafterPowerMultiply();
            }
            if (controller.isActiveCooling() && controller.canConsumeCoolant(5)) {
                controller.consumeCoolant(5);
            } else {
                return TickRateModulation.IDLE;
            }
            int overlockTimes = controller.getOverlockTimes();
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
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            AtomicBoolean pushed = new AtomicBoolean(false);
            craftingThreads.stream().filter(ECOCraftingThread::isFree).forEach(t -> {
                if (pushed.get()) {
                    return;
                }
                if (t.pushPattern(pattern, table)) {
                    pushed.set(true);
                }
            });
            if (!pushed.get()) {
                if (craftingThreads.size() < controller.getThreadCountPerWorker()) {
                    ECOCraftingThread thread = new ECOCraftingThread(this);
                    craftingThreads.add(thread);
                    setChanged();
                    markForUpdate();
                    return thread.pushPattern(pattern, table);
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
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (getRunningThreads() >= controller.getThreadCountPerWorker()) {
                return true;
            }
            if (craftingThreads.stream().anyMatch(ECOCraftingThread::isFree)) {
                return false;
            }
            if (craftingThreads.size() < controller.getThreadCountPerWorker()) {
                ECOCraftingThread thread = new ECOCraftingThread(this);
                craftingThreads.add(thread);
                setChanged();
                markForUpdate();
                return false;
            }
        }
        return true;
    }

    public void onThreadWork() {
        runningThreads++;
        setChanged();
    }

    public void onThreadStop() {
        runningThreads--;
        setChanged();
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
        for (int i = 0; i < threads.size(); i++) {
            ECOCraftingThread thread = new ECOCraftingThread(this);
            thread.deserializeNBT(registries, threads.getCompound(i));
            craftingThreads.add(thread);
        }
        runningThreads = data.getInt("runningThreads");
    }
}
