package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;
import com.lowdragmc.lowdraglib.networking.s2c.SPacketRPCMethodPayload;
import com.lowdragmc.lowdraglib.syncdata.rpc.RPCSender;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Carries the LDLib argument format over a registered C2S channel with block interaction checks. */
public final class ManagedRpcC2SPacket implements IPacket {
    private BlockPos pos;
    private SPacketRPCMethodPayload payload;

    public ManagedRpcC2SPacket() {}

    ManagedRpcC2SPacket(NEManagedBlockEntity host, String method, Object[] args) {
        pos = host.getCurrentPos();
        payload = host.generateRpcPacket(host, method, args);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        payload.encode(buffer);
    }

    @Override
    public void decode(FriendlyByteBuf buffer) {
        pos = buffer.readBlockPos();
        payload = new SPacketRPCMethodPayload(buffer);
    }

    @Override
    public void execute(IHandlerContext context) {
        ServerPlayer player = context.getPlayer();
        if (context.isClient() || player == null || player.isSpectator()
            || !player.level().hasChunkAt(pos) || player.distanceToSqr(pos.getCenter()) > 64
            || !player.containerMenu.stillValid(player)) return;
        var blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof NEManagedBlockEntity && !blockEntity.isRemoved()) {
            // Invoke against the checked position, never the separately encoded payload position.
            payload.processPacket(blockEntity, RPCSender.ofClient(player));
        }
    }
}
