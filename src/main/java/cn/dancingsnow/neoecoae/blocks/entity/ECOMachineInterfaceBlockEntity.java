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
    private static final int PATTERN_TRANSFER_MAX_INSERTIONS_PER_TICK = 2;
    private static final long PATTERN_TRANSFER_MAX_NANOS_PER_TICK = 1_000_000L;
    private static final long PATTERN_TRANSFER_SYNC_INTERVAL_TICKS = 5L;
    private static final int PATTERN_PREVIEW_COLUMNS = 9;
    private static final int PATTERN_PREVIEW_ROWS = 5;
    private static final int PATTERN_PREVIEW_VISIBLE_SLOTS = PATTERN_PREVIEW_COLUMNS * PATTERN_PREVIEW_ROWS;
    private static final int PATTERN_PREVIEW_MAX_SLOTS_PER_TICK = 24;

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
    private List<PatternPreviewEntry> patternPreviewEntries = new ArrayList<>();
    private List<PatternPreviewTracker> patternPreviewTrackers = List.of();
    @Nullable
    private PatternPreviewTask patternPreviewTask;
    private boolean patternPreviewInitialized;
    private boolean patternPreviewCacheReady;
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

    public void refreshPatternPreview() {
        if (level instanceof ServerLevel serverLevel) {
            beginPatternPreviewScan(serverLevel);
        }
    }

    public void ensurePatternPreview() {
        // The preview is an intentionally explicit cache: reopening the terminal
        // must not start another full network scan. The refresh button is the
        // opt-in way to rebuild it.
        if (level instanceof ServerLevel serverLevel && !patternPreviewCacheReady && patternPreviewTask == null) {
            beginPatternPreviewScan(serverLevel);
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
            "gui.neoecoae.crafting_interface.preview.entries",
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
        if (patternPreviewTask != null && System.nanoTime() < deadline) {
            tickPatternPreview(serverLevel, deadline);
        }
        if (patternPreviewCacheReady && patternPreviewTask == null) {
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

    private void beginPatternPreviewScan(ServerLevel serverLevel) {
        PatternPreviewTask task = createPatternPreviewTask();
        patternPreviewInitialized = task != null;
        patternPreviewCacheReady = false;
        patternPreviewScannedSlots = 0;
        patternPreviewTotalSlots = task == null ? 0 : task.totalSlots();
        patternPreviewTask = task;
        patternPreviewScanning = task != null;
        if (task == null) {
            patternPreviewEntries = new ArrayList<>();
            patternPreviewTrackers = List.of();
            patternPreviewEntryCount = 0;
            patternPreviewScrollRow = 0;
        }
        syncPatternPreviewState(serverLevel.getGameTime(), true);
    }

    @Nullable
    private PatternPreviewTask createPatternPreviewTask() {
        if (!formed || !supportsCraftingInterfaceUi()) {
            return null;
        }
        IGrid grid = getMainNode().getGrid();
        return grid == null ? null : new PatternPreviewTask(grid, getFPatternSources(grid));
    }

    private void tickPatternPreview(ServerLevel serverLevel, long deadline) {
        PatternPreviewTask task = patternPreviewTask;
        if (task == null) {
            return;
        }
        if (!formed || getMainNode().getGrid() != task.grid()) {
            finishPatternPreview(serverLevel, true);
            return;
        }

        int scannedThisTick = 0;
        while (scannedThisTick < PATTERN_PREVIEW_MAX_SLOTS_PER_TICK
            && System.nanoTime() < deadline
            && !task.isFinished()) {
            PatternPreviewStep step = task.nextStep();
            if (step == null) {
                break;
            }
            scannedThisTick++;
            patternPreviewScannedSlots++;
            ItemStack stack = step.inventory().getStackInSlot(step.slot());
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                task.entries().add(new PatternPreviewEntry(step.source(), step.slot(), stack.copy()));
            }
        }
        if (task.isFinished()) {
            patternPreviewTrackers = task.sources().stream().map(PatternPreviewTracker::new).toList();
            rebuildPatternPreviewEntries();
            patternPreviewCacheReady = true;
            finishPatternPreview(serverLevel, false);
        } else {
            syncPatternPreviewState(serverLevel.getGameTime(), false);
        }
    }

    private void finishPatternPreview(ServerLevel level, boolean discardEntries) {
        patternPreviewTask = null;
        patternPreviewScanning = false;
        if (discardEntries) {
            patternPreviewEntries = new ArrayList<>();
            patternPreviewTrackers = List.of();
            patternPreviewEntryCount = 0;
            patternPreviewScrollRow = 0;
            patternPreviewInitialized = false;
            patternPreviewCacheReady = false;
        }
        syncPatternPreviewState(level.getGameTime(), true);
    }

    /**
     * Mirrors AE2's pattern access terminal: retain one snapshot per pattern container and only rebuild the visible
     * list when a slot actually changes. AE2 does not expose this tracker as a public API, so the custom terminal
     * keeps the same server-side semantics while retaining its own transfer controls.
     */
    private void tickPatternPreviewCache(ServerLevel serverLevel) {
        IGrid grid = getMainNode().getGrid();
        if (!formed || grid == null) {
            finishPatternPreview(serverLevel, true);
            return;
        }
        List<PatternContainer> sources = getFPatternSources(grid);
        if (sources.size() != patternPreviewTrackers.size()
                || !samePatternPreviewSources(sources)) {
            beginPatternPreviewScan(serverLevel);
            return;
        }
        boolean changed = false;
        for (PatternPreviewTracker tracker : patternPreviewTrackers) {
            changed |= tracker.update();
        }
        if (changed) {
            rebuildPatternPreviewEntries();
            syncPatternPreviewState(serverLevel.getGameTime(), true);
        }
    }

    private boolean samePatternPreviewSources(List<PatternContainer> sources) {
        for (int i = 0; i < sources.size(); i++) {
            PatternPreviewTracker tracker = patternPreviewTrackers.get(i);
            if (tracker.source() != sources.get(i) || tracker.source().getGrid() != getMainNode().getGrid()) {
                return false;
            }
        }
        return true;
    }

    private void rebuildPatternPreviewEntries() {
        String query = patternPreviewSearch.toLowerCase(java.util.Locale.ROOT);
        List<PatternPreviewEntry> entries = new ArrayList<>();
        for (PatternPreviewTracker tracker : patternPreviewTrackers) {
            for (int slot = 0; slot < tracker.snapshot().length; slot++) {
                ItemStack stack = tracker.snapshot()[slot];
                if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack) && matchesPatternPreviewSearch(stack, query)) {
                    entries.add(new PatternPreviewEntry(tracker.source(), slot, stack.copy()));
                }
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
        if (query.isEmpty() || stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(query)
                || stack.getComponentsPatch().toString().toLowerCase(java.util.Locale.ROOT).contains(query)) {
            return true;
        }
        var details = level == null ? null : PatternDetailsHelper.decodePattern(stack, level);
        return details != null && details.getOutputs().stream()
            .anyMatch(output -> output.what().getDisplayName().getString().toLowerCase(java.util.Locale.ROOT).contains(query));
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

    private final class PatternPreviewTask {
        private final IGrid grid;
        private final List<PatternContainer> sources;
        private final List<PatternPreviewEntry> entries = new ArrayList<>();
        private final int totalSlots;
        private int sourceIndex;
        private int slotIndex;

        private PatternPreviewTask(IGrid grid, List<PatternContainer> sources) {
            this.grid = grid;
            this.sources = sources;
            this.totalSlots = sources.stream().mapToInt(source -> source.getTerminalPatternInventory().size()).sum();
        }

        private IGrid grid() {
            return grid;
        }

        private int totalSlots() {
            return totalSlots;
        }

        private List<PatternPreviewEntry> entries() {
            return entries;
        }

        private List<PatternContainer> sources() {
            return sources;
        }

        private boolean isFinished() {
            return sourceIndex >= sources.size();
        }

        @Nullable
        private PatternPreviewStep nextStep() {
            while (!isFinished()) {
                PatternContainer source = sources.get(sourceIndex);
                if (source.getGrid() != grid) {
                    sourceIndex++;
                    slotIndex = 0;
                    continue;
                }
                InternalInventory inventory = source.getTerminalPatternInventory();
                if (slotIndex < inventory.size()) {
                    return new PatternPreviewStep(source, inventory, slotIndex++);
                }
                sourceIndex++;
                slotIndex = 0;
            }
            return null;
        }
    }

    private record PatternPreviewEntry(PatternContainer source, int sourceSlot, ItemStack fingerprint) {
    }

    private record PatternPreviewStep(PatternContainer source, InternalInventory inventory, int slot) {
    }

    private static final class PatternPreviewTracker {
        private final PatternContainer source;
        private final ItemStack[] snapshot;

        private PatternPreviewTracker(PatternContainer source) {
            this.source = source;
            InternalInventory inventory = source.getTerminalPatternInventory();
            this.snapshot = new ItemStack[inventory.size()];
            for (int slot = 0; slot < snapshot.length; slot++) {
                snapshot[slot] = inventory.getStackInSlot(slot).copy();
            }
        }

        private PatternContainer source() {
            return source;
        }

        private ItemStack[] snapshot() {
            return snapshot;
        }

        private boolean update() {
            InternalInventory inventory = source.getTerminalPatternInventory();
            if (inventory.size() != snapshot.length) {
                return true;
            }
            boolean changed = false;
            for (int slot = 0; slot < snapshot.length; slot++) {
                ItemStack current = inventory.getStackInSlot(slot);
                if (!ItemStack.matches(current, snapshot[slot])) {
                    snapshot[slot] = current.copy();
                    changed = true;
                }
            }
            return changed;
        }
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
            return isCurrentPreviewEntry(entry) ? getPreviewInventory(entry).getStackInSlot(entry.sourceSlot()) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int visualSlot, ItemStack stack, boolean simulate) {
            if (!isPreviewSlotValidForInsert(visualSlot, stack)) {
                return stack;
            }
            if (isClientPreviewHandler()) {
                return stack;
            }
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            InternalInventory inventory = getPreviewInventory(entry);
            ItemStack remaining = inventory.insertItem(entry.sourceSlot(), stack, simulate);
            if (!simulate && remaining.getCount() != stack.getCount()) {
                updatePreviewEntryFingerprint(visualSlot, inventory.getStackInSlot(entry.sourceSlot()));
            }
            return remaining;
        }

        @Override
        public ItemStack extractItem(int visualSlot, int amount, boolean simulate) {
            if (!isValidPreviewSlot(visualSlot) || isClientPreviewHandler()) {
                return ItemStack.EMPTY;
            }
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            if (!isCurrentPreviewEntry(entry)) {
                return ItemStack.EMPTY;
            }
            InternalInventory inventory = getPreviewInventory(entry);
            ItemStack extracted = inventory.extractItem(entry.sourceSlot(), amount, simulate);
            if (!simulate && !extracted.isEmpty()) {
                updatePreviewEntryFingerprint(visualSlot, inventory.getStackInSlot(entry.sourceSlot()));
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int visualSlot) {
            if (!isValidPreviewSlot(visualSlot) || isClientPreviewHandler()) {
                return 0;
            }
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            return isCurrentPreviewEntry(entry) || isEmptyPreviewEntry(entry)
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
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            if (entry == null || !isPreviewSourceActive(entry)) {
                return;
            }
            InternalInventory inventory = getPreviewInventory(entry);
            ItemStack current = inventory.getStackInSlot(entry.sourceSlot());
            if (!current.isEmpty() && !matchesPreviewFingerprint(current, entry)) {
                return;
            }
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
            updatePreviewEntryFingerprint(visualSlot, inventory.getStackInSlot(entry.sourceSlot()));
        }

        private boolean isPreviewSlotValidForInsert(int visualSlot, ItemStack stack) {
            if (!isValidPreviewSlot(visualSlot) || stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return false;
            }
            if (isClientPreviewHandler()) {
                return true;
            }
            PatternPreviewEntry entry = getPreviewEntry(visualSlot);
            if (entry == null || !isPreviewSourceActive(entry)) {
                return false;
            }
            InternalInventory inventory = getPreviewInventory(entry);
            ItemStack current = inventory.getStackInSlot(entry.sourceSlot());
            return (current.isEmpty() || matchesPreviewFingerprint(current, entry))
                && inventory.isItemValid(entry.sourceSlot(), stack);
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

    private boolean isCurrentPreviewEntry(@Nullable PatternPreviewEntry entry) {
        if (entry == null || !isPreviewSourceActive(entry)) {
            return false;
        }
        ItemStack stack = getPreviewInventory(entry).getStackInSlot(entry.sourceSlot());
        return matchesPreviewFingerprint(stack, entry);
    }

    private boolean isEmptyPreviewEntry(@Nullable PatternPreviewEntry entry) {
        return entry != null && isPreviewSourceActive(entry)
            && getPreviewInventory(entry).getStackInSlot(entry.sourceSlot()).isEmpty();
    }

    private boolean isPreviewSourceActive(PatternPreviewEntry entry) {
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

    private boolean matchesPreviewFingerprint(ItemStack stack, PatternPreviewEntry entry) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, entry.fingerprint());
    }

    private void updatePreviewEntryFingerprint(int visualSlot, ItemStack stack) {
        int entryIndex = patternPreviewScrollRow * PATTERN_PREVIEW_COLUMNS + visualSlot;
        if (entryIndex < 0 || entryIndex >= patternPreviewEntries.size() || stack.isEmpty()) {
            return;
        }
        PatternPreviewEntry entry = patternPreviewEntries.get(entryIndex);
        patternPreviewEntries.set(entryIndex, new PatternPreviewEntry(entry.source(), entry.sourceSlot(), stack.copy()));
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
