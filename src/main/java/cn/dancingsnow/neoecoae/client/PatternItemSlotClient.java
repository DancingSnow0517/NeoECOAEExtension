package cn.dancingsnow.neoecoae.client;

import appeng.crafting.pattern.EncodedPatternItem;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
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
        if (itemStack.getItem() instanceof EncodedPatternItem<?> patternItem) {
            ItemStack output = patternItem.getOutput(itemStack);
            if (!output.isEmpty()) {
                DrawerHelper.drawItemStack(guiContext.graphics, output, 0, 0, -1, null);
            } else {
                super.drawItemStack(guiContext, itemStack);
            }
        } else {
            super.drawItemStack(guiContext, itemStack);
        }
        if (isHighlighted()) {
            guiContext.graphics.fill(0, 0, 18, 1, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(0, 17, 18, 18, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(0, 0, 1, 18, SEARCH_HIGHLIGHT_COLOR);
            guiContext.graphics.fill(17, 0, 18, 18, SEARCH_HIGHLIGHT_COLOR);
        }
    }
}
