package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Draws the atlas at native size. Only the visible page and selected CPU cross the menu boundary. */
final class ComputationCpuPanel extends BindableValue<CompoundTag> {
    static final int WIDTH = 261, HEIGHT = 214, ROWS = 8;
    private static final int LIST_X = 176, LIST_Y = 21, ROW_WIDTH = 67, ROW_HEIGHT = 22, ROW_STRIDE = 23;
    private static final int TRACK_X = 249, TRACK_Y = 21, TRACK_HEIGHT = 183;
    private static final IGuiTexture BACKGROUND = sprite(0, 0, WIDTH, HEIGHT);
    private static final IGuiTexture NORMAL = sprite(0, 222, ROW_WIDTH, ROW_HEIGHT);
    private static final IGuiTexture SELECTED = sprite(69, 222, ROW_WIDTH, ROW_HEIGHT);

    private final ComputationHostPanelUI.Config config;
    private final BindableValue<Integer> selectionRequest = new BindableValue<>(-1);
    private final BindableValue<Integer> scrollRequest = new BindableValue<>(0);
    private List<ComputationCpuEntry> page = List.of();
    private ComputationCpuEntry selected;
    private int selectedSerial = -1, total, offset, pageOffset;
    private boolean dragging;
    private float grabOffset;

    ComputationCpuPanel(ComputationHostPanelUI.Config config) {
        super(new CompoundTag());
        this.config = config;
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        style(style -> style.backgroundTexture(BACKGROUND));
        setOverflowVisible(true);
        ServerState server = new ServerState(config);
        bind(DataBindingBuilder.create(server::snapshot, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        selectionRequest.bind(DataBindingBuilder.intValC2S(server::select).build());
        scrollRequest.bind(DataBindingBuilder.intValC2S(server::scroll).build());
        selectionRequest.setDisplay(false);
        scrollRequest.setDisplay(false);
        addChildren(selectionRequest, scrollRequest);
        for (int row = 0; row < ROWS; row++) addChild(rowHitbox(row));

        UIElement track = hitbox(TRACK_X - 1, TRACK_Y, 6, TRACK_HEIGHT);
        track.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0 || total <= ROWS) return;
            float y = event.y - getPositionY() - TRACK_Y;
            float top = thumbTop();
            grabOffset = y >= top && y < top + thumbHeight() ? y - top : thumbHeight() / 2.0F;
            dragging = true;
            dragTo(event.y);
            event.stopImmediatePropagation();
        });
        addChild(track);
        addEventListener(UIEvents.MOUSE_MOVE, event -> { if (dragging) dragTo(event.y); }, true);
        addEventListener(UIEvents.MOUSE_UP, event -> {
            if (dragging) {
                dragging = false;
                event.stopImmediatePropagation();
            }
        }, true);
        addEventListener(UIEvents.MOUSE_LEAVE, event -> {
            if (event.x < getPositionX() || event.x >= getPositionX() + WIDTH
                || event.y < getPositionY() || event.y >= getPositionY() + HEIGHT) dragging = false;
        }, true);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.x < getPositionX() + LIST_X || event.x >= getPositionX() + TRACK_X + 5
                || event.y < getPositionY() + LIST_Y || event.y >= getPositionY() + TRACK_Y + TRACK_HEIGHT) return;
            scrollTo(offset + (event.deltaY < 0 ? 2 : -2));
            event.stopImmediatePropagation();
        }, true);
        UIElement summary = hitbox(12, 25, 151, 96);
        summary.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> lines = capacityTooltip();
            if (selected != null) lines.addAll(tooltip(selected));
            event.hoverTooltips = HoverTooltips.empty().append(lines.toArray(Component[]::new));
        });
        addChild(summary);
    }

    @Override
    public ComputationCpuPanel setValue(CompoundTag value, boolean notify) {
        super.setValue(value, notify);
        if (value == null || !value.contains("total")) return this;
        total = value.getInt("total");
        offset = pageOffset = value.getInt("offset");
        selectedSerial = value.getInt("selectedSerial");
        page = value.getList("page", Tag.TAG_COMPOUND).stream()
            .map(tag -> ComputationCpuEntry.read((CompoundTag) tag, config.registries().get())).toList();
        selected = value.contains("selected") ? ComputationCpuEntry.read(value.getCompound("selected"), config.registries().get()) : null;
        scrollRequest.setValue(offset, false);
        selectionRequest.setValue(selectedSerial, false);
        return this;
    }

    private UIElement rowHitbox(int row) {
        UIElement hitbox = hitbox(LIST_X, LIST_Y + row * ROW_STRIDE, ROW_WIDTH, ROW_HEIGHT);
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, event -> event.stopImmediatePropagation());
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> {
            ComputationCpuEntry entry = rowEntry(row);
            if (entry == null) return;
            selected = entry;
            selectedSerial = entry.serial();
            selectionRequest.setValue(entry.serial());
            event.stopImmediatePropagation();
        });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            ComputationCpuEntry entry = rowEntry(row);
            if (entry != null) event.hoverTooltips = HoverTooltips.empty().append(tooltip(entry).toArray(Component[]::new));
        });
        return hitbox;
    }

    private ComputationCpuEntry rowEntry(int row) {
        return pageOffset == offset && row < page.size() ? page.get(row) : null;
    }

    private void scrollTo(int requested) {
        offset = Math.clamp(requested, 0, Math.max(0, total - ROWS));
        scrollRequest.setValue(offset);
    }

    private int thumbHeight() { return Math.max(10, TRACK_HEIGHT * ROWS / Math.max(ROWS, total)); }
    private float thumbTop() { return (TRACK_HEIGHT - thumbHeight()) * offset / (float) Math.max(1, total - ROWS); }
    private void dragTo(float mouseY) {
        float position = mouseY - getPositionY() - TRACK_Y - grabOffset;
        scrollTo(Math.round(position / Math.max(1, TRACK_HEIGHT - thumbHeight()) * Math.max(0, total - ROWS)));
    }

    @Override
    public void drawContents(GUIContext context) {
        Font font = Minecraft.getInstance().font;
        float x = getPositionX(), y = getPositionY();
        text(context, font, config.title().get().getString(), x + 8, y + 5, 157, 0.85F, 0x3F3D52);
        text(context, font, GuiText.CPUs.text().getString() + " (" + total + ")", x + LIST_X, y + 7, ROW_WIDTH, 0.75F, 0x3F3D52);
        CompoundTag value = getValue();
        metric(context, font, "cpu_storage", HostText.ae2Amount(value.getLong("usedBytes")) + " / " + HostText.ae2Amount(value.getLong("totalBytes")), x, y + 27);
        metric(context, font, "thread_usage", HostText.ae2Amount(value.getInt("usedThreads")) + " / " + HostText.ae2Amount(value.getInt("totalThreads")), x, y + 39);
        metric(context, font, "parallel_count", HostText.ae2Amount(value.getInt("parallel")), x, y + 51);
        metric(context, font, "free_memory", HostText.ae2Amount(value.getLong("availableBytes")), x, y + 63);
        context.graphics.fill((int) x + 13, (int) y + 75, (int) x + 162, (int) y + 76, 0xFF484252);
        if (selected == null) {
            text(context, font, GuiText.NoCraftingJobs.text().getString(), x + 13, y + 83, 149, 0.85F, HostText.MUTED);
        } else {
            text(context, font, name(selected).getString() + " · " + status(selected).getString(), x + 13, y + 80, 149, 0.85F, HostText.PRIMARY);
            if (selected.output() != null) {
                key(context, selected, x + 13, y + 92, 0.666F);
                text(context, font, selected.output().getDisplayName().getString(), x + 26, y + 94, 136, 0.85F, HostText.PRIMARY);
                text(context, font, selected.amount(selected.remaining(), false) + " / " + selected.amount(selected.requested(), false),
                    x + 13, y + 105, 149, 0.85F, HostText.VALUE);
            }
            text(context, font, Tooltips.ofPercent(selected.progress()).getString() + " · " + Tooltips.ofDuration(selected.elapsed(), TimeUnit.NANOSECONDS).getString(),
                x + 13, y + 115, 149, 0.7F, HostText.MUTED);
        }
        String connection = Component.translatable(value.getBoolean("connected")
            ? "gui.neoecoae.host.network.connected" : "gui.neoecoae.host.network.disconnected").getString();
        text(context, font, connection + " · x" + Math.max(1, value.getInt("multiplier")), x + 8, y + 13, 157, 0.6F,
            value.getBoolean("connected") ? HostText.USED : HostText.ERROR);
        for (int row = 0; row < ROWS; row++) {
            ComputationCpuEntry entry = rowEntry(row);
            if (entry != null) drawRow(context, font, entry, x + LIST_X, y + LIST_Y + row * ROW_STRIDE);
        }
        int trackLeft = Math.round(x + TRACK_X), trackTop = Math.round(y + TRACK_Y);
        context.graphics.fill(trackLeft, trackTop, trackLeft + 4, trackTop + TRACK_HEIGHT, 0xFF696D88);
        int thumbY = Math.round(trackTop + thumbTop());
        context.graphics.fill(trackLeft, thumbY, trackLeft + 4, thumbY + thumbHeight(), 0xFF9A9FB4);
    }

    private void drawRow(GUIContext context, Font font, ComputationCpuEntry entry, float x, float y) {
        boolean chosen = entry.serial() == selectedSerial;
        context.drawTexture(chosen ? SELECTED : NORMAL, x, y, ROW_WIDTH, ROW_HEIGHT);
        int color = chosen ? 0x263D53 : HostText.PRIMARY;
        text(context, font, name(entry).getString(), x + 3, y + 2, 52, 0.666F, color);
        context.graphics.blit(entry.overlay(), Math.round(x + 57), Math.round(y + 2), 0, 0, 7, 7, 7, 7);
        if (entry.output() != null && !entry.status().equals("idle")) {
            icon(context, Icon.S_CRAFT, x + 2, y + 10);
            text(context, font, entry.amount(entry.remaining(), false), x + 14, y + 12, 39, 0.666F, color);
            key(context, entry, x + 55, y + 9, 0.666F);
            context.graphics.fill(Math.round(x + 1), Math.round(y + 20), Math.round(x + 1 + entry.progress() * 65), Math.round(y + 21),
                chosen ? 0xFF427FA9 : 0xFF8377FF);
        } else {
            icon(context, Icon.S_PROCESSOR, x + 2, y + 10);
            text(context, font, HostText.ae2Amount(entry.parallel()), x + 13, y + 12, 15, 0.666F, color);
            icon(context, Icon.S_STORAGE, x + 28, y + 10);
            text(context, font, entry.storageText(), x + 39, y + 12, entry.mode() == CpuSelectionMode.ANY ? 26 : 16, 0.666F, color);
            if (entry.mode() != CpuSelectionMode.ANY) icon(context, entry.mode() == CpuSelectionMode.PLAYER_ONLY ? Icon.S_TERMINAL : Icon.S_MACHINE, x + 55, y + 9);
        }
    }

    private static void metric(GUIContext context, Font font, String metric, String value, float x, float y) {
        String label = Component.translatable("gui.neoecoae.host.computation." + metric).getString();
        float scale = 0.8F;
        int valueWidth = Math.round(font.width(value) * scale);
        text(context, font, label, x + 13, y, Math.max(0, 145 - valueWidth), scale, HostText.MUTED);
        text(context, font, value, x + 162 - valueWidth, y, valueWidth, scale, HostText.VALUE);
    }

    private static void text(GUIContext context, Font font, String value, float x, float y, int width, float scale, int color) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        context.graphics.pose().scale(scale, scale, 1);
        context.graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, (int) (width / scale))), 0, 0, 0xFF000000 | color, false);
        context.graphics.pose().popPose();
    }

    private static void key(GUIContext context, ComputationCpuEntry entry, float x, float y, float scale) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        context.graphics.pose().scale(scale, scale, 1);
        AEKeyRendering.drawInGui(Minecraft.getInstance(), context.graphics, 0, 0, entry.output());
        context.graphics.pose().popPose();
    }

    private static void icon(GUIContext context, Icon icon, float x, float y) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        context.graphics.pose().scale(0.666F, 0.666F, 1);
        icon.getBlitter().dest(0, 0).blit(context.graphics);
        context.graphics.pose().popPose();
    }

    private static Component name(ComputationCpuEntry entry) {
        return entry.name().isEmpty() ? GuiText.CPUs.text().append(" #" + entry.serial()) : Component.literal(entry.name());
    }

    private static Component status(ComputationCpuEntry entry) {
        return Component.translatable("gui.neoecoae.cpu.status." + entry.status());
    }

    private static List<Component> tooltip(ComputationCpuEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(entry));
        lines.add(status(entry));
        lines.add(ButtonToolTips.CpuStatusStorage.text(Component.literal(entry.fullStorageText()).withStyle(Tooltips.NUMBER_TEXT)));
        lines.add(ButtonToolTips.CpuStatusCoProcessors.text(Tooltips.ofNumber(entry.parallel())));
        if (entry.mode() != CpuSelectionMode.ANY) lines.add(entry.mode() == CpuSelectionMode.PLAYER_ONLY
            ? ButtonToolTips.CpuSelectionModePlayersOnly.text() : ButtonToolTips.CpuSelectionModeAutomationOnly.text());
        if (entry.output() != null) {
            lines.add(entry.output().getDisplayName());
            lines.add(Component.translatable("gui.neoecoae.cpu.remaining", entry.amount(entry.remaining(), true)));
            lines.add(Component.translatable("gui.neoecoae.big_order.requested", entry.amount(entry.requested(), true)));
            lines.add(ButtonToolTips.CpuStatusCraftedIn.text(Tooltips.ofPercent(entry.progress()), Tooltips.ofDuration(entry.elapsed(), TimeUnit.NANOSECONDS)));
        }
        return lines;
    }

    private List<Component> capacityTooltip() {
        CompoundTag value = getValue();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.host.computation.cpu_storage").append(": ")
            .append(HostText.expandedNumber(value.getLong("usedBytes")) + " / " + HostText.expandedNumber(value.getLong("totalBytes"))));
        lines.add(Component.translatable("gui.neoecoae.host.computation.thread_usage").append(": ")
            .append(HostText.expandedNumber(value.getInt("usedThreads")) + " / " + HostText.expandedNumber(value.getInt("totalThreads"))));
        lines.add(Component.translatable("gui.neoecoae.host.computation.parallel_count").append(": ").append(HostText.expandedNumber(value.getInt("parallel"))));
        lines.add(Component.translatable("gui.neoecoae.host.computation.free_memory").append(": ")
            .append(HostText.expandedNumber(value.getLong("availableBytes")) + " B"));
        return lines;
    }

    private static IGuiTexture sprite(int x, int y, int width, int height) {
        return SpriteTexture.of(NeoECOAE.id("textures/gui/eco_cpu_controller.png")).setSprite(x, y, width, height);
    }

    private static UIElement hitbox(int x, int y, int width, int height) {
        return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }

    private static final class ServerState {
        private final ComputationHostPanelUI.Config config;
        private final CpuSelectionState<ECOCraftingCPU> selection = new CpuSelectionState<>(ROWS);
        private CompoundTag cached;
        private long sampledTick = Long.MIN_VALUE, revision;

        private ServerState(ComputationHostPanelUI.Config config) { this.config = config; }
        private void refresh() {
            selection.update(config.cpus().get(), ECOCraftingCPU::isBusy,
                cpu -> cpu.getName() == null ? null : cpu.getName().getString());
        }
        private void select(int serial) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            selection.select(serial);
            cached = null;
            revision++;
        }
        private void scroll(int offset) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            selection.scroll(offset);
            cached = null;
            revision++;
        }
        private CompoundTag snapshot() {
            long tick = config.gameTime().getAsLong();
            if (cached != null && tick >= sampledTick && tick - sampledTick < 5) return cached;
            sampledTick = tick;
            refresh();
            CompoundTag tag = new CompoundTag();
            tag.putLong("revision", revision);
            tag.putInt("total", selection.size());
            tag.putInt("offset", selection.offset());
            tag.putInt("selectedSerial", selection.selectedSerial());
            tag.putLong("usedBytes", config.usedBytes().getAsLong());
            tag.putLong("totalBytes", config.totalBytes().getAsLong());
            tag.putLong("availableBytes", config.availableBytes().getAsLong());
            tag.putInt("usedThreads", config.usedThreads().getAsInt());
            tag.putInt("totalThreads", config.totalThreads().getAsInt());
            tag.putInt("parallel", config.parallelCount().getAsInt());
            tag.putBoolean("connected", config.connected().getAsBoolean());
            tag.putInt("multiplier", config.networkMultiplier().getAsInt());
            var registries = config.registries().get();
            ListTag page = new ListTag();
            for (ECOCraftingCPU cpu : selection.page()) page.add(ComputationCpuEntry.sample(cpu, selection.serial(cpu)).write(registries));
            tag.put("page", page);
            if (selection.selected() != null) tag.put("selected", ComputationCpuEntry.sample(selection.selected(), selection.selectedSerial()).write(registries));
            return cached = tag;
        }
    }
}
