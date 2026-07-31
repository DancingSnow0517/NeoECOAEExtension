package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;

/** Exact per-craft composition selected for one AE2 pattern input. */
public record ECOAE2InputSelection(List<Alternative> alternatives) {
    public ECOAE2InputSelection {
        alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("An input selection must contain at least one alternative");
        }
    }

    public static ECOAE2InputSelection single(GenericStack template, long multiplier) {
        return new ECOAE2InputSelection(List.of(new Alternative(template, multiplier)));
    }

    public long totalMultiplier() {
        long total = 0L;
        for (Alternative alternative : alternatives) {
            total = Math.addExact(total, alternative.multiplier());
        }
        return total;
    }

    public record Alternative(GenericStack template, long multiplier) {
        public Alternative {
            Objects.requireNonNull(template, "template");
            if (template.amount() <= 0L || multiplier <= 0L) {
                throw new IllegalArgumentException("Selected input amounts and multipliers must be positive");
            }
        }
    }
}
