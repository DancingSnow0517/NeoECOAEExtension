package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.20.1 transport for planner status shown by the crafting confirmation screen. */
public final class ECOPlannerNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(NeoECOAE.id("planner"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private ECOPlannerNetwork() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        CHANNEL.registerMessage(
                0,
                ECOPlannerNoticePayload.class,
                ECOPlannerNoticePayload::encode,
                ECOPlannerNoticePayload::decode,
                ECOPlannerNoticePayload::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                1,
                ECOCycleDiagnosticsPayload.class,
                ECOCycleDiagnosticsPayload::encode,
                ECOCycleDiagnosticsPayload::decode,
                ECOCycleDiagnosticsPayload::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
