package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.client.AEKeyRendering;
import appeng.api.util.AEColor;
import appeng.client.gui.widgets.AE2Button;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.time.DurationFormatUtils;

/** The 326x254 ECO crafting CPU page shown when a CPU row is clicked. */
final class ComputationCpuStatusPanel extends BindableValue<CompoundTag> {
    static final int WIDTH = 326, HEIGHT = 254, ROWS = 9;
    private static final int CPU_X = 8, CPU_Y = 19, CPU_WIDTH = 67, CPU_HEIGHT = 22, CPU_STRIDE = 23;
    private static final int CPU_TRACK_X = 77, TRACK_Y = 19, TRACK_HEIGHT = 206;
    private static final int ITEM_X = 97, ITEM_Y = 19, ITEM_WIDTH = 67, ITEM_HEIGHT = 22, ITEM_COLUMN_STRIDE = 68;
    private static final int ITEM_COLS = 3, ITEM_ROWS = 9, ITEM_STRIDE = 23, ITEM_PAGE = ITEM_COLS * ITEM_ROWS;
    private static final int ITEM_TRACK_X = 306;
    private static final int TEXT = 0x413F54;
    private static final int BATCH_ICON_SIZE = 16, BATCH_ICON_LEFT = 1;
    private static final ResourceLocation BATCH_ICON = NeoECOAE.id("textures/gui/eco_batching.png");
    // The source sheet is 384x384, but only the 326x254 top slice is the page background.
    // The two 67x22 CPU row states live at y=260 in the same sheet.
    private static final IGuiTexture BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/gui/eco_craftingcpu.png")).setSprite(0, 0, WIDTH, HEIGHT);
    private static final IGuiTexture CPU_NORMAL = SpriteTexture.of(
        NeoECOAE.id("textures/gui/eco_craftingcpu.png")).setSprite(0, 260, CPU_WIDTH, CPU_HEIGHT);
    private static final IGuiTexture CPU_SELECTED = SpriteTexture.of(
        NeoECOAE.id("textures/gui/eco_craftingcpu.png")).setSprite(69, 260, CPU_WIDTH, CPU_HEIGHT);

    private final ComputationHostPanelUI.Config config;
    private final BindableValue<Integer> selectionRequest = new BindableValue<>(-1);
    private final BindableValue<Integer> cpuScrollRequest = new BindableValue<>(0);
    private final BindableValue<Integer> itemScrollRequest = new BindableValue<>(0);
    private final StatusButton pauseButton = new StatusButton();
    private final StatusButton cancelButton = new StatusButton();
    private final ComputationScrollbar cpuScrollbar;
    private final ComputationScrollbar itemScrollbar;
    private List<ComputationCpuEntry> cpus = List.of();
    private List<ComputationCpuItemEntry> items = List.of();
    private ComputationCpuEntry selected;
    private int selectedSerial = -1, total, cpuOffset, cpuPageOffset, itemTotal, itemOffset, itemPageOffset;
    private Runnable backAction = () -> {};

    ComputationCpuStatusPanel(ComputationHostPanelUI.Config config, CpuSelectionState.Identities<ECOCraftingCPU> identities) {
        super(new CompoundTag());
        this.config = config;
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        style(style -> style.backgroundTexture(BACKGROUND));
        setOverflowVisible(true);
        ServerState server = new ServerState(config, identities);
        var cpuAction = addRPCEvent(RPCEventBuilder.simple(Integer.class, Integer.class, server::action));
        bind(DataBindingBuilder.create(server::snapshot, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        selectionRequest.bind(DataBindingBuilder.intValC2S(server::select).build());
        cpuScrollRequest.bind(DataBindingBuilder.intValC2S(server::scrollCpu).build());
        itemScrollRequest.bind(DataBindingBuilder.intValC2S(server::scrollItems).build());
        selectionRequest.setDisplay(false);
        cpuScrollRequest.setDisplay(false);
        itemScrollRequest.setDisplay(false);
        addChildren(selectionRequest, cpuScrollRequest, itemScrollRequest);
        for (int row = 0; row < ROWS; row++) addChild(cpuHitbox(row));
        for (int cell = 0; cell < ITEM_PAGE; cell++) addChild(itemHitbox(cell));
        cpuScrollbar = new ComputationScrollbar(this, CPU_TRACK_X, TRACK_Y, TRACK_HEIGHT, this::scrollCpuTo);
        itemScrollbar = new ComputationScrollbar(this, ITEM_TRACK_X, TRACK_Y, TRACK_HEIGHT,
            row -> scrollItemsTo(row * ITEM_COLS));
        addChildren(cpuScrollbar, itemScrollbar);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.x >= getPositionX() + CPU_X && event.x < getPositionX() + CPU_TRACK_X + ComputationScrollbar.WIDTH
                && event.y >= getPositionY() + CPU_Y && event.y < getPositionY() + CPU_Y + ROWS * CPU_STRIDE) {
                scrollCpuTo(cpuOffset + (event.deltaY < 0 ? ROWS / 3 : -ROWS / 3));
                event.stopImmediatePropagation();
            } else if (event.x >= getPositionX() + ITEM_X && event.x < getPositionX() + ITEM_TRACK_X + ComputationScrollbar.WIDTH
                && event.y >= getPositionY() + ITEM_Y && event.y < getPositionY() + ITEM_Y + ITEM_ROWS * ITEM_STRIDE) {
                scrollItemsTo(itemOffset + (event.deltaY < 0 ? ITEM_COLS : -ITEM_COLS));
                event.stopImmediatePropagation();
            }
        }, true);

