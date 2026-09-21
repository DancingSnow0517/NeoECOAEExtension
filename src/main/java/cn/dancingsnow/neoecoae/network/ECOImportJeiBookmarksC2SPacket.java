package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ECOImportJeiBookmarksC2SPacket(int containerId, List<AEKey> keys) implements CustomPacketPayload {
    public static final Type<ECOImportJeiBookmarksC2SPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "import_jei_bookmarks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOImportJeiBookmarksC2SPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeVarInt(Math.min(packet.keys.size(), 256));
            packet.keys.stream().limit(256).forEach(key -> AEKey.writeKey(buf, key));
        }, buf -> {
            int containerId = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), 256);
            var keys = new java.util.ArrayList<AEKey>(count);
            for (int i = 0; i < count; i++) keys.add(AEKey.readKey(buf));
            return new ECOImportJeiBookmarksC2SPacket(containerId, keys);
        });

    public static void handle(ECOImportJeiBookmarksC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CellWorkbenchMenu menu)
                    || menu.containerId != packet.containerId || !menu.stillValid(player)) return;
            var config = menu.getHost().getConfig();
            config.clear();
            int slot = 0;
            for (AEKey key : packet.keys) {
                if (slot >= config.size() || !config.isAllowedIn(slot, key)) break;
                config.setStack(slot++, new GenericStack(key, 1));
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
