package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmount;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmountClientCache;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ECOExactAmountsS2CPacket(int containerId, Map<AEKey, ExactAmount> amounts)
    implements CustomPacketPayload {
    public static final Type<ECOExactAmountsS2CPacket> TYPE = new Type<>(NeoECOAE.id("terminal_exact_amounts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOExactAmountsS2CPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeVarInt(packet.amounts.size());
            packet.amounts.forEach((key, amount) -> {
                AEKey.writeKey(buf, key);
                buf.writeBoolean(amount.infinite());
                if (!amount.infinite()) buf.writeUtf(amount.value().toString(), 1024);
            });
        },
        buf -> {
            int containerId = buf.readVarInt();
            int size = buf.readVarInt();
            if (size < 0 || size > 100_000) throw new IllegalArgumentException("Invalid exact amount count: " + size);
            Map<AEKey, ExactAmount> amounts = new HashMap<>();
            for (int i = 0; i < size; i++) {
                AEKey key = AEKey.readKey(buf);
                boolean infinite = buf.readBoolean();
                amounts.put(key, infinite ? ExactAmount.unbounded()
                    : ExactAmount.finite(new BigInteger(buf.readUtf(1024))));
            }
            return new ECOExactAmountsS2CPacket(containerId, amounts);
        });

    public static void handle(ECOExactAmountsS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ExactAmountClientCache.replace(packet.containerId, packet.amounts));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
