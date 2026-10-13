package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.core.format.BigNumberFormatter;
import java.math.BigInteger;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** One item row in the ECO crafting CPU status page. */
record ComputationCpuItemEntry(AEKey key, BigInteger stored, BigInteger active, BigInteger pending, boolean batched) {
    String amount(BigInteger value, boolean full) {
        String suffix = key.getUnitSymbol();
        return BigNumberFormatter.format(value, key.getAmountPerUnit(), full) + (suffix == null ? "" : " " + suffix);
    }

    CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("key", key.toTagGeneric(registries));
        tag.putByteArray("stored", stored.toByteArray());
        tag.putByteArray("active", active.toByteArray());
        tag.putByteArray("pending", pending.toByteArray());
        tag.putBoolean("batched", batched);
        return tag;
    }

    static ComputationCpuItemEntry read(CompoundTag tag, HolderLookup.Provider registries) {
        return new ComputationCpuItemEntry(
            AEKey.fromTagGeneric(registries, tag.getCompound("key")),
            new BigInteger(tag.getByteArray("stored")),
            new BigInteger(tag.getByteArray("active")),
            new BigInteger(tag.getByteArray("pending")),
            tag.getBoolean("batched"));
    }
}
