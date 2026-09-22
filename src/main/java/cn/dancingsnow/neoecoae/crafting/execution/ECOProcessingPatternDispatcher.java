package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.crafting.execution.batch.*;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ThunderboltApi;
import java.lang.reflect.Method;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProviderPatternIntrospection;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtBatchCapability;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusScaling;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusBlocking;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Native providers own their ramp; ordinary processing is probed and accounted by the ECO CPU. */
final class ECOProcessingPatternDispatcher {
    private static final long TICK_BUDGET = 1_000_000L;
    private static final long FAIR_SHARE = 50_000L;
    private static final long ELIGIBILITY_CACHE_TICKS = 10L;
    private static final long SCALE_PROBE_INTERVAL_TICKS = 5L;
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingEnergyTransaction energy;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, IdentityHashMap<IPatternDetails, ProbeState>> states = new IdentityHashMap<>();
    private final Map<ICraftingProvider, IdentityHashMap<IPatternDetails, Eligibility>> eligibility = new IdentityHashMap<>();
    private long tick = Long.MIN_VALUE, used;

    ECOProcessingPatternDispatcher(ECOCraftingCPULogic owner, ECOCraftingEnergyTransaction energy,
            ECOCraftingDispatchAccounting accounting) { this.owner = owner; this.energy = energy; this.accounting = accounting; }
    void beginTick(long gameTick) { if (tick != gameTick) { tick = gameTick; used = 0; } }
    void reset() { states.clear(); eligibility.clear(); tick = Long.MIN_VALUE; used = 0; }
    boolean supportsScaledDispatchCached(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        IdentityHashMap<IPatternDetails, Eligibility> byPattern = eligibility.computeIfAbsent(provider,
                ignored -> new IdentityHashMap<>());
        Eligibility cached = byPattern.get(request.pattern());
        if (cached != null && tick - cached.tick() < ELIGIBILITY_CACHE_TICKS) return cached.supported();
        boolean supported = supportsScaledDispatch(request, provider);
        byPattern.put(request.pattern(), new Eligibility(tick, supported));
        return supported;
    }
    boolean supports(ICraftingProvider provider, @Nullable IPatternDetails pattern) {
        return Contract.forProvider(provider) != null
                && (pattern == null || ECOProviderPatternIntrospection.unwrap(pattern) instanceof AEProcessingPattern);
    }

