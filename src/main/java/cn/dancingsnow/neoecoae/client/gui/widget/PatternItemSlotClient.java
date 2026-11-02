package cn.dancingsnow.neoecoae.client.gui.widget;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
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
    protected void drawItemStack(IGUIContext context, ItemStack itemStack) {
        super.drawItemStack(context, itemStack);
        if (!(context instanceof GUIContext guiContext)) return;
        if (isDimmed()) guiContext.graphics.fill(0, 0, 16, 16, 0x88000000);
        if (isHighlighted()) {
            guiContext.graphics.fill(-1, -1, 17, 0, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(-1, 16, 17, 17, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(-1, -1, 0, 17, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(16, -1, 17, 17, SEARCH_HIGHLIGHT_COLOR);
        }
    }
}
