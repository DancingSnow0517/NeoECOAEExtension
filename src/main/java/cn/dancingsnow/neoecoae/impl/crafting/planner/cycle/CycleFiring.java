package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;

/**
 * One step of an execution witness: fire {@code pattern} exactly once at position {@code step}.
 *
 * <p>Single-firing granularity is deliberate. Run-length compression would pick one particular
 * interleaving, and the witness has to stay replayable step by step for the non-negativity check.
 */
public record CycleFiring(int step, CompiledPattern pattern) {
    public CycleFiring {
        if (step < 0) throw new IllegalArgumentException("Witness step must not be negative");
        if (pattern == null) throw new IllegalArgumentException("Witness step must name a pattern");
    }
}
