package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.stacks.AEKey;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runtime-only ownership ledger for one cycle component. */
public final class CycleResourceLedger {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");
    private static final boolean TRACE_EVENTS = false;
    private final int componentId;
    private final Map<AEKey, Long> bootstrapReserve = new LinkedHashMap<>();
    private final Map<AEKey, Long> bootstrapRequirement = new LinkedHashMap<>();
    private final Map<AEKey, Long> internalConsumed = new LinkedHashMap<>();
    private final Map<AEKey, Long> generated = new LinkedHashMap<>();
    private final Map<AEKey, Long> releasedSurplus = new LinkedHashMap<>();

    public CycleResourceLedger(int componentId, Map<AEKey, Long> bootstrap) {
        this.componentId = componentId;
        if (bootstrap != null) bootstrap.forEach((key, value) -> {
            if (key != null && value != null && value > 0) { bootstrapReserve.put(key, value); bootstrapRequirement.put(key, value); }
        });
    }
    public int componentId() { return componentId; }
    public Map<AEKey, Long> bootstrapReserve() { return Map.copyOf(bootstrapReserve); }
    public Map<AEKey, Long> internalConsumed() { return Map.copyOf(internalConsumed); }
    public Map<AEKey, Long> generated() { return Map.copyOf(generated); }
    public Map<AEKey, Long> releasedSurplus() { return Map.copyOf(releasedSurplus); }
    public long reserve(AEKey key) { return bootstrapReserve.getOrDefault(key, 0L); }
    public long generated(AEKey key) { return generated.getOrDefault(key, 0L); }
    public long released(AEKey key) { return releasedSurplus.getOrDefault(key, 0L); }

    public void consume(AEKey key, long amount) {
        if (key == null || amount <= 0) return;
        long before = reserve(key);
        long consumed = Math.min(before, amount);
        if (consumed > 0) bootstrapReserve.put(key, before - consumed);
        internalConsumed.merge(key, amount, Math::addExact);
        log(key, before, reserve(key), 0, 0, "internal_consumed");
    }

    /** Records cycle output and replenishes reserve before exposing any surplus. */
    public long recordGenerated(AEKey key, long amount) {
        if (key == null || amount <= 0) return 0;
        generated.merge(key, amount, Math::addExact);
        long before = reserve(key);
        long target = Math.max(0L, before);
        long replenish = Math.min(amount, Math.max(0L, requiredReserve(key) - before));
        if (replenish > 0) bootstrapReserve.put(key, before + replenish);
        long surplus = amount - replenish;
        log(key, before, reserve(key), amount, surplus, "kept_cycle_reserve");
        return surplus;
    }

    /** The minimum reserve is the bootstrap amount; generated surplus is never required reserve. */
    public long requiredReserve(AEKey key) { return bootstrapRequirement.getOrDefault(key, 0L); }

    public long availableForOutside(AEKey key) {
        return Math.max(0L, generated(key) - reserve(key) - released(key));
    }

    public long releaseSurplus(AEKey key, long amount) {
        long released = Math.min(Math.max(0L, amount), availableForOutside(key));
        if (released > 0) releasedSurplus.merge(key, released, Math::addExact);
        return released;
    }

    public void restoreReserve(AEKey key, long amount) {
        if (key != null && amount > 0) bootstrapReserve.merge(key, amount, Math::addExact);
    }

    private void log(AEKey key, long before, long after, long produced, long released, String reason) {
        if (TRACE_EVENTS) {
            LOGGER.trace("[ECO-CYCLE-RESOURCE] cycle={} item={} bootstrapReserve={} reserveAfter={} produced={} released={} reason={}",
                componentId, key, before, after, produced, released, reason);
        }
    }
}
