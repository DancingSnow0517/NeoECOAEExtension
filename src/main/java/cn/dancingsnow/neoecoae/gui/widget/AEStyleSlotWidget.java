package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public class AEStyleSlotWidget extends SlotWidget {
    public AEStyleSlotWidget() {
        super();
    }

    @Setter
    protected IGuiTexture slotOverlay;

    public AEStyleSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int xPosition, int yPosition) {
        super(itemHandler, slotIndex, xPosition, yPosition, true, true);
    }

    @Override
    public void initTemplate() {
        setBackgroundTexture(GuiTextures.ITEM_SLOT);
        setDrawHoverOverlay(false);
    }

    @Override
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        drawSlotOverlay(graphics, mouseX, mouseY, partialTicks);
        if (isMouseOverElement(mouseX, mouseY) && getHoverElement(mouseX, mouseY) == this) {
            RenderSystem.colorMask(true, true, true, false);
            DrawerHelper.drawSolidRect(graphics, getPosition().x, getPosition().y, 18, 18, 0x80ace9ff);
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    public void drawSlotOverlay(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (slotOverlay != null) {
            Position pos = getPosition();
            slotOverlay.draw(graphics, mouseX, mouseY, pos.x, pos.y, getSize().width, getSize().height);
        }
    }
}
