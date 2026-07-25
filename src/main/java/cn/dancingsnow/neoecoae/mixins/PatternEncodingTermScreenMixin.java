package cn.dancingsnow.neoecoae.mixins;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.gui.widget.UploadButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PatternEncodingTermScreen.class, remap = false)
public abstract class PatternEncodingTermScreenMixin<C extends PatternEncodingTermMenu> extends MEStorageScreen<C> {
    public PatternEncodingTermScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        super.init();

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        WidgetStyle modeTabStyle = getStyle().getWidget("modeTabButton0");
        var modeTabPosition = modeTabStyle.resolve(new Rect2i(0, 0, this.imageWidth, this.imageHeight));
        int uploadX = left + modeTabPosition.getX();
        int uploadY = top + this.imageHeight - 90;
        int uploadWidth = modeTabStyle.getWidth() > 0 ? modeTabStyle.getWidth() : 20;
        int uploadHeight = modeTabStyle.getHeight() > 0 ? modeTabStyle.getHeight() : 22;

        addRenderableWidget(new UploadButton(
                uploadX,
                uploadY,
                uploadWidth,
                uploadHeight,
                new ItemStack(NEBlocks.CRAFTING_SYSTEM_L4.get()),
                button -> ((PatternEncodingTermMenuExtension) this.getMenu()).neoecoae$uploadPattern()));
    }
}
