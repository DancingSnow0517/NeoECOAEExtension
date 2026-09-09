package cn.dancingsnow.neoecoae.gui.storage;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Item-only view of the storage directly owned by a storage host. */
public final class StorageItemDetailsUI {
    private static final int WINDOW_WIDTH = 195;
    private static final int WINDOW_HEIGHT = 134;
    private static final int GRID_LEFT = 7;
    private static final int GRID_TOP = 17;
    private static final int COLUMNS = 9;
    private static final int ROWS = 6;
    private static final int SLOT_SIZE = 18;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int SCROLL_LEFT = 179;
    private static final int SCROLL_TOP = 17;
    private static final int SCROLL_WIDTH = 5;
    private static final int SCROLL_HEIGHT = ROWS * SLOT_SIZE;

    private StorageItemDetailsUI() {
    }

    public record StoredItem(AEItemKey key, BigInteger amount) {
    }

    @FunctionalInterface
    public interface InteractionHandler {
        void interact(@Nullable AEItemKey key, int mouseButton, boolean shiftDown);
    }

    public record Config(
        Player player,
        Supplier<List<StoredItem>> items,
        InteractionHandler interactionHandler
    ) {
    }

    public static UIElement createFloatingPanel(Config config) {
        PageState page = new PageState();
        Scroller.Vertical scrollbar = createScrollbar(page);
        ItemGrid grid = new ItemGrid(config, page, scrollbar);

        UIElement window = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(6)
            .top(6)
            .display(TaffyDisplay.NONE)
            .width(WINDOW_WIDTH)
            .height(WINDOW_HEIGHT))
            .setOverflowVisible(true)
            .style(style -> style.backgroundTexture(NETextures.BIG_INTEGER_ITEM));

