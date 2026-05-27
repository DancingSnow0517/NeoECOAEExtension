package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;

/**
 * LDLib1 container widget with explicit relative-coordinate child management,
 * consistent visibility/activation control, and window-position API.
 *
 * <p>Extends LDLib's native {@link WidgetGroup} which already provides child
 * list, draw delegation, mouse hit-testing, and visibility cascade. This
 * subclass adds convenience methods for adding children at relative positions
 * and ensures {@code setVisible(false)} also disables mouse interaction.</p>
 */
public class NEWidgetGroup extends WidgetGroup {

    public NEWidgetGroup(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public NEWidgetGroup(Position position, com.lowdragmc.lowdraglib.utils.Size size) {
        super(position, size);
    }

    // ── Position API ──

    /** Move the entire group (and all children) to a new screen position. */
    public void moveTo(int x, int y) {
        setSelfPosition(x, y);
    }

    /** Current x position (relative to parent). */
    public int getX() {
        return getSelfPositionX();
    }

    /** Current y position (relative to parent). */
    public int getY() {
        return getSelfPositionY();
    }

    // ── Relative-coordinate child helpers ──

    /**
     * Add a child widget whose coordinates are relative to this group's
     * top-left corner. The child's self-position is preserved; LDLib
     * internally adds the parent offset for absolute positioning.
     */
    public <T extends Widget> T addRelative(T child, int relX, int relY) {
        child.setSelfPosition(relX, relY);
        addWidget(child);
        return child;
    }

    /**
     * Add a child widget at a relative position, with an initializer callback.
     */
    public <T extends Widget> T addRelative(T child, int relX, int relY,
                                             java.util.function.Consumer<T> initializer) {
        child.setSelfPosition(relX, relY);
        addWidget(child);
        initializer.accept(child);
        return child;
    }

    // ── Visibility + activation in one call ──

    /**
     * Toggle both visibility and activation so hidden groups don't intercept
     * mouse clicks meant for widgets behind them.
     */
    @Override
    public WidgetGroup setVisible(boolean visible) {
        super.setVisible(visible);
        setActive(visible);
        return this;
    }

    public boolean isHidden() {
        return !isVisible();
    }

    public void show() {
        setVisible(true);
    }

    public void hide() {
        setVisible(false);
    }

    public void toggle() {
        setVisible(!isVisible());
    }
}
