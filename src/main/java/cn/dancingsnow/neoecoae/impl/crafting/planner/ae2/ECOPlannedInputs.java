package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transfers exact planner input selections from a freshly assembled plan to its executing CPU. */
public final class ECOPlannedInputs {
    private static final ReferenceQueue<ICraftingPlan> STALE_PLANS = new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, Map<IPatternDetails, ArrayDeque<PlannedInputBatch>>> PENDING =
        new HashMap<>();

    private ECOPlannedInputs() {
    }

    public static void register(
        CraftingPlan plan,
        List<ECOScheduledStep<ECOAE2PatternVariant>> steps
    ) {
        Map<IPatternDetails, ArrayDeque<PlannedInputBatch>> selections = new LinkedHashMap<>();
        for (var step : steps) {
            ECOAE2PatternVariant variant = step.operation();
            ArrayDeque<PlannedInputBatch> batches = selections.computeIfAbsent(
                variant.pattern(), ignored -> new ArrayDeque<>());
            PlannedInputBatch last = batches.peekLast();
            if (last != null && last.selectedInputs().equals(variant.selectedInputs())) {
                last.add(step.batches());
            } else {
                batches.addLast(new PlannedInputBatch(variant.selectedInputs(), step.batches()));
            }
        }
        synchronized (PENDING) {
            removeStalePlans();
            PENDING.put(new IdentityWeakReference(plan, STALE_PLANS), selections);
        }
    }

    public static Map<IPatternDetails, ArrayDeque<PlannedInputBatch>> take(ICraftingPlan plan) {
        synchronized (PENDING) {
            removeStalePlans();
            Map<IPatternDetails, ArrayDeque<PlannedInputBatch>> selections = PENDING.remove(
                new IdentityWeakReference(plan)
            );
            return selections == null ? Map.of() : selections;
        }
    }

    private static void removeStalePlans() {
        IdentityWeakReference reference;
        while ((reference = (IdentityWeakReference) STALE_PLANS.poll()) != null) {
            PENDING.remove(reference);
        }
    }

    private static final class IdentityWeakReference extends WeakReference<ICraftingPlan> {
        private final int identityHash;

        private IdentityWeakReference(ICraftingPlan plan, ReferenceQueue<ICraftingPlan> queue) {
            super(plan, queue);
            this.identityHash = System.identityHashCode(plan);
        }

        private IdentityWeakReference(ICraftingPlan plan) {
            super(plan);
            this.identityHash = System.identityHashCode(plan);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            ICraftingPlan plan = get();
            return other instanceof IdentityWeakReference reference
                && plan != null
                && plan == reference.get();
        }

        @Override
        public int hashCode() {
            return identityHash;
        }
    }

    public static final class PlannedInputBatch {
        private final List<ECOAE2InputSelection> selectedInputs;
        private long remaining;

        public PlannedInputBatch(List<ECOAE2InputSelection> selectedInputs, long remaining) {
            this.selectedInputs = List.copyOf(selectedInputs);
            if (remaining <= 0L) {
                throw new IllegalArgumentException("Planned input batch must contain at least one craft");
            }
            this.remaining = remaining;
        }

        public List<ECOAE2InputSelection> selectedInputs() {
            return selectedInputs;
        }

        public long remaining() {
            return remaining;
        }

        public void consume(long crafts) {
            if (crafts <= 0L || crafts > remaining) {
                throw new IllegalArgumentException("Invalid planned input consumption: " + crafts);
            }
            remaining -= crafts;
        }

        public void consumeOne() {
            consume(1L);
        }

        private void add(long crafts) {
            remaining = Math.addExact(remaining, crafts);
        }
    }
}
