package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEStorageControllerScreen;
import net.minecraft.client.Minecraft;
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
 * Network channel for Storage Controller UI state sync (S2C only).
 */
public final class NENetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(NeoECOAE.MOD_ID, "storage_ui"),
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
                () -> () -> handleClient(pkt)));
            ctx.setPacketHandled(true);
        }

        private static void handleClient(NEStorageUiStatePacket pkt) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof NEStorageControllerScreen screen) {
                if (screen.getMenu().getMachinePos().equals(pkt.state().pos())) {
                    screen.setStorageUiState(pkt.state());
                }
            }
        }
    }
}
