package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests ECO planning for the currently visible confirmation menu only. */
public record ECOPlanRequestPayload(int containerId) implements CustomPacketPayload {
    public static final Type<ECOPlanRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "eco_plan_request"));
    public static final StreamCodec<FriendlyByteBuf, ECOPlanRequestPayload> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> buf.writeVarInt(packet.containerId),
        buf -> new ECOPlanRequestPayload(buf.readVarInt()));

    public static void handle(ECOPlanRequestPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftConfirmMenu menu)
                    || menu.containerId != packet.containerId()
                    || !((Object) menu instanceof ECOCraftConfirmMenuMode mode)) {
                return;
            }
            mode.neoecoae$startEcoPlanning();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
