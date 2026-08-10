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
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.NEFakePlayer;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingWork;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStage;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOReusableCraftingPlan;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
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

    @Nullable private UUID craftingJobId = null;

    private boolean releaseJobOutputsToNetwork = false;

    private int progress = 0;
    private double progressRemainder = 0.0D;
    private int occupiedThreadSlots = 1;
    private int assignedLaneIndex = -1;
    private int networkCoolingMultiplier = 1;
    private boolean outputsReady = false;
    private boolean virtualCrafting = false;
    private RecoveryState recoveryState = RecoveryState.CLEARED;
    private long lastEjectionFailureLogTick = Long.MIN_VALUE;
    private long lastRecoveryFailureLogTick = Long.MIN_VALUE;

    private final TransientCraftingContainer craftingInv;

    public ECOCraftingThread(ECOCraftingWorkerBlockEntity worker) {
        this.worker = worker;
        this.actionSource = IActionSource.ofMachine(worker);
        this.craftingInv = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
    }

    public TickRateModulation tick(
            int overlockTimes,
            int powerMultiply,
            int ticksSinceLastCall,
            boolean fullNetworkPowerMode,
            boolean networkPowerPrepaid) {
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

        if (outputsReady && !virtualCrafting) {
            return ejectOutputsSafely();
        }

        if (fullNetworkPowerMode && !networkPowerPrepaid) {
            return TickRateModulation.URGENT;
        }

        if (networkCoolingMultiplier > 1) {
            ECOCraftingSystemBlockEntity controller =
                    worker.getCluster() == null ? null : worker.getCluster().getController();
            if (controller != null
                    && !controller.tryConsumeNetworkCoolantTick(networkCoolingMultiplier, ticksSinceLastCall)) {
                return TickRateModulation.URGENT;
            }
        }

        if (virtualCrafting) {
            progress = MAX_PROGRESS;
            outputsReady = true;
            setChanged();
            return ejectOutputsSafely();
        }

        int bonusValue = calculateProgressPerTick(overlockTimes);
        if (fullNetworkPowerMode) {
            progressRemainder = 0.0D;
            progress += calculateRequestedProgress(ticksSinceLastCall, bonusValue, MAX_PROGRESS - progress);
        } else {
            progress += userPower(ticksSinceLastCall, bonusValue, powerMultiply, MAX_PROGRESS - progress);
        }

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

    public int getAssignedLaneIndex() {
        return isBusy ? assignedLaneIndex : -1;
    }

    public ItemStack getOutputItem() {
        return firstOutputItem().copy();
    }

    public boolean hasOutput(ItemStack output) {
        if (!isBusy || output.isEmpty()) {
            return false;
        }
        for (ItemStack stack : outputItems) {
            if (ItemStack.isSameItemSameTags(output, stack)) {
                return true;
            }
        }
        for (ItemStack stack : remainingItems) {
            if (ItemStack.isSameItemSameTags(output, stack)) {
                return true;
            }
        }
        for (GenericStack stack : batchOutputItems) {
            if (stack.what() instanceof AEItemKey itemKey && ItemStack.isSameItemSameTags(output, itemKey.toStack(1))) {
                return true;
            }
        }
        for (GenericStack stack : batchRemainingItems) {
            if (stack.what() instanceof AEItemKey itemKey && ItemStack.isSameItemSameTags(output, itemKey.toStack(1))) {
                return true;
            }
        }
        return false;
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
                craftingJobId);
    }

    public boolean pushPattern(
            IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table, ECOCraftingSystemBlockEntity controller) {
        return pushPattern(pattern, table, controller, null);
    }

    public boolean pushPattern(
            IMolecularAssemblerSupportedPattern pattern,
            KeyCounter[] table,
            ECOCraftingSystemBlockEntity controller,
            @Nullable UUID craftingJobId) {
        return pushPattern(ECOExtractedPatternExecution.slow(pattern, table), controller, craftingJobId);
    }

    public boolean pushPattern(
            ECOExtractedPatternExecution execution,
            ECOCraftingSystemBlockEntity controller,
            @Nullable UUID craftingJobId) {
        ECOCraftingSystemBlockEntity.CraftingLane lane = controller.findAvailableCraftingLane(1);
        return lane != null && pushPattern(execution, controller, craftingJobId, lane.index());
    }

    public boolean pushPattern(
            ECOExtractedPatternExecution execution,
            ECOCraftingSystemBlockEntity controller,
            @Nullable UUID craftingJobId,
            int laneIndex) {
        if (isBusy) {
            return false;
        }

        return acceptPattern(execution, controller, craftingJobId, laneIndex);
    }

    public boolean pushBatch(
            ECOBatchCraftingRequest request,
            ECOCraftingSystemBlockEntity controller,
            ECOFastPathResult verifiedResult) {
        int requiredLaneCapacity = controller.isVirtualCraftingMode() ? 1 : Math.toIntExact(request.batchSize());
        ECOCraftingSystemBlockEntity.CraftingLane lane = controller.findAvailableCraftingLane(requiredLaneCapacity);
        return lane != null && pushBatch(request, controller, verifiedResult, lane.index());
    }

    public boolean pushBatch(
            ECOBatchCraftingRequest request,
            ECOCraftingSystemBlockEntity controller,
            ECOFastPathResult verifiedResult,
            int laneIndex) {
        if (isBusy) {
            return false;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        if (!worker.isControlledBy(controller)) {
            cache.recordNoThreadReject();
            return false;
        }
        int controllerAvailableSlots =
                Math.max(0, controller.getLocalThreadCount() - controller.getLocalRunningThreadCount());
        if (worker.getAvailableThreadSlots() <= 0 || controllerAvailableSlots <= 0) {
            cache.recordNoThreadReject();
            return false;
        }
        if (verifiedResult == null || verifiedResult.isNegative() || !verifiedResult.matchesBatchRequest(request)) {
            cache.recordExpectedMismatch();
            return false;
        }
        boolean virtualCrafting = controller.isVirtualCraftingMode();
        var reusablePlan = ECOReusableCraftingPlan.of(verifiedResult.inputEntries(), verifiedResult.remainingEntries());
        var outputTotal = ECOBatchCraftingHelper.multiply(verifiedResult.outputEntries(), request.batchSize());
        var inputTotal = virtualCrafting ? List.<GenericStack>of() : reusablePlan.batchInputs(request.batchSize());
        var remainingTotal = reusablePlan.batchRemaining(request.batchSize());
        var work = new ECOBatchCraftingWork(
                request.batchSize(), inputTotal, outputTotal, remainingTotal, request.craftingJobId(), 0, 1);
        return acceptBatch(work, controller, laneIndex, virtualCrafting);
    }

    private boolean acceptBatch(
            ECOBatchCraftingWork work,
            ECOCraftingSystemBlockEntity controller,
            int laneIndex,
            boolean virtualCrafting) {
        if (!canRetainGenericStacks(work.outputTotal())
                || !canRetainGenericStacks(work.inputTotal())
                || !canRetainGenericStacks(work.remainingTotal())) {
            worker.getFastPathCache().recordNonItemKey();
            return false;
        }
        int coolingMultiplier =
                prepareCraftingCooling(controller, virtualCrafting ? 1 : Math.toIntExact(work.batchSize()));
        if (coolingMultiplier < 0) {
            worker.getFastPathCache().recordCoolantReject();
            return false;
        }
        startBatchWork(
                work.outputTotal(),
                work.inputTotal(),
                work.remainingTotal(),
                work.craftingJobId(),
                work.occupiedThreadSlots(),
                laneIndex,
                coolingMultiplier,
                virtualCrafting);
        worker.getFastPathCache().recordFastPathAccepted();
        return true;
    }

    private boolean acceptPattern(
            ECOExtractedPatternExecution execution,
            ECOCraftingSystemBlockEntity controller,
            @Nullable UUID craftingJobId,
            int laneIndex) {
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        ECOFastPathKey key = execution.key();
        if (!canUseFastPath(execution, key)) {
            cache.recordDisabled();
            logSlowPath(execution, fastPathRejectionReason(execution, key), tick);
            return calcPatternSlow(execution, controller, craftingJobId, false, tick, laneIndex);
        }

        ECOFastPathResult cached = cache.get(key, tick);
        if (cached != null) {
            if (cached.isNegative()) {
                cache.recordFallbackSlowPath();
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.NEGATIVE_CACHE,
                        ECOFastPathStage.CACHE_LOOKUP,
                        worker.getBlockPos(),
                        tick,
                        "negative_cache_entry");
                return calcPatternSlow(execution, controller, craftingJobId, false, tick, laneIndex);
            }
            if (!cached.matchesExecution(execution)) {
                cache.putNegative(key, tick);
                cache.recordFallbackSlowPath();
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.CACHE_ENTRY_MISMATCH,
                        ECOFastPathStage.CACHE_VERIFY,
                        worker.getBlockPos(),
                        tick,
                        "cached_result_does_not_match_extracted_execution");
                return calcPatternSlow(execution, controller, craftingJobId, false, tick, laneIndex);
            }
            FastPathWork fastPathWork = createFastPathWork(cached);
            if (fastPathWork == null) {
                cache.putNegative(key, tick);
                cache.recordFallbackSlowPath();
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.RUNTIME_STACK_CONVERSION_FAILED,
                        ECOFastPathStage.CACHE_VERIFY,
                        worker.getBlockPos(),
                        tick,
                        "cached_generic_stacks_cannot_be_converted_to_item_stacks");
                return calcPatternSlow(execution, controller, craftingJobId, false, tick, laneIndex);
            }
            int coolingMultiplier = prepareCraftingCooling(controller, 1);
            if (coolingMultiplier < 0) {
                cache.recordCoolantReject();
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.COOLANT_LIMIT,
                        ECOFastPathStage.RESOURCE_LIMIT,
                        worker.getBlockPos(),
                        tick,
                        "single_craft_cooling_limit_rejected");
                return false;
            }
            cache.recordFastPathAccepted();
            cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
            startWork(
                    List.of(fastPathWork.output()),
                    fastPathWork.inputs(),
                    fastPathWork.remaining(),
                    craftingJobId,
                    1,
                    laneIndex,
                    coolingMultiplier);
            return true;
        }

        return calcPatternSlow(execution, controller, craftingJobId, true, tick, laneIndex);
    }

    private boolean canUseFastPath(ECOExtractedPatternExecution execution, @Nullable ECOFastPathKey key) {
        return key != null
                && execution.fastPathEligible()
                && NEConfig.isEcoAe2FastPathEnabled()
                && !NEConfig.postCraftingEvent;
    }

    private ECOFastPathFallbackReason fastPathRejectionReason(
            ECOExtractedPatternExecution execution, @Nullable ECOFastPathKey key) {
        if (!NEConfig.isEcoAe2FastPathEnabled()) {
            return ECOFastPathFallbackReason.FAST_PATH_DISABLED;
        }
        if (NEConfig.postCraftingEvent) {
            return ECOFastPathFallbackReason.POST_CRAFTING_EVENT;
        }
        ECOFastPathFallbackReason reason = execution.fallbackReason();
        if (reason != null) {
            return reason;
        }
        return key == null
                ? ECOFastPathFallbackReason.KEY_BUILD_FAILED
                : ECOFastPathFallbackReason.LEGACY_SLOW_EXECUTION;
    }

    private void logSlowPath(ECOExtractedPatternExecution execution, ECOFastPathFallbackReason reason, long tick) {
        ECOFastPathDiagnostics.logSlowPath(execution, reason, worker.getBlockPos(), tick);
    }

    @Nullable private FastPathWork createFastPathWork(ECOFastPathResult cached) {
        var output = ECOFastPathStacks.toSingleItemStack(cached.outputEntries());
        var inputs = ECOFastPathStacks.toItemStacks(cached.inputEntries());
        var remaining = ECOFastPathStacks.toItemStacks(cached.remainingEntries());
        // A recipe may legitimately have no consumed inputs or no crafting-container return
        // items. Optional.empty() means conversion failure; an empty converted list is valid.
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
            long tick,
            int laneIndex) {
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null) {
            return false;
        }
        KeyCounter[] table = execution.craftingContainer();
        craftingInv.clearContent();
        pattern.fillCraftingGrid(table, craftingInv::setItem);
        ItemStack outputItem = pattern.assemble(craftingInv, worker.getLevel());
        if (outputItem.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        int coolingMultiplier = prepareCraftingCooling(controller, 1);
        if (coolingMultiplier < 0) {
            craftingInv.clearContent();
            return false;
        }

        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : pattern.getRemainingItems(craftingInv)) {
            if (!item.isEmpty()) {
                list.add(item.copy());
            }
        }

        List<ItemStack> inputs = snapshotCraftingInputs();
        if (verifyFastPath) {
            if (verifyAndCacheFastPath(execution, outputItem, inputs, list, tick)) {
                logSlowPath(execution, ECOFastPathFallbackReason.CACHE_MISS_VERIFYING, tick);
            }
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        cache.recordSlowPathAccepted();
        cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
        startWork(List.of(outputItem.copy()), inputs, list, craftingJobId, 1, laneIndex, coolingMultiplier);
        return true;
    }

    private boolean verifyAndCacheFastPath(
            ECOExtractedPatternExecution execution,
            ItemStack outputItem,
            List<ItemStack> inputs,
            List<ItemStack> remaining,
            long tick) {
        ECOFastPathKey key = execution.key();
        if (key == null) {
            return false;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        var outputEntries = ECOFastPathStacks.fromItemStack(outputItem);
        var inputEntries = ECOFastPathStacks.fromItemStacks(inputs);
        var remainingEntries = ECOFastPathStacks.fromItemStacks(remaining);
        if (outputEntries.isEmpty() || inputEntries.isEmpty() || remainingEntries.isEmpty()) {
            cache.putNegative(key, tick);
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.RUNTIME_STACK_CONVERSION_FAILED,
                    ECOFastPathStage.CACHE_VERIFY,
                    worker.getBlockPos(),
                    tick,
                    "assembled_output_or_input_or_container_conversion_failed");
            return false;
        }
        if (!outputEntries.get().equals(execution.expectedOutputs())) {
            cache.putNegative(key, tick);
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.OUTPUT_MISMATCH,
                    ECOFastPathStage.CACHE_VERIFY,
                    worker.getBlockPos(),
                    tick,
                    "expected=" + execution.expectedOutputs() + " actual=" + outputEntries.get());
            return false;
        }
        if (!remainingEntries.get().equals(execution.expectedContainerItems())) {
            cache.putNegative(key, tick);
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.CONTAINER_MISMATCH,
                    ECOFastPathStage.CACHE_VERIFY,
                    worker.getBlockPos(),
                    tick,
                    "expected=" + execution.expectedContainerItems() + " actual=" + remainingEntries.get());
            return false;
        }
        if (!inputEntries.get().equals(execution.inputItems())) {
            cache.putNegative(key, tick);
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.INPUT_MISMATCH,
                    ECOFastPathStage.CACHE_VERIFY,
                    worker.getBlockPos(),
                    tick,
                    "expected=" + execution.inputItems() + " actual=" + inputEntries.get());
            return false;
        }
        if (!cache.putPositive(key, outputEntries.get(), remainingEntries.get(), inputEntries.get(), tick)) {
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.CACHE_VALIDATION_REJECTED,
                    ECOFastPathStage.CACHE_VERIFY,
                    worker.getBlockPos(),
                    tick,
                    "cache_entry_failed_item_stack_safety_validation");
            return false;
        }
        return true;
    }

    private int prepareCraftingCooling(ECOCraftingSystemBlockEntity controller, int craftCount) {
        if (!controller.isLocalActiveCooling()) {
            return 1;
        }
        int networkMultiplier = controller.getActiveNetworkCoolingMultiplier();
        if (controller.isVirtualCraftingMode()) {
            int virtualCoolingMode = Math.max(2, networkMultiplier);
            return controller.canStartNetworkCooledTask(virtualCoolingMode) ? virtualCoolingMode : -1;
        }
        if (networkMultiplier > 1) {
            return controller.canStartNetworkCooledTask(networkMultiplier) ? networkMultiplier : -1;
        }
        return controller.tryConsumeCoolant(
                        5 * Math.max(1, craftCount), controller.getCoolingRequirementForCurrentNetwork())
                ? 1
                : -1;
    }

    private void startWork(
            List<ItemStack> outputs,
            List<ItemStack> inputs,
            List<ItemStack> remaining,
            @Nullable UUID craftingJobId,
            int occupiedThreadSlots,
            int laneIndex,
            int networkCoolingMultiplier) {
        outputItems.clear();
        copyStacks(outputs, outputItems);
        this.craftingJobId = craftingJobId;
        this.occupiedThreadSlots = Math.max(1, occupiedThreadSlots);
        this.assignedLaneIndex = laneIndex;
        this.networkCoolingMultiplier = networkCoolingMultiplier;
        this.progressRemainder = 0.0D;
        this.outputsReady = false;
        inputItems.clear();
        copyStacks(inputs, inputItems);
        remainingItems.clear();
        copyStacks(remaining, remainingItems);
        batchOutputItems.clear();
        batchInputItems.clear();
        batchRemainingItems.clear();
        craftingEventOutput =
                outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
        try {
            worker.onThreadWork(this.occupiedThreadSlots);
            recoveryState = RecoveryState.ACTIVE;
            reboot = true;
            isBusy = true;
        } catch (RuntimeException | Error e) {
            clearWork();
            throw e;
        }
    }

    private void startBatchWork(
            List<GenericStack> outputs,
            List<GenericStack> inputs,
            List<GenericStack> remaining,
            @Nullable UUID craftingJobId,
            int occupiedThreadSlots,
            int laneIndex,
            int networkCoolingMultiplier,
            boolean virtualCrafting) {
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
        this.assignedLaneIndex = laneIndex;
        this.networkCoolingMultiplier = networkCoolingMultiplier;
        this.progressRemainder = 0.0D;
        this.virtualCrafting = virtualCrafting;
        this.outputsReady = virtualCrafting;
        try {
            worker.onThreadWork(this.occupiedThreadSlots);
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

    private int userPower(int ticksPassed, int bonusValue, double acceleratorTax, int remainingProgress) {
        var grid = this.worker.getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }

        int requestedProgress = calculateRequestedProgress(ticksPassed, bonusValue, remainingProgress);
        double powerPerProgress = calculatePowerPerProgress(acceleratorTax, occupiedThreadSlots);
        if (requestedProgress <= 0 || powerPerProgress <= 0.0D) {
            return 0;
        }

        double requestedPower = Math.max(0.0D, requestedProgress - progressRemainder) * powerPerProgress;
        if (!Double.isFinite(requestedPower) || requestedPower <= 0.0D) {
            return 0;
        }
        double extractedPower =
                grid.getEnergyService().extractAEPower(requestedPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        PowerProgress powered =
                accumulatePoweredProgress(extractedPower, powerPerProgress, requestedProgress, progressRemainder);
        progressRemainder = powered.remainder();
        return powered.completed();
    }

    static int calculateProgressPerTick(int overclockTimes) {
        return Mth.clamp(10 + Math.max(0, overclockTimes) * 10, 10, MAX_PROGRESS);
    }

    static int calculateRequestedProgress(int ticksPassed, int bonusValue, int remainingProgress) {
        long requested = (long) Math.max(0, ticksPassed) * Math.max(0, bonusValue);
        return (int) Math.min(Math.max(0, remainingProgress), Math.min(Integer.MAX_VALUE, requested));
    }

    static double calculatePowerPerProgress(double acceleratorTax, int occupiedThreadSlots) {
        if (!Double.isFinite(acceleratorTax) || acceleratorTax <= 0.0D) {
            return 0.0D;
        }
        return acceleratorTax * Math.max(1, occupiedThreadSlots);
    }

    static int calculatePoweredProgress(double extractedPower, double powerPerProgress, int requestedProgress) {
        return accumulatePoweredProgress(extractedPower, powerPerProgress, requestedProgress, 0.0D)
                .completed();
    }

    static PowerProgress accumulatePoweredProgress(
            double extractedPower, double powerPerProgress, int requestedProgress, double previousRemainder) {
        double safeRemainder =
                Double.isFinite(previousRemainder) && previousRemainder >= 0.0D && previousRemainder < 1.0D
                        ? previousRemainder
                        : 0.0D;
        if (!Double.isFinite(extractedPower)
                || extractedPower <= 0.0D
                || !Double.isFinite(powerPerProgress)
                || powerPerProgress <= 0.0D
                || requestedProgress <= 0) {
            return new PowerProgress(0, safeRemainder);
        }
        double fundedProgress = Math.min(requestedProgress, safeRemainder + extractedPower / powerPerProgress);
        int completed = (int) Math.min(requestedProgress, Math.floor(fundedProgress + 1.0E-9D));
        double remainder = completed >= requestedProgress
                ? 0.0D
                : Math.max(0.0D, Math.min(Math.nextDown(1.0D), fundedProgress - completed));
        return new PowerProgress(completed, remainder);
    }

    record PowerProgress(int completed, double remainder) {}

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

    private KeyCounter ejectAllAndCollectRemainder(
            CraftingService craftingService, MEStorage storage, KeyCounter stacks) {
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
            if (craftingJobId != null && !releaseJobOutputsToNetwork) {
                // A forming/reforming computation host can be absent from CraftingService's transient CPU list.
                // Prefer the exact owning CPU, then retain AE2's normal CPU/storage fallback for any remainder.
                // The AE2 CPU insert contract checks its own waitingFor inventory, so this cannot route an
                // output to an unrelated CPU.
                ECOCraftingCPULogic.JobOutputDelivery delivery =
                        ECOCraftingCPULogic.deliverJobOutput(craftingJobId, key, remaining, Actionable.MODULATE);
                long remainingAfterOwner =
                        remainingAfterOwnerDelivery(delivery.routeAvailable(), remaining, delivery.inserted());
                long delivered = remaining - remainingAfterOwner;
                if (delivered > 0L) {
                    remaining = remainingAfterOwner;
                    removePendingOutput(stacks, key, delivered);
                }
            }

            if (remaining > 0L) {
                long insertedIntoCpus = validateInsertionAmount(
                        craftingService.insertIntoCpus(key, remaining, Actionable.MODULATE),
                        remaining,
                        "crafting CPUs");
                if (insertedIntoCpus > 0L) {
                    remaining -= insertedIntoCpus;
                    removePendingOutput(stacks, key, insertedIntoCpus);
                }
            }
            if (remaining > 0L) {
                long insertedIntoStorage = validateInsertionAmount(
                        storage.insert(key, remaining, Actionable.MODULATE, actionSource),
                        remaining,
                        "network storage");
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
                    "Invalid insertion result from " + target + ": " + inserted + " for " + requested);
        }
        return inserted;
    }

    /** Returns the amount that must continue through the normal AE2 fallback route. */
    static long remainingAfterOwnerDelivery(boolean routeAvailable, long requested, long inserted) {
        if (requested < 0L) {
            throw new IllegalArgumentException("requested output amount must be non-negative");
        }
        if (!routeAvailable) {
            return requested;
        }
        return requested - validateInsertionAmount(inserted, requested, "owning crafting CPU");
    }

    private KeyCounter insertAllAndCollectRemainder(MEStorage storage, KeyCounter stacks, boolean recoverOutputs) {
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
                    "network recovery storage");
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

    public void releaseJobOutputsToNetwork(UUID jobId) {
        if (belongsToJob(jobId) && !releaseJobOutputsToNetwork) {
            releaseJobOutputsToNetwork = true;
            setChanged();
        }
    }

    public boolean recoverInputsToNetwork(MEStorage storage) {
        if (!isRecoverableState()) {
            return true;
        }
        return recoverItemsToNetwork(storage, shouldRecoverOutputs());
    }

    /** Compatibility hook used by the retained 1.20.1 orphan-work sweep. */
    public boolean recoverOrphanedWorkToNetwork(Set<UUID> activeJobIds, MEStorage storage) {
        if (!isBusy || craftingJobId == null || activeJobIds.contains(craftingJobId)) {
            return true;
        }
        return recoverInputsToNetwork(storage);
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
        return recoveryState == RecoveryState.RECOVERING_INPUTS || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
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
        releaseJobOutputsToNetwork = false;
        isBusy = false;
        reboot = true;
        progress = 0;
        progressRemainder = 0.0D;
        occupiedThreadSlots = 1;
        assignedLaneIndex = -1;
        networkCoolingMultiplier = 1;
        outputsReady = false;
        virtualCrafting = false;
        recoveryState = RecoveryState.CLEARED;
    }

    private void retainRemainderForRetry(KeyCounter remainder, RecoveryState nextState) {
        List<GenericStack> stacks = keyCounterToGenericStacks(remainder);
        if (stacks.isEmpty() && !isEmpty(remainder)) {
            LOGGER.error(
                    "ECO crafting thread cannot retain non-item output remainder for retry: worker={}",
                    worker.getBlockPos());
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
                    worker.getBlockPos());
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
        if (stack == null
                || stack.amount() <= 0
                || stack.amount() > Integer.MAX_VALUE
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
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(
                    NEFakePlayer.getFakePlayer((ServerLevel) worker.getLevel()), craftedOutput, craftingInv));
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
                amount = saturatingOutputAmount(amount, stack.getCount());
            }
        }
        for (GenericStack stack : batchOutputItems) {
            if (stack != null && stack.amount() > 0) {
                amount = saturatingOutputAmount(amount, stack.amount());
            }
        }
        return Math.max(1L, amount);
    }

    private static long saturatingOutputAmount(long current, long added) {
        return added > 0L && current > Long.MAX_VALUE - added ? Long.MAX_VALUE : current + Math.max(0L, added);
    }

    public int getOccupiedThreadSlots() {
        return isBusy ? Math.max(1, occupiedThreadSlots) : 0;
    }

    private void setChanged() {
        worker.setChanged();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        boolean batchGenericWork =
                !batchOutputItems.isEmpty() || !batchInputItems.isEmpty() || !batchRemainingItems.isEmpty();
        tag.putBoolean("isBusy", isBusy);
        tag.putBoolean("reboot", reboot);
        tag.putInt("progress", progress);
        writeProgressRemainder(tag, progressRemainder);
        tag.putInt("neoecoae_version", 4);
        tag.putInt("occupiedThreadSlots", occupiedThreadSlots);
        if (assignedLaneIndex >= 0) {
            tag.putInt("assignedLaneIndex", assignedLaneIndex);
        }
        if (networkCoolingMultiplier > 1) {
            tag.putInt("networkCoolingMultiplier", networkCoolingMultiplier);
        }
        tag.putBoolean("outputsReady", outputsReady);
        tag.putBoolean("virtualCrafting", virtualCrafting);
        tag.putString("recoveryState", recoveryState.name());
        if (craftingJobId != null) {
            tag.putUUID("craftingJobId", craftingJobId);
        }
        tag.putBoolean("releaseJobOutputsToNetwork", releaseJobOutputsToNetwork);
        if (!craftingEventOutput.isEmpty()) {
            tag.put("craftingEventOutput", saveSerializableStack(craftingEventOutput));
        }
        if (batchGenericWork) {
            tag.putBoolean("batchGenericWork", true);
            tag.put("batchOutputItems", ECOFastPathStacks.writeGenericStacks(batchOutputItems));
            tag.put("batchInputItems", ECOFastPathStacks.writeGenericStacks(batchInputItems));
            tag.put("batchRemainingItems", ECOFastPathStacks.writeGenericStacks(batchRemainingItems));
        } else {
            tag.put("outputItem", saveSerializableStack(firstOutputItem()));
        }

        ListTag outputs = new ListTag();
        saveSerializableStacks(outputItems, outputs);
        tag.put("outputItems", outputs);

        ListTag inputs = new ListTag();
        saveSerializableStacks(inputItems, inputs);
        tag.put("inputItems", inputs);

        ListTag remaining = new ListTag();
        saveSerializableStacks(remainingItems, remaining);
        tag.put("remainingItems", remaining);
        return tag;
    }

    private static Tag saveSerializableStack(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() <= MAX_SERIALIZED_ITEM_STACK_COUNT) {
            return stack.save(new CompoundTag());
        }
        return stack.copyWithCount(MAX_SERIALIZED_ITEM_STACK_COUNT).save(new CompoundTag());
    }

    private static void saveSerializableStacks(List<ItemStack> stacks, ListTag tag) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copySerializableStacks(stack, tag);
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

    private static void copySerializableStacks(ItemStack stack, ListTag tag) {
        int remaining = stack.getCount();
        while (remaining > 0) {
            int count = Math.min(remaining, MAX_SERIALIZED_ITEM_STACK_COUNT);
            tag.add(stack.copyWithCount(count).save(new CompoundTag()));
            remaining -= count;
        }
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.isBusy = nbt.getBoolean("isBusy");
        this.reboot = nbt.getBoolean("reboot");
        int persistedProgress = nbt.getInt("progress");
        int persistedOccupiedThreadSlots = nbt.contains("occupiedThreadSlots") ? nbt.getInt("occupiedThreadSlots") : 1;
        boolean invalidPersistedState = persistedProgress < 0
                || persistedOccupiedThreadSlots <= 0
                || persistedOccupiedThreadSlots > ECOBatchCraftingHelper.MAX_BATCH_SIZE;
        this.progress = Mth.clamp(persistedProgress, 0, MAX_PROGRESS);
        this.progressRemainder = readProgressRemainder(nbt);
        this.occupiedThreadSlots = 1;
        this.assignedLaneIndex = nbt.contains("assignedLaneIndex") ? nbt.getInt("assignedLaneIndex") : -1;
        if (assignedLaneIndex < -1) {
            assignedLaneIndex = -1;
            invalidPersistedState = true;
        }
        this.networkCoolingMultiplier =
                nbt.contains("networkCoolingMultiplier") ? nbt.getInt("networkCoolingMultiplier") : 1;
        if (networkCoolingMultiplier != 1 && networkCoolingMultiplier != 2 && networkCoolingMultiplier != 8) {
            networkCoolingMultiplier = 1;
            invalidPersistedState = true;
        }
        this.outputsReady = nbt.getBoolean("outputsReady");
        this.virtualCrafting = nbt.getBoolean("virtualCrafting");
        this.craftingJobId = nbt.hasUUID("craftingJobId") ? nbt.getUUID("craftingJobId") : null;
        this.releaseJobOutputsToNetwork = nbt.getBoolean("releaseJobOutputsToNetwork");
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
                    ItemStack output = ItemStack.of(outputs.getCompound(i));
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
                ItemStack output = ItemStack.of(nbt.getCompound("outputItem"));
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
                ItemStack input = ItemStack.of(inputs.getCompound(i));
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
                ItemStack remainingItem = ItemStack.of(remaining.getCompound(i));
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
                    nbt.getList("batchOutputItems", Tag.TAG_COMPOUND), !recoveringInputs);
            var batchInputs = ECOFastPathStacks.readValidatedBatchItemStacks(
                    nbt.getList("batchInputItems", Tag.TAG_COMPOUND), recoveringInputs);
            var batchRemaining = ECOFastPathStacks.readValidatedBatchItemStacks(
                    nbt.getList("batchRemainingItems", Tag.TAG_COMPOUND), false);
            batchOutputs.ifPresent(batchOutputItems::addAll);
            batchInputs.ifPresent(batchInputItems::addAll);
            batchRemaining.ifPresent(batchRemainingItems::addAll);
            invalidPersistedState |= batchOutputs.isEmpty() || batchInputs.isEmpty() || batchRemaining.isEmpty();
        }
        try {
            craftingEventOutput = ItemStack.of(nbt.getCompound("craftingEventOutput"));
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
            invalidPersistedState |=
                    recoveryState == RecoveryState.RECOVERING_INPUTS ? inputItems.isEmpty() : outputItems.isEmpty();
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
                recoverOutputs);
        progress = 0;
        progressRemainder = 0.0D;
        reboot = true;
        if (recoverOutputs) {
            inputItems.clear();
            batchInputItems.clear();
            outputsReady = true;
            recoveryState = RecoveryState.RECOVERING_OUTPUTS;
            if (outputItems.isEmpty()
                    && remainingItems.isEmpty()
                    && batchOutputItems.isEmpty()
                    && batchRemainingItems.isEmpty()) {
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

    static void writeProgressRemainder(CompoundTag tag, double remainder) {
        double safeRemainder = sanitizeProgressRemainder(remainder);
        if (safeRemainder > 0.0D) {
            tag.putDouble("progressRemainder", safeRemainder);
        }
    }

    static double readProgressRemainder(CompoundTag tag) {
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
            int occupiedThreadSlots,
            ItemStack outputItem,
            long outputAmount,
            List<ItemStack> remainingItems,
            boolean outputsReady,
            @Nullable UUID craftingJobId) {}
}
