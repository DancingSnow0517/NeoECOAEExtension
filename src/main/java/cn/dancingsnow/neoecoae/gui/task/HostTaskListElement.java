package cn.dancingsnow.neoecoae.gui.task;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class HostTaskListElement extends UIElement implements IBindable<CompoundTag> {
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
    private final HostTaskListSyncState syncState;

    private List<ComputationTaskEntry> syncedTasks = List.of();
    private int syncedTotalTasks;
    private int scrollOffset;

    public HostTaskListElement(
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
        this.syncState = new HostTaskListSyncState(registries);
        bind(DataBindingBuilder.create(
            () -> syncState.createDelta(this.tasks.get()),
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
            HostText.PRIMARY
        );
        drawRightString(
            guiContext,
            font,
            taskCountText(),
            x + countRightX(),
            y + titleY(),
            HostText.VALUE
        );

        scrollOffset = clampTaskScrollOffset(scrollOffset, syncedTasks.size());
        if (syncedTasks.isEmpty()) {
            String emptyText = Component.translatable("gui.neoecoae.crafting.no_tasks").getString();
            drawString(guiContext, font, emptyText, x + emptyTextX(font, emptyText), y + emptyTextY(), HostText.MUTED);
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

    public static void drawRightString(GUIContext guiContext, Font font, String text, float rightX, float y, int color) {
        drawString(guiContext, font, text, rightX - font.width(text), y, color);
    }

    public static void drawString(GUIContext guiContext, Font font, String text, float x, float y, int color) {
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
        return syncState.payload();
    }

    @Override
    public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
        syncState.setPayload(value);
        syncedTasks = syncState.tasks();
        syncedTotalTasks = syncState.totalTasks();
        return this;
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

}
