package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;

/**
 * Compact execution metadata: fire {@code pattern} {@code count} times.
 *
 * <p>This is the run-length form of {@link CycleFiring}. It exists because a single-transition cycle phase
 * has no interleaving to pick — the order of {@code N} firings of the only pattern in the phase is not a
 * choice — so a plan of a million firings is one entry, never a million witness steps.
 *
 * <p>For an ordinary multi-pattern cycle the per-firing {@link CycleFiring} witness stays authoritative and
 * keeps its semantics; the compact form of such a witness is only its order-preserving run-length encoding.
 */
public record PatternRun(CompiledPattern pattern, long count) {
    public PatternRun {
        if (pattern == null) throw new IllegalArgumentException("A pattern run must name a pattern");
        if (count < 0) throw new IllegalArgumentException("A pattern run count must not be negative");
    }

    public IPatternDetails details() {
        return pattern.details();
    }
}