    @Nullable ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            double onePower, IEnergyService service, Consumer<ICraftingProvider> mark) {
        Contract c = Contract.forProvider(provider);
        if (c == null) return null;
        IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
        if (!(base instanceof AEProcessingPattern)) return null;
        var overload = ECOOverloadCpuAccountingBridge.prepare(owner, request.pattern(),
                request.job().link.getCraftingID(),
                request.job().finalOutput == null ? null : request.job().finalOutput.what());
        if (!overload.canDispatch()) return null;
        long cap = c.inspect(request.pattern(), request.inputs(), request.allowedCrafts());
        long budget = Math.max(0L, TICK_BUDGET - used);
        long quota = c.unbounded(provider, request.pattern()) ? budget : Math.min(budget, FAIR_SHARE);
        var plan = ECOBatchDispatchPlanning.plan(request, provider, cap, quota, onePower, service, ECOBatchMode.LINEAR);
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
        long budget = TICK_BUDGET - used;
        if (budget <= 0) return null;
        ProbeState state = states.computeIfAbsent(provider, x -> new IdentityHashMap<>())
                .computeIfAbsent(request.pattern(), x -> new ProbeState());
        long limit = ECOExtendedAEPlusScaling.cap(request.pattern(), Math.min(request.allowedCrafts(), budget));
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
                            // Only this external API boundary needs an AE2 pattern-shaped execution view.
                            // The task, plan and accounting retain the original pattern identity.
                            IPatternDetails scaled = batch.craftCount() == 1 ? batch.identity().originalPattern() :
                                    java.util.Objects.requireNonNullElse(
                                        ECOExtendedAEPlusScaling.scale(batch.identity().originalPattern(), batch.craftCount()),
                                        new ScaledProcessingPattern(batch.identity().originalPattern(), batch.craftCount()));
                            var scaledRequest = new ECOCraftingDispatchRequest(request.job(), request.candidate(), scaled,
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
        Object logic = providerLogic(provider);
        return logic instanceof PatternProviderLogicAccessor accessor && accessor.neoecoae$getSendList().isEmpty();
    }

    private static Object providerLogic(ICraftingProvider provider) {
        if (provider instanceof PatternProviderLogic) return provider;
        try {
            return provider.getClass().getMethod("getLogic").invoke(provider);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    /**
     * Scale only inspectable non-blocking or smart-blocking providers with the existing single-input contract.
     * Acceptance transfers the entire chunk; the send buffer separately determines capacity proof.
     */
    static boolean supportsScaledDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        if (Contract.forProvider(provider) != null) return false;
        // A successful push can still own overflow. Require a live observation of AE2's send buffer.
        Object providerLogic = providerLogic(provider);
        if (!(providerLogic instanceof PatternProviderLogic logic)
                || !(providerLogic instanceof PatternProviderLogicAccessor)) return false;
        IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
        if (!(base instanceof AEProcessingPattern) || base != request.pattern() || request.remainders().size() != 0
                || request.pattern().getInputs().length != 1
                || request.pattern().getOutputs().isEmpty()
                || !request.pattern().supportsPushInputsToExternalInventory()) return false;
        try {
            // EAEP checks input presence rather than quantity. Scaling preserves the input keys,
            // and every offer still passes through the provider's own pushPattern blocking check.
            if (logic.isBlocking() && !ECOExtendedAEPlusBlocking.isEnabled(logic.getConfigManager())) return false;
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

    private static final class ScaledProcessingPattern implements IPatternDetails {
        private final IPatternDetails original; private final long multiplier;
        private ScaledProcessingPattern(IPatternDetails original, long multiplier) { this.original = original; this.multiplier = multiplier; }
        public appeng.api.stacks.AEItemKey getDefinition() { return original.getDefinition(); }
        public IInput[] getInputs() { IInput[] inputs = original.getInputs(), scaled = new IInput[inputs.length]; for (int i = 0; i < inputs.length; i++) scaled[i] = new ScaledInput(inputs[i], multiplier); return scaled; }
        public List<appeng.api.stacks.GenericStack> getOutputs() { return ECOBatchCraftingHelper.multiply(original.getOutputs(), multiplier); }
        public boolean supportsPushInputsToExternalInventory() { return true; }
        public void pushInputsToExternalInventory(KeyCounter[] input, PatternInputSink sink) {
            // Eligibility requires one logical input and no remainder, so flattening the already-scaled concrete
            // counter is equivalent to repeating the original processing pattern without sparse-slot ambiguity.
            for (KeyCounter counter : input) for (var entry : counter) sink.pushInput(entry.getKey(), entry.getLongValue());
        }
        public boolean equals(Object other) { return other == original || other instanceof ScaledProcessingPattern scaled && original.equals(scaled.original) && multiplier == scaled.multiplier; }
        public int hashCode() { return original.hashCode(); }
    }
    private record ScaledInput(IPatternDetails.IInput original, long scale) implements IPatternDetails.IInput {
        public appeng.api.stacks.GenericStack[] getPossibleInputs() { return original.getPossibleInputs(); }
        public long getMultiplier() { return Math.multiplyExact(original.getMultiplier(), scale); }
        public boolean isValid(appeng.api.stacks.AEKey input, net.minecraft.world.level.Level level) { return original.isValid(input, level); }
        public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey template) { return null; }
    }

    private static final class Contract {
        private static final Method CAPACITY = ThunderboltApi.method(ThunderboltApi.BATCH_PROVIDER,
                "getBatchCapacity", IPatternDetails.class);
        private static final Method MODE = ThunderboltApi.method(ThunderboltApi.BATCH_PROVIDER,
                "getBatchDispatchMode", IPatternDetails.class);
        private static final Method PUSH = ThunderboltApi.method(ThunderboltApi.BATCH_PROVIDER,
                "pushBatch", IPatternDetails.class, KeyCounter[].class, long.class);
        private final ECOAe2LtBatchCapability.Session lightning;
        private final Object legacy;

        private Contract(ECOAe2LtBatchCapability.Session lightning, Object legacy) {
            this.lightning = lightning;
            this.legacy = legacy;
        }

        static Contract forProvider(Object value) {
            // The native contract preserves execution-pattern wrappers and owns the one-copy
            // fallback when adaptive batching is disabled or unsupported for this pattern.
            if (ThunderboltApi.isInstance(ThunderboltApi.BATCH_PROVIDER, value)) {
                return new Contract(null, value);
            }
            var lightning = ECOAe2LtBatchCapability.open(value);
            if (lightning != null) return new Contract(lightning, null);
            return null;
        }

        long inspect(IPatternDetails details, KeyCounter[] inputs, long requested) {
            if (lightning == null) return Math.max(0L, (long) ThunderboltApi.invoke(CAPACITY, legacy, details));
            return lightning.inspect(details, inputs, requested);
        }

        boolean unbounded(Object ignored, IPatternDetails details) {
            if (lightning != null) return lightning.unbounded();
            return ThunderboltApi.invoke(MODE, legacy, details) instanceof Enum<?> mode
                    && mode.name().equals("UNBOUNDED");
        }

        long push(IPatternDetails details, KeyCounter[] inputs, long copies) {
            try {
                if (lightning != null) {
                    return lightning.submit(details, inputs, copies);
                }
                return (long) ThunderboltApi.invoke(PUSH, legacy, details, inputs, copies);
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
