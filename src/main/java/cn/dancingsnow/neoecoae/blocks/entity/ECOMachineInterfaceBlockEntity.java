package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.orientation.BlockOrientation;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import cn.dancingsnow.neoecoae.api.ECOPatternSourceSlot;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.grid.PatternMigrationCoordinator;
import cn.dancingsnow.neoecoae.grid.PatternCatalog;
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
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewEntry;
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewSync;
import cn.dancingsnow.neoecoae.gui.computation.ComputationInterfaceUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageInterfaceUI;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ECOMachineInterfaceBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, ECOMachineInterfaceBlockEntity<C>>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost {
    private static final int PATTERN_TRANSFER_SAFETY_LIMIT_PER_TICK = 256;
    private static final long PATTERN_TRANSFER_SYNC_INTERVAL_TICKS = 5L;
    private static final int PATTERN_ORGANIZE_SAFETY_LIMIT_PER_TICK = 256;
    public static final int FUZZY_PLANNING_SLOT_COUNT = 63;
    public static final int PATTERN_INTERFACE_VISIBLE_SLOTS = 36;
    private static final String PATTERN_BUS_POSITIONS_SYNC_KEY = "patternBusPositions";
    private static final String PATTERN_BUS_SLOT_COUNTS_SYNC_KEY = "patternBusSlotCounts";

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
    private boolean patternTransferIndexing;
    @DescSynced
    private int patternTransferScannedSlots;
    @DescSynced
    private int patternTransferTotalSlots;
    @Nullable
    private PatternTransferTask patternTransferTask;
    @DescSynced
    private boolean patternOrganizeInProgress;
    @DescSynced
    private int patternOrganizeScannedSlots;
    @DescSynced
    private int patternOrganizeTotalSlots;
    @DescSynced
    private boolean patternOrganizePerformed;
    @DescSynced
    private int patternOrganizeInvalidRecovered;
    @DescSynced
    private int patternOrganizeDuplicatesRecovered;
    @DescSynced
    private int patternOrganizeRecoveryBlocked;
    @Nullable
    private PatternOrganizeTask patternOrganizeTask;
    private long lastPatternTransferSyncTick = Long.MIN_VALUE;
    private long[] patternBusPositions = new long[0];
    private int[] patternBusSlotCounts = new int[0];
    @DescSynced
    private int patternContentRevision;
    private transient List<PatternSlotRef> patternSlotRefs = List.of();
    private transient boolean patternInterfaceMappingInitialized;
    @Getter
    private final transient PatternPreviewSync patternPreviewSync = new PatternPreviewSync(this);
    private transient int migrationScannedThisTick;
    private transient int migrationInsertedThisTick;
    public ECOMachineInterfaceBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<C> calculator
    ) {
        super(type, pos, blockState, calculator);
    }

    @Override
    public synchronized void writeCustomSyncData(HolderLookup.Provider provider, CompoundTag tag) {
        // LDLib2's DirectArrayRef can expose a partially rebuilt element-ref array while a dynamically sized
        // primitive array is replaced. Serialize both mapping arrays as one custom snapshot instead.
        tag.putLongArray(PATTERN_BUS_POSITIONS_SYNC_KEY, patternBusPositions);
        tag.putIntArray(PATTERN_BUS_SLOT_COUNTS_SYNC_KEY, patternBusSlotCounts);
    }

    @Override
    public synchronized void readCustomSyncData(HolderLookup.Provider provider, CompoundTag tag) {
        long[] positions = tag.getLongArray(PATTERN_BUS_POSITIONS_SYNC_KEY);
        int[] slotCounts = tag.getIntArray(PATTERN_BUS_SLOT_COUNTS_SYNC_KEY);
        if (positions.length != slotCounts.length) {
            patternBusPositions = new long[0];
            patternBusSlotCounts = new int[0];
            return;
        }
        for (int index = 0; index < slotCounts.length; index++) {
            slotCounts[index] = Math.max(0, slotCounts[index]);
        }
        patternBusPositions = positions;
        patternBusSlotCounts = slotCounts;
    }

    public ECOStorageInterfaceMode getStorageInterfaceMode() { return storageInterfaceMode; }
    public long getTransferredLastTick() { return transferredLastTick; }
    public boolean isStorageInputMode() { return storageInterfaceMode == ECOStorageInterfaceMode.INPUT; }
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

    /** Stores the sample selected by a client-side JEI ghost drop without moving a real item. */
    @RPCMethod
    public void setFuzzyPlanningFilter(RPCSender sender, int slot, ItemStack stack) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)
            || slot < 0 || slot >= fuzzyPlanningInventory.size() || !supportsComputationInterfaceUi()) {
            return;
        }
        ServerPlayer player = sender.asPlayer();
        if (player == null || player.level() != serverLevel
            || player.blockPosition().distSqr(worldPosition) > 64.0D) {
            return;
        }
        fuzzyPlanningItemHandler.setStackInSlot(
            slot,
            stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)
        );
        setChanged();
        markForUpdate();
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
        if (patternTransferTask != null || patternOrganizeTask != null || !(level instanceof ServerLevel serverLevel)) {
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
        if (!task.coordinator().tryAcquire(this)) {
            return;
        }
        clearPatternOrganizeResults();
        patternTransferTask = task;
        patternTransferInProgress = true;
        patternTransferIndexing = true;
        patternTransferTotalSlots = task.totalSlots();
        syncPatternTransferState(serverLevel.getGameTime(), true);
    }

    public Component getPatternTransferPrimaryStatus() {
        if (patternOrganizeInProgress) {
            return Component.translatable(
                    "gui.neoecoae.crafting_interface.preview.organizing",
                    getPatternTransferProgressPercent());
        }
        if (patternTransferInProgress) {
            return Component.translatable(
                    patternTransferIndexing
                            ? "gui.neoecoae.host.crafting.pattern_transfer.indexing"
                            : "gui.neoecoae.host.crafting.pattern_transfer.progress",
                    getPatternTransferProgressPercent());
        }
        if (patternOrganizePerformed) {
            return Component.translatable(
                    "gui.neoecoae.crafting_interface.preview.organize.result_primary",
                    patternOrganizeInvalidRecovered,
                    patternOrganizeDuplicatesRecovered);
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
        if (patternOrganizePerformed) {
            return patternOrganizeRecoveryBlocked > 0
                    ? Component.translatable(
                            "gui.neoecoae.crafting_interface.preview.organize.result_secondary",
                            patternOrganizeRecoveryBlocked)
                    : Component.empty();
        }
        if (patternOrganizeInProgress || !patternTransferPerformed || patternTransferUnavailable
                || (patternTransferInserted == 0 && patternTransferAlreadyPresent == 0 && patternTransferNoTarget > 0)
                || (patternTransferNoSpace == 0 && patternTransferIncompatible == 0)) {
            return Component.empty();
        }
        return Component.translatable(
                "gui.neoecoae.host.crafting.pattern_transfer.result_secondary",
                patternTransferNoSpace,
                patternTransferIncompatible);
    }

    public boolean hasPatternTransferSecondaryStatus() {
        return (patternOrganizePerformed && patternOrganizeRecoveryBlocked > 0)
                || (!patternOrganizeInProgress && patternTransferPerformed && !patternTransferUnavailable
                && !(patternTransferInserted == 0 && patternTransferAlreadyPresent == 0 && patternTransferNoTarget > 0)
                && (patternTransferNoSpace > 0 || patternTransferIncompatible > 0));
    }

    public PatternPreviewEntry getPatternPreviewEntry(int index) {
        PatternSlotRef ref = patternSlotRefs.get(index);
        PatternCatalog.PatternRecord record = getCatalogRecord(ref);
        ItemStack stack = record == null ? ItemStack.EMPTY : record.stack().copy();
        return new PatternPreviewEntry(ref.bus().getBlockPos().asLong(), ref.slot(), stack,
                record == null ? "" : record.searchKeywords(), patternSearchFlags(stack));
    }

    @Nullable
    private PatternCatalog.PatternRecord getCatalogRecord(PatternSlotRef ref) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        IECOPatternStorageService service = grid.getService(IECOPatternStorageService.class);
        return service instanceof PatternCatalog catalog
                ? catalog.getPatternRecord(ref.bus(), ref.slot())
                : null;
    }

    public void refreshPatternCatalog() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        IECOPatternStorageService service = grid.getService(IECOPatternStorageService.class);
        if (service instanceof PatternCatalog catalog) {
            catalog.refresh();
        }
    }

    @RPCMethod
    public void setPatternPreview(RPCSender sender, CompoundTag payload) {
        if (sender.isServer() && level != null && level.isClientSide && payload != null) {
            patternPreviewSync.receive(payload);
        }
    }

    /** Resolve physical identity only when acting; browsing never modifies server state. */
    @RPCMethod
    public void actOnPatternPreview(RPCSender sender, CompoundTag payload) {
        if (sender.isServer() || !(level instanceof ServerLevel) || !formed
                || !supportsCraftingInterfaceUi() || payload == null) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null || !patternPreviewSync.isViewer(player)
                || payload.getInt("menu") != player.containerMenu.containerId) return;
        ensurePatternInterfaceMapping();
        if (payload.getInt("revision") != patternContentRevision) {
            patternPreviewSync.resend(player);
            player.containerMenu.broadcastFullState();
            return;
        }
        long busPosition = payload.getLong("bus");
        int physicalSlot = payload.getInt("slot");
        PatternSlotRef target = null;
        for (PatternSlotRef ref : patternSlotRefs) {
            if (ref.slot() == physicalSlot && ref.bus().getBlockPos().asLong() == busPosition) {
                target = ref;
                break;
            }
        }
        if (target == null || target.bus().isRemoved() || target.bus().getGrid() != getMainNode().getGrid()) return;
        InternalInventory inventory = target.bus().getTerminalPatternInventory();
        if (physicalSlot < 0 || physicalSlot >= inventory.size()) return;
        ItemStack existing = inventory.getStackInSlot(physicalSlot);
        ItemStack carried = player.containerMenu.getCarried();
        int action = payload.getInt("action");
        int button = payload.getInt("button");
        if (button < 0 || button > 1) return;
        if (action == 2) {
            int sourceSlot = payload.getInt("sourceSlot");
            if (sourceSlot < 0 || sourceSlot >= 36 || !carried.isEmpty()) return;
            ItemStack source = player.getInventory().getItem(sourceSlot);
            ItemStack expected = ItemStack.parseOptional(level.registryAccess(), payload.getCompound("sourceStack"));
            if (source.isEmpty() || !ItemStack.matches(source, expected) || !existing.isEmpty()
                    || !inventory.isItemValid(physicalSlot, source) || hasDuplicatePattern(target, source)) return;
            ItemStack remainder = inventory.insertItem(physicalSlot, source.copy(), false);
            player.getInventory().setItem(sourceSlot, remainder);
        } else if (action == 1) {
            ItemStack available = inventory.extractItem(physicalSlot, Integer.MAX_VALUE, true);
            if (!available.isEmpty() && canStoreInPlayerInventory(player, available)) {
                ItemStack extracted = inventory.extractItem(physicalSlot, available.getCount(), false);
                player.getInventory().add(extracted);
                if (!extracted.isEmpty()) player.drop(extracted, false);
            }
        } else if (action == 0) {
            if (carried.isEmpty()) {
                int amount = button == 1 ? (existing.getCount() + 1) / 2 : existing.getCount();
                player.containerMenu.setCarried(inventory.extractItem(physicalSlot, amount, false));
            } else if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, carried)) {
                if (inventory.isItemValid(physicalSlot, carried) && !hasDuplicatePattern(target, carried)) {
                    int amount = button == 1 ? 1 : carried.getCount();
                    ItemStack remainder = inventory.insertItem(physicalSlot, carried.copyWithCount(amount), false);
                    carried.shrink(amount - remainder.getCount());
                    player.containerMenu.setCarried(carried);
                }
            } else if (inventory.isItemValid(physicalSlot, carried) && !hasDuplicatePattern(target, carried)
                    && carried.getCount() <= Math.min(inventory.getSlotLimit(physicalSlot), carried.getMaxStackSize())) {
                ItemStack removed = existing.copy();
                inventory.setItemDirect(physicalSlot, carried.copy());
                player.containerMenu.setCarried(removed);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /** Executes a client drag gesture atomically with respect to its starting preview revision. */
    @RPCMethod
    public void quickMovePatternPreview(RPCSender sender, CompoundTag payload) {
        if (sender.isServer() || !(level instanceof ServerLevel) || !formed
                || !supportsCraftingInterfaceUi() || payload == null) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null || !patternPreviewSync.isViewer(player)
                || payload.getInt("menu") != player.containerMenu.containerId) return;
        ensurePatternInterfaceMapping();
        if (payload.getInt("revision") != patternContentRevision) {
            patternPreviewSync.resend(player);
            player.containerMenu.broadcastFullState();
            return;
        }
        ListTag targets = payload.getList("targets", Tag.TAG_COMPOUND);
        int targetCount = Math.min(targets.size(), PATTERN_INTERFACE_VISIBLE_SLOTS);
        for (int index = 0; index < targetCount; index++) {
            CompoundTag targetTag = targets.getCompound(index);
            int physicalSlot = targetTag.getInt("slot");
            PatternSlotRef target = resolvePatternSlot(targetTag.getLong("bus"), physicalSlot);
            if (target == null || target.bus().isRemoved() || target.bus().getGrid() != getMainNode().getGrid()) {
                continue;
            }
            InternalInventory inventory = target.bus().getTerminalPatternInventory();
            if (physicalSlot < 0 || physicalSlot >= inventory.size()) continue;
            ItemStack existing = inventory.getStackInSlot(physicalSlot);
            ItemStack expected = ItemStack.parseOptional(level.registryAccess(), targetTag.getCompound("stack"));
            if (existing.isEmpty() || !ItemStack.matches(existing, expected)) continue;
            ItemStack available = inventory.extractItem(physicalSlot, Integer.MAX_VALUE, true);
            if (available.isEmpty() || !canStoreInPlayerInventory(player, available)) continue;
            ItemStack extracted = inventory.extractItem(physicalSlot, available.getCount(), false);
            player.getInventory().add(extracted);
            if (!extracted.isEmpty()) player.drop(extracted, false);
        }
        player.containerMenu.broadcastChanges();
    }

    @Nullable
    private PatternSlotRef resolvePatternSlot(long busPosition, int physicalSlot) {
        for (PatternSlotRef ref : patternSlotRefs) {
            if (ref.slot() == physicalSlot && ref.bus().getBlockPos().asLong() == busPosition) return ref;
        }
        return null;
    }

    public boolean isPatternTransferInProgress() {
        return patternTransferInProgress || patternOrganizeInProgress;
    }

    public float getPatternTransferProgress() {
        if (patternOrganizeInProgress) {
            if (patternOrganizeTotalSlots <= 0) {
                return 1.0F;
            }
            return Math.clamp(
                    (float) patternOrganizeScannedSlots / patternOrganizeTotalSlots,
                    0.0F,
                    1.0F);
        }
        if (patternTransferTotalSlots <= 0) {
            return patternTransferInProgress ? 0.0F : 1.0F;
        }
        return Math.clamp((float) patternTransferScannedSlots / patternTransferTotalSlots, 0.0F, 1.0F);
    }

    private int getPatternTransferProgressPercent() {
        return Math.round(getPatternTransferProgress() * 100.0F);
    }

    public int getPatternInterfaceSlotCount() {
        ensurePatternInterfaceMapping();
        if (level != null && level.isClientSide) {
            int total = 0;
            for (int count : patternBusSlotCounts) {
                total += Math.max(0, count);
            }
            return total;
        }
        return patternSlotRefs.size();
    }

    public ItemStack getPatternInterfaceStack(int slot) {
        ensurePatternInterfaceMapping();
        return getPatternStack(slot);
    }

    public int getPatternContentRevision() {
        return patternContentRevision;
    }

    @Override
    public void updateCluster(@Nullable C nextCluster) {
        releasePatternMigrationLease();
        super.updateCluster(nextCluster);
        patternInterfaceMappingInitialized = false;
        patternSlotRefs = List.of();
        if (level instanceof ServerLevel) {
            closePatternInterfaceMenus();
        }
    }

    @Override
    protected void onMainNodeGridChanged() {
        releasePatternMigrationLease();
        super.onMainNodeGridChanged();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        patternInterfaceMappingInitialized = false;
        patternSlotRefs = List.of();
        ensurePatternInterfaceMapping();
    }

    @Override
    public void onChunkUnloaded() {
        releasePatternMigrationLease();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        releasePatternMigrationLease();
        super.setRemoved();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        super.onMainNodeStateChanged(reason);
        if (reason == IGridNodeListener.State.POWER || reason == IGridNodeListener.State.GRID_BOOT) {
            patternInterfaceMappingInitialized = false;
            if (level instanceof ServerLevel) {
                ensurePatternInterfaceMapping();
            }
        }
    }

    /** Called when a Pattern Bus joins or leaves the grid. */
    public void onPatternBusTopologyChanged(ECOCraftingPatternBusBlockEntity bus) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        patternInterfaceMappingInitialized = false;
        ensurePatternInterfaceMapping();
    }

    /** Called by a Pattern Bus after a real slot mutation; this path never scans the inventory. */
    public void onPatternBusInventoryChanged(ECOCraftingPatternBusBlockEntity bus) {
        onPatternBusInventoryChanged(bus, -1);
    }

    public void onPatternBusInventoryChanged(ECOCraftingPatternBusBlockEntity bus, int changedSlot) {
        if (!(level instanceof ServerLevel) || !patternInterfaceMappingInitialized) {
            return;
        }
        int busIndex = Arrays.binarySearch(patternBusPositions, bus.getBlockPos().asLong());
        int slotCount = bus.getPatternSlotCount();
        boolean mappingInvalid = busIndex < 0 || busIndex >= patternBusSlotCounts.length
                || patternBusSlotCounts[busIndex] != slotCount
                || !isMappedBus(busIndex, bus);
        if (mappingInvalid) {
            patternInterfaceMappingInitialized = false;
            ensurePatternInterfaceMapping();
        }
        busIndex = Arrays.binarySearch(patternBusPositions, bus.getBlockPos().asLong());
        if (busIndex < 0) return;
        int offset = 0;
        for (int index = 0; index < busIndex; index++) offset += patternBusSlotCounts[index];
        if (changedSlot >= 0 && changedSlot < slotCount) patternPreviewSync.dirty(offset + changedSlot, 1);
        else patternPreviewSync.dirty(offset, slotCount);
        patternContentRevision = nextPatternContentRevision();
        markForUpdate();
    }

    public void onPatternBusInventoryChanged(ECOCraftingPatternBusBlockEntity bus, int[] changedSlots) {
        if (!(level instanceof ServerLevel) || !patternInterfaceMappingInitialized) {
            return;
        }
        int busIndex = Arrays.binarySearch(patternBusPositions, bus.getBlockPos().asLong());
        int slotCount = bus.getPatternSlotCount();
        if (busIndex < 0 || busIndex >= patternBusSlotCounts.length
                || patternBusSlotCounts[busIndex] != slotCount || !isMappedBus(busIndex, bus)) {
            patternInterfaceMappingInitialized = false;
            ensurePatternInterfaceMapping();
            busIndex = Arrays.binarySearch(patternBusPositions, bus.getBlockPos().asLong());
        }
        if (busIndex < 0) {
            return;
        }
        int offset = 0;
        for (int index = 0; index < busIndex; index++) {
            offset += patternBusSlotCounts[index];
        }
        for (int changedSlot : changedSlots) {
            if (changedSlot >= 0 && changedSlot < slotCount) {
                patternPreviewSync.dirty(offset + changedSlot, 1);
            }
        }
        patternContentRevision = nextPatternContentRevision();
        markForUpdate();
    }

    private boolean isMappedBus(int busIndex, ECOCraftingPatternBusBlockEntity bus) {
        int offset = 0;
        for (int index = 0; index < busIndex; index++) {
            offset += patternBusSlotCounts[index];
        }
        return offset < patternSlotRefs.size() && patternSlotRefs.get(offset).bus() == bus;
    }

    private void ensurePatternInterfaceMapping() {
        if (level == null || level.isClientSide || patternInterfaceMappingInitialized) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>();
        if (formed && supportsCraftingInterfaceUi() && grid != null) {
            buses.addAll(grid.getActiveMachines(ECOCraftingPatternBusBlockEntity.class));
            buses.removeIf(bus -> bus.getGrid() != grid || bus.isRemoved() || bus.getBlockPos() == null);
            buses.sort(Comparator.comparingLong(bus -> bus.getBlockPos().asLong()));
        }

        long[] positions = new long[buses.size()];
        int[] slotCounts = new int[buses.size()];
        List<PatternSlotRef> refs = new ArrayList<>();
        for (int busIndex = 0; busIndex < buses.size(); busIndex++) {
            ECOCraftingPatternBusBlockEntity bus = buses.get(busIndex);
            int slotCount = Math.max(0, bus.getPatternSlotCount());
            positions[busIndex] = bus.getBlockPos().asLong();
            slotCounts[busIndex] = slotCount;
            for (int slot = 0; slot < slotCount; slot++) {
                refs.add(new PatternSlotRef(bus, slot));
            }
        }

        synchronized (this) {
            patternBusPositions = positions;
            patternBusSlotCounts = slotCounts;
        }
        patternSlotRefs = List.copyOf(refs);
        patternInterfaceMappingInitialized = true;
        patternPreviewSync.reset();
        // Equal positions can still refer to replacement block entities or inventories.
        patternContentRevision = nextPatternContentRevision();
        markForUpdate();
    }

    private int nextPatternContentRevision() {
        return patternContentRevision == Integer.MAX_VALUE ? 1 : patternContentRevision + 1;
    }

    private void closePatternInterfaceMenus() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (!(player.containerMenu instanceof ModularUIContainerMenu menu)
                    || !(menu.uiHolder instanceof BlockUIMenuType.BlockUIHolder holder)
                    || !holder.pos.equals(worldPosition)) {
                continue;
            }
            player.closeContainer();
            patternPreviewSync.resend(player);
        }
    }

    private ItemStack getPatternStack(int slot) {
        if (slot < 0 || slot >= patternSlotRefs.size()) {
            return ItemStack.EMPTY;
        }
        return getPatternStack(patternSlotRefs.get(slot));
    }

    private ItemStack getPatternStack(PatternSlotRef ref) {
        InternalInventory inventory = ref.bus().getTerminalPatternInventory();
        return ref.slot() < inventory.size() ? inventory.getStackInSlot(ref.slot()) : ItemStack.EMPTY;
    }

    private void setPatternStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= patternSlotRefs.size()) {
            return;
        }
        setPatternStack(patternSlotRefs.get(slot), stack);
    }

    private void setPatternStack(PatternSlotRef ref, ItemStack stack) {
        InternalInventory inventory = ref.bus().getTerminalPatternInventory();
        if (ref.slot() < inventory.size()) {
            ref.bus().setPatternDirect(ref.slot(), stack == null ? ItemStack.EMPTY : stack);
        }
    }

    private boolean hasDuplicatePattern(PatternSlotRef target, ItemStack candidate) {
        AEItemKey candidateKey = AEItemKey.of(candidate);
        if (candidateKey == null) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        IECOPatternStorageService service = grid == null
                ? null
                : grid.getService(IECOPatternStorageService.class);
        if (service instanceof PatternCatalog catalog) {
            for (PatternCatalog.PatternLocation location : catalog.locationsForKey(candidateKey)) {
                ECOCraftingPatternBusBlockEntity bus = location.bus();
                if (bus != null && (bus != target.bus() || location.physicalSlot() != target.slot())) {
                    return true;
                }
            }
            return false;
        }
        for (PatternSlotRef ref : patternSlotRefs) {
            if (ref.equals(target)) {
                continue;
            }
            if (candidateKey.equals(AEItemKey.of(getPatternStack(ref)))) {
                return true;
            }
        }
        return false;
    }

    private ItemStack insertPatternSlot(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= patternSlotRefs.size()) {
            return stack;
        }
        PatternSlotRef ref = patternSlotRefs.get(slot);
        InternalInventory inventory = ref.bus().getTerminalPatternInventory();
        return ref.slot() < inventory.size() ? inventory.insertItem(ref.slot(), stack, simulate) : stack;
    }

    private static byte patternSearchFlags(ItemStack stack) {
        var encoded = stack.get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null) {
            return 0;
        }
        return (byte) (4 | (encoded.canSubstitute() ? 1 : 0) | (encoded.canSubstituteFluids() ? 2 : 0));
    }

    private record PatternSlotRef(ECOCraftingPatternBusBlockEntity bus, int slot) {
    }

    public void organizePatternBuses(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)
                || !formed
                || !supportsCraftingInterfaceUi()
                || patternTransferTask != null
                || patternOrganizeTask != null
                || !isPatternInterfacePlayer(player, serverLevel)) {
            return;
        }
        ensurePatternInterfaceMapping();
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        PatternMigrationCoordinator coordinator = PatternMigrationCoordinator.forGrid(grid);
        if (!coordinator.tryAcquire(this)) {
            return;
        }
        IECOPatternStorageService service = grid.getService(IECOPatternStorageService.class);
        if (!(service instanceof PatternCatalog catalog)) {
            coordinator.release(this);
            return;
        }
        clearPatternTransferResults();
        clearPatternOrganizeResults();
        patternOrganizeTask = new PatternOrganizeTask(
                patternSlotRefs, catalog.occupiedPatterns(), coordinator, player.getUUID());
        patternOrganizeInProgress = true;
        patternOrganizeScannedSlots = 0;
        patternOrganizeTotalSlots = patternOrganizeTask.totalSlots();
        syncPatternOperationState(serverLevel.getGameTime(), true);
    }

    /** Validates the player-inventory quick-move fallback for clients without a normal menu click. */
    @RPCMethod
    public void insertPatternFromPlayer(RPCSender sender, int inventorySlot) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)
                || inventorySlot < 0 || inventorySlot >= 36 || !formed || !supportsCraftingInterfaceUi()) {
            return;
        }
        ServerPlayer player = sender.asPlayer();
        if (!isPatternInterfacePlayer(player, serverLevel)) {
            return;
        }
        tryInsertPatternFromPlayer(player, inventorySlot);
    }

    /** Server-authoritative single-slot quick move shared by RPC and menu integrations. */
    public boolean tryInsertPatternFromPlayer(ServerPlayer player, int inventorySlot) {
        if (player == null || inventorySlot < 0 || inventorySlot >= 36 || level == null || level.isClientSide
                || !formed || !supportsCraftingInterfaceUi() || !isPatternInterfacePlayer(player, (ServerLevel) level)) {
            return false;
        }
        ItemStack source = player.getInventory().getItem(inventorySlot);
        if (source.isEmpty() || !PatternDetailsHelper.isEncodedPattern(source)
                || !(PatternDetailsHelper.decodePattern(source, level) instanceof IMolecularAssemblerSupportedPattern)) {
            return false;
        }
        ensurePatternInterfaceMapping();
        for (int slot = 0; slot < patternSlotRefs.size(); slot++) {
            PatternSlotRef ref = patternSlotRefs.get(slot);
            if (!getPatternStack(slot).isEmpty() || hasDuplicatePattern(ref, source)) {
                continue;
            }
            InternalInventory target = ref.bus().getTerminalPatternInventory();
            if (!target.isItemValid(ref.slot(), source)) {
                continue;
            }
            ItemStack remainder = target.insertItem(ref.slot(), source.copy(), false);
            if (remainder.getCount() < source.getCount()) {
                player.getInventory().setItem(inventorySlot, remainder);
                return true;
            }
        }
        return false;
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (supportsCraftingInterfaceUi()) patternPreviewSync.tick(serverLevel);
        long startedNanos = System.nanoTime();
        migrationScannedThisTick = 0;
        migrationInsertedThisTick = 0;
        PatternMigrationCoordinator coordinator = patternTransferTask != null
                ? patternTransferTask.coordinator()
                : patternOrganizeTask != null ? patternOrganizeTask.coordinator() : null;
        long deadline = coordinator == null
                ? 0L
                : coordinator.beginSlice(this, serverLevel.getGameTime());
        if (deadline == 0L) {
            if (patternTransferTask != null) {
                finishPatternTransfer(serverLevel, true);
            } else if (patternOrganizeTask != null) {
                finishPatternOrganize(serverLevel);
            }
            return;
        }
        if (patternTransferTask != null) {
            tickPatternTransfer(serverLevel, deadline);
        } else if (patternOrganizeTask != null) {
            tickPatternOrganize(serverLevel, deadline);
        }
        coordinator.recordSlice(System.nanoTime() - startedNanos,
                migrationScannedThisTick, migrationInsertedThisTick);
    }

    private void tickPatternOrganize(ServerLevel serverLevel, long deadline) {
        PatternOrganizeTask task = patternOrganizeTask;
        if (task == null) {
            return;
        }
        if (!formed || !supportsCraftingInterfaceUi() || !task.matches(patternSlotRefs)) {
            finishPatternOrganize(serverLevel);
            return;
        }

        int operationsThisTick = 0;
        task.beginBatch();
        try {
        while (operationsThisTick < PATTERN_ORGANIZE_SAFETY_LIMIT_PER_TICK
                && System.nanoTime() < deadline && !task.isFinished()) {
            int readSlot = task.nextReadSlot();
            patternOrganizeScannedSlots = readSlot + 1;
            operationsThisTick++;
            migrationScannedThisTick++;

            PatternCatalog.PatternRecord record = task.record(readSlot);
            PatternSlotRef sourceRef = task.sourceRef(readSlot);
            ItemStack stack = getPatternStack(sourceRef);
            if (stack.isEmpty()) {
                continue;
            }

            if (!task.isSourceUnchanged(record, stack)) {
                task.blockRecovery();
                patternOrganizeRecoveryBlocked++;
                continue;
            }

            PatternOrganizeDisposition disposition = task.classify(record);
            if (disposition != PatternOrganizeDisposition.VALID) {
                if (task.recoveryBlocked() || !returnBlankPattern(serverLevel, task.playerId(), stack)) {
                    task.blockRecovery();
                    patternOrganizeRecoveryBlocked++;
                    continue;
                }
                task.ensureBatch(sourceRef.bus());
                setPatternStack(sourceRef, ItemStack.EMPTY);
                if (disposition == PatternOrganizeDisposition.INVALID) {
                    patternOrganizeInvalidRecovered++;
                } else {
                    patternOrganizeDuplicatesRecovered++;
                }
                continue;
            }

            if (task.recoveryBlocked()) {
                continue;
            }

            int writeSlot = task.nextWriteSlot();
            PatternSlotRef targetRef = task.targetRef(writeSlot);
            if (!sourceRef.equals(targetRef)) {
                ItemStack moved = stack.copy();
                task.ensureBatch(targetRef.bus());
                task.ensureBatch(sourceRef.bus());
                setPatternStack(targetRef, moved);
                setPatternStack(sourceRef, ItemStack.EMPTY);
            }
            task.advanceWriteSlot();
        }
        } finally {
            task.endBatch();
        }
        if (task.isFinished()) {
            finishPatternOrganize(serverLevel);
        } else {
            syncPatternOperationState(serverLevel.getGameTime(), false);
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
        return new PatternTransferTask(grid, storageService);
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

        if (!task.prepare()) {
            patternTransferIndexing = true;
            patternTransferScannedSlots = task.indexScannedSlots();
            patternTransferTotalSlots = task.indexTotalSlots();
            syncPatternTransferState(serverLevel.getGameTime(), false);
            return;
        }
        if (task.justPrepared()) {
            patternTransferIndexing = !task.indexReady();
            patternTransferScannedSlots = 0;
            patternTransferTotalSlots = task.totalSlots();
        }

        int operationsThisTick = 0;
        while (operationsThisTick < PATTERN_TRANSFER_SAFETY_LIMIT_PER_TICK
                && System.nanoTime() < deadline && !task.isFinished()) {
            PatternTransferStep step = task.nextStep();
            patternTransferIndexing = !task.indexReady();
            patternTransferTotalSlots = Math.max(patternTransferTotalSlots, task.indexTotalSlots());
            if (step == null) {
                break;
            }
            operationsThisTick++;
            migrationScannedThisTick++;
            patternTransferScannedSlots++;
            ItemStack stack = step.inventory().getStackInSlot(step.slot());
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                task.removeCandidate(step.candidate());
                continue;
            }
            var details = PatternDetailsHelper.decodePattern(stack, level);
            if (!(details instanceof IMolecularAssemblerSupportedPattern)) {
                task.removeCandidate(step.candidate());
                patternTransferIncompatible++;
                continue;
            }

            ECOPreparedPattern prepared = new ECOPreparedPattern(stack, details, AEItemKey.of(stack));
            switch (task.storageService().insertPreparedPattern(prepared)) {
                case INSERTED -> {
                    if (task.isSourceUnchanged(step)) {
                        step.inventory().setItemDirect(step.slot(), ItemStack.EMPTY);
                        task.removeCandidate(step.candidate());
                    }
                    patternTransferInserted++;
                    migrationInsertedThisTick++;
                }
                case ALREADY_PRESENT -> {
                    if (task.isSourceUnchanged(step)) {
                        step.inventory().setItemDirect(step.slot(), ItemStack.EMPTY);
                        task.removeCandidate(step.candidate());
                    }
                    patternTransferAlreadyPresent++;
                    migrationInsertedThisTick++;
                }
                case NO_SPACE -> {
                    task.skipCandidate(step.candidate());
                    patternTransferNoSpace++;
                }
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

    private boolean isPatternInterfacePlayer(@Nullable ServerPlayer player, ServerLevel serverLevel) {
        return player != null && player.level() == serverLevel
                && player.blockPosition().distSqr(worldPosition) <= 64.0D;
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
        patternTransferIndexing = false;
    }

    private void clearPatternOrganizeResults() {
        patternOrganizePerformed = false;
        patternOrganizeInvalidRecovered = 0;
        patternOrganizeDuplicatesRecovered = 0;
        patternOrganizeRecoveryBlocked = 0;
    }

    private void finishPatternTransfer(ServerLevel level, boolean unavailable) {
        PatternTransferTask task = patternTransferTask;
        if (task != null) {
            task.releaseClaims();
            task.coordinator().release(this);
        }
        patternTransferTask = null;
        patternTransferInProgress = false;
        patternTransferIndexing = false;
        patternTransferUnavailable |= unavailable;
        syncPatternTransferState(level.getGameTime(), true);
    }

    private void finishPatternOrganize(ServerLevel level) {
        PatternOrganizeTask task = patternOrganizeTask;
        if (task != null) {
            task.coordinator().release(this);
        }
        patternOrganizeTask = null;
        patternOrganizeInProgress = false;
        patternOrganizeScannedSlots = patternOrganizeTotalSlots;
        patternOrganizePerformed = true;
        syncPatternOperationState(level.getGameTime(), true);
    }

    private boolean returnBlankPattern(ServerLevel serverLevel, UUID playerId, ItemStack encodedPattern) {
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
        if (player == null || encodedPattern.isEmpty()) {
            return false;
        }
        ItemStack blankPattern = AEItems.BLANK_PATTERN.stack(encodedPattern.getCount());
        if (!canStoreInPlayerInventory(player, blankPattern)) {
            return false;
        }
        player.getInventory().add(blankPattern);
        return blankPattern.isEmpty();
    }

    private static boolean canStoreInPlayerInventory(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        int stackLimit = Math.min(player.getInventory().getMaxStackSize(), stack.getMaxStackSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing.isEmpty()) {
                remaining -= stackLimit;
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                remaining -= Math.max(0, Math.min(stackLimit, existing.getMaxStackSize()) - existing.getCount());
            }
        }
        return remaining <= 0;
    }

    private void releasePatternMigrationLease() {
        if (patternTransferTask != null) {
            patternTransferTask.releaseClaims();
            patternTransferTask.coordinator().release(this);
        }
        if (patternOrganizeTask != null) {
            patternOrganizeTask.coordinator().release(this);
        }
    }

    private void syncPatternTransferState(long gameTime, boolean force) {
        syncPatternOperationState(gameTime, force);
    }

    private void syncPatternOperationState(long gameTime, boolean force) {
        if (force || gameTime - lastPatternTransferSyncTick >= PATTERN_TRANSFER_SYNC_INTERVAL_TICKS) {
            lastPatternTransferSyncTick = gameTime;
            markForUpdate();
        }
    }

    private final class PatternOrganizeTask {
        private final List<PatternSlotRef> refs;
        private final List<PatternCatalog.PatternRecord> records;
        private final PatternMigrationCoordinator coordinator;
        private final UUID playerId;
        private final Set<AEItemKey> retainedPatternKeys = new HashSet<>();
        private int nextReadSlot;
        private int nextWriteSlot;
        private boolean recoveryBlocked;
        private final Set<ECOCraftingPatternBusBlockEntity> batchBuses = new LinkedHashSet<>();

        private PatternOrganizeTask(List<PatternSlotRef> refs,
                                    List<PatternCatalog.PatternRecord> records,
                                    PatternMigrationCoordinator coordinator,
                                    UUID playerId) {
            this.refs = List.copyOf(refs);
            this.records = List.copyOf(records);
            this.coordinator = coordinator;
            this.playerId = playerId;
        }

        private PatternMigrationCoordinator coordinator() {
            return coordinator;
        }

        private int totalSlots() {
            return records.size();
        }

        private boolean isFinished() {
            return nextReadSlot >= records.size();
        }

        private int nextReadSlot() {
            return nextReadSlot++;
        }

        private int nextWriteSlot() {
            return nextWriteSlot;
        }

        private PatternSlotRef targetRef(int slot) {
            return refs.get(slot);
        }

        private PatternCatalog.PatternRecord record(int index) {
            return records.get(index);
        }

        private PatternSlotRef sourceRef(int index) {
            PatternCatalog.PatternLocation location = records.get(index).location();
            return new PatternSlotRef(java.util.Objects.requireNonNull(location.bus()), location.physicalSlot());
        }

        private boolean matches(List<PatternSlotRef> currentRefs) {
            return refs.equals(currentRefs);
        }

        private void advanceWriteSlot() {
            nextWriteSlot++;
        }

        private UUID playerId() {
            return playerId;
        }

        private boolean recoveryBlocked() {
            return recoveryBlocked;
        }

        private void blockRecovery() {
            recoveryBlocked = true;
        }

        private void beginBatch() {
            batchBuses.clear();
        }

        private void ensureBatch(ECOCraftingPatternBusBlockEntity bus) {
            if (batchBuses.add(bus)) {
                bus.beginPatternBatch();
            }
        }

        private void endBatch() {
            // Destinations are at or before their sources. Publishing in physical order makes a
            // cross-bus move briefly duplicated rather than briefly unavailable to providers.
            List<ECOCraftingPatternBusBlockEntity> ordered = new ArrayList<>(batchBuses);
            ordered.sort(Comparator.comparingLong(bus -> bus.getBlockPos().asLong()));
            for (ECOCraftingPatternBusBlockEntity bus : ordered) {
                bus.endPatternBatch();
            }
        }

        private boolean isSourceUnchanged(PatternCatalog.PatternRecord record, ItemStack stack) {
            return ItemStack.matches(record.stack(), stack);
        }

        private PatternOrganizeDisposition classify(PatternCatalog.PatternRecord record) {
            if (!record.supported()) {
                return PatternOrganizeDisposition.INVALID;
            }
            AEItemKey key = record.key();
            if (key == null) {
                return PatternOrganizeDisposition.INVALID;
            }
            return retainedPatternKeys.add(key)
                    ? PatternOrganizeDisposition.VALID
                    : PatternOrganizeDisposition.DUPLICATE;
        }
    }

    private enum PatternOrganizeDisposition {
        VALID,
        INVALID,
        DUPLICATE
    }

    private final class PatternTransferTask {
        private static final int CANDIDATE_BATCH_SIZE = 64;
        private final IGrid grid;
        private final IECOPatternStorageService storageService;
        private final PatternMigrationCoordinator coordinator;
        private final UUID owner = UUID.randomUUID();
        private final Set<ECOPatternSourceSlot> skippedCandidates = new HashSet<>();
        private List<ECOPatternSourceSlot> candidates = List.of();
        private int candidateIndex;
        private boolean prepared;
        private boolean justPrepared;
        private boolean noMoreCandidates;
        private int indexScannedSlots;
        private int indexTotalSlots;
        private boolean indexReady;

        private PatternTransferTask(
                IGrid grid,
                IECOPatternStorageService storageService) {
            this.grid = grid;
            this.storageService = storageService;
            this.coordinator = PatternMigrationCoordinator.forGrid(grid);
        }

        private IGrid grid() {
            return grid;
        }

        private IECOPatternStorageService storageService() {
            return storageService;
        }

        private PatternMigrationCoordinator coordinator() {
            return coordinator;
        }

        private int totalSlots() {
            return prepared && indexTotalSlots <= 0 ? candidates.size() : indexTotalSlots;
        }

        private boolean isFinished() {
            return prepared && noMoreCandidates && candidateIndex >= candidates.size();
        }

        private boolean prepare() {
            justPrepared = false;
            if (prepared) {
                return true;
            }
            IECOPatternStorageService.ExternalPatternIndexState index = storageService.getExternalPatternIndex(grid);
            indexScannedSlots = index.scannedSlots();
            indexTotalSlots = index.totalSlots();
            indexReady = index.ready();
            candidates = List.of();
            candidateIndex = 0;
            noMoreCandidates = false;
            prepared = true;
            justPrepared = true;
            return true;
        }

        private boolean justPrepared() {
            return justPrepared;
        }

        private boolean indexReady() {
            return indexReady;
        }

        private int indexScannedSlots() {
            return indexScannedSlots;
        }

        private int indexTotalSlots() {
            return indexTotalSlots;
        }

        private void removeCandidate(ECOPatternSourceSlot candidate) {
            storageService.removeExternalPatternCandidate(candidate);
            skippedCandidates.add(candidate);
        }

        private void skipCandidate(ECOPatternSourceSlot candidate) {
            skippedCandidates.add(candidate);
        }

        private void releaseClaims() {
            storageService.releaseExternalPatternCandidates(owner);
        }

        private boolean isSourceUnchanged(PatternTransferStep step) {
            ItemStack current = step.inventory().getStackInSlot(step.slot());
            return current.getCount() == step.snapshot().getCount()
                    && ItemStack.isSameItemSameComponents(current, step.snapshot());
        }

        @Nullable
        private PatternTransferStep nextStep() {
            while (true) {
                while (candidateIndex < candidates.size()) {
                    ECOPatternSourceSlot candidate = candidates.get(candidateIndex++);
                    if (skippedCandidates.contains(candidate)) {
                        continue;
                    }
                    PatternContainer source = candidate.source();
                    if (source.getGrid() != grid) {
                        removeCandidate(candidate);
                        continue;
                    }
                    InternalInventory inventory = source.getTerminalPatternInventory();
                    if (candidate.slot() < inventory.size()) {
                        ItemStack snapshot = inventory.getStackInSlot(candidate.slot()).copy();
                        return new PatternTransferStep(candidate, inventory, candidate.slot(), snapshot);
                    }
                    removeCandidate(candidate);
                }

                if (noMoreCandidates) {
                    return null;
                }
                IECOPatternStorageService.ExternalPatternClaim claim =
                        storageService.claimExternalPatternCandidates(grid, owner, CANDIDATE_BATCH_SIZE);
                indexScannedSlots = claim.scannedSlots();
                indexTotalSlots = claim.totalSlots();
                indexReady = claim.ready();
                candidates = claim.candidates();
                candidateIndex = 0;
                if (candidates.isEmpty()) {
                    noMoreCandidates = claim.ready();
                    return null;
                }
            }
        }
    }

    private record PatternTransferStep(ECOPatternSourceSlot candidate,
                                       InternalInventory inventory,
                                       int slot,
                                       ItemStack snapshot) {
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
