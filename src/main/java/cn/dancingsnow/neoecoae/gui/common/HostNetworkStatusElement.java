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
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Status line shown below a host title, matching the network-switch host UI. */
public final class HostNetworkStatusElement {
    private HostNetworkStatusElement() {}

    public static UIElement create(IntSupplier multiplier, BooleanSupplier connected) {
        UIElement row = new UIElement().layout(layout -> layout.width(150).height(12)
            .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        UIElement mode = new AnimatedModeLabel(multiplier);
        mode.layout(layout -> layout.width(84).height(12));
        BindableValue<Integer> syncedMultiplier = new BindableValue<>(multiplier.getAsInt());
        syncedMultiplier.bind(DataBindingBuilder.intValS2C(multiplier::getAsInt).build());
        syncedMultiplier.registerValueListener(value -> ((AnimatedModeLabel) mode).setMultiplier(value == null ? 1 : value));
        syncedMultiplier.setDisplay(false);
        mode.addChild(syncedMultiplier);
        row.addChild(mode);
        Label network = new Label();
        network.setText(connectionText(connected.getAsBoolean()));
        network.textStyle(style -> style.adaptiveHeight(true).adaptiveWidth(false).fontSize(8.0F)
            .textAlignHorizontal(Horizontal.LEFT).textWrap(TextWrap.HOVER_ROLL).textShadow(false));
        network.layout(layout -> layout.width(62).height(10));
        BindableValue<Boolean> syncedConnected = new BindableValue<>(connected.getAsBoolean());
        syncedConnected.bind(DataBindingBuilder.boolS2C(connected::getAsBoolean).build());
        syncedConnected.registerValueListener(value -> network.setText(connectionText(Boolean.TRUE.equals(value))));
        syncedConnected.setDisplay(false);
        network.addChild(syncedConnected);
        row.addChild(network);
        return row;
    }

    private static Component modeText(int multiplier) {
        return Component.translatable(multiplier >= 8 ? "gui.neoecoae.host.network.mode.high_energy"
            : multiplier >= 2 ? "gui.neoecoae.host.network.mode.normal" : "gui.neoecoae.host.network.mode.local");
    }

    private static Component connectionText(boolean connected) {
        return Component.translatable(connected ? "gui.neoecoae.host.network.connected"
            : "gui.neoecoae.host.network.disconnected").withColor(connected ? 0xFF20A94B : 0xFFE03B45);
    }

    private static final class AnimatedModeLabel extends UIElement {
        private int multiplier;
        private AnimatedModeLabel(IntSupplier multiplier) { this.multiplier = multiplier.getAsInt(); }
        private void setMultiplier(int multiplier) { this.multiplier = multiplier; }
        @Override public void drawContents(GUIContext context) {
            super.drawContents(context);
            Font font = Minecraft.getInstance().font;
            String text = modeText(multiplier).getString();
            long now = Util.getMillis();
            float scale = 0.8F, x = getPositionX(), baseY = getPositionY() + 2.0F;
            context.graphics.pose().pushPose(); context.graphics.pose().scale(scale, scale, 1.0F);
            for (int i = 0; i < text.length(); i++) {
                String ch = text.substring(i, i + 1);
                float wave = multiplier > 1 ? (float)Math.sin(now / 180.0D + i * 0.65D) * 0.75F : 0.0F;
                int color = multiplier <= 1 ? 0x77727F : Mth.hsvToRgb(multiplier >= 8 ? 0.55F : 0.46F, 0.65F, 0.9F);
                context.graphics.drawString(font, ch, x / scale, (baseY + wave) / scale, 0xFF000000 | color, false);
                x += font.width(ch) * scale;
            }
            context.graphics.pose().popPose();
        }
    }
}
