package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
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
                CompiledPattern pattern = compilePattern(nextPatternId++, details, key);
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

    private static CompiledPattern compilePattern(int id, IPatternDetails details, AEKey producedKey) {
        List<CompiledInput> inputs = new ArrayList<>();
        List<GenericStack> outputs;
        long outputPerPattern = 0;
        String unsupported = null;
        boolean netGrowthValidated = NetGrowthPatternValidationRegistry.isValidated(details);
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
                    outputPerPattern = Math.addExact(outputPerPattern, output.amount());
                }
            }
            if (outputPerPattern <= 0) {
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
                        if (!compiledInput.fastSupported() && unsupported == null) {
                            unsupported = compiledInput.unsupportedReason();
                        }
                    }
                }
            }
        } catch (ArithmeticException e) {
            outputs = safeOutputs(details);
            unsupported = "AMOUNT_OVERFLOW";
        } catch (RuntimeException e) {
            outputs = safeOutputs(details);
            unsupported = "MALFORMED_PATTERN:" + e.getClass().getSimpleName();
        }
        return new CompiledPattern(
            id, details, producedKey, outputPerPattern, inputs, outputs, unsupported == null,
            unsupported == null ? "" : unsupported, netGrowthValidated
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
        if (possible.length != 1) {
            List<CompiledInput> alternatives = new ArrayList<>(possible.length);
            for (GenericStack alternative : possible) {
                if (alternative == null || alternative.what() == null || alternative.amount() <= 0) {
                    throw new IllegalArgumentException("invalid possible input");
                }
                alternatives.add(new CompiledInput(input, alternative.what(),
                    Math.multiplyExact(alternative.amount(), multiplier), false, "UNSUPPORTED_SUBSTITUTION"));
            }
            return List.copyOf(alternatives);
        }
        long amount = Math.multiplyExact(primary.amount(), multiplier);
        AEKey remainder = input.getRemainingKey(primary.what());
        if (remainder != null) {
            return List.of(new CompiledInput(input, primary.what(), amount, false, "UNSUPPORTED_REMAINDER",
                remainder, multiplier));
        }
        return List.of(new CompiledInput(input, primary.what(), amount, true, ""));
    }

    private static List<GenericStack> safeOutputs(IPatternDetails details) {
        try {
            return details.getOutputs() == null ? List.of() : List.copyOf(details.getOutputs());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
