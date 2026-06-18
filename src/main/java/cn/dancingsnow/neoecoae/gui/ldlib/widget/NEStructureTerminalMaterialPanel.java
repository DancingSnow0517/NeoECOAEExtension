package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEStructureTerminalConfigState;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

final class NEStructureTerminalMaterialPanel {
    private final Supplier<NEStructureTerminalConfigState> stateSupplier;
    private final Supplier<List<ItemStack>> materialsSupplier;
    private final Consumer<Integer> scrollWriter;
    private int scroll;

    NEStructureTerminalMaterialPanel(
            Supplier<NEStructureTerminalConfigState> stateSupplier,
            Supplier<List<ItemStack>> materialsSupplier,
            Consumer<Integer> scrollWriter) {
        this.stateSupplier = stateSupplier;
        this.materialsSupplier = materialsSupplier;
        this.scrollWriter = scrollWriter;
    }

    void drawSlots(NEStructureTerminalRenderContext context, GuiGraphics graphics) {
        for (int i = 0; i < NEStructureTerminalLayout.patternVisibleSlots(); i++) {
            NELDLibClientStyle.drawDarkSlot(
                    graphics,
                    context.absX(NEStructureTerminalLayout.patternSlotX(i)),
                    context.absY(NEStructureTerminalLayout.patternSlotY(i)),
                    NEStructureTerminalLayout.SLOT_SIZE);
        }
    }

    void drawItems(NEStructureTerminalRenderContext context, GuiGraphics graphics) {
        List<ItemStack> materials = materialsSupplier.get();
        scroll = clampScroll(stateSupplier.get().previewMaterialScroll(), materials.size());
        int count = Math.min(NEStructureTerminalLayout.patternVisibleSlots(), Math.max(0, materials.size() - scroll));
        NELDLibGuiRenderState.beginVanillaGuiItemBatch(graphics);
        for (int i = 0; i < count; i++) {
            ItemStack stack = materials.get(scroll + i);
            renderMaterialItem(
                    context,
                    graphics,
                    stack,
                    NEStructureTerminalLayout.patternSlotX(i),
                    NEStructureTerminalLayout.patternSlotY(i),
                    stack.getCount());
        }
        NELDLibGuiRenderState.endVanillaGuiItemBatch(graphics);
    }

    void drawTooltip(NEStructureTerminalRenderContext context, GuiGraphics graphics, int mouseX, int mouseY) {
        int visibleIndex = slotAt(context, mouseX, mouseY);
        List<ItemStack> materials = materialsSupplier.get();
        int index = visibleIndex < 0 ? -1 : scroll + visibleIndex;
        if (index < 0 || index >= materials.size()) {
            return;
        }
        ItemStack stack = materials.get(index);
        graphics.renderTooltip(
                context.font(),
                List.of(
                        stack.getHoverName(),
                        Component.translatable(
                                "gui.neoecoae.structure_terminal.required", NELDLibText.number(stack.getCount()))),
                java.util.Optional.empty(),
                mouseX,
                mouseY);
    }

    void drawPageText(NEStructureTerminalRenderContext context, GuiGraphics graphics, int rightX, int y, int color) {
        int size = materialsSupplier.get().size();
        if (size <= NEStructureTerminalLayout.patternVisibleSlots()) {
            return;
        }
        String text = (scroll + 1)
                + "-"
                + Math.min(size, scroll + NEStructureTerminalLayout.patternVisibleSlots())
                + "/"
                + size;
        context.drawRightLocalString(graphics, Component.literal(text), rightX, y, color);
    }

    boolean mouseWheelMove(NEStructureTerminalRenderContext context, double mouseX, double mouseY, double wheelDelta) {
        if (!isMouseOverGrid(context, mouseX, mouseY)) {
            return false;
        }
        List<ItemStack> materials = materialsSupplier.get();
        int current = clampScroll(stateSupplier.get().previewMaterialScroll(), materials.size());
        scroll = clampScroll(
                current
                        + (wheelDelta < 0
                                ? NEStructureTerminalLayout.PATTERN_MATERIAL_COLS
                                : -NEStructureTerminalLayout.PATTERN_MATERIAL_COLS),
                materials.size());
        if (scroll != current) {
            scrollWriter.accept(scroll);
        }
        return materials.size() > NEStructureTerminalLayout.patternVisibleSlots();
    }

    boolean isMouseOverGrid(NEStructureTerminalRenderContext context, double mouseX, double mouseY) {
        return Widget.isMouseOver(
                context.absX(NEStructureTerminalLayout.INFO_PANEL_X),
                context.absY(NEStructureTerminalLayout.PATTERN_MATERIAL_Y),
                NEStructureTerminalLayout.INFO_PANEL_W,
                NEStructureTerminalLayout.PATTERN_MATERIAL_ROWS * NEStructureTerminalLayout.SLOT_SIZE,
                mouseX,
                mouseY);
    }

    private void renderMaterialItem(
            NEStructureTerminalRenderContext context, GuiGraphics graphics, ItemStack source, int x, int y, int count) {
        ItemStack display = source.copy();
        if (display.isEmpty()) {
            return;
        }
        display.setCount(1);
        NELDLibGuiRenderState.renderVanillaSlotItem(
                graphics,
                context.font(),
                display,
                context.absX(x + 1),
                context.absY(y + 1),
                "x" + NELDLibText.compactCount(count));
    }

    private int slotAt(NEStructureTerminalRenderContext context, double mouseX, double mouseY) {
        for (int i = 0; i < NEStructureTerminalLayout.patternVisibleSlots(); i++) {
            int x = context.absX(NEStructureTerminalLayout.patternSlotX(i));
            int y = context.absY(NEStructureTerminalLayout.patternSlotY(i));
            if (Widget.isMouseOver(
                    x, y, NEStructureTerminalLayout.SLOT_SIZE, NEStructureTerminalLayout.SLOT_SIZE, mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private int clampScroll(int value, int size) {
        return Mth.clamp(value, 0, Math.max(0, size - NEStructureTerminalLayout.patternVisibleSlots()));
    }
}
