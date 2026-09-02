package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.item.ItemStack;

/** Linear durability transition for one or more reusable crafting tools. */
public final class ECODurabilityBatchModel implements ECOReusableStateModel {
    public enum BreakBehavior {
        DISAPPEAR
    }

    private final List<Transition> transitions;
    private final long maxBatchSize;

    private ECODurabilityBatchModel(List<Transition> transitions) {
        this.transitions = List.copyOf(transitions);
        long max = Long.MAX_VALUE;
        for (Transition transition : transitions) {
            if (transition.damageDelta() > 0) {
                max = Math.min(max,
                    (Long.MAX_VALUE - transition.initialDamage()) / transition.damageDelta());
            }
        }
        this.maxBatchSize = max;
    }

    public static Optional<ECODurabilityBatchModel> analyze(List<ItemStack> before, List<ItemStack> after) {
        if (before.size() != after.size()) return Optional.empty();
        List<Transition> found = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            ItemStack initial = before.get(i);
            ItemStack result = after.get(i);
            if (initial == null || initial.isEmpty() || !initial.isDamageableItem()) continue;

            int initialDamage = initial.getDamageValue();
            int maxDamage = initial.getMaxDamage();
            int delta;
            if (result == null || result.isEmpty()) {
                // The observed first craft broke this exact resolved stack. Every positive batch has the same
                // terminal state, so no unobserved post-break stack is synthesized.
                delta = Math.max(1, maxDamage - initialDamage);
            } else {
                if (!ItemStack.isSameItem(initial, result) || !result.isDamageableItem()) return Optional.empty();
                ItemStack normalizedInitial = initial.copyWithCount(1);
                ItemStack normalizedResult = result.copyWithCount(1);
                normalizedInitial.setDamageValue(0);
                normalizedResult.setDamageValue(0);
                if (!ItemStack.isSameItemSameComponents(normalizedInitial, normalizedResult)) return Optional.empty();
                delta = result.getDamageValue() - initialDamage;
                if (delta < 0) return Optional.empty();
            }
            found.add(new Transition(
                initial.copyWithCount(1),
                result == null ? ItemStack.EMPTY : result.copyWithCount(1),
                initialDamage,
                delta,
                maxDamage,
                BreakBehavior.DISAPPEAR
            ));
        }
        return found.isEmpty() ? Optional.empty() : Optional.of(new ECODurabilityBatchModel(found));
    }

    @Override
    public FastPathCapability capability() {
        return FastPathCapability.DURABILITY_LINEAR;
    }

    @Override
    public long maxBatchSize() {
        return maxBatchSize;
    }

    @Override
    public List<GenericStack> batchInputs(List<GenericStack> ordinaryInputs, long crafts) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : ordinaryInputs) {
            if (!matchesInitial(stack.what())) {
                counter.add(stack.what(), Math.multiplyExact(stack.amount(), crafts));
            }
        }
        for (Transition transition : transitions) {
            GenericStack initial = GenericStack.fromItemStack(transition.initialStack());
            counter.add(initial.what(), 1L);
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    @Override
    public List<GenericStack> batchRemainders(List<GenericStack> ordinaryRemainders, long crafts) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : ordinaryRemainders) {
            if (!matchesObservedResult(stack.what())) {
                counter.add(stack.what(), Math.multiplyExact(stack.amount(), crafts));
            }
        }
        for (Transition transition : transitions) {
            ItemStack finalStack = transition.apply(crafts);
            if (!finalStack.isEmpty()) {
                GenericStack result = GenericStack.fromItemStack(finalStack);
                counter.add(result.what(), result.amount());
            }
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    private boolean matchesInitial(AEKey key) {
        return transitions.stream().anyMatch(transition ->
            GenericStack.fromItemStack(transition.initialStack()).what().equals(key));
    }

    private boolean matchesObservedResult(AEKey key) {
        return transitions.stream().anyMatch(transition -> !transition.observedResult().isEmpty()
            && GenericStack.fromItemStack(transition.observedResult()).what().equals(key));
    }

    List<Transition> transitions() {
        return transitions;
    }

    public record Transition(
        ItemStack initialStack,
        ItemStack observedResult,
        int initialDamage,
        int damageDelta,
        int maxDamage,
        BreakBehavior breakBehavior
    ) {
        public Transition {
            initialStack = initialStack.copyWithCount(1);
            observedResult = observedResult == null ? ItemStack.EMPTY : observedResult.copyWithCount(1);
        }

        ItemStack apply(long crafts) {
            OptionalInt finalDamage = calculateFinalDamage(
                initialDamage, damageDelta, maxDamage, breakBehavior, crafts);
            if (finalDamage.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = initialStack.copyWithCount(1);
            result.setDamageValue(finalDamage.getAsInt());
            return result;
        }
    }

    static OptionalInt calculateFinalDamage(
        int initialDamage,
        int damageDelta,
        int maxDamage,
        BreakBehavior breakBehavior,
        long crafts
    ) {
        if (crafts < 0L || damageDelta < 0) throw new IllegalArgumentException("negative durability transition");
        long finalDamage = Math.addExact(initialDamage, Math.multiplyExact((long) damageDelta, crafts));
        if (finalDamage >= maxDamage && breakBehavior == BreakBehavior.DISAPPEAR) return OptionalInt.empty();
        return OptionalInt.of(Math.toIntExact(finalDamage));
    }
}
