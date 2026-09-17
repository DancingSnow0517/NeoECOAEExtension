package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.lang.reflect.Method;

/** Normalizes AE2 Lightning Technology overload patterns for ECO's static planner. */
public final class ThunderPatternSemanticAdapter implements PatternSemanticAdapter {
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

    @Override
    public PatternSemantics analyze(IPatternDetails pattern) {
        Object definition = null;
        try {
            definition = pattern.getDefinition();
            if (!supports(pattern)) {
                return PatternSemantics.unsupported(pattern, definition, "THUNDER_UNSUPPORTED_SEMANTICS");
            }

            IPatternDetails sourcePattern = unwrap(pattern);
            PatternSemantics ae2 = ae2Adapter.analyze(sourcePattern);
            if (!ae2.supported()) {
                return PatternSemantics.unsupported(pattern, definition,
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

            // Static planning commits ID_ONLY slots to the concrete templates advertised by IPatternDetails.
            // AE2LT remains free to substitute component variants at execution time. Such substitutions are
            // deliberately excluded from cycle-safety proofs because the concrete returned/output key may differ.
            PatternSemantics.MatchingMode matching = usesIdOnlyMatching
                ? PatternSemantics.MatchingMode.SUBSTITUTION : ae2.matchingMode();
            return new PatternSemantics(pattern, definition, ae2.consumedInputs(), ae2.producedOutputs(),
                ae2.returnedOutputs(), ae2.feedbackEdges(), matching,
                PatternSemantics.ExecutionRestriction.NONE, true,
                ae2.cycleSafe() && !usesIdOnlyMatching, null);
        } catch (RuntimeException rejected) {
            return PatternSemantics.unsupported(pattern, definition,
                "MALFORMED_THUNDER_PATTERN:" + rejected.getClass().getSimpleName());
        }
    }

    @Override
    public boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
        return supports(pattern) && inputSlot >= 0
            && (boolean) ThunderboltApi.invoke(IS_FUZZY_INPUT, pattern, inputSlot);
    }

    @Override
    public String name() {
        return "Thunder";
    }

    private static IPatternDetails unwrap(IPatternDetails pattern) {
        Set<IPatternDetails> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        IPatternDetails current = pattern;
        while (ThunderboltApi.isInstance(ThunderboltApi.WRAPPER, current)) {
            if (!visited.add(current)) throw new IllegalArgumentException("Cyclic Thunder pattern wrapper");
            IPatternDetails next = (IPatternDetails) ThunderboltApi.invoke(WRAPPED_PATTERN, current);
            if (next == null) throw new IllegalArgumentException("Null Thunder wrapped pattern");
            current = next;
        }
        return current;
    }

}
