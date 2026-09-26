package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Necessary material balance, independent of firing order: stock + (out - in) x >= target, x >= 0.
 * Exact two-phase simplex minimizes total firings; bounded integer branching refines fractional answers.
 * Infeasibility is a proof only after every branch closes. A feasible vector is never a liveness proof.
 * Boundary ingredients have unlimited imports here; the caller still verifies their real DAG supply.
 */
final class CycleStateEquation {
    enum Status { OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN }
    record Result(Status status, PlannerAmount[] counts) {}
    private record Constraint(BigInteger[] coefficients, BigInteger bound) {}
    private static final int MAX_NODES = 128;
    private static final int MAX_PIVOTS = 2_048;
    private static final int MAX_BITS = 4_096;

    static Result solve(long[][] consumed, long[][] produced, boolean[] boundary,
            PlannerAmount[] stock, PlannerAmount[] target, ECOCancellation cancellation) throws InterruptedException {
        int variables = consumed.length;
        List<Constraint> constraints = new ArrayList<>();
        for (int key = 0; key < stock.length; key++) {
            if (boundary[key]) continue;
            BigInteger[] row = new BigInteger[variables];
            for (int p = 0; p < variables; p++) {
                row[p] = BigInteger.valueOf(consumed[p][key]).subtract(BigInteger.valueOf(produced[p][key]));
            }
            constraints.add(new Constraint(row, stock[key].subtract(target[key]).toBigInteger()));
        }
        return minimize(variables, constraints, cancellation);
    }

    private static Result minimize(int variables, List<Constraint> constraints, ECOCancellation cancellation)
            throws InterruptedException {
        var pending = new ArrayDeque<List<Constraint>>();
        pending.push(constraints);
        BigInteger[] best = null;
        BigInteger bestCost = null;
        Budget budget = new Budget(cancellation);
        int nodes = 0;
        try {
            while (!pending.isEmpty()) {
                if (++nodes > MAX_NODES) throw new Limit();
                cancellation.checkpoint();
                List<Constraint> branch = pending.pop();
                Rational[] vector = new Tableau(variables, branch, budget).solve();
                if (vector == null) continue;
                Rational cost = Rational.ZERO;
                int fractional = -1;
                for (int p = 0; p < variables; p++) {
                    cost = cost.add(vector[p]);
                    if (!vector[p].denominator.equals(BigInteger.ONE) && fractional < 0) fractional = p;
                }
                if (bestCost != null && cost.compareTo(Rational.of(bestCost)) >= 0) continue;
                if (fractional < 0) {
                    best = Arrays.stream(vector).map(value -> value.numerator).toArray(BigInteger[]::new);
                    bestCost = cost.numerator;
                    continue;
                }
                BigInteger floor = vector[fractional].numerator.divide(vector[fractional].denominator);
                BigInteger[] row = new BigInteger[variables];
                Arrays.fill(row, BigInteger.ZERO);
                row[fractional] = BigInteger.ONE;
                var lower = new ArrayList<>(branch);
                lower.add(new Constraint(row, floor));
                row = row.clone();
                row[fractional] = BigInteger.ONE.negate();
                var upper = new ArrayList<>(branch);
                upper.add(new Constraint(row, floor.add(BigInteger.ONE).negate()));
                pending.push(lower);
                pending.push(upper);
            }
        } catch (Limit exhausted) {
            return result(best == null ? Status.UNKNOWN : Status.FEASIBLE, best);
        }
        return result(best == null ? Status.INFEASIBLE : Status.OPTIMAL, best);
    }

    private static Result result(Status status, BigInteger[] vector) {
        return new Result(status, vector == null ? null
            : Arrays.stream(vector).map(PlannerAmount::of).toArray(PlannerAmount[]::new));
    }

    private static final class Limit extends RuntimeException {
        Limit() { super(null, null, false, false); }
    }

    private static final class Budget {
        final ECOCancellation cancellation;
        int pivots;
        Budget(ECOCancellation cancellation) { this.cancellation = cancellation; }
        void pivot() throws InterruptedException {
            cancellation.checkpoint();
            if (++pivots > MAX_PIVOTS) throw new Limit();
        }
    }

    /** Dictionary for Ax <= b, x >= 0; artificial variable supplies the phase-one feasible basis. */
    private static final class Tableau {
        final int m, n;
        final int[] basic, nonbasic;
        final Rational[][] d;
        final Budget budget;

