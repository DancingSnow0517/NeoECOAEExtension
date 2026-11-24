package cn.dancingsnow.neoecoae.grid.crafting.execution.dispatch;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.grid.crafting.amount.PlannerAmount;

import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;

import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath.ECOBatchCraftingHelper;

/**
 * Optional batch dispatch boundary. It owns FastPath preparation, energy reservation, physical push, and rollback;
 * the task scheduler only observes an accepted immutable result.
 */
public final class ECOCraftingFastPathDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;

    public ECOCraftingFastPathDispatcher(ECOCraftingEnergyTransaction energyTransaction,
                                  ECOCraftingDispatchAccounting accounting) {
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    boolean supportsBatch(ICraftingProvider provider) {
        return ECOFastPathFacade.supports(provider);
    }

    @Nullable
    java.math.BigInteger tryExactDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            double singlePower, IEnergyService energyService, Consumer<ICraftingProvider> recordAttempt) {
        return tryExactDispatch(request, provider, singlePower, energyService, recordAttempt, null);
    }

    @Nullable
    java.math.BigInteger tryExactDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            double singlePower, IEnergyService energyService, Consumer<ICraftingProvider> recordAttempt,
            @Nullable Long dispatchLimit) {
        if (!request.job().exactOrder
                || !(request.inventory() instanceof cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory inventory)) {
            return null;
        }
        var target = ECOFastPathFacade.resolveProvider(provider);
        if (target == null) return null;
        var initialRequest = request.job().tasks.get(request.pattern()).remainingQuantity();
        var requested = dispatchLimit == null ? initialRequest
            : initialRequest.min(PlannerAmount.of(dispatchLimit));
        var runtime = request.job().executionRuntime;
        if (runtime != null) requested = runtime.exactAllowance(request.candidate(), requested);
        if (!Double.isFinite(singlePower) || singlePower < 0) return null;
        if (singlePower > 0) {
            requested = PlannerAmount.of(ECOBatchCraftingHelper.maxAffordableCrafts(singlePower, requested.toBigInteger(),
                offer -> energyService.extractAEPower(offer, appeng.api.config.Actionable.SIMULATE,
                    appeng.api.config.PowerMultiplier.CONFIG)));
            if (requested.signum() <= 0) return null;
        }
        var context = cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext.create(request.pattern(),
            request.inputs(), request.outputs(), request.remainders(), request.level(), request.job().link.getCraftingID());
        var batch = cn.dancingsnow.neoecoae.grid.crafting.execution.batch.plan.ECOExactBatchPlanner.prepare(
            target, context, inventory, requested,
            runtime == null ? java.util.Map.of() : runtime.protectedStartupSeed(request.candidate()));
        if (batch == null) return null;
        var energy = energyTransaction.reserve(energyService, singlePower, batch.craftCount());
        if (energy == null) return null;
        recordAttempt.accept(provider);
        try {
            if (!batch.submit(energy)) return null;
        } catch (ECOIndeterminateBatchException failure) {
            request.job().failPermanently("INDETERMINATE_EXACT_BATCH_ACCEPTANCE");
            throw failure;
        }
        try {
            accounting.applyExact(request, batch, () -> {}, provider);
        } catch (RuntimeException failure) {
            request.job().failPermanently("POST_ACCEPT_EXACT_BATCH_ACCOUNTING_FAILURE");
            throw failure;
        }
        return batch.craftCount();
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

        long batchCount = batch.craftCount();
        boolean accepted = false;
        recordProviderAttempt.accept(provider);
        try {
            accepted = batch.submit(ignored -> energyTransaction.reserve(energyService, singlePower, batchCount));
        } catch (ECOIndeterminateBatchException failure) {
            request.job().failPermanently("INDETERMINATE_FAST_PATH_ACCEPTANCE");
            throw failure;
        } catch (RuntimeException failure) {
            if (NEConfig.ecoDispatchWatchdogDebug) LOGGER.warn("Atomic batch rejected; inputs restored, trying ordinary provider push", failure);
        }
        if (!accepted) {
            diagnostics.batchRejected(request.pattern(), provider);
            return null;
        }

        ECOCraftingDispatchResult result = ECOCraftingDispatchResult.batch(
                batch.craftCount(), batch.outputs(), batch.remainders());
        try {
            accounting.apply(request, result, () -> {}, provider);
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
