package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
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
        if (isOverload(pattern)) return true;
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
            if (!isOverload(pattern)) {
                return PatternSemantics.unsupported(pattern, definition, "THUNDER_UNSUPPORTED_SEMANTICS");
            }

            IPatternDetails sourcePattern = unwrap(pattern);
            PatternSemantics ae2 = ae2Adapter.analyze(sourcePattern);
            if (!ae2.supported()) {
                return PatternSemantics.unsupported(pattern, definition,
                    ae2.unsupportedReason() == null ? "THUNDER_INVALID_SOURCE_PATTERN" : ae2.unsupportedReason());
            }

            boolean hasIdOnlyInput = flag(pattern, "hasFuzzyInputs");
            boolean hasIdOnlyOutput = false;
            for (int slot = 0; slot < pattern.getOutputs().size(); slot++) {
                if (flag(pattern, "isFuzzyOutput", slot)) {
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
        return isOverload(pattern) && inputSlot >= 0 && flag(pattern, "isFuzzyInput", inputSlot);
    }

    @Override
    public String name() {
        return "Thunder";
    }

    private static IPatternDetails unwrap(IPatternDetails pattern) {
        Set<IPatternDetails> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        IPatternDetails current = pattern;
        while (isWrapper(current)) {
            if (!visited.add(current)) throw new IllegalArgumentException("Cyclic Thunder pattern wrapper");
            IPatternDetails next = (IPatternDetails) invoke(current, "wrappedPatternDetails");
            if (next == null) throw new IllegalArgumentException("Null Thunder wrapped pattern");
            current = next;
        }
        return current;
    }

    private static boolean isOverload(Object pattern) {
        return hasContract(pattern, "com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails")
            || hasContract(pattern, "com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails");
    }

    private static boolean isWrapper(Object pattern) {
        return hasContract(pattern, "com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails")
            || hasContract(pattern, "com.moakiee.thunderbolt.ae2.overload.pattern.WrappedPatternDetails");
    }

    private static boolean hasContract(Object value, String name) {
        for (Class<?> type : hierarchy(value.getClass())) if (type.getName().equals(name)) return true;
        return false;
    }

    private static boolean flag(Object value, String name, Object... args) {
        return Boolean.TRUE.equals(invoke(value, name, args));
    }

    private static Object invoke(Object value, String name, Object... args) {
        try {
            Class<?>[] parameters = args.length == 0 ? new Class<?>[0] : new Class<?>[]{int.class};
            return value.getClass().getMethod(name, parameters).invoke(value, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Unsupported Thunderbolt pattern contract: " + name, failure);
        }
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
