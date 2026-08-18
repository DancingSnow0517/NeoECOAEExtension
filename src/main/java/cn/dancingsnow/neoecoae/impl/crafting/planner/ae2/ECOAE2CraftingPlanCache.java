package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bounded exact cache for fully assembled, immutable-by-convention AE2 planning results. */
public final class ECOAE2CraftingPlanCache {
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<PlanKey, CraftingPlan> PLANS =
        new LinkedHashMap<>(16, 0.75F, true);

    private ECOAE2CraftingPlanCache() {
    }

    public static Optional<CraftingPlan> get(
        ECOAE2PlanningSnapshot snapshot,
        CalculationStrategy strategy
    ) {
        synchronized (LOCK) {
            CraftingPlan plan = PLANS.get(PlanKey.of(snapshot, strategy));
            return plan == null ? Optional.empty() : Optional.of(copyPlan(plan));
        }
    }

    public static void put(
        ECOAE2PlanningSnapshot snapshot,
        CalculationStrategy strategy,
        CraftingPlan plan
    ) {
        synchronized (LOCK) {
            PLANS.put(PlanKey.of(snapshot, strategy), copyPlan(plan));
            int limit = Math.clamp(
                NEConfig.ecoPlannerResultCacheSize,
                16,
                NEConfig.ECO_PLANNER_CACHE_HARD_MAX
            );
            while (PLANS.size() > limit) {
                PLANS.remove(PLANS.keySet().iterator().next());
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            PLANS.clear();
        }
    }

    private static CraftingPlan copyPlan(CraftingPlan plan) {
        return new CraftingPlan(
            plan.finalOutput(),
            plan.bytes(),
            plan.simulation(),
            plan.multiplePaths(),
            copyCounter(plan.usedItems()),
            copyCounter(plan.emittedItems()),
            copyCounter(plan.missingItems()),
            Map.copyOf(plan.patternTimes())
        );
    }

    private static KeyCounter copyCounter(KeyCounter source) {
        KeyCounter copy = new KeyCounter();
        for (var entry : source) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key != null && amount != 0L) {
                copy.add(key, amount);
            }
        }
        return copy;
    }

    private record PlanKey(
        List<?> operations,
        Object requested,
        Object inventory,
        Object unlimitedInventory,
        CalculationStrategy strategy,
        boolean multiplePaths,
        Object inputSlotCounts,
        List<?> stateCapacityTemplates,
        boolean truncatedStateExpansion,
        boolean excludedDynamicPaths,
        boolean dynamicSmithing,
        Set<?> fuzzyItemIds
    ) {
        private static PlanKey of(
            ECOAE2PlanningSnapshot snapshot,
            CalculationStrategy strategy
        ) {
            var problem = snapshot.problem();
            return new PlanKey(
                problem.operations(),
                problem.requested(),
                problem.inventory(),
                problem.unlimitedInventory(),
                strategy,
                snapshot.multiplePaths(),
                snapshot.inputSlotCounts(),
                snapshot.stateCapacityTemplates(),
                snapshot.truncatedStateExpansion(),
                snapshot.excludedDynamicPaths(),
                snapshot.dynamicSmithing(),
                snapshot.fuzzyItemIds()
            );
        }
    }
}
