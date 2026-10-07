package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOPatternPushDiagnostics;
import cn.dancingsnow.neoecoae.crafting.execution.batch.*;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;

import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;
import appeng.hooks.ticking.TickHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries providers in the already-selected order. This is the boundary between task scheduling and provider-level
 * dispatch. Every selected lane plans and commits through execution.batch; this class only selects adapters
 * and applies the CPU ledger after acceptance. It does not own material or energy transactions.
 */
final class ECOCraftingProviderDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.dispatch");
    private final ECOCraftingFastPathDispatcher fastPath;
    private final ECOProcessingPatternDispatcher processing;
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, Set<IPatternDetails>> omniDeferredPatterns = new IdentityHashMap<>();
    private long omniDispatchTick = Long.MIN_VALUE;

    ECOCraftingProviderDispatcher(ECOCraftingCPULogic owner, ECOCraftingFastPathDispatcher fastPath,
            ECOCraftingEnergyTransaction energyTransaction, ECOCraftingDispatchAccounting accounting) {
        this.fastPath = fastPath;
        this.processing = new ECOProcessingPatternDispatcher(owner, energyTransaction, accounting);
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    void beginTick(long gameTick) {
        processing.beginTick(gameTick);
        if (omniDispatchTick != gameTick) {
            omniDispatchTick = gameTick;
            omniDeferredPatterns.clear();
        }
    }

    void reset() {
        processing.reset();
        omniDeferredPatterns.clear();
        omniDispatchTick = Long.MIN_VALUE;
    }

    boolean isEligible(ICraftingProvider provider, ECOCraftingDispatchBudget budget) {
        if (ECOExternalProviderBlocking.isEnabled(provider)) return budget.canAttemptOrdinary();
        return budget.canAttemptOrdinary()
                || fastPath.supportsBatch(provider)
                || ECOParallelCraftingProviders.find(provider) != null
                || cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.open(provider) != null
                || cn.dancingsnow.neoecoae.compat.omnisequence.ECOOmniSequenceDirectDispatch.isEligible(provider)
                || processing.supports(provider, null);
    }

    Result dispatchCandidate(ECOCraftingDispatchRequest request, List<ICraftingProvider> providers,
            ECOCraftingDispatchBudget budget, IEnergyService energyService,
            ECODispatchStallDiagnostics diagnostics, Consumer<ICraftingProvider> markProviderAttempt,
            Runnable markNormalResume, ECOCraftingNormalPush normalPush) {
        double singlePower = CraftingCpuHelper.calculatePatternPower(request.inputs());

        for (var provider : providers) {
            if (isOmniDispatchDeferred(provider, request.pattern())) continue;
            boolean blocking = ECOExternalProviderBlocking.isEnabled(provider);
            if (!blocking && request.job().exactOrder) {
                var exact = fastPath.tryExactDispatch(request, provider, singlePower, energyService, markProviderAttempt);
                if (exact != null) return Result.accepted(
                    cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(exact), true);
                if (request.job().suspended) return Result.none();
            }
            // Processing providers get the CPU-owned batch attempt before other optional dispatch paths.
            var processingResult = blocking ? null : processing.tryDispatch(
                    request, provider, singlePower, energyService, markProviderAttempt);
            if (processingResult != null) {
                return Result.accepted(processingResult.acceptedCrafts(), true);
            }
            if (request.job().suspended) return Result.none();
            // Native batch providers already own their one-copy fallback and target recovery.
            if (!blocking && processing.supports(provider, request.pattern())) continue;

            boolean scaledProvider = !blocking && processing.supportsScaledDispatchCached(request, provider);
            if (scaledProvider) {
                if (processing.isScaledAttemptBudgetExhausted()) continue;
                if (processing.isScaledFallbackDeferred(request, provider)) continue;
                if (!processing.isScaledDispatchDeferred(request, provider)) {
                    var scaledProcessingResult = processing.tryScaledDispatch(
                            request, provider, singlePower, energyService, markProviderAttempt, normalPush);
                    if (scaledProcessingResult != null) {
                        return Result.accepted(scaledProcessingResult.acceptedCrafts(), true);
                    }
                    if (request.job().suspended) return Result.none();
                }
                // A scaled adapter is an optimization boundary.  Its eligibility can be
                // valid while the live target rejects the current offer (stale target,
                // backpressure, or a version-specific transport mismatch).  Do not let
                // that rejection suppress AE2's ordinary one-copy transaction; otherwise
                // an overloaded provider becomes completely undispatchable.
            }

            var fastResult = blocking ? null : fastPath.tryDispatch(
                    request, provider, singlePower, energyService, diagnostics, markProviderAttempt);
            if (fastResult != null) {
                return Result.accepted(fastResult.acceptedCrafts(), true);
            }
            if (request.job().suspended) return Result.none();

            var omniResult = blocking ? null : tryDispatchOmniSequenceBatch(
                    request, provider, singlePower, energyService, budget, diagnostics,
                    markProviderAttempt, markNormalResume);
            if (omniResult != null) {
                return omniResult;
            }
            if (request.job().suspended) return Result.none();
            if (isOmniDispatchDeferred(provider, request.pattern())) continue;

            var parallelResult = blocking ? null : tryDispatchOrdinaryBatch(
                    request, provider, singlePower, energyService, budget, diagnostics,
                    markProviderAttempt, markNormalResume);
            if (parallelResult != null) {
                return parallelResult;
            }

            // A batch is an optional optimization. If it is unavailable, rejected, or dynamically ambiguous,
            // the same provider still receives the normal one-copy fallback.
            if (!budget.canAttemptOrdinary()) {
                diagnostics.budget();
                continue;
            }

            var plan = ECOBatchDispatchPlanning.plan(request, provider, 1, 1, singlePower,
                    energyService, ECOBatchMode.SINGLE);
            if (plan == null) continue;
            if (scaledProvider && !processing.claimFallbackAttempt()) continue;
            ECOBatchAdmission admission;
            try {
                admission = ECOBatchExecutor.execute(plan, request.inputs(), request.outputs(), request.remainders(),
                        request.inventory(), request.level(), request.job().link.getCraftingID(),
                        () -> energyTransaction.reserve(energyService, singlePower, plan.craftCount()), batch -> {
                            markProviderAttempt.accept(provider);
                            budget.recordNormalProbe();
                            diagnostics.probe();
                            markNormalResume.run();
                            if (diagnostics.isActive()) clearProviderDiagnostics(provider);
                            var single = new ECOCraftingDispatchRequest(request.job(), request.candidate(),
                                    batch.identity().originalPattern(), batch.inputCounters(), batch.outputCounter(),
                                    batch.remainderCounter(), batch.craftCount(), request.inventory(), request.level());
                            return normalPush.push(single, provider)
                                    ? ECOBatchAdmission.accepted(1, false) : ECOBatchAdmission.rejected();
                        });
            } catch (ECOIndeterminateBatchException failure) {
                failAmbiguousDispatch(request, provider, "ordinary", failure);
                return Result.none();
            } catch (RuntimeException failure) {
                request.job().failPermanently("ORDINARY_BATCH_SETTLEMENT_FAILURE");
                throw failure;
            }
            if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
                diagnostics.pushRejected(request.pattern(), provider);
                continue;
            }
            try {
                accounting.apply(request, ECOCraftingDispatchResult.single(request.outputs(), request.remainders()),
                        budget::recordAcceptedNormalPush, provider);
            } catch (RuntimeException failure) {
                request.job().failPermanently("POST_ACCEPT_ORDINARY_ACCOUNTING_FAILURE");
                throw failure;
            }
            diagnostics.progress(TickHandler.instance().getCurrentTick());
            return Result.accepted(1L, false);
        }

        return Result.none();
    }

    /**
     * Ordinary-path batch dispatch for providers that explicitly own a parallel queue. This does not use the
     * FastPath cache or any FastPath recipe proof; the provider validates the complete scaled input contract.
     */
    @Nullable
    private Result tryDispatchOrdinaryBatch(
            ECOCraftingDispatchRequest request,
            ICraftingProvider provider,
            double singlePower,
            IEnergyService energyService,
            ECOCraftingDispatchBudget budget,
            ECODispatchStallDiagnostics diagnostics,
            Consumer<ICraftingProvider> markProviderAttempt,
            Runnable markNormalResume) {
        var parallelProvider = ECOParallelCraftingProviders.find(provider);
        if (parallelProvider == null) {
            return null;
        }

        int providerCapacity;
        try {
            providerCapacity = parallelProvider.eco$getAvailableParallelSlots();
        } catch (RuntimeException unavailable) {
            return null;
        }
        var plan = ECOBatchDispatchPlanning.plan(request, provider, providerCapacity, Long.MAX_VALUE,
                singlePower, energyService, ECOBatchMode.LINEAR);
        if (plan == null) return null;
        ECOBatchAdmission admission;
        try {
            admission = ECOBatchExecutor.execute(plan, request.inputs(), request.outputs(), request.remainders(),
                    request.inventory(), request.level(), request.job().link.getCraftingID(),
                    () -> energyTransaction.reserve(energyService, singlePower, plan.craftCount()), batch -> {
                        markProviderAttempt.accept(provider);
                        budget.recordNormalProbe();
                        diagnostics.probe();
                        markNormalResume.run();
                        return parallelProvider.eco$pushPatternBatch(batch.identity().originalPattern(),
                                batch.inputCounters(), batch.craftCount(), batch.jobId())
                                ? ECOBatchAdmission.accepted(batch.craftCount(), false) : ECOBatchAdmission.rejected();
                    });
        } catch (ECOIndeterminateBatchException failure) {
            failAmbiguousDispatch(request, provider, "parallel", failure);
            return Result.none();
        } catch (RuntimeException failure) {
            request.job().failPermanently("PARALLEL_BATCH_SETTLEMENT_FAILURE");
            throw failure;
        }
        if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
            diagnostics.pushRejected(request.pattern(), provider);
            return null;
        }
        long accepted = admission.acceptedCrafts();
        var result = ECOCraftingDispatchResult.batch(accepted,
                ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), accepted),
                ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), accepted));
        try {
            accounting.apply(request, result, budget::recordAcceptedNormalPush, provider);
        } catch (RuntimeException failure) {
            request.job().failPermanently("POST_ACCEPT_PARALLEL_ACCOUNTING_FAILURE");
            throw failure;
        }
        diagnostics.progress(TickHandler.instance().getCurrentTick());
        return Result.accepted(accepted, false);
    }

    @Nullable
    private Result tryDispatchOmniSequenceBatch(
            ECOCraftingDispatchRequest request,
            ICraftingProvider provider,
            double singlePower,
            IEnergyService energyService,
            ECOCraftingDispatchBudget budget,
            ECODispatchStallDiagnostics diagnostics,
            Consumer<ICraftingProvider> markProviderAttempt,
            Runnable markNormalResume) {
        if (!cn.dancingsnow.neoecoae.compat.omnisequence.ECOOmniSequenceDirectDispatch.isLoaded()) {
            return null;
        }
        try (var omni = cn.dancingsnow.neoecoae.compat.omnisequence.ECOOmniSequenceDirectDispatch.open(
                provider, request.pattern(), request.inputs(), request.allowedCrafts())) {
            if (omni == null) {
                return null;
            }

            long providerCapacity = omni.maxBatchSize();
            if (providerCapacity < 2) return null;

            var plan = ECOBatchDispatchPlanning.plan(request, provider, providerCapacity, Long.MAX_VALUE,
                    singlePower, energyService, ECOBatchMode.LINEAR);
            // MolecularBatchDispatchContext rejects one craft; use the ordinary path before debiting anything.
            if (plan == null || plan.craftCount() < 2) return null;

            ECOBatchAdmission admission;
            try {
                admission = ECOBatchExecutor.execute(plan, request.inputs(), request.outputs(), request.remainders(),
                        request.inventory(), request.level(), request.job().link.getCraftingID(),
                        () -> energyTransaction.reserve(energyService, singlePower, plan.craftCount()), batch -> {
                            markProviderAttempt.accept(provider);
                            budget.recordNormalProbe();
                            diagnostics.probe();
                            markNormalResume.run();
                            return omni.submit(batch, request.inputs());
                        });
            } catch (ECOIndeterminateBatchException failure) {
                failAmbiguousDispatch(request, provider, "omnisequence", failure);
                return Result.none();
            } catch (RuntimeException failure) {
                request.job().failPermanently("OMNISEQUENCE_BATCH_SETTLEMENT_FAILURE");
                throw failure;
            }
            if (omni.shouldDeferFurtherDispatch()) {
                omniDeferredPatterns.computeIfAbsent(provider,
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(request.pattern());
            }
            if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
                diagnostics.pushRejected(request.pattern(), provider);
                return null;
            }
            long accepted = admission.acceptedCrafts();
            var result = ECOCraftingDispatchResult.batch(accepted,
                    ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), accepted),
                    ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), accepted));
            try {
                accounting.apply(request, result, () -> {}, provider);
            } catch (RuntimeException failure) {
                request.job().failPermanently("POST_ACCEPT_OMNISEQUENCE_ACCOUNTING_FAILURE");
                throw failure;
            }
            diagnostics.progress(TickHandler.instance().getCurrentTick());
            return Result.accepted(accepted, false);
        }
    }

    private boolean isOmniDispatchDeferred(ICraftingProvider provider, IPatternDetails pattern) {
        var patterns = omniDeferredPatterns.get(provider);
        return patterns != null && patterns.contains(pattern);
    }

    private static void failAmbiguousDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            String path, RuntimeException failure) {
        String reason = "AMBIGUOUS_" + path.toUpperCase(java.util.Locale.ROOT) + "_PROVIDER_OWNERSHIP";
        request.job().failPermanently(reason);
        LOGGER.error("ECO {} provider {} threw after dispatch began; the job is suspended without replaying inputs",
                path, provider.getClass().getName(), failure);
    }

    private static void clearProviderDiagnostics(ICraftingProvider provider) {
        if (provider instanceof ECOPatternPushDiagnostics diagnostics) {
            try {
                diagnostics.neoecoae$clearPushDiagnostics();
            } catch (RuntimeException ignored) {
                // Observability must not prevent dispatch or interfere with ownership transfer.
            }
        }
    }

    @FunctionalInterface
    interface ECOCraftingNormalPush {
        boolean push(ECOCraftingDispatchRequest request, ICraftingProvider provider);
    }

    record Result(long acceptedCrafts, boolean accepted, boolean fastPath) {
        static Result accepted(long count, boolean fastPath) {
            return new Result(count, true, fastPath);
        }

        static Result none() {
            return new Result(0L, false, false);
        }
    }
}
