package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared construction policy for ECO's synchronized LDLib2 menus. */
public final class ECOModularUI extends ModularUI {
    private final UnaryOperator<ItemStack> hostQuickMove;

    private ECOModularUI(UI ui, Player player, UnaryOperator<ItemStack> hostQuickMove) {
        super(ui, player);
        this.hostQuickMove = hostQuickMove;
    }

    public static ModularUI create(UIElement root, Player player) {
        return new ModularUI(themedUI(root), player);
    }

    public static ModularUI create(UIElement root, Player player, UnaryOperator<ItemStack> hostQuickMove) {
        return new ECOModularUI(themedUI(root), player, hostQuickMove);
    }

    public ItemStack quickMoveIntoHost(ItemStack stack) {
        return hostQuickMove.apply(stack);
    }

    private static UI themedUI(UIElement root) {
        return UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO)));
    }
}
