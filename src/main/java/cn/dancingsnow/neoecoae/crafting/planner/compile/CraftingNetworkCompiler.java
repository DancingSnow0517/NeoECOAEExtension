package cn.dancingsnow.neoecoae.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalyzer;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry;

/** Compiles only the closure reachable from one goal. Inventory and requested amount are deliberately absent. */
public final class CraftingNetworkCompiler {
    private final List<PatternSemanticAdapter> semanticAdapters;
    private final SpecialPatternAnalyzer specialPatternAnalyzer;

    public CraftingNetworkCompiler() {
        // Resolve a fresh registry snapshot per compilation, even when this compiler is reused.
        this.semanticAdapters = null;
        this.specialPatternAnalyzer = new SpecialPatternAnalyzer();
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
        return compile(service, goal, cyclePlanningEnabled, false, fuzzyPlanningItemIds, cancellation);
    }

    public CompiledNetwork compile(ICraftingService service, AEKey goal, boolean cyclePlanningEnabled,
            boolean liteCrafting, Set<ResourceLocation> fuzzyPlanningItemIds, ECOCancellation cancellation)
            throws InterruptedException {
        if (semanticAdapters == null && !liteCrafting) {
            return new CraftingNetworkCompiler(PatternSemanticAdapters.defaults(), specialPatternAnalyzer)
                .compile(service, goal, cyclePlanningEnabled, fuzzyPlanningItemIds, cancellation);
        }
        Set<ResourceLocation> ignoredItemIds = fuzzyPlanningItemIds == null ? Set.of()
            : fuzzyPlanningItemIds;
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        Set<AEKey> emittable = new HashSet<>();
        Set<AEKey> queued = new HashSet<>();
        ArrayDeque<AEKey> work = new ArrayDeque<>();
        work.add(goal);
        queued.add(goal);
        int nextPatternId = 0;
        int edgeCount = 0;

        while (true) {
            while (!work.isEmpty()) {
                cancellation.checkpoint();
                AEKey key = work.removeFirst();
                if (service.canEmitFor(key)) {
                    emittable.add(key);
                }
                List<CompiledPattern> compiled = new ArrayList<>();
                if (liteCrafting) {
                    // Keep one producer in AE2 priority order, widening only explicitly marked item IDs.
                    // No alternative-route search, semantic callbacks, or byproduct-only discovery.
                    boolean componentInsensitiveOutput = !key.equals(goal) && ignoresComponents(key, ignoredItemIds);
                    for (IPatternDetails details : craftingFor(service, key, componentInsensitiveOutput)) {
                        cancellation.checkpoint();
                        if (!yields(details, key, componentInsensitiveOutput)) continue;
                        CompiledPattern pattern = compileLitePattern(nextPatternId++, details, key,
                            componentInsensitiveOutput, ignoredItemIds);
                        compiled.add(pattern);
                        edgeCount += enqueue(pattern, queued, work);
                        break;
                    }
                    producers.put(key, compiled);
                    continue;
                }
                Set<PatternDefinition> definitions = new HashSet<>();
                boolean componentInsensitiveOutput = !key.equals(goal) && ignoresComponents(key, ignoredItemIds);
                List<IPatternDetails> offered = new ArrayList<>();
                for (IPatternDetails details : craftingFor(service, key, componentInsensitiveOutput)) {
                    cancellation.checkpoint();
                    PatternDefinition definition = patternDefinition(details);
                    if (definition == null || definitions.add(definition)) offered.add(details);
                }
                // AE2 registers a pattern under its primary output only; extended crafting services may also offer
                // patterns that yield this key as a byproduct. Such a pattern is not a route to the key while a
                // fast-plannable pattern makes it as its primary output: its byproduct is still credited whenever it
                // runs for its own product, but it no longer multiplies producer choices, structural cycles and
                // fallback route attempts. Keys obtainable only as byproducts, or whose primary producers cannot be
                // fast-planned, keep those producers. AE's producer order is kept either way.
                CompiledPattern[] byOffer = new CompiledPattern[offered.size()];
                boolean primaryPlannable = false;
                for (int i = 0; i < offered.size(); i++) {
                    if (!producesAsPrimary(offered.get(i), key, componentInsensitiveOutput)) continue;
                    cancellation.checkpoint();
                    byOffer[i] = compilePattern(nextPatternId++, offered.get(i), key, cyclePlanningEnabled,
                        ignoredItemIds, componentInsensitiveOutput, service);
                    primaryPlannable |= byOffer[i].fastSupported();
                }
                for (int i = 0; i < offered.size() && !primaryPlannable; i++) {
                    if (byOffer[i] != null) continue;
                    cancellation.checkpoint();
                    byOffer[i] = compilePattern(nextPatternId++, offered.get(i), key, cyclePlanningEnabled,
                        ignoredItemIds, componentInsensitiveOutput, service);
                }
                for (CompiledPattern pattern : byOffer) {
                    if (pattern == null) continue;
                    compiled.add(pattern);
                    edgeCount += enqueue(pattern, queued, work);
                }
                producers.put(key, compiled);
            }
            // Vanilla AE2 does not list a key that every pattern makes only as a byproduct (electrolysis oxygen,
            // for example), so the loop above found no plannable producer for it. Running such a pattern for that
            // key is still valid: give the key byproduct views of the compiled patterns that yield it. Their inputs
            // are already queued, so this normally settles in one pass; new keys go back through the loop above.
            int before = nextPatternId;
            List<IPatternDetails> known = new ArrayList<>();
            Set<IPatternDetails> knownIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<PatternDefinition> knownDefinitions = new HashSet<>();
            for (List<CompiledPattern> views : producers.values()) {
                for (CompiledPattern view : views) {
                    if (!knownIdentity.add(view.details())) continue;
                    PatternDefinition definition = patternDefinition(view.details());
                    if (definition == null || knownDefinitions.add(definition)) known.add(view.details());
                }
            }
            Map<AEKey, IPatternDetails> liteSources = new LinkedHashMap<>();
            if (liteCrafting) {
                for (List<CompiledPattern> views : producers.values()) for (CompiledPattern view : views) {
                    cancellation.checkpoint();
                    for (GenericStack output : view.outputs()) liteSources.putIfAbsent(output.what(), view.details());
                }
            }
            for (var entry : producers.entrySet()) {
                AEKey key = entry.getKey();
                if (emittable.contains(key)
                        || liteCrafting && !entry.getValue().isEmpty()
                        || entry.getValue().stream().anyMatch(CompiledPattern::fastSupported)) continue;
                boolean componentInsensitiveOutput = !key.equals(goal) && ignoresComponents(key, ignoredItemIds);
                Set<IPatternDetails> present = Collections.newSetFromMap(new IdentityHashMap<>());
                for (CompiledPattern view : entry.getValue()) present.add(view.details());
                List<CompiledPattern> extended = null;
                List<IPatternDetails> sources = liteCrafting && !componentInsensitiveOutput
                    ? liteSources.containsKey(key) ? List.of(liteSources.get(key)) : List.of()
                    : known;
                for (IPatternDetails details : sources) {
                    if (present.contains(details) || !yields(details, key, componentInsensitiveOutput)) continue;
                    cancellation.checkpoint();
                    CompiledPattern view = liteCrafting
                        ? compileLitePattern(nextPatternId++, details, key, componentInsensitiveOutput, ignoredItemIds)
                        : compilePattern(nextPatternId++, details, key, cyclePlanningEnabled,
                            ignoredItemIds, componentInsensitiveOutput, service);
                    if (extended == null) extended = new ArrayList<>(entry.getValue());
                    extended.add(view);
                    edgeCount += enqueue(view, queued, work);
                    if (liteCrafting) break;
                }
                if (extended != null) entry.setValue(extended);
            }
            // Lite views share the already-expanded inputs of their one fixed physical pattern.
            // Discover secondary-only producers once; never add alternatives or reopen the closure.
            if (liteCrafting) break;
            if (work.isEmpty() || nextPatternId == before) break;
        }
        return new CompiledNetwork(goal, producers, emittable, nextPatternId, edgeCount);
    }

