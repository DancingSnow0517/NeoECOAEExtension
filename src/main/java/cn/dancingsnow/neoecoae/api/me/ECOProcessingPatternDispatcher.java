package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.pattern.AEProcessingPattern;
import com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider;
import java.lang.reflect.Method;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Processing-pattern-only adaptive batch dispatcher. */
final class ECOProcessingPatternDispatcher {
    private static final long TICK_BUDGET = 1_000_000L;
    private static final long WARM_START = 50_000L;
    private static final long[] PROBE_LADDER = {1L, 64L, 1_024L, 8_192L, WARM_START};

    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, IdentityHashMap<IPatternDetails, ProbeState>> states =
            new IdentityHashMap<>();
    private long tick = Long.MIN_VALUE;
    private long usedThisTick;

    ECOProcessingPatternDispatcher(ECOCraftingEnergyTransaction energyTransaction,
            ECOCraftingDispatchAccounting accounting) {
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    void beginTick(long gameTick) {
        if (tick != gameTick) {
            tick = gameTick;
            usedThisTick = 0L;
        }
    }

    boolean supports(ICraftingProvider provider, @Nullable IPatternDetails pattern) {
        return provider instanceof IBatchCraftingProvider
                && (pattern == null || isProcessingPattern(pattern));
    }

    @Nullable
    ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request,
            ICraftingProvider provider, double oneCopyPower,
            appeng.api.networking.energy.IEnergyService energyService,
            Consumer<ICraftingProvider> markProviderAttempt) {
        if (!isProcessingPattern(request.pattern())
                || !(provider instanceof IBatchCraftingProvider batchProvider)) {
            return null;
        }
        long remaining = Math.min(request.allowedCrafts(), TICK_BUDGET - usedThisTick);
        if (remaining <= 0L) return null;

        var state = states.computeIfAbsent(provider, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(request.pattern(), ignored -> new ProbeState());
        IPatternDetails providerPattern = providerLookupPattern(request.pattern());
        long acceptedTotal = 0L;
        while (remaining > 0L) {
            long offer = Math.min(remaining, Math.max(1L, state.nextProbe));
            var oneCopy = request.inputs();
            var totalInputs = ECOBatchCraftingHelper.multiply(
                    cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks.copyCounters(oneCopy), offer);
            if (!ECOBatchCraftingHelper.extractExact(request.inventory(), totalInputs)) break;

            var reservation = energyTransaction.reserve(energyService, oneCopyPower * offer);
            if (reservation == null) {
                ECOBatchCraftingHelper.insertAll(request.inventory(), totalInputs);
                break;
            }

            markProviderAttempt.accept(provider);
            long leftover;
            try {
                leftover = batchProvider.pushBatch(providerPattern, oneCopy, offer);
            } catch (Throwable failure) {
                leftover = offer;
            }
            if (leftover < 0L || leftover > offer) leftover = offer;
            long accepted = offer - leftover;
            if (leftover > 0L) {
                ECOBatchCraftingHelper.insertAll(request.inventory(),
                        ECOBatchCraftingHelper.multiply(
                                cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks.copyCounters(oneCopy),
                                leftover));
            }
            if (accepted <= 0L) {
                reservation.refund();
                state.onFailure(offer);
                break;
            }

            reservation.refundUnaccepted(accepted, offer);
            state.onSuccess(offer, accepted);
            acceptedTotal = saturatingAdd(acceptedTotal, accepted);
            usedThisTick = saturatingAdd(usedThisTick, accepted);
            remaining -= accepted;
            if (accepted < offer || request.job().tasks.get(request.pattern()).value <= 0L) break;
        }
        if (acceptedTotal <= 0L) return null;
        var outputs = ECOBatchCraftingHelper.multiply(
                cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks.copyCounter(request.outputs()),
                acceptedTotal);
        var remainders = ECOBatchCraftingHelper.multiply(
                cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks.copyCounter(request.remainders()),
                acceptedTotal);
        var result = ECOCraftingDispatchResult.batch(acceptedTotal, outputs, remainders);
        accounting.apply(request, result, () -> {});
        return result;
    }

    private static boolean isProcessingPattern(IPatternDetails pattern) {
        try {
            return providerLookupPattern(pattern) instanceof AEProcessingPattern;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static IPatternDetails providerLookupPattern(IPatternDetails pattern) {
        try {
            Class<?> delegates = Class.forName(
                    "com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates",
                    false, ECOProcessingPatternDispatcher.class.getClassLoader());
            Method method = delegates.getMethod("forProviderLookup", IPatternDetails.class);
            Object result = method.invoke(null, pattern);
            return result instanceof IPatternDetails details ? details : pattern;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return pattern;
        }
    }

    private static long saturatingAdd(long left, long right) {
        return right <= 0L || left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static final class ProbeState {
        private long nextProbe = 1L;
        private long provenBatch;

        private void onSuccess(long offered, long accepted) {
            if (accepted < offered) {
                provenBatch = Math.max(1L, accepted);
                nextProbe = Math.max(1L, Math.min(offered / 2L, provenBatch));
                return;
            }
            provenBatch = Math.max(provenBatch, offered);
            for (int i = 0; i < PROBE_LADDER.length - 1; i++) {
                if (offered <= PROBE_LADDER[i]) {
                    nextProbe = PROBE_LADDER[i + 1];
                    return;
                }
            }
            nextProbe = offered >= Long.MAX_VALUE / 2L ? Long.MAX_VALUE : offered * 2L;
        }

        private void onFailure(long offered) {
            long base = provenBatch > 0L ? provenBatch : offered;
            nextProbe = Math.max(1L, base / 2L);
        }
    }
}
