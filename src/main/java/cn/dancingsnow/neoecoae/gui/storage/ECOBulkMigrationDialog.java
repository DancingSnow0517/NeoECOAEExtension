package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Modal, read-only preview; only the explicit confirmation sends a migration request. */
final class ECOBulkMigrationDialog extends UIElement {
    static final int PREVIEW_LIMIT = 6;
    private static final int WIDTH = 240;
    private static final int PADDING = 14;
    private static final int PREVIEW_TOP = 34;
    private static final int ICON_SIZE = 16;
    private static final int ICON_STEP = 20;
    private static final int ROW_HEIGHT = ICON_SIZE;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    private final HolderLookup.Provider registries;
    private final BiConsumer<BlockPos, UUID> confirm;
    private final List<ItemStack> items = new ArrayList<>();
    private BlockPos position;
    private UUID expectedCell;
    private long sequence;
    private int total;
    private int hovered = -1;
    private int pressed = -1;
    private boolean loaded;

    ECOBulkMigrationDialog(HolderLookup.Provider registries, BiConsumer<BlockPos, UUID> confirm) {
        this.registries = registries;
        this.confirm = confirm;
        setId("eco-bulk-migration-confirm");
        setFocusable(true);
        setDisplay(false);
        style(style -> style.zIndex(1000));
        setOverflowVisible(true);
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            pressed = event.button == 0 ? button(event.x, event.y) : -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_UP, event -> {
            int button = button(event.x, event.y);
            if (event.button == 0 && button >= 0 && button == pressed) {
                if (button == 0) confirm();
                else close();
            }
            pressed = -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_MOVE, event -> {
            hovered = button(event.x, event.y);
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_WHEEL, event -> event.stopImmediatePropagation());
        addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == 256) close();
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            event.hoverTooltips = HoverTooltips.empty();
            var ui = getModularUI();
            if (ui == null) return;
            float x = ui.getLastMouseX() - panelX(), y = ui.getLastMouseY() - panelY();
            int left = previewLeft();
            if (!items.isEmpty() && y >= PREVIEW_TOP && y < PREVIEW_TOP + ROW_HEIGHT
                && x >= left && x < left + previewWidth()) {
                int index = (int) ((x - left) / ICON_STEP);
                if (index >= 0 && index < items.size()) {
                    event.hoverTooltips = HoverTooltips.empty().append(items.get(index).getHoverName());
                }
            }
            event.stopImmediatePropagation();
        });
    }

    long open(BlockPos position, float parentWidth, float parentHeight) {
        this.position = position;
        expectedCell = null;
        items.clear();
        total = 0;
        loaded = false;
        hovered = pressed = -1;
        sequence++;
        var ui = getModularUI();
        float width = ui == null ? parentWidth : Math.max(parentWidth, ui.getScreenWidth());
        float height = ui == null ? parentHeight : Math.max(parentHeight, ui.getScreenHeight());
        // Cover the screen outside the host too, including its side buttons and vanilla's drop area.
        float left = ui == null ? 0 : -ui.getLeftPos();
        float top = ui == null ? 0 : -ui.getTopPos();
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top)
            .width(width).height(height));
        setDisplay(true);
        focus();
        return sequence;
    }

    void accept(long request, CompoundTag preview) {
        if (position == null || request != sequence) return;
        loaded = true;
        expectedCell = preview.hasUUID("cell_id") ? preview.getUUID("cell_id") : null;
        total = Math.max(0, preview.getInt("total"));
        items.clear();
        for (var raw : preview.getList("items", Tag.TAG_COMPOUND)) {
            if (items.size() >= PREVIEW_LIMIT) break;
            ItemStack stack = ItemStack.of((net.minecraft.nbt.CompoundTag) raw);
            if (!stack.isEmpty()) items.add(stack);
        }
        hovered = pressed = -1;
    }

    void close() {
        sequence++;
        position = null;
        expectedCell = null;
        blur();
        setDisplay(false);
    }

    private void confirm() {
        if (!loaded || position == null || expectedCell == null || items.isEmpty()) return;
        BlockPos selected = position;
        UUID expected = expectedCell;
        close();
        confirm.accept(selected, expected);
    }

    private float panelX() { return getPositionX() + (getSizeWidth() - WIDTH) / 2; }
    private float panelY() { return getPositionY() + (getSizeHeight() - panelHeight()) / 2; }

    private int previewRows() { return items.isEmpty() ? 0 : 1; }

    private int previewWidth() {
        return items.isEmpty() ? 0 : items.size() * ICON_STEP - (ICON_STEP - ICON_SIZE);
    }

    private int previewLeft() { return (WIDTH - previewWidth()) / 2; }

    private int buttonTop() {
        return PREVIEW_TOP + (items.isEmpty() ? 24 : previewRows() * ROW_HEIGHT + 20);
    }

    int panelHeight() { return buttonTop() + BUTTON_HEIGHT + 8; }

    private int button(float x, float y) {
        x -= panelX();
        y -= panelY();
        if (y < buttonTop() || y >= buttonTop() + BUTTON_HEIGHT) return -1;
        return x >= PADDING && x < PADDING + BUTTON_WIDTH ? 0
            : x >= WIDTH - PADDING - BUTTON_WIDTH && x < WIDTH - PADDING ? 1 : -1;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        float x = panelX(), y = panelY();
        context.graphics.flush();
        context.graphics.fill(Math.round(getPositionX()), Math.round(getPositionY()),
            Math.round(getPositionX() + getSizeWidth()), Math.round(getPositionY() + getSizeHeight()), 0x88000000);
        context.drawTexture(NETextures.AE2_CONTEXT_BACKGROUND, x, y, WIDTH, panelHeight());
        context.graphics.flush();
        drawText(context, text("title"), PADDING, 10, 0x413F54, WIDTH - PADDING * 2);
        for (int i = 0; i < items.size(); i++) {
            int left = previewLeft() + i * ICON_STEP;
            context.graphics.renderItem(items.get(i), Math.round(x + left), Math.round(y + PREVIEW_TOP));
        }
        if (!loaded || items.isEmpty()) {
            drawText(context, text(!loaded ? "loading" : expectedCell == null ? "unavailable" : "empty"),
                PADDING, PREVIEW_TOP + 5, 0x666477, WIDTH - PADDING * 2);
        }
        Component summary = total > PREVIEW_LIMIT
            ? Component.translatable("gui.neoecoae.storage.bulk_cell.migrate.confirm.more", total - PREVIEW_LIMIT)
            : Component.empty();
        if (loaded && !items.isEmpty() && total > PREVIEW_LIMIT) {
            drawText(context, summary, PADDING, PREVIEW_TOP + previewRows() * ROW_HEIGHT + 2,
                0x666477, WIDTH - PADDING * 2);
        }
        drawButton(context, 0, PADDING, Component.translatable("gui.ok"), loaded && expectedCell != null && !items.isEmpty());
        drawButton(context, 1, WIDTH - PADDING - BUTTON_WIDTH, Component.translatable("gui.cancel"), true);
    }

    private void drawButton(GUIContext context, int index, int left, Component label, boolean enabled) {
        var texture = !enabled ? NETextures.AE2_BUTTON_DISABLED
            : hovered == index ? NETextures.AE2_BUTTON_HIGHLIGHTED : NETextures.AE2_BUTTON;
        context.drawTexture(texture, panelX() + left, panelY() + buttonTop(), BUTTON_WIDTH, BUTTON_HEIGHT);
        context.graphics.flush();
        var font = Minecraft.getInstance().font;
        drawText(context, label, left + (BUTTON_WIDTH - font.width(label)) / 2F, buttonTop() + 6,
            enabled ? 0xFFFFFF : 0xA0A0A0, BUTTON_WIDTH);
    }

    private void drawText(GUIContext context, Component value, float left, float top, int color, int width) {
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(value.getString(), width);
        context.graphics.drawString(font, label, Math.round(panelX() + left), Math.round(panelY() + top),
            0xFF000000 | color, false);
    }

    private static Component text(String suffix) {
        return Component.translatable("gui.neoecoae.storage.bulk_cell.migrate.confirm." + suffix);
    }
}
