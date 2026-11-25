package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost;
import cn.dancingsnow.neoecoae.grid.crafting.display.terminal.ExactAmountClientCache;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingPatternMenu;
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

final class MenuDataCodecs {
    private MenuDataCodecs() {}

    static void receive(Player player, MenuDataTransport.Channel channel, RegistryFriendlyByteBuf buf) {
        var menu = player.containerMenu;
        switch (channel) {
            case TERMINAL -> {
                boolean full = buf.readBoolean();
                var delta = ExactMapSync.read(buf);
                if (menu instanceof MEStorageMenu) ExactAmountClientCache.apply(menu, full, delta);
            }
            case CPU -> {
                int serial = buf.readVarInt();
                boolean full = buf.readBoolean();
                var stored = ExactMapSync.read(buf);
                var active = ExactMapSync.read(buf);
                var pending = ExactMapSync.read(buf);
                if (menu instanceof ECOBigOrderStatusHost host)
                    host.neoecoae$applyExactAmounts(serial, full, stored, active, pending);
            }
            case GRAPH -> CraftingReportStream.receive(menu, buf);
            case GRAPH_PROGRESS -> CraftingReportStream.receiveProgress(menu, buf);
            case PATTERNS -> {
                var pos = buf.readBlockPos();
                var tag = PatternPreviewCodec.read(buf);
                if (menu instanceof CraftingPatternMenu patterns) patterns.receivePreview(player, pos, tag);
            }
        }
    }
}
