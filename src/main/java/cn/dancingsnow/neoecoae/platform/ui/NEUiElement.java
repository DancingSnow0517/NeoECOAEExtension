package cn.dancingsnow.neoecoae.platform.ui;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import java.util.ArrayList;
import java.util.List;

/** A project-owned layout node whose rendering, slots, actions and sync are real LDLib1 widgets. */
public final class NEUiElement extends WidgetGroup {
    private final UiLayout.Node layout;
    private final List<NEUiElement> elements = new ArrayList<>();
    private final Widget content;

    public NEUiElement() { this(null, 0, 0); }

    public NEUiElement(Widget content, int width, int height) {
        super(0, 0, width, height);
        this.layout = new UiLayout.Node(width, height);
        this.content = content;
        if (content != null) addWidget(content);
    }

    public UiLayout.Node layoutNode() { return layout; }

    public NEUiElement addChild(NEUiElement child) {
        elements.add(child);
        layout.children.add(child.layout);
        addWidget(child);
        return this;
    }

    public void arrange() {
        UiLayout.arrange(layout);
        applyBounds();
    }

    private void applyBounds() {
        setSelfPosition(new Position(Math.round(layout.x), Math.round(layout.y)));
        setSize(new Size(Math.round(layout.width), Math.round(layout.height)));
        setVisible(layout.style.display);
        setActive(layout.style.display);
        if (content != null) {
            content.setSelfPosition(Position.ORIGIN);
            content.setSize(getSize());
        }
        elements.forEach(NEUiElement::applyBounds);
    }
}
