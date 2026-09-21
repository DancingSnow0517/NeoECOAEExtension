package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingDispatchPolicyRegistry;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;

/**
 * Owns candidate/phase traversal and input resolution. It deliberately exposes only a small pass result to the CPU
 * logic; provider algorithms and FastPath internals live behind {@link ECOCraftingProviderDispatcher}.
 */
final class ECOCraftingTaskScheduler {
    private static final int MIN_NORMAL_PROBES_PER_TICK = 64;
    // A large exact order can contain thousands of task candidates. Walking every
    // candidate in one CPU callback turns a temporary provider stall into a multi-
    // second server tick. The cursor resumes at the next candidate on the next call.
    private static final int MAX_CANDIDATE_INSPECTIONS_PER_EXECUTE = 64;

    private final ECOProviderCursor providerCursor = new ECOProviderCursor();
    private final ECOCraftingInputTemplateCache inputTemplateCache = new ECOCraftingInputTemplateCache();
    private final ECOCraftingRemainderCache remainderCache = ECOCraftingRemainderCache.shared();
    private final ECODispatchStallDiagnostics stallDiagnostics = new ECODispatchStallDiagnostics();
    private final ECOCraftingProviderDispatcher providerDispatcher;
    private final List<ECOExecutionRuntime.DispatchCandidate> nativeCandidateBuffer = new java.util.ArrayList<>();
    private final Map<FailedCandidateKey, InputAvailabilityEpoch> missingInputFailures = new HashMap<>();
    private final Map<AEKey, Long> physicalInsertGenerations = new HashMap<>();

    private long physicalInsertGeneration;
    private IPatternDetails resumeDispatchPattern;
    private int sharedRemainingNormalProbes = -1;
    private DispatchPassResult lastPass = DispatchPassResult.EMPTY;

    ECOCraftingTaskScheduler(ECOCraftingProviderDispatcher providerDispatcher) {
        this.providerDispatcher = providerDispatcher;
    }

    void resetDispatchState() {
        inputTemplateCache.clear();
        providerCursor.clear();
        resumeDispatchPattern = null;
        resetMissingInputMemo();
    }

    void reset() {
        resetDispatchState();
        providerDispatcher.reset();
        sharedRemainingNormalProbes = -1;
        lastPass = DispatchPassResult.EMPTY;
        stallDiagnostics.reset();
    }

    void bindDiagnostics(UUID jobId, long tick) {
        stallDiagnostics.bind(jobId, tick);
    }

    void beginResolveTick(long tick) {
        stallDiagnostics.beginResolveTick(tick);
    }

    void finishResolveTick() {
        stallDiagnostics.finishResolveTick();
    }

    void check(ExecutingCraftingJob job) {
        stallDiagnostics.check(TickHandler.instance().getCurrentTick(), job);
    }

    void progress(long tick) {
        stallDiagnostics.progress(tick);
    }

    void finalDeliveryBlocked(AEKey key, long amount) {
        stallDiagnostics.finalDeliveryBlocked(key, amount);
    }

    boolean hasPendingTasks(ExecutingCraftingJob current) {
        if (!current.deferredStock.isEmpty() || !current.deferredEmitted.isEmpty()) return true;
        for (var task : current.tasks.values()) {
            if (task.value > 0L) return true;
        }
        return false;
    }

    void recordPhysicalInsert(AEKey key) {
        if (key == null) return;
        long current = physicalInsertGenerations.getOrDefault(key, 0L);
        if (current != Long.MAX_VALUE) physicalInsertGenerations.put(key, current + 1L);
        if (physicalInsertGeneration != Long.MAX_VALUE) physicalInsertGeneration++;
    }

    void invalidateInputTemplates(@org.jetbrains.annotations.Nullable AEKey key) {
        inputTemplateCache.inventoryChanged(key);
    }

    void beginSharedProbeBudget(int operationLimit) {
        sharedRemainingNormalProbes = Math.max(MIN_NORMAL_PROBES_PER_TICK, operationLimit);
    }

    void consumeSharedProbeBudget(int normalProbes) {
        if (sharedRemainingNormalProbes >= 0) {
            sharedRemainingNormalProbes = Math.max(0, sharedRemainingNormalProbes - normalProbes);
        }
    }

