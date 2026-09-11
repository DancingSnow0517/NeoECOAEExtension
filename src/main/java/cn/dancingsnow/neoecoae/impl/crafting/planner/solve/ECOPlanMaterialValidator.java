package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;

/** Validates the physical material contract of a supposedly successful executable plan. */
public final class ECOPlanMaterialValidator {
    private ECOPlanMaterialValidator() {
    }

    /**
     * Returns the first material deficit, or {@code null} when every primary pattern input can be supplied by the
     * plan's reserved initial items, emitted items, and physical pattern outputs. This check intentionally uses the
     * raw AE2 pattern contract: it is the contract the CPU will execute after all planner metadata has been discarded.
     */
    public static @Nullable Issue firstDeficit(SolveState state, AEKey finalGoal, long finalAmount,
            KeyCounter initialInventory, @Nullable CompiledNetwork network) {
        if (state == null || finalGoal == null || finalAmount <= 0L) {
            return new Issue(null, PlannerAmount.ZERO, PlannerAmount.ZERO, "INVALID_PLAN_ARGUMENT");
        }

        Map<AEKey, PlannerAmount> supply = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> demand = new LinkedHashMap<>();
        List<InputDemand> componentInsensitiveDemands = new ArrayList<>();
        Map<IPatternDetails, List<CompiledInput>> compiledInputs = compiledInputs(network);
        // Only usedItems are transferred into the crafting CPU when the plan is submitted. Unreserved network
        // inventory is therefore not executable supply and must not be allowed to close this material balance.
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
                    AEKey remaining = input.getRemainingKey(primary.what());
                    if (sameItemStateTransition(primary.what(), remaining)) {
                        // The special resolver reserves/manufactures working stock once. Charging the source and
                        // returned damage/component keys per firing would recreate the false material cycle.
                        continue;
                    }
                    PlannerAmount inputAmount = PlannerAmount.of(primary.amount())
                        .multiply(input.getMultiplier()).multiply(times);
                    if (ignoresComponents(compiledInputs.get(pattern), input)) {
                        componentInsensitiveDemands.add(new InputDemand(primary.what(), inputAmount));
                    } else {
                        add(demand, primary.what(), inputAmount);
                    }

                    // AE2 retains a remainder/container when the input contract declares one. Include it as physical
                    // supply so a valid closed-loop plan is not rejected by this raw balance check.
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
            PlannerAmount supplied = consumeSupply(supply, entry.getKey(), entry.getValue(), false);
            if (supplied.compareTo(entry.getValue()) < 0) {
                return new Issue(entry.getKey(), entry.getValue(), supplied, "MATERIAL_DEFICIT");
            }
        }
        for (InputDemand entry : componentInsensitiveDemands) {
            PlannerAmount supplied = consumeSupply(supply, entry.key(), entry.amount(), true);
            if (supplied.compareTo(entry.amount()) < 0) {
                return new Issue(entry.key(), entry.amount(), supplied, "ID_ONLY_MATERIAL_DEFICIT");
            }
        }
        return null;
    }

    public static @Nullable Issue firstDeficit(SolveState state, AEKey finalGoal, long finalAmount,
            KeyCounter initialInventory) {
        return firstDeficit(state, finalGoal, finalAmount, initialInventory, null);
    }

    /** Backward-compatible form for callers that intentionally validate only the planner counters. */
    public static @Nullable Issue firstDeficit(SolveState state, AEKey finalGoal, long finalAmount) {
        return firstDeficit(state, finalGoal, finalAmount, null);
    }

    private static void add(Map<AEKey, PlannerAmount> counter, AEKey key, PlannerAmount amount) {
        if (key == null || amount == null || amount.signum() <= 0) return;
        counter.merge(key, amount, PlannerAmount::add);
    }

    private static Map<IPatternDetails, List<CompiledInput>> compiledInputs(@Nullable CompiledNetwork network) {
        Map<IPatternDetails, List<CompiledInput>> result = new IdentityHashMap<>();
        if (network == null) return result;
        network.producers().values().forEach(patterns -> patterns.forEach(pattern ->
            result.put(pattern.details(), pattern.inputs())));
        return result;
    }

    private static boolean ignoresComponents(@Nullable List<CompiledInput> inputs,
            IPatternDetails.IInput source) {
        return inputs != null && inputs.stream().anyMatch(input -> input.source() == source
            && input.ignoresComponents());
    }

    private static PlannerAmount consumeSupply(Map<AEKey, PlannerAmount> supply, AEKey key,
            PlannerAmount requested, boolean ignoreComponents) {
        if (requested.signum() <= 0) return PlannerAmount.ZERO;
        if (!ignoreComponents || !(key instanceof AEItemKey wanted)) {
            PlannerAmount available = supply.getOrDefault(key, PlannerAmount.ZERO);
            PlannerAmount taken = requested.min(available);
            remove(supply, key, taken);
            return taken;
        }
        PlannerAmount remaining = requested;
        PlannerAmount consumed = PlannerAmount.ZERO;
        for (var entry : new ArrayList<>(supply.entrySet())) {
            if (remaining.isZero() || !(entry.getKey() instanceof AEItemKey candidate)
                    || candidate.getItem() != wanted.getItem()) continue;
            PlannerAmount taken = remaining.min(entry.getValue());
            remove(supply, entry.getKey(), taken);
            consumed = consumed.add(taken);
            remaining = remaining.subtract(taken);
        }
        return consumed;
    }

    private static void remove(Map<AEKey, PlannerAmount> supply, AEKey key, PlannerAmount amount) {
        if (amount.signum() <= 0) return;
        PlannerAmount remaining = supply.getOrDefault(key, PlannerAmount.ZERO).subtract(amount);
        if (remaining.signum() > 0) supply.put(key, remaining);
        else supply.remove(key);
    }

    private static boolean sameItemStateTransition(AEKey source, AEKey returned) {
        if (!(source instanceof AEItemKey sourceItem) || !(returned instanceof AEItemKey returnedItem)) return false;
        ItemStack left = sourceItem.toStack(1);
        ItemStack right = returnedItem.toStack(1);
        return !left.isEmpty() && !right.isEmpty() && ItemStack.isSameItem(left, right);
    }

    public record Issue(@Nullable AEKey key, PlannerAmount required, PlannerAmount supplied, String reason) {
        public Issue {
            required = required == null ? PlannerAmount.ZERO : required;
            supplied = supplied == null ? PlannerAmount.ZERO : supplied;
        }
    }

    private record InputDemand(AEKey key, PlannerAmount amount) {}
}
