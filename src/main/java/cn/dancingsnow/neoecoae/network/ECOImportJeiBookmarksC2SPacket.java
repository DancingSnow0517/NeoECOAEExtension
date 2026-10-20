package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.network.FriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.network.NEStreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;

import java.util.List;

public record ECOImportJeiBookmarksC2SPacket(int containerId, List<AEKey> keys) {

    public static final NEStreamCodec<FriendlyByteBuf, ECOImportJeiBookmarksC2SPacket> STREAM_CODEC = NEStreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeVarInt(Math.min(packet.keys.size(), 256));
            packet.keys.stream().limit(256).forEach(key -> AEKey.writeKey(buf, key));
        }, buf -> {
            int containerId = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid bookmark count");
            var keys = new java.util.ArrayList<AEKey>(count);
            for (int i = 0; i < count; i++) keys.add(AEKey.readKey(buf));
            return new ECOImportJeiBookmarksC2SPacket(containerId, keys);
        });

    public static void handle(ECOImportJeiBookmarksC2SPacket packet, NEPacketContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CellWorkbenchMenu menu)
                    || menu.containerId != packet.containerId || !menu.stillValid(player)) return;
            var config = menu.getHost().getConfig();
            var cell = menu.getHost().getCell();
            var cellConfig = cell == null ? null : cell.getConfigInventory(menu.getWorkbenchItem());
            int capacity = cellConfig == null ? config.size() : Math.min(config.size(), cellConfig.size());
            config.clear();
            int slot = 0;
            for (AEKey key : packet.keys) {
                if (slot >= capacity) break;
                if (key == null || !config.isAllowedIn(slot, key)
                        || cellConfig != null && !cellConfig.isAllowedIn(slot, key)) continue;
                config.setStack(slot++, new GenericStack(key, 1));
            }
            menu.broadcastChanges();
        });
    }

}
