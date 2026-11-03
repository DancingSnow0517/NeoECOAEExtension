package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.gui.widget.UploadTabButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(PatternEncodingTermScreen.class)
public class PatternEncodingTermScreenMixin<C extends PatternEncodingTermMenu> extends MEStorageScreen<C> {
    /** AE2 stacks the mode tabs 21px apart in pattern_encoding_terminal.json. */
    @Unique
    private static final int NEOECOAE$TAB_STEP = 21;

    @Shadow
    @Final
    private Map<EncodingMode, TabButton> modeTabButtons;

    public PatternEncodingTermScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        super.init();
        // Mode tabs are laid out by AE2's style during super.init(); continue the column below the last one.
        int x = leftPos + 173;
        int y = topPos + imageHeight - 111;
        for (TabButton tab : modeTabButtons.values()) {
            x = tab.getX();
            y = Math.max(y, tab.getY());
        }
        var uploadTab = new UploadTabButton(
            b -> ((PatternEncodingTermMenuExtension) this.getMenu()).neoecoae$uploadPattern()
        );
        uploadTab.setPosition(x, y + NEOECOAE$TAB_STEP);
        addRenderableWidget(uploadTab);
    }
}
