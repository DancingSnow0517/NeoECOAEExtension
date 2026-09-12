package cn.dancingsnow.neoecoae.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ECONetwork {
    private ECONetwork() {}

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ECOForceCraftStartFlagC2SPacket.TYPE,
            ECOForceCraftStartFlagC2SPacket.STREAM_CODEC, ECOForceCraftStartFlagC2SPacket::handle);
    }
}
