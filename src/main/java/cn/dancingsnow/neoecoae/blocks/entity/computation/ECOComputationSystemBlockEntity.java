package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.client.gui.Icon;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.AETextures;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity> implements ISyncPersistRPCBlockEntity {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    private int usedThread;
    @DescSynced
    private int totalThread;
    @DescSynced
    private int parallelCount;
    @DescSynced
    private long availableBytes;
    @DescSynced
    private long totalBytes;
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

    public ECOComputationSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            updateInfos();
        }
    }

    public void updateInfos() {
        if (cluster != null) {
            availableBytes = cluster.getAvailableStorage();
            totalBytes = 0;
            for (ECOComputationDriveBlockEntity drive : cluster.getUpperDrives()) {
                ItemStack cellStack = drive.getCellStack();
                if (cellStack != null && cellStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    totalBytes += cellItem.getTier().getCPUTotalBytes();
                }
            }
            for (ECOComputationDriveBlockEntity drive : cluster.getLowerDrives()) {
                ItemStack cellStack = drive.getCellStack();
                if (cellStack != null && cellStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    totalBytes += cellItem.getTier().getCPUTotalBytes();
                }
            }

            usedThread = cluster.getActiveCPUs().size();
            totalThread = cluster.getMaxThreads();
            parallelCount = cluster.getParallelCores().stream().mapToInt(e -> e.getTier().getCPUAccelerators()).sum();
        } else {
            totalThread = 0;
            parallelCount = 0;
            availableBytes = 0;
            totalBytes = 0;
        }
        setChanged();
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
            .paddingAll( 4)
            .gapAll(2)
            .justifyContent(AlignContent.CENTER)
        ).addClass("panel_bg");

        UIElement buildWindow = buildPanel(holder);

        ScrollerView textPanel = new ScrollerView().viewContainer(view -> view.getLayout().gapAll(2));
        textPanel.addScrollViewChild(new TextElement()
            .setText(getItemFromBlockEntity().getDescription())
            .textStyle(ECOComputationSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(5)));

        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.computation.thread_info", usedThread, totalThread)))
            .textStyle(ECOComputationSystemBlockEntity::textStyle));
        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.computation.parallel_info", parallelCount)))
            .textStyle(ECOComputationSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(10)));

        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.computation.storage_info", Tooltips.ofBytes(availableBytes), Tooltips.ofBytes(totalBytes))))
            .textStyle(ECOComputationSystemBlockEntity::textStyle));

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
            .addPostIcon(AETextures.icon(Icon.HELP))
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
            .textStyle(ECOComputationSystemBlockEntity::buildPanelTextStyle));
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
                    .textStyle(ECOComputationSystemBlockEntity::buildPanelTextStyle),
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
            .textStyle(ECOComputationSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.missing", previewMissingBlocks)))
            .textStyle(ECOComputationSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.conflicts", previewConflictBlocks)))
            .textStyle(ECOComputationSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.required_items", previewRequiredItems)))
            .textStyle(ECOComputationSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(this::buildPreviewStatusComponent))
            .textStyle(ECOComputationSystemBlockEntity::buildPanelTextStyle));

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
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            serverLevel,
            worldPosition,
            getBlockState(),
            definition,
            selectedBuildLength
        );
        boolean hasMaterials = player instanceof ServerPlayer serverPlayer
            && MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems());
        String statusKey = plan.getConflictPositions().isEmpty()
            ? (plan.getMissingBlocks().isEmpty()
                ? "gui.neoecoae.multiblock.status.structure_ready"
                : (hasMaterials ? "gui.neoecoae.multiblock.status.ready_to_build" : "gui.neoecoae.multiblock.status.not_enough_items"))
            : "gui.neoecoae.multiblock.status.conflicts_detected";
        syncPreview(
            plan.getMissingBlocks().size(),
            plan.getConflictPositions().size(),
            plan.getReusedBlockCount(),
            plan.getRequiredItemCount(),
            statusKey
        );
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
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            serverLevel,
            worldPosition,
            getBlockState(),
            definition,
            selectedBuildLength
        );
        if (!plan.getConflictPositions().isEmpty()) {
            syncPreview(
                plan.getMissingBlocks().size(),
                plan.getConflictPositions().size(),
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.conflicts_detected"
            );
            return;
        }
        if (!serverPlayer.isCreative() && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            syncPreview(
                plan.getMissingBlocks().size(),
                0,
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.not_enough_items"
            );
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
                    "gui.neoecoae.multiblock.status.build_failed"
                );
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
        return NEMultiBlocks.getComputationSystemDefinition(tier);
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
