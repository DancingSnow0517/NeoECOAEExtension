package cn.dancingsnow.neoecoae.gui;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class ComputationHostPanelUI {
    public static final int LEFT_PANEL_WIDTH = 162;
    public static final int RIGHT_PANEL_WIDTH = 156;
    public static final int PANEL_HEIGHT = 200;

    private static final int CPU_MODE_BUTTON_SIZE = 18;
    private static final int CPU_MODE_BUTTON_OFFSET = 2;
    private static final int LEFT_CAPACITY_HEIGHT = 108;
    private static final int LEFT_INVENTORY_HEIGHT = 88;
    private static final int PANEL_PADDING = 2;
    private static final int PANEL_GAP = 2;
    private static final int SCROLLBAR_HORIZONTAL_OFFSET = 2;
    private static final int PROGRESS_ROW_LABEL_WIDTH = 54;
    private static final int PROGRESS_ROW_BAR_WIDTH = 36;
    private static final int RIGHT_TASK_PANEL_X = 0;
    private static final int RIGHT_TASK_PANEL_Y = 0;
    private static final int RIGHT_TASK_PANEL_WIDTH = RIGHT_PANEL_WIDTH - 12;
    private static final int RIGHT_TASK_PANEL_HEIGHT = PANEL_HEIGHT - 15;
    private static final int TASK_CARD_X = RIGHT_TASK_PANEL_X + 6;
    private static final int TASK_CARD_Y = RIGHT_TASK_PANEL_Y + 19;
    private static final int TASK_CARD_WIDTH = RIGHT_TASK_PANEL_WIDTH - 12;
    private static final int TASK_CARD_HEIGHT = 28;
    private static final int TASK_CARD_STRIDE = 30;
    private static final int TASK_LIST_BOTTOM_Y = RIGHT_TASK_PANEL_Y + RIGHT_TASK_PANEL_HEIGHT - 3;
    private static final int TASK_SCROLLBAR_WIDTH = 3;

    private ComputationHostPanelUI() {
    }

    public record Config(
        LongSupplier usedBytes,
        LongSupplier totalBytes,
        LongSupplier availableBytes,
        IntSupplier usedThreads,
        IntSupplier totalThreads,
        IntSupplier parallelCount,
        Supplier<CpuSelectionMode> cpuSelectionMode,
        Runnable cycleCpuSelectionMode,
        Supplier<HolderLookup.Provider> registries,
        Supplier<List<ComputationTaskEntry>> tasks
    ) {
    }

    public static ScrollerView createLeftCapacityPanel(Config config) {
        ScrollerView panel = createPanel(LEFT_PANEL_WIDTH, LEFT_CAPACITY_HEIGHT);
        panel.addScrollViewChild(StorageHostElements.sectionLabel(
            () -> Component.translatable("gui.neoecoae.host.computation.capacity"),
            () -> StorageHostText.PRIMARY
        ));
        panel.addScrollViewChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.computation.cpu_storage"),
            () -> StorageHostText.byteProgress(config.usedBytes().getAsLong(), config.totalBytes().getAsLong()),
            config.usedBytes(),
            config.totalBytes()
        ));
        panel.addScrollViewChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.computation.thread_usage"),
            () -> StorageHostText.typeProgress(config.usedThreads().getAsInt(), config.totalThreads().getAsInt()),
            () -> config.usedThreads().getAsInt(),
            () -> config.totalThreads().getAsInt()
        ));
        panel.addScrollViewChild(valueRow(
            () -> Component.translatable("gui.neoecoae.host.computation.parallel_count"),
            () -> Component.literal(Integer.toString(config.parallelCount().getAsInt())),
            () -> StorageHostText.VALUE
        ));
        panel.addScrollViewChild(valueRow(
            () -> Component.translatable("gui.neoecoae.host.computation.free_memory"),
            () -> Component.literal(StorageHostText.byteProgress(config.availableBytes().getAsLong(), 0).usedText()),
            () -> StorageHostText.MUTED
        ));
        return panel;
    }

    public static Button createCpuSelectionButton(Config config) {
        Button button = new Button();
        UIElement icon = cpuSelectionIcon(config.cpuSelectionMode().get());
        button.noText();
        button.addChild(icon);
        button.setOnServerClick(event -> config.cycleCpuSelectionMode().run());
        button.addEventListener(UIEvents.TICK, event -> icon.style(style -> style.backgroundTexture(cpuSelectionModeIcon(config.cpuSelectionMode().get()))));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.of(
                ButtonToolTips.CpuSelectionMode.text(),
                cpuSelectionModeTooltip(config.cpuSelectionMode().get())
            ),
            null,
            null,
            null
        ));
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));
        return button;
    }

    private static UIElement cpuSelectionIcon(CpuSelectionMode mode) {
        return new UIElement()
            .layout(layout -> {
                layout.width(12);
                layout.height(12);
            })
            .style(style -> style.backgroundTexture(cpuSelectionModeIcon(mode)));
    }

    private static com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture cpuSelectionModeIcon(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> AETextures.icon(Icon.CRAFT_HAMMER);
            case PLAYER_ONLY -> AETextures.icon(Icon.S_TERMINAL);
            case MACHINE_ONLY -> AETextures.icon(Icon.S_MACHINE);
        };
    }

    private static Component cpuSelectionModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> ButtonToolTips.CpuSelectionModeAny.text();
            case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly.text();
            case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly.text();
        };
    }

    public static UIElement createInventoryPanel() {
        UIElement panel = new UIElement().layout(layout -> {
            layout.width(LEFT_PANEL_WIDTH);
            layout.height(LEFT_INVENTORY_HEIGHT);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        panel.addChild(new TextElement()
            .setText("container.inventory", true)
            .textStyle(ComputationHostPanelUI::inventoryTitleTextStyle));
        panel.addChild(new InventorySlots().layout(layout -> layout.marginTop(2)));
        return panel;
    }

    private static void inventoryTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }

    public static ScrollerView createRightPanel(Config config) {
        ScrollerView panel = createPanel(RIGHT_PANEL_WIDTH, PANEL_HEIGHT);
        panel.scrollerStyle(style -> style
            .verticalScrollDisplay(ScrollDisplay.NEVER)
            .horizontalScrollDisplay(ScrollDisplay.NEVER));
        panel.viewContainer(view -> {
            view.getLayout().paddingAll(0);
            view.addChild(StorageHostElements.absolute(
                new TaskListElement(config.registries(), config.tasks()),
                RIGHT_TASK_PANEL_X,
                RIGHT_TASK_PANEL_Y,
                RIGHT_TASK_PANEL_WIDTH,
                RIGHT_TASK_PANEL_HEIGHT
            ));
        });
        return panel;
    }

    private static ScrollerView createPanel(int width, int height) {
        return ECOHostWidgets.storagePanel(width, height, PANEL_PADDING, PANEL_GAP, SCROLLBAR_HORIZONTAL_OFFSET);
    }

    private static UIElement usageProgressRow(
        Supplier<Component> label,
        Supplier<StorageHostText.UsedTotal> text,
        LongSupplier used,
        LongSupplier max
    ) {
        UIElement row = StorageHostElements.horizontalRow(10, 2);
        row.addChild(StorageHostElements.textSegment(label, () -> StorageHostText.MUTED)
            .layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH)));
        row.addChild(new ProgressBar()
            .label(progressLabel -> progressLabel.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            .bind(DataBindingBuilder.floatValS2C(() -> StorageHostText.usageRatio(used.getAsLong(), max.getAsLong())).build())
            .layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4))
            .addClass("eco-host-progress"));

        UIElement value = StorageHostElements.horizontalRow(10, 0);
        value.addChild(StorageHostElements.textSegment(
            () -> Component.literal(text.get().usedText()),
            () -> StorageHostText.usedValueColor(used.getAsLong(), max.getAsLong())
        ));
        value.addChild(StorageHostElements.textSegment(() -> Component.literal(" / "), () -> StorageHostText.MUTED));
        value.addChild(StorageHostElements.textSegment(() -> Component.literal(text.get().maxText()), () -> StorageHostText.VALUE));
        row.addChild(value);
        return row;
    }

    private static UIElement valueRow(Supplier<Component> label, Supplier<Component> value, java.util.function.IntSupplier color) {
        UIElement row = StorageHostElements.horizontalRow(10, 2);
        row.addChild(StorageHostElements.textSegment(label, () -> StorageHostText.MUTED)
            .layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH)));
        row.addChild(StorageHostElements.textSegment(value, color));
        return row;
    }

    private static final class TaskListElement extends UIElement implements IBindable<CompoundTag> {
        private static final String NBT_SEQUENCE = "seq";
        private static final String NBT_UPDATES = "updates";
        private static final String NBT_REMOVED = "removed";
        private static final String NBT_ORDER = "order";

        private final Supplier<HolderLookup.Provider> registries;
        private final Supplier<List<ComputationTaskEntry>> tasks;
        private List<ComputationTaskEntry> syncedTasks = List.of();
        private Map<String, ComputationTaskEntry> lastServerTasks = Map.of();
        private List<String> lastServerOrder = List.of();
        private CompoundTag lastSyncPayload = new CompoundTag();
        private long syncSequence;
        private int scrollOffset;

        private TaskListElement(Supplier<HolderLookup.Provider> registries, Supplier<List<ComputationTaskEntry>> tasks) {
            this.registries = registries;
            this.tasks = tasks;
            bind(DataBindingBuilder.create(
                () -> createTaskDelta(registries.get(), this.tasks.get()),
                ignored -> {
                }).syncType(CompoundTag.class).c2sStrategy(com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy.NONE).build());
            addEventListener(UIEvents.MOUSE_WHEEL, event -> {
                List<ComputationTaskEntry> entries = safeTasks();
                int visible = visibleTaskCardCount();
                if (entries.size() <= visible) {
                    scrollOffset = 0;
                    return;
                }
                scrollOffset = clampTaskScrollOffset(scrollOffset + (event.deltaY < 0 ? 1 : -1), entries.size());
                event.stopImmediatePropagation();
            });
            for (int row = 0; row < visibleTaskCardCount(); row++) {
                addChild(createTaskHitbox(row));
            }
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            List<ComputationTaskEntry> entries = safeTasks();
            Font font = Minecraft.getInstance().font;
            float x = getPositionX();
            float y = getPositionY();
            drawString(
                guiContext,
                font,
                Component.translatable("gui.neoecoae.crafting.tasks").getString(),
                x + 8,
                y + 6,
                StorageHostText.PRIMARY,
                1.0F
            );
            drawRightString(
                guiContext,
                font,
                ComputationTaskCards.compactAmount(entries.size()),
                x + RIGHT_TASK_PANEL_WIDTH - 8,
                y + 6,
                StorageHostText.VALUE
            );

            scrollOffset = clampTaskScrollOffset(scrollOffset, entries.size());
            if (entries.isEmpty()) {
                String emptyText = Component.translatable("gui.neoecoae.crafting.no_tasks").getString();
                drawString(
                    guiContext,
                    font,
                    emptyText,
                    x + (RIGHT_TASK_PANEL_WIDTH - font.width(emptyText)) / 2.0F,
                    y + RIGHT_TASK_PANEL_HEIGHT / 2.0F - 4.0F,
                    StorageHostText.MUTED,
                    1.0F);
                return;
            }

            int visible = Math.min(visibleTaskCardCount(), entries.size() - scrollOffset);
            guiContext.graphics.enableScissor(
                Math.round(x + RIGHT_TASK_PANEL_X + 4),
                Math.round(y + TASK_CARD_Y),
                Math.round(x + RIGHT_TASK_PANEL_X + RIGHT_TASK_PANEL_WIDTH - 4),
                Math.round(y + TASK_LIST_BOTTOM_Y + 1)
            );
            for (int i = 0; i < visible; i++) {
                drawTaskCard(guiContext, font, entries.get(scrollOffset + i), x + TASK_CARD_X, y + TASK_CARD_Y + i * TASK_CARD_STRIDE);
            }
            guiContext.graphics.disableScissor();
            drawScrollbar(guiContext, x + RIGHT_TASK_PANEL_WIDTH - 5, y + TASK_CARD_Y, entries.size(), visibleTaskCardCount());
        }

        private UIElement createTaskHitbox(int row) {
            UIElement hitbox = new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(TASK_CARD_X);
                layout.top(TASK_CARD_Y + row * TASK_CARD_STRIDE);
                layout.width(TASK_CARD_WIDTH);
                layout.height(TASK_CARD_HEIGHT);
            });
            hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                ComputationTaskEntry entry = taskAtVisibleRow(row);
                if (entry == null) {
                    return;
                }
                List<Component> lines = ComputationTaskCards.tooltipLines(entry);
                event.hoverTooltips = HoverTooltips.empty().append(lines.toArray(Component[]::new));
            });
            return hitbox;
        }

        private ComputationTaskEntry taskAtVisibleRow(int row) {
            List<ComputationTaskEntry> entries = safeTasks();
            scrollOffset = clampTaskScrollOffset(scrollOffset, entries.size());
            int visible = Math.min(visibleTaskCardCount(), entries.size() - scrollOffset);
            if (row < 0 || row >= visible) {
                return null;
            }
            return entries.get(scrollOffset + row);
        }

        private List<ComputationTaskEntry> safeTasks() {
            return syncedTasks;
        }

        @Override
        public CompoundTag getValue() {
            return lastSyncPayload;
        }

        @Override
        public IDataSource<CompoundTag> setValue(CompoundTag value) {
            lastSyncPayload = value == null ? new CompoundTag() : value.copy();
            applyTaskDelta(registries.get(), lastSyncPayload);
            return this;
        }

        private CompoundTag createTaskDelta(HolderLookup.Provider registries, List<ComputationTaskEntry> entries) {
            List<ComputationTaskEntry> currentTasks = entries == null ? List.of() : entries;
            Map<String, ComputationTaskEntry> currentById = new LinkedHashMap<>();
            List<String> currentOrder = new ArrayList<>(currentTasks.size());
            for (ComputationTaskEntry entry : currentTasks) {
                currentById.put(entry.id(), entry);
                currentOrder.add(entry.id());
            }

            ListTag updates = new ListTag();
            for (ComputationTaskEntry entry : currentTasks) {
                ComputationTaskEntry previous = lastServerTasks.get(entry.id());
                if (!entry.equals(previous)) {
                    updates.add(entry.writeToNBT(registries));
                }
            }

            ListTag removed = new ListTag();
            for (String previousId : lastServerTasks.keySet()) {
                if (!currentById.containsKey(previousId)) {
                    removed.add(StringTag.valueOf(previousId));
                }
            }

            boolean orderChanged = !currentOrder.equals(lastServerOrder);
            if (updates.isEmpty() && removed.isEmpty() && !orderChanged) {
                return lastSyncPayload;
            }

            CompoundTag payload = new CompoundTag();
            payload.putLong(NBT_SEQUENCE, ++syncSequence);
            if (!updates.isEmpty()) {
                payload.put(NBT_UPDATES, updates);
            }
            if (!removed.isEmpty()) {
                payload.put(NBT_REMOVED, removed);
            }
            payload.put(NBT_ORDER, writeOrder(currentOrder));

            lastServerTasks = Map.copyOf(currentById);
            lastServerOrder = List.copyOf(currentOrder);
            lastSyncPayload = payload;
            return payload;
        }

        private void applyTaskDelta(HolderLookup.Provider registries, CompoundTag payload) {
            if (payload.isEmpty() && !payload.contains(NBT_SEQUENCE)) {
                return;
            }

            Map<String, ComputationTaskEntry> entriesById = new LinkedHashMap<>();
            for (ComputationTaskEntry entry : syncedTasks) {
                entriesById.put(entry.id(), entry);
            }

            if (payload.contains(NBT_REMOVED, Tag.TAG_LIST)) {
                ListTag removed = payload.getList(NBT_REMOVED, Tag.TAG_STRING);
                for (int i = 0; i < removed.size(); i++) {
                    entriesById.remove(removed.getString(i));
                }
            }

            if (payload.contains(NBT_UPDATES, Tag.TAG_LIST)) {
                ListTag updates = payload.getList(NBT_UPDATES, Tag.TAG_COMPOUND);
                for (int i = 0; i < updates.size(); i++) {
                    ComputationTaskEntry entry = ComputationTaskEntry.readFromNBT(registries, updates.getCompound(i));
                    entriesById.put(entry.id(), entry);
                }
            }

            if (payload.contains(NBT_ORDER, Tag.TAG_LIST)) {
                syncedTasks = orderTasks(entriesById, payload.getList(NBT_ORDER, Tag.TAG_STRING));
            } else {
                syncedTasks = List.copyOf(entriesById.values());
            }
            scrollOffset = clampTaskScrollOffset(scrollOffset, syncedTasks.size());
        }

        private static ListTag writeOrder(List<String> order) {
            ListTag orderTag = new ListTag();
            for (String id : order) {
                orderTag.add(StringTag.valueOf(id));
            }
            return orderTag;
        }

        private static List<ComputationTaskEntry> orderTasks(Map<String, ComputationTaskEntry> entriesById, ListTag order) {
            List<ComputationTaskEntry> ordered = new ArrayList<>(entriesById.size());
            for (int i = 0; i < order.size(); i++) {
                ComputationTaskEntry entry = entriesById.remove(order.getString(i));
                if (entry != null) {
                    ordered.add(entry);
                }
            }
            ordered.addAll(entriesById.values());
            return List.copyOf(ordered);
        }

        private void drawTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, float x, float y) {
            ComputationTaskCards.drawCard(guiContext, font, entry, Math.round(x), Math.round(y), TASK_CARD_WIDTH, TASK_CARD_HEIGHT);
        }

        private void drawScrollbar(GUIContext guiContext, float x, float y, int total, int visible) {
            if (total <= visible) {
                return;
            }
            int height = Math.max(1, TASK_LIST_BOTTOM_Y - TASK_CARD_Y);
            guiContext.graphics.fill((int)x, (int)y, (int)x + TASK_SCROLLBAR_WIDTH, (int)y + height, 0xAA17141E);
            int thumbHeight = Math.max(10, height * visible / Math.max(visible, total));
            int maxOffset = Math.max(1, total - visible);
            int thumbY = (int)y + Math.round((height - thumbHeight) * (scrollOffset / (float)maxOffset));
            guiContext.graphics.fill((int)x, thumbY, (int)x + TASK_SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF8B83A0);
        }

        private static int visibleTaskCardCount() {
            int space = TASK_LIST_BOTTOM_Y - TASK_CARD_Y;
            if (space < TASK_CARD_HEIGHT) {
                return 1;
            }
            return Math.max(1, 1 + (space - TASK_CARD_HEIGHT) / TASK_CARD_STRIDE);
        }

        private static int clampTaskScrollOffset(int value, int total) {
            int visible = visibleTaskCardCount();
            return Mth.clamp(value, 0, Math.max(0, total - visible));
        }

        private static void drawRightString(GUIContext guiContext, Font font, String text, float rightX, float y, int color) {
            drawString(guiContext, font, text, rightX - font.width(text), y, color, 1.0F);
        }

        private static void drawString(GUIContext guiContext, Font font, String text, float x, float y, int color, float scale) {
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(x, y, 0);
            guiContext.graphics.pose().scale(scale, scale, 1.0F);
            guiContext.graphics.drawString(font, text, 0, 0, color, false);
            guiContext.graphics.pose().popPose();
        }
    }
}
