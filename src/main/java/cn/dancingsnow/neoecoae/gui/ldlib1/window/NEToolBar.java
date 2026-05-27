package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A vertical tool button bar, typically placed on the left side of a
 * controller UI. Buttons are square with the default LDLib button texture.
 */
public class NEToolBar extends NEWidgetGroup {

    private int nextY = 0;
    private final int buttonSize;
    private final int padding;

    public NEToolBar(int x, int y) {
        this(x, y, 20, 2);
    }

    public NEToolBar(int x, int y, int buttonSize, int padding) {
        super(x, y, buttonSize + 4, 100);
        this.buttonSize = buttonSize;
        this.padding = padding;
    }

    /**
     * Add a text-labeled button using the proven squareButton pattern.
     */
    public NEToolBar addButton(String label, Component tooltip, Consumer<ClickData> onClick) {
        int btnX = 2;
        int btnY = nextY;

        var btn = new ButtonWidget(btnX, btnY, buttonSize, buttonSize,
            NELDLib1Textures.text(() -> label, 0xFFFFFFFF, Math.max(1, buttonSize - 4))
                .setBackgroundTexture(NELDLib1Textures.BUTTON),
            onClick)
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER)
            .setClickedTexture(NELDLib1Textures.BUTTON_HIGHLIGHTED);
        if (tooltip != null) {
            btn.setHoverTooltips(tooltip);
        }
        addWidget(btn);

        nextY += buttonSize + padding;
        setSize(getSizeWidth(), nextY + 2);
        return this;
    }

    /**
     * Add a button with an icon texture (e.g. AE2 hammer icon) instead of text.
     */
    public NEToolBar addIconButton(IGuiTexture icon, Component tooltip, Consumer<ClickData> onClick) {
        int btnX = 2;
        int btnY = nextY;

        var btn = new ButtonWidget(btnX, btnY, buttonSize, buttonSize,
            icon, onClick)
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER)
            .setClickedTexture(NELDLib1Textures.BUTTON_HIGHLIGHTED);
        if (tooltip != null) {
            btn.setHoverTooltips(tooltip);
        }
        addWidget(btn);

        nextY += buttonSize + padding;
        setSize(getSizeWidth(), nextY + 2);
        return this;
    }

    /** Add a button with a Component label (convenience). */
    public NEToolBar addButton(Component label, Component tooltip, Consumer<ClickData> onClick) {
        return addButton(label.getString(), tooltip, onClick);
    }
}
