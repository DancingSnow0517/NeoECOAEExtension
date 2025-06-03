package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.GuiTextures;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextBoxWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
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
        return new TickingRequest(5, 10, false);
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
        WidgetGroup root = new WidgetGroup();
        root.setSize(120, 120);

        WidgetGroup switchGroup = new WidgetGroup();
        switchGroup.setSize(80, 36);
        switchGroup.setSelfPosition(8, 8);

        SwitchWidget overclockSwitch = new SwitchWidget(0, 0, 36, 36, ((d, b) -> overclocked = b));
        overclockSwitch.initTemplate();
        overclockSwitch.setBaseTexture(GuiTextures.OVERCLOCK_OFF);
        overclockSwitch.setPressedTexture(GuiTextures.OVERCLOCK_ON);
        overclockSwitch.setPressed(overclocked);
        switchGroup.addWidget(overclockSwitch);

        SwitchWidget cooledSwitch = new SwitchWidget(40, 0, 36, 36, ((d, b) -> activeCooling = b));
        cooledSwitch.initTemplate();
        cooledSwitch.setBaseTexture(GuiTextures.COOLING_OFF);
        cooledSwitch.setPressedTexture(GuiTextures.COOLING_ON);
        cooledSwitch.setPressed(activeCooling);
        switchGroup.addWidget(cooledSwitch);

        root.addWidget(switchGroup);

        WidgetGroup infoGroup = new WidgetGroup();

        infoGroup.setSelfPosition(8, 50);
        TextBoxWidget totalInfoWidget = new TextBoxWidget(0, 0, 40, List.of(
            Component.translatable("gui.neoecoae.crafting.pattern_bus_count", patternBusCount).getString(),
            Component.translatable("gui.neoecoae.crafting.parallel_core_count", parallelCount).getString(),
            Component.translatable("gui.neoecoae.crafting.worker_count", workerCount).getString()
        ));
        totalInfoWidget.setSize(200, 6);
        totalInfoWidget.setFontSize(7);
        totalInfoWidget.setFontColor(0x69F0AE);

        infoGroup.addWidget(totalInfoWidget);

        root.addWidget(infoGroup);

        return root;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(createUI(), this, entityPlayer);
    }
}