    /** Queues every key the pattern can depend on and returns the number of dependency edges it adds. */
    private static int enqueue(CompiledPattern pattern, Set<AEKey> queued, ArrayDeque<AEKey> work) {
        int edges = 0;
        for (CompiledInput input : pattern.inputs()) {
            edges++;
            if (queued.add(input.key())) {
                work.addLast(input.key());
            }
            // The solver may select a reusable substitute from stock, or fall back from it to a
            // durability-mutating member. Compile those producer chains now; inventory is intentionally
            // unavailable at this stage.
            for (AEKey alternative : remainderAlternatives(input)) {
                if (queued.add(alternative)) {
                    edges++;
                    work.addLast(alternative);
                }
            }
        }
        for (AEKey returned : pattern.semantics().returnedKeys()) {
            edges++;
            if (queued.add(returned)) work.addLast(returned);
        }
        for (var feedback : pattern.semantics().feedbackEdges()) {
            edges++;
            if (queued.add(feedback.returnedKey())) work.addLast(feedback.returnedKey());
            if (queued.add(feedback.dependentOutput())) work.addLast(feedback.dependentOutput());
        }
        return edges;
    }

    /** One of the pattern's outputs, primary or byproduct, is {@code key}. */
    private static boolean yields(IPatternDetails details, AEKey key, boolean componentInsensitiveOutput) {
        for (GenericStack output : safeOutputs(details)) {
            if (output == null || output.what() == null) continue;
            if (output.what().equals(key) || componentInsensitiveOutput && sameItem(key, output.what())) return true;
        }
        return false;
    }

