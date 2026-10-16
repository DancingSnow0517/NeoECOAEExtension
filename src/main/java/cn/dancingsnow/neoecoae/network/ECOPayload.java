package cn.dancingsnow.neoecoae.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One scheduling boundary for ECO server payloads. Security validation stays in the server action. */
public interface ECOPayload extends CustomPacketPayload {
    void handleOnServer(IPayloadContext context);
    static <P extends ECOPayload> void dispatch(P packet, IPayloadContext context) {
        context.enqueueWork(() -> packet.handleOnServer(context));
    }
}
