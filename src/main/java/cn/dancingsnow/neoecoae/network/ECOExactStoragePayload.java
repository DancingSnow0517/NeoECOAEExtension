package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record ECOExactStoragePayload(int containerId, Map<AEKey, BigInteger> amounts) {
    public ECOExactStoragePayload {
        amounts = Map.copyOf(amounts);
    }

    public static void encode(ECOExactStoragePayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.amounts().size());
        payload.amounts().forEach((key, amount) -> {
            AEKey.writeKey(buffer, key);
            buffer.writeByteArray(amount.toByteArray());
        });
    }

    public static ECOExactStoragePayload decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > 65536) throw new IllegalArgumentException("Invalid exact inventory size");
        Map<AEKey, BigInteger> amounts = new HashMap<>();
        for (int i = 0; i < size; i++) {
            AEKey key = AEKey.readKey(buffer);
            BigInteger amount = new BigInteger(buffer.readByteArray(65536));
            if (key == null || amount.signum() <= 0)
                throw new IllegalArgumentException("Invalid exact inventory entry");
            amounts.put(key, amount);
        }
        return new ECOExactStoragePayload(containerId, amounts);
    }

    public static void handle(ECOExactStoragePayload payload, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> Client.apply(payload));
        context.setPacketHandled(true);
    }

    private static final class Client {
        private static void apply(ECOExactStoragePayload payload) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null
                    && player.containerMenu.containerId == payload.containerId()
                    && player.containerMenu instanceof ECOExactStorageMenu menu) {
                menu.neoecoae$setExactAmounts(payload.amounts());
            }
        }
    }
}
