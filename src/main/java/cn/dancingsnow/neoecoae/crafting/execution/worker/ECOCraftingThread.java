package cn.dancingsnow.neoecoae.crafting.execution.worker;

import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import appeng.menu.AutoCraftingMenu;
import cn.dancingsnow.neoecoae.api.NEFakePlayer;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingWork;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVirtualCraftingWork;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.NeoECOAE;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingThread implements INBTSerializable<CompoundTag> {
    private static final int CURRENT_NBT_VERSION = 4;
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    public static final int MAX_PROGRESS = 100;
    private static final int MAX_SERIALIZED_ITEM_STACK_COUNT = 99;
    private static final int MAX_PERSISTED_ITEM_STACK_ENTRIES = 256;
    private static final long BLOCKED_PROGRESS_LOG_INTERVAL_TICKS = 100L;
    private static final long OWNING_CPU_RECOVERY_TIMEOUT_TICKS = 2_400L;

    private enum RecoveryState {
        ACTIVE,
        // A completed output remains owned by this worker until its job CPU returns.
        WAITING_FOR_OWNER,
        RECOVERING_INPUTS,
        RECOVERING_OUTPUTS,
        RECOVERED_TO_NETWORK,
        DROPPED_TO_WORLD,
        CLEARED
    }

    private final ECOCraftingWorkerBlockEntity worker;
    private final IActionSource actionSource;

    @Getter
    private boolean isBusy = false;

    private boolean reboot = true;

    private final List<ItemStack> outputItems = new ArrayList<>();
    private final List<ItemStack> inputItems = new ArrayList<>();
    private final List<ItemStack> remainingItems = new ArrayList<>();
    private final List<GenericStack> batchOutputItems = new ArrayList<>();
    private final List<GenericStack> batchInputItems = new ArrayList<>();
    private final List<GenericStack> batchRemainingItems = new ArrayList<>();
    private ItemStack craftingEventOutput = ItemStack.EMPTY;

    @Nullable
    private UUID craftingJobId = null;
    private boolean completedJobOutputsReleased;

    @Nullable
    private String fastPathReason = null;

    private int progress = 0;
    private double progressRemainder = 0.0D;
    private int finiteBatchCraftCount = 1;
    private long craftCount = 1L;
    private boolean virtualBatch = false;
    private @Nullable ECOExactVirtualLedger exactVirtual;
    private boolean outputsReady = false;
    private RecoveryState recoveryState = RecoveryState.CLEARED;
    private long lastEjectionFailureLogTick = Long.MIN_VALUE;
    private long lastRecoveryFailureLogTick = Long.MIN_VALUE;
    private long lastBlockedProgressLogTick = Long.MIN_VALUE;
    private long owningCpuMissingSinceGameTime = Long.MIN_VALUE;
    private final ECOCraftingThreadOutputDiagnostics outputDiagnostics =
        new ECOCraftingThreadOutputDiagnostics();

    private final TransientCraftingContainer craftingInv;
    private final ECOCraftingFastPathVerifier fastPathVerifier;

    public ECOCraftingThread(ECOCraftingWorkerBlockEntity worker) {
        this.worker = worker;
        this.actionSource = IActionSource.ofMachine(worker);
        this.craftingInv = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        this.fastPathVerifier = new ECOCraftingFastPathVerifier(worker, craftingInv);
    }

    public TickRateModulation tick(
        ECOCraftingSystemBlockEntity controller,
        int overlockTimes,
        int powerMultiply,
        int ticksSinceLastCall
    ) {
        if (!isBusy) {
            progress = 0;
            progressRemainder = 0.0D;
            setChanged();
            return TickRateModulation.SLEEP;
        }
        if (this.reboot) {
            ticksSinceLastCall = 1;
        }

        this.reboot = false;
        TickRateModulation recoveryRate = tickRecovery();
        if (recoveryRate != null) return recoveryRate;

        if (outputsReady) {
            return ejectOutputsSafely();
        }

        if (virtualBatch && controller.isFullVirtualCraftingMode()) {
            if (!controller.tryStartVirtualLaneTick()) {
                return TickRateModulation.SLOWER;
            }
            // Virtual execution has its own explicit one-tick path. It never depends on ordinary overclock.
            progress = MAX_PROGRESS;
            outputsReady = true;
            setChanged();
            return ejectOutputsSafely();
        }

        int bonusValue = calculateProgressPerTick(overlockTimes);
        int attemptedProgress = calculateRequestedProgress(
            ticksSinceLastCall,
            bonusValue,
            MAX_PROGRESS - progress
        );
        if (!controller.tryConsumeTickBasedCoolant(finiteBatchCraftCount, attemptedProgress, overlockTimes)) {
            logBlockedProgress(controller, "coolant-unavailable", attemptedProgress, overlockTimes, powerMultiply);
            return TickRateModulation.SLOWER;
        }
        int progressed = userPower(controller, ticksSinceLastCall, bonusValue, powerMultiply, MAX_PROGRESS - progress);
        if (attemptedProgress > 0 && progressed <= 0) {
            String reason = worker.getMainNode().getGrid() == null
                ? "grid-unavailable" : "energy-unavailable";
            logBlockedProgress(controller, reason, attemptedProgress, overlockTimes, powerMultiply);
        }
        progress += progressed;

        if (this.progress >= MAX_PROGRESS) {
            outputsReady = true;
            setChanged();
            return ejectOutputsSafely();
        }
        setChanged();
        return TickRateModulation.URGENT;
    }

    public boolean isFree() {
        return !isBusy;
    }

    /** Recovery must also run while the worker has no formed crafting controller. */
    public @Nullable TickRateModulation tickRecovery() {
        reconcileJobTermination();
        if (recoveryState == RecoveryState.WAITING_FOR_OWNER && hasOwningCpuRecoveryTimedOut()) {
            UUID orphanedJobId = craftingJobId;
            ECOCraftingJobLifecycle.finish(worker.getLevel(), orphanedJobId, false);
            markRecoveryPending(true);
            LOGGER.warn(
                "ECO crafting worker stopped waiting for an unavailable owning CPU after {} ticks; "
                    + "the orphaned job was cancelled and its outputs will be recovered to network storage: "
                    + "worker={} job={}",
                OWNING_CPU_RECOVERY_TIMEOUT_TICKS,
                worker.getBlockPos(),
                orphanedJobId
            );
        }
        if (!isBusy || !isRecoveringToNetwork()) return null;
        return retryRecoveryToNetwork() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    /** Deliver already-produced items even while the crafting multiblock is unavailable. */
    public @Nullable TickRateModulation tickOutputOnly() {
        TickRateModulation recoveryRate = tickRecovery();
        if (recoveryRate != null) return recoveryRate;
        return isBusy && outputsReady ? ejectOutputsSafely() : null;
    }

    public void reconcileJobTermination() {
        if (isBusy && !isRecoveringToNetwork()
                && ECOCraftingJobLifecycle.isTerminated(worker.getLevel(), craftingJobId)) {
            markRecoveryPending(shouldRecoverOutputs());
        }
    }

    public ItemStack getOutputItem() {
        return firstOutputItem().copy();
    }

    /** Monitor-only view; batch keys never need to be materialized as ItemStacks. */
    public @Nullable AEItemKey getDisplayedOutputKey() {
        if (!isBusy) return null;
        if (!outputItems.isEmpty()) return AEItemKey.of(outputItems.getFirst());
        for (var stack : batchOutputItems) {
            if (stack.what() instanceof AEItemKey key) return key;
        }
        return null;
    }

    public long getDisplayedOutputAmount() {
        return isBusy ? getOutputAmount() : 0L;
    }

    public List<ItemStack> getRemainingItems() {
        return copyStacks(remainingItems);
    }

    public Snapshot createSnapshot() {
        return new Snapshot(
            isBusy,
            progress,
            MAX_PROGRESS,
            getFiniteBatchCraftCount(),
            getCraftCount(),
            virtualBatch,
            getOutputItem(),
            getOutputAmount(),
            getRemainingItems(),
            outputsReady,
            craftingJobId,
            fastPathReason
        );
    }

    public boolean pushPattern(
        ECOExtractedPatternExecution execution,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        if (isBusy) {
            return false;
        }

        return acceptPattern(execution, controller, craftingJobId);
    }

    public boolean pushBatch(
        ECOVerifiedFastPathExecution verified,
        ECOCraftingSystemBlockEntity controller
    ) {
        if (isBusy) {
            return false;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        if (!worker.isControlledBy(controller)) {
            return false;
        }
        if (!verified.recipe().isIssuedBy(cache)) {
            return false;
        }
        // The credential was minted by the shared cache after a full value verification of this very dispatch,
        // so the only things still worth checking here are that it has not been invalidated by a reload and
        // that its batch size still fits the live thread capacity. Re-comparing the three per-craft stack lists
        // a third time in the same synchronous call chain could not detect anything new.
        if (!verified.isCurrent(AE2PatternIntrospection.reloadGeneration())) {
            return false;
        }
        int batchSize = verified.batchSize();
        if (batchSize > worker.getAvailableBatchCapacity()) {
            return false;
        }
        var work = new ECOBatchCraftingWork(
            batchSize,
            verified.inputTotal(),
            verified.outputTotal(),
            verified.remainingTotal(),
            verified.craftingJobId()
        );
        return acceptBatch(work, controller);
    }

    public boolean pushExactVirtualBatch(
            cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe recipe,
            java.math.BigInteger count, UUID job, ECOCraftingSystemBlockEntity controller) {
        if (count.signum() <= 0 || job == null || recipe.reusableStateModel() != null
                || recipe.durabilityModel() != null) return false;
        var ledger = new ECOExactVirtualLedger(count, recipe.inputsPerCraft(),
            recipe.outputsPerCraft(), recipe.remainingPerCraft());
        var unit = recipe.withVirtualBatch(1L, job);
        if (unit == null || !pushVirtualBatch(unit, controller)) return false;
        exactVirtual = ledger;
        craftCount = count.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        setChanged();
        return true;
    }

    public java.math.BigInteger getExactCraftCount() {
        return exactVirtual == null ? java.math.BigInteger.valueOf(getCraftCount()) : exactVirtual.crafts();
    }

    public boolean pushVirtualBatch(
        ECOVerifiedVirtualExecution verified,
        ECOCraftingSystemBlockEntity controller
    ) {
        if (isBusy || !controller.isFullVirtualCraftingMode()) {
            return false;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        if (!worker.isControlledBy(controller)
            || !verified.recipe().isIssuedBy(cache)
            || !verified.isCurrent(AE2PatternIntrospection.reloadGeneration())) {
            return false;
        }
        ECOVirtualCraftingWork work = new ECOVirtualCraftingWork(
            verified.craftCount(),
            verified.inputTotal(),
            verified.outputTotal(),
            verified.remainingTotal(),
            verified.craftingJobId()
        );
        if (!ECOCraftingStackCodec.canRetain(work.outputTotal(), false)
            || !ECOCraftingStackCodec.canRetain(work.inputTotal(), true)
            || !ECOCraftingStackCodec.canRetain(work.remainingTotal(), false)) {
            return false;
        }
        startVirtualWork(work);
        fastPathReason = "FAST_PATH_HIT";
        return true;
    }

    private boolean acceptBatch(ECOBatchCraftingWork work, ECOCraftingSystemBlockEntity controller) {
        if (!ECOCraftingStackCodec.canRetain(work.outputTotal(), false)
            || !ECOCraftingStackCodec.canRetain(work.inputTotal(), true)
            || !ECOCraftingStackCodec.canRetain(work.remainingTotal(), false)) {
            return false;
        }
        if (!consumeCraftingCoolant(controller, work.batchSize())) {
            return false;
        }
        startBatchWork(work);
        fastPathReason = "FAST_PATH_HIT";
        return true;
    }

    private boolean acceptPattern(
        ECOExtractedPatternExecution execution,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        var prepared = fastPathVerifier.prepare(execution, TickHandler.instance().getCurrentTick());
        if (prepared == null) return false;
        if (!consumeCraftingCoolant(controller, 1)) {
            craftingInv.clearContent();
            return false;
        }
        fastPathReason = prepared.reason();
        startWork(prepared.outputs(), prepared.inputs(), prepared.remaining(), craftingJobId, 1);
        return true;
    }

    private boolean consumeCraftingCoolant(ECOCraftingSystemBlockEntity controller, int craftCount) {
        if (!controller.isActiveCooling() || controller.usesTickBasedCoolant()) {
            return true;
        }
        long requested = 5L * Math.max(1, craftCount);
        return requested <= Integer.MAX_VALUE
            && controller.tryConsumeCoolant((int) requested, controller.getEffectiveOverclockTimes());
    }

    private void startWork(
        List<ItemStack> outputs,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        @Nullable UUID craftingJobId,
        int finiteBatchCraftCount
    ) {
        installWork(ECOCraftingThreadWork.items(
            outputs, inputs, remaining, craftingJobId, finiteBatchCraftCount));
    }

    private void startBatchWork(ECOBatchCraftingWork work) {
        installWork(ECOCraftingThreadWork.batch(work));
    }

    private void startVirtualWork(ECOVirtualCraftingWork work) {
        installWork(ECOCraftingThreadWork.virtual(work));
    }

    private void installWork(ECOCraftingThreadWork work) {
        worker.markDisplayDirty();
        outputItems.clear();
        copyStacks(work.itemOutputs(), outputItems);
        inputItems.clear();
        copyStacks(work.itemInputs(), inputItems);
        remainingItems.clear();
        copyStacks(work.itemRemaining(), remainingItems);
        batchOutputItems.clear();
        batchOutputItems.addAll(work.genericOutputs());
        batchInputItems.clear();
        batchInputItems.addAll(work.genericInputs());
        batchRemainingItems.clear();
        batchRemainingItems.addAll(work.genericRemaining());
        craftingEventOutput = work.craftingEventOutput().copy();
        craftingJobId = work.craftingJobId();
        completedJobOutputsReleased = false;
        finiteBatchCraftCount = Math.max(1, work.finiteBatchCraftCount());
        craftCount = Math.max(1L, work.craftCount());
        virtualBatch = work.virtualBatch();
        if (work.resetProgress()) {
            progress = 0;
        }
        progressRemainder = 0.0D;
        outputsReady = false;
        try {
            worker.onBatchStarted();
            recoveryState = RecoveryState.ACTIVE;
            reboot = true;
            isBusy = true;
            worker.onThreadAvailabilityChanged();
        } catch (RuntimeException | Error e) {
            clearWork();
            throw e;
        }
    }

    private static void copyStacks(List<ItemStack> source, List<ItemStack> target) {
        for (ItemStack stack : source) {
            if (!stack.isEmpty()) {
                target.add(stack.copy());
            }
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>();
        copyStacks(source, copy);
        return List.copyOf(copy);
    }

    private List<ItemStack> snapshotCraftingInputs() {
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < craftingInv.getContainerSize(); slot++) {
            ItemStack stack = craftingInv.getItem(slot);
            if (!stack.isEmpty()) {
                inputs.add(stack.copy());
            }
        }
        return inputs;
    }

    private int userPower(
        ECOCraftingSystemBlockEntity controller,
        int ticksPassed,
        int bonusValue,
        double acceleratorTax,
        int remainingProgress
    ) {
        var grid = this.worker.getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }

        int requestedProgress = calculateRequestedProgress(ticksPassed, bonusValue, remainingProgress);
        if (controller.isFullVirtualCraftingMode()) {
            // The group already pays a flat draw once per tick, so scaling this thread's cost by its occupied
            // slots would bill the same work twice - and would make a large batch unaffordable by construction.
            return controller.tryConsumeVirtualCraftingPower() ? requestedProgress : 0;
        }
        double powerPerProgress = calculatePowerPerProgress(acceleratorTax, finiteBatchCraftCount);
        if (requestedProgress <= 0 || powerPerProgress <= 0.0D) {
            return 0;
        }

        double requestedPower = Math.max(0.0D, requestedProgress - progressRemainder) * powerPerProgress;
        if (!Double.isFinite(requestedPower) || requestedPower <= 0.0D) {
            return 0;
        }
        double extractedPower = grid.getEnergyService().extractAEPower(
            requestedPower, Actionable.MODULATE, PowerMultiplier.CONFIG
        );
        PowerProgress powered = accumulatePoweredProgress(
            extractedPower,
            powerPerProgress,
            requestedProgress,
            progressRemainder
        );
        progressRemainder = powered.remainder();
        return powered.completed();
    }

    private int calculateProgressPerTick(int overclockTimes) {
        return Math.clamp(10 + Math.max(0, overclockTimes) * 10, 10, MAX_PROGRESS);
    }

    private int calculateRequestedProgress(int ticksPassed, int bonusValue, int remainingProgress) {
        long requested = (long) Math.max(0, ticksPassed) * Math.max(0, bonusValue);
        return (int) Math.min(Math.max(0, remainingProgress), Math.min(Integer.MAX_VALUE, requested));
    }

    private double calculatePowerPerProgress(double acceleratorTax, int finiteBatchCraftCount) {
        if (!Double.isFinite(acceleratorTax) || acceleratorTax <= 0.0D) {
            return 0.0D;
        }
        return acceleratorTax * Math.max(1, finiteBatchCraftCount);
    }

    private PowerProgress accumulatePoweredProgress(
        double extractedPower,
        double powerPerProgress,
        int requestedProgress,
        double previousRemainder
    ) {
        double safeRemainder = Double.isFinite(previousRemainder)
            && previousRemainder >= 0.0D
            && previousRemainder < 1.0D
                ? previousRemainder
                : 0.0D;
        if (!Double.isFinite(extractedPower) || extractedPower <= 0.0D
            || !Double.isFinite(powerPerProgress) || powerPerProgress <= 0.0D
            || requestedProgress <= 0) {
            return new PowerProgress(0, safeRemainder);
        }
        double fundedProgress = Math.min(
            requestedProgress,
            safeRemainder + extractedPower / powerPerProgress
        );
        int completed = (int) Math.min(
            requestedProgress,
            Math.floor(fundedProgress + 1.0E-9D)
        );
        double remainder = completed >= requestedProgress
            ? 0.0D
            : Math.max(0.0D, Math.min(Math.nextDown(1.0D), fundedProgress - completed));
        return new PowerProgress(completed, remainder);
    }

    record PowerProgress(int completed, double remainder) {}

    private boolean ejectOutputs() {
        IGrid grid = worker.getMainNode().getGrid();
        if (grid == null) {
            logBlockedOutput("network-unavailable", null);
            return false;
        }

        CraftingService craftingService = (CraftingService) grid.getCraftingService();
        MEStorage storage = grid.getStorageService().getInventory();
        ItemStack eventOutput = NEConfig.postCraftingEvent
            ? (craftingEventOutput.isEmpty() ? firstOutputItem().copy() : craftingEventOutput.copy())
            : ItemStack.EMPTY;
        if (exactVirtual != null) {
            boolean finished = exactVirtual.drainExact(true, (key, amount) -> {
                if (!completedJobOutputsReleased && craftingJobId != null) {
                    if (!(craftingService instanceof ECOCraftingOutputRouter router)) return java.math.BigInteger.ZERO;
                    long offered = amount.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
                    long inserted = router.neoecoae$insertIntoCpuForJob(
                            craftingJobId, key, offered, Actionable.MODULATE);
                    if (inserted == 0 && !completedJobOutputsReleased)
                        recoveryState = RecoveryState.WAITING_FOR_OWNER;
                    if (inserted > 0) {
                        recoveryState = RecoveryState.ACTIVE;
                        owningCpuMissingSinceGameTime = Long.MIN_VALUE;
                    }
                    return java.math.BigInteger.valueOf(inserted);
                }
                return ECOBigIntegerStorage.insert(storage, key, amount, Actionable.MODULATE, actionSource);
            }, this::setChanged);
            if (!finished) return false;
            worker.onBatchStopped();
            clearWork();
            return true;
        }
        KeyCounter outputs = collectOutputItems();

        KeyCounter remainder = ejectAllAndCollectRemainder(craftingService, storage, outputs);
        if (!ECOCraftingStackCodec.isEmpty(remainder)) {
            RecoveryState retryState = recoveryState == RecoveryState.WAITING_FOR_OWNER
                ? RecoveryState.WAITING_FOR_OWNER
                : RecoveryState.ACTIVE;
            retainRemainderForRetry(remainder, retryState);
            if (retryState != RecoveryState.WAITING_FOR_OWNER) {
                logBlockedOutput("network-capacity", remainder);
            }
            return false;
        }

        if (NEConfig.postCraftingEvent) {
            postCraftingEventSafely(eventOutput);
        }
        worker.onBatchStopped();
        clearWork();
        return true;
    }

    private TickRateModulation ejectOutputsSafely() {
        try {
            if (ejectOutputs()) {
                setChanged();
            }
            return TickRateModulation.URGENT;
        } catch (RuntimeException e) {
            long tick = TickHandler.instance().getCurrentTick();
            long elapsed = tick - lastEjectionFailureLogTick;
            if (lastEjectionFailureLogTick == Long.MIN_VALUE || elapsed < 0L || elapsed >= 100L) {
                lastEjectionFailureLogTick = tick;
                LOGGER.error("ECO crafting output ejection failed; pending outputs will be retried", e);
            }
            return TickRateModulation.SLOWER;
        }
    }

    private void logBlockedProgress(
        ECOCraftingSystemBlockEntity controller,
        String reason,
        int attemptedProgress,
        int overclockTimes,
        int powerMultiply
    ) {
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastBlockedProgressLogTick;
        if (lastBlockedProgressLogTick != Long.MIN_VALUE && elapsed >= 0L
            && elapsed < BLOCKED_PROGRESS_LOG_INTERVAL_TICKS) {
            return;
        }
        lastBlockedProgressLogTick = tick;
        LOGGER.warn(
            "ECO crafting progress blocked: worker={} reason={} job={} progress={}/{} attemptedProgress={} "
                + "batchCrafts={} craftCount={} virtualBatch={} overclockTimes={} powerMultiply={} "
                + "activeCooling={} coolant={}/{}",
            worker.getBlockPos(),
            reason,
            craftingJobId,
            progress,
            MAX_PROGRESS,
            attemptedProgress,
            finiteBatchCraftCount,
            craftCount,
            virtualBatch,
            overclockTimes,
            powerMultiply,
            controller.isActiveCooling(),
            controller.getDisplayedCoolantAmount(),
            controller.getDisplayedCoolantCapacity()
        );
    }

    private void logBlockedOutput(String reason, @Nullable KeyCounter pending) {
        outputDiagnostics.blocked(worker, craftingJobId, reason, pending, progress, MAX_PROGRESS,
            finiteBatchCraftCount, craftCount, virtualBatch, TickHandler.instance().getCurrentTick());
    }

    private void finishBlockedOutputDiagnostic() {
        outputDiagnostics.finished(worker, craftingJobId, TickHandler.instance().getCurrentTick());
    }

    private KeyCounter collectOutputItems() {
        KeyCounter outputs = new KeyCounter();
        for (ItemStack outputItem : outputItems) {
            addStack(outputs, outputItem);
        }
        for (ItemStack remainingItem : remainingItems) {
            addStack(outputs, remainingItem);
        }
        addGenericStacks(outputs, batchOutputItems);
        addGenericStacks(outputs, batchRemainingItems);
        return outputs;
    }

    private static void addStack(KeyCounter counter, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            GenericStack genericStack = GenericStack.fromItemStack(stack);
            if (genericStack != null && genericStack.amount() > 0L) {
                counter.add(genericStack.what(), genericStack.amount());
            }
        }
    }

    private KeyCounter ejectAllAndCollectRemainder(CraftingService craftingService, MEStorage storage, KeyCounter stacks) {
        List<GenericStack> pendingEntries = ECOCraftingStackCodec.toGenericStacks(stacks, false);
        if (pendingEntries.isEmpty() && !ECOCraftingStackCodec.isEmpty(stacks)) {
            throw new IllegalStateException("Cannot retain non-item crafting outputs for retry");
        }

        // Persist a shrinking pending ledger so completed external inserts are never retried.
        stacks.removeZeros();
        retainRemainderForRetry(stacks, recoveryState == RecoveryState.WAITING_FOR_OWNER
            ? RecoveryState.WAITING_FOR_OWNER : RecoveryState.ACTIVE);
        for (GenericStack entry : pendingEntries) {
            AEKey key = entry.what();
            long remaining = entry.amount();
            long insertedIntoCpus;
            boolean routedToOwningJob = craftingJobId != null
                && craftingService instanceof ECOCraftingOutputRouter;
            if (completedJobOutputsReleased) {
                insertedIntoCpus = 0L;
            } else if (routedToOwningJob) {
                insertedIntoCpus = validateInsertionAmount(
                    ((ECOCraftingOutputRouter) craftingService).neoecoae$insertIntoCpuForJob(
                        craftingJobId, key, remaining, Actionable.MODULATE),
                    remaining,
                    "owning crafting CPU"
                );
            } else {
                insertedIntoCpus = validateInsertionAmount(
                    craftingService.insertIntoCpus(key, remaining, Actionable.MODULATE),
                    remaining,
                    "crafting CPUs"
                );
            }
            if (insertedIntoCpus > 0L) {
                remaining -= insertedIntoCpus;
                removePendingOutput(stacks, key, insertedIntoCpus);
            }

            // An ECO worker knows which CPU owns its output. Never fall through to another CPU or network storage
            // when that owner has not accepted it yet; doing so loses the job's dependency edge permanently.
            if (routedToOwningJob && !completedJobOutputsReleased && remaining > 0L) {
                // A missing owner is transient until the job is explicitly cancelled. Keep the output owned
                // by this worker and retry job-directed delivery; never leak it into generic ME storage.
                retainRemainderForRetry(stacks, RecoveryState.WAITING_FOR_OWNER);
                logBlockedOutput("waiting-for-owning-cpu", stacks);
                continue;
            }

            if (remaining > 0L) {
                long insertedIntoStorage = validateInsertionAmount(
                    storage.insert(key, remaining, Actionable.MODULATE, actionSource),
                    remaining,
                    "network storage"
                );
                if (insertedIntoStorage > 0L) {
                    removePendingOutput(stacks, key, insertedIntoStorage);
                }
            }
        }
        return stacks;
    }

    private void removePendingOutput(KeyCounter pending, AEKey key, long amount) {
        pending.remove(key, amount);
        pending.removeZeros();
        retainRemainderForRetry(pending, RecoveryState.ACTIVE);
    }

    private static long validateInsertionAmount(long inserted, long requested, String target) {
        if (inserted < 0L || inserted > requested) {
            throw new IllegalStateException(
                "Invalid insertion result from " + target + ": " + inserted + " for " + requested
            );
        }
        return inserted;
    }

    private KeyCounter insertAllAndCollectRemainder(
        MEStorage storage,
        KeyCounter stacks,
        boolean recoverOutputs
    ) {
        List<GenericStack> pendingEntries = ECOCraftingStackCodec.toGenericStacks(stacks, !recoverOutputs);
        if (pendingEntries.isEmpty() && !ECOCraftingStackCodec.isEmpty(stacks)) {
            throw new IllegalStateException("Cannot retain non-item crafting recovery stacks");
        }
        stacks.removeZeros();
        retainRecoveryRemainder(stacks, recoverOutputs);
        for (GenericStack entry : pendingEntries) {
            long inserted = validateInsertionAmount(
                storage.insert(entry.what(), entry.amount(), Actionable.MODULATE, actionSource),
                entry.amount(),
                "network recovery storage"
            );
            if (inserted > 0L) {
                stacks.remove(entry.what(), inserted);
                stacks.removeZeros();
                retainRecoveryRemainder(stacks, recoverOutputs);
            }
        }
        return stacks;
    }

    private void retainRecoveryRemainder(KeyCounter remainder, boolean recoverOutputs) {
        if (recoverOutputs) {
            retainRemainderForRetry(remainder, RecoveryState.RECOVERING_OUTPUTS);
        } else {
            retainInputRemainderForRetry(remainder);
        }
    }

    public boolean belongsToJob(UUID jobId) {
        return this.isBusy && Objects.equals(jobId, this.craftingJobId);
    }

    public void releaseCompletedJobOutputs(UUID jobId) {
        if (belongsToJob(jobId)) {
            // Do not eject here: completion can be reported inside the current output insertion.
            completedJobOutputsReleased = true;
            setChanged();
        }
    }

    public boolean recoverInputsToNetwork(MEStorage storage) {
        if (!isRecoverableState()) {
            return true;
        }
        return recoverItemsToNetwork(storage, shouldRecoverOutputs());
    }

    private boolean retryRecoveryToNetwork() {
        IGrid grid = worker.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        return recoverItemsToNetwork(grid.getStorageService().getInventory(), shouldRecoverOutputs());
    }

    private boolean recoverItemsToNetwork(MEStorage storage, boolean recoverOutputs) {
        if (exactVirtual != null) {
            try {
                if (!exactVirtual.drainExact(recoverOutputs,
                        (key, amount) -> ECOBigIntegerStorage.insert(
                                storage, key, amount, Actionable.MODULATE, actionSource),
                        this::setChanged)) return false;
                worker.onBatchStopped();
                clearWork();
                setChanged();
                return true;
            } catch (RuntimeException failure) {
                markRecoveryPending(recoverOutputs);
                logRecoveryFailure(failure);
                return false;
            }
        }
        List<ItemStack> recoverable = recoverOutputs ? outputAndRemainingItems() : inputItems;
        List<GenericStack> recoverableGeneric = recoverOutputs ? batchOutputAndRemainingItems() : batchInputItems;
        if (recoverable.isEmpty() && recoverableGeneric.isEmpty()) {
            recoveryState = RecoveryState.RECOVERED_TO_NETWORK;
            worker.onBatchStopped();
            clearWork();
            setChanged();
            return true;
        }
        try {
        KeyCounter stacks = ECOCraftingStackCodec.collect(recoverable);
            addGenericStacks(stacks, recoverableGeneric);
            // Commit whatever fits. The insertion loop persists the remainder after each successful
            // key, so a full cell or a later exception cannot replay earlier transfers on retry.
            KeyCounter remainder = insertAllAndCollectRemainder(storage, stacks, recoverOutputs);
            if (!ECOCraftingStackCodec.isEmpty(remainder)) {
                retainRecoveryRemainder(remainder, recoverOutputs);
                return false;
            }
        } catch (RuntimeException e) {
            markRecoveryPending(recoverOutputs);
            logRecoveryFailure(e);
            return false;
        }
        recoveryState = RecoveryState.RECOVERED_TO_NETWORK;
        worker.onBatchStopped();
        clearWork();
        setChanged();
        return true;
    }

    /**
     * Makes this thread portable in the worker block item's block-entity data.
     *
     * <p>The complete ItemStack/GenericStack custody remains in NBT. In particular, fluid keys and amounts above
     * {@link Integer#MAX_VALUE} are never narrowed to ordinary world drops. When the worker is placed again, the
     * durable terminal decision makes the normal recovery path return the retained custody to its new grid.</p>
     */
    public boolean preparePortableRecovery() {
        if (!isRecoverableState()) {
            return false;
        }
        ECOCraftingJobLifecycle.finish(worker.getLevel(), craftingJobId, false);
        reboot = true;
        setChanged();
        return true;
    }

    private boolean isRecoveringToNetwork() {
        return recoveryState == RecoveryState.RECOVERING_INPUTS
            || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
    }

    private boolean isRecoverableState() {
        return isBusy
            && (recoveryState == RecoveryState.ACTIVE
                || recoveryState == RecoveryState.WAITING_FOR_OWNER
                || recoveryState == RecoveryState.RECOVERING_INPUTS
                || recoveryState == RecoveryState.RECOVERING_OUTPUTS);
    }

    private boolean shouldRecoverOutputs() {
        return outputsReady || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
    }

    private boolean hasOwningCpuRecoveryTimedOut() {
        long gameTime = worker.getLevel() == null ? TickHandler.instance().getCurrentTick()
            : worker.getLevel().getGameTime();
        if (owningCpuMissingSinceGameTime == Long.MIN_VALUE || gameTime < owningCpuMissingSinceGameTime) {
            owningCpuMissingSinceGameTime = gameTime;
            setChanged();
            return false;
        }
        return gameTime - owningCpuMissingSinceGameTime >= OWNING_CPU_RECOVERY_TIMEOUT_TICKS;
    }

    private void markRecoveryPending(boolean recoverOutputs) {
        if (!recoverOutputs && (!outputItems.isEmpty() || !batchOutputItems.isEmpty())) {
            worker.markDisplayDirty();
        }
        isBusy = true;
        reboot = true;
        if (recoverOutputs) {
            inputItems.clear();
            batchInputItems.clear();
            outputsReady = true;
            recoveryState = RecoveryState.RECOVERING_OUTPUTS;
        } else {
            outputItems.clear();
            remainingItems.clear();
            batchOutputItems.clear();
            batchRemainingItems.clear();
            outputsReady = false;
            recoveryState = RecoveryState.RECOVERING_INPUTS;
        }
        setChanged();
    }

    private List<ItemStack> outputAndRemainingItems() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.addAll(outputItems);
        stacks.addAll(remainingItems);
        return stacks;
    }

    private List<GenericStack> batchOutputAndRemainingItems() {
        List<GenericStack> stacks = new ArrayList<>(batchOutputItems.size() + batchRemainingItems.size());
        stacks.addAll(batchOutputItems);
        stacks.addAll(batchRemainingItems);
        return List.copyOf(stacks);
    }

    private void clearWork() {
        boolean availabilityChanged = isBusy;
        exactVirtual = null;
        finishBlockedOutputDiagnostic();
        worker.markDisplayDirty();
        outputItems.clear();
        inputItems.clear();
        remainingItems.clear();
        batchOutputItems.clear();
        batchInputItems.clear();
        batchRemainingItems.clear();
        craftingInv.clearContent();
        craftingEventOutput = ItemStack.EMPTY;
        craftingJobId = null;
        completedJobOutputsReleased = false;
        fastPathReason = null;
        isBusy = false;
        reboot = true;
        progress = 0;
        progressRemainder = 0.0D;
        finiteBatchCraftCount = 1;
        craftCount = 1L;
        virtualBatch = false;
        outputsReady = false;
        recoveryState = RecoveryState.CLEARED;
        owningCpuMissingSinceGameTime = Long.MIN_VALUE;
        lastBlockedProgressLogTick = Long.MIN_VALUE;
        outputDiagnostics.reset();
        if (availabilityChanged) {
            worker.onThreadAvailabilityChanged();
        }
    }

    private void retainRemainderForRetry(KeyCounter remainder, RecoveryState nextState) {
        worker.markDisplayDirty();
        List<GenericStack> stacks = ECOCraftingStackCodec.toGenericStacks(remainder, false);
        if (stacks.isEmpty() && !ECOCraftingStackCodec.isEmpty(remainder)) {
            LOGGER.error(
                "ECO crafting thread cannot retain non-item output remainder for retry: worker={}",
                worker.getBlockPos()
            );
            worker.onBatchStopped();
            clearWork();
            return;
        }

        outputItems.clear();
        remainingItems.clear();
        inputItems.clear();
        batchOutputItems.clear();
        batchOutputItems.addAll(stacks);
        batchRemainingItems.clear();
        batchInputItems.clear();
        isBusy = true;
        outputsReady = true;
        if (nextState == RecoveryState.WAITING_FOR_OWNER
                && recoveryState != RecoveryState.WAITING_FOR_OWNER) {
            owningCpuMissingSinceGameTime = worker.getLevel() == null
                ? TickHandler.instance().getCurrentTick() : worker.getLevel().getGameTime();
        } else if (nextState != RecoveryState.WAITING_FOR_OWNER) {
            owningCpuMissingSinceGameTime = Long.MIN_VALUE;
        }
        recoveryState = nextState;
        setChanged();
    }

    private void retainInputRemainderForRetry(KeyCounter remainder) {
        worker.markDisplayDirty();
        List<GenericStack> stacks = ECOCraftingStackCodec.toGenericStacks(remainder, true);
        if (stacks.isEmpty() && !ECOCraftingStackCodec.isEmpty(remainder)) {
            LOGGER.error(
                "ECO crafting thread cannot retain non-item input remainder for retry: worker={}",
                worker.getBlockPos()
            );
            worker.onBatchStopped();
            clearWork();
            return;
        }

        inputItems.clear();
        outputItems.clear();
        remainingItems.clear();
        batchInputItems.clear();
        batchInputItems.addAll(stacks);
        batchOutputItems.clear();
        batchRemainingItems.clear();
        isBusy = true;
        outputsReady = false;
        recoveryState = RecoveryState.RECOVERING_INPUTS;
        setChanged();
    }

    private void logRecoveryFailure(RuntimeException e) {
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastRecoveryFailureLogTick;
        if (lastRecoveryFailureLogTick == Long.MIN_VALUE || elapsed < 0L || elapsed >= 100L) {
            lastRecoveryFailureLogTick = tick;
            LOGGER.error("ECO crafting recovery failed; pending items will be retried", e);
        }
    }

    private void postCraftingEventSafely(ItemStack craftedOutput) {
        try {
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(
                NEFakePlayer.getFakePlayer((ServerLevel) worker.getLevel()), craftedOutput, craftingInv
            ));
        } catch (RuntimeException e) {
            LOGGER.warn("ECO crafting post-crafting event failed: worker={}", worker.getBlockPos(), e);
        }
    }

    private ItemStack firstOutputItem() {
        if (!outputItems.isEmpty()) {
            return outputItems.get(0);
        }
        for (GenericStack stack : batchOutputItems) {
            if (stack.what() instanceof AEItemKey itemKey) {
                ItemStack itemStack = itemKey.toStack(1);
                if (!itemStack.isEmpty()) {
                    return itemStack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public java.math.BigInteger getExactOutputAmount() {
        return exactVirtual == null ? java.math.BigInteger.valueOf(getOutputAmount())
            : exactVirtual.snapshot(true).values().stream().reduce(java.math.BigInteger.ZERO, java.math.BigInteger::add);
    }

    private long getOutputAmount() {
        if (exactVirtual != null) return getExactOutputAmount()
            .min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        long amount = 0;
        for (ItemStack stack : outputItems) {
            if (!stack.isEmpty()) {
                amount += stack.getCount();
            }
        }
        for (GenericStack stack : batchOutputItems) {
            if (stack != null && stack.amount() > 0) {
                amount = amount > Long.MAX_VALUE - stack.amount() ? Long.MAX_VALUE : amount + stack.amount();
            }
        }
        return Math.max(1L, amount);
    }

    public int getFiniteBatchCraftCount() {
        return isBusy ? Math.max(1, finiteBatchCraftCount) : 0;
    }

    public long getCraftCount() {
        return isBusy ? Math.max(1L, craftCount) : 0L;
    }

    private void setChanged() {
        worker.setChanged();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        boolean batchGenericWork =
            !batchOutputItems.isEmpty() || !batchInputItems.isEmpty() || !batchRemainingItems.isEmpty();
        tag.putBoolean("isBusy", isBusy);
        tag.putBoolean("reboot", reboot);
        tag.putInt("progress", progress);
        writeProgressRemainder(tag, progressRemainder);
        tag.putInt("neoecoae_version", CURRENT_NBT_VERSION);
        tag.putInt("finiteBatchCraftCount", finiteBatchCraftCount);
        tag.putLong("craftCount", craftCount);
        tag.putBoolean("virtualBatch", virtualBatch);
        if (exactVirtual != null) tag.put("exactVirtual", exactVirtual.write(provider));
        tag.putBoolean("outputsReady", outputsReady);
        tag.putBoolean("completedJobOutputsReleased", completedJobOutputsReleased);
        tag.putString("recoveryState", recoveryState.name());
        if (owningCpuMissingSinceGameTime != Long.MIN_VALUE) {
            tag.putLong("owningCpuMissingSinceGameTime", owningCpuMissingSinceGameTime);
        }
        if (craftingJobId != null) {
            tag.putUUID("craftingJobId", craftingJobId);
        }
        if (fastPathReason != null) {
            tag.putString("fastPathReason", fastPathReason);
        }
        if (!craftingEventOutput.isEmpty()) {
            tag.put("craftingEventOutput", saveSerializableStack(craftingEventOutput, provider));
        }
        if (batchGenericWork) {
            tag.putBoolean("batchGenericWork", true);
            tag.put("batchOutputItems", ECOFastPathStacks.writeGenericStacks(provider, batchOutputItems));
            tag.put("batchInputItems", ECOFastPathStacks.writeGenericStacks(provider, batchInputItems));
            tag.put("batchRemainingItems", ECOFastPathStacks.writeGenericStacks(provider, batchRemainingItems));
        } else {
            tag.put("outputItem", saveSerializableStack(firstOutputItem(), provider));
        }

        ListTag outputs = new ListTag();
        saveSerializableStacks(outputItems, outputs, provider);
        tag.put("outputItems", outputs);

        ListTag inputs = new ListTag();
        saveSerializableStacks(inputItems, inputs, provider);
        tag.put("inputItems", inputs);

        ListTag remaining = new ListTag();
        saveSerializableStacks(remainingItems, remaining, provider);
        tag.put("remainingItems", remaining);
        return tag;
    }

    private static Tag saveSerializableStack(ItemStack stack, HolderLookup.Provider provider) {
        if (stack.isEmpty() || stack.getCount() <= MAX_SERIALIZED_ITEM_STACK_COUNT) {
            return stack.saveOptional(provider);
        }
        return stack.copyWithCount(MAX_SERIALIZED_ITEM_STACK_COUNT).saveOptional(provider);
    }

    private static void saveSerializableStacks(
        List<ItemStack> stacks,
        ListTag tag,
        HolderLookup.Provider provider
    ) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copySerializableStacks(stack, tag, provider);
            }
        }
    }

    private static void addGenericStacks(KeyCounter counter, List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                counter.add(stack.what(), stack.amount());
            }
        }
    }

    private static void copySerializableStacks(ItemStack stack, ListTag tag, HolderLookup.Provider provider) {
        int remaining = stack.getCount();
        while (remaining > 0) {
            int count = Math.min(remaining, MAX_SERIALIZED_ITEM_STACK_COUNT);
            tag.add(stack.copyWithCount(count).saveOptional(provider));
            remaining -= count;
        }
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        worker.markDisplayDirty();
        exactVirtual = nbt.contains("exactVirtual", Tag.TAG_COMPOUND)
            ? ECOExactVirtualLedger.read(nbt.getCompound("exactVirtual"), provider) : null;
        int persistedVersion = nbt.getInt("neoecoae_version");
        this.isBusy = nbt.getBoolean("isBusy");
        this.reboot = nbt.getBoolean("reboot");
        int persistedProgress = nbt.getInt("progress");
        int persistedFiniteBatchCraftCount = nbt.contains("finiteBatchCraftCount")
            ? nbt.getInt("finiteBatchCraftCount")
            : nbt.contains("occupiedThreadSlots") ? nbt.getInt("occupiedThreadSlots") : 1;
        boolean invalidPersistedState = persistedProgress < 0
            || persistedFiniteBatchCraftCount <= 0;
        this.progress = Math.clamp(persistedProgress, 0, MAX_PROGRESS);
        this.progressRemainder = readProgressRemainder(nbt);
        this.finiteBatchCraftCount = ECOBatchCraftingHelper.clampPersistedBatchSize(persistedFiniteBatchCraftCount);
        this.craftCount = nbt.contains("craftCount") ? Math.max(1L, nbt.getLong("craftCount"))
            : this.finiteBatchCraftCount;
        this.virtualBatch = nbt.getBoolean("virtualBatch");
        this.outputsReady = nbt.getBoolean("outputsReady");
        this.craftingJobId = nbt.hasUUID("craftingJobId") ? nbt.getUUID("craftingJobId") : null;
        this.completedJobOutputsReleased = nbt.getBoolean("completedJobOutputsReleased");
        this.fastPathReason = nbt.contains("fastPathReason", Tag.TAG_STRING)
            ? nbt.getString("fastPathReason") : null;
        this.recoveryState = this.isBusy ? RecoveryState.ACTIVE : RecoveryState.CLEARED;
        if (nbt.contains("recoveryState", Tag.TAG_STRING)) {
            try {
                this.recoveryState = RecoveryState.valueOf(nbt.getString("recoveryState"));
            } catch (IllegalArgumentException e) {
                invalidPersistedState = true;
            }
        }
        this.owningCpuMissingSinceGameTime = nbt.contains("owningCpuMissingSinceGameTime")
            ? nbt.getLong("owningCpuMissingSinceGameTime") : Long.MIN_VALUE;
        if (recoveryState != RecoveryState.WAITING_FOR_OWNER) {
            owningCpuMissingSinceGameTime = Long.MIN_VALUE;
        }
        boolean batchGenericWork = nbt.getBoolean("batchGenericWork");

        outputItems.clear();
        ListTag outputs = nbt.getList("outputItems", Tag.TAG_COMPOUND);
        invalidPersistedState |= outputs.size() > MAX_PERSISTED_ITEM_STACK_ENTRIES;
        if (batchGenericWork) {
            outputItems.clear();
        } else if (!outputs.isEmpty()) {
            for (int i = 0; i < Math.min(outputs.size(), MAX_PERSISTED_ITEM_STACK_ENTRIES); i++) {
                try {
                    ItemStack output = ItemStack.parseOptional(provider, outputs.getCompound(i));
                    if (output.isEmpty()) {
                        invalidPersistedState = true;
                    } else {
                        outputItems.add(output);
                    }
                } catch (RuntimeException e) {
                    invalidPersistedState = true;
                }
            }
        } else if (persistedVersion < CURRENT_NBT_VERSION) {
            // Compatibility with the singular output format used through 1.21.1-1.3.4.
            try {
                ItemStack output = ItemStack.parseOptional(provider, nbt.getCompound("outputItem"));
                if (!output.isEmpty()) {
                    outputItems.add(output);
                }
            } catch (RuntimeException e) {
                invalidPersistedState = true;
            }
        }

        inputItems.clear();
        ListTag inputs = nbt.getList("inputItems", Tag.TAG_COMPOUND);
        invalidPersistedState |= inputs.size() > MAX_PERSISTED_ITEM_STACK_ENTRIES;
        for (int i = 0; i < Math.min(inputs.size(), MAX_PERSISTED_ITEM_STACK_ENTRIES); i++) {
            try {
                ItemStack input = ItemStack.parseOptional(provider, inputs.getCompound(i));
                if (input.isEmpty()) {
                    invalidPersistedState = true;
                } else {
                    inputItems.add(input);
                }
            } catch (RuntimeException e) {
                invalidPersistedState = true;
            }
        }

        remainingItems.clear();
        ListTag remaining = nbt.getList("remainingItems", Tag.TAG_COMPOUND);
        invalidPersistedState |= remaining.size() > MAX_PERSISTED_ITEM_STACK_ENTRIES;
        for (int i = 0; i < Math.min(remaining.size(), MAX_PERSISTED_ITEM_STACK_ENTRIES); i++) {
            try {
                ItemStack remainingItem = ItemStack.parseOptional(provider, remaining.getCompound(i));
                if (remainingItem.isEmpty()) {
                    invalidPersistedState = true;
                } else {
                    remainingItems.add(remainingItem);
                }
            } catch (RuntimeException e) {
                invalidPersistedState = true;
            }
        }
        // This mixed state is not produced by either format; reject externally corrupted NBT.
        if (batchGenericWork && (!outputs.isEmpty() || !inputItems.isEmpty() || !remainingItems.isEmpty())) {
            invalidPersistedState = true;
            outputItems.clear();
            inputItems.clear();
            remainingItems.clear();
        }

        batchOutputItems.clear();
        batchInputItems.clear();
        batchRemainingItems.clear();
        if (batchGenericWork) {
            boolean recoveringInputs = recoveryState == RecoveryState.RECOVERING_INPUTS;
            long persistedAmountLimit = virtualBatch ? Long.MAX_VALUE : ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT;
            var batchOutputs = ECOFastPathStacks.readValidatedBatchItemStacks(
                provider, nbt.getList("batchOutputItems", Tag.TAG_COMPOUND), !recoveringInputs, persistedAmountLimit
            );
            var batchInputs = ECOFastPathStacks.readValidatedBatchInputStacks(
                provider, nbt.getList("batchInputItems", Tag.TAG_COMPOUND), recoveringInputs, persistedAmountLimit
            );
            var batchRemaining = ECOFastPathStacks.readValidatedBatchItemStacks(
                provider, nbt.getList("batchRemainingItems", Tag.TAG_COMPOUND), false, persistedAmountLimit
            );
            batchOutputs.ifPresent(batchOutputItems::addAll);
            batchInputs.ifPresent(batchInputItems::addAll);
            batchRemaining.ifPresent(batchRemainingItems::addAll);
            invalidPersistedState |= batchOutputs.isEmpty()
                || batchInputs.isEmpty()
                || batchRemaining.isEmpty();
        }
        // A thread can never occupy more slots than its own totals account for: batch work multiplies every
        // entry by the batch size, and non-batch work always occupies exactly one slot. Deriving the bound
        // from the persisted content rejects corrupted NBT without capping how large a legitimate batch may
        // grow on a high-capability host.
        int occupiedSlotsUpperBound;
        if (batchGenericWork) {
            List<GenericStack> slotWitness = batchOutputItems.isEmpty() ? batchInputItems : batchOutputItems;
            occupiedSlotsUpperBound = ECOBatchCraftingHelper.maxBatchSizeFromTotals(slotWitness);
        } else {
            occupiedSlotsUpperBound = 1;
        }
        if (!virtualBatch && this.finiteBatchCraftCount > occupiedSlotsUpperBound) {
            invalidPersistedState = true;
            this.finiteBatchCraftCount = Math.max(1, occupiedSlotsUpperBound);
        }
        try {
            craftingEventOutput = ItemStack.parseOptional(provider, nbt.getCompound("craftingEventOutput"));
        } catch (RuntimeException e) {
            craftingEventOutput = ItemStack.EMPTY;
            invalidPersistedState = true;
        }
        if (craftingEventOutput.isEmpty() && !batchGenericWork && !outputItems.isEmpty()) {
            craftingEventOutput = outputItems.get(0).copy();
        }

        boolean missingBatchRecoveryStacks = batchGenericWork
            && (recoveryState == RecoveryState.RECOVERING_INPUTS
                ? batchInputItems.isEmpty()
                : batchOutputItems.isEmpty());
        if (isBusy && (!isRecoverableState() || missingBatchRecoveryStacks)) {
            invalidPersistedState = true;
        }
        if (recoveryState == RecoveryState.WAITING_FOR_OWNER && craftingJobId == null) {
            invalidPersistedState = true;
        }
        if (!batchGenericWork && isBusy) {
            invalidPersistedState |= recoveryState == RecoveryState.RECOVERING_INPUTS
                ? inputItems.isEmpty()
                : outputItems.isEmpty();
        }
        if (!isBusy) {
            clearWork();
        } else if (invalidPersistedState) {
            quarantineInvalidDeserializedWork();
        }
    }

    private void quarantineInvalidDeserializedWork() {
        boolean recoverOutputs = shouldRecoverOutputs();
        LOGGER.error(
            "Invalid persisted ECO crafting work was quarantined for recovery: worker={} recoverOutputs={}",
            worker.getBlockPos(),
            recoverOutputs
        );
        progress = 0;
        progressRemainder = 0.0D;
        reboot = true;
        if (recoverOutputs) {
            inputItems.clear();
            batchInputItems.clear();
            outputsReady = true;
            recoveryState = RecoveryState.RECOVERING_OUTPUTS;
            if (outputItems.isEmpty() && remainingItems.isEmpty()
                && batchOutputItems.isEmpty() && batchRemainingItems.isEmpty()) {
                clearWork();
            }
        } else {
            outputItems.clear();
            remainingItems.clear();
            batchOutputItems.clear();
            batchRemainingItems.clear();
            outputsReady = false;
            recoveryState = RecoveryState.RECOVERING_INPUTS;
            if (inputItems.isEmpty() && batchInputItems.isEmpty()) {
                clearWork();
            }
        }
    }

    private void writeProgressRemainder(CompoundTag tag, double remainder) {
        double safeRemainder = sanitizeProgressRemainder(remainder);
        if (safeRemainder > 0.0D) {
            tag.putDouble("progressRemainder", safeRemainder);
        }
    }

    private double readProgressRemainder(CompoundTag tag) {
        return sanitizeProgressRemainder(tag.getDouble("progressRemainder"));
    }

    private static double sanitizeProgressRemainder(double remainder) {
        return Double.isFinite(remainder) && remainder >= 0.0D && remainder < 1.0D ? remainder : 0.0D;
    }

    public record Snapshot(
        boolean busy,
        int progress,
        int maxProgress,
        int finiteBatchCraftCount,
        long craftCount,
        boolean virtualBatch,
        ItemStack outputItem,
        long outputAmount,
        List<ItemStack> remainingItems,
        boolean outputsReady,
        @Nullable UUID craftingJobId,
        @Nullable String fastPathReason
    ) {}
}
