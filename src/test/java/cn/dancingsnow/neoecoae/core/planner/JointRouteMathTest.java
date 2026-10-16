package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JointRouteMathTest {
    private record Pattern(int id, Object identity, String producedKey, List<String> inputs,
            List<PlannerAmount> amounts, List<String> outputs, List<Long> outputAmounts) implements PatternView<String> {
        public int inputCount() { return inputs.size(); }
        public String inputKey(int i) { return inputs.get(i); }
        public PlannerAmount inputAmount(int i) { return amounts.get(i); }
        public boolean inputFastSupported(int i) { return true; }
        public boolean inputIgnoresComponents(int i) { return false; }
        public int grossOutputCount() { return outputs.size(); }
        public String grossOutputKey(int i) { return outputs.get(i); }
        public long grossOutputAmount(int i) { return outputAmounts.get(i); }
        public boolean fastSupported() { return true; }
        public boolean completeForStaticPlanning() { return true; }
        public boolean special() { return false; }
    }

    private static Pattern recipe(int id, String input, long amount, String output, long produced) {
        return new Pattern(id, new Object(), output, List.of(input), List.of(PlannerAmount.of(amount)),
            List.of(output), List.of(produced));
    }

    @Test void exactNetColumnsAggregateDuplicateSlotsAndCancelReturns() throws Exception {
        var huge = PlannerAmount.of(BigInteger.ONE.shiftLeft(90));
        var pattern = new Pattern(0, new Object(), "b", List.of("a", "a", "b"),
            List.of(huge, PlannerAmount.ONE, PlannerAmount.of(3)), List.of("b", "a"), List.of(3L, 1L));
        var columns = JointRouteMath.netConsumptionMatrix(List.of(pattern), Map.of("a", 0, "b", 1), Cancellation.NONE);
        assertEquals(Map.of(0, huge.toBigInteger()), columns.getFirst());
        assertEquals(huge.add(1), JointRouteMath.inputs(pattern).get("a"));
    }

    @Test void presenceRequiresSeedButCanDiscoverRecipesOutOfOrder() throws Exception {
        var back = recipe(0, "b", 1, "a", 2);
        var forward = recipe(1, "a", 1, "b", 1);
        var candidates = List.of(back, forward);
        assertTrue(JointRouteMath.presenceClosure(candidates, new LinkedHashSet<>(), Cancellation.NONE).isEmpty());
        var marking = new LinkedHashSet<>(Set.of("a"));
        assertEquals(Set.copyOf(candidates), JointRouteMath.presenceClosure(candidates, marking, Cancellation.NONE));
        assertEquals(Set.of("a", "b"), marking);
    }

    @Test void demandClosureUsesPhysicalIdentityRatherThanEquals() throws Exception {
        Object left = new String("same"), right = new String("same");
        var a = new Pattern(0, left, "goal", List.of("seed"), List.of(PlannerAmount.ONE), List.of("goal"), List.of(1L));
        var b = new Pattern(1, right, "goal", List.of("other"), List.of(PlannerAmount.ONE), List.of("goal"), List.of(1L));
        var producerMap = Map.of("goal", List.of(a, b));
        var demanded = JointRouteMath.demandedPatterns("goal", key -> producerMap.getOrDefault(key, List.of()),
            ignored -> true, Cancellation.NONE);
        assertEquals(2, demanded.size());
        assertTrue(demanded.contains(left));
        assertTrue(demanded.contains(right));
    }

    @Test void dominanceRequiresIdenticalOutputsAndNeverAddsInputs() {
        var cheap = recipe(0, "a", 1, "b", 1);
        var costly = recipe(1, "a", 3, "b", 1);
        assertTrue(JointRouteMath.dominates(cheap, costly));
        assertFalse(JointRouteMath.dominates(costly, cheap));
        assertFalse(JointRouteMath.dominates(recipe(2, "a", 1, "b", 2), costly));
        assertFalse(JointRouteMath.dominates(recipe(3, "c", 1, "b", 1), costly));
    }

    @Test void firingCostPromotesWithoutNarrowing() {
        assertEquals(PlannerAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
            JointRouteMath.firingCost(List.of(PlannerAmount.of(Long.MAX_VALUE), PlannerAmount.ONE)));
    }

    @Test void allSearchLoopsHonorCancellation() {
        Cancellation stop = () -> { throw new InterruptedException("stop"); };
        var pattern = recipe(0, "a", 1, "b", 1);
        assertThrows(InterruptedException.class, () -> JointRouteMath.presenceClosure(List.of(pattern),
            new LinkedHashSet<>(Set.of("a")), stop));
        assertThrows(InterruptedException.class, () -> JointRouteMath.netConsumptionMatrix(List.of(pattern),
            new LinkedHashMap<>(Map.of("a", 0, "b", 1)), stop));
        assertThrows(InterruptedException.class, () -> JointRouteMath.demandedPatterns("b", key -> List.of(pattern),
            ignored -> true, stop));
    }

    @Test void startupRanksFindEarliestSupplyAndPruneLaterLoopReturns() throws Exception {
        var later = recipe(0, "b", 1, "a", 1);
        var forward = recipe(1, "a", 1, "b", 1);
        var startup = recipe(2, "seed", 1, "a", 1);
        var unavailable = recipe(3, "absent", 1, "a", 1);
        Map<String, List<Pattern>> producers = new LinkedHashMap<>();
        producers.put("a", List.of(later, startup, unavailable));
        producers.put("b", List.of(forward));
        var ranks = StartupRouteSearch.ranks(producers.values(), List.of("seed"), ignored -> true, Cancellation.NONE);
        assertEquals(Map.of("seed", 0, "a", 1, "b", 2), ranks);
        var selected = StartupRouteSearch.earlierRoutes(List.of("b"), key -> producers.getOrDefault(key, List.of()),
            ranks, ignored -> true, Cancellation.NONE);
        assertEquals(List.of(forward), selected.get("b"));
        assertEquals(List.of(startup), selected.get("a"));
        assertEquals(List.of(), selected.get("seed"));
        assertEquals(List.of("b", "a", "seed"), List.copyOf(selected.keySet()));
    }

    @Test void startupSearchLeavesUnseededCyclesUnreachableAndHonorsPolicyAndCancellation() throws Exception {
        var forward = recipe(0, "a", 1, "b", 1);
        var back = recipe(1, "b", 1, "a", 1);
        Map<String, List<Pattern>> producers = Map.of("a", List.of(back), "b", List.of(forward));
        assertTrue(StartupRouteSearch.ranks(producers.values(), List.<String>of(), ignored -> true,
            Cancellation.NONE).isEmpty());
        assertEquals(Map.of("a", 0), StartupRouteSearch.ranks(producers.values(), List.of("a"),
            ignored -> false, Cancellation.NONE));
        Cancellation stop = () -> { throw new InterruptedException(); };
        assertThrows(InterruptedException.class, () -> StartupRouteSearch.ranks(producers.values(), List.of("a"),
            ignored -> true, stop));
        assertThrows(InterruptedException.class, () -> StartupRouteSearch.earlierRoutes(List.of("b"),
            key -> producers.getOrDefault(key, List.of()), Map.of("a", 0, "b", 1), ignored -> true, stop));
    }
}
