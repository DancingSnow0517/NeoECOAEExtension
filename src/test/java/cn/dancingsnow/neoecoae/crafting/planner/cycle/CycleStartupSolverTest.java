package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CycleStartupSolverTest {
    @Test void everyBinaryTwoTransitionNetworkAgreesWithSingleFiringReachability() throws Exception {
        for (int bits = 0; bits < 256; bits++) {
            long[][] in = new long[2][2], out = new long[2][2];
            for (int t = 0; t < 2; t++) for (int k = 0; k < 2; k++) {
                in[t][k] = (bits >>> (4 * t + k)) & 1;
                out[t][k] = (bits >>> (4 * t + 2 + k)) & 1;
            }
            for (int stored = 0; stored < 4; stored++) for (int times = 0; times < 4; times++) {
                verify(in, out, new boolean[2], new int[] {1 + (times & 1), 1 + (times >>> 1)},
                    new long[] {stored & 1, stored >>> 1});
            }
        }
    }

    @Test void multiInputNetworksAndTheirPermutationsAgreeWithAnIndependentOracle() throws Exception {
        Random random = new Random(20261103L);
        for (int sample = 0; sample < 600; sample++) {
            int transitions = 3 + random.nextInt(2), keys = 2 + random.nextInt(3);
            long[][] in = new long[transitions][keys], out = new long[transitions][keys];
            int[] counts = new int[transitions];
            long[] stock = new long[keys];
            boolean[] imports = new boolean[keys];
            for (int k = 0; k < keys; k++) {
                stock[k] = random.nextInt(5);
                imports[k] = random.nextInt(6) == 0;
            }
            for (int t = 0; t < transitions; t++) {
                counts[t] = random.nextInt(3);
                for (int k = 0; k < keys; k++) {
                    in[t][k] = random.nextInt(4);
                    out[t][k] = random.nextInt(4);
                }
            }
            verify(in, out, imports, counts, stock);
            long[][] reversedIn = new long[transitions][keys], reversedOut = new long[transitions][keys];
            long[] reversedStock = new long[keys];
            boolean[] reversedImports = new boolean[keys];
            int[] reversedCounts = new int[transitions];
            for (int k = 0; k < keys; k++) {
                reversedStock[k] = stock[keys - k - 1];
                reversedImports[k] = imports[keys - k - 1];
                for (int t = 0; t < transitions; t++) {
                    reversedIn[t][k] = in[transitions - t - 1][keys - k - 1];
                    reversedOut[t][k] = out[transitions - t - 1][keys - k - 1];
                    reversedCounts[t] = counts[transitions - t - 1];
                }
            }
            verify(reversedIn, reversedOut, reversedImports, reversedCounts, reversedStock);
        }
    }

    @Test void independentGroupsAvoidTheWholeCountProductAndPreserveSharedFuelAndCatalysts() throws Exception {
        int groups = 32, transitions = 2 * groups, keys = 2 * groups + 2;
        int catalyst = keys - 2, fuel = keys - 1;
        long[][] in = new long[transitions][keys], out = new long[transitions][keys];
        long[] stock = new long[keys];
        int[] counts = new int[transitions];
        Arrays.fill(counts, 1);
        for (int g = 0; g < groups; g++) {
            int a = 2 * g, b = a + 1;
            in[a][a] = 1; out[a][b] = 1;
            in[b][a] = 1; in[b][b] = 1; out[b][a] = 1;
            stock[a] = 1; stock[b] = 1;
        }
        for (int t = 0; t < transitions; t++) {
            in[t][catalyst] = 1; out[t][catalyst] = 1; in[t][fuel] = 1;
        }
        stock[catalyst] = 1; stock[fuel] = transitions;
        var result = solve(in, out, new boolean[keys], amounts(counts), amounts(stock));
        assertEquals(CycleStartupSolver.Status.FEASIBLE, result.status());
        assertEquals(groups, result.components());
        assertEquals(2 * groups, result.orderingKeys());
        assertTrue(result.states() <= 4 * groups, "Independent startup choices must not form a Cartesian product");
        long[] after = replay(in, out, new boolean[keys], counts, stock, result);
        assertEquals(1, after[catalyst]);
        assertEquals(0, after[fuel]);

        stock[fuel]--;
        assertEquals(CycleStartupSolver.Status.INFEASIBLE_FIXED_VECTOR,
            solve(in, out, new boolean[keys], amounts(counts), amounts(stock)).status());
        stock[fuel]++; stock[catalyst] = 0;
        assertEquals(CycleStartupSolver.Status.INFEASIBLE_FIXED_VECTOR,
            solve(in, out, new boolean[keys], amounts(counts), amounts(stock)).status());
    }

    @Test void enormousSingleRunKeepsItsExactInitialRequirement() throws Exception {
        PlannerAmount count = PlannerAmount.of(BigInteger.TEN.pow(120));
        long[][] in = {{2, 1}}, out = {{1, 1}};
        var stock = new PlannerAmount[] {count.add(1), PlannerAmount.ONE};
        var result = solve(in, out, new boolean[2], new PlannerAmount[] {count}, stock);
        assertEquals(CycleStartupSolver.Status.FEASIBLE, result.status());
        assertEquals(1, result.witness().size());
        assertEquals(count, result.witness().getFirst().exactCount());
        assertEquals(1, result.states());
        stock[0] = count;
        assertEquals(CycleStartupSolver.Status.INFEASIBLE_FIXED_VECTOR,
            solve(in, out, new boolean[2], new PlannerAmount[] {count}, stock).status());
    }

    @Test void enormousUnitRingUsesOnePeriodAndAnExactRepetitionCount() throws Exception {
        int size = 8, catalyst = size, fuel = size + 1;
        PlannerAmount repetitions = PlannerAmount.of(BigInteger.TEN.pow(120));
        long[][] in = new long[size][size + 2], out = new long[size][size + 2];
        PlannerAmount[] counts = new PlannerAmount[size], stock = new PlannerAmount[size + 2];
        Arrays.fill(counts, repetitions); Arrays.fill(stock, PlannerAmount.ZERO);
        for (int t = 0; t < size; t++) {
            in[t][t] = 1; out[t][(t + 1) % size] = 1;
            in[t][catalyst] = 1; out[t][catalyst] = 1; in[t][fuel] = 1;
        }
        stock[size - 1] = PlannerAmount.ONE;
        stock[catalyst] = PlannerAmount.ONE;
        stock[fuel] = repetitions.multiply(size);
        var result = solve(in, out, new boolean[size + 2], counts, stock);
        assertEquals(CycleStartupSolver.Status.FEASIBLE, result.status());
        assertEquals(1, result.states());
        assertEquals(size, result.witness().size());
        assertEquals(repetitions, result.witness().getLast().repetitions());
        assertEquals(size, result.witness().getLast().repeatWidth());
        for (int i = 0; i < size; i++) {
            assertEquals((size - 1 + i) % size, result.witness().get(i).transition());
            assertEquals(PlannerAmount.ONE, result.witness().get(i).exactCount());
        }
        stock[size - 1] = PlannerAmount.ZERO;
        assertEquals(CycleStartupSolver.Status.INFEASIBLE_FIXED_VECTOR,
            solve(in, out, new boolean[size + 2], counts, stock).status());
    }

    @Test void unusedRecipesDoNotAddResourceRequirementsOrCoupling() throws Exception {
        verify(new long[][] {{1, 0}, {1, 1}}, new long[][] {{0, 1}, {1, 0}},
            new boolean[2], new int[] {1, 0}, new long[] {1, 0});
        var empty = solve(new long[][] {{1}}, new long[][] {{0}}, new boolean[1], amounts(0), amounts(0L));
        assertEquals(CycleStartupSolver.Status.FEASIBLE, empty.status());
        assertTrue(empty.witness().isEmpty());
    }

    @Test void aCoupledLatticeCutoffIsUnknownAndReleasesScratchMemory() throws Exception {
        CycleMemoryBudget memory = new CycleMemoryBudget(1_000_000);
        memory.retain(128);
        var result = CycleStartupSolver.solve(new long[][] {{2, 0}, {0, 1}},
            new long[][] {{0, 2}, {1, 0}}, new boolean[2], amounts(2000, 2000), amounts(3L, 2L),
            ECOCancellation.NONE, memory);
        assertEquals(CycleStartupSolver.Status.UNKNOWN, result.status());
        assertTrue(result.witness().isEmpty());
        assertEquals(128, memory.retainedBytes());
    }

    @Test void memoryExhaustionDoesNotBecomeAnInfeasibilityProof() throws Exception {
        CycleMemoryBudget memory = new CycleMemoryBudget(256);
        memory.retain(128);
        var result = CycleStartupSolver.solve(new long[][] {{1}}, new long[][] {{2}}, new boolean[1],
            amounts(1), amounts(1L), ECOCancellation.NONE, memory);
        assertEquals(CycleStartupSolver.Status.UNKNOWN, result.status());
        assertEquals(128, memory.retainedBytes());
    }

    @Test void cancellationAndSharedWorkExhaustionPropagateAfterReleasingScratchMemory() {
        CycleMemoryBudget memory = new CycleMemoryBudget(1_000_000);
        memory.retain(128);
        int[] checkpoints = {0};
        ECOCancellation cancel = () -> { if (++checkpoints[0] == 6) throw new InterruptedException("cancel"); };
        assertThrows(InterruptedException.class, () -> CycleStartupSolver.solve(new long[][] {{1, 0}, {1, 1}},
            new long[][] {{0, 1}, {1, 0}}, new boolean[2], amounts(2, 2), amounts(2L, 1L), cancel, memory));
        assertEquals(128, memory.retainedBytes());
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 6, Long.MAX_VALUE, () -> 0L);
        assertThrows(ECOPlanningBudget.Exhausted.class, () -> CycleStartupSolver.solve(new long[][] {{1, 0}, {1, 1}},
            new long[][] {{0, 1}, {1, 0}}, new boolean[2], amounts(2, 2), amounts(2L, 1L), budget, memory));
        assertEquals(128, memory.retainedBytes());
    }

    private static CycleStartupSolver.Result solve(long[][] in, long[][] out, boolean[] imports,
            PlannerAmount[] counts, PlannerAmount[] stock) throws InterruptedException {
        return CycleStartupSolver.solve(in, out, imports, counts, stock, ECOCancellation.NONE,
            new CycleMemoryBudget(64L * 1024 * 1024));
    }

    private static void verify(long[][] in, long[][] out, boolean[] imports, int[] counts, long[] stock)
            throws InterruptedException {
        boolean expected = reachable(in, out, imports, counts, stock, new HashSet<>());
        var result = solve(in, out, imports, amounts(counts), amounts(stock));
        String context = "in=" + Arrays.deepToString(in) + " out=" + Arrays.deepToString(out)
            + " counts=" + Arrays.toString(counts) + " stock=" + Arrays.toString(stock);
        assertEquals(expected ? CycleStartupSolver.Status.FEASIBLE : CycleStartupSolver.Status.INFEASIBLE_FIXED_VECTOR,
            result.status(), context);
        if (expected) replay(in, out, imports, counts, stock, result);
        else assertTrue(result.witness().isEmpty());
    }

    /** Independent physical DFS: fire once, subtract each input, then add its outputs. No startup algebra. */
    private static boolean reachable(long[][] in, long[][] out, boolean[] imports, int[] left, long[] stock,
            Set<String> visited) {
        if (Arrays.stream(left).allMatch(n -> n == 0)) return true;
        if (!visited.add(Arrays.toString(left) + Arrays.toString(stock))) return false;
        for (int t = 0; t < left.length; t++) {
            if (left[t] == 0) continue;
            long[] after = fire(in[t], out[t], imports, stock);
            if (after == null) continue;
            int[] remaining = left.clone(); remaining[t]--;
            if (reachable(in, out, imports, remaining, after, visited)) return true;
        }
        return false;
    }

    private static long[] replay(long[][] in, long[][] out, boolean[] imports, int[] counts, long[] stock,
            CycleStartupSolver.Result result) {
        int[] fired = new int[counts.length];
        long[] after = stock.clone();
        var runs = result.witness();
        for (int start = 0; start < runs.size();) {
            int end = start;
            while (end < runs.size() && runs.get(end).repetitions().equals(PlannerAmount.ONE)) end++;
            int plainEnd = end == runs.size() ? end : end - runs.get(end).repeatWidth() + 1;
            for (int i = start; i < plainEnd; i++) after = replayRun(in, out, imports, after, fired, runs.get(i));
            if (end == runs.size()) break;
            assertTrue(plainEnd >= start, "Compressed periods must not overlap");
            for (long repeat = 0; repeat < runs.get(end).repetitions().longValueExact(); repeat++)
                for (int i = plainEnd; i <= end; i++) after = replayRun(in, out, imports, after, fired, runs.get(i));
            start = end + 1;
        }
        assertArrayEquals(counts, fired, "The physical order must preserve every fixed firing count");
        return after;
    }

    private static long[] replayRun(long[][] in, long[][] out, boolean[] imports, long[] stock, int[] fired,
            BoundedCycleSolver.BatchFiring run) {
        for (long step = 0; step < run.exactCount().longValueExact(); step++) {
            stock = fire(in[run.transition()], out[run.transition()], imports, stock);
            assertNotNull(stock, "A witness must never borrow unreported finite stock");
            fired[run.transition()]++;
        }
        return stock;
    }

    private static long[] fire(long[] in, long[] out, boolean[] imports, long[] stock) {
        long[] after = stock.clone();
        for (int k = 0; k < stock.length; k++) {
            if (imports[k]) after[k] = Math.max(after[k], in[k]);
            if (after[k] < in[k]) return null;
            after[k] -= in[k];
        }
        for (int k = 0; k < stock.length; k++) after[k] += out[k];
        return after;
    }

    private static PlannerAmount[] amounts(int... values) {
        return Arrays.stream(values).mapToObj(PlannerAmount::of).toArray(PlannerAmount[]::new);
    }

    private static PlannerAmount[] amounts(long... values) {
        return Arrays.stream(values).mapToObj(PlannerAmount::of).toArray(PlannerAmount[]::new);
    }
}
