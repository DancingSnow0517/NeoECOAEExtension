package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostMetric;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostSwitchRow;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.util.ComponentUtil;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ECOCraftingSystemBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, IGridTickable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    public static final int MAX_COOLANT = 1_000_000;
    private static final int COOLANT_PER_CRAFT = 5;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Getter
    @Persisted
    private boolean overclocked = false;

    @Getter
    @Persisted
    private boolean activeCooling = false;

    @Getter
    @Persisted
    private int coolant = 0;
    @Getter
    @Persisted
    private int coolantMaxOverclock = -1;

    private int patternBusCount, parallelCount, workerCount = 0;

    @Getter
    private int runningThreadCount = 0;

    @Getter
    private int threadCount = 0;

    @Getter
    private int threadCountPerWorker = 0;

    @Getter
    private int overlockTimes = 0;
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
    @Setter
    private boolean mirrored;

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
        updateInfo();
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                setChanged();
                markForUpdate();
                updateInfo();
            });
        }
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOCraftingSystem.MIRRORED)) {
                BlockState newState = state.setValue(ECOCraftingSystem.MIRRORED, formed && mirrored);
                if (newState != state) {
                    level.setBlock(
                        worldPosition,
                        newState,
                        Block.UPDATE_CLIENTS
                    );
                }
            }
        }
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
        if (!activeCooling) {
            return TickRateModulation.IDLE;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        if (recipe == null) {
            return TickRateModulation.IDLE;
        }
        if (!canRefillWith(recipe.maxOverclock())) {
            return TickRateModulation.IDLE;
        }

        int targetCoolant = getTargetCoolantBuffer();
        if (targetCoolant <= coolant) {
            return TickRateModulation.IDLE;
        }

        int refillAmount = refillCoolant(recipe, targetCoolant - coolant);
        if (refillAmount <= 0) {
            return TickRateModulation.IDLE;
        }
        return coolant < targetCoolant ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    private void updateInfo() {
        updateCount();
        updateThreadCount();
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
            recalculateRunningThreadCountFromWorkers();
        } else {
            threadCount = 0;
            threadCountPerWorker = 0;
            runningThreadCount = 0;
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

    public int getWorkerCount() {
        if (cluster != null) {
            return cluster.getWorkers().size();
        }
        return workerCount;
    }

    public void onWorkerThreadCountChanged(int delta) {
        int before = runningThreadCount;
        runningThreadCount += delta;
        if (runningThreadCount < 0) {
            LOGGER.warn(
                "ECO controller runningThreadCount underflow: controller={} delta={} before correction previous={}",
                getBlockPos(),
                delta,
                before
            );
            runningThreadCount = 0;
        }
        setChanged();
    }

    public void recalculateRunningThreadCountFromWorkers() {
        if (cluster == null) {
            runningThreadCount = 0;
            return;
        }

        runningThreadCount = cluster.getWorkers()
            .stream()
            .mapToInt(ECOCraftingWorkerBlockEntity::getRunningThreads)
            .sum();
    }

    private void updateOverlockTimes() {
        int overflow = threadCount - threadCountPerWorker * workerCount;
        if (overflow <= 0) {
            overlockTimes = 0;
            return;
        }
        float radio = (float) threadCount / overflow;
        overlockTimes = Math.clamp(Math.round(radio / 0.05f), 0, 9);
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        if (amount <= 0) {
            return true;
        }
        if (coolant < amount) {
            return false;
        }
        if (requiredOverclock > 0 && coolantMaxOverclock < requiredOverclock) {
            return false;
        }
        coolant -= amount;
        if (coolant == 0) {
            coolantMaxOverclock = -1;
        }
        setChanged();
        return true;
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!activeCooling || requestedCrafts <= 0) {
            return Math.max(0, requestedCrafts);
        }
        if (coolantPerCraft <= 0) {
            return Math.max(0, requestedCrafts);
        }
        if (requiredOverclock > 0 && coolantMaxOverclock < requiredOverclock) {
            return 0;
        }
        return Math.min(requestedCrafts, coolant / coolantPerCraft);
    }

    public int getEffectiveOverclockTimes() {
        if (!overclocked) {
            return 0;
        }
        if (!activeCooling) {
            return overlockTimes;
        }
        int coolingMaxOverclock = getCurrentCoolingMaxOverclock();
        if (coolingMaxOverclock < 0) {
            return 0;
        }
        return Math.min(overlockTimes, coolingMaxOverclock);
    }

    public int getDisplayedCoolingMaxOverclock() {
        return getCurrentCoolingMaxOverclock();
    }

    public void clearCoolant() {
        coolant = 0;
        coolantMaxOverclock = -1;
        setChanged();
    }

    private int getOverflowThreads() {
        return Math.max(0, threadCount - getAvailableThreads());
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

    @Nullable
    private CoolingRecipe getCoolingRecipe() {
        if (cluster == null || cluster.getInputHatch() == null || cluster.getOutputHatch() == null || getLevel() == null) {
            return null;
        }
        FluidTank inputHatch = cluster.getInputHatch().tank;
        if (inputHatch.getFluidAmount() <= 0) {
            return null;
        }
        FluidTank outputHatch = cluster.getOutputHatch().tank;
        return getLevel().getRecipeManager().getRecipeFor(
            NERecipeTypes.COOLING.get(),
            new CoolingRecipe.Input(inputHatch.getFluid(), outputHatch.getFluid()),
            getLevel()
        ).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
    }

    private boolean canRefillWith(int maxOverclock) {
        return coolant <= 0 || coolantMaxOverclock < 0 || coolantMaxOverclock == maxOverclock;
    }

    private int getRequiredCoolingOverclock() {
        return getEffectiveOverclockTimes();
    }

    private int getCurrentCoolingMaxOverclock() {
        if (coolant > 0 && coolantMaxOverclock >= 0) {
            return coolantMaxOverclock;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        return recipe == null ? -1 : recipe.maxOverclock();
    }

    private int getTargetCoolantBuffer() {
        int requiredPerTick = getAvailableThreads() * COOLANT_PER_CRAFT;
        if (requiredPerTick <= 0) {
            return 0;
        }
        return MAX_COOLANT;
    }

    private int refillCoolant(CoolingRecipe recipe, int deficit) {
        if (cluster == null || cluster.getInputHatch() == null || cluster.getOutputHatch() == null) {
            return 0;
        }
        FluidTank inputHatch = cluster.getInputHatch().tank;
        FluidTank outputHatch = cluster.getOutputHatch().tank;
        int inputAmount = recipe.inputAmount();
        if (deficit <= 0 || inputAmount <= 0 || recipe.coolant() <= 0) {
            return 0;
        }

        long requiredInput = ((long) deficit * inputAmount + recipe.coolant() - 1L) / recipe.coolant();
        long drainAmount = Math.min(requiredInput, inputHatch.getFluidAmount());
        drainAmount = Math.min(drainAmount, getMaxDrainByOutput(recipe, outputHatch));
        if (drainAmount <= 0) {
            return 0;
        }

        int drained = inputHatch.drain((int) drainAmount, IFluidHandler.FluidAction.EXECUTE).getAmount();
        if (drained <= 0) {
            return 0;
        }

        FluidStack output = recipe.output();
        if (!output.isEmpty()) {
            int outputAmount = (int) ((long) drained * recipe.outputAmount() / inputAmount);
            if (outputAmount > 0) {
                outputHatch.fill(output.copyWithAmount(outputAmount), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        int coolantGain = (int) ((long) drained * recipe.coolant() / inputAmount);
        if (coolantGain <= 0) {
            return 0;
        }
        coolant = Math.min(MAX_COOLANT, coolant + coolantGain);
        coolantMaxOverclock = recipe.maxOverclock();
        setChanged();
        return coolantGain;
    }

    private long getMaxDrainByOutput(CoolingRecipe recipe, FluidTank outputHatch) {
        FluidStack output = recipe.output();
        if (output.isEmpty()) {
            return Long.MAX_VALUE;
        }
        FluidStack stored = outputHatch.getFluid();
        if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, output)) {
            return 0;
        }
        int outputAmount = recipe.outputAmount();
        if (outputAmount <= 0) {
            return Long.MAX_VALUE;
        }
        long outputSpace = outputHatch.getCapacity() - outputHatch.getFluidAmount();
        return outputSpace * recipe.inputAmount() / outputAmount;
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
        UIElement buildWindow = buildPanel(holder);

        UIElement details = ECOHostWidgets.detailArea(false);
        ECOHostWidgets.addDetailChild(details, ECOHostWidgets.sectionTitle("gui.neoecoae.host.crafting.overclock_cooling"));
        ECOHostWidgets.addDetailChild(details, createOverclockCoolingCard());

        UIElement root = ECOHostWidgets.hostPanel(
            () -> getItemFromBlockEntity().getDescription(),
            () -> Component.translatable("gui.neoecoae.host.crafting.subtitle"),
            () -> Component.translatable(buildInProgress ? "gui.neoecoae.host.status.running" : "gui.neoecoae.host.status.online"),
            List.of(
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.crafting.working_threads"),
                    () -> ComponentUtil.coloredNumberPair(runningThreadCount, getAvailableThreads(), false),
                    () -> ECOHostStyles.ratio(runningThreadCount, getAvailableThreads())
                ),
                ECOHostMetric.scalar(
                    () -> Component.translatable("gui.neoecoae.host.crafting.overflow"),
                    () -> Component.literal(String.valueOf(getOverflowThreads()))
                ),
                ECOHostMetric.scalar(
                    () -> Component.translatable("gui.neoecoae.host.crafting.max_energy_usage"),
                    () -> Tooltips.ofNumber(getMaxEnergyUsage()).append(" AE")
                )
            ),
            details,
            () -> Component.translatable("gui.neoecoae.host.crafting.footer"),
            buildWindow
        );
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private UIElement createOverclockCoolingCard() {
        UIElement card = ECOHostWidgets.card();
        Label summary = new Label();
        summary.bind(DataBindingBuilder.componentS2C(this::buildOverclockSummaryComponent).build());
        summary.textStyle(ECOHostStyles::compactHintText);
        card.addChild(summary);
        card.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.crafting.coolant",
            () -> ComponentUtil.coloredNumberPair(coolant, MAX_COOLANT, true),
            () -> ECOHostStyles.ratio(coolant, MAX_COOLANT)
        ));
        card.addChild(createControls());
        return card;
    }

    private UIElement createControls() {
        UIElement controls = new UIElement().layout(layout -> layout
            .flexDirection(FlexDirection.COLUMN)
            .gapAll(2)
            .height(29)
        );
        controls.addChildren(
            new ECOHostSwitchRow(
                Component.translatable("gui.neoecoae.crafting.enable_overlock"),
                Component.translatable("gui.neoecoae.crafting.overclocked.tooltip"),
                () -> overclocked,
                this::setOverclocked
            ).layout(layout -> layout.width(190)),
            new ECOHostSwitchRow(
                Component.translatable("gui.neoecoae.crafting.enable_active_cooling"),
                Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip"),
                () -> activeCooling,
                this::setActiveCooling
            ).layout(layout -> layout.width(190))
        );
        return controls;
    }

    private void setOverclocked(boolean overclocked) {
        if (this.overclocked == overclocked) {
            return;
        }
        this.overclocked = overclocked;
        updateInfo();
        setChanged();
    }

    private void setActiveCooling(boolean activeCooling) {
        if (this.activeCooling == activeCooling) {
            return;
        }
        this.activeCooling = activeCooling;
        setChanged();
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

    private Component buildOverclockSummaryComponent() {
        int displayedMaxOverclock = getCurrentCoolingMaxOverclock();
        return Component.translatable(
            "gui.neoecoae.host.crafting.overclock_summary",
            overlockTimes,
            getEffectiveOverclockTimes(),
            displayedMaxOverclock < 0 ? "-" : Tooltips.ofNumber(displayedMaxOverclock)
        );
    }
}

