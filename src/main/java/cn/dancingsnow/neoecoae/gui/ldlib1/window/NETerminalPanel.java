package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Widgets;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Dark terminal-style status panel with light text rows and a decorative
 * scrollbar on the right edge.
 *
 * <p>Built on {@link NEWindow}; provides {@link #addStatusLine(String, Supplier)}
 * to add labeled status rows.</p>
 */
public class NETerminalPanel extends NEWindow {

    private int nextLineY;
    private final int lineSpacing;
    private final int textX;

    /**
     * @param x          absolute x
     * @param y          absolute y
     * @param width      total width
     * @param height     total height
     * @param title      panel title (e.g. "ECO - L9 Storage System")
     * @param lineSpacing vertical spacing between status rows (e.g. 11)
     */
    public NETerminalPanel(int x, int y, int width, int height,
                           Component title, int lineSpacing) {
        super(x, y, width, height, title,
            NELDLib1Textures.BACKGROUND,
            NELDLib1Textures.CRAFTING_BACKGROUND_DARK,
            4, 14, 4, 4);
        this.lineSpacing = lineSpacing;
        this.textX = 4;
        this.nextLineY = this.contentY + 2;

        // Title uses light color on dark background
        setTitleColor(0xFFE8E8F0);

        // Decorative scrollbar track on right edge
        addWidget(NELDLib1Widgets.scrollbarTrack(
            contentX + contentWidth - 6, contentY + 2,
            4, contentHeight - 4));
    }

    /**
     * Add a labeled status row.
     *
     * @param labelKey translation key for the label (e.g. "gui.neoecoae.common.formed")
     * @param value    supplier for the value string
     * @return this panel for chaining
     */
    public NETerminalPanel addStatusLine(String labelKey, Supplier<String> value) {
        var label = NELDLib1Widgets.dynamicLabelLight(
            contentX + textX, nextLineY,
            () -> Component.translatable(labelKey).getString() + ": " + value.get()
        );
        addWidget(label);
        nextLineY += lineSpacing;
        return this;
    }

    /**
     * Add a simple dynamic text line (no label prefix).
     */
    public NETerminalPanel addLine(Supplier<String> text) {
        var label = NELDLib1Widgets.dynamicLabelLight(
            contentX + textX, nextLineY,
            text
        );
        addWidget(label);
        nextLineY += lineSpacing;
        return this;
    }

    /**
     * Add a static label line.
     */
    public NETerminalPanel addLine(Component text) {
        var label = new LabelWidget(contentX + textX, nextLineY, text)
            .setTextColor(0xFFE8E8F0)
            .setDropShadow(false);
        addWidget(label);
        nextLineY += lineSpacing;
        return this;
    }

    /** Reset line cursor to the top of the content area. */
    public void resetLines() {
        nextLineY = contentY + 2;
    }
}
