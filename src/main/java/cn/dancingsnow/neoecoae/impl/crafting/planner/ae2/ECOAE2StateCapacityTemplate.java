package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A finite state-transition chain for one reusable pattern input.
 *
 * <p>The planner must account for the number of transitions available from the
 * concrete input states, but it must not materialize every state as an ordinary
 * recipe variant. This template keeps that capacity separate until the selected
 * AE2 batches are assembled.</p>
 */
final class ECOAE2StateCapacityTemplate {
    private final Map<AEKey, AEKey> nextStates;

    ECOAE2StateCapacityTemplate(Map<AEKey, AEKey> nextStates) {
        this.nextStates = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(nextStates, "nextStates")));
    }

    long availableBatches(Map<AEKey, Long> inventory) {
        long total = 0L;
        for (var entry : inventory.entrySet()) {
            long count = entry.getValue();
            if (count <= 0L) {
                continue;
            }
            long uses = remainingUses(entry.getKey());
            if (uses > 0L) {
                total = Math.addExact(total, Math.multiplyExact(count, uses));
            }
        }
        return total;
    }

    boolean accepts(AEKey state) {
        return nextStates.containsKey(state);
    }

    List<AEKey> sequence(Map<AEKey, Long> inventory, long batches) {
        if (batches < 0L || batches > availableBatches(inventory)) {
            throw new IllegalArgumentException("Requested state batches exceed available capacity");
        }
        Map<AEKey, Long> remaining = new LinkedHashMap<>(inventory);
        List<AEKey> result = new ArrayList<>();
        while (batches > 0L) {
            AEKey current = nextUsableState(remaining);
            if (current == null) {
                throw new IllegalStateException("State capacity disappeared while expanding sequence");
            }
            remaining.merge(current, -1L, Long::sum);
            AEKey next = nextStates.get(current);
            if (next != null) {
                remaining.merge(next, 1L, Long::sum);
            }
            result.add(current);
            batches--;
        }
        return List.copyOf(result);
    }

    private long remainingUses(AEKey initial) {
        long uses = 0L;
        AEKey current = initial;
        Map<AEKey, Boolean> seen = new LinkedHashMap<>();
        while (nextStates.containsKey(current) && seen.put(current, Boolean.TRUE) == null) {
            uses++;
            current = nextStates.get(current);
        }
        return uses;
    }

    private AEKey nextUsableState(Map<AEKey, Long> inventory) {
        for (var entry : inventory.entrySet()) {
            if (entry.getValue() > 0L && nextStates.containsKey(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
