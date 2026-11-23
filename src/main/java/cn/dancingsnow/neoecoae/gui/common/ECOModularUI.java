package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import java.util.List;
import net.minecraft.world.entity.player.Player;

/** Shared construction policy for ECO's synchronized LDLib2 menus. */
public final class ECOModularUI extends ModularUI {
    private ECOModularUI(UI ui, Player player) {
        super(ui, player);
    }

    public static ModularUI create(UIElement root, Player player) {
        return new ECOModularUI(UI.of(root,
            List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }
}
