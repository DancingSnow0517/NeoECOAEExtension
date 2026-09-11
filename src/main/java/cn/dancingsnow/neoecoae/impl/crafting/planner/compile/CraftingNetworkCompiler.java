package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.NetGrowthPatternValidationRegistry;

/** Compiles only the closure reachable from one goal. Inventory and requested amount are deliberately absent. */
public final class CraftingNetworkCompiler {
    private final List<PatternSemanticAdapter> semanticAdapters;
    private final SpecialPatternAnalyzer specialPatternAnalyzer;

    public CraftingNetworkCompiler() {
        this(PatternSemanticAdapters.defaults(), new SpecialPatternAnalyzer());
    }

    /** Constructor kept injectable so planner tests and integrations can supply an explicit semantic contract. */
    public CraftingNetworkCompiler(List<PatternSemanticAdapter> semanticAdapters) {
        this(semanticAdapters, new SpecialPatternAnalyzer());
    }

    public CraftingNetworkCompiler(List<PatternSemanticAdapter> semanticAdapters,
            SpecialPatternAnalyzer specialPatternAnalyzer) {
        this.semanticAdapters = PatternSemanticAdapters.copy(semanticAdapters);
        this.specialPatternAnalyzer = java.util.Objects.requireNonNull(specialPatternAnalyzer);
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
        return compile(service, goal, cyclePlanningEnabled, Set.of(), cancellation);
    }

