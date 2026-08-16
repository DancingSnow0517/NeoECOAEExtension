package cn.dancingsnow.neoecoae.gui.storage;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** A movable 9-column view of the infinite storage entries. */
public final class StorageBigIntPreviewUI {
    private static final int COLUMNS = 9;
    private static final int VISIBLE_ROWS = 6;
    private static final int SLOT_SIZE = 18;
    private static final int CONTENT_WIDTH = COLUMNS * SLOT_SIZE;
    private static final int CONTENT_HEIGHT = VISIBLE_ROWS * SLOT_SIZE;
    private static final int WINDOW_WIDTH = 180;
    private static final int WINDOW_HEIGHT = 138;

    private StorageBigIntPreviewUI() {
    }

    public static UIElement createFloatingPanel(
        Supplier<HolderLookup.Provider> registries,
        Supplier<List<StorageHostHugeStackList.Entry>> entries
    ) {
        UIElement window = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(12)
            .top(18)
            .display(TaffyDisplay.NONE)
            .width(WINDOW_WIDTH)
            .height(WINDOW_HEIGHT)
        ).setOverflowVisible(true).style(style -> style.backgroundTexture(NETextures.CARD_BACKGROUND));

        UIElement titleBar = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(0)
            .top(0)
            .width(WINDOW_WIDTH)
            .height(20)
        );
        titleBar.addChild(new TextElement()
            .setText(Component.translatable("gui.neoecoae.storage.bigint_preview"))
            .textStyle(style -> style.adaptiveHeight(true).adaptiveWidth(false)
                .textWrap(TextWrap.NONE).textColor(0x3f3d52).textShadow(false))
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(7).top(6).width(148).height(12)));
        titleBar.addChild(closeButton(window));
        WindowDragHelper.setDragMove(titleBar, window, null, null);
        window.addChild(titleBar);

        window.addChild(new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(7)
            .top(22)
            .width(CONTENT_WIDTH)
            .height(CONTENT_HEIGHT)
        ).addClass("panel_border"));
        window.addChild(new StorageHostHugeStackList(registries, entries, CONTENT_WIDTH, CONTENT_HEIGHT, COLUMNS)
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(7).top(22)));
        return window;
    }

    public static Button createInlineOpenButton(UIElement window) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(AETextures.icon(Icon.VIEW_MODE_ALL))
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)));
        button.getChildren().getLast().layout(layout -> layout.width(14).height(14));
        button.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.HOVER_TOOLTIPS,
            event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gui.neoecoae.storage.bigint_preview.open")), null, null, null
            ));
        return button;
    }

    private static Button closeButton(UIElement window) {
        Button button = new Button().noText()
            .addPostIcon(AETextures.icon(Icon.BACK))
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)));
        button.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(158).top(2).width(16).height(16));
        return button;
    }
}
