package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.cluster.IAEMultiBlock;
import appeng.util.ReadableNumberConverter;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.achievement.ECOAdvancementService;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.workstation.LargeWorkstationUI;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationControllerCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import cn.dancingsnow.neoecoae.platform.sync.NEManagedBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Controller for the fixed 3x2x2 large integrated working station. */
public class ECOLargeIntegratedWorkingStationBlockEntity
    extends ECOIntegratedWorkingStationBlockEntity
    implements NEManagedBlockEntity, IGridTickable, IAEMultiBlock<NEIntegratedWorkingStationCluster> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOLargeIntegratedWorkingStationBlockEntity.class);
    private static final int MAX_PROCESSING_STEPS = 200;
    /** Processing steps available per formed-controller tick; energy still limits actual progress. */
    public static final int PARALLELISM = 256;
    private static final int COOLANT_PER_PROCESSING_TICK = LargeWorkstationThermal.COOLANT_PER_TICK;
    private static final int MAX_PENDING_BATCHES = 512;
    private static final long MAX_PENDING_OUTPUT_STACKS = 100_000L;
    private static final long MAX_SAVE_STACKS = 50_000L;
    private static final int MAX_POWER_STORAGE = 16_000_000;

    private final NEIntegratedWorkingStationControllerCalculator calculator;
    private final LargeWorkstationCoolingOutput coolingOutput = new LargeWorkstationCoolingOutput();
    private final LargeWorkstationModuleInventory moduleInventory = new LargeWorkstationModuleInventory(this::moduleChanged);
    /** Accepted ordinary-path batches, bounded by MAX_PENDING_BATCHES backpressure. */
    private final Deque<PendingBatch> pendingBatches = new ArrayDeque<>();
    @Nullable
    private NEIntegratedWorkingStationCluster cluster;
    @DescSynced
    private boolean formed;
    @Persisted
    @DescSynced
    private boolean overclocked;
    @Persisted
    @DescSynced
    private boolean activeCooling;
    private int preheatTargetTier = 2;
    /** Paid work awaiting the next server thermal tick; never persisted or replayed after loading. */
    private double pendingThermalLoad;
    @Persisted
    @DescSynced
    private boolean preheating;
    @DescSynced
    private double temperature;
    @DescSynced
    private boolean overheated;
    @DescSynced
    private int recoveryTicks;
    @DescSynced
    private int coolingProblemId;
    @DescSynced
    private int pauseReasonId;
    @DescSynced
    private int outputBlockedReasonId;
    private double pendingPowerRefund;
    private LargeWorkstationEnergyUsage uiEnergyUsage = new LargeWorkstationEnergyUsage();
    private LargeWorkstationThermalUsage uiThermalUsage = new LargeWorkstationThermalUsage();

    public ECOLargeIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.calculator = new NEIntegratedWorkingStationControllerCalculator(this);
        getMainNode().setFlags(appeng.api.networking.GridFlags.MULTIBLOCK)
            .setIdlePowerUsage(0)
            .addService(IGridTickable.class, this)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);
        setInternalMaxPower(MAX_POWER_STORAGE);
        setPowerSides(getGridConnectableSides(getOrientation()));
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) return EnumSet.allOf(Direction.class);
        // Keep physical links to this structure's members, so the communication interface
        // can still supply the controller's grid services without exposing any cable ports.
        EnumSet<Direction> sides = EnumSet.noneOf(Direction.class);
        if (cluster != null && level != null) {
            for (Direction side : Direction.values()) {
                BlockPos adjacent = worldPosition.relative(side);
                if (level.hasChunkAt(adjacent)
                    && cluster.containsBlockEntity(level.getBlockEntity(adjacent))) {
                    sides.add(side);
                }
            }
        }
        return sides;
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        // Client block state is available before the managed formation field is synchronized.
        boolean formedState = getBlockState().hasProperty(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED)
            && getBlockState().getValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED);
        return formed || formedState ? AECableType.NONE : super.getCableConnectionType(direction);
    }

    @Override
    public @Nullable IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return formed ? null : super.getEnergyStorage(side);
    }

    @Override
    public <T>net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability,Direction side){
        if(formed&&capability==net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY)return net.minecraftforge.common.util.LazyOptional.empty();
        return super.getCapability(capability,side);
    }

    @Override
    protected double getFunnelPowerDemand(double maxRequired) {
        return formed ? 0.0D : super.getFunnelPowerDemand(maxRequired);
    }

    @Override
    protected double funnelPowerIntoStorage(double power, Actionable mode) {
        // Also reject adapters cached before formation, while retaining internal batch refunds.
        return formed ? power : super.funnelPowerIntoStorage(power, mode);
    }

    @Override
    public void onReady() {
        super.onReady();
        setPowerSides(getGridConnectableSides(getOrientation()));
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            calculator.calculateMultiblock(serverLevel, worldPosition);
            serverLevel.getServer().executeIfPossible(() -> {
                if (!isRemoved() && level == serverLevel) {
                    rebuildMultiblock();
                    // A restored batch is loaded before the AE2 grid finishes booting.  Re-forming the
                    // multiblock therefore does not necessarily register a tick immediately; explicitly
                    // wake the node after the deferred rebuild so persisted work can resume on the first
                    // server tick after a reconnect.
                    if (!pendingBatches.isEmpty()) {
                        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
                        requestCommunicationProviderUpdate();
                    }
                }
            });
        }
    }

    public void updateState(boolean updateExposed) {
        if (level == null || level.isClientSide || isRemoved()) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() == NEBlocks.INTEGRATED_WORKING_STATION.get()) {
            BlockState next = state
                .setValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.WORKING, isWorking())
                .setValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED, formed)
                .setValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.MODULE, getInstalledModule());
            if (next != state) {
                level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void setWorking(boolean value) {
        super.setWorking(value);
        updateState(false);
    }

    public boolean isFormed() {
        return formed;
    }

    public void setFormed(boolean value) {
        if (formed != value) {
            boolean wasFormed = formed;
            formed = value;
            if (!wasFormed && value && level instanceof ServerLevel serverLevel) {
                ECOAdvancementService.awardNearest(
                    serverLevel,
                    worldPosition,
                    16.0D,
                    "eco/large_integrated_formed",
                    "formed",
                    null
                );
            }
            PendingBatch batch = pendingBatches.peekFirst();
            setProcessingTime(value && batch != null ? batch.progress : 0);
            cachedTask = null;
            super.onChangeTank();
            updateState(true);
            setPowerSides(value ? EnumSet.noneOf(Direction.class) : EnumSet.allOf(Direction.class));
            if (getMainNode().isReady()) {
                onGridConnectableSidesChanged();
            }
            cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.refresh(this);
            markForUpdate();
        }
    }

    @Override
    public NEIntegratedWorkingStationCluster getCluster() {
        return cluster;
    }

    public void updateCluster(@Nullable NEIntegratedWorkingStationCluster next) {
        cluster = next;
        setFormed(next != null);
    }

    private void transferStoredFluid(FluidTank source, FluidTank destination) {
        if (source == destination || source.getFluid().isEmpty()) return;
        int inserted = destination.fill(source.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (inserted > 0) source.drain(inserted, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public void disconnect(boolean update) {
        if (cluster != null) {
            cluster.destroy();
            cluster = null;
            setFormed(false);
            if (update) updateState(true);
        }
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            calculator.updateMultiblockAfterNeighborUpdate(serverLevel, worldPosition, changedPos);
        }
    }

    public void rebuildMultiblock() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            calculator.calculateMultiblock(serverLevel, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        disconnect(false);
        super.setRemoved();
    }

    public void breakCluster() {
        if (cluster != null) cluster.breakCluster();
    }

    private java.util.Iterator<IGridNode> getMultiblockNodes() {
        if (cluster == null) return java.util.Collections.emptyIterator();
        List<IGridNode> nodes = new ArrayList<>();
        java.util.Iterator<? extends net.minecraft.world.level.block.entity.BlockEntity> iterator = cluster.getBlockEntities();
        while (iterator.hasNext()) {
            net.minecraft.world.level.block.entity.BlockEntity blockEntity = iterator.next();
            IGridNode node = null;
            if (blockEntity instanceof NEBlockEntity<?, ?> member) node = member.getGridNode();
            else if (blockEntity instanceof ECOLargeIntegratedWorkingStationBlockEntity member) node = member.getMainNode().getNode();
            if (node != null) nodes.add(node);
        }
        return nodes.iterator();
    }

    public int getParallelism() {
        return formed ? PARALLELISM : 1;
    }

    @Override
    protected FluidTank getInputTank() {
        return cluster == null || cluster.getInputHatch() == null ? super.getInputTank() : cluster.getInputHatch().tank;
    }

    @Override
    protected FluidTank getOutputTank() {
        return cluster == null || cluster.getOutputHatch() == null ? super.getOutputTank() : cluster.getOutputHatch().tank;
    }

    @Nullable
    private CoolingRecipe getCoolingRecipeForInput() {
        if (level == null) return null;
        FluidStack input = getInputTank().getFluid();
        if (input.isEmpty()) return null;

        CoolingRecipe best = null;
        for (CoolingRecipe holder : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            CoolingRecipe recipe = holder;
            if (recipe.input().ingredient().test(input)
                && (best == null || recipe.maxOverclock() > best.maxOverclock())) {
                best = holder;
            }
        }
        return best;
    }

    private LargeWorkstationOverclock getCurrentBatchProfile() {
        if (!isTemperatureEnabled()) {
            CoolingRecipe holder = getCoolingRecipeForInput();
            return LargeWorkstationOverclock.forCoolant(overclocked, activeCooling,
                holder == null ? 0 : holder.maxOverclock());
        }
        return LargeWorkstationOverclock.forTemperature(overclocked, temperature);
    }

    public boolean isTemperatureEnabled() {
        return NEConfig.largeWorkstationTemperatureEnabled;
    }

    /** Maximum craft count advertised to AE2 for one newly submitted batch. */
    public int getMaxBatchParallelism() {
        return isTemperatureEnabled() && overheated ? 0 : getCurrentBatchProfile().maxParallelism();
    }

    public boolean isOverclocked() {
        return overclocked;
    }

    public boolean isActiveCooling() {
        return activeCooling;
    }

    public void toggleOverclocked() {
        overclocked = !overclocked;
        if (!overclocked) preheating = false;
        settingsChanged();
    }

    public void toggleActiveCooling() {
        activeCooling = !activeCooling;
        if (activeCooling) preheating = false;
        settingsChanged();
    }

    public void togglePreheating() {
        if (!isTemperatureEnabled() || overheated) return;
        preheating = !preheating;
        if (preheating) {
            preheatTargetTier = temperature < 20 ? 2 : temperature < 50 ? 6 : 9;
            overclocked = true;
            activeCooling = false;
        }
        settingsChanged();
    }

    public double getTemperature() { return temperature; }

    public Component getSelectedTierText() {
        if (!isTemperatureEnabled()) return coolantModeText();
        int tier = LargeWorkstationOverclock.forTemperature(true, temperature).coolingTier();
        int displayBand = tier == 2 ? 4 : tier;
        return Component.translatable("gui.neoecoae.large_integrated_working_station.selected_tier", displayBand);
    }

    public Component getPreheatText() {
        if (!isTemperatureEnabled()) return coolantModeText();
        return Component.translatable(preheating
            ? "gui.neoecoae.large_integrated_working_station.preheat.on"
            : "gui.neoecoae.large_integrated_working_station.preheat.off");
    }

    public Component getTemperatureText() {
        if (!isTemperatureEnabled()) return coolantModeText();
        String heat = String.format(java.util.Locale.ROOT, "%.2f", temperature);
        return overheated ? Component.translatable("gui.neoecoae.large_integrated_working_station.recovery",
            heat, String.format(java.util.Locale.ROOT, "%.1f", recoveryTicks / 20.0))
            : Component.translatable("gui.neoecoae.large_integrated_working_station.temperature", heat);
    }

    private void settingsChanged() {
        setChanged();
        markForUpdate();
        requestCommunicationProviderUpdate();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    public LargeWorkstationModule getInstalledModule() {
        return moduleInventory == null ? LargeWorkstationModule.REGULAR
            : LargeWorkstationModule.fromUpgradeCard(moduleInventory.getStackInSlot(0));
    }

    public IItemHandlerModifiable getModuleInventory() { return moduleInventory; }

    private void moduleChanged() {
        if (level == null || level.isClientSide) return;
        refreshModuleAppearance();
        settingsChanged();
    }

    public Component getOverclockSettingsText() {
        return Component.translatable(
            "gui.neoecoae.large_integrated_working_station.settings",
            Component.translatable(overclocked
                ? "gui.neoecoae.large_integrated_working_station.enabled"
                : "gui.neoecoae.large_integrated_working_station.disabled"),
            Component.translatable(activeCooling
                ? "gui.neoecoae.large_integrated_working_station.enabled"
                : "gui.neoecoae.large_integrated_working_station.disabled"));
    }

    public Component getCoolingTierText() {
        CoolingRecipe holder = getCoolingRecipeForInput();
        CoolingRecipe recipe = holder == null ? null : holder;
        LargeWorkstationOverclock profile = recipe == null
            ? null : LargeWorkstationOverclock.forCoolingTier(recipe.maxOverclock());
        String tier = profile == null ? "none" : switch (profile.coolingTier()) {
            case 2 -> "water";
            case 6 -> "sodium";
            case 9 -> "cryotheum";
            default -> "none";
        };
        return Component.translatable(
            "gui.neoecoae.large_integrated_working_station.coolant_tier",
            Component.translatable("gui.neoecoae.large_integrated_working_station.tier." + tier));
    }

    public Component getBatchSettingsText() {
        PendingBatch batch = pendingBatches.peekFirst();
        LargeWorkstationOverclock profile = batch != null && batch.segmented
            ? LargeWorkstationOverclock.NORMAL : batch != null
            ? LargeWorkstationOverclock.fromPersisted(batch.coolingTier, batch.energyMultiplier)
            : getCurrentBatchProfile();
        if (profile == null) profile = LargeWorkstationOverclock.NORMAL;
        return Component.translatable(
            "gui.neoecoae.large_integrated_working_station.parallel_and_energy",
            profile.maxParallelism(), profile.energyMultiplier());
    }

    public Component getPauseReasonText() {
        boolean thermal = isTemperatureEnabled();
        if (thermal && overheated) return Component.translatable(PauseReason.OVERHEATED.translationKey);
        if (thermal && preheating) return getPreheatText();
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch != null && !batch.segmented && batch.progress < MAX_PROCESSING_STEPS && batch.coolingTier > 0) {
            if (!overclocked) return Component.translatable(PauseReason.OVERCLOCK_DISABLED.translationKey);
            LargeWorkstationOverclock locked = LargeWorkstationOverclock.forCoolingTier(batch.coolingTier);
            if (thermal && locked != null && !locked.isTemperatureReady(temperature)) {
                return Component.translatable(PauseReason.TEMPERATURE_LOW.translationKey);
            }
        }
        PauseReason[] reasons = PauseReason.values();
        int status = thermal && activeCooling && coolingProblemId > 0 ? coolingProblemId : pauseReasonId;
        if (!thermal && (status == PauseReason.TEMPERATURE_LOW.ordinal() || status == PauseReason.OVERHEATED.ordinal())) {
            status = PauseReason.NONE.ordinal();
        }
        int index = Math.max(0, Math.min(status, reasons.length - 1));
        PauseReason reason = reasons[index];
        var result = Component.translatable(reason.translationKey);
        if (reason == PauseReason.OUTPUT_BLOCKED || reason == PauseReason.INPUT_RETURN_BLOCKED) {
            OutputBlockedReason[] details = OutputBlockedReason.values();
            int detailIndex = Math.max(0, Math.min(outputBlockedReasonId, details.length - 1));
            OutputBlockedReason detail = details[detailIndex];
            if (detail != OutputBlockedReason.NONE) {
                result.append(Component.translatable(detail.translationKey));
            }
        }
        return result;
    }

    private Component coolantModeText() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.temperature_disabled");
    }

    /** Returns the concrete materials accepted for the batch currently at the head of the work queue. */
    public List<GenericStack> getCurrentBatchInputs() {
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch == null || batch.progress >= MAX_PROCESSING_STEPS) {
            return List.of();
        }
        return counterEntries(batch.inputTotal);
    }

    private void setPauseReason(PauseReason reason) {
        setPauseReason(reason, reason == PauseReason.OUTPUT_BLOCKED || reason == PauseReason.INPUT_RETURN_BLOCKED
            ? OutputBlockedReason.UNKNOWN : OutputBlockedReason.NONE);
    }

    private void setPauseReason(PauseReason reason, OutputBlockedReason detail) {
        boolean changed = false;
        if (pauseReasonId != reason.ordinal()) {
            pauseReasonId = reason.ordinal();
            changed = true;
        }
        if (outputBlockedReasonId != detail.ordinal()) {
            outputBlockedReasonId = detail.ordinal();
            changed = true;
        }
        if (changed) {
            markForUpdate();
        }
    }

    private void setOutputBlockedReason(OutputBlockedReason detail) {
        setPauseReason(PauseReason.OUTPUT_BLOCKED, detail);
    }

    private void setInputReturnBlockedReason() {
        setPauseReason(PauseReason.INPUT_RETURN_BLOCKED, OutputBlockedReason.INPUT_RETURN);
    }

    public IFluidHandler getFluidCombined() {
        return new IFluidHandler() {
            public int getTanks() { return 2; }
            public FluidStack getFluidInTank(int tank) { return tank == 0 ? getInputTank().getFluid() : getOutputTank().getFluid(); }
            public int getTankCapacity(int tank) { return tank == 0 ? getInputTank().getCapacity() : getOutputTank().getCapacity(); }
            public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 ? getInputTank().isFluidValid(stack) : getOutputTank().isFluidValid(stack); }
            public int fill(FluidStack stack, FluidAction action) { return getInputTank().fill(stack, action); }
            public FluidStack drain(FluidStack stack, FluidAction action) { return getOutputTank().drain(stack, action); }
            public FluidStack drain(int amount, FluidAction action) { return getOutputTank().drain(amount, action); }
        };
    }

    public void onChangeTankFromHatch() {
        super.onChangeTank();
        cachedTask = null;
        setChanged();
        markForUpdate();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        requestCommunicationProviderUpdate();
    }

    @Override
    public void onChangeInventory(appeng.api.inventories.InternalInventory inventory, int slot) {
        super.onChangeInventory(inventory, slot);
        cachedTask = null;
        setChanged();
    }

    private void recordUiEnergy(double amount) {
        if (level != null && !level.isClientSide) {
            uiEnergyUsage.record(level.getGameTime(), amount);
        }
    }

    /** The installed card selects the screen and formed-controller theme, including while idle. */
    private void refreshModuleAppearance() {
        if (level == null || level.isClientSide || !formed
            || !(getBlockState().getBlock() instanceof ECOIntegratedWorkingStation)) return;
        LargeWorkstationModule module = getInstalledModule();
        BlockState current = getBlockState();
        if (current.hasProperty(ECOIntegratedWorkingStation.MODULE)
            && current.getValue(ECOIntegratedWorkingStation.MODULE) != module) {
            level.setBlock(worldPosition, current.setValue(ECOIntegratedWorkingStation.MODULE, module), Block.UPDATE_CLIENTS);
        }
    }

    /** Returns one queued row's owned inputs after validating the menu's stable row identity. */
    private void returnTaskInputs(UUID interactionId) {
        if (level == null || level.isClientSide || interactionId == null) return;
        PendingBatch selected = null;
        for (PendingBatch batch : pendingBatches) {
            if (batch.interactionId.equals(interactionId)) {
                selected = batch;
                break;
            }
        }
        if (selected == null || selected.progress >= MAX_PROCESSING_STEPS) return;
        if (!deliverBatchOutputs(selected)) {
            setChanged();
            return;
        }
        if (!recoverCounterToNetwork(selected.inputTotal)) {
            setInputReturnBlockedReason();
            setChanged();
            return;
        }
        if (selected.craftingJobId != null) {
            ECOCraftingJobLifecycle.finish(level, selected.craftingJobId, false);
        }
        notifyPatternAborted(selected.unlockStack);
        pendingBatches.remove(selected);
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(first == null ? 0 : first.progress);
        setWorking(first != null);
        cachedTask = null;
        setPauseReason(PauseReason.NONE);
        refreshModuleAppearance();
        setChanged();
        requestCommunicationProviderUpdate();
    }

    /** Serialized page snapshot consumed by the large-workstation client panel. */
    private CompoundTag getUiSnapshot(int requestedOffset) {
        CompoundTag snapshot = new CompoundTag();
        int total = pendingBatches.size();
        int offset = JdkCompat.clamp(requestedOffset, 0, Math.max(0, total - LargeWorkstationUI.PAGE_SIZE));
        snapshot.putInt("offset", offset);
        int taskOffset = Math.max(0, offset - LargeWorkstationUI.SNAPSHOT_PADDING);
        snapshot.putInt("taskOffset", taskOffset);
        snapshot.putInt("totalTasks", total);
        snapshot.putBoolean("overclocked", overclocked);
        snapshot.putBoolean("activeCooling", activeCooling);
        snapshot.putBoolean("temperatureEnabled", isTemperatureEnabled());
        snapshot.putBoolean("preheating", isTemperatureEnabled() && preheating);
        snapshot.putBoolean("overheated", isTemperatureEnabled() && overheated);
        snapshot.putInt("recoveryTicks", isTemperatureEnabled() ? recoveryTicks : 0);
        snapshot.putBoolean("ignoreUpgradeCards", NEConfig.largeWorkstationIgnoreUpgradeCards);
        snapshot.putInt("currentTier", isTemperatureEnabled()
            ? LargeWorkstationOverclock.forTemperature(true, temperature).coolingTier() : getCurrentBatchProfile().coolingTier());
        snapshot.putInt("coolantTier", getCoolingTierId());
        snapshot.putDouble("temperature", isTemperatureEnabled() ? temperature : 0);
        var thermalRates = isTemperatureEnabled()
            ? uiThermalUsage.rates(level == null ? 0 : level.getGameTime()) : LargeWorkstationThermalUsage.Rates.ZERO;
        snapshot.putDouble("processingHeatPerSecond", thermalRates.processing());
        snapshot.putDouble("preheatPerSecond", thermalRates.preheat());
        snapshot.putDouble("coolingPerSecond", thermalRates.cooling());
        snapshot.putDouble("naturalCoolingPerSecond", thermalRates.naturalCooling());
        snapshot.putDouble("temperatureChangePerSecond", thermalRates.net());
        snapshot.putDouble("energyUsage", uiEnergyUsage.average(level == null ? 0 : level.getGameTime()));
        snapshot.putDouble("energyStored", getAECurrentPower());
        snapshot.putDouble("energyCapacity", getAEMaxPower());
        snapshot.putDouble("energyScale", Math.max(1.0, getAEMaxPower()));
        PendingBatch head = pendingBatches.peekFirst();
        snapshot.putLong("parallelUsed", head == null ? 0 : head.segmented
            ? Math.min(LargeWorkstationOverclock.BASE_PARALLELISM, head.craftCount) : head.craftCount);
        snapshot.putLong("parallelCapacity", getMaxBatchParallelism());
        LargeWorkstationModule module = LargeWorkstationModule.REGULAR;
        if (head != null && head.recipeId != null && level != null) {
            var adapted = LargeWorkstationRecipes.getById(level, head.recipeId);
            if (adapted != null) module = LargeWorkstationModule.fromSourceType(adapted.sourceType());
        }
        snapshot.putString("recipeModule", module.getSerializedName());
        snapshot.putString("module", getInstalledModule().getSerializedName());
        snapshot.putBoolean("moduleInstalled", !moduleInventory.getStackInSlot(0).isEmpty());
        snapshot.putString("pause", pauseTranslationKey());
        snapshot.putString("coolingProblem", PauseReason.values()[JdkCompat.clamp(coolingProblemId, 0, PauseReason.values().length - 1)].translationKey);
        ListTag entries = new ListTag();
        int index = 0;
        for (PendingBatch batch : pendingBatches) {
            if (index++ < taskOffset) continue;
            if (entries.size() >= LargeWorkstationUI.SNAPSHOT_SIZE) break;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", batch.interactionId);
            entry.putInt("progress", batch.progress);
            entry.putBoolean("returnable", batch.progress < MAX_PROCESSING_STEPS);
            entry.putBoolean("current", batch == head);
            entry.putString("status", batch != head ? "gui.neoecoae.workstation.task_queued"
                : batch.progress >= MAX_PROCESSING_STEPS ? "gui.neoecoae.workstation.task_output"
                : pauseTranslationKey());
            if (batch == head && (pauseReasonId == PauseReason.OUTPUT_BLOCKED.ordinal()
                || pauseReasonId == PauseReason.INPUT_RETURN_BLOCKED.ordinal())) {
                entry.putString("detail", OutputBlockedReason.values()[JdkCompat.clamp(outputBlockedReasonId,
                    0, OutputBlockedReason.values().length - 1)].translationKey);
            }
            var source = batch.recipeId == null || level == null ? null : LargeWorkstationRecipes.getById(level, batch.recipeId);
            entry.putString("module", (source == null ? LargeWorkstationModule.REGULAR
                : LargeWorkstationModule.fromSourceType(source.sourceType())).getSerializedName());
            GenericStack output = firstPositive(batch.outputTotal);
            if (output != null) {
                entry.putLong("amount", output.amount());
                if (output.what() instanceof AEItemKey item && level != null) {
                    // The icon is one stack; the batch's exact long count is synchronized separately.
                    entry.put("item", item.toStack(1).save(new CompoundTag()));
                } else if (output.what() instanceof AEFluidKey fluid && level != null) {
                    entry.put("fluid", fluid.toStack(1).writeToNBT(new CompoundTag()));
                }
            }
            entries.add(entry);
        }
        snapshot.put("tasks", entries);
        return snapshot;
    }

    @Nullable
    private static GenericStack firstPositive(KeyCounter counter) {
        for (var entry : counter) if (entry.getLongValue() > 0) {
            return new GenericStack(entry.getKey(), entry.getLongValue());
        }
        return null;
    }

    private String pauseTranslationKey() {
        return getPauseReasonText().getContents() instanceof TranslatableContents text
            ? text.getKey() : PauseReason.NONE.translationKey;
    }

    private int getCoolingTierId() {
        CoolingRecipe holder = getCoolingRecipeForInput();
        return holder == null ? 0 : holder.maxOverclock();
    }

    @Nullable
    public IntegratedWorkingStationRecipe getTask() {
        if (!formed) return super.getTask();
        PendingBatch batch = pendingBatches.peekFirst();
        return batch == null ? null : resolveRecipe(batch);
    }

    @Nullable
    private IntegratedWorkingStationRecipe resolveRecipe(PendingBatch batch) {
        if (batch.recipe != null || level == null) return batch.recipe;

        if (batch.recipeId != null) {
            var adapted = LargeWorkstationRecipes.getById(level, batch.recipeId);
            if (adapted != null) {
                batch.recipe = adapted.display();
                return batch.recipe;
            }
            var holder = level.getRecipeManager().byKey(batch.recipeId).orElse(null);
            if (holder != null && holder instanceof IntegratedWorkingStationRecipe recipe) {
                batch.recipe = recipe;
                return recipe;
            }
        }

        // Saves written before recipeId was persisted still contain the exact owned input ledger.
        // Rebuild one craft's inputs from it so already-running jobs become visible again after updating.
        IntegratedWorkingStationRecipe holder = null;
        KeyCounter perCraftInputs = divideCounter(batch.inputTotal, batch.craftCount);
        if (perCraftInputs != null) {
            IntegratedWorkingStationRecipe.Input input = createRecipeInput(perCraftInputs);
            if (input != null) {
                holder = level.getRecipeManager().getRecipeFor(
                    NERecipeTypes.INTEGRATED_WORKING_STATION.get(), input, level).orElse(null);
            }
        }
        // A completed legacy batch has already exchanged inputTotal for pendingOutput. Its saved primary
        // pattern result plus energy still identifies the recipe well enough to restore the status display.
        if (holder == null && batch.unlockStack != null) {
            for (IntegratedWorkingStationRecipe candidate
                : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
                if (candidate.energy() == batch.energyPerCraft
                    && recipeOutputMatches(candidate, batch.unlockStack)) {
                    holder = candidate;
                    break;
                }
            }
        }
        if (holder == null) return null;
        batch.recipeId = holder.getId();
        batch.recipe = holder;
        if (!level.isClientSide) setChanged();
        return batch.recipe;
    }

    private static boolean recipeOutputMatches(IntegratedWorkingStationRecipe recipe, GenericStack expected) {
        GenericStack output = recipe.hasItemOutput()
            ? GenericStack.fromItemStack(recipe.itemOutput())
            : recipe.hasFluidOutput() ? GenericStack.fromFluidStack(recipe.fluidOutput()) : null;
        return output != null && output.what().equals(expected.what()) && output.amount() == expected.amount();
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.put("workstationModule", moduleInventory.serializeNBT());
        CompoundTag thermal = new CompoundTag();
        LargeWorkstationThermal.save(thermalState(), thermal);
        data.put("largeWorkstationThermal", thermal);
        if (pendingPowerRefund > 0.0D) data.putDouble("pendingPowerRefund", pendingPowerRefund);
        coolingOutput.save(data);
        ListTag batches = new ListTag();
        for (PendingBatch batch : pendingBatches) {
            batches.add(batch.save());
        }
        data.put("pendingBatches", batches);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        uiThermalUsage = new LargeWorkstationThermalUsage();
        moduleInventory.deserializeNBT(data.getCompound("workstationModule"));
        var thermal = LargeWorkstationThermal.load(data.getCompound("largeWorkstationThermal"));
        temperature = thermal.temperature();
        overheated = thermal.overheated();
        recoveryTicks = thermal.recoveryTicks();
        pendingThermalLoad = 0;
        preheatTargetTier = temperature < 20 ? 2 : temperature < 50 ? 6 : 9;
        if (overheated) preheating = false;
        double restoredRefund = data.getDouble("pendingPowerRefund");
        pendingPowerRefund = Double.isFinite(restoredRefund) && restoredRefund > 0.0D ? restoredRefund : 0.0D;
        coolingOutput.load(data);
        pendingBatches.clear();
        ListTag batches = data.getList("pendingBatches", Tag.TAG_COMPOUND);
        setPauseReason(PauseReason.NONE);
        long currentTick = level == null ? 0L : level.getGameTime();
        for (int index = 0; index < batches.size(); index++) {
            PendingBatch batch = PendingBatch.load(batches.getCompound(index), currentTick);
            if (batch != null) {
                pendingBatches.addLast(batch);
            }
        }
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(first == null ? 0 : first.progress);
    }

    public Component getEnergyText() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.energy",
            ReadableNumberConverter.format(getAECurrentPower(), 5),
            ReadableNumberConverter.format(getAEMaxPower(), 5));
    }

    public Component getTaskText() {
        IntegratedWorkingStationRecipe task = getTask();
        return task == null
            ? Component.translatable("gui.neoecoae.large_integrated_working_station.no_task")
            : Component.translatable("gui.neoecoae.large_integrated_working_station.task",
                task.hasItemOutput() ? task.itemOutput().getHoverName() : task.fluidOutput().getDisplayName());
    }

    public Component getRecipeText() {
        IntegratedWorkingStationRecipe task = getTask();
        if (task == null) return Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_empty");
        long itemCount = task.inputItems().stream().mapToLong(it -> it.count()).sum();
        int fluidAmount = task.inputFluid().ingredient().isEmpty() ? 0 : task.inputFluid().amount();
        return Component.translatable("gui.neoecoae.large_integrated_working_station.recipe",
            itemCount, fluidAmount);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return formed ? new TickingRequest(1, 20, false, true) : super.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        retryPendingPowerRefund();
        if (!formed) return super.tickingRequest(node, ticksSinceLastCall);
        refreshModuleAppearance();
        transferStoredFluid(inputTank, getInputTank());
        transferStoredFluid(outputTank, getOutputTank());
        flushOutputs();
        if (!getMainNode().isActive()) {
            setWorking(false);
            return TickRateModulation.SLOWER;
        }
        return tickPendingBatch();
    }

    public boolean canAcceptPattern() {
        return formed
            && (!isTemperatureEnabled() || !overheated)
            && getMainNode().isActive()
            && pendingBatches.size() < MAX_PENDING_BATCHES
            && getPendingOutputStackCount() < MAX_PENDING_OUTPUT_STACKS;
    }

    private long getPendingOutputStackCount() {
        long count = 0L;
        for (PendingBatch batch : pendingBatches) {
            for (var entry : batch.pendingOutput) {
                if (entry.getLongValue() > 0L) {
                    if (count >= MAX_PENDING_OUTPUT_STACKS) return MAX_PENDING_OUTPUT_STACKS;
                    count++;
                }
            }
        }
        return count;
    }

    /** Applies AE2 pattern-provider blocking semantics to the controller's owned, pending input ledger. */
    public boolean containsPendingPatternInput(Set<AEKey> patternInputs) {
        if (patternInputs == null || patternInputs.isEmpty()) {
            return false;
        }
        for (PendingBatch batch : pendingBatches) {
            for (var entry : batch.inputTotal) {
                AEKey key = entry.getKey();
                if (entry.getLongValue() > 0L && key != null && patternInputs.contains(key.dropSecondary())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Validate the entire input/output contract before taking ownership of CPU inputs. */
    public boolean acceptPattern(IPatternDetails pattern, KeyCounter[] holders, boolean commit) {
        PendingBatch batch = createPendingBatch(pattern, holders, 1L, null, pattern == null ? null : pattern.getPrimaryOutput());
        if (batch == null || (commit && !canAcceptPattern())) return false;
        if (commit) {
            enqueueBatch(batch, holders);
        }
        return true;
    }

    /** Ordinary-path batch entry used by the communication interface; one call owns the whole scaled input total. */
    public boolean acceptPatternBatch(
        IPatternDetails pattern,
        KeyCounter[] inputTotal,
        long craftCount,
        @Nullable UUID craftingJobId
    ) {
        if (!canAcceptPattern()) return false;
        PendingBatch batch = createPendingBatch(
            pattern,
            inputTotal,
            craftCount,
            craftingJobId,
            pattern == null ? null : pattern.getPrimaryOutput());
        if (batch == null) return false;
        enqueueBatch(batch, inputTotal);
        return true;
    }

    private void enqueueBatch(PendingBatch batch, @Nullable KeyCounter[] consumedInputs) {
        boolean wasEmpty = pendingBatches.isEmpty();
        pendingBatches.addLast(batch);
        if (consumedInputs != null) {
            for (KeyCounter counter : consumedInputs) {
                if (counter != null) counter.clear();
            }
        }
        if (wasEmpty) setProcessingTime(batch.progress);
        setPauseReason(PauseReason.NONE);
        cachedTask = null;
        setChanged();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private TickRateModulation tickPendingBatch() {
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch == null) {
            setWorking(false);
            setProcessingTime(0);
            setPauseReason(PauseReason.NONE);
            return TickRateModulation.SLOWER;
        }

        // A queued batch represents work even when its progress completed in one tick or its output
        // is temporarily waiting for a destination. Keep the controller screen on until the batch
        // is actually removed from the queue.
        setWorking(true);
        setProcessingTime(batch.progress);
        boolean terminal = batch.craftingJobId != null
            && ECOCraftingJobLifecycle.isTerminated(level, batch.craftingJobId);
        if (terminal) {
            boolean incomplete = batch.progress < MAX_PROCESSING_STEPS;
            boolean cleared = deliverBatchOutputs(batch);
            if (cleared && incomplete && !recoverCounterToNetwork(batch.inputTotal)) {
                setInputReturnBlockedReason();
                cleared = false;
            }
            if (cleared) {
                if (incomplete) notifyPatternAborted(batch.unlockStack);
                removeFirstBatch(batch);
            }
            if (cleared) setPauseReason(PauseReason.NONE);
            setChanged();
            return cleared ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        if (!isCounterEmpty(batch.pendingOutput)) {
            boolean delivered = deliverBatchOutputs(batch);
            if (delivered && batch.progress >= MAX_PROCESSING_STEPS) removeFirstBatch(batch);
            if (delivered) setPauseReason(PauseReason.NONE);
            setChanged();
            return delivered ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        boolean thermal = isTemperatureEnabled();
        if (thermal && overheated) {
            setPauseReason(PauseReason.OVERHEATED);
            return TickRateModulation.SLOWER;
        }
        if (batch.coolingTier > 0 && !batch.segmented) {
            if (!overclocked) {
                setPauseReason(PauseReason.OVERCLOCK_DISABLED);
                return TickRateModulation.SLOWER;
            }
            LargeWorkstationOverclock locked = LargeWorkstationOverclock.forCoolingTier(batch.coolingTier);
            if (thermal && (locked == null || !locked.isTemperatureReady(temperature))) {
                // Keep one ownership ledger and spend its already-paid work on 256-craft portions.
                batch.segmented = true;
                setChanged();
            }
        }

        boolean restoreBatch = batch.segmented && batch.coolingTier > 0
            && getCurrentBatchProfile().coolingTier() >= batch.coolingTier;
        if (!acquireExtraInputs(batch, batch.segmented && !restoreBatch)) {
            setPauseReason(PauseReason.LIGHTNING_MISSING);
            return TickRateModulation.SLOWER;
        }
        if (restoreBatch) {
            // Restore only after owning the full batch's extras, keeping interrupted saves valid.
            batch.segmented = false;
            setChanged();
        }
        refillPendingBatch(batch);

        int remainingProgress = MAX_PROCESSING_STEPS - batch.progress;
        int advance = Math.min(getParallelism(), Math.max(0, remainingProgress));
        if (advance <= 0) {
            batch.progress = MAX_PROCESSING_STEPS;
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            boolean delivered = deliverBatchOutputs(batch);
            if (delivered) removeFirstBatch(batch);
            if (delivered) setPauseReason(PauseReason.NONE);
            setChanged();
            return TickRateModulation.URGENT;
        }

        CoolingTickPlan legacyCooling = null;
        if (!thermal && batch.coolingTier > 0 && !batch.segmented) {
            legacyCooling = prepareLegacyCoolingTick(batch.coolingTier);
            if (legacyCooling == null) return TickRateModulation.SLOWER;
        }

        long completedWork = batch.progress * batch.craftCount + batch.progressRemainder;
        long portionCount = Math.min(LargeWorkstationOverclock.BASE_PARALLELISM, batch.craftCount);
        long portionWork = portionCount * MAX_PROCESSING_STEPS;
        long work = batch.segmented
            ? Math.min(Math.max(0L, portionWork - completedWork), (long) getParallelism() * portionCount)
            : (long) advance * batch.craftCount - batch.progressRemainder;
        double required = (double) batch.energyPerCraft * work / MAX_PROCESSING_STEPS;
        IEnergySource source = this;
        if (required > 0.0D
            && source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.001D < required) {
            IGrid grid = getMainNode().getGrid();
            if (grid != null) {
                IEnergySource network = grid.getEnergyService();
                if (network.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                    > source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG)) {
                    source = network;
                }
            }
        }

        double available = source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (required > 0.0D && available + 0.001D < required) {
            if (batch.segmented) {
                work = Math.min(work, Math.max(0L, (long) Math.floor(available * MAX_PROCESSING_STEPS / batch.energyPerCraft)));
            } else {
                advance = (int) Math.min(
                    (long) advance,
                    Math.max(0L, (long) Math.floor((available * MAX_PROCESSING_STEPS / batch.energyPerCraft
                        + batch.progressRemainder) / batch.craftCount))
                );
                work = (long) advance * batch.craftCount - batch.progressRemainder;
            }
            if (work <= 0 || !batch.segmented && advance <= 0) {
                setPauseReason(PauseReason.POWER_MISSING);
                return TickRateModulation.SLOWER;
            }
            required = (double) batch.energyPerCraft * work / MAX_PROCESSING_STEPS;
        }

        IEnergySource paymentSource = source;
        CoolingTickPlan coolantPayment = legacyCooling;
        LargeWorkstationTickPayment.Result payment = LargeWorkstationTickPayment.commit(
            paymentSource,
            required,
            () -> coolantPayment == null || consumeCoolingTick(coolantPayment),
            amount -> refundProcessingPower(paymentSource, amount)
        );
        if (payment != LargeWorkstationTickPayment.Result.PAID) {
            setPauseReason(payment == LargeWorkstationTickPayment.Result.POWER_MISSING
                ? PauseReason.POWER_MISSING : PauseReason.COOLANT_OUTPUT_BLOCKED);
            return TickRateModulation.SLOWER;
        }

        recordUiEnergy(required);
        preheating = false;

        if (thermal && overclocked) {
            var profile = batch.coolingTier > 0 && !batch.segmented ? LargeWorkstationOverclock.forCoolingTier(batch.coolingTier)
                : LargeWorkstationOverclock.NORMAL;
            double load = (double) work * batch.thermalWorkMultiplier
                / profile.maxParallelism()
                / MAX_PROCESSING_STEPS;
            pendingThermalLoad += load;
        }
        if (batch.segmented) {
            completedWork += work;
            if (completedWork >= portionWork) {
                finishBatchPortion(batch, portionCount, completedWork - portionWork);
            } else {
                batch.progress = (int) (completedWork / batch.craftCount);
                batch.progressRemainder = completedWork % batch.craftCount;
            }
            setProcessingTime(batch.progress);
            setPauseReason(PauseReason.NONE);
            if (!isCounterEmpty(batch.pendingOutput) && deliverBatchOutputs(batch)
                && batch.progress >= MAX_PROCESSING_STEPS) removeFirstBatch(batch);
            setChanged();
            return TickRateModulation.URGENT;
        }
        batch.progress += advance;
        batch.progressRemainder = 0;
        setProcessingTime(batch.progress);
        setPauseReason(PauseReason.NONE);
        if (batch.progress >= MAX_PROCESSING_STEPS) {
            batch.progress = MAX_PROCESSING_STEPS;
            // Inputs are already owned by this controller. Once the batch reaches completion, only the output
            // ledger remains recoverable and the original input total must never be replayed.
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            if (deliverBatchOutputs(batch)) removeFirstBatch(batch);
        } else {
            setWorking(true);
        }
        setChanged();
        return TickRateModulation.URGENT;
    }

    /** Fill available parallelism from consecutive, equivalent owned work without replaying paid progress. */
    private void refillPendingBatch(PendingBatch batch) {
        if (batch.segmented) return;
        LargeWorkstationOverclock profile = getCurrentBatchProfile();
        if (batch.recipeId == null || profile.coolingTier() < batch.coolingTier
            || batch.craftCount >= profile.maxParallelism() || batch.progress >= MAX_PROCESSING_STEPS) return;
        KeyCounter inputsPerCraft = divideCounter(batch.inputTotal, batch.craftCount);
        KeyCounter outputsPerCraft = divideCounter(batch.outputTotal, batch.craftCount);
        if (inputsPerCraft == null || outputsPerCraft == null) return;

        var iterator = pendingBatches.iterator();
        if (!iterator.hasNext() || iterator.next() != batch) return;
        boolean changed = false;
        while (iterator.hasNext() && batch.craftCount < profile.maxParallelism()) {
            PendingBatch next = iterator.next();
            if (next.segmented || next.progress != 0 || next.progressRemainder != 0 || !isCounterEmpty(next.pendingOutput)
                || next.coolingTier > profile.coolingTier() || next.energyPerCraft != batch.energyPerCraft
                || next.energyMultiplier != batch.energyMultiplier || next.thermalWorkMultiplier != batch.thermalWorkMultiplier
                || !Objects.equals(next.recipeId, batch.recipeId) || !Objects.equals(next.craftingJobId, batch.craftingJobId)
                || !Objects.equals(next.unlockStack, batch.unlockStack)) break;

            long taken = Math.min(profile.maxParallelism() - batch.craftCount, next.craftCount);
            KeyCounter ownedInputs;
            KeyCounter ownedOutputs;
            KeyCounter inputPortion;
            KeyCounter outputPortion;
            try {
                KeyCounter expectedInputs = addCountersExact(next.inputTotal, next.missingExtras);
                if (!sameCounter(inputsPerCraft, divideCounter(expectedInputs, next.craftCount))
                    || !sameCounter(outputsPerCraft, divideCounter(next.outputTotal, next.craftCount))) break;
                inputPortion = scaleCounter(inputsPerCraft, taken);
                outputPortion = scaleCounter(outputsPerCraft, taken);
                ownedInputs = addCountersExact(batch.inputTotal, inputPortion);
                ownedOutputs = addCountersExact(batch.outputTotal, outputPortion);
            } catch (ArithmeticException overflow) {
                break;
            }
            // Compatibility inputs must be fully owned before any of this task joins progressed work.
            // A partial withdrawal stays on its original queued task and is saved/refunded there.
            if (!acquireExtraInputs(next)) break;

            long completedWork = batch.progress * batch.craftCount + batch.progressRemainder;
            batch.craftCount += taken;
            batch.coolingTier = profile.coolingTier();
            batch.progress = (int) (completedWork / batch.craftCount);
            batch.progressRemainder = completedWork % batch.craftCount;
            batch.inputTotal.clear();
            batch.inputTotal.addAll(ownedInputs);
            batch.outputTotal.clear();
            batch.outputTotal.addAll(ownedOutputs);
            if (taken == next.craftCount) {
                iterator.remove();
            } else {
                next.craftCount -= taken;
                for (var entry : inputPortion) next.inputTotal.remove(entry.getKey(), entry.getLongValue());
                for (var entry : outputPortion) next.outputTotal.remove(entry.getKey(), entry.getLongValue());
            }
            changed = true;
        }
        if (changed) {
            setProcessingTime(batch.progress);
            cachedTask = null;
            setChanged();
            requestCommunicationProviderUpdate();
        }
    }

    /** Finish just one portion; remaining inputs, outputs and paid craft steps stay on the same task. */
    private void finishBatchPortion(PendingBatch batch, long count, long remainingPaidWork) {
        if (count == batch.craftCount) {
            batch.progress = MAX_PROCESSING_STEPS;
            batch.progressRemainder = 0;
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            return;
        }
        KeyCounter inputs = counterPortion(addCountersExact(batch.inputTotal, batch.missingExtras), count, batch.craftCount);
        KeyCounter outputs = counterPortion(batch.outputTotal, count, batch.craftCount);
        for (var entry : inputs) batch.inputTotal.remove(entry.getKey(), entry.getLongValue());
        for (var entry : outputs) batch.outputTotal.remove(entry.getKey(), entry.getLongValue());
        batch.pendingOutput.addAll(outputs);
        batch.craftCount -= count;
        batch.progress = (int) (remainingPaidWork / batch.craftCount);
        batch.progressRemainder = remainingPaidWork % batch.craftCount;
        cachedTask = null;
    }

    private static long portionAmount(long amount, long count, long totalCount) {
        // Avoid multiplying a possibly long-sized ingredient total by the portion size.
        return amount / totalCount * count + amount % totalCount * count / totalCount;
    }

    private static KeyCounter counterPortion(KeyCounter total, long count, long totalCount) {
        KeyCounter result = new KeyCounter();
        for (var entry : total) {
            long amount = portionAmount(entry.getLongValue(), count, totalCount);
            if (amount > 0) result.add(entry.getKey(), amount);
        }
        return result;
    }

    private static KeyCounter addCountersExact(KeyCounter first, KeyCounter second) {
        KeyCounter result = new KeyCounter();
        result.addAll(first);
        for (var entry : second) {
            Math.addExact(result.get(entry.getKey()), entry.getLongValue());
            result.add(entry.getKey(), entry.getLongValue());
        }
        return result;
    }

    private static boolean sameCounter(KeyCounter expected, @Nullable KeyCounter actual) {
        if (actual == null) return false;
        for (var entry : expected) if (actual.get(entry.getKey()) != entry.getLongValue()) return false;
        for (var entry : actual) if (expected.get(entry.getKey()) != entry.getLongValue()) return false;
        return true;
    }

    private void refundProcessingPower(IEnergySource source, double amount) {
        double remaining = source instanceof IEnergyService network
            ? network.injectPower(amount, Actionable.MODULATE)
            : injectAEPower(amount, Actionable.MODULATE);
        if (remaining > 0.0D && source != this) {
            remaining = injectAEPower(remaining, Actionable.MODULATE);
        } else if (remaining > 0.0D) {
            IGrid grid = getMainNode().getGrid();
            if (grid != null) remaining = grid.getEnergyService().injectPower(remaining, Actionable.MODULATE);
        }
        if (remaining > 0.0D) {
            pendingPowerRefund += remaining;
            setChanged();
        }
    }

    private void retryPendingPowerRefund() {
        if (pendingPowerRefund <= 0.0D) return;
        double remaining = injectAEPower(pendingPowerRefund, Actionable.MODULATE);
        if (remaining > 0.0D) {
            IGrid grid = getMainNode().getGrid();
            if (grid != null) remaining = grid.getEnergyService().injectPower(remaining, Actionable.MODULATE);
        }
        if (remaining != pendingPowerRefund) {
            pendingPowerRefund = remaining;
            setChanged();
        }
    }

    private LargeWorkstationThermal.State thermalState() {
        return new LargeWorkstationThermal.State(temperature, overheated, recoveryTicks);
    }

    private void applyThermalState(LargeWorkstationThermal.State next) {
        int previousLimit = getMaxBatchParallelism();
        boolean changed = temperature != next.temperature() || overheated != next.overheated()
            || recoveryTicks != next.recoveryTicks();
        temperature = next.temperature();
        overheated = next.overheated();
        recoveryTicks = next.recoveryTicks();
        if (overheated) preheating = false;
        if (changed) setChanged();
        if (previousLimit != getMaxBatchParallelism()) {
            requestCommunicationProviderUpdate();
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }
    }

    /** Runs independently of AE2's adaptive processing scheduler, including paused and idle machines. */
    public void tickThermal(Level tickLevel, BlockPos pos, BlockState state) {
        if (tickLevel.isClientSide) return;
        double processingLoad = pendingThermalLoad;
        pendingThermalLoad = 0;
        if (!isTemperatureEnabled()) {
            uiThermalUsage.clear();
            // Freeze saved thermal state while disabled; it applies again when temperature mode is enabled.
            if (preheating || recoveryTicks > 0) {
                preheating = false;
                recoveryTicks = 0;
                if (overheated) temperature = 100;
                setChanged();
                markForUpdate();
            }
            coolingProblemId = 0;
            return;
        }
        if (!formed) {
            settleThermalTick(tickLevel.getGameTime(), 0, 0, 0, false);
            return;
        }
        double preheatFraction = 0;
        if (preheating && !overheated && getMainNode().isActive()) {
            LargeWorkstationOverclock selected = LargeWorkstationOverclock.forCoolingTier(preheatTargetTier);
            double target = selected == null ? 20 : selected.minimumTemperature();
            if (temperature >= target) {
                preheating = false;
                setChanged();
            } else {
                double fraction = Math.min(1, (target - temperature)
                    * LargeWorkstationThermal.TICKS_PER_SECOND / LargeWorkstationThermal.preheatPerSecond(temperature));
                double required = LargeWorkstationThermal.PREHEAT_AE_PER_TICK * fraction;
                IEnergySource source = this;
                IGrid grid = getMainNode().getGrid();
                if (grid != null && grid.getEnergyService().extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                    > source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG)) {
                    source = grid.getEnergyService();
                }
                double paid = source.extractAEPower(required, Actionable.MODULATE, PowerMultiplier.CONFIG);
                recordUiEnergy(paid);
                preheatFraction = JdkCompat.clamp(paid / LargeWorkstationThermal.PREHEAT_AE_PER_TICK, 0, fraction);
            }
        }
        int coolingTier = 0;
        boolean consumed = false;
        coolingProblemId = 0;
        if (activeCooling && !preheating && temperature > 0) {
            CoolingRecipe holder = getCoolingRecipeForInput();
            // Only the real cryotheum fluid can clear the safety latch, even with custom cooling recipes.
            boolean recoveryFluid = new ResourceLocation("neoecoae", "cryotheum_solution")
                .equals(BuiltInRegistries.FLUID.getKey(getInputTank().getFluid().getFluid()));
            if (!overheated || recoveryFluid) {
                CoolingTickPlan plan = prepareCoolingTick(holder);
                if (plan != null && consumeCoolingTick(plan)) {
                    coolingTier = overheated ? 9 : holder.maxOverclock();
                    consumed = true;
                }
            }
        }
        settleThermalTick(tickLevel.getGameTime(), processingLoad, preheatFraction, coolingTier, consumed);
        if (preheating) {
            LargeWorkstationOverclock selected = LargeWorkstationOverclock.forCoolingTier(preheatTargetTier);
            if (selected != null && temperature >= selected.minimumTemperature()) {
                preheating = false;
                setChanged();
            }
        }
        if (tickLevel.getGameTime() % 20 == 0) markForUpdate();
    }

    private void settleThermalTick(long tick, double processingLoad, double preheatFraction, int coolingTier, boolean consumed) {
        var before = thermalState();
        var result = LargeWorkstationThermal.step(before, processingLoad, preheatFraction, coolingTier, consumed);
        uiThermalUsage.record(tick, before, result);
        applyThermalState(result.state());
    }

    @Nullable
    private CoolingTickPlan prepareLegacyCoolingTick(int requiredTier) {
        if (!activeCooling) {
            setPauseReason(PauseReason.COOLING_DISABLED);
            return null;
        }
        CoolingRecipe holder = getCoolingRecipeForInput();
        if (holder != null && holder.maxOverclock() < requiredTier) {
            setPauseReason(PauseReason.COOLANT_TIER_LOW);
            return null;
        }
        CoolingTickPlan plan = prepareCoolingTick(holder);
        if (plan == null) {
            setPauseReason(PauseReason.values()[coolingProblemId]);
        }
        return plan;
    }

    @Nullable
    private CoolingTickPlan prepareCoolingTick(@Nullable CoolingRecipe holder) {
        if (holder == null) {
            coolingProblemId = PauseReason.COOLANT_MISSING.ordinal();
            return null;
        }
        CoolingRecipe recipe = holder;
        LargeWorkstationOverclock current = LargeWorkstationOverclock.forCoolingTier(recipe.maxOverclock());
        if (current == null) {
            coolingProblemId = PauseReason.COOLANT_TIER_LOW.ordinal();
            return null;
        }
        if (getInputTank().getFluidAmount() < COOLANT_PER_PROCESSING_TICK) {
            coolingProblemId = PauseReason.COOLANT_INSUFFICIENT.ordinal();
            return null;
        }

        LargeWorkstationCoolingOutput.Plan output = coolingOutput.plan(
            holder.getId(), recipe.output(), recipe.inputAmount(), recipe.outputAmount(), COOLANT_PER_PROCESSING_TICK);
        if (output == null) {
            coolingProblemId = PauseReason.COOLANT_OUTPUT_BLOCKED.ordinal();
            return null;
        }
        FluidStack byproduct = output.output();
        if (!byproduct.isEmpty()
            && getOutputTank().fill(byproduct, IFluidHandler.FluidAction.SIMULATE) != byproduct.getAmount()) {
            coolingProblemId = PauseReason.COOLANT_OUTPUT_BLOCKED.ordinal();
            return null;
        }
        coolingProblemId = PauseReason.NONE.ordinal();
        return new CoolingTickPlan(cn.dancingsnow.neoecoae.platform.nbt.NEStacks.withAmount(getInputTank().getFluid(), COOLANT_PER_PROCESSING_TICK), output);
    }

    private boolean consumeCoolingTick(CoolingTickPlan plan) {
        FluidStack drained = getInputTank().drain(COOLANT_PER_PROCESSING_TICK, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != COOLANT_PER_PROCESSING_TICK
            || !cn.dancingsnow.neoecoae.platform.nbt.NEStacks.sameFluid(drained, plan.input)) {
            if (!drained.isEmpty()) getInputTank().fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }

        FluidStack byproduct = plan.output.output();
        if (!byproduct.isEmpty()) {
            int filled = getOutputTank().fill(byproduct, IFluidHandler.FluidAction.EXECUTE);
            if (filled != byproduct.getAmount()) {
                if (filled > 0) getOutputTank().drain(filled, IFluidHandler.FluidAction.EXECUTE);
                getInputTank().fill(drained, IFluidHandler.FluidAction.EXECUTE);
                return false;
            }
        }
        coolingOutput.commit(plan.output);
        return true;
    }

    private void removeFirstBatch(PendingBatch batch) {
        if (pendingBatches.peekFirst() != batch) return;
        pendingBatches.removeFirst();
        PendingBatch next = pendingBatches.peekFirst();
        setProcessingTime(next == null ? 0 : next.progress);
        if (next != null) {
            setWorking(true);
        }
        cachedTask = null;
        requestCommunicationProviderUpdate();
    }

    private boolean deliverBatchOutputs(PendingBatch batch) {
        if (isCounterEmpty(batch.pendingOutput)) return true;
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            setOutputBlockedReason(OutputBlockedReason.NETWORK_UNAVAILABLE);
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        Object craftingService = grid.getCraftingService();
        for (GenericStack stack : counterEntries(batch.pendingOutput)) {
            long requested = stack.amount();
            if (batch.craftingJobId != null && !ECOCraftingJobLifecycle.isTerminated(level, batch.craftingJobId)) {
                if (!(craftingService instanceof ECOCraftingOutputRouter ownerRouter)) {
                    setOutputBlockedReason(OutputBlockedReason.CPU_ROUTE);
                    return false;
                }
                long inserted = ownerRouter.neoecoae$insertIntoCpuForJob(
                    batch.craftingJobId, stack.what(), requested, Actionable.MODULATE);
                if (inserted < 0L || inserted > requested) {
                    throw new IllegalStateException(
                        "Invalid large workstation output insertion amount: " + inserted + " for " + requested);
                }
                if (inserted > 0L) batch.pendingOutput.remove(stack.what(), inserted);
                requested -= inserted;
                if (requested == 0L) continue;
                // An external CPU can finish while accepting the final output. Its remaining
                // byproducts and surplus now belong in storage, including the rest of this stack.
                if (!ECOCraftingJobLifecycle.isTerminated(level, batch.craftingJobId)) {
                    setOutputBlockedReason(OutputBlockedReason.CPU_INSERT);
                    return false;
                }
            }
            long inserted = storage.insert(stack.what(), requested, Actionable.MODULATE, IActionSource.ofMachine(this));
            if (inserted < 0L || inserted > requested) {
                throw new IllegalStateException(
                    "Invalid large workstation output insertion amount: " + inserted + " for " + requested);
            }
            if (inserted > 0L) batch.pendingOutput.remove(stack.what(), inserted);
            if (inserted < requested) {
                setOutputBlockedReason(OutputBlockedReason.NETWORK_INSERT);
                return false;
            }
        }
        boolean complete = isCounterEmpty(batch.pendingOutput);
        if (complete && batch.progress >= MAX_PROCESSING_STEPS) notifyPatternResult(batch.unlockStack);
        return complete;
    }

    private void notifyPatternResult(@Nullable GenericStack result) {
        if (result == null || cluster == null) return;
        if (cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            communication.getWorkstationProvider().onPatternResult(result);
        }
    }

    private void notifyPatternAborted(@Nullable GenericStack expectedResult) {
        if (expectedResult == null || cluster == null) return;
        if (cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            communication.getWorkstationProvider().onPatternAborted(expectedResult);
        }
    }

    private void requestCommunicationProviderUpdate() {
        if (cluster != null && cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            ICraftingProvider.requestUpdate(communication.getMainNode());
        }
    }

    /** Partial extractions remain owned and persisted, so retries and cancellation cannot double-charge. */
    private boolean acquireExtraInputs(PendingBatch batch) {
        return acquireExtraInputs(batch, batch.segmented);
    }

    private boolean acquireExtraInputs(PendingBatch batch, boolean segmented) {
        if (isCounterEmpty(batch.missingExtras)) return true;
        IGrid grid = getMainNode().getGrid();
        if (grid == null) return false;
        if (segmented) {
            boolean complete = true;
            MEStorage storage = grid.getStorageService().getInventory();
            for (GenericStack extra : counterEntries(batch.missingExtras)) {
                long owned = batch.inputTotal.get(extra.what());
                long target = portionAmount(Math.addExact(owned, extra.amount()),
                    Math.min(LargeWorkstationOverclock.BASE_PARALLELISM, batch.craftCount), batch.craftCount);
                long requested = Math.max(0L, target - owned);
                if (requested == 0) continue;
                long extracted = storage.extract(extra.what(), requested, Actionable.MODULATE, IActionSource.ofMachine(this));
                if (extracted < 0 || extracted > requested) throw new IllegalStateException("Invalid extra input extraction");
                if (extracted > 0) {
                    batch.inputTotal.add(extra.what(), extracted);
                    batch.missingExtras.remove(extra.what(), extracted);
                    setChanged();
                }
                if (extracted < requested) complete = false;
            }
            return complete;
        }
        return LargeWorkstationExtraInputs.acquire(batch.missingExtras, batch.inputTotal,
            grid.getStorageService().getInventory(), IActionSource.ofMachine(this), this::setChanged);
    }

    private boolean recoverCounterToNetwork(KeyCounter counter) {
        if (isCounterEmpty(counter)) return true;
        IGrid grid = getMainNode().getGrid();
        if (grid == null) return false;
        return LargeWorkstationExtraInputs.returnOwned(
            counter, grid.getStorageService().getInventory(), IActionSource.ofMachine(this), this::setChanged);
    }

    @Nullable
    private PendingBatch createPendingBatch(
        IPatternDetails pattern,
        @Nullable KeyCounter[] holders,
        long craftCount,
        @Nullable UUID craftingJobId,
        @Nullable GenericStack unlockStack
    ) {
        if (!formed || level == null || pattern == null) return null;
        LargeWorkstationOverclock profile = getCurrentBatchProfile();
        if (!profile.acceptsCraftCount(craftCount)) return null;
        // Inputs delivered by AE2 are retained in the workstation's unbounded ledger while
        // the batch runs. Recipe lookup is performed against one encoded pattern's actual input set,
        // while the owned ledger keeps the complete scaled amount for cancellation/refund.
        KeyCounter totalInputs = collectInputTotals(pattern, holders);
        if (totalInputs == null || isCounterEmpty(totalInputs)) return null;
        KeyCounter perCraftInputs = divideCounter(totalInputs, craftCount);
        if (perCraftInputs == null) return null;

        KeyCounter outputPerCraft = collectPatternOutputs(pattern);
        if (outputPerCraft == null) return null;
        var match = LargeWorkstationRecipes.findScaled(level, perCraftInputs, outputPerCraft, getInstalledModule());
        if (match == null) return null;
        var adapted = match.recipe();

        KeyCounter remainderTotal = collectRemainderTotals(pattern, holders);
        if (remainderTotal == null) return null;
        long energyPerCraft;
        KeyCounter outputTotal;
        KeyCounter missingExtras = new KeyCounter();
        try {
            // craftCount counts encoded pattern executions. Each multiplied pattern must pay for
            // all of its base recipe operations, including extras absent from the encoded inputs.
            energyPerCraft = Math.multiplyExact(adapted.energy(), match.multiplier());
            outputTotal = scaleCounter(outputPerCraft, craftCount);
            for (var extra : adapted.extraInputs()) {
                if (perCraftInputs.get(extra.what()) == 0) {
                    long perPattern = Math.multiplyExact(extra.amount(), match.multiplier());
                    missingExtras.add(extra.what(), Math.multiplyExact(perPattern, craftCount));
                }
            }
        } catch (ArithmeticException overflow) {
            return null;
        }
        outputTotal.addAll(remainderTotal);
        if (isCounterEmpty(outputTotal)) return null;
        PendingBatch batch = new PendingBatch(
            craftCount, energyPerCraft, profile.coolingTier(), profile.energyMultiplier(), totalInputs, outputTotal,
            craftingJobId, adapted.id(), adapted.display(), unlockStack, level.getGameTime());
        batch.thermalWorkMultiplier = match.multiplier();
        // Compatibility-only inputs (for example AE2LT lightning) are not represented by
        // ordinary AE2 pattern holders.  Keep them in the pending ledger so the actual
        // adapted recipe still charges them from the ME network before processing.
        batch.missingExtras.addAll(missingExtras);
        return batch;
    }

    @Nullable
    private static IntegratedWorkingStationRecipe.Input createRecipeInput(KeyCounter perCraftInputs) {
        List<ItemStack> items = new ArrayList<>();
        FluidStack fluid = FluidStack.EMPTY;
        for (var entry : perCraftInputs) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key == null || amount <= 0L) return null;
            if (key instanceof AEItemKey itemKey) {
                ItemStack one = itemKey.toStack();
                if (one.isEmpty()) return null;
                int maxStackSize = Math.max(1, Math.min(64, one.getMaxStackSize()));
                while (amount > 0L) {
                    int count = (int) Math.min(amount, maxStackSize);
                    items.add(itemKey.toStack(count));
                    amount -= count;
                }
            } else if (key instanceof AEFluidKey fluidKey) {
                if (!fluid.isEmpty() || amount > Integer.MAX_VALUE) return null;
                fluid = fluidKey.toStack((int) amount);
            } else {
                return null;
            }
        }
        return new IntegratedWorkingStationRecipe.Input(items, fluid);
    }

    @Nullable
    private static KeyCounter collectInputTotals(IPatternDetails pattern, @Nullable KeyCounter[] holders) {
        KeyCounter result = new KeyCounter();
        if (holders != null) {
            for (KeyCounter holder : holders) {
                if (holder != null) result.addAll(holder);
            }
            return result;
        }
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null) return null;
        for (IPatternDetails.IInput input : inputs) {
            if (input == null || input.getMultiplier() <= 0L) return null;
            GenericStack[] possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0 || possible[0] == null
                || possible[0].what() == null || possible[0].amount() <= 0L) return null;
            result.add(possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier()));
        }
        return result;
    }

    @Nullable
    private static KeyCounter divideCounter(KeyCounter total, long divisor) {
        KeyCounter result = new KeyCounter();
        for (var entry : total) {
            long amount = entry.getLongValue();
            if (amount <= 0L || amount % divisor != 0L) return null;
            result.add(entry.getKey(), amount / divisor);
        }
        return result;
    }

    @Nullable
    private static KeyCounter collectPatternOutputs(IPatternDetails pattern) {
        KeyCounter result = new KeyCounter();
        List<GenericStack> outputs = java.util.Arrays.asList(pattern.getOutputs());
        if (outputs == null || outputs.isEmpty()) return null;
        for (GenericStack output : outputs) {
            if (output == null || output.what() == null || output.amount() <= 0L) return null;
            result.add(output.what(), output.amount());
        }
        return result;
    }

    @Nullable
    private static KeyCounter collectRemainderTotals(IPatternDetails pattern, @Nullable KeyCounter[] holders) {
        KeyCounter result = new KeyCounter();
        if (holders == null) return result;
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null || inputs.length != holders.length) return null;
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            KeyCounter holder = holders[slot];
            if (input == null || holder == null) return null;
            for (var entry : holder) {
                AEKey remainder = input.getRemainingKey(entry.getKey());
                if (remainder == null) continue;
                long templateAmount = templateAmount(input, entry.getKey());
                if (templateAmount <= 0L || entry.getLongValue() % templateAmount != 0L) return null;
                result.add(remainder, entry.getLongValue() / templateAmount);
            }
        }
        return result;
    }

    private static long templateAmount(IPatternDetails.IInput input, AEKey actual) {
        long exact = 0L;
        GenericStack[] possible = input.getPossibleInputs();
        if (possible == null) return 0L;
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.what() != null && candidate.what().equals(actual)) {
                if (exact != 0L && exact != candidate.amount()) return 0L;
                exact = candidate.amount();
            }
        }
        if (exact != 0L) return exact;
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.what() != null
                && candidate.what().getPrimaryKey().equals(actual.getPrimaryKey())) {
                if (exact != 0L && exact != candidate.amount()) return 0L;
                exact = candidate.amount();
            }
        }
        return exact;
    }

    private static KeyCounter scaleCounter(KeyCounter source, long multiplier) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) {
            result.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
        }
        return result;
    }

    private static boolean isCounterEmpty(KeyCounter counter) {
        for (var entry : counter) {
            if (entry.getLongValue() != 0L) return false;
        }
        return true;
    }

    private static List<GenericStack> counterEntries(KeyCounter counter) {
        List<GenericStack> result = new ArrayList<>();
        for (var entry : counter) {
            if (entry.getLongValue() > 0L) result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }

    private void flushOutputs() {
        if (!getMainNode().isActive()) return;
        getMainNode().ifPresent((grid, node) -> {
            MEStorage storage = grid.getStorageService().getInventory();
            GenericStack item = GenericStack.fromItemStack(outputInv.getStackInSlot(0));
            if (item != null) {
                long inserted = storage.insert(item.what(), item.amount(), Actionable.MODULATE, IActionSource.ofMachine(this));
                if (inserted > 0) outputInv.extractItem(0, (int) inserted, false);
            }
        });
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        // The open-menu state can arrive before the client world's block update.
        BlockState state = holder != null && holder.blockState != null ? holder.blockState : getBlockState();
        if (!state.getValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED)) {
            return super.createUI(holder);
        }
        return LargeWorkstationUI.create(new LargeWorkstationUI.Config(
            getFluidCombined(), this::getUiSnapshot, () -> level.registryAccess(), this::returnTaskInputs,
            this::toggleOverclocked, this::toggleActiveCooling,
            this::togglePreheating, moduleInventory), holder.player);
    }

    /** Returns inputs still owned by incomplete batches to the ME network. */
    public void returnStoredInputs() {
        if (level == null || level.isClientSide) return;
        var batches = new ArrayList<>(pendingBatches);
        for (PendingBatch batch : batches) {
            if (batch.progress >= MAX_PROCESSING_STEPS) continue;
            if (!deliverBatchOutputs(batch)) {
                setChanged();
                return;
            }
            if (!recoverCounterToNetwork(batch.inputTotal)) {
                setInputReturnBlockedReason();
                setChanged();
                return;
            }
            if (batch.craftingJobId != null) {
                ECOCraftingJobLifecycle.finish(level, batch.craftingJobId, false);
            }
            notifyPatternAborted(batch.unlockStack);
            pendingBatches.remove(batch);
        }
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(first == null ? 0 : first.progress);
        setWorking(first != null);
        cachedTask = null;
        setPauseReason(PauseReason.NONE);
        setChanged();
        requestCommunicationProviderUpdate();
    }

    private void autoBuild(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
            || player.level() != level || player.distanceToSqr(worldPosition.getCenter()) > 64
            || isRemoved() || formed || !player.mayBuild() || !level.mayInteract(player, worldPosition)) return;
        var plan = MultiBlockPlacementService.preview(level, worldPosition, getBlockState(),
            NEMultiBlocks.LARGE_INTEGRATED_WORKING_STATION, 1, false);
        if (!plan.getConflictPositions().isEmpty()) {
            player.displayClientMessage(Component.translatable("gui.neoecoae.multiblock.status.conflicts_detected"), true);
            return;
        }
        if (!player.isCreative() && !MultiBlockPlacementService.hasRequiredItems(player, plan.getRequiredItems())) {
            player.displayClientMessage(Component.translatable("gui.neoecoae.multiblock.status.not_enough_items"), true);
            return;
        }
        if (!MultiBlockPlacementService.buildInstant(serverLevel, plan, serverPlayer)) return;
        rebuildMultiblock();
        if (formed) BlockUIMenuType.openUI(serverPlayer, worldPosition);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack card = moduleInventory.getStackInSlot(0);
        if (!card.isEmpty()) drops.add(card.copy());
        for (PendingBatch batch : pendingBatches) {
            if (batch.craftingJobId != null) {
                ECOCraftingJobLifecycle.finish(level, batch.craftingJobId, false);
            }
            // A segmented task can own finished products and unprocessed inputs at the same time.
            for (KeyCounter owned : List.of(batch.inputTotal, batch.pendingOutput)) {
                for (GenericStack stack : counterEntries(owned)) {
                    stack.what().addDrops(stack.amount(), drops, level, pos);
                }
            }
        }
        pendingBatches.clear();
    }

    @Override
    public void clearContent() {
        for (PendingBatch batch : pendingBatches) {
            if (batch.craftingJobId != null) {
                ECOCraftingJobLifecycle.finish(level, batch.craftingJobId, false);
            }
        }
        pendingBatches.clear();
        super.clearContent();
        moduleInventory.setStackInSlot(0, ItemStack.EMPTY);
    }

    private enum PauseReason {
        NONE("gui.neoecoae.large_integrated_working_station.pause.none"),
        OVERCLOCK_DISABLED("gui.neoecoae.large_integrated_working_station.pause.overclock_disabled"),
        COOLING_DISABLED("gui.neoecoae.large_integrated_working_station.pause.cooling_disabled"),
        COOLANT_MISSING("gui.neoecoae.large_integrated_working_station.pause.coolant_missing"),
        COOLANT_TIER_LOW("gui.neoecoae.large_integrated_working_station.pause.coolant_tier_low"),
        COOLANT_INSUFFICIENT("gui.neoecoae.large_integrated_working_station.pause.coolant_insufficient"),
        COOLANT_OUTPUT_BLOCKED("gui.neoecoae.large_integrated_working_station.pause.coolant_output_blocked"),
        POWER_MISSING("gui.neoecoae.large_integrated_working_station.pause.power_missing"),
        OUTPUT_BLOCKED("gui.neoecoae.large_integrated_working_station.pause.output_blocked"),
        INPUT_RETURN_BLOCKED("gui.neoecoae.large_integrated_working_station.pause.input_return_blocked"),
        LIGHTNING_MISSING("gui.neoecoae.large_integrated_working_station.pause.lightning_missing"),
        TEMPERATURE_LOW("gui.neoecoae.large_integrated_working_station.pause.temperature_low"),
        OVERHEATED("gui.neoecoae.large_integrated_working_station.pause.overheated");

        private final String translationKey;

        PauseReason(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private enum OutputBlockedReason {
        NONE(null),
        UNKNOWN("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.unknown"),
        CPU_ROUTE("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.cpu_route"),
        CPU_INSERT("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.cpu_insert"),
        NETWORK_UNAVAILABLE("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.network_unavailable"),
        NETWORK_INSERT("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.network_insert"),
        INPUT_RETURN("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.input_return");

        private final String translationKey;

        OutputBlockedReason(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record CoolingTickPlan(FluidStack input, LargeWorkstationCoolingOutput.Plan output) {
    }

    private static final class PendingBatch {
        /** Menu actions identify an owned batch, never its changing viewport index. */
        private final UUID interactionId = UUID.randomUUID();
        private long craftCount;
        private final long energyPerCraft;
        private int coolingTier;
        private final int energyMultiplier;
        private final KeyCounter inputTotal = new KeyCounter();
        private final KeyCounter outputTotal = new KeyCounter();
        private final KeyCounter pendingOutput = new KeyCounter();
        private final KeyCounter missingExtras = new KeyCounter();
        private final long createdTick;
        @Nullable
        private final UUID craftingJobId;
        @Nullable
        private ResourceLocation recipeId;
        @Nullable
        private IntegratedWorkingStationRecipe recipe;
        @Nullable
        private final GenericStack unlockStack;
        private int progress;
        /** Already-paid craft steps left over when a larger batch cannot express them as whole progress steps. */
        private long progressRemainder;
        private long thermalWorkMultiplier = 1;
        /** Temperature fallback uses 256-craft portions until this batch's overclock conditions recover. */
        private boolean segmented;

        private PendingBatch(
            long craftCount,
            long energyPerCraft,
            int coolingTier,
            int energyMultiplier,
            KeyCounter inputTotal,
            KeyCounter outputTotal,
            @Nullable UUID craftingJobId,
            @Nullable ResourceLocation recipeId,
            @Nullable IntegratedWorkingStationRecipe recipe,
            @Nullable GenericStack unlockStack,
            long createdTick
        ) {
            this.craftCount = craftCount;
            this.energyPerCraft = energyPerCraft;
            this.coolingTier = coolingTier;
            this.energyMultiplier = energyMultiplier;
            this.inputTotal.addAll(inputTotal);
            this.outputTotal.addAll(outputTotal);
            this.craftingJobId = craftingJobId;
            this.recipeId = recipeId;
            this.recipe = recipe;
            this.unlockStack = unlockStack == null ? null : new GenericStack(unlockStack.what(), unlockStack.amount());
            this.createdTick = createdTick;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("craftCount", craftCount);
            tag.putLong("energyPerCraft", energyPerCraft);
            tag.putInt("coolingTier", coolingTier);
            tag.putInt("energyMultiplier", energyMultiplier);
            tag.putInt("progress", progress);
            if (progressRemainder > 0) tag.putLong("progressRemainder", progressRemainder);
            if (segmented) tag.putBoolean("segmented", true);
            tag.putLong("thermalWorkMultiplier", thermalWorkMultiplier);
            tag.putLong("createdTick", createdTick);
            if (craftingJobId != null) tag.putUUID("craftingJobId", craftingJobId);
            if (recipeId != null) tag.putString("recipeId", recipeId.toString());
            long saveStackCount = countStackEntries(inputTotal)
                + countStackEntries(outputTotal)
                + countStackEntries(pendingOutput)
                + countStackEntries(missingExtras)
                + (unlockStack == null ? 0L : 1L);
            if (saveStackCount > MAX_SAVE_STACKS) {
                LOGGER.error("Skipping oversized large workstation batch save at {}: {} stack entries",
                    createdTick, saveStackCount);
                tag.putBoolean("pendingStateTooLarge", true);
                return tag;
            }
            tag.put("inputTotal", ECOFastPathStacks.writeGenericStacks(ECOFastPathStacks.copyCounter(inputTotal)));
            tag.put("missingExtras", ECOFastPathStacks.writeGenericStacks(ECOFastPathStacks.copyCounter(missingExtras)));
            tag.put("outputTotal", ECOFastPathStacks.writeGenericStacks(ECOFastPathStacks.copyCounter(outputTotal)));
            tag.put("pendingOutput", ECOFastPathStacks.writeGenericStacks(ECOFastPathStacks.copyCounter(pendingOutput)));
            if (unlockStack != null) tag.put("unlockStack", GenericStack.writeTag(unlockStack));
            return tag;
        }

        @Nullable
        private static PendingBatch load(CompoundTag tag, long currentTick) {
            if (tag.getBoolean("pendingStateTooLarge")) {
                LOGGER.error("Discarding a large workstation batch whose persisted state exceeded the save limit");
                return null;
            }
            long craftCount = tag.getLong("craftCount");
            long energyPerCraft = tag.getLong("energyPerCraft");
            int coolingTier = tag.contains("coolingTier", Tag.TAG_INT) ? tag.getInt("coolingTier") : 0;
            int energyMultiplier = tag.contains("energyMultiplier", Tag.TAG_INT) ? tag.getInt("energyMultiplier") : 1;
            int progress = tag.getInt("progress");
            long progressRemainder = tag.getLong("progressRemainder");
            boolean segmented = tag.getBoolean("segmented");
            LargeWorkstationOverclock profile = LargeWorkstationOverclock.fromPersisted(coolingTier, energyMultiplier);
            // Grandfather already-owned normal batches accepted under the old 1,024 limit.
            long savedLimit = coolingTier == 0 ? 1_024 : profile == null ? 0 : profile.maxParallelism();
            if (profile == null || craftCount <= 0 || craftCount > savedLimit || energyPerCraft < 0
                || progress < 0 || progress > MAX_PROCESSING_STEPS
                || progressRemainder < 0 || progressRemainder >= craftCount
                || progress == MAX_PROCESSING_STEPS && progressRemainder != 0) {
                return null;
            }
            KeyCounter inputTotal = readCounter(tag, "inputTotal", false);
            KeyCounter outputTotal = readCounter(tag, "outputTotal", true);
            KeyCounter pendingOutput = readCounter(tag, "pendingOutput", false);
            KeyCounter missingExtras = readCounter(tag, "missingExtras", false);
            if (inputTotal == null || outputTotal == null || pendingOutput == null || missingExtras == null
                || !segmented && (progress > 0 || progressRemainder > 0) && !missingExtras.isEmpty()
                || progress < MAX_PROCESSING_STEPS && inputTotal.isEmpty()
                || progress >= MAX_PROCESSING_STEPS && pendingOutput.isEmpty()) {
                return null;
            }
            UUID craftingJobId = tag.hasUUID("craftingJobId") ? tag.getUUID("craftingJobId") : null;
            ResourceLocation recipeId = tag.contains("recipeId", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("recipeId")) : null;
            GenericStack unlockStack = tag.contains("unlockStack", Tag.TAG_COMPOUND)
                ? GenericStack.readTag(tag.getCompound("unlockStack")) : null;
            long createdTick = tag.contains("createdTick", Tag.TAG_LONG)
                ? tag.getLong("createdTick") : currentTick;
            PendingBatch batch = new PendingBatch(
                craftCount, energyPerCraft, profile.coolingTier(), profile.energyMultiplier(), inputTotal, outputTotal,
                craftingJobId, recipeId, null, unlockStack, createdTick);
            batch.pendingOutput.addAll(pendingOutput);
            batch.missingExtras.addAll(missingExtras);
            batch.progress = progress;
            batch.progressRemainder = progressRemainder;
            batch.segmented = segmented;
            batch.thermalWorkMultiplier = tag.contains("thermalWorkMultiplier", Tag.TAG_LONG)
                ? Math.max(1, tag.getLong("thermalWorkMultiplier")) : 1;
            return batch;
        }

        private static long countStackEntries(KeyCounter counter) {
            long count = 0L;
            for (var entry : counter) {
                if (entry.getLongValue() > 0L) count++;
            }
            return count;
        }

        @Nullable
        private static KeyCounter readCounter(
            CompoundTag tag,
            String name,
            boolean requireNonEmpty
        ) {
            ListTag entries = tag.getList(name, Tag.TAG_COMPOUND);
            if (entries.size() > ECOBatchCraftingHelper.MAX_BATCH_STACK_ENTRIES
                || requireNonEmpty && entries.isEmpty()) return null;
            try {
                KeyCounter result = new KeyCounter();
                for (int i = 0; i < entries.size(); i++) {
                    GenericStack stack = GenericStack.readTag(entries.getCompound(i));
                    if (stack == null || stack.amount() <= 0
                        || stack.amount() > ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT) return null;
                    long total = result.get(stack.what());
                    if (total > ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT - stack.amount()) return null;
                    result.add(stack.what(), stack.amount());
                }
                return result;
            } catch (RuntimeException failure) {
                return null;
            }
        }
    }
}