    public CompiledNetwork compile(ICraftingService service, AEKey goal, boolean cyclePlanningEnabled,
            Set<ResourceLocation> fuzzyPlanningItemIds, ECOCancellation cancellation) throws InterruptedException {
        Set<ResourceLocation> ignoredItemIds = fuzzyPlanningItemIds == null ? Set.of()
            : Set.copyOf(fuzzyPlanningItemIds);
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
            boolean componentInsensitiveOutput = !key.equals(goal) && ignoresComponents(key, ignoredItemIds);
            for (IPatternDetails details : craftingFor(service, key, componentInsensitiveOutput)) {
                cancellation.checkpoint();
                CompiledPattern pattern = compilePattern(nextPatternId++, details, key, cyclePlanningEnabled,
                    ignoredItemIds, componentInsensitiveOutput);
                compiled.add(pattern);
                for (CompiledInput input : pattern.inputs()) {
                    edgeCount++;
                    if (queued.add(input.key())) {
                        work.addLast(input.key());
                    }
                    // The solver may select a reusable substitute from stock, or fall back from it to a
                    // durability-mutating member. Compile those producer chains now; inventory is intentionally
                    // unavailable at this stage.
                    for (AEKey alternative : remainderAlternatives(input)) {
                        if (queued.add(alternative)) {
                            edgeCount++;
                            work.addLast(alternative);
                        }
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

    /**
     * Keep AE2's exact producer order first, then add deterministic same-item producers for an explicitly
     * component-insensitive dependency. The selected physical pattern remains unchanged; only its planner-facing
     * primary output is aliased to the dependency key below.
     */
    private static List<IPatternDetails> craftingFor(ICraftingService service, AEKey key,
            boolean componentInsensitiveOutput) {
        LinkedHashSet<IPatternDetails> result = new LinkedHashSet<>(service.getCraftingFor(key));
        if (!componentInsensitiveOutput || !(key instanceof AEItemKey wanted)) {
            return List.copyOf(result);
        }

        service.getCraftables(candidate -> candidate instanceof AEItemKey itemKey
                && itemKey.getItem() == wanted.getItem()).stream()
            .sorted(Comparator.comparing(AEKey::toString))
            .forEach(candidate -> result.addAll(service.getCraftingFor(candidate)));
        return List.copyOf(result);
    }

    private CompiledPattern compilePattern(int id, IPatternDetails details, AEKey producedKey,
            boolean cyclePlanningEnabled, Set<ResourceLocation> fuzzyPlanningItemIds,
            boolean componentInsensitiveOutput) {
        List<CompiledInput> inputs;
        List<GenericStack> outputs;
        PlannerAmount outputPerPattern = PlannerAmount.ZERO;
        String unsupported = null;
        String contractEvidence = null;
        PatternSemanticAdapter adapter = PatternSemanticAdapters.find(semanticAdapters, details);
        PatternSemantics semantics;
        ECORecipeClassifier.Classification fastClassification;
        SpecialPatternAnalysis specialAnalysis = SpecialPatternAnalysis.NONE;
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
            GenericStack primaryOutput = details.getPrimaryOutput();
            for (GenericStack output : outputs) {
                if (output == null || output.what() == null || output.amount() <= 0) {
                    unsupported = "INVALID_OUTPUT";
                }
            }
            // AE2 indexes a pattern by getPrimaryOutput(); all remaining entries in getOutputs() are
            // byproducts and must not change the firing ratio or make a byproduct look craftable on its own.
            if (primaryOutput != null && primaryOutput.what() != null && primaryOutput.amount() > 0L
                    && (producedKey.equals(primaryOutput.what())
                        || componentInsensitiveOutput && sameItem(producedKey, primaryOutput.what()))) {
                outputPerPattern = PlannerAmount.of(primaryOutput.amount());
                if (!producedKey.equals(primaryOutput.what())) {
                    outputs = aliasPrimaryOutput(outputs, primaryOutput.what(), producedKey);
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
                inputs = compileInputs(semantics, fastClassification, adapter, fuzzyPlanningItemIds);
            } else {
                inputs = compileRawInputs(details, fuzzyPlanningItemIds);
            }
            specialAnalysis = specialPatternAnalyzer.analyze(id, details, semantics, inputs);
            for (CompiledInput compiledInput : inputs) {
                if (!compiledInput.unsupportedReason().isEmpty() && contractEvidence == null) {
                    contractEvidence = compiledInput.unsupportedReason();
                }
                if (!compiledInput.fastSupported() && unsupported == null
                        && !specialAnalysis.excludesFromCycleGraph(compiledInput)) {
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
            recordedReason, netGrowthValidated, semantics, specialAnalysis
        );
    }

    private static List<GenericStack> aliasPrimaryOutput(List<GenericStack> outputs,
            AEKey physicalPrimaryOutput, AEKey plannerKey) {
        List<GenericStack> aliased = new ArrayList<>(outputs.size());
        for (GenericStack output : outputs) {
            aliased.add(output != null && physicalPrimaryOutput.equals(output.what())
                ? new GenericStack(plannerKey, output.amount()) : output);
        }
        return List.copyOf(aliased);
    }

    private static boolean sameItem(AEKey left, AEKey right) {
        return left instanceof AEItemKey leftItem && right instanceof AEItemKey rightItem
            && leftItem.getItem() == rightItem.getItem();
    }

    private static List<CompiledInput> compileRawInputs(IPatternDetails details,
            Set<ResourceLocation> fuzzyPlanningItemIds) {
        List<CompiledInput> inputs = new ArrayList<>();
        IPatternDetails.IInput[] rawInputs = details.getInputs();
        if (rawInputs == null) throw new IllegalArgumentException("null input array");
        for (IPatternDetails.IInput input : rawInputs) {
            inputs.addAll(compileInputs(input, fuzzyPlanningItemIds));
        }
        return inputs;
    }

    private static List<AEKey> remainderAlternatives(CompiledInput input) {
        if (input.remainderKey() == null || input.source() == null) return List.of();
        try {
            List<AEKey> alternatives = new ArrayList<>();
            for (GenericStack possible : input.source().getPossibleInputs()) {
                if (possible == null || possible.what() == null || possible.what().equals(input.key())) continue;
                if (input.source().getRemainingKey(possible.what()) != null) alternatives.add(possible.what());
            }
            return List.copyOf(alternatives);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<CompiledInput> compileInputs(PatternSemantics semantics,
            ECORecipeClassifier.Classification classification, PatternSemanticAdapter adapter,
            Set<ResourceLocation> fuzzyPlanningItemIds) {
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
            boolean ignoresComponents = ignoresComponents(input.key(), fuzzyPlanningItemIds) || input.source() != null
                && adapter != null
                && adapter.ignoresComponents(semantics.physicalPattern(), indexOfInput(semantics, input));
            inputs.add(new CompiledInput(input.source(), input.key(), input.amountPerPattern(), fastSupported, reason,
                input.returnedKey(), input.returnedAmountPerPattern(), ignoresComponents));
        }
        return List.copyOf(inputs);
    }

    private static int indexOfInput(PatternSemantics semantics, PatternSemantics.Input target) {
        return semantics.consumedInputs().indexOf(target);
    }

    private static List<CompiledInput> compileInputs(IPatternDetails.IInput input,
            Set<ResourceLocation> fuzzyPlanningItemIds) {
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
            return List.of(new CompiledInput(input, primary.what(), PlannerAmount.ZERO, false, "INVALID_INPUT_AMOUNT",
                null, PlannerAmount.ZERO, ignoresComponents(primary.what(), fuzzyPlanningItemIds)));
        }
        // A substitution set is still safe to plan when the planner commits to one concrete member. Use the
        // pattern's primary input deterministically; AE2 may accept other members at execution time, but the
        // aggregate plan never relies on that substitution.
        PlannerAmount amount = PlannerAmount.of(primary.amount()).multiply(multiplier);
        AEKey remainder = input.getRemainingKey(primary.what());
        if (remainder != null) {
            return List.of(new CompiledInput(input, primary.what(), amount, false, "UNSUPPORTED_REMAINDER",
                remainder, PlannerAmount.of(multiplier), ignoresComponents(primary.what(), fuzzyPlanningItemIds)));
        }
        return List.of(new CompiledInput(input, primary.what(), amount, true,
            possible.length == 1 ? "" : "UNSUPPORTED_SUBSTITUTION", null, PlannerAmount.ZERO,
            ignoresComponents(primary.what(), fuzzyPlanningItemIds)));
    }

    private static boolean ignoresComponents(AEKey key, Set<ResourceLocation> fuzzyPlanningItemIds) {
        return key instanceof AEItemKey itemKey
            && fuzzyPlanningItemIds.contains(BuiltInRegistries.ITEM.getKey(itemKey.getItem()));
    }

    private static List<GenericStack> safeOutputs(IPatternDetails details) {
        try {
            return details.getOutputs() == null ? List.of() : List.copyOf(details.getOutputs());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

}
