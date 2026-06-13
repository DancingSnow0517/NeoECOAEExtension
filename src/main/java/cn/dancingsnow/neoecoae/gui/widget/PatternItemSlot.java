package cn.dancingsnow.neoecoae.gui.widget;

import appeng.crafting.pattern.EncodedPatternItem;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@LDLRegister(name = "pattern-item-slot", group = "inventory", registry = "ldlib2:ui_element")
public class PatternItemSlot extends ItemSlot {

    public PatternItemSlot() {
        this(new LocalSlot());
    }

    public PatternItemSlot(Slot slot) {
        super(slot);
    }

    @LDLRegisterClient(name = "pattern_item_slot", registry = "ldlib2:ui_element_renderer")
    public static final class Renderer extends DelegatingUIElementRenderer<PatternItemSlot, Renderer> {
        @Override
        public Class<PatternItemSlot> type() {
            return PatternItemSlot.class;
        }

        @Override
        public void drawBackgroundAdditional(PatternItemSlot itemSlot, IGUIContext context) {
            drawParentBackgroundAdditional(itemSlot, context);
            if (!(context instanceof GUIContext guiContext)) {
                return;
            }
            ItemStack stack = itemSlot.getValue();
            if (stack.getItem() instanceof EncodedPatternItem<?> patternItem) {
                ItemStack output = patternItem.getOutput(stack);
                if (!output.isEmpty()) {
                    guiContext.pose.pushPose();
                    float contentWidth = itemSlot.getContentWidth();
                    float contentHeight = itemSlot.getContentHeight();
                    guiContext.pose.scale(contentWidth / 16f, contentHeight / 16f);
                    guiContext.pose.translate(itemSlot.getContentX() * 16 / contentWidth, itemSlot.getContentY() * 16 / contentHeight);
                    DrawerHelperClient.drawItemStack(guiContext, output, 0, 0, -1);
                    guiContext.pose.popPose();
                }
            }
        }
    }
}
