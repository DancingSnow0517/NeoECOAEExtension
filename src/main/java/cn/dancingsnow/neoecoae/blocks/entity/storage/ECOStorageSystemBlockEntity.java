package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.StorageHostPanelUI;
import cn.dancingsnow.neoecoae.gui.StoragePriority;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import appeng.api.storage.IStorageProvider;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
        StorageHostActionUI.Elements actionUI = createActionUI(holder);

        UIElement root = new UIElement().layout(layout -> {
            layout.width(344);
            layout.height(232);
            layout.gapAll(0);
        }).addClass("panel_bg");

        root.addChild(new TextElement()
            .setText(getItemFromBlockEntity().getDescription())
            .textStyle(ECOStorageSystemBlockEntity::titleTextStyle)
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(8);
                layout.top(8);
            }));

        UIElement panels = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(6);
            layout.top(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(4);
        });
        panels.addChild(StorageHostPanelUI.createLeftPanel(createStoragePanelConfig()));
        panels.addChild(StorageHostPanelUI.createRightPanel());

        root.addChild(panels);
        actionUI.addTo(root);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static void titleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }

    private StorageHostPanelUI.Config createStoragePanelConfig() {
        return new StorageHostPanelUI.Config(
            this::getStoredEnergy,
            this::getMaxEnergy,
            NERegistries.CELL_TYPE.stream()
                .map(cellType -> {
                    int id = NERegistries.CELL_TYPE.getId(cellType);
                    return new StorageHostPanelUI.StorageTypeLine(
                        cellType,
                        id,
                        () -> getStorageValue(id, StorageValue.USED_TYPES),
                        () -> getStorageValue(id, StorageValue.TOTAL_TYPES),
                        () -> getStorageValue(id, StorageValue.USED_BYTES),
                        () -> getStorageValue(id, StorageValue.TOTAL_BYTES)
                    );
                })
                .toList()
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    private long getStoredEnergy() {
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
    private long getMaxEnergy() {
        if (cluster == null) {
            return 0;
        }
        long total = 0;
        for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
            total += (long) energyCell.getAEMaxPower();
        }
        return total;
    }

    private long getStorageValue(int cellTypeId, StorageValue value) {
        if (cluster == null || cellTypeId < 0) {
            return 0;
        }
        long total = 0;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell inv = drive.getCellInventory();
            if (inv == null || NERegistries.CELL_TYPE.getId(inv.getCellType()) != cellTypeId) {
                continue;
            }
            total += getCellValue(inv, value);
        }
        return total;
    }

    private static long getCellValue(IECOStorageCell inv, StorageValue value) {
        return switch (value) {
            case USED_TYPES -> inv.getStoredItemTypes();
            case TOTAL_TYPES -> inv.getTotalItemTypes();
            case USED_BYTES -> inv.getUsedBytes();
            case TOTAL_BYTES -> inv.getTotalBytes();
        };
    }

    private enum StorageValue {
        USED_TYPES,
        TOTAL_TYPES,
        USED_BYTES,
        TOTAL_BYTES
    }

    private StorageHostActionUI.Elements createActionUI(BlockUIMenuType.BlockUIHolder holder) {
        return StorageHostActionUI.create(new StorageHostActionUI.Config(
            holder.player,
            () -> selectedBuildLength,
            () -> mirrorBuild,
            mirror -> setMirrorBuild(holder.player, mirror),
            () -> decreaseBuildLength(holder.player),
            () -> increaseBuildLength(holder.player),
            () -> autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            this::createLocalPreviewPlan,
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
