package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.util.Objects;

/** A physical producer-to-consumer edge. Multiple edges for one key are intentional. */
public record ExecutionDependency(IPatternDetails producer, IPatternDetails consumer, AEKey key) {
    public ExecutionDependency {
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(key, "key");
    }
}
