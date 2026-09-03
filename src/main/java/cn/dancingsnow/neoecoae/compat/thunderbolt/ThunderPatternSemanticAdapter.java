package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.WrappedPatternDetails;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/** Normalizes AE2 Lightning Technology overload patterns for ECO's static planner. */
public final class ThunderPatternSemanticAdapter implements PatternSemanticAdapter {
    private static final String[] KNOWN_CONTRACT_NAMES = {
        "FuzzyPatternInputs",
        "IWrappedPatternDetails",
        "IProviderLookupPattern",
        "ReusableStockPattern",
        "CraftingCpuRestrictedPattern",
        "PatternFiringExpander",
        "ClosedLoopPatternDetails",
        "ClosedLoopBatchPatternDetails",
        "ReusableSeedPattern"
    };

    private final AE2PatternSemanticAdapter ae2Adapter = new AE2PatternSemanticAdapter();

    @Override
    public boolean supports(IPatternDetails pattern) {
        if (pattern == null) return false;
        if (pattern instanceof OverloadedProviderOnlyPatternDetails) return true;
        for (Class<?> type : hierarchy(pattern.getClass())) {
            String lowerName = type.getName().toLowerCase(Locale.ROOT);
            if (lowerName.contains("thunder") || lowerName.contains("ae2lt")) return true;
            for (String known : KNOWN_CONTRACT_NAMES) {
                if (type.getSimpleName().equals(known)) return true;
            }
        }
        return false;
    }

    @Override
    public PatternSemantics analyze(IPatternDetails pattern) {
        Object definition = null;
        try {
            definition = pattern.getDefinition();
            if (!(pattern instanceof OverloadedProviderOnlyPatternDetails overloadPattern)) {
                return PatternSemantics.unsupported(pattern, definition, "THUNDER_UNSUPPORTED_SEMANTICS");
            }

            IPatternDetails sourcePattern = unwrap(pattern);
            PatternSemantics ae2 = ae2Adapter.analyze(sourcePattern);
            if (!ae2.supported()) {
                return PatternSemantics.unsupported(pattern, definition,
                    ae2.unsupportedReason() == null ? "THUNDER_INVALID_SOURCE_PATTERN" : ae2.unsupportedReason());
            }

            boolean hasIdOnlyInput = overloadPattern.hasFuzzyInputs();
            boolean hasIdOnlyOutput = false;
            for (int slot = 0; slot < pattern.getOutputs().size(); slot++) {
                if (overloadPattern.isFuzzyOutput(slot)) {
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
    public String name() {
        return "Thunder";
    }

    private static IPatternDetails unwrap(IPatternDetails pattern) {
        Set<IPatternDetails> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        IPatternDetails current = pattern;
        while (current instanceof WrappedPatternDetails wrapped) {
            if (!visited.add(current)) throw new IllegalArgumentException("Cyclic Thunder pattern wrapper");
            IPatternDetails next = wrapped.wrappedPatternDetails();
            if (next == null) throw new IllegalArgumentException("Null Thunder wrapped pattern");
            current = next;
        }
        return current;
    }

    private static Iterable<Class<?>> hierarchy(Class<?> start) {
        Set<Class<?>> result = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Class<?>> pending = new java.util.ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!result.add(type)) continue;
            Collections.addAll(pending, type.getInterfaces());
            if (type.getSuperclass() != null) pending.addLast(type.getSuperclass());
        }
        return result;
    }
}
