package cn.dancingsnow.neoecoae.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record MenuDataRequest(int containerId, UUID session, UUID request, int kind) {
    public static void encode(MenuDataRequest value, FriendlyByteBuf buf) {
        buf.writeVarInt(value.containerId);
        buf.writeUUID(value.session);
        buf.writeUUID(value.request);
        buf.writeVarInt(value.kind);
    }

    public static MenuDataRequest decode(FriendlyByteBuf buf) {
        return new MenuDataRequest(buf.readVarInt(), buf.readUUID(), buf.readUUID(), buf.readVarInt());
    }

    public static void handle(MenuDataRequest value, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null
                    && player.containerMenu.containerId == value.containerId
                    && player.containerMenu instanceof NetworkMenu menu) {
                menu.neoecoae$dataSync().acceptRequest(value);
            }
        });
        context.setPacketHandled(true);
    }
}
