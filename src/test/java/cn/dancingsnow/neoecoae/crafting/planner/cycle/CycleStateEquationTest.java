package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CycleStateEquationTest {
    @Test void integerOptimaAgreeWithIndependentExhaustiveEnumeration() throws Exception {
        Random random = new Random(20260926);
        for (int sample = 0; sample < 250; sample++) {
            long[][] in = new long[3][4], out = new long[3][4];
            long[] stock = {random.nextInt(4), random.nextInt(4), random.nextInt(4), 6};
            long[] target = {random.nextInt(8), random.nextInt(8), random.nextInt(8), 0};
            for (int t = 0; t < 3; t++) {
                in[t][3] = 1; // Every firing consumes finite fuel: enumeration is complete.
                for (int k = 0; k < 3; k++) { in[t][k] = random.nextInt(4); out[t][k] = random.nextInt(5); }
            }
            int optimum = Integer.MAX_VALUE;
            for (int a = 0; a <= 6; a++) for (int b = 0; b <= 6 - a; b++) for (int c = 0; c <= 6 - a - b; c++) {
                int[] counts = {a, b, c};
                boolean feasible = true;
                for (int k = 0; k < 4; k++) {
                    long balance = stock[k];
                    for (int t = 0; t < 3; t++) balance += (out[t][k] - in[t][k]) * counts[t];
                    feasible &= balance >= target[k];
                }
                if (feasible) optimum = Math.min(optimum, a + b + c);
            }
            var result = CycleStateEquation.solve(in, out, new boolean[4], amounts(stock), amounts(target), ECOCancellation.NONE);
            if (optimum == Integer.MAX_VALUE) {
                assertEquals(CycleStateEquation.Status.INFEASIBLE, result.status(), "sample=" + sample);
            } else {
                assertEquals(CycleStateEquation.Status.OPTIMAL, result.status(), "sample=" + sample);
                assertEquals(optimum, Arrays.stream(result.counts()).mapToLong(PlannerAmount::longValueExact).sum());
                for (int k = 0; k < 4; k++) {
                    PlannerAmount balance = PlannerAmount.of(stock[k]);
                    for (int t = 0; t < 3; t++) balance = balance.add(result.counts()[t].multiply(out[t][k] - in[t][k]));
                    assertTrue(balance.compareTo(PlannerAmount.of(target[k])) >= 0);
                }
            }
        }
    }

    @Test void fractionalRelaxationDoesNotCertifyIntegerFeasibility() throws Exception {
        // x >= 1/2 from output; x <= 1/2 from finite input. No integer solution.
        var result = CycleStateEquation.solve(new long[][] {{0, 2}}, new long[][] {{2, 0}},
            new boolean[2], amounts(0, 1), amounts(1, 0), ECOCancellation.NONE);
        assertEquals(CycleStateEquation.Status.INFEASIBLE, result.status());
    }

    @Test void exactArithmeticDoesNotRoundAwayOneMissingItemAboveDoublePrecision() throws Exception {
        BigInteger stock = BigInteger.TEN.pow(50);
        var result = CycleStateEquation.solve(new long[][] {{1, 0}}, new long[][] {{0, 1}}, new boolean[2],
            new PlannerAmount[] {PlannerAmount.of(stock), PlannerAmount.ZERO},
            new PlannerAmount[] {PlannerAmount.ZERO, PlannerAmount.of(stock.add(BigInteger.ONE))}, ECOCancellation.NONE);
        assertEquals(CycleStateEquation.Status.INFEASIBLE, result.status());
    }

    @Test void unboundedIrrelevantGrowthStillAllowsAnInfeasibilityProof() throws Exception {
        var result = CycleStateEquation.solve(new long[][] {{1, 0, 0}, {0, 1, 0}},
            new long[][] {{2, 0, 0}, {0, 0, 1}}, new boolean[3], amounts(1, 0, 0), amounts(0, 0, 1), ECOCancellation.NONE);
        assertEquals(CycleStateEquation.Status.INFEASIBLE, result.status());
    }

    @Test void boundaryFuelIsImportedAndUnitScalingDoesNotChangeTheFiringVector() throws Exception {
        for (long scale : new long[] {1, 1000, 1_000_000_000}) {
            var result = CycleStateEquation.solve(new long[][] {{scale, 0, 1000}, {0, scale, 0}},
                new long[][] {{0, scale, 0}, {2 * scale, 0, 0}}, new boolean[] {false, false, true},
                amounts(scale, 0, 0), amounts(10 * scale, 0, 0), ECOCancellation.NONE);
            assertEquals(CycleStateEquation.Status.OPTIMAL, result.status());
            assertArrayEquals(amounts(9, 9), result.counts());
        }
    }

    @Test void graphDimensionsDoNotImposeAnArbitrarySearchOrStructureCutoff() {
        var small = CycleSolveLimits.forWorkload(8, 3, 0);
        var wide = CycleSolveLimits.forWorkload(16, 3, 0);
        assertEquals(CycleSolveLimits.DEFAULT, small);
        assertEquals(small, wide);
        assertEquals(wide, CycleSolveLimits.forWorkload(1000, 1000, 1000));
        assertEquals(0, wide.maxKeys());
        assertEquals(0, wide.maxPatterns());
        assertEquals(0, wide.maxStates());
        assertEquals(0, wide.maxFirings());
    }

    @Test void largeExactIntegersHaveNoBitCutoffAndCancellationPropagates() throws Exception {
        var result = CycleStateEquation.solve(new long[][] {{1}}, new long[][] {{2}}, new boolean[1],
            amounts(1), new PlannerAmount[] {PlannerAmount.of(BigInteger.ONE.shiftLeft(5000))}, ECOCancellation.NONE);
        assertEquals(CycleStateEquation.Status.OPTIMAL, result.status());
        assertEquals(BigInteger.ONE.shiftLeft(5000).subtract(BigInteger.ONE), result.counts()[0].toBigInteger());
        assertThrows(InterruptedException.class, () -> CycleStateEquation.solve(new long[][] {{1}},
            new long[][] {{2}}, new boolean[1], amounts(1), amounts(10), () -> { throw new InterruptedException(); }));
    }

    @Test void integerBranchingCanProveTheOptimumBeyondTheFormerNodeBudget() throws Exception {
        int variables = 9;
        long[][] consumed = new long[variables][variables + 1], produced = new long[variables][variables + 1];
        long[] stock = new long[variables + 1], target = new long[variables + 1];
        for (int t = 0; t < variables; t++) {
            consumed[t][t] = 1;
            stock[t] = 1;
            produced[t][variables] = 2;
        }
        target[variables] = 9;
        var result = CycleStateEquation.solve(consumed, produced, new boolean[variables + 1],
            amounts(stock), amounts(target), ECOCancellation.NONE);
        assertEquals(CycleStateEquation.Status.OPTIMAL, result.status());
        assertEquals(5, Arrays.stream(result.counts()).mapToLong(PlannerAmount::longValueExact).sum());
    }

    @Test void matrixMemoryIsLimitedInBytesRatherThanVariablesOrIntegerBits() {
        assertThrows(CycleMemoryBudget.Exhausted.class, () -> CycleStateEquation.solve(
            new long[][] {{1}}, new long[][] {{2}}, new boolean[1], amounts(1), amounts(10),
            ECOCancellation.NONE, new CycleMemoryBudget(512)));
    }

    private static PlannerAmount[] amounts(long... values) {
        return Arrays.stream(values).mapToObj(PlannerAmount::of).toArray(PlannerAmount[]::new);
    }
}
