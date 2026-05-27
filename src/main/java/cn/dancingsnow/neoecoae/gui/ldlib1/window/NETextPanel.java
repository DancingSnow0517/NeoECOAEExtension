package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Theme;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Widgets;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Compact scroller-like text panel mimicking the LDLib2 ScrollerView.
 * Fixed 220×160 dark panel with light text rows and decorative scrollbar.
 */
public class NETextPanel extends NEWindow {

    private int nextLineY;
    private final int lineSpacing;
    private final int textX;

    public NETextPanel(int x, int y, int width, int height,
                       Component title, int lineSpacing) {
        super(x, y, width, height, title,
            NELDLib1Textures.BACKGROUND,
            NELDLib1Textures.CRAFTING_BACKGROUND_DARK,
            3, 14, 3, 3);
        this.lineSpacing = lineSpacing;
        this.textX = 6;
        this.nextLineY = this.contentY + 1; // slight offset from title bar

        // Title uses light text on dark panel, not too close to top edge
        setTitleColor(NELDLib1Theme.TEXT_TITLE);

        // Decorative scrollbar on right edge
        addWidget(NELDLib1Widgets.scrollbarTrack(
            contentX + contentWidth - 6, contentY,
            4, contentHeight));
    }

    /** Add a dynamic text line with light-on-dark styling. */
    public NETextPanel addLine(Supplier<String> text) {
        var label = NELDLib1Widgets.dynamicLabelLight(
            contentX + textX, nextLineY, text);
        addWidget(label);
        nextLineY += lineSpacing;
        return this;
    }

    /** Add a static text line. */
    public NETextPanel addLine(Component text) {
        return addLine(text::getString);
    }

    /** Reset the line cursor. */
    public void resetLines() {
        nextLineY = contentY + 1;
    }
}
