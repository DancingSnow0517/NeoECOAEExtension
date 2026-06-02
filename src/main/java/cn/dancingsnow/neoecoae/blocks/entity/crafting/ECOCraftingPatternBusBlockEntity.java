package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathResult;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class ECOCraftingPatternBusBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingPatternBusBlockEntity>
    implements InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage {


    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;

    private final AppEngInternalInventory inventory;
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    public final IItemHandlerModifiable itemHandler;
    private final LazyOptional<IItemHandlerModifiable> itemHandlerCap;
    private int nextWorkerIndex = 0;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patternDetails;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushPattern(patternDetails, inputHolder, null);
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, @Nullable UUID craftingJobId) {
        return pushPattern(ECOExtractedPatternExecution.slow(patternDetails, inputHolder), craftingJobId);
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, @Nullable UUID craftingJobId) {
        if (execution.molecularPattern() == null) {
            return false;
        }
        if (cluster != null) {
            List<ECOCraftingWorkerBlockEntity> workers = cluster.getWorkers();
            if (workers.isEmpty()) {
                return false;
            }
            int start = Math.floorMod(nextWorkerIndex, workers.size());
            for (int offset = 0; offset < workers.size(); offset++) {
                int index = (start + offset) % workers.size();
                ECOCraftingWorkerBlockEntity worker = workers.get(index);
                if (worker.pushPattern(execution, craftingJobId)) {
                    nextWorkerIndex = (index + 1) % Math.max(1, workers.size());
                    return true;
                }
            }
        }
        return false;
    }

    public boolean pushBatch(ECOBatchCraftingRequest request) {
        BatchFastPathOffer offer = findBatchFastPathOffer(request.key(), null, request.batchSize());
        if (offer == null) {
            return false;
        }
        if (offer.worker().pushBatch(request)) {
            nextWorkerIndex = nextWorkerIndexAfter(offer.worker());
            return true;
        }
        return false;
    }

    @Nullable
    public BatchFastPathOffer findBatchFastPathOffer(ECOExtractedPatternExecution execution, int requestedBatchSize) {
        if (execution.key() == null) {
            return null;
        }
        return findBatchFastPathOffer(execution.key(), execution, requestedBatchSize);
    }

    @Nullable
    private BatchFastPathOffer findBatchFastPathOffer(
        cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathKey key,
        @Nullable ECOExtractedPatternExecution execution,
        int requestedBatchSize
    ) {
        if (cluster == null || requestedBatchSize <= 0) {
            return null;
        }
        List<ECOCraftingWorkerBlockEntity> workers = cluster.getWorkers();
        if (workers.isEmpty()) {
            return null;
        }
        int start = Math.floorMod(nextWorkerIndex, workers.size());
        for (int offset = 0; offset < workers.size(); offset++) {
            int index = (start + offset) % workers.size();
            ECOCraftingWorkerBlockEntity worker = workers.get(index);
            int availableSlots = worker.getAvailableThreadSlots();
            if (availableSlots <= 0) {
                continue;
            }
            ECOFastPathResult result = execution == null
                ? worker.getFastPathCache().peek(key)
                : worker.getVerifiedFastPathResult(execution);
            if (result == null || result.isNegative()) {
                continue;
            }
            int maxBatchSize = Math.min(requestedBatchSize, availableSlots);
            if (maxBatchSize > 0) {
                return new BatchFastPathOffer(worker, result, maxBatchSize);
            }
        }
        return null;
    }

    private int nextWorkerIndexAfter(ECOCraftingWorkerBlockEntity acceptedWorker) {
        if (cluster == null) {
            return nextWorkerIndex;
        }
        List<ECOCraftingWorkerBlockEntity> workers = cluster.getWorkers();
        int index = workers.indexOf(acceptedWorker);
        return index < 0 ? nextWorkerIndex : (index + 1) % Math.max(1, workers.size());
    }

    public boolean recoverJobToNetwork(UUID craftingJobId, appeng.api.storage.MEStorage storage) {
        if (cluster == null) {
            return false;
        }
        boolean recoveredAll = true;
        for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
            if (!worker.recoverJobToNetwork(craftingJobId, storage)) {
                recoveredAll = false;
            }
        }
        return recoveredAll;
    }

    @Override
    public boolean isBusy() {
        return getAvailableThreadSlots() <= 0;
    }

    public int getAvailableThreadSlots() {
        ECOCraftingSystemBlockEntity controller = getCraftingController();
        if (cluster == null || controller == null) {
            return 0;
        }
        long runningThreads = controller.getRunningThreadCount();
        long controllerRemaining = Math.max(0, controller.getThreadCount() - runningThreads);
        long workerRemaining = Math.max(
            0,
            (long) controller.getThreadCountPerWorker() * controller.getWorkerCount() - runningThreads
        );
        return (int) Math.min(Integer.MAX_VALUE, Math.min(controllerRemaining, workerRemaining));
    }

    public record BatchFastPathOffer(
        ECOCraftingWorkerBlockEntity worker,
        ECOFastPathResult result,
        int maxBatchSize
    ) {
    }

    @Nullable
    public ECOCraftingSystemBlockEntity getCraftingController() {
        if (cluster != null) {
            return cluster.getController();
        }
        return null;
    }

    @Override
    public @Nullable IGrid getGrid() {
        return getGridNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return inventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (cluster != null && cluster.getController() != null) {
            var block = cluster.getController().getBlockState().getBlock();
            if (block != Blocks.AIR) {
                return new PatternContainerGroup(
                    AEItemKey.of(block.asItem()),
                    block.getName(),
                    List.of()
                );
            }
        }
        return new PatternContainerGroup(
            AEItemKey.of(NEBlocks.CRAFTING_PATTERN_BUS.asStack()),
            NEBlocks.CRAFTING_PATTERN_BUS.get().getName(),
            List.of()
        );
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        ItemStack result = inventory.addItems(itemStack.copy());
        return result.isEmpty();
    }

    class AEEncodedPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern;
        }
    }

    public ECOCraftingPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.inventory = new AppEngInternalInventory(this, ROW_SIZE * COL_SIZE);
        this.inventory.setFilter(new AEEncodedPatternFilter());
        this.itemHandler = (IItemHandlerModifiable) inventory.toItemHandler();
        this.itemHandlerCap = LazyOptional.of(() -> this.itemHandler);
        this.getMainNode().addService(ICraftingProvider.class, this)
            .addService(IECOPatternStorage.class, this);
    }

    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        this.saveChanges();
        updatePatternDetails();
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePatternDetails();
    }

    private void updatePatternDetails() {
        patternDetails.clear();
        for (ItemStack itemStack : this.inventory) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(itemStack, this.level);
            if (details != null) {
                patternDetails.add(details);
            }
        }
        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markForUpdate();
            });
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        IntStream.range(0, ROW_SIZE * COL_SIZE)
            .mapToObj(inventory::getStackInSlot)
            .filter(s -> !s.isEmpty())
            .forEach(drops::add);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        inventory.writeToNBT(data, "patternInventory");
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        inventory.readFromNBT(data, "patternInventory");
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
    }
}
