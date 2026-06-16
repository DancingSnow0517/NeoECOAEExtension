package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.gui.host.NEAeIconButtonCanvas;
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
            layout.left(6);
            layout.top(6);
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

        window.addChild(new NEPriorityPanelCanvas(config.priority()));
        window.addChild(dragArea);
        window.addChild(closeButton(window));
        for (int i = 0; i < NEPriorityPanelCanvas.STEP_VALUES.length; i++) {
            window.addChild(priorityButton(i, config.changePriority()));
        }
        window.addChild(priorityField(config));
        return window;
    }

    public static UIElement createOpenButton(UIElement window) {
        UIElement priorityButtonPanel = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(-22);
            layout.top(26);
            layout.width(18);
            layout.height(20);
        });
        priorityButtonPanel.addChild(new NEAeIconButtonCanvas(NEAeSprite.PRIORITY));
        priorityButtonPanel.addChild(new Button()
            .noText()
            .setOnClick(event -> window.setDisplay(true))
            .buttonStyle(StoragePriorityUI::transparentButton)
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.open"))
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(0);
                layout.width(18);
                layout.height(20);
            }));
        return priorityButtonPanel;
    }

    private static Button closeButton(UIElement window) {
        Button button = new Button();
        button.noText();
        button.setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)));
        button.buttonStyle(StoragePriorityUI::transparentButton);
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
        button.buttonStyle(StoragePriorityUI::transparentButton);
        button.setOnServerClick(event -> changePriority.accept(NEPriorityPanelCanvas.STEP_VALUES[index]));
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
        field.setTextResponder(text -> parsePriority(text, config.setPriority()));
        field.bind(DataBindingBuilder.string(
            () -> String.valueOf(config.priority().getAsInt()),
            text -> parsePriority(text, config.setPriority())
        ).build());
        field.textFieldStyle(style -> style
            .textColor(0x3F3D52)
            .cursorColor(0x3F3D52)
            .textShadow(false)
            .placeholder(Component.literal("0"))
            .focusOverlay(IGuiTexture.EMPTY));
        field.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        field.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NEPriorityPanelCanvas.INPUT_X + 1);
            layout.top(NEPriorityPanelCanvas.INPUT_Y + 1);
            layout.width(NEPriorityPanelCanvas.INPUT_W - 2);
            layout.height(NEPriorityPanelCanvas.INPUT_H - 2);
            layout.paddingAll(0);
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

    private static HoverTooltips tooltip(String key) {
        return new HoverTooltips(List.of(Component.translatable(key)), null, null, null);
    }

    private static void transparentButton(Button.ButtonStyle style) {
        style.baseTexture(IGuiTexture.EMPTY)
            .hoverTexture(IGuiTexture.EMPTY)
            .pressedTexture(IGuiTexture.EMPTY);
    }
}
