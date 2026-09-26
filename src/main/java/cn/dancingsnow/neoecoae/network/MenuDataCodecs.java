package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountClientCache;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
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
            case GRAPH -> {
                long version = buf.readVarLong();
                var graph = new CraftingGraphSnapshot(buf);
                var cycles = new ECOCycleItemList(buf);
                if (menu instanceof ECOCraftConfirmMenuMode host) host.neoecoae$setDiagnostics(version, graph, cycles);
            }
            case PATTERNS -> {
                var pos = buf.readBlockPos();
                var tag = cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec.read(buf);
                if (menu instanceof ModularUIContainerMenu modular
                        && modular.uiHolder instanceof BlockUIMenuType.BlockUIHolder holder
                        && holder.pos.equals(pos)
                        && player.level().getBlockEntity(pos) instanceof ECOMachineInterfaceBlockEntity<?> host)
                    host.getPatternPreviewSync().receive(tag);
            }
        }
    }
}
