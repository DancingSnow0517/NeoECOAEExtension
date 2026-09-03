package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
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
                max = Math.min(max, maxCraftsBeforeBreak(
                    transition.initialDamage(), transition.damageDelta(), transition.maxDamage()));
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

    @Override
    public boolean requiresSecondStepProof() {
        return transitions.stream().anyMatch(transition ->
            !transition.observedResult().isEmpty() && transition.damageDelta() != 0);
    }

    @Override
    public boolean sameTransition(ECOReusableStateModel other) {
        if (!(other instanceof ECODurabilityBatchModel durability)
                || transitions.size() != durability.transitions.size()) return false;
        for (int i = 0; i < transitions.size(); i++) {
            Transition left = transitions.get(i);
            Transition right = durability.transitions.get(i);
            if (left.damageDelta() != right.damageDelta() || left.maxDamage() != right.maxDamage()
                    || !ItemStack.isSameItem(left.initialStack(), right.initialStack())) return false;
        }
        return true;
    }

    /**
     * Re-bases a previously verified linear durability proof onto the next concrete damage state. The ordinary
     * inputs and remainders are compared exactly, while each transition is allowed to differ only in damage.
     * This keeps the concrete fast-path key strict for every other recipe kind without forcing one slow proof
     * for every use of a tool whose state changes predictably.
     */
    Optional<ECODurabilityBatchModel> rebase(
        List<GenericStack> sourceInputs,
        List<GenericStack> sourceRemainders,
        List<GenericStack> currentInputs,
        List<GenericStack> currentRemainders
    ) {
        KeyCounter sourceInputCounter = toCounter(sourceInputs);
        KeyCounter sourceRemainderCounter = toCounter(sourceRemainders);
        KeyCounter currentInputCounter = toCounter(currentInputs);
        KeyCounter currentRemainderCounter = toCounter(currentRemainders);
        List<Transition> rebased = new ArrayList<>(transitions.size());

        for (Transition transition : transitions) {
            AEItemKey sourceInitialKey = AEItemKey.of(transition.initialStack());
            if (sourceInitialKey == null || !removeOne(sourceInputCounter, sourceInitialKey)) {
                return Optional.empty();
            }
            if (!transition.observedResult().isEmpty()) {
                AEItemKey sourceObservedKey = AEItemKey.of(transition.observedResult());
                if (sourceObservedKey == null || !removeOne(sourceRemainderCounter, sourceObservedKey)) {
                    return Optional.empty();
                }
            }

            AEItemKey currentInitialKey = findMatchingDamageable(currentInputCounter, transition.initialStack());
            if (currentInitialKey == null || !removeOne(currentInputCounter, currentInitialKey)) {
                return Optional.empty();
            }
            ItemStack currentInitial = currentInitialKey.toStack(1);
            if (currentInitial.isEmpty() || !sameItemAndComponentsIgnoringDamage(
                    transition.initialStack(), currentInitial)) {
                return Optional.empty();
            }
            int currentDamage = currentInitial.getDamageValue();
            if (currentDamage < 0 || currentDamage >= transition.maxDamage()) {
                return Optional.empty();
            }

            long finalDamage = (long) currentDamage + transition.damageDelta();
            ItemStack currentObserved = ItemStack.EMPTY;
            if (finalDamage < transition.maxDamage()) {
                if (finalDamage < 0L || finalDamage > Integer.MAX_VALUE) return Optional.empty();
                currentObserved = currentInitial.copyWithCount(1);
                currentObserved.setDamageValue((int) finalDamage);
                AEItemKey currentObservedKey = AEItemKey.of(currentObserved);
                if (currentObservedKey == null || !removeOne(currentRemainderCounter, currentObservedKey)) {
                    return Optional.empty();
                }
            }
            rebased.add(new Transition(
                currentInitial,
                currentObserved,
                currentDamage,
                transition.damageDelta(),
                transition.maxDamage(),
                transition.breakBehavior()
            ));
        }

        return sameCounter(sourceInputCounter, currentInputCounter)
                && sameCounter(sourceRemainderCounter, currentRemainderCounter)
            ? Optional.of(new ECODurabilityBatchModel(rebased))
            : Optional.empty();
    }

    List<GenericStack> initialEntries() {
        KeyCounter counter = new KeyCounter();
        for (Transition transition : transitions) {
            GenericStack initial = GenericStack.fromItemStack(transition.initialStack());
            if (initial != null) counter.add(initial.what(), 1L);
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    private static KeyCounter toCounter(List<GenericStack> stacks) {
        KeyCounter counter = new KeyCounter();
        if (stacks != null) {
            for (GenericStack stack : stacks) {
                if (stack == null || stack.what() == null || stack.amount() <= 0L) return new KeyCounter();
                counter.add(stack.what(), stack.amount());
            }
        }
        return counter;
    }

    private static boolean removeOne(KeyCounter counter, AEKey key) {
        if (counter.get(key) <= 0L) return false;
        counter.remove(key, 1L);
        counter.removeZeros();
        return true;
    }

    private static AEItemKey findMatchingDamageable(KeyCounter counter, ItemStack template) {
        for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey itemKey) || entry.getLongValue() <= 0L) continue;
            ItemStack candidate = itemKey.toStack(1);
            if (!candidate.isEmpty() && candidate.isDamageableItem()
                    && sameItemAndComponentsIgnoringDamage(template, candidate)) {
                return itemKey;
            }
        }
        return null;
    }

    private static boolean sameItemAndComponentsIgnoringDamage(ItemStack left, ItemStack right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()
                || !left.isDamageableItem() || !right.isDamageableItem()) return false;
        ItemStack normalizedLeft = left.copyWithCount(1);
        ItemStack normalizedRight = right.copyWithCount(1);
        normalizedLeft.setDamageValue(0);
        normalizedRight.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(normalizedLeft, normalizedRight);
    }

    private static boolean sameCounter(KeyCounter left, KeyCounter right) {
        for (var entry : left) {
            if (right.get(entry.getKey()) != entry.getLongValue()) return false;
        }
        for (var entry : right) {
            if (left.get(entry.getKey()) != entry.getLongValue()) return false;
        }
        return true;
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

    static long maxCraftsBeforeBreak(int initialDamage, int damageDelta, int maxDamage) {
        if (initialDamage < 0 || damageDelta <= 0 || maxDamage <= initialDamage) return 0L;
        long remaining = (long) maxDamage - initialDamage;
        return (remaining + damageDelta - 1L) / damageDelta;
    }
}
