package cn.dancingsnow.neoecoae.crafting.planner.provenance;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.Objects;
import java.util.UUID;

/** A single numeric demand, retained even when its physical task is merged with another task. */
public record MaterialDemand(UUID id, Kind kind, IPatternDetails consumer, int inputSlot,
        Integer componentId, AEKey key, PlannerAmount amount) {
    public enum Kind { INPUT, GOAL, CYCLE_BOUNDARY }

    public MaterialDemand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Demand must be positive");
        if (kind == Kind.INPUT && (consumer == null || inputSlot < 0))
            throw new IllegalArgumentException("Input demand needs a consumer and slot");
        if (kind == Kind.CYCLE_BOUNDARY && componentId == null)
            throw new IllegalArgumentException("Cycle boundary needs a component");
        if (kind != Kind.INPUT && consumer != null)
            throw new IllegalArgumentException("Only an input demand has a pattern consumer");
        if (kind != Kind.CYCLE_BOUNDARY && componentId != null)
            throw new IllegalArgumentException("Only a cycle boundary has a component");
    }

    public static MaterialDemand input(IPatternDetails consumer, int slot, AEKey key, PlannerAmount amount) {
        return new MaterialDemand(UUID.randomUUID(), Kind.INPUT, consumer, slot, null, key, amount);
    }

    public static MaterialDemand goal(AEKey key, PlannerAmount amount) {
        return new MaterialDemand(UUID.randomUUID(), Kind.GOAL, null, -1, null, key, amount);
    }

    public static MaterialDemand boundary(int component, AEKey key, PlannerAmount amount) {
        return new MaterialDemand(UUID.randomUUID(), Kind.CYCLE_BOUNDARY, null, -1, component, key, amount);
    }
}
