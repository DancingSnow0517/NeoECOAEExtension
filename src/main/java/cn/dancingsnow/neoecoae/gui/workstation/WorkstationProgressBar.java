package cn.dancingsnow.neoecoae.gui.workstation;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

final class WorkstationProgressBar extends UIElement implements IBindable<Float> {
    private static final int WIDTH = 16;
    private static final int HEIGHT = 3;

    private final int maxProcessingSteps;

    WorkstationProgressBar(int maxProcessingSteps) {
        this.maxProcessingSteps = Math.max(1, maxProcessingSteps);
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
        int width = Mth.floor(WIDTH * value / maxProcessingSteps);
        if (width > 0) {
            guiContext.drawTexture(WorkstationTextures.widget(0, 0, width, HEIGHT),
                getPositionX(), getPositionY(), width, HEIGHT);
        }
    }
}
