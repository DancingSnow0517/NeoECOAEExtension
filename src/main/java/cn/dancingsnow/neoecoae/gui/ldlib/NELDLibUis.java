package cn.dancingsnow.neoecoae.gui.ldlib;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStructureTerminalWidget;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
public final class NELDLibUis {
    public static ModularUI createStructureTerminal(Player player, HeldItemUIFactory.HeldItemHolder holder) {
        return new ModularUI(NEStructureTerminalWidget.WIDTH, NEStructureTerminalWidget.HEIGHT, holder, player)
                .widget(new NEStructureTerminalWidget(holder));
    }

    private NELDLibUis() {}
}
