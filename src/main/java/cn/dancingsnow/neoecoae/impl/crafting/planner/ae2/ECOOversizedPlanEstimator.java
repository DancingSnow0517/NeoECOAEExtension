package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOBigIntegerDagSolver;
import java.math.BigInteger;
import java.util.Optional;

/** Computes exact display-only byte usage when a DAG plan no longer fits AE2's long contract. */
public final class ECOOversizedPlanEstimator {
    private static final BigInteger EIGHT = BigInteger.valueOf(8L);

    private ECOOversizedPlanEstimator() {
    }

    public static Optional<BigInteger> estimateBytes(ECOAE2PlanningSnapshot snapshot) {
        var graph = ECOGraphPruner.targetReachable(snapshot.problem());
        return ECOBigIntegerDagSolver.trySolve(snapshot.problem(), graph)
            .map(result -> estimateBytes(snapshot, result));
    }

    private static BigInteger estimateBytes(
        ECOAE2PlanningSnapshot snapshot,
        ECOBigIntegerDagSolver.Result<ECOAE2PatternVariant> result
    ) {
        Fraction bytes = new Fraction();
        bytes.add(
            BigInteger.valueOf(snapshot.requestedAmount()).multiply(EIGHT),
            snapshot.requestedKey().getType().getAmountPerByte()
        );

        long graphNodes = 1L;
        for (var operation : snapshot.problem().operations()) {
            BigInteger count = result.executions().getOrDefault(operation.reference(), BigInteger.ZERO);
            if (count.signum() <= 0) {
                continue;
            }
            bytes.add(count, 1L);
            graphNodes += 1L + snapshot.inputSlotCounts().getOrDefault(
                operation.reference(),
                operation.reference().selectedInputs().size()
            );
            for (var input : operation.inputs().entrySet()) {
                bytes.add(
                    BigInteger.valueOf(input.getValue()).multiply(count).multiply(EIGHT),
                    input.getKey().getType().getAmountPerByte()
                );
            }
        }
        bytes.add(BigInteger.valueOf(graphNodes).multiply(EIGHT), 1L);
        return bytes.ceil().max(BigInteger.ONE);
    }

    private static final class Fraction {
        private BigInteger numerator = BigInteger.ZERO;
        private BigInteger denominator = BigInteger.ONE;

        void add(BigInteger value, long divisor) {
            if (divisor <= 0L) {
                throw new IllegalArgumentException("Byte divisor must be positive");
            }
            BigInteger otherDenominator = BigInteger.valueOf(divisor);
            numerator = numerator.multiply(otherDenominator).add(value.multiply(denominator));
            denominator = denominator.multiply(otherDenominator);
            BigInteger gcd = numerator.gcd(denominator);
            if (!gcd.equals(BigInteger.ONE)) {
                numerator = numerator.divide(gcd);
                denominator = denominator.divide(gcd);
            }
        }

        BigInteger ceil() {
            BigInteger[] division = numerator.divideAndRemainder(denominator);
            return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
        }
    }
}
