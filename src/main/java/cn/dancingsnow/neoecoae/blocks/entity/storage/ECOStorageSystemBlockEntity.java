package cn.dancingsnow.neoecoae.blocks.entity.storage;

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
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostMetric;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity> implements ISyncPersistRPCBlockEntity,  IGridTickable {
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
    @Persisted
    @DescSynced
    private boolean mirrorBuild;
    @DescSynced
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;
    private boolean mirrored;

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
        if (updateExposed) {
            updateInfos();
        }
    }

    public void setMirrored(boolean mirrored) {
        this.mirrored = mirrored;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 20, false);
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
                IECOStorageCell inv = drive.getCellInventory();
                if (inv != null) {
                    ECOCellType cellType = inv.getCellType();
                    int id = NERegistries.CELL_TYPE.getId(cellType);
                    if (id < 0 || id >= typeCount) {
                        continue;
                    }
                    usedTypes[id] += inv.getStoredItemTypes();
                    totalTypes[id] += inv.getTotalItemTypes();
                    usedBytes[id] += inv.getUsedBytes();
                    totalBytes[id] += inv.getTotalBytes();
                }
            }
            setChanged();
        } else {
            resetStorageInfos();
            setChanged();
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
            case WAITING -> {
            }
            case ADVANCED -> {
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
        resetStorageInfosIfNeeded();
        UIElement buildWindow = buildPanel(holder);

        UIElement details = ECOHostWidgets.detailArea(true);
        ECOHostWidgets.addDetailChild(details, ECOHostWidgets.sectionTitle("gui.neoecoae.host.storage.channels"));
        NERegistries.CELL_TYPE.stream()
            .forEachOrdered(cellType -> {
                int id = NERegistries.CELL_TYPE.getId(cellType);
                ECOHostWidgets.addDetailChild(details, createStorageChannelCard(cellType, id));
            });

        UIElement root = ECOHostWidgets.hostPanel(
            () -> getItemFromBlockEntity().getDescription(),
            () -> Component.translatable("gui.neoecoae.host.storage.subtitle"),
            () -> Component.translatable(buildInProgress ? "gui.neoecoae.host.status.running" : "gui.neoecoae.host.status.online"),
            List.of(
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.storage.type_usage"),
                    () -> numberPair(getTotalUsedTypes(), getTotalTypes()),
                    () -> ECOHostStyles.ratio(getTotalUsedTypes(), getTotalTypes())
                ),
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.storage.storage_usage"),
                    () -> bytesPair(getTotalUsedBytes(), getTotalBytes()),
                    () -> ECOHostStyles.ratio(getTotalUsedBytes(), getTotalBytes())
                ),
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.storage.energy_buffer"),
                    () -> numberPair(storedEnergy, maxEnergy),
                    () -> ECOHostStyles.ratio(storedEnergy, maxEnergy)
                )
            ),
            details,
            () -> Component.translatable("gui.neoecoae.host.storage.footer"),
            buildWindow
        );
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private long getTotalUsedTypes() {
        return sumArray(usedTypes);
    }

    private long getTotalTypes() {
        return sumArray(totalTypes);
    }

    private long getTotalUsedBytes() {
        return sumArray(usedBytes);
    }

    private long getTotalBytes() {
        return sumArray(totalBytes);
    }

    private static long sumArray(long[] array) {
        if (array == null) {
            return 0;
        }
        long total = 0;
        for (long value : array) {
            total += value;
        }
        return total;
    }

    private UIElement createStorageChannelCard(ECOCellType cellType, int id) {
        UIElement card = ECOHostWidgets.card();
        card.addChild(new Label()
            .setText(cellType.desc())
            .textStyle(ECOHostStyles::valueText));
        card.addChild(new Label()
            .setText(Component.literal(storageChannelHint(cellType, id)))
            .textStyle(ECOHostStyles::hintText));
        card.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.metric.types",
            () -> numberPair(getArrayValue(usedTypes, id), getArrayValue(totalTypes, id)),
            () -> ECOHostStyles.ratio(getArrayValue(usedTypes, id), getArrayValue(totalTypes, id))
        ));
        card.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.metric.bytes",
            () -> bytesPair(getArrayValue(usedBytes, id), getArrayValue(totalBytes, id)),
            () -> ECOHostStyles.ratio(getArrayValue(usedBytes, id), getArrayValue(totalBytes, id))
        ));
        return card;
    }

    private static Component numberPair(long used, long total) {
        return Component.literal(used + " / " + total);
    }

    private static Component bytesPair(long used, long total) {
        return Component.literal(Tooltips.ofBytes(used).getString() + " / " + Tooltips.ofBytes(total).getString());
    }

    private static String storageChannelHint(ECOCellType cellType, int id) {
        ResourceLocation key = NERegistries.CELL_TYPE.getKey(cellType);
        if (key == null) {
            return "Channel #" + id;
        }
        return "Channel #" + id + " - " + key;
    }

    private void resetStorageInfosIfNeeded() {
        int typeCount = getCellTypeCount();
        if (usedTypes == null || totalTypes == null || usedBytes == null || totalBytes == null) {
            resetStorageInfos();
            return;
        }
        if (usedTypes.length != typeCount || totalTypes.length != typeCount || usedBytes.length != typeCount || totalBytes.length != typeCount) {
            resetStorageInfos();
        }
    }

    private int getCellTypeCount() {
        return Math.max(NERegistries.CELL_TYPE.size(), 1);
    }

    private static long getArrayValue(long[] array, int index) {
        if (array == null || index < 0 || index >= array.length) {
            return 0;
        }
        return array[index];
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

    private MultiBlockDefinition getBuildDefinition() {
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

    private MultiBlockPlacementPlan createLocalPreviewPlan() {
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
