package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.core.io.AmountMapCodec;

import appeng.api.stacks.AEKey;
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
    private static final String ENTRIES = "entries";

    private AmountMapNbt() {}

    public static ListTag write(Map<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        var root = new CompoundTag();
        AmountMapCodec.write(new NbtData(root, registries), ENTRIES, amounts);
        return root.getList(ENTRIES, net.minecraft.nbt.Tag.TAG_COMPOUND);
    }

    /** An unresolvable key, a non-positive amount, or a malformed amount string throws IllegalArgumentException. */
    public static void read(ListTag list, HolderLookup.Provider registries, Map<AEKey, BigInteger> target) {
        var root = new CompoundTag();
        root.put(ENTRIES, list);
        AmountMapCodec.read(new NbtData(root, registries), ENTRIES, target);
    }
}
