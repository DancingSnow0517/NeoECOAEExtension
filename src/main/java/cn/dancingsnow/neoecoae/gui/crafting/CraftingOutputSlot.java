package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util.DrawerHelper;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class CraftingOutputSlot extends ItemSlot {
    private BigInteger amount = BigInteger.ZERO;

    CraftingOutputSlot() {
        getStyle().backgroundTexture(IGuiTexture.EMPTY);
        slotStyle(style -> style.hoverOverlay(NETextures.AE2_SLOT_HIGHLIGHT).acceptQuickMove(false));
    }

    void setOutput(CraftingOutputDisplay output) {
        amount = output.amount();
        setItem(output.item(), false);
    }

    @Override
    public List<Component> getFullTooltipTexts(boolean withItemTooltips) {
        List<Component> lines = super.getFullTooltipTexts(withItemTooltips);
        if (!getValue().isEmpty()) {
            lines.add(Component.translatable("gui.neoecoae.crafting.ui.amount_short").append(": ")
                .append(BigNumberFormatter.formatTooltip(amount, 1)));
        }
        return lines;
    }

    @Override
    protected void drawItemStack(GUIContext context, ItemStack item) {
        super.drawItemStack(context, item);
        String count = BigNumberFormatter.formatSI(amount, 1).replace("K", "k");
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(0.8F, 16F / Math.max(1, font.width(count)));
        context.pose.pushPose();
        context.pose.translate(0, 0, 240);
        DrawerHelper.drawStringFixedCorner(context.graphics, count, 16, 16, context.elementColor, true, scale);
        context.pose.popPose();
    }

    @Override
    protected void drawHover(GUIContext context) {
        // Use the same outline as InventorySlots for this unregistered slot subclass.
        context.drawTexture(NETextures.AE2_SLOT_HIGHLIGHT, 0, 0, 16, 16);
    }
}
