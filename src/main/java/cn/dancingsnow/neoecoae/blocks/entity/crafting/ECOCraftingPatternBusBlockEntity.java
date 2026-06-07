package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
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
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathLimits;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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

public class ECOCraftingPatternBusBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingPatternBusBlockEntity>
        implements InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage {

    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;
    public static final int SLOTS_PER_PAGE = ROW_SIZE * COL_SIZE;
    private static final String NBT_PATTERN_INVENTORY = "patternInventory";
    private static final String NBT_PATTERN_INVENTORY_PAGES = "patternInventoryPages";

    private final AppEngInternalInventory inventory;
    private final InternalInventory effectiveInventory = new EffectivePatternInventory();
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    public final IItemHandlerModifiable itemHandler;
    private final LazyOptional<IItemHandlerModifiable> itemHandlerCap;
    private int nextWorkerIndex = 0;
    private int activePages = NEConfig.getCraftingPatternBusPages();

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
            int requestedBatchSize) {
        if (cluster == null || requestedBatchSize <= 0) {
            return null;
        }
        ECOCraftingSystemBlockEntity controller = getCraftingController();
        if (controller == null) {
            return null;
        }
        int controllerAvailableSlots = controller.getCurrentBatchSlots();
        if (controllerAvailableSlots <= 0) {
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
            int maxBatchSize = ECOFastPathLimits.limitBatchSize(requestedBatchSize, availableSlots,
                    controllerAvailableSlots);
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
        long workerRemaining = cluster.getWorkers().stream()
                .mapToLong(ECOCraftingWorkerBlockEntity::getAvailableThreadSlots)
                .sum();
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.min(controller.getCurrentBatchSlots(), workerRemaining));
    }

    public record BatchFastPathOffer(ECOCraftingWorkerBlockEntity worker, ECOFastPathResult result, int maxBatchSize) {
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
        return effectiveInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (cluster != null && cluster.getController() != null) {
            var block = cluster.getController().getBlockState().getBlock();
            if (block != Blocks.AIR) {
                return new PatternContainerGroup(AEItemKey.of(block.asItem()), block.getName(), List.of());
            }
        }
        return new PatternContainerGroup(
                AEItemKey.of(NEBlocks.CRAFTING_PATTERN_BUS.asStack()),
                NEBlocks.CRAFTING_PATTERN_BUS.get().getName(),
                List.of());
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        ItemStack result = effectiveInventory.addItems(itemStack.copy());
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
        this.inventory = new AppEngInternalInventory(this, NEConfig.getMaxCraftingPatternBusSlotCount());
        this.inventory.setFilter(new AEEncodedPatternFilter());
        this.itemHandler = (IItemHandlerModifiable) effectiveInventory.toItemHandler();
        this.itemHandlerCap = LazyOptional.of(() -> this.itemHandler);
        this.getMainNode().addService(ICraftingProvider.class, this).addService(IECOPatternStorage.class, this);
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
        for (ItemStack itemStack : this.effectiveInventory) {
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
        IntStream.range(0, inventory.size())
                .mapToObj(inventory::getStackInSlot)
                .filter(s -> !s.isEmpty())
                .forEach(drops::add);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        inventory.writeToNBT(data, NBT_PATTERN_INVENTORY);
        data.putInt(NBT_PATTERN_INVENTORY_PAGES, activePages);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        inventory.clear();
        inventory.readFromNBT(data, NBT_PATTERN_INVENTORY);
        int savedPages = data.contains(NBT_PATTERN_INVENTORY_PAGES) ? data.getInt(NBT_PATTERN_INVENTORY_PAGES) : 1;
        activePages = clampPages(
                Math.max(NEConfig.getCraftingPatternBusPages(), Math.max(savedPages, getHighestOccupiedPage())));
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

    public int getPageCount() {
        return activePages;
    }

    public int getPatternSlotCount() {
        return activePages * SLOTS_PER_PAGE;
    }

    private int getHighestOccupiedPage() {
        int highestSlot = -1;
        for (int slot = inventory.size() - 1; slot >= 0; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                highestSlot = slot;
                break;
            }
        }
        return highestSlot < 0 ? 1 : highestSlot / SLOTS_PER_PAGE + 1;
    }

    private static int clampPages(int pages) {
        return Math.max(NEConfig.PATTERN_BUS_MIN_PAGES, Math.min(NEConfig.PATTERN_BUS_MAX_PAGES, pages));
    }

    private final class EffectivePatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            return getPatternSlotCount();
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            inventory.setItemDirect(slot, stack);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < size() && inventory.isItemValid(slot, stack);
        }
    }
}
