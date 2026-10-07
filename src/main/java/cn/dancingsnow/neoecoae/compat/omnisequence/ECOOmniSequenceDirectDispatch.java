package cn.dancingsnow.neoecoae.compat.omnisequence;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchDispatchRequest;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import com.atir.molecularmanipulator.api.crafting.OmniBatchRequest;
import com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputProvider;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Direct batch dispatch bridge from NeoECO's multiblock CPU to OmniSequence providers.
 *
 * <p>Supports both {@link MolecularBatchCraftingProvider} (Molecular Center / 构序阵列羽冠)
 * and {@link OmniBatchCraftingProvider} (Matter Fabrication Pattern Assembly / 物质构筑样板总成),
 * as well as post-accounting output flushes through OmniSequence's output registry.</p>
 */
public final class ECOOmniSequenceDirectDispatch {
    public static final String MOD_ID = "molecularmanipulator";
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.dispatch");
    private static final ThreadLocal<Set<ICraftingProvider>> FLUSHING =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private ECOOmniSequenceDirectDispatch() {}

    public static boolean isLoaded() {
        var mods = ModList.get();
        return mods != null && mods.isLoaded(MOD_ID);
    }

    public static boolean isEligible(ICraftingProvider provider) {
        if (!isLoaded() || provider == null) return false;
        try {
            return Delegate.isEligible(provider);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean supports(ICraftingProvider provider, @Nullable IPatternDetails pattern) {
        if (!isLoaded() || provider == null) return false;
        try {
            return Delegate.supports(provider, pattern);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static Session open(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] oneCraftInputs,
            long requestedCrafts) {
        if (requestedCrafts < 2 || !isLoaded() || provider == null || pattern == null) return null;
        try {
            return Delegate.open(provider, pattern, oneCraftInputs, requestedCrafts);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean flushAfterCpuAccounting(@Nullable ICraftingProvider provider) {
        if (provider == null || !isLoaded()) return false;
        var set = FLUSHING.get();
        if (!set.add(provider)) return false;
        try {
            return Delegate.flushAfterCpuAccounting(provider);
        } catch (Throwable ignored) {
            return false;
        } finally {
            set.remove(provider);
            if (set.isEmpty()) FLUSHING.remove();
        }
    }

    public interface Session extends AutoCloseable {
        long maxBatchSize();

        ECOBatchAdmission submit(ECOBatchDispatchRequest batch, KeyCounter[] oneCraftInputs);

        /** Applies only to this provider/pattern in the current CPU tick. */
        default boolean shouldDeferFurtherDispatch() { return false; }

        @Override
        default void close() {}
    }

    private static final class Delegate {
        private static final Method REGISTRY_FLUSH = findRegistryFlush();

        private static Method findRegistryFlush() {
            try {
                Class<?> registry = Class.forName(
                        "com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry",
                        false, ECOOmniSequenceDirectDispatch.class.getClassLoader());
                return registry.getMethod("flushAfterCpuAccounting", ICraftingProvider.class);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static ICraftingProvider unwrap(ICraftingProvider provider) {
            if (provider instanceof MolecularBatchCraftingProvider || provider instanceof OmniBatchCraftingProvider) {
                return provider;
            }
            try {
                Method m = provider.getClass().getMethod("getLogic");
                Object logic = m.invoke(provider);
                if (logic instanceof ICraftingProvider p
                        && (p instanceof MolecularBatchCraftingProvider || p instanceof OmniBatchCraftingProvider)) {
                    return p;
                }
            } catch (Throwable ignored) {}
            return provider;
        }

        static boolean isEligible(ICraftingProvider provider) {
            ICraftingProvider target = unwrap(provider);
            if (target instanceof MolecularBatchCraftingProvider) {
                return !MolecularBatchCraftingProvider.requiresSerialDispatch(target);
            }
            return target instanceof OmniBatchCraftingProvider;
        }

        static boolean supports(ICraftingProvider provider, @Nullable IPatternDetails pattern) {
            ICraftingProvider target = unwrap(provider);
            if (target instanceof MolecularBatchCraftingProvider) {
                return pattern == null
                        ? !MolecularBatchCraftingProvider.requiresSerialDispatch(target)
                        : MolecularBatchCraftingProvider.supports(target, pattern);
            }
            if (target instanceof OmniBatchCraftingProvider) {
                return true;
            }
            return false;
        }

        @Nullable
        static Session open(
                ICraftingProvider provider,
                IPatternDetails pattern,
                KeyCounter[] oneCraftInputs,
                long requestedCrafts) {
            ICraftingProvider target = unwrap(provider);
            if (target instanceof MolecularBatchCraftingProvider) {
                if (!MolecularBatchCraftingProvider.supports(target, pattern)) {
                    return null;
                }
                long limit = Math.min(requestedCrafts,
                        MolecularBatchCraftingProvider.getBatchLimit(target, pattern, oneCraftInputs));
                if (limit < 2) return null;
                return new Session() {
                    @Override
                    public long maxBatchSize() {
                        return limit;
                    }

                    @Override
                    public ECOBatchAdmission submit(ECOBatchDispatchRequest batch, KeyCounter[] oneCraft) {
                        if (batch.craftCount() < 2 || batch.craftCount() > limit) {
                            return ECOBatchAdmission.rejected();
                        }
                        try (var scope = MolecularBatchDispatchContext.open(
                                batch.jobId(),
                                batch.identity().originalPattern(),
                                batch.inputCounters(),
                                oneCraft,
                                batch.craftCount(),
                                null)) {
                            boolean pushed = target.pushPattern(batch.identity().originalPattern(), batch.inputCounters());
                            return pushed
                                    ? ECOBatchAdmission.accepted(batch.craftCount(), false)
                                    : ECOBatchAdmission.rejected();
                        }
                    }
                };
            }
            if (target instanceof OmniBatchCraftingProvider omni) {
                var probeInputs = new ArrayList<OmniBatchProbe.Input>();
                for (int slot = 0; slot < oneCraftInputs.length; slot++) {
                    for (var entry : oneCraftInputs[slot]) {
                        if (entry.getLongValue() > 0) {
                            probeInputs.add(new OmniBatchProbe.Input(slot, entry.getKey(), entry.getLongValue()));
                        }
                    }
                }
                var probe = new OmniBatchProbe(pattern, probeInputs, requestedCrafts);
                var admission = omni.prepareOmniBatch(probe);
                if (admission == null || admission.maxCrafts() < 2) {
                    if (admission != null) admission.close();
                    return null;
                }
                long maxCrafts = admission.maxCrafts();
                return new Session() {
                    private boolean committed;
                    private boolean deferred;

                    @Override
                    public long maxBatchSize() {
                        return maxCrafts;
                    }

                    @Override
                    public ECOBatchAdmission submit(ECOBatchDispatchRequest batch, KeyCounter[] oneCraft) {
                        if (committed || batch.craftCount() < 2 || batch.craftCount() > maxCrafts) {
                            return ECOBatchAdmission.rejected();
                        }
                        committed = true;
                        var requestInputs = new ArrayList<OmniBatchRequest.Input>();
                        for (int slot = 0; slot < batch.inputCounters().length; slot++) {
                            for (var entry : batch.inputCounters()[slot]) {
                                if (entry.getLongValue() > 0) {
                                    requestInputs.add(new OmniBatchRequest.Input(slot, entry.getKey(), entry.getLongValue()));
                                }
                            }
                        }
                        var expectedOutputs = new ArrayList<GenericStack>();
                        for (var entry : batch.outputCounter()) {
                            if (entry.getLongValue() > 0) {
                                expectedOutputs.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                            }
                        }
                        if (requestInputs.isEmpty() || expectedOutputs.isEmpty()) {
                            return ECOBatchAdmission.rejected();
                        }
                        var omniRequest = new OmniBatchRequest(
                                UUID.randomUUID(),
                                batch.jobId(),
                                batch.identity().originalPattern(),
                                batch.craftCount(),
                                requestInputs,
                                expectedOutputs
                        );
                        var delivery = new Delivery(omniRequest);
                        try {
                            admission.commit(delivery);
                        } catch (RuntimeException | LinkageError | AssertionError failure) {
                            if (!delivery.hasDecision()) {
                                throw new ECOIndeterminateBatchException(
                                        "OmniSequence provider failed without an ownership receipt", failure);
                            }
                            // accept is irrevocable, including when provider cleanup or a duplicate callback fails.
                            LOGGER.warn("OmniSequence provider failed after reporting batch ownership; retaining its receipt",
                                    failure);
                        } finally {
                            delivery.seal();
                        }
                        deferred = delivery.deferred;
                        return delivery.outcome;
                    }

                    @Override
                    public boolean shouldDeferFurtherDispatch() {
                        return deferred;
                    }

                    @Override
                    public void close() {
                        try {
                            admission.close();
                        } catch (RuntimeException | LinkageError | AssertionError failure) {
                            LOGGER.warn("OmniSequence admission cleanup failed; retaining the recorded ownership", failure);
                        }
                    }
                };
            }
            return null;
        }

        /** A receipt may be completed only on the dispatch thread, before commit returns. */
        private static final class Delivery implements OmniBatchDelivery {
            private final OmniBatchRequest request;
            private final Thread ownerThread = Thread.currentThread();
            private ECOBatchAdmission outcome;
            private boolean deferred;
            private boolean sealed;

            private Delivery(OmniBatchRequest request) {
                this.request = request;
            }

            @Override
            public OmniBatchRequest request() {
                requireOpenThread();
                return request;
            }

            @Override
            public void accept(Receipt receipt) {
                Objects.requireNonNull(receipt, "receipt");
                complete(ECOBatchAdmission.accepted(request.craftCount(),
                                receipt.backpressure() == Backpressure.MAY_ACCEPT_MORE),
                        receipt.backpressure() != Backpressure.MAY_ACCEPT_MORE);
            }

            @Override
            public void reject(Rejection rejection) {
                Objects.requireNonNull(rejection, "rejection");
                complete(ECOBatchAdmission.rejected(), rejection.reason() == RejectReason.CAPACITY_CHANGED);
            }

            private void complete(ECOBatchAdmission decision, boolean defer) {
                requireOpenThread();
                if (outcome != null) {
                    // Never refund after acceptance. Conflicting callbacks after rejection make ownership uncertain.
                    if (outcome.status() != ECOBatchAdmission.Status.ACCEPTED) {
                        outcome = ECOBatchAdmission.indeterminate();
                    }
                    throw new IllegalStateException("OmniSequence batch delivery was already completed");
                }
                outcome = decision;
                deferred = defer;
            }

            private boolean hasDecision() {
                return outcome != null && outcome.status() != ECOBatchAdmission.Status.INDETERMINATE;
            }

            private void seal() {
                sealed = true;
                // A missing receipt does not establish that the provider retained no materials.
                if (outcome == null) outcome = ECOBatchAdmission.indeterminate();
            }

            private void requireOpenThread() {
                if (Thread.currentThread() != ownerThread) {
                    throw new IllegalStateException("OmniSequence delivery must complete synchronously");
                }
                if (sealed) throw new IllegalStateException("OmniSequence delivery is no longer active");
            }
        }

        static boolean flushAfterCpuAccounting(ICraftingProvider provider) {
            ICraftingProvider target = unwrap(provider);
            boolean flushed = false;
            if (REGISTRY_FLUSH != null) {
                try {
                    flushed = Boolean.TRUE.equals(REGISTRY_FLUSH.invoke(null, target));
                    if (target != provider) {
                        flushed |= Boolean.TRUE.equals(REGISTRY_FLUSH.invoke(null, provider));
                    }
                } catch (Throwable ignored) {}
            }
            if (!flushed) {
                if (target instanceof OmniPostAccountingOutputProvider o) {
                    try {
                        o.flushOutputsAfterCpuAccounting();
                        flushed = true;
                    } catch (Throwable ignored) {}
                } else if (target instanceof MolecularBatchCraftingProvider m) {
                    try {
                        m.molecularmanipulator$flushOutputsAfterCpuAccounting();
                        flushed = true;
                    } catch (Throwable ignored) {}
                }
            }
            return flushed;
        }
    }
}
