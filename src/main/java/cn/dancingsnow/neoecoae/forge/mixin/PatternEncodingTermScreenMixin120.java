package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.gui.widget.UploadButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
        int uploadX = left + this.imageWidth - 22;
        int uploadY = top + this.imageHeight - 90;

        addRenderableWidget(new UploadButton(
            uploadX,
            uploadY,
            new ItemStack(NEBlocks.CRAFTING_SYSTEM_L4.get()),
            button -> ((PatternEncodingTermMenuExtension) this.getMenu()).neoecoae$uploadPattern()
        ));
    }
}
