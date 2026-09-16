package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchDispatchMode;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProviderPatternIntrospection;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtBatchCapability;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Native providers own their ramp; ordinary processing is probed and accounted by the ECO CPU. */
final class ECOProcessingPatternDispatcher {
    private static final long TICK_BUDGET = 1_000_000L;
    private static final long FAIR_SHARE = 50_000L;
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingEnergyTransaction energy;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, IdentityHashMap<IPatternDetails, ProbeState>> states = new IdentityHashMap<>();
    private long tick = Long.MIN_VALUE, used;

    ECOProcessingPatternDispatcher(ECOCraftingCPULogic owner, ECOCraftingEnergyTransaction energy,
            ECOCraftingDispatchAccounting accounting) { this.owner = owner; this.energy = energy; this.accounting = accounting; }
    void beginTick(long gameTick) { if (tick != gameTick) { tick = gameTick; used = 0; } }
    void reset() { states.clear(); tick = Long.MIN_VALUE; used = 0; }
    boolean supports(ICraftingProvider provider, @Nullable IPatternDetails pattern) {
        return Contract.forProvider(provider) != null
                && (pattern == null || ECOProviderPatternIntrospection.unwrap(pattern) instanceof AEProcessingPattern);
    }

    @Nullable ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
            double onePower, IEnergyService service, Consumer<ICraftingProvider> mark) {
        Contract c = Contract.forProvider(provider);
        IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
        if (c == null || !(base instanceof AEProcessingPattern)) return null;
        var overload = ECOOverloadCpuAccountingBridge.prepare(owner, request.pattern(),
                request.job().link.getCraftingID(),
                request.job().finalOutput == null ? null : request.job().finalOutput.what());
        if (!overload.canDispatch()) return null;
        long cap = c.inspect(request.pattern(), request.inputs(), request.allowedCrafts());
        long budget = TICK_BUDGET - used;
        if (cap <= 0 || budget <= 0) return null;
        long limit = Math.min(request.allowedCrafts(), Math.min(cap, c.unbounded(provider, request.pattern()) ? budget : Math.min(budget, FAIR_SHARE)));
        var perCraftInputs = ECOFastPathStacks.copyCounters(request.inputs());
        limit = Math.min(limit, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(perCraftInputs,
                ECOFastPathStacks.copyCounter(request.outputs()), ECOFastPathStacks.copyCounter(request.remainders())));
        limit = ECOBatchCraftingHelper.maxCraftsFromInventory(request.inventory(), perCraftInputs, limit);
        limit = ECOBatchCraftingHelper.maxAffordableCrafts(onePower, limit, n -> service.extractAEPower(n, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        if (limit <= 0) return null;
        // Give the provider the entire allowance, once. It owns target probing and recovery.
        List<appeng.api.stacks.GenericStack> consumed = ECOBatchCraftingHelper.multiply(perCraftInputs, limit);
        if (!ECOBatchCraftingHelper.extractExact(request.inventory(), consumed)) return null;
        var reservation = energy.reserve(service, onePower * limit);
        if (reservation == null) {
            ECOBatchCraftingHelper.insertAll(request.inventory(), consumed);
            return null;
        }
        mark.accept(provider);
        long leftover;
        try {
            leftover = c.push(request.pattern(), request.inputs(), limit);
        } catch (AmbiguousDispatchException failure) {
            request.job().failPermanently("AMBIGUOUS_PROCESSING_PROVIDER_OWNERSHIP");
            reservation.commit();
            return null;
        }
        if (leftover < 0 || leftover > limit) {
            request.job().failPermanently("INVALID_PROCESSING_PROVIDER_OWNERSHIP");
            reservation.commit();
            return null;
        }
        long totalAccepted = limit - leftover;
        if (leftover > 0) ECOBatchCraftingHelper.insertAll(request.inventory(),
                ECOBatchCraftingHelper.multiply(perCraftInputs, leftover));
        if (totalAccepted <= 0) { reservation.refund(); return null; }
        try {
            overload.registerAccepted(totalAccepted);
            reservation.refundUnaccepted(totalAccepted, limit);
        } catch (RuntimeException failure) {
            request.job().failPermanently("POST_ACCEPT_PROCESSING_REGISTRATION_FAILURE");
            throw failure;
        }
        used += totalAccepted;
        var result = ECOCraftingDispatchResult.batch(totalAccepted, ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), totalAccepted), ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), totalAccepted));
        try {
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
        if (!supportsScaledDispatch(request, provider)) return null;
        long budget = TICK_BUDGET - used;
        if (budget <= 0) return null;
        ProbeState state = states.computeIfAbsent(provider, x -> new IdentityHashMap<>())
                .computeIfAbsent(request.pattern(), x -> new ProbeState());
        var per = ECOFastPathStacks.copyCounters(request.inputs());
        long limit = Math.min(request.allowedCrafts(), budget);
        limit = Math.min(limit, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(per,
                ECOFastPathStacks.copyCounter(request.outputs()), ECOFastPathStacks.copyCounter(request.remainders())));
        limit = ECOBatchCraftingHelper.maxCraftsFromInventory(request.inventory(), per, limit);
        limit = ECOBatchCraftingHelper.maxAffordableCrafts(onePower, limit,
                n -> service.extractAEPower(n, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        var ramp = state.beginRun();
        while (ramp.owned < limit && !provider.isBusy()) {
            long offer = ramp.offer(limit - ramp.owned);
            List<appeng.api.stacks.GenericStack> consumed = ECOBatchCraftingHelper.multiply(per, offer);
            var inputTransaction = ECOProviderInputTransaction.begin(request.inventory(), consumed);
            if (inputTransaction == null) break;
            var reservation = energy.reserve(service, onePower * offer);
            if (reservation == null) { inputTransaction.rollback(); break; }
            boolean accepted = false;
            boolean ownershipUncertain = false;
            try {
                mark.accept(provider);
                IPatternDetails scaled = offer == 1 ? request.pattern() : new ScaledProcessingPattern(request.pattern(), offer);
                KeyCounter[] counters = ECOCraftingDispatchStacks.scaleCounters(request.inputs(), offer);
                var scaledRequest = new ECOCraftingDispatchRequest(request.job(), request.candidate(), scaled, counters,
                        request.outputs(), request.remainders(), offer, request.inventory(), request.level());
                try {
                    accepted = normalPush.push(scaledRequest, provider);
                } catch (RuntimeException failure) {
                    ownershipUncertain = true;
                    inputTransaction.transferOwnership();
                    request.job().failPermanently("AMBIGUOUS_SCALED_PROVIDER_OWNERSHIP");
                    reservation.commit();
                    return null;
                }
                if (!accepted) {
                    if (ramp.record(offer, 0, false)) continue;
                    break;
                }
                inputTransaction.transferOwnership();
                reservation.commit();
                used += offer;
                // Record each owned chunk before attempting another; a later exception cannot lose it.
                accounting.apply(request, scaledResult(request, offer), () -> {}, provider);
                if (!ramp.record(offer, offer, fullyInserted(provider))) break;
            } catch (RuntimeException failure) {
                if (accepted) request.job().failPermanently("POST_ACCEPT_PROCESSING_ACCOUNTING_FAILURE");
                throw failure;
            } finally {
                if (!accepted && !ownershipUncertain) {
                    inputTransaction.rollback();
                    reservation.refund();
                }
            }
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
     * Scale only inspectable non-blocking processing providers with the existing single-input contract.
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
            if (logic.isBlocking()) return false;
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
        private final ECOAe2LtBatchCapability.Session lightning;
        private final IBatchCraftingProvider legacy;

        private Contract(ECOAe2LtBatchCapability.Session lightning, IBatchCraftingProvider legacy) {
            this.lightning = lightning;
            this.legacy = legacy;
        }

        static Contract forProvider(Object value) {
            var lightning = ECOAe2LtBatchCapability.open(value);
            if (lightning != null) return new Contract(lightning, null);
            return value instanceof IBatchCraftingProvider provider ? new Contract(null, provider) : null;
        }

        long inspect(IPatternDetails details, KeyCounter[] inputs, long requested) {
            if (lightning == null) return Math.max(0L, legacy.getBatchCapacity(details));
            return lightning.inspect(details, inputs, requested);
        }

        boolean unbounded(Object ignored, IPatternDetails details) {
            if (lightning != null) return lightning.unbounded();
            return legacy.getBatchDispatchMode(details) == BatchDispatchMode.UNBOUNDED;
        }

        long push(IPatternDetails details, KeyCounter[] inputs, long copies) {
            if (lightning != null) {
                return lightning.submit(details, inputs, copies);
            }
            try {
                return legacy.pushBatch(details, inputs, copies);
            } catch (RuntimeException failure) {
                throw new AmbiguousDispatchException(failure);
            }
        }

    }
    private static final class AmbiguousDispatchException extends RuntimeException {
        private AmbiguousDispatchException(Throwable cause) { super(cause); }
    }
    /** History survives visits; growth/recovery flags belong only to the current visit. */
    static final class ProbeState {
        long remembered = 1;

        Run beginRun() { return new Run(); }

        final class Run {
            long next = remembered;
            long owned;
            boolean fullChunkAccepted;
            boolean backingOff;

            long offer(long remaining) { return Math.min(next, remaining); }

            /** Returns whether this visit should try another chunk. */
            boolean record(long offered, long accepted, boolean fullyInserted) {
                if (accepted <= 0) {
                    if (fullChunkAccepted) return false;
                    remembered = next = Math.max(1, offered / 2);
                    backingOff = true;
                    return offered > 1;
                }
                owned += accepted;
                if (accepted != offered || !fullyInserted) {
                    if (!fullChunkAccepted) remembered = Math.max(1, offered / 2);
                    return false;
                }
                fullChunkAccepted = true;
                if (offered == next) remembered = offered;
                if (backingOff || offered == Integer.MAX_VALUE) return false;
                next = Math.min(owned, Integer.MAX_VALUE);
                return true;
            }
        }
    }
}
