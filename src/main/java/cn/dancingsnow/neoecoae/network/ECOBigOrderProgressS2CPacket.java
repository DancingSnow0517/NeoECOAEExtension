package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderState;
import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrder;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.bigorder.*;
import cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record ECOBigOrderProgressS2CPacket(int containerId, int cpuSerial, @Nullable ECOBigOrderProgress progress)
        implements CustomPacketPayload {
    public static final Type<ECOBigOrderProgressS2CPacket> TYPE = new Type<>(NeoECOAE.id("big_order_progress"));
    public static final StreamCodec<FriendlyByteBuf, ECOBigOrderProgressS2CPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeVarInt(packet.cpuSerial);
            var p = packet.progress;
            buf.writeBoolean(p != null);
            if (p == null) return;
            buf.writeUUID(p.orderId());
            buf.writeEnum(p.state());
            ExactMapSync.writeInteger(buf, p.requested());
            ExactMapSync.writeInteger(buf, p.completed());
            ExactMapSync.writeInteger(buf, p.remaining());
            buf.writeVarLong(p.childTarget());
            buf.writeVarLong(p.childRemaining());
            buf.writeUtf(p.waitingReason(), 256);
        },
        buf -> {
            int menu = buf.readVarInt();
            int serial = buf.readVarInt();
            ECOBigOrderProgress p = null;
            if (buf.readBoolean()) p = new ECOBigOrderProgress(buf.readUUID(), buf.readEnum(ECOBigOrderState.class),
                ExactMapSync.readInteger(buf),
                ExactMapSync.readInteger(buf),
                ExactMapSync.readInteger(buf),
                buf.readVarLong(), buf.readVarLong(), buf.readUtf(256));
            return new ECOBigOrderProgressS2CPacket(menu, serial, p);
        });
    public static void handle(ECOBigOrderProgressS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var menu = context.player().containerMenu;
            if (menu.containerId == packet.containerId && menu instanceof ECOBigOrderStatusHost host)
                host.neoecoae$setBigOrderProgress(packet.cpuSerial, packet.progress);
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
