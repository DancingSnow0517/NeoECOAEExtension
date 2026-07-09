package cn.dancingsnow.neoecoae.gui;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

abstract class HostTaskListElement extends UIElement implements IBindable<CompoundTag> {
    private static final String NBT_SEQUENCE = "seq";
    private static final String NBT_UPDATES = "updates";
    private static final String NBT_REMOVED = "removed";
    private static final String NBT_ORDER = "order";
    private static final String NBT_TOTAL = "total";

    private static final int MAX_SYNCED_TASKS = 96;
    private static final int MAX_SYNCED_TASK_TAG_CHARS = 24_000;

    private final Supplier<HolderLookup.Provider> registries;
    private final Supplier<List<ComputationTaskEntry>> tasks;
    private final int panelWidth;
    private final int panelHeight;
    private final int cardX;
    private final int cardY;
    private final int cardWidth;
    private final int cardHeight;
    private final int cardStride;
    private final int listBottomY;
    private final int scrollbarWidth;

    private List<ComputationTaskEntry> syncedTasks = List.of();
    private int syncedTotalTasks;
    private Map<String, ComputationTaskEntry> lastServerTasks = Map.of();
    private List<String> lastServerOrder = List.of();
    private CompoundTag lastSyncPayload = new CompoundTag();
    private long syncSequence;
    private int lastServerTotalTasks;
    private int scrollOffset;

