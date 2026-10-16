package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.core.amount.ExactAmount;
import cn.dancingsnow.neoecoae.core.network.ExactAmountPacketCodec;
import cn.dancingsnow.neoecoae.platform.network.MinecraftPacketBuf;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Shared binary exact-amount delta codec. The enclosing stream provides atomic delivery. */
public final class ExactMapSync {
    private ExactMapSync() {}

    public static void write(RegistryFriendlyByteBuf buf, MapDelta<AEKey, ExactAmount> delta) {
        ExactAmountPacketCodec.write(new MinecraftPacketBuf(buf), delta.updates(), delta.removed());
    }

    public static MapDelta<AEKey, ExactAmount> read(RegistryFriendlyByteBuf buf) {
        var delta = ExactAmountPacketCodec.read(new MinecraftPacketBuf(buf));
        return new MapDelta<>(delta.updates(), delta.removed());
    }

    public static void writeInteger(net.minecraft.network.FriendlyByteBuf buf, BigInteger value) {
        ExactAmountPacketCodec.writeInteger(new MinecraftPacketBuf(buf), value);
    }

    public static BigInteger readInteger(net.minecraft.network.FriendlyByteBuf buf) {
        return ExactAmountPacketCodec.readInteger(new MinecraftPacketBuf(buf));
    }

    /** Length-prefixed two's-complement bytes with no sign check; for values that may legitimately be negative. */
    public static void writeSignedInteger(net.minecraft.network.FriendlyByteBuf buf, BigInteger value) {
        ExactAmountPacketCodec.writeSignedInteger(new MinecraftPacketBuf(buf), value);
    }

    public static BigInteger readSignedInteger(net.minecraft.network.FriendlyByteBuf buf) {
        return ExactAmountPacketCodec.readSignedInteger(new MinecraftPacketBuf(buf));
    }

    public static Map<AEKey, ExactAmount> finite(Map<AEKey, BigInteger> amounts) {
        Map<AEKey, ExactAmount> result = new HashMap<>();
        amounts.forEach((key, value) -> result.put(key, ExactAmount.finite(value)));
        return result;
    }

    public static Map<AEKey, BigInteger> integers(Map<AEKey, ExactAmount> amounts) {
        Map<AEKey, BigInteger> result = new HashMap<>();
        amounts.forEach((key, value) -> {
            if (value.infinite()) throw new IllegalArgumentException("Infinite CPU quantity");
            result.put(key, value.value());
        });
        return result;
    }

}
