package cn.dancingsnow.neoecoae.api.me;

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
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import appeng.menu.AutoCraftingMenu;
import cn.dancingsnow.neoecoae.api.NEFakePlayer;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingWork;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOReusableStateAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOReusableStateModel;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingStateSlots;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathLookup;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVirtualCraftingWork;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathRecipe;
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
    private static final int CURRENT_NBT_VERSION = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    public static final int MAX_PROGRESS = 100;
    private static final int MAX_SERIALIZED_ITEM_STACK_COUNT = 99;
    private static final int MAX_PERSISTED_ITEM_STACK_ENTRIES = 256;
    private static final long BLOCKED_PROGRESS_LOG_INTERVAL_TICKS = 100L;
    private static final long BLOCKED_OUTPUT_LOG_INTERVAL_TICKS = 100L;

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

    @Nullable
    private String fastPathReason = null;

    private int progress = 0;
    private double progressRemainder = 0.0D;
    private int finiteBatchCraftCount = 1;
    private long craftCount = 1L;
    private boolean virtualBatch = false;
    private boolean outputsReady = false;
    private RecoveryState recoveryState = RecoveryState.CLEARED;
    private long lastEjectionFailureLogTick = Long.MIN_VALUE;
    private long lastRecoveryFailureLogTick = Long.MIN_VALUE;
    private long lastBlockedProgressLogTick = Long.MIN_VALUE;
    private long lastBlockedOutputLogTick = Long.MIN_VALUE;

    private final TransientCraftingContainer craftingInv;

    public ECOCraftingThread(ECOCraftingWorkerBlockEntity worker) {
        this.worker = worker;
        this.actionSource = IActionSource.ofMachine(worker);
        this.craftingInv = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
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
            cache.recordNoThreadReject();
            return false;
        }
        if (!verified.recipe().isIssuedBy(cache)) {
            cache.recordExpectedMismatch();
            return false;
        }
        // The credential was minted by the shared cache after a full value verification of this very dispatch,
        // so the only things still worth checking here are that it has not been invalidated by a reload and
        // that its batch size still fits the live thread capacity. Re-comparing the three per-craft stack lists
        // a third time in the same synchronous call chain could not detect anything new.
        if (!verified.isCurrent(AE2PatternIntrospection.reloadGeneration())) {
            cache.recordExpectedMismatch();
            return false;
        }
        int batchSize = verified.batchSize();
        if (batchSize > worker.getAvailableBatchCapacity()) {
            cache.recordNoThreadReject();
            return false;
        }
        var outputTotal = ECOBatchCraftingHelper.multiply(verified.outputsPerCraft(), batchSize);
        var inputTotal = verified.recipe().batchInputs(batchSize);
        var remainingTotal = verified.recipe().batchRemainders(batchSize);
        var work = new ECOBatchCraftingWork(
            batchSize,
            inputTotal,
            outputTotal,
            remainingTotal,
            verified.craftingJobId()
        );
        return acceptBatch(work, controller);
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
            cache.recordExpectedMismatch();
            return false;
        }
        ECOVirtualCraftingWork work = new ECOVirtualCraftingWork(
            verified.craftCount(),
            verified.recipe().batchInputs(verified.craftCount()),
            ECOBatchCraftingHelper.multiply(verified.recipe().outputsPerCraft(), verified.craftCount()),
            verified.recipe().batchRemainders(verified.craftCount()),
            verified.craftingJobId()
        );
        if (!canRetainGenericStacks(work.outputTotal())
            || !canRetainGenericStacks(work.inputTotal(), true)
            || !canRetainGenericStacks(work.remainingTotal())) {
            cache.recordNonItemKey();
            return false;
        }
        startVirtualWork(work);
        fastPathReason = "FAST_PATH_HIT";
        cache.recordFastPathAccepted();
        return true;
    }

    private boolean acceptBatch(ECOBatchCraftingWork work, ECOCraftingSystemBlockEntity controller) {
        if (!canRetainGenericStacks(work.outputTotal())
            || !canRetainGenericStacks(work.inputTotal(), true)
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
            work.batchSize()
        );
        fastPathReason = "FAST_PATH_HIT";
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
        if (!execution.canUseFastPath()) {
            fastPathReason = execution.fastPathReason();
            cache.recordDisabled(execution);
            return calcPatternSlow(execution, controller, craftingJobId, false, tick);
        }

        // One lookup, one value verification. The status tells the three non-usable cases apart without a
        // second map probe or a second comparison.
        ECOFastPathLookup lookup = cache.lookup(execution, tick, AE2PatternIntrospection.reloadGeneration());
        switch (lookup.status()) {
            case NEGATIVE -> {
                fastPathReason = lookup.reason() == null ? "NEGATIVE_CACHE" : lookup.reason();
                cache.recordFallbackSlowPath();
                return calcPatternSlow(execution, controller, craftingJobId, false, tick);
            }
            case MISMATCH -> {
                fastPathReason = lookup.reason() == null ? "CACHE_RESULT_MISMATCH" : lookup.reason();
                cache.putNegative(key, tick);
                cache.recordFallbackSlowPath();
                return calcPatternSlow(execution, controller, craftingJobId, false, tick);
            }
            case VERIFIED -> {
                ECOVerifiedFastPathRecipe recipe = lookup.recipe();
                if (recipe.hasFluidInput()) {
                    // Single-craft work stores physical ItemStacks for recovery, while batch work can retain
                    // the raw fluid key. Keep the verified cache positive for batch dispatches and verify this
                    // one craft through AE2's normal container-aware assembler path.
                    fastPathReason = "FLUID_INPUT_SINGLE_CRAFT";
                    cache.recordFallbackSlowPath();
                    return calcPatternSlow(execution, controller, craftingJobId, false, tick);
                }
                FastPathWork fastPathWork = createFastPathWork(recipe);
                if (fastPathWork == null) {
                    fastPathReason = "CACHED_RESULT_MATERIALIZATION_FAILED";
                    cache.putNegative(key, tick, "CACHED_RESULT_MATERIALIZATION_FAILED");
                    cache.recordFallbackSlowPath();
                    return calcPatternSlow(execution, controller, craftingJobId, false, tick);
                }
                if (!consumeCraftingCoolant(controller, 1)) {
                    cache.recordCoolantReject();
                    return false;
                }
                cache.recordFastPathAccepted();
                fastPathReason = "FAST_PATH_HIT";
                startWork(
                    List.of(fastPathWork.output()), fastPathWork.inputs(), fastPathWork.remaining(),
                    craftingJobId, 1
                );
                return true;
            }
            default -> {
                fastPathReason = lookup.reason() == null ? "CACHE_MISS" : lookup.reason();
                return calcPatternSlow(execution, controller, craftingJobId, true, tick);
            }
        }
    }

    @Nullable
    private FastPathWork createFastPathWork(ECOVerifiedFastPathRecipe recipe) {
        var output = ECOFastPathStacks.toSingleItemStack(recipe.outputsPerCraft());
        var inputs = ECOFastPathStacks.toItemStacks(recipe.inputsPerCraft());
        var remaining = ECOFastPathStacks.toItemStacks(recipe.remainingPerCraft());
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
        List<ItemStack> beforeSlots = new ArrayList<>();
        for (int slot = 0; slot < craftingInv.getContainerSize(); slot++) beforeSlots.add(craftingInv.getItem(slot).copy());
        var positionedInput = craftingInv.asPositionedCraftInput();
        ItemStack outputItem = pattern.assemble(positionedInput.input(), worker.getLevel());
        if (outputItem.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        if (!consumeCraftingCoolant(controller, 1)) {
            craftingInv.clearContent();
            return false;
        }

        List<ItemStack> remainingSlots = ECOCraftingStateSlots.expandRemainingItems(
            positionedInput,
            pattern.getRemainingItems(positionedInput.input()),
            craftingInv.getWidth(),
            craftingInv.getHeight()
        );
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : remainingSlots) {
            if (!item.isEmpty()) {
                list.add(item.copy());
            }
        }

        List<ItemStack> inputs = snapshotCraftingInputs();
        if (verifyFastPath) {
            verifyAndCacheFastPath(execution, outputItem, inputs, list, beforeSlots, remainingSlots, tick);
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        cache.recordSlowPathAccepted();
        startWork(List.of(outputItem.copy()), inputs, list, craftingJobId, 1);
        return true;
    }

    private void verifyAndCacheFastPath(
        ECOExtractedPatternExecution execution,
        ItemStack outputItem,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        List<ItemStack> beforeSlots,
        List<ItemStack> remainingSlots,
        long tick
    ) {
        ECOFastPathKey key = execution.key();
        if (key == null) {
            return;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        var outputEntries = ECOFastPathStacks.fromItemStack(outputItem);
        var materializedInputEntries = ECOFastPathStacks.fromItemStacks(inputs);
        var remainingEntries = ECOFastPathStacks.fromItemStacks(remaining);
        boolean hasFluidInput = execution.inputItems().stream()
            .anyMatch(stack -> stack.what() instanceof AEFluidKey);
        List<GenericStack> inputEntries = hasFluidInput
            ? execution.inputItems()
            : materializedInputEntries.orElse(List.of());
        if (outputEntries.isEmpty() || inputEntries.isEmpty()) {
            cache.putNegative(key, tick, "VERIFIED_OUTPUT_OR_INPUT_CONVERSION_FAILED");
            return;
        }
        if (!outputEntries.get().equals(execution.expectedOutputs())
            || !remainingEntries.get().equals(execution.expectedContainerItems())
            || (!hasFluidInput && !inputEntries.equals(execution.inputItems()))) {
            cache.putNegative(key, tick, "ASSEMBLY_CONTRACT_MISMATCH");
            return;
        }
        ECOReusableStateAnalyzer.Analysis stateAnalysis =
            ECOReusableStateAnalyzer.analyze(beforeSlots, remainingSlots,
                execution.fastPathType() == ECORecipeClassifier.Type.DURABILITY_MUTATION);
        if (stateAnalysis.rejected()) {
            if ("STATE_SLOT_COUNT_MISMATCH".equals(stateAnalysis.rejectReason())) {
                logFastPathStateSlotMismatch(beforeSlots, remainingSlots);
            }
            cache.putNegative(key, tick, stateAnalysis.rejectReason());
            return;
        }
        if (!verifySecondStateStep(execution, outputItem, beforeSlots, remainingSlots, stateAnalysis.model())) {
            cache.putNegative(key, tick, "STATE_SECOND_STEP_PROOF_FAILED");
            return;
        }
        List<ItemStack> expectedOutputStacks = ECOFastPathStacks.toSingleItemStack(execution.expectedOutputs())
            .map(List::of).orElse(List.of());
        cache.putPositive(key, outputEntries.get(), remainingEntries.get(), inputEntries, tick,
            stateAnalysis.model(),
            execution.fastPathType(),
            ECOFastPathResult.componentChanges(beforeSlots, remainingSlots),
            ECOFastPathResult.componentChanges(expectedOutputStacks, List.of(outputItem)),
            ECOFastPathResult.durabilityDeltas(beforeSlots, remainingSlots),
            ECOFastPathResult.reusableInputs(beforeSlots, remainingSlots));
    }

    private static void logFastPathStateSlotMismatch(
        List<ItemStack> expectedSlots,
        List<ItemStack> actualSlots
    ) {
        LOGGER.warn(
            "Fast path state slot mismatch:\nexpectedSlotCount={}\nactualSlotCount={}\nexpectedSlots:\n{}\nactualSlots:\n{}",
            expectedSlots.size(),
            actualSlots.size(),
            formatFastPathStateSlots(expectedSlots),
            formatFastPathStateSlots(actualSlots)
        );
    }

    private static String formatFastPathStateSlots(List<ItemStack> slots) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < slots.size(); index++) {
            ItemStack stack = slots.get(index);
            result.append("\n  {index=").append(index);
            if (stack == null || stack.isEmpty()) {
                result.append(", AEKey=null, amount=0, component/state=EMPTY}");
            } else {
                result.append(", AEKey=").append(AEItemKey.of(stack))
                    .append(", amount=").append(stack.getCount())
                    .append(", component/state={componentsPatch=").append(stack.getComponentsPatch())
                    .append(", damage=").append(stack.getDamageValue()).append("}}");
            }
        }
        return result.append("\n]").toString();
    }

    private boolean verifySecondStateStep(
        ECOExtractedPatternExecution execution,
        ItemStack firstOutput,
        List<ItemStack> initialSlots,
        List<ItemStack> firstRemainingSlots,
        @Nullable ECOReusableStateModel model
    ) {
        if (model == null || !model.requiresSecondStepProof()) return true;
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null || initialSlots.size() != firstRemainingSlots.size()) return false;
        try {
            for (int slot = 0; slot < initialSlots.size(); slot++) {
                ItemStack initial = initialSlots.get(slot);
                ItemStack firstRemainder = firstRemainingSlots.get(slot);
                craftingInv.setItem(slot,
                    !initial.isEmpty() && !firstRemainder.isEmpty()
                            && ItemStack.isSameItem(initial, firstRemainder)
                        ? firstRemainder.copy()
                        : initial.copy());
            }
            var secondPositionedInput = craftingInv.asPositionedCraftInput();
            ItemStack secondOutput = pattern.assemble(secondPositionedInput.input(), worker.getLevel());
            if (secondOutput.isEmpty() || secondOutput.getCount() != firstOutput.getCount()
                    || !ItemStack.isSameItemSameComponents(firstOutput, secondOutput)) return false;
            List<ItemStack> secondRemaining = ECOCraftingStateSlots.expandRemainingItems(
                secondPositionedInput,
                pattern.getRemainingItems(secondPositionedInput.input()),
                craftingInv.getWidth(),
                craftingInv.getHeight()
            );
            ECOReusableStateAnalyzer.Analysis second = ECOReusableStateAnalyzer.analyze(
                firstRemainingSlots, secondRemaining,
                execution.fastPathType() == ECORecipeClassifier.Type.DURABILITY_MUTATION);
            return !second.rejected() && second.model() != null && model.sameTransition(second.model());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private boolean consumeCraftingCoolant(ECOCraftingSystemBlockEntity controller, int craftCount) {
        return !controller.isActiveCooling()
            || controller.usesTickBasedCoolant()
            || controller.tryConsumeCoolant(5 * Math.max(1, craftCount), controller.getEffectiveOverclockTimes());
    }

    private void startWork(
        List<ItemStack> outputs,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        @Nullable UUID craftingJobId,
        int finiteBatchCraftCount
    ) {
        outputItems.clear();
        copyStacks(outputs, outputItems);
        this.craftingJobId = craftingJobId;
        this.finiteBatchCraftCount = Math.max(1, finiteBatchCraftCount);
        this.craftCount = this.finiteBatchCraftCount;
        this.virtualBatch = false;
        this.progressRemainder = 0.0D;
        this.outputsReady = false;
        inputItems.clear();
        copyStacks(inputs, inputItems);
        remainingItems.clear();
        copyStacks(remaining, remainingItems);
        batchOutputItems.clear();
        batchInputItems.clear();
        batchRemainingItems.clear();
        craftingEventOutput = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
        try {
            worker.onBatchStarted();
            recoveryState = RecoveryState.ACTIVE;
            reboot = true;
            isBusy = true;
        } catch (RuntimeException | Error e) {
            // Error is included so partially installed work is cleared before the failure escapes.
            clearWork();
            throw e;
        }
    }

    private void startBatchWork(
        List<GenericStack> outputs,
        List<GenericStack> inputs,
        List<GenericStack> remaining,
        @Nullable UUID craftingJobId,
        int finiteBatchCraftCount
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
        this.finiteBatchCraftCount = Math.max(1, finiteBatchCraftCount);
        this.craftCount = this.finiteBatchCraftCount;
        this.virtualBatch = false;
        this.progressRemainder = 0.0D;
        this.outputsReady = false;
        try {
            worker.onBatchStarted();
            recoveryState = RecoveryState.ACTIVE;
            reboot = true;
            isBusy = true;
        } catch (RuntimeException | Error e) {
            // Error is included so partially installed batch work is cleared before the failure escapes.
            clearWork();
            throw e;
        }
    }

    private void startVirtualWork(ECOVirtualCraftingWork work) {
        outputItems.clear();
        inputItems.clear();
        remainingItems.clear();
        batchOutputItems.clear();
        batchOutputItems.addAll(work.outputTotal());
        batchInputItems.clear();
        batchInputItems.addAll(work.inputTotal());
        batchRemainingItems.clear();
        batchRemainingItems.addAll(work.remainingTotal());
        craftingEventOutput = ItemStack.EMPTY;
        craftingJobId = work.craftingJobId();
        finiteBatchCraftCount = 1;
        craftCount = work.craftCount();
        virtualBatch = true;
        progress = 0;
        progressRemainder = 0.0D;
        outputsReady = false;
        try {
            worker.onBatchStarted();
            recoveryState = RecoveryState.ACTIVE;
            reboot = true;
            isBusy = true;
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
        KeyCounter outputs = collectOutputItems();

        KeyCounter remainder = ejectAllAndCollectRemainder(craftingService, storage, outputs);
        if (!isEmpty(remainder)) {
            retainRemainderForRetry(remainder, RecoveryState.ACTIVE);
            logBlockedOutput("network-capacity", remainder);
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
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastBlockedOutputLogTick;
        if (lastBlockedOutputLogTick != Long.MIN_VALUE && elapsed >= 0L
            && elapsed < BLOCKED_OUTPUT_LOG_INTERVAL_TICKS) {
            return;
        }
        lastBlockedOutputLogTick = tick;
        LOGGER.warn(
            "ECO crafting output delivery blocked: worker={} reason={} job={} progress={}/{} "
                + "pending={} batchCrafts={} craftCount={} virtualBatch={}",
            worker.getBlockPos(),
            reason,
            craftingJobId,
            progress,
            MAX_PROGRESS,
            pending == null ? "unknown" : pending,
            finiteBatchCraftCount,
            craftCount,
            virtualBatch
        );
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
        List<GenericStack> pendingEntries = keyCounterToGenericStacks(stacks, !recoverOutputs);
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
            worker.onBatchStopped();
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
        worker.onBatchStopped();
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
        worker.onBatchStopped();
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
        lastBlockedProgressLogTick = Long.MIN_VALUE;
        lastBlockedOutputLogTick = Long.MIN_VALUE;
    }

    private void retainRemainderForRetry(KeyCounter remainder, RecoveryState nextState) {
        List<GenericStack> stacks = keyCounterToGenericStacks(remainder);
        if (stacks.isEmpty() && !isEmpty(remainder)) {
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
        recoveryState = nextState;
        setChanged();
    }

    private void retainInputRemainderForRetry(KeyCounter remainder) {
        List<GenericStack> stacks = keyCounterToGenericStacks(remainder, true);
        if (stacks.isEmpty() && !isEmpty(remainder)) {
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

    private static List<GenericStack> keyCounterToGenericStacks(KeyCounter counter) {
        return keyCounterToGenericStacks(counter, false);
    }

    private static List<GenericStack> keyCounterToGenericStacks(KeyCounter counter, boolean allowFluid) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            if (!(entry.getKey() instanceof AEItemKey)
                && !(allowFluid && entry.getKey() instanceof AEFluidKey)) {
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
        return canRetainGenericStacks(stacks, false);
    }

    private static boolean canRetainGenericStacks(List<GenericStack> stacks, boolean allowFluid) {
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0
                || (!(stack.what() instanceof AEItemKey)
                    && !(allowFluid && stack.what() instanceof AEFluidKey))) {
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
        tag.putBoolean("outputsReady", outputsReady);
        tag.putString("recoveryState", recoveryState.name());
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

    private record FastPathWork(ItemStack output, List<ItemStack> inputs, List<ItemStack> remaining) {}

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
