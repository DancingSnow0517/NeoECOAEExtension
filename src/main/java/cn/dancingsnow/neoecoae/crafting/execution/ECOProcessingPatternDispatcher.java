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
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusBlocking;
import cn.dancingsnow.neoecoae.compat.advanced_ae.ECOAdvancedAEPatternScaling;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** ECO owns processing batch growth; optional adapters only transport the selected batch. */
final class ECOProcessingPatternDispatcher {
    private static final long TICK_BUDGET = 1_000_000L;
    private static final long ELIGIBILITY_CACHE_TICKS = 10L;
    private static final long SCALE_PROBE_INTERVAL_TICKS = 5L;
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingEnergyTransaction energy;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, ProbeState>> states = new Reference2ObjectOpenHashMap<>();
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, Eligibility>> eligibility = new Reference2ObjectOpenHashMap<>();
    private long tick = Long.MIN_VALUE, used;

    ECOProcessingPatternDispatcher(ECOCraftingCPULogic owner, ECOCraftingEnergyTransaction energy,
            ECOCraftingDispatchAccounting accounting) { this.owner = owner; this.energy = energy; this.accounting = accounting; }
    void beginTick(long gameTick) { if (tick != gameTick) { tick = gameTick; used = 0; } }
    void reset() { states.clear(); eligibility.clear(); tick = Long.MIN_VALUE; used = 0; }
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
        Contract c = Contract.forProvider(provider);
        if (c == null) return null;
        IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
        if (!isProcessingPattern(base)) return null;
        var overload = ECOOverloadCpuAccountingBridge.prepare(owner, request.pattern(),
                request.job().link.getCraftingID(),
                request.job().finalOutput == null ? null : request.job().finalOutput.what());
        if (!overload.canDispatch()) return null;
        long cap = c.inspect(request.pattern(), request.inputs(), request.allowedCrafts());
        long budget = Math.max(0L, TICK_BUDGET - used);
        var plan = ECOBatchDispatchPlanning.plan(request, provider, cap, budget, onePower, service, ECOBatchMode.LINEAR);
        if (plan == null) return null;
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
        used += totalAccepted;
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

