package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import cn.dancingsnow.neoecoae.config.NEConfig;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;

public class ECOCraftingCPULogic {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOCraftingCPULogic.class);
    private static final double POWER_EPSILON = 0.01;
    private static final int LOW_POWER_BACKOFF_TICKS = 2;
    private static final int BUDGET_WARNING_THRESHOLD = 20;
    private static final long BUDGET_WARNING_INTERVAL_TICKS = 200;

    final ECOCraftingCPU cpu;

    /**
     * Current job.
     */
    @Getter
    private ExecutingCraftingJob job = null;
    /**
     * Inventory.
     */
    @Getter
    private final ListCraftingInventory inventory = new ListCraftingInventory(ECOCraftingCPULogic.this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    /**
     * True if the CPU is currently trying to clear its inventory but is not able to.
     */
    @Getter
    private boolean cantStoreItems = false;

    @Getter
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    @Getter
    private boolean markedForDeletion = false;
    private long lastBudgetWarningTick = Long.MIN_VALUE;
    private int consecutiveBudgetExhaustions = 0;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(
        IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        // Already have a job.
        if (this.job != null) return CraftingSubmitResult.CPU_BUSY;
        // Check that the node is active.
        if (!cpu.isActive()) return CraftingSubmitResult.CPU_OFFLINE;
        // Check bytes.
        if (cpu.getAvailableStorage() < plan.bytes()) return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty()) AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // Try to extract required items.
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null) return CraftingSubmitResult.missingIngredient(missingIngredient);

        // Set CPU link and job.
        var playerId = src.player()
            .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
            .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);

        // Crafting Monitor unsupported
        // cpu.updateOutput(plan.finalOutput());
        cpu.markDirty();

        // TODO: post monitor difference?

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        // Non-standalone jobs need another link for the requester, and both links need to be submitted to the cache.
        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);

            var craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);

            return CraftingSubmitResult.successful(linkReq);
        } else {
            return CraftingSubmitResult.successful(null);
        }
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        // Don't tick if we're not active.
        if (!cpu.isActive()) return;
        cantStoreItems = false;
        // If we don't have a job, just try to dump our items.
        if (this.job == null) {
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            } else {
                if (markedForDeletion) {
                    cpu.deactivate();
                }
            }
            return;
        }
        // Check if the job was cancelled.
        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        // Don't schedule more work while suspended
        if (job.suspended) {
            return;
        }

        var currentTick = TickHandler.instance().getCurrentTick();
        if (job.lowPowerUntilTick > currentTick) {
            return;
        }

        var budget = createTickBudget();
        var result = executeCrafting(budget, cc, eg, cpu.getLevel(), currentTick);
        handleTickResult(result, budget, currentTick);
    }

    /**
     * Try to push patterns into available interfaces, i.e. do the actual crafting execution.
     *
     * @return How many patterns were successfully pushed.
     */
    private CraftingTickResult executeCrafting(
        CraftingTickBudget budget, CraftingService craftingService, IEnergyService energyService, Level level, long currentTick) {
        var job = this.job;
        if (job == null) return new CraftingTickResult(0, ExhaustionReason.NONE);
        if (job.tasks.isEmpty()) {
            job.nextTaskIndex = 0;
            return new CraftingTickResult(0, ExhaustionReason.NONE);
        }

        var initialTaskCount = job.tasks.size();
        var startTaskIndex = normalizeIndex(job.nextTaskIndex, initialTaskCount);
        var result = processTaskRange(
            job,
            startTaskIndex,
            initialTaskCount,
            budget,
            craftingService,
            energyService,
            level,
            currentTick);

        if (result.exhaustionReason == ExhaustionReason.NONE && startTaskIndex > 0 && budget.canContinue()) {
            var wrappedResult = processTaskRange(
                job,
                0,
                Math.min(startTaskIndex, job.tasks.size()),
                budget,
                craftingService,
                energyService,
                level,
                currentTick);
            result = result.merge(wrappedResult);
        }

        if (job.tasks.isEmpty()) {
            job.nextTaskIndex = 0;
        } else {
            job.nextTaskIndex = normalizeIndex(job.nextTaskIndex, job.tasks.size());
        }
        return result;
    }

    /**
     * Called by the CraftingService with an Integer.MAX_VALUE priority to inject items that are being waited for.
     *
     * @return Consumed amount.
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // also stop accepting items when the job is complete, i.e. to prevent re-insertion when pushing out
        // items during storeItems
        if (what == null || job == null) return 0;

        // Only accept items we are waiting for.
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // Make sure we don't insert more than what we are waiting for.
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE) {
            job.timeTracker.decrementItems(amount, what.getType());
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            cpu.markDirty();
        }

        long inserted = amount;
        if (what.matches(job.finalOutput)) {
            // Final output is special: it goes directly into the requester
            inserted = job.link.insert(what, amount, type);

            // Note: we ignore any remainder (could be the entire input if there is no requester),
            // we already marked the items as done, and we might even finish the job.

            // This means that the job can be marked as finished even if some items were not actually inserted.
            // In some cases, repeated failed inserts of a fraction of the final output might prevent some recipes from
            // being pushed.
            // TODO: Look into fixing this, perhaps we could use the network monitor to check how much was really
            // TODO: inserted into the network.
            // TODO: Another solution is to wait until all recipes have been pushed before cancelling the job.

            if (type == Actionable.MODULATE) {
                // Update count and displayed CPU stack, and finish the job if possible.
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - amount);

                if (job.remainingAmount <= 0) {
                    finishJob(true);
                }
            }
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
            }
        }

        return inserted;
    }

    /**
     * Finish the current job.
     *
     * @param success True if the job is complete, false if it was cancelled.
     */
    private void finishJob(boolean success) {
        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: log

        // Clear waitingFor list and post all the relevant changes.
        job.waitingFor.clear();
        // Notify opened menus of cancelled scheduled tasks.
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(
            job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // Finish job.
        this.job = null;

        // Store all remaining items.
        this.storeItems();
    }

    /**
     * Cancel the current job.
     */
    public void cancel() {
        // No job to cancel :P
        if (job == null) return;

        finishJob(false);
    }

    /**
     * Tries to dump all locally stored items back into the storage network.
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        // Short-circuit if there is nothing to do.
        if (this.inventory.list.isEmpty()) return;

        var g = cpu.getGrid();
        if (g == null) return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            this.postChange(entry.getKey());
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, cpu.getActionSource());

            // The network was unable to receive all of the items, i.e. no or not enough storage space left
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        cpu.markDirty();
    }

    private void postChange(AEKey what) {
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    public boolean hasJob() {
        return this.job != null;
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    public ElapsedTimeTracker getElapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        } else {
            return new ElapsedTimeTracker();
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
        }
    }

    public ICraftingLink getLastLink() {
        if (this.job != null) {
            return this.job.link;
        }
        return null;
    }

    /**
     * Register a listener that will receive stacks when either the stored items, await items or pending outputs change.
     * This is only used by the menu. Make sure to remove it by calling {@link #removeListener}.
     */
    public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener);
    }

    public long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long getWaitingFor(AEKey template) {
        if (this.job != null) {
            return this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        }
        return 0;
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job != null) {
            for (var entry : this.job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    public long getPendingOutputs(AEKey template) {
        long count = 0;
        if (this.job != null) {
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    if (template.matches(output)) {
                        count += output.amount() * t.getValue().value;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Used by the menu to gather all the kinds of stored items.
     */
    public void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job != null) {
            out.addAll(job.waitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    out.add(output.what(), output.amount() * t.getValue().value);
                }
            }
        }
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        var server = cpu.getLevel().getServer();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            ClientboundPacket message = new CraftingJobStatusPacket(
                jobId, job.finalOutput.what(), job.finalOutput.amount(), job.remainingAmount, status);
            connectedPlayer.connection.send(message);
        }
    }

    public void markForDeletion() {
        this.markedForDeletion = true;
    }

    private CraftingTickBudget createTickBudget() {
        var coProcessors = Math.max(0, cpu.getCoProcessors());
        var effectiveCoProcessors = Math.min(coProcessors, NEConfig.ecoCraftingEffectiveCoProcessorCap);
        var scaledBonus = (int) Math.sqrt((double) effectiveCoProcessors);
        var maxOperations = Math.max(1, NEConfig.ecoCraftingMaxOperationsPerTick);
        var maxPatterns = Math.max(1, Math.min(Math.min(NEConfig.ecoCraftingMaxPatternsPerTick, 1 + scaledBonus), maxOperations));
        var maxProviderChecks = Math.max(maxPatterns, NEConfig.ecoCraftingMaxProviderChecksPerTick);
        return new CraftingTickBudget(
            maxOperations,
            maxPatterns,
            maxProviderChecks,
            System.nanoTime() + NEConfig.ecoCraftingTimeBudgetNanos,
            effectiveCoProcessors);
    }

    private CraftingTickResult processTaskRange(
        ExecutingCraftingJob job,
        int startInclusive,
        int endExclusive,
        CraftingTickBudget budget,
        CraftingService craftingService,
        IEnergyService energyService,
        Level level,
        long currentTick
    ) {
        var pushedPatterns = 0;
        var exhaustionReason = ExhaustionReason.NONE;
        var iterator = job.tasks.entrySet().iterator();
        for (int skipped = 0; skipped < startInclusive && iterator.hasNext(); skipped++) {
            iterator.next();
        }

        var taskIndex = startInclusive;
        while (iterator.hasNext() && taskIndex < endExclusive) {
            if (!budget.canContinue()) {
                exhaustionReason = budget.getExhaustionReason();
                break;
            }

            var task = iterator.next();
            var progress = task.getValue();
            if (progress.value <= 0) {
                iterator.remove();
                continue;
            }

            var attempt = tryPushPattern(task, budget, craftingService, energyService, level, currentTick);
            pushedPatterns += attempt.pushedPatterns;

            if (progress.value <= 0) {
                iterator.remove();
            }

            if (attempt.advanceTaskCursor) {
                job.nextTaskIndex = progress.value > 0 ? taskIndex + 1 : taskIndex;
            } else {
                job.nextTaskIndex = taskIndex;
            }

            if (attempt.exhaustionReason != ExhaustionReason.NONE) {
                exhaustionReason = attempt.exhaustionReason;
                break;
            }

            taskIndex++;
        }

        return new CraftingTickResult(pushedPatterns, exhaustionReason);
    }

    private PatternPushAttempt tryPushPattern(
        Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress> task,
        CraftingTickBudget budget,
        CraftingService craftingService,
        IEnergyService energyService,
        Level level,
        long currentTick
    ) {
        if (!budget.tryConsumeOperation()) {
            return PatternPushAttempt.exhausted(budget.getExhaustionReason());
        }

        var details = task.getKey();
        var progress = task.getValue();
        var expectedOutputs = new KeyCounter();
        var expectedContainerItems = new KeyCounter();
        @Nullable
        var craftingContainer = CraftingCpuHelper.extractPatternInputs(
            details, inventory, level, expectedOutputs, expectedContainerItems);

        if (craftingContainer == null) {
            progress.nextProviderIndex = 0;
            return PatternPushAttempt.noProgress(true);
        }

        if (!budget.tryConsumePatternAttempt()) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            return PatternPushAttempt.exhausted(budget.getExhaustionReason());
        }

        var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
        budget.recordEnergySimulation();
        if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
            < patternPower - POWER_EPSILON) {
            job.lowPowerUntilTick = currentTick + LOW_POWER_BACKOFF_TICKS;
            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            return new PatternPushAttempt(0, ExhaustionReason.LOW_POWER, false);
        }

        var remainingProviderChecks = budget.getRemainingProviderChecks();
        if (remainingProviderChecks <= 0) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            return PatternPushAttempt.exhausted(budget.getExhaustionReason());
        }

        int enumeratedProviders = 0;
        int deferredProviderCount = Math.min(Math.max(0, progress.nextProviderIndex), Math.max(0, remainingProviderChecks - 1));
        var deferredProviders = new java.util.ArrayList<appeng.api.networking.crafting.ICraftingProvider>(
            Math.min(deferredProviderCount, budget.getRemainingProviderChecks()));
        boolean providerBudgetExhausted = false;
        for (var provider : craftingService.getProviders(details)) {
            if (!budget.tryConsumeProviderCheck()) {
                providerBudgetExhausted = true;
                break;
            }

            enumeratedProviders++;
            if (deferredProviderCount > 0) {
                deferredProviders.add(provider);
                deferredProviderCount--;
                continue;
            }

            if (provider == null || provider.isBusy()) {
                continue;
            }

            if (provider.pushPattern(details, craftingContainer)) {
                job.lowPowerUntilTick = 0;
                // The provider has already accepted the pattern, so this path cannot roll the dispatch back.
                // If actual extraction underflows after a successful simulation on the same server-thread tick,
                // suspend further dispatch and surface it loudly instead of silently continuing free pushes.
                var extractedPower = energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                if (extractedPower < patternPower - POWER_EPSILON) {
                    LOGGER.error(
                        "ECO crafting CPU at {} in {} accepted a pattern after power simulation, but actual extraction underflowed (required={}, extracted={}). Suspending further dispatch.",
                        formatCpuPosition(),
                        cpu.getLevel().dimension().location(),
                        patternPower,
                        extractedPower);
                    job.suspended = true;
                    job.lowPowerUntilTick = currentTick + LOW_POWER_BACKOFF_TICKS;
                }
                finishPatternPush(expectedOutputs, expectedContainerItems);
                progress.value--;
                progress.nextProviderIndex = enumeratedProviders;
                cpu.markDirty();
                return new PatternPushAttempt(1, ExhaustionReason.NONE, true);
            }
        }

        if (enumeratedProviders == 0) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            return PatternPushAttempt.noProgress(true);
        }

        for (int deferredIndex = 0; deferredIndex < deferredProviders.size(); deferredIndex++) {
            var provider = deferredProviders.get(deferredIndex);
            if (provider == null || provider.isBusy()) {
                continue;
            }

            if (provider.pushPattern(details, craftingContainer)) {
                job.lowPowerUntilTick = 0;
                var extractedPower = energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                if (extractedPower < patternPower - POWER_EPSILON) {
                    LOGGER.error(
                        "ECO crafting CPU at {} in {} accepted a pattern after power simulation, but actual extraction underflowed (required={}, extracted={}). Suspending further dispatch.",
                        formatCpuPosition(),
                        cpu.getLevel().dimension().location(),
                        patternPower,
                        extractedPower);
                    job.suspended = true;
                    job.lowPowerUntilTick = currentTick + LOW_POWER_BACKOFF_TICKS;
                }
                finishPatternPush(expectedOutputs, expectedContainerItems);
                progress.value--;
                progress.nextProviderIndex = normalizeIndex(deferredIndex + 1, enumeratedProviders);
                cpu.markDirty();
                return new PatternPushAttempt(1, ExhaustionReason.NONE, true);
            }
        }

        if (providerBudgetExhausted) {
            progress.nextProviderIndex = normalizeIndex(deferredProviders.size() + 1, enumeratedProviders);
            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            return PatternPushAttempt.exhausted(budget.getExhaustionReason());
        }

        progress.nextProviderIndex = normalizeIndex(progress.nextProviderIndex + 1, enumeratedProviders);
        CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
        return PatternPushAttempt.noProgress(true);
    }

    private void finishPatternPush(KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
        var job = this.job;
        if (job == null) {
            return;
        }

        for (var expectedOutput : expectedOutputs) {
            job.waitingFor.insert(expectedOutput.getKey(), expectedOutput.getLongValue(), Actionable.MODULATE);
        }
        for (var expectedContainerItem : expectedContainerItems) {
            job.waitingFor.insert(
                expectedContainerItem.getKey(),
                expectedContainerItem.getLongValue(),
                Actionable.MODULATE);
            job.timeTracker.addMaxItems(
                expectedContainerItem.getLongValue(),
                expectedContainerItem.getKey().getType());
        }
    }

    private void handleTickResult(CraftingTickResult result, CraftingTickBudget budget, long currentTick) {
        if (result.exhaustionReason == ExhaustionReason.NONE) {
            consecutiveBudgetExhaustions = 0;
            return;
        }

        if (result.exhaustionReason == ExhaustionReason.LOW_POWER) {
            consecutiveBudgetExhaustions = 0;
            if (NEConfig.ecoCraftingDebugProfiling) {
                LOGGER.info(
                    "ECO crafting CPU throttled for low power at {} in {} (queue={}, coProcessors={}, effectiveCoProcessors={}, providerChecks={}, energySimulations={})",
                    formatCpuPosition(),
                    cpu.getLevel().dimension().location(),
                    job != null ? job.tasks.size() : 0,
                    Math.max(0, cpu.getCoProcessors()),
                    budget.effectiveCoProcessors,
                    budget.providerChecks,
                    budget.energySimulations);
            }
            return;
        }

        consecutiveBudgetExhaustions++;
        if (NEConfig.ecoCraftingDebugProfiling) {
            LOGGER.info(
                "ECO crafting CPU deferred work at {} in {} (reason={}, queue={}, coProcessors={}, effectiveCoProcessors={}, pushedPatterns={}, operations={}, providerChecks={}, energySimulations={})",
                formatCpuPosition(),
                cpu.getLevel().dimension().location(),
                result.exhaustionReason.name(),
                job != null ? job.tasks.size() : 0,
                Math.max(0, cpu.getCoProcessors()),
                budget.effectiveCoProcessors,
                result.pushedPatterns,
                budget.operations,
                budget.providerChecks,
                budget.energySimulations);
        }

        if (consecutiveBudgetExhaustions >= BUDGET_WARNING_THRESHOLD
            && currentTick - lastBudgetWarningTick >= BUDGET_WARNING_INTERVAL_TICKS) {
            lastBudgetWarningTick = currentTick;
            LOGGER.warn(
                "ECO crafting CPU at {} in {} repeatedly hit per-tick limits (reason={}, queue={}, effectiveCoProcessors={}, providerChecks={}, pushedPatterns={}).",
                formatCpuPosition(),
                cpu.getLevel().dimension().location(),
                result.exhaustionReason.name(),
                job != null ? job.tasks.size() : 0,
                budget.effectiveCoProcessors,
                budget.providerChecks,
                result.pushedPatterns);
        }
    }

    private String formatCpuPosition() {
        return cpu.getOwner() != null ? cpu.getOwner().getBlockPos().toShortString() : "<virtual>";
    }

    private static int normalizeIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.floorMod(index, size);
    }

    private enum ExhaustionReason {
        NONE,
        OPERATION_LIMIT,
        PATTERN_LIMIT,
        PROVIDER_CHECK_LIMIT,
        TIME_LIMIT,
        LOW_POWER
    }

    private static final class CraftingTickBudget {
        private final int maxOperations;
        private final int maxPatterns;
        private final int maxProviderChecks;
        private final long deadlineNanos;
        private final int effectiveCoProcessors;
        private int operations;
        private int patternAttempts;
        private int providerChecks;
        private int energySimulations;

        private CraftingTickBudget(
            int maxOperations,
            int maxPatterns,
            int maxProviderChecks,
            long deadlineNanos,
            int effectiveCoProcessors
        ) {
            this.maxOperations = maxOperations;
            this.maxPatterns = maxPatterns;
            this.maxProviderChecks = maxProviderChecks;
            this.deadlineNanos = deadlineNanos;
            this.effectiveCoProcessors = effectiveCoProcessors;
        }

        private boolean canContinue() {
            return getExhaustionReason() == ExhaustionReason.NONE;
        }

        private boolean tryConsumeOperation() {
            if (getExhaustionReason() != ExhaustionReason.NONE || operations >= maxOperations) {
                return false;
            }
            operations++;
            return getExhaustionReason() == ExhaustionReason.NONE;
        }

        private boolean tryConsumePatternAttempt() {
            if (getExhaustionReason() != ExhaustionReason.NONE || patternAttempts >= maxPatterns) {
                return false;
            }
            patternAttempts++;
            return getExhaustionReason() == ExhaustionReason.NONE;
        }

        private boolean tryConsumeProviderCheck() {
            if (getExhaustionReason() != ExhaustionReason.NONE || providerChecks >= maxProviderChecks) {
                return false;
            }
            providerChecks++;
            return getExhaustionReason() == ExhaustionReason.NONE;
        }

        private int getRemainingProviderChecks() {
            return Math.max(0, maxProviderChecks - providerChecks);
        }

        private void recordEnergySimulation() {
            energySimulations++;
        }

        private ExhaustionReason getExhaustionReason() {
            if (System.nanoTime() >= deadlineNanos) {
                return ExhaustionReason.TIME_LIMIT;
            }
            if (operations >= maxOperations) {
                return ExhaustionReason.OPERATION_LIMIT;
            }
            if (patternAttempts >= maxPatterns) {
                return ExhaustionReason.PATTERN_LIMIT;
            }
            if (providerChecks >= maxProviderChecks) {
                return ExhaustionReason.PROVIDER_CHECK_LIMIT;
            }
            return ExhaustionReason.NONE;
        }
    }

    private record CraftingTickResult(int pushedPatterns, ExhaustionReason exhaustionReason) {
        private CraftingTickResult merge(CraftingTickResult other) {
            var mergedReason = this.exhaustionReason != ExhaustionReason.NONE ? this.exhaustionReason : other.exhaustionReason;
            return new CraftingTickResult(this.pushedPatterns + other.pushedPatterns, mergedReason);
        }
    }

    private record PatternPushAttempt(int pushedPatterns, ExhaustionReason exhaustionReason, boolean advanceTaskCursor) {
        private static PatternPushAttempt exhausted(ExhaustionReason reason) {
            return new PatternPushAttempt(0, reason, false);
        }

        private static PatternPushAttempt noProgress(boolean advanceTaskCursor) {
            return new PatternPushAttempt(0, ExhaustionReason.NONE, advanceTaskCursor);
        }
    }
}
