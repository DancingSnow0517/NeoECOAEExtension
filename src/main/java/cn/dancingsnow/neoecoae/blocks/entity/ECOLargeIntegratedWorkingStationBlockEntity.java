package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.networking.energy.IEnergySource;
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
import cn.dancingsnow.neoecoae.api.me.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
    private static final int MAX_INPUT_SLOTS = 9;
    private static final int MAX_PROCESSING_STEPS = 200;
    /** Processing steps available per formed-controller tick; energy still limits actual progress. */
    public static final int PARALLELISM = 1024;
    private static final int MAX_POWER_STORAGE = 16_000_000;
    private static final int MAX_TANK_CAPACITY = 64_000;
    private static final IGuiTexture UI_BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/gui/large_integrated_working_station.png")
    ).setSprite(0, 0, 176, 180);

    private final NEIntegratedWorkingStationControllerCalculator calculator;
    /** Accepted ordinary-path batches. The communication interface has no queue-size limit. */
    private final Deque<PendingBatch> pendingBatches = new ArrayDeque<>();
    @Nullable
    private NEIntegratedWorkingStationCluster cluster;
    @DescSynced
    private boolean formed;

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
                if (!isRemoved() && level == serverLevel) rebuildMultiblock();
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
        if (next != null) {
            transferStoredFluid(inputTank, getInputTank());
            transferStoredFluid(outputTank, getOutputTank());
        }
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

    protected FluidTank getInputTank() {
        if (level != null && level.isClientSide && getBlockState().getValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED)) {
            Direction front = getBlockState().getValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FACING);
            if (level.getBlockEntity(worldPosition.below().relative(front.getOpposite()).relative(front.getCounterClockWise()))
                instanceof ECOLargeIntegratedWorkingStationInputHatchBlockEntity hatch) return hatch.getTank();
        }
        NEIntegratedWorkingStationCluster cluster = getCluster();
        return cluster != null && cluster.getInputHatch() != null
            ? cluster.getInputHatch().getTank() : inputTank;
    }

    protected FluidTank getOutputTank() {
        if (level != null && level.isClientSide && getBlockState().getValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FORMED)) {
            Direction front = getBlockState().getValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.FACING);
            if (level.getBlockEntity(worldPosition.below().relative(front.getOpposite()).relative(front.getClockWise()))
                instanceof ECOLargeIntegratedWorkingStationOutputHatchBlockEntity hatch) return hatch.getTank();
        }
        NEIntegratedWorkingStationCluster cluster = getCluster();
        return cluster != null && cluster.getOutputHatch() != null
            ? cluster.getOutputHatch().getTank() : outputTank;
    }

    public IFluidHandler getFluidCombined() {
        return new IFluidHandler() {
            public int getTanks() { return 2; }
            public FluidStack getFluidInTank(int tank) { return tank == 0 ? getInputTank().getFluid() : getOutputTank().getFluid(); }
            public int getTankCapacity(int tank) { return MAX_TANK_CAPACITY; }
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
        return batch == null ? null : batch.recipe;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        ListTag batches = new ListTag();
        for (PendingBatch batch : pendingBatches) {
            batches.add(batch.save(registries));
        }
        data.put("pendingBatches", batches);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        pendingBatches.clear();
        ListTag batches = data.getList("pendingBatches", Tag.TAG_COMPOUND);
        for (int index = 0; index < batches.size(); index++) {
            PendingBatch batch = PendingBatch.load(batches.getCompound(index), registries);
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
        // The communication interface owns an unbounded queue. A batch is retained as generic AE keys, so the
        // controller's nine physical preview slots and one fluid tank are not a capacity ceiling for CPU jobs.
        return formed && getMainNode().isActive();
    }

    /** Validate the entire input/output contract before taking ownership of CPU inputs. */
    public boolean acceptPattern(IPatternDetails pattern, KeyCounter[] holders, boolean commit) {
        PendingBatch batch = createPendingBatch(pattern, holders, 1L, null);
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
        PendingBatch batch = createPendingBatch(pattern, inputTotal, craftCount, craftingJobId);
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
        cachedTask = null;
        setChanged();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private TickRateModulation tickPendingBatch() {
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch == null) {
            setWorking(false);
            setProcessingTime(0);
            return TickRateModulation.SLOWER;
        }

        setProcessingTime(batch.progress);
        boolean terminal = batch.craftingJobId != null
            && ECOCraftingJobLifecycle.isTerminated(level, batch.craftingJobId);
        if (terminal) {
            boolean cleared = isCounterEmpty(batch.pendingOutput)
                ? recoverCounterToNetwork(batch.inputTotal)
                : deliverBatchOutputs(batch);
            setWorking(false);
            if (cleared) removeFirstBatch(batch);
            setChanged();
            return cleared ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        if (!isCounterEmpty(batch.pendingOutput)) {
            setWorking(false);
            boolean delivered = deliverBatchOutputs(batch);
            if (delivered) removeFirstBatch(batch);
            setChanged();
            return delivered ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        int remainingProgress = MAX_PROCESSING_STEPS - batch.progress;
        int advance = Math.min(getParallelism(), Math.max(0, remainingProgress));
        if (advance <= 0) {
            batch.progress = MAX_PROCESSING_STEPS;
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            setWorking(false);
            if (deliverBatchOutputs(batch)) removeFirstBatch(batch);
            setChanged();
            return TickRateModulation.URGENT;
        }

        double energyPerBatch = (double) batch.energyPerCraft * batch.craftCount;
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
                setWorking(false);
                return TickRateModulation.SLOWER;
            }
            required = energyPerBatch * advance / MAX_PROCESSING_STEPS;
        }

        double extracted = source.extractAEPower(required, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (required > 0.0D && extracted + 0.001D < required) {
            advance = (int) Math.min(
                (long) advance,
                Math.max(0L, (long) Math.floor(extracted * MAX_PROCESSING_STEPS / energyPerBatch))
            );
        }
        if (advance <= 0) {
            setWorking(false);
            return TickRateModulation.SLOWER;
        }

        batch.progress += advance;
        setProcessingTime(batch.progress);
        if (batch.progress >= MAX_PROCESSING_STEPS) {
            batch.progress = MAX_PROCESSING_STEPS;
            // Inputs are already owned by this controller. Once the batch reaches completion, only the output
            // ledger remains recoverable and the original input total must never be replayed.
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            setWorking(false);
            if (deliverBatchOutputs(batch)) removeFirstBatch(batch);
        } else {
            setWorking(true);
        }
        setChanged();
        return TickRateModulation.URGENT;
    }

    private void removeFirstBatch(PendingBatch batch) {
        if (pendingBatches.peekFirst() != batch) return;
        pendingBatches.removeFirst();
        PendingBatch next = pendingBatches.peekFirst();
        setProcessingTime(next == null ? 0 : next.progress);
        cachedTask = null;
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
        return isCounterEmpty(batch.pendingOutput);
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
        @Nullable UUID craftingJobId
    ) {
        if (level == null || pattern == null || craftCount <= 0L || craftCount > PARALLELISM) return null;
        KeyCounter totalInputs = collectInputTotals(pattern, holders);
        if (totalInputs == null || isCounterEmpty(totalInputs)) return null;
        KeyCounter perCraftInputs = divideCounter(totalInputs, craftCount);
        if (perCraftInputs == null) return null;

        List<ItemStack> items = new ArrayList<>();
        FluidStack fluid = FluidStack.EMPTY;
        long totalItems = 0L;
        for (var entry : perCraftInputs) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key == null || amount <= 0L) return null;
            if (key instanceof AEItemKey itemKey) {
                ItemStack one = itemKey.toStack();
                if (one.isEmpty()) return null;
                totalItems = Math.addExact(totalItems, amount);
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

        IntegratedWorkingStationRecipe recipe = level.getRecipeManager().getRecipeFor(
            NERecipeTypes.INTEGRATED_WORKING_STATION.get(),
            new IntegratedWorkingStationRecipe.Input(items, fluid), level
        ).map(RecipeHolder::value).orElse(null);
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
        return new PendingBatch(craftCount, recipe.energy(), totalInputs, outputTotal, craftingJobId, recipe);
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

        root.addChild(staticLabel("block.neoecoae.large_integrated_working_station", 8, 4, 160, 10));
        root.addChild(staticLabel("gui.neoecoae.large_integrated_working_station.input_fluid", 8, 82, 32, 9));
        root.addChild(staticLabel("container.inventory", 42, 82, 92, 9));
        root.addChild(staticLabel("gui.neoecoae.large_integrated_working_station.output_fluid", 140, 82, 28, 9));

        root.addChild(syncedLabel(this::getEnergyText, 42, 27, 92, 10));
        root.addChild(syncedLabel(this::getTaskText, 42, 41, 92, 10));
        root.addChild(syncedLabel(this::getRecipeText, 42, 55, 92, 22));

        UIElement input = new FluidSlot().bind(getInputTank(), 0)
            .slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP).showFluidTooltips(true))
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(8).top(20).width(18).height(60));
        UIElement output = new FluidSlot().bind(getOutputTank(), 0).setAllowClickDrained(false)
            .slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP).showFluidTooltips(true))
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(150).top(20).width(18).height(60));
        root.addChild(input);
        root.addChild(output);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row == 3 ? col : 9 + row * 9 + col;
                int x = 8 + col * 18;
                int y = row == 3 ? 154 : 96 + row * 18;
                root.addChild(new ItemSlot(new net.minecraft.world.inventory.Slot(holder.player.getInventory(), index, 0, 0))
                    .style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
                    .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(18).height(18)));
            }
        }
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
        Label label = new Label();
        label.setText(supplier.get());
        label.bind(DataBindingBuilder.componentS2C(supplier).build());
        label.textStyle(style -> style.fontSize(6).adaptiveWidth(false).textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true).textShadow(false).textColor(0x403E53));
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

    private static final class PendingBatch {
        private final long craftCount;
        private final int energyPerCraft;
        private final KeyCounter inputTotal = new KeyCounter();
        private final KeyCounter outputTotal = new KeyCounter();
        private final KeyCounter pendingOutput = new KeyCounter();
        @Nullable
        private final UUID craftingJobId;
        @Nullable
        private final IntegratedWorkingStationRecipe recipe;
        private int progress;

        private PendingBatch(
            long craftCount,
            int energyPerCraft,
            KeyCounter inputTotal,
            KeyCounter outputTotal,
            @Nullable UUID craftingJobId,
            @Nullable IntegratedWorkingStationRecipe recipe
        ) {
            this.craftCount = craftCount;
            this.energyPerCraft = energyPerCraft;
            this.inputTotal.addAll(inputTotal);
            this.outputTotal.addAll(outputTotal);
            this.craftingJobId = craftingJobId;
            this.recipe = recipe;
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("craftCount", craftCount);
            tag.putInt("energyPerCraft", energyPerCraft);
            tag.putInt("progress", progress);
            if (craftingJobId != null) tag.putUUID("craftingJobId", craftingJobId);
            tag.put("inputTotal", ECOFastPathStacks.writeGenericStacks(registries, counterEntries(inputTotal)));
            tag.put("outputTotal", ECOFastPathStacks.writeGenericStacks(registries, counterEntries(outputTotal)));
            tag.put("pendingOutput", ECOFastPathStacks.writeGenericStacks(registries, counterEntries(pendingOutput)));
            return tag;
        }

        @Nullable
        private static PendingBatch load(CompoundTag tag, HolderLookup.Provider registries) {
            long craftCount = tag.getLong("craftCount");
            int energyPerCraft = tag.getInt("energyPerCraft");
            int progress = tag.getInt("progress");
            if (craftCount <= 0L || craftCount > PARALLELISM || energyPerCraft < 0
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
            PendingBatch batch = new PendingBatch(
                craftCount, energyPerCraft, inputTotal, outputTotal, craftingJobId, null);
            batch.pendingOutput.addAll(pendingOutput);
            batch.progress = progress;
            return batch;
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
