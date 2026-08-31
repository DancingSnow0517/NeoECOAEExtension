package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Validates the physical material contract of a supposedly successful executable plan. */
public final class ECOPlanMaterialValidator {
    private ECOPlanMaterialValidator() {
    }

    /**
     * Returns the first material deficit, or {@code null} when every primary pattern input can be supplied by the
     * plan's initial/emitted items and physical pattern outputs. This check intentionally uses the raw AE2 pattern
     * contract: it is the contract the CPU will execute after all planner metadata has been discarded.
     */
    public static @Nullable Issue firstDeficit(SolveState state, AEKey finalGoal, long finalAmount) {
        if (state == null || finalGoal == null || finalAmount <= 0L) {
            return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "INVALID_PLAN_ARGUMENT");
        }

        Map<AEKey, PlannerAmount> supply = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> demand = new LinkedHashMap<>();
        state.usedAmounts().forEach((key, amount) -> add(supply, key, amount));
        state.emittedAmounts().forEach((key, amount) -> add(supply, key, amount));

        try {
            for (var task : state.plannerPatternTimes().entrySet()) {
                IPatternDetails pattern = task.getKey();
                PlannerAmount times = task.getValue();
                if (pattern == null || times == null || times.signum() < 0) {
                    return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "INVALID_PATTERN_TASK");
                }
                if (times.isZero()) continue;

                GenericStack[] outputs = pattern.getOutputs() == null
                    ? null : pattern.getOutputs().toArray(GenericStack[]::new);
                if (outputs == null) {
                    return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "NULL_PATTERN_OUTPUTS");
                }
                for (GenericStack output : outputs) {
                    if (output == null || output.what() == null || output.amount() <= 0L) {
                        return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "INVALID_PATTERN_OUTPUT");
                    }
                    add(supply, output.what(), PlannerAmount.of(output.amount()).multiply(times));
                }

                IPatternDetails.IInput[] inputs = pattern.getInputs();
                if (inputs == null) {
                    return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "NULL_PATTERN_INPUTS");
                }
                for (IPatternDetails.IInput input : inputs) {
                    if (input == null) {
                        return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "NULL_PATTERN_INPUT");
                    }
                    GenericStack[] possible = input.getPossibleInputs();
                    if (possible == null || possible.length == 0 || possible[0] == null
                            || possible[0].what() == null || possible[0].amount() <= 0L
                            || input.getMultiplier() <= 0L) {
                        return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "INVALID_PATTERN_INPUT");
                    }
                    GenericStack primary = possible[0];
                    PlannerAmount inputAmount = PlannerAmount.of(primary.amount())
                        .multiply(input.getMultiplier()).multiply(times);
                    add(demand, primary.what(), inputAmount);

                    // AE2 retains a remainder/container when the input contract declares one. Include it as physical
                    // supply so a valid closed-loop plan is not rejected by this raw balance check.
                    AEKey remaining = input.getRemainingKey(primary.what());
                    if (remaining != null) {
                        add(supply, remaining, PlannerAmount.of(input.getMultiplier()).multiply(times));
                    }
                }
            }
        } catch (RuntimeException rejected) {
            return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO,
                "PATTERN_CONTRACT_FAILED:" + rejected.getClass().getSimpleName());
        }

        add(demand, finalGoal, PlannerAmount.of(finalAmount));
        for (var entry : demand.entrySet()) {
            PlannerAmount available = supply.getOrDefault(entry.getKey(), PlannerAmount.ZERO);
            if (available.compareTo(entry.getValue()) < 0) {
                return new Issue(entry.getKey(), entry.getValue(), available, "MATERIAL_DEFICIT");
            }
        }
        return null;
    }

    private static void add(Map<AEKey, PlannerAmount> counter, AEKey key, PlannerAmount amount) {
        if (key == null || amount == null || amount.signum() <= 0) return;
        counter.merge(key, amount, PlannerAmount::add);
    }

    public record Issue(@Nullable AEKey key, PlannerAmount required, PlannerAmount supplied, String reason) {
        public Issue {
            required = required == null ? PlannerAmount.ZERO : required;
            supplied = supplied == null ? PlannerAmount.ZERO : supplied;
        }
    }
}
