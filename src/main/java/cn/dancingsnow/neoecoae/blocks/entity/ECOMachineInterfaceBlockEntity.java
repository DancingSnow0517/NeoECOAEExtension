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
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPatternSourceSlot;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.grid.PatternMigrationCoordinator;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ECOMachineInterfaceBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, ECOMachineInterfaceBlockEntity<C>>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost {
    private static final int PATTERN_TRANSFER_MAX_SLOTS_PER_TICK = 24;
    private static final int PATTERN_TRANSFER_MAX_INSERTIONS_PER_TICK = 8;
    private static final long PATTERN_TRANSFER_SYNC_INTERVAL_TICKS = 5L;
    private static final int PATTERN_ORGANIZE_MAX_SLOTS_PER_TICK = 24;
    private static final int PATTERN_ORGANIZE_MAX_MOVES_PER_TICK = 8;
    public static final int FUZZY_PLANNING_SLOT_COUNT = 63;
    public static final int PATTERN_INTERFACE_VISIBLE_SLOTS = 36;

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
    @DescSynced
    private long[] patternBusPositions = new long[0];
    @DescSynced
    private int[] patternBusSlotCounts = new int[0];
    @DescSynced
    private int patternContentRevision;
    private transient List<PatternSlotRef> patternSlotRefs = List.of();
    private transient boolean patternInterfaceMappingInitialized;
    private transient Map<UUID, PatternInterfaceItemHandler> patternInterfaceViews = new HashMap<>();
    private transient PatternSearchIndex clientPatternSearchIndex = PatternSearchIndex.EMPTY;
    private transient int patternSearchIndexRevision = Integer.MIN_VALUE;
    private transient CompoundTag patternSearchIndexPayload = new CompoundTag();
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

    public IItemHandlerModifiable createPatternInterfaceItemHandler(UUID playerId) {
        ensurePatternInterfaceMapping();
        if (level != null && level.isClientSide) {
            AppEngInternalInventory inventory = new AppEngInternalInventory(
                    null, PATTERN_INTERFACE_VISIBLE_SLOTS, 1);
            return new PatternInterfaceItemHandler((IItemHandlerModifiable) inventory.toItemHandler());
        }
        PatternInterfaceItemHandler view = new PatternInterfaceItemHandler(playerId);
        view.setView(defaultPatternInterfaceView());
        patternInterfaceViews.put(playerId, view);
        return view;
    }

    public PatternSearchIndex getClientPatternSearchIndex() {
        return clientPatternSearchIndex;
    }

    private int[] defaultPatternInterfaceView() {
        int[] view = new int[Math.min(PATTERN_INTERFACE_VISIBLE_SLOTS, patternSlotRefs.size())];
        for (int slot = 0; slot < view.length; slot++) {
            view[slot] = slot;
        }
        return view;
    }

    /** Sends client-side search terms without turning every actual Bus slot into a menu slot. */
    @RPCMethod
    public void requestPatternSearchIndex(RPCSender sender, int knownRevision) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel) || !formed || !supportsCraftingInterfaceUi()) {
            return;
        }
        ServerPlayer player = sender.asPlayer();
        if (!isPatternInterfacePlayer(player, serverLevel)) {
            return;
        }
        ensurePatternInterfaceMapping();
        if (knownRevision == patternContentRevision) {
            return;
        }
        rpcToPlayer(player, "setPatternSearchIndex", patternContentRevision, getPatternSearchIndexPayload());
    }

    private CompoundTag getPatternSearchIndexPayload() {
        if (patternSearchIndexRevision == patternContentRevision) {
            return patternSearchIndexPayload;
        }
        CompoundTag payload = new CompoundTag();
        ListTag keywords = new ListTag();
        byte[] flags = new byte[patternSlotRefs.size()];
        for (int slot = 0; slot < patternSlotRefs.size(); slot++) {
            PatternSlotRef ref = patternSlotRefs.get(slot);
            ItemStack stack = getPatternStack(slot);
            keywords.add(StringTag.valueOf(ref.bus().getPatternSearchKeywords(ref.slot())));
            flags[slot] = patternSearchFlags(stack);
        }
        payload.put("keywords", keywords);
        payload.putByteArray("flags", flags);
        patternSearchIndexRevision = patternContentRevision;
        patternSearchIndexPayload = payload;
        return payload;
    }

    /** Maps the client's visible window to server-authoritative actual Pattern Bus slots. */
    @RPCMethod
    public void setPatternInterfaceView(RPCSender sender, CompoundTag payload) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel) || !formed || !supportsCraftingInterfaceUi()) {
            return;
        }
        ServerPlayer player = sender.asPlayer();
        if (!isPatternInterfacePlayer(player, serverLevel)) {
            return;
        }
        PatternInterfaceItemHandler view = patternInterfaceViews.get(player.getUUID());
        if (view == null) {
            return;
        }
        ensurePatternInterfaceMapping();
        int[] requestedSlots = payload == null ? new int[0] : payload.getIntArray("slots");
        view.setView(requestedSlots);
    }

    @RPCMethod
    public void setPatternSearchIndex(RPCSender sender, int revision, CompoundTag payload) {
        if (!sender.isServer() || level == null || !level.isClientSide) {
            return;
        }
        ListTag keywords = payload == null ? new ListTag() : payload.getList("keywords", Tag.TAG_STRING);
        String[] entries = new String[keywords.size()];
        for (int slot = 0; slot < entries.length; slot++) {
            entries[slot] = keywords.getString(slot);
        }
        byte[] flags = payload == null ? new byte[0] : payload.getByteArray("flags");
        clientPatternSearchIndex = new PatternSearchIndex(Math.max(0, revision), entries, flags);
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
        boolean hadMapping = patternInterfaceMappingInitialized;
        patternInterfaceMappingInitialized = false;
        patternSlotRefs = List.of();
        ensurePatternInterfaceMapping();
        if (hadMapping) {
            closePatternInterfaceMenus();
        }
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
        boolean hadMapping = patternInterfaceMappingInitialized;
        long[] previousPositions = patternBusPositions;
        int[] previousSlotCounts = patternBusSlotCounts;
        patternInterfaceMappingInitialized = false;
        ensurePatternInterfaceMapping();
        if (hadMapping || !Arrays.equals(previousPositions, patternBusPositions)
                || !Arrays.equals(previousSlotCounts, patternBusSlotCounts)) {
            closePatternInterfaceMenus();
        }
    }

    /** Called by a Pattern Bus after a real slot mutation; this path never scans the inventory. */
    public void onPatternBusInventoryChanged(ECOCraftingPatternBusBlockEntity bus) {
        if (!(level instanceof ServerLevel) || !patternInterfaceMappingInitialized) {
            return;
        }
        int busIndex = Arrays.binarySearch(patternBusPositions, bus.getBlockPos().asLong());
        int slotCount = bus.getPatternSlotCount();
        boolean mappingInvalid = busIndex < 0 || busIndex >= patternBusSlotCounts.length
                || patternBusSlotCounts[busIndex] != slotCount
                || !isMappedBus(busIndex, bus);
        if (mappingInvalid) {
            long[] previousPositions = patternBusPositions;
            int[] previousSlotCounts = patternBusSlotCounts;
            patternInterfaceMappingInitialized = false;
            ensurePatternInterfaceMapping();
            if (!Arrays.equals(previousPositions, patternBusPositions)
                    || !Arrays.equals(previousSlotCounts, patternBusSlotCounts)) {
                closePatternInterfaceMenus();
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
            buses.removeIf(bus -> bus.getGrid() != grid || bus.isRemoved());
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

        boolean changed = !Arrays.equals(patternBusPositions, positions)
                || !Arrays.equals(patternBusSlotCounts, slotCounts);
        patternBusPositions = positions;
        patternBusSlotCounts = slotCounts;
        patternSlotRefs = List.copyOf(refs);
        patternInterfaceMappingInitialized = true;
        if (changed) {
            patternContentRevision = nextPatternContentRevision();
            markForUpdate();
        }
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
            patternInterfaceViews.remove(player.getUUID());
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
            inventory.setItemDirect(ref.slot(), stack == null ? ItemStack.EMPTY : stack);
        }
    }

    private boolean hasDuplicatePattern(PatternSlotRef target, ItemStack candidate) {
        AEItemKey candidateKey = AEItemKey.of(candidate);
        if (candidateKey == null) {
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

    private final class PatternInterfaceItemHandler implements IItemHandlerModifiable {
        @Nullable
        private final IItemHandlerModifiable clientDelegate;
        private List<PatternSlotRef> refs;

        private PatternInterfaceItemHandler(IItemHandlerModifiable clientDelegate) {
            this.clientDelegate = clientDelegate;
            this.refs = List.of();
        }

        private PatternInterfaceItemHandler(UUID playerId) {
            this.clientDelegate = null;
            this.refs = List.of();
        }

        private void setView(@Nullable int[] requestedSlots) {
            if (clientDelegate != null) {
                return;
            }
            List<PatternSlotRef> next = new ArrayList<>(PATTERN_INTERFACE_VISIBLE_SLOTS);
            if (requestedSlots != null) {
                for (int slot : requestedSlots) {
                    if (next.size() >= PATTERN_INTERFACE_VISIBLE_SLOTS) {
                        break;
                    }
                    if (slot >= 0 && slot < patternSlotRefs.size()) {
                        next.add(patternSlotRefs.get(slot));
                    }
                }
            }
            refs = List.copyOf(next);
        }

        @Override
        public int getSlots() {
            return clientDelegate != null ? clientDelegate.getSlots() : PATTERN_INTERFACE_VISIBLE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (clientDelegate != null) {
                return clientDelegate.getStackInSlot(slot);
            }
            PatternSlotRef ref = ref(slot);
            if (ref == null) {
                return ItemStack.EMPTY;
            }
            InternalInventory inventory = ref.bus().getTerminalPatternInventory();
            return ref.slot() < inventory.size() ? inventory.getStackInSlot(ref.slot()) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (clientDelegate != null) {
                return clientDelegate.insertItem(slot, stack, simulate);
            }
            PatternSlotRef ref = ref(slot);
            if (ref == null) {
                return stack;
            }
            if (hasDuplicatePattern(ref, stack)) {
                return stack;
            }
            InternalInventory inventory = ref.bus().getTerminalPatternInventory();
            return ref.slot() < inventory.size() ? inventory.insertItem(ref.slot(), stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (clientDelegate != null) {
                return clientDelegate.extractItem(slot, amount, simulate);
            }
            PatternSlotRef ref = ref(slot);
            if (ref == null) {
                return ItemStack.EMPTY;
            }
            InternalInventory inventory = ref.bus().getTerminalPatternInventory();
            return ref.slot() < inventory.size() ? inventory.extractItem(ref.slot(), amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (clientDelegate != null) {
                return clientDelegate.getSlotLimit(slot);
            }
            PatternSlotRef ref = ref(slot);
            if (ref == null) {
                return 0;
            }
            InternalInventory inventory = ref.bus().getTerminalPatternInventory();
            return ref.slot() < inventory.size() ? inventory.getSlotLimit(ref.slot()) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (clientDelegate != null) {
                return clientDelegate.isItemValid(slot, stack);
            }
            PatternSlotRef ref = ref(slot);
            if (ref == null) {
                return false;
            }
            InternalInventory inventory = ref.bus().getTerminalPatternInventory();
            return ref.slot() < inventory.size()
                    && !hasDuplicatePattern(ref, stack)
                    && inventory.isItemValid(ref.slot(), stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (clientDelegate != null) {
                clientDelegate.setStackInSlot(slot, stack);
                return;
            }
            PatternSlotRef ref = ref(slot);
            if (ref == null) {
                return;
            }
            InternalInventory inventory = ref.bus().getTerminalPatternInventory();
            if (ref.slot() < inventory.size()) {
                if (stack != null && !stack.isEmpty()
                        && (hasDuplicatePattern(ref, stack) || !inventory.isItemValid(ref.slot(), stack))) {
                    return;
                }
                inventory.setItemDirect(ref.slot(), stack == null ? ItemStack.EMPTY : stack);
            }
        }

        @Nullable
        private PatternSlotRef ref(int slot) {
            return slot >= 0 && slot < refs.size() ? refs.get(slot) : null;
        }
    }

    public record PatternSearchIndex(int revision, String[] keywords, byte[] flags) {
        private static final PatternSearchIndex EMPTY = new PatternSearchIndex(-1, new String[0], new byte[0]);

        public PatternSearchIndex {
            keywords = keywords == null ? new String[0] : keywords;
            flags = flags == null ? new byte[0] : flags;
        }

        public int size() {
            return keywords.length;
        }

        public String keywords(int slot) {
            return slot >= 0 && slot < keywords.length ? keywords[slot] : "";
        }

        public byte flags(int slot) {
            return slot >= 0 && slot < flags.length ? flags[slot] : 0;
        }
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
        clearPatternTransferResults();
        clearPatternOrganizeResults();
        patternOrganizeTask = new PatternOrganizeTask(patternSlotRefs, coordinator, player.getUUID());
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
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return;
        }

        ensurePatternInterfaceMapping();
        for (int slot = 0; slot < patternSlotRefs.size(); slot++) {
            if (!getPatternStack(slot).isEmpty()) {
                continue;
            }
            ItemStack remaining = insertPatternSlot(slot, stack.copy(), false);
            if (remaining.getCount() != stack.getCount()) {
                player.getInventory().setItem(inventorySlot, remaining);
                return;
            }
        }
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
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

        int scannedThisTick = 0;
        int movesThisTick = 0;
        while (scannedThisTick < PATTERN_ORGANIZE_MAX_SLOTS_PER_TICK
                && movesThisTick < PATTERN_ORGANIZE_MAX_MOVES_PER_TICK
                && System.nanoTime() < deadline
                && !task.isFinished()) {
            int readSlot = task.nextReadSlot();
            patternOrganizeScannedSlots = readSlot + 1;
            scannedThisTick++;
            migrationScannedThisTick++;

            ItemStack stack = getPatternStack(task.ref(readSlot));
            if (stack.isEmpty()) {
                continue;
            }

            PatternOrganizeDisposition disposition = task.classify(stack);
            if (disposition != PatternOrganizeDisposition.VALID) {
                if (task.recoveryBlocked() || !returnBlankPattern(serverLevel, task.playerId(), stack)) {
                    task.blockRecovery();
                    patternOrganizeRecoveryBlocked++;
                    continue;
                }
                setPatternStack(task.ref(readSlot), ItemStack.EMPTY);
                if (disposition == PatternOrganizeDisposition.INVALID) {
                    patternOrganizeInvalidRecovered++;
                } else {
                    patternOrganizeDuplicatesRecovered++;
                }
                movesThisTick++;
                continue;
            }

            if (task.recoveryBlocked()) {
                continue;
            }

            int writeSlot = task.nextWriteSlot();
            if (readSlot != writeSlot) {
                ItemStack moved = stack.copy();
                setPatternStack(task.ref(writeSlot), moved);
                setPatternStack(task.ref(readSlot), ItemStack.EMPTY);
                movesThisTick++;
            }
            task.advanceWriteSlot();
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
            patternTransferIndexing = false;
            patternTransferScannedSlots = 0;
            patternTransferTotalSlots = task.totalSlots();
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

            insertionsThisTick++;
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
        private final PatternMigrationCoordinator coordinator;
        private final UUID playerId;
        private final Set<AEItemKey> retainedPatternKeys = new HashSet<>();
        private int nextReadSlot;
        private int nextWriteSlot;
        private boolean recoveryBlocked;

        private PatternOrganizeTask(List<PatternSlotRef> refs, PatternMigrationCoordinator coordinator, UUID playerId) {
            this.refs = List.copyOf(refs);
            this.coordinator = coordinator;
            this.playerId = playerId;
        }

        private PatternMigrationCoordinator coordinator() {
            return coordinator;
        }

        private int totalSlots() {
            return refs.size();
        }

        private boolean isFinished() {
            return nextReadSlot >= refs.size();
        }

        private int nextReadSlot() {
            return nextReadSlot++;
        }

        private int nextWriteSlot() {
            return nextWriteSlot;
        }

        private PatternSlotRef ref(int slot) {
            return refs.get(slot);
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

        private PatternOrganizeDisposition classify(ItemStack stack) {
            try {
                if (!(PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern)) {
                    return PatternOrganizeDisposition.INVALID;
                }
            } catch (RuntimeException ignored) {
                return PatternOrganizeDisposition.INVALID;
            }
            AEItemKey key = AEItemKey.of(stack);
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
            if (!index.ready()) {
                return false;
            }
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
                if (!claim.ready()) {
                    prepared = false;
                    candidates = List.of();
                    candidateIndex = 0;
                    return null;
                }
                candidates = claim.candidates();
                candidateIndex = 0;
                if (candidates.isEmpty()) {
                    noMoreCandidates = true;
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