        Tableau(int variables, List<Constraint> rows, Budget budget) {
            m = rows.size(); n = variables; this.budget = budget;
            basic = new int[m]; nonbasic = new int[n + 1];
            d = new Rational[m + 2][n + 2];
            for (var row : d) Arrays.fill(row, Rational.ZERO);
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) d[i][j] = Rational.of(rows.get(i).coefficients[j]);
                basic[i] = n + i;
                d[i][n] = Rational.NEGATIVE_ONE;
                d[i][n + 1] = Rational.of(rows.get(i).bound);
            }
            for (int j = 0; j < n; j++) {
                nonbasic[j] = j;
                d[m][j] = Rational.ONE; // Maximize -sum(x).
            }
            nonbasic[n] = -1;
            d[m + 1][n] = Rational.ONE;
        }

        void pivot(int row, int column) throws InterruptedException {
            budget.pivot();
            Rational inverse = Rational.ONE.divide(d[row][column]);
            for (int i = 0; i < m + 2; i++) {
                if (i == row || d[i][column].signum() == 0) continue;
                budget.cancellation.checkpoint();
                Rational factor = d[i][column].multiply(inverse);
                for (int j = 0; j < n + 2; j++) {
                    if (j != column) d[i][j] = d[i][j].subtract(d[row][j].multiply(factor));
                }
            }
            for (int j = 0; j < n + 2; j++) if (j != column) d[row][j] = d[row][j].multiply(inverse);
            for (int i = 0; i < m + 2; i++) if (i != row) d[i][column] = d[i][column].multiply(inverse).negate();
            d[row][column] = inverse;
            int previous = basic[row]; basic[row] = nonbasic[column]; nonbasic[column] = previous;
        }

        boolean simplex(int phase) throws InterruptedException {
            int objective = phase == 1 ? m + 1 : m;
            while (true) {
                budget.cancellation.checkpoint();
                int entering = -1;
                // Bland's rule, including tie-breaking on the leaving variable, prevents cycling.
                for (int j = 0; j <= n; j++) {
                    if (phase == 2 && nonbasic[j] == -1 || d[objective][j].signum() >= 0) continue;
                    if (entering < 0 || nonbasic[j] < nonbasic[entering]) entering = j;
                }
                if (entering < 0) return true;
                int leaving = -1;
                for (int i = 0; i < m; i++) {
                    if (d[i][entering].signum() <= 0) continue;
                    int comparison = leaving < 0 ? -1 : d[i][n + 1].divide(d[i][entering])
                        .compareTo(d[leaving][n + 1].divide(d[leaving][entering]));
                    if (comparison < 0 || comparison == 0 && basic[i] < basic[leaving]) leaving = i;
                }
                if (leaving < 0) return false;
                pivot(leaving, entering);
            }
        }

        Rational[] solve() throws InterruptedException {
            int row = -1;
            for (int i = 0; i < m; i++) if (row < 0 || d[i][n + 1].compareTo(d[row][n + 1]) < 0) row = i;
            if (row >= 0 && d[row][n + 1].signum() < 0) {
                pivot(row, n);
                if (!simplex(1)) throw new Limit();
                if (d[m + 1][n + 1].signum() < 0) return null;
                if (d[m + 1][n + 1].signum() != 0) throw new Limit();
                for (int i = 0; i < m; i++) {
                    if (basic[i] != -1) continue;
                    int column = -1;
                    for (int j = 0; j <= n; j++) {
                        if (d[i][j].signum() != 0 && (column < 0 || nonbasic[j] < nonbasic[column])) column = j;
                    }
                    if (column >= 0) pivot(i, column);
                }
            }
            if (!simplex(2)) throw new Limit(); // -sum(x), x >= 0 is bounded above by zero.
            Rational[] vector = new Rational[n];
            Arrays.fill(vector, Rational.ZERO);
            for (int i = 0; i < m; i++) if (basic[i] >= 0 && basic[i] < n) vector[basic[i]] = d[i][n + 1];
            return vector;
        }
    }

    private record Rational(BigInteger numerator, BigInteger denominator) implements Comparable<Rational> {
        static final Rational ZERO = of(BigInteger.ZERO), ONE = of(BigInteger.ONE), NEGATIVE_ONE = of(BigInteger.ONE.negate());
        Rational {
            if (denominator.signum() == 0) throw new ArithmeticException("Zero denominator");
            if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
            if (numerator.bitLength() > MAX_BITS || denominator.bitLength() > MAX_BITS) throw new Limit();
        }
        static Rational of(BigInteger value) { return new Rational(value, BigInteger.ONE); }
        int signum() { return numerator.signum(); }
        Rational negate() { return new Rational(numerator.negate(), denominator); }
        Rational add(Rational other) { return new Rational(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator)); }
        Rational subtract(Rational other) { return add(other.negate()); }
        Rational multiply(Rational other) { return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator)); }
        Rational divide(Rational other) { return new Rational(numerator.multiply(other.denominator), denominator.multiply(other.numerator)); }
        @Override public int compareTo(Rational other) { return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator)); }
    }
}
