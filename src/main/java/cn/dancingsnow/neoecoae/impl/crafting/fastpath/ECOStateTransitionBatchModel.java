package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Linear count/identity state for reusable, non-damageable container items. */
public final class ECOStateTransitionBatchModel implements ECOReusableStateModel {
    private final List<Transition> transitions;
    private final long maxBatchSize;

    private ECOStateTransitionBatchModel(List<Transition> transitions) {
        this.transitions = List.copyOf(transitions);
        long max = Long.MAX_VALUE;
        for (Transition transition : transitions) max = Math.min(max, transition.maxCrafts());
        this.maxBatchSize = max;
    }

    public static Optional<ECOStateTransitionBatchModel> analyze(List<ItemStack> before, List<ItemStack> after) {
        if (before.size() != after.size()) return Optional.empty();
        List<Transition> found = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            ItemStack initial = before.get(i);
            ItemStack result = after.get(i);
            if (initial == null || result == null || initial.isEmpty() || result.isEmpty()
                    || initial.isDamageableItem() || !ItemStack.isSameItem(initial, result)) continue;
            NumericCompoundDelta customDataDelta = null;
            if (!ItemStack.isSameItemSameComponents(initial, result)) {
                customDataDelta = NumericCompoundDelta.analyze(initial, result).orElse(null);
                if (customDataDelta == null) return Optional.empty();
            }
            long delta = (long) result.getCount() - initial.getCount();
            long maxCrafts = delta >= 0L ? Long.MAX_VALUE : initial.getCount() / -delta;
            found.add(new Transition(initial.copy(), result.copy(), delta, maxCrafts, customDataDelta));
        }
        return found.isEmpty() ? Optional.empty() : Optional.of(new ECOStateTransitionBatchModel(found));
    }

    @Override
    public FastPathCapability capability() {
        return FastPathCapability.STATE_TRANSITION_LINEAR;
    }

    @Override
    public long maxBatchSize() {
        return maxBatchSize;
    }

    @Override
    public List<GenericStack> batchInputs(List<GenericStack> ordinaryInputs, long crafts) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : ordinaryInputs) {
            if (!matchesInitial(stack.what())) counter.add(stack.what(), Math.multiplyExact(stack.amount(), crafts));
        }
        for (Transition transition : transitions) {
            GenericStack initial = GenericStack.fromItemStack(transition.initial.copyWithCount(1));
            counter.add(initial.what(), transition.initial.getCount());
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
            long finalCount = Math.addExact(transition.initial.getCount(),
                Math.multiplyExact(transition.countDelta, crafts));
            if (finalCount > 0L) {
                ItemStack finalStack = transition.apply(crafts);
                GenericStack result = GenericStack.fromItemStack(finalStack.copyWithCount(1));
                counter.add(result.what(), finalCount);
            }
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    @Override
    public boolean requiresSecondStepProof() {
        return transitions.stream().anyMatch(transition ->
            transition.countDelta != 0L || transition.customDataDelta != null);
    }

    @Override
    public boolean sameTransition(ECOReusableStateModel other) {
        if (!(other instanceof ECOStateTransitionBatchModel state)
                || transitions.size() != state.transitions.size()) return false;
        for (int i = 0; i < transitions.size(); i++) {
            Transition left = transitions.get(i);
            Transition right = state.transitions.get(i);
            Map<String, NumericDelta> leftDeltas = left.customDataDelta == null
                ? Map.of() : left.customDataDelta.deltas;
            Map<String, NumericDelta> rightDeltas = right.customDataDelta == null
                ? Map.of() : right.customDataDelta.deltas;
            if (left.countDelta != right.countDelta || !leftDeltas.equals(rightDeltas)
                    || !ItemStack.isSameItem(left.initial, right.initial)) return false;
        }
        return true;
    }

    private boolean matchesInitial(AEKey key) {
        return transitions.stream().anyMatch(transition ->
            GenericStack.fromItemStack(transition.initial.copyWithCount(1)).what().equals(key));
    }

    private boolean matchesObservedResult(AEKey key) {
        return transitions.stream().anyMatch(transition ->
            GenericStack.fromItemStack(transition.observedResult.copyWithCount(1)).what().equals(key));
    }

    ItemStack applyTransition(int index, long crafts) {
        return transitions.get(index).apply(crafts);
    }

    private record Transition(
        ItemStack initial,
        ItemStack observedResult,
        long countDelta,
        long maxCrafts,
        NumericCompoundDelta customDataDelta
    ) {
        private Transition {
            initial = initial.copy();
            observedResult = observedResult.copy();
        }

        private ItemStack apply(long crafts) {
            ItemStack result = initial.copyWithCount(1);
            if (customDataDelta != null) {
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataDelta.apply(crafts)));
            }
            return result;
        }
    }

    /** Only equal-shape compounds containing numeric leaves are admitted. */
    private record NumericCompoundDelta(CompoundTag initial, Map<String, NumericDelta> deltas) {
        private static Optional<NumericCompoundDelta> analyze(ItemStack initialStack, ItemStack resultStack) {
            ItemStack initialBase = initialStack.copyWithCount(1);
            ItemStack resultBase = resultStack.copyWithCount(1);
            CustomData initialData = initialBase.get(DataComponents.CUSTOM_DATA);
            CustomData resultData = resultBase.get(DataComponents.CUSTOM_DATA);
            initialBase.remove(DataComponents.CUSTOM_DATA);
            resultBase.remove(DataComponents.CUSTOM_DATA);
            if (!ItemStack.isSameItemSameComponents(initialBase, resultBase)
                    || initialData == null || resultData == null) return Optional.empty();

            return analyze(initialData.copyTag(), resultData.copyTag());
        }

        private static Optional<NumericCompoundDelta> analyze(CompoundTag before, CompoundTag after) {
            if (!before.getAllKeys().equals(after.getAllKeys())) return Optional.empty();
            Map<String, NumericDelta> deltas = new LinkedHashMap<>();
            for (String key : before.getAllKeys()) {
                Tag left = before.get(key);
                Tag right = after.get(key);
                if (!(left instanceof NumericTag leftNumber) || !(right instanceof NumericTag rightNumber)
                        || left.getId() != right.getId()) return Optional.empty();
                try {
                    NumericDelta delta = NumericDelta.of(leftNumber, rightNumber);
                    if (!Double.isFinite(delta.initialDouble()) || !Double.isFinite(delta.deltaDouble())) {
                        return Optional.empty();
                    }
                    deltas.put(key, delta);
                } catch (ArithmeticException overflow) {
                    return Optional.empty();
                }
            }
            return deltas.isEmpty() ? Optional.empty()
                : Optional.of(new NumericCompoundDelta(before.copy(), Map.copyOf(deltas)));
        }

        private CompoundTag apply(long crafts) {
            CompoundTag result = initial.copy();
            for (Map.Entry<String, NumericDelta> entry : deltas.entrySet()) {
                result.put(entry.getKey(), entry.getValue().apply(crafts));
            }
            return result;
        }
    }

    static boolean isUnchangedState(CompoundTag before, CompoundTag after) {
        return before.equals(after);
    }

    static Optional<CompoundTag> applyCustomDataTransition(
        CompoundTag before,
        CompoundTag after,
        long crafts
    ) {
        return NumericCompoundDelta.analyze(before, after).map(delta -> delta.apply(crafts));
    }

    private record NumericDelta(byte type, long initialLong, long deltaLong, double initialDouble, double deltaDouble) {
        private static NumericDelta of(NumericTag before, NumericTag after) {
            byte type = before.getId();
            if (type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE) {
                return new NumericDelta(type, 0L, 0L, before.getAsDouble(),
                    after.getAsDouble() - before.getAsDouble());
            }
            long initial = before.getAsLong();
            return new NumericDelta(type, initial, Math.subtractExact(after.getAsLong(), initial), 0.0D, 0.0D);
        }

        private NumericTag apply(long crafts) {
            long integral = type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE
                ? 0L : Math.addExact(initialLong, Math.multiplyExact(deltaLong, crafts));
            double floating = initialDouble + deltaDouble * crafts;
            return switch (type) {
                case Tag.TAG_BYTE -> ByteTag.valueOf((byte) checkedRange(integral, Byte.MIN_VALUE, Byte.MAX_VALUE));
                case Tag.TAG_SHORT -> ShortTag.valueOf((short) checkedRange(integral, Short.MIN_VALUE, Short.MAX_VALUE));
                case Tag.TAG_INT -> IntTag.valueOf((int) checkedRange(integral, Integer.MIN_VALUE, Integer.MAX_VALUE));
                case Tag.TAG_LONG -> LongTag.valueOf(integral);
                case Tag.TAG_FLOAT -> FloatTag.valueOf((float) checkedFinite(floating));
                case Tag.TAG_DOUBLE -> DoubleTag.valueOf(checkedFinite(floating));
                default -> throw new IllegalStateException("Unsupported numeric tag type " + type);
            };
        }

        private static long checkedRange(long value, long min, long max) {
            if (value < min || value > max) throw new ArithmeticException("Linear NBT transition overflow");
            return value;
        }

        private static double checkedFinite(double value) {
            if (!Double.isFinite(value)) throw new ArithmeticException("Linear NBT transition overflow");
            return value;
        }
    }
}
