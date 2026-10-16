package cn.dancingsnow.neoecoae.core.planner.acyclic;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.planner.PatternView;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Recursive producer accounting for working stock. Ingredient and provenance interpretation remain platform policies. */
public final class SpecialDemandResolver<K, P extends PatternView<K>, D> {
    public interface Domain<K, P, D> {
        D register(P owner, K key, PlannerAmount amount);
        void inputProducer(K key, P owner);
        PlannerAmount stock(P owner, D demand, K key, PlannerAmount amount, boolean ignoresComponents);
        void consumeCrafted(D demand, K key, PlannerAmount amount);
        P selected(K key);
        boolean hasProducer(K key);
        boolean emittable(K key);
        boolean cycleSafe(P pattern);
        void emitted(D demand, K key, PlannerAmount amount);
        void supplied(D demand, K key, P producer, PlannerAmount amount);
        PlannerAmount outputPerPattern(P pattern);
        int outputCount(P pattern);
        K outputKey(P pattern, int index);
        long outputAmount(P pattern, int index);
        void special(P pattern, PlannerAmount times) throws InterruptedException;
        boolean excludedInput(P pattern, int slot);
    }

    private final AcyclicPass.State<K, P> state;
    private final Domain<K, P, D> domain;
    private final Map<K, PlannerAmount> resolving = new LinkedHashMap<>();

    public SpecialDemandResolver(AcyclicPass.State<K, P> state, Domain<K, P, D> domain) {
        this.state = state;
        this.domain = domain;
    }

    public void resolve(P owner, K key, PlannerAmount requested, boolean ignoresComponents) throws InterruptedException {
        if (requested.signum() <= 0) return;
        state.demand().merge(key, requested, PlannerAmount::add);
        domain.inputProducer(key, owner);
        state.parents().computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(owner.producedKey());
        D demand = domain.register(owner, key, requested);
        requested = requested.subtract(domain.stock(owner, demand, key, requested, ignoresComponents));
        PlannerAmount crafted = requested.min(state.craftedAmount(key));
        if (crafted.signum() > 0) {
            domain.consumeCrafted(demand, key, crafted);
            requested = requested.subtract(crafted);
        }
        if (requested.isZero()) return;
        if (domain.emittable(key)) {
            state.emitted().add(key, requested);
            domain.emitted(demand, key, requested);
            return;
        }
        PlannerAmount pending = resolving.get(key);
        if (pending != null) {
            // Supported, non-growing feedback still needs concrete material from outside this recursion.
            boolean safe = requested.compareTo(pending) >= 0;
            for (K member : resolving.keySet()) {
                P pattern = domain.selected(member);
                if (pattern == null || !domain.cycleSafe(pattern)) { safe = false; break; }
            }
            if (safe) state.missing().add(key, requested);
            else state.unsupported().add(key);
            return;
        }
        resolving.put(key, requested);
        try {
            P producer = domain.selected(key);
            if (producer == null) {
                if (domain.hasProducer(key)) state.unsupported().add(key);
                else state.missing().add(key, requested);
                return;
            }
            state.selected().put(key, producer);
            domain.supplied(demand, key, producer, requested);
            PlannerAmount times = requested.ceilDiv(domain.outputPerPattern(producer));
            state.recordPattern(producer, times);
            state.addBytes(times);
            for (int i = 0; i < domain.outputCount(producer); i++) {
                K output = domain.outputKey(producer, i);
                PlannerAmount produced = PlannerAmount.of(domain.outputAmount(producer, i)).multiply(times);
                if (output.equals(key)) produced = produced.subtract(requested);
                if (produced.signum() > 0) state.creditCrafted(output, producer, produced);
            }
            domain.special(producer, times);
            for (int slot = 0; slot < producer.inputCount(); slot++) {
                if (domain.excludedInput(producer, slot)) continue;
                resolve(producer, producer.inputKey(slot), producer.inputAmount(slot).multiply(times),
                    producer.inputIgnoresComponents(slot));
            }
        } finally {
            resolving.remove(key);
        }
    }
}
