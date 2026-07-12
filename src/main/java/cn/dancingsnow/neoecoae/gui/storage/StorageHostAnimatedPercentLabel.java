package cn.dancingsnow.neoecoae.gui.storage;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class StorageHostAnimatedPercentLabel extends UIElement {
    private final StorageHostAnimatedRatio ratio;
    @Nullable
    private final String prefixTranslationKey;
    private final IntSupplier color;
    @Nullable
    private final Supplier<Component> textOverride;
    private final float scale;
    private final boolean centered;

    private StorageHostAnimatedPercentLabel(
        StorageHostAnimatedRatio ratio,
        @Nullable String prefixTranslationKey,
        IntSupplier color,
        @Nullable Supplier<Component> textOverride,
        float scale,
        boolean centered
    ) {
        this.ratio = ratio;
        this.prefixTranslationKey = prefixTranslationKey;
        this.color = color;
        this.textOverride = textOverride;
        this.scale = scale;
        this.centered = centered;
    }

    static StorageHostAnimatedPercentLabel left(
        StorageHostAnimatedRatio ratio,
        String prefixTranslationKey,
        IntSupplier color,
        float scale
    ) {
        return new StorageHostAnimatedPercentLabel(ratio, prefixTranslationKey, color, null, scale, false);
    }

    static StorageHostAnimatedPercentLabel centered(StorageHostAnimatedRatio ratio, IntSupplier color, float scale) {
        return new StorageHostAnimatedPercentLabel(ratio, null, color, null, scale, true);
    }

    static StorageHostAnimatedPercentLabel centered(
        StorageHostAnimatedRatio ratio,
        IntSupplier color,
        Supplier<Component> textOverride,
        float scale
    ) {
        return new StorageHostAnimatedPercentLabel(ratio, null, color, textOverride, scale, true);
    }

    @Override
    public void drawContents(GUIContext guiContext) {
        super.drawContents(guiContext);
        Component text = text();
        Font font = Minecraft.getInstance().font;
        float width = getSizeWidth() / scale;
        int textX = centered ? Math.round((width - font.width(text)) / 2.0F) : 0;
        guiContext.graphics.pose().pushPose();
        guiContext.graphics.pose().translate(getPositionX(), getPositionY(), 0.0F);
        guiContext.graphics.pose().scale(scale, scale, 1.0F);
        guiContext.graphics.drawString(font, text, textX, 0, 0xFF000000 | color.getAsInt(), false);
        guiContext.graphics.pose().popPose();
    }

    private Component text() {
        if (textOverride != null) {
            return textOverride.get();
        }
        Component percent = Component.literal(StorageHostText.percent(ratio.value()));
        if (prefixTranslationKey == null) {
            return percent;
        }
        return Component.translatable(prefixTranslationKey).append(": ").append(percent);
    }
}
