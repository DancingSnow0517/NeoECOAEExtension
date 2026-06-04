package cn.dancingsnow.neoecoae.gui.widget;

import appeng.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class UploadButton extends Button {
    private final ItemStack iconStack;

    public UploadButton(int x, int y, ItemStack iconStack, OnPress onPress) {
        super(
            x,
            y,
            18,
            20,
            Component.empty(),
            onPress,
            unused -> Component.empty()
        );
        this.iconStack = iconStack.copy();
        this.iconStack.setCount(1);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int yOffset = isHovered() ? 1 : 0;
        Icon bgIcon = Icon.TOOLBAR_BUTTON_BACKGROUND;
        bgIcon.getBlitter()
            .dest(getX() - 1, getY() + yOffset, 18, 20)
            .blit(guiGraphics);

        guiGraphics.renderItem(this.iconStack, getX(), getY() + 2 + yOffset);

        if (isHovered()) {
            guiGraphics.renderComponentTooltip(
                Minecraft.getInstance().font,
                List.of(
                    Component.translatable("neoecoae.tooltip.upload_pattern")
                ),
                mouseX,
                mouseY
            );
        }
    }
}
