package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.implementations.CellWorkbenchScreen;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.client.NeoECOAEClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CellWorkbenchScreen.class)
public abstract class CellWorkbenchScreenMixin extends UpgradeableScreen<CellWorkbenchMenu> {
    @Unique
    private Button neoecoae$importJeiBookmarks;

    protected CellWorkbenchScreenMixin(CellWorkbenchMenu menu, Inventory playerInventory,
                                       Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        if (neoecoae$importJeiBookmarks == null) {
            neoecoae$importJeiBookmarks = addToLeftToolbar(
                NeoECOAEClient.createJeiBookmarkButton(menu));
        }
        super.init();
    }
}
