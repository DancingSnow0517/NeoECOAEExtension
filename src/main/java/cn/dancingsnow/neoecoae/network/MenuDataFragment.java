package cn.dancingsnow.neoecoae.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record MenuDataFragment(
        int containerId, UUID session, UUID request, int kind, int total, int offset, byte[] data) {
    public static void encode(MenuDataFragment value, FriendlyByteBuf buf) {
        if (value.data.length > BoundedData.PART_BYTES) throw new IllegalArgumentException("Fragment exceeds budget");
        buf.writeVarInt(value.containerId);
        buf.writeUUID(value.session);
        buf.writeUUID(value.request);
        buf.writeVarInt(value.kind);
        buf.writeVarInt(value.total);
        buf.writeVarInt(value.offset);
        buf.writeByteArray(value.data);
    }

    public static MenuDataFragment decode(FriendlyByteBuf buf) {
        return new MenuDataFragment(
                buf.readVarInt(),
                buf.readUUID(),
                buf.readUUID(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray(BoundedData.PART_BYTES));
    }

    public static void handle(MenuDataFragment value, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> Client.apply(value));
        context.setPacketHandled(true);
    }

    private static final class Client {
        static void apply(MenuDataFragment value) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null
                    && player.containerMenu.containerId == value.containerId
                    && player.containerMenu instanceof NetworkMenu menu) {
                menu.neoecoae$dataSync().receive(player.containerMenu, menu, value);
            }
        }
    }
}
