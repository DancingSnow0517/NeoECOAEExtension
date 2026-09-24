package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.orientation.BlockOrientation;
import appeng.me.cluster.IAEMultiBlock;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationControllerCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Formed controller using the legacy workstation inventory and persistent processing state. */
public class ECOLargeIntegratedWorkingStationBlockEntity extends ECOIntegratedWorkingStationBlockEntity
        implements IAEMultiBlock<NEIntegratedWorkingStationCluster> {
    private static final int PROCESSING_STEPS = 200;
    private static final int MAX_PENDING_BATCHES = 512;
    private static final int MAX_BATCH_ENTRIES = 4096;
    private static final long MAX_BATCH_AMOUNT = 1_000_000_000_000L;
    private final NEIntegratedWorkingStationControllerCalculator calculator;
    private final Deque<PendingBatch> pendingBatches = new ArrayDeque<>();
    private final List<CompoundTag> quarantinedBatches = new ArrayList<>();
    @Nullable private NEIntegratedWorkingStationCluster cluster;
    private boolean formed;
    private boolean overclocked;
    private boolean activeCooling;
    private double pendingPowerRefund;

    public ECOLargeIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        calculator = new NEIntegratedWorkingStationControllerCalculator(this);
        getMainNode().setFlags(GridFlags.MULTIBLOCK).addService(IGridMultiblock.class, this::getMultiblockNodes);
        setInternalMaxPower(16_000_000);
        setPowerSides(EnumSet.allOf(Direction.class));
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void onReady() {
        super.onReady();
        rebuildMultiblock();
        if (level instanceof ServerLevel server) {
            server.getServer().executeIfPossible(() -> {
                if (!isRemoved() && level == server) {
                    rebuildMultiblock();
                    wake();
                }
            });
        }
    }

    public void rebuildMultiblock() {
        if (level instanceof ServerLevel server) calculator.calculateMultiblock(server, worldPosition);
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (level instanceof ServerLevel server) {
            calculator.updateMultiblockAfterNeighborUpdate(server, worldPosition, changedPos);
        }
    }

    public void breakCluster() {
        if (cluster != null) cluster.destroy();
    }

    public boolean isFormed() { return formed; }

    public void setFormed(boolean value) {
        if (formed == value) return;
        formed = value;
        setProcessingTime(value && !pendingBatches.isEmpty() ? pendingBatches.peekFirst().progress : 0);
        updateState();
        setChanged();
        markForUpdate();
        wake();
    }

    public void updateState() {
        if (level == null || isRemoved()) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() != getBlockState().getBlock() || !state.hasProperty(ECOIntegratedWorkingStation.FORMED)) return;
        BlockState next = state.setValue(ECOIntegratedWorkingStation.FORMED, formed);
        if (next != state) level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        retryPowerRefund();
        if (!formed) return super.tickingRequest(node, ticksSinceLastCall);
        if (cluster == null || cluster.getInputHatch() == null || cluster.getOutputHatch() == null) {
            setWorking(false);
            return TickRateModulation.SLOWER;
        }
        moveFluid(super.getInputTank(), cluster.getInputHatch().tank);
        moveFluid(super.getOutputTank(), cluster.getOutputHatch().tank);
        if (!getMainNode().isActive()) {
            setWorking(false);
            return TickRateModulation.SLOWER;
        }
        return tickBatch();
    }

    private static void moveFluid(net.minecraftforge.fluids.capability.templates.FluidTank source,
            net.minecraftforge.fluids.capability.templates.FluidTank destination) {
        if (source.getFluid().isEmpty()) return;
        int filled = destination.fill(source.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) source.drain(filled, IFluidHandler.FluidAction.EXECUTE);
    }

    public void onChangeTankFromHatch() {
        wake();
    }

    private void wake() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    public boolean isOverclocked() { return overclocked; }
    public boolean isActiveCooling() { return activeCooling; }
    public void setOverclocked(boolean value) { overclocked = value; setChanged(); wake(); }
    public void setActiveCooling(boolean value) { activeCooling = value; setChanged(); wake(); }

    private LargeWorkstationOverclock currentProfile() {
        CoolingRecipe recipe = coolingRecipe();
        return LargeWorkstationOverclock.forCurrentSettings(overclocked, activeCooling,
                recipe == null ? 0 : recipe.maxOverclock());
    }

    @Nullable private CoolingRecipe coolingRecipe() {
        if (level == null || cluster == null || cluster.getInputHatch() == null) return null;
        FluidStack input = cluster.getInputHatch().tank.getFluid();
        if (input.isEmpty()) return null;
        CoolingRecipe best = null;
        for (CoolingRecipe candidate : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            if (candidate.input().ingredient().test(input)
                    && (best == null || candidate.maxOverclock() > best.maxOverclock())) best = candidate;
        }
        return best;
    }

    public int getMaxBatchParallelism() { return currentProfile().maxParallelism(); }

    public List<GenericStack> getCurrentBatchInputs() {
        PendingBatch batch = pendingBatches.peekFirst();
        return formed && batch != null ? ECOFastPathStacks.copyCounter(batch.inputs) : List.of();
    }

    public boolean canAcceptPattern() {
        if (!formed || cluster == null || !getMainNode().isActive() || !quarantinedBatches.isEmpty()
                || pendingBatches.size() >= MAX_PENDING_BATCHES) return false;
        for (PendingBatch batch : pendingBatches) if (batch.canceling) return false;
        return true;
    }

    public boolean containsPendingPatternInput(Set<AEKey> patternInputs) {
        if (patternInputs == null || patternInputs.isEmpty()) return false;
        for (PendingBatch batch : pendingBatches) {
            for (var entry : batch.inputs) {
                if (entry.getLongValue() > 0 && patternInputs.contains(entry.getKey().dropSecondary())) return true;
            }
        }
        return false;
    }

    public boolean acceptPattern(IPatternDetails pattern, KeyCounter[] holders, boolean commit) {
        if (commit && (holders == null || !canAcceptPattern())) return false;
        PendingBatch batch = makeBatch(pattern, holders, 1, pattern == null ? null : pattern.getPrimaryOutput());
        if (batch == null) return false;
        if (commit) enqueue(batch, holders);
        return true;
    }

    public boolean acceptPatternBatch(IPatternDetails pattern, KeyCounter[] holders, long craftCount,
            @Nullable UUID craftingJobId, @Nullable GenericStack expectedResult) {
        // This AE2 version has no job-targeted output router.
        if (craftingJobId != null || holders == null || !canAcceptPattern()) return false;
        PendingBatch batch = makeBatch(pattern, holders, craftCount, expectedResult);
        if (batch == null) return false;
        enqueue(batch, holders);
        return true;
    }

    private void enqueue(PendingBatch batch, @Nullable KeyCounter[] holders) {
        pendingBatches.addLast(batch);
        if (holders != null) for (KeyCounter holder : holders) holder.clear();
        if (pendingBatches.size() == 1) setProcessingTime(0);
        setChanged();
        wake();
    }

    @Nullable private PendingBatch makeBatch(IPatternDetails pattern, @Nullable KeyCounter[] holders,
            long crafts, @Nullable GenericStack unlock) {
        if (!formed || level == null || pattern == null || !currentProfile().acceptsCraftCount(crafts)) return null;
        try {
            KeyCounter inputs = new KeyCounter();
            IPatternDetails.IInput[] patternInputs = pattern.getInputs();
            if (patternInputs == null) return null;
            if (holders == null) {
                for (IPatternDetails.IInput input : patternInputs) {
                    if (input == null || input.getMultiplier() <= 0) return null;
                    GenericStack[] possible = input.getPossibleInputs();
                    if (possible == null || possible.length == 0 || possible[0] == null
                            || possible[0].what() == null || possible[0].amount() <= 0) return null;
                    inputs.add(possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier()));
                }
            } else {
                if (holders.length != patternInputs.length) return null;
                for (KeyCounter holder : holders) {
                    if (holder == null) return null;
                    inputs.addAll(holder);
                }
            }
            KeyCounter oneCraft = divide(inputs, crafts);
            if (oneCraft == null || empty(oneCraft)) return null;
            KeyCounter outputs = new KeyCounter();
            GenericStack[] patternOutputs = pattern.getOutputs();
            if (patternOutputs == null) return null;
            for (GenericStack output : patternOutputs) {
                if (output == null || output.what() == null || output.amount() <= 0) return null;
                outputs.add(output.what(), output.amount());
            }
            if (empty(outputs)) return null;
            var recipe = LargeWorkstationRecipes.find(level, oneCraft, outputs);
            if (recipe == null || !recipe.extraInputs().isEmpty()) return null;
            KeyCounter totalOutputs = scale(outputs, crafts);
            if (holders != null) {
                for (int i = 0; i < holders.length; i++) {
                    if (patternInputs[i] == null) return null;
                    for (var entry : holders[i]) {
                        AEKey remainder = patternInputs[i].getRemainingKey(entry.getKey());
                        if (remainder != null) {
                            long perInput = patternInputAmount(patternInputs[i], entry.getKey());
                            if (perInput <= 0 || entry.getLongValue() % perInput != 0) return null;
                            totalOutputs.add(remainder, entry.getLongValue() / perInput);
                        }
                    }
                }
            }
            if (!validCounter(inputs) || !validCounter(totalOutputs)) return null;
            LargeWorkstationOverclock profile = currentProfile();
            return new PendingBatch(crafts, recipe.energy(), profile.coolingTier(),
                    profile.energyMultiplier(), inputs, totalOutputs, unlock);
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            return null;
        }
    }

    private static long patternInputAmount(IPatternDetails.IInput input, AEKey key) {
        long amount = 0;
        GenericStack[] possible = input.getPossibleInputs();
        if (possible == null) return 0;
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.what() != null && candidate.what().equals(key)) {
                if (amount != 0 && amount != candidate.amount()) return 0;
                amount = candidate.amount();
            }
        }
        return amount;
    }

    @Nullable private static KeyCounter divide(KeyCounter counter, long divisor) {
        KeyCounter result = new KeyCounter();
        for (var entry : counter) {
            if (entry.getLongValue() <= 0 || entry.getLongValue() % divisor != 0) return null;
            result.add(entry.getKey(), entry.getLongValue() / divisor);
        }
        return result;
    }

    private static KeyCounter scale(KeyCounter counter, long multiplier) {
        KeyCounter result = new KeyCounter();
        for (var entry : counter) result.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
        return result;
    }

    private static boolean validCounter(KeyCounter counter) {
        int entries = 0;
        for (var entry : counter) {
            if (++entries > MAX_BATCH_ENTRIES || entry.getKey() == null || entry.getLongValue() <= 0
                    || entry.getLongValue() > MAX_BATCH_AMOUNT) return false;
        }
        return true;
    }

    private static boolean empty(KeyCounter counter) {
        for (var entry : counter) if (entry.getLongValue() > 0) return false;
        return true;
    }

    private TickRateModulation tickBatch() {
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch == null) {
            setWorking(false);
            setProcessingTime(0);
            return TickRateModulation.SLOWER;
        }
        setWorking(true);
        setProcessingTime(batch.progress);
        if (batch.canceling) {
            returnStoredInputs();
            return TickRateModulation.SLOWER;
        }
        if (!empty(batch.pendingOutput)) {
            if (deliver(batch)) finishBatch();
            return TickRateModulation.SLOWER;
        }
        int advance = Math.min(1024, PROCESSING_STEPS - batch.progress);
        if (advance <= 0) return complete(batch);
        FluidStack byproduct = FluidStack.EMPTY;
        if (batch.coolingTier > 0) {
            CoolingRecipe cooling = coolingRecipe();
            if (!overclocked || !activeCooling || cooling == null
                    || cooling.maxOverclock() < batch.coolingTier) return TickRateModulation.SLOWER;
            FluidTank input = cluster.getInputHatch().tank;
            FluidTank output = cluster.getOutputHatch().tank;
            if (input.getFluidAmount() < 100 || cooling.inputAmount() <= 0) return TickRateModulation.SLOWER;
            long amount = (long) cooling.outputAmount() * 100 / cooling.inputAmount();
            if (amount < 0 || amount > Integer.MAX_VALUE) return TickRateModulation.SLOWER;
            if (amount > 0) {
                byproduct = cooling.output().copy();
                byproduct.setAmount((int) amount);
            }
            if (!byproduct.isEmpty() && output.fill(byproduct, IFluidHandler.FluidAction.SIMULATE)
                    != byproduct.getAmount()) return TickRateModulation.SLOWER;
        }
        double energy = (double) batch.energyPerCraft * batch.energyMultiplier * batch.crafts;
        double required = energy * advance / PROCESSING_STEPS;
        if (!Double.isFinite(required)) return TickRateModulation.SLOWER;
        IEnergySource source = this;
        var grid = getMainNode().getGrid();
        if (required > 0 && extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.001 < required
                && grid != null) source = grid.getEnergyService();
        double available = source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (required > 0 && available + 0.001 < required) {
            advance = (int) Math.min(advance, Math.max(0L, (long) Math.floor(available * PROCESSING_STEPS / energy)));
            if (advance == 0) return TickRateModulation.SLOWER;
            required = energy * advance / PROCESSING_STEPS;
        }
        IEnergySource paymentSource = source;
        FluidStack output = byproduct;
        LargeWorkstationTickPayment.Result payment = LargeWorkstationTickPayment.commit(source, required,
                () -> batch.coolingTier == 0 || consumeCooling(output), amount -> refundPower(paymentSource, amount));
        if (payment != LargeWorkstationTickPayment.Result.PAID) return TickRateModulation.SLOWER;
        batch.progress += advance;
        setProcessingTime(batch.progress);
        setChanged();
        return batch.progress >= PROCESSING_STEPS ? complete(batch) : TickRateModulation.URGENT;
    }

    private boolean consumeCooling(FluidStack byproduct) {
        FluidTank input = cluster.getInputHatch().tank;
        FluidTank output = cluster.getOutputHatch().tank;
        FluidStack drained = input.drain(100, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != 100) {
            if (!drained.isEmpty()) input.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }
        if (!byproduct.isEmpty()) {
            int filled = output.fill(byproduct, IFluidHandler.FluidAction.EXECUTE);
            if (filled != byproduct.getAmount()) {
                if (filled > 0) output.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                input.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                return false;
            }
        }
        return true;
    }

    private TickRateModulation complete(PendingBatch batch) {
        batch.progress = PROCESSING_STEPS;
        batch.inputs.clear();
        batch.pendingOutput.addAll(batch.outputs);
        setChanged();
        if (deliver(batch)) finishBatch();
        return TickRateModulation.URGENT;
    }

    private boolean deliver(PendingBatch batch) {
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        MEStorage storage = grid.getStorageService().getInventory();
        for (GenericStack stack : ECOFastPathStacks.copyCounter(batch.pendingOutput)) {
            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, IActionSource.ofMachine(this));
            if (inserted < 0 || inserted > stack.amount()) throw new IllegalStateException("Invalid output insertion");
            if (inserted > 0) {
                batch.pendingOutput.remove(stack.what(), inserted);
                setChanged();
            }
            if (inserted < stack.amount()) return false;
        }
        if (cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            communication.getWorkstationProvider().onPatternResult(batch.unlock);
        }
        return true;
    }

    /** Returns only unfinished work; a completed batch must deliver its output instead. */
    public void returnStoredInputs() {
        if (level == null || level.isClientSide) return;
        for (PendingBatch batch : pendingBatches) {
            if (batch.progress < PROCESSING_STEPS) batch.canceling = true;
        }
        setChanged();
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        MEStorage storage = grid.getStorageService().getInventory();
        for (PendingBatch batch : new ArrayList<>(pendingBatches)) {
            if (batch.progress >= PROCESSING_STEPS) continue;
            for (GenericStack stack : ECOFastPathStacks.copyCounter(batch.inputs)) {
                long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE,
                        IActionSource.ofMachine(this));
                if (inserted < 0 || inserted > stack.amount()) {
                    throw new IllegalStateException("Invalid input recovery insertion");
                }
                if (inserted > 0) {
                    batch.inputs.remove(stack.what(), inserted);
                    setChanged();
                }
                if (inserted < stack.amount()) return;
            }
            if (cluster != null
                    && cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
                communication.getWorkstationProvider().onPatternAborted(batch.unlock);
            }
            pendingBatches.remove(batch);
            setChanged();
        }
        setProcessingTime(pendingBatches.isEmpty() ? 0 : pendingBatches.peekFirst().progress);
        setWorking(!pendingBatches.isEmpty());
        if (cluster != null
                && cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            ICraftingProvider.requestUpdate(communication.getMainNode());
        }
    }

    private void finishBatch() {
        pendingBatches.removeFirst();
        setProcessingTime(pendingBatches.isEmpty() ? 0 : pendingBatches.peekFirst().progress);
        setWorking(!pendingBatches.isEmpty());
        setChanged();
        if (cluster != null && cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            ICraftingProvider.requestUpdate(communication.getMainNode());
        }
    }

    private void refundPower(IEnergySource source, double amount) {
        double leftover = injectAEPower(amount, Actionable.MODULATE);
        if (leftover > 0 && getMainNode().getGrid() != null) {
            leftover = getMainNode().getGrid().getEnergyService().injectPower(leftover, Actionable.MODULATE);
        }
        if (leftover > 0) pendingPowerRefund += leftover;
        setChanged();
    }

    private void retryPowerRefund() {
        if (pendingPowerRefund <= 0) return;
        double leftover = injectAEPower(pendingPowerRefund, Actionable.MODULATE);
        if (leftover > 0 && getMainNode().getGrid() != null) {
            leftover = getMainNode().getGrid().getEnergyService().injectPower(leftover, Actionable.MODULATE);
        }
        if (leftover != pendingPowerRefund) {
            pendingPowerRefund = leftover;
            setChanged();
        }
    }

    @Override public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putBoolean("largeOverclocked", overclocked);
        data.putBoolean("largeActiveCooling", activeCooling);
        data.putDouble("largePowerRefund", pendingPowerRefund);
        ListTag batches = new ListTag();
        for (PendingBatch batch : pendingBatches) batches.add(batch.save());
        for (CompoundTag quarantined : quarantinedBatches) batches.add(quarantined.copy());
        data.put("pendingBatches", batches);
    }

    @Override public void loadTag(CompoundTag data) {
        super.loadTag(data);
        overclocked = data.getBoolean("largeOverclocked");
        activeCooling = data.getBoolean("largeActiveCooling");
        double refund = data.getDouble("largePowerRefund");
        pendingPowerRefund = Double.isFinite(refund) && refund > 0 ? refund : 0;
        pendingBatches.clear();
        quarantinedBatches.clear();
        ListTag batches = data.getList("pendingBatches", Tag.TAG_COMPOUND);
        for (int i = 0; i < batches.size(); i++) {
            PendingBatch batch;
            try {
                batch = PendingBatch.load(batches.getCompound(i));
            } catch (RuntimeException invalid) {
                batch = null;
            }
            if (batch != null && pendingBatches.size() < MAX_PENDING_BATCHES) pendingBatches.addLast(batch);
            else quarantinedBatches.add(batches.getCompound(i).copy());
        }
        if (!pendingBatches.isEmpty()) setProcessingTime(pendingBatches.peekFirst().progress);
    }

    @Override public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (PendingBatch batch : pendingBatches) {
            KeyCounter owned = empty(batch.pendingOutput) ? batch.inputs : batch.pendingOutput;
            for (GenericStack stack : ECOFastPathStacks.copyCounter(owned)) {
                stack.what().addDrops(stack.amount(), drops, level, pos);
            }
        }
        for (CompoundTag quarantined : quarantinedBatches) {
            String ownedKey = quarantined.getInt("progress") >= PROCESSING_STEPS
                    || !quarantined.getList("pendingOutput", Tag.TAG_COMPOUND).isEmpty()
                    ? "pendingOutput" : "inputTotal";
            ListTag entries = quarantined.getList(ownedKey, Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                try {
                    GenericStack stack = GenericStack.readTag(entries.getCompound(i));
                    if (stack != null && stack.what() != null && stack.amount() > 0) {
                        stack.what().addDrops(stack.amount(), drops, level, pos);
                    }
                } catch (RuntimeException invalid) {
                    // Corrupted entries cannot be converted into physical drops.
                }
            }
        }
        pendingBatches.clear();
        quarantinedBatches.clear();
    }

    @Override public void clearContent() {
        pendingBatches.clear();
        quarantinedBatches.clear();
        super.clearContent();
    }

    static final class PendingBatch {
        final long crafts;
        final long energyPerCraft;
        final int coolingTier;
        final int energyMultiplier;
        final KeyCounter inputs = new KeyCounter();
        final KeyCounter outputs = new KeyCounter();
        final KeyCounter pendingOutput = new KeyCounter();
        @Nullable final GenericStack unlock;
        int progress;
        boolean canceling;

        PendingBatch(long crafts, long energyPerCraft, int coolingTier, int energyMultiplier,
                KeyCounter inputs, KeyCounter outputs, @Nullable GenericStack unlock) {
            this.crafts = crafts;
            this.energyPerCraft = energyPerCraft;
            this.coolingTier = coolingTier;
            this.energyMultiplier = energyMultiplier;
            this.inputs.addAll(inputs);
            this.outputs.addAll(outputs);
            this.unlock = unlock;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("craftCount", crafts);
            tag.putLong("energyPerCraft", energyPerCraft);
            tag.putInt("coolingTier", coolingTier);
            tag.putInt("energyMultiplier", energyMultiplier);
            tag.putInt("progress", progress);
            tag.putBoolean("canceling", canceling);
            tag.put("inputTotal", ECOFastPathStacks.writeGenericStacks(null, ECOFastPathStacks.copyCounter(inputs)));
            tag.put("outputTotal", ECOFastPathStacks.writeGenericStacks(null, ECOFastPathStacks.copyCounter(outputs)));
            tag.put("pendingOutput", ECOFastPathStacks.writeGenericStacks(null, ECOFastPathStacks.copyCounter(pendingOutput)));
            if (unlock != null) tag.put("unlockStack", GenericStack.writeTag(unlock));
            return tag;
        }

        @Nullable static PendingBatch load(CompoundTag tag) {
            if (tag.hasUUID("craftingJobId")
                    || !tag.getList("missingExtras", Tag.TAG_COMPOUND).isEmpty()) return null;
            long crafts = tag.getLong("craftCount");
            long energy = tag.getLong("energyPerCraft");
            int tier = tag.getInt("coolingTier");
            int multiplier = tag.getInt("energyMultiplier");
            int progress = tag.getInt("progress");
            LargeWorkstationOverclock profile = LargeWorkstationOverclock.fromPersisted(tier, multiplier);
            if (profile == null || !profile.acceptsCraftCount(crafts) || energy < 0
                    || progress < 0 || progress > PROCESSING_STEPS) return null;
            KeyCounter inputs = readCounter(tag, "inputTotal");
            KeyCounter outputs = readCounter(tag, "outputTotal");
            KeyCounter pending = readCounter(tag, "pendingOutput");
            boolean canceling = tag.getBoolean("canceling");
            if (inputs == null || outputs == null || pending == null || empty(outputs)
                    || progress < PROCESSING_STEPS && empty(inputs) && !canceling
                    || progress < PROCESSING_STEPS && !empty(pending)
                    || progress == PROCESSING_STEPS && (empty(pending) || !empty(inputs) || canceling)) return null;
            GenericStack unlock = tag.contains("unlockStack", Tag.TAG_COMPOUND)
                    ? GenericStack.readTag(tag.getCompound("unlockStack")) : null;
            PendingBatch batch = new PendingBatch(crafts, energy, tier, multiplier, inputs, outputs, unlock);
            batch.pendingOutput.addAll(pending);
            batch.progress = progress;
            batch.canceling = canceling;
            return batch;
        }

        @Nullable private static KeyCounter readCounter(CompoundTag tag, String key) {
            ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
            if (entries.size() > MAX_BATCH_ENTRIES) return null;
            KeyCounter result = new KeyCounter();
            try {
                for (int i = 0; i < entries.size(); i++) {
                    GenericStack stack = GenericStack.readTag(entries.getCompound(i));
                    if (stack == null || stack.what() == null || stack.amount() <= 0
                            || stack.amount() > MAX_BATCH_AMOUNT
                            || result.get(stack.what()) > MAX_BATCH_AMOUNT - stack.amount()) return null;
                    result.add(stack.what(), stack.amount());
                }
            } catch (RuntimeException invalid) {
                return null;
            }
            return result;
        }
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        List<IGridNode> nodes = new ArrayList<>();
        if (cluster != null) {
            cluster.getBlockEntities().forEachRemaining(member -> {
                IGridNode node = member.getGridNode();
                if (node != null) nodes.add(node);
            });
        }
        return nodes.iterator();
    }

    @Override public NEIntegratedWorkingStationCluster getCluster() { return cluster; }

    public void updateCluster(@Nullable NEIntegratedWorkingStationCluster next) {
        cluster = next;
        setFormed(next != null);
    }

    @Override
    public void disconnect(boolean update) {
        if (cluster != null) {
            cluster.destroy();
            cluster = null;
            setFormed(false);
            if (update) updateState();
        }
    }

    @Override public boolean isValid() { return !isRemoved(); }
}
