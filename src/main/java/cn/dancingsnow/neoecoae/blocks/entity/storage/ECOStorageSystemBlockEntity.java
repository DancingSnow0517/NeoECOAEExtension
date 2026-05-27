package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.client.gui.Icon;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;

import java.util.List;
import java.util.UUID;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity> implements ISyncPersistRPCBlockEntity, IGridTickable {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @DescSynced
    private long[] usedTypes;
    @DescSynced
    private long[] totalTypes;
    @DescSynced
    private long[] usedBytes;
    @DescSynced
    private long[] totalBytes;

    @DescSynced
    private long storedEnergy;
    @DescSynced
    private long maxEnergy;
    @Persisted
    @DescSynced
    private int selectedBuildLength = 1;
    @DescSynced
    private int previewMissingBlocks;
    @DescSynced
    private int previewConflictBlocks;
    @DescSynced
    private int previewReusedBlocks;
    @DescSynced
    private int previewRequiredItems;
    @DescSynced
    private String previewStatusKey = "gui.neoecoae.multiblock.status.idle";
    @DescSynced
    private int previewStatusArg1;
    @DescSynced
    private int previewStatusArg2;
    @DescSynced
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;

    public ECOStorageSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
        resetStorageInfos();

        getMainNode().addService(IGridTickable.class, this);
    }

    public static ECOStorageSystemBlockEntity createL4(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L4);
    }

    public static ECOStorageSystemBlockEntity createL6(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L6);
    }

    public static ECOStorageSystemBlockEntity createL9(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
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
    }

    @SuppressWarnings("UnstableApiUsage")
    private void updateInfos() {
        System.out.println("[NeoECOAE TRACE] updateInfos called @ " + worldPosition +
            " side=" + (level == null ? "null" : level.isClientSide ? "CLIENT" : "SERVER") +
            " cluster=" + (cluster != null));
        int driveCount = 0;
        int cellCount = 0;
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
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                driveCount++;
                IECOStorageCell inv = drive.getCellInventory();
                if (inv != null) {
                    cellCount++;
                    ECOCellType cellType = inv.getCellType();
                    int id = findCellTypeId(cellType);
                    if (id < 0 || id >= typeCount) {
                        LOGGER.info("[NeoECOAE] updateInfos SKIP cell: id={} typeCount={} cellType={}", id, typeCount, cellType);
                        continue;
                    }
                    long st = inv.getStoredItemTypes();
                    long tt = inv.getTotalItemTypes();
                    long ub = inv.getUsedBytes();
                    long tb = inv.getTotalBytes();
                    // One-time log for first cell found (per controller session)
                    if (cellCount == 1 && !_loggedCellDetail) {
                        _loggedCellDetail = true;
                        LOGGER.info("[NeoECOAE] updateInfos CELL detail @ {}: drivePos={} cellClass={}" +
                            " cellType={} id={} storedTypes={} totalTypes={} usedBytes={} totalBytes={} tier={}",
                            worldPosition, drive.getBlockPos(), inv.getClass().getSimpleName(),
                            cellType, id, st, tt, ub, tb, inv.getTier());
                    }
                    usedTypes[id] += st;
                    totalTypes[id] += tt;
                    usedBytes[id] += ub;
                    totalBytes[id] += tb;
                }
            }
            // Diagnostic: ALWAYS log for debugging
            long tu = sum(usedTypes), tt = sum(totalTypes), bu = sum(usedBytes), bt = sum(totalBytes);
            _synUsedTypes = tu;
            _synTotalTypes = tt;
            _synUsedBytes = bu;
            _synTotalBytes = bt;
            String msg = "[NeoECOAE] updateInfos @ " + worldPosition + " cluster=" + (cluster != null) +
                " drives=" + driveCount + " cells=" + cellCount +
                " types=" + tu + "/" + tt + " bytes=" + bu + "/" + bt +
                " energy=" + storedEnergy + "/" + maxEnergy + " formed=" + formed;
            LOGGER.info(msg);
            System.out.println(msg);
            _loggedUpdateOnce = true;
            setChanged();
        } else {
            resetStorageInfos();
            setChanged();
        }
        // Sync UI display fields to client
        syncUiToClient();
    }

    // Diagnostic: track last logged values to avoid log spam
    private transient boolean _loggedUpdateOnce;
    private transient boolean _loggedCellDetail;

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            int remainingBlocks = buildSession.getRemainingBlockCount();
            buildSession = null;
            buildPlayerId = null;
            buildInProgress = false;
            syncPreview(remainingBlocks, 0, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.builder_unavailable");
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING -> {
            }
            case ADVANCED -> syncPreview(
                buildSession.getRemainingBlockCount(),
                0,
                previewReusedBlocks,
                previewRequiredItems,
                "gui.neoecoae.multiblock.status.building",
                buildSession.getPlacedBlockCount(),
                buildSession.getTotalBlocks()
            );
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
                syncPreview(remainingBlocks, 1, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.build_interrupted");
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

    // Scalar synced fields 鈥?written directly to avoid long[] array sync issues on client
    @DescSynced
    private long _synUsedTypes;
    @DescSynced
    private long _synTotalTypes;
    @DescSynced
    private long _synUsedBytes;
    @DescSynced
    private long _synTotalBytes;

    public long getTotalUsedBytes() {
        long s = sum(usedBytes);
        return s != 0 ? s : _synUsedBytes;
    }

    public long getTotalBytes() {
        long s = sum(totalBytes);
        return s != 0 ? s : _synTotalBytes;
    }

    public long getTotalUsedTypes() {
        long s = sum(usedTypes);
        return s != 0 ? s : _synUsedTypes;
    }

    public long getTotalTypes() {
        long s = sum(totalTypes);
        return s != 0 ? s : _synTotalTypes;
    }

    public Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

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

    private int getCellTypeCount() {
        return Math.max(NERegistries.CELL_TYPE.size(), 1);
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
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void decreaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void previewStructure(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress && buildSession != null) {
            syncPreview(buildSession.getRemainingBlockCount(), 0, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.building", buildSession.getPlacedBlockCount(), buildSession.getTotalBlocks());
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength);
        boolean hasMaterials = player instanceof ServerPlayer serverPlayer
            && MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems());
        String statusKey = plan.getConflictPositions().isEmpty()
            ? (plan.getMissingBlocks().isEmpty() ? "gui.neoecoae.multiblock.status.structure_ready" : (hasMaterials ? "gui.neoecoae.multiblock.status.ready_to_build" : "gui.neoecoae.multiblock.status.not_enough_items"))
            : "gui.neoecoae.multiblock.status.conflicts_detected";
        syncPreview(plan.getMissingBlocks().size(), plan.getConflictPositions().size(), plan.getReusedBlockCount(), plan.getRequiredItemCount(), statusKey);
    }

    public void autoBuild(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.closeContainer();
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress) {
            syncPreview(previewMissingBlocks, previewConflictBlocks, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.build_already_in_progress");
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength);
        if (!plan.getConflictPositions().isEmpty()) {
            syncPreview(plan.getMissingBlocks().size(), plan.getConflictPositions().size(), plan.getReusedBlockCount(), plan.getRequiredItemCount(), "gui.neoecoae.multiblock.status.conflicts_detected");
            return;
        }
        if (!serverPlayer.isCreative() && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            syncPreview(plan.getMissingBlocks().size(), 0, plan.getReusedBlockCount(), plan.getRequiredItemCount(), "gui.neoecoae.multiblock.status.not_enough_items");
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan)) {
                syncPreview(plan.getMissingBlocks().size(), plan.getConflictPositions().size(), plan.getReusedBlockCount(), plan.getRequiredItemCount(), "gui.neoecoae.multiblock.status.build_failed");
                return;
            }
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        syncPreview(plan.getMissingBlocks().size(), 0, plan.getReusedBlockCount(), plan.getRequiredItemCount(), "gui.neoecoae.multiblock.status.building", buildSession.getPlacedBlockCount(), buildSession.getTotalBlocks());
    }

    private MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getStorageSystemDefinition(tier);
    }

    private int findCellTypeId(ECOCellType cellType) {
        int id = 0;
        for (ECOCellType entry : NERegistries.CELL_TYPE) {
            if (entry.equals(cellType)) {
                return id;
            }
            id++;
        }
        return -1;
    }

    private int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    private int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    private void resetPreview(String statusKey) {
        syncPreview(0, 0, 0, 0, statusKey);
    }

    private void syncPreview(int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey) {
        syncPreview(missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, 0, 0);
    }

    private void syncPreview(int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey, int statusArg1, int statusArg2) {
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

    private Component buildPreviewStatusComponent() {
        if ("gui.neoecoae.multiblock.status.building".equals(previewStatusKey)) {
            return Component.translatable(previewStatusKey, previewStatusArg1, previewStatusArg2);
        }
        return Component.translatable(previewStatusKey);
    }


    // 鈹€鈹€ Client sync for Controller UI (LDLib1 ModularUI data bridge) 鈹€鈹€

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

    private void syncUiToClient() {
        if (level != null && !level.isClientSide && getBlockPos() != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void writeUiSyncTag(CompoundTag tag) {
        tag.putLong("neo_storedEnergy", storedEnergy);
        tag.putLong("neo_maxEnergy", maxEnergy);
        tag.putBoolean("neo_formed", formed);
        // Scalars (reliable) 鈥?used as primary read path by Screen getters
        tag.putLong("neo_usedTypes_s", _synUsedTypes);
        tag.putLong("neo_totalTypes_s", _synTotalTypes);
        tag.putLong("neo_usedBytes_s", _synUsedBytes);
        tag.putLong("neo_totalBytes_s", _synTotalBytes);
        // Arrays (fallback) 鈥?kept for compatibility
        if (usedTypes != null) tag.putLongArray("neo_usedTypes", usedTypes);
        if (totalTypes != null) tag.putLongArray("neo_totalTypes", totalTypes);
        if (usedBytes != null) tag.putLongArray("neo_usedBytes", usedBytes);
        if (totalBytes != null) tag.putLongArray("neo_totalBytes", totalBytes);
    }

    private void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_storedEnergy")) storedEnergy = tag.getLong("neo_storedEnergy");
        if (tag.contains("neo_maxEnergy")) maxEnergy = tag.getLong("neo_maxEnergy");
        if (tag.contains("neo_formed")) formed = tag.getBoolean("neo_formed");
        // Scalars 鈥?reliable scalar sync
        if (tag.contains("neo_usedTypes_s")) _synUsedTypes = tag.getLong("neo_usedTypes_s");
        if (tag.contains("neo_totalTypes_s")) _synTotalTypes = tag.getLong("neo_totalTypes_s");
        if (tag.contains("neo_usedBytes_s")) _synUsedBytes = tag.getLong("neo_usedBytes_s");
        if (tag.contains("neo_totalBytes_s")) _synTotalBytes = tag.getLong("neo_totalBytes_s");
        // Arrays 鈥?fallback
        if (tag.contains("neo_usedTypes")) usedTypes = tag.getLongArray("neo_usedTypes");
        if (tag.contains("neo_totalTypes")) totalTypes = tag.getLongArray("neo_totalTypes");
        if (tag.contains("neo_usedBytes")) usedBytes = tag.getLongArray("neo_usedBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLongArray("neo_totalBytes");
    }
}
