package cn.dancingsnow.neoecoae.gui.storage;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Context actions for one configured ECO bulk cell. */
final class ECOBulkCellActionsPopup extends UIElement {
    private static final int WIDTH = 126;
    private static final int ROW_HEIGHT = 20;
    private static final int HEIGHT = ROW_HEIGHT * 3;
    private static final SpriteTexture BACKGROUND = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/vendor/ae2/context_background.png"))
        .setSprite(0, 0, 256, 256).setBorder(2);
    private static final List<Component> LABELS = List.of(
        Component.translatable("gui.neoecoae.storage.bulk_cell.action.migrate"),
        Component.translatable("gui.neoecoae.storage.bulk_cell.action.priority"),
        Component.translatable("gui.neoecoae.storage.bulk_cell.action.compression_display")
    );

    private final Consumer<BlockPos> migrate;
    private final Runnable priority;
    private final Consumer<BlockPos> compressionDisplay;
    private BlockPos position;
    private int hovered = -1;
    private int pressed = -1;

    ECOBulkCellActionsPopup(Consumer<BlockPos> migrate, Runnable priority, Consumer<BlockPos> compressionDisplay) {
        this.migrate = migrate;
        this.priority = priority;
        this.compressionDisplay = compressionDisplay;
        setId("eco-bulk-cell-actions");
        setFocusable(true);
        setDisplay(false);
        style(style -> style.zIndex(400).backgroundTexture(BACKGROUND));
        setOverflowVisible(true);
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

    void open(BlockPos position, float x, float y, int parentWidth, int parentHeight) {
        this.position = position;
        this.hovered = this.pressed = -1;
        int left = Math.max(0, Math.min(Math.max(0, parentWidth - WIDTH), Math.round(x)));
        int top = Math.max(0, Math.min(Math.max(0, parentHeight - HEIGHT), Math.round(y)));
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top)
            .width(WIDTH).height(HEIGHT));
        setDisplay(true);
        focus();
    }

    void close() {
        blur();
        setDisplay(false);
        position = null;
        hovered = pressed = -1;
    }

    private int row(double x, double y) {
        if (x < getPositionX() || x >= getPositionX() + WIDTH
            || y < getPositionY() || y >= getPositionY() + HEIGHT) return -1;
        return (int) ((y - getPositionY()) / ROW_HEIGHT);
    }

    private void activate(int row) {
        if (position == null || row < 0 || row >= LABELS.size()) return;
        BlockPos selected = position;
        close();
        switch (row) {
            case 0 -> migrate.accept(selected);
            case 1 -> priority.run();
            case 2 -> compressionDisplay.accept(selected);
            default -> { }
        }
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (hovered >= 0) {
            context.graphics.fill(Math.round(getPositionX() + 4), Math.round(getPositionY() + hovered * ROW_HEIGHT + 2),
                Math.round(getPositionX() + WIDTH - 4), Math.round(getPositionY() + (hovered + 1) * ROW_HEIGHT - 2),
                0x807B81B8);
        }
    }

    @Override
    public void drawContents(GUIContext context) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < LABELS.size(); i++) {
            Component label = LABELS.get(i);
            int color = i == hovered ? 0xFFFFFF : 0x413F54;
            context.graphics.drawString(font, label.getString(), Math.round(getPositionX() + 8),
                Math.round(getPositionY() + i * ROW_HEIGHT + 6), 0xFF000000 | color, false);
        }
    }
}
