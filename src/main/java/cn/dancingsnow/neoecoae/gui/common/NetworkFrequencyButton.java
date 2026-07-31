package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Compact host control for the persisted logical-network frequency. */
public final class NetworkFrequencyButton {
    private static final int BUTTON_WIDTH = 22;
    private static final int BUTTON_HEIGHT = 16;

    private NetworkFrequencyButton() {
    }

    public static Button create(IntSupplier frequency, IntConsumer adjust) {
        Button button = new Button()
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
        button.layout(layout -> layout.width(BUTTON_WIDTH).height(BUTTON_HEIGHT));

        Label label = new Label();
        label.setText(frequencyText(frequency.getAsInt()));
        label.textStyle(style -> style
            .adaptiveHeight(true)
            .adaptiveWidth(true)
            .textAlignHorizontal(Horizontal.CENTER)
            .textShadow(false));
        label.layout(layout -> layout.width(BUTTON_WIDTH).height(BUTTON_HEIGHT));
        button.addChild(label);

        BindableValue<Integer> syncedFrequency = new BindableValue<>(frequency.getAsInt());
        syncedFrequency.bind(DataBindingBuilder.intValS2C(frequency::getAsInt).build());
        syncedFrequency.registerValueListener(value -> label.setText(frequencyText(value == null ? 0 : value)));
        syncedFrequency.setDisplay(false);
        button.addChild(syncedFrequency);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            int selected = syncedFrequency.getValue() == null ? 0 : syncedFrequency.getValue();
            event.hoverTooltips = new HoverTooltips(List.of(
                Component.translatable("gui.neoecoae.host.network.frequency", selected + 1),
                Component.translatable("gui.neoecoae.host.network.frequency.tooltip")
            ), null, null, null);
        });
        return button;
    }

    private static Component frequencyText(int frequency) {
        return Component.literal(Integer.toString(Math.max(0, frequency) + 1));
    }
}
