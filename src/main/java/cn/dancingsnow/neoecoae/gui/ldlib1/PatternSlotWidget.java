package cn.dancingsnow.neoecoae.gui.ldlib1;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.Container;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;

/**
 * A SlotWidget variant for Pattern Bus slots.
 * <p>
 * Draws an empty-overlay texture in the <em>background</em> layer
 * (before item rendering) only when the slot is empty, so the overlay
 * never covers a real ItemStack. This replaces the foreground
 * {@code setOverlay()} approach that would always draw on top of items.
 * </p>
 */
public class PatternSlotWidget extends SlotWidget {
    private final Container container;
    private final int slotIndex;
    private IGuiTexture emptyOverlay;

    public PatternSlotWidget(Container container, int slotIndex, int x, int y, boolean canPut, boolean canTake) {
        super(container, slotIndex, x, y, canPut, canTake);
        this.container = container;
        this.slotIndex = slotIndex;
    }

    /**
     * Set the texture drawn when the slot is empty.
     * This draws in the background layer so items always render on top.
     */
    public PatternSlotWidget setEmptyOverlay(IGuiTexture texture) {
        this.emptyOverlay = texture;
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Draw the standard slot background first (frame, etc.)
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);

        // Only draw the empty-overlay when the slot has no item.
        // Because this runs in the background layer, the ItemStack (rendered
        // later by the widget system) will always appear on top when present.
        if (emptyOverlay != null && container.getItem(slotIndex).isEmpty()) {
            emptyOverlay.draw(graphics, mouseX, mouseY, getPosition().x, getPosition().y, 18, 18);
        }
    }
}
