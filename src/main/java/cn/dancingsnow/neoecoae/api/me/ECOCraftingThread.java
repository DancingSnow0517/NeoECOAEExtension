package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import appeng.menu.AutoCraftingMenu;
import cn.dancingsnow.neoecoae.api.NEFakePlayer;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingWork;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.NeoECOAE;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    public static final int MAX_PROGRESS = 100;
    private static final int MAX_SERIALIZED_ITEM_STACK_COUNT = 99;
    private static final int MAX_PERSISTED_ITEM_STACK_ENTRIES = 256;

    private enum RecoveryState {
        ACTIVE,
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

    private int progress = 0;
    private int occupiedThreadSlots = 1;
    private boolean outputsReady = false;
    private RecoveryState recoveryState = RecoveryState.CLEARED;
    private long lastEjectionFailureLogTick = Long.MIN_VALUE;
    private long lastRecoveryFailureLogTick = Long.MIN_VALUE;

    private final TransientCraftingContainer craftingInv;

    public ECOCraftingThread(ECOCraftingWorkerBlockEntity worker) {
        this.worker = worker;
        this.actionSource = IActionSource.ofMachine(worker);
        this.craftingInv = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
    }

    public TickRateModulation tick(int overlockTimes, int powerMultiply, int ticksSinceLastCall) {
        if (!isBusy) {
            progress = 0;
            setChanged();
            return TickRateModulation.SLEEP;
        }
        if (this.reboot) {
            ticksSinceLastCall = 1;
        }

        this.reboot = false;
        if (isRecoveringToNetwork()) {
            if (retryRecoveryToNetwork()) {
                setChanged();
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        if (outputsReady) {
            return ejectOutputsSafely();
        }

        int bonusValue = Math.min(10 + overlockTimes * 10, 100);
        progress += userPower(ticksSinceLastCall, bonusValue, powerMultiply);

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

    public int getProgress() {
        return progress;
    }

    public ItemStack getOutputItem() {
        return firstOutputItem().copy();
    }

    public List<ItemStack> getRemainingItems() {
        return copyStacks(remainingItems);
    }

    public Snapshot createSnapshot() {
        return new Snapshot(
            isBusy,
            progress,
            MAX_PROGRESS,
            getOccupiedThreadSlots(),
            getOutputItem(),
            getOutputAmount(),
            getRemainingItems(),
            outputsReady,
            craftingJobId
        );
    }

    public boolean pushPattern(
        IMolecularAssemblerSupportedPattern pattern,
        KeyCounter[] table,
        ECOCraftingSystemBlockEntity controller
    ) {
        return pushPattern(pattern, table, controller, null);
    }

    public boolean pushPattern(
        IMolecularAssemblerSupportedPattern pattern,
        KeyCounter[] table,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        return pushPattern(ECOExtractedPatternExecution.slow(pattern, table), controller, craftingJobId);
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

    public boolean pushBatch(ECOBatchCraftingRequest request, ECOCraftingSystemBlockEntity controller) {
        if (isBusy) {
            return false;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        if (!worker.isControlledBy(controller)) {
            cache.recordNoThreadReject();
            return false;
        }
        int controllerAvailableSlots = Math.max(0, controller.getThreadCount() - controller.getRunningThreadCount());
        if (request.batchSize() > worker.getAvailableThreadSlots()
            || request.batchSize() > controllerAvailableSlots) {
            cache.recordNoThreadReject();
            return false;
        }
        ECOFastPathResult cached = cache.peek(request.key());
        if (cached == null || !cached.matchesBatchRequest(request)) {
            cache.recordExpectedMismatch();
            return false;
        }
        var outputTotal = ECOBatchCraftingHelper.multiply(cached.outputEntries(), request.batchSize());
        var inputTotal = ECOBatchCraftingHelper.multiply(cached.inputEntries(), request.batchSize());
        var remainingTotal = ECOBatchCraftingHelper.multiply(cached.remainingEntries(), request.batchSize());
        var work = new ECOBatchCraftingWork(
            request.batchSize(),
            inputTotal,
            outputTotal,
            remainingTotal,
            request.craftingJobId(),
            0,
            request.batchSize()
        );
        return acceptBatch(work, controller);
    }

    private boolean acceptBatch(ECOBatchCraftingWork work, ECOCraftingSystemBlockEntity controller) {
        if (!canRetainGenericStacks(work.outputTotal())
            || !canRetainGenericStacks(work.inputTotal())
            || !canRetainGenericStacks(work.remainingTotal())) {
            worker.getFastPathCache().recordNonItemKey();
            return false;
        }
        if (!consumeCraftingCoolant(controller, work.batchSize())) {
            worker.getFastPathCache().recordCoolantReject();
            return false;
        }
        startBatchWork(
            work.outputTotal(),
            work.inputTotal(),
            work.remainingTotal(),
            work.craftingJobId(),
            work.occupiedThreadSlots()
        );
        worker.getFastPathCache().recordFastPathAccepted();
        return true;
    }

    private boolean acceptPattern(
        ECOExtractedPatternExecution execution,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        ECOFastPathKey key = execution.key();
        if (!canUseFastPath(execution, key)) {
            cache.recordDisabled();
            return calcPatternSlow(execution, controller, craftingJobId, false, tick);
        }

        ECOFastPathResult cached = cache.get(key, tick);
        if (cached != null) {
            if (cached.isNegative()) {
                cache.recordFallbackSlowPath();
                return calcPatternSlow(execution, controller, craftingJobId, false, tick);
            }
            FastPathWork fastPathWork = createFastPathWork(cached, execution);
            if (fastPathWork == null) {
                cache.putNegative(key, tick);
                cache.recordFallbackSlowPath();
                return calcPatternSlow(execution, controller, craftingJobId, false, tick);
            }
            if (!consumeCraftingCoolant(controller, 1)) {
                cache.recordCoolantReject();
                return false;
            }
            startWork(
                List.of(fastPathWork.output()), fastPathWork.inputs(), fastPathWork.remaining(), craftingJobId, 1
            );
            cache.recordFastPathAccepted();
            cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
            return true;
        }

        return calcPatternSlow(execution, controller, craftingJobId, true, tick);
    }

    private boolean canUseFastPath(ECOExtractedPatternExecution execution, @Nullable ECOFastPathKey key) {
        return key != null
            && execution.fastPathEligible()
            && NEConfig.ecoAe2FastPathEnabled
            && !NEConfig.postCraftingEvent;
    }

    @Nullable
    private FastPathWork createFastPathWork(ECOFastPathResult cached, ECOExtractedPatternExecution execution) {
        if (!cached.matchesExecution(execution)) {
            return null;
        }
        var output = ECOFastPathStacks.toSingleItemStack(cached.outputEntries());
        var inputs = ECOFastPathStacks.toItemStacks(cached.inputEntries());
        var remaining = ECOFastPathStacks.toItemStacks(cached.remainingEntries());
        if (output.isEmpty() || inputs.isEmpty() || remaining.isEmpty()) {
            return null;
        }
        return new FastPathWork(output.get(), inputs.get(), remaining.get());
    }

    private boolean calcPatternSlow(
        ECOExtractedPatternExecution execution,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId,
        boolean verifyFastPath,
        long tick
    ) {
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null) {
            return false;
        }
        KeyCounter[] table = execution.craftingContainer();
        craftingInv.clearContent();
        pattern.fillCraftingGrid(table, craftingInv::setItem);
        ItemStack outputItem = pattern.assemble(craftingInv.asCraftInput(), worker.getLevel());
        if (outputItem.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        if (!consumeCraftingCoolant(controller, 1)) {
            craftingInv.clearContent();
            return false;
        }

        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : pattern.getRemainingItems(craftingInv.asCraftInput())) {
            if (!item.isEmpty()) {
                list.add(item.copy());
            }
        }

        List<ItemStack> inputs = snapshotCraftingInputs();
        if (verifyFastPath) {
            verifyAndCacheFastPath(execution, outputItem, inputs, list, tick);
        }
        startWork(List.of(outputItem.copy()), inputs, list, craftingJobId, 1);
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        cache.recordSlowPathAccepted();
        cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
        return true;
    }

    private void verifyAndCacheFastPath(
        ECOExtractedPatternExecution execution,
        ItemStack outputItem,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        long tick
    ) {
        ECOFastPathKey key = execution.key();
        if (key == null) {
            return;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        var outputEntries = ECOFastPathStacks.fromItemStack(outputItem);
        var inputEntries = ECOFastPathStacks.fromItemStacks(inputs);
        var remainingEntries = ECOFastPathStacks.fromItemStacks(remaining);
        if (outputEntries.isEmpty() || inputEntries.isEmpty() || remainingEntries.isEmpty()) {
            cache.putNegative(key, tick);
            return;
        }
        if (!outputEntries.get().equals(execution.expectedOutputs())
            || !remainingEntries.get().equals(execution.expectedContainerItems())
            || !inputEntries.get().equals(execution.inputItems())) {
            cache.putNegative(key, tick);
            return;
        }
        cache.putPositive(key, outputEntries.get(), remainingEntries.get(), inputEntries.get(), tick);
    }

    private boolean consumeCraftingCoolant(ECOCraftingSystemBlockEntity controller, int craftCount) {
        return !controller.isActiveCooling()
            || controller.tryConsumeCoolant(5 * Math.max(1, craftCount), controller.getEffectiveOverclockTimes());
    }

    private void startWork(
        List<ItemStack> outputs,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        @Nullable UUID craftingJobId,
        int occupiedThreadSlots
    ) {
        outputItems.clear();
        copyStacks(outputs, outputItems);
        this.craftingJobId = craftingJobId;
        this.occupiedThreadSlots = Math.max(1, occupiedThreadSlots);
        this.outputsReady = false;
        inputItems.clear();
        copyStacks(inputs, inputItems);
        remainingItems.clear();
        copyStacks(remaining, remainingItems);
        batchOutputItems.clear();
        batchInputItems.clear();
        batchRemainingItems.clear();
        craftingEventOutput = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
        worker.onThreadWork(this.occupiedThreadSlots);
        recoveryState = RecoveryState.ACTIVE;
        reboot = true;
        isBusy = true;
    }

    private void startBatchWork(
        List<GenericStack> outputs,
        List<GenericStack> inputs,
        List<GenericStack> remaining,
        @Nullable UUID craftingJobId,
        int occupiedThreadSlots
    ) {
        outputItems.clear();
        inputItems.clear();
        remainingItems.clear();
        batchOutputItems.clear();
        batchOutputItems.addAll(outputs);
        batchInputItems.clear();
        batchInputItems.addAll(inputs);
        batchRemainingItems.clear();
        batchRemainingItems.addAll(remaining);
        craftingEventOutput = ItemStack.EMPTY;
        this.craftingJobId = craftingJobId;
        this.occupiedThreadSlots = Math.max(1, occupiedThreadSlots);
        this.outputsReady = false;
        worker.onThreadWork(this.occupiedThreadSlots);
        recoveryState = RecoveryState.ACTIVE;
        reboot = true;
        isBusy = true;
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

    private int userPower(int ticksPassed, int bonusValue, double acceleratorTax) {
        var grid = this.worker.getMainNode().getGrid();
        if (grid != null) {
            double slotScaledTax = acceleratorTax * Math.max(1, occupiedThreadSlots);
            var safePower = Math.min(ticksPassed * bonusValue * slotScaledTax, 500000);
            return (int) (grid.getEnergyService().extractAEPower(safePower, Actionable.MODULATE, PowerMultiplier.CONFIG)
                / slotScaledTax);
        } else {
            return 0;
        }
    }

    private boolean ejectOutputs() {
        IGrid grid = worker.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        CraftingService craftingService = (CraftingService) grid.getCraftingService();
        MEStorage storage = grid.getStorageService().getInventory();
        ItemStack eventOutput = NEConfig.postCraftingEvent
            ? (craftingEventOutput.isEmpty() ? firstOutputItem().copy() : craftingEventOutput.copy())
            : ItemStack.EMPTY;
        KeyCounter outputs = collectOutputItems();

        KeyCounter remainder = ejectAllAndCollectRemainder(craftingService, storage, outputs);
        if (!isEmpty(remainder)) {
            retainRemainderForRetry(remainder, RecoveryState.ACTIVE);
            return false;
        }

        if (NEConfig.postCraftingEvent) {
            postCraftingEventSafely(eventOutput);
        }
        worker.onThreadStop(occupiedThreadSlots);
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
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                counter.add(key, stack.getCount());
            }
        }
    }

    private boolean canInsertAll(MEStorage storage, KeyCounter stacks) {
        for (Object2LongMap.Entry<AEKey> entry : stacks) {
            long inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, actionSource);
            if (inserted != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private KeyCounter ejectAllAndCollectRemainder(CraftingService craftingService, MEStorage storage, KeyCounter stacks) {
        List<GenericStack> pendingEntries = keyCounterToGenericStacks(stacks);
        if (pendingEntries.isEmpty() && !isEmpty(stacks)) {
            throw new IllegalStateException("Cannot retain non-item crafting outputs for retry");
        }

        // Persist a shrinking pending ledger so completed external inserts are never retried.
        stacks.removeZeros();
        retainRemainderForRetry(stacks, RecoveryState.ACTIVE);
        for (GenericStack entry : pendingEntries) {
            AEKey key = entry.what();
            long remaining = entry.amount();
            long insertedIntoCpus = validateInsertionAmount(
                craftingService.insertIntoCpus(key, remaining, Actionable.MODULATE),
                remaining,
                "crafting CPUs"
            );
            if (insertedIntoCpus > 0L) {
                remaining -= insertedIntoCpus;
                removePendingOutput(stacks, key, insertedIntoCpus);
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
        List<GenericStack> pendingEntries = keyCounterToGenericStacks(stacks);
        if (pendingEntries.isEmpty() && !isEmpty(stacks)) {
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
        List<ItemStack> recoverable = recoverOutputs ? outputAndRemainingItems() : inputItems;
        List<GenericStack> recoverableGeneric = recoverOutputs ? batchOutputAndRemainingItems() : batchInputItems;
        if (recoverable.isEmpty() && recoverableGeneric.isEmpty()) {
            recoveryState = RecoveryState.RECOVERED_TO_NETWORK;
            worker.onThreadStop(occupiedThreadSlots);
            clearWork();
            setChanged();
            return true;
        }
        try {
            KeyCounter stacks = collectStacks(recoverable);
            addGenericStacks(stacks, recoverableGeneric);
            if (!canInsertAll(storage, stacks)) {
                markRecoveryPending(recoverOutputs);
                return false;
            }
            KeyCounter remainder = insertAllAndCollectRemainder(storage, stacks, recoverOutputs);
            if (!isEmpty(remainder)) {
                retainRecoveryRemainder(remainder, recoverOutputs);
                return false;
            }
        } catch (RuntimeException e) {
            markRecoveryPending(recoverOutputs);
            logRecoveryFailure(e);
            return false;
        }
        recoveryState = RecoveryState.RECOVERED_TO_NETWORK;
        worker.onThreadStop(occupiedThreadSlots);
        clearWork();
        setChanged();
        return true;
    }

    public void dropRecoverablesAndClear(List<ItemStack> drops) {
        if (!isRecoverableState()) {
            return;
        }
        List<ItemStack> recoverable = shouldRecoverOutputs() ? outputAndRemainingItems() : inputItems;
        for (ItemStack stack : recoverable) {
            if (!stack.isEmpty()) {
                copySerializableStacks(stack, drops);
            }
        }
        for (GenericStack stack : shouldRecoverOutputs() ? batchOutputAndRemainingItems() : batchInputItems) {
            copyGenericStackToDrops(stack, drops);
        }
        recoveryState = RecoveryState.DROPPED_TO_WORLD;
        worker.onThreadStop(occupiedThreadSlots);
        clearWork();
        setChanged();
    }

    private boolean isRecoveringToNetwork() {
        return recoveryState == RecoveryState.RECOVERING_INPUTS
            || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
    }

    private boolean isRecoverableState() {
        return isBusy
            && (recoveryState == RecoveryState.ACTIVE
                || recoveryState == RecoveryState.RECOVERING_INPUTS
                || recoveryState == RecoveryState.RECOVERING_OUTPUTS);
    }

    private boolean shouldRecoverOutputs() {
        return outputsReady || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
    }

    private void markRecoveryPending(boolean recoverOutputs) {
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

    private static KeyCounter collectStacks(List<ItemStack> stacks) {
        KeyCounter counter = new KeyCounter();
        for (ItemStack stack : stacks) {
            addStack(counter, stack);
        }
        return counter;
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
        outputItems.clear();
        inputItems.clear();
        remainingItems.clear();
        batchOutputItems.clear();
        batchInputItems.clear();
        batchRemainingItems.clear();
        craftingInv.clearContent();
        craftingEventOutput = ItemStack.EMPTY;
        craftingJobId = null;
        isBusy = false;
        reboot = true;
        progress = 0;
        occupiedThreadSlots = 1;
        outputsReady = false;
        recoveryState = RecoveryState.CLEARED;
    }

    private void retainRemainderForRetry(KeyCounter remainder, RecoveryState nextState) {
        List<GenericStack> stacks = keyCounterToGenericStacks(remainder);
        if (stacks.isEmpty() && !isEmpty(remainder)) {
            LOGGER.error(
                "ECO crafting thread cannot retain non-item output remainder for retry: worker={}",
                worker.getBlockPos()
            );
            worker.onThreadStop(occupiedThreadSlots);
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
        recoveryState = nextState;
        setChanged();
    }

    private void retainInputRemainderForRetry(KeyCounter remainder) {
        List<GenericStack> stacks = keyCounterToGenericStacks(remainder);
        if (stacks.isEmpty() && !isEmpty(remainder)) {
            LOGGER.error(
                "ECO crafting thread cannot retain non-item input remainder for retry: worker={}",
                worker.getBlockPos()
            );
            worker.onThreadStop(occupiedThreadSlots);
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

    private static List<ItemStack> keyCounterToItemStacks(KeyCounter counter) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            if (!(entry.getKey() instanceof AEItemKey itemKey) || entry.getLongValue() > Integer.MAX_VALUE) {
                return List.of();
            }
            int remaining = (int) entry.getLongValue();
            while (remaining > 0) {
                int count = Math.min(remaining, MAX_SERIALIZED_ITEM_STACK_COUNT);
                ItemStack stack = itemKey.toStack(count);
                if (stack.isEmpty()) {
                    return List.of();
                }
                stacks.add(stack);
                remaining -= count;
            }
        }
        return List.copyOf(stacks);
    }

    private static List<GenericStack> keyCounterToGenericStacks(KeyCounter counter) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            if (!(entry.getKey() instanceof AEItemKey)) {
                return List.of();
            }
            stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return List.copyOf(stacks);
    }

    private static boolean isEmpty(KeyCounter counter) {
        for (var ignored : counter) {
            return false;
        }
        return true;
    }

    private void logRecoveryFailure(RuntimeException e) {
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastRecoveryFailureLogTick;
        if (lastRecoveryFailureLogTick == Long.MIN_VALUE || elapsed < 0L || elapsed >= 100L) {
            lastRecoveryFailureLogTick = tick;
            LOGGER.error("ECO crafting recovery failed; pending items will be retried", e);
        }
    }

    private static boolean canRetainGenericStacks(List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0 || !(stack.what() instanceof AEItemKey)) {
                return false;
            }
        }
        return true;
    }

    private static void copyGenericStackToDrops(GenericStack stack, List<ItemStack> drops) {
        if (stack == null || stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE
            || !(stack.what() instanceof AEItemKey itemKey)) {
            return;
        }
        int remaining = (int) stack.amount();
        while (remaining > 0) {
            int count = Math.min(remaining, MAX_SERIALIZED_ITEM_STACK_COUNT);
            ItemStack itemStack = itemKey.toStack(count);
            if (itemStack.isEmpty()) {
                return;
            }
            drops.add(itemStack);
            remaining -= count;
        }
    }

    private void postCraftingEventSafely(ItemStack craftedOutput) {
        try {
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(
                NEFakePlayer.getFakePlayer((ServerLevel) worker.getLevel()), craftedOutput, craftingInv
            ));
        } catch (RuntimeException | Error e) {
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

    private long getOutputAmount() {
        long amount = 0;
        for (ItemStack stack : outputItems) {
            if (!stack.isEmpty()) {
                amount += stack.getCount();
            }
        }
        for (GenericStack stack : batchOutputItems) {
            if (stack != null && stack.amount() > 0) {
                amount += stack.amount();
            }
        }
        return Math.max(1L, amount);
    }

    public int getOccupiedThreadSlots() {
        return isBusy ? Math.max(1, occupiedThreadSlots) : 0;
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
        tag.putInt("neoecoae_version", 2);
        tag.putInt("occupiedThreadSlots", occupiedThreadSlots);
        tag.putBoolean("outputsReady", outputsReady);
        tag.putString("recoveryState", recoveryState.name());
        if (craftingJobId != null) {
            tag.putUUID("craftingJobId", craftingJobId);
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

    private static void copySerializableStacks(ItemStack stack, List<ItemStack> target) {
        int remaining = stack.getCount();
        while (remaining > 0) {
            int count = Math.min(remaining, MAX_SERIALIZED_ITEM_STACK_COUNT);
            target.add(stack.copyWithCount(count));
            remaining -= count;
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
        this.isBusy = nbt.getBoolean("isBusy");
        this.reboot = nbt.getBoolean("reboot");
        int persistedProgress = nbt.getInt("progress");
        int persistedOccupiedThreadSlots = nbt.contains("occupiedThreadSlots")
            ? nbt.getInt("occupiedThreadSlots")
            : 1;
        boolean invalidPersistedState = persistedProgress < 0
            || persistedOccupiedThreadSlots <= 0
            || persistedOccupiedThreadSlots > ECOBatchCraftingHelper.MAX_BATCH_SIZE;
        this.progress = Math.clamp(persistedProgress, 0, MAX_PROGRESS);
        this.occupiedThreadSlots = Math.clamp(
            persistedOccupiedThreadSlots, 1, ECOBatchCraftingHelper.MAX_BATCH_SIZE
        );
        this.outputsReady = nbt.getBoolean("outputsReady");
        this.craftingJobId = nbt.hasUUID("craftingJobId") ? nbt.getUUID("craftingJobId") : null;
        this.recoveryState = this.isBusy ? RecoveryState.ACTIVE : RecoveryState.CLEARED;
        if (nbt.contains("recoveryState", Tag.TAG_STRING)) {
            try {
                this.recoveryState = RecoveryState.valueOf(nbt.getString("recoveryState"));
            } catch (IllegalArgumentException e) {
                invalidPersistedState = true;
            }
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
        } else {
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
            var batchOutputs = ECOFastPathStacks.readValidatedBatchItemStacks(
                provider, nbt.getList("batchOutputItems", Tag.TAG_COMPOUND), !recoveringInputs
            );
            var batchInputs = ECOFastPathStacks.readValidatedBatchItemStacks(
                provider, nbt.getList("batchInputItems", Tag.TAG_COMPOUND), recoveringInputs
            );
            var batchRemaining = ECOFastPathStacks.readValidatedBatchItemStacks(
                provider, nbt.getList("batchRemainingItems", Tag.TAG_COMPOUND), false
            );
            batchOutputs.ifPresent(batchOutputItems::addAll);
            batchInputs.ifPresent(batchInputItems::addAll);
            batchRemaining.ifPresent(batchRemainingItems::addAll);
            invalidPersistedState |= batchOutputs.isEmpty()
                || batchInputs.isEmpty()
                || batchRemaining.isEmpty();
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

    private record FastPathWork(ItemStack output, List<ItemStack> inputs, List<ItemStack> remaining) {}

    public record Snapshot(
        boolean busy,
        int progress,
        int maxProgress,
        int occupiedThreadSlots,
        ItemStack outputItem,
        long outputAmount,
        List<ItemStack> remainingItems,
        boolean outputsReady,
        @Nullable UUID craftingJobId
    ) {}
}
