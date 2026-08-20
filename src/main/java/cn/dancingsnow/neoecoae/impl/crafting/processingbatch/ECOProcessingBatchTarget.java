package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.Objects;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;

/** One ordinary AE2 Pattern Provider side selected for a counted dispatch. */
public final class ECOProcessingBatchTarget {
    private final Direction direction;
    private final PatternProviderTarget target;
    private final Object identity;

    public ECOProcessingBatchTarget(Direction direction, PatternProviderTarget target) {
        this(direction, target, target);
    }

    public ECOProcessingBatchTarget(
        Direction direction,
        PatternProviderTarget target,
        Object identity
    ) {
        this.direction = Objects.requireNonNull(direction, "direction");
        this.target = Objects.requireNonNull(target, "target");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    public ECOProcessingBatchTarget(
        Identity identity,
        Direction direction,
        PatternProviderTarget target
    ) {
        this(direction, target, identity);
    }

    public Direction direction() {
        return direction;
    }

    /** Identity of the external machine route used by dispatch reservations. */
    public Object identity() {
        return identity;
    }

    public record MachineTargetIdentity(ResourceLocation dimension, BlockPos position) {
        public MachineTargetIdentity {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(position, "position");
        }
    }

    /** Stable value identity used by callers that already have a compact machine id. */
    public record Identity(ResourceLocation dimension, long id) {
        public Identity {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    public long insert(AEKey key, long amount, Actionable mode) {
        return target.insert(key, amount, mode);
    }

    public boolean containsPatternInput(Set<AEKey> patternInputs) {
        return target.containsPatternInput(patternInputs);
    }
}
