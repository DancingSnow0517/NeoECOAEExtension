package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Transfers exact planner input selections from a freshly assembled plan to its executing CPU. */
public final class ECOPlannedInputs {
    private static final Map<ICraftingPlan, Map<IPatternDetails, ArrayDeque<PlannedInputBatch>>> PENDING =
        new WeakHashMap<>();

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
            PENDING.put(plan, selections);
        }
    }

    public static Map<IPatternDetails, ArrayDeque<PlannedInputBatch>> take(ICraftingPlan plan) {
        synchronized (PENDING) {
            Map<IPatternDetails, ArrayDeque<PlannedInputBatch>> selections = PENDING.remove(plan);
            return selections == null ? Map.of() : selections;
        }
    }

    public static final class PlannedInputBatch {
        private final List<GenericStack> selectedInputs;
        private long remaining;

        public PlannedInputBatch(List<GenericStack> selectedInputs, long remaining) {
            this.selectedInputs = List.copyOf(selectedInputs);
            if (remaining <= 0L) {
                throw new IllegalArgumentException("Planned input batch must contain at least one craft");
            }
            this.remaining = remaining;
        }

        public List<GenericStack> selectedInputs() {
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
