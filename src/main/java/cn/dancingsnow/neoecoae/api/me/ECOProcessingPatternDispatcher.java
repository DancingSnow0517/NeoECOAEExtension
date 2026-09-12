package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.pattern.AEProcessingPattern;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
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
        return BatchProvider.METHOD != null && BatchProvider.TYPE.isInstance(provider)
                && (pattern == null || isProcessingPattern(pattern));
    }

    @Nullable
    ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request,
            ICraftingProvider provider, double oneCopyPower,
            appeng.api.networking.energy.IEnergyService energyService,
            Consumer<ICraftingProvider> markProviderAttempt) {
        if (!supports(provider, request.pattern())) {
            return null;
        }
        IPatternDetails providerPattern;
        try {
            providerPattern = providerLookupPattern(request.pattern());
        } catch (RuntimeException unavailable) {
            return null;
        }
        if (!(providerPattern instanceof AEProcessingPattern)) return null;
        long remaining = Math.min(request.allowedCrafts(), TICK_BUDGET - usedThisTick);
        if (remaining <= 0L) return null;

        var state = states.computeIfAbsent(provider, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(request.pattern(), ignored -> new ProbeState());
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
                leftover = (long) BatchProvider.METHOD.invoke(provider, providerPattern, oneCopy, offer);
            } catch (InvocationTargetException | IllegalAccessException | LinkageError failure) {
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
        Method method = ProviderLookup.METHOD;
        if (method == null) return pattern;
        try {
            Object result = method.invoke(null, pattern);
            return result instanceof IPatternDetails details ? details : pattern;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return pattern;
        }
    }

    @Nullable
    private static Method resolveProviderLookup() {
        try {
            Class<?> delegates = Class.forName(
                    "com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates",
                    false, ECOProcessingPatternDispatcher.class.getClassLoader());
            return delegates.getMethod("forProviderLookup", IPatternDetails.class);
        } catch (ReflectiveOperationException | LinkageError | SecurityException unavailable) {
            return null;
        }
    }

    private static final class ProviderLookup {
        // Mod classes are fixed for this class loader, including an absent optional compatibility class.
        private static final Method METHOD = resolveProviderLookup();
    }

    /** Resolves both the legacy and refactored Thunderbolt batch-provider contracts. */
    private static final class BatchProvider {
        private static final String[] TYPE_NAMES = {
                "com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider",
                "com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider"
        };
        private static final Class<?> TYPE = resolveType();
        private static final Method METHOD = resolveMethod();

        private static Class<?> resolveType() {
            for (String name : TYPE_NAMES) {
                try {
                    return Class.forName(name, false, ECOProcessingPatternDispatcher.class.getClassLoader());
                } catch (ClassNotFoundException | LinkageError ignored) {
                    // Try the other known Thunderbolt API package.
                }
            }
            return MissingType.class;
        }

        private static Method resolveMethod() {
            if (TYPE == MissingType.class) return null;
            try {
                return TYPE.getMethod("pushBatch", IPatternDetails.class, appeng.api.stacks.KeyCounter[].class,
                        long.class);
            } catch (NoSuchMethodException | LinkageError ignored) {
                return null;
            }
        }

        private static final class MissingType {
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
