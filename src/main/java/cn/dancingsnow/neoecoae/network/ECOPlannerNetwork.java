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
    private static final String PROTOCOL = "3";
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
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ECOPlannerNetwork::onMenuClosed);
        CHANNEL.registerMessage(
                1,
                MenuDataFragment.class,
                MenuDataFragment::encode,
                MenuDataFragment::decode,
                MenuDataFragment::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                2,
                MenuDataRequest.class,
                MenuDataRequest::encode,
                MenuDataRequest::decode,
                MenuDataRequest::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                0,
                ECOCpuOverlayPayload.class,
                ECOCpuOverlayPayload::encode,
                ECOCpuOverlayPayload::decode,
                ECOCpuOverlayPayload::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    private static void onMenuClosed(net.minecraftforge.event.entity.player.PlayerContainerEvent.Close event) {
        if (event.getContainer() instanceof NetworkMenu menu)
            menu.neoecoae$dataSync().close();
    }
}
