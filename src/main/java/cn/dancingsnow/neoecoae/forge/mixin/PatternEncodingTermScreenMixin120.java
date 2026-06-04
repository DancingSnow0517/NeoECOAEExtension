package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PatternEncodingTermScreen.class, remap = false)
public abstract class PatternEncodingTermScreenMixin120<C extends PatternEncodingTermMenu> extends MEStorageScreen<C> {
    public PatternEncodingTermScreenMixin120(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        super.init();

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        addRenderableWidget(Button.builder(
                Component.literal("Upload"),
                button -> ((PatternEncodingTermMenuExtension) this.getMenu()).neoecoae$uploadPattern()
            )
            .bounds(left + this.imageWidth - 58, top + 4, 54, 20)
            .build());
    }
}
