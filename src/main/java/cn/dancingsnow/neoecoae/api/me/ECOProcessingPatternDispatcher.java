package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProviderPatternIntrospection;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Optional Thunderbolt batch bridge. All probing and accounting remain owned by NeoECO. */
final class ECOProcessingPatternDispatcher {
    private static final long TICK_BUDGET = 1_000_000L;
    private static final long FAIR_SHARE = 50_000L;
    private static final long[] LADDER = {1L, 64L, 1_024L, 8_192L, 50_000L};
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
        long cap = c.capacity(provider, request.pattern()), budget = TICK_BUDGET - used;
        if (cap <= 0 || budget <= 0) return null;
        long limit = Math.min(request.allowedCrafts(), Math.min(cap, c.unbounded(provider, request.pattern()) ? budget : Math.min(budget, FAIR_SHARE)));
        var perCraftInputs = ECOFastPathStacks.copyCounters(request.inputs());
        limit = Math.min(limit, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(perCraftInputs,
                ECOFastPathStacks.copyCounter(request.outputs()), ECOFastPathStacks.copyCounter(request.remainders())));
        limit = ECOBatchCraftingHelper.maxCraftsFromInventory(request.inventory(), perCraftInputs, limit);
        limit = ECOBatchCraftingHelper.maxAffordableCrafts(onePower, limit, n -> service.extractAEPower(n, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        if (limit <= 0) return null;
        ProbeState state = states.computeIfAbsent(provider, x -> new IdentityHashMap<>()).computeIfAbsent(request.pattern(), x -> new ProbeState());
        long totalAccepted = 0;
        while (limit > 0) {
            long offer = Math.min(limit, state.next); var per = ECOFastPathStacks.copyCounters(request.inputs());
            List<appeng.api.stacks.GenericStack> consumed = ECOBatchCraftingHelper.multiply(per, offer);
            if (!ECOBatchCraftingHelper.extractExact(request.inventory(), consumed)) break;
            var reservation = energy.reserve(service, onePower * offer); if (reservation == null) { ECOBatchCraftingHelper.insertAll(request.inventory(), consumed); break; }
            mark.accept(provider); long leftover;
            try {
                leftover = c.push(provider, request.pattern(), request.inputs(), offer);
            } catch (AmbiguousDispatchException failure) {
                request.job().failPermanently("AMBIGUOUS_PROCESSING_PROVIDER_OWNERSHIP");
                reservation.commit();
                return null;
            }
            if (leftover < 0 || leftover > offer) leftover = offer;
            long accepted = offer - leftover;
            if (leftover > 0) ECOBatchCraftingHelper.insertAll(request.inventory(), ECOBatchCraftingHelper.multiply(per, leftover));
            if (accepted <= 0) { reservation.refund(); state.fail(offer); break; }
            try {
                overload.registerAccepted(accepted);
                reservation.refundUnaccepted(accepted, offer);
            } catch (RuntimeException failure) {
                request.job().failPermanently("POST_ACCEPT_PROCESSING_REGISTRATION_FAILURE");
                throw failure;
            }
            state.success(offer, accepted); totalAccepted += accepted; used += accepted; limit -= accepted;
            if (accepted < offer) break;
        }
        if (totalAccepted <= 0) return null;
        var result = ECOCraftingDispatchResult.batch(totalAccepted, ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), totalAccepted), ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), totalAccepted));
        try {
            accounting.apply(request, result, () -> {}, provider);
        } catch (RuntimeException failure) {
            request.job().failPermanently("POST_ACCEPT_PROCESSING_ACCOUNTING_FAILURE");
            throw failure;
        }
        return result;
    }

    /** Conservative all-or-nothing scaling for ordinary packaged providers without the native batch API. */
    @Nullable ECOCraftingDispatchResult tryScaledDispatch(ECOCraftingDispatchRequest request,
            ICraftingProvider provider, double onePower, IEnergyService service,
            Consumer<ICraftingProvider> mark, ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        if (Contract.forProvider(provider) != null || !safeForGenericScaling(request, provider)) return null;
        long budget = TICK_BUDGET - used;
        if (budget <= 0) return null;
        ProbeState state = states.computeIfAbsent(provider, x -> new IdentityHashMap<>())
                .computeIfAbsent(request.pattern(), x -> new ProbeState());
        long offer = Math.min(Math.min(request.allowedCrafts(), budget), Math.max(1, state.next));
        var per = ECOFastPathStacks.copyCounters(request.inputs());
        offer = ECOBatchCraftingHelper.maxCraftsFromInventory(request.inventory(), per, offer);
        offer = ECOBatchCraftingHelper.maxAffordableCrafts(onePower, offer,
                n -> service.extractAEPower(n, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        if (offer <= 1) return null; // The ordinary path owns the 1x fallback and its fairness budget.
        List<appeng.api.stacks.GenericStack> consumed = ECOBatchCraftingHelper.multiply(per, offer);
        var inputTransaction = ECOProviderInputTransaction.begin(request.inventory(), consumed);
        if (inputTransaction == null) return null;
        var reservation = energy.reserve(service, onePower * offer);
        if (reservation == null) { inputTransaction.rollback(); return null; }
        boolean accepted = false;
        boolean ownershipUncertain = false;
        try {
            mark.accept(provider);
            var scaled = new ScaledProcessingPattern(request.pattern(), offer);
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
            if (!accepted) { state.fail(offer); return null; }
            inputTransaction.transferOwnership();
            reservation.commit(); state.success(offer, offer); used += offer;
            var result = ECOCraftingDispatchResult.batch(offer,
                    ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), offer), List.of());
            accounting.apply(request, result, () -> {}, provider); return result;
        } catch (RuntimeException failure) {
            if (accepted) {
                request.job().failPermanently("POST_ACCEPT_PROCESSING_ACCOUNTING_FAILURE");
                throw failure;
            }
            state.fail(offer); return null;
        } finally {
            if (!accepted && !ownershipUncertain) {
                inputTransaction.rollback();
                reservation.refund();
            }
        }
    }

    private static boolean safeForGenericScaling(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
        if (!(base instanceof AEProcessingPattern) || base != request.pattern() || request.remainders().size() != 0
                || request.pattern().getInputs().length != 1
                || request.pattern().getOutputs().isEmpty()
                || !request.pattern().supportsPushInputsToExternalInventory()) return false;
        String name = provider.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (!name.contains("patternprovider") && !name.contains("pattern_provider")) return false;
        if (name.contains("wireless") || name.contains("direction")) return false;
        try {
            Method directBlocking = provider.getClass().getMethod("isBlocking");
            Object logic = provider;
            Method blocking = directBlocking;
            if (blocking == null) return false;
            if (Boolean.TRUE.equals(blocking.invoke(logic))) return false;
        } catch (NoSuchMethodException noDirectContract) {
            try {
                Object logic = provider.getClass().getMethod("getLogic").invoke(provider);
                if (Boolean.TRUE.equals(logic.getClass().getMethod("isBlocking").invoke(logic))) return false;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                // Providers without an inspectable non-blocking contract cannot prove atomic scaling.
                return false;
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
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

    private record Contract(Class<?> type, Method capacity, Method mode, Method push) {
        private static final List<Contract> ALL = resolve();
        static Contract forProvider(Object p) { for (Contract c : ALL) if (c.type.isInstance(p)) return c; return null; }
        long capacity(Object p, IPatternDetails d) { try { Object v = capacity.invoke(p, d); return v instanceof Number n ? Math.max(0, n.longValue()) : 0; } catch (ReflectiveOperationException | RuntimeException e) { return 0; } }
        boolean unbounded(Object p, IPatternDetails d) { try { Object v = mode.invoke(p, d); return v instanceof Enum<?> e && e.name().equals("UNBOUNDED"); } catch (ReflectiveOperationException | RuntimeException e) { return false; } }
        long push(Object p, IPatternDetails d, KeyCounter[] i, long n) {
            try {
                Object v = push.invoke(p, d, i, n);
                return v instanceof Number x ? x.longValue() : n;
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new AmbiguousDispatchException(e);
            }
        }
        private static List<Contract> resolve() { java.util.ArrayList<Contract> r = new java.util.ArrayList<>(); for (String n : new String[]{"com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider","com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider"}) try { Class<?> t = Class.forName(n, false, ECOProcessingPatternDispatcher.class.getClassLoader()); r.add(new Contract(t, t.getMethod("getBatchCapacity", IPatternDetails.class), t.getMethod("getBatchDispatchMode", IPatternDetails.class), t.getMethod("pushBatch", IPatternDetails.class, KeyCounter[].class, long.class))); } catch (ReflectiveOperationException | LinkageError ignored) {} return List.copyOf(r); }
    }
    private static final class AmbiguousDispatchException extends RuntimeException {
        private AmbiguousDispatchException(Throwable cause) { super(cause); }
    }
    static final class ProbeState { long next = 1, proven; void success(long o, long a) { if (a < o) { proven = Math.max(1, a); next = Math.max(1, Math.min(o / 2, proven)); } else { proven = Math.max(proven, o); next = o >= LADDER[LADDER.length - 1] ? (o >= Long.MAX_VALUE / 2 ? Long.MAX_VALUE : o * 2) : LADDER[nextIndex(o)]; } } void fail(long o) { next = Math.max(1, (proven > 0 ? Math.min(proven, o) : o) / 2); } private int nextIndex(long o) { for (int i = 0; i < LADDER.length - 1; i++) if (o <= LADDER[i]) return i + 1; return LADDER.length - 1; } }
}
