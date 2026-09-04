package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Map-based specification used to validate the primitive ownership kernel and event replay. */
public final class ReferenceOwnershipLedger {
    private final Map<Object, Long> onHand = new LinkedHashMap<>();
    private final Map<Object, Long> futureNeed = new LinkedHashMap<>();
    private final List<PendingChoiceGroup> choices;
    private final List<OwnershipEvent> events = new ArrayList<>();

    public ReferenceOwnershipLedger(Map<?, Long> onHand, Map<?, Long> futureNeed,
            List<PendingChoiceGroup> choices) {
        this.onHand.putAll(onHand);
        this.futureNeed.putAll(futureNeed);
        this.choices = new ArrayList<>(choices);
    }

    public long onHand(Object key) { return onHand.getOrDefault(key, 0L); }
    public long futureNeed(Object key) { return futureNeed.getOrDefault(key, 0L); }
    public long reserve(Object key) {
        long result = futureNeed(key);
        for (PendingChoiceGroup group : choices) {
            long groupMaximum = 0L;
            for (Map<?, Long> branch : group.branches()) {
                Long amount = branch.get(key);
                groupMaximum = Math.max(groupMaximum, amount == null ? 0L : amount);
            }
            result = Math.addExact(result, groupMaximum);
        }
        return result;
    }
    public long releasable(Object key) { return Math.max(0L, onHand(key) - reserve(key)); }

    public void commitAccepted(Map<?, Long> consumed) {
        consumed.forEach((key, amount) -> {
            if (amount == null || amount < 0L || onHand(key) < amount) {
                throw new IllegalArgumentException("Dispatch consumption exceeds ownership");
            }
        });
        consumed.forEach((key, amount) -> {
            onHand.put(key, onHand(key) - amount);
            futureNeed.put(key, Math.max(0L, futureNeed(key) - amount));
        });
        events.add(new OwnershipEvent(OwnershipEvent.Type.DISPATCH_COMMITTED, Map.copyOf(consumed), 1L));
    }

    public void acceptOutput(Object key, long amount) {
        if (amount <= 0L) throw new IllegalArgumentException("Output amount must be positive");
        onHand.merge(key, amount, Math::addExact);
        events.add(new OwnershipEvent(OwnershipEvent.Type.OUTPUT_RETURNED, key, amount));
    }

    public void releaseExternal(Object key, long amount) {
        if (amount <= 0L || amount > releasable(key)) {
            throw new IllegalArgumentException("Release exceeds unreserved ownership");
        }
        onHand.put(key, onHand(key) - amount);
        events.add(new OwnershipEvent(OwnershipEvent.Type.OWNERSHIP_RELEASED, key, amount));
    }

    public void choose(String id, int branchIndex) {
        for (int index = 0; index < choices.size(); index++) {
            PendingChoiceGroup group = choices.get(index);
            if (!group.id().equals(id)) continue;
            group.branches().get(branchIndex).forEach((key, amount) ->
                futureNeed.merge(key, amount, Math::addExact));
            choices.remove(index);
            return;
        }
        throw new IllegalArgumentException("Unknown choice group " + id);
    }

    public List<OwnershipEvent> events() { return List.copyOf(events); }
    public Map<Object, Long> onHandSnapshot() { return Map.copyOf(onHand); }
    public Map<Object, Long> futureNeedSnapshot() { return Map.copyOf(futureNeed); }
}
