package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import java.util.List;
import net.minecraft.world.entity.player.Player;

/** Shared construction policy for ECO's synchronized LDLib2 menus. */
public final class ECOModularUI {
    private ECOModularUI() {}

    public static ModularUI create(UIElement root) {
        return new ModularUI(ui(root));
    }

    public static ModularUI create(UIElement root, Player player) {
        return new ModularUI(ui(root), player);
    }

    private static UI ui(UIElement root) {
        return UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO)));
    }
}
