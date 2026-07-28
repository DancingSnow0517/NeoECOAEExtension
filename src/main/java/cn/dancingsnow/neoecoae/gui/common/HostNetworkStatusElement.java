package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class HostNetworkStatusElement {
    private static final int MODE_WIDTH = 84;
    private static final int CONNECTION_WIDTH = 62;
    private static final int CONNECTED_COLOR = 0xFF20A94B;
    private static final int DISCONNECTED_COLOR = 0xFFE03B45;

    private HostNetworkStatusElement() {
    }

    public static UIElement create(IntSupplier multiplier, BooleanSupplier connected) {
        UIElement row = new UIElement().layout(layout -> layout
            .width(MODE_WIDTH + CONNECTION_WIDTH + 4)
            .height(12)
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.CENTER)
            .gapAll(4));

        row.addChild(new AnimatedModeLabel(multiplier)
            .layout(layout -> layout.width(MODE_WIDTH).height(12)));

        Supplier<Component> connectionText = () -> Component.translatable(connected.getAsBoolean()
                ? "gui.neoecoae.host.network.connected"
                : "gui.neoecoae.host.network.disconnected")
            .withColor(connected.getAsBoolean() ? CONNECTED_COLOR : DISCONNECTED_COLOR);
        Label connection = new Label();
        connection.setText(connectionText.get());
        connection.bind(DataBindingBuilder.componentS2C(connectionText).build());
        connection.textStyle(style -> style
            .adaptiveHeight(true)
            .adaptiveWidth(false)
            .fontSize(8.0F)
            .textAlignHorizontal(Horizontal.LEFT)
            .textWrap(TextWrap.HOVER_ROLL)
            .textShadow(false));
        connection.layout(layout -> layout.width(CONNECTION_WIDTH).height(10));
        row.addChild(connection);
        return row;
    }

    private static final class AnimatedModeLabel extends UIElement {
        private int multiplier;

        private AnimatedModeLabel(IntSupplier multiplier) {
            this.multiplier = multiplier.getAsInt();
            BindableValue<Integer> syncedMultiplier = new BindableValue<>(this.multiplier);
            syncedMultiplier.bind(DataBindingBuilder.intValS2C(multiplier::getAsInt).build());
            syncedMultiplier.registerValueListener(value -> this.multiplier = value == null ? 1 : value);
            syncedMultiplier.setDisplay(false);
            addChild(syncedMultiplier);
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            Font font = Minecraft.getInstance().font;
            String text = Component.translatable(translationKey()).getString();
            long now = Util.getMillis();
            float scale = 0.8F;
            float x = getPositionX();
            float baseY = getPositionY() + 2.0F;

            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().scale(scale, scale, 1.0F);
            for (int index = 0; index < text.length(); index++) {
                String character = text.substring(index, index + 1);
                float wave = multiplier > 1
                    ? (float)Math.sin(now / 180.0D + index * 0.65D) * 0.75F
                    : 0.0F;
                int color = animatedColor(now, index);
                guiContext.graphics.drawString(
                    font,
                    character,
                    x / scale,
                    (baseY + wave) / scale,
                    0xFF000000 | color,
                    false
                );
                x += font.width(character) * scale;
            }
            guiContext.graphics.pose().popPose();
        }

        private String translationKey() {
            if (multiplier >= 8) {
                return "gui.neoecoae.host.network.mode.high_energy";
            }
            if (multiplier >= 2) {
                return "gui.neoecoae.host.network.mode.normal";
            }
            return "gui.neoecoae.host.network.mode.local";
        }

        private int animatedColor(long now, int index) {
            if (multiplier <= 1) {
                return 0x77727F;
            }
            float phase = (now / 2400.0F + index * 0.055F) % 1.0F;
            if (multiplier < 8) {
                phase = 0.43F + phase * 0.16F;
            }
            float brightness = 0.82F + 0.18F * (float)Math.sin(now / 260.0D + index * 0.45D);
            return Mth.hsvToRgb(phase, multiplier >= 8 ? 0.72F : 0.58F, brightness);
        }
    }
}
