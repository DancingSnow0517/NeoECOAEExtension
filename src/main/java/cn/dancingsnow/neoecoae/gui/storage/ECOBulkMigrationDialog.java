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
    private static final int WIDTH = 240;
    private static final int HEIGHT = 158;
    private static final int BUTTON_TOP = 132;
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
            if (x >= -17 && x < 1 && y >= 4 && y < 22) {
                event.hoverTooltips = HoverTooltips.empty().append(text("help"));
            } else if (y >= 40 && y < 106) {
                int column = x >= 16 && x < 120 ? 0 : x >= 128 && x < 232 ? 1 : -1;
                int index = (int) ((y - 40) / 22) * 2 + column;
                if (column >= 0 && index < items.size()) {
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
    private float panelY() { return getPositionY() + (getSizeHeight() - HEIGHT) / 2; }

    private int button(float x, float y) {
        x -= panelX();
        y -= panelY();
        if (y < BUTTON_TOP || y >= BUTTON_TOP + 20) return -1;
        return x >= 16 && x < 104 ? 0 : x >= WIDTH - 104 && x < WIDTH - 16 ? 1 : -1;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        float x = panelX(), y = panelY();
        context.graphics.flush();
        context.graphics.fill(Math.round(getPositionX()), Math.round(getPositionY()),
            Math.round(getPositionX() + getSizeWidth()), Math.round(getPositionY() + getSizeHeight()), 0x88000000);
        context.drawTexture(BACKGROUND, x, y, WIDTH, HEIGHT);
        context.drawTexture(NETextures.AE2_BUTTON, x - 17, y + 4, 18, 18);
        context.graphics.flush();
        drawText(context, Component.literal("?"), -11, 9, 0xFFFFFF, 12);
        drawText(context, text("title"), 12, 10, 0x413F54, WIDTH - 24);
        drawText(context, text("description"), 12, 24, 0x666477, WIDTH - 24);
        for (int i = 0; i < items.size(); i++) {
            int left = 16 + i % 2 * 112, top = 40 + i / 2 * 22;
            context.graphics.renderItem(items.get(i), Math.round(x + left), Math.round(y + top));
            drawText(context, items.get(i).getHoverName(), left + 20, top + 4, 0x413F54, 80);
        }
        if (!loaded || items.isEmpty()) {
            drawText(context, text(!loaded ? "loading" : expectedCell == null ? "unavailable" : "empty"),
                16, 65, 0x666477, WIDTH - 32);
        }
        Component summary = total > PREVIEW_LIMIT
            ? Component.translatable("gui.neoecoae.storage.bulk_cell.migrate.confirm.more", total - PREVIEW_LIMIT)
            : text("hint");
        if (loaded && !items.isEmpty()) drawText(context, summary, 12, 113, 0x666477, WIDTH - 24);
        drawButton(context, 0, 16, Component.translatable("gui.ok"), loaded && expectedCell != null && !items.isEmpty());
        drawButton(context, 1, WIDTH - 104, Component.translatable("gui.cancel"), true);
    }

    private void drawButton(GUIContext context, int index, int left, Component label, boolean enabled) {
        var texture = !enabled ? NETextures.AE2_BUTTON_DISABLED
            : hovered == index ? NETextures.AE2_BUTTON_HIGHLIGHTED : NETextures.AE2_BUTTON;
        context.drawTexture(texture, panelX() + left, panelY() + BUTTON_TOP, 88, 20);
        context.graphics.flush();
        var font = Minecraft.getInstance().font;
        drawText(context, label, left + (88 - font.width(label)) / 2F, BUTTON_TOP + 6,
            enabled ? 0xFFFFFF : 0xA0A0A0, 88);
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
