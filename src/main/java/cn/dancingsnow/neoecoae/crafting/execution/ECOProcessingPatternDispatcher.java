package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.crafting.execution.batch.*;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProviderPatternIntrospection;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern;
import cn.dancingsnow.neoecoae.compat.mekenergistics.ECOMekEnergisticsBatchCapability;
import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;
import cn.dancingsnow.neoecoae.compat.advanced_ae.ECOAdvancedAEPatternScaling;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * ECO owns processing batch growth; optional adapters only transport the selected batch.
 *
 * <p>The per-tick budget counts provider submissions, never crafts: one counted submission carrying a billion
 * copies costs one attempt. Batch size comes from the task, materials, energy, cycle seeds, the target's
 * capacity and numeric bounds.
 */
final class ECOProcessingPatternDispatcher {
    private static final long ELIGIBILITY_CACHE_TICKS = 10L;
    private static final long SCALE_PROBE_INTERVAL_TICKS = 5L;
    static final int MAX_ATTEMPTS_PER_TICK = 64;
    /** One continuously accepting pattern may use half of a tick; other ready tasks keep the rest. */
    static final int MAX_ATTEMPTS_PER_PATTERN_PER_TICK = MAX_ATTEMPTS_PER_TICK / 2;
    static final int MAX_ATTEMPTS_PER_VISIT = 32;
    private static final ClassValue<Boolean> HAS_LOGIC = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                type.getMethod("getLogic");
                return true;
            } catch (NoSuchMethodException | LinkageError absent) {
                return false;
            }
        }
    };
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingEnergyTransaction energy;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, ProbeState>> states = new Reference2ObjectOpenHashMap<>();
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, Eligibility>> eligibility = new Reference2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<IPatternDetails> patternAttempts = new Reference2IntOpenHashMap<>();
    private long tick = Long.MIN_VALUE;
    private int attempts;

    ECOProcessingPatternDispatcher(ECOCraftingCPULogic owner, ECOCraftingEnergyTransaction energy,
            ECOCraftingDispatchAccounting accounting) { this.owner = owner; this.energy = energy; this.accounting = accounting; }
    void beginTick(long gameTick) { if (tick != gameTick) { tick = gameTick; attempts = 0; patternAttempts.clear(); } }
    void reset() { states.clear(); eligibility.clear(); patternAttempts.clear(); tick = Long.MIN_VALUE; attempts = 0; }

    boolean isScaledAttemptBudgetExhausted() {
        return attempts >= MAX_ATTEMPTS_PER_TICK;
    }

    /** A pattern that used its share pauses until the next tick; this is not a provider failure. */
    boolean isScaledAttemptBudgetExhausted(IPatternDetails pattern) {
        return isScaledAttemptBudgetExhausted() || patternAttempts.getInt(pattern) >= MAX_ATTEMPTS_PER_PATTERN_PER_TICK;
    }

    /** Whether a batch-capable provider may still receive a submission for this pattern this tick. */
    boolean mayAttemptBatch(ICraftingProvider provider, IPatternDetails pattern) {
        if (isScaledAttemptBudgetExhausted(pattern) || provider instanceof ECOParallelCraftingProvider) return false;
        if (Contract.forProvider(provider) != null) return true;
        var byPattern = eligibility.get(provider);
        var cached = byPattern == null ? null : byPattern.get(pattern);
        if (cached != null && tick - cached.tick() < ELIGIBILITY_CACHE_TICKS) return cached.supported();
        return ECOAe2LtDirectDispatch.isProvider(provider)
                || provider instanceof PatternProviderLogic || ECOAdvancedAEPatternScaling.isProvider(provider)
                || HAS_LOGIC.get(provider.getClass());
    }

    boolean claimFallbackAttempt(IPatternDetails pattern) {
        if (isScaledAttemptBudgetExhausted(pattern)) return false;
        claimAttempt(pattern);
        return true;
    }

    private void claimAttempt(IPatternDetails pattern) {
        attempts++;
        patternAttempts.addTo(pattern, 1);
    }

    boolean isScaledDispatchDeferred(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        var byPattern = states.get(provider);
        var state = byPattern == null ? null : byPattern.get(request.pattern());
        return state != null && tick < state.nextDispatchTick;
    }

    boolean isScaledFallbackDeferred(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        var byPattern = states.get(provider);
        var state = byPattern == null ? null : byPattern.get(request.pattern());
        return state != null && tick < state.nextFallbackTick;
    }
    boolean supportsScaledDispatchCached(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        Reference2ObjectOpenHashMap<IPatternDetails, Eligibility> byPattern = eligibility.computeIfAbsent(provider,
                ignored -> new Reference2ObjectOpenHashMap<>());
        Eligibility cached = byPattern.get(request.pattern());
        if (cached != null && tick - cached.tick() < ELIGIBILITY_CACHE_TICKS) return cached.supported();
        boolean supported = supportsScaledDispatch(request, provider);
        byPattern.put(request.pattern(), new Eligibility(tick, supported));
        return supported;
    }
    boolean supports(ICraftingProvider provider, @Nullable IPatternDetails pattern) {
        return Contract.forProvider(provider) != null
                && (pattern == null || isProcessingPattern(ECOProviderPatternIntrospection.unwrap(pattern)));
    }

    @Nullable ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            double onePower, IEnergyService service, Consumer<ICraftingProvider> mark) {
        if (ECOExternalProviderBlocking.isEnabled(provider)) return null;
        Contract c = Contract.forProvider(provider);
        if (c == null) return null;
        IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
        if (!isProcessingPattern(base)) return null;
        var overload = ECOOverloadCpuAccountingBridge.prepare(owner, request.pattern(),
                request.job().link.getCraftingID(),
                request.job().finalOutput == null ? null : request.job().finalOutput.what(), request.job().waitingFor.list);
        if (!overload.canDispatch() || isScaledAttemptBudgetExhausted(request.pattern())) return null;
        // The counted queue takes one-copy inputs plus a count: its own capacity, not a craft budget, bounds it.
        long cap = c.inspect(request.pattern(), request.inputs(), request.allowedCrafts());
        var plan = ECOBatchDispatchPlanning.plan(request, provider, cap, Long.MAX_VALUE, onePower, service,
                ECOBatchMode.LINEAR);
        if (plan == null) return null;
        claimAttempt(request.pattern());
        ECOBatchAdmission admission;
        try {
            admission = ECOBatchExecutor.execute(plan, request.inputs(), request.outputs(), request.remainders(),
                    request.inventory(), request.level(), request.job().link.getCraftingID(),
                    () -> energy.reserve(service, onePower, plan.craftCount()), batch -> {
                        mark.accept(provider);
                        // Native APIs take one-copy counters plus a count; the materializer owns the total debit.
                        var prototype = new KeyCounter[request.inputs().length];
                        for (int i = 0; i < prototype.length; i++) {
                            prototype[i] = new KeyCounter();
                            prototype[i].addAll(request.inputs()[i]);
                        }
                        long leftover = c.push(batch.identity().originalPattern(), prototype, batch.craftCount());
                        if (leftover < 0 || leftover > batch.craftCount()) return ECOBatchAdmission.indeterminate();
                        long accepted = batch.craftCount() - leftover;
                        return accepted == 0 ? ECOBatchAdmission.rejected()
                                : ECOBatchAdmission.accepted(accepted, leftover == 0);
                    });
        } catch (ECOIndeterminateBatchException failure) {
            request.job().failPermanently("AMBIGUOUS_PROCESSING_PROVIDER_OWNERSHIP");
            return null;
        } catch (RuntimeException failure) {
            request.job().failPermanently("PROCESSING_BATCH_SETTLEMENT_FAILURE");
            throw failure;
        }
        if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) return null;
        long totalAccepted = admission.acceptedCrafts();
        var result = ECOCraftingDispatchResult.batch(totalAccepted,
                ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), totalAccepted),
                ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), totalAccepted));
        try {
            overload.registerAccepted(totalAccepted);
            accounting.apply(request, result, () -> {}, provider);
        } catch (RuntimeException failure) {
            request.job().failPermanently("POST_ACCEPT_PROCESSING_ACCOUNTING_FAILURE");
            throw failure;
        }
        return result;
    }

    /** Learns from fully inserted chunks; target saturation ends a visit without erasing its successes. */
    @Nullable ECOCraftingDispatchResult tryScaledDispatch(ECOCraftingDispatchRequest request,
            ICraftingProvider provider, double onePower, IEnergyService service,
            Consumer<ICraftingProvider> mark, ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        var direct = ECOAe2LtDirectDispatch.open(provider);
        if (blocksScaledDispatch(provider, direct)
                || isScaledAttemptBudgetExhausted(request.pattern()) || isScaledDispatchDeferred(request, provider)
                || !supportsScaledDispatchCached(request, provider)) return null;
        Object logic = direct == null ? providerLogic(provider) : null;
        boolean advancedPattern = direct == null && ECOAdvancedAEPatternScaling.isAdvancedPattern(request.pattern());
        long transportLimit = direct == null ? Long.MAX_VALUE : direct.maxBatchSize(request.pattern());
        var overload = ECOOverloadCpuAccountingBridge.prepare(owner, request.pattern(),
                request.job().link.getCraftingID(),
                request.job().finalOutput == null ? null : request.job().finalOutput.what(), request.job().waitingFor.list);
        if (!overload.canDispatch()) return null;
        ProbeState state = states.computeIfAbsent(provider, x -> new Reference2ObjectOpenHashMap<>())
                .computeIfAbsent(request.pattern(), x -> new ProbeState());
        long limit = request.allowedCrafts();
        var ramp = state.beginRun(tick, SCALE_PROBE_INTERVAL_TICKS);
        var planning = ECOBatchDispatchPlanning.prepare(request, provider);
        IPatternDetails cachedExecutionPattern = null;
        long cachedCopies = 0;
        int visitAttempts = 0;
        while (ramp.owned < limit && !isScaledAttemptBudgetExhausted(request.pattern())
                && visitAttempts < MAX_ATTEMPTS_PER_VISIT && !provider.isBusy()
                && !blocksScaledDispatch(provider, direct)) {
            claimAttempt(request.pattern());
            visitAttempts++;
            long offer = Math.min(ramp.offer(limit - ramp.owned), transportLimit);
            var plan = planning.plan(offer, limit - ramp.owned, onePower, service, ECOBatchMode.LINEAR);
            if (plan == null) {
                state.nextDispatchTick = tick + 1;
                break;
            }
            offer = plan.craftCount();
            if (direct == null && cachedCopies != offer) {
                cachedExecutionPattern = offer == 1 ? request.pattern()
                        : advancedPattern ? ECOAdvancedAEPatternScaling.scale(request.pattern(), offer)
                        : new ECOProcessingExecutionPattern(request.pattern(), offer);
                cachedCopies = offer;
            }
            IPatternDetails executionPattern = cachedExecutionPattern;
            ECOBatchAdmission admission;
            try {
                admission = ECOBatchExecutor.execute(plan, request.inputs(), request.outputs(), request.remainders(),
                        request.inventory(), request.level(), request.job().link.getCraftingID(),
                        () -> energy.reserve(service, onePower, plan.craftCount()), batch -> {
                            mark.accept(provider);
                            if (direct != null) return direct.submit(request.pattern(), request.inputs(), batch.craftCount());
                            // Only this external API boundary needs an AE2 pattern-shaped execution view.
                            // The task, plan and accounting retain the original pattern identity.
                            if (executionPattern == null) return ECOBatchAdmission.rejected();
                            var scaledRequest = new ECOCraftingDispatchRequest(request.job(), request.candidate(), executionPattern,
                                    batch.inputCounters(), batch.outputCounter(), batch.remainderCounter(),
                                    batch.craftCount(), request.inventory(), request.level());
                            return normalPush.push(scaledRequest, provider)
                                    ? ECOBatchAdmission.accepted(batch.craftCount(), fullyInserted(logic))
                                    : ECOBatchAdmission.rejected();
                        });
            } catch (ECOIndeterminateBatchException failure) {
                request.job().failPermanently("AMBIGUOUS_SCALED_PROVIDER_OWNERSHIP");
                return null;
            } catch (RuntimeException failure) {
                request.job().failPermanently("SCALED_BATCH_SETTLEMENT_FAILURE");
                throw failure;
            }
            if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
                if (ramp.record(offer, 0, false, tick)) continue;
                break;
            }
            try {
                overload.registerAccepted(admission.acceptedCrafts());
                accounting.apply(request, scaledResult(request, admission.acceptedCrafts()), () -> {}, provider);
            } catch (RuntimeException failure) {
                request.job().failPermanently("POST_ACCEPT_PROCESSING_ACCOUNTING_FAILURE");
                throw failure;
            }
            if (!ramp.record(offer, admission.acceptedCrafts(), admission.canContinue(), tick)) break;
        }
        return ramp.owned > 0 ? scaledResult(request, ramp.owned) : null;
    }

    private static ECOCraftingDispatchResult scaledResult(ECOCraftingDispatchRequest request, long copies) {
        return ECOCraftingDispatchResult.batch(copies,
                ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), copies),
                ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), copies));
    }

    private static boolean fullyInserted(Object logic) {
        if (ECOAdvancedAEPatternScaling.isProvider(logic)) return !((ICraftingProvider) logic).isBusy();
        return logic instanceof PatternProviderLogicAccessor accessor && accessor.neoecoae$getSendList().isEmpty();
    }

    private static boolean isProcessingPattern(Object pattern) {
        return pattern instanceof AEProcessingPattern || ECOAdvancedAEPatternScaling.isAdvancedPattern(pattern);
    }

    private static Object providerLogic(ICraftingProvider provider) {
        if (provider instanceof PatternProviderLogic || ECOAdvancedAEPatternScaling.isProvider(provider)) return provider;
        try {
            return provider.getClass().getMethod("getLogic").invoke(provider);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    /**
     * Selects providers for ECO-owned aggregate submission. AE2LT uses a direct counted
     * transport; ordinary providers receive an execution view preserving sparse input order.
     * Acceptance transfers the entire chunk; the send buffer separately determines capacity proof.
     */
    static boolean supportsScaledDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        var direct = ECOAe2LtDirectDispatch.open(provider);
        if (blocksScaledDispatch(provider, direct)) return false;
        if (provider instanceof ECOParallelCraftingProvider) return false;
        if (Contract.forProvider(provider) != null) return false;
        // A successful push can still own overflow. Require a live observation of AE2's send buffer.
        if (ECOAe2LtDirectDispatch.isProvider(provider)) {
            return direct != null
                    && isProcessingPattern(ECOProviderPatternIntrospection.unwrap(request.pattern()))
                    && request.pattern().getInputs().length > 0 && !request.pattern().getOutputs().isEmpty()
                    && request.pattern().supportsPushInputsToExternalInventory();
        }
        Object providerLogic = providerLogic(provider);
        boolean ae2Logic = providerLogic instanceof PatternProviderLogic
                && providerLogic instanceof PatternProviderLogicAccessor;
        boolean advancedLogic = ECOAdvancedAEPatternScaling.isProvider(providerLogic);
        if (!ae2Logic && !advancedLogic) return false;
        IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
        if (!isProcessingPattern(base)
                || (ECOAdvancedAEPatternScaling.isAdvancedPattern(base) && !advancedLogic)
                || base != request.pattern()
                || request.pattern().getInputs().length == 0
                || request.pattern().getOutputs().isEmpty()
                || !request.pattern().supportsPushInputsToExternalInventory()) return false;
        // Known directional/wireless provider modes split one push across targets and are not atomic here.
        for (String method : new String[]{"getProviderMode", "getWirelessDispatchMode", "getPushDirection"}) {
            try {
                Object value = provider.getClass().getMethod(method).invoke(provider);
                if (value != null && !value.toString().equalsIgnoreCase("NORMAL")) return false;
            } catch (NoSuchMethodException ignored) {
                // Absence is normal for the stock provider.
            } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }
        return true;
    }

    private static boolean blocksScaledDispatch(ICraftingProvider provider,
            @Nullable ECOAe2LtDirectDispatch.Session direct) {
        return ECOExternalProviderBlocking.isEnabled(provider)
                && (direct == null || !direct.canBatchWhileBlocking());
    }

    private static final class Contract {
        private final ECOMekEnergisticsBatchCapability.Session mek;

        private Contract(ECOMekEnergisticsBatchCapability.Session mek) {
            this.mek = mek;
        }

        static Contract forProvider(ICraftingProvider value) {
            // Prefer Mek-E's persistent smart queue over its physical-capacity Thunderbolt bridge.
            var mek = ECOMekEnergisticsBatchCapability.open(value);
            if (mek != null) return new Contract(mek);
            // Thunderbolt and AE2LT native batch contracts are deliberately not
            // selected here. Their adaptive ramps must never become the ECO CPU's
            // multiplier policy; eligible providers use the ECO-owned path below.
            return null;
        }

        long inspect(IPatternDetails details, KeyCounter[] inputs, long requested) {
            return mek.inspect(details, inputs, requested);
        }

        long push(IPatternDetails details, KeyCounter[] inputs, long copies) {
            try {
                return mek.submit(details, inputs, copies);
            } catch (RuntimeException failure) {
                throw new AmbiguousDispatchException(failure);
            }
        }

    }
    private static final class AmbiguousDispatchException extends RuntimeException {
        private AmbiguousDispatchException(Throwable cause) { super(cause); }
    }
    private record Eligibility(long tick, boolean supported) { }
    /** History survives visits; growth/recovery flags belong only to the current visit. */
    static final class ProbeState {
        private static final int MAX_RECOVERY_RETRIES = 3;
        long remembered = 1;
        long nextProbeTick = Long.MIN_VALUE;
        long nextDispatchTick = Long.MIN_VALUE;
        long nextFallbackTick = Long.MIN_VALUE;
        boolean probed;

        Run beginRun(long currentTick, long probeInterval) {
            boolean probe = currentTick >= nextProbeTick;
            long target = probe
                    ? (probed ? doubled(remembered) : remembered)
                    : remembered;
            // A new provider/pattern starts with the same one-copy proof but may grow immediately.
            return new Run(target, probe && (probed || remembered == 1), probeInterval);
        }

        /** Probing views stop growing at Integer.MAX_VALUE; larger proven batches are never shrunk by it. */
        private static long doubled(long count) {
            return count >= Integer.MAX_VALUE ? count : Math.min(Integer.MAX_VALUE, count * 2L);
        }

        final class Run {
            long next = remembered;
            long owned;
            final boolean growing;
            final long probeInterval;
            boolean backingOff;
            int recoveryRetries;

            private Run(long target, boolean growing, long probeInterval) {
                this.next = target;
                this.growing = growing;
                this.probeInterval = probeInterval;
            }

            long offer(long remaining) { return Math.min(next, remaining); }

            /** Returns whether this visit should try another chunk. */
            boolean record(long offered, long accepted, boolean fullyInserted, long currentTick) {
                if (accepted <= 0) {
                    nextProbeTick = currentTick + probeInterval;
                    // A target filled by earlier chunks cannot prove that those chunks were too large.
                    if (owned > 0 || offered < next) {
                        nextDispatchTick = currentTick + 1;
                        if (owned > 0) nextFallbackTick = currentTick + 1;
                        return false;
                    }
                    remembered = next = Math.max(1, offered / 2);
                    backingOff = true;
                    if (offered > 1 && recoveryRetries++ < MAX_RECOVERY_RETRIES) return true;
                    nextDispatchTick = currentTick + probeInterval;
                    nextProbeTick = currentTick + 2 * probeInterval;
                    return false;
                }
                if (accepted != offered || !fullyInserted) {
                    if (owned == 0 && offered == next) {
                        remembered = Math.min(remembered, Math.max(1, Math.min(accepted, offered / 2)));
                    }
                    owned += accepted;
                    nextProbeTick = currentTick + probeInterval;
                    nextDispatchTick = currentTick + 1;
                    nextFallbackTick = currentTick + 1;
                    return false;
                }
                owned += accepted;
                probed = true;
                if (backingOff) {
                    remembered = offered;
                    return false;
                }
                remembered = Math.max(remembered, offered);
                if (growing && offered == next) {
                    nextProbeTick = currentTick + 1;
                    next = doubled(offered);
                } else {
                    next = remembered;
                }
                return true;
            }
        }
    }
}