    HostTaskListElement(
        Supplier<HolderLookup.Provider> registries,
        Supplier<List<ComputationTaskEntry>> tasks,
        int panelWidth,
        int panelHeight,
        int cardX,
        int cardY,
        int cardWidth,
        int cardHeight,
        int cardStride,
        int listBottomY,
        int scrollbarWidth
    ) {
        this.registries = registries;
        this.tasks = tasks;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.cardX = cardX;
        this.cardY = cardY;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.cardStride = cardStride;
        this.listBottomY = listBottomY;
        this.scrollbarWidth = scrollbarWidth;
        bind(DataBindingBuilder.create(
            () -> createTaskDelta(registries.get(), this.tasks.get()),
            ignored -> {
            }).syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (syncedTasks.size() <= visibleTaskCardCount()) {
                scrollOffset = 0;
                return;
            }
            scrollOffset = clampTaskScrollOffset(scrollOffset + (event.deltaY < 0 ? 1 : -1), syncedTasks.size());
            event.stopImmediatePropagation();
        });
        for (int row = 0; row < visibleTaskCardCount(); row++) {
            addChild(createTaskHitbox(row));
        }
    }

    @Override
    public void drawContents(GUIContext guiContext) {
        Font font = Minecraft.getInstance().font;
        float x = getPositionX();
        float y = getPositionY();
        drawString(
            guiContext,
            font,
            Component.translatable("gui.neoecoae.crafting.tasks").getString(),
            x + titleX(),
            y + titleY(),
            StorageHostText.PRIMARY
        );
        drawRightString(
            guiContext,
            font,
            taskCountText(),
            x + countRightX(),
            y + titleY(),
            StorageHostText.VALUE
        );

        scrollOffset = clampTaskScrollOffset(scrollOffset, syncedTasks.size());
        if (syncedTasks.isEmpty()) {
            String emptyText = Component.translatable("gui.neoecoae.crafting.no_tasks").getString();
            drawString(guiContext, font, emptyText, x + emptyTextX(font, emptyText), y + emptyTextY(), StorageHostText.MUTED);
            return;
        }

        int visible = Math.min(visibleTaskCardCount(), syncedTasks.size() - scrollOffset);
        guiContext.graphics.enableScissor(
            Math.round(x + scissorLeft()),
            Math.round(y + cardY),
            Math.round(x + scissorRight()),
            Math.round(y + listBottomY + 1)
        );
        for (int i = 0; i < visible; i++) {
            drawTaskCard(guiContext, font, syncedTasks.get(scrollOffset + i), x + cardX, y + cardY + i * cardStride);
        }
        guiContext.graphics.disableScissor();
        drawScrollbar(guiContext, x + scrollbarX(), y + cardY, syncedTasks.size(), visibleTaskCardCount());
    }

    protected abstract List<Component> tooltipLines(ComputationTaskEntry entry);

    protected abstract void drawTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, float x, float y);

    protected int titleX() {
        return 8;
    }

    protected int titleY() {
        return 6;
    }

    protected int countRightX() {
        return panelWidth - 8;
    }

    protected int scissorLeft() {
        return 4;
    }

    protected int scissorRight() {
        return panelWidth - 4;
    }

    protected int scrollbarX() {
        return panelWidth - 5;
    }

    protected float emptyTextX(Font font, String text) {
        return (panelWidth - font.width(text)) / 2.0F;
    }

    protected float emptyTextY() {
        return panelHeight / 2.0F - 4.0F;
    }

    protected static void drawRightString(GUIContext guiContext, Font font, String text, float rightX, float y, int color) {
        drawString(guiContext, font, text, rightX - font.width(text), y, color);
    }

    protected static void drawString(GUIContext guiContext, Font font, String text, float x, float y, int color) {
        guiContext.graphics.drawString(font, text, x, y, color, false);
    }

    private UIElement createTaskHitbox(int row) {
        UIElement hitbox = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(cardX);
            layout.top(cardY + row * cardStride);
            layout.width(cardWidth);
            layout.height(cardHeight);
        });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            ComputationTaskEntry entry = taskAtVisibleRow(row);
            if (entry == null) {
                return;
            }
            List<Component> lines = tooltipLines(entry);
            event.hoverTooltips = HoverTooltips.empty().append(lines.toArray(Component[]::new));
        });
        return hitbox;
    }

    @Nullable
    private ComputationTaskEntry taskAtVisibleRow(int row) {
        scrollOffset = clampTaskScrollOffset(scrollOffset, syncedTasks.size());
        int visible = Math.min(visibleTaskCardCount(), syncedTasks.size() - scrollOffset);
        if (row < 0 || row >= visible) {
            return null;
        }
        return syncedTasks.get(scrollOffset + row);
    }

    @Override
    public CompoundTag getValue() {
        return lastSyncPayload.copy();
    }

    @Override
    public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
        lastSyncPayload = value == null ? new CompoundTag() : value.copy();
        applyTaskDelta(registries.get(), lastSyncPayload);
        return this;
    }

    private CompoundTag createTaskDelta(HolderLookup.Provider registries, List<ComputationTaskEntry> entries) {
        PreparedTasks current = prepareTasks(registries, entries);
        Map<String, ComputationTaskEntry> currentById = current.byId();
        List<String> currentOrder = current.order();

        ListTag updates = new ListTag();
        for (Map.Entry<String, ComputationTaskEntry> entry : currentById.entrySet()) {
            ComputationTaskEntry previous = lastServerTasks.get(entry.getKey());
            if (!entry.getValue().equals(previous)) {
                updates.add(current.tagsById().get(entry.getKey()));
            }
        }

        ListTag removed = new ListTag();
        for (String previousId : lastServerTasks.keySet()) {
            if (!currentById.containsKey(previousId)) {
                removed.add(StringTag.valueOf(previousId));
            }
        }

        boolean orderChanged = !currentOrder.equals(lastServerOrder);
        boolean totalChanged = current.totalTasks() != lastServerTotalTasks;
        if (updates.isEmpty() && removed.isEmpty() && !orderChanged && !totalChanged) {
            return lastSyncPayload;
        }

        CompoundTag payload = new CompoundTag();
        payload.putLong(NBT_SEQUENCE, ++syncSequence);
        payload.putInt(NBT_TOTAL, current.totalTasks());
        if (!updates.isEmpty()) {
            payload.put(NBT_UPDATES, updates);
        }
        if (!removed.isEmpty()) {
            payload.put(NBT_REMOVED, removed);
        }
        payload.put(NBT_ORDER, writeOrder(currentOrder));

        lastServerTasks = Map.copyOf(currentById);
        lastServerOrder = List.copyOf(currentOrder);
        lastServerTotalTasks = current.totalTasks();
        lastSyncPayload = payload;
        return payload;
    }

    private PreparedTasks prepareTasks(HolderLookup.Provider registries, List<ComputationTaskEntry> entries) {
        List<ComputationTaskEntry> source = entries == null ? List.of() : entries;
        Map<String, ComputationTaskEntry> byId = new LinkedHashMap<>();
        Map<String, CompoundTag> tagsById = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        int remainingBudget = MAX_SYNCED_TASK_TAG_CHARS;
        for (ComputationTaskEntry entry : source) {
            if (byId.size() >= MAX_SYNCED_TASKS || byId.containsKey(entry.id())) {
                continue;
            }
            CompoundTag tag = entry.writeToNBT(registries);
            int estimatedSize = tag.toString().length();
            if (estimatedSize > remainingBudget) {
                break;
            }
            byId.put(entry.id(), entry);
            tagsById.put(entry.id(), tag);
            order.add(entry.id());
            remainingBudget -= estimatedSize;
        }
        return new PreparedTasks(byId, tagsById, order, source.size());
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
        syncedTotalTasks = payload.contains(NBT_TOTAL, Tag.TAG_INT) ? payload.getInt(NBT_TOTAL) : syncedTasks.size();
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

    private String taskCountText() {
        if (syncedTotalTasks > syncedTasks.size()) {
            return ComputationTaskCards.compactAmount(syncedTasks.size())
                + "/"
                + ComputationTaskCards.compactAmount(syncedTotalTasks);
        }
        return ComputationTaskCards.compactAmount(syncedTasks.size());
    }

    private void drawScrollbar(GUIContext guiContext, float x, float y, int total, int visible) {
        if (total <= visible) {
            return;
        }
        int height = Math.max(1, listBottomY - cardY);
        guiContext.graphics.fill((int)x, (int)y, (int)x + scrollbarWidth, (int)y + height, 0xAA17141E);
        int thumbHeight = Math.max(10, height * visible / Math.max(visible, total));
        int maxOffset = Math.max(1, total - visible);
        int thumbY = (int)y + Math.round((height - thumbHeight) * (scrollOffset / (float)maxOffset));
        guiContext.graphics.fill((int)x, thumbY, (int)x + scrollbarWidth, thumbY + thumbHeight, 0xFF8B83A0);
    }

    private int visibleTaskCardCount() {
        int space = listBottomY - cardY;
        return space < cardHeight ? 1 : Math.max(1, 1 + (space - cardHeight) / cardStride);
    }

    private int clampTaskScrollOffset(int value, int total) {
        return Math.clamp(value, 0, Math.max(0, total - visibleTaskCardCount()));
    }

    private record PreparedTasks(
        Map<String, ComputationTaskEntry> byId,
        Map<String, CompoundTag> tagsById,
        List<String> order,
        int totalTasks
    ) {
    }
}
