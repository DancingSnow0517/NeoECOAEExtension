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
import java.util.function.IntFunction;
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
        return inline(multiplier, connected, null, null, null);
    }

    public static UIElement createWithStatus(
        IntSupplier multiplier,
        BooleanSupplier connected,
        IntSupplier status,
        IntFunction<Component> statusText) {
        return inline(multiplier, connected, null, status, statusText);
    }

    public static UIElement createWithTrailing(
        IntSupplier multiplier,
        BooleanSupplier connected,
        Supplier<Component> trailingText) {
        return inline(multiplier, connected, trailingText, null, null);
    }

    private static UIElement inline(
        IntSupplier multiplier,
        BooleanSupplier connected,
        Supplier<Component> trailingText,
        IntSupplier trailingValue,
        IntFunction<Component> trailingValueText) {
        return new InlineStatusElement(
            multiplier,
            connected,
            trailingText,
            trailingValue,
            trailingValueText)
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
        GUIContext guiContext,
        Font font,
        Component component,
        float x,
        float baseY,
        int fallbackColor) {
        String text = component.getString();
        TextColor textColor = component.getStyle().getColor();
        int color = textColor == null ? fallbackColor : textColor.getValue();
        guiContext.graphics.drawString(
            font,
            text,
            x / TEXT_SCALE,
            baseY / TEXT_SCALE,
            0xFF000000 | color,
            false);
        return x + font.width(text) * TEXT_SCALE;
    }

    private static float drawSeparator(GUIContext guiContext, Font font, float x, float baseY) {
        return drawComponent(
            guiContext,
            font,
            Component.literal(" - "),
            x,
            baseY,
            SEPARATOR_COLOR);
    }

    private static final class InlineStatusElement extends UIElement {
        private final Supplier<Component> trailingText;
        private final IntFunction<Component> trailingValueText;
        private int multiplier;
        private boolean connected;
        private int trailingValue;

        private InlineStatusElement(
            IntSupplier multiplier,
            BooleanSupplier connected,
            Supplier<Component> trailingText,
            IntSupplier trailingValue,
            IntFunction<Component> trailingValueText) {
            this.multiplier = multiplier.getAsInt();
            this.connected = connected.getAsBoolean();
            this.trailingText = trailingText;
            this.trailingValueText = trailingValueText;

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

            if (trailingValue != null && trailingValueText != null) {
                this.trailingValue = trailingValue.getAsInt();
                BindableValue<Integer> syncedTrailingValue = new BindableValue<>(this.trailingValue);
                syncedTrailingValue.bind(DataBindingBuilder.intValS2C(trailingValue::getAsInt).build());
                syncedTrailingValue.registerValueListener(value -> this.trailingValue = value == null ? 0 : value);
                syncedTrailingValue.setDisplay(false);
                addChild(syncedTrailingValue);
            }
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            Font font = Minecraft.getInstance().font;
            long now = Util.getMillis();
            float x = getPositionX();
            float baseY = getPositionY() + 2.0F;
            String mode = Component.translatable(modeTranslationKey(multiplier)).getString();

            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            x = drawMode(guiContext, font, mode, x, baseY, now);
            x = drawSeparator(guiContext, font, x, baseY);
            x = drawComponent(guiContext, font, connectionText(connected), x, baseY, DISCONNECTED_COLOR);
            if (hasTrailingText()) {
                x = drawSeparator(guiContext, font, x, baseY);
                drawComponent(guiContext, font, trailingComponent(), x, baseY, SEPARATOR_COLOR);
            }
            guiContext.graphics.pose().popPose();
        }

        private float drawMode(GUIContext guiContext, Font font, String text, float x, float baseY, long now) {
            for (int index = 0; index < text.length(); index++) {
                String character = text.substring(index, index + 1);
                float wave = multiplier > 1
                    ? (float) Math.sin(now / 180.0D + index * 0.65D) * 0.75F
                    : 0.0F;
                int color = animatedColor(now, index);
                guiContext.graphics.drawString(
                    font,
                    character,
                    x / TEXT_SCALE,
                    (baseY + wave) / TEXT_SCALE,
                    0xFF000000 | color,
                    false);
                x += font.width(character) * TEXT_SCALE;
            }
            return x;
        }

        private boolean hasTrailingText() {
            return trailingText != null || trailingValueText != null;
        }

        private Component trailingComponent() {
            return trailingText != null ? trailingText.get() : trailingValueText.apply(trailingValue);
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
