package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.util.NEMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
    public long maxBatchSize() {
        return maxBatchSize;
    }

    /**
     * A single durability slot may be backed by a pool of concrete tools with different damage values. Multiple
     * stateful slots are deliberately left on the original per-tool path because their pools would need a
     * cross-slot matching proof.
     */
    boolean supportsToolPool() {
        return transitions.size() == 1 && transitions.getFirst().damageDelta() > 0;
    }

    @Nullable
    ToolPoolBatch prepareToolPoolBatch(
        ListCraftingInventory inventory,
        List<GenericStack> ordinaryInputs,
        List<GenericStack> ordinaryRemainders,
        long requestedCrafts
    ) {
        if (!supportsToolPool() || requestedCrafts <= 0L) return null;
        Transition transition = transitions.getFirst();

        KeyCounter consumedPerCraft = toCounter(ordinaryInputs);
        AEItemKey resolvedTool = AEItemKey.of(transition.initialStack());
        if (resolvedTool == null || !removeOne(consumedPerCraft, resolvedTool)) return null;
        List<GenericStack> ordinaryConsumed = ECOFastPathStacks.copyCounter(consumedPerCraft);
        long crafts = ECOBatchCraftingHelper.maxCraftsFromInventory(
            inventory, ordinaryConsumed, requestedCrafts);
        if (crafts <= 0L) return null;

        int toolEntryLimit = Math.max(1,
            ECOBatchCraftingHelper.MAX_BATCH_STACK_ENTRIES - ordinaryConsumed.size());
        List<ToolStock> tools = collectToolStock(inventory, transition, toolEntryLimit);
        long availableUses = 0L;
        for (ToolStock tool : tools) {
            availableUses = NEMath.saturatingAdd(availableUses,
                NEMath.saturatingMultiply(tool.count(), tool.craftsPerTool()));
        }
        crafts = Math.min(crafts, availableUses);
        if (crafts <= 0L) return null;

        KeyCounter totalInputs = new KeyCounter();
        for (GenericStack input : ordinaryConsumed) {
            totalInputs.add(input.what(), Math.multiplyExact(input.amount(), crafts));
        }
        KeyCounter totalRemainders = toCounter(ordinaryRemainders);
        if (!transition.observedResult().isEmpty()) {
            AEItemKey observed = AEItemKey.of(transition.observedResult());
            if (observed == null || !removeOne(totalRemainders, observed)) return null;
        }
        totalRemainders = multiplyCounter(totalRemainders, crafts);

        long unallocatedUses = crafts;
        for (ToolStock tool : tools) {
            if (unallocatedUses <= 0L) break;
            long stockUses = NEMath.saturatingMultiply(tool.count(), tool.craftsPerTool());
            long assignedUses = Math.min(unallocatedUses, stockUses);
            long fullTools = assignedUses / tool.craftsPerTool();
            long partialUses = assignedUses % tool.craftsPerTool();
            long toolsTaken = fullTools + (partialUses > 0L ? 1L : 0L);
            totalInputs.add(tool.key(), toolsTaken);

            // A fully-used tool reaches or crosses maxDamage and disappears. Only the final partially-used tool
            // survives, carrying the exact damage state that the worker must return to the CPU.
            if (partialUses > 0L) {
                ItemStack remainder = tool.stack().copyWithCount(1);
                long finalDamage = Math.addExact(remainder.getDamageValue(),
                    Math.multiplyExact((long) transition.damageDelta(), partialUses));
                if (finalDamage >= transition.maxDamage() || finalDamage > Integer.MAX_VALUE) return null;
                remainder.setDamageValue((int) finalDamage);
                GenericStack generic = GenericStack.fromItemStack(remainder);
                if (generic == null || generic.amount() <= 0L) return null;
                totalRemainders.add(generic.what(), 1L);
            }
            unallocatedUses -= assignedUses;
        }
        if (unallocatedUses != 0L) return null;
        return new ToolPoolBatch(
            crafts,
            ECOFastPathStacks.copyCounter(totalInputs),
            ECOFastPathStacks.copyCounter(totalRemainders)
        );
    }

    private static List<ToolStock> collectToolStock(
        ListCraftingInventory inventory,
        Transition transition,
        int entryLimit
    ) {
        List<ToolStock> result = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : inventory.list) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0L) continue;
            ItemStack stack = key.toStack(1);
            if (stack.isEmpty() || stack.getMaxDamage() != transition.maxDamage()
                    || !sameItemAndComponentsIgnoringDamage(transition.initialStack(), stack)) continue;
            long craftsPerTool = maxCraftsBeforeBreak(
                stack.getDamageValue(), transition.damageDelta(), transition.maxDamage());
            if (craftsPerTool > 0L) {
                result.add(new ToolStock(key, stack, entry.getLongValue(), craftsPerTool));
            }
        }
        // Consume the most damaged tools first. Besides being deterministic, this collapses fragmented durability
        // stock instead of leaving a growing collection of almost-broken AE keys in the CPU.
        result.sort(Comparator
            .comparingInt((ToolStock stock) -> stock.stack().getDamageValue()).reversed()
            .thenComparing(stock -> ECOFastPathStacks.keySortId(stock.key())));
        return result.size() <= entryLimit ? List.copyOf(result) : List.copyOf(result.subList(0, entryLimit));
    }

    private static KeyCounter multiplyCounter(KeyCounter source, long multiplier) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) {
            result.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
        }
        return result;
    }

    record ToolPoolBatch(long craftCount, List<GenericStack> inputs, List<GenericStack> remainders) {
        ToolPoolBatch {
            if (craftCount <= 0L) throw new IllegalArgumentException("craftCount must be positive");
            inputs = List.copyOf(inputs);
            remainders = List.copyOf(remainders);
        }
    }

    private record ToolStock(AEItemKey key, ItemStack stack, long count, long craftsPerTool) {}

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
