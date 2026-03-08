package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.client.gui.Icon;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.AETextures;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
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
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ECOCraftingSystemBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, IGridTickable {

    public static final int MAX_COOLANT = 100000;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Getter
    @Persisted
    @DescSynced
    private boolean overclocked = false;

    @Getter
    @Persisted
    @DescSynced
    private boolean activeCooling = false;

    @Getter
    @Persisted
    @DescSynced
    private int coolant = 0;

    @DescSynced
    private int patternBusCount, parallelCount, workerCount = 0;

    @Getter
    @DescSynced
    private int runningThreadCount = 0;

    @Getter
    @DescSynced
    private int threadCount = 0;

    @Getter
    @DescSynced
    private int threadCountPerWorker = 0;

    @Getter
    @DescSynced
    private int overlockTimes = 0;
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

    public ECOCraftingSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markForUpdate();
                updateInfo();
            });
        }
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            updateInfo();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (coolant >= MAX_COOLANT) {
            return TickRateModulation.IDLE;
        }
        if (cluster != null && cluster.getInputHatch() != null && cluster.getOutputHatch() != null && getLevel() != null) {
            FluidTank inputHatch = cluster.getInputHatch().tank;
            FluidTank outputHatch = cluster.getOutputHatch().tank;
            var r = getLevel().getRecipeManager().getRecipeFor(
                NERecipeTypes.COOLING.get(),
                new CoolingRecipe.Input(inputHatch.getFluid(), outputHatch.getFluid()),
                getLevel()
            );
            if (r.isPresent()) {
                CoolingRecipe recipe = r.get().value();
                FluidStack input = null;
                FluidStack output = recipe.output();
                boolean canConsume = false;
                for (FluidStack fluid : recipe.input().getFluids()) {
                    if (FluidStack.matches(fluid, inputHatch.drain(fluid, IFluidHandler.FluidAction.SIMULATE))) {
                        canConsume = true;
                        input = fluid;
                    }
                }
                if (canConsume && outputHatch.fill(output, IFluidHandler.FluidAction.SIMULATE) == output.getAmount()) {
                    inputHatch.drain(input, IFluidHandler.FluidAction.EXECUTE);
                    outputHatch.fill(output, IFluidHandler.FluidAction.EXECUTE);
                    coolant += recipe.coolant();
                    return TickRateModulation.FASTER;
                }
            }
        }
        return TickRateModulation.IDLE;
    }

    private void updateInfo() {
        updateThreadCount();
        updateCount();
        updateOverlockTimes();
    }

    private void updateThreadCount() {
        if (cluster != null && !cluster.getParallelCores().isEmpty()) {
            int perCore = tier.getCrafterParallel();
            if (overclocked) {
                perCore += tier.getOverclockedCrafterParallel();
                threadCountPerWorker = 32 * getTier().getOverclockedCrafterQueueMultiply();
            } else {
                threadCountPerWorker = 32;
            }
            threadCount = cluster.getParallelCores().size() * perCore;
            runningThreadCount = cluster.getWorkers().stream().mapToInt(ECOCraftingWorkerBlockEntity::getRunningThreads).sum();
        } else {
            threadCount = 0;
        }
    }

    private void updateCount() {
        if (cluster != null) {
            parallelCount = cluster.getParallelCores().size();
            patternBusCount = cluster.getParallelCores().size();
            workerCount = cluster.getWorkers().size();
        } else {
            parallelCount = 0;
            patternBusCount = 0;
            workerCount = 0;
        }
    }

    private void updateOverlockTimes() {
        int overflow = threadCount - threadCountPerWorker * workerCount;
        float radio = (float) threadCount / overflow;
        overlockTimes = Math.clamp(Math.round(radio / 0.05f), 0, 9);
    }

    public boolean canConsumeCoolant(int coolant) {
        return this.coolant >= coolant;
    }

    public void consumeCoolant(int coolant) {
        this.coolant -= coolant;
    }

    private double getOverflowThreadsPercentage() {
        double totalThread = threadCount;
        return totalThread > 0 ? getOverflowThreads() / totalThread : 0.0;
    }

    private int getOverflowThreads() {
        return Math.min(0, threadCount - getAvailableThreads());
    }

    private int getAvailableThreads() {
        return threadCountPerWorker * workerCount;
    }

    private long getMaxEnergyUsage() {
        if (overclocked && !activeCooling) {
            return getAvailableThreads() * tier.getOverclockedCrafterPowerMultiply() * 100L;
        }
        return getAvailableThreads() * 100L;
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
            .textStyle(ECOCraftingSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(5)));

        textPanel.addScrollViewChildren(
            new Label()
                .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.crafting.pattern_bus_count", patternBusCount)))
                .textStyle(ECOCraftingSystemBlockEntity::textStyle),
            new Label()
                .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.crafting.parallel_core_count", parallelCount)))
                .textStyle(ECOCraftingSystemBlockEntity::textStyle),
            new Label()
                .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.crafting.worker_count", workerCount)))
                .textStyle(ECOCraftingSystemBlockEntity::textStyle)
                .layout(layout -> layout.marginBottom(10))
        );

        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.crafting.working_threads", runningThreadCount, getAvailableThreads(), (int) ((float) runningThreadCount / getAvailableThreads() * 100))))
            .textStyle(ECOCraftingSystemBlockEntity::textStyle));

        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.crafting.total_parallelism", threadCount)))
            .textStyle(ECOCraftingSystemBlockEntity::textStyle));
        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.crafting.total_parallelism.overflow", getOverflowThreads(), (int) (getOverflowThreadsPercentage() * 100))))
            .textStyle(ECOCraftingSystemBlockEntity::textStyle));

        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.crafting.max_energy_usage", Tooltips.ofNumber(getMaxEnergyUsage()))))
            .textStyle(ECOCraftingSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(5)));

        textPanel.addScrollViewChild(new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER))
            .addChildren(
                new TextElement()
                    .setText(Component.translatable("gui.neoecoae.crafting.enable_overlock"))
                    .textStyle(ECOCraftingSystemBlockEntity::textStyle),
                new Switch()
                    .bind(DataBindingBuilder.bool(() -> overclocked, b -> overclocked = b).build()))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, e -> {
                e.hoverTooltips = new HoverTooltips(
                    List.of(Component.translatable("gui.neoecoae.crafting.overclocked.tooltip")),
                    null,
                    null,
                    null
                );
            }));
        textPanel.addScrollViewChild(new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER))
            .addChildren(
                new TextElement()
                    .setText(Component.translatable("gui.neoecoae.crafting.enable_active_cooling"))
                    .textStyle(ECOCraftingSystemBlockEntity::textStyle),
                new Switch()
                    .bind(DataBindingBuilder.bool(() -> activeCooling, b -> activeCooling = b).build()))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, e -> {
                e.hoverTooltips = new HoverTooltips(
                    List.of(Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip")),
                    null,
                    null,
                    null
                );
            }));

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
            .textStyle(ECOCraftingSystemBlockEntity::buildPanelTextStyle));
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
                    .textStyle(ECOCraftingSystemBlockEntity::buildPanelTextStyle),
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
            .textStyle(ECOCraftingSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.missing", previewMissingBlocks)))
            .textStyle(ECOCraftingSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.conflicts", previewConflictBlocks)))
            .textStyle(ECOCraftingSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.multiblock.required_items", previewRequiredItems)))
            .textStyle(ECOCraftingSystemBlockEntity::buildPanelTextStyle));
        window.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(this::buildPreviewStatusComponent))
            .textStyle(ECOCraftingSystemBlockEntity::buildPanelTextStyle));

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

    private @Nullable MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getCraftingSystemDefinition(tier);
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

