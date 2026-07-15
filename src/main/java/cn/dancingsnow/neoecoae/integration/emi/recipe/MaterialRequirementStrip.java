package cn.dancingsnow.neoecoae.integration.emi.recipe;

import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

final class MaterialRequirementStrip {
    private final MultiblockPreviewState state;

    MaterialRequirementStrip(MultiblockPreviewState state) {
        this.state = state;
    }

    void render(GuiGraphics g, MultiblockPreviewLayout layout, int mouseX, int mouseY, float delta) {
        int start = state.materialPage() * MultiblockPreviewState.MATERIAL_PAGE_SIZE;
        int count = Math.min(
                MultiblockPreviewState.MATERIAL_PAGE_SIZE,
                Math.max(0, state.materialStacks().size() - start));
        for (int i = 0; i < MultiblockPreviewState.MATERIAL_PAGE_SIZE; i++) {
            MultiblockPreviewLayout.Rect slot = layout.materialSlot(i);
            boolean hovered = slot.contains(mouseX, mouseY);
            g.fill(slot.x(), slot.y(), slot.right(), slot.bottom(), hovered ? 0xFFFFFFFF : 0xFFE8E8E8);
            g.fill(slot.x(), slot.y(), slot.right(), slot.y() + 1, 0xFF707070);
            g.fill(slot.x(), slot.bottom() - 1, slot.right(), slot.bottom(), 0xFF707070);
            g.fill(slot.x(), slot.y(), slot.x() + 1, slot.bottom(), 0xFF707070);
            g.fill(slot.right() - 1, slot.y(), slot.right(), slot.bottom(), 0xFF707070);
            if (i < count) {
                state.materialStacks().get(start + i).render(g, slot.x() + 1, slot.y() + 1, delta);
            }
        }

        if (state.materialPages() > 1) {
            MultiblockPreviewStyle.drawButton(g, layout.previousPageButton(), "<", mouseX, mouseY);
            MultiblockPreviewStyle.drawButton(g, layout.nextPageButton(), ">", mouseX, mouseY);
            String page = (state.materialPage() + 1) + "/" + state.materialPages();
            Font font = Minecraft.getInstance().font;
            g.drawString(
                    font,
                    page,
                    layout.previousPageButton().x() - 4 - font.width(page),
                    layout.previousPageButton().y() + 2,
                    MultiblockPreviewStyle.TEXT_COLOR,
                    false);
        }
    }

    boolean mouseClicked(MultiblockPreviewLayout layout, int mouseX, int mouseY) {
        if (state.materialPages() <= 1) {
            return false;
        }
        if (layout.previousPageButton().contains(mouseX, mouseY)) {
            state.previousMaterialsPage();
            return true;
        }
        if (layout.nextPageButton().contains(mouseX, mouseY)) {
            state.nextMaterialsPage();
            return true;
        }
        return false;
    }

    List<ClientTooltipComponent> getTooltip(MultiblockPreviewLayout layout, int mouseX, int mouseY) {
        if (state.materialPages() > 1 && layout.previousPageButton().contains(mouseX, mouseY)) {
            return MultiblockPreviewStyle.tooltip(Component.translatable("emi.neoecoae.multiblock.previous_page"));
        }
        if (state.materialPages() > 1 && layout.nextPageButton().contains(mouseX, mouseY)) {
            return MultiblockPreviewStyle.tooltip(Component.translatable("emi.neoecoae.multiblock.next_page"));
        }

        int hovered = hoveredMaterial(layout, mouseX, mouseY);
        if (hovered >= 0) {
            EmiStack stack = state.materialStacks().get(hovered);
            return stack.getTooltip();
        }
        return List.of();
    }

    private int hoveredMaterial(MultiblockPreviewLayout layout, int mouseX, int mouseY) {
        if (!layout.materialSlots().contains(mouseX, mouseY)) {
            return -1;
        }
        int slot = (mouseX - layout.materialSlotsX()) / MultiblockPreviewStyle.SLOT_SIZE;
        return state.materialIndexForSlot(slot);
    }
}
