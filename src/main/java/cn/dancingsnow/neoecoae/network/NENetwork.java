package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Unified mod network channel for UI state sync and future packets.
 * <p>
 * All machine UI S2C packets (Storage, Computation, Crafting, IWS, etc.)
 * share this single channel. New packet types are registered with an
 * incrementing index via {@link #register()}.
 * </p>
 */
public final class NENetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(NeoECOAE.MOD_ID, "ui"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
            packetId++,
            NEStorageUiStatePacket.class,
            NEStorageUiStatePacket::encode,
            NEStorageUiStatePacket::decode,
            NEStorageUiStatePacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    /**
     * S2C packet carrying a {@link NEStorageUiState} snapshot.
     * <p>
     * Encoder/decoder are common-safe. Client-side handling is delegated to
     * {@link NEClientUiPacketHandlers} via {@link DistExecutor} so that the
     * dedicated server never loads screen classes.
     * </p>
     */
    public record NEStorageUiStatePacket(NEStorageUiState state) {

        public static void encode(NEStorageUiStatePacket pkt, FriendlyByteBuf buf) {
            NEStorageUiState s = pkt.state();
            buf.writeBlockPos(s.pos());
            buf.writeLong(s.usedTypes());
            buf.writeLong(s.totalTypes());
            buf.writeLong(s.usedBytes());
            buf.writeLong(s.totalBytes());
            buf.writeLong(s.storedEnergy());
            buf.writeLong(s.maxEnergy());
            buf.writeBoolean(s.formed());
        }

        public static NEStorageUiStatePacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            long usedTypes = buf.readLong();
            long totalTypes = buf.readLong();
            long usedBytes = buf.readLong();
            long totalBytes = buf.readLong();
            long storedEnergy = buf.readLong();
            long maxEnergy = buf.readLong();
            boolean formed = buf.readBoolean();
            return new NEStorageUiStatePacket(
                new NEStorageUiState(pos, usedTypes, totalTypes, usedBytes, totalBytes,
                    storedEnergy, maxEnergy, formed)
            );
        }

        public static void handle(NEStorageUiStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> NEClientUiPacketHandlers.handleStorageUiState(pkt)));
            ctx.setPacketHandled(true);
        }
    }
}
