package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.networking.INetworking;
import com.lowdragmc.lowdraglib.networking.LDLNetworking;
import net.minecraft.resources.ResourceLocation;

/** LDLib 1 only registers its built-in managed RPC packet for S2C; our C2S path has its own channel. */
public final class ManagedRpcNetwork {
    private static INetworking network;

    private ManagedRpcNetwork() {}

    public static void register(String protocolVersion) {
        if (network != null) throw new IllegalStateException("Managed RPC channel already registered");
        network = LDLNetworking.createNetworking(new ResourceLocation("neoecoae", "managed_rpc"), protocolVersion);
        network.registerC2S(ManagedRpcC2SPacket.class);
    }

    static void send(NEManagedBlockEntity host, String method, Object[] args) {
        if (network == null) throw new IllegalStateException("Managed RPC channel not registered");
        network.sendToServer(new ManagedRpcC2SPacket(host, method, args));
    }
}
