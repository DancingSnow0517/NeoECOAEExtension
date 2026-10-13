package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrder;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.math.BigInteger;
import java.util.Map;

/**
 * Persists {@code Map<AEKey, BigInteger>} as a list of {@code {key: <GenericStack tag>, amount: "<decimal>"}}.
 * Saved worlds depend on these tag names and the decimal string encoding; do not change them.
 */
public final class AmountMapNbt {
    private static final String KEY = "key";
    private static final String AMOUNT = "amount";

    public enum InvalidEntryPolicy {
        /** Drops entries whose key does not resolve, without parsing their amount. Zero amounts are kept. */
        SKIP,
        /** An unresolvable key or a non-positive amount throws IllegalArgumentException. */
        THROW
    }

    private AmountMapNbt() {}

    public static ListTag write(Map<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        var list = new ListTag();
        amounts.forEach((key, amount) -> {
            var entry = new CompoundTag();
            entry.put(KEY, GenericStack.writeTag(registries, new GenericStack(key, 1)));
            entry.putString(AMOUNT, amount.toString());
            list.add(entry);
        });
        return list;
    }

    /** Malformed amount strings always throw; {@code policy} only decides how unresolved keys and zero amounts are treated. */
    public static void read(ListTag list, HolderLookup.Provider registries, Map<AEKey, BigInteger> target,
            InvalidEntryPolicy policy) {
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            var stack = GenericStack.readTag(registries, entry.getCompound(KEY));
            if (stack == null) {
                if (policy == InvalidEntryPolicy.THROW) throw new IllegalArgumentException("Invalid deferred material");
                continue;
            }
            var amount = ECOBigCraftingOrder.decode(entry.getString(AMOUNT));
            if (policy == InvalidEntryPolicy.THROW && amount.signum() <= 0) {
                throw new IllegalArgumentException("Invalid deferred material");
            }
            target.put(stack.what(), amount);
        }
    }
}
