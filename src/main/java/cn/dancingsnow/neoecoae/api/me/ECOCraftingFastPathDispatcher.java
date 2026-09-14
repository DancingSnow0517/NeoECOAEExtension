package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;

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
        return ECOFastPathFacade.supports(provider);
    }

    /**
     * Attempts only the optional batch path. {@code null} means ordinary one-copy fallback remains available.
     */
    @Nullable
    ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
                                          double singlePower, IEnergyService energyService, ECODispatchStallDiagnostics diagnostics,
                                          Consumer<ICraftingProvider> recordProviderAttempt) {
        ECOFastPathFacade.PreparedBatch batch = ECOFastPathFacade.prepare(
                provider,
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

        boolean accepted = false;
        recordProviderAttempt.accept(provider);
        try {
            accepted = batch.submit(amount -> energyTransaction.reserve(energyService, amount));
        } catch (ECOIndeterminateBatchException failure) {
            request.job().failPermanently("INDETERMINATE_FAST_PATH_ACCEPTANCE");
            throw failure;
        } catch (RuntimeException failure) {
            LOGGER.warn("Atomic batch rejected; inputs restored, trying ordinary provider push", failure);
        }
        if (!accepted) {
            diagnostics.batchRejected(request.pattern(), provider);
            return null;
        }

        ECOCraftingDispatchResult result = ECOCraftingDispatchResult.batch(
                batch.craftCount(), batch.outputs(), batch.remainders());
        try {
            accounting.apply(request, result, () -> {
                try {
                    registration.commit(request.job().link.getCraftingID(),
                            request.job().finalOutput == null ? null : request.job().finalOutput.what());
                } catch (RuntimeException failure) {
                    LOGGER.error("Accepted batch could not register Useless dynamic outputs", failure);
                }
            }, provider);
        } catch (RuntimeException failure) {
            request.job().failPermanently("POST_ACCEPT_FAST_PATH_ACCOUNTING_FAILURE");
            throw failure;
        }
        diagnostics.progress(TickHandler.instance().getCurrentTick());
        return result;
    }
}
