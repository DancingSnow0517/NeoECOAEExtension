package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Theme;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import net.minecraft.network.chat.Component;

/**
 * A floating overlay window with a visible title bar, close button, and
 * title-bar drag support. Only the visible title bar area is draggable.
 */
public class NEFloatingWindow extends NEWindow {

    /** Height of the visible draggable title bar (px from top). */
    protected static final int TITLE_BAR_HEIGHT = 14;

    protected NEButton closeBtn;
    protected Runnable onClose;

    // Drag state (client-side only)
    protected boolean dragging;
    protected int dragOffsetX;
    protected int dragOffsetY;

    // Clamp bounds
    protected Integer dragMinX, dragMinY, dragMaxX, dragMaxY;

    public NEFloatingWindow(int x, int y, int width, int height,
                            Component title,
                            IGuiTexture frame, IGuiTexture content,
                            Runnable onClose) {
        super(x, y, width, height, title, frame, content);
        this.onClose = onClose;
        buildTitleBar();
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

    public void setDragBounds(int minX, int minY, int maxX, int maxY) {
        this.dragMinX = minX;
        this.dragMinY = minY;
        this.dragMaxX = maxX;
        this.dragMaxY = maxY;
    }

    /** Draw a visible title bar background strip so the draggable area is obvious. */
    protected void buildTitleBar() {
        // Dark strip at the top as the visible title bar
        addWidget(new ImageWidget(0, 0, getSizeWidth(), TITLE_BAR_HEIGHT,
            NELDLib1Textures.SCROLLBAR_TRACK));
    }

    protected void buildCloseButton() {
        int btnSize = 10;
        int btnX = getSizeWidth() - btnSize - 5;
        int btnY = 2;
        closeBtn = NEButton.text(btnX, btnY, btnSize, btnSize,
            "\u2715", NELDLib1Theme.TEXT_TITLE,
            data -> { if (onClose != null) onClose.run(); });
        addWidget(closeBtn);
    }

    // ── Drag logic ──

    /** Only the visible title bar strip (top TITLE_BAR_HEIGHT px) is draggable. */
    protected boolean isInTitleBar(double mx, double my) {
        return my >= 0 && my < TITLE_BAR_HEIGHT
            && mx >= 0 && mx < getSizeWidth();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;

        // Check if close button is under mouse first
        if (closeBtn != null && closeBtn.isVisible()) {
            double cx = mouseX - closeBtn.getSelfPositionX();
            double cy = mouseY - closeBtn.getSelfPositionY();
            if (closeBtn.isMouseOverElement(cx, cy)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        // Start drag only in visible title bar area
        if (button == 0 && isInTitleBar(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = (int) mouseX;
            dragOffsetY = (int) mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                 double dragX, double dragY) {
        if (!dragging || !isVisible()) return false;
        int newX = getX() + (int) dragX;
        int newY = getY() + (int) dragY;
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
