package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jetbrains.annotations.Nullable;

/** Bridges planner results across AE2's null CraftingPlan boundary. */
public final class ECOPlanningResultCache {
    private static final ConcurrentMap<String, ECOPlanningResult> RESULTS = new ConcurrentHashMap<>();

    private ECOPlanningResultCache() {}

    public static void put(String goal, long amount, ECOPlanningResult result) {
        RESULTS.put(key(goal, amount), result);
    }

    public static @Nullable ECOPlanningResult get(String goal, long amount) {
        return RESULTS.get(key(goal, amount));
    }

    private static String key(String goal, long amount) {
        return goal + '#' + amount;
    }
}
