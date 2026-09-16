package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOPatternPushDiagnostics;

import java.util.List;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import appeng.hooks.ticking.TickHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries providers in the already-selected order. This is the boundary between task scheduling and provider-level
 * dispatch; it knows the optional FastPath and ordinary fallback, but not phase/candidate selection.
 */
final class ECOCraftingProviderDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.dispatch");
    private final ECOCraftingFastPathDispatcher fastPath;
    private final ECOProcessingPatternDispatcher processing;
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;

    ECOCraftingProviderDispatcher(ECOCraftingCPULogic owner, ECOCraftingFastPathDispatcher fastPath,
            ECOCraftingEnergyTransaction energyTransaction, ECOCraftingDispatchAccounting accounting) {
        this.fastPath = fastPath;
        this.processing = new ECOProcessingPatternDispatcher(owner, energyTransaction, accounting);
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    void beginTick(long gameTick) {
        processing.beginTick(gameTick);
    }

    void reset() {
        processing.reset();
    }

    boolean isEligible(ICraftingProvider provider, ECOCraftingDispatchBudget budget) {
        return budget.canAttemptOrdinary()
                || fastPath.supportsBatch(provider)
                || provider instanceof ECOParallelCraftingProvider
                || processing.supports(provider, null);
    }

    Result dispatchCandidate(ECOCraftingDispatchRequest request, List<ICraftingProvider> providers,
            ECOCraftingDispatchBudget budget, IEnergyService energyService,
            ECODispatchStallDiagnostics diagnostics, Consumer<ICraftingProvider> markProviderAttempt,
            Runnable markNormalResume, ECOCraftingNormalPush normalPush) {
        double singlePower = CraftingCpuHelper.calculatePatternPower(request.inputs());
        List<appeng.api.stacks.GenericStack> ordinaryInputStacks = null;

        for (var provider : providers) {
            var processingResult = processing.tryDispatch(
                    request, provider, singlePower, energyService, markProviderAttempt);
            if (processingResult != null) {
                return Result.accepted(processingResult.acceptedCrafts(), true);
            }
            if (request.job().suspended) return Result.none();
            // Native batch providers already own their one-copy fallback and target recovery.
            if (processing.supports(provider, request.pattern())) continue;

            var fastResult = fastPath.tryDispatch(
                    request, provider, singlePower, energyService, diagnostics, markProviderAttempt);
            if (fastResult != null) {
                return Result.accepted(fastResult.acceptedCrafts(), true);
            }
            if (request.job().suspended) return Result.none();

            var parallelResult = tryDispatchOrdinaryBatch(
                    request, provider, singlePower, energyService, budget, diagnostics,
                    markProviderAttempt, markNormalResume);
            if (parallelResult != null) {
                return parallelResult;
            }

            if (ECOProcessingPatternDispatcher.supportsScaledDispatch(request, provider)) {
                var scaledProcessingResult = processing.tryScaledDispatch(
                        request, provider, singlePower, energyService, markProviderAttempt, normalPush);
                if (scaledProcessingResult != null) {
                    return Result.accepted(scaledProcessingResult.acceptedCrafts(), true);
                }
                if (request.job().suspended) return Result.none();
                // This ramp includes 1x and same-visit recovery; do not replay it via ordinary fallback.
                continue;
            }
            if (request.job().suspended) return Result.none();

            // A batch is an optional optimization. If it is unavailable, rejected, or dynamically ambiguous,
            // the same provider still receives the normal one-copy fallback.
            if (!budget.canAttemptOrdinary()) {
                diagnostics.budget();
                continue;
            }

            double availablePower = energyService.extractAEPower(
                    singlePower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (availablePower < singlePower - 0.01D) {
                diagnostics.insufficientPower(singlePower, availablePower);
                // Power is shared by all providers for this pattern; retrying the rest of this snapshot has no value.
                break;
            }
            if (ordinaryInputStacks == null) {
                ordinaryInputStacks = cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks
                        .copyCounters(request.inputs());
            }

            var reservation = energyTransaction.reserve(energyService, singlePower);
            if (reservation == null) {
                diagnostics.insufficientPower(singlePower, 0.0D);
                break;
            }

            ECOProviderInputTransaction inputTransaction;
            try {
                inputTransaction = ECOProviderInputTransaction.begin(request.inventory(), ordinaryInputStacks);
            } catch (RuntimeException failure) {
                reservation.refund();
                throw failure;
            }
            if (inputTransaction == null) {
                reservation.refund();
                break;
            }

            boolean accepted = false;
            boolean ownershipUncertain = false;
            try {
                markProviderAttempt.accept(provider);
                budget.recordNormalProbe();
                diagnostics.probe();
                // Preserve the next-candidate resume point even when this provider rejects the push.
                markNormalResume.run();
                if (diagnostics.isActive()) {
                    clearProviderDiagnostics(provider);
                }
                try {
                    accepted = normalPush.push(request, provider);
                } catch (RuntimeException failure) {
                    ownershipUncertain = true;
                    inputTransaction.transferOwnership();
                    failAmbiguousDispatch(request, provider, "ordinary", failure);
                    reservation.commit();
                    return Result.none();
                }
                if (!accepted && !ownershipUncertain) {
                    diagnostics.pushRejected(request.pattern(), provider);
                    continue;
                }

                inputTransaction.transferOwnership();
                reservation.commit();
                try {
                    accounting.apply(request,
                            ECOCraftingDispatchResult.single(request.outputs(), request.remainders()),
                            budget::recordAcceptedNormalPush, provider);
                } catch (RuntimeException failure) {
                    request.job().failPermanently("POST_ACCEPT_ORDINARY_ACCOUNTING_FAILURE");
                    throw failure;
                }
                diagnostics.progress(TickHandler.instance().getCurrentTick());
                return Result.accepted(1L, false);
            } finally {
                // A rejected ordinary provider does not own the extracted inputs; the next provider may try the same
                // exact snapshot. An accepted provider owns it and the reservation is already committed.
                if (!accepted && !ownershipUncertain) {
                    inputTransaction.rollback();
                    reservation.refund();
                }
            }
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
        if (!(provider instanceof ECOParallelCraftingProvider parallelProvider)) {
            return null;
        }

        int providerCapacity;
        try {
            providerCapacity = parallelProvider.eco$getAvailableParallelSlots();
        } catch (RuntimeException unavailable) {
            return null;
        }
        long requested = Math.min(request.allowedCrafts(), Math.max(0L, providerCapacity));
        if (requested <= 0L) {
            return null;
        }

        List<appeng.api.stacks.GenericStack> perCraftInputs = ECOFastPathStacks.copyCounters(request.inputs());
        long materialLimit = ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                perCraftInputs,
                ECOFastPathStacks.copyCounter(request.outputs()),
                ECOFastPathStacks.copyCounter(request.remainders()));
        requested = Math.min(requested, materialLimit);
        requested = ECOBatchCraftingHelper.maxCraftsFromInventory(request.inventory(), perCraftInputs, requested);
        requested = ECOBatchCraftingHelper.maxAffordableCrafts(
                singlePower,
                requested,
                amount -> energyService.extractAEPower(
                        amount, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        if (requested <= 0L) {
            return null;
        }

        List<appeng.api.stacks.GenericStack> totalInputs =
                ECOBatchCraftingHelper.multiply(perCraftInputs, requested);
        KeyCounter[] scaledCounters = ECOCraftingDispatchStacks.scaleCounters(request.inputs(), requested);
        double batchPower = singlePower * requested;
        if (!Double.isFinite(batchPower) || batchPower < 0.0D) {
            return null;
        }

        var reservation = energyTransaction.reserve(energyService, batchPower);
        if (reservation == null) {
            diagnostics.insufficientPower(batchPower, 0.0D);
            return null;
        }

        var inputTransaction = ECOProviderInputTransaction.begin(request.inventory(), totalInputs);
        if (inputTransaction == null) {
            reservation.refund();
            return null;
        }

        boolean accepted = false;
        boolean ownershipUncertain = false;
        try {
            markProviderAttempt.accept(provider);
            budget.recordNormalProbe();
            diagnostics.probe();
            markNormalResume.run();
            try {
                accepted = parallelProvider.eco$pushPatternBatch(
                        request.pattern(), scaledCounters, requested, request.job().link.getCraftingID());
            } catch (RuntimeException failure) {
                ownershipUncertain = true;
                inputTransaction.transferOwnership();
                failAmbiguousDispatch(request, provider, "parallel", failure);
                reservation.commit();
                return Result.none();
            }
            if (!accepted && !ownershipUncertain) {
                diagnostics.pushRejected(request.pattern(), provider);
                return null;
            }

            inputTransaction.transferOwnership();
            reservation.commit();
            var result = ECOCraftingDispatchResult.batch(
                    requested,
                    ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), requested),
                    ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), requested));
            try {
                accounting.apply(request, result, budget::recordAcceptedNormalPush, provider);
            } catch (RuntimeException failure) {
                request.job().failPermanently("POST_ACCEPT_PARALLEL_ACCOUNTING_FAILURE");
                throw failure;
            }
            diagnostics.progress(TickHandler.instance().getCurrentTick());
            return Result.accepted(requested, false);
        } finally {
            if (!accepted && !ownershipUncertain) {
                inputTransaction.rollback();
                reservation.refund();
            }
        }
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
