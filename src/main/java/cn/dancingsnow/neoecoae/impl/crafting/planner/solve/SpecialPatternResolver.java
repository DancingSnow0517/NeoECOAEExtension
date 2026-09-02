package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.impl.crafting.planner.provenance.MaterialSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** Resolves working stock and the producer chain needed only to obtain that stock. */
public final class SpecialPatternResolver {
    private final CompiledNetwork network;
    private final SolveState state;
    private final Map<AEKey, Integer> choices;
    private final ECOCancellation cancellation;
    private final Set<AEKey> resolving = new LinkedHashSet<>();

    SpecialPatternResolver(CompiledNetwork network, SolveState state, Map<AEKey, Integer> choices,
            ECOCancellation cancellation) {
        this.network = network;
        this.state = state;
        this.choices = choices;
        this.cancellation = cancellation;
    }

    public static PlannerAmount requiredTools(PlannerAmount uses, int capacityPerTool) {
        if (capacityPerTool <= 0) throw new IllegalArgumentException("capacityPerTool must be positive");
        return uses.ceilDiv(PlannerAmount.of(capacityPerTool));
    }

    void resolve(CompiledPattern pattern, PlannerAmount times) throws InterruptedException {
        for (var requirement : pattern.specialAnalysis().requirements()) {
            cancellation.checkpoint();
            if (requirement.type() == SpecialPatternAnalysis.Type.DURABILITY) {
                resolveDurability(pattern, requirement, times);
            } else {
                PlannerAmount count = requirement.type() == SpecialPatternAnalysis.Type.CONTAINER
                    ? requirement.input().amountPerPattern().multiply(times)
                    : requirement.input().amountPerPattern();
                resolveSpecialKey(pattern, requirement.input().key(), count);
            }
        }
    }

    private void resolveDurability(CompiledPattern owner, SpecialPatternAnalysis.Requirement requirement,
            PlannerAmount times) throws InterruptedException {
        PlannerAmount uses = requirement.input().amountPerPattern().multiply(times);
        ItemStack template = ((AEItemKey) requirement.input().key()).toStack(1);
        List<Map.Entry<AEKey, PlannerAmount>> available = new ArrayList<>(state.stored.asMap().entrySet());
        for (var entry : available) {
            if (uses.isZero()) break;
            if (!(entry.getKey() instanceof AEItemKey itemKey)) continue;
            ItemStack candidate = itemKey.toStack(1);
            if (candidate.isEmpty() || !candidate.isDamageableItem()
                    || !ItemStack.isSameItem(template, candidate)) continue;
            int capacity = (candidate.getMaxDamage() - candidate.getDamageValue()) / requirement.damagePerUse();
            if (capacity <= 0) continue;
            PlannerAmount tools = requiredTools(uses, capacity).min(entry.getValue());
            if (tools.signum() <= 0) continue;
            state.stored.remove(entry.getKey(), tools);
            state.used.add(entry.getKey(), tools);
            state.provenance.supplied(entry.getKey(), MaterialSource.Stock.INSTANCE, tools);
            uses = uses.subtract(tools.multiply(capacity)).max(PlannerAmount.ZERO);
        }
        if (uses.isZero()) return;

        int freshCapacity = (requirement.maxDamage() - template.getDamageValue()) / requirement.damagePerUse();
        if (freshCapacity <= 0) {
            state.unsupported.add(requirement.input().key());
            return;
        }
        resolveSpecialKey(owner, requirement.input().key(),
            requiredTools(uses, freshCapacity));
    }

    private void resolveSpecialKey(CompiledPattern owner, AEKey key, PlannerAmount requested)
            throws InterruptedException {
        if (requested.signum() <= 0) return;
        state.demand.merge(key, requested, PlannerAmount::add);
        state.demandProducers.put(key, owner.details());
        state.parents.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(owner.producedKey());

        PlannerAmount stored = requested.min(state.stored.get(key));
        if (stored.signum() > 0) {
            state.stored.remove(key, stored);
            state.used.add(key, stored);
            state.provenance.supplied(key, MaterialSource.Stock.INSTANCE, stored);
            requested = requested.subtract(stored);
        }
        PlannerAmount crafted = requested.min(state.craftedAmount(key));
        if (crafted.signum() > 0) {
            state.consumeCrafted(key, crafted);
            requested = requested.subtract(crafted);
        }
        if (requested.isZero()) return;
        if (network.emittable().contains(key)) {
            state.emitted.add(key, requested);
            state.provenance.supplied(key, MaterialSource.Emitted.INSTANCE, requested);
            return;
        }
        if (!resolving.add(key)) {
            state.unsupported.add(key);
            return;
        }
        try {
            CompiledPattern producer = selectedPattern(key);
            if (producer == null) {
                if (network.producersOf(key).isEmpty()) state.missing.add(key, requested);
                else state.unsupported.add(key);
                return;
            }
            state.selected.put(key, producer);
            state.provenance.supplied(key, new MaterialSource.PatternOutput(producer.details(), true), requested);
            PlannerAmount times = requested.ceilDiv(producer.outputPerPattern());
            state.patternTimes.merge(producer.details(), times, PlannerAmount::add);
            state.bytes = state.bytes.add(times);
            for (var output : producer.outputs()) {
                PlannerAmount produced = PlannerAmount.of(output.amount()).multiply(times);
                if (output.what().equals(key)) produced = produced.subtract(requested);
                if (produced.signum() > 0) state.creditCrafted(output.what(), producer.details(), produced);
            }
            resolve(producer, times);
            for (CompiledInput input : producer.inputs()) {
                if (producer.specialAnalysis().excludesFromCycleGraph(input)) continue;
                resolveSpecialKey(producer, input.key(), input.amountPerPattern().multiply(times));
            }
        } finally {
            resolving.remove(key);
        }
    }

    private CompiledPattern selectedPattern(AEKey key) {
        List<CompiledPattern> candidates = network.producersOf(key).stream()
            .filter(CompiledPattern::fastSupported).toList();
        if (candidates.isEmpty()) return null;
        int choice = Math.max(0, choices.getOrDefault(key, 0));
        return candidates.get(Math.min(choice, candidates.size() - 1));
    }
}
