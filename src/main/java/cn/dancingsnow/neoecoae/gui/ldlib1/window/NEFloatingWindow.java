package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import net.minecraft.network.chat.Component;

/**
 * A floating overlay window with a close button in the top-right corner
 * and title-bar drag support.
 *
 * <p>Built on {@link NEWindow}; supports {@link #show()}/{@link #hide()}/{@link #toggle()}
 * via the inherited {@link NEWidgetGroup} visibility system.</p>
 */
public class NEFloatingWindow extends NEWindow {

    /** Height of the draggable title bar area (px from top). */
    protected static final int TITLE_BAR_HEIGHT = 16;

    protected ButtonWidget closeButton;
    protected Runnable onClose;

    // Drag state (client-side only, not synced)
    protected boolean dragging;
    protected int dragOffsetX;
    protected int dragOffsetY;

    // Clamp bounds (minX, minY, maxX, maxY) — null = no limit
    protected Integer dragMinX, dragMinY, dragMaxX, dragMaxY;

    public NEFloatingWindow(int x, int y, int width, int height,
                            Component title,
                            IGuiTexture frame, IGuiTexture content,
                            Runnable onClose) {
        super(x, y, width, height, title, frame, content);
        this.onClose = onClose;
        buildCloseButton();
    }

    public NEFloatingWindow(int x, int y, int width, int height,
                            Component title, Runnable onClose) {
        this(x, y, width, height, title,
            NELDLib1Textures.BACKGROUND,
            NELDLib1Textures.CRAFTING_BACKGROUND_LIGHT,
            onClose);
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /** Set drag boundary clamping. */
    public void setDragBounds(int minX, int minY, int maxX, int maxY) {
        this.dragMinX = minX;
        this.dragMinY = minY;
        this.dragMaxX = maxX;
        this.dragMaxY = maxY;
    }

    protected void buildCloseButton() {
        int btnSize = 10;
        int btnX = getSizeWidth() - btnSize - 6;
        int btnY = 4;
        closeButton = new ButtonWidget(btnX, btnY, btnSize, btnSize,
            NELDLib1Textures.text("\u2715", 0xFFFFFFFF, btnSize)
                .setBackgroundTexture(NELDLib1Textures.BUTTON),
            (ClickData data) -> {
                if (onClose != null) onClose.run();
            })
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER);
        addWidget(closeButton);
    }

    // ── Drag logic ──

    /** Check if (mx, my) relative to this window falls within the title bar. */
    protected boolean isInTitleBar(double mx, double my) {
        return my >= 0 && my <= TITLE_BAR_HEIGHT
            && mx >= 0 && mx <= getSizeWidth();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        // Let children (close button, etc.) handle first
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        // Start drag if click is in title bar
        if (button == 0 && isInTitleBar(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = (int) mouseX;
            dragOffsetY = (int) mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                 double dragX, double dragY) {
        if (!dragging || !isVisible()) return false;
        // New position = current self position + drag delta
        int newX = getX() + (int) dragX;
        int newY = getY() + (int) dragY;
        // Clamp
        if (dragMinX != null) newX = Math.max(dragMinX, Math.min(dragMaxX, newX));
        if (dragMinY != null) newY = Math.max(dragMinY, Math.min(dragMaxY, newY));
        moveTo(newX, newY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void buildFrame() {
        super.buildFrame();
    }
}
