package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.style.StylesheetManager;
import java.util.List;
import net.minecraft.world.entity.player.Player;

/** Shared construction policy for ECO's synchronized LDLib2 menus. */
public final class ECOModularUI {
    private ECOModularUI() {}

    public static ModularUI create(UIElement root, Player player) {
        return new ModularUI(UI.of(root,
            List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }
}
