package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

final class WorkstationProgressBar extends UIElement implements IBindable<Float> {
    private static final ResourceLocation TEXTURE = NeoECOAE.id("textures/guis/inscriber.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int BACKGROUND_SOURCE_X = 135;
    private static final int BACKGROUND_SOURCE_Y = 39;
    private static final int FILL_SOURCE_X = 176;
    private static final int WIDTH = 6;
    private static final int HEIGHT = 18;

    private final int maxProcessingSteps;

    WorkstationProgressBar(int maxProcessingSteps) {
        this.maxProcessingSteps = maxProcessingSteps;
    }

    private float value;

    @Override
    public IDataSource<Float> setValue(@Nullable Float value) {
        this.value = value == null ? 0.0F : Mth.clamp(value, 0.0F, maxProcessingSteps);
        return this;
    }

    @Override
    public Float getValue() {
        return value;
    }

    @Override
    public void drawContents(GUIContext guiContext) {
        int x = Mth.floor(getPositionX());
        int y = Mth.floor(getPositionY());
        guiContext.graphics.blit(
            TEXTURE,
            x,
            y,
            0,
            BACKGROUND_SOURCE_X,
            BACKGROUND_SOURCE_Y,
            WIDTH,
            HEIGHT,
            TEXTURE_SIZE,
            TEXTURE_SIZE
        );

        int current = Mth.floor(value);
        int diff = HEIGHT - HEIGHT * current / maxProcessingSteps;
        int visibleHeight = HEIGHT - diff;
        if (visibleHeight <= 0) {
            return;
        }
        guiContext.graphics.blit(
            TEXTURE,
            x,
            y + diff,
            0,
            FILL_SOURCE_X,
            diff,
            WIDTH,
            visibleHeight,
            TEXTURE_SIZE,
            TEXTURE_SIZE
        );
    }
}
