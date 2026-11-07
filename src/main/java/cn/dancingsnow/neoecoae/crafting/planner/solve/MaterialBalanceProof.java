package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleStateEquation;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-network material balance as an infeasibility proof for the fallback route search.
 *
 * <p>Every executable plan, whatever its producer choices, firing order, seeds or byproduct credits, has a
 * non-negative firing vector x over the compiled patterns with {@code stock + (out - in) x >= target}. Each
 * physical pattern is one column, so all outputs of one firing are counted once; the optimistic per-key bound
 * counts a byproduct-rich firing once per output and cannot see that. When the linear relaxation has no
 * solution, no alternative producer route can succeed and searching them only spends the session.
 *
 * <p>The proof is withheld (returns false) whenever the compiled model is not an exact relaxation: unsupported
 * or incomplete contracts, special (durability/tool) patterns, component-insensitive or substitutable inputs.
 */
final class MaterialBalanceProof {
    private static final long WORK = 500_000L;
    private static final long NANOS = 2_000_000_000L;

    private MaterialBalanceProof() {}

    /**
     * @param floors amounts that must remain in stock after the order (retained cycle seeds); may be empty.
     * @return true only when the order is proven infeasible; false means "not proven", never "feasible".
     */
    static boolean infeasible(CompiledNetwork network, PlannerInventorySnapshot inventory, long amount,
            Map<AEKey, PlannerAmount> floors, boolean ignorePatternSubstitutions, ECOCancellation cancellation)
            throws InterruptedException {
        if (amount <= 0L) return false;
        List<CompiledPattern> patterns = new ArrayList<>();
        Map<IPatternDetails, List<GenericStack>> seen = new Reference2ObjectOpenHashMap<>();
        for (List<CompiledPattern> views : network.producers().values()) {
            for (CompiledPattern view : views) {
                cancellation.checkpoint();
                List<GenericStack> known = seen.putIfAbsent(view.details(), view.grossOutputs());
                if (known != null) {
                    // Views of one pattern differ only where a component-insensitive key aliased an output. One
                    // column cannot credit both readings, and keeping either could be stricter than the planner.
                    if (!known.equals(view.grossOutputs())) return false;
                    continue;
                }
                if (!exact(view, ignorePatternSubstitutions)) return false;
                patterns.add(view);
            }
        }
        if (patterns.isEmpty()) return false;
        Map<AEKey, Integer> keys = new LinkedHashMap<>();
        keys.put(network.goal(), 0);
        List<Map<Integer, PlannerAmount>> columns = new ArrayList<>(patterns.size());
        for (CompiledPattern pattern : patterns) {
            cancellation.checkpoint();
            Map<Integer, PlannerAmount> column = new LinkedHashMap<>();
            for (CompiledInput input : pattern.inputs()) {
                // A reusable same-key return is net zero; any other remainder is credited as an output below.
                PlannerAmount consumed = input.reusable() ? PlannerAmount.ZERO : input.amountPerPattern();
                if (input.remainderKey() != null && input.remainderKey().equals(input.key()) && !input.reusable()) {
                    consumed = consumed.subtract(input.remainderAmountPerPattern()).max(PlannerAmount.ZERO);
                }
                if (consumed.signum() > 0) {
                    column.merge(index(keys, input.key()), consumed, PlannerAmount::add);
                }
                if (input.remainderKey() != null && !input.remainderKey().equals(input.key())
                        && input.remainderAmountPerPattern().signum() > 0) {
                    column.merge(index(keys, input.remainderKey()),
                        input.remainderAmountPerPattern().negate(), PlannerAmount::add);
                }
            }
            for (GenericStack output : pattern.grossOutputs()) {
                column.merge(index(keys, output.what()), PlannerAmount.of(output.amount()).negate(),
                    PlannerAmount::add);
            }
            column.values().removeIf(value -> value.signum() == 0);
            columns.add(column);
        }
        floors.keySet().forEach(key -> index(keys, key));
        SolveState stock = new SolveState(inventory);
        boolean[] imports = new boolean[keys.size()];
        PlannerAmount[] initial = new PlannerAmount[keys.size()];
        PlannerAmount[] target = new PlannerAmount[keys.size()];
        Arrays.fill(target, PlannerAmount.ZERO);
        for (var entry : keys.entrySet()) {
            AEKey key = entry.getKey();
            int slot = entry.getValue();
            boolean goal = key.equals(network.goal());
            imports[slot] = !goal && (network.emittable().contains(key) || inventory.isUnbounded(key));
            // AE2 crafts the requested amount anew: stored copies of the goal do not count toward it.
            initial[slot] = goal ? PlannerAmount.ZERO : stock.stored.get(key);
            target[slot] = floors.getOrDefault(key, PlannerAmount.ZERO).max(PlannerAmount.ZERO);
        }
        target[0] = target[0].add(PlannerAmount.of(amount));
        ECOCancellation bounded = cancellation instanceof ECOPlanningBudget budget ? budget.slice(WORK, NANOS)
            : new ECOPlanningBudget(cancellation, WORK, NANOS, System::nanoTime);
        try {
            return CycleStateEquation.solveSparseAmounts(columns, imports, initial, target, true, bounded)
                .status() == CycleStateEquation.Status.INFEASIBLE;
        } catch (ECOPlanningBudget.Exhausted exhausted) {
            // An unfinished relaxation proves nothing; keep the caller's own allowance for its search.
            cancellation.checkpoint();
            return false;
        }
    }

    /** Patterns whose compiled view is an exact, deterministic relaxation of what the planner may execute. */
    private static boolean exact(CompiledPattern pattern, boolean ignorePatternSubstitutions) {
        if (!pattern.fastSupported() || !pattern.semantics().completeForStaticPlanning()
                || pattern.specialAnalysis().special() || pattern.outputs().isEmpty()) return false;
        for (GenericStack output : pattern.grossOutputs()) {
            if (output == null || output.what() == null || output.amount() <= 0) return false;
        }
        for (CompiledInput input : pattern.inputs()) {
            if (!input.fastSupported() || input.ignoresComponents() || input.amountPerPattern().signum() <= 0) {
                return false;
            }
            if (!ignorePatternSubstitutions && input.source() != null) {
                GenericStack[] possible;
                try {
                    possible = input.source().getPossibleInputs();
                } catch (RuntimeException e) {
                    return false;
                }
                if (possible != null && possible.length > 1) return false;
            }
        }
        return true;
    }

    private static int index(Map<AEKey, Integer> keys, AEKey key) {
        return keys.computeIfAbsent(key, ignored -> keys.size());
    }
}
