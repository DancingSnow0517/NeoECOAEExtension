package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.appliedenergistics.yoga.YogaAlign;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaFlexDirection;
import org.appliedenergistics.yoga.YogaGutter;
import org.appliedenergistics.yoga.YogaJustify;

import java.util.List;

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
        return threadCount - getAvailableThreads();
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


    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .setPadding(YogaEdge.ALL, 4)
            .setGap(YogaGutter.ALL, 2)
            .setJustifyContent(YogaJustify.CENTER)
        ).addClass("panel_bg");

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
            .layout(layout -> layout.flexDirection(YogaFlexDirection.ROW).alignItems(YogaAlign.CENTER))
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
            .layout(layout -> layout.flexDirection(YogaFlexDirection.ROW).alignItems(YogaAlign.CENTER))
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

        textPanel.layout(layout -> layout.setHeight(160).setWidth(220));

        root.addChild(textPanel);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static void textStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0xadb0c4).textShadow(false);
    }
}

