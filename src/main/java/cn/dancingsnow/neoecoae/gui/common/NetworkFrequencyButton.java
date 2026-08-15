package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Compact host control for the persisted logical-network frequency. */
public final class NetworkFrequencyButton {
    private NetworkFrequencyButton() {
    }

    public static Button create(IntSupplier frequency, IntConsumer adjust, int width, int height) {
        Button button = HostSideButtonBar.createButton()
                .noText()
                .setOnServerClick(event -> {
                    if (event.button == 0) {
                        adjust.accept(1);
                    } else if (event.button == 1) {
                        adjust.accept(-1);
                    }
                });
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-frequency-button");
        button.layout(layout -> layout.width(width).height(height));

        UIElement icon = new UIElement()
                .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1)
                .top(1)
                .width(12)
                .height(12))
                .style(style -> style.backgroundTexture(frequencyIcon(frequency.getAsInt())));
        UIElement content = new UIElement().layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(16)
                .height(16));
        content.addChild(icon);
        button.addChild(content);

        BindableValue<Integer> syncedFrequency = new BindableValue<>(frequency.getAsInt());
        syncedFrequency.bind(DataBindingBuilder.intValS2C(frequency::getAsInt).build());
        syncedFrequency.registerValueListener(value -> icon.style(
                style -> style.backgroundTexture(frequencyIcon(value == null ? 0 : value))));
        syncedFrequency.setDisplay(false);
        button.addChild(syncedFrequency);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            int selected = normalizedFrequency(syncedFrequency.getValue() == null ? 0 : syncedFrequency.getValue()) + 1;
            event.hoverTooltips = new HoverTooltips(List.of(
                    Component.translatable("gui.neoecoae.host.network.frequency", selected),
                    Component.translatable("gui.neoecoae.host.network.frequency.tooltip")), null, null, null);
        });
        return button;
    }

    private static IGuiTexture frequencyIcon(int frequency) {
        return switch (normalizedFrequency(frequency)) {
            case 0 -> NETextures.FREQUENCY_1;
            case 1 -> NETextures.FREQUENCY_2;
            case 2 -> NETextures.FREQUENCY_3;
            case 3 -> NETextures.FREQUENCY_4;
            default -> NETextures.FREQUENCY_1;
        };
    }

    private static int normalizedFrequency(int frequency) {
        return Math.floorMod(frequency, NEFrequencyAllocator.FREQUENCY_COUNT);
    }
}
