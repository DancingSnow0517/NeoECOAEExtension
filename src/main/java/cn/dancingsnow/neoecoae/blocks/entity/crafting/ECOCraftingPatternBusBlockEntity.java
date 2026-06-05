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
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class ECOCraftingPatternBusBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingPatternBusBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;

    @Persisted
    @DescSynced
    private final AppEngInternalInventory inventory;
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    public final IItemHandlerModifiable itemHandler;
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
        ECOFastPathKey key,
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
        int globalAvailableSlots = getAvailableThreadSlots();
        if (globalAvailableSlots <= 0) {
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
            int maxBatchSize = Math.min(requestedBatchSize, Math.min(availableSlots, globalAvailableSlots));
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

    public record BatchFastPathOffer(ECOCraftingWorkerBlockEntity worker, ECOFastPathResult result, int maxBatchSize) {}

    @Override
    public boolean isBusy() {
        if (getAvailableThreadSlots() <= 0) {
            return true;
        }
        if (cluster == null) {
            return true;
        }
        for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
            if (worker.getAvailableThreadSlots() > 0 || !worker.isBusy()) {
                return false;
            }
        }
        return true;
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
        this.getMainNode().addService(ICraftingProvider.class, this)
            .addService(IECOPatternStorage.class, this);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
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

    @Override
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

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .paddingAll(4)
            .gapAll(2)
            .justifyContent(AlignContent.CENTER)
        ).addClass("panel_bg");

        root.addChild(new TextElement()
            .setText(Component.translatable("block.neoecoae.crafting_pattern_bus"))
            .textStyle(textStyle -> textStyle
                .textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true)));

        UIElement patternInv = new UIElement().addClass("panel_border");
        for (int row = 0; row < COL_SIZE; row++) {
            UIElement rowInv = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int col = 0; col < ROW_SIZE; col++) {
                int slotIndex = row * ROW_SIZE + col;
                UIElement slot = new PatternItemSlot(new ItemHandlerSlot(itemHandler, slotIndex))
                    .slotStyle(slotStyle -> slotStyle.slotOverlay(NETextures.PATTERN_OVERLAY));
                rowInv.addChild(slot);
            }
            patternInv.addChild(rowInv);
        }
        root.addChild(patternInv);
        root.addChild(new InventorySlots().layout(layout -> layout.marginTop(5)));
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
}
