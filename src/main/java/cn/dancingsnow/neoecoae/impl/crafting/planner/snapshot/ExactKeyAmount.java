package cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExactCycleAmount;
import java.math.BigInteger;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Packet-safe arbitrary-precision key amount used only by planner diagnostics. */
public record ExactKeyAmount(AEKey key, ExactCycleAmount amount) {
    private static final int MAX_INTEGER_BYTES = 4096;

    static ExactKeyAmount read(RegistryFriendlyByteBuf data) {
        return new ExactKeyAmount(AEKey.readKey(data), new ExactCycleAmount(readBigInteger(data)));
    }

    void write(RegistryFriendlyByteBuf data) {
        AEKey.writeKey(data, key);
        byte[] bytes = amount.value().toByteArray();
        if (bytes.length > MAX_INTEGER_BYTES) throw new IllegalArgumentException("Exact cycle amount is too large");
        data.writeByteArray(bytes);
    }

    private static BigInteger readBigInteger(RegistryFriendlyByteBuf data) {
        byte[] bytes = data.readByteArray(MAX_INTEGER_BYTES);
        if (bytes.length == 0) throw new IllegalArgumentException("Empty exact cycle amount");
        return new BigInteger(bytes);
    }
}
