package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.GuiTextures;
import cn.dancingsnow.neoecoae.gui.widget.*;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget;
import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

public class ECOCraftingSystemBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingSystemBlockEntity>
    implements IUIHolder.Block, IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IManaged, IGridTickable {

    public static final int MAX_COOLANT = 100000;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ECOCraftingSystemBlockEntity.class);
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
    private int threadCount = 0;

    @Getter
    @DescSynced
    private int threadCountPerWorker = 0;

    @Getter
    @DescSynced
    private int overlockTimes = 0;

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
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public IManagedStorage getSyncStorage() {
        return syncStorage;
    }

    @Override
    public IManagedStorage getRootStorage() {
        return getSyncStorage();
    }

    @Override
    public void onChanged() {
        setChanged();
        markForUpdate();
        updateInfo();
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
        overlockTimes = Math.min(Math.round(radio / 0.05f), 9);
    }

    public boolean canConsumeCoolant(int coolant) {
        return this.coolant >= coolant;
    }

    public void consumeCoolant(int coolant) {
        this.coolant -= coolant;
    }

    private WidgetGroup createUI() {
        var inventory = new PlayerInventoryWidget();
        inventory.setSlotBackground(GuiTextures.Crafting.SLOT);
        inventory.setSelfPosition(2, 51);

        WidgetGroup root = new TranslucentBackgroundWidgetGroup(0, 0, 243, 140)
            .addWidget(new WidgetGroup(7, 15, 228, 36) // controlPanel
                .addWidget(new ExtendedSwitchWidget(
                    0, 0, 36, 36,
                    (data, isPressed) -> this.overclocked = isPressed)
                    .setTooltipSupplier(isPressed -> List.of(isPressed
                        ? Component.translatable("gui.neoecoae.crafting.overclocked.disable.tip")
                        : Component.translatable("gui.neoecoae.crafting.overclocked.enable.tip")
                    ))
                    .setMouseDownTexture(GuiTextures.OVERCLOCK_ON_DOWN)
                    .setPressedMouseDownTexture(GuiTextures.OVERCLOCK_OFF_DOWN)
                    .setBaseTexture(GuiTextures.OVERCLOCK_OFF)
                    .setPressedTexture(GuiTextures.OVERCLOCK_ON)
                    .setSupplier(() -> this.overclocked))
                .addWidget(new ExtendedSwitchWidget(
                    40, 0, 36, 36,
                    (data, isPressed) -> this.activeCooling = isPressed)
                    .setTooltipSupplier(isPressed -> {
                        var tooltips = new ArrayList<Component>();
                        if (isPressed) {
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.disable.tip"));
                        } else {
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.enable.tip.0"));
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.enable.tip.1"));
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.enable.tip.2"));
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.enable.tip.3"));
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.enable.tip.4"));
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.enable.tip.5"));
                            tooltips.add(Component.translatable("gui.neoecoae.crafting.active_cooling.enable.tip.6"));
                        }
                        return tooltips;
                    })
                    .setMouseDownTexture(GuiTextures.COOLING_ON_DOWN)
                    .setPressedMouseDownTexture(GuiTextures.COOLING_OFF_DOWN)
                    .setBaseTexture(GuiTextures.COOLING_OFF)
                    .setPressedTexture(GuiTextures.COOLING_ON)
                    .setSupplier(() -> this.activeCooling))
                .addWidget(new WidgetGroup(80, 0, 148, 36)
                    .addWidget(new WidgetGroup(2, 2, 40, 32)
                        .addWidget(new ImageWidget(2, 2, 10, 9, tier::getCraftingOverlayTexture))
                        .addWidget(new ScalableTextBoxWidget(2, 14, 36, 18)
                            .setTextSupplier(() -> List.of(
                                Component.translatable("gui.neoecoae.crafting.pattern_bus_count", patternBusCount),
                                Component.translatable("gui.neoecoae.crafting.parallel_core_count", parallelCount),
                                Component.translatable("gui.neoecoae.crafting.worker_count", workerCount)
                            ))
                            .setShadow(true))
                        .setBackground(GuiTextures.Crafting.PANEL))
                    .addWidget(new WidgetGroup(43, 2, 51, 32)
                        .addWidget(new ScalableTextBoxWidget(2, 2, 36, 28)
                            .setTextSupplier(() -> List.of(
                                Component.translatable("gui.neoecoae.crafting.crafting_progress", (int) (getRunningThreadsPercentage() * 100)),
                                Component.translatable("gui.neoecoae.crafting.crafting_progress.1", getRunningThreads(), getAvailableThreads())
                            ))
                            .setShadow(true))
                        .addWidget(new ProgressWidget(this::getRunningThreadsPercentage, 40, 2, 9, 28)
                            .setProgressTexture(GuiTextures.Crafting.PROGRESS_BAR_EMPTY, GuiTextures.PROGRESS_BAR_CRAFTING)
                            .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP))
                        .setBackground(GuiTextures.Crafting.PANEL))
                    .addWidget(new WidgetGroup(95, 2, 51, 32)
                        .addWidget(new ScalableTextBoxWidget(2, 2, 36, 28)
                            .setTextSupplier(() -> List.of(
                                Component.translatable("gui.neoecoae.crafting.total_parallelism", threadCount),
                                Component.translatable("gui.neoecoae.crafting.total_parallelism.limit", getAvailableThreads()),
                                Component.translatable("gui.neoecoae.crafting.total_parallelism.overflow", getOverflowThreads(), (int) (getOverflowThreadsPercentage() * 100))
                            ))
                            .setShadow(true))
                        .addWidget(new ProgressWidget(this::getOverflowThreadsPercentage, 40, 2, 9, 28)
                            .setProgressTexture(GuiTextures.Crafting.PROGRESS_BAR_EMPTY, GuiTextures.PROGRESS_BAR_LIMIT)
                            .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP))
                        .setBackground(GuiTextures.Crafting.PANEL))
                    .setBackground(new GuiTextureGroup(GuiTextures.Crafting.PANEL_BACKGROUND, GuiTextures.Crafting.PANEL_BORDER))))
            .addWidget(new WidgetGroup(173, 55, 62, 77) // heatStatisticPanel
                .addWidget(new WidgetGroup(3, 3, 56, 9)
                    .addWidget(new ScalableTextBoxWidget(1, 1, 54, 7)
                        .setTextSupplier(() -> List.of(Component.translatable("gui.neoecoae.crafting.max_energy_usage", getFormatedMaxEnergyUsage())))
                        .setCenter(true)
                        .setShadow(true)
                        .appendHoverTooltips(Component.translatable("gui.neoecoae.crafting.max_energy_usage.tip")))
                    .setBackground(GuiTextures.Crafting.PANEL))
                .addWidget(new WidgetGroup(3, 15, 56, 9)
                    .addWidget(new ScalableTextBoxWidget(1, 1, 54, 7)
                        .setTextSupplier(() -> {
                            Component mode;
                            if (activeCooling && overclocked) {
                                mode = Component.translatable("gui.neoecoae.crafting.overclocked.on.3");
                            } else if (activeCooling) {
                                mode = Component.translatable("gui.neoecoae.crafting.overclocked.on.2");
                            } else if (overclocked) {
                                mode = Component.translatable("gui.neoecoae.crafting.overclocked.on.1");
                            } else {
                                mode = Component.translatable("gui.neoecoae.crafting.overclocked.off");
                            }
                            return List.of(Component.translatable("gui.neoecoae.crafting.overclocked", mode));
                        })
                        .setCenter(true)
                        .setShadow(true))
                    .setBackground(GuiTextures.Crafting.PANEL))
                .addWidget(new WidgetGroup(3, 27, 56, 9)
                    .addWidget(new ScalableTextBoxWidget(1, 1, 54, 7)
                        .setTextSupplier(() -> List.of(Component.translatable("gui.neoecoae.crafting.active_cooling", this.activeCooling
                            ? Component.translatable("gui.neoecoae.crafting.active_cooling.on")
                            : Component.translatable("gui.neoecoae.crafting.active_cooling.off"))))
                        .setCenter(true)
                        .setShadow(true))
                    .setBackground(GuiTextures.Crafting.PANEL))
                .addWidget(new ProgressWidget(() -> (double) coolant / MAX_COOLANT, 3, 39, 27, 36)
                    .setProgressTexture(GuiTextures.Crafting.PROGRESS_BAR_EMPTY, GuiTextures.PROGRESS_BAR_COOLANT)
                    .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP))
                .addWidget(new ProgressWidget(() -> {
                    if (cluster != null) {
                        var output = cluster.getOutputHatch().tank;
                        return output.getFluidAmount() / (double) output.getCapacity();
                    }
                    return 0.0;
                }, 32, 39, 27, 36)
                    .setProgressTexture(GuiTextures.Crafting.PROGRESS_BAR_EMPTY, GuiTextures.PROGRESS_BAR_HOT_COOLANT)
                    .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP))
                .setBackground(GuiTextures.Crafting.PANEL_BACKGROUND))
            .addWidget(inventory);
        root.setBackground(GuiTextures.Crafting.BACKGROUND_DARK);
        return root;
    }

    private double getRunningThreadsPercentage() {
        double availableThreads = getAvailableThreads();
        return availableThreads > 0 ? getRunningThreads() / availableThreads : 0.0;
    }

    private double getOverflowThreadsPercentage() {
        double totalThread = threadCount;
        return totalThread > 0 ? getOverflowThreads() / totalThread : 0.0;
    }

    private int getOverflowThreads() {
        return threadCount - getAvailableThreads();
    }

    private int getAvailableThreads() {
        return threadCountPerWorker * workerCount;
    }

    private int getRunningThreads() {
        if (cluster != null) {
            return cluster.getWorkers().stream().mapToInt(ECOCraftingWorkerBlockEntity::getRunningThreads).sum();
        }
        return 0;
    }

    private long getMaxEnergyUsage() {
        if (overclocked && !activeCooling) {
            return getAvailableThreads() * tier.getOverclockedCrafterPowerMultiply() * 100L;
        }
        return getAvailableThreads() * 100L;
    }

    private String getFormatedMaxEnergyUsage() {
        Tooltips.Amount amount = Tooltips.getAmount(getMaxEnergyUsage());
        return amount.digit() + amount.unit();
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(createUI(), this, entityPlayer);
    }
}