    void endSharedProbeBudget() {
        sharedRemainingNormalProbes = -1;
    }

    DispatchPassResult lastPass() {
        return lastPass;
    }

    DispatchPassResult execute(int maxPatterns, CraftingService craftingService, IEnergyService energyService,
            Level level, ExecutingCraftingJob current, ListCraftingInventory inventory,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerSupplier,
            BooleanSupplier jobStillActive, ECOCraftingCpuContext cpuContext,
            ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        providerCursor.beginPass(craftingService, TickHandler.instance().getCurrentTick());
        providerDispatcher.beginTick(TickHandler.instance().getCurrentTick());
        int ordinaryLimit = Math.max(0, maxPatterns);
        int probeLimit = sharedRemainingNormalProbes >= 0
                ? sharedRemainingNormalProbes : Math.max(MIN_NORMAL_PROBES_PER_TICK, ordinaryLimit);
        var budget = new ECOCraftingDispatchBudget(ordinaryLimit, probeLimit);
        stallDiagnostics.beginDispatch(ordinaryLimit, probeLimit);

        int totalPushed = 0;
        // A CPU callback may advance several independent ready tasks. Keep the existing
        // operation limit as the task-start budget so verified provider batches cannot
        // turn one callback into an unbounded walk over the whole execution graph.
        int taskDispatchLimit = Math.max(1, Math.min(MAX_CANDIDATE_INSPECTIONS_PER_EXECUTE,
                Math.max(0, maxPatterns)));
        int acceptedDispatches = 0;
        BitSet blockedOrderedPhases = new BitSet();
        while (jobStillActive.getAsBoolean()) {
            var candidates = current.executionRuntime == null
                    ? nativeDispatchCandidates(current)
                    : current.executionRuntime.candidates();
            stallDiagnostics.candidates(candidates.size());
            if (candidates.isEmpty()) {
                if (stallDiagnostics.isActive()) {
                    stallDiagnostics.noCandidates(current.executionRuntime != null && hasPendingTasks(current));
                }
                break;
            }

            int start = resumeIndex(candidates);
            blockedOrderedPhases.clear();
            boolean acceptedInPass = false;
            int inspected = 0;
            for (int offset = 0; offset < candidates.size()
                    && inspected < MAX_CANDIDATE_INSPECTIONS_PER_EXECUTE; offset++) {
                int candidateIndex = (start + offset) % candidates.size();
                var candidate = candidates.get(candidateIndex);
                inspected++;
                if (blockedOrderedPhases.get(candidate.phaseIndex())) continue;

                var progress = current.tasks.get(candidate.pattern());
                if (progress == null || progress.value <= 0L) {
                    providerCursor.forget(candidate.pattern());
                    continue;
                }
                long allowedCount = Math.min(candidate.maxDispatchCount(), progress.value);
                if (allowedCount <= 0L) {
                    if (candidate.blocksOrderedPhase()) stallDiagnostics.phaseBarrier();
                    continue;
                }

                var pattern = candidate.pattern();
                // The explicit execution runtime owns phase/cycle gating. Legacy jobs retain the growth barrier.
                if (current.executionRuntime == null && !current.canDispatchAfterGrowth(pattern)) {
                    stallDiagnostics.phaseBarrier();
                    continue;
                }

                FailedCandidateKey failedCandidateKey = current.executionRuntime == null
                        ? null : new FailedCandidateKey(candidate.phaseIndex(), candidate.taskId());
                Set<AEKey> dependencyKeys = current.executionRuntime == null
                        ? Set.of() : current.executionRuntime.inputKeys(candidate.taskId());
                boolean preciseFailureEpoch = failedCandidateKey != null && !dependencyKeys.isEmpty()
                        && !ECOCraftingInputPreview.hasReusableTemplates(pattern, remainderCache);
                if (failedCandidateKey != null) {
                    var previousFailure = missingInputFailures.get(failedCandidateKey);
                    if (previousFailure != null && availabilityUnchanged(previousFailure, dependencyKeys,
                            current.executionRuntime, preciseFailureEpoch)) {
                        stallDiagnostics.repeatedFailureSameEpoch();
                        if (candidate.blocksOrderedPhase()) {
                            stallDiagnostics.phaseBarrier();
                            blockedOrderedPhases.set(candidate.phaseIndex());
                        }
                        continue;
                    }
                }

                var providers = providerCursor.availableProviders(
                        pattern,
                        () -> providerSupplier.apply(pattern),
                        providerCandidate -> {
                            boolean eligible = providerDispatcher.isEligible(providerCandidate, budget);
                            stallDiagnostics.providerConsidered(eligible);
                            return eligible;
                        },
                        (providerCandidate, busy) -> stallDiagnostics.provider(pattern, providerCandidate, busy),
                        (providerCandidate, busy) -> ECOCraftingDispatchPolicyRegistry.isProviderAvailable(
                                cpuContext, providerCandidate, busy));
                if (providers.isEmpty()) {
                    stallDiagnostics.noReadyProvider();
                    if (candidate.blocksOrderedPhase()) blockedOrderedPhases.set(candidate.phaseIndex());
                    continue;
                }

                long physicalGenerationBeforeResolve = physicalInsertGeneration;
                long seedGenerationBeforeResolve = current.executionRuntime == null
                        ? 0L : current.executionRuntime.startupSeedGeneration();
                long reloadGenerationBeforeResolve = AE2PatternIntrospection.reloadGeneration();
                var outputs = new appeng.api.stacks.KeyCounter();
                var remainders = new appeng.api.stacks.KeyCounter();
                var protectedStartupSeed = current.executionRuntime == null
                        ? java.util.Map.<AEKey, Long>of()
                        : current.executionRuntime.protectedStartupSeed(candidate);
                var inputInventory = current.executionRuntime == null
                        ? new ECOCraftingInputPreview(inventory, inputTemplateCache)
                        : new ECOCraftingInputPreview(inventory, pattern, protectedStartupSeed, remainderCache);
                stallDiagnostics.resolveAttempt();
                var inputs = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                        pattern, inputInventory, level, outputs, remainders, remainderCache);
                if (inputs == null) {
                    stallDiagnostics.resolveFailure();
                    if (failedCandidateKey != null) {
                        boolean stable = physicalGenerationBeforeResolve == physicalInsertGeneration
                                && seedGenerationBeforeResolve == current.executionRuntime.startupSeedGeneration()
                                && reloadGenerationBeforeResolve == AE2PatternIntrospection.reloadGeneration();
                        if (stable) {
                            missingInputFailures.put(failedCandidateKey,
                                    captureAvailabilityEpoch(dependencyKeys, current.executionRuntime,
                                            preciseFailureEpoch));
                        } else {
                            missingInputFailures.remove(failedCandidateKey);
                        }
                    }
                    if (stallDiagnostics.isActive()) {
                        var diagnosticInventory = current.executionRuntime == null
                                ? new ECOCraftingInputPreview(inventory)
                                : new ECOCraftingInputPreview(inventory, pattern, protectedStartupSeed, remainderCache);
                        stallDiagnostics.missingInputs(pattern, diagnosticInventory);
                    }
                    if (candidate.blocksOrderedPhase()) {
                        stallDiagnostics.phaseBarrier();
                        blockedOrderedPhases.set(candidate.phaseIndex());
                    }
                    continue;
                }

                var request = new ECOCraftingDispatchRequest(
                        current, candidate, pattern, inputs, outputs, remainders, allowedCount, inventory, level);
                var dispatch = providerDispatcher.dispatchCandidate(
                        request,
                        providers,
                        budget,
                        energyService,
                        stallDiagnostics,
                        provider -> providerCursor.advanceAfter(pattern, provider),
                        () -> resumeDispatchPattern = nextCandidatePattern(candidates, candidateIndex),
                        normalPush);
                if (dispatch.accepted()) {
                    totalPushed = addPushed(totalPushed, dispatch.acceptedCrafts());
                    // Rebuild the candidate list after every accepted task. This commits
                    // progress and newly available inputs before another independent task
                    // is selected, while the runtime still gates true dependencies.
                    resumeDispatchPattern = nextCandidatePattern(candidates, candidateIndex);
                    acceptedDispatches++;
                    acceptedInPass = true;
                    if (failedCandidateKey != null) missingInputFailures.remove(failedCandidateKey);
                    break;
                }
                if (candidate.blocksOrderedPhase()) blockedOrderedPhases.set(candidate.phaseIndex());
            }
            if (!acceptedInPass && inspected > 0 && inspected < candidates.size()) {
                // Preserve progress through a stalled candidate set. This is deliberately
                // separate from provider round-robin state: no provider was accepted.
                resumeDispatchPattern = candidates.get((start + inspected) % candidates.size()).pattern();
            }
            if (!acceptedInPass) break;
            if (acceptedDispatches >= taskDispatchLimit) break;
            // Re-enter with a fresh candidate snapshot so independent tasks can advance
            // in the same CPU callback without allowing an old dependency snapshot to leak.
        }