    /**
     * Keep AE2's exact producer order first, then add deterministic same-item producers for an explicitly
     * component-insensitive dependency. The selected physical pattern remains unchanged; only its planner-facing
     * matching outputs are aliased to the dependency key below.
     */
    private static Iterable<IPatternDetails> craftingFor(ICraftingService service, AEKey key,
            boolean componentInsensitiveOutput) {
        LinkedHashSet<IPatternDetails> result = new LinkedHashSet<>(service.getCraftingFor(key));
        if (!componentInsensitiveOutput || !(key instanceof AEItemKey wanted)) {
            return result;
        }

        service.getCraftables(candidate -> candidate instanceof AEItemKey itemKey
                && itemKey.getItem() == wanted.getItem()).stream()
            .sorted(Comparator.comparing(AEKey::toString))
            .forEach(candidate -> result.addAll(service.getCraftingFor(candidate)));
        return result;
    }

    /** Encoded definitions include quantities, components and matching settings; IO alone is not an identity. */
    private static PatternDefinition patternDefinition(IPatternDetails details) {
        try {
            AEItemKey definition = details.getDefinition();
            // Keep the first physical instance in AE2's priority order. Distinct wrappers may have different
            // execution contracts even when they expose the same encoded item.
            return definition == null ? null : new PatternDefinition(details.getClass(), definition);
        } catch (RuntimeException ignored) {
            // A missing/malformed definition is not evidence that two patterns are equivalent.
            return null;
        }
    }

    private record PatternDefinition(Class<?> implementation, AEItemKey definition) {}

