package cn.dancingsnow.neoecoae.impl.crafting.planner.provenance;

import appeng.api.crafting.IPatternDetails;
import java.util.Objects;

/** One origin of material consumed by the numeric plan. */
public sealed interface MaterialSource {
    record Stock() implements MaterialSource {
        public static final Stock INSTANCE = new Stock();
    }

    record Emitted() implements MaterialSource {
        public static final Emitted INSTANCE = new Emitted();
    }

    record PatternOutput(IPatternDetails pattern, boolean primary) implements MaterialSource {
        public PatternOutput {
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    record CycleOutput(int componentId) implements MaterialSource { }
}
