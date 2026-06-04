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
            20,
            22,
            Component.empty(),
            onPress,
            unused -> Component.empty()
        );
        this.iconStack = iconStack.copy();
        this.iconStack.setCount(1);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Icon background = isHoveredOrFocused() ? Icon.HORIZONTAL_TAB_FOCUS : Icon.HORIZONTAL_TAB;
        background.getBlitter()
            .dest(getX(), getY())
            .blit(guiGraphics);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        guiGraphics.renderItem(this.iconStack, getX() + 1, getY() + 3);
        guiGraphics.pose().popPose();

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
