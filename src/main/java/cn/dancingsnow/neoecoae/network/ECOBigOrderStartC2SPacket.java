package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Contains intent only; the server resolves the result, target and CPU from the open menu. */
public record ECOBigOrderStartC2SPacket(int containerId, boolean forced) implements CustomPacketPayload {
    public static final Type<ECOBigOrderStartC2SPacket> TYPE = new Type<>(NeoECOAE.id("big_order_start"));
    public static final StreamCodec<FriendlyByteBuf, ECOBigOrderStartC2SPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> { buf.writeVarInt(packet.containerId); buf.writeBoolean(packet.forced); },
        buf -> new ECOBigOrderStartC2SPacket(buf.readVarInt(), buf.readBoolean()));
    public static void handle(ECOBigOrderStartC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof CraftConfirmMenu menu
                    && menu.containerId == packet.containerId && menu.stillValid(context.player())
                    && menu instanceof ECOCraftConfirmMenuMode mode)
                mode.neoecoae$startBigOrder(packet.forced);
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
