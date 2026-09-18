package cn.dancingsnow.neoecoae.api.me.menu;

import appeng.api.stacks.AEKey;
import appeng.menu.guisync.PacketWritable;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Exact display-only quantities, scoped to the selected CPU menu. */
public record ECOExactPending(Map<AEKey, BigInteger> amounts) implements PacketWritable {
    public ECOExactPending { amounts = Map.copyOf(amounts); }
    public ECOExactPending(RegistryFriendlyByteBuf buf) { this(read(buf)); }
    private static Map<AEKey, BigInteger> read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 100000) throw new IllegalArgumentException("Invalid preview size");
        var result = new java.util.HashMap<AEKey, BigInteger>();
        for (int i = 0; i < size; i++) result.put(AEKey.readKey(buf),
            new BigInteger(buf.readByteArray(4096)));
        return result;
    }
    @Override public void writeToPacket(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(amounts.size());
        amounts.forEach((key, amount) -> { AEKey.writeKey(buf, key); buf.writeByteArray(amount.toByteArray()); });
    }
}
