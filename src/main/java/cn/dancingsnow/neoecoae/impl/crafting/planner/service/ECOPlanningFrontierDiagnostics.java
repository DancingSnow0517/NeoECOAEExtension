package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PatternVariant;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOCycleBootstrap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Formats the unresolved solver frontier without changing planning behavior. */
final class ECOPlanningFrontierDiagnostics {
    private static final int MAX_MATERIALS = 6;
    private static final int MAX_PRODUCERS = 3;
    private static final int MAX_IO_ENTRIES = 5;

    private ECOPlanningFrontierDiagnostics() {
    }

    static String describe(
        ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
        ECOPlanCandidate<ECOAE2PatternVariant> candidate
    ) {
        try {
            Map<AEKey, Long> balances = balancesAfterCandidate(problem, candidate);
            ECOPlanningGraph<AEKey, ECOAE2PatternVariant> graph = new ECOPlanningGraph<>(problem.operations());
            List<Map.Entry<AEKey, Long>> unresolved = new ArrayList<>();
            for (var entry : balances.entrySet()) {
                if (entry.getValue() < 0L && !graph.producersOf(entry.getKey()).isEmpty()) {
                    unresolved.add(entry);
                }
            }
            unresolved.sort(Comparator.comparingLong((Map.Entry<AEKey, Long> entry) ->
                entry.getValue() == Long.MIN_VALUE ? Long.MAX_VALUE : -entry.getValue()
            ).reversed());

            StringJoiner materials = new StringJoiner("; ");
            for (int i = 0; i < Math.min(MAX_MATERIALS, unresolved.size()); i++) {
                var entry = unresolved.get(i);
                materials.add(describeMaterial(entry.getKey(), -entry.getValue(), graph, balances, problem.requested()));
            }
            return materials.length() == 0 ? "no craftable unresolved materials" : materials.toString();
        } catch (RuntimeException failure) {
            return "frontier diagnostic failed: " + failure.getClass().getSimpleName();
        }
    }

    private static String describeMaterial(
        AEKey material,
        long missing,
        ECOPlanningGraph<AEKey, ECOAE2PatternVariant> graph,
        Map<AEKey, Long> balances,
        Map<AEKey, Long> requested
    ) {
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> producers = graph.producersOf(material);
        int startable = 0;
        StringJoiner details = new StringJoiner(", ");
        for (int i = 0; i < producers.size(); i++) {
            var producer = producers.get(i);
            boolean canStart = ECOCycleBootstrap.canPotentiallyStart(producer, balances, requested);
            if (canStart) {
                startable++;
            }
            if (i < MAX_PRODUCERS) {
                details.add(describeProducer(material, producer, canStart, balances, requested));
            }
        }
        return material + " missing=" + missing
            + " producers=" + producers.size() + " startable=" + startable
            + " [" + details + "]";
    }

    private static String describeProducer(
        AEKey material,
        ECOPlanningOperation<AEKey, ECOAE2PatternVariant> producer,
        boolean canStart,
        Map<AEKey, Long> balances,
        Map<AEKey, Long> requested
    ) {
        long net;
        try {
            net = producer.netOutput(material);
        } catch (ArithmeticException ignored) {
            net = Long.MAX_VALUE;
        }
        String seedShortage = describeSeedShortage(producer, balances, requested);
        return "variant=" + producer.reference().ordinal()
            + " net=" + net
            + " startable=" + canStart
            + (seedShortage.isEmpty() ? "" : " seed=" + seedShortage)
            + " in=" + describeAmounts(producer.inputs())
            + " out=" + describeAmounts(producer.outputs());
    }

    private static String describeSeedShortage(
        ECOPlanningOperation<AEKey, ECOAE2PatternVariant> operation,
        Map<AEKey, Long> balances,
        Map<AEKey, Long> requested
    ) {
        StringJoiner shortages = new StringJoiner("+");
        for (var input : operation.inputs().entrySet()) {
            if (!operation.outputs().containsKey(input.getKey())) {
                continue;
            }
            long available = ECOCycleBootstrap.availableBeforeRequest(input.getKey(), balances, requested);
            if (available < input.getValue()) {
                shortages.add(input.getKey() + " x" + (input.getValue() - available));
            }
        }
        return shortages.toString();
    }

    private static String describeAmounts(Map<AEKey, Long> amounts) {
        StringJoiner values = new StringJoiner("+");
        int emitted = 0;
        for (var entry : amounts.entrySet()) {
            if (emitted++ == MAX_IO_ENTRIES) {
                values.add("...");
                break;
            }
            values.add(entry.getKey() + " x" + entry.getValue());
        }
        return values.toString();
    }

    private static Map<AEKey, Long> balancesAfterCandidate(
        ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
        ECOPlanCandidate<ECOAE2PatternVariant> candidate
    ) {
        Map<AEKey, Long> balances = new LinkedHashMap<>(problem.inventory());
        for (var operation : problem.operations()) {
            long batches = candidate.executions().getOrDefault(operation.reference(), 0L);
            if (batches == 0L) {
                continue;
            }
            operation.inputs().forEach((key, amount) -> mergeScaled(balances, key, amount, -batches));
            operation.outputs().forEach((key, amount) -> mergeScaled(balances, key, amount, batches));
        }
        problem.requested().forEach((key, amount) -> mergeScaled(balances, key, amount, -1L));
        return balances;
    }

    private static void mergeScaled(Map<AEKey, Long> balances, AEKey key, long amount, long batches) {
        long delta;
        try {
            delta = Math.multiplyExact(amount, batches);
        } catch (ArithmeticException ignored) {
            delta = batches < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        balances.merge(key, delta, ECOPlanningFrontierDiagnostics::saturatedAdd);
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }
}
