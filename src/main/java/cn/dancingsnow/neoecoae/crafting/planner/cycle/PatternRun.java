package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;

/**
 * Compact execution metadata: fire {@code pattern} {@code count} times per lap.
 * The final run repeats the preceding {@code repeatWidth} runs (itself included)
 * {@code repetitions} times in total. Circuits are non-nested and preserve physical pattern order.
 *
 * <p>This is the run-length form of {@link CycleFiring}. It exists because a single-transition cycle phase
 * has no interleaving to pick — the order of {@code N} firings of the only pattern in the phase is not a
 * choice — so a plan of a million firings is one entry, never a million witness steps.
 *
 * <p>For a normal-sized witness this is its order-preserving run-length encoding. For a large batch search result it
 * can be the only materialized execution trace, retaining the exact order without allocating one object per firing.
 */
public record PatternRun(CompiledPattern pattern, long count, int repeatWidth, long repetitions)
        implements RepeatLayout.Run {
    public PatternRun(CompiledPattern pattern, long count) { this(pattern, count, 1, 1L); }

    public PatternRun {
        if (pattern == null) throw new IllegalArgumentException("A pattern run must name a pattern");
        if (count < 0) throw new IllegalArgumentException("A pattern run count must not be negative");
        if (repeatWidth < 1 || repetitions < 1 || repetitions == 1 && repeatWidth != 1) {
            throw new IllegalArgumentException("Invalid pattern circuit repeat");
        }
    }

    public IPatternDetails details() {
        return pattern.details();
    }
}
