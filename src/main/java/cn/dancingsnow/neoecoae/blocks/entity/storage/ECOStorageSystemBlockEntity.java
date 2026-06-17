package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.StoragePriority;
import cn.dancingsnow.neoecoae.gui.StoragePriorityUI;
import cn.dancingsnow.neoecoae.gui.host.NEStorageHostUI;
import cn.dancingsnow.neoecoae.gui.host.NEStorageMatrixCell;
import cn.dancingsnow.neoecoae.gui.host.NEStorageTypeStat;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.storage.IStorageProvider;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity> implements ISyncPersistRPCBlockEntity {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    @DescSynced
    private int selectedBuildLength = 1;
    @Persisted
    @DescSynced
    private boolean mirrorBuild;
    @Getter
    @Persisted
    @DescSynced
    private int storagePriority;
    @DescSynced
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;
    @Setter
    private boolean mirrored;

    public ECOStorageSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
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
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOStorageSystemBlock.MIRRORED)) {
                level.setBlock(
                    worldPosition,
                    state.setValue(ECOStorageSystemBlock.MIRRORED, formed && mirrored),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                );
            }
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            buildSession = null;
            buildPlayerId = null;
            buildInProgress = false;
            setChanged();
            markForUpdate();
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING, ADVANCED -> {
            }
            case COMPLETED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                rebuildMultiblock();
                setChanged();
                markForUpdate();
            }
            case BLOCKED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                setChanged();
                markForUpdate();
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return NEStorageHostUI.create(this, holder, buildPanel(holder), priorityPanel(holder));
    }

    public Component getHostTitle() {
        return getItemFromBlockEntity().getDescription();
    }

    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    public long getTotalUsedTypes() {
        return getTotalStorageValue(StorageValue.USED_TYPES);
    }

    public long getTotalTypes() {
        return getTotalStorageValue(StorageValue.TOTAL_TYPES);
    }

    public long getTotalUsedBytes() {
        return getTotalStorageValue(StorageValue.USED_BYTES);
    }

    public long getTotalBytes() {
        return getTotalStorageValue(StorageValue.TOTAL_BYTES);
    }

    @SuppressWarnings("UnstableApiUsage")
    public long getStoredEnergy() {
        if (cluster == null) {
            return 0;
        }
        long total = 0;
        for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
            total += (long) energyCell.getAECurrentPower();
        }
        return total;
    }

    @SuppressWarnings("UnstableApiUsage")
    public long getMaxEnergy() {
        if (cluster == null) {
            return 0;
        }
        long total = 0;
        for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
            total += (long) energyCell.getAEMaxPower();
        }
        return total;
    }

    private long getTotalStorageValue(StorageValue value) {
        if (cluster == null) {
            return 0;
        }
        long total = 0;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell inv = drive.getCellInventory();
            if (inv != null) {
                total += getCellValue(inv, value);
            }
        }
        return total;
    }

    public List<NEStorageTypeStat> createStorageTypeStats() {
        if (cluster == null) {
            return List.of();
        }
        Map<Integer, StorageTypeTotals> grouped = new LinkedHashMap<>();
        for (ECOCellType cellType : NERegistries.CELL_TYPE) {
            int id = NERegistries.CELL_TYPE.getId(cellType);
            grouped.put(id, new StorageTypeTotals(NERegistries.CELL_TYPE.getKey(cellType), cellType.desc()));
        }

        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell inv = drive.getCellInventory();
            if (inv == null) {
                continue;
            }
            int id = NERegistries.CELL_TYPE.getId(inv.getCellType());
            StorageTypeTotals totals = grouped.get(id);
            if (totals != null) {
                totals.add(inv);
            }
        }

        return grouped.values().stream()
            .map(StorageTypeTotals::toStat)
            .sorted(Comparator.comparing(stat -> stat.typeId().toString()))
            .toList();
    }

    public List<NEStorageMatrixCell> createStorageMatrixCells() {
        if (cluster == null) {
            return List.of();
        }
        List<NEStorageMatrixCell> cells = new ArrayList<>(cluster.getDrives().size());
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction top = strategy.getSide(getBlockState(), RelativeSide.TOP);
        Direction left = strategy.getSide(getBlockState(), RelativeSide.RIGHT);
        Direction right = mirrored ? left : left.getOpposite();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            BlockPos offset = drive.getBlockPos().subtract(worldPosition);
            int row = 1 - directionDistance(offset, top);
            int column = directionDistance(offset, right) - 1;
            ItemStack cellStack = drive.getCellStack();
            IECOStorageCell inv = drive.getCellInventory();
            if (inv == null || cellStack.isEmpty()) {
                cells.add(new NEStorageMatrixCell(row, column, ItemStack.EMPTY, 0, 0L, 0L, 0L, 0L));
                continue;
            }
            cells.add(new NEStorageMatrixCell(
                row,
                column,
                new ItemStack(cellStack.getItem()),
                Math.max(0, Math.min(3, inv.getTier().getTier())),
                inv.getStoredItemTypes(),
                inv.getTotalItemTypes(),
                inv.getUsedBytes(),
                inv.getTotalBytes()
            ));
        }
        cells.sort(Comparator.comparingInt(NEStorageMatrixCell::row).thenComparingInt(NEStorageMatrixCell::column));
        return List.copyOf(cells);
    }

    private static int directionDistance(BlockPos offset, Direction direction) {
        return offset.getX() * direction.getStepX()
            + offset.getY() * direction.getStepY()
            + offset.getZ() * direction.getStepZ();
    }

    private static long getCellValue(IECOStorageCell inv, StorageValue value) {
        return switch (value) {
            case USED_TYPES -> inv.getStoredItemTypes();
            case TOTAL_TYPES -> inv.getTotalItemTypes();
            case USED_BYTES -> inv.getUsedBytes();
            case TOTAL_BYTES -> inv.getTotalBytes();
        };
    }

    private static final class StorageTypeTotals {
        private final net.minecraft.resources.ResourceLocation typeId;
        private final Component displayName;
        private long usedTypes;
        private long totalTypes;
        private long usedBytes;
        private long totalBytes;

        private StorageTypeTotals(net.minecraft.resources.ResourceLocation typeId, Component displayName) {
            this.typeId = typeId;
            this.displayName = displayName;
        }

        private void add(IECOStorageCell inv) {
            usedTypes += inv.getStoredItemTypes();
            totalTypes += inv.getTotalItemTypes();
            usedBytes += inv.getUsedBytes();
            totalBytes += inv.getTotalBytes();
        }

        private NEStorageTypeStat toStat() {
            return new NEStorageTypeStat(typeId, displayName, usedTypes, totalTypes, usedBytes, totalBytes);
        }
    }

    private enum StorageValue {
        USED_TYPES,
        TOTAL_TYPES,
        USED_BYTES,
        TOTAL_BYTES
    }

    private UIElement buildPanel(BlockUIMenuType.BlockUIHolder holder) {
        return MultiblockBuilderUI.createFloatingPanel(new MultiblockBuilderUI.Config(
            holder.player,
            () -> selectedBuildLength,
            () -> mirrorBuild,
            mirror -> setMirrorBuild(holder.player, mirror),
            () -> decreaseBuildLength(holder.player),
            () -> increaseBuildLength(holder.player),
            () -> autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            this::createLocalPreviewPlan
        ));
    }

    private UIElement priorityPanel(BlockUIMenuType.BlockUIHolder holder) {
        return StoragePriorityUI.createFloatingPanel(new StoragePriorityUI.Config(
            () -> storagePriority,
            priority -> setStoragePriority(holder.player, priority),
            delta -> changeStoragePriority(holder.player, delta)
        ));
    }

    private void changeStoragePriority(Player player, int delta) {
        setStoragePriority(player, StoragePriority.adjust(storagePriority, delta));
    }

    private void setStoragePriority(Player player, int priority) {
        if (storagePriority == priority) {
            return;
        }
        storagePriority = priority;
        setChanged();
        markForUpdate();
        refreshDriveStorageProviders();
    }

    private void refreshDriveStorageProviders() {
        if (cluster == null) {
            return;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IStorageProvider.requestUpdate(drive.getMainNode());
        }
    }

    private void increaseBuildLength(Player player) {
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void decreaseBuildLength(Player player) {
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void autoBuild(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (formed) {
            return;
        }
        if (buildInProgress) {
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrorBuild);
        if (!plan.getConflictPositions().isEmpty()) {
            return;
        }
        if (!serverPlayer.isCreative() && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan)) {
                return;
            }
            rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        setChanged();
        markForUpdate();
        serverPlayer.closeContainer();
    }

    private @Nullable MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getStorageSystemDefinition(tier);
    }

    private int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    private int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    private void setMirrorBuild(Player player, boolean mirrorBuild) {
        if (buildInProgress) {
            return;
        }
        this.mirrorBuild = mirrorBuild;
        setChanged();
        markForUpdate();
    }

    private @Nullable MultiBlockPlacementPlan createLocalPreviewPlan() {
        if (level == null || formed) {
            return null;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            return null;
        }
        int buildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        return MultiBlockPlacementService.preview(level, worldPosition, getBlockState(), definition, buildLength, mirrorBuild);
    }
}
