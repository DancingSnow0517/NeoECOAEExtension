package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskCards;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.gui.task.HostTaskListElement;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Package-private inventory and task composition for the crafting host page. */
final class CraftingBottomPanelsUI {
    private static final int BOTTOM_PANEL_HEIGHT = 88;
    private static final int INVENTORY_WIDTH = 162;
    private static final int TASK_WIDTH = 122;
    private static final int TASK_HEIGHT = 88;
    private static final int TASK_CARD_X = 8;
    private static final int TASK_CARD_Y = 19;
    private static final int TASK_CARD_WIDTH = TASK_WIDTH - 16;
    private static final int TASK_CARD_HEIGHT = 16;
    private static final int TASK_CARD_STRIDE = 18;
    private static final int TASK_LIST_BOTTOM_Y = TASK_HEIGHT - 4;
    private static final int TASK_SCROLLBAR_WIDTH = 3;

    private CraftingBottomPanelsUI() {
    }

    static UIElement create(CraftingHostPanelUI.Config config) {
        UIElement row = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(BOTTOM_PANEL_HEIGHT)
                .flexDirection(FlexDirection.ROW));
        row.addClass("eco-host-bottom-row");
        row.addChildren(inventoryPanel(), taskPanel(config));
        return row;
    }

    private static UIElement inventoryPanel() {
        Label title = new Label();
        title.setText(Component.translatable("container.inventory").withColor(CraftingHostStyles.ROOT_TEXT));
        title.addClass("eco-host-label");
        title.textStyle(CraftingHostStyles::compact);
        title.layout(layout -> layout.height(10));
        return HostElements.inventoryPanel(title, INVENTORY_WIDTH, BOTTOM_PANEL_HEIGHT);
    }

    private static UIElement taskPanel(CraftingHostPanelUI.Config config) {
        UIElement panel = new UIElement()
                .addClasses("eco-host-card", "eco-host-task-panel")
                .layout(layout -> layout.width(TASK_WIDTH).height(TASK_HEIGHT));
        panel.addChild(new HostTaskListElement(
                config.registries(),
                config.tasks(),
                TASK_WIDTH,
                TASK_HEIGHT,
                TASK_CARD_X,
                TASK_CARD_Y,
                TASK_CARD_WIDTH,
                TASK_CARD_HEIGHT,
                TASK_CARD_STRIDE,
                TASK_LIST_BOTTOM_Y,
                TASK_SCROLLBAR_WIDTH) {
            @Override
            protected List<Component> tooltipLines(ComputationTaskEntry entry) {
                return craftingTooltip(entry);
            }

            @Override
            protected void drawTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, float x,
                    float y) {
                drawCraftingTaskCard(guiContext, font, entry, Math.round(x), Math.round(y));
            }

            @Override
            protected int titleX() {
                return 8 + CraftingHostStyles.PANEL_TEXT_SHIFT_X;
            }

            @Override
            protected int countRightX() {
                return TASK_WIDTH - 12 + CraftingHostStyles.PANEL_TEXT_SHIFT_X;
            }

            @Override
            protected int scissorRight() {
                return TASK_WIDTH - 8;
            }

            @Override
            protected int scrollbarX() {
                return TASK_WIDTH - 9;
            }

            @Override
            protected float emptyTextX(Font font, String text) {
                return Math.max(0, TASK_WIDTH - font.width(text)) / 2.0F + CraftingHostStyles.PANEL_TEXT_SHIFT_X;
            }
        }.layout(layout -> layout.width(TASK_WIDTH).height(TASK_HEIGHT)));
        return panel;
    }

    private static List<Component> craftingTooltip(ComputationTaskEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.addAll(ComputationTaskCards.tooltipLines(entry));
        lines.add(Component.translatable(ComputationTaskCards.statusKey(entry.status()))
                .append(" ")
                .append(Component.literal(ComputationTaskCards.progressText(entry))));
        return lines;
    }

    private static void drawCraftingTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, int x,
            int y) {
        int accent = ComputationTaskCards.statusColor(entry.status());
        guiContext.graphics.fill(x, y, x + TASK_CARD_WIDTH, y + TASK_CARD_HEIGHT, 0xFFD8D3E4);
        guiContext.graphics.fill(x + 1, y + 1, x + TASK_CARD_WIDTH - 1, y + TASK_CARD_HEIGHT - 1, 0xFF17141E);
        guiContext.graphics.fill(x + 2, y + 2, x + TASK_CARD_WIDTH - 2, y + TASK_CARD_HEIGHT - 2, 0xFF2C2735);
        guiContext.graphics.fill(x + 2, y + TASK_CARD_HEIGHT - 2, x + TASK_CARD_WIDTH - 2, y + TASK_CARD_HEIGHT - 1,
                accent);
        if (!entry.output().isEmpty()) {
            DrawerHelper.drawItemStack(guiContext.graphics, entry.output(), x + 3, y, -1, null);
        }
        String name = font.plainSubstrByWidth(entry.output().getHoverName().getString(), TASK_CARD_WIDTH - 52);
        HostTaskListElement.drawString(guiContext, font, name, x + 22 + CraftingHostStyles.PANEL_TEXT_SHIFT_X, y + 3,
                HostText.PRIMARY);
        HostTaskListElement.drawRightString(guiContext, font, "x" + ComputationTaskCards.compactAmount(entry.outputAmount()),
                x + TASK_CARD_WIDTH - 4, y + 3, HostText.VALUE);
    }
}
