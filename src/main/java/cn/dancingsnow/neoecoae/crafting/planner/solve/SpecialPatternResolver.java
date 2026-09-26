package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
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
    private final boolean ignorePatternSubstitutions;
    private final Map<AEKey, PlannerAmount> resolving = new java.util.LinkedHashMap<>();
    private final Map<AEKey, PlannerAmount> reusableStock = new java.util.LinkedHashMap<>();
    private CompiledPattern specialOwner;
    private int specialSlot = -1;

    SpecialPatternResolver(CompiledNetwork network, SolveState state, Map<AEKey, Integer> choices,
            ECOCancellation cancellation, boolean ignorePatternSubstitutions) {
        this.network = network;
        this.state = state;
        this.choices = choices;
        this.cancellation = cancellation;
        this.ignorePatternSubstitutions = ignorePatternSubstitutions;
    }

    public static PlannerAmount requiredTools(PlannerAmount uses, int capacityPerTool) {
        if (capacityPerTool <= 0) throw new IllegalArgumentException("capacityPerTool must be positive");
        return uses.ceilDiv(PlannerAmount.of(capacityPerTool));
    }

    void resolve(CompiledPattern pattern, PlannerAmount times) throws InterruptedException {
        Map<AEKey, PlannerAmount> simultaneous = new java.util.LinkedHashMap<>();
        for (var requirement : pattern.specialAnalysis().requirements()) {
            cancellation.checkpoint();
            CompiledPattern previousOwner = specialOwner;
            int previousSlot = specialSlot;
            specialOwner = pattern;
            specialSlot = pattern.inputs().indexOf(requirement.input());
            try {
                if (consumeStoredExactReusableAlternative(pattern, requirement, simultaneous)) {
                    continue;
                }
                if (requirement.type() == SpecialPatternAnalysis.Type.DURABILITY) {
                    resolveDurability(pattern, requirement, times);
                } else if (requirement.type() == SpecialPatternAnalysis.Type.REUSABLE) {
                    DurabilityChoice fallback = findDurabilityAlternative(requirement.input());
                    if (fallback != null) {
                        resolveDurability(pattern, fallback, times);
                    } else {
                        AEKey key = requirement.input().key();
                        PlannerAmount needed = simultaneous.merge(key, requirement.input().amountPerPattern(), PlannerAmount::add);
                        PlannerAmount reserved = reusableStock.getOrDefault(key, PlannerAmount.ZERO);
                        if (needed.compareTo(reserved) > 0) {
                            resolveSpecialKey(pattern, key, needed.subtract(reserved), requirement.input().ignoresComponents());
                            reusableStock.put(key, needed);
                        }
                    }
                } else {
                    PlannerAmount count = requirement.type() == SpecialPatternAnalysis.Type.CONTAINER
                        ? requirement.input().amountPerPattern().multiply(times)
                        : requirement.input().amountPerPattern();
                    resolveSpecialKey(pattern, requirement.input().key(), count,
                        requirement.input().ignoresComponents());
                }
            } finally {
                specialOwner = previousOwner;
                specialSlot = previousSlot;
            }
        }
    }

    private void resolveDurability(CompiledPattern owner, SpecialPatternAnalysis.Requirement requirement,
            PlannerAmount times) throws InterruptedException {
        resolveDurability(owner, new DurabilityChoice(requirement.input().key(),
            requirement.input().amountPerPattern(), requirement.damagePerUse(), requirement.maxDamage()), times);
    }

    private void resolveDurability(CompiledPattern owner, DurabilityChoice choice,
            PlannerAmount times) throws InterruptedException {
        PlannerAmount uses = choice.amountPerPattern().multiply(times);
        ItemStack template = ((AEItemKey) choice.key()).toStack(1);
        List<AEKey> available = state.stored.keysSnapshot();
        for (AEKey storedKey : available) {
            if (uses.isZero()) break;
            if (!(storedKey instanceof AEItemKey itemKey)) continue;
            ItemStack candidate = itemKey.toStack(1);
            if (candidate.isEmpty() || !candidate.isDamageableItem()
                    || !ItemStack.isSameItem(template, candidate)) continue;
            int capacity = durabilityUsesBeforeBreak(
                candidate.getDamageValue(), choice.damagePerUse(), candidate.getMaxDamage());
            if (capacity <= 0) continue;
            PlannerAmount tools = state.stored.available(storedKey, requiredTools(uses, capacity));
            if (tools.signum() <= 0) continue;
            state.stored.remove(storedKey, tools);
            state.used.add(storedKey, tools);
            MaterialDemand demand = inputDemand(owner, choice.key(), tools);
            state.provenance.allocate(demand, storedKey, MaterialSource.Stock.INSTANCE, tools);
            uses = uses.subtract(tools.multiply(capacity)).max(PlannerAmount.ZERO);
        }
        if (uses.isZero()) return;

        int freshCapacity = durabilityUsesBeforeBreak(
            template.getDamageValue(), choice.damagePerUse(), choice.maxDamage());
        if (freshCapacity <= 0) {
            state.unsupported.add(choice.key());
            return;
        }
        resolveSpecialKey(owner, choice.key(), requiredTools(uses, freshCapacity), false);
    }

    /** Prefer any accepted ingredient that the recipe returns byte-for-byte unchanged. */
    private boolean consumeStoredExactReusableAlternative(CompiledPattern owner, SpecialPatternAnalysis.Requirement requirement,
            Map<AEKey, PlannerAmount> simultaneous) {
        CompiledInput input = requirement.input();
        IPatternDetails.IInput source = input.source();
        if (source == null) return false;
        try {
            GenericStack[] possibleInputs = source.getPossibleInputs();
            int limit = ignorePatternSubstitutions ? Math.min(1, possibleInputs.length) : possibleInputs.length;
            for (int i = 0; i < limit; i++) {
                var possible = possibleInputs[i];
                if (possible == null || possible.what() == null || possible.amount() <= 0L) continue;
                AEKey returned = source.getRemainingKey(possible.what());
                if (returned == null || !returned.equals(possible.what())) continue;
                PlannerAmount needed = PlannerAmount.of(possible.amount()).multiply(source.getMultiplier());
                if (needed.signum() <= 0) continue;
                needed = needed.add(simultaneous.getOrDefault(possible.what(), PlannerAmount.ZERO));
                PlannerAmount reserved = reusableStock.getOrDefault(possible.what(), PlannerAmount.ZERO);
                PlannerAmount additional = needed.subtract(reserved).max(PlannerAmount.ZERO);
                if (availableStored(possible.what(), input.ignoresComponents(), additional).compareTo(additional) < 0) continue;
                if (additional.signum() > 0) {
                    MaterialDemand demand = inputDemand(owner, input.key(), additional);
                    consumeStored(possible.what(), additional, input.ignoresComponents(), demand);
                }
                reusableStock.put(possible.what(), reserved.max(needed));
                simultaneous.put(possible.what(), needed);
                return true;
            }
        } catch (RuntimeException ignored) {
            // Keep the compiled requirement as the conservative fallback.
        }
        return false;
    }

    private DurabilityChoice findDurabilityAlternative(CompiledInput input) {
        IPatternDetails.IInput source = input.source();
        if (source == null) return null;
        try {
            GenericStack[] possibleInputs = source.getPossibleInputs();
            int limit = ignorePatternSubstitutions ? Math.min(1, possibleInputs.length) : possibleInputs.length;
            for (int i = 0; i < limit; i++) {
                var possible = possibleInputs[i];
                if (!(possible.what() instanceof AEItemKey candidateKey) || possible.amount() <= 0L) continue;
                AEKey returnedKey = source.getRemainingKey(candidateKey);
                if (!(returnedKey instanceof AEItemKey returned)) continue;
                ItemStack candidate = candidateKey.toStack(1);
                ItemStack remainder = returned.toStack(1);
                if (!candidate.isDamageableItem() || !remainder.isDamageableItem()
                        || !ItemStack.isSameItem(candidate, remainder)) continue;
                int damagePerUse = remainder.getDamageValue() - candidate.getDamageValue();
                if (damagePerUse <= 0) continue;
                PlannerAmount amount = PlannerAmount.of(possible.amount()).multiply(source.getMultiplier());
                return new DurabilityChoice(candidateKey, amount, damagePerUse, candidate.getMaxDamage());
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private record DurabilityChoice(AEKey key, PlannerAmount amountPerPattern, int damagePerUse, int maxDamage) {}

    /** The final use that reaches maxDamage is still a successful craft; the tool disappears afterwards. */
    static int durabilityUsesBeforeBreak(int damage, int damagePerUse, int maxDamage) {
        if (damage < 0 || damagePerUse <= 0 || maxDamage <= damage) return 0;
        long remaining = (long) maxDamage - damage;
        return Math.toIntExact((remaining + damagePerUse - 1L) / damagePerUse);
    }

    private void resolveSpecialKey(CompiledPattern owner, AEKey key, PlannerAmount requested,
            boolean ignoreComponents)
            throws InterruptedException {
        if (requested.signum() <= 0) return;
        state.demand.merge(key, requested, PlannerAmount::add);
        state.demandProducers.put(key, owner.details());
        state.parents.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(owner.producedKey());

        MaterialDemand demand = inputDemand(owner, key, requested);
        PlannerAmount stored = consumeStored(key, requested, ignoreComponents, demand);
        requested = requested.subtract(stored);
        PlannerAmount crafted = requested.min(state.craftedAmount(key));
        if (crafted.signum() > 0) {
            state.consumeCrafted(demand, key, crafted);
            requested = requested.subtract(crafted);
        }
        if (requested.isZero()) return;
        if (network.emittable().contains(key)) {
            state.emitted.add(key, requested);
            state.provenance.allocate(demand, key, MaterialSource.Emitted.INSTANCE, requested);
            return;
        }
        PlannerAmount pending = resolving.get(key);
        if (pending != null) {
            // A non-growing conversion loop (for example block <-> dust while making a tool)
            // cannot supply its own outstanding ingredient. The fully expanded batch needs this
            // concrete quantity from outside. Keep it as a simulated material deficit, rather than
            // misclassifying supported recipes as an unsupported pattern contract.
            if (requested.compareTo(pending) >= 0 && resolving.keySet().stream().allMatch(member -> {
                CompiledPattern pattern = selectedPattern(member);
                return pattern != null && pattern.semantics().cycleSafeForStaticPlanning();
            })) state.missing.add(key, requested);
            else state.unsupported.add(key);
            return;
        }
        resolving.put(key, requested);
        try {
            CompiledPattern producer = selectedPattern(key);
            if (producer == null) {
                if (network.producersOf(key).isEmpty()) state.missing.add(key, requested);
                else state.unsupported.add(key);
                return;
            }
            state.selected.put(key, producer);
            state.provenance.allocate(demand, key, new MaterialSource.PatternOutput(producer.details(), true), requested);
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
                resolveSpecialKey(producer, input.key(), input.amountPerPattern().multiply(times),
                    input.ignoresComponents());
            }
        } finally {
            resolving.remove(key);
        }
    }

    private MaterialDemand inputDemand(CompiledPattern owner, AEKey key, PlannerAmount amount) {
        int slot = -1;
        for (int i = 0; i < owner.inputs().size(); i++) {
            CompiledInput input = owner.inputs().get(i);
            if (input.key().equals(key)) { slot = i; break; }
        }
        // A transformed durability alternative still belongs to its original requirement's slot.
        if (slot < 0 && owner == specialOwner) slot = specialSlot;
        if (slot < 0) throw new IllegalStateException("Special input has no consumer slot: " + key);
        MaterialDemand demand = MaterialDemand.input(owner.details(), slot, key, amount);
        state.provenance.register(demand);
        return demand;
    }

    private CompiledPattern selectedPattern(AEKey key) {
        List<CompiledPattern> candidates = network.fastProducersOf(key);
        if (candidates.isEmpty()) return null;
        int choice = Math.max(0, choices.getOrDefault(key, 0));
        return candidates.get(Math.min(choice, candidates.size() - 1));
    }

    private PlannerAmount availableStored(AEKey key, boolean ignoreComponents, PlannerAmount requested) {
        if (!ignoreComponents || !(key instanceof AEItemKey wanted)) return state.stored.available(key, requested);
        PlannerAmount available = PlannerAmount.ZERO;
        for (var entry : state.stored.asMap().entrySet()) {
            if (entry.getKey() instanceof AEItemKey candidate && candidate.getItem() == wanted.getItem()) {
                if (state.stored.isUnbounded(entry.getKey())) return requested;
                available = available.add(entry.getValue());
            }
        }
        return available;
    }

    private PlannerAmount consumeStored(AEKey key, PlannerAmount requested, boolean ignoreComponents,
            MaterialDemand demand) {
        if (requested.signum() <= 0) return PlannerAmount.ZERO;
        if (!ignoreComponents || !(key instanceof AEItemKey wanted)) {
            PlannerAmount exact = state.stored.available(key, requested);
            if (exact.signum() > 0) consumeExact(demand, key, exact);
            return exact;
        }
        PlannerAmount remaining = requested;
        PlannerAmount consumed = PlannerAmount.ZERO;
        for (AEKey storedKey : state.stored.keysSnapshot()) {
            if (remaining.isZero() || !(storedKey instanceof AEItemKey candidate)
                    || candidate.getItem() != wanted.getItem()) continue;
            PlannerAmount take = state.stored.available(storedKey, remaining);
            if (take.signum() <= 0) continue;
            consumeExact(demand, storedKey, take);
            consumed = consumed.add(take);
            remaining = remaining.subtract(take);
        }
        return consumed;
    }

    private void consumeExact(MaterialDemand demand, AEKey key, PlannerAmount amount) {
        state.stored.remove(key, amount);
        state.used.add(key, amount);
        state.provenance.allocate(demand, key, MaterialSource.Stock.INSTANCE, amount);
    }
}
