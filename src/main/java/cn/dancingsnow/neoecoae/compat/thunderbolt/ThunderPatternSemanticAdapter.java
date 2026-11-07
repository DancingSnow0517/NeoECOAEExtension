package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.lang.reflect.Method;

/** Normalizes AE2 Lightning Technology overload patterns for ECO's static planner. */
public final class ThunderPatternSemanticAdapter implements ECOPatternSemanticAdapter {
    private static final Method HAS_FUZZY_INPUTS = ThunderboltApi.method(ThunderboltApi.OVERLOAD, "hasFuzzyInputs");
    private static final Method IS_FUZZY_INPUT = ThunderboltApi.method(ThunderboltApi.OVERLOAD, "isFuzzyInput", int.class);
    private static final Method IS_FUZZY_OUTPUT = ThunderboltApi.method(ThunderboltApi.OVERLOAD, "isFuzzyOutput", int.class);
    private static final Method WRAPPED_PATTERN = ThunderboltApi.method(ThunderboltApi.WRAPPER, "wrappedPatternDetails");
    private final AE2PatternSemanticAdapter ae2Adapter = new AE2PatternSemanticAdapter();

    @Override
    public boolean supports(IPatternDetails pattern) {
        if (pattern == null) return false;
        return ThunderboltApi.isInstance(ThunderboltApi.OVERLOAD, pattern);
    }

    public PatternSemantics analyze(IPatternDetails pattern) {
        return PatternSemanticAdapters.toInternal(analyze(pattern,
            PatternSemanticAdapters.toPublic(ae2Adapter.analyze(pattern))));
    }

    @Override
    public ECOPatternSemantics analyze(IPatternDetails pattern, ECOPatternSemantics base) {
        AEItemKey definition = null;
        try {
            definition = pattern.getDefinition();
            if (!supports(pattern)) {
                return ECOPatternSemantics.unsupported(pattern, definition, "THUNDER_UNSUPPORTED_SEMANTICS");
            }

            IPatternDetails sourcePattern = unwrap(pattern);
            ECOPatternSemantics ae2 = sourcePattern == pattern ? base
                : PatternSemanticAdapters.toPublic(ae2Adapter.analyze(sourcePattern));
            if (!ae2.supported()) {
                return ECOPatternSemantics.unsupported(pattern, definition,
                    ae2.unsupportedReason() == null ? "THUNDER_INVALID_SOURCE_PATTERN" : ae2.unsupportedReason());
            }

            boolean hasIdOnlyInput = (boolean) ThunderboltApi.invoke(HAS_FUZZY_INPUTS, pattern);
            boolean hasIdOnlyOutput = false;
            for (int slot = 0; slot < pattern.getOutputs().size(); slot++) {
                if ((boolean) ThunderboltApi.invoke(IS_FUZZY_OUTPUT, pattern, slot)) {
                    hasIdOnlyOutput = true;
                    break;
                }
            }
            boolean usesIdOnlyMatching = hasIdOnlyInput || hasIdOnlyOutput;

            // Quantities/remainders come from the source, but membership belongs to the
            // physical overload input. The unwrapped processing input is still STRICT.
            var physicalInputs = pattern.getInputs();
            if (physicalInputs.length != ae2.consumedInputs().size()) {
                return ECOPatternSemantics.unsupported(pattern, definition, "THUNDER_INPUT_SLOT_MISMATCH");
            }
            var inputs = new java.util.ArrayList<ECOPatternSemantics.Input>(physicalInputs.length);
            for (int slot = 0; slot < physicalInputs.length; slot++) {
                var input = ae2.consumedInputs().get(slot);
                inputs.add(new ECOPatternSemantics.Input(physicalInputs[slot], input.key(),
                    input.amountPerPattern(), input.returnedKey(), input.returnedAmountPerPattern()));
            }

            // Static planning commits ID_ONLY slots to the concrete templates advertised by IPatternDetails.
            // AE2LT remains free to substitute component variants at execution time. Such substitutions are
            // deliberately excluded from cycle-safety proofs because the concrete returned/output key may differ.
            ECOPatternSemantics.MatchingMode matching = usesIdOnlyMatching
                ? ECOPatternSemantics.MatchingMode.SUBSTITUTION : ae2.matchingMode();
            return new ECOPatternSemantics(pattern, definition, inputs, ae2.producedOutputs(),
                ae2.returnedOutputs(), ae2.feedbackEdges(), matching,
                ECOPatternSemantics.ExecutionRestriction.NONE, true,
                ae2.cycleSafe() && !usesIdOnlyMatching, null);
        } catch (RuntimeException rejected) {
            return ECOPatternSemantics.unsupported(pattern, definition,
                "MALFORMED_THUNDER_PATTERN:" + rejected.getClass().getSimpleName());
        }
    }

    @Override
    public boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
        return supports(pattern) && inputSlot >= 0
            && (boolean) ThunderboltApi.invoke(IS_FUZZY_INPUT, pattern, inputSlot);
    }

    @Override
    public AEKey preferredInputKey(IPatternDetails pattern, int inputSlot, AEKey encodedKey,
            ICraftingService service) {
        if (!ignoresComponents(pattern, inputSlot) || !(encodedKey instanceof AEItemKey wanted)) return encodedKey;
        var input = pattern.getInputs()[inputSlot];
        if (input.getRemainingKey(encodedKey) != null || !service.getCraftingFor(encodedKey).isEmpty()) return encodedKey;
        AEKey preferred = service.getFuzzyCraftable(encodedKey, candidate -> candidate instanceof AEItemKey item
            && item.getItem() == wanted.getItem() && input.isValid(candidate, null));
        return preferred == null ? encodedKey : preferred;
    }

    @Override
    public String name() {
        return "Thunder";
    }

    private static IPatternDetails unwrap(IPatternDetails pattern) {
        ReferenceSet<IPatternDetails> visited = null;
        IPatternDetails current = pattern;
        while (ThunderboltApi.isInstance(ThunderboltApi.WRAPPER, current)) {
            if (visited == null) visited = new ReferenceOpenHashSet<>();
            if (!visited.add(current)) throw new IllegalArgumentException("Cyclic Thunder pattern wrapper");
            IPatternDetails next = (IPatternDetails) ThunderboltApi.invoke(WRAPPED_PATTERN, current);
            if (next == null) throw new IllegalArgumentException("Null Thunder wrapped pattern");
            current = next;
        }
        return current;
    }

}
