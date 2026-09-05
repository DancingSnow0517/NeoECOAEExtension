package cn.dancingsnow.neoecoae.api.me;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.compat.dataenergistics.ECODataEnergisticsCountedBridge;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltBatchBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;

/** Caches provider membership per pass while reading live capacity on each attempt. */
final class ECOCraftingProviders {
    private final Map<PlanIdentity.PatternIdentity, List<ICraftingProvider>> providerTopologyCache = new HashMap<>();
    private int taskDispatchCursor;

    void clearTopologyCache() {
        providerTopologyCache.clear();
    }

    /**
     * Whether this provider's crafting energy is already covered by the flat per-tick draw of a fully
     * virtualized exchange group. Only ECO hosts can be: any other provider keeps paying per pattern.
     */
    static boolean paysFlatRateCraftingPower(ICraftingProvider provider) {
        if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus)) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
        return controller != null && controller.isFullVirtualCraftingMode();
    }

    /**
     * Topological candidates for one pattern: every provider that advertises it. Collected once per task so the
     * inner dispatch loop stops rebuilding the same {@link ArrayList} and rescanning a fixed topology, and
     * copied because the grid may add or remove providers while a dispatch runs.
     *
     * <p>Availability is intentionally excluded. A provider's busy state and free capacity change with every
     * dispatch, so they are re-read from the provider on each attempt.
     */
    List<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        var identity = PlanIdentity.patternIdentityFor(details);
        if (identity != null) {
            var cached = providerTopologyCache.get(identity);
            if (cached != null) return cached;
        }
        List<ICraftingProvider> providers = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            providers.add(provider);
        }
        List<ICraftingProvider> result = List.copyOf(providers);
        if (identity != null && !result.isEmpty()) providerTopologyCache.put(identity, result);
        return result;
    }

    /** Live availability check over the reusable candidate set; it does not mutate provider state. */
    static boolean hasAvailableProvider(List<ICraftingProvider> candidateProviders) {
        for (int i = 0; i < candidateProviders.size(); i++) {
            if (!candidateProviders.get(i).isBusy()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Seeds the dispatch queue in a rotating order. A task is re-added after a successful dispatch, so the queue
     * remains fair for the whole CPU pass and is not capped by a fixed number of copies.
     */
    List<ExecutingCraftingJob.DispatchTask> fairTaskOrder(
            List<ExecutingCraftingJob.DispatchTask> readyTasks) {
        if (readyTasks.size() <= 1) return readyTasks;
        int offset = Math.floorMod(taskDispatchCursor++, readyTasks.size());
        List<ExecutingCraftingJob.DispatchTask> result = new ArrayList<>(readyTasks.size());
        for (int i = 0; i < readyTasks.size(); i++) result.add(readyTasks.get((offset + i) % readyTasks.size()));
        return result;
    }

    static boolean hasFastPathProvider(List<ICraftingProvider> candidateProviders) {
        for (var provider : candidateProviders) {
            if (provider instanceof ECOCraftingPatternBusBlockEntity) return true;
        }
        return false;
    }

    static boolean hasExternalCountedProvider(List<ICraftingProvider> candidateProviders) {
        for (var provider : candidateProviders) {
            // Thunderbolt can optionally mix a bridge onto NeoECO's own bus. Its native verified fast paths retain
            // stronger recipe and ownership guarantees, so external contracts are considered only for other hosts.
            if (provider instanceof ECOCraftingPatternBusBlockEntity) continue;
            if (ECOThunderboltBatchBridge.supports(provider)
                    || ECODataEnergisticsCountedBridge.supports(provider)) return true;
        }
        return false;
    }

    static boolean hasBatchProbeProvider(List<ICraftingProvider> candidateProviders) {
        for (var provider : candidateProviders) {
            if (isUnknownBatchProbeProvider(provider)) return true;
        }
        return false;
    }

    static boolean isUnknownBatchProbeProvider(ICraftingProvider provider) {
        return provider != null && isUnknownBatchProbeProviderType(provider.getClass());
    }

    static boolean isUnknownBatchProbeProviderType(Class<?> providerType) {
        return ECOBatchProbeCraftingProvider.class.isAssignableFrom(providerType)
            && !ECOCraftingPatternBusBlockEntity.class.isAssignableFrom(providerType);
    }

    static Object patternIdentityOrObject(IPatternDetails pattern) {
        Object identity = PlanIdentity
            .patternIdentityFor(pattern);
        return identity == null ? pattern : identity;
    }

    /** Probe-batch providers are a distinct ownership contract and never enter the ordinary push loop. */
    static List<ICraftingProvider> ordinaryProviders(List<ICraftingProvider> providers) {
        boolean filtered = false;
        for (var provider : providers) {
            if (provider instanceof ECOBatchProbeCraftingProvider) {
                filtered = true;
                break;
            }
        }
        if (!filtered) return providers;
        List<ICraftingProvider> ordinary = new ArrayList<>(providers.size());
        for (var provider : providers) {
            if (!(provider instanceof ECOBatchProbeCraftingProvider)) ordinary.add(provider);
        }
        return List.copyOf(ordinary);
    }

    /**
     * Conservative ordinary-path capacity hint supplied to the policy layer.
     *
     * <p>ECO pattern buses expose the number of reachable worker slots, while generic AE2 providers only expose
     * the busy predicate. Generic providers therefore contribute the remaining CPU budget and stop naturally when
     * their live {@code isBusy()} gate closes (or a push is rejected). Network-switch buses are deduplicated by
     * dispatch scope so the same pooled workers are not counted once per physical bus.</p>
     */
    static int estimateOrdinaryDispatchSlots(List<ICraftingProvider> candidateProviders, int dispatchBudget) {
        var visitedScopes = Collections.newSetFromMap(new IdentityHashMap<>());
        long slots = 0L;
        for (ICraftingProvider provider : candidateProviders) {
            if (provider.isBusy()) continue;
            long contribution;
            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus) {
                ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
                if (controller == null || !visitedScopes.add(controller.getDispatchScope())) continue;
                contribution = Math.max(1L, patternBus.getAvailableThreadSlots());
            } else {
                // A plain ICraftingProvider did not opt in to any numeric capacity contract. It may consume the
                // remaining global CPU budget and naturally stops on isBusy(), rejection, or task/runtime limits.
                contribution = Math.max(0, dispatchBudget);
            }
            slots = slots > Integer.MAX_VALUE - contribution
                ? Integer.MAX_VALUE : slots + contribution;
            if (slots >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) slots;
    }
}
