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

import java.util.ArrayList;
import java.util.List;
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
        registerS2C(NEStorageUiStatePacket.class,
            NEStorageUiStatePacket::encode,
            NEStorageUiStatePacket::decode,
            NEStorageUiStatePacket::handle);

        registerS2C(NEComputationUiStatePacket.class,
            NEComputationUiStatePacket::encode,
            NEComputationUiStatePacket::decode,
            NEComputationUiStatePacket::handle);
    }

    /**
     * Registers a PLAY_TO_CLIENT packet with an auto-incrementing id.
     * <p>
     * Keeps the {@link #register()} method readable as more machine UI
     * state packets are added in future phases.
     * </p>
     */
    @SuppressWarnings("SameParameterValue")
    private static <MSG> void registerS2C(Class<MSG> clazz,
                                           java.util.function.BiConsumer<MSG, FriendlyByteBuf> encoder,
                                           java.util.function.Function<FriendlyByteBuf, MSG> decoder,
                                           java.util.function.BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(
            packetId++, clazz, encoder, decoder, handler,
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
            buf.writeLong(s.storedEnergy());
            buf.writeLong(s.maxEnergy());
            buf.writeBoolean(s.formed());

            List<NEStorageUiTypeState> types = s.typeStates();
            buf.writeVarInt(types.size());
            for (NEStorageUiTypeState ts : types) {
                buf.writeResourceLocation(ts.typeId());
                buf.writeUtf(ts.displayName(), 128);
                buf.writeLong(ts.usedTypes());
                buf.writeLong(ts.totalTypes());
                buf.writeLong(ts.usedBytes());
                buf.writeLong(ts.totalBytes());
            }
        }

        public static NEStorageUiStatePacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            long storedEnergy = buf.readLong();
            long maxEnergy = buf.readLong();
            boolean formed = buf.readBoolean();

            int typeCount = buf.readVarInt();
            List<NEStorageUiTypeState> typeStates = new ArrayList<>(typeCount);
            for (int i = 0; i < typeCount; i++) {
                ResourceLocation typeId = buf.readResourceLocation();
                String displayName = buf.readUtf(128);
                long usedTypes = buf.readLong();
                long totalTypes = buf.readLong();
                long usedBytes = buf.readLong();
                long totalBytes = buf.readLong();
                typeStates.add(new NEStorageUiTypeState(typeId, displayName,
                    usedTypes, totalTypes, usedBytes, totalBytes));
            }

            return new NEStorageUiStatePacket(
                new NEStorageUiState(pos, typeStates, storedEnergy, maxEnergy, formed)
            );
        }

        public static void handle(NEStorageUiStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> NEClientUiPacketHandlers.handleStorageUiState(pkt)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * S2C packet carrying a {@link NEComputationUiState} snapshot.
     * <p>
     * Encoder/decoder are common-safe. Client-side handling is delegated to
     * {@link NEClientUiPacketHandlers} via {@link DistExecutor} so that the
     * dedicated server never loads screen classes.
     * </p>
     */
    public record NEComputationUiStatePacket(NEComputationUiState state) {

        public static void encode(NEComputationUiStatePacket pkt, FriendlyByteBuf buf) {
            NEComputationUiState s = pkt.state();
            buf.writeBlockPos(s.pos());
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.active());
            buf.writeInt(s.usedThreads());
            buf.writeInt(s.maxThreads());
            buf.writeLong(s.availableStorage());
            buf.writeLong(s.totalStorage());
            buf.writeInt(s.parallelCount());
            buf.writeInt(s.accelerators());
        }

        public static NEComputationUiStatePacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean formed = buf.readBoolean();
            boolean active = buf.readBoolean();
            int usedThreads = buf.readInt();
            int maxThreads = buf.readInt();
            long availableStorage = buf.readLong();
            long totalStorage = buf.readLong();
            int parallelCount = buf.readInt();
            int accelerators = buf.readInt();

            return new NEComputationUiStatePacket(
                new NEComputationUiState(pos, formed, active, usedThreads, maxThreads,
                    availableStorage, totalStorage, parallelCount, accelerators)
            );
        }

        public static void handle(NEComputationUiStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> NEClientUiPacketHandlers.handleComputationUiState(pkt)));
            ctx.setPacketHandled(true);
        }
    }
}
