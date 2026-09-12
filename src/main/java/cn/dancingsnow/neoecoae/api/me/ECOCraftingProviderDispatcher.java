package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOPatternPushDiagnostics;

import java.util.List;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import appeng.hooks.ticking.TickHandler;

/**
 * Tries providers in the already-selected order. This is the boundary between task scheduling and provider-level
 * dispatch; it knows the optional FastPath and ordinary fallback, but not phase/candidate selection.
 */
final class ECOCraftingProviderDispatcher {
    private final ECOCraftingFastPathDispatcher fastPath;
    private final ECOProcessingPatternDispatcher processing;
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;

    ECOCraftingProviderDispatcher(ECOCraftingFastPathDispatcher fastPath,
            ECOCraftingEnergyTransaction energyTransaction, ECOCraftingDispatchAccounting accounting) {
        this.fastPath = fastPath;
        this.processing = new ECOProcessingPatternDispatcher(energyTransaction, accounting);
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    void beginTick(long gameTick) {
        processing.beginTick(gameTick);
    }

    boolean isEligible(ICraftingProvider provider, ECOCraftingDispatchBudget budget) {
        return budget.canAttemptOrdinary()
                || fastPath.supportsBatch(provider)
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

            var fastResult = fastPath.tryDispatch(
                    request, provider, singlePower, energyService, diagnostics, markProviderAttempt);
            if (fastResult != null) {
                return Result.accepted(fastResult.acceptedCrafts(), true);
            }

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

            boolean inputsExtracted;
            try {
                inputsExtracted = ECOBatchCraftingHelper.extractExact(request.inventory(), ordinaryInputStacks);
            } catch (RuntimeException failure) {
                reservation.refund();
                throw failure;
            }
            if (!inputsExtracted) {
                // extractExact already restored the partial extraction. Do not replay the full resolved input set.
                reservation.refund();
                break;
            }

            boolean accepted = false;
            try {
                markProviderAttempt.accept(provider);
                budget.recordNormalProbe();
                diagnostics.probe();
                // Preserve the next-candidate resume point even when this provider rejects the push.
                markNormalResume.run();
                if (diagnostics.isActive()) {
                    clearProviderDiagnostics(provider);
                }
                accepted = normalPush.push(request, provider);
                if (!accepted) {
                    diagnostics.pushRejected(request.pattern(), provider);
                    continue;
                }

                reservation.commit();
                accounting.apply(request,
                        ECOCraftingDispatchResult.single(request.outputs(), request.remainders()),
                        budget::recordAcceptedNormalPush);
                diagnostics.progress(TickHandler.instance().getCurrentTick());
                return Result.accepted(1L, false);
            } finally {
                // A rejected ordinary provider does not own the extracted inputs; the next provider may try the same
                // exact snapshot. An accepted provider owns it and the reservation is already committed.
                if (!accepted) {
                    appeng.crafting.execution.CraftingCpuHelper.reinjectPatternInputs(
                            request.inventory(), request.inputs());
                    reservation.refund();
                }
            }
        }

        return Result.none();
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
