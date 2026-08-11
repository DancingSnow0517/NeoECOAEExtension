package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingInterfaceUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageInterfaceUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEBlockEntityUIHolder;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.google.common.math.LongMath;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

public class ECOMachineInterfaceBlockEntity<C extends NECluster<C>>
        extends NEBlockEntity<C, ECOMachineInterfaceBlockEntity<C>> implements NEBlockEntityUIHolder {
    private static final String NBT_STORAGE_INTERFACE_MODE = "storageInterfaceMode";
    private static final int PREVIEW_COLUMNS = 9;
    private static final int PREVIEW_ROWS = 5;
    private static final int PREVIEW_SLOTS = PREVIEW_COLUMNS * PREVIEW_ROWS;
    private static final int TRANSFER_SLOTS_PER_TICK = 24;
    private static final int TRANSFER_INSERTIONS_PER_TICK = 8;

    private ECOStorageInterfaceMode storageInterfaceMode = ECOStorageInterfaceMode.STORAGE;
    private long exportedLastTick;
    private long exportedTotal;
    private boolean patternTransferPerformed;
    private boolean patternTransferInProgress;
    private boolean patternTransferUnavailable;
    private int patternTransferScannedSlots;
    private int patternTransferTotalSlots;
    private int patternTransferInserted;
    private int patternTransferAlreadyPresent;
    private int patternTransferNoSpace;
    private int patternTransferIncompatible;

    @Nullable private PatternTransferTask patternTransferTask;

    private String patternPreviewSearch = "";
    private boolean showSubstitutionPatterns = true;
    private boolean showFluidSubstitutionPatterns = true;
    private int patternPreviewScrollRow;
    private List<PatternPreviewEntry> allPatternPreviewEntries = List.of();
    private List<PatternPreviewEntry> patternPreviewEntries = List.of();
    private List<ECOCraftingPatternBusBlockEntity> patternPreviewBuses = List.of();
    private boolean patternPreviewDataPrepared;
    /**
     * Search results are kept in display order so a longer query can be matched against the
     * result of its previous prefix instead of scanning every pattern again.
     */
    private final Map<String, List<PatternPreviewEntry>> patternPreviewSearchCache = new HashMap<>();

    private final IItemHandlerModifiable patternPreviewItemHandler = new PatternPreviewItemHandler();

    public ECOMachineInterfaceBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState blockState, NEClusterCalculator.Factory<C> calculator) {
        super(type, pos, blockState, calculator);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.allOf(Direction.class);
    }

    public ECOStorageInterfaceMode getStorageInterfaceMode() {
        return storageInterfaceMode;
    }

    public boolean isStorageInputMode() {
        return storageInterfaceMode == ECOStorageInterfaceMode.INPUT;
    }

    public boolean isStorageOutputMode() {
        return storageInterfaceMode == ECOStorageInterfaceMode.OUTPUT;
    }

    public boolean isStorageTransferMode() {
        return storageInterfaceMode == ECOStorageInterfaceMode.INPUT
                || storageInterfaceMode == ECOStorageInterfaceMode.OUTPUT;
    }

    public boolean supportsStorageInterfaceUi() {
        return cluster instanceof NEStorageCluster || calculator instanceof NEStorageClusterCalculator;
    }

    public boolean supportsCraftingInterfaceUi() {
        return cluster instanceof NECraftingCluster || calculator instanceof NECraftingClusterCalculator;
    }

    public boolean isTargetOnline() {
        return getMainNode().isOnline() && getMainNode().getGrid() != null;
    }

    public IItemHandlerModifiable getPatternPreviewItemHandler() {
        return patternPreviewItemHandler;
    }

    /**
     * Converts an encoded pattern to the stack shown in the terminal's pattern preview slots.
     * The slots themselves retain the encoded pattern so native quick-move always transfers it.
     */
    public ItemStack getPatternPreviewDisplayStack(ItemStack pattern) {
        var details = PatternDetailsHelper.decodePattern(pattern, level);
        if (details == null || details.getPrimaryOutput() == null) {
            return ItemStack.EMPTY;
        }
        return details.getPrimaryOutput().what().wrapForDisplayOrFilter();
    }

    public String getPatternPreviewSearch() {
        return patternPreviewSearch;
    }

    public boolean showsSubstitutionPatterns() {
        return showSubstitutionPatterns;
    }

    public boolean showsFluidSubstitutionPatterns() {
        return showFluidSubstitutionPatterns;
    }

    public int getPatternPreviewEntryCount() {
        return patternPreviewEntries.size();
    }

    public int getPatternPreviewScrollRow() {
        return patternPreviewScrollRow;
    }

    public int getPatternPreviewMaxScrollRow() {
        return Math.max(0, (patternPreviewEntries.size() + PREVIEW_COLUMNS - 1) / PREVIEW_COLUMNS - PREVIEW_ROWS);
    }

    public void setPatternPreviewSearch(String search) {
        String next = search == null ? "" : search.strip();
        if (!next.equals(patternPreviewSearch)) {
            patternPreviewSearch = next;
            patternPreviewScrollRow = 0;
            rebuildPatternPreview();
            markForUpdate();
        }
    }

    public void clearPatternPreviewSearch() {
        setPatternPreviewSearch("");
    }

    public void toggleSubstitutionPatterns() {
        showSubstitutionPatterns = !showSubstitutionPatterns;
        rebuildPatternPreview();
        markForUpdate();
    }

    public void toggleFluidSubstitutionPatterns() {
        showFluidSubstitutionPatterns = !showFluidSubstitutionPatterns;
        rebuildPatternPreview();
        markForUpdate();
    }

    public void scrollPatternPreview(int delta) {
        int next = Math.max(0, Math.min(getPatternPreviewMaxScrollRow(), patternPreviewScrollRow + delta));
        if (next != patternPreviewScrollRow) {
            patternPreviewScrollRow = next;
            markForUpdate();
        }
    }

    public void ensurePatternPreview() {
        refreshPatternPreviewSources();
    }

    public void organizePatternBuses() {
        if (!(level instanceof ServerLevel) || !supportsCraftingInterfaceUi()) {
            return;
        }
        refreshPatternPreviewSources();
        List<ItemStack> patterns = new ArrayList<>();
        for (PatternPreviewEntry entry : allPatternPreviewEntries) {
            ItemStack stack = entry.bus().itemHandler.getStackInSlot(entry.slot());
            if (!stack.isEmpty()) patterns.add(stack.copy());
        }
        for (int index = 0; index < allPatternPreviewEntries.size(); index++) {
            PatternPreviewEntry entry = allPatternPreviewEntries.get(index);
            entry.bus()
                    .itemHandler
                    .setStackInSlot(entry.slot(), index < patterns.size() ? patterns.get(index) : ItemStack.EMPTY);
            entry.bus().notifyPersistence();
        }
        patternPreviewDataPrepared = true;
        rebuildPatternPreviewEntries();
        rebuildPatternPreview();
        markForUpdate();
    }

    public void startNetworkPatternTransfer() {
        if (!(level instanceof ServerLevel) || patternTransferTask != null) {
            return;
        }
        clearPatternTransferResults();
        patternTransferPerformed = true;
        IGrid grid = getMainNode().getGrid();
        IECOPatternStorageService storage = grid == null ? null : grid.getService(IECOPatternStorageService.class);
        if (!formed || grid == null || storage == null) {
            patternTransferUnavailable = true;
            markForUpdate();
            return;
        }
        List<PatternContainer> sources = getExternalPatternSources(grid);
        patternTransferTask = new PatternTransferTask(grid, storage, sources);
        patternTransferTotalSlots = patternTransferTask.totalSlots();
        patternTransferInProgress = true;
        markForUpdate();
    }

    public String getPatternTransferStatusKey() {
        if (patternTransferInProgress) return "gui.neoecoae.host.crafting.pattern_transfer.progress";
        if (!patternTransferPerformed) return "gui.neoecoae.host.crafting.pattern_transfer.ready";
        if (patternTransferUnavailable) return "gui.neoecoae.host.crafting.pattern_transfer.unavailable";
        return "gui.neoecoae.host.crafting.pattern_transfer.result_primary";
    }

    public int getPatternTransferStatusArg1() {
        return patternTransferInProgress ? patternTransferScannedSlots : patternTransferInserted;
    }

    public int getPatternTransferStatusArg2() {
        return patternTransferInProgress ? patternTransferTotalSlots : patternTransferAlreadyPresent;
    }

    public int getPatternTransferNoSpace() {
        return patternTransferNoSpace;
    }

    public int getPatternTransferIncompatible() {
        return patternTransferIncompatible;
    }

    public void setStorageInterfaceMode(ECOStorageInterfaceMode mode) {
        if (mode == null) {
            mode = ECOStorageInterfaceMode.STORAGE;
        }
        if (storageInterfaceMode == mode) {
            return;
        }
        storageInterfaceMode = mode;
        exportedLastTick = 0L;
        setChanged();
        markForUpdate();
        notifyStorageControllerModeChanged();
    }

    public void recordStorageInterfaceExport(long amount) {
        exportedLastTick = Math.max(0L, amount);
        if (amount > 0L) {
            exportedTotal = saturatedAdd(exportedTotal, amount);
            setChanged();
        }
    }

    public NEStorageInterfaceUiState createStorageInterfaceUiState() {
        boolean hasController =
                cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null;
        boolean targetOnline = getMainNode().isOnline() && getMainNode().getGrid() != null;
        return new NEStorageInterfaceUiState(
                worldPosition,
                formed,
                storageInterfaceMode,
                exportedLastTick,
                exportedTotal,
                targetOnline,
                hasController);
    }

    public NECraftingInterfaceUiState createCraftingInterfaceUiState() {
        return new NECraftingInterfaceUiState(
                worldPosition,
                formed,
                isTargetOnline(),
                patternPreviewSearch,
                showSubstitutionPatterns,
                showFluidSubstitutionPatterns,
                getPatternPreviewEntryCount(),
                patternPreviewScrollRow,
                getPatternPreviewMaxScrollRow(),
                getPatternTransferStatusKey(),
                getPatternTransferStatusArg1(),
                getPatternTransferStatusArg2(),
                patternTransferNoSpace,
                patternTransferIncompatible);
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (patternTransferTask != null) {
            tickPatternTransfer(serverLevel);
        }
        if (supportsCraftingInterfaceUi()) {
            refreshPatternPreviewSources();
        }
    }

    private void tickPatternTransfer(ServerLevel serverLevel) {
        PatternTransferTask task = patternTransferTask;
        if (task == null) return;
        if (!formed || getMainNode().getGrid() != task.grid()) {
            finishPatternTransfer(true);
            return;
        }
        int scanned = 0;
        int insertions = 0;
        while (scanned++ < TRANSFER_SLOTS_PER_TICK && insertions < TRANSFER_INSERTIONS_PER_TICK) {
            PatternTransferStep step = task.nextStep();
            if (step == null) {
                finishPatternTransfer(false);
                return;
            }
            patternTransferScannedSlots++;
            ItemStack stack = step.inventory().getStackInSlot(step.slot());
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) continue;
            if (!(PatternDetailsHelper.decodePattern(stack, serverLevel)
                    instanceof IMolecularAssemblerSupportedPattern)) {
                patternTransferIncompatible++;
                continue;
            }
            insertions++;
            switch (task.storage().getPatternStorage().insertPattern(stack.copy())) {
                case INSERTED -> {
                    step.inventory().setItemDirect(step.slot(), ItemStack.EMPTY);
                    patternTransferInserted++;
                }
                case ALREADY_PRESENT -> {
                    step.inventory().setItemDirect(step.slot(), ItemStack.EMPTY);
                    patternTransferAlreadyPresent++;
                }
                case NO_SPACE -> patternTransferNoSpace++;
                case INCOMPATIBLE -> patternTransferIncompatible++;
                case NO_TARGET -> {
                    finishPatternTransfer(false);
                    return;
                }
            }
        }
        markForUpdate();
    }

    private void finishPatternTransfer(boolean unavailable) {
        patternTransferTask = null;
        patternTransferInProgress = false;
        patternTransferUnavailable |= unavailable;
        markForUpdate();
    }

    private void clearPatternTransferResults() {
        patternTransferInProgress = false;
        patternTransferUnavailable = false;
        patternTransferScannedSlots = 0;
        patternTransferTotalSlots = 0;
        patternTransferInserted = 0;
        patternTransferAlreadyPresent = 0;
        patternTransferNoSpace = 0;
        patternTransferIncompatible = 0;
    }

    private List<PatternContainer> getExternalPatternSources(IGrid grid) {
        List<PatternContainer> sources = new ArrayList<>();
        Set<PatternContainer> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) continue;
            Class<? extends PatternContainer> type = machineClass.asSubclass(PatternContainer.class);
            for (PatternContainer container : grid.getActiveMachines(type)) {
                if (visited.add(container) && !(container instanceof ECOCraftingPatternBusBlockEntity)) {
                    sources.add(container);
                }
            }
        }
        return sources;
    }

    private void refreshPatternPreviewSources() {
        if (!(level instanceof ServerLevel) || !formed) {
            if (!allPatternPreviewEntries.isEmpty()) clearPatternPreview();
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            if (!allPatternPreviewEntries.isEmpty()) clearPatternPreview();
            return;
        }
        List<ECOCraftingPatternBusBlockEntity> buses =
                new ArrayList<>(grid.getActiveMachines(ECOCraftingPatternBusBlockEntity.class));
        buses.sort((left, right) ->
                Long.compare(left.getBlockPos().asLong(), right.getBlockPos().asLong()));
        if (buses.equals(patternPreviewBuses)) return;
        patternPreviewBuses = List.copyOf(buses);
        rebuildPatternPreviewEntries();
        rebuildPatternPreview();
        markForUpdate();
    }

    private void rebuildPatternPreviewEntries() {
        List<PatternPreviewEntry> entries = new ArrayList<>();
        for (ECOCraftingPatternBusBlockEntity bus : patternPreviewBuses) {
            for (int slot = 0; slot < bus.itemHandler.getSlots(); slot++) {
                entries.add(
                        patternPreviewDataPrepared
                                ? createPatternPreviewEntry(bus, slot)
                                : PatternPreviewEntry.empty(bus, slot));
            }
        }
        allPatternPreviewEntries = List.copyOf(entries);
        patternPreviewSearchCache.clear();
    }

    private void onPatternPreviewContentsChanged() {
        patternPreviewDataPrepared = true;
        rebuildPatternPreviewEntries();
        rebuildPatternPreview();
        markForUpdate();
    }

    private void clearPatternPreview() {
        patternPreviewBuses = List.of();
        allPatternPreviewEntries = List.of();
        patternPreviewEntries = List.of();
        patternPreviewDataPrepared = false;
        patternPreviewSearchCache.clear();
        patternPreviewScrollRow = 0;
    }

    private void rebuildPatternPreview() {
        if (patternPreviewSearch.isEmpty() && showSubstitutionPatterns && showFluidSubstitutionPatterns) {
            patternPreviewEntries = allPatternPreviewEntries;
        } else {
            List<PatternPreviewEntry> searchMatches = getPatternPreviewSearchMatches();
            List<PatternPreviewEntry> filtered = new ArrayList<>();
            List<PatternPreviewEntry> candidates =
                    patternPreviewSearch.isEmpty() ? allPatternPreviewEntries : searchMatches;
            for (PatternPreviewEntry entry : candidates) {
                if (entry.hasPattern() && matchesPatternPreviewFilters(entry)) {
                    filtered.add(entry);
                }
            }
            patternPreviewEntries = List.copyOf(filtered);
        }
        patternPreviewScrollRow = Math.max(0, Math.min(patternPreviewScrollRow, getPatternPreviewMaxScrollRow()));
    }

    private boolean matchesPatternPreviewFilters(PatternPreviewEntry entry) {
        return (showSubstitutionPatterns || !entry.canSubstitute())
                && (showFluidSubstitutionPatterns || !entry.canSubstituteFluids());
    }

    private List<PatternPreviewEntry> getPatternPreviewSearchMatches() {
        if (patternPreviewSearch.isEmpty()) {
            return List.of();
        }
        String search = patternPreviewSearch.toLowerCase(Locale.ROOT);
        List<PatternPreviewEntry> cached = patternPreviewSearchCache.get(search);
        if (cached != null) {
            return cached;
        }
        if (patternPreviewSearchCache.size() >= 128) {
            patternPreviewSearchCache.clear();
        }
        // Typing normally extends the previous query. Reusing the longest cached prefix makes
        // each keystroke scan only the entries that could still match it.
        List<PatternPreviewEntry> candidates = allPatternPreviewEntries;
        int longestPrefix = 0;
        for (Map.Entry<String, List<PatternPreviewEntry>> cacheEntry : patternPreviewSearchCache.entrySet()) {
            String prefix = cacheEntry.getKey();
            if (prefix.length() > longestPrefix && search.startsWith(prefix)) {
                longestPrefix = prefix.length();
                candidates = cacheEntry.getValue();
            }
        }
        List<PatternPreviewEntry> matches = new ArrayList<>();
        List<String> queryTokens = tokenizePatternSearch(search);
        for (PatternPreviewEntry entry : candidates) {
            if (entry.matchesSearchTokens(queryTokens)) {
                matches.add(entry);
            }
        }
        List<PatternPreviewEntry> immutableMatches = List.copyOf(matches);
        patternPreviewSearchCache.put(search, immutableMatches);
        return immutableMatches;
    }

    private PatternPreviewEntry createPatternPreviewEntry(ECOCraftingPatternBusBlockEntity bus, int slot) {
        ItemStack pattern = bus.itemHandler.getStackInSlot(slot);
        var details = PatternDetailsHelper.decodePattern(pattern, level);
        if (details == null) {
            return PatternPreviewEntry.empty(bus, slot);
        }
        GenericStack primaryOutput = details.getPrimaryOutput();
        ItemStack displayStack =
                primaryOutput == null ? ItemStack.EMPTY : primaryOutput.what().wrapForDisplayOrFilter();
        List<List<String>> outputs = new ArrayList<>();
        for (GenericStack output : details.getOutputs()) {
            if (output != null) {
                outputs.add(tokenizePatternSearch(normalizedDisplayName(output)));
            }
        }
        List<List<String>> inputs = new ArrayList<>();
        for (var input : details.getInputs()) {
            for (GenericStack option : input.getPossibleInputs()) {
                if (option != null) {
                    inputs.add(tokenizePatternSearch(normalizedDisplayName(option)));
                }
            }
        }
        boolean canSubstitute = details instanceof AECraftingPattern craftingPattern && craftingPattern.canSubstitute();
        boolean canSubstituteFluids =
                details instanceof AECraftingPattern craftingPattern && craftingPattern.canSubstituteFluids();
        return new PatternPreviewEntry(
                bus, slot, displayStack, List.copyOf(outputs), List.copyOf(inputs), canSubstitute, canSubstituteFluids);
    }

    private static String normalizedDisplayName(GenericStack stack) {
        return stack.what().getDisplayName().getString().toLowerCase(Locale.ROOT);
    }

    private static List<String> tokenizePatternSearch(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : value.trim().split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token.trim());
            }
        }
        return List.copyOf(tokens);
    }

    @Nullable private PatternPreviewEntry getPatternPreviewEntry(int visibleSlot) {
        int entry = patternPreviewScrollRow * PREVIEW_COLUMNS + visibleSlot;
        return entry >= 0 && entry < patternPreviewEntries.size() ? patternPreviewEntries.get(entry) : null;
    }

    private static final class PatternPreviewEntry {
        private final ECOCraftingPatternBusBlockEntity bus;
        private final int slot;
        private final ItemStack displayStack;
        private final List<List<String>> outputs;
        private final List<List<String>> inputs;
        private final boolean canSubstitute;
        private final boolean canSubstituteFluids;

        private PatternPreviewEntry(
                ECOCraftingPatternBusBlockEntity bus,
                int slot,
                ItemStack displayStack,
                List<List<String>> outputs,
                List<List<String>> inputs,
                boolean canSubstitute,
                boolean canSubstituteFluids) {
            this.bus = bus;
            this.slot = slot;
            this.displayStack = displayStack;
            this.outputs = outputs;
            this.inputs = inputs;
            this.canSubstitute = canSubstitute;
            this.canSubstituteFluids = canSubstituteFluids;
        }

        private static PatternPreviewEntry empty(ECOCraftingPatternBusBlockEntity bus, int slot) {
            return new PatternPreviewEntry(bus, slot, ItemStack.EMPTY, List.of(), List.of(), false, false);
        }

        private ECOCraftingPatternBusBlockEntity bus() {
            return bus;
        }

        private int slot() {
            return slot;
        }

        private ItemStack displayStack() {
            return displayStack;
        }

        private boolean hasPattern() {
            return !displayStack.isEmpty();
        }

        private boolean canSubstitute() {
            return canSubstitute;
        }

        private boolean canSubstituteFluids() {
            return canSubstituteFluids;
        }

        private boolean matchesSearchTokens(List<String> queryTokens) {
            return outputs.stream().anyMatch(name -> matchesTokens(queryTokens, name))
                    || inputs.stream().anyMatch(name -> matchesTokens(queryTokens, name));
        }

        private static boolean matchesTokens(List<String> queryTokens, List<String> nameTokens) {
            for (int start = 0; start <= nameTokens.size() - queryTokens.size(); start++) {
                int queryIndex = 0;
                while (queryIndex < queryTokens.size()
                        && nameTokens.get(start + queryIndex).contains(queryTokens.get(queryIndex))) {
                    queryIndex++;
                }
                if (queryIndex == queryTokens.size()) {
                    return true;
                }
            }
            return false;
        }
    }

    private record PatternTransferStep(InternalInventory inventory, int slot) {}

    private final class PatternTransferTask {
        private final IGrid grid;
        private final IECOPatternStorageService storage;
        private final List<PatternContainer> sources;
        private int sourceIndex;
        private int slotIndex;

        private PatternTransferTask(IGrid grid, IECOPatternStorageService storage, List<PatternContainer> sources) {
            this.grid = grid;
            this.storage = storage;
            this.sources = sources;
        }

        private IGrid grid() {
            return grid;
        }

        private IECOPatternStorageService storage() {
            return storage;
        }

        private int totalSlots() {
            return sources.stream()
                    .mapToInt(source -> source.getTerminalPatternInventory().size())
                    .sum();
        }

        @Nullable private PatternTransferStep nextStep() {
            while (sourceIndex < sources.size()) {
                PatternContainer source = sources.get(sourceIndex);
                if (source.getGrid() != grid) {
                    sourceIndex++;
                    slotIndex = 0;
                    continue;
                }
                InternalInventory inventory = source.getTerminalPatternInventory();
                if (slotIndex < inventory.size()) return new PatternTransferStep(inventory, slotIndex++);
                sourceIndex++;
                slotIndex = 0;
            }
            return null;
        }
    }

    private final class PatternPreviewItemHandler implements IItemHandlerModifiable {
        private final ItemStack[] clientStacks = new ItemStack[PREVIEW_SLOTS];

        private PatternPreviewItemHandler() {
            Arrays.fill(clientStacks, ItemStack.EMPTY);
        }

        @Override
        public int getSlots() {
            return PREVIEW_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= PREVIEW_SLOTS) return ItemStack.EMPTY;
            if (level != null && level.isClientSide) return clientStacks[slot];
            PatternPreviewEntry entry = getPatternPreviewEntry(slot);
            return entry == null ? ItemStack.EMPTY : entry.bus().itemHandler.getStackInSlot(entry.slot());
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return stack;
            }
            if (level != null && level.isClientSide) {
                return stack;
            }
            for (PatternPreviewEntry entry : allPatternPreviewEntries) {
                if (entry.bus().itemHandler.getStackInSlot(entry.slot()).isEmpty()) {
                    ItemStack remaining = entry.bus().itemHandler.insertItem(entry.slot(), stack, simulate);
                    if (!simulate && remaining.getCount() != stack.getCount()) {
                        entry.bus().notifyPersistence();
                        onPatternPreviewContentsChanged();
                    }
                    return remaining;
                }
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= PREVIEW_SLOTS || level != null && level.isClientSide) {
                return ItemStack.EMPTY;
            }
            PatternPreviewEntry entry = getPatternPreviewEntry(slot);
            if (entry == null) {
                return ItemStack.EMPTY;
            }
            ItemStack result = entry.bus().itemHandler.extractItem(entry.slot(), amount, simulate);
            if (!simulate && !result.isEmpty()) {
                entry.bus().notifyPersistence();
                onPatternPreviewContentsChanged();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < PREVIEW_SLOTS ? 1 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < PREVIEW_SLOTS && PatternDetailsHelper.isEncodedPattern(stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (level != null && level.isClientSide) {
                if (slot >= 0 && slot < PREVIEW_SLOTS) {
                    clientStacks[slot] = stack.copy();
                }
                return;
            }
            if (slot < 0 || slot >= PREVIEW_SLOTS) {
                return;
            }
            PatternPreviewEntry entry = getPatternPreviewEntry(slot);
            if (entry == null) {
                return;
            }
            entry.bus().itemHandler.setStackInSlot(entry.slot(), stack);
            entry.bus().notifyPersistence();
            onPatternPreviewContentsChanged();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModularUI createUI(Player player) {
        if (supportsStorageInterfaceUi()) {
            return NELDLibUis.createStorageInterface((ECOMachineInterfaceBlockEntity<NEStorageCluster>) this, player);
        }
        if (supportsCraftingInterfaceUi()) {
            // The terminal opens in the same compact arrangement as its explicit organize action.
            organizePatternBuses();
            return NELDLibUis.createCraftingInterface((ECOMachineInterfaceBlockEntity<NECraftingCluster>) this, player);
        }
        return null;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_STORAGE_INTERFACE_MODE, storageInterfaceMode.name());
        tag.putLong("exportedTotal", exportedTotal);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        storageInterfaceMode = ECOStorageInterfaceMode.byName(tag.getString(NBT_STORAGE_INTERFACE_MODE));
        exportedTotal = Math.max(0L, tag.getLong("exportedTotal"));
    }

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putString(NBT_STORAGE_INTERFACE_MODE, storageInterfaceMode.name());
        tag.putLong("exportedLastTick", exportedLastTick);
        tag.putLong("exportedTotal", exportedTotal);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        storageInterfaceMode = ECOStorageInterfaceMode.byName(tag.getString(NBT_STORAGE_INTERFACE_MODE));
        exportedLastTick = Math.max(0L, tag.getLong("exportedLastTick"));
        exportedTotal = Math.max(0L, tag.getLong("exportedTotal"));
    }

    private void notifyStorageControllerModeChanged() {
        if (level == null || level.isClientSide || !(cluster instanceof NEStorageCluster storageCluster)) {
            return;
        }
        if (storageCluster.getController() != null) {
            storageCluster.getController().onStorageInterfaceModeChanged();
        }
    }

    private static long saturatedAdd(long left, long right) {
        return LongMath.saturatedAdd(left, right);
    }
}
