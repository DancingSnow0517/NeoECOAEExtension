package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Optional Thunder boundary.  Thunder is not a compile-time dependency of this extension, so the adapter detects
 * its contract by type name and accepts semantics only when Thunder (or a bridge) supplies a complete normalized
 * contract.  A recognized but incomplete pattern is explicitly declined.
 */
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

    @Override
    public boolean supports(IPatternDetails pattern) {
        if (pattern == null) return false;
        for (Class<?> type : hierarchy(pattern.getClass())) {
            String name = type.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("thunderbolt") || lower.contains("thunder")) return true;
            for (String known : KNOWN_CONTRACT_NAMES) if (type.getSimpleName().equals(known)) return true;
        }
        return findSemanticMethod(pattern) != null;
    }

    @Override
    public PatternSemantics analyze(IPatternDetails pattern) {
        Object definition = null;
        try {
            definition = pattern.getDefinition();
            Method method = findSemanticMethod(pattern);
            if (method != null) {
                Object value = method.invoke(pattern);
                if (value instanceof PatternSemantics semantics && semantics.physicalPattern() == pattern
                        && semantics.completeForStaticPlanning()) {
                    return semantics;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Decline below. Reflection failures are an unknown semantic contract, never a reason to guess.
        }
        return PatternSemantics.unsupported(pattern, definition, "THUNDER_UNSUPPORTED_SEMANTICS");
    }

    @Override
    public String name() {
        return "Thunder";
    }

    private static Method findSemanticMethod(IPatternDetails pattern) {
        for (String name : new String[] {"neoecoae$patternSemantics", "getNeoECOAESemantics",
                "getPatternSemantics"}) {
            try {
                Method method = pattern.getClass().getMethod(name);
                if (PatternSemantics.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 0) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next optional bridge name.
            }
        }
        return null;
    }

    private static Iterable<Class<?>> hierarchy(Class<?> start) {
        java.util.LinkedHashSet<Class<?>> result = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Class<?>> pending = new java.util.ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!result.add(type)) continue;
            for (Class<?> contract : type.getInterfaces()) pending.addLast(contract);
            if (type.getSuperclass() != null) pending.addLast(type.getSuperclass());
        }
        return result;
    }
}