        lastPass = new DispatchPassResult(totalPushed, budget.normalProbes(), budget.acceptedNormalPushes());
        return lastPass;
    }

    private int resumeIndex(List<ECOExecutionRuntime.DispatchCandidate> candidates) {
        if (resumeDispatchPattern == null) return 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).pattern().equals(resumeDispatchPattern)) return i;
        }
        return 0;
    }

    private static IPatternDetails nextCandidatePattern(List<ECOExecutionRuntime.DispatchCandidate> candidates,
            int candidateIndex) {
        return candidates.get((candidateIndex + 1) % candidates.size()).pattern();
    }

    private static int addPushed(int current, long accepted) {
        return (int) Math.min(Integer.MAX_VALUE, (long) current + Math.max(0L, accepted));
    }

    private List<ECOExecutionRuntime.DispatchCandidate> nativeDispatchCandidates(ExecutingCraftingJob current) {
        var result = nativeCandidateBuffer;
        result.clear();
        for (var entry : current.tasks.entrySet()) {
            if (entry.getValue().value > 0L) {
                // Native jobs do not consult task ids; the placeholder id is never committed to a runtime.
                result.add(new ECOExecutionRuntime.DispatchCandidate(0, 0, entry.getKey(),
                        entry.getValue().value, false));
            }
        }
        return result;
    }

    private void resetMissingInputMemo() {
        missingInputFailures.clear();
        physicalInsertGenerations.clear();
        physicalInsertGeneration = 0L;
    }

    private InputAvailabilityEpoch captureAvailabilityEpoch(Set<AEKey> dependencyKeys,
            ECOExecutionRuntime runtime, boolean precise) {
        Map<AEKey, Long> physical = new HashMap<>();
        Map<AEKey, Long> startupSeeds = new HashMap<>();
        if (precise) {
            for (AEKey key : dependencyKeys) {
                physical.put(key, physicalInsertGenerations.getOrDefault(key, 0L));
                startupSeeds.put(key, runtime.startupSeedGeneration(key));
            }
        }
        return new InputAvailabilityEpoch(precise, physicalInsertGeneration, runtime.startupSeedGeneration(),
                Map.copyOf(physical), Map.copyOf(startupSeeds), AE2PatternIntrospection.reloadGeneration());
    }

    private boolean availabilityUnchanged(InputAvailabilityEpoch previous, Set<AEKey> dependencyKeys,
            ECOExecutionRuntime runtime, boolean precise) {
        if (previous.reloadGeneration() != AE2PatternIntrospection.reloadGeneration()) return false;
        if (previous.precise() != precise) return false;
        if (!precise) {
            return previous.globalPhysicalInsertGeneration() == physicalInsertGeneration
                    && previous.globalStartupSeedGeneration() == runtime.startupSeedGeneration();
        }
        for (AEKey key : dependencyKeys) {
            if (previous.physicalInsertGenerations().getOrDefault(key, 0L)
                    != physicalInsertGenerations.getOrDefault(key, 0L)
                    || previous.startupSeedGenerations().getOrDefault(key, 0L)
                    != runtime.startupSeedGeneration(key)) return false;
        }
        return true;
    }

    record DispatchPassResult(int totalPushed, int normalProbes, int acceptedNormalPushes) {
        static final DispatchPassResult EMPTY = new DispatchPassResult(0, 0, 0);
    }

    private record FailedCandidateKey(int phaseIndex, int taskId) {
    }

    private record InputAvailabilityEpoch(boolean precise, long globalPhysicalInsertGeneration,
            long globalStartupSeedGeneration, Map<AEKey, Long> physicalInsertGenerations,
            Map<AEKey, Long> startupSeedGenerations, long reloadGeneration) {
    }
}
