package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
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
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** CPU list sidebar; only the visible page and the selected CPU cross the menu boundary. */
final class ComputationCpuSelector extends BindableValue<CompoundTag> {
    static final int WIDTH = 90, HEIGHT = 164, ROWS = 6;
    private static final int LIST_X = 8, LIST_Y = 19, ROW_WIDTH = 67, ROW_HEIGHT = 22, ROW_STRIDE = 23;
    private static final int TRACK_X = 78, TRACK_Y = 19, TRACK_HEIGHT = 137;
    private static final IGuiTexture BACKGROUND = sprite(0, 0, WIDTH, HEIGHT);
    private static final IGuiTexture NORMAL = sprite(100, 0, ROW_WIDTH, ROW_HEIGHT);
    private static final IGuiTexture SELECTED = sprite(100, 22, ROW_WIDTH, ROW_HEIGHT);

    private final ComputationControllerUI.Config config;
    private final IntConsumer openDetails;
    private final BindableValue<Integer> selectionRequest = new BindableValue<>(-1);
    private final BindableValue<Integer> scrollRequest = new BindableValue<>(0);
    private final ComputationScrollbar scrollbar;
    private List<ComputationCpuEntry> page = List.of();
    private int selectedSerial = -1, total, offset, pageOffset;

    ComputationCpuSelector(ComputationControllerUI.Config config, IntConsumer openDetails,
            CpuSelectionState.Identities<ECOCraftingCPU> identities) {
        super(new CompoundTag());
        this.config = config;
        this.openDetails = openDetails;
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        style(style -> style.backgroundTexture(BACKGROUND));
        setOverflowVisible(true);
        ServerState server = new ServerState(config, identities);
        bind(DataBindingBuilder.create(server::snapshot, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        selectionRequest.bind(DataBindingBuilder.intValC2S(server::select).build());
        scrollRequest.bind(DataBindingBuilder.intValC2S(server::scroll).build());
        selectionRequest.setDisplay(false);
        scrollRequest.setDisplay(false);
        addChildren(selectionRequest, scrollRequest);
        for (int row = 0; row < ROWS; row++) addChild(rowHitbox(row));

        scrollbar = new ComputationScrollbar(this, TRACK_X, TRACK_Y, TRACK_HEIGHT, Scrollbar.SMALL, ComputationScrollbar.SMALL_WIDTH, this::scrollTo);
        addChild(scrollbar);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.x < getPositionX() + LIST_X || event.x >= getPositionX() + TRACK_X + ComputationScrollbar.SMALL_WIDTH
                || event.y < getPositionY() + LIST_Y || event.y >= getPositionY() + LIST_Y + ROWS * ROW_STRIDE) return;
            scrollTo(offset + (event.deltaY < 0 ? 2 : -2));
            event.stopImmediatePropagation();
        }, true);
    }

    @Override
    public ComputationCpuSelector setValue(CompoundTag value, boolean notify) {
        super.setValue(value, notify);
        if (value == null || !value.contains("total")) return this;
        total = value.getInt("total");
        offset = pageOffset = value.getInt("offset");
        selectedSerial = value.getInt("selectedSerial");
        page = value.getList("page", Tag.TAG_COMPOUND).stream()
            .map(tag -> ComputationCpuEntry.read((CompoundTag) tag, config.registries())).toList();
        scrollbar.update(offset, total - ROWS, ROWS / 3);
        scrollRequest.setValue(offset, false);
        selectionRequest.setValue(selectedSerial, false);
        return this;
    }

    private UIElement rowHitbox(int row) {
        UIElement hitbox = hitbox(LIST_X, LIST_Y + row * ROW_STRIDE, ROW_WIDTH, ROW_HEIGHT);
        hitbox.setId("computation-cpu-row-" + row);
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, event -> event.stopImmediatePropagation());
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button != 0) return;
            ComputationCpuEntry entry = rowEntry(row);
            if (entry == null) return;
            if (entry.serial() == selectedSerial) {
                openDetails.accept(entry.serial());
            } else {
                selectedSerial = entry.serial();
                selectionRequest.setValue(entry.serial());
            }
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
        scrollbar.update(offset, total - ROWS, ROWS / 3);
        scrollRequest.setValue(offset);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        // Leave drawContents to LDLib2 so inventory, toolbar and floating windows are drawn too.
        Font font = Minecraft.getInstance().font;
        float x = getPositionX(), y = getPositionY();
        context.graphics.flush();
        text(context, font, GuiText.CPUs.text().getString() + " (" + total + ")", x + 8, y + 6, 70, 0.75F, 0x3F3D52);
        for (int row = 0; row < ROWS; row++) {
            ComputationCpuEntry entry = rowEntry(row);
            if (entry != null) drawRow(context, font, entry, x + LIST_X, y + LIST_Y + row * ROW_STRIDE);
        }
    }

    private void drawRow(GUIContext context, Font font, ComputationCpuEntry entry, float x, float y) {
        drawCpuRow(context, font, entry, x, y, entry.serial() == selectedSerial, NORMAL, SELECTED);
    }

    static void drawCpuRow(GUIContext context, Font font, ComputationCpuEntry entry, float x, float y,
            boolean chosen, IGuiTexture normal, IGuiTexture selected) {
        context.drawTexture(chosen ? selected : normal, x, y, ROW_WIDTH, ROW_HEIGHT);
        context.graphics.flush();
        int color = 0x413F54;
        text(context, font, name(entry).getString(), x + 3, y + 2, 50, 0.666F, color);
        context.graphics.blit(entry.overlay(), Math.round(x + 55), Math.round(y), 0, 0, 10, 10, 10, 10);
        if (entry.output() != null && !entry.status().equals("idle")) {
            icon(context, Icon.S_CRAFT, x + 2, y + 10);
            fittedText(context, font, entry.amount(entry.requested(), false), x + 14, y + 13, 39, 0.666F, color);
            key(context, entry, x + 55, y + 10, 0.625F);
            context.graphics.fill(Math.round(x + 1), Math.round(y + 20), Math.round(x + 1 + entry.progress() * 66), Math.round(y + 21),
                chosen ? 0xFF7DA9D2 : 0xFFACE9FF);
        } else {
            if (entry.parallel() > 0) {
                icon(context, Icon.S_PROCESSOR, x + 2, y + 10);
                fittedText(context, font, HostText.ae2Amount(entry.parallel()), x + 14, y + 13, 12, 0.666F, color);
            }
            icon(context, Icon.S_STORAGE, x + 27, y + 10);
            fittedText(context, font, entry.storageText(), x + 39, y + 13,
                entry.mode() == CpuSelectionMode.ANY ? 26 : 15, 0.666F, color);
            if (entry.mode() != CpuSelectionMode.ANY) icon(context, entry.mode() == CpuSelectionMode.PLAYER_ONLY ? Icon.S_TERMINAL : Icon.S_MACHINE, x + 55, y + 10);
        }
    }

    private static void text(GUIContext context, Font font, String value, float x, float y, int width, float scale, int color) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        context.graphics.pose().scale(scale, scale, 1);
        context.graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, (int) (width / scale))), 0, 0, 0xFF000000 | color, false);
        context.graphics.pose().popPose();
    }

    static void fittedText(GUIContext context, Font font, String value, float x, float y, int width, float scale, int color) {
        float fittedScale = Math.min(scale, width / (float) Math.max(1, font.width(value)));
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y + (scale - fittedScale) * font.lineHeight / 2, 0);
        context.graphics.pose().scale(fittedScale, fittedScale, 1);
        context.graphics.drawString(font, value, 0, 0, 0xFF000000 | color, false);
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
        icon.getBlitter().dest(0, 0).blit(context.graphics);
        context.graphics.pose().popPose();
    }

    static Component name(ComputationCpuEntry entry) {
        return entry.name().isEmpty() ? GuiText.CPUs.text().append(" #" + entry.serial()) : Component.literal(entry.name());
    }

    static Component status(ComputationCpuEntry entry) {
        return Component.translatable("gui.neoecoae.cpu.status." + entry.status());
    }

    static List<Component> tooltip(ComputationCpuEntry entry) {
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

    private static IGuiTexture sprite(int x, int y, int width, int height) {
        return SpriteTexture.of(ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/guis/cpu_selector.png")).setSprite(x, y, width, height);
    }

    private static UIElement hitbox(int x, int y, int width, int height) {
        return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }

    private static final class ServerState extends CpuSelectionServerState {
        private CompoundTag cached;
        private long sampledTick = Long.MIN_VALUE, revision;

        private ServerState(ComputationControllerUI.Config config, CpuSelectionState.Identities<ECOCraftingCPU> identities) {
            super(config, ROWS, identities);
        }
        private void select(int serial) {
            if (!beginInteraction()) return;
            selection.select(serial);
            cached = null;
            revision++;
        }
        private void scroll(int offset) {
            if (!beginInteraction()) return;
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
            var registries = config.registries();
            ListTag page = new ListTag();
            for (ECOCraftingCPU cpu : selection.page()) page.add(ComputationCpuEntry.sample(cpu, selection.serial(cpu)).write(registries));
            tag.put("page", page);
            return cached = tag;
        }
    }
}
