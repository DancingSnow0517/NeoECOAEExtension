package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Context actions for an installed cell, with additional actions for bulk cells. */
final class ECOBulkCellActionsPopup extends UIElement {
    private static final int MIN_WIDTH = 126;
    private static final int VERTICAL_PADDING = 4;
    private static final int FIRST_ROW_HEIGHT = 22;
    private static final int DIVIDER_HEIGHT = 2;
    private static final int DIVIDER_GAP = 2;
    private static final int ROW_HEIGHT = 20;
    private static final int ACTIONS_TOP = VERTICAL_PADDING + FIRST_ROW_HEIGHT + DIVIDER_GAP * 2 + DIVIDER_HEIGHT;
    private static final int PRIORITY_COLOR = 0xFF006600;
    private enum Action {
        MIGRATE("gui.neoecoae.storage.bulk_cell.action.migrate"),
        PRIORITY("gui.neoecoae.storage.bulk_cell.action.priority"),
        CONFIGURE("gui.neoecoae.storage.cell.action.configure"),
        COMPRESSION("gui.neoecoae.storage.bulk_cell.action.compression_display");

        private final Component label;
        Action(String key) { label = Component.translatable(key); }
    }

    private final Consumer<BlockPos> migrate;
    private final BiConsumer<BlockPos, UUID> priority;
    private final BiConsumer<BlockPos, UUID> configure;
    private final Consumer<BlockPos> compressionDisplay;
    private BlockPos position;
    private UUID installation;
    private Supplier<StorageHostUI.CellEntry> selected;
    private List<Action> actions = List.of();
    private boolean bulk;
    private int width = MIN_WIDTH;
    private int height;
    private int hovered = -1;
    private int pressed = -1;

