package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.me.cluster.IAEMultiBlock;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationControllerCalculator;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Deque;
import java.util.UUID;

/** Controller for the fixed 3x2x2 large integrated working station. */
public class ECOLargeIntegratedWorkingStationBlockEntity
    extends ECOIntegratedWorkingStationBlockEntity
    implements ISyncPersistRPCBlockEntity, IGridTickable, IAEMultiBlock<NEIntegratedWorkingStationCluster> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOLargeIntegratedWorkingStationBlockEntity.class);
    private static final int MAX_INPUT_SLOTS = 9;
    private static final int MAX_PROCESSING_STEPS = 200;
    /** Processing steps available per formed-controller tick; energy still limits actual progress. */
    public static final int PARALLELISM = 1024;
    private static final int COOLANT_PER_PROCESSING_TICK = 100;
    private static final int MAX_PENDING_BATCHES = 512;
    private static final long MAX_PENDING_OUTPUT_STACKS = 100_000L;
    private static final long MAX_SAVE_STACKS = 50_000L;
    private static final long MAX_BATCH_AGE = 72_000L;
    private static final int MAX_POWER_STORAGE = 16_000_000;
    private static final IGuiTexture UI_BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/gui/large_integrated_working_station.png")
    ).setSprite(0, 0, 176, 180);

    private final NEIntegratedWorkingStationControllerCalculator calculator;
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
    @DescSynced
    private int pauseReasonId;
    private double pendingPowerRefund;

    public ECOLargeIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.calculator = new NEIntegratedWorkingStationControllerCalculator(this);
        getMainNode().setFlags(appeng.api.networking.GridFlags.MULTIBLOCK)
            .setIdlePowerUsage(0)
            .addService(IGridTickable.class, this)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);
        setInternalMaxPower(MAX_POWER_STORAGE);
        setPowerSides(EnumSet.allOf(Direction.class));
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void onReady() {
        super.onReady();
        setPowerSides(EnumSet.allOf(Direction.class));
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
                .setValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED, formed);
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
            formed = value;
            PendingBatch batch = pendingBatches.peekFirst();
            setProcessingTime(value && batch != null ? batch.progress : 0);
            cachedTask = null;
            super.onChangeTank();
            updateState(true);
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
        for (RecipeHolder<CoolingRecipe> holder : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            CoolingRecipe recipe = holder.value();
            if (recipe.input().ingredient().test(input)
                && (best == null || recipe.maxOverclock() > best.maxOverclock())) {
                best = recipe;
            }
        }
        return best;
    }

    private LargeWorkstationOverclock getCurrentLiquidProfile() {
        CoolingRecipe recipe = getCoolingRecipeForInput();
        LargeWorkstationOverclock profile = recipe == null
            ? null : LargeWorkstationOverclock.forCoolingTier(recipe.maxOverclock());
        return profile == null ? LargeWorkstationOverclock.NORMAL : profile;
    }

    private LargeWorkstationOverclock getCurrentBatchProfile() {
        return LargeWorkstationOverclock.forCurrentSettings(
            overclocked, activeCooling, getCurrentLiquidProfile().coolingTier());
    }

    /** Maximum craft count advertised to AE2 for one newly submitted batch. */
    public int getMaxBatchParallelism() {
        return getCurrentBatchProfile().maxParallelism();
    }

    public boolean isOverclocked() {
        return overclocked;
    }

    public boolean isActiveCooling() {
        return activeCooling;
    }

    public void toggleOverclocked() {
        overclocked = !overclocked;
        settingsChanged();
    }

    public void toggleActiveCooling() {
        activeCooling = !activeCooling;
        settingsChanged();
    }

    private void settingsChanged() {
        setChanged();
        markForUpdate();
        requestCommunicationProviderUpdate();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
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
        CoolingRecipe recipe = getCoolingRecipeForInput();
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
        LargeWorkstationOverclock profile = batch != null
            ? LargeWorkstationOverclock.fromPersisted(batch.coolingTier, batch.energyMultiplier)
            : getCurrentBatchProfile();
        if (profile == null) profile = LargeWorkstationOverclock.NORMAL;
        return Component.translatable(
            "gui.neoecoae.large_integrated_working_station.parallel_and_energy",
            profile.maxParallelism(), profile.energyMultiplier());
    }

    public Component getPauseReasonText() {
        PauseReason[] reasons = PauseReason.values();
        int index = Math.max(0, Math.min(pauseReasonId, reasons.length - 1));
        return Component.translatable(reasons[index].translationKey);
    }

    private void setPauseReason(PauseReason reason) {
        if (pauseReasonId != reason.ordinal()) {
            pauseReasonId = reason.ordinal();
            markForUpdate();
        }
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
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        super.onChangeInventory(inventory, slot);
        cachedTask = null;
        setChanged();
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
            var holder = level.getRecipeManager().byKey(batch.recipeId).orElse(null);
            if (holder != null && holder.value() instanceof IntegratedWorkingStationRecipe recipe) {
                batch.recipe = recipe;
                return recipe;
            }
        }

        // Saves written before recipeId was persisted still contain the exact owned input ledger.
        // Rebuild one craft's inputs from it so already-running jobs become visible again after updating.
        RecipeHolder<IntegratedWorkingStationRecipe> holder = null;
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
            for (RecipeHolder<IntegratedWorkingStationRecipe> candidate
                : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
                if (candidate.value().energy() == batch.energyPerCraft
                    && recipeOutputMatches(candidate.value(), batch.unlockStack)) {
                    holder = candidate;
                    break;
                }
            }
        }
        if (holder == null) return null;
        batch.recipeId = holder.id();
        batch.recipe = holder.value();
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
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (pendingPowerRefund > 0.0D) data.putDouble("pendingPowerRefund", pendingPowerRefund);
        ListTag batches = new ListTag();
        for (PendingBatch batch : pendingBatches) {
            batches.add(batch.save(registries));
        }
        data.put("pendingBatches", batches);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        double restoredRefund = data.getDouble("pendingPowerRefund");
        pendingPowerRefund = Double.isFinite(restoredRefund) && restoredRefund > 0.0D ? restoredRefund : 0.0D;
        pendingBatches.clear();
        ListTag batches = data.getList("pendingBatches", Tag.TAG_COMPOUND);
        setPauseReason(PauseReason.NONE);
        long currentTick = level == null ? 0L : level.getGameTime();
        for (int index = 0; index < batches.size(); index++) {
            PendingBatch batch = PendingBatch.load(batches.getCompound(index), registries, currentTick);
            if (batch != null) {
                pendingBatches.addLast(batch);
            }
        }
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(first == null ? 0 : first.progress);
    }

    public Component getEnergyText() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.energy",
            Math.round(getAECurrentPower()), Math.round(getAEMaxPower()));
    }

    public Component getTaskText() {
        IntegratedWorkingStationRecipe task = getTask();
        return task == null
            ? Component.translatable("gui.neoecoae.large_integrated_working_station.no_task")
            : Component.translatable("gui.neoecoae.large_integrated_working_station.task",
                task.hasItemOutput() ? task.itemOutput().getHoverName() : task.fluidOutput().getHoverName());
    }

    public Component getRecipeText() {
        IntegratedWorkingStationRecipe task = getTask();
        if (task == null) return Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_empty");
        int itemCount = task.inputItems().stream().mapToInt(it -> it.count()).sum();
        int fluidAmount = task.inputFluid().ingredient().isEmpty() ? 0 : task.inputFluid().amount();
        return Component.translatable("gui.neoecoae.large_integrated_working_station.recipe",
            itemCount, fluidAmount);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return formed ? new TickingRequest(1, 20, false) : super.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        retryPendingPowerRefund();
        if (!formed) return super.tickingRequest(node, ticksSinceLastCall);
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
        if (isBatchExpired(batch)) {
            boolean cleared = expireBatch(batch);
            setPauseReason(cleared ? PauseReason.NONE : PauseReason.OUTPUT_BLOCKED);
            setChanged();
            return cleared ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        boolean terminal = batch.craftingJobId != null
            && ECOCraftingJobLifecycle.isTerminated(level, batch.craftingJobId);
        if (terminal) {
            boolean abandonedWithoutOutput = isCounterEmpty(batch.pendingOutput);
            boolean cleared = abandonedWithoutOutput
                ? recoverCounterToNetwork(batch.inputTotal)
                : deliverBatchOutputs(batch);
            if (cleared) {
                if (abandonedWithoutOutput) notifyPatternAborted(batch.unlockStack);
                removeFirstBatch(batch);
            }
            setPauseReason(cleared ? PauseReason.NONE : PauseReason.OUTPUT_BLOCKED);
            setChanged();
            return cleared ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        if (!isCounterEmpty(batch.pendingOutput)) {
            boolean delivered = deliverBatchOutputs(batch);
            if (delivered) removeFirstBatch(batch);
            setPauseReason(delivered ? PauseReason.NONE : PauseReason.OUTPUT_BLOCKED);
            setChanged();
            return delivered ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        int remainingProgress = MAX_PROCESSING_STEPS - batch.progress;
        int advance = Math.min(getParallelism(), Math.max(0, remainingProgress));
        if (advance <= 0) {
            batch.progress = MAX_PROCESSING_STEPS;
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            boolean delivered = deliverBatchOutputs(batch);
            if (delivered) removeFirstBatch(batch);
            setPauseReason(delivered ? PauseReason.NONE : PauseReason.OUTPUT_BLOCKED);
            setChanged();
            return TickRateModulation.URGENT;
        }

        CoolingTickPlan coolingPlan = null;
        if (batch.coolingTier > 0) {
            PauseReason coolingPause = getCoolingPauseReason(batch);
            if (coolingPause != PauseReason.NONE) {
                setPauseReason(coolingPause);
                return TickRateModulation.SLOWER;
            }
            CoolingRecipe coolingRecipe = getCoolingRecipeForInput();
            if (coolingRecipe == null) {
                setPauseReason(PauseReason.COOLANT_MISSING);
                return TickRateModulation.SLOWER;
            }
            coolingPlan = new CoolingTickPlan(getCoolingOutputPerTick(coolingRecipe));
        }

        double energyPerBatch = (double) batch.energyPerCraft * batch.energyMultiplier * batch.craftCount;
        double required = energyPerBatch * advance / MAX_PROCESSING_STEPS;
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
            advance = (int) Math.min(
                (long) advance,
                Math.max(0L, (long) Math.floor(available * MAX_PROCESSING_STEPS / energyPerBatch))
            );
            if (advance <= 0) {
                setPauseReason(PauseReason.POWER_MISSING);
                return TickRateModulation.SLOWER;
            }
            required = energyPerBatch * advance / MAX_PROCESSING_STEPS;
        }

        IEnergySource paymentSource = source;
        CoolingTickPlan tickCoolingPlan = coolingPlan;
        LargeWorkstationTickPayment.Result payment = LargeWorkstationTickPayment.commit(
            paymentSource,
            required,
            () -> tickCoolingPlan == null || consumeCoolingTick(tickCoolingPlan),
            amount -> refundProcessingPower(paymentSource, amount)
        );
        if (payment != LargeWorkstationTickPayment.Result.PAID) {
            setPauseReason(payment == LargeWorkstationTickPayment.Result.POWER_MISSING
                ? PauseReason.POWER_MISSING : PauseReason.COOLANT_OUTPUT_BLOCKED);
            return TickRateModulation.SLOWER;
        }

        batch.progress += advance;
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

    private PauseReason getCoolingPauseReason(PendingBatch batch) {
        if (!overclocked) return PauseReason.OVERCLOCK_DISABLED;
        if (!activeCooling) return PauseReason.COOLING_DISABLED;

        CoolingRecipe recipe = getCoolingRecipeForInput();
        if (recipe == null) return PauseReason.COOLANT_MISSING;
        LargeWorkstationOverclock current = LargeWorkstationOverclock.forCoolingTier(recipe.maxOverclock());
        if (current == null || current.coolingTier() < batch.coolingTier) {
            return PauseReason.COOLANT_TIER_LOW;
        }
        if (getInputTank().getFluidAmount() < COOLANT_PER_PROCESSING_TICK) {
            return PauseReason.COOLANT_INSUFFICIENT;
        }

        FluidStack byproduct = getCoolingOutputPerTick(recipe);
        if (byproduct == null) return PauseReason.COOLANT_OUTPUT_BLOCKED;
        if (byproduct.isEmpty()) return PauseReason.NONE;

        FluidStack stored = getOutputTank().getFluid();
        if (!stored.isEmpty() && !recipe.output().is(stored.getFluid())) {
            return PauseReason.COOLANT_OUTPUT_BLOCKED;
        }
        return getOutputTank().fill(byproduct, IFluidHandler.FluidAction.SIMULATE) == byproduct.getAmount()
            ? PauseReason.NONE : PauseReason.COOLANT_OUTPUT_BLOCKED;
    }

    @Nullable
    private static FluidStack getCoolingOutputPerTick(CoolingRecipe recipe) {
        FluidStack output = recipe.output();
        if (output.isEmpty()) return FluidStack.EMPTY;
        int recipeInput = recipe.inputAmount();
        if (recipeInput <= 0 || recipe.outputAmount() <= 0) return null;
        long amount = (long) recipe.outputAmount() * COOLANT_PER_PROCESSING_TICK / recipeInput;
        if (amount <= 0L) return FluidStack.EMPTY;
        if (amount > Integer.MAX_VALUE) return null;
        return output.copyWithAmount((int) amount);
    }

    private boolean consumeCoolingTick(CoolingTickPlan plan) {
        FluidStack drained = getInputTank().drain(COOLANT_PER_PROCESSING_TICK, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != COOLANT_PER_PROCESSING_TICK) {
            if (!drained.isEmpty()) getInputTank().fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }

        if (!plan.output.isEmpty()) {
            int filled = getOutputTank().fill(plan.output, IFluidHandler.FluidAction.EXECUTE);
            if (filled != plan.output.getAmount()) {
                if (filled > 0) getOutputTank().drain(filled, IFluidHandler.FluidAction.EXECUTE);
                getInputTank().fill(drained, IFluidHandler.FluidAction.EXECUTE);
                return false;
            }
        }
        return true;
    }

    private boolean isBatchExpired(PendingBatch batch) {
        return level != null && level.getGameTime() - batch.createdTick > MAX_BATCH_AGE;
    }

    private boolean expireBatch(PendingBatch batch) {
        long age = level == null ? MAX_BATCH_AGE : Math.max(0L, level.getGameTime() - batch.createdTick);
        if (!batch.expirationLogged) {
            LOGGER.warn("Expiring large workstation batch at {} after {} ticks, job={}",
                getBlockPos(), age, batch.craftingJobId);
            batch.expirationLogged = true;
        }
        if (batch.craftingJobId != null && !ECOCraftingJobLifecycle.isTerminated(level, batch.craftingJobId)) {
            ECOCraftingJobLifecycle.finish(level, batch.craftingJobId, false);
        }

        boolean abandonedWithoutOutput = isCounterEmpty(batch.pendingOutput);
        boolean cleared = abandonedWithoutOutput
            ? recoverCounterToNetwork(batch.inputTotal)
            : deliverBatchOutputs(batch);
        if (cleared) {
            if (abandonedWithoutOutput) notifyPatternAborted(batch.unlockStack);
            removeFirstBatch(batch);
        }
        return cleared;
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
        if (grid == null) return false;

        MEStorage storage = grid.getStorageService().getInventory();
        Object craftingService = grid.getCraftingService();
        boolean terminal = batch.craftingJobId != null
            && ECOCraftingJobLifecycle.isTerminated(level, batch.craftingJobId);
        ECOCraftingOutputRouter ownerRouter = null;
        if (batch.craftingJobId != null && !terminal) {
            if (!(craftingService instanceof ECOCraftingOutputRouter router)) return false;
            ownerRouter = router;
        }

        for (GenericStack stack : counterEntries(batch.pendingOutput)) {
            long requested = stack.amount();
            long inserted = ownerRouter != null
                ? ownerRouter.neoecoae$insertIntoCpuForJob(
                    batch.craftingJobId, stack.what(), requested, Actionable.MODULATE)
                : storage.insert(stack.what(), requested, Actionable.MODULATE, IActionSource.ofMachine(this));
            if (inserted < 0L || inserted > requested) {
                throw new IllegalStateException(
                    "Invalid large workstation output insertion amount: " + inserted + " for " + requested);
            }
            if (inserted > 0L) batch.pendingOutput.remove(stack.what(), inserted);
            if (inserted < requested) return false;
        }
        boolean complete = isCounterEmpty(batch.pendingOutput);
        if (complete) notifyPatternResult(batch.unlockStack);
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

    private boolean recoverCounterToNetwork(KeyCounter counter) {
        if (isCounterEmpty(counter)) return true;
        IGrid grid = getMainNode().getGrid();
        if (grid == null) return false;
        MEStorage storage = grid.getStorageService().getInventory();
        for (GenericStack stack : counterEntries(counter)) {
            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, IActionSource.ofMachine(this));
            if (inserted < 0L || inserted > stack.amount()) {
                throw new IllegalStateException(
                    "Invalid large workstation recovery insertion amount: " + inserted + " for " + stack.amount());
            }
            if (inserted > 0L) counter.remove(stack.what(), inserted);
            if (inserted < stack.amount()) return false;
        }
        return isCounterEmpty(counter);
    }

    @Nullable
    private PendingBatch createPendingBatch(
        IPatternDetails pattern,
        @Nullable KeyCounter[] holders,
        long craftCount,
        @Nullable UUID craftingJobId,
        @Nullable GenericStack unlockStack
    ) {
        LargeWorkstationOverclock profile = getCurrentBatchProfile();
        if (level == null || pattern == null || !profile.acceptsCraftCount(craftCount)) return null;
        KeyCounter totalInputs = collectInputTotals(pattern, holders);
        if (totalInputs == null || isCounterEmpty(totalInputs)) return null;
        KeyCounter perCraftInputs = divideCounter(totalInputs, craftCount);
        if (perCraftInputs == null) return null;

        IntegratedWorkingStationRecipe.Input recipeInput = createRecipeInput(perCraftInputs);
        if (recipeInput == null) return null;
        FluidStack fluid = recipeInput.fluid() == null ? FluidStack.EMPTY : recipeInput.fluid();
        long totalItems = 0L;
        for (var entry : perCraftInputs) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key == null || amount <= 0L) return null;
            if (key instanceof AEItemKey) {
                totalItems = Math.addExact(totalItems, amount);
            } else if (!(key instanceof AEFluidKey)) {
                return null;
            }
        }

        RecipeHolder<IntegratedWorkingStationRecipe> recipeHolder = level.getRecipeManager().getRecipeFor(
            NERecipeTypes.INTEGRATED_WORKING_STATION.get(), recipeInput, level
        ).orElse(null);
        IntegratedWorkingStationRecipe recipe = recipeHolder == null ? null : recipeHolder.value();
        if (recipe == null
            || totalItems != recipe.inputItems().stream().mapToLong(it -> it.count()).sum()
            || fluid.getAmount() != (recipe.inputFluid().ingredient().isEmpty() ? 0 : recipe.inputFluid().amount())
            || !recipe.inputFluid().ingredient().isEmpty() && !recipe.inputFluid().test(fluid)) {
            return null;
        }

        KeyCounter outputPerCraft = collectPatternOutputs(pattern);
        if (outputPerCraft == null) return null;
        KeyCounter outputCheck = copyCounter(outputPerCraft);
        if (recipe.hasItemOutput()) {
            GenericStack output = GenericStack.fromItemStack(recipe.itemOutput());
            if (output == null) return null;
            outputCheck.add(output.what(), -output.amount());
        }
        if (recipe.hasFluidOutput()) {
            GenericStack output = GenericStack.fromFluidStack(recipe.fluidOutput());
            if (output == null) return null;
            outputCheck.add(output.what(), -output.amount());
        }
        for (var entry : outputCheck) {
            if (entry.getLongValue() != 0L) return null;
        }

        KeyCounter remainderTotal = collectRemainderTotals(pattern, holders);
        if (remainderTotal == null) return null;
        KeyCounter outputTotal = scaleCounter(outputPerCraft, craftCount);
        outputTotal.addAll(remainderTotal);
        if (isCounterEmpty(outputTotal)) return null;
        return new PendingBatch(
            craftCount, recipe.energy(), profile.coolingTier(), profile.energyMultiplier(), totalInputs, outputTotal,
            craftingJobId, recipeHolder.id(), recipe, unlockStack, level.getGameTime());
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
        List<GenericStack> outputs = pattern.getOutputs();
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

    private static KeyCounter copyCounter(KeyCounter source) {
        KeyCounter result = new KeyCounter();
        result.addAll(source);
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
            GenericStack fluid = GenericStack.fromFluidStack(getOutputTank().getFluid());
            if (fluid != null) {
                long inserted = storage.insert(fluid.what(), fluid.amount(), Actionable.MODULATE, IActionSource.ofMachine(this));
                if (inserted > 0) getOutputTank().drain((int) inserted, IFluidHandler.FluidAction.EXECUTE);
            }
        });
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (!getBlockState().getValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED)) {
            return super.createUI(holder);
        }
        UIElement root = new UIElement().layout(layout -> layout.width(176).height(180))
            .style(style -> style.backgroundTexture(UI_BACKGROUND));

        TextElement title = staticLabel("block.neoecoae.large_integrated_working_station", 8, 4, 106, 10);
        title.textStyle(style -> style.fontSize(8));
        root.addChild(title);
        root.addChild(staticLabel("container.inventory", 42, 94, 92, 9));

        root.addChild(syncedLabel(this::getEnergyText, 42, 20, 128, 9));
        root.addChild(syncedLabel(this::getTaskText, 42, 30, 128, 9));
        root.addChild(syncedLabel(this::getRecipeText, 42, 40, 128, 18, TextWrap.NONE));
        root.addChild(syncedLabel(this::getOverclockSettingsText, 42, 59, 128, 8));
        root.addChild(syncedLabel(this::getCoolingTierText, 42, 67, 128, 8));
        root.addChild(syncedLabel(this::getBatchSettingsText, 42, 75, 128, 8));
        root.addChild(syncedLabel(this::getPauseReasonText, 42, 83, 128, 8));

        Button overclockButton = new Button();
        overclockButton.setText(Component.translatable("gui.neoecoae.large_integrated_working_station.overclock_button"));
        overclockButton.setOnServerClick(event -> toggleOverclocked());
        overclockButton.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE).left(118).top(3).width(25).height(13));
        root.addChild(overclockButton);

        Button coolingButton = new Button();
        coolingButton.setText(Component.translatable("gui.neoecoae.large_integrated_working_station.cooling_button"));
        coolingButton.setOnServerClick(event -> toggleActiveCooling());
        coolingButton.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE).left(146).top(3).width(25).height(13));
        root.addChild(coolingButton);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row == 3 ? col : 9 + row * 9 + col;
                int x = 7 + col * 18;
                int y = row == 3 ? 162 : 104 + row * 18;
                root.addChild(new ItemSlot(new net.minecraft.world.inventory.Slot(holder.player.getInventory(), index, 0, 0))
                    .style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
                    .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(18).height(18)));
            }
        }
        root.addChild(HostSideButtonBar.left(
            GuideButton.create(holder.player, "neoecoae:neoecoae_intro/large_integrated_working_station.md")
        ));
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(
            cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets.ECO))), holder.player);
    }

    private static TextElement staticLabel(String translationKey, int left, int top, int width, int height) {
        TextElement label = new TextElement()
            .setText(translationKey, true)
            .textStyle(style -> style
                .fontSize(6)
                .adaptiveWidth(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true)
                .textShadow(false)
                .textColor(0x403E53));
        label.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(left)
            .top(top)
            .width(width)
            .height(height));
        return label;
    }

    private static Label syncedLabel(java.util.function.Supplier<Component> supplier, int left, int top, int width, int height) {
        return syncedLabel(supplier, left, top, width, height, TextWrap.HOVER_ROLL);
    }

    private static Label syncedLabel(
        java.util.function.Supplier<Component> supplier,
        int left,
        int top,
        int width,
        int height,
        TextWrap textWrap
    ) {
        Label label = new Label();
        label.setText(supplier.get());
        label.bind(DataBindingBuilder.componentS2C(supplier).build());
        label.textStyle(style -> style.fontSize(6).adaptiveWidth(false).textWrap(textWrap).adaptiveHeight(true).textShadow(false).textColor(0x403E53));
        label.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top).width(width).height(height));
        return label;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (PendingBatch batch : pendingBatches) {
            if (batch.craftingJobId != null) {
                ECOCraftingJobLifecycle.finish(level, batch.craftingJobId, false);
            }
            KeyCounter owned = isCounterEmpty(batch.pendingOutput) ? batch.inputTotal : batch.pendingOutput;
            for (GenericStack stack : counterEntries(owned)) {
                stack.what().addDrops(stack.amount(), drops, level, pos);
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
        OUTPUT_BLOCKED("gui.neoecoae.large_integrated_working_station.pause.output_blocked");

        private final String translationKey;

        PauseReason(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record CoolingTickPlan(FluidStack output) {
    }

    private static final class PendingBatch {
        private final long craftCount;
        private final int energyPerCraft;
        private final int coolingTier;
        private final int energyMultiplier;
        private final KeyCounter inputTotal = new KeyCounter();
        private final KeyCounter outputTotal = new KeyCounter();
        private final KeyCounter pendingOutput = new KeyCounter();
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
        private boolean expirationLogged;

        private PendingBatch(
            long craftCount,
            int energyPerCraft,
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

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("craftCount", craftCount);
            tag.putInt("energyPerCraft", energyPerCraft);
            tag.putInt("coolingTier", coolingTier);
            tag.putInt("energyMultiplier", energyMultiplier);
            tag.putInt("progress", progress);
            tag.putLong("createdTick", createdTick);
            if (craftingJobId != null) tag.putUUID("craftingJobId", craftingJobId);
            if (recipeId != null) tag.putString("recipeId", recipeId.toString());
            long saveStackCount = countStackEntries(inputTotal)
                + countStackEntries(outputTotal)
                + countStackEntries(pendingOutput)
                + (unlockStack == null ? 0L : 1L);
            if (saveStackCount > MAX_SAVE_STACKS) {
                LOGGER.error("Skipping oversized large workstation batch save at {}: {} stack entries",
                    createdTick, saveStackCount);
                tag.putBoolean("pendingStateTooLarge", true);
                return tag;
            }
            tag.put("inputTotal", ECOFastPathStacks.writeGenericStacks(registries, counterEntries(inputTotal)));
            tag.put("outputTotal", ECOFastPathStacks.writeGenericStacks(registries, counterEntries(outputTotal)));
            tag.put("pendingOutput", ECOFastPathStacks.writeGenericStacks(registries, counterEntries(pendingOutput)));
            if (unlockStack != null) tag.put("unlockStack", GenericStack.writeTag(registries, unlockStack));
            return tag;
        }

        @Nullable
        private static PendingBatch load(CompoundTag tag, HolderLookup.Provider registries, long currentTick) {
            if (tag.getBoolean("pendingStateTooLarge")) {
                LOGGER.error("Discarding a large workstation batch whose persisted state exceeded the save limit");
                return null;
            }
            long craftCount = tag.getLong("craftCount");
            int energyPerCraft = tag.getInt("energyPerCraft");
            int coolingTier = tag.contains("coolingTier", Tag.TAG_INT) ? tag.getInt("coolingTier") : 0;
            int energyMultiplier = tag.contains("energyMultiplier", Tag.TAG_INT) ? tag.getInt("energyMultiplier") : 1;
            int progress = tag.getInt("progress");
            LargeWorkstationOverclock profile = LargeWorkstationOverclock.fromPersisted(coolingTier, energyMultiplier);
            if (profile == null || !profile.acceptsCraftCount(craftCount) || energyPerCraft < 0
                || progress < 0 || progress > MAX_PROCESSING_STEPS) {
                return null;
            }
            KeyCounter inputTotal = readCounter(registries, tag, "inputTotal", false);
            KeyCounter outputTotal = readCounter(registries, tag, "outputTotal", true);
            KeyCounter pendingOutput = readCounter(registries, tag, "pendingOutput", false);
            if (inputTotal == null || outputTotal == null || pendingOutput == null
                || progress < MAX_PROCESSING_STEPS && isCounterEmpty(inputTotal)
                || progress >= MAX_PROCESSING_STEPS && isCounterEmpty(pendingOutput)) {
                return null;
            }
            UUID craftingJobId = tag.hasUUID("craftingJobId") ? tag.getUUID("craftingJobId") : null;
            ResourceLocation recipeId = tag.contains("recipeId", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("recipeId")) : null;
            GenericStack unlockStack = tag.contains("unlockStack", Tag.TAG_COMPOUND)
                ? GenericStack.readTag(registries, tag.getCompound("unlockStack")) : null;
            long createdTick = tag.contains("createdTick", Tag.TAG_LONG)
                ? tag.getLong("createdTick") : currentTick;
            PendingBatch batch = new PendingBatch(
                craftCount, energyPerCraft, profile.coolingTier(), profile.energyMultiplier(), inputTotal, outputTotal,
                craftingJobId, recipeId, null, unlockStack, createdTick);
            batch.pendingOutput.addAll(pendingOutput);
            batch.progress = progress;
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
            HolderLookup.Provider registries,
            CompoundTag tag,
            String name,
            boolean requireNonEmpty
        ) {
            var decoded = ECOFastPathStacks.readValidatedBatchInputStacks(
                registries,
                tag.getList(name, Tag.TAG_COMPOUND),
                requireNonEmpty,
                ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT
            );
            if (decoded.isEmpty()) return null;
            KeyCounter result = new KeyCounter();
            for (GenericStack stack : decoded.get()) result.add(stack.what(), stack.amount());
            return result;
        }
    }
}
