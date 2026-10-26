package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Modal, read-only preview; only the explicit confirmation sends a migration request. */
final class ECOBulkMigrationDialog extends UIElement {
    static final int PREVIEW_LIMIT = 6;
    private static final int WIDTH = 216;
    private static final int PADDING = 12;
    private static final int PREVIEW_TOP = 40;
    private static final int ROW_HEIGHT = 20;
    private static final int COLUMN_WIDTH = (WIDTH - PADDING * 2) / 2;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HELP_LEFT = WIDTH - PADDING - 16;
    private static final SpriteTexture BACKGROUND = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/vendor/ae2/context_background.png"))
        .setSprite(0, 0, 256, 256).setBorder(2);

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
            if (x >= HELP_LEFT && x < HELP_LEFT + 16 && y >= 6 && y < 22) {
                event.hoverTooltips = HoverTooltips.empty().append(text("help"));
            } else if (x >= PADDING && x < WIDTH - PADDING && y >= PREVIEW_TOP) {
                int column = (int) ((x - PADDING) / COLUMN_WIDTH);
                int index = (int) ((y - PREVIEW_TOP) / ROW_HEIGHT) * 2 + column;
                if (index < items.size()) {
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
            ItemStack.parse(registries, raw).filter(stack -> !stack.isEmpty()).ifPresent(items::add);
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

    private int previewRows() { return (items.size() + 1) / 2; }

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
        context.drawTexture(BACKGROUND, x, y, WIDTH, panelHeight());
        context.drawTexture(NETextures.AE2_BUTTON, x + HELP_LEFT, y + 6, 16, 16);
        context.graphics.flush();
        drawText(context, Component.literal("?"), HELP_LEFT + 5, 10, 0xFFFFFF, 8);
        drawText(context, text("title"), PADDING, 10, 0x413F54, HELP_LEFT - PADDING - 4);
        drawText(context, text("description"), PADDING, 24, 0x666477, WIDTH - PADDING * 2);
        for (int i = 0; i < items.size(); i++) {
            int left = PADDING + i % 2 * COLUMN_WIDTH, top = PREVIEW_TOP + i / 2 * ROW_HEIGHT;
            context.graphics.renderItem(items.get(i), Math.round(x + left), Math.round(y + top));
            drawText(context, items.get(i).getHoverName(), left + 20, top + 4, 0x413F54, COLUMN_WIDTH - 24);
        }
        if (!loaded || items.isEmpty()) {
            drawText(context, text(!loaded ? "loading" : expectedCell == null ? "unavailable" : "empty"),
                PADDING, PREVIEW_TOP + 5, 0x666477, WIDTH - PADDING * 2);
        }
        Component summary = total > PREVIEW_LIMIT
            ? Component.translatable("gui.neoecoae.storage.bulk_cell.migrate.confirm.more", total - PREVIEW_LIMIT)
            : text("hint");
        if (loaded && !items.isEmpty()) {
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
