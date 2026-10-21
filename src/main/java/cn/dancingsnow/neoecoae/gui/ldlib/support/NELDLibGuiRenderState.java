package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class NELDLibGuiRenderState {
    private NELDLibGuiRenderState() {}

    public static void beginVanillaGuiItemBatch(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        Lighting.setupFor3DItems();
    }

    public static void renderVanillaSlotItem(
            GuiGraphics graphics, Font font, ItemStack stack, int x, int y, String countLabel) {
        if (stack.isEmpty()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y, countLabel);
        graphics.pose().popPose();
    }

    public static void endVanillaGuiItemBatch(GuiGraphics graphics) {
        graphics.flush();
        restoreGuiItemState();
    }

    public static void restoreGui2dStateAfterScene(GuiGraphics graphics) {
        graphics.flush();
        restoreGuiItemState();
    }

    private static void restoreGuiItemState() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        Lighting.setupFor3DItems();
    }
}