    /** Mirrors ProviderTarget's bounded ramp, including accepted-but-buffered chunks. */
    @Nullable ECOCraftingDispatchResult tryScaledDispatch(ECOCraftingDispatchRequest request,
            ICraftingProvider provider, double onePower, IEnergyService service,
            Consumer<ICraftingProvider> mark, ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        if (!supportsScaledDispatchCached(request, provider)) return null;
        var direct = ECOAe2LtDirectDispatch.open(provider);
        var overload = ECOOverloadCpuAccountingBridge.prepare(owner, request.pattern(),
                request.job().link.getCraftingID(),
                request.job().finalOutput == null ? null : request.job().finalOutput.what());
        if (!overload.canDispatch()) return null;
        long budget = TICK_BUDGET - used;
        if (budget <= 0) return null;
        ProbeState state = states.computeIfAbsent(provider, x -> new Reference2ObjectOpenHashMap<>())
                .computeIfAbsent(request.pattern(), x -> new ProbeState());
        long limit = Math.min(request.allowedCrafts(), budget);
        var ramp = state.beginRun(tick, SCALE_PROBE_INTERVAL_TICKS);
        while (ramp.owned < limit && !provider.isBusy()) {
            long offer = ramp.offer(limit - ramp.owned);
            var plan = ECOBatchDispatchPlanning.plan(request, provider, offer, limit - ramp.owned,
                    onePower, service, ECOBatchMode.LINEAR);
            if (plan == null) break;
            offer = plan.craftCount();
            ECOBatchAdmission admission;
            try {
                admission = ECOBatchExecutor.execute(plan, request.inputs(), request.outputs(), request.remainders(),
                        request.inventory(), request.level(), request.job().link.getCraftingID(),
                        () -> energy.reserve(service, onePower, plan.craftCount()), batch -> {
                            mark.accept(provider);
                            if (direct != null) return direct.submit(request.pattern(), request.inputs(), batch.craftCount());
                            // Only this external API boundary needs an AE2 pattern-shaped execution view.
                            // The task, plan and accounting retain the original pattern identity.
                            IPatternDetails original = batch.identity().originalPattern();
                            IPatternDetails executionPattern = batch.craftCount() == 1 ? original
                                    : ECOAdvancedAEPatternScaling.isAdvancedPattern(original)
                                    ? ECOAdvancedAEPatternScaling.scale(original, batch.craftCount())
                                    : new ECOProcessingExecutionPattern(original, batch.craftCount());
                            if (executionPattern == null) return ECOBatchAdmission.rejected();
                            var scaledRequest = new ECOCraftingDispatchRequest(request.job(), request.candidate(), executionPattern,
                                    batch.inputCounters(), batch.outputCounter(), batch.remainderCounter(),
                                    batch.craftCount(), request.inventory(), request.level());
                            return normalPush.push(scaledRequest, provider)
                                    ? ECOBatchAdmission.accepted(batch.craftCount(), fullyInserted(provider))
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
            used += admission.acceptedCrafts();
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
                ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), copies), List.of());
    }

    private static boolean fullyInserted(ICraftingProvider provider) {
        if (ECOAdvancedAEPatternScaling.isProvider(provider)) return !provider.isBusy();
        Object logic = providerLogic(provider);
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
        if (Contract.forProvider(provider) != null) return false;
        // A successful push can still own overflow. Require a live observation of AE2's send buffer.
        if (ECOAe2LtDirectDispatch.isProvider(provider)) {
            return ECOAe2LtDirectDispatch.open(provider) != null
                    && isProcessingPattern(ECOProviderPatternIntrospection.unwrap(request.pattern()))
                    && request.remainders().isEmpty()
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
                || base != request.pattern() || request.remainders().size() != 0
                || request.pattern().getInputs().length == 0
                || request.pattern().getOutputs().isEmpty()
                || !request.pattern().supportsPushInputsToExternalInventory()) return false;
        try {
            // EAEP checks input presence rather than quantity. Scaling preserves the input keys,
            // and every offer still passes through the provider's own pushPattern blocking check.
            boolean blocking = ae2Logic ? ((PatternProviderLogic) providerLogic).isBlocking()
                    : ECOAdvancedAEPatternScaling.isBlocking(providerLogic);
            var config = ae2Logic ? ((PatternProviderLogic) providerLogic).getConfigManager()
                    : ECOAdvancedAEPatternScaling.configManager(providerLogic);
            if (blocking && (config == null || !ECOExtendedAEPlusBlocking.isEnabled(config))) return false;
        } catch (RuntimeException unavailable) {
            return false;
        }
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
        long remembered = 1;
        long nextProbeTick = Long.MIN_VALUE;
        boolean probed;

        Run beginRun(long currentTick, long probeInterval) {
            boolean probe = currentTick >= nextProbeTick;
            boolean coldStart = !probed && remembered == 1;
            long target = probe
                    ? (probed ? Math.min(Integer.MAX_VALUE, Math.max(remembered + 1L, remembered * 2L)) : remembered)
                    : remembered;
            return new Run(target, probe, probed && target > remembered, coldStart, probeInterval);
        }

        final class Run {
            long next = remembered;
            long owned;
            final long target;
            final boolean probe;
            final boolean growthProbe;
            final boolean coldStart;
            final long probeInterval;
            boolean backingOff;

            private Run(long target, boolean probe, boolean growthProbe, boolean coldStart, long probeInterval) {
                this.target = target;
                this.next = target;
                this.probe = probe;
                this.growthProbe = growthProbe;
                this.coldStart = coldStart;
                this.probeInterval = probeInterval;
            }

            long offer(long remaining) { return Math.min(next, remaining); }

            /** Returns whether this visit should try another chunk. */
            boolean record(long offered, long accepted, boolean fullyInserted, long currentTick) {
                if (accepted <= 0) {
                    remembered = next = Math.max(1, offered / 2);
                    if (probe) nextProbeTick = currentTick + probeInterval;
                    backingOff = offered > 1 && (owned > 0 || growthProbe);
                    return backingOff;
                }
                owned += accepted;
                if (accepted != offered || !fullyInserted) {
                    remembered = Math.max(1, offered / 2);
                    if (probe) nextProbeTick = currentTick + probeInterval;
                    return false;
                }
                if (backingOff) {
                    remembered = Math.max(remembered, offered);
                    probed = true;
                    return false;
                }
                if (coldStart) {
                    remembered = Math.max(remembered, offered);
                    probed = true;
                    nextProbeTick = currentTick + probeInterval;
                    next = Math.min(Integer.MAX_VALUE, Math.max(offered + 1L, offered * 2L));
                    return offered < Integer.MAX_VALUE;
                }
                if (probe && offered == target) {
                    remembered = offered;
                    probed = true;
                    nextProbeTick = currentTick + probeInterval;
                    next = remembered;
                    return !growthProbe;
                }
                // Between upward probes, reuse the last proven chunk size without
                // repeatedly re-running the expensive eligibility/scale decision.
                next = remembered;
                return true;
            }
        }
    }
}
