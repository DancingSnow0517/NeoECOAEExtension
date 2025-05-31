package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public class AEPatternViewSlotWidget extends SlotWidget {

    public AEPatternViewSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int xPosition, int yPosition, boolean canTakeItems, boolean canPutItems) {
        super(itemHandler, slotIndex, xPosition, yPosition, canTakeItems, canPutItems);
    }

    public AEPatternViewSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int xPosition, int yPosition) {
        this(itemHandler, slotIndex, xPosition, yPosition, true, true);
    }

    @Override
    public void initTemplate() {
        setBackgroundTexture(ITEM_SLOT_TEXTURE);
        setOverlay(GuiTextures.PATTERN_OVERLAY);
    }

    @Override
    public void drawOverlay(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (getHandler() != null && getHandler().hasItem()) {
            return;
        }
        super.drawOverlay(graphics, mouseX, mouseY, partialTicks);
    }
}
