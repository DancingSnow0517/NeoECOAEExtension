package cn.dancingsnow.neoecoae.api.me;

import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.chargeAcceptedPatternEnergy;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.consumedInputs;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.mergeConsumedInputs;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.reinjectPatternInputs;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.validateRuntimeConsumption;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.hasBatchProbeProvider;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.hasExternalCountedProvider;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.hasFastPathProvider;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.isUnknownBatchProbeProvider;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.patternIdentityOrObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.compat.dataenergistics.ECODataEnergisticsCountedBridge;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOExternalBatchContracts;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltBatchBridge;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedCraft;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import net.minecraft.world.level.Level;

/** Owns batch admission and probe budgets; each provider protocol retains its ownership boundary. */
final class ECOCraftingBatchDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final long BATCH_REJECTION_LOG_INTERVAL_TICKS = 100L;
    private final ECOCraftingCPULogic logic;
    private final ECOCraftingAccounting accounting;
    private final ListCraftingInventory inventory;
    private final Map<BatchProbeKey, BatchCapacityProbeState> batchProbeStates = new HashMap<>();
    private final Set<Object> batchProbedTasksThisTick = new HashSet<>();
    private long batchProbeBudgetTick = Long.MIN_VALUE;
    private int batchProbesUsedThisTick;
    private long lastBatchRejectionLogTick = Long.MIN_VALUE;

    ECOCraftingBatchDispatcher(ECOCraftingCPULogic logic, ECOCraftingAccounting accounting) {
        this.logic = logic;
        this.accounting = accounting;
        this.inventory = logic.getInventory();
    }

    record Attempt(@Nullable ECOExtractedPatternExecution execution, DispatchResult result,
            boolean virtualAccepted, boolean finiteAccepted) {}

    private static final class BatchProbeKey {
        private final Object scope;
        private final Object pattern;
        private final int hash;

        private BatchProbeKey(Object scope, Object pattern) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.pattern = Objects.requireNonNull(pattern, "pattern");
            this.hash = 31 * System.identityHashCode(scope) + pattern.hashCode();
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof BatchProbeKey key && scope == key.scope && pattern.equals(key.pattern);
        }
    }

    private record ProbeDispatchOutcome(DispatchResult result, int probeCount) {}

    private record TaskProbeKey(ExecutingCraftingJob job, Object taskIdentity) {
        @Override public boolean equals(Object other) {
            return other instanceof TaskProbeKey key && job == key.job && taskIdentity.equals(key.taskIdentity);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(job) + taskIdentity.hashCode();
        }
    }

    Attempt dispatch(ExecutingCraftingJob job, ExecutingCraftingJob.DispatchTask task,
            ECOExtractedCraft craft, List<ICraftingProvider> providers, IEnergyService energy,
            long dispatchLimit, int remainingOperations, Level level, boolean firstFiniteBatch) {
        DispatchResult result = new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        boolean nativeProvider = hasFastPathProvider(providers);
        boolean externalProvider = hasExternalCountedProvider(providers);
        if (!nativeProvider && !externalProvider && !hasBatchProbeProvider(providers)) {
            return new Attempt(null, result, false, false);
        }

        var execution = ECOExtractedPatternExecution.create(task.pattern(), craft.craftingContainer(),
            craft.expectedOutputs(), craft.expectedContainerItems(), level);
        boolean virtualAccepted = false;
        boolean finiteAccepted = false;
        if (nativeProvider) {
            result = tryPushVerifiedVirtualBatch(job, execution, craft.craftingContainer(), providers, dispatchLimit);
            virtualAccepted = result instanceof DispatchResult.Accepted;
            if (result instanceof DispatchResult.Waiting) {
                result = tryPushVerifiedFastPathBatch(job, execution, craft.craftingContainer(), providers,
                    energy, craft.patternPower(), dispatchLimit, firstFiniteBatch);
                finiteAccepted = result instanceof DispatchResult.Accepted;
            }
        }
        if (result instanceof DispatchResult.Waiting && externalProvider) {
            result = tryPushExternalCountedBatch(job, execution, craft.craftingContainer(), providers,
                energy, craft.patternPower(), dispatchLimit, task.progress().value, remainingOperations, level);
        }
        int remainingProbeBudget = Math.max(0,
            ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK - batchProbesUsedThisTick);
        if (result instanceof DispatchResult.Waiting && remainingProbeBudget > 0) {
            Object taskIdentity = task.taskId() >= 0
                ? Integer.valueOf(task.taskId()) : patternIdentityOrObject(task.pattern());
            if (batchProbedTasksThisTick.add(new TaskProbeKey(job, taskIdentity))) {
                ProbeDispatchOutcome probe = tryPushProbedBatch(job, execution, craft.craftingContainer(),
                    providers, energy, craft.patternPower(), dispatchLimit, task.progress().value, remainingProbeBudget);
                batchProbesUsedThisTick += probe.probeCount();
                result = probe.result();
            }
        }
        return new Attempt(execution, result, virtualAccepted, finiteAccepted);
    }

    /**
     * Dispatches through external counted contracts without teaching ECO any third-party machine internals.
     * Native Thunderbolt providers take precedence over Data Energistics' registry because a subclass may inherit
     * Data Energistics' conservative single-copy fallback while exposing a stronger native batch contract itself.
     */
    private DispatchResult tryPushExternalCountedBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            IEnergyService energyService,
            double patternPower,
            long runtimeDispatchLimit,
            long taskRemaining,
            long cpuCopyBudget,
            Level level) {
        long legalUpper = calculateBatchLegalUpper(
            execution, energyService, patternPower, runtimeDispatchLimit, taskRemaining);
        if (legalUpper <= 1L) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        for (ICraftingProvider candidate : candidateProviders) {
            if (candidate instanceof ECOCraftingPatternBusBlockEntity || candidate.isBusy()) continue;
            if (ECOThunderboltBatchBridge.supports(candidate)) {
                DispatchResult result = tryPushThunderboltBatch(job, execution, firstCraftingContainer,
                    candidate, energyService, patternPower,
                    legalUpper, cpuCopyBudget, level);
                if (!(result instanceof DispatchResult.Waiting)) return result;
            }
        }

        String patternIdentity = String.valueOf(patternIdentityOrObject(execution.details()));
        long tick = Math.max(0L, TickHandler.instance().getCurrentTick());
        for (ICraftingProvider provider : candidateProviders) {
            if (provider instanceof ECOCraftingPatternBusBlockEntity
                    || ECOThunderboltBatchBridge.supports(provider)
                    || provider.isBusy()
                    || !ECODataEnergisticsCountedBridge.supports(provider)) continue;
            var admission = ECODataEnergisticsCountedBridge.prepare(
                provider, execution.details(), firstCraftingContainer, legalUpper, patternIdentity, tick);
            if (admission == null) continue;
            DispatchResult result = tryCommitDataEnergisticsAdmission(job, execution, firstCraftingContainer,
                provider, admission, energyService, patternPower);
            if (!(result instanceof DispatchResult.Waiting)) return result;
        }
        return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
    }

    private long calculateBatchLegalUpper(
            ECOExtractedPatternExecution execution,
            IEnergyService energyService,
            double patternPower,
            long runtimeDispatchLimit,
            long taskRemaining) {
        long initial = calculateBatchRequestSize(execution, Math.min(taskRemaining, runtimeDispatchLimit));
        long energyLimit = maxBatchSizeFromEnergy(energyService, patternPower, initial);
        long availableExtra = ECOBatchCraftingHelper.maxCraftsFromInventory(
            inventory, execution.inputItems(), Math.max(0L, initial - 1L));
        long extractableLimit = availableExtra == Long.MAX_VALUE ? Long.MAX_VALUE : availableExtra + 1L;
        return calculateProbeLegalUpperBound(taskRemaining, runtimeDispatchLimit,
            execution.arithmeticBatchLimit(), extractableLimit, energyLimit);
    }

    private DispatchResult tryPushThunderboltBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            ICraftingProvider provider,
            IEnergyService energyService,
            double patternPower,
            long legalUpper,
            long cpuCopyBudget,
            Level level) {
        final long capacity;
        try {
            capacity = Math.max(0L, ECOThunderboltBatchBridge.capacity(provider, execution.details()));
        } catch (RuntimeException failure) {
            LOGGER.error("Thunderbolt batch provider failed while reporting capacity: " + provider, failure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        long requested = ECOExternalBatchContracts.thunderboltRequest(legalUpper, capacity, cpuCopyBudget);
        if (requested <= 1L) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        List<GenericStack> extraInputs;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), requested - 1L);
            validateRuntimeConsumption(job, mergeConsumedInputs(firstCraftingContainer, extraInputs));
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
        } catch (RuntimeException extractionFailure) {
            LOGGER.error("Thunderbolt batch inputs could not be extracted", extractionFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        Map<AEKey, Long> firstConsumed = consumedInputs(firstCraftingContainer);
        final long leftover;
        try {
            leftover = ECOThunderboltBatchBridge.pushBatch(provider, execution.details(),
                firstCraftingContainer, requested, level, job.link.getCraftingID());
        } catch (RuntimeException providerFailure) {
            // Thunderbolt's own dispatcher treats an exception as a full leftover. The provider contract requires
            // failure before ownership when it cannot return a valid leftover count.
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            LOGGER.error("Thunderbolt batch provider failed; treating the complete batch as leftover: " + provider,
                providerFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        final long accepted;
        try {
            accepted = ECOExternalBatchContracts.acceptedFromLeftover(requested, leftover);
        } catch (IllegalArgumentException invalidContract) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            LOGGER.error("Thunderbolt batch provider {} returned invalid leftover {} for requested {}",
                provider, leftover, requested);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        if (accepted <= 0L) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        if (leftover > 0L) {
            ECOBatchCraftingHelper.insertAll(
                inventory, ECOBatchCraftingHelper.multiply(execution.inputItems(), leftover));
        }
        chargeCountedBatchEnergy(energyService, patternPower, accepted);
        if (logic.getJob() == job) accounting.recordPushedPattern(job, execution, accepted);
        Map<AEKey, Long> actualConsumed = mergeConsumedInputs(
            firstConsumed, ECOBatchCraftingHelper.multiply(execution.inputItems(), accepted - 1L));
        return new DispatchResult.Accepted(accepted, actualConsumed);
    }

    private DispatchResult tryCommitDataEnergisticsAdmission(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            ICraftingProvider provider,
            ECODataEnergisticsCountedBridge.PreparedAdmission admission,
            IEnergyService energyService,
            double patternPower) {
        long count = admission.count();
        final ECOUselessDynamicOutputBridge.Registration dynamicRegistration;
        try {
            dynamicRegistration = ECOUselessDynamicOutputBridge.prepare(
                logic, execution.details(), count);
        } catch (RuntimeException compatibilityFailure) {
            LOGGER.error("Useless dynamic-output compatibility preparation failed", compatibilityFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        if (dynamicRegistration == null) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        List<GenericStack> extraInputs;
        final Map<AEKey, Long> actualConsumed;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), count - 1L);
            actualConsumed = mergeConsumedInputs(firstCraftingContainer, extraInputs);
            validateRuntimeConsumption(job, actualConsumed);
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
        } catch (RuntimeException extractionFailure) {
            LOGGER.error("Data Energistics counted inputs could not be extracted", extractionFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        boolean accepted = false;
        RuntimeException providerFailure = null;
        try {
            accepted = admission.commit(firstCraftingContainer);
        } catch (RuntimeException failure) {
            providerFailure = failure;
        }
        boolean ownershipTransferred = accepted || admission.hasTransferredInputOwnership();
        if (!ownershipTransferred) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            if (providerFailure != null) {
                LOGGER.error("Data Energistics counted provider failed before ownership: " + provider, providerFailure);
            }
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        if (providerFailure != null) {
            LOGGER.error("Data Energistics counted provider failed after taking ownership; accounting as accepted: "
                + provider, providerFailure);
        } else if (!accepted) {
            LOGGER.error("Data Energistics counted provider returned false after taking ownership; accounting {} crafts as accepted",
                count);
        }
        try {
            dynamicRegistration.commit(
                job.link.getCraftingID(), job.finalOutput == null ? null : job.finalOutput.what());
        } catch (RuntimeException compatibilityFailure) {
            // The provider owns the inputs already. Preserve counted accounting and surface the missing dynamic
            // registration loudly; rolling inputs back here would duplicate the physical batch.
            LOGGER.error("Useless dynamic outputs could not be registered after counted ownership transfer",
                compatibilityFailure);
        }
        chargeCountedBatchEnergy(energyService, patternPower, count);
        if (logic.getJob() == job) accounting.recordPushedPattern(job, execution, count);
        return new DispatchResult.Accepted(count, actualConsumed);
    }

    private void chargeCountedBatchEnergy(IEnergyService energyService, double patternPower, long count) {
        double requiredPower = patternPower * count;
        if (Double.isFinite(requiredPower)) chargeAcceptedPatternEnergy(energyService, requiredPower);
    }

    private ProbeDispatchOutcome tryPushProbedBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            IEnergyService energyService,
            double patternPower,
            long runtimeDispatchLimit,
            long taskRemaining,
            int remainingCpuProbeBudget) {
        ECOBatchProbeCraftingProvider selected = null;
        for (var provider : candidateProviders) {
            if (isUnknownBatchProbeProvider(provider) && provider instanceof ECOBatchProbeCraftingProvider capable
                    && !provider.isBusy()) {
                selected = capable;
                break;
            }
        }
        if (selected == null) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), 0);
        }

        long legalUpper = calculateBatchLegalUpper(
            execution, energyService, patternPower, runtimeDispatchLimit, taskRemaining);
        if (legalUpper <= 0L) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), 0);
        }

        final Object scope;
        try {
            scope = Objects.requireNonNull(selected.eco$getBatchProbeScope(),
                "batch probe provider returned a null scope");
        } catch (RuntimeException contractViolation) {
            reinjectPatternInputs(inventory, firstCraftingContainer);
            LOGGER.error("Batch probe provider returned an invalid dispatch scope", contractViolation);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), 0);
        }
        BatchProbeKey key = new BatchProbeKey(scope, patternIdentityOrObject(execution.details()));
        BatchCapacityProbeState state = batchProbeStates.computeIfAbsent(key, ignored -> new BatchCapacityProbeState());
        int requiredBudget = ECOBatchProbeScheduler.candidates(state.startingUpperBound(legalUpper)).length;
        int probesAlreadyUsed = ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK
            - remainingCpuProbeBudget;
        if (!ECOBatchProbeScheduler.canStartCpuProbeWindow(probesAlreadyUsed, requiredBudget)) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), 0);
        }

        ECOBatchProbeCraftingProvider provider = selected;
        ECOBatchProbeScheduler.ProbeResult probe;
        int[] attemptedProbes = { 0 };
        try {
            probe = ECOBatchProbeScheduler.probe(state, legalUpper, remainingCpuProbeBudget,
                candidate -> {
                    attemptedProbes[0]++;
                    return provider.eco$simulateBatch(execution, candidate);
                });
        } catch (RuntimeException contractViolation) {
            reinjectPatternInputs(inventory, firstCraftingContainer);
            LOGGER.error("Batch probe side-effect-free simulation failed", contractViolation);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), attemptedProbes[0]);
        }
        if (probe.selected() <= 0L) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), probe.probeCount());
        }

        long craftCount = probe.selected();
        List<GenericStack> extraInputs;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), craftCount - 1L);
        } catch (RuntimeException invalidBatch) {
            reinjectPatternInputs(inventory, firstCraftingContainer);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), probe.probeCount());
        }
        Map<AEKey, Long> actualConsumed = mergeConsumedInputs(firstCraftingContainer, extraInputs);
        boolean extraExtracted = false;
        boolean ownershipTransferred = false;
        try {
            validateRuntimeConsumption(job, actualConsumed);
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extraExtracted = true;
            if (!provider.eco$commitBatch(execution, craftCount, job.link.getCraftingID())) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
                return new ProbeDispatchOutcome(
                    new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), probe.probeCount());
            }
            ownershipTransferred = true;
            chargeCountedBatchEnergy(energyService, patternPower, craftCount);
            if (logic.getJob() == job) accounting.recordPushedPattern(job, execution, craftCount);
            return new ProbeDispatchOutcome(
                new DispatchResult.Accepted(craftCount, actualConsumed), probe.probeCount());
        } catch (RuntimeException failure) {
            if (ownershipTransferred) {
                LOGGER.error("Batch probe commit failed after ownership transfer; accounting as accepted", failure);
                return new ProbeDispatchOutcome(
                    new DispatchResult.Accepted(craftCount, actualConsumed), probe.probeCount());
            }
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraExtracted);
            LOGGER.error("Batch probe commit failed before ownership transfer; inputs were rolled back", failure);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), probe.probeCount());
        } catch (Error failure) {
            if (!ownershipTransferred) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraExtracted);
            }
            throw failure;
        }
    }

    void resetBatchProbeBudgetForCurrentTick() {
        long currentTick = TickHandler.instance().getCurrentTick();
        if (batchProbeBudgetTick == currentTick) return;
        batchProbeBudgetTick = currentTick;
        batchProbesUsedThisTick = 0;
        batchProbedTasksThisTick.clear();
    }

    private DispatchResult tryPushVerifiedFastPathBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            IEnergyService energyService,
            double patternPower,
            long taskRemaining,
            boolean firstBatch) {
        if (!execution.canUseFastPath() || taskRemaining <= 1) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        // Ask providers for the full remaining task. The selected F-series host and worker cap the
        // offer to their live thread capacity; inventory, energy and coolant apply further bounds.
        int requested = (int) Math.min(Integer.MAX_VALUE, calculateBatchRequestSize(execution, taskRemaining));
        if (requested <= 1) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        ECOCraftingPatternBusBlockEntity selectedPatternBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        // One offer search per dispatch scope. A Network Switch group answers as a single scope now, because
        // one of its buses already searches every worker of the group.
        List<Object> visitedScopes = new ArrayList<>(4);
        for (ICraftingProvider provider : candidateProviders) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus) || provider.isBusy()) {
                continue;
            }
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null) {
                continue;
            }
            Object scope = controller.getDispatchScope();
            if (containsIdentity(visitedScopes, scope)) {
                continue;
            }
            visitedScopes.add(scope);
            var offer = patternBus.findBatchFastPathOffer(execution, requested);
            if (offer != null && offer.maxBatchSize() > 1
                    && (selectedOffer == null || offer.maxBatchSize() > selectedOffer.maxBatchSize())) {
                selectedPatternBus = patternBus;
                selectedOffer = offer;
                if (offer.maxBatchSize() >= requested) {
                    break;
                }
            }
        }
        if (selectedPatternBus == null || selectedOffer == null) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        var verifiedRecipe = selectedOffer.recipe();
        // The credential must have been minted for this very execution context, never for an earlier one.
        if (!verifiedRecipe.isVerifiedFor(execution)) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        ECOCraftingSystemBlockEntity controller = selectedPatternBus.getCraftingController();
        if (controller == null) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        // A fully virtualized host pays one flat group-wide draw per tick instead of a per-craft charge, so the
        // batch must be neither sized by nor billed for pattern power here.
        boolean flatRatePower = controller.isFullVirtualCraftingMode();
        if (!firstBatch) {
            // The probe craft was extracted before the offer search. Put it back before sizing a complete
            // subsequent batch, otherwise the first input would be counted twice or falsely appear unavailable.
            reinjectPatternInputs(inventory, firstCraftingContainer);
        }

        int batchSize = 0;
        List<GenericStack> batchInputs = List.of();
        Map<AEKey, Long> actualConsumed = Map.of();
        boolean batchInputsExtracted = false;
        boolean ownershipTransferred = false;
        try {
            batchSize = Math.min(requested, selectedOffer.maxBatchSize());
            if (!flatRatePower) {
                batchSize = Math.min(batchSize, (int) maxBatchSizeFromEnergy(energyService, patternPower, (long) batchSize));
            }
            batchSize = controller.getCraftingCoolantCraftLimit(5, controller.getEffectiveOverclockTimes(), batchSize);
            if (batchSize <= 1) {
                if (!firstBatch) reclaimProbeCraftingInputs(firstCraftingContainer);
                return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
            }

            if (verifiedRecipe.reusableStateModel() == null) {
                int requestedInventoryCrafts = firstBatch ? batchSize - 1 : batchSize;
                int availableCrafts = ECOBatchCraftingHelper.maxCraftsFromInventory(
                    inventory, execution.inputItems(), requestedInventoryCrafts);
                batchSize = Math.min(batchSize, firstBatch ? availableCrafts + 1 : availableCrafts);
            } else {
                batchSize = (int) (firstBatch
                    ? ECOBatchCraftingHelper.maxBatchSizeFromAdditionalInputs(
                        inventory, (long) batchSize,
                        (java.util.function.LongFunction<List<GenericStack>>) verifiedRecipe::additionalInputs)
                    : ECOBatchCraftingHelper.maxBatchSizeFromBatchInputs(
                        inventory, (long) batchSize,
                        (java.util.function.LongFunction<List<GenericStack>>) verifiedRecipe::batchInputs));
            }
            if (batchSize <= 1) {
                if (!firstBatch) reclaimProbeCraftingInputs(firstCraftingContainer);
                return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
            }

            double requiredPower = flatRatePower ? 0.0D : patternPower * batchSize;
            if (!Double.isFinite(requiredPower)) {
                if (!firstBatch) reclaimProbeCraftingInputs(firstCraftingContainer);
                return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
            }
            if (firstBatch) {
                // The first craft was extracted before the offer search. Only the remainder of this batch is
                // still CPU-owned at this point.
                batchInputs = verifiedRecipe.additionalInputs(batchSize);
            } else {
                // This task already dispatched a finite batch earlier in the same CPU pass. The probe craft was
                // returned above, so extract the complete next batch from a clean per-batch ownership boundary.
                batchInputs = verifiedRecipe.batchInputs(batchSize);
            }
            actualConsumed = firstBatch
                ? mergeConsumedInputs(firstCraftingContainer, batchInputs)
                : consumedInputs(batchInputs);
            validateRuntimeConsumption(job, actualConsumed);
            ECOBatchCraftingHelper.extractExact(inventory, batchInputs);
            batchInputsExtracted = true;
            // Bind the already-verified recipe credential to this batch size. No stack list is re-copied and no
            // stack list is compared again from here on.
            var verified = verifiedRecipe.withBatch(batchSize, job.link.getCraftingID());
            if (verified == null || !selectedPatternBus.pushBatch(verified, selectedOffer)) {
                rollbackBatchInputs(inventory, firstCraftingContainer, batchInputs, firstBatch, batchInputsExtracted);
                return new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED);
            }
            // The worker owns every input from this point onward. Never reinject them into the CPU.
            ownershipTransferred = true;
            if (!flatRatePower) {
                try {
                    double chargedPower = energyService.extractAEPower(
                        requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG
                    );
                    if (Double.isNaN(chargedPower) || chargedPower < requiredPower - 0.01D) {
                        selectedOffer.worker().getFastPathCache().recordException();
                        LOGGER.error(
                            "ECO batch was accepted, but only {} of {} crafting energy was charged",
                            chargedPower,
                            requiredPower
                        );
                    }
                } catch (RuntimeException e) {
                    selectedOffer.worker().getFastPathCache().recordException();
                    LOGGER.error("ECO batch was accepted, but its crafting energy could not be charged", e);
                }
            }
            try {
                if (logic.getJob() == job) {
                    accounting.recordPushedBatchPattern(job, verifiedRecipe, batchSize);
                }
            } catch (RuntimeException e) {
                selectedOffer.worker().getFastPathCache().recordException();
                LOGGER.error("ECO batch was accepted, but its CPU accounting update failed", e);
            }
            return new DispatchResult.Accepted(batchSize, actualConsumed);
        } catch (RuntimeException e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (ownershipTransferred) {
                LOGGER.error("ECO batch failed after ownership transfer; accounting it as accepted", e);
                return new DispatchResult.Accepted(batchSize, actualConsumed);
            }
            rollbackBatchInputs(inventory, firstCraftingContainer, batchInputs, firstBatch, batchInputsExtracted);
            logBatchRejection(batchSize, taskRemaining, e);
            return new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED);
        } catch (Error e) {
            // Error is included so extracted inputs are returned before the failure escapes.
            selectedOffer.worker().getFastPathCache().recordException();
            if (!ownershipTransferred) {
                rollbackBatchInputs(inventory, firstCraftingContainer, batchInputs, firstBatch, batchInputsExtracted);
            }
            throw e;
        }
    }

    /**
     * Virtual dispatch is intentionally separate from the finite int fast path. One free physical FX lane owns
     * the complete remaining long task and materializes long-valued GenericStack totals without creating one
     * thread object per craft.
     */
    private DispatchResult tryPushVerifiedVirtualBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            long taskRemaining) {
        if (!execution.canUseFastPath() || taskRemaining <= 0L) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        ECOCraftingPatternBusBlockEntity selectedBus = null;
        ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer selectedOffer = null;
        List<Object> visitedScopes = new ArrayList<>(4);
        for (ICraftingProvider provider : candidateProviders) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus) || provider.isBusy()) {
                continue;
            }
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null || !controller.getCapabilitySnapshot().virtualMode()) {
                continue;
            }
            Object scope = controller.getDispatchScope();
            if (containsIdentity(visitedScopes, scope)) {
                continue;
            }
            visitedScopes.add(scope);
            var offer = patternBus.findVirtualFastPathOffer(execution);
            if (offer != null) {
                selectedBus = patternBus;
                selectedOffer = offer;
                break;
            }
        }
        if (selectedBus == null || selectedOffer == null || !selectedOffer.recipe().isVerifiedFor(execution)) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        var verifiedRecipe = selectedOffer.recipe();

        long requestedBatchSize = Math.min(taskRemaining, verifiedRecipe.arithmeticBatchLimit());
        long craftCount;
        if (verifiedRecipe.reusableStateModel() == null) {
            long availableExtra = ECOBatchCraftingHelper.maxCraftsFromInventory(
                inventory, execution.inputItems(), requestedBatchSize - 1L);
            craftCount = Math.min(requestedBatchSize, availableExtra + 1L);
        } else {
            craftCount = ECOBatchCraftingHelper.maxBatchSizeFromAdditionalInputs(
                inventory, requestedBatchSize,
                (java.util.function.LongFunction<List<GenericStack>>) verifiedRecipe::additionalInputs);
        }
        if (craftCount <= 0L) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        List<GenericStack> extraInputs;
        try {
            extraInputs = verifiedRecipe.additionalInputs(craftCount);
        } catch (RuntimeException e) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        Map<AEKey, Long> actualConsumed = mergeConsumedInputs(firstCraftingContainer, extraInputs);
        boolean extracted = false;
        boolean ownershipTransferred = false;
        try {
            validateRuntimeConsumption(job, actualConsumed);
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extracted = true;
            var verified = verifiedRecipe.withVirtualBatch(craftCount, job.link.getCraftingID());
            if (verified == null || !selectedBus.pushVirtualBatch(verified, selectedOffer)) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
                return new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED);
            }
            ownershipTransferred = true;
            if (logic.getJob() == job) {
                accounting.recordPushedBatchPattern(job, verifiedRecipe, craftCount);
            }
            return new DispatchResult.Accepted(craftCount, actualConsumed);
        } catch (RuntimeException e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (ownershipTransferred) {
                LOGGER.error("Virtual ECO batch failed after ownership transfer; accounting it as accepted", e);
                return new DispatchResult.Accepted(craftCount, actualConsumed);
            }
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extracted);
            logBatchRejection(craftCount, taskRemaining, e);
            return new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED);
        } catch (Error e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (!ownershipTransferred) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extracted);
            }
            throw e;
        }
    }

    /**
     * How many crafts this CPU would like to hand over in one batch. There is deliberately no fixed batch
     * ceiling: the request is the whole remaining task, bounded only by what the multiplied stack amounts
     * can still represent. The accepted size is decided by the F-series host in
     * {@link ECOCraftingPatternBusBlockEntity#findBatchFastPathOffer}, which reports its live thread
     * capability.
     */
    private long calculateBatchRequestSize(ECOExtractedPatternExecution execution, long taskRemaining) {
        long requested = Math.max(0L, taskRemaining);
        // The arithmetic ceiling was computed once when the execution context was built.
        return Math.min(requested, execution.arithmeticBatchLimit());
    }

    static long calculateProbeLegalUpperBound(long taskRemaining, long runtimeDispatchLimit,
            long arithmeticLimit, long extractableInputLimit, long energyResourceLimit) {
        return Math.min(Math.min(Math.max(0L, taskRemaining), Math.max(0L, runtimeDispatchLimit)),
            Math.min(Math.max(0L, arithmeticLimit),
                Math.min(Math.max(0L, extractableInputLimit), Math.max(0L, energyResourceLimit))));
    }

    private static boolean containsIdentity(List<Object> visited, Object candidate) {
        for (int i = 0; i < visited.size(); i++) {
            if (visited.get(i) == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * A batch that fails before ownership transfer aborts the whole task for this tick, so it must never be
     * silent: an unnoticed rejection here looks exactly like "the host stopped dispatching".
     */
    void logBatchRejection(long batchSize, long taskRemaining, RuntimeException e) {
        long tick = TickHandler.instance().getCurrentTick();
        if (tick - lastBatchRejectionLogTick < BATCH_REJECTION_LOG_INTERVAL_TICKS
            && lastBatchRejectionLogTick != Long.MIN_VALUE) {
            return;
        }
        lastBatchRejectionLogTick = tick;
        LOGGER.error(
            "ECO batch of {} crafts was rejected before ownership transfer (task remaining {}); inputs were returned to the CPU",
            batchSize,
            taskRemaining,
            e
        );
    }

    static void rollbackBatchInputs(
            ListCraftingInventory inventory,
            KeyCounter[] firstCraftingContainer,
            List<GenericStack> extraInputs,
            boolean firstInputsOwned,
            boolean extraInputsExtracted) {
        if (firstInputsOwned) {
            reinjectPatternInputs(inventory, firstCraftingContainer);
        }

        if (extraInputsExtracted) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
        }
    }

    /** Returns a temporarily reinserted probe craft to the caller's original ownership state. */
    private void reclaimProbeCraftingInputs(KeyCounter[] firstCraftingContainer) {
        if (firstCraftingContainer == null || firstCraftingContainer.length == 0) return;
        ECOBatchCraftingHelper.extractExact(inventory, ECOFastPathStacks.copyCounters(firstCraftingContainer));
    }

    private long maxBatchSizeFromEnergy(IEnergyService energyService, double patternPower, long requested) {
        return ECOBatchCraftingHelper.maxAffordableCrafts(
            patternPower,
            requested,
            totalPower -> energyService.extractAEPower(
                totalPower, Actionable.SIMULATE, PowerMultiplier.CONFIG
            )
        );
    }
}
