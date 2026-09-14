package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import java.util.Map;

/** Semantic ownership transition used by the reference replay harness. */
public record OwnershipEvent(Type type, Object resource, long amount) {
    public enum Type { DISPATCH_COMMITTED, OUTPUT_RETURNED, OWNERSHIP_RELEASED }

    public Map<?, Long> consumed() {
        if (type != Type.DISPATCH_COMMITTED || !(resource instanceof Map<?, ?> values)) return Map.of();
        java.util.LinkedHashMap<Object, Long> result = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!(value instanceof Long amountValue)) throw new IllegalArgumentException("Invalid consumed event");
            result.put(key, amountValue);
        });
        return Map.copyOf(result);
    }
}
