package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraftforge.fml.ModList;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;
import cn.dancingsnow.neoecoae.platform.network.NEStreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class ECONetwork {
    private static SimpleChannel channel;
    private ECONetwork() {}

    public static void register() {
        // LDLib menu bindings can change between releases without changing our payload codecs.
        // Require the same ECO release before entering play, where those menus can be opened.
        String modVersion = ModList.get().getModContainerById(NeoECOAE.MOD_ID)
            .orElseThrow().getModInfo().getVersion().toString();
        String protocol = protocolVersion(modVersion);
        channel = NetworkRegistry.newSimpleChannel(NeoECOAE.id("network"), () -> protocol,
            protocol::equals, protocol::equals);
        register(0, ECOMenuChunkS2CPacket.class, NetworkDirection.PLAY_TO_CLIENT,
            ECOMenuChunkS2CPacket.STREAM_CODEC, ECOMenuChunkS2CPacket::handle);
        register(1, ECOBigOrderStartC2SPacket.class, NetworkDirection.PLAY_TO_SERVER,
            ECOBigOrderStartC2SPacket.STREAM_CODEC, ECOBigOrderStartC2SPacket::handle);
        register(2, ECOBigOrderProgressS2CPacket.class, NetworkDirection.PLAY_TO_CLIENT,
            ECOBigOrderProgressS2CPacket.STREAM_CODEC, ECOBigOrderProgressS2CPacket::handle);
        register(3, ECOForceCraftStartFlagC2SPacket.class, NetworkDirection.PLAY_TO_SERVER,
            ECOForceCraftStartFlagC2SPacket.STREAM_CODEC, ECOForceCraftStartFlagC2SPacket::handle);
        register(4, ECOImportJeiBookmarksC2SPacket.class, NetworkDirection.PLAY_TO_SERVER,
            ECOImportJeiBookmarksC2SPacket.STREAM_CODEC, ECOImportJeiBookmarksC2SPacket::handle);
        register(5, ECOCompressionSyncS2CPacket.class, NetworkDirection.PLAY_TO_CLIENT,
            ECOCompressionSyncS2CPacket.STREAM_CODEC, ECOCompressionSyncS2CPacket::handle);
    }

    private static <P> void register(int id, Class<P> type, NetworkDirection direction,
            NEStreamCodec<FriendlyByteBuf, P> codec, BiConsumer<P, NEPacketContext> handler) {
        channel.registerMessage(id, type, (packet, buffer) -> codec.encode(buffer, packet), codec::decode,
            (packet, supplier) -> {
                NetworkEvent.Context context = supplier.get();
                handler.accept(packet, new NEPacketContext(context));
                context.setPacketHandled(true);
            }, Optional.of(direction));
    }

    public static void sendToServer(Object packet) { channel.sendToServer(packet); }
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void sendToAllPlayers(Object packet) {
        channel.send(PacketDistributor.ALL.noArg(), packet);
    }

    static String protocolVersion(String modVersion) {
        return "4/" + modVersion;
    }
}