        UIElement dragArea = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(0)
            .top(0)
            .width(WINDOW_WIDTH)
            .height(GRID_TOP));
        WindowDragHelper.setDragMove(dragArea, window, null, null);
        window.addChild(dragArea);
        window.addChild(grid.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(GRID_LEFT)
            .top(GRID_TOP)
            .width(COLUMNS * SLOT_SIZE)
            .height(ROWS * SLOT_SIZE)));
        window.addChild(scrollbar.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(SCROLL_LEFT)
            .top(SCROLL_TOP)
            .width(SCROLL_WIDTH)
            .height(SCROLL_HEIGHT)));
        return window;
    }

    public static Button createInlineOpenButton(UIElement window) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(AETextures.icon(Icon.S_TERMINAL))
            .setOnClick(event -> window.layout(layout -> layout.display(
                window.isDisplayed() ? TaffyDisplay.NONE : TaffyDisplay.FLEX)));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.of(Component.translatable("gui.neoecoae.storage.item_details")), null, null, null));
        button.layout(layout -> layout.width(18).height(20));
        return button;
    }

    private static Scroller.Vertical createScrollbar(PageState page) {
        Scroller.Vertical scrollbar = new Scroller.Vertical();
        scrollbar.setRange(0, 1);
        scrollbar.scrollerStyle(style -> style.scrollDelta(1.0F).scrollBarSize(100));
        scrollbar.headButton(button -> button.buttonStyle(style -> style
            .baseTexture(IGuiTexture.EMPTY).hoverTexture(IGuiTexture.EMPTY).pressedTexture(IGuiTexture.EMPTY)));
        scrollbar.tailButton(button -> button.buttonStyle(style -> style
            .baseTexture(IGuiTexture.EMPTY).hoverTexture(IGuiTexture.EMPTY).pressedTexture(IGuiTexture.EMPTY)));
        scrollbar.scrollContainer(track -> track.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        scrollbar.scrollBar(handle -> handle.buttonStyle(style -> style
            .baseTexture(NETextures.AE_SCROLLBAR_THUMB)
            .hoverTexture(NETextures.BUTTON_HOVER)
            .pressedTexture(NETextures.BUTTON_HIGHLIGHTED)));
        scrollbar.bind(DataBindingBuilder.floatVal(
            () -> page.row,
            value -> page.row = Math.max(0, Math.round(value))
        ).build());
        return scrollbar;
    }

    private static final class PageState {
        private float row;
    }

    private static final class ItemGrid extends UIElement implements IBindable<CompoundTag> {
        private static final String NBT_ENTRIES = "entries";
        private static final String NBT_STACK = "stack";
        private static final String NBT_AMOUNT = "amount";
        private static final String NBT_SERIAL = "serial";
        private static final String NBT_ROWS = "rows";
        private static final IGuiTexture HOVER = new ColorRectTexture(0x6686C5DA);

        private final Config config;
        private final PageState page;
        private final Scroller.Vertical scrollbar;
        private final Map<AEItemKey, Long> serialByKey = new HashMap<>();
        private final Map<Long, AEItemKey> keyBySerial = new HashMap<>();
        private long nextSerial = 1;
        private List<VisibleItem> visibleItems = List.of();
        private CompoundTag syncedTag = new CompoundTag();

        private ItemGrid(Config config, PageState page, Scroller.Vertical scrollbar) {
            this.config = config;
            this.page = page;
            this.scrollbar = scrollbar;
            bind(DataBindingBuilder.create(this::writePage, ignored -> {
            }).syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
            addEventListener(UIEvents.MOUSE_WHEEL, this::scroll);
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                VisibleItem item = itemAt(event.x, event.y);
                event.command = (item == null ? -1L : item.serial()) + ":" + (event.isShiftDown() ? "1" : "0");
            });
            addServerEventListener(UIEvents.MOUSE_DOWN, event -> {
                ClickTarget target = parseTarget(event.command);
                config.interactionHandler().interact(
                    keyBySerial.get(target.serial()), event.button, target.shiftDown());
            });
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                VisibleItem item = itemAt(event.x, event.y);
                if (item == null) {
                    return;
                }
                List<Component> tooltip = new java.util.ArrayList<>(DrawerHelper.getItemToolTip(item.stack()));
                tooltip.add(Component.translatable("gui.neoecoae.storage.item_details.amount",
                    HostText.expandedStorageBytes(item.amount())));
                event.hoverTooltips = new HoverTooltips(
                    tooltip, item.stack().getTooltipImage().orElse(null), null, item.stack());
            });
        }

        private CompoundTag writePage() {
            List<StoredItem> allItems = config.items().get();
            int totalRows = Math.max(0, (allItems.size() + COLUMNS - 1) / COLUMNS - ROWS);
            page.row = Math.clamp(Math.round(page.row), 0, totalRows);
            int first = Math.round(page.row) * COLUMNS;
            CompoundTag result = new CompoundTag();
            result.putInt(NBT_ROWS, totalRows);
            ListTag entries = new ListTag();
            int end = Math.min(allItems.size(), first + PAGE_SIZE);
            for (int index = first; index < end; index++) {
                StoredItem item = allItems.get(index);
                long serial = serialByKey.computeIfAbsent(item.key(), ignored -> nextSerial++);
                keyBySerial.put(serial, item.key());
                CompoundTag entry = new CompoundTag();
                entry.put(NBT_STACK, item.key().toStack().save(configRegistryAccess()));
                entry.putString(NBT_AMOUNT, item.amount().toString());
                entry.putLong(NBT_SERIAL, serial);
                entries.add(entry);
            }
            result.put(NBT_ENTRIES, entries);
            return result;
        }

        private net.minecraft.core.HolderLookup.Provider configRegistryAccess() {
            return config.player().registryAccess();
        }

        private void scroll(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
            if ((scrollbar.getMaxValue() <= 1 && scrollbar.getScrollerStyle().scrollBarSize() >= 100)
                || event.deltaY == 0) {
                return;
            }
            scrollbar.setValue(scrollbar.getValue() + (event.deltaY < 0 ? 1 : -1));
            event.stopImmediatePropagation();
        }

        @Override
        public void drawContents(GUIContext context) {
            super.drawContents(context);
            float left = getPositionX();
            float top = getPositionY();
            for (int index = 0; index < PAGE_SIZE; index++) {
                int column = index % COLUMNS;
                int row = index / COLUMNS;
                float x = left + column * SLOT_SIZE;
                float y = top + row * SLOT_SIZE;
                if (index >= visibleItems.size()) {
                    continue;
                }
                VisibleItem item = visibleItems.get(index);
                DrawerHelper.drawItemStack(context.graphics, item.stack(), Math.round(x + 1), Math.round(y + 1),
                    context.elementColor, HostText.ae2Amount(item.amount()));
            }
            int hovered = slotIndexAt(context.mouseX, context.mouseY);
            if (hovered >= 0) {
                int column = hovered % COLUMNS;
                int row = hovered / COLUMNS;
                context.drawTexture(HOVER, left + column * SLOT_SIZE + 1, top + row * SLOT_SIZE + 1, 16, 16);
            }
        }

        @Nullable
        private VisibleItem itemAt(float mouseX, float mouseY) {
            int index = slotIndexAt(mouseX, mouseY);
            return index >= 0 && index < visibleItems.size() ? visibleItems.get(index) : null;
        }

        private int slotIndexAt(float mouseX, float mouseY) {
            int column = (int) ((mouseX - getPositionX()) / SLOT_SIZE);
            int row = (int) ((mouseY - getPositionY()) / SLOT_SIZE);
            if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
                return -1;
            }
            return row * COLUMNS + column;
        }

        @Override
        public CompoundTag getValue() {
            return syncedTag.copy();
        }

        @Override
        public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
            syncedTag = value == null ? new CompoundTag() : value.copy();
            int maxRows = Math.max(0, syncedTag.getInt(NBT_ROWS));
            scrollbar.setRange(0, Math.max(1, maxRows));
            scrollbar.setActive(maxRows > 0);
            scrollbar.scrollerStyle(style -> style
                .scrollDelta(maxRows == 0 ? 1.0F : 1.0F / maxRows)
                .scrollBarSize(maxRows == 0 ? 100.0F : ROWS * 100.0F / (ROWS + maxRows)));
            ListTag entries = syncedTag.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);
            java.util.ArrayList<VisibleItem> decoded = new java.util.ArrayList<>(entries.size());
            for (Tag raw : entries) {
                CompoundTag entry = (CompoundTag) raw;
                ItemStack.parse(configRegistryAccess(), entry.getCompound(NBT_STACK)).ifPresent(stack -> decoded.add(
                    new VisibleItem(stack, readAmount(entry.getString(NBT_AMOUNT)), entry.getLong(NBT_SERIAL))));
            }
            visibleItems = List.copyOf(decoded);
            return this;
        }

        private static ClickTarget parseTarget(@Nullable String command) {
            try {
                String[] parts = command == null ? new String[0] : command.split(":", 2);
                return new ClickTarget(Long.parseLong(parts[0]), parts.length > 1 && "1".equals(parts[1]));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                return new ClickTarget(-1L, false);
            }
        }

        private static BigInteger readAmount(String amount) {
            try {
                return new BigInteger(amount).max(BigInteger.ZERO);
            } catch (NumberFormatException ignored) {
                return BigInteger.ZERO;
            }
        }
    }

    private record VisibleItem(ItemStack stack, BigInteger amount, long serial) {
    }

    private record ClickTarget(long serial, boolean shiftDown) {
    }
}
