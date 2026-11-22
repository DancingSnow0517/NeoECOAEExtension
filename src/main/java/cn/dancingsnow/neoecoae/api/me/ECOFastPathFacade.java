package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.integration.extendedaeplus.ECOExtendedAEPlusMatrixBridge;
import cn.dancingsnow.neoecoae.integration.ae2.ECOExternalProviderBlocking;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOStatefulBatchPlanner;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOParallelProviderAdapter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOReusableStateModel;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator;
import cn.dancingsnow.neoecoae.integration.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * NeoECO-owned synchronous CPU integration boundary. Prepare and submit on the owning server thread
 * in the same tick. External CPUs integrate with this facade directly; providers are not required to
 * implement an API owned by the calling CPU or its infrastructure library.
 * Input slots describe one copy and are previews: all physical inputs must still be in the CPU inventory.
 * The facade owns extraction and rollback. The CPU must never extract or refund those inputs itself.
 * Provider verification and stateful material calculations remain in the provider's preparation contract.
 * After acceptance the CPU owns task/output accounting, exactly once, using the returned batch totals.
 */
public final class ECOFastPathFacade {
    private static final Map<Class<?>, ProviderRegistration> PROVIDERS = new LinkedHashMap<>();

    private ECOFastPathFacade() {}

    /** Register on common setup, before the server starts dispatching jobs. */
    public static <T extends ICraftingProvider> void registerProvider(Class<T> providerType,
            Function<? super T, ? extends ECOFastPathDispatchProvider> factory, boolean takesPrecedenceOverScaling) {
        registerProvider(providerType, factory, takesPrecedenceOverScaling, null);
    }

    @FunctionalInterface
    public interface JobAwarePush<T extends ICraftingProvider> {
        boolean push(T provider, IPatternDetails pattern, KeyCounter[] inputs, @Nullable UUID jobId);
    }

    public static <T extends ICraftingProvider> void registerProvider(Class<T> providerType,
            Function<? super T, ? extends ECOFastPathDispatchProvider> factory, boolean takesPrecedenceOverScaling,
            @Nullable JobAwarePush<T> push) {
        PROVIDERS.put(providerType, new ProviderRegistration(
                provider -> factory.apply(providerType.cast(provider)), takesPrecedenceOverScaling,
                push == null ? null : (provider, pattern, inputs, jobId) ->
                        push.push(providerType.cast(provider), pattern, inputs, jobId)));
    }

    private record ProviderRegistration(Function<ICraftingProvider, ECOFastPathDispatchProvider> factory,
            boolean takesPrecedenceOverScaling, @Nullable JobAwarePush<ICraftingProvider> push) {}

    private static @Nullable ProviderRegistration registeredProvider(ICraftingProvider provider) {
        var direct = PROVIDERS.get(provider.getClass());
        if (direct != null) return direct;
        for (var entry : PROVIDERS.entrySet()) {
            if (entry.getKey().isInstance(provider)) return entry.getValue();
        }
        return null;
    }

    public static boolean takesPrecedenceOverScaling(ICraftingProvider provider) {
        var registration = registeredProvider(provider);
        return registration != null && registration.takesPrecedenceOverScaling();
    }

    public static @Nullable ECOStatefulBatchCalculator prepareStatefulCalculator(
            ECOBatchDispatchContext context, ECOReusableStateModel model) {
        if (context.level() == null) return null;
        var execution = context.execution();
        if (!execution.canUseFastPath() || execution.key() == null) return null;
        var cache = new ECOCraftingFastPathCache(ECOCraftingFastPathCache.MIN_CACHE_SIZE);
        long tick = context.level().getGameTime();
        cache.putPositive(execution.key(), context.outputs(), context.containerItems(), context.inputItems(), tick, model);
        var lookup = cache.lookup(execution, tick, AE2PatternIntrospection.reloadGeneration());
        return lookup.isVerified() ? ECOStatefulBatchCalculator.create(lookup.recipe(), execution) : null;
    }

    /**
     * Ordinary fallback for an already allocated copy. True transfers its inputs; false guarantees
     * rejection. This does not extract, refund, scale inputs, or change the caller's budget.
     */
    public static boolean pushPatternForJob(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, @Nullable UUID craftingJobId) {
        var registered = pushRegisteredProvider(provider, pattern, inputs, craftingJobId);
        if (registered != null) return registered;
        return provider instanceof ECOJobAwareCraftingProvider aware
                ? aware.pushPattern(pattern, inputs, craftingJobId) : provider.pushPattern(pattern, inputs);
    }