        Button back = new ComputationSettingsPanel.BackButton().noText()
            .setOnClick(event -> backAction.run());
        back.setId("computation-status-back");
        back.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(302).top(-5).width(20).height(20).paddingAll(0));
        back.setOverflowVisible(true);
        HostElements.tooltips(back,
            Component.translatable("gui.neoecoae.computation.settings.back"));
        pauseButton.setId("computation-status-pause");
        cancelButton.setId("computation-status-cancel");
        pauseButton.setText(GuiText.Suspend.text())
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 1); });
        placeButton(pauseButton, 191, HEIGHT - 25);
        cancelButton.setText(GuiText.Cancel.text())
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 2); });
        placeButton(cancelButton, 251, HEIGHT - 25);
        pauseButton.setActive(false);
        cancelButton.setActive(false);
        addChildren(back, pauseButton, cancelButton);
    }

    void setBackAction(Runnable action) { backAction = action; }

    void open(int serial) {
        selectedSerial = serial;
        selectionRequest.setValue(serial);
    }

    @Override
    public ComputationCpuStatusPanel setValue(CompoundTag value, boolean notify) {
        super.setValue(value, notify);
        if (value == null || !value.contains("total")) return this;
        total = value.getInt("total");
        cpuOffset = cpuPageOffset = value.getInt("cpuOffset");
        selectedSerial = value.getInt("selectedSerial");
        itemTotal = value.getInt("itemTotal");
        itemOffset = itemPageOffset = value.getInt("itemOffset");
        cpus = value.getList("cpus", Tag.TAG_COMPOUND).stream()
            .map(tag -> ComputationCpuEntry.read((CompoundTag) tag, config.registries().get())).toList();
        items = value.getList("items", Tag.TAG_COMPOUND).stream()
            .map(tag -> ComputationCpuItemEntry.read((CompoundTag) tag, config.registries().get())).toList();
        selected = value.contains("selected")
            ? ComputationCpuEntry.read(value.getCompound("selected"), config.registries().get()) : null;
        cpuScrollRequest.setValue(cpuOffset, false);
        itemScrollRequest.setValue(itemOffset, false);
        selectionRequest.setValue(selectedSerial, false);
        cpuScrollbar.update(cpuOffset, total - ROWS, ROWS / 3);
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal), 1);
        boolean busy = selected != null && !selected.status().equals("idle");
        pauseButton.setActive(busy && !selected.status().equals("error") && !selected.status().equals("returning"));
        cancelButton.setActive(busy);
        pauseButton.setText(selected != null && selected.status().equals("suspended") ? GuiText.Resume.text() : GuiText.Suspend.text());
        return this;
    }

    private UIElement cpuHitbox(int row) {
        UIElement hitbox = hitbox(CPU_X, CPU_Y + row * CPU_STRIDE, CPU_WIDTH, CPU_HEIGHT);
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button != 0 || cpuOffset != cpuPageOffset || row >= cpus.size()) return;
            selectedSerial = cpus.get(row).serial();
            selectionRequest.setValue(selectedSerial);
            event.stopImmediatePropagation();
        });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (cpuOffset == cpuPageOffset && row < cpus.size()) {
                event.hoverTooltips = HoverTooltips.empty().append(
                    ComputationCpuPanel.tooltip(cpus.get(row)).toArray(Component[]::new));
            }
        });
        return hitbox;
    }

    private UIElement itemHitbox(int cell) {
        UIElement hitbox = hitbox(ITEM_X + cell % ITEM_COLS * ITEM_COLUMN_STRIDE,
            ITEM_Y + cell / ITEM_COLS * ITEM_STRIDE, ITEM_WIDTH, ITEM_HEIGHT);
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (itemOffset != itemPageOffset || cell >= items.size()) return;
            ComputationCpuItemEntry item = items.get(cell);
            List<Component> lines = new ArrayList<>(AEKeyRendering.getTooltip(item.key()));
            lines.addAll(itemDescription(item, true));
            if (item.batched()) {
                lines.add(Component.translatable("gui.neoecoae.crafting.smart_doubling").withColor(0xC7A6FF));
            }
            event.hoverTooltips = HoverTooltips.empty().append(lines.toArray(Component[]::new));
        });
        return hitbox;
    }

    private void scrollCpuTo(int value) {
        cpuOffset = Math.clamp(value, 0, Math.max(0, total - ROWS));
        cpuScrollbar.update(cpuOffset, total - ROWS, ROWS / 3);
        cpuScrollRequest.setValue(cpuOffset);
    }

    private void scrollItemsTo(int value) {
        itemOffset = Math.clamp(value / ITEM_COLS, 0, maxItemRows(itemTotal)) * ITEM_COLS;
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal), 1);
        itemScrollRequest.setValue(itemOffset);
    }

    private static int maxItemRows(int total) {
        return Math.max(0, (total + ITEM_COLS - 1) / ITEM_COLS - ITEM_ROWS);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        Font font = Minecraft.getInstance().font;
        float x = getPositionX(), y = getPositionY();
        context.graphics.flush();
        context.graphics.drawString(font, GuiText.CraftingCPUs.text(), Math.round(x + CPU_X), Math.round(y + 7), 0xFF000000 | TEXT, false);
        context.graphics.drawString(font, title(selected, itemTotal > 0), Math.round(x + ITEM_X - 1), Math.round(y + 7), 0xFF000000 | TEXT, false);
        for (int row = 0; cpuOffset == cpuPageOffset && row < cpus.size() && row < ROWS; row++) drawCpu(context, font, cpus.get(row), x + CPU_X, y + CPU_Y + row * CPU_STRIDE);
        for (int index = 0; itemOffset == itemPageOffset && index < items.size() && index < ITEM_PAGE; index++) {
            drawItem(context, font, items.get(index), x + ITEM_X + (index % ITEM_COLS) * ITEM_COLUMN_STRIDE,
                y + ITEM_Y + (index / ITEM_COLS) * ITEM_STRIDE, AEConfig.instance().isUseColoredCraftingStatus());
        }
    }

    static Component title(ComputationCpuEntry cpu, boolean hasItems) {
        var title = GuiText.CraftingStatus.text();
        if (cpu == null) return title;
        BigInteger completed = cpu.requested().subtract(cpu.remaining()).max(BigInteger.ONE);
        BigInteger eta = BigInteger.valueOf(cpu.elapsed()).multiply(cpu.remaining()).divide(completed);
        if (hasItems && eta.signum() > 0) {
            long nanos = eta.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            title.append(" - " + DurationFormatUtils.formatDuration(
                TimeUnit.NANOSECONDS.toMillis(nanos), GuiText.ETAFormat.getLocal()));
        }
        if (cpu.status().equals("returning")) {
            title.append(" - ").append(GuiText.CantStoreItems.text().withStyle(ChatFormatting.RED));
        }
        return title;
    }

    private void drawCpu(GUIContext context, Font font, ComputationCpuEntry entry, float x, float y) {
        ComputationCpuPanel.drawCpuRow(context, font, entry, x, y, entry.serial() == selectedSerial,
            CPU_NORMAL, CPU_SELECTED);
    }

    static void drawItem(GUIContext context, Font font, ComputationCpuItemEntry entry, float x, float y,
            boolean colored) {
        if (colored) {
            int color = entry.active().signum() > 0
                ? (entry.batched() ? AEColor.PURPLE : AEColor.GREEN).blackVariant
                : entry.pending().signum() > 0 ? AEColor.YELLOW.blackVariant : 0;
            if (color != 0) context.graphics.fill(Math.round(x), Math.round(y), Math.round(x + ITEM_WIDTH),
                Math.round(y + ITEM_HEIGHT), color | 0x5A000000);
        }
        if (entry.batched()) {
            context.graphics.blit(BATCH_ICON, Math.round(x + BATCH_ICON_LEFT),
                Math.round(y + (ITEM_HEIGHT - BATCH_ICON_SIZE) / 2F), 0, 0,
                BATCH_ICON_SIZE, BATCH_ICON_SIZE, BATCH_ICON_SIZE, BATCH_ICON_SIZE);
        }
        key(context, entry.key(), x + ITEM_WIDTH - 19, y + 3, 1.0F);
        drawItemDescription(context, font, itemDescription(entry, false), x, y);
    }

    static void drawItemDescription(GUIContext context, Font font, List<Component> lines, float x, float y) {
        float scale = 0.5F;
        float height = lines.size() * font.lineHeight * scale + Math.max(0, lines.size() - 1);
        float top = Math.round(y + (ITEM_HEIGHT - height) / 2);
        for (int line = 0; line < lines.size(); line++) {
            Component value = lines.get(line);
            context.graphics.pose().pushPose();
            context.graphics.pose().translate(x + ITEM_WIDTH - 21 - font.width(value) * scale,
                top + line * (font.lineHeight * scale + 1), 0);
            context.graphics.pose().scale(scale, scale, 1);
            context.graphics.drawString(font, value, 0, 0, 0xFF000000 | TEXT, false);
            context.graphics.pose().popPose();
        }
    }

    private static List<Component> itemDescription(ComputationCpuItemEntry item, boolean full) {
        List<Component> lines = new ArrayList<>();
        if (item.stored().signum() > 0) lines.add(GuiText.FromStorage.text(item.amount(item.stored(), full)));
        if (item.active().signum() > 0) lines.add(GuiText.Crafting.text(item.amount(item.active(), full)));
        if (item.pending().signum() > 0) lines.add(GuiText.Scheduled.text(item.amount(item.pending(), full)));
        return lines;
    }

    private static void key(GUIContext context, appeng.api.stacks.AEKey key, float x, float y, float scale) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        context.graphics.pose().scale(scale, scale, 1);
        AEKeyRendering.drawInGui(Minecraft.getInstance(), context.graphics, 0, 0, key);
        context.graphics.pose().popPose();
    }

    private static UIElement hitbox(int x, int y, int width, int height) {
        return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }

    private static void placeButton(Button button, int x, int y) {
        button.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(50).height(20).paddingAll(0));
    }

    private static final class StatusButton extends Button {
        private Component label = Component.empty();

        private StatusButton() { noText(); }

        @Override
        public Button setText(Component label) {
            this.label = label;
            return this;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            IGuiTexture texture = !isActive() ? NETextures.AE2_BUTTON_DISABLED
                : getState() == State.DEFAULT ? NETextures.AE2_BUTTON : NETextures.AE2_BUTTON_HIGHLIGHTED;
            context.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }

        @Override
        public void drawContents(GUIContext context) {
            super.drawContents(context);
            int x = Math.round(getPositionX()), y = Math.round(getPositionY());
            int offset = !isActive() ? -1 : getState() == State.DEFAULT ? 1 : 0;
            int color = !isActive() ? TEXT : getState() == State.DEFAULT ? 0xF2F2F2 : 0x517497;
            context.graphics.flush();
            AE2Button.renderButtonText(context.graphics, Minecraft.getInstance().font, label,
                x + 2, y, x + Math.round(getSizeWidth()) - 2, y + Math.round(getSizeHeight()), offset, 0xFF000000 | color);
        }
    }

    private static final class ServerState {
        private final ComputationHostPanelUI.Config config;
        private final CpuSelectionState<ECOCraftingCPU> selection;
        private CompoundTag cached;
        private long sampledTick = Long.MIN_VALUE;
        private int itemOffset;

        private ServerState(ComputationHostPanelUI.Config config, CpuSelectionState.Identities<ECOCraftingCPU> identities) {
            this.config = config;
            selection = new CpuSelectionState<>(ROWS, identities);
        }

        private void refresh() {
            selection.update(config.cpus().get(), ECOCraftingCPU::isBusy,
                cpu -> cpu.getName() == null ? null : cpu.getName().getString());
        }

        private void select(int serial) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            selection.select(serial);
            itemOffset = 0;
            cached = null;
        }

        private void scrollCpu(int offset) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            selection.scroll(offset);
            cached = null;
        }

        private void scrollItems(int offset) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            int total = selectedItems(selection.selected()).size();
            itemOffset = Math.clamp(offset / ITEM_COLS, 0, maxItemRows(total)) * ITEM_COLS;
            cached = null;
        }

        private void action(int serial, int action) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            // Carry the target in this request; selection synchronization may arrive on a later tick.
            if (!selection.select(serial)) return;
            ECOCraftingCPU cpu = selection.selected();
            if (action == 1) config.toggleCpuSuspended().accept(cpu);
            if (action == 2) config.cancelCpu().accept(cpu);
            cached = null;
        }

        private CompoundTag snapshot() {
            long tick = config.gameTime().getAsLong();
            if (cached != null && tick >= sampledTick && tick - sampledTick < 3) return cached;
            sampledTick = tick;
            refresh();
            CompoundTag tag = new CompoundTag();
            tag.putInt("total", selection.size());
            tag.putInt("cpuOffset", selection.offset());
            tag.putInt("selectedSerial", selection.selectedSerial());
            var registries = config.registries().get();
            ListTag cpuPage = new ListTag();
            for (ECOCraftingCPU cpu : selection.page()) cpuPage.add(ComputationCpuEntry.sample(cpu, selection.serial(cpu)).write(registries));
            tag.put("cpus", cpuPage);
            ECOCraftingCPU selectedCpu = selection.selected();
            if (selectedCpu != null) {
                ComputationCpuEntry selectedEntry = ComputationCpuEntry.sample(selectedCpu, selection.selectedSerial());
                tag.put("selected", selectedEntry.write(registries));
                List<ComputationCpuItemEntry> all = selectedItems(selectedCpu);
                tag.putInt("itemTotal", all.size());
                itemOffset = Math.clamp(itemOffset / ITEM_COLS, 0, maxItemRows(all.size())) * ITEM_COLS;
                tag.putInt("itemOffset", itemOffset);
                ListTag itemPage = new ListTag();
                for (ComputationCpuItemEntry item : all.subList(itemOffset, Math.min(all.size(), itemOffset + ITEM_PAGE))) {
                    itemPage.add(item.write(registries));
                }
                tag.put("items", itemPage);
            } else {
                tag.putInt("itemTotal", 0);
                tag.putInt("itemOffset", 0);
                tag.put("items", new ListTag());
            }
            return cached = tag;
        }

        private List<ComputationCpuItemEntry> selectedItems(ECOCraftingCPU cpu) {
            if (cpu == null) return List.of();
            var logic = cpu.getLogic();
            Map<appeng.api.stacks.AEKey, BigInteger> stored = new HashMap<>(logic.getExactStoredPreview());
            Map<appeng.api.stacks.AEKey, BigInteger> active = new HashMap<>(logic.getExactActivePreview());
            Map<appeng.api.stacks.AEKey, BigInteger> pending = new HashMap<>(logic.getExactPendingPreview());
            Set<appeng.api.stacks.AEKey> keys = new HashSet<>();
            appeng.api.stacks.KeyCounter all = new appeng.api.stacks.KeyCounter();
            logic.getAllItems(all);
            all.forEach(entry -> keys.add(entry.getKey()));
            keys.addAll(stored.keySet());
            keys.addAll(active.keySet());
            keys.addAll(pending.keySet());
            return keys.stream().map(key -> itemForKey(logic, key, stored, active, pending))
                .filter(item -> item.stored().signum() != 0 || item.active().signum() != 0 || item.pending().signum() != 0)
                .sorted(Comparator.comparing(item -> item.key().getDisplayName().getString()))
                .toList();
        }

        private ComputationCpuItemEntry itemForKey(cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic logic,
                appeng.api.stacks.AEKey key, Map<appeng.api.stacks.AEKey, BigInteger> stored,
                Map<appeng.api.stacks.AEKey, BigInteger> active, Map<appeng.api.stacks.AEKey, BigInteger> pending) {
            BigInteger storedAmount = stored.containsKey(key) ? stored.get(key)
                : BigInteger.valueOf(Math.max(0L, logic.getStored(key)));
            BigInteger activeAmount = active.containsKey(key) ? active.get(key)
                : BigInteger.valueOf(Math.max(0L, logic.getWaitingFor(key)));
            BigInteger pendingAmount = pending.containsKey(key) ? pending.get(key)
                : BigInteger.valueOf(Math.max(0L, logic.getPendingOutputs(key)));
            return new ComputationCpuItemEntry(key, storedAmount, activeAmount, pendingAmount,
                logic.isBatchedOutput(key));
        }
    }
}
