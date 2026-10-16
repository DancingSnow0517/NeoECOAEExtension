package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.core.planner.acyclic.StockConsumption;
import cn.dancingsnow.neoecoae.core.planner.acyclic.SpecialDemandResolver;
import cn.dancingsnow.neoecoae.core.planner.acyclic.WorkingStockAmounts;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
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
    private final SpecialDemandResolver<AEKey, CompiledPattern, MaterialDemand> procurement;
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
        this.procurement = new SpecialDemandResolver<>(state,
            new SpecialDemandResolver.Domain<AEKey, CompiledPattern, MaterialDemand>() {
                @Override public MaterialDemand register(CompiledPattern owner, AEKey key, PlannerAmount amount) {
                    return inputDemand(owner, key, amount);
                }
                @Override public void inputProducer(AEKey key, CompiledPattern owner) { state.demandProducers.put(key, owner.details()); }
                @Override public PlannerAmount stock(CompiledPattern owner, MaterialDemand demand, AEKey key,
                        PlannerAmount amount, boolean ignoresComponents) {
                    return consumeStored(key, amount, ignoresComponents, demand, owner.inputs().get(demand.inputSlot()).source());
                }
                @Override public void consumeCrafted(MaterialDemand demand, AEKey key, PlannerAmount amount) {
                    state.consumeCrafted(demand, key, amount);
                }
                @Override public CompiledPattern selected(AEKey key) { return selectedPattern(key); }
                @Override public boolean hasProducer(AEKey key) { return !network.producersOf(key).isEmpty(); }
                @Override public boolean emittable(AEKey key) { return network.emittable().contains(key); }
                @Override public boolean cycleSafe(CompiledPattern pattern) { return pattern.semantics().cycleSafeForStaticPlanning(); }
                @Override public void emitted(MaterialDemand demand, AEKey key, PlannerAmount amount) {
                    state.provenance.allocate(demand, key, MaterialSource.Emitted.INSTANCE, amount);
                }
                @Override public void supplied(MaterialDemand demand, AEKey key, CompiledPattern producer, PlannerAmount amount) {
                    state.provenance.allocate(demand, key, new MaterialSource.PatternOutput(producer.details(), true), amount);
                }
                @Override public PlannerAmount outputPerPattern(CompiledPattern pattern) { return pattern.outputPerPattern(); }
                @Override public int outputCount(CompiledPattern pattern) { return pattern.outputs().size(); }
                @Override public AEKey outputKey(CompiledPattern pattern, int i) { return pattern.outputs().get(i).what(); }
                @Override public long outputAmount(CompiledPattern pattern, int i) { return pattern.outputs().get(i).amount(); }
                @Override public void special(CompiledPattern pattern, PlannerAmount times) throws InterruptedException {
                    SpecialPatternResolver.this.resolve(pattern, times);
                }
                @Override public boolean excludedInput(CompiledPattern pattern, int slot) {
                    return pattern.specialAnalysis().excludesFromCycleGraph(pattern.inputs().get(slot));
                }
            });
    }

    public static PlannerAmount requiredTools(PlannerAmount uses, int capacityPerTool) {
        return WorkingStockAmounts.requiredTools(uses, capacityPerTool);
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
                if (availableStored(possible.what(), input.ignoresComponents(), additional,
                        owner.details(), source).compareTo(additional) < 0) continue;
                if (additional.signum() > 0) {
                    MaterialDemand demand = inputDemand(owner, input.key(), additional);
                    consumeStored(possible.what(), additional, input.ignoresComponents(), demand, source);
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
        return WorkingStockAmounts.durabilityUsesBeforeBreak(damage, damagePerUse, maxDamage);
    }

    private void resolveSpecialKey(CompiledPattern owner, AEKey key, PlannerAmount requested,
            boolean ignoreComponents)
            throws InterruptedException {
        procurement.resolve(owner, key, requested, ignoreComponents);
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

    private PlannerAmount availableStored(AEKey key, boolean ignoreComponents, PlannerAmount requested,
            IPatternDetails consumer, IPatternDetails.IInput input) {
        if (!ignoreComponents || !(key instanceof AEItemKey wanted)) return state.stored.available(key, requested);
        PlannerAmount available = PlannerAmount.ZERO;
        for (var entry : state.stored.asMap().entrySet()) {
            if (entry.getKey() instanceof AEItemKey candidate && candidate.getItem() == wanted.getItem()) {
                if (!cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching
                        .isValid(consumer, input, key, entry.getKey(), null)) continue;
                if (state.stored.isUnbounded(entry.getKey())) return requested;
                available = available.add(entry.getValue());
            }
        }
        return available;
    }

    private PlannerAmount consumeStored(AEKey key, PlannerAmount requested, boolean ignoreComponents,
            MaterialDemand demand, IPatternDetails.IInput input) {
        return StockConsumption.consume(state.stored, state.used,
            key, requested, ignoreComponents && key instanceof AEItemKey,
            storedKey -> storedKey instanceof AEItemKey candidate && key instanceof AEItemKey wanted
                && candidate.getItem() == wanted.getItem()
                && cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching
                    .isValid(demand.consumer(), input, key, storedKey, null),
            (storedKey, amount) -> state.provenance.allocate(demand, storedKey, MaterialSource.Stock.INSTANCE, amount));
    }
}
