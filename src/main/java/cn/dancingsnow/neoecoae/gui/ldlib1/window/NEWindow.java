package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;

/**
 * A bordered window with a title bar, content background, and optional close
 * button. Children added via {@link #addRelative} use coordinates relative to
 * the content area (inside the border).
 *
 * <p>Built on {@link NEWidgetGroup} so it supports visibility toggle and
 * can be added to any {@code ModularUI} as a top-level widget.</p>
 */
public class NEWindow extends NEWidgetGroup {

    protected final int contentX;
    protected final int contentY;
    protected final int contentWidth;
    protected final int contentHeight;

    protected Component title;
    protected int titleColor = 0xFFE8E8F0; // light text default
    protected LabelWidget titleLabel;

    protected IGuiTexture frameTexture;
    protected IGuiTexture contentTexture;

    /**
     * @param x          absolute x on screen
     * @param y          absolute y on screen
     * @param width      total window width (including border)
     * @param height     total window height (including title bar)
     * @param title      window title (use {@link Component#translatable} or {@link Component#literal})
     * @param frame      outer border texture (e.g. BACKGROUND)
     * @param content    inner content background (e.g. CRAFTING_BACKGROUND_DARK)
     * @param padLeft    content area left padding (inside frame)
     * @param padTop     content area top padding (below title)
     * @param padRight   content area right padding
     * @param padBottom  content area bottom padding
     */
    public NEWindow(int x, int y, int width, int height,
                    Component title,
                    IGuiTexture frame, IGuiTexture content,
                    int padLeft, int padTop, int padRight, int padBottom) {
        super(x, y, width, height);
        this.title = title;
        this.frameTexture = frame;
        this.contentTexture = content;

        this.contentX = padLeft;
        this.contentY = padTop;
        this.contentWidth = width - padLeft - padRight;
        this.contentHeight = height - padTop - padBottom;

        buildFrame();
    }

    /**
     * Simplified constructor with sensible defaults (4px border padding).
     */
    public NEWindow(int x, int y, int width, int height,
                    Component title,
                    IGuiTexture frame, IGuiTexture content) {
        this(x, y, width, height, title, frame, content, 4, 12, 4, 4);
    }

    protected void buildFrame() {
        // Outer frame (covers entire window area)
        if (frameTexture != null) {
            addWidget(new ImageWidget(0, 0, getSizeWidth(), getSizeHeight(), frameTexture));
        }
        // Inner content area (inset)
        if (contentTexture != null) {
            addWidget(new ImageWidget(contentX, contentY, contentWidth, contentHeight, contentTexture));
        }
        // Title label
        titleLabel = new LabelWidget(contentX + 4, contentY - 8, title)
            .setTextColor(titleColor)
            .setDropShadow(false);
        addWidget(titleLabel);
    }

    // ── Title ──

    public void setTitle(Component title) {
        this.title = title;
        if (titleLabel != null) {
            // Re-create label with new text; LDLib LabelWidget doesn't support setText
            var old = titleLabel;
            titleLabel = new LabelWidget(old.getSelfPositionX(), old.getSelfPositionY(), title)
                .setTextColor(titleColor)
                .setDropShadow(false);
            removeWidget(old);
            addWidget(titleLabel);
        }
    }

    public void setTitleColor(int color) {
        this.titleColor = color;
        if (titleLabel != null) {
            titleLabel.setTextColor(color);
        }
    }

    // ── Convenience ──

    /** Content area left edge in absolute coordinates. */
    public int contentAbsX() {
        return getPositionX() + contentX;
    }

    /** Content area top edge in absolute coordinates. */
    public int contentAbsY() {
        return getPositionY() + contentY;
    }

    /** Content area width. */
    public int contentW() {
        return contentWidth;
    }

    /** Content area height. */
    public int contentH() {
        return contentHeight;
    }

    // ── Update title through translatable keys ──

    public void setTitleKey(String key, Object... args) {
        setTitle(Component.translatable(key, args));
    }
}
