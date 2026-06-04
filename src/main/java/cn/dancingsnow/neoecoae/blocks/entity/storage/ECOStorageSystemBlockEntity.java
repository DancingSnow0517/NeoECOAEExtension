package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.network.NEStorageUiState;
import cn.dancingsnow.neoecoae.network.NEStorageUiTypeState;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity>
        implements IGridTickable, INEMultiblockBuildHost {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    @Getter
    private final IECOTier tier;

    private long[] usedTypes;
    private long[] totalTypes;
    private long[] usedBytes;
    private long[] totalBytes;
    private boolean storageStatsDirty = true;

    private long storedEnergy;
    private long maxEnergy;
    private int selectedBuildLength = 1;
    private int previewMissingBlocks;
    private int previewConflictBlocks;
    private int previewReusedBlocks;
    private int previewRequiredItems;
    private String previewStatusKey = "gui.neoecoae.multiblock.status.idle";
    private int previewStatusArg1;
    private int previewStatusArg2;
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;

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

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
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
            syncUiToClient();
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
        if (cluster != null) {
            // Group by cell type key; LinkedHashMap preserves insertion order
            Map<ResourceLocation, NEStorageUiTypeState> grouped = new LinkedHashMap<>();

            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                IECOStorageCell inv = drive.getCellInventory();
                if (inv == null) continue;

                ECOCellType cellType = inv.getCellType();
                ResourceLocation typeId = getCellTypeKey(cellType);
                String displayName = cellType.desc().getString();

                long st = inv.getStoredItemTypes();
                long tt = inv.getTotalItemTypes();
                long ub = inv.getUsedBytes();
                long tb = inv.getTotalBytes();

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
        } else {
            typeStates = new ArrayList<>();
        }

        return new NEStorageUiState(worldPosition, typeStates, storedEnergy, maxEnergy, formed);
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
        if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null
                ? null
                : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            int remainingBlocks = buildSession.getRemainingBlockCount();
            buildSession = null;
            buildPlayerId = null;
            buildInProgress = false;
            syncPreview(
                    remainingBlocks,
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.builder_unavailable");
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING -> {}
            case ADVANCED -> syncPreview(
                    buildSession.getRemainingBlockCount(),
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    buildSession.getPlacedBlockCount(),
                    buildSession.getTotalBlocks());
            case COMPLETED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                rebuildMultiblock();
                syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            }
            case BLOCKED -> {
                int remainingBlocks = buildSession.getRemainingBlockCount();
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                syncPreview(
                        remainingBlocks,
                        1,
                        previewReusedBlocks,
                        previewRequiredItems,
                        "gui.neoecoae.multiblock.status.build_interrupted");
            }
        }
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

    // ── INEMultiblockBuildHost interface ──

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

    @Override
    public void setSelectedBuildLength(int length) {
        this.selectedBuildLength = Mth.clamp(length, getMinBuildLength(), getMaxBuildLength());
    }

    @Override
    public int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    @Override
    public int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    @Override
    public void previewStructure(ServerPlayer player, int displayLength) {
        previewStructure(player, displayLength, false);
    }

    @Override
    public void previewStructure(ServerPlayer player, int displayLength, boolean mirrored) {
        setSelectedBuildLength(displayLength);
        previewStructure((Player) player, mirrored);
    }

    @Override
    public void autoBuild(ServerPlayer player, int displayLength) {
        autoBuild(player, displayLength, false);
    }

    @Override
    public void autoBuild(ServerPlayer player, int displayLength, boolean mirrored) {
        setSelectedBuildLength(displayLength);
        autoBuild((Player) player, mirrored);
    }

    @Deprecated
    @Override
    public void previewStructure(ServerPlayer player) {
        previewStructure((Player) player);
    }

    @Deprecated
    @Override
    public void autoBuild(ServerPlayer player) {
        autoBuild((Player) player);
    }

    @Override
    public void dismantle(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        player.closeContainer();
        boolean dismantled = MultiBlockPlacementService.dismantle(serverLevel, this, player);
        syncPreview(
                0,
                0,
                0,
                0,
                dismantled
                        ? "gui.neoecoae.multiblock.status.dismantled"
                        : "gui.neoecoae.multiblock.status.dismantle_failed");
    }

    // ── Legacy public accessors ──

    public int getSelectedBuildLength() {
        return selectedBuildLength;
    }

    public int getPreviewMissingBlocks() {
        return previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return previewRequiredItems;
    }

    public boolean isBuildInProgress() {
        return buildInProgress;
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

    public void markStorageStatsDirty() {
        storageStatsDirty = true;
    }

    private int getCellTypeCount() {
        var reg = NERegistries.cellTypeRegistry();
        return Math.max(reg != null ? reg.size() : 1, 1);
    }

    private static long sum(long[] values) {
        if (values == null) {
            return 0;
        }
        long result = 0;
        for (long value : values) {
            result += value;
        }
        return result;
    }

    public void increaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void decreaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void previewStructure(Player player) {
        previewStructure(player, false);
    }

    public void previewStructure(Player player, boolean mirrored) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress && buildSession != null) {
            syncPreview(
                    buildSession.getRemainingBlockCount(),
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    buildSession.getPlacedBlockCount(),
                    buildSession.getTotalBlocks());
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrored);
        boolean hasMaterials = player instanceof ServerPlayer serverPlayer
                && MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems());
        String statusKey = plan.getConflictPositions().isEmpty()
                ? (plan.getMissingBlocks().isEmpty()
                        ? "gui.neoecoae.multiblock.status.structure_ready"
                        : (hasMaterials
                                ? "gui.neoecoae.multiblock.status.ready_to_build"
                                : "gui.neoecoae.multiblock.status.not_enough_items"))
                : "gui.neoecoae.multiblock.status.conflicts_detected";
        syncPreview(
                plan.getMissingBlocks().size(),
                plan.getConflictPositions().size(),
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                statusKey);
    }

    public void autoBuild(Player player) {
        autoBuild(player, false);
    }

    public void autoBuild(Player player, boolean mirrored) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.closeContainer();
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress) {
            syncPreview(
                    previewMissingBlocks,
                    previewConflictBlocks,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.build_already_in_progress");
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrored);
        if (!plan.getConflictPositions().isEmpty()) {
            syncPreview(
                    plan.getMissingBlocks().size(),
                    plan.getConflictPositions().size(),
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.conflicts_detected");
            return;
        }
        if (!serverPlayer.isCreative()
                && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            syncPreview(
                    plan.getMissingBlocks().size(),
                    0,
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.not_enough_items");
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan)) {
                syncPreview(
                        plan.getMissingBlocks().size(),
                        plan.getConflictPositions().size(),
                        plan.getReusedBlockCount(),
                        plan.getRequiredItemCount(),
                        "gui.neoecoae.multiblock.status.build_failed");
                return;
            }
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        syncPreview(
                plan.getMissingBlocks().size(),
                0,
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.building",
                buildSession.getPlacedBlockCount(),
                buildSession.getTotalBlocks());
    }

    private void syncPreview(
            int missingBlocks,
            int conflictBlocks,
            int reusedBlocks,
            int requiredItems,
            String statusKey,
            int statusArg1,
            int statusArg2) {
        previewMissingBlocks = missingBlocks;
        previewConflictBlocks = conflictBlocks;
        previewReusedBlocks = reusedBlocks;
        previewRequiredItems = requiredItems;
        previewStatusKey = statusKey;
        previewStatusArg1 = statusArg1;
        previewStatusArg2 = statusArg2;
        setChanged();
        markForUpdate();
    }

    private void syncPreview(
            int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey) {
        syncPreview(missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, 0, 0);
    }

    private void resetPreview(String statusKey) {
        syncPreview(0, 0, 0, 0, statusKey);
    }

    private Component buildPreviewStatusComponent() {
        if ("gui.neoecoae.multiblock.status.building".equals(previewStatusKey)) {
            return Component.translatable(previewStatusKey, previewStatusArg1, previewStatusArg2);
        }
        return Component.translatable(previewStatusKey);
    }

    // ── NBT persistence ──

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedBuildLength", selectedBuildLength);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        selectedBuildLength = tag.getInt("selectedBuildLength");
        if (selectedBuildLength < 1) selectedBuildLength = 1;
        // Safety: build session is transient; reset in-progress state on load
        buildInProgress = false;
        previewMissingBlocks = 0;
        previewConflictBlocks = 0;
        previewReusedBlocks = 0;
        previewRequiredItems = 0;
        previewStatusKey = "gui.neoecoae.multiblock.status.idle";
        previewStatusArg1 = 0;
        previewStatusArg2 = 0;
    }

    // ── Native UI fallback sync via BE update tags (chunk load / block update) ──
    // Primary runtime UI sync uses the NENetwork S2C channel.

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        writeUiSyncTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        readUiSyncTag(tag);
    }

    @Override
    @Nullable public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncUiToClient() {
        if (level != null && !level.isClientSide && getBlockPos() != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void writeUiSyncTag(CompoundTag tag) {
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
        // Build/preview state
        tag.putInt("selectedBuildLength", selectedBuildLength);
        tag.putInt("previewMissingBlocks", previewMissingBlocks);
        tag.putInt("previewConflictBlocks", previewConflictBlocks);
        tag.putInt("previewReusedBlocks", previewReusedBlocks);
        tag.putInt("previewRequiredItems", previewRequiredItems);
        tag.putString(
                "previewStatusKey",
                previewStatusKey != null ? previewStatusKey : "gui.neoecoae.multiblock.status.idle");
        tag.putInt("previewStatusArg1", previewStatusArg1);
        tag.putInt("previewStatusArg2", previewStatusArg2);
        tag.putBoolean("buildInProgress", buildInProgress);
    }

    private void readUiSyncTag(CompoundTag tag) {
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
        // Build/preview state
        if (tag.contains("selectedBuildLength")) selectedBuildLength = tag.getInt("selectedBuildLength");
        if (tag.contains("previewMissingBlocks")) previewMissingBlocks = tag.getInt("previewMissingBlocks");
        if (tag.contains("previewConflictBlocks")) previewConflictBlocks = tag.getInt("previewConflictBlocks");
        if (tag.contains("previewReusedBlocks")) previewReusedBlocks = tag.getInt("previewReusedBlocks");
        if (tag.contains("previewRequiredItems")) previewRequiredItems = tag.getInt("previewRequiredItems");
        if (tag.contains("previewStatusKey")) previewStatusKey = tag.getString("previewStatusKey");
        if (tag.contains("previewStatusArg1")) previewStatusArg1 = tag.getInt("previewStatusArg1");
        if (tag.contains("previewStatusArg2")) previewStatusArg2 = tag.getInt("previewStatusArg2");
        if (tag.contains("buildInProgress")) buildInProgress = tag.getBoolean("buildInProgress");
        // Safety: no build session means build cannot be in progress
        if (buildInProgress && buildSession == null) {
            buildInProgress = false;
        }
    }
}
