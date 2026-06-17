package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.gui.host.NEAeButtons;
import cn.dancingsnow.neoecoae.gui.host.NEAeSprite;
import cn.dancingsnow.neoecoae.gui.host.NEPriorityPanelCanvas;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class StoragePriorityUI {
    private static final int[] MODIFIED_STEP_VALUES = {1, 16, 32, 64, -1, -16, -32, -64};

    private StoragePriorityUI() {
    }

    public record Config(
        IntSupplier priority,
        IntConsumer setPriority,
        IntConsumer changePriority
    ) {
    }

    public static UIElement createFloatingPanel(Config config) {
        UIElement window = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(10);
            layout.top(30);
            layout.display(TaffyDisplay.NONE);
            layout.width(NEPriorityPanelCanvas.WIDTH);
            layout.height(NEPriorityPanelCanvas.HEIGHT);
        });
        window.setOverflowVisible(true);

        UIElement dragArea = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(NEPriorityPanelCanvas.WIDTH);
            layout.height(24);
        });
        WindowDragHelper.setDragMove(dragArea, window, null, null);

        window.addChild(new NEPriorityPanelCanvas());
        window.addChild(dragArea);
        window.addChild(closeButton(window));
        for (int i = 0; i < NEPriorityPanelCanvas.STEP_VALUES.length; i++) {
            window.addChild(priorityButton(i, config.changePriority()));
        }
        window.addChild(priorityField(config));
        return window;
    }

    public static UIElement createOpenButton(UIElement window) {
        return createOpenButton(window, -22, 26);
    }

    public static UIElement createOpenButton(UIElement window, int left, int top) {
        UIElement priorityButtonPanel = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(left);
            layout.top(top);
            layout.width(20);
            layout.height(20);
        });
        priorityButtonPanel.addChild(NEAeButtons.tabIcon(() -> NEAeSprite.PRIORITY)
            .setOnClick(event -> window.setDisplay(true))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.open"))
            .layout(layout -> {
                layout.width(20);
                layout.height(20);
            }));
        return priorityButtonPanel;
    }

    private static Button closeButton(UIElement window) {
        Button button = new Button();
        button.noText();
        button.setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)));
        button.buttonStyle(NEAeButtons::transparentStyle);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.close"));
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NEPriorityPanelCanvas.BACK_X);
            layout.top(NEPriorityPanelCanvas.BACK_Y);
            layout.width(NEPriorityPanelCanvas.BACK_W);
            layout.height(NEPriorityPanelCanvas.BACK_H);
        });
        return button;
    }

    private static Button priorityButton(int index, IntConsumer changePriority) {
        Button button = new Button();
        button.noText();
        button.buttonStyle(NEAeButtons::transparentStyle);
        button.setOnServerClick(event -> changePriority.accept(stepValue(index, event.isShiftDown() || event.isCtrlDown())));
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NEPriorityPanelCanvas.STEP_X[index]);
            layout.top(NEPriorityPanelCanvas.STEP_Y[index]);
            layout.width(NEPriorityPanelCanvas.STEP_W[index]);
            layout.height(NEPriorityPanelCanvas.STEP_H);
        });
        return button;
    }

    private static TextField priorityField(Config config) {
        TextField field = new TextField();
        field.setNumbersOnlyInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        field.bind(DataBindingBuilder.string(
            () -> String.valueOf(config.priority().getAsInt()),
            text -> parsePriority(text, config.setPriority())
        ).build());
        field.textFieldStyle(style -> style
            .textColor(0xE6E6F0)
            .cursorColor(0xE6E6F0)
            .textShadow(false)
            .placeholder(Component.literal("0"))
            .focusOverlay(IGuiTexture.EMPTY));
        field.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        field.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NEPriorityPanelCanvas.INPUT_X);
            layout.top(NEPriorityPanelCanvas.INPUT_Y);
            layout.width(NEPriorityPanelCanvas.INPUT_W);
            layout.height(NEPriorityPanelCanvas.INPUT_H);
            layout.paddingLeft(2);
            layout.paddingRight(2);
            layout.paddingTop(0);
            layout.paddingBottom(0);
        });
        return field;
    }

    private static void parsePriority(String text, IntConsumer setPriority) {
        if (text == null || text.isEmpty() || "-".equals(text) || "+".equals(text)) {
            return;
        }
        try {
            setPriority.accept(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
        }
    }

    private static int stepValue(int index, boolean modified) {
        return modified ? MODIFIED_STEP_VALUES[index] : NEPriorityPanelCanvas.STEP_VALUES[index];
    }

    private static HoverTooltips tooltip(String key) {
        return new HoverTooltips(List.of(Component.translatable(key)), null, null, null);
    }

}
