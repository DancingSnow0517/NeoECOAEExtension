package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier;
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
    private final List<PatternSemanticAdapter> semanticAdapters;

    public CraftingNetworkCompiler() {
        this(PatternSemanticAdapters.defaults());
    }

    /** Constructor kept injectable so planner tests and integrations can supply an explicit semantic contract. */
    public CraftingNetworkCompiler(List<PatternSemanticAdapter> semanticAdapters) {
        this.semanticAdapters = PatternSemanticAdapters.copy(semanticAdapters);
    }

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
                for (AEKey returned : pattern.semantics().returnedKeys()) {
                    edgeCount++;
                    if (queued.add(returned)) work.addLast(returned);
                }
                for (var feedback : pattern.semantics().feedbackEdges()) {
                    edgeCount++;
                    if (queued.add(feedback.returnedKey())) work.addLast(feedback.returnedKey());
                    if (queued.add(feedback.dependentOutput())) work.addLast(feedback.dependentOutput());
                }
            }
            producers.put(key, List.copyOf(compiled));
        }
        return new CompiledNetwork(goal, producers, emittable, nextPatternId, edgeCount);
    }

    private CompiledPattern compilePattern(int id, IPatternDetails details, AEKey producedKey,
            boolean cyclePlanningEnabled) {
        List<CompiledInput> inputs;
        List<GenericStack> outputs;
        PlannerAmount outputPerPattern = PlannerAmount.ZERO;
        String unsupported = null;
        String contractEvidence = null;
        PatternSemanticAdapter adapter = PatternSemanticAdapters.find(semanticAdapters, details);
        PatternSemantics semantics;
        ECORecipeClassifier.Classification fastClassification;
        try {
            PatternSemantics analyzed = adapter == null
                ? PatternSemantics.unsupported(details, null, "NO_PATTERN_SEMANTIC_ADAPTER")
                : adapter.analyze(details);
            semantics = analyzed == null
                ? PatternSemantics.unsupported(details, null, "NULL_PATTERN_SEMANTICS") : analyzed;
        } catch (RuntimeException e) {
            semantics = PatternSemantics.unsupported(details, null,
                "SEMANTIC_ANALYSIS_FAILED:" + e.getClass().getSimpleName());
        }
        fastClassification = ECORecipeClassifier.classify(details);
        try {
            outputs = semantics.producedOutputs().isEmpty() ? safeOutputs(details) : semantics.producedOutputs();
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

            if (!semantics.exactStaticAnalysis() && unsupported == null) {
                unsupported = semantics.unsupportedReason() == null
                    ? "UNSUPPORTED_PATTERN_SEMANTICS" : semantics.unsupportedReason();
            } else if ((semantics.matchingMode() == PatternSemantics.MatchingMode.FUZZY
                    || semantics.matchingMode() == PatternSemantics.MatchingMode.UNKNOWN) && unsupported == null) {
                unsupported = "UNSUPPORTED_MATCHING_SEMANTICS";
            } else if (semantics.executionRestriction() != PatternSemantics.ExecutionRestriction.NONE
                    && unsupported == null) {
                unsupported = "UNSUPPORTED_EXECUTION_RESTRICTION";
            }

            if (!semantics.consumedInputs().isEmpty()) {
                inputs = compileInputs(semantics, fastClassification);
            } else {
                inputs = compileRawInputs(details);
            }
            for (CompiledInput compiledInput : inputs) {
                if (!compiledInput.unsupportedReason().isEmpty() && contractEvidence == null) {
                    contractEvidence = compiledInput.unsupportedReason();
                }
                if (!compiledInput.fastSupported() && unsupported == null) {
                    unsupported = compiledInput.unsupportedReason();
                }
            }
            if (!semantics.supported() && unsupported == null) {
                unsupported = semantics.unsupportedReason() == null
                    ? "UNSUPPORTED_PATTERN_SEMANTICS" : semantics.unsupportedReason();
            } else if (adapter == null && unsupported == null) {
                unsupported = "NO_PATTERN_SEMANTIC_ADAPTER";
            }
        } catch (RuntimeException e) {
            outputs = safeOutputs(details);
            inputs = List.of();
            unsupported = "MALFORMED_PATTERN:" + e.getClass().getSimpleName();
        }

        boolean netGrowthValidated = cyclePlanningEnabled
            && (NetGrowthPatternValidationRegistry.isValidated(details)
                || NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(details)
                || semantics.cycleSafeForStaticPlanning());
        String recordedReason = unsupported != null ? unsupported
            : contractEvidence == null ? "" : contractEvidence;
        return new CompiledPattern(
            id, details, producedKey, outputPerPattern, inputs, outputs, unsupported == null,
            recordedReason, netGrowthValidated, semantics
        );
    }

    private static List<CompiledInput> compileRawInputs(IPatternDetails details) {
        List<CompiledInput> inputs = new ArrayList<>();
        IPatternDetails.IInput[] rawInputs = details.getInputs();
        if (rawInputs == null) throw new IllegalArgumentException("null input array");
        for (IPatternDetails.IInput input : rawInputs) inputs.addAll(compileInputs(input));
        return inputs;
    }

    private static List<CompiledInput> compileInputs(PatternSemantics semantics,
            ECORecipeClassifier.Classification classification) {
        List<CompiledInput> inputs = new ArrayList<>();
        for (PatternSemantics.Input input : semantics.consumedInputs()) {
            String reason = "";
            boolean fastSupported = semantics.supported();
            if (semantics.matchingMode() == PatternSemantics.MatchingMode.SUBSTITUTION) {
                reason = "UNSUPPORTED_SUBSTITUTION";
            } else if (semantics.matchingMode() == PatternSemantics.MatchingMode.FUZZY
                    || semantics.matchingMode() == PatternSemantics.MatchingMode.UNKNOWN) {
                fastSupported = false;
                reason = "UNSUPPORTED_MATCHING_SEMANTICS";
            }
            if (semantics.executionRestriction() != PatternSemantics.ExecutionRestriction.NONE) {
                fastSupported = false;
                reason = "UNSUPPORTED_EXECUTION_RESTRICTION";
            }
            // A reusable component or durability-mutating tool is proven by the FastPath classifier and
            // represented by the runtime batch model. It must not be rejected as a generic remainder.
            boolean mutationRemainder = classification.supported()
                && classification.type() != ECORecipeClassifier.Type.NORMAL;
            if (input.returnedKey() != null && !semantics.cycleSafeForStaticPlanning() && !mutationRemainder) {
                fastSupported = false;
                reason = "UNSUPPORTED_REMAINDER";
            }
            if (input.amountPerPattern().signum() <= 0) {
                fastSupported = false;
                reason = "INVALID_INPUT_AMOUNT";
            }
            inputs.add(new CompiledInput(input.source(), input.key(), input.amountPerPattern(), fastSupported, reason,
                input.returnedKey(), input.returnedAmountPerPattern()));
        }
        return List.copyOf(inputs);
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
        // aggregate plan never relies on that substitution.
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
