package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Theme;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A vertical tool button bar using NEButton for proper text/icon layering.
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

    /** Add a text-labeled button using NEButton proper layering. */
    public NEToolBar addButton(String label, Component tooltip, Consumer<ClickData> onClick) {
        int btnX = 2;
        int btnY = nextY;
        var btn = NEButton.text(btnX, btnY, buttonSize, buttonSize,
            label, NELDLib1Theme.BUTTON_TEXT_LIGHT, onClick);
        if (tooltip != null) btn.tooltip(tooltip);
        addWidget(btn);
        nextY += buttonSize + padding;
        setSize(getSizeWidth(), nextY + 2);
        return this;
    }

    /** Add an icon button using NEButton.icon. */
    public NEToolBar addIconButton(IGuiTexture icon, Component tooltip, Consumer<ClickData> onClick) {
        int btnX = 2;
        int btnY = nextY;
        var btn = NEButton.icon(btnX, btnY, buttonSize, icon, tooltip, onClick);
        addWidget(btn);
        nextY += buttonSize + padding;
        setSize(getSizeWidth(), nextY + 2);
        return this;
    }

    public NEToolBar addButton(Component label, Component tooltip, Consumer<ClickData> onClick) {
        return addButton(label.getString(), tooltip, onClick);
    }
}
