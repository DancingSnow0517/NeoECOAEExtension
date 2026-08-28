package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PatternItemSlotClient extends PatternItemSlot {
    private static final int SEARCH_HIGHLIGHT_COLOR = 0xFFFFD95A;

    public PatternItemSlotClient() {
        this(new LocalSlot());
    }

    public PatternItemSlotClient(Slot slot) {
        super(slot);
        getStyle().backgroundTexture(NETextures.ITEM_SLOT);
    }

    @Override
    protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
        super.drawItemStack(guiContext, itemStack);
        if (isHighlighted()) {
            guiContext.graphics.fill(0, 0, 18, 1, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(0, 17, 18, 18, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(0, 0, 1, 18, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(17, 0, 18, 18, SEARCH_HIGHLIGHT_COLOR);
        }
    }
}
