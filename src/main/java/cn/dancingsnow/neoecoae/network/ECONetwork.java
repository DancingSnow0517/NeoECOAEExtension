package cn.dancingsnow.neoecoae.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ECONetwork {
    private ECONetwork() {}

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(ECOMenuChunkS2CPacket.TYPE,
            ECOMenuChunkS2CPacket.STREAM_CODEC, ECOMenuChunkS2CPacket::handle);
        registrar.playToServer(ECOBigOrderStartC2SPacket.TYPE,
            ECOBigOrderStartC2SPacket.STREAM_CODEC, ECOBigOrderStartC2SPacket::handle);
        registrar.playToClient(ECOBigOrderProgressS2CPacket.TYPE,
            ECOBigOrderProgressS2CPacket.STREAM_CODEC, ECOBigOrderProgressS2CPacket::handle);
        registrar.playToServer(ECOForceCraftStartFlagC2SPacket.TYPE,
            ECOForceCraftStartFlagC2SPacket.STREAM_CODEC, ECOForceCraftStartFlagC2SPacket::handle);
        registrar.playToServer(ECOImportJeiBookmarksC2SPacket.TYPE,
            ECOImportJeiBookmarksC2SPacket.STREAM_CODEC, ECOImportJeiBookmarksC2SPacket::handle);
    }
}
