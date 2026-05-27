package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEFluidHatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screen for ECO Fluid Input/Output Hatches — Phase 6 proof of concept.
 * <p>
 * A single generic screen serves both input and output hatches.
 * The machine display name (e.g. "ECO Fluid Input Hatch") distinguishes them.
 * </p>
 */
public class NEFluidHatchScreen extends NEBaseMachineScreen<NEFluidHatchMenu> {
    private static final Logger LOG = LoggerFactory.getLogger("NeoECOAE/NativeUI");

    public NEFluidHatchScreen(NEFluidHatchMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 220;
        this.imageHeight = 110;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
            Component.literal("Test"),
            btn -> LOG.info("[NeoECOAE] Native Fluid Hatch UI test button clicked: {}",
                title.getString())
        ).pos(leftPos + 82, topPos + 70).size(56, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 8, 0xFFE8E8F0);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.ui.rebuilding"), 8, 24, 0xFF8A8AA0);
        guiGraphics.drawString(font,
            Component.literal("Native UI active"), 8, 40, 0xFF6AFF6A);
    }
}
