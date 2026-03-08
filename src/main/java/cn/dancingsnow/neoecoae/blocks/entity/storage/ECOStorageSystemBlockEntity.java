package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.client.gui.Icon;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.gui.AETextures;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import lombok.Getter;
import net.minecraft.core.BlockPos;
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
        return new TickingRequest(20, 20, false);
    }


    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        updateInfos();
        return TickRateModulation.URGENT;
    }

    private void resetStorageInfos() {
        int typeCount = NERegistries.CELL_TYPE.size();
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

            usedTypes = new long[AEKeyTypesInternal.getAllTypes().size()];
            totalTypes = new long[AEKeyTypesInternal.getAllTypes().size()];
            usedBytes = new long[AEKeyTypesInternal.getAllTypes().size()];
            totalBytes = new long[AEKeyTypesInternal.getAllTypes().size()];
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                IECOStorageCell inv = drive.getCellInventory();
                if (inv != null) {
                    ECOCellType cellType = inv.getCellType();
                    int id = NERegistries.CELL_TYPE.getId(cellType);
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

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .paddingAll(4)
            .gapAll(2)
            .justifyContent(AlignContent.CENTER)
        ).addClass("panel_bg");

        UIElement buildWindow = buildPanel(holder);

        ScrollerView textPanel = new ScrollerView().viewContainer(view -> view.getLayout().gapAll(2));
        textPanel.addScrollViewChild(new TextElement()
            .setText(getItemFromBlockEntity().getDescription())
            .textStyle(ECOStorageSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(5)));
        NERegistries.CELL_TYPE.stream()
            .forEachOrdered(cellType -> {
                int id = NERegistries.CELL_TYPE.getId(cellType);
                textPanel.addScrollViewChild(new Label()
                    .setText(cellType.desc())
                    .textStyle(ECOStorageSystemBlockEntity::textStyle));
                textPanel.addScrollViewChild(new Label()
                    .bindDataSource(SupplierDataSource.of(() -> Tooltips.typesUsed(usedTypes[id], totalTypes[id])))
                    .textStyle(ECOStorageSystemBlockEntity::textStyle));
                textPanel.addScrollViewChild(new Label()
                    .bindDataSource(SupplierDataSource.of(() -> Tooltips.bytesUsed(usedBytes[id], totalBytes[id])))
                    .textStyle(ECOStorageSystemBlockEntity::textStyle));
            });

        textPanel.addScrollViewChild(new Label().setText("").textStyle(ECOStorageSystemBlockEntity::textStyle));

        textPanel.addScrollViewChild(new Label()
            .setText(Component.translatable("gui.neoecoae.storage.energy"))
            .textStyle(ECOStorageSystemBlockEntity::textStyle));
        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable(
                "gui.neoecoae.storage.energy_status",
                Tooltips.ofNumber(storedEnergy),
                Tooltips.ofNumber(maxEnergy),
                maxEnergy > 0 ? (int) ((double) storedEnergy / maxEnergy * 100) : 0
            )))
            .textStyle(ECOStorageSystemBlockEntity::textStyle));

        textPanel.layout(layout -> layout.height(160).width(220));

        UIElement buildButtonPanel = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(-22);
            layout.top(0);
            layout.paddingAll(2);
            layout.paddingBottom(4);
        }).style(style -> style.background(NETextures.BACKGROUND));
        buildButtonPanel.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(Icon.CRAFT_HAMMER))
            .setOnClick(event -> buildWindow.layout(layout -> layout.display(TaffyDisplay.FLEX)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                event.hoverTooltips = new HoverTooltips(
                    List.of(Component.translatable("gui.neoecoae.multiblock.builder")),
                    null,
                    null,
                    null
                );
            })
            .layout(layout -> {
                layout.width(18);
                layout.height(20);
            }));

        root.addChild(textPanel);
        root.addChild(buildButtonPanel);
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private UIElement buildPanel(BlockUIMenuType.BlockUIHolder holder) {
        UIElement window = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(6);
            layout.top(6);
            layout.display(TaffyDisplay.NONE);
            layout.paddingAll(4);
            layout.gapAll(2);
            layout.width(160);
        }).addClass("panel_bg");

        UIElement titleBar = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(2);
        });
        titleBar.addChild(new TextElement()
            .setText(Component.translatable("gui.neoecoae.multiblock.builder"))
            .textStyle(ECOStorageSystemBlockEntity::buildPanelTextStyle));
        titleBar.addChild(new Button()
            .setText("X")
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                event.hoverTooltips = new HoverTooltips(
                    List.of(Component.translatable("gui.neoecoae.multiblock.close_builder")),
                    null,
                    null,
                    null
                );
            })
            .layout(layout -> layout.width(16).height(16)));
        WindowDragHelper.setDragMove(titleBar, window, null, null);
        window.addChild(titleBar);

        window.addChild(new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2))
            .addChildren(
                new Button()
                    .setText("-")
                    .setOnServerClick(event -> decreaseBuildLength())
                    .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                        event.hoverTooltips = new HoverTooltips(
                            List.of(Component.translatable("gui.neoecoae.multiblock.decrease_length")),
                            null,
                            null,
                            null
                        );
                    })
                    .layout(layout -> layout.width(18).height(18)),
                new Label()
                    .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.length", selectedBuildLength)))
                    .textStyle(ECOStorageSystemBlockEntity::buildPanelTextStyle),
                new Button()
                    .setText("+")
                    .setOnServerClick(event -> increaseBuildLength())
                    .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                        event.hoverTooltips = new HoverTooltips(
                            List.of(Component.translatable("gui.neoecoae.multiblock.increase_length")),
                            null,
                            null,
                            null
                        );
                    })
                    .layout(layout -> layout.width(18).height(18))
            ));

        window.addChild(new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).gapAll(4))
            .addChildren(
                new Button()
                    .setText("gui.neoecoae.multiblock.preview", true)
                    .setOnServerClick(event -> previewStructure(holder.player))
                    .layout(layout -> layout.width(48).height(18)),
                new Button()
                    .setText("gui.neoecoae.multiblock.build", true)
                    .setOnServerClick(event -> autoBuild(holder.player))
                    .layout(layout -> layout.width(48).height(18))
            ));

        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.reused", previewReusedBlocks)))
            .textStyle(ECOStorageSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.missing", previewMissingBlocks)))
            .textStyle(ECOStorageSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.conflicts", previewConflictBlocks)))
            .textStyle(ECOStorageSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.required_items", previewRequiredItems)))
            .textStyle(ECOStorageSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(this::buildPreviewStatusComponent))
            .textStyle(ECOStorageSystemBlockEntity::buildPanelTextStyle));

        return window;
    }

    private void increaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    private void decreaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    private void previewStructure(Player player) {
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
        selectedBuildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength);
        boolean hasMaterials = player instanceof ServerPlayer serverPlayer
            && MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems());
        String statusKey = plan.getConflictPositions().isEmpty()
            ? (plan.getMissingBlocks().isEmpty() ? "gui.neoecoae.multiblock.status.structure_ready" : (hasMaterials ? "gui.neoecoae.multiblock.status.ready_to_build" : "gui.neoecoae.multiblock.status.not_enough_items"))
            : "gui.neoecoae.multiblock.status.conflicts_detected";
        syncPreview(plan.getMissingBlocks().size(), plan.getConflictPositions().size(), plan.getReusedBlockCount(), plan.getRequiredItemCount(), statusKey);
    }

    private void autoBuild(Player player) {
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
        selectedBuildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
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

    private static void textStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0xadb0c4).textShadow(false);
    }

    private static void buildPanelTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }
}
