package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.CycleDetector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.ReachabilityScanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ECOPlannerInvariantTest {
    @Test void deterministicRandomDagsConserveEveryMaterial() throws Exception {
        Random random = new Random(0xEC0AE);
        for (int sample = 0; sample < 40; sample++) {
            int size = 12 + random.nextInt(28);
            List<AEKey> keys = new ArrayList<>();
            for (int i = 0; i < size; i++) keys.add(PlannerTestKey.of("rnd_" + sample + "_" + i));
            Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
            Map<AEKey, Long> stockAmounts = new LinkedHashMap<>();
            int id = 0;
            for (int i = 0; i < size; i++) {
                AEKey output = keys.get(i);
                if (i < 3) {
                    producers.put(output, List.of());
                    stockAmounts.put(output, 1_000_000L);
                    continue;
                }
                int inputCount = 1 + random.nextInt(Math.min(3, i));
                Object[] pairs = new Object[inputCount * 2];
                for (int slot = 0; slot < inputCount; slot++) {
                    pairs[slot * 2] = keys.get(random.nextInt(i));
                    pairs[slot * 2 + 1] = 1L + random.nextInt(3);
                }
                var pattern = PlannerFixtures.pattern("rnd_pattern_" + i, output, 1 + random.nextInt(3), pairs);
                producers.put(output, List.of(PlannerFixtures.compiled(id++, pattern, output, true, "")));
            }
            AEKey goal = keys.getLast();
            var network = PlannerFixtures.network(goal, producers);
            KeyCounter stock = new KeyCounter();
            stockAmounts.forEach(stock::add);
            long requested = 1 + random.nextInt(500);
            var reachable = new ReachabilityScanner().scan(network, ECOCancellation.NONE);
            var route = new CycleDetector().detect(network, reachable, ECOCancellation.NONE);
            var solved = new AcyclicCraftingSolver().solve(network, route.route(), stock, requested, ECOCancellation.NONE);
            assertEquals(PlanningStatus.SUCCESS, solved.status());
            validateConservation(goal, requested, stock, solved.state().usedItems(), solved.state().emittedItems(),
                solved.state().patternTimes());
        }
    }

    private static void validateConservation(AEKey goal, long requested, KeyCounter stock, KeyCounter used,
            KeyCounter emitted, Map<appeng.api.crafting.IPatternDetails, Long> patternTimes) {
        Map<AEKey, Long> supply = new LinkedHashMap<>();
        Map<AEKey, Long> consumption = new LinkedHashMap<>();
        Set<AEKey> keys = new LinkedHashSet<>();
        for (var entry : used) {
            assertTrue(entry.getLongValue() <= stock.get(entry.getKey()));
            supply.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            keys.add(entry.getKey());
        }
        for (var entry : emitted) {
            supply.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            keys.add(entry.getKey());
        }
        for (var entry : patternTimes.entrySet()) {
            long times = entry.getValue();
            assertTrue(times >= 0);
            for (var output : entry.getKey().getOutputs()) {
                supply.merge(output.what(), Math.multiplyExact(output.amount(), times), Math::addExact);
                keys.add(output.what());
            }
            for (var input : entry.getKey().getInputs()) {
                var primary = input.getPossibleInputs()[0];
                long amount = Math.multiplyExact(Math.multiplyExact(primary.amount(), input.getMultiplier()), times);
                consumption.merge(primary.what(), amount, Math::addExact);
                keys.add(primary.what());
            }
        }
        consumption.merge(goal, requested, Math::addExact);
        keys.add(goal);
        for (AEKey key : keys) {
            assertTrue(supply.getOrDefault(key, 0L) >= consumption.getOrDefault(key, 0L),
                () -> "material conservation failed for " + key);
        }
    }
}
