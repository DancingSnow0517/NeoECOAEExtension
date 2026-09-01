package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Conservative slot-local model for reusable/damageable crafting inputs. */
public final class ECODurabilityBatchModel {
    private final List<Transition> transitions;
    private final long maxBatchSize;

    private ECODurabilityBatchModel(List<Transition> transitions) {
        this.transitions = List.copyOf(transitions);
        long max = Long.MAX_VALUE;
        for (Transition t : transitions) max = Math.min(max, t.maxCrafts());
        this.maxBatchSize = max;
    }

    public static Optional<ECODurabilityBatchModel> analyze(List<ItemStack> before, List<ItemStack> after) {
        if (before.size() != after.size()) return Optional.empty();
        List<Transition> found = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            ItemStack in = before.get(i);
            ItemStack out = after.get(i);
            if (in.isEmpty()) continue;
            if (out.isEmpty()) {
                // A damageable tool that is consumed rather than returned is an ordinary per-craft input. It
                // needs no reusable-state model: multiplying that input is exactly the physical contract.
                continue;
            }
            if (!ItemStack.isSameItem(in, out)) continue;
            if (!ItemStack.isSameItemSameComponents(in, out) && !in.isDamageableItem()) {
                // Non-damageable component transitions may encode arbitrary recipe state. Durability-bearing
                // stacks are safe to extrapolate from their monotonic damage delta; the remaining components
                // are carried by the final stack emitted by the batch model.
                continue;
            }
            if (!in.isDamageableItem()) {
                found.add(new Transition(in.copyWithCount(1), out.copyWithCount(1), 0, Long.MAX_VALUE));
                continue;
            }
            int delta = out.getDamageValue() - in.getDamageValue();
            if (delta < 0) return Optional.empty();
            found.add(new Transition(in.copyWithCount(1), out.copyWithCount(1), delta,
                delta == 0 ? Long.MAX_VALUE : (in.getMaxDamage() - in.getDamageValue()) / delta));
        }
        return found.isEmpty() ? Optional.empty() : Optional.of(new ECODurabilityBatchModel(found));
    }

    public long maxBatchSize() { return maxBatchSize; }

    public List<GenericStack> batchInputs(List<GenericStack> ordinaryInputs, long crafts) {
        KeyCounter c = new KeyCounter();
        for (GenericStack s : ordinaryInputs) {
            boolean reusable = transitions.stream().anyMatch(t -> GenericStack.fromItemStack(t.initial).what().equals(s.what()));
            if (!reusable) c.add(s.what(), Math.multiplyExact(s.amount(), crafts));
        }
        for (Transition t : transitions) c.add(GenericStack.fromItemStack(t.initial).what(), 1);
        return ECOFastPathStacks.copyCounter(c);
    }

    public List<GenericStack> batchRemainders(List<GenericStack> ordinaryRemainders, long crafts) {
        KeyCounter c = new KeyCounter();
        for (GenericStack s : ordinaryRemainders) {
            boolean reusable = transitions.stream().anyMatch(t -> t.finalStack != null
                && GenericStack.fromItemStack(t.finalStack).what().equals(s.what()));
            if (!reusable) c.add(s.what(), Math.multiplyExact(s.amount(), crafts));
        }
        for (Transition t : transitions) {
            if (t.finalStack != null && crafts <= t.maxCrafts()) {
                c.add(GenericStack.fromItemStack(t.finalStack.copyWithCount(1)).what(), 1);
            }
        }
        return ECOFastPathStacks.copyCounter(c);
    }

    private record Transition(ItemStack initial, ItemStack finalStack, int delta, long maxCrafts) { }
}
