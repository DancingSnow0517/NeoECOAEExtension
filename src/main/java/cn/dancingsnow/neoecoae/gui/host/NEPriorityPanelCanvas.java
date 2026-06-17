package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;

public final class NEPriorityPanelCanvas extends NEAeWidgetCanvas {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 125;
    public static final int BACK_X = 152;
    public static final int BACK_Y = -5;
    public static final int BACK_W = 20;
    public static final int BACK_H = 20;
    public static final int INPUT_X = 60;
    public static final int INPUT_Y = 55;
    public static final int INPUT_W = 61;
    public static final int INPUT_H = 12;

    public static final int[] STEP_VALUES = {1, 10, 100, 1000, -1, -10, -100, -1000};
    public static final int[] STEP_X = {20, 48, 82, 120, 20, 48, 82, 120};
    public static final int[] STEP_Y = {30, 30, 30, 30, 72, 72, 72, 72};
    public static final int[] STEP_W = {22, 28, 32, 38, 22, 28, 32, 38};
    public static final int STEP_H = 20;

    public NEPriorityPanelCanvas() {
        super(WIDTH, HEIGHT);
    }

    @Override
    protected void drawWidget(GUIContext context) {
        drawPriorityBackground(context);
        drawDarkText(context, Component.translatableWithFallback("gui.neoecoae.storage_priority.title", "Priority"), 8, 6);
        for (int i = 0; i < STEP_VALUES.length; i++) {
            Component text = Component.literal(STEP_VALUES[i] > 0 ? "+" + STEP_VALUES[i] : String.valueOf(STEP_VALUES[i]));
            drawAeButton(context, STEP_X[i], STEP_Y[i], STEP_W[i], STEP_H, text,
                    hovered(context, STEP_X[i], STEP_Y[i], STEP_W[i], STEP_H));
        }
        drawPriorityValueSlot(context, INPUT_X, INPUT_Y, INPUT_W, INPUT_H,
                hovered(context, INPUT_X, INPUT_Y, INPUT_W, INPUT_H));
        drawTabIconButton(context, BACK_X, BACK_Y, NEAeSprite.BACK,
                hovered(context, BACK_X, BACK_Y, BACK_W, BACK_H));
        drawDarkText(context, Component.translatableWithFallback("gui.ae2.PriorityInsertionHint", "Insertion: Higher priority first"), 8, 98);
        drawDarkText(context, Component.translatableWithFallback("gui.ae2.PriorityExtractionHint", "Extraction: Lower priority first"), 8, 110);
    }
}