    public static boolean supportsJobAwarePush(ICraftingProvider provider) {
        var registration = registeredProvider(provider);
        return provider instanceof ECOJobAwareCraftingProvider || registration != null && registration.push() != null;
    }

    public static @Nullable Boolean pushRegisteredProvider(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, @Nullable UUID craftingJobId) {
        var registration = registeredProvider(provider);
        return registration == null || registration.push() == null ? null
            : registration.push().push(provider, pattern, inputs, craftingJobId);
    }

    /** Publish cancellation and recover loaded workers before the external CPU discards its job. */
    public static void cancelJob(@Nullable Level level, UUID craftingJobId) {
        cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle
                .cancelAndRecover(level, craftingJobId);
    }

    public static boolean supports(ICraftingProvider provider) {
        return supportsNativeProvider(provider) || ECOExtendedAEPlusMatrixBridge.supportsProvider(provider);
    }

    public static boolean supportsNativeProvider(ICraftingProvider provider) {
        return provider instanceof ECOFastPathDispatchProvider || registeredProvider(provider) != null;
    }

    @Nullable
    public static ECOFastPathDispatchProvider resolveProvider(ICraftingProvider provider) {
        var nativeProvider = resolveNativeProvider(provider);
        return nativeProvider != null ? nativeProvider : ECOExtendedAEPlusMatrixBridge.adapt(provider);
    }

    private static @Nullable ECOFastPathDispatchProvider resolveNativeProvider(ICraftingProvider provider) {
        if (provider instanceof ECOFastPathDispatchProvider nativeProvider) return nativeProvider;
        var registration = registeredProvider(provider);
        return registration == null ? null : registration.factory().apply(provider);
    }

    /** Null means no resources moved and ordinary dispatch is available. craftCount is the live upper bound. */
    @Nullable
    public static PreparedBatch prepare(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory,
            long maxCrafts, double singlePower, IEnergyService energy, Level level, @Nullable UUID jobId) {
        return prepare(provider, pattern, inputs, outputs, remainders, inventory, maxCrafts,
            singlePower, energy, level, jobId, false);
    }

    /** Explicit opt-in from the owning CPU's exact-order flag, never inferred from an amount or inventory. */
    @Nullable
    public static PreparedBatch prepare(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory,
            long maxCrafts, double singlePower, IEnergyService energy, Level level, @Nullable UUID jobId,
            boolean exactOrder) {
        if (ECOExternalProviderBlocking.isEnabled(provider)) return null;
        var target = resolveProvider(provider);
        if (target == null) return null;
        var batch = ECOStatefulBatchPlanner.prepare(target, pattern, inputs, outputs, remainders,
            inventory, maxCrafts, singlePower, energy, level, jobId, exactOrder);
        return batch == null ? null : new PreparedBatch(batch, inventory);
    }

    /** External native CPUs can use the workstation queue without changing ECO's ordinary batch lane. */
    @Nullable
    public static PreparedBatch prepareParallel(ECOParallelCraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory,
            long maxCrafts, double singlePower, IEnergyService energy, Level level, @Nullable UUID jobId) {
        if (provider instanceof ICraftingProvider crafting && ECOExternalProviderBlocking.isEnabled(crafting)) return null;
        var batch = ECOStatefulBatchPlanner.prepare(new ECOParallelProviderAdapter(provider), pattern,
            inputs, outputs, remainders, inventory, maxCrafts, singlePower, energy, level, jobId);
        return batch == null ? null : new PreparedBatch(batch, inventory);
    }

    /** CPU-owned energy ledger; a failed reservation must restore any partial debit before returning null. */
    @FunctionalInterface
    public interface EnergyAccount {
        @Nullable Reservation reserve(double amount);
    }

    /** Commit must not throw. Refund must retain any energy the network cannot accept in persistent CPU credit. */
    public interface Reservation {
        void commit();
        void refund();
    }

