package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.IPriorityHost;
import appeng.menu.ISubMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity>
        implements IGridTickable, INEMultiblockBuildHost, IPriorityHost, IUIHolder.BlockEntityUI {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int STORAGE_INTERFACE_EXPORT_KEYS_PER_TICK = 64;

    @Getter
    private final IECOTier tier;

    private long[] usedTypes;
    private long[] totalTypes;
    private long[] usedBytes;
    private long[] totalBytes;
    private boolean storageStatsDirty = true;

    /** Storage priority for AE2 network insertion/extraction ordering. */
    private int priority = 0;

    /** Shared preview/build state, delegates NBT sync to {@link BuildPreviewState}. */
    private final BuildPreviewState buildPreview = new BuildPreviewState();

    private long storedEnergy;
    private long maxEnergy;

    public ECOStorageSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        resetStorageInfos();

        getMainNode().addService(IGridTickable.class, this);
    }

    public static ECOStorageSystemBlockEntity createL4(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L4);
    }

    public static ECOStorageSystemBlockEntity createL6(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L6);
    }

    public static ECOStorageSystemBlockEntity createL9(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L9);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(256 + (1 << (1 + 4 * tier.getTier())));
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            markStorageStatsDirty();
            updateInfos();
        }
    }

    public void onStorageClusterFormed() {
        if (level == null || level.isClientSide || !formed || cluster == null) {
            return;
        }
        markStorageStatsDirty();
        updateInfos();
        requestProviderUpdates();
        setChanged();
        markForUpdate();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        long exported = exportStorageInterfaceContents();
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface != null) {
            storageInterface.recordStorageInterfaceExport(exported);
        }
        updateInfos();
        return TickRateModulation.URGENT;
    }

    private void resetStorageInfos() {
        int typeCount = getCellTypeCount();
        usedTypes = new long[typeCount];
        totalTypes = new long[typeCount];
        usedBytes = new long[typeCount];
        totalBytes = new long[typeCount];
        storedEnergy = 0;
        maxEnergy = 0;
        _synUsedTypes = 0;
        _synTotalTypes = 0;
        _synUsedBytes = 0;
        _synTotalBytes = 0;
    }

    /**
     * Core stats recalculation from cluster drives and energy cells.
     * Updates _syn* scalars and per-type arrays but does NOT mark dirty
     * or sync to client. Safe to call on server only.
     */
    @SuppressWarnings("UnstableApiUsage")
    private void recalculateStorageStats() {
        if (cluster != null) {
            storedEnergy = 0;
            maxEnergy = 0;
            for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
                storedEnergy += (long) energyCell.getAECurrentPower();
                maxEnergy += (long) energyCell.getAEMaxPower();
            }

            int typeCount = getCellTypeCount();
            usedTypes = new long[typeCount];
            totalTypes = new long[typeCount];
            usedBytes = new long[typeCount];
            totalBytes = new long[typeCount];

            // Aggregate scalars - always populated regardless of registry-id lookup
            long aggUsedTypes = 0, aggTotalTypes = 0, aggUsedBytes = 0, aggTotalBytes = 0;

            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                IECOStorageCell inv = drive.getCellInventory();
                if (inv == null) continue;

                long st = inv.getStoredItemTypes();
                long tt = inv.getTotalItemTypes();
                long ub = inv.getUsedBytes();
                long tb = inv.getTotalBytes();

                aggUsedTypes += st;
                aggTotalTypes += tt;
                aggUsedBytes += ub;
                aggTotalBytes += tb;

                // Per-cell-type arrays - best-effort, may skip if id lookup fails
                ECOCellType cellType = inv.getCellType();
                var reg = NERegistries.cellTypeRegistry();
                int id = reg != null ? reg.getId(cellType) : -1;
                if (id >= 0 && id < typeCount) {
                    usedTypes[id] += st;
                    totalTypes[id] += tt;
                    usedBytes[id] += ub;
                    totalBytes[id] += tb;
                }
            }

            _synUsedTypes = aggUsedTypes;
            _synTotalTypes = aggTotalTypes;
            _synUsedBytes = aggUsedBytes;
            _synTotalBytes = aggTotalBytes;
        } else {
            resetStorageInfos();
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void updateInfos() {
        if (ensureStorageStatsCurrent()) {
            setChanged();
        }
    }

    private boolean ensureStorageStatsCurrent() {
        if (!storageStatsDirty) {
            return false;
        }
        recalculateStorageStats();
        storageStatsDirty = false;
        return true;
    }

    /**
     * Creates a snapshot of current storage stats for S2C UI sync.
     * <p>
     * Stats are grouped by ECOCellType registry key so the screen can display
     * separate rows for Items, Fluids, and future cell types.
     * </p>
     */
    public NEStorageUiState createStorageUiState() {
        if (level != null && !level.isClientSide) {
            ensureStorageStatsCurrent();
        }

        List<NEStorageUiTypeState> typeStates;
        List<NEStorageUiMatrixState> matrixStates;
        if (cluster != null) {
            // Group by cell type key; LinkedHashMap preserves insertion order
            Map<ResourceLocation, NEStorageUiTypeState> grouped = new LinkedHashMap<>();
            matrixStates = new ArrayList<>(cluster.getDrives().size());
            IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
            Direction top = strategy.getSide(getBlockState(), RelativeSide.TOP);
            Direction left = strategy.getSide(getBlockState(), RelativeSide.RIGHT);
            Direction right = cluster.isMirrored() ? left : left.getOpposite();

            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                BlockPos offset = drive.getBlockPos().subtract(worldPosition);
                int row = 1 - directionDistance(offset, top);
                int column = directionDistance(offset, right) - 1;
                ItemStack cellStack = drive.getCellStack();
                IECOStorageCell inv = drive.getCellInventory();
                if (inv == null || cellStack.isEmpty()) {
                    matrixStates.add(new NEStorageUiMatrixState(row, column, ItemStack.EMPTY, 0, 0L, 0L, 0L, 0L));
                    continue;
                }

                ECOCellType cellType = inv.getCellType();
                ResourceLocation typeId = getCellTypeKey(cellType);
                String displayName = cellType.desc().getString();

                long st = inv.getStoredItemTypes();
                long tt = inv.getTotalItemTypes();
                long ub = inv.getUsedBytes();
                long tb = inv.getTotalBytes();
                int matrixTier = Math.max(0, Math.min(3, inv.getTier().getTier()));
                matrixStates.add(new NEStorageUiMatrixState(
                        row, column, new ItemStack(cellStack.getItem()), matrixTier, st, tt, ub, tb));

                NEStorageUiTypeState existing = grouped.get(typeId);
                if (existing != null) {
                    grouped.put(
                            typeId,
                            new NEStorageUiTypeState(
                                    typeId,
                                    displayName,
                                    existing.usedTypes() + st,
                                    existing.totalTypes() + tt,
                                    existing.usedBytes() + ub,
                                    existing.totalBytes() + tb));
                } else {
                    grouped.put(typeId, new NEStorageUiTypeState(typeId, displayName, st, tt, ub, tb));
                }
            }
            typeStates = new ArrayList<>(grouped.values());
            // Stable ordering: Items first, Fluids second, others by typeId string
            typeStates.sort(
                    java.util.Comparator.comparingInt((NEStorageUiTypeState s) -> storageTypeSortPriority(s.typeId()))
                            .thenComparing(s -> s.typeId().toString()));
            matrixStates.sort(Comparator.comparingInt(NEStorageUiMatrixState::row)
                    .thenComparingInt(NEStorageUiMatrixState::column));
        } else {
            typeStates = new ArrayList<>();
            matrixStates = List.of();
        }

        return new NEStorageUiState(worldPosition, typeStates, matrixStates, storedEnergy, maxEnergy, formed);
    }

    private static int directionDistance(BlockPos offset, Direction direction) {
        return offset.getX() * direction.getStepX()
                + offset.getY() * direction.getStepY()
                + offset.getZ() * direction.getStepZ();
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createStorageController(this, player);
    }

    /**
     * Returns the stable identity key for a cell type.
     * Uses the {@code id} field embedded in {@link ECOCellType} directly,
     * avoiding {@code Registry.getKey()} which is unreliable for custom
     * Registrate-built registries.
     */
    private static ResourceLocation getCellTypeKey(ECOCellType cellType) {
        ResourceLocation id = cellType.id();
        return id != null ? id : ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "unknown");
    }

    /**
     * Returns a sort priority for stable UI ordering.
     * Items (0) always first, Fluids (1) second, other types (100+) sorted
     * by their full typeId string.
     */
    private static int storageTypeSortPriority(ResourceLocation id) {
        if (id.equals(NeoECOAE.id("items"))) {
            return 0;
        }
        if (id.equals(NeoECOAE.id("fluids"))) {
            return 1;
        }
        return 100;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        tickBuild(level);
    }

    public long getStoredEnergy() {
        return storedEnergy;
    }

    public boolean isFormed() {
        return formed;
    }

    public long getMaxEnergy() {
        return maxEnergy;
    }

    // Scalar synced fields - written directly to avoid long[] array sync issues on client
    private long _synUsedTypes;
    private long _synTotalTypes;
    private long _synUsedBytes;
    private long _synTotalBytes;

    public long getTotalUsedBytes() {
        return _synUsedBytes;
    }

    public long getTotalBytes() {
        return _synTotalBytes;
    }

    public long getTotalUsedTypes() {
        return _synUsedTypes;
    }

    public long getTotalTypes() {
        return _synTotalTypes;
    }

    public Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

    // INEMultiblockBuildHost implementation

    @Override
    public BlockPos getHostPos() {
        return worldPosition;
    }

    @Override
    public BlockState getHostBlockState() {
        return getBlockState();
    }

    @Override
    public MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getStorageSystemDefinition(tier);
    }

    @Deprecated
    @Override
    public void previewStructure(ServerPlayer player) {
        previewStructure(player, false);
    }

    @Deprecated
    @Override
    public void autoBuild(ServerPlayer player) {
        autoBuild(player, false);
    }

    // Legacy public accessors

    public int getSelectedBuildLength() {
        return buildPreview.selectedBuildLength;
    }

    public int getPreviewMissingBlocks() {
        return buildPreview.previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return buildPreview.previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return buildPreview.previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return buildPreview.previewRequiredItems;
    }

    /**
     * Called by Drive block entities to notify the controller that storage
     * stats should be recalculated (cell inserted, removed, or content changed).
     * Only executes on the server side.
     */
    public void refreshStorageUiState() {
        if (level == null || level.isClientSide) {
            return;
        }
        markStorageStatsDirty();
    }

    /**
     * Marks the cached storage stats (per-type used/total bytes and types)
     * as stale. The next call to {@link #ensureStorageStatsCurrent()} will
     * recalculate from cluster drives and trigger a UI state push.
     */
    public void markStorageStatsDirty() {
        storageStatsDirty = true;
    }

    public boolean isStorageInterfaceOutputMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        return storageInterface != null && storageInterface.isStorageOutputMode();
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        markStorageStatsDirty();
        requestProviderUpdates();
        markForUpdate();
        setChanged();
    }

    @Nullable private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }

    private long exportStorageInterfaceContents() {
        if (!isStorageInterfaceOutputMode() || !formed || cluster == null) {
            return 0L;
        }
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface == null || !storageInterface.getMainNode().isOnline()) {
            return 0L;
        }
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }

        MEStorage target = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(storageInterface);
        long exported = 0L;
        int remainingKeys = STORAGE_INTERFACE_EXPORT_KEYS_PER_TICK;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (remainingKeys <= 0) {
                break;
            }
            IECOStorageCell cell = drive.getCellInventory();
            if (cell == null || !canExportDriveCell(drive)) {
                continue;
            }
            ExportResult result = exportFromStorageLimited(cell, target, source, remainingKeys);
            exported = saturatedAdd(exported, result.exported());
            remainingKeys -= result.keysVisited();
        }
        if (exported > 0L) {
            markStorageStatsDirty();
            setChanged();
            markForUpdate();
        }
        return exported;
    }

    private boolean canExportDriveCell(ECODriveBlockEntity drive) {
        if (!formed || cluster == null) {
            return false;
        }
        ItemStack stack = drive.getCellStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        IECOStorageCell cell = drive.getCellInventory();
        return cell != null && tier.compareTo(cell.getTier()) >= 0;
    }

    private ExportResult exportFromStorageLimited(
            MEStorage sourceStorage, MEStorage targetStorage, IActionSource source, int maxKeys) {
        if (maxKeys <= 0) {
            return new ExportResult(0L, 0);
        }
        appeng.api.stacks.KeyCounter available = new appeng.api.stacks.KeyCounter();
        sourceStorage.getAvailableStacks(available);
        long exported = 0L;
        int keysVisited = 0;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (keysVisited >= maxKeys) {
                break;
            }
            long amount = entry.getLongValue();
            if (amount <= 0L) {
                continue;
            }
            keysVisited++;
            long moved = exportKey(sourceStorage, targetStorage, source, entry.getKey(), amount);
            if (moved > 0L) {
                exported = saturatedAdd(exported, moved);
            }
        }
        return new ExportResult(exported, keysVisited);
    }

    private long exportKey(
            MEStorage sourceStorage, MEStorage targetStorage, IActionSource source, AEKey key, long availableAmount) {
        long request = Math.max(0L, availableAmount);
        if (request <= 0L) {
            return 0L;
        }
        long accepted = targetStorage.insert(key, request, Actionable.SIMULATE, source);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceStorage.extract(key, Math.min(request, accepted), Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }
        long inserted = targetStorage.insert(key, extracted, Actionable.MODULATE, source);
        if (inserted < extracted) {
            long remainder = extracted - Math.max(0L, inserted);
            sourceStorage.insert(key, remainder, Actionable.MODULATE, source);
        }
        return Math.max(0L, inserted);
    }

    private void requestProviderUpdates() {
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                drive.requestStorageProviderUpdate();
                drive.scheduleRenderUpdate();
            }
        }
    }

    // IPriorityHost implementation

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        setChanged();
        requestProviderUpdates();
        markForUpdate();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getBlockState().getBlock().asItem());
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer && level != null && !level.isClientSide && !isRemoved()) {
            BlockEntityUIFactory.INSTANCE.openUI(this, serverPlayer);
        }
    }

    private int getCellTypeCount() {
        var reg = NERegistries.cellTypeRegistry();
        return Math.max(reg != null ? reg.size() : 1, 1);
    }

    private record ExportResult(long exported, int keysVisited) {}

    private static long saturatedAdd(long left, long right) {
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE || right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    // increaseBuildLength / decreaseBuildLength are provided by INEMultiblockBuildHost default

    @Override
    public BuildPreviewState getBuildPreview() {
        return buildPreview;
    }

    @Override
    public void markPreviewDirty() {
        setChanged();
        markForUpdate();
    }

    // buildPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // NBT persistence
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedBuildLength", getSelectedBuildLength());
        tag.putInt("priority", priority);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        buildPreview.selectedBuildLength = Math.max(1, tag.getInt("selectedBuildLength"));
        priority = tag.getInt("priority");
        buildPreview.buildInProgress = false;
        buildPreview.resetPreview(BuildPreviewState.DEFAULT_STATUS_KEY);
    }

    // UI sync (Layer 1: chunk-load NBT)
    // getUpdateTag/handleUpdateTag/getUpdatePacket are provided by NEBlockEntity.
    // We only need to override writeUiSyncTag/readUiSyncTag.

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putLong("neo_storedEnergy", storedEnergy);
        tag.putLong("neo_maxEnergy", maxEnergy);
        tag.putBoolean("neo_formed", formed);
        tag.putLong("neo_usedTypes_s", _synUsedTypes);
        tag.putLong("neo_totalTypes_s", _synTotalTypes);
        tag.putLong("neo_usedBytes_s", _synUsedBytes);
        tag.putLong("neo_totalBytes_s", _synTotalBytes);
        if (usedTypes != null) tag.putLongArray("neo_usedTypes", usedTypes);
        if (totalTypes != null) tag.putLongArray("neo_totalTypes", totalTypes);
        if (usedBytes != null) tag.putLongArray("neo_usedBytes", usedBytes);
        if (totalBytes != null) tag.putLongArray("neo_totalBytes", totalBytes);
        buildPreview.writeToTag(tag);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_storedEnergy")) storedEnergy = tag.getLong("neo_storedEnergy");
        if (tag.contains("neo_maxEnergy")) maxEnergy = tag.getLong("neo_maxEnergy");
        if (tag.contains("neo_formed")) formed = tag.getBoolean("neo_formed");
        if (tag.contains("neo_usedTypes_s")) _synUsedTypes = tag.getLong("neo_usedTypes_s");
        if (tag.contains("neo_totalTypes_s")) _synTotalTypes = tag.getLong("neo_totalTypes_s");
        if (tag.contains("neo_usedBytes_s")) _synUsedBytes = tag.getLong("neo_usedBytes_s");
        if (tag.contains("neo_totalBytes_s")) _synTotalBytes = tag.getLong("neo_totalBytes_s");
        if (tag.contains("neo_usedTypes")) usedTypes = tag.getLongArray("neo_usedTypes");
        if (tag.contains("neo_totalTypes")) totalTypes = tag.getLongArray("neo_totalTypes");
        if (tag.contains("neo_usedBytes")) usedBytes = tag.getLongArray("neo_usedBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLongArray("neo_totalBytes");
        buildPreview.readFromTag(tag);
    }
}