    /** Lite contract: encoded primary inputs and all physical outputs, without remainder analysis. */
    private static CompiledPattern compileLitePattern(int id, IPatternDetails details, AEKey producedKey,
            boolean componentInsensitiveOutput, Set<ResourceLocation> fuzzyPlanningItemIds) {
        List<CompiledInput> inputs = new ArrayList<>();
        List<PatternSemantics.Input> consumed = new ArrayList<>();
        List<GenericStack> outputs = new ArrayList<>();
        PlannerAmount outputAmount = PlannerAmount.ZERO;
        String unsupported = null;
        try {
            for (GenericStack output : safeOutputs(details)) {
                if (output == null || output.what() == null || output.amount() <= 0L) {
                    throw new IllegalArgumentException("Invalid pattern output");
                }
                if (producedKey.equals(output.what())
                        || componentInsensitiveOutput && sameItem(producedKey, output.what())) {
                    outputs.add(new GenericStack(producedKey, output.amount()));
                    outputAmount = outputAmount.add(PlannerAmount.of(output.amount()));
                } else {
                    outputs.add(output);
                }
            }
            if (outputAmount.isZero()) throw new IllegalArgumentException("Output does not match request");
            for (IPatternDetails.IInput input : details.getInputs()) {
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null || possible.length == 0 || possible[0] == null
                        || possible[0].what() == null || possible[0].amount() <= 0L || input.getMultiplier() <= 0L) {
                    throw new IllegalArgumentException("Invalid primary pattern input");
                }
                GenericStack primary = possible[0];
                PlannerAmount amount = PlannerAmount.of(primary.amount()).multiply(input.getMultiplier());
                inputs.add(new CompiledInput(input, primary.what(), amount, true, "", null,
                    PlannerAmount.ZERO, ECOProcessingComponentMatching.enabled(details, primary.what(), fuzzyPlanningItemIds)));
                consumed.add(new PatternSemantics.Input(input, primary.what(), amount, null, PlannerAmount.ZERO));
            }
        } catch (RuntimeException failure) {
            unsupported = "MALFORMED_PATTERN:" + failure.getClass().getSimpleName();
        }
        PatternSemantics semantics = new PatternSemantics(details, null, consumed, outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE,
            unsupported == null, true, unsupported);
        return new CompiledPattern(id, details, producedKey, outputAmount, inputs, outputs, unsupported == null,
            unsupported == null ? "" : unsupported, false, semantics, SpecialPatternAnalysis.NONE);
    }

    private CompiledPattern compilePattern(int id, IPatternDetails details, AEKey producedKey,
            boolean cyclePlanningEnabled, Set<ResourceLocation> fuzzyPlanningItemIds,
            boolean componentInsensitiveOutput, ICraftingService service) {
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
            // Compile the view offered by the service, including secondary outputs from extended services.
            // Matching stacks contribute together to both the firing ratio and the solver's output credit.
            List<GenericStack> plannerOutputs = new ArrayList<>(outputs.size());
            for (GenericStack output : outputs) {
                if (output == null || output.what() == null || output.amount() <= 0) {
                    unsupported = "INVALID_OUTPUT";
                    plannerOutputs.add(output);
                    continue;
                }
                if (producedKey.equals(output.what())
                        || componentInsensitiveOutput && sameItem(producedKey, output.what())) {
                    outputPerPattern = outputPerPattern.add(PlannerAmount.of(output.amount()));
                    if (!producedKey.equals(output.what())) {
                        output = new GenericStack(producedKey, output.amount());
                    }
                }
                plannerOutputs.add(output);
            }
            outputs = plannerOutputs;
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
                inputs = compileInputs(semantics, fastClassification, adapter, fuzzyPlanningItemIds, service);
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

    private static boolean sameItem(AEKey left, AEKey right) {
        return left instanceof AEItemKey leftItem && right instanceof AEItemKey rightItem
            && leftItem.getItem() == rightItem.getItem();
    }

    /** Unknown or malformed primary metadata keeps the pattern: only a proven byproduct view is dropped. */
    private static boolean producesAsPrimary(IPatternDetails details, AEKey key, boolean componentInsensitiveOutput) {
        GenericStack primary;
        try {
            primary = details.getPrimaryOutput();
        } catch (RuntimeException e) {
            return true;
        }
        if (primary == null || primary.what() == null) return true;
        return primary.what().equals(key) || componentInsensitiveOutput && sameItem(key, primary.what());
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
            return alternatives;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<CompiledInput> compileInputs(PatternSemantics semantics,
            ECORecipeClassifier.Classification classification, PatternSemanticAdapter adapter,
            Set<ResourceLocation> fuzzyPlanningItemIds, ICraftingService service) {
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
            int inputSlot = indexOfInput(semantics, input);
            boolean ignoresComponents = ignoresComponents(input.key(), fuzzyPlanningItemIds) || input.source() != null
                && adapter != null
                && adapter.ignoresComponents(semantics.physicalPattern(), inputSlot);
            AEKey preferredKey = input.source() != null && adapter != null
                ? adapter.preferredInputKey(semantics.physicalPattern(), inputSlot, input.key(), service) : input.key();
            inputs.add(new CompiledInput(input.source(), preferredKey, input.amountPerPattern(), fastSupported, reason,
                input.returnedKey(), input.returnedAmountPerPattern(), ignoresComponents));
        }
        return inputs;
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
            return details.getOutputs() == null ? List.of() : details.getOutputs();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

}
