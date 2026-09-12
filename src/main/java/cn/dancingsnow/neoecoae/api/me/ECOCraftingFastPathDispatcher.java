package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessBatchProviderBridge;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingExecutor;

/**
 * Optional batch dispatch boundary. It owns FastPath preparation, energy reservation, physical push, and rollback;
 * the task scheduler only observes an accepted immutable result.
 */
final class ECOCraftingFastPathDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private final Object dynamicOutputOwner;
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;

    ECOCraftingFastPathDispatcher(Object dynamicOutputOwner, ECOCraftingEnergyTransaction energyTransaction,
            ECOCraftingDispatchAccounting accounting) {
        this.dynamicOutputOwner = dynamicOutputOwner;
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    boolean supportsBatch(ICraftingProvider provider) {
        return provider instanceof ECOFastPathDispatchProvider
                || ECOUselessBatchProviderBridge.supports(provider);
    }

    /**
     * Attempts only the optional batch path. {@code null} means ordinary one-copy fallback remains available.
     */
    @Nullable
    ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            double singlePower, IEnergyService energyService, ECODispatchStallDiagnostics diagnostics,
            Consumer<ICraftingProvider> recordProviderAttempt) {
        var batchProvider = provider instanceof ECOFastPathDispatchProvider nativeProvider
                ? nativeProvider : ECOUselessBatchProviderBridge.adapt(provider);
        if (batchProvider == null) return null;

        ECOBatchCraftingExecutor.PreparedBatch batch = ECOBatchCraftingExecutor.prepare(
                batchProvider,
                request.pattern(),
                request.inputs(),
                request.outputs(),
                request.remainders(),
                request.inventory(),
                request.allowedCrafts(),
                singlePower,
                energyService,
                request.level(),
                request.job().link.getCraftingID());
        if (batch != null && request.job().executionRuntime != null
                && !request.job().executionRuntime.preservesStartupSeeds(
                        request.candidate(), batch.inputTotal(), request.inventory())) {
            // The batch calculator sees the physical CPU inventory. Do not cross another phase's seed lease;
            // ordinary fallback still uses the protected input preview selected by the scheduler.
            batch = null;
        }
        if (batch == null) return null;

        ECOUselessDynamicOutputBridge.Registration registration;
        try {
            registration = ECOUselessDynamicOutputBridge.prepare(
                    dynamicOutputOwner, request.pattern(), batch.craftCount());
        } catch (RuntimeException failure) {
            LOGGER.warn("Batch dynamic output registration unavailable; trying ordinary provider push", failure);
            return null;
        }
        if (registration == null) return null;

        var reservation = energyTransaction.reserve(energyService, batch.power());
        if (reservation == null) {
            diagnostics.insufficientPower(batch.power(), 0.0D);
            return null;
        }

        boolean accepted = false;
        try {
            recordProviderAttempt.accept(provider);
            try {
                accepted = batch.push(request.inventory());
            } catch (RuntimeException failure) {
                LOGGER.warn("Atomic batch rejected; inputs restored, trying ordinary provider push", failure);
            }
            if (!accepted) {
                diagnostics.batchRejected(request.pattern(), provider);
                reservation.refund();
                return null;
            }

            reservation.commit();
            ECOCraftingDispatchResult result = ECOCraftingDispatchResult.batch(
                    batch.craftCount(), batch.outputs(), batch.remainders());
            accounting.apply(request, result, () -> {
                try {
                    registration.commit(request.job().link.getCraftingID(),
                            request.job().finalOutput == null ? null : request.job().finalOutput.what());
                } catch (RuntimeException failure) {
                    // The provider already owns this batch. Never replay its inputs or task on a notification failure.
                    LOGGER.error("Accepted batch could not register Useless dynamic outputs", failure);
                }
            });
            diagnostics.progress(TickHandler.instance().getCurrentTick());
            return result;
        } finally {
            // PreparedBatch restores its exact input total when the provider rejects or throws. Energy is settled
            // independently so an accounting failure cannot make the physical batch replayable.
            if (!accepted) reservation.refund();
        }
    }
}
