package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.NetGrowthPatternValidationRegistry;

/** Compiles only the closure reachable from one goal. Inventory and requested amount are deliberately absent. */
public final class CraftingNetworkCompiler {
    public CompiledNetwork compile(ICraftingService service, AEKey goal, ECOCancellation cancellation)
            throws InterruptedException {
        return compile(service, goal, false, cancellation);
    }

    /**
     * Compiles cycle capability evidence only for the opt-in cycle-planning path. The disabled path keeps the
     * original one-pass pattern contract read and does not run the determinism probe.
     */
    public CompiledNetwork compile(ICraftingService service, AEKey goal, boolean cyclePlanningEnabled,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        Set<AEKey> emittable = new HashSet<>();
        Set<AEKey> queued = new HashSet<>();
        ArrayDeque<AEKey> work = new ArrayDeque<>();
        work.add(goal);
        queued.add(goal);
        int nextPatternId = 0;
        int edgeCount = 0;

        while (!work.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = work.removeFirst();
            if (service.canEmitFor(key)) {
                emittable.add(key);
            }
            List<CompiledPattern> compiled = new ArrayList<>();
            for (IPatternDetails details : service.getCraftingFor(key)) {
                cancellation.checkpoint();
                CompiledPattern pattern = compilePattern(nextPatternId++, details, key, cyclePlanningEnabled);
                compiled.add(pattern);
                for (CompiledInput input : pattern.inputs()) {
                    edgeCount++;
                    if (queued.add(input.key())) {
                        work.addLast(input.key());
                    }
                }
            }
            producers.put(key, List.copyOf(compiled));
        }
        return new CompiledNetwork(goal, producers, emittable, nextPatternId, edgeCount);
    }

    private static CompiledPattern compilePattern(int id, IPatternDetails details, AEKey producedKey,
            boolean cyclePlanningEnabled) {
        List<CompiledInput> inputs = new ArrayList<>();
        List<GenericStack> outputs;
        PlannerAmount outputPerPattern = PlannerAmount.ZERO;
        String unsupported = null;
        String contractEvidence = null;
        boolean netGrowthValidated = cyclePlanningEnabled
            && (NetGrowthPatternValidationRegistry.isValidated(details)
                || NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(details));
        try {
            outputs = List.copyOf(details.getOutputs());
            if (outputs.isEmpty()) {
                unsupported = "NO_OUTPUTS";
            }
            for (GenericStack output : outputs) {
                if (output == null || output.what() == null || output.amount() <= 0) {
                    unsupported = "INVALID_OUTPUT";
                    continue;
                }
                if (producedKey.equals(output.what())) {
                    outputPerPattern = outputPerPattern.add(output.amount());
                }
            }
            if (outputPerPattern.signum() <= 0) {
                unsupported = "PRIMARY_OUTPUT_MISMATCH";
            }

            IPatternDetails.IInput[] rawInputs = details.getInputs();
            if (rawInputs == null) {
                unsupported = "NULL_INPUT_ARRAY";
            } else {
                for (IPatternDetails.IInput input : rawInputs) {
                    List<CompiledInput> compiledInputs = compileInputs(input);
                    inputs.addAll(compiledInputs);
                    for (CompiledInput compiledInput : compiledInputs) {
                        if (!compiledInput.unsupportedReason().isEmpty() && contractEvidence == null) {
                            contractEvidence = compiledInput.unsupportedReason();
                        }
                        if (!compiledInput.fastSupported() && unsupported == null) {
                            unsupported = compiledInput.unsupportedReason();
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            outputs = safeOutputs(details);
            unsupported = "MALFORMED_PATTERN:" + e.getClass().getSimpleName();
        }
        String recordedReason = unsupported != null ? unsupported
            : contractEvidence == null ? "" : contractEvidence;
        return new CompiledPattern(
            id, details, producedKey, outputPerPattern, inputs, outputs, unsupported == null,
            recordedReason, netGrowthValidated
        );
    }

    private static List<CompiledInput> compileInputs(IPatternDetails.IInput input) {
        if (input == null) {
            throw new IllegalArgumentException("null input");
        }
        GenericStack[] possible = input.getPossibleInputs();
        if (possible == null || possible.length == 0 || possible[0] == null || possible[0].what() == null) {
            throw new IllegalArgumentException("empty possible inputs");
        }
        GenericStack primary = possible[0];
        long multiplier = input.getMultiplier();
        if (primary.amount() <= 0 || multiplier <= 0) {
            return List.of(new CompiledInput(input, primary.what(), 0, false, "INVALID_INPUT_AMOUNT"));
        }
        // A substitution set is still safe to plan when the planner commits to one concrete member. Use the
        // pattern's primary input deterministically; AE2 may accept other members at execution time, but the
        // aggregate plan never relies on that substitution. Keeping every alternative here would turn one input
        // slot into simultaneous dependencies and excluding the pattern would hide valid acyclic producer routes.
        PlannerAmount amount = PlannerAmount.of(primary.amount()).multiply(multiplier);
        AEKey remainder = input.getRemainingKey(primary.what());
        if (remainder != null) {
            return List.of(new CompiledInput(input, primary.what(), amount, false, "UNSUPPORTED_REMAINDER",
                remainder, PlannerAmount.of(multiplier)));
        }
        return List.of(new CompiledInput(input, primary.what(), amount, true,
            possible.length == 1 ? "" : "UNSUPPORTED_SUBSTITUTION"));
    }

    private static List<GenericStack> safeOutputs(IPatternDetails details) {
        try {
            return details.getOutputs() == null ? List.of() : List.copyOf(details.getOutputs());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
