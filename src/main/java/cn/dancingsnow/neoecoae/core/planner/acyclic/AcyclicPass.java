package cn.dancingsnow.neoecoae.core.planner.acyclic;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import cn.dancingsnow.neoecoae.core.planner.PatternView;
import cn.dancingsnow.neoecoae.core.planner.PlannerCounter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregated demand/credit pass. Pattern decoding, matching and provenance representation belong to adapters. */
public final class AcyclicPass {
    private AcyclicPass() {}

    public interface Queue<K> {
        void add(K key);
        boolean isEmpty();
        K poll() throws InterruptedException;
    }

    /** Mutable bookkeeping shared with the owning solve transaction, without copying its counters. */
    public interface State<K, P> {
        PlannerCounter<K> stored();
        PlannerCounter<K> emitted();
        PlannerCounter<K> missing();
        Map<K, PlannerAmount> demand();
        Map<K, P> selected();
        Map<K, Set<K>> parents();
        Set<K> unsupported();
        void addBytes(PlannerAmount amount);
        void recordPattern(P pattern, PlannerAmount times);
        PlannerAmount craftedAmount(K key);
        void consumeCrafted(K key, PlannerAmount amount);
        void creditCrafted(K key, P pattern, PlannerAmount amount);
    }

    /** Platform policy only: no aggregate firing-count or demand arithmetic is delegated here. */
    public interface Domain<K, P extends PatternView<K>> {
        K goal();
        P selected(K key);
        boolean hasProducer(K key);
        boolean emittable(K key);
        boolean deferred(P pattern);
        long amountPerByte(K key);
        PlannerAmount outputPerPattern(P pattern);
        int outputCount(P pattern);
        K outputKey(P pattern, int index);
        long outputAmount(P pattern, int index);
        boolean excludedInput(P pattern, int slot);
        boolean reusableInput(P pattern, int slot);
        Queue<K> queue(List<K> route, Map<K, P> selected, Set<K> stockLeaves);
        void registerGoal(K key, PlannerAmount amount);
        PlannerAmount pending(K key);
        PlannerAmount consumeRequestedStock(K key, PlannerAmount requested);
        void emitted(K key, PlannerAmount amount);
        void supplied(K key, P pattern, PlannerAmount amount, boolean deferred);
        void resolveSpecial(P pattern, PlannerAmount times) throws InterruptedException;
        /** Register attribution and consume any matching alternative stock; returns only the quantity consumed. */
        PlannerAmount registerInput(P pattern, int slot, PlannerAmount required);
        void inputProducer(K key, P pattern);
    }

    public static <K, P extends PatternView<K>> void run(State<K, P> state, Domain<K, P> domain,
            List<K> route, Map<K, PlannerAmount> goals, boolean additionalGoal,
            Set<K> stockLeaves, Cancellation cancellation) throws InterruptedException {
        if (additionalGoal) state.stored().set(domain.goal(), PlannerAmount.ZERO);
        goals.forEach((key, amount) -> {
            state.demand().put(key, amount);
            domain.registerGoal(key, amount);
        });
        state.addBytes(PlannerAmount.of(route.size()).multiply(8L));
        Map<K, P> selected = new LinkedHashMap<>();
        for (K key : route) {
            P pattern = domain.selected(key);
            if (pattern != null) selected.put(key, pattern);
        }
        Queue<K> pending = domain.queue(route, selected, stockLeaves);
        Set<K> unsettled = new LinkedHashSet<>();
        goals.keySet().forEach(pending::add);
        while (!pending.isEmpty()) {
            cancellation.checkpoint();
            K key = pending.poll();
            PlannerAmount requested = domain.pending(key);
            if (requested.signum() <= 0) continue;
            state.addBytes(PlannerAmount.stackBytes(requested, domain.amountPerByte(key)));
            requested = requested.subtract(domain.consumeRequestedStock(key, requested));
            PlannerAmount crafted = requested.min(state.craftedAmount(key));
            if (crafted.signum() > 0) {
                state.consumeCrafted(key, crafted);
                requested = requested.subtract(crafted);
            }
            if (requested.isZero()) continue;
            if (domain.emittable(key)) {
                state.emitted().add(key, requested);
                domain.emitted(key, requested);
                continue;
            }
            P pattern = domain.selected(key);
            if (stockLeaves.contains(key) || pattern == null) {
                unsettled.add(key);
                continue;
            }
            state.selected().put(key, pattern);
            boolean deferred = domain.deferred(pattern);
            domain.supplied(key, pattern, requested, deferred);
            if (deferred) continue;
            PlannerAmount times = requested.ceilDiv(domain.outputPerPattern(pattern));
            state.recordPattern(pattern, times);
            state.addBytes(times);
            Map<K, PlannerAmount> produced = new LinkedHashMap<>();
            for (int i = 0; i < domain.outputCount(pattern); i++) {
                produced.merge(domain.outputKey(pattern, i),
                    PlannerAmount.of(domain.outputAmount(pattern, i)).multiply(times), PlannerAmount::add);
            }
            for (var output : produced.entrySet()) {
                PlannerAmount available = output.getValue();
                if (output.getKey().equals(key)) available = available.subtract(requested);
                if (available.signum() > 0) state.creditCrafted(output.getKey(), pattern, available);
            }
            domain.resolveSpecial(pattern, times);
            for (int slot = 0; slot < pattern.inputCount(); slot++) {
                if (domain.excludedInput(pattern, slot)) continue;
                PlannerAmount required = pattern.inputAmount(slot);
                if (!domain.reusableInput(pattern, slot)) required = required.multiply(times);
                required = required.subtract(domain.registerInput(pattern, slot, required));
                K input = pattern.inputKey(slot);
                state.demand().merge(input, required, PlannerAmount::add);
                domain.inputProducer(input, pattern);
                state.parents().computeIfAbsent(input, ignored -> new LinkedHashSet<>()).add(key);
                pending.add(input);
            }
        }
        for (K key : unsettled) {
            cancellation.checkpoint();
            PlannerAmount requested = domain.pending(key);
            PlannerAmount credited = requested.min(state.craftedAmount(key));
            if (credited.signum() > 0) state.consumeCrafted(key, credited);
            requested = requested.subtract(credited);
            if (requested.isZero()) continue;
            if (stockLeaves.contains(key) || domain.hasProducer(key)) state.unsupported().add(key);
            else state.missing().add(key, requested);
        }
    }
}