    /**
     * Adapter for CPUs that already allocated a uniform batch and own its energy/accounting transaction.
     * Only stateless recipes without returned inputs are supported by this delivery convention. No physical
     * CPU inventory is accessed: the private ledger describes the supplied materials. On rejection the CPU
     * still owns them; on success it owns only the unaccepted copies. Submit with its existing reservation.
     */
    @Nullable
    public static PreparedBatch prepareAllocated(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] oneCopy, long allocatedCopies, Level level, @Nullable UUID jobId) {
        if (allocatedCopies <= 0 || ECOExternalProviderBlocking.isEnabled(provider)) return null;
        var target = resolveNativeProvider(provider);
        if (target == null) return null;
        var execution = ECOExtractedPatternExecution.fromProviderPush(pattern, oneCopy, level);
        if (!execution.canUseFastPath() || !execution.expectedContainerItems().isEmpty()) return null;
        long bounded = Math.min(allocatedCopies, execution.arithmeticBatchLimit());
        if (bounded <= 0) return null;
        var allocated = new ListCraftingInventory(ignored -> {});
        for (var stack : ECOBatchCraftingHelper.multiply(execution.inputItems(), bounded)) {
            allocated.list.add(stack.what(), stack.amount());
        }
        var outputs = new KeyCounter();
        for (var stack : execution.expectedOutputs()) outputs.add(stack.what(), stack.amount());
        var batch = ECOStatefulBatchPlanner.prepareAllocated(target, pattern, oneCopy, outputs,
            allocated, bounded, level, jobId);
        return batch == null ? null : new PreparedBatch(batch, allocated);
    }

    /**
     * Exact allocated admission for FX virtual lanes. Counts and totals are never narrowed to long.
     * The caller reserves its exact inputs and waiting outputs before submit, then settles acceptance
     * and energy once. A null preparation or false submit transfers no ownership.
     */
    public static @Nullable PreparedExactBatch prepareExactAllocated(ICraftingProvider provider,
            IPatternDetails pattern, KeyCounter[] oneCopy, java.math.BigInteger allocatedCopies,
            Level level, UUID jobId) {
        if (allocatedCopies.signum() <= 0
                || jobId == null || ECOExternalProviderBlocking.isEnabled(provider)) return null;
        var target = resolveNativeProvider(provider);
        if (target == null) return null;
        var execution = ECOExtractedPatternExecution.fromProviderPush(pattern, oneCopy, level);
        if (!execution.canUseFastPath() || !execution.expectedContainerItems().isEmpty()) return null;
        var outputs = new KeyCounter();
        for (var stack : execution.expectedOutputs()) outputs.add(stack.what(), stack.amount());
        var context = ECOBatchDispatchContext.create(pattern, oneCopy, outputs, new KeyCounter(), level, jobId);
        var admission = target.eco$prepareExactFastPath(context, allocatedCopies);
        if (admission == null) return null;
        if (admission.craftCount().compareTo(allocatedCopies) > 0)
            throw new IllegalArgumentException("Exact provider exceeded allocated copies");
        return new PreparedExactBatch(admission);
    }

    public static final class PreparedExactBatch {
        private final ECOFastPathDispatchProvider.ExactPreparation admission;
        private boolean submitted;

        private PreparedExactBatch(ECOFastPathDispatchProvider.ExactPreparation admission) {
            this.admission = admission;
        }

        public java.math.BigInteger craftCount() { return admission.craftCount(); }

        public boolean submit() {
            if (submitted) throw new IllegalStateException("Exact batch already submitted");
            submitted = true;
            return admission.dispatch().getAsBoolean();
        }
    }

    public static final class PreparedBatch {
        private final ECOStatefulBatchPlanner.PreparedBatch batch;
        private final ListCraftingInventory inventory;
        private boolean submitted;

        private PreparedBatch(ECOStatefulBatchPlanner.PreparedBatch batch, ListCraftingInventory inventory) {
            this.batch = batch;
            this.inventory = inventory;
        }

        public long craftCount() { return batch.craftCount(); }
        public double power() { return batch.power(); }
        public List<GenericStack> inputTotal() { return batch.inputTotal(); }
        public java.util.Map<appeng.api.stacks.AEKey, java.math.BigInteger> exactInputTotal() {
            return batch.exactInputTotal();
        }
        public List<GenericStack> outputs() { return batch.outputs(); }
        public List<GenericStack> remainders() { return batch.remainders(); }

        /**
         * Single-use, including rejection. False restores inputs and energy. Ordinary provider exceptions
         * propagate after rollback. Indeterminate exceptions retain both resources and require the CPU to
         * stop the job for reconciliation, never fall back. Accounting failures after true cannot undo acceptance.
         */
        public boolean submit(EnergyAccount energy) {
            Objects.requireNonNull(energy);
            if (submitted) throw new IllegalStateException("Batch already submitted");
            submitted = true;
            var reservation = energy.reserve(power());
            if (reservation == null) return false;
            return batch.submit(inventory, reservation);
        }
    }
}
