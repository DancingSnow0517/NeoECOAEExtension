package cn.dancingsnow.neoecoae.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ECONetwork {
    private ECONetwork() {}

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ECOForceCraftStartFlagC2SPacket.TYPE,
            ECOForceCraftStartFlagC2SPacket.STREAM_CODEC, ECOForceCraftStartFlagC2SPacket::handle);
        registrar.playToClient(ECOExactAmountsS2CPacket.TYPE,
            ECOExactAmountsS2CPacket.STREAM_CODEC, ECOExactAmountsS2CPacket::handle);
    }
}
