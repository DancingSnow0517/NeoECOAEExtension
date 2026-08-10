package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.orientation.BlockOrientation;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageInterfaceUI;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class ECOMachineInterfaceBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, ECOMachineInterfaceBlockEntity<C>> implements ISyncPersistRPCBlockEntity {
    private static final int PATTERN_TRANSFER_MAX_SLOTS_PER_TICK = 24;
    private static final int PATTERN_TRANSFER_MAX_INSERTIONS_PER_TICK = 8;
    private static final long PATTERN_TRANSFER_MAX_NANOS_PER_TICK = 4_000_000L;
    private static final long PATTERN_TRANSFER_SYNC_INTERVAL_TICKS = 5L;
    private static final int PATTERN_PREVIEW_COLUMNS = 9;
    private static final int PATTERN_PREVIEW_ROWS = 5;
    private static final int PATTERN_PREVIEW_VISIBLE_SLOTS = PATTERN_PREVIEW_COLUMNS * PATTERN_PREVIEW_ROWS;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    @Persisted
    @DescSynced
    private ECOStorageInterfaceMode storageInterfaceMode = ECOStorageInterfaceMode.STORAGE;
    @DescSynced
    private long transferredLastTick;
    @DescSynced
    private int patternTransferInserted;
    @DescSynced
    private int patternTransferAlreadyPresent;
    @DescSynced
    private int patternTransferNoSpace;
    @DescSynced
    private int patternTransferNoTarget;
    @DescSynced
    private int patternTransferIncompatible;
    @DescSynced
    private boolean patternTransferUnavailable;
    @DescSynced
    private boolean patternTransferPerformed;
    @DescSynced
    private boolean patternTransferInProgress;
    @DescSynced
    private int patternTransferScannedSlots;
    @DescSynced
    private int patternTransferTotalSlots;
    @Nullable
    private PatternTransferTask patternTransferTask;
    private long lastPatternTransferSyncTick = Long.MIN_VALUE;
    @DescSynced
    private boolean patternPreviewScanning;
    @DescSynced
    private int patternPreviewScannedSlots;
    @DescSynced
    private int patternPreviewTotalSlots;
    @DescSynced
    private int patternPreviewEntryCount;
    @DescSynced
    private int patternPreviewScrollRow;
    @DescSynced
    private String patternPreviewSearch = "";
    @DescSynced
    private boolean showSubstitutionPatterns = true;
    @DescSynced
    private boolean showFluidSubstitutionPatterns = true;
    private List<PatternContainer> patternPreviewSources = List.of();
    private List<PatternPreviewEntry> allPatternPreviewEntries = new ArrayList<>();
    private List<PatternPreviewEntry> patternPreviewEntries = new ArrayList<>();
    private boolean patternPreviewInitialized;
    private long lastPatternPreviewSyncTick = Long.MIN_VALUE;
    private final IItemHandlerModifiable patternPreviewItemHandler = new PatternPreviewItemHandler();
    public ECOMachineInterfaceBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<C> calculator
    ) {
        super(type, pos, blockState, calculator);
    }

    public ECOStorageInterfaceMode getStorageInterfaceMode() { return storageInterfaceMode; }
    public long getTransferredLastTick() { return transferredLastTick; }
    public boolean isStorageInputMode() { return storageInterfaceMode == ECOStorageInterfaceMode.INPUT; }
    public boolean isStorageOutputMode() { return storageInterfaceMode == ECOStorageInterfaceMode.OUTPUT; }
    public boolean isStorageTransferMode() { return storageInterfaceMode != ECOStorageInterfaceMode.STORAGE; }
    public boolean isInfiniteTransferAvailable() {
        return formed && cluster instanceof NEStorageCluster storage && storage.getController() != null
            && storage.getController().isFormedInfiniteMode();
    }
    public boolean isTargetOnline() { return getMainNode().isOnline() && getMainNode().getGrid() != null; }
    public boolean supportsStorageInterfaceUi() {
        return cluster instanceof NEStorageCluster || calculator instanceof NEStorageClusterCalculator;
    }
    public boolean supportsCraftingInterfaceUi() {
        return cluster instanceof NECraftingCluster || calculator instanceof NECraftingClusterCalculator;
    }
    public boolean supportsInterfaceUi() {
        return supportsStorageInterfaceUi() || supportsCraftingInterfaceUi();
    }

    public void setStorageInterfaceMode(ECOStorageInterfaceMode mode) {
        ECOStorageInterfaceMode next = mode == null ? ECOStorageInterfaceMode.STORAGE : mode;
        if (storageInterfaceMode == next) return;
        storageInterfaceMode = next;
        transferredLastTick = 0L;
        setChanged();
        markForUpdate();
        if (cluster instanceof NEStorageCluster storage && storage.getController() != null) {
            storage.getController().onStorageInterfaceModeChanged();
        }
    }

    public void recordStorageInterfaceTransfer(long amount) {
        transferredLastTick = Math.max(0L, amount);
    }

    public void startNetworkPatternTransfer() {
        if (patternTransferTask != null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PatternTransferTask task = createPatternTransferTask();
        clearPatternTransferResults();
        patternTransferPerformed = true;
        if (task == null) {
            patternTransferUnavailable = true;
            syncPatternTransferState(serverLevel.getGameTime(), true);
            return;
        }
        patternTransferTask = task;
        patternTransferInProgress = true;
        patternTransferTotalSlots = task.totalSlots();
        syncPatternTransferState(serverLevel.getGameTime(), true);
    }

    public Component getPatternTransferPrimaryStatus() {
        if (patternTransferInProgress) {
            return Component.translatable(
                    "gui.neoecoae.host.crafting.pattern_transfer.progress",
                    patternTransferScannedSlots,
                    patternTransferTotalSlots);
        }
        if (!patternTransferPerformed) {
            return Component.translatable("gui.neoecoae.host.crafting.pattern_transfer.ready");
        }
        if (patternTransferUnavailable) {
            return Component.translatable("gui.neoecoae.host.crafting.pattern_transfer.unavailable");
        }
        if (patternTransferInserted == 0 && patternTransferAlreadyPresent == 0 && patternTransferNoTarget > 0) {
            return Component.translatable("gui.neoecoae.host.crafting.pattern_transfer.no_target");
        }
        return Component.translatable(
                "gui.neoecoae.host.crafting.pattern_transfer.result_primary",
                patternTransferInserted,
                patternTransferAlreadyPresent);
    }

    public Component getPatternTransferSecondaryStatus() {
        if (!patternTransferPerformed || patternTransferUnavailable
                || (patternTransferInserted == 0 && patternTransferAlreadyPresent == 0 && patternTransferNoTarget > 0)) {
            return Component.empty();
        }
        return Component.translatable(
                "gui.neoecoae.host.crafting.pattern_transfer.result_secondary",
                patternTransferNoSpace,
                patternTransferIncompatible);
    }

    public IItemHandlerModifiable getPatternPreviewItemHandler() {
        return patternPreviewItemHandler;
    }

    public void organizePatternBuses() {
        if (!(level instanceof ServerLevel serverLevel) || !formed || !supportsCraftingInterfaceUi()) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }

        List<PatternPreviewEntry> slots = new ArrayList<>();
        List<ItemStack> patterns = new ArrayList<>();
        for (PatternContainer source : getFPatternSources(grid)) {
            if (source.getGrid() != grid) {
                continue;
            }
            InternalInventory inventory = source.getTerminalPatternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                slots.add(new PatternPreviewEntry(source, slot));
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    patterns.add(stack.copy());
                }
            }
        }

        for (int index = 0; index < slots.size(); index++) {
            PatternPreviewEntry target = slots.get(index);
            ItemStack desired = index < patterns.size() ? patterns.get(index) : ItemStack.EMPTY;
            InternalInventory inventory = getPreviewInventory(target);
            if (!ItemStack.matches(inventory.getStackInSlot(target.sourceSlot()), desired)) {
                inventory.setItemDirect(target.sourceSlot(), desired);
            }
        }
        loadPatternPreview(serverLevel);
    }

    public void ensurePatternPreview() {
        if (level instanceof ServerLevel serverLevel && !patternPreviewInitialized) {
            loadPatternPreview(serverLevel);
        }
    }

    public void scrollPatternPreview(int rowDelta) {
        int nextRow = Math.clamp(patternPreviewScrollRow + rowDelta, 0, getPatternPreviewMaxScrollRow());
        if (nextRow == patternPreviewScrollRow) {
            return;
        }
        patternPreviewScrollRow = nextRow;
        markForUpdate();
    }

    public String getPatternPreviewSearch() {
        return patternPreviewSearch;
    }

    public void setPatternPreviewSearch(String search) {
        String next = search == null ? "" : search.trim();
        if (next.equals(patternPreviewSearch)) {
            return;
        }
        patternPreviewSearch = next;
        patternPreviewScrollRow = 0;
        rebuildPatternPreviewEntries();
        markForUpdate();
    }

    public boolean showsSubstitutionPatterns() {
        return showSubstitutionPatterns;
    }

    public boolean showsFluidSubstitutionPatterns() {
        return showFluidSubstitutionPatterns;
    }

    public void toggleSubstitutionPatterns() {
        showSubstitutionPatterns = !showSubstitutionPatterns;
        rebuildPatternPreviewEntries();
        markForUpdate();
    }

    public void toggleFluidSubstitutionPatterns() {
        showFluidSubstitutionPatterns = !showFluidSubstitutionPatterns;
        rebuildPatternPreviewEntries();
        markForUpdate();
    }

    public Component getPatternPreviewStatus() {
        if (patternPreviewScanning) {
            return Component.translatable(
                "gui.neoecoae.crafting_interface.preview.scanning",
                patternPreviewScannedSlots,
                patternPreviewTotalSlots
            );
        }
        return Component.translatable(
            "gui.neoecoae.crafting_interface.preview.slots",
            patternPreviewEntryCount
        );
    }

    public Component getPatternPreviewScrollStatus() {
        return Component.translatable(
            "gui.neoecoae.crafting_interface.preview.scroll",
            patternPreviewScrollRow + 1,
            getPatternPreviewMaxScrollRow() + 1
        );
    }

    private int getPatternPreviewMaxScrollRow() {
        int totalRows = (patternPreviewEntryCount + PATTERN_PREVIEW_COLUMNS - 1) / PATTERN_PREVIEW_COLUMNS;
        return Math.max(0, totalRows - PATTERN_PREVIEW_ROWS);
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long deadline = System.nanoTime() + PATTERN_TRANSFER_MAX_NANOS_PER_TICK;
        if (patternTransferTask != null) {
            tickPatternTransfer(serverLevel, deadline);
        }
        if (patternPreviewInitialized) {
            tickPatternPreviewCache(serverLevel);
        }
    }

    @Nullable
    private PatternTransferTask createPatternTransferTask() {
        if (!formed || !supportsCraftingInterfaceUi()) {
            return null;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        IECOPatternStorageService storageService = grid.getService(IECOPatternStorageService.class);
        if (storageService == null) {
            return null;
        }
        return new PatternTransferTask(grid, storageService, getExternalPatternSources(grid));
    }

    private List<PatternContainer> getExternalPatternSources(IGrid grid) {
        List<PatternContainer> sources = new ArrayList<>();
        Set<PatternContainer> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }
            Class<? extends PatternContainer> containerClass = machineClass.asSubclass(PatternContainer.class);
            for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                if (!visited.add(container) || container instanceof ECOCraftingPatternBusBlockEntity) {
                    continue;
                }
                sources.add(container);
            }
        }
        return sources;
    }

    private List<PatternContainer> getFPatternSources(IGrid grid) {
        List<PatternContainer> sources = new ArrayList<>();
        sources.addAll(grid.getActiveMachines(ECOCraftingPatternBusBlockEntity.class));
        return sources;
    }

    private void tickPatternTransfer(ServerLevel serverLevel, long deadline) {
        PatternTransferTask task = patternTransferTask;
        if (task == null) {
            return;
        }
        if (!formed || getMainNode().getGrid() != task.grid()) {
            finishPatternTransfer(serverLevel, true);
            return;
        }

        int scannedThisTick = 0;
        int insertionsThisTick = 0;
        while (scannedThisTick < PATTERN_TRANSFER_MAX_SLOTS_PER_TICK
                && insertionsThisTick < PATTERN_TRANSFER_MAX_INSERTIONS_PER_TICK
                && System.nanoTime() < deadline
                && !task.isFinished()) {
            PatternTransferStep step = task.nextStep();
            if (step == null) {
                break;
            }
            scannedThisTick++;
            patternTransferScannedSlots++;
            ItemStack stack = step.inventory().getStackInSlot(step.slot());
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                continue;
            }
            if (!(PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern)) {
                patternTransferIncompatible++;
                continue;
            }

            insertionsThisTick++;
            switch (task.storageService().getPatternStorage().insertPattern(stack.copy())) {
                case INSERTED -> {
                    step.inventory().setItemDirect(step.slot(), ItemStack.EMPTY);
                    patternTransferInserted++;
                }
                case ALREADY_PRESENT -> {
                    step.inventory().setItemDirect(step.slot(), ItemStack.EMPTY);
                    patternTransferAlreadyPresent++;
                }
                case NO_SPACE -> patternTransferNoSpace++;
                case NO_TARGET -> {
                    patternTransferNoTarget++;
                    finishPatternTransfer(serverLevel, false);
                    return;
                }
                case INCOMPATIBLE -> patternTransferIncompatible++;
            }
        }
        if (task.isFinished()) {
            finishPatternTransfer(serverLevel, false);
        } else {
            syncPatternTransferState(serverLevel.getGameTime(), false);
        }
    }

    /**
     * Builds an ordered address book for the F pattern buses. It deliberately does not inspect or decode every
     * pattern: the menu slots read the bus inventories directly, just like AE2's pattern access terminal.
     */
    private void loadPatternPreview(ServerLevel serverLevel) {
        if (!formed || !supportsCraftingInterfaceUi()) {
            clearPatternPreview();
            syncPatternPreviewState(serverLevel.getGameTime(), true);
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            clearPatternPreview();
            syncPatternPreviewState(serverLevel.getGameTime(), true);
            return;
        }
        List<PatternContainer> sources = getFPatternSources(grid);
        List<PatternPreviewEntry> entries = new ArrayList<>();
        for (PatternContainer source : sources) {
            InternalInventory inventory = source.getTerminalPatternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                entries.add(new PatternPreviewEntry(source, slot));
            }
        }
        patternPreviewSources = sources;
        allPatternPreviewEntries = entries;
        patternPreviewInitialized = true;
        patternPreviewScanning = false;
        patternPreviewScannedSlots = 0;
        patternPreviewTotalSlots = entries.size();
        rebuildPatternPreviewEntries();
        syncPatternPreviewState(serverLevel.getGameTime(), true);
    }

    /**
     * Refresh the ordered source list only when buses are attached or removed. Stack changes require no cache refresh:
     * menu slots always delegate to the source inventory.
     */
    private void tickPatternPreviewCache(ServerLevel serverLevel) {
        IGrid grid = getMainNode().getGrid();
        if (!formed || grid == null) {
            clearPatternPreview();
            return;
        }
        List<PatternContainer> sources = getFPatternSources(grid);
        if (!samePatternPreviewSources(sources)) {
            loadPatternPreview(serverLevel);
            return;
        }
    }

    private boolean samePatternPreviewSources(List<PatternContainer> sources) {
        if (sources.size() != patternPreviewSources.size()) {
            return false;
        }
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i) != patternPreviewSources.get(i)
                    || sources.get(i).getGrid() != getMainNode().getGrid()) {
                return false;
            }
        }
        return true;
    }

    private void clearPatternPreview() {
        patternPreviewSources = List.of();
        allPatternPreviewEntries = new ArrayList<>();
        patternPreviewEntries = new ArrayList<>();
        patternPreviewEntryCount = 0;
        patternPreviewScrollRow = 0;
        patternPreviewInitialized = false;
        patternPreviewScanning = false;
        patternPreviewScannedSlots = 0;
        patternPreviewTotalSlots = 0;
    }

    private void rebuildPatternPreviewEntries() {
        // Search and filters are intentionally applied only when the player changes them. The normal terminal view
        // retains every physical bus slot, including empty ones, so insertion can be ordered and direct.
        if (patternPreviewSearch.isEmpty() && showSubstitutionPatterns && showFluidSubstitutionPatterns) {
            patternPreviewEntries = allPatternPreviewEntries;
            patternPreviewEntryCount = patternPreviewEntries.size();
            patternPreviewScrollRow = Math.min(patternPreviewScrollRow, getPatternPreviewMaxScrollRow());
            return;
        }
        List<PatternPreviewEntry> entries = new ArrayList<>();
        for (PatternPreviewEntry entry : allPatternPreviewEntries) {
            if (!isPreviewSourceActive(entry)) {
                continue;
            }
            ItemStack stack = getPreviewInventory(entry).getStackInSlot(entry.sourceSlot());
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)
                    && matchesPatternPreviewSearch(stack, patternPreviewSearch.toLowerCase(java.util.Locale.ROOT))) {
                entries.add(entry);
            }
        }
        patternPreviewEntries = entries;
        patternPreviewEntryCount = entries.size();
        patternPreviewScrollRow = Math.min(patternPreviewScrollRow, getPatternPreviewMaxScrollRow());
    }

    private boolean matchesPatternPreviewSearch(ItemStack stack, String query) {
        var encodedPattern = stack.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encodedPattern != null && (!showSubstitutionPatterns && encodedPattern.canSubstitute()
                || !showFluidSubstitutionPatterns && encodedPattern.canSubstituteFluids())) {
            return false;
        }
        var details = level == null ? null : PatternDetailsHelper.decodePattern(stack, level);
        if (details == null) {
            return false;
        }

        // Match AE2's pattern access terminal: only pattern outputs take part in its text search.
        return query.isEmpty() || details.getOutputs().stream()
                .anyMatch(output -> output.what().getDisplayName().getString()
                        .toLowerCase(java.util.Locale.ROOT).contains(query));
    }

    private void syncPatternPreviewState(long gameTime, boolean force) {
        if (force || gameTime - lastPatternPreviewSyncTick >= PATTERN_TRANSFER_SYNC_INTERVAL_TICKS) {
            lastPatternPreviewSyncTick = gameTime;
            markForUpdate();
        }
    }

    private void clearPatternTransferResults() {
        patternTransferInserted = 0;
        patternTransferAlreadyPresent = 0;
        patternTransferNoSpace = 0;
        patternTransferNoTarget = 0;
        patternTransferIncompatible = 0;
        patternTransferUnavailable = false;
        patternTransferScannedSlots = 0;
        patternTransferTotalSlots = 0;
        patternTransferInProgress = false;
    }

    private void finishPatternTransfer(ServerLevel level, boolean unavailable) {
        patternTransferTask = null;
        patternTransferInProgress = false;
        patternTransferUnavailable |= unavailable;
        syncPatternTransferState(level.getGameTime(), true);
    }

    private void syncPatternTransferState(long gameTime, boolean force) {
        if (force || gameTime - lastPatternTransferSyncTick >= PATTERN_TRANSFER_SYNC_INTERVAL_TICKS) {
            lastPatternTransferSyncTick = gameTime;
            markForUpdate();
        }
    }

    private final class PatternTransferTask {
        private final IGrid grid;
        private final IECOPatternStorageService storageService;
        private final List<PatternContainer> sources;
        private final int totalSlots;
        private int sourceIndex;
        private int slotIndex;

        private PatternTransferTask(
                IGrid grid,
                IECOPatternStorageService storageService,
                List<PatternContainer> sources) {
            this.grid = grid;
            this.storageService = storageService;
            this.sources = sources;
            this.totalSlots = sources.stream().mapToInt(source -> source.getTerminalPatternInventory().size()).sum();
        }

        private IGrid grid() {
            return grid;
        }

        private IECOPatternStorageService storageService() {
            return storageService;
        }

        private int totalSlots() {
            return totalSlots;
        }

        private boolean isFinished() {
            return sourceIndex >= sources.size();
        }

        @Nullable
        private PatternTransferStep nextStep() {
            while (!isFinished()) {
                PatternContainer source = sources.get(sourceIndex);
                if (source.getGrid() != grid) {
                    sourceIndex++;
                    slotIndex = 0;
                    continue;
                }
                InternalInventory inventory = source.getTerminalPatternInventory();
                if (slotIndex < inventory.size()) {
                    return new PatternTransferStep(inventory, slotIndex++);
                }
                sourceIndex++;
                slotIndex = 0;
            }
            return null;
        }
    }

    private record PatternTransferStep(InternalInventory inventory, int slot) {
    }

    private record PatternPreviewEntry(PatternContainer source, int sourceSlot) {
    }

    /**
     * A fixed-size menu inventory whose server slots point at the current preview page. The client copy exists only
     * to receive ordinary menu slot packets; source locations are never sent to or trusted from the client.
     */
    private final class PatternPreviewItemHandler implements IItemHandlerModifiable {
        private final ItemStack[] clientStacks = new ItemStack[PATTERN_PREVIEW_VISIBLE_SLOTS];

        private PatternPreviewItemHandler() {
            Arrays.fill(clientStacks, ItemStack.EMPTY);
        }

        @Override
        public int getSlots() {
            return PATTERN_PREVIEW_VISIBLE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int visualSlot) {
            if (!isValidPreviewSlot(visualSlot)) {
                return ItemStack.EMPTY;
            }
            if (isClientPreviewHandler()) {
                return clientStacks[visualSlot];
            }
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            return isPreviewSourceActive(entry) ? getPreviewInventory(entry).getStackInSlot(entry.sourceSlot()) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int visualSlot, ItemStack stack, boolean simulate) {
            if (!isPreviewSlotValidForInsert(visualSlot, stack)) {
                return stack;
            }
            if (isClientPreviewHandler()) {
                return stack;
            }
            // Insertions are independent of the clicked visual page. This matches the pattern terminal workflow:
            // the first free slot is chosen in bus/slot order, then the next one on subsequent inserts.
            PatternPreviewEntry entry = findFirstEmptyPreviewEntry();
            if (entry == null) {
                return stack;
            }
            InternalInventory inventory = getPreviewInventory(entry);
            ItemStack remaining = inventory.insertItem(entry.sourceSlot(), stack, simulate);
            return remaining;
        }

        @Override
        public ItemStack extractItem(int visualSlot, int amount, boolean simulate) {
            if (!isValidPreviewSlot(visualSlot) || isClientPreviewHandler()) {
                return ItemStack.EMPTY;
            }
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            if (!isPreviewSourceActive(entry)) {
                return ItemStack.EMPTY;
            }
            InternalInventory inventory = getPreviewInventory(entry);
            return inventory.extractItem(entry.sourceSlot(), amount, simulate);
        }

        @Override
        public int getSlotLimit(int visualSlot) {
            if (!isValidPreviewSlot(visualSlot) || isClientPreviewHandler()) {
                return 0;
            }
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            return isPreviewSourceActive(entry)
                ? getPreviewInventory(entry).getSlotLimit(entry.sourceSlot())
                : 0;
        }

        @Override
        public boolean isItemValid(int visualSlot, ItemStack stack) {
            return isPreviewSlotValidForInsert(visualSlot, stack);
        }

        @Override
        public void setStackInSlot(int visualSlot, ItemStack stack) {
            if (!isValidPreviewSlot(visualSlot)) {
                return;
            }
            if (isClientPreviewHandler()) {
                clientStacks[visualSlot] = stack.copy();
                return;
            }
            PatternPreviewEntry entry = stack.isEmpty() ? getPreviewEntry(visualSlot) : findFirstEmptyPreviewEntry();
            if (entry == null || !isPreviewSourceActive(entry)) {
                return;
            }
            InternalInventory inventory = getPreviewInventory(entry);
            ItemStack current = inventory.getStackInSlot(entry.sourceSlot());
            if (stack.isEmpty()) {
                if (!current.isEmpty()) {
                    inventory.extractItem(entry.sourceSlot(), current.getCount(), false);
                }
                return;
            }
            if (!isPreviewSlotValidForInsert(visualSlot, stack)) {
                return;
            }
            if (!current.isEmpty()) {
                inventory.extractItem(entry.sourceSlot(), current.getCount(), false);
            }
            ItemStack remaining = inventory.insertItem(entry.sourceSlot(), stack, false);
            if (!remaining.isEmpty()) {
                inventory.insertItem(entry.sourceSlot(), current, false);
                return;
            }
        }

        private boolean isPreviewSlotValidForInsert(int visualSlot, ItemStack stack) {
            if (!isValidPreviewSlot(visualSlot) || stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return false;
            }
            if (isClientPreviewHandler()) {
                return true;
            }
            return findFirstEmptyPreviewEntry() != null;
        }
    }

    private boolean isClientPreviewHandler() {
        return level != null && level.isClientSide;
    }

    private boolean isValidPreviewSlot(int visualSlot) {
        return visualSlot >= 0 && visualSlot < PATTERN_PREVIEW_VISIBLE_SLOTS;
    }

    @Nullable
    private PatternPreviewEntry getPreviewEntry(int visualSlot) {
        int entryIndex = patternPreviewScrollRow * PATTERN_PREVIEW_COLUMNS + visualSlot;
        return entryIndex >= 0 && entryIndex < patternPreviewEntries.size() ? patternPreviewEntries.get(entryIndex) : null;
    }

    @Nullable
    private PatternPreviewEntry findFirstEmptyPreviewEntry() {
        for (PatternPreviewEntry entry : allPatternPreviewEntries) {
            if (isPreviewSourceActive(entry)
                    && getPreviewInventory(entry).getStackInSlot(entry.sourceSlot()).isEmpty()) {
                return entry;
            }
        }
        return null;
    }

    private boolean isPreviewSourceActive(@Nullable PatternPreviewEntry entry) {
        if (entry == null) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null || entry.source().getGrid() != grid) {
            return false;
        }
        InternalInventory inventory = getPreviewInventory(entry);
        return entry.sourceSlot() >= 0 && entry.sourceSlot() < inventory.size();
    }

    private InternalInventory getPreviewInventory(PatternPreviewEntry entry) {
        return entry.source().getTerminalPatternInventory();
    }


    @SuppressWarnings("unchecked")
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (supportsStorageInterfaceUi()) {
            return StorageInterfaceUI.create((ECOMachineInterfaceBlockEntity<NEStorageCluster>) this, holder.player);
        }
        if (supportsCraftingInterfaceUi()) {
            return CraftingInterfaceUI.create((ECOMachineInterfaceBlockEntity<NECraftingCluster>) this, holder.player);
        }
        return null;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.allOf(Direction.class);
    }
}
