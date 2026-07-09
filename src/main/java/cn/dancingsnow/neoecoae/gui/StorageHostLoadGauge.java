package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

final class StorageHostLoadGauge extends UIElement implements IBindable<Float> {
    private static final ResourceLocation STORAGE_ELEMENTS =
        NeoECOAE.id("textures/gui/storage/estorage_controller_elements.png");
    private static final int GAUGE_WIDTH = 32;
    private static final int GAUGE_CAP_HEIGHT = 8;
    private static final int GAUGE_TOP_U = 1;
    private static final int GAUGE_TOP_V = 246;
    private static final int GAUGE_MID_U = 34;
    private static final int GAUGE_MID_V = 250;
    private static final int GAUGE_MID_HEIGHT = 4;
    private static final int GAUGE_BOTTOM_U = 1;
    private static final int GAUGE_BOTTOM_V = 246;
    private static final SpriteTexture TOP_CAP = SpriteTexture.of(STORAGE_ELEMENTS)
        .setSprite(GAUGE_TOP_U, GAUGE_TOP_V, GAUGE_WIDTH, GAUGE_CAP_HEIGHT);
    private static final SpriteTexture MID_BODY = SpriteTexture.of(STORAGE_ELEMENTS)
        .setSprite(GAUGE_MID_U, GAUGE_MID_V, GAUGE_WIDTH, GAUGE_MID_HEIGHT);
    private static final SpriteTexture BOTTOM_CAP = SpriteTexture.of(STORAGE_ELEMENTS)
        .setSprite(GAUGE_BOTTOM_U, GAUGE_BOTTOM_V, GAUGE_WIDTH, GAUGE_CAP_HEIGHT);

    private final StorageHostAnimatedRatio animatedRatio;
    private float ratio;

    private StorageHostLoadGauge(Supplier<Float> ratio, StorageHostAnimatedRatio animatedRatio) {
        this.animatedRatio = animatedRatio;
        bind(DataBindingBuilder.floatValS2C(ratio).build());
    }

    static StorageHostLoadGauge bindRatio(Supplier<Float> ratio, StorageHostAnimatedRatio animatedRatio) {
        return new StorageHostLoadGauge(ratio, animatedRatio);
    }

    @Override
    public void drawContents(GUIContext guiContext) {
        super.drawContents(guiContext);
        float clamped = (float)animatedRatio.value();
        if (clamped <= 0.0F && !animatedRatio.infinite()) {
            return;
        }

        float height = getSizeHeight();
        float bottom = getPositionY() + height;
        float top;
        int color;
        if (animatedRatio.infinite()) {
            top = getPositionY();
            color = 0x22CA6CFF;
        } else {
            float bodyHeight = Math.max(0.0F, height - GAUGE_CAP_HEIGHT);
            float barHeight = Math.round(bodyHeight * clamped);
            top = bottom - barHeight - GAUGE_CAP_HEIGHT;
            color = StorageHostText.gaugeColor(clamped);
        }
        drawGaugeSegment(
            guiContext,
            getPositionX(),
            getSizeWidth(),
            top,
            bottom,
            color
        );
    }

    @Override
    public Float getValue() {
        return ratio;
    }

    @Override
    public IDataSource<Float> setValue(@Nullable Float value) {
        ratio = value == null ? 0.0F : value;
        animatedRatio.setTarget(ratio);
        return this;
    }

    private static void drawGaugeSegment(GUIContext guiContext, float x, float width, float top, float bottom, int color) {
        if (width <= 0.0F || bottom <= top) {
            return;
        }

        guiContext.drawTexture(TOP_CAP.copy().setColor(color), x, top, width, GAUGE_CAP_HEIGHT);
        float midStart = top + GAUGE_CAP_HEIGHT / 2.0F + 1.0F;
        float midEnd = bottom - GAUGE_CAP_HEIGHT / 2.0F + 1.0F;
        for (float drawY = midStart; drawY < midEnd; drawY += 1.0F) {
            guiContext.drawTexture(MID_BODY.copy().setColor(color), x, drawY, width, GAUGE_MID_HEIGHT);
        }
        guiContext.drawTexture(BOTTOM_CAP.copy().setColor(color), x, bottom - GAUGE_CAP_HEIGHT, width, GAUGE_CAP_HEIGHT);
    }

}
