package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class HostNetworkStatusElement {
    private static final float TEXT_SCALE = 0.9F;
    private static final int CONNECTED_COLOR = 0xFF20A94B;
    private static final int DISCONNECTED_COLOR = 0xFFE03B45;
    private static final int SEPARATOR_COLOR = 0xFF77727F;

    private HostNetworkStatusElement() {
    }

    public static UIElement create(IntSupplier multiplier, BooleanSupplier connected) {
        return inline(multiplier, connected, null);
    }

    public static UIElement createWithTrailing(
        IntSupplier multiplier,
        BooleanSupplier connected,
        Supplier<Component> trailingText) {
        return inline(multiplier, connected, trailingText);
    }

    private static UIElement inline(
        IntSupplier multiplier,
        BooleanSupplier connected,
        Supplier<Component> trailingText) {
        return new InlineStatusElement(multiplier, connected, trailingText)
            .layout(layout -> layout.widthPercent(100).height(12));
    }

    private static Component connectionText(boolean connected) {
        return Component.translatable(connected
                ? "gui.neoecoae.host.network.connected"
                : "gui.neoecoae.host.network.disconnected")
            .withColor(connected ? CONNECTED_COLOR : DISCONNECTED_COLOR);
    }

    private static String modeTranslationKey(int multiplier) {
        if (multiplier >= 8) {
            return "gui.neoecoae.host.network.mode.high_energy";
        }
        if (multiplier >= 2) {
            return "gui.neoecoae.host.network.mode.normal";
        }
        return "gui.neoecoae.host.network.mode.local";
    }

    private static float drawComponent(
        GUIContext context,
        Font font,
        Component component,
        float x,
        float baseY,
        int fallbackColor) {
        String text = component.getString();
        TextColor textColor = component.getStyle().getColor();
        int color = textColor == null ? fallbackColor : textColor.getValue();
        context.graphics.drawString(font, text, x / TEXT_SCALE, baseY / TEXT_SCALE,
            0xFF000000 | color, false);
        return x + font.width(text) * TEXT_SCALE;
    }

    private static float drawSeparator(GUIContext context, Font font, float x, float baseY) {
        return drawComponent(context, font, Component.literal(" - "), x, baseY, SEPARATOR_COLOR);
    }

    private static final class InlineStatusElement extends UIElement {
        private final Supplier<Component> trailingText;
        private int multiplier;
        private boolean connected;

        private InlineStatusElement(
            IntSupplier multiplier,
            BooleanSupplier connected,
            Supplier<Component> trailingText) {
            this.multiplier = multiplier.getAsInt();
            this.connected = connected.getAsBoolean();
            this.trailingText = trailingText;

            BindableValue<Integer> syncedMultiplier = new BindableValue<>(this.multiplier);
            syncedMultiplier.bind(DataBindingBuilder.intValS2C(multiplier::getAsInt).build());
            syncedMultiplier.registerValueListener(value -> this.multiplier = value == null ? 1 : value);
            syncedMultiplier.setDisplay(false);
            addChild(syncedMultiplier);

            BindableValue<Boolean> syncedConnected = new BindableValue<>(this.connected);
            syncedConnected.bind(DataBindingBuilder.boolS2C(connected::getAsBoolean).build());
            syncedConnected.registerValueListener(value -> this.connected = Boolean.TRUE.equals(value));
            syncedConnected.setDisplay(false);
            addChild(syncedConnected);
        }

        @Override
        public void drawContents(GUIContext context) {
            super.drawContents(context);
            Font font = Minecraft.getInstance().font;
            long now = Util.getMillis();
            float x = getPositionX();
            float baseY = getPositionY() + 2.0F;
            String mode = Component.translatable(modeTranslationKey(multiplier)).getString();

            context.graphics.pose().pushPose();
            context.graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            x = drawMode(context, font, mode, x, baseY, now);
            x = drawSeparator(context, font, x, baseY);
            x = drawComponent(context, font, connectionText(connected), x, baseY, DISCONNECTED_COLOR);
            if (trailingText != null) {
                x = drawSeparator(context, font, x, baseY);
                drawComponent(context, font, trailingText.get(), x, baseY, SEPARATOR_COLOR);
            }
            context.graphics.pose().popPose();
        }

        private float drawMode(GUIContext context, Font font, String text, float x, float baseY, long now) {
            for (int index = 0; index < text.length(); index++) {
                String character = text.substring(index, index + 1);
                float wave = multiplier > 1
                    ? (float) Math.sin(now / 180.0D + index * 0.65D) * 0.75F
                    : 0.0F;
                int color = animatedColor(now, index);
                context.graphics.drawString(font, character, x / TEXT_SCALE,
                    (baseY + wave) / TEXT_SCALE, 0xFF000000 | color, false);
                x += font.width(character) * TEXT_SCALE;
            }
            return x;
        }

        private int animatedColor(long now, int index) {
            if (multiplier <= 1) {
                return 0x77727F;
            }
            float phase = (now / 2400.0F + index * 0.055F) % 1.0F;
            if (multiplier < 8) {
                phase = 0.43F + phase * 0.16F;
            }
            float brightness = 0.82F + 0.18F * (float) Math.sin(now / 260.0D + index * 0.45D);
            return Mth.hsvToRgb(phase, multiplier >= 8 ? 0.72F : 0.58F, brightness);
        }
    }
}
