package cn.dancingsnow.neoecoae.integration.megacells;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.execution.ECOFuzzyCraftingInventory;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional batch bridge for the MEGACells decompression provider. */
public final class MEGACellsBatchCraftingBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final String MEGACELLS_PACKAGE_PREFIX = "gripe._90.megacells.";
    private static final BatchMethods UNSUPPORTED = new BatchMethods(null, null, null);
    private static final Set<String> LOGGED_CONTRACT_FAILURES = ConcurrentHashMap.newKeySet();

    private static final ClassValue<BatchMethods> BATCH_METHODS = new ClassValue<>() {
        @Override
        protected BatchMethods computeValue(Class<?> type) {
            if (!isMEGACellsType(type)) {
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
                return new BatchMethods(capacity, push, null);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                try {
                    Field outputQueue = findField(type, "patternOutputs");
                    if (!Map.class.isAssignableFrom(outputQueue.getType())) {
                        return UNSUPPORTED;
                    }
                    outputQueue.setAccessible(true);
                    return new BatchMethods(null, null, outputQueue);
                } catch (ReflectiveOperationException | LinkageError | SecurityException ignoredFallback) {
                    return UNSUPPORTED;
                }
            }
        }
    };

    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> deferredProviders =
        new IdentityHashMap<>();
    private long currentTick = Long.MIN_VALUE;

    public void beginTick(long tick) {
        if (tick != currentTick) {
            currentTick = tick;
            deferredProviders.clear();
        }
    }

    /**
     * Attempts one MEGACells batch dispatch. The first copy has already been extracted by ECO;
     * this method reserves and restores any extra copies around the reflective provider call.
     */
    public int tryPushBatch(
        List<ICraftingProvider> providers,
        IPatternDetails details,
        KeyCounter[] oneCopyTemplate,
        ListCraftingInventory inventory,
        IEnergyService energyService,
        double patternPower,
        long maxCrafts,
        Set<ResourceLocation> fuzzyItemIds
    ) {
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
            if (methods.usesOutputQueueFallback() && !isDecompressionPattern(details)) {
                continue;
            }

            final long capacity;
            try {
                capacity = methods.getCapacity(provider, details);
            } catch (RuntimeException exception) {
                defer(provider, details);
                logContractFailure(provider, "getBatchCapacity", exception);
                continue;
            }
            if (capacity <= 1L) {
                continue;
            }

            int requested = (int) Math.min(Integer.MAX_VALUE, Math.min(maxCrafts, capacity));
            if (requested <= 1) {
                continue;
            }

            if (inputsPerCraft == null) {
                inputsPerCraft = ECOFastPathStacks.copyCounters(oneCopyTemplate);
            }
            ICraftingInventory extractionInventory = fuzzyItemIds.isEmpty()
                ? inventory
                : new ECOFuzzyCraftingInventory(inventory, fuzzyItemIds);
            long availableExtras = ECOBatchCraftingHelper.inventoryBatchLimit(
                extractionInventory, inputsPerCraft, requested - 1L, fuzzyItemIds
            ).crafts();
            requested = (int) Math.min(requested, availableExtras + 1L);
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

            List<GenericStack> extraInputTemplates = ECOBatchCraftingHelper.multiply(
                inputsPerCraft, requested - 1
            );
            List<GenericStack> extractedExtraInputs;
            try {
                extractedExtraInputs = ECOBatchCraftingHelper.extractExactReturning(
                    extractionInventory, extraInputTemplates, fuzzyItemIds
                );
            } catch (RuntimeException exception) {
                logContractFailure(provider, "reserveBatchInputs", exception);
                return 0;
            }

            defer(provider, details);
            final long leftover;
            try {
                leftover = methods.pushBatch(provider, details, copyTemplate(oneCopyTemplate), requested);
                if (leftover < 0L || leftover > requested) {
                    throw new IllegalStateException(
                        "MEGACells pushBatch returned invalid leftover " + leftover
                            + " for " + requested + " requested crafts"
                    );
                }
            } catch (RuntimeException exception) {
                ECOBatchCraftingHelper.insertAll(inventory, extractedExtraInputs);
                logContractFailure(provider, "pushBatch", exception);
                continue;
            }

            int accepted = requested - (int) leftover;
            if (accepted <= 0) {
                ECOBatchCraftingHelper.insertAll(inventory, extractedExtraInputs);
                continue;
            }
            if (leftover > 0L) {
                ECOBatchCraftingHelper.insertAll(
                    inventory,
                    ECOBatchCraftingHelper.takeMatchingEntries(
                        extractedExtraInputs,
                        ECOBatchCraftingHelper.multiply(inputsPerCraft, leftover),
                        fuzzyItemIds
                    )
                );
            }
            return accepted;
        }
        return 0;
    }

    /** Compatibility overload for callers without computation-interface fuzzy configuration. */
    public int tryPushBatch(
        List<ICraftingProvider> providers,
        IPatternDetails details,
        KeyCounter[] oneCopyTemplate,
        ListCraftingInventory inventory,
        IEnergyService energyService,
        double patternPower,
        long maxCrafts
    ) {
        return tryPushBatch(
            providers, details, oneCopyTemplate, inventory, energyService, patternPower, maxCrafts, Set.of()
        );
    }

    public boolean shouldSkip(ICraftingProvider provider, IPatternDetails details) {
        IdentityHashMap<ICraftingProvider, Boolean> providers = deferredProviders.get(details);
        return providers != null && providers.containsKey(provider);
    }

    public void recordFailedSinglePush(ICraftingProvider provider, IPatternDetails details) {
        if (isMEGACellsType(provider.getClass())) {
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

    private static boolean isMEGACellsType(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().startsWith(MEGACELLS_PACKAGE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLongReturnType(Method method) {
        return method.getReturnType() == long.class || method.getReturnType() == Long.class;
    }

    private static void logContractFailure(
        ICraftingProvider provider, String operation, RuntimeException exception
    ) {
        String warningKey = provider.getClass().getName() + '#' + operation;
        if (LOGGED_CONTRACT_FAILURES.add(warningKey)) {
            LOGGER.error(
                "MEGACells crafting integration disabled {} for provider {}; CPU-owned inputs were retained",
                operation,
                provider.getClass().getName(),
                exception
            );
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the provider's class hierarchy.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean isDecompressionPattern(IPatternDetails details) {
        return details != null
            && details.getClass().getName().equals(
                MEGACELLS_PACKAGE_PREFIX + "misc.DecompressionPattern"
            );
    }

    private record BatchMethods(Method capacity, Method push, Field outputQueue) {
        boolean isSupported() {
            return (capacity != null && push != null) || outputQueue != null;
        }

        boolean usesOutputQueueFallback() {
            return outputQueue != null;
        }

        long getCapacity(ICraftingProvider provider, IPatternDetails details) {
            if (usesOutputQueueFallback()) {
                return isDecompressionPattern(details) ? Long.MAX_VALUE : 0L;
            }
            return invokeLong(capacity, provider, details);
        }

        long pushBatch(
            ICraftingProvider provider,
            IPatternDetails details,
            KeyCounter[] oneCopyTemplate,
            long maxCrafts
        ) {
            if (usesOutputQueueFallback()) {
                try {
                    GenericStack output = details.getPrimaryOutput();
                    long amount = Math.multiplyExact(output.amount(), maxCrafts);
                    Object value = outputQueue.get(provider);
                    if (!(value instanceof Map<?, ?> rawMap)) {
                        throw new IllegalStateException("MEGACells output queue is not a map");
                    }
                    @SuppressWarnings("unchecked")
                    Map<AEKey, Long> queue = (Map<AEKey, Long>) rawMap;
                    queue.merge(output.what(), amount, Math::addExact);
                    return 0L;
                } catch (IllegalAccessException | ArithmeticException exception) {
                    throw new IllegalStateException(
                        "MEGACells output queue batch dispatch failed", exception
                    );
                }
            }
            return invokeLong(push, provider, details, oneCopyTemplate, maxCrafts);
        }

        private static long invokeLong(Method method, Object receiver, Object... arguments) {
            try {
                Object result = method.invoke(receiver, arguments);
                return result instanceof Number number
                    ? number.longValue()
                    : throwInvalidReturn(method);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("MEGACells batch method is not accessible", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("MEGACells batch method failed", cause);
            }
        }

        private static long throwInvalidReturn(Method method) {
            throw new IllegalStateException("MEGACells batch method must return long: " + method);
        }
    }
}
