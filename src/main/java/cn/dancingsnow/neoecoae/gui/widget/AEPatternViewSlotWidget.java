package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.GuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public class AEPatternViewSlotWidget extends AEStyleSlotWidget {

    public AEPatternViewSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int xPosition, int yPosition) {
        super(itemHandler, slotIndex, xPosition, yPosition);
    }

    @Override
    public void initTemplate() {
        super.initTemplate();
        setSlotOverlay(GuiTextures.PATTERN_OVERLAY);
    }

    @Override
    public void drawSlotOverlay(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (getHandler() != null && getHandler().hasItem()) {
            return;
        }
        super.drawSlotOverlay(graphics, mouseX, mouseY, partialTicks);
    }
}