    ECOBulkCellActionsPopup(Consumer<BlockPos> migrate, BiConsumer<BlockPos, UUID> priority,
        BiConsumer<BlockPos, UUID> configure, Consumer<BlockPos> compressionDisplay) {
        this.migrate = migrate;
        this.priority = priority;
        this.configure = configure;
        this.compressionDisplay = compressionDisplay;
        setId("eco-bulk-cell-actions");
        setFocusable(true);
        setDisplay(false);
        style(style -> style.zIndex(400).backgroundTexture(NETextures.AE2_CONTEXT_BACKGROUND));
        setOverflowVisible(true);
        // Stop tooltip lookup here instead of falling through to the disk underneath the menu.
        addEventListener(UIEvents.HOVER_TOOLTIPS,
            event -> event.hoverTooltips = new HoverTooltips(List.of(), null, null, null));
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            pressed = event.button == 0 ? row(event.x, event.y) : -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_UP, event -> {
            int row = row(event.x, event.y);
            if (event.button == 0 && row == pressed) activate(row);
            pressed = -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_MOVE, event -> hovered = row(event.x, event.y));
        addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == 256) {
                close();
                event.stopImmediatePropagation();
            }
        });
    }

    void open(StorageHostUI.CellEntry cell, Supplier<StorageHostUI.CellEntry> selected,
        float x, float y, int parentWidth, int parentHeight) {
        this.position = cell.position();
        this.installation = cell.interactionId();
        this.selected = selected;
        bulk = cell.stack().getItem() instanceof ECOBulkStorageCellItem;
        // 1.20.1 has no remote AE2 cell workbench, so CONFIGURE is never offered.
        actions = bulk ? List.of(Action.MIGRATE, Action.PRIORITY, Action.COMPRESSION)
            : List.of(Action.PRIORITY);
        var font = Minecraft.getInstance().font;
        width = MIN_WIDTH;
        for (var action : actions) {
            int numberWidth = action == Action.PRIORITY ? font.width(String.valueOf(Integer.MIN_VALUE)) + 12 : 0;
            width = Math.max(width, font.width(action.label) + numberWidth + 16);
        }
        height = (bulk ? ACTIONS_TOP + ROW_HEIGHT * (actions.size() - 1)
            : VERTICAL_PADDING + ROW_HEIGHT * actions.size()) + VERTICAL_PADDING;
        this.hovered = this.pressed = -1;
        int left = Math.max(0, Math.min(Math.max(0, parentWidth - width), Math.round(x)));
        int top = Math.max(0, Math.min(Math.max(0, parentHeight - height), Math.round(y)));
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top)
            .width(width).height(height));
        setDisplay(true);
        focus();
    }

    void close() {
        blur();
        setDisplay(false);
        position = null;
        installation = null;
        selected = null;
        hovered = pressed = -1;
    }

    boolean isOpen() {
        return position != null;
    }

    private int row(double x, double y) {
        if (x < getPositionX() || x >= getPositionX() + width
            || y < getPositionY() || y >= getPositionY() + height) return -1;
        int localY = (int) (y - getPositionY());
        if (localY < VERTICAL_PADDING || localY >= height - VERTICAL_PADDING) return -1;
        if (!bulk) return (localY - VERTICAL_PADDING) / ROW_HEIGHT;
        if (localY < VERTICAL_PADDING + FIRST_ROW_HEIGHT) return 0;
        if (localY < ACTIONS_TOP) return -1;
        int action = (localY - ACTIONS_TOP) / ROW_HEIGHT;
        return action < actions.size() - 1 ? action + 1 : -1;
    }

    private int rowTop(int row) {
        return !bulk ? VERTICAL_PADDING + row * ROW_HEIGHT
            : row == 0 ? VERTICAL_PADDING : ACTIONS_TOP + (row - 1) * ROW_HEIGHT;
    }

    private void activate(int row) {
        if (position == null || row < 0 || row >= actions.size() || !selectionValid()) return;
        BlockPos selected = position;
        UUID expected = installation;
        Action action = actions.get(row);
        close();
        switch (action) {
            case MIGRATE -> migrate.accept(selected);
            case PRIORITY -> priority.accept(selected, expected);
            case CONFIGURE -> configure.accept(selected, expected);
            case COMPRESSION -> compressionDisplay.accept(selected);
        }
    }

    private boolean selectionValid() {
        if (selected == null || installation == null) return false;
        var cell = selected.get();
        return installation.equals(cell.interactionId()) && position.equals(cell.position())
            && ECOStorageCellSettings.canConfigure(cell.stack());
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        // Flush the style background before drawing the divider and hover overlay.
        context.graphics.flush();
        if (bulk) {
            int dividerX = Math.round(getPositionX() + 5);
            int dividerY = Math.round(getPositionY() + VERTICAL_PADDING + FIRST_ROW_HEIGHT + DIVIDER_GAP);
            int dividerRight = Math.round(getPositionX() + width - 5);
            context.graphics.fill(dividerX, dividerY, dividerRight, dividerY + 1, 0xFF8B8A9A);
            context.graphics.fill(dividerX, dividerY + 1, dividerRight, dividerY + DIVIDER_HEIGHT, 0xFFE8E7EF);
        }
        if (hovered >= 0) {
            int top = rowTop(hovered);
            int rowHeight = bulk && hovered == 0 ? FIRST_ROW_HEIGHT : ROW_HEIGHT;
            context.graphics.fill(Math.round(getPositionX() + 4), Math.round(getPositionY() + top + 2),
                Math.round(getPositionX() + width - 4), Math.round(getPositionY() + top + rowHeight - 2),
                0x807B81B8);
        }
        context.graphics.flush();
    }

    @Override
    public void drawContents(GUIContext context) {
        if (!selectionValid()) {
            close();
            return;
        }
        // UIElement invokes drawBackgroundAdditional from its drawContents implementation.
        super.drawContents(context);
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            int color = i == hovered ? 0xFFFFFF : 0x413F54;
            int rowHeight = bulk && i == 0 ? FIRST_ROW_HEIGHT : ROW_HEIGHT;
            int textY = Math.round(getPositionY() + rowTop(i) + (rowHeight - font.lineHeight) / 2F);
            context.graphics.drawString(font, action.label.getString(), Math.round(getPositionX() + 8),
                textY, 0xFF000000 | color, false);
            if (action == Action.PRIORITY) {
                String value = String.valueOf(ECOStorageCellSettings.priority(selected.get().stack()));
                context.graphics.drawString(font, value, Math.round(getPositionX() + width - 8 - font.width(value)),
                    textY, PRIORITY_COLOR, false);
            }
        }
    }
}
