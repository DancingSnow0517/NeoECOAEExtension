package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.orientation.BlockOrientation;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.gui.computation.ComputationInterfaceUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageInterfaceUI;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RPCMethod;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class ECOMachineInterfaceBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, ECOMachineInterfaceBlockEntity<C>>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost {
    private static final int PATTERN_TRANSFER_MAX_SLOTS_PER_TICK = 24;
    private static final int PATTERN_TRANSFER_MAX_INSERTIONS_PER_TICK = 8;
    private static final long PATTERN_TRANSFER_MAX_NANOS_PER_TICK = 4_000_000L;
    private static final long PATTERN_TRANSFER_SYNC_INTERVAL_TICKS = 5L;
    public static final int FUZZY_PLANNING_SLOT_COUNT = 36;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    @Persisted
    @DescSynced
    private ECOStorageInterfaceMode storageInterfaceMode = ECOStorageInterfaceMode.STORAGE;
    @Persisted
    @DescSynced
    private final AppEngInternalInventory fuzzyPlanningInventory = new AppEngInternalInventory(
        this, FUZZY_PLANNING_SLOT_COUNT, 1
    );
    private final IItemHandlerModifiable fuzzyPlanningItemHandler =
        (IItemHandlerModifiable) fuzzyPlanningInventory.toItemHandler();
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
    /**
     * The physical F-bus slot order, encoded as one NBT string for client-local preview filtering.
     * ldlib2's array accessor cannot safely create refs for ItemStack[] elements.
     */
    @DescSynced
    private String patternPreviewSnapshotData = "";
    private transient ItemStack[] decodedPatternPreviewSnapshot = new ItemStack[0];
    private transient String decodedPatternPreviewSnapshotData;
    private List<PatternContainer> patternPreviewSources = List.of();
    private List<PatternPreviewEntry> allPatternPreviewEntries = new ArrayList<>();
    private boolean patternPreviewInitialized;
    private long lastPatternPreviewSyncTick = Long.MIN_VALUE;
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
    public boolean supportsComputationInterfaceUi() {
        return cluster instanceof NEComputationCluster || calculator instanceof NEComputationClusterCalculator;
    }
    public boolean supportsInterfaceUi() {
        return supportsStorageInterfaceUi() || supportsCraftingInterfaceUi() || supportsComputationInterfaceUi();
    }

    public IItemHandlerModifiable getFuzzyPlanningItemHandler() {
        return fuzzyPlanningItemHandler;
    }

    public Set<ResourceLocation> getFuzzyPlanningItemIds() {
        Set<ResourceLocation> result = new java.util.LinkedHashSet<>();
        for (int slot = 0; slot < fuzzyPlanningInventory.size(); slot++) {
            ItemStack stack = fuzzyPlanningInventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                result.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
        return Set.copyOf(result);
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

    public ItemStack[] getPatternPreviewSnapshot() {
        if (!java.util.Objects.equals(decodedPatternPreviewSnapshotData, patternPreviewSnapshotData)) {
            decodedPatternPreviewSnapshot = decodePatternPreviewSnapshot(patternPreviewSnapshotData,
                    level == null ? null : level.registryAccess());
            decodedPatternPreviewSnapshotData = patternPreviewSnapshotData;
        }
        return decodedPatternPreviewSnapshot;
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

    /**
     * Client-local slots cannot participate in vanilla container clicks. Preserve the useful take action by
     * resolving the client's snapshot index on the server, where the backing F-bus inventory is authoritative.
     */
    @RPCMethod
    public void takePatternPreviewEntry(RPCSender sender, int sourceIndex, boolean single) {
        if (sender.isServer() || !(level instanceof ServerLevel) || sourceIndex < 0
                || sourceIndex >= allPatternPreviewEntries.size()) {
            return;
        }
        ServerPlayer player = sender.asPlayer();
        PatternPreviewEntry entry = allPatternPreviewEntries.get(sourceIndex);
        if (player == null || !isPreviewSourceActive(entry)) {
            return;
        }
        InternalInventory inventory = getPreviewInventory(entry);
        ItemStack current = inventory.getStackInSlot(entry.sourceSlot());
        if (current.isEmpty()) {
            return;
        }
        ItemStack taken = inventory.extractItem(entry.sourceSlot(), single ? 1 : current.getCount(), false);
        if (!taken.isEmpty()) {
            player.getInventory().placeItemBackInInventory(taken);
        }
    }

    /** Receives the old pattern-terminal Shift-click path from the client-local player inventory slots. */
    @RPCMethod
    public void insertPatternFromPlayer(RPCSender sender, int inventorySlot) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)
                || inventorySlot < 0 || inventorySlot >= 36 || !formed || !supportsCraftingInterfaceUi()) {
            return;
        }
        ServerPlayer player = sender.asPlayer();
        if (player == null || player.level() != serverLevel) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return;
        }

        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        for (PatternContainer source : getFPatternSources(grid)) {
            InternalInventory inventory = source.getTerminalPatternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty()) {
                    continue;
                }
                ItemStack remaining = inventory.insertItem(slot, stack.copy(), false);
                if (remaining.getCount() != stack.getCount()) {
                    player.getInventory().setItem(inventorySlot, remaining);
                    refreshPatternPreviewSnapshot();
                    syncPatternPreviewState(serverLevel.getGameTime(), true);
                    return;
                }
            }
        }
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
        List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>(
                grid.getActiveMachines(ECOCraftingPatternBusBlockEntity.class));
        buses.sort(Comparator.comparingLong(bus -> bus.getBlockPos().asLong()));
        return new ArrayList<>(buses);
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

    /** Builds the server-side F-bus address book and its client snapshot. */
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
        refreshPatternPreviewSnapshot();
        syncPatternPreviewState(serverLevel.getGameTime(), true);
    }

    /**
     * Refresh the address book only when buses are attached or removed, and the snapshot whenever a source slot
     * changes. Search data deliberately never enters this server-side path.
     */
    private void tickPatternPreviewCache(ServerLevel serverLevel) {
        IGrid grid = getMainNode().getGrid();
        if (!formed || grid == null) {
            clearPatternPreview();
            syncPatternPreviewState(serverLevel.getGameTime(), false);
            return;
        }
        List<PatternContainer> sources = getFPatternSources(grid);
        if (!samePatternPreviewSources(sources)) {
            loadPatternPreview(serverLevel);
            return;
        }
        if (refreshPatternPreviewSnapshot()) {
            syncPatternPreviewState(serverLevel.getGameTime(), false);
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
        patternPreviewSnapshotData = "";
        patternPreviewInitialized = false;
    }

    private boolean refreshPatternPreviewSnapshot() {
        ItemStack[] next = new ItemStack[allPatternPreviewEntries.size()];
        ItemStack[] previous = getPatternPreviewSnapshot();
        boolean changed = next.length != previous.length;
        for (int index = 0; index < next.length; index++) {
            PatternPreviewEntry entry = allPatternPreviewEntries.get(index);
            ItemStack stack = isPreviewSourceActive(entry)
                    ? getPreviewInventory(entry).getStackInSlot(entry.sourceSlot()) : ItemStack.EMPTY;
            next[index] = stack == null ? ItemStack.EMPTY : stack.copy();
            changed |= index >= previous.length || !ItemStack.matches(previous[index], next[index]);
        }
        if (changed) {
            patternPreviewSnapshotData = encodePatternPreviewSnapshot(next,
                    level == null ? null : level.registryAccess());
            decodedPatternPreviewSnapshot = next;
            decodedPatternPreviewSnapshotData = patternPreviewSnapshotData;
        }
        return changed;
    }

    private static String encodePatternPreviewSnapshot(ItemStack[] stacks, @Nullable HolderLookup.Provider registries) {
        if (registries == null) {
            return "";
        }
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add((stack == null ? ItemStack.EMPTY : stack).save(registries));
        }
        return list.toString();
    }

    private static ItemStack[] decodePatternPreviewSnapshot(String data, @Nullable HolderLookup.Provider registries) {
        if (data == null || data.isEmpty() || registries == null) {
            return new ItemStack[0];
        }
        try {
            Tag parsed = new TagParser(new StringReader(data)).readValue();
            if (!(parsed instanceof ListTag list)) {
                return new ItemStack[0];
            }
            ItemStack[] result = new ItemStack[list.size()];
            for (int index = 0; index < list.size(); index++) {
                result[index] = ItemStack.parseOptional(registries, list.getCompound(index));
            }
            return result;
        } catch (CommandSyntaxException | RuntimeException ignored) {
            return new ItemStack[0];
        }
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
        if (supportsComputationInterfaceUi()) {
            return ComputationInterfaceUI.create((ECOMachineInterfaceBlockEntity<NEComputationCluster>) this, holder.player);
        }
        return null;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        setChanged();
        markForUpdate();
    }

    @Override
    public boolean isClientSide() {
        return level != null && level.isClientSide;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.allOf(Direction.class);
    }
}
