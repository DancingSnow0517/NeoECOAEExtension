package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;

import java.util.function.Consumer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Set<String> EXACT_DIAGNOSTICS = ConcurrentHashMap.newKeySet();

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

    @Nullable
    java.math.BigInteger tryExactDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            double singlePower, IEnergyService energyService, Consumer<ICraftingProvider> recordAttempt) {
        if (!request.job().exactOrder
                || !(request.inventory() instanceof cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory inventory)) {
            exactDiagnostic(request, provider, "not-an-exact-inventory", null);
            return null;
        }
        var target = ECOFastPathFacade.resolveProvider(provider);
        if (target == null) {
            exactDiagnostic(request, provider, "no-fast-path-adapter", null);
            return null;
        }
        var requested = request.job().tasks.get(request.pattern()).remainingExact();
        exactDiagnostic(request, provider, "requested", requested);
        var runtime = request.job().executionRuntime;
        if (runtime != null) requested = runtime.exactAllowance(request.candidate(), requested);
        exactDiagnostic(request, provider, "after-runtime-allowance", requested);
        if (!Double.isFinite(singlePower) || singlePower < 0) return null;
        if (singlePower > 0) {
            var unit = new java.math.BigDecimal(singlePower);
            double offer = Math.min(Double.MAX_VALUE, unit.multiply(new java.math.BigDecimal(requested)).doubleValue());
            double available = energyService.extractAEPower(offer, appeng.api.config.Actionable.SIMULATE,
                appeng.api.config.PowerMultiplier.CONFIG);
            if (!Double.isFinite(available) || available <= 0) {
                exactDiagnostic(request, provider, "energy-limit", requested);
                return null;
            }
            requested = requested.min(new java.math.BigDecimal(available).divideToIntegralValue(unit).toBigInteger());
        }
        var context = cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext.create(request.pattern(),
            request.inputs(), request.outputs(), request.remainders(), request.level(), request.job().link.getCraftingID());
        var batch = cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExactBatchCraftingExecutor.prepare(
            target, context, inventory, requested, runtime == null ? java.util.Map.of() : runtime.protectedStartupSeed(request.candidate()));
        if (batch == null) {
            exactDiagnostic(request, provider, "provider-rejected-or-long-only", requested);
            return null;
        }
        exactDiagnostic(request, provider, "provider-admitted", batch.craftCount());
        var registration = ECOUselessDynamicOutputBridge.prepareExact(dynamicOutputOwner, request.pattern(), batch.craftCount());
        if (registration == null) {
            exactDiagnostic(request, provider, "dynamic-output-registration-is-long-only", batch.craftCount());
            return null;
        }
        var energy = energyTransaction.reserve(energyService, singlePower, batch.craftCount());
        if (energy == null) {
            exactDiagnostic(request, provider, "exact-energy-reservation-failed", batch.craftCount());
            return null;
        }
        recordAttempt.accept(provider);
        try {
            if (!batch.submit(energy)) {
                exactDiagnostic(request, provider, "provider-commit-rejected", batch.craftCount());
                return null;
            }
        } catch (ECOIndeterminateBatchException failure) {
            request.job().failPermanently("INDETERMINATE_EXACT_BATCH_ACCEPTANCE");
            throw failure;
        }
        try {
            accounting.applyExact(request, batch, () -> registration.commit(request.job().link.getCraftingID(),
                request.job().finalOutput == null ? null : request.job().finalOutput.what()), provider);
        } catch (RuntimeException failure) {
            request.job().failPermanently("POST_ACCEPT_EXACT_BATCH_ACCOUNTING_FAILURE");
            throw failure;
        }
        return batch.craftCount();
    }

    private static void exactDiagnostic(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            String reason, java.math.BigInteger amount) {
        var jobId = request.job().link.getCraftingID();
        var key = jobId + ":" + provider.getClass().getName() + ":" + reason;
        if (EXACT_DIAGNOSTICS.add(key)) {
            LOGGER.warn("[big-order] exact dispatch diagnostic reason={} requested={} provider={} job={}",
                reason, amount, provider.getClass().getName(), jobId);
        }
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
                safeWaitingBatch(request, singlePower),
                singlePower,
                energyService,
                request.level(),
                request.job().link.getCraftingID(), request.job().exactOrder);
        if (batch != null && request.job().executionRuntime != null
                && !(request.job().exactOrder
                    ? request.job().executionRuntime.preservesStartupSeeds(request.candidate(), batch.exactInputTotal(), request.inventory())
                    : request.job().executionRuntime.preservesStartupSeeds(request.candidate(), batch.inputTotal(), request.inventory()))) {
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

        long batchCount = batch.craftCount();
        boolean accepted = false;
        recordProviderAttempt.accept(provider);
        try {
            accepted = batch.submit(ignored -> energyTransaction.reserve(energyService, singlePower, batchCount));
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

    static long safeWaitingBatch(ECOCraftingDispatchRequest request, double singlePower) {
        long limit = Math.min(request.allowedCrafts(), ECOCraftingEnergyTransaction.maxSafeCrafts(singlePower));
        if (request.job().exactOrder) return limit;
        var perCraft = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var entry : request.outputs()) perCraft.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            for (var entry : request.remainders()) perCraft.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            for (var entry : perCraft.entrySet()) {
                if (entry.getValue() <= 0L) return 0L;
                long waiting = request.job().waitingFor.list.get(entry.getKey());
                if (waiting < 0L) return 0L;
                limit = Math.min(limit, (Long.MAX_VALUE - waiting) / entry.getValue());
            }
            return limit;
        } catch (ArithmeticException overflow) {
            return 0L;
        }
    }
}
