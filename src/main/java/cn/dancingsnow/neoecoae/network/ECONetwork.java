package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ECONetwork {
    private ECONetwork() {}

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        // LDLib menu bindings can change between releases without changing our payload codecs.
        // Require the same ECO release before entering play, where those menus can be opened.
        String modVersion = ModList.get().getModContainerById(NeoECOAE.MOD_ID)
            .orElseThrow().getModInfo().getVersion().toString();
        var registrar = event.registrar(protocolVersion(modVersion));
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

    static String protocolVersion(String modVersion) {
        return "3/" + modVersion;
    }
}
