package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ECOForceCraftStartFlagC2SPacket(int containerId, boolean forceStart) implements CustomPacketPayload {
    public static final Type<ECOForceCraftStartFlagC2SPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "force_craft_start_flag"));
    public static final StreamCodec<FriendlyByteBuf, ECOForceCraftStartFlagC2SPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> { buf.writeVarInt(packet.containerId); buf.writeBoolean(packet.forceStart); },
            buf -> new ECOForceCraftStartFlagC2SPacket(buf.readVarInt(), buf.readBoolean()));

    public static void handle(ECOForceCraftStartFlagC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof CraftConfirmMenu menu
                    && menu.containerId == packet.containerId && menu.stillValid(player)
                    && menu instanceof ECOForceCraftStartSync sync) {
                sync.neoecoae$setForceCraftStart(packet.forceStart);
                menu.startJob();
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
