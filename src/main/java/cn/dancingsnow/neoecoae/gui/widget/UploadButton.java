package cn.dancingsnow.neoecoae.gui.widget;

import appeng.client.gui.widgets.TabButton;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import net.minecraft.network.chat.Component;

public class UploadButton extends TabButton {
    public UploadButton(int x, int y, OnPress onPress) {
        super(
            NEBlocks.CRAFTING_SYSTEM_L4.asStack(),
            Component.translatable("neoecoae.tooltip.upload_pattern"),
            onPress
        );
        setStyle(Style.HORIZONTAL);
        setX(x);
        setY(y);
        setWidth(20);
    }
}
