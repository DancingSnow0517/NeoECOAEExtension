package cn.dancingsnow.neoecoae.integration.ae2lt;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional bridge between the ECO CPU scheduler and AE2 Lightning Tech crafting providers.
 *
 * <p>The bridge intentionally has no linkage to AE2LT or Thunderbolt classes. Older AE2LT
 * releases only expose AE2's one-copy provider contract, while newer releases may expose the
 * public {@code getBatchCapacity}/{@code pushBatch} contract. Reflective capability discovery
 * keeps both versions, and installations without AE2LT, loadable from the same ECO jar.
 */
public final class AE2LTBatchCraftingBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final String AE2LT_PACKAGE_PREFIX = "com.moakiee.ae2lt.";

    private static final BatchMethods UNSUPPORTED = new BatchMethods(null, null);
    private static final Set<String> LOGGED_CONTRACT_FAILURES = ConcurrentHashMap.newKeySet();

    private static final ClassValue<BatchMethods> BATCH_METHODS = new ClassValue<>() {
        @Override
        protected BatchMethods computeValue(Class<?> type) {
            if (!isAE2LTType(type)) {
                return UNSUPPORTED;
            }

            try {
                Method capacity = type.getMethod("getBatchCapacity", IPatternDetails.class);
                Method push = type.getMethod(
                    "pushBatch", IPatternDetails.class, KeyCounter[].class, long.class
                );
                if (!isLongReturnType(capacity) || !isLongReturnType(push)) {
                    return UNSUPPORTED;
                }
                return new BatchMethods(capacity, push);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return UNSUPPORTED;
            }
        }
    };

    /** Providers deferred after a batch attempt or failed one-copy push in the current server tick. */
    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> deferredProviders =
        new IdentityHashMap<>();
    private long currentTick = Long.MIN_VALUE;

    /** Resets per-tick provider state when the server tick advances. */
    public void beginTick(long tick) {
        if (tick != currentTick) {
            currentTick = tick;
            deferredProviders.clear();
        }
    }

    /**
     * Attempts one direct AE2LT batch dispatch.
     *
     * <p>The first copy has already been extracted by the CPU. This method reserves the remaining
     * copies, transfers only the accepted copies to the provider, and restores every unaccepted
     * extra copy before returning. A provider is attempted at most once per pattern and server tick.
     *
     * @return number of whole crafting copies accepted by an AE2LT provider, or zero
     */
    public int tryPushBatch(
            List<ICraftingProvider> providers,
            IPatternDetails details,
            KeyCounter[] oneCopyTemplate,
            ListCraftingInventory inventory,
            IEnergyService energyService,
            double patternPower,
            long maxCrafts) {
        if (maxCrafts <= 1L || oneCopyTemplate == null) {
            return 0;
        }

        List<GenericStack> inputsPerCraft = null;
        for (ICraftingProvider provider : providers) {
            if (provider.isBusy() || shouldSkip(provider, details)) {
                continue;
            }

            BatchMethods methods = BATCH_METHODS.get(provider.getClass());
            if (!methods.isSupported()) {
                continue;
            }

            final long capacity;
            try {
                capacity = methods.getCapacity(provider, details);
            } catch (RuntimeException e) {
                defer(provider, details);
                logContractFailure(provider, "getBatchCapacity", e);
                continue;
            }
            if (capacity < 0L) {
                defer(provider, details);
                logContractFailure(
                    provider,
                    "getBatchCapacity",
                    new IllegalStateException("AE2LT batch capacity must not be negative: " + capacity)
                );
                continue;
            }

            int requested = (int) Math.min(
                Integer.MAX_VALUE,
                Math.min(maxCrafts, capacity)
            );
            if (requested <= 1) {
                continue;
            }

            if (inputsPerCraft == null) {
                inputsPerCraft = ECOFastPathStacks.copyCounters(oneCopyTemplate);
            }
            int availableExtras = ECOBatchCraftingHelper.maxCraftsFromInventory(
                inventory, inputsPerCraft, requested - 1
            );
            requested = Math.min(requested, availableExtras + 1);
            requested = Math.min(
                requested,
                ECOBatchCraftingHelper.maxAffordableCrafts(
                    patternPower,
                    requested,
                    totalPower -> energyService.extractAEPower(
                        totalPower, Actionable.SIMULATE, PowerMultiplier.CONFIG
                    )
                )
            );
            if (requested <= 1) {
                continue;
            }

            List<GenericStack> extraInputs = ECOBatchCraftingHelper.multiply(
                inputsPerCraft, requested - 1
            );
            try {
                ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            } catch (RuntimeException e) {
                logContractFailure(provider, "reserveBatchInputs", e);
                return 0;
            }

            // One adaptive dispatch per provider/pattern/tick bounds old target scans and prevents
            // an approximate capacity result from immediately hammering the same provider again.
            defer(provider, details);

            final long leftover;
            try {
                leftover = methods.pushBatch(
                    provider, details, copyTemplate(oneCopyTemplate), requested
                );
                if (leftover < 0L || leftover > requested) {
                    throw new IllegalStateException(
                        "AE2LT pushBatch returned invalid leftover " + leftover
                            + " for " + requested + " requested crafts"
                    );
                }
            } catch (RuntimeException e) {
                ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
                logContractFailure(provider, "pushBatch", e);
                continue;
            }

            int accepted = requested - (int) leftover;
            if (accepted <= 0) {
                ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
                continue;
            }

            if (leftover > 0L) {
                ECOBatchCraftingHelper.insertAll(
                    inventory,
                    ECOBatchCraftingHelper.multiply(inputsPerCraft, (int) leftover)
                );
            }
            return accepted;
        }

        return 0;
    }

    /** Whether this provider must not be called again for the same pattern in this tick. */
    public boolean shouldSkip(ICraftingProvider provider, IPatternDetails details) {
        IdentityHashMap<ICraftingProvider, Boolean> providers = deferredProviders.get(details);
        return providers != null && providers.containsKey(provider);
    }

    /** Records a failed legacy one-copy push without affecting non-AE2LT providers. */
    public void recordFailedSinglePush(ICraftingProvider provider, IPatternDetails details) {
        if (isAE2LTType(provider.getClass())) {
            defer(provider, details);
        }
    }

    private void defer(ICraftingProvider provider, IPatternDetails details) {
        deferredProviders
            .computeIfAbsent(details, ignored -> new IdentityHashMap<>())
            .put(provider, Boolean.TRUE);
    }

    private static KeyCounter[] copyTemplate(KeyCounter[] template) {
        KeyCounter[] copy = new KeyCounter[template.length];
        for (int i = 0; i < template.length; i++) {
            KeyCounter source = template[i];
            if (source != null) {
                KeyCounter counter = new KeyCounter();
                counter.addAll(source);
                copy[i] = counter;
            }
        }
        return copy;
    }

    private static boolean isAE2LTType(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().startsWith(AE2LT_PACKAGE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLongReturnType(Method method) {
        return method.getReturnType() == long.class || method.getReturnType() == Long.class;
    }

    private static void logContractFailure(
            ICraftingProvider provider, String operation, RuntimeException exception) {
        String warningKey = provider.getClass().getName() + '#' + operation;
        if (LOGGED_CONTRACT_FAILURES.add(warningKey)) {
            LOGGER.error(
                "AE2LT crafting integration disabled {} for provider {}; CPU-owned inputs were retained",
                operation,
                provider.getClass().getName(),
                exception
            );
        }
    }

    private record BatchMethods(Method capacity, Method push) {
        boolean isSupported() {
            return capacity != null && push != null;
        }

        long getCapacity(ICraftingProvider provider, IPatternDetails details) {
            return invokeLong(capacity, provider, details);
        }

        long pushBatch(
                ICraftingProvider provider,
                IPatternDetails details,
                KeyCounter[] oneCopyTemplate,
                long maxCrafts) {
            return invokeLong(push, provider, details, oneCopyTemplate, maxCrafts);
        }

        private static long invokeLong(Method method, Object receiver, Object... arguments) {
            try {
                return (Long) method.invoke(receiver, arguments);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("AE2LT batch method is not accessible", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("AE2LT batch method failed", cause);
            }
        }
    }
}
