package cn.dancingsnow.neoecoae.gui.ldlib;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class NELDLibUis {
    public static ModularUI createStructureTerminal(Player player, HeldItemUIFactory.HeldItemHolder holder) {
        WidgetGroup panel = new WidgetGroup(0, 0, 320, 32);
        panel.setBackground(new ColorRectTexture(0xFFCBCBD3));
        TextTextureWidget hint = new TextTextureWidget(8, 12, 304, 9)
                .setText(() -> Component.translatable("gui.neoecoae.structure_terminal.hint_host_builder"));
        hint.textureStyle(texture -> texture.setColor(0xFF404040).setDropShadow(false));
        panel.addWidget(hint);
        return new ModularUI(320, 32, holder, player).widget(panel);
    }

    private NELDLibUis() {}
}
