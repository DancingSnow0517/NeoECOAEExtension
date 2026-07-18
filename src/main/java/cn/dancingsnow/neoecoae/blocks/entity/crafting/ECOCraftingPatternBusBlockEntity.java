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
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class ECOCraftingPatternBusBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingPatternBusBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;
    private static final int PAGE_BUTTON_SIZE = 16;
    private static final int PAGE_CONTROL_GAP = 4;
    private static final int HEADER_HEIGHT = 36;
    private static final int SINGLE_PAGE_HEADER_HEIGHT = 16;
    private static final int HEADER_TITLE_TOP = 2;
    private static final int PAGE_TOP_MARGIN = 19;
    private static final int PAGE_RIGHT_MARGIN = 2;
    private static final int PAGE_CONTROLS_OFFSET_X = 1;
    private static final int PAGE_LABEL_WIDTH = 16;
    private static final int UI_CONTENT_WIDTH = ROW_SIZE * 18;
    private static final int PAGE_CONTROLS_WIDTH = PAGE_BUTTON_SIZE * 2 + PAGE_CONTROL_GAP * 2 + PAGE_LABEL_WIDTH;
    private static final int PATTERN_UPDATE_QUIET_TICKS = 2;
    public static final int SLOTS_PER_PAGE = ROW_SIZE * COL_SIZE;

    @Persisted
    @DescSynced
    private final AppEngInternalInventory inventory;
    private final InternalInventory effectiveInventory = new EffectivePatternInventory();
    private final IItemHandlerModifiable pageItemHandler = new PagedPatternItemHandler();
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    private final IPatternDetails[] decodedPatternDetails =
        new IPatternDetails[NEConfig.getMaxCraftingPatternBusSlotCount()];
    private final BitSet dirtyPatternSlots = new BitSet(NEConfig.getMaxCraftingPatternBusSlotCount());
    public final IItemHandlerModifiable itemHandler;
    @Persisted
    @DescSynced
    private int activePages = NEConfig.getCraftingPatternBusPages();
    @DescSynced
    private int currentPage;
    private int nextWorkerIndex = 0;
    private boolean patternDetailsUpdateQueued;
    private boolean rebuildAllPatternDetails = true;
    private int patternDetailsUpdateTick;

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
        BatchFastPathOffer offer = findBatchFastPathOffer(
            request.key(), null, request, request.batchSize()
        );
        return pushBatch(request, offer);
    }

    public boolean pushBatch(ECOBatchCraftingRequest request, @Nullable BatchFastPathOffer offer) {
        if (offer == null
            || cluster == null
            || !cluster.getWorkers().contains(offer.worker())
            || offer.maxBatchSize() < request.batchSize()
            || offer.worker().getAvailableThreadSlots() < request.batchSize()
            || getAvailableThreadSlots() < request.batchSize()
            || !offer.result().matchesBatchRequest(request)) {
            return false;
        }
        int nextIndex = nextWorkerIndexAfter(offer.worker());
        // Reuse the result already verified while selecting the offer. Crafting runs on the
        // server thread, so looking the same key up again in the Worker cannot make it safer.
        if (offer.worker().pushBatch(request, offer.result())) {
            nextWorkerIndex = nextIndex;
            return true;
        }
        return false;
    }

    @Nullable
    public BatchFastPathOffer findBatchFastPathOffer(ECOExtractedPatternExecution execution, int requestedBatchSize) {
        if (execution.key() == null) {
            return null;
        }
        return findBatchFastPathOffer(execution.key(), execution, null, requestedBatchSize);
    }

    @Nullable
    private BatchFastPathOffer findBatchFastPathOffer(
        ECOFastPathKey key,
        @Nullable ECOExtractedPatternExecution execution,
        @Nullable ECOBatchCraftingRequest request,
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
        BatchFastPathOffer bestOffer = null;
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
            if (request != null && !result.matchesBatchRequest(request)) {
                worker.getFastPathCache().recordExpectedMismatch();
                continue;
            }
            int maxBatchSize = calculateBatchOfferSize(requestedBatchSize, availableSlots, globalAvailableSlots);
            if (maxBatchSize > 0 && (bestOffer == null || maxBatchSize > bestOffer.maxBatchSize())) {
                bestOffer = new BatchFastPathOffer(worker, result, maxBatchSize);
                if (maxBatchSize >= requestedBatchSize) {
                    break;
                }
            }
        }
        return bestOffer;
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

    static int calculateBatchOfferSize(int requestedBatchSize, int workerAvailableSlots, int hostAvailableSlots) {
        return Math.max(0, Math.min(requestedBatchSize, Math.min(workerAvailableSlots, hostAvailableSlots)));
    }

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
        return effectiveInventory;
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
        // ECO Workers only execute molecular-assembler crafting patterns. Reject processing patterns before they can
        // be advertised to a crafting CPU, which would otherwise extract and later reinject their inputs.
        if (!isExecutablePattern(itemStack)) {
            return false;
        }
        if (containsPatternInCluster(itemStack)) {
            return false;
        }
        ItemStack result = effectiveInventory.addItems(itemStack.copy());
        return result.isEmpty();
    }

    private boolean containsPatternInCluster(ItemStack pattern) {
        if (cluster == null) {
            return containsPattern(pattern);
        }
        for (ECOCraftingPatternBusBlockEntity patternBus : cluster.getPatternBuses()) {
            if (patternBus.containsPattern(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPattern(ItemStack pattern) {
        for (ItemStack storedPattern : effectiveInventory) {
            if (ItemStack.isSameItemSameComponents(storedPattern, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExecutablePattern(ItemStack stack) {
        return PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern;
    }

    class AEEncodedPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return slot >= 0
                && slot < getPatternSlotCount()
                && isExecutablePattern(stack);
        }
    }

    public ECOCraftingPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.inventory = new AppEngInternalInventory(this, NEConfig.getMaxCraftingPatternBusSlotCount());
        this.inventory.setFilter(new AEEncodedPatternFilter());
        this.itemHandler = (IItemHandlerModifiable) effectiveInventory.toItemHandler();
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
        if (slot >= 0 && slot < decodedPatternDetails.length) {
            dirtyPatternSlots.set(slot);
        } else {
            rebuildAllPatternDetails = true;
        }
        queuePatternDetailsUpdate();
    }

    @Override
    public void onReady() {
        super.onReady();
        rebuildAllPatternDetails = true;
        updatePatternDetails();
    }

    private void updatePatternDetails() {
        int slotCount = getPatternSlotCount();
        if (rebuildAllPatternDetails) {
            Arrays.fill(decodedPatternDetails, null);
            for (int slot = 0; slot < slotCount; slot++) {
                decodedPatternDetails[slot] = PatternDetailsHelper.decodePattern(
                    inventory.getStackInSlot(slot), level
                );
            }
        } else {
            for (int slot = dirtyPatternSlots.nextSetBit(0);
                 slot >= 0;
                 slot = dirtyPatternSlots.nextSetBit(slot + 1)) {
                decodedPatternDetails[slot] = PatternDetailsHelper.decodePattern(
                    inventory.getStackInSlot(slot), level
                );
            }
        }
        rebuildAllPatternDetails = false;
        dirtyPatternSlots.clear();

        patternDetails.clear();
        for (int slot = 0; slot < slotCount; slot++) {
            IPatternDetails details = decodedPatternDetails[slot];
            // Old saves and external inventory APIs may bypass the slot filter. Never publish such processing
            // patterns as executable providers, even if their encoded item remains stored for manual removal.
            if (details instanceof IMolecularAssemblerSupportedPattern) {
                patternDetails.add(details);
            }
        }
        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    private void queuePatternDetailsUpdate() {
        if (!(level instanceof ServerLevel serverLevel)) {
            updatePatternDetails();
            return;
        }
        patternDetailsUpdateTick = serverLevel.getServer().getTickCount() + PATTERN_UPDATE_QUIET_TICKS;
        if (patternDetailsUpdateQueued) {
            return;
        }
        patternDetailsUpdateQueued = true;
        schedulePatternDetailsUpdate(serverLevel, patternDetailsUpdateTick);
    }

    private void schedulePatternDetailsUpdate(ServerLevel serverLevel, int tick) {
        serverLevel.getServer().tell(new TickTask(tick, () -> {
            if (isRemoved() || level != serverLevel) {
                patternDetailsUpdateQueued = false;
                return;
            }
            if (serverLevel.getServer().getTickCount() < patternDetailsUpdateTick) {
                schedulePatternDetailsUpdate(serverLevel, patternDetailsUpdateTick);
                return;
            }
            patternDetailsUpdateQueued = false;
            updatePatternDetails();
        }));
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
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

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .paddingAll(4)
            .paddingBottom(6)
            .gapAll(2)
            .width(UI_CONTENT_WIDTH + 8)
            .justifyContent(AlignContent.CENTER)
        ).addClass("panel_bg");

        root.addChild(headerRow());

        UIElement patternInv = new UIElement().addClass("panel_border");
        for (int row = 0; row < COL_SIZE; row++) {
            UIElement rowInv = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int col = 0; col < ROW_SIZE; col++) {
                int slotIndex = row * ROW_SIZE + col;
                UIElement slot = new PatternItemSlot(new ItemHandlerSlot(pageItemHandler, slotIndex))
                    .slotStyle(slotStyle -> slotStyle.slotOverlay(NETextures.PATTERN_OVERLAY));
                rowInv.addChild(slot);
            }
            patternInv.addChild(rowInv);
        }
        root.addChild(patternInv);
        root.addChild(new InventorySlots().layout(layout -> layout.marginTop(5)));
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private UIElement headerRow() {
        boolean showPageControls = getPageCount() > 1;
        UIElement row = new UIElement().layout(layout -> {
            layout.width(UI_CONTENT_WIDTH);
            layout.height(showPageControls ? HEADER_HEIGHT : SINGLE_PAGE_HEADER_HEIGHT);
        });
        row.addChild(new TextElement()
            .setText(Component.translatable("block.neoecoae.crafting_pattern_bus"))
            .textStyle(textStyle -> textStyle
                .textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true))
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(HEADER_TITLE_TOP);
                layout.width(UI_CONTENT_WIDTH);
                layout.height(12);
            }));
        if (showPageControls) {
            row.addChild(pageControls());
        }
        return row;
    }

    private UIElement pageControls() {
        UIElement controls = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(UI_CONTENT_WIDTH - PAGE_RIGHT_MARGIN - PAGE_CONTROLS_WIDTH + PAGE_CONTROLS_OFFSET_X);
            layout.top(PAGE_TOP_MARGIN);
            layout.width(PAGE_CONTROLS_WIDTH);
            layout.height(PAGE_BUTTON_SIZE);
        });
        controls.addChild(pageButton("<", () -> changePage(currentPage - 1)).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(PAGE_BUTTON_SIZE);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        controls.addChild(new PageNumberElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP);
            layout.top(0);
            layout.width(PAGE_LABEL_WIDTH);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        controls.addChild(pageButton(">", () -> changePage(currentPage + 1)).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP + PAGE_LABEL_WIDTH + PAGE_CONTROL_GAP);
            layout.top(0);
            layout.width(PAGE_BUTTON_SIZE);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        return controls;
    }

    private Button pageButton(String text, Runnable action) {
        Button button = new Button().setText(text);
        button.setOnServerClick(event -> action.run());
        return button;
    }

    private final class PageNumberElement extends UIElement implements IBindable<Integer> {
        private int syncedPageCount = getPageCount();

        private PageNumberElement() {
            bind(DataBindingBuilder.intValS2C(ECOCraftingPatternBusBlockEntity.this::getPageCount).build());
        }

        @Override
        public IDataSource<Integer> setValue(@Nullable Integer value) {
            syncedPageCount = value == null ? getPageCount() : Math.max(1, value);
            return this;
        }

        @Override
        public Integer getValue() {
            return syncedPageCount;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            Font font = Minecraft.getInstance().font;
            Component text = Component.literal((currentPage + 1) + "/" + syncedPageCount);
            int x = (int)getPositionX() + Math.round((getSizeWidth() - font.width(text)) / 2.0F);
            int y = (int)getPositionY() + Math.round((getSizeHeight() - font.lineHeight) / 2.0F);
            guiContext.graphics.drawString(font, text, x, y, 0x3F3D52, false);
        }
    }

    public int getPageCount() {
        activePages = clampPages(Math.max(NEConfig.getCraftingPatternBusPages(), getHighestOccupiedPage()));
        currentPage = Math.clamp(currentPage, 0, activePages - 1);
        return activePages;
    }

    public int getPatternSlotCount() {
        return getPageCount() * SLOTS_PER_PAGE;
    }

    private void changePage(int targetPage) {
        int pageCount = getPageCount();
        int clamped = Math.clamp(targetPage, 0, pageCount - 1);
        if (clamped == currentPage) {
            return;
        }
        currentPage = clamped;
        setChanged();
        markForUpdate();
    }

    private int getHighestOccupiedPage() {
        for (int slot = inventory.size() - 1; slot >= 0; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return slot / SLOTS_PER_PAGE + 1;
            }
        }
        return 1;
    }

    private static int clampPages(int pages) {
        return Math.clamp(pages, NEConfig.PATTERN_BUS_MIN_PAGES, NEConfig.PATTERN_BUS_MAX_PAGES);
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
            return slot >= 0 && slot < size() ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (slot >= 0 && slot < size()) {
                inventory.setItemDirect(slot, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < size() && inventory.isItemValid(slot, stack);
        }
    }

    private final class PagedPatternItemHandler implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return SLOTS_PER_PAGE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? inventory.getStackInSlot(actualSlot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? itemHandler.insertItem(actualSlot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? itemHandler.extractItem(actualSlot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? itemHandler.getSlotLimit(actualSlot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 && itemHandler.isItemValid(actualSlot, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            int actualSlot = toActualSlot(slot);
            if (actualSlot >= 0) {
                itemHandler.setStackInSlot(actualSlot, stack);
            }
        }

        private int toActualSlot(int visibleSlot) {
            if (visibleSlot < 0 || visibleSlot >= SLOTS_PER_PAGE) {
                return -1;
            }
            int page = Math.clamp(currentPage, 0, getPageCount() - 1);
            int actualSlot = page * SLOTS_PER_PAGE + visibleSlot;
            return actualSlot < getPatternSlotCount() ? actualSlot : -1;
        }
    }
}
