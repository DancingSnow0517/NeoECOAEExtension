package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.Objects;
import java.util.Set;

import net.minecraft.core.Direction;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;

/** One ordinary AE2 Pattern Provider side selected for a counted dispatch. */
public final class ECOProcessingBatchTarget {
    private final Direction direction;
    private final PatternProviderTarget target;

    public ECOProcessingBatchTarget(Direction direction, PatternProviderTarget target) {
        this.direction = Objects.requireNonNull(direction, "direction");
        this.target = Objects.requireNonNull(target, "target");
    }

    public Direction direction() {
        return direction;
    }

    public long insert(AEKey key, long amount, Actionable mode) {
        return target.insert(key, amount, mode);
    }

    public boolean containsPatternInput(Set<AEKey> patternInputs) {
        return target.containsPatternInput(patternInputs);
    }
}
