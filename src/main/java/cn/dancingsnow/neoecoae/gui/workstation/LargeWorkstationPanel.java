package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;

import appeng.client.api.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationOverclock;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingTooltipCard;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationThermal;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.common.GuiDraw;
import cn.dancingsnow.neoecoae.gui.workstation.LargeWorkstationUI.Config;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Clipped three-row viewport with a small synchronized buffer for continuous scrolling. */
final class LargeWorkstationPanel extends BindableValue<Tag> {
    private static final int TEXT = 0xFF303448;
    private static final int MUTED = 0xFF596177;
    private static final int SUCCESS = 0xFF226B58;
    private static final int WARNING = 0xFF9B4438;
    private static final int TASK_RUNNING = 0xFF55FF55;
    private static final int TASK_QUEUED = 0xFFFFFF55;
    private static final String PREFIX = "gui.neoecoae.workstation.";
    /** Temperature scale colours: regular, then the 4 / 6 / 9 bands. */
    private static final int[] BAND_COLORS = {0xFF8A7FA0, 0xFFA48AD0, 0xFFD08AC0, 0xFFF0A080};
    private final Config config;
    private final Player player;
    private final BindableValue<Integer> pageRequest = new BindableValue<>(0);
    private final Button[] returns = new Button[LargeWorkstationUI.SNAPSHOT_SIZE];
    private final UIElement[] taskRows = new UIElement[LargeWorkstationUI.SNAPSHOT_SIZE];
    private final LargeWorkstationTaskScroll taskScroll = new LargeWorkstationTaskScroll();
    private final Button overclock;
    private final Button cooling;
    private CompoundTag state = new CompoundTag();
    private List<Task> tasks = List.of();
    private int serverOffset;
    private long lastFrame;
    private float scrollbarGrabOffset;

    LargeWorkstationPanel(Config config, Player player) {
        super(new CompoundTag());
        this.config = config;
        this.player = player;
        layout(layout -> layout.width(253).height(214).paddingAll(0));
        setOverflowVisible(true);
        pageRequest.bind(DataBindingBuilder.intValC2S(value -> serverOffset = Math.clamp(value, 0, 509)).build());
        pageRequest.setDisplay(false);
        addChild(pageRequest);
        var returnTask = addRPCEvent(RPCEventBuilder.simple(UUID.class, id -> {
            var mui = getModularUI();
            if (id != null && mui != null && mui.getMenu() == player.containerMenu
                && player.containerMenu.stillValid(player)) config.returnTask().accept(id);
        }));
        UIElement viewport = new UIElement() {
            @Override
            public void drawBackgroundAdditional(IGUIContext ctx) {
                if (!(ctx instanceof GUIContext context)) return;
                context.pose.pushPose();
                try {
                    context.pose.translate(0, (float) taskScroll.rowShift());
                    for (int row = 0; row < tasks.size(); row++) drawTask(context, tasks.get(row), row);
                } finally {
                    context.pose.popPose();
                }
            }
        };
        viewport.setId("large-workstation-task-viewport");
        absolute(viewport, 9, 38, 69, LargeWorkstationTaskScroll.VIEWPORT_HEIGHT);
        viewport.setOverflowVisible(false);
        addChild(viewport);
        for (int row = 0; row < taskRows.length; row++) {
            final int index = row;
            UIElement hitbox = region("task-" + row, 0, row * 26, 69, 24, () -> taskTooltip(index));
            hitbox.setOverflowVisible(true);
            taskRows[row] = hitbox;
            viewport.addChild(hitbox);
            Button button = new Button().noText().setOnClick(event -> {
                Task task = task(index);
                if (event.button == 0 && task != null && task.returnable()) {
                    taskScroll.stop();
                    returnTask.send(task.id());
                }
            });
            button.setId("large-workstation-return-" + row);
            button.buttonStyle(style -> style.baseTexture(WorkstationTextures.widget(0, 6, 12, 11))
                .hoverTexture(WorkstationTextures.widget(12, 6, 12, 11))
                .pressedTexture(WorkstationTextures.widget(24, 6, 12, 11)));
            absolute(button, 56, 13, 12, 11);
            button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips =
                new HoverTooltips(new java.util.ArrayList<Object>(returnTooltip(index)), null, null, null, null));
            hitbox.addChild(button);
            returns[row] = button;
        }
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0 && event.x >= getPositionX() + 9 && event.x < getPositionX() + 87
                && event.y >= getPositionY() + 38 && event.y < getPositionY() + 114) {
                taskScroll.scrollBy(-event.deltaY);
                requestPage();
                event.stopImmediatePropagation();
            }
        }, true);
        addChild(scrollbar());
        overclock = toggle("overclock", 102, config.toggleOverclocked(), config.togglePreheating(),
            () -> overclockCard(true));
        addChild(overclock);
        cooling = toggle("cooling", 120, config.toggleActiveCooling(), null, () -> coolingCard(true));
        addChild(cooling);
        addChild(cardRegion("module-logo", 6, 6, 15, 15, this::moduleCard));
        addChild(region("machine-title", 25, 6, 115, 14, () -> List.of(
            Component.translatable("block.neoecoae.large_integrated_working_station").withStyle(ChatFormatting.YELLOW), pauseText())));
        addChild(cardRegion("module-info", 176, 34, 69, 31, this::moduleCard));
        addChild(informationIcon());
        addChild(region("parallel", 99, 35, 64, 32, this::parallelTooltip));
        addChild(cardRegion("temperature", 172, 69, 15, 55, this::temperatureCard));
        addChild(cardRegion("energy", 189, 69, 12, 55, this::energyCard));
        addChild(cardRegion("temperature-setting", 99, 117, 64, 10, this::temperatureCard));
        addChild(cardRegion("temperature-info", 176, 130, 68, 24, this::temperatureCard));
        addChild(cardRegion("cooling-info", 176, 155, 68, 24, () -> coolingCard(false)));
        addChild(cardRegion("overclock-info", 176, 180, 68, 24, () -> overclockCard(false)));
        registerValueListener(this::read);
        read(config.snapshot().apply(0));
        bind(DataBindingBuilder.tagS2C(() -> config.snapshot().apply(serverOffset)).build());
    }

    private Button toggle(String name, int x, Runnable action, Runnable secondaryAction, Supplier<HoverTooltips> tooltip) {
        Button button = new Button().noText().setOnServerClick(event -> {
            if (event.button == 0) action.run();
            else if (event.button == 1 && secondaryAction != null) secondaryAction.run();
        });
        button.setId("large-workstation-" + name);
        absolute(button, x, 89, 15, 26);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip.get());
        return button;
    }

    private UIElement informationIcon() {
        SpriteTexture normal = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information.png"));
        SpriteTexture highlighted = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information_highlight.png"));
        UIElement frame = new UIElement().style(style -> style.backgroundTexture(SpriteTexture.of(
            NeoECOAE.id("textures/gui/common/side_bar/mirrored_button_slot_up.png"))));
        frame.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).right(-21).top(0).width(23).height(30));
        frame.setAllowHitTest(false);
        UIElement info = region("status", 0, 3, 21, 21, this::statusTooltip);
        info.style(style -> style.backgroundTexture(normal));
        info.addEventListener(UIEvents.MOUSE_ENTER, event -> info.style(style -> style.backgroundTexture(highlighted)));
        info.addEventListener(UIEvents.MOUSE_LEAVE, event -> info.style(style -> style.backgroundTexture(normal)));
        return frame.addChild(info);
    }

    private List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(title("machine_status"));
        lines.add(shortStatus().copy().withStyle(hasProblem() ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        if (hasProblem()) lines.add(pauseText());
        lines.add(tr("task_count", state.getIntOr("totalTasks", 0)));
        if (state.getIntOr("totalTasks", 0) > 0) lines.add(tr("task_source",
            moduleDescription("recipeModuleId", "recipeModule").title()));
        if (state.getBooleanOr("ignoreUpgradeCards", false)) lines.add(tr("compat_all_unlocked").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private void read(Tag value) {
        state = value instanceof CompoundTag tag ? tag : new CompoundTag();
        HolderLookup.Provider registries = config.registries().get();
        List<Task> decoded = new ArrayList<>();
        var entries = state.getListOrEmpty("tasks");
        for (int i = 0; i < Math.min(LargeWorkstationUI.SNAPSHOT_SIZE, entries.size()); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            if (!entry.read("id", net.minecraft.core.UUIDUtil.CODEC).isPresent()) continue;
            decoded.add(new Task(entry.read("id", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), registries == null ? ItemStack.EMPTY
                : cn.dancingsnow.neoecoae.util.LegacyNbt.parseItem(registries, entry.getCompoundOrEmpty("item")), registries == null ? FluidStack.EMPTY
                : cn.dancingsnow.neoecoae.util.LegacyNbt.parseFluid(registries, entry.getCompoundOrEmpty("fluid")), entry.getLongOr("amount", 0L),
                entry.getIntOr("progress", 0), entry.getStringOr("status", ""), entry.getStringOr("detail", ""), entry.getBooleanOr("returnable", false),
                entry.getBooleanOr("current", false), moduleDescription(entry, "moduleId", "module"),
                entry.getIntOr("elapsedTicks", 0), entry.getIntOr("processingTicks", 0)));
        }
        int first = state.contains("taskOffset") ? state.getIntOr("taskOffset", 0) : state.getIntOr("offset", 0);
        if (taskScroll.receive(state.getIntOr("totalTasks", 0), first, decoded.size())) tasks = List.copyOf(decoded);
        updateTaskRows();
        updateToggle(overclock, state.getBooleanOr("overclocked", false));
        updateToggle(cooling, state.getBooleanOr("activeCooling", false));
    }

    private static void updateToggle(Button button, boolean enabled) {
        if (button != null) button.buttonStyle(style -> style.baseTexture(WorkstationTextures.toggle(enabled, false))
            .hoverTexture(WorkstationTextures.toggle(enabled, true)).pressedTexture(WorkstationTextures.toggle(enabled, true)));
    }

    void advanceScroll(double seconds) {
        taskScroll.advance(seconds);
        updateTaskRows();
        requestPage();
    }

    void advanceFrame() {
        long now = System.nanoTime();
        if (lastFrame != 0) advanceScroll((now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
    }

    private void updateTaskRows() {
        float shift = (float) taskScroll.rowShift();
        for (int row = 0; row < taskRows.length; row++) {
            Task task = task(row);
            taskRows[row].transform(transform -> transform.translate(0, shift));
            taskRows[row].setVisible(task != null || row == 0 && tasks.isEmpty());
            returns[row].setDisplay(task != null && task.returnable());
        }
    }

    private void requestPage() {
        int requested = taskScroll.requestedRow();
        if (pageRequest.getValue() != requested) pageRequest.setValue(requested);
    }

    private UIElement scrollbar() {
        UIElement track = new UIElement() {
            @Override
            public void drawBackgroundAdditional(IGUIContext ctx) {
                if (!(ctx instanceof GUIContext context)) return;
                int max = maxOffset();
                float y = max == 0 ? 0 : (float) (62 * taskScroll.thumbOffset() / taskScroll.maximum());
                // Keep the artist's six-pixel handle centered on the baked track.
                context.drawTexture(WorkstationTextures.taskScrollThumb(max > 0),
                    getPositionX() + 1, getPositionY() + y, 6, 15);
            }
        };
        track.setId("large-workstation-scrollbar");
        track.style(style -> style.zIndex(1));
        absolute(track, 79, 37, 8, 77);
        track.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0 || maxOffset() == 0) return;
            float localY = event.y - track.getPositionY();
            float thumbY = (float) (62 * taskScroll.thumbOffset() / taskScroll.maximum());
            scrollbarGrabOffset = localY >= thumbY && localY < thumbY + 15 ? localY - thumbY : 7.5f;
            taskScroll.jumpTo((localY - scrollbarGrabOffset) / 62 * taskScroll.maximum());
            updateTaskRows();
            requestPage();
            if (track.getModularUI() != null) track.getModularUI().getDragHandler().startDrag(0f, null, track);
            event.hasHandler = true;
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            taskScroll.jumpTo((event.y - track.getPositionY() - scrollbarGrabOffset) / 62 * taskScroll.maximum());
            updateTaskRows();
            requestPage();
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                taskScroll.scrollBy(-event.deltaY);
                requestPage();
            }
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty()
            .append(title("queue_heading"), queueRange(), hint(maxOffset() == 0 ? "queue_all_visible" : "queue_scroll_hint")));
        return track;
    }

    private int maxOffset() { return Math.max(0, state.getIntOr("totalTasks", 0) - LargeWorkstationUI.PAGE_SIZE); }

    private Task task(int index) { return index < tasks.size() ? tasks.get(index) : null; }

    private List<Component> taskTooltip(int index) {
        Task task = task(index);
        if (task == null) return List.of(title("empty_task"), hint("queue_empty_hint"));
        List<Component> lines = new ArrayList<>();
        lines.add(task.title().copy().withStyle(ChatFormatting.YELLOW));
        lines.add(tr(task.fluid().isEmpty() ? "task_amount" : "task_fluid_amount", HostText.expandedNumber(task.amount())));
        lines.add(tr("task_source", task.module().title()));
        lines.add(Component.translatable(task.status()).withColor(task.statusColor()));
        if (!task.detail().isEmpty()) lines.add(Component.translatable(task.detail()));
        lines.add(tr("progress", Math.clamp(task.progress(), 0, 200) / 2));
        if (task.processingTicks() > 0) lines.add(tr("task_duration", DisplayNumbers.grouped(task.elapsedTicks()), DisplayNumbers.grouped(task.processingTicks())));
        if (task.returnable()) lines.add(hint("task_return_hint"));
        return lines;
    }

    private List<Component> returnTooltip(int index) {
        Task task = task(index);
        return task == null ? List.of() : List.of(title("return_task"), task.title(), hint("return_task_hint"));
    }

    private Component queueRange() {
        int total = state.getIntOr("totalTasks", 0);
        return total == 0 ? tr("task_count", 0) : tr("queue_range", taskScroll.firstVisibleRow() + 1,
            taskScroll.lastVisibleRow(), total);
    }

    private HoverTooltips moduleCard() {
        return CraftingListCard.hover(moduleCardBuilder(shiftDown()).build());
    }

    /** Installed upgrade module card; {@code expanded} lists every supported card (held Shift). */
    CraftingListCard.Builder moduleCardBuilder(boolean expanded) {
        var selected = moduleDescription("moduleId", "module");
        List<Component> cards = new ArrayList<>();
        for (var module : LargeWorkstationModule.values()) {
            if (module.hasUpgradeCard()) cards.add(Component.translatable("item.neoecoae." + module.upgradeCardId().getPath())
                .withColor(module.color()));
        }
        for (var custom : ECOWorkstationModules.registered()) cards.add(custom.title().copy().withColor(custom.color()));
        var card = CraftingListCard.builder()
            .header(CraftingListCard.Glyph.PUZZLE, tr("module_slot"), Component.empty(), CraftingTooltipCard.MUTED)
            .row(null, CraftingTooltipCard.MUTED, card("installed_module"), selected.title(), 0xFF000000 | selected.color())
            .row(null, CraftingTooltipCard.MUTED, card("recipe_source"),
                moduleDescription("recipeModuleId", "recipeModule").title(), CraftingTooltipCard.PRIMARY)
            .panel(CraftingListCard.Glyph.STACK, card("card_slot"), Component.empty(), CraftingTooltipCard.MUTED, List.of(
                new CraftingListCard.PanelLine(null, card("card_slot_hint"), CraftingTooltipCard.MUTED,
                    Component.empty(), CraftingTooltipCard.MUTED),
                new CraftingListCard.PanelLine(CraftingListCard.Glyph.CHECK, card("regular_always"), CraftingTooltipCard.OK,
                    Component.empty(), CraftingTooltipCard.OK)))
            .wrapped(CraftingListCard.Glyph.INFO, card("module_swap"), CraftingTooltipCard.MUTED);
        if (state.getBooleanOr("ignoreUpgradeCards", false)) {
            card.wrapped(CraftingListCard.Glyph.CHECK, tr("compat_all_unlocked"), CraftingTooltipCard.OK);
        }
        card.divider().row(null, CraftingTooltipCard.MUTED, card("supported_cards"),
            card(expanded ? "card_count" : "card_count_shift", cards.size()), CraftingTooltipCard.ACCENT);
        if (expanded) cards.forEach(name -> card.note(name, CraftingTooltipCard.PRIMARY));
        return card;
    }

    private List<Component> parallelTooltip() {
        return List.of(title("parallel"), tr("parallel_used", HostText.expandedNumber(state.getLongOr("parallelUsed", 0L))),
            tr("parallel_capacity", HostText.expandedNumber(state.getLongOr("parallelCapacity", 0L))), hint("parallel_hint"));
    }

    private HoverTooltips energyCard() {
        return CraftingListCard.hover(energyCardBuilder().build());
    }

    /** Energy card: last-second AE-equivalent usage, physical FE consumption and the internal buffer. */
    CraftingListCard.Builder energyCardBuilder() {
        long stored = (long) state.getDoubleOr("energyStored", 0.0), capacity = (long) state.getDoubleOr("energyCapacity", 0.0);
        double fraction = capacity > 0 ? (double) stored / capacity : 0;
        return CraftingListCard.builder()
            .header(CraftingListCard.Glyph.BATTERY, card("energy_monitor"), card("last_second"), CraftingTooltipCard.MUTED)
            .wrapped(CraftingListCard.Glyph.INFO, Component.translatable(state.getBooleanOr("fePreferred", false)
                ? "gui.neoecoae.workstation.power_priority.fe" : "gui.neoecoae.workstation.power_priority.ae"), CraftingTooltipCard.PRIMARY)
            .metricUnit(card("actual_energy"), Component.literal(DisplayNumbers.compact(state.getDoubleOr("energyUsage", 0.0))),
                CraftingTooltipCard.PRIMARY, Component.literal("AE/t"), CraftingTooltipCard.WARN)
            .panel(null, Component.empty(), Component.empty(), CraftingTooltipCard.MUTED, List.of(
                new CraftingListCard.PanelLine(null, card("fe_usage"), CraftingTooltipCard.PRIMARY,
                    card("fe_rate", DisplayNumbers.compact(state.getDoubleOr("feUsage", 0.0))), CraftingTooltipCard.PRIMARY),
                new CraftingListCard.PanelLine(null, card("fe_pending"), CraftingTooltipCard.PRIMARY,
                    card("fe_amount", DisplayNumbers.compact(state.getLongOr("feBuffer", 0L))), CraftingTooltipCard.PRIMARY)))
            .row(null, CraftingTooltipCard.MUTED, card("internal_storage"),
                card("ae_stored", HostText.ae2Amount(stored), HostText.ae2Amount(capacity)), CraftingTooltipCard.PRIMARY)
            .bar(fraction, CraftingTooltipCard.WARN)
            .row(null, CraftingTooltipCard.MUTED, Component.literal(HostText.percent(fraction))
                    .withColor(CraftingTooltipCard.MUTED),
                card("ae_amount", CraftingTooltipCard.group(capacity)), CraftingTooltipCard.MUTED)
            .divider()
            .wrapped(CraftingListCard.Glyph.INFO, card("energy_footer"), CraftingTooltipCard.MUTED);
    }

    private HoverTooltips overclockCard(boolean toggle) {
        return CraftingListCard.hover(overclockCardBuilder(toggle).build());
    }

    /** Overclock card; {@code toggle} adds the mouse hints shown on the toggle button. */
    CraftingListCard.Builder overclockCardBuilder(boolean toggle) {
        boolean enabled = state.getBooleanOr("overclocked", false);
        boolean preheat = temperatureEnabled() && !state.getBooleanOr("smartCooling", false);
        var card = CraftingListCard.builder()
            .header(CraftingListCard.Glyph.BOLT, tr("overclock"), tr(enabled ? "enabled" : "disabled"),
                enabled ? CraftingTooltipCard.OK : CraftingTooltipCard.MUTED)
            .largeValue(card("parallel_limit"), CraftingTooltipCard.group(state.getLongOr("parallelCapacity", 0L)),
                CraftingTooltipCard.PRIMARY)
            .row(null, CraftingTooltipCard.MUTED, card("recipe_energy"), Component.literal("×1"), CraftingTooltipCard.OK);
        if (preheat) {
            boolean running = state.getBooleanOr("preheating", false);
            card.panel(CraftingListCard.Glyph.THERMOMETER, card("auto_preheat"),
                card(running ? "preheat_running" : "preheat_off"),
                running ? CraftingTooltipCard.WARN : CraftingTooltipCard.MUTED, List.of(
                    new CraftingListCard.PanelLine(null, card("preheat_max_energy"), CraftingTooltipCard.MUTED,
                        card("ae_rate", CraftingTooltipCard.group((long) LargeWorkstationThermal.PREHEAT_AE_PER_TICK)),
                        CraftingTooltipCard.WARN),
                    new CraftingListCard.PanelLine(null, card("preheat_stop"), CraftingTooltipCard.MUTED,
                        Component.empty(), CraftingTooltipCard.MUTED)));
        }
        card.wrapped(CraftingListCard.Glyph.INFO,
            tr(temperatureEnabled() ? "overclock_thermal_hint" : "overclock_coolant_hint"), CraftingTooltipCard.MUTED);
        if (toggle) {
            card.divider().text(mouseHint("mouse_left", "toggle_overclock"),
                preheat ? mouseHint("mouse_right", "toggle_preheat") : Component.empty(), CraftingTooltipCard.MUTED);
        }
        return card;
    }

    private static Component mouseHint(String button, String action) {
        return Component.empty().append(card(button).withStyle(ChatFormatting.BOLD).withColor(CraftingTooltipCard.PRIMARY))
            .append(" ").append(card(action).withColor(CraftingTooltipCard.MUTED));
    }

    /** Client-only: whether Shift is held, false when no window exists (tests, early init). */
    private static boolean shiftDown() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        return minecraft != null && minecraft.getWindow() != null && net.minecraft.client.Minecraft.getInstance().hasShiftDown();
    }

    private HoverTooltips temperatureCard() {
        return CraftingListCard.hover(temperatureCardBuilder().build());
    }

    /** Temperature and automatic band card drawn inside the vanilla tooltip frame. */
    CraftingListCard.Builder temperatureCardBuilder() {
        if (!temperatureEnabled()) {
            return CraftingListCard.builder()
                .header(CraftingListCard.Glyph.THERMOMETER, tr("thermal"), coolantModeText(), CraftingTooltipCard.MUTED)
                .text(tr("thermal_disabled_hint"), Component.empty(), CraftingTooltipCard.MUTED);
        }
        double temperature = state.getDoubleOr("temperature", 0.0);
        boolean overheated = state.getBooleanOr("overheated", false);
        var profile = LargeWorkstationOverclock.forTemperature(true, temperature);
        long limit = overheated ? 0 : profile.maxParallelism();
        int[] tiers = {2, 6, 9};
        List<CraftingListCard.Band> bands = new ArrayList<>();
        List<Component> ticks = new ArrayList<>();
        List<CraftingListCard.Tile> tiles = new ArrayList<>();
        ticks.add(Component.literal("0%"));
        for (int i = 0; i < tiers.length; i++) {
            var threshold = LargeWorkstationOverclock.forCoolingTier(tiers[i]);
            int minimum = (int) threshold.minimumTemperature();
            bands.add(new CraftingListCard.Band(minimum / 100.0, BAND_COLORS[i]));
            ticks.add(Component.literal(minimum + "%"));
            tiles.add(new CraftingListCard.Tile(Component.literal("≥" + minimum + "%"),
                Component.literal(CraftingTooltipCard.group(threshold.maxParallelism())),
                card("tile_caption", displayTier(tiers[i])), !overheated && profile.coolingTier() == tiers[i]));
        }
        bands.add(new CraftingListCard.Band(1.0, BAND_COLORS[tiers.length]));
        ticks.add(Component.literal("100%"));

        var card = CraftingListCard.builder()
            .header(CraftingListCard.Glyph.THERMOMETER, tr("temperature_setting"), temperatureBandName(),
                overheated ? CraftingListCard.DANGER : CraftingTooltipCard.MUTED)
            .metrics(card("current_temperature"), Component.literal(precise(temperature) + "%"),
                overheated ? CraftingListCard.DANGER : CraftingTooltipCard.ACCENT,
                card("current_parallel"), Component.literal(CraftingTooltipCard.group(limit)), CraftingTooltipCard.PRIMARY)
            .scale(bands, temperature / 100.0, ticks)
            .divider()
            .section(card("band_unlock"))
            .tiles(tiles)
            .divider()
            .row(null, CraftingTooltipCard.MUTED, card("net_change"),
                card("rate", signedRate(state.getDoubleOr("temperatureChangePerSecond", 0.0))), CraftingTooltipCard.PRIMARY);
        rateRow(card, "processing", "+", state.getDoubleOr("processingHeatPerSecond", 0.0), CraftingTooltipCard.WARN, true);
        rateRow(card, "preheat", "+", state.getDoubleOr("preheatPerSecond", 0.0), CraftingTooltipCard.WARN, false);
        rateRow(card, "cooling", "-", state.getDoubleOr("coolingPerSecond", 0.0), CraftingTooltipCard.CYAN, true);
        rateRow(card, "natural", "-", state.getDoubleOr("naturalCoolingPerSecond", 0.0), CraftingTooltipCard.CYAN, false);
        int recoverySeconds = LargeWorkstationThermal.RECOVERY_TICKS / 20;
        if (overheated) {
            card.banner(CraftingListCard.Glyph.WARNING, tr("status_overheated"),
                tr("recovery_progress", decimal(state.getIntOr("recoveryTicks", 0) / 20.0), recoverySeconds),
                CraftingListCard.DANGER, CraftingListCard.DANGER_BACKGROUND);
        } else {
            card.banner(CraftingListCard.Glyph.WARNING, card("overheat_warning"), card("overheat_recovery", recoverySeconds),
                CraftingListCard.DANGER, CraftingListCard.DANGER_BACKGROUND);
        }
        return card.text(card("temperature_footer"), Component.empty(), CraftingTooltipCard.MUTED);
    }

    private static void rateRow(CraftingListCard.Builder card, String key, String sign, double value, int color, boolean always) {
        if (!always && !(value > 0)) return;
        card.row(null, CraftingTooltipCard.MUTED, card(key).withColor(CraftingTooltipCard.MUTED),
            card("rate_short", sign + precise(value)), color);
    }

    /** Active cooling card; {@code toggle} adds the left-click hint shown on the toggle button. */
    private HoverTooltips coolingCard(boolean toggle) {
        boolean enabled = state.getBooleanOr("activeCooling", false);
        boolean smart = state.getBooleanOr("smartCooling", false);
        var card = CraftingListCard.builder()
            .header(CraftingListCard.Glyph.SNOW, tr("active_cooling"), tr(enabled ? "enabled" : "disabled"),
                enabled ? CraftingTooltipCard.OK : CraftingTooltipCard.MUTED);
        String problem = state.getStringOr("coolingProblem", "");
        if (enabled && !problem.isEmpty() && !problem.endsWith(".none")) {
            card.banner(CraftingListCard.Glyph.PAUSE, Component.translatable(problem), Component.empty(),
                CraftingTooltipCard.WARN, CraftingListCard.WARN_BACKGROUND);
        }
        if (smart) {
            int target = state.getIntOr("coolingTarget", 0);
            card.metric(card("target_temperature"), Component.literal(target + "%"), CraftingTooltipCard.CYAN,
                    card("smart_constant"), CraftingTooltipCard.MUTED)
                .bar(target / 100.0, CraftingTooltipCard.CYAN);
        }
        boolean supplied = state.getIntOr("coolantTier", 0) > 0;
        card.divider()
            .row(null, CraftingTooltipCard.MUTED, tr("coolant"), supplied ? coolantTitle() : card("coolant_unsupplied"),
                supplied ? CraftingTooltipCard.CYAN : CraftingTooltipCard.WARN);
        if (temperatureEnabled()) {
            double circulation = smart
                ? (double) state.getIntOr("coolantPerTick", 0) / LargeWorkstationThermal.COOLANT_PER_TICK : 1;
            card.row(null, CraftingTooltipCard.MUTED, card("actual_cooling"),
                    card("rate", precise(state.getDoubleOr("coolingPerSecond", 0.0))), CraftingTooltipCard.PRIMARY)
                .row(null, CraftingTooltipCard.MUTED, card("cooling_capacity"), card("rate",
                    precise(LargeWorkstationThermal.coolingPerSecond(state.getIntOr("coolantTier", 0)) * circulation)),
                    CraftingTooltipCard.PRIMARY);
        }
        card.divider();
        if (smart) {
            card.row(null, CraftingTooltipCard.MUTED,
                card("energy", CraftingTooltipCard.group(ECOLargeIntegratedWorkingStationBlockEntity.SMART_COOLING_AE_PER_TICK))
                    .withColor(CraftingTooltipCard.MUTED),
                card("coolant_usage", CraftingTooltipCard.group(state.getIntOr("coolantPerTick", 0))), CraftingTooltipCard.MUTED);
        } else {
            card.text(tr(temperatureEnabled() ? "cooling_thermal_hint" : "cooling_coolant_hint"), Component.empty(),
                    CraftingTooltipCard.MUTED)
                .text(tr("cooling_preheat_hint"), Component.empty(), CraftingTooltipCard.MUTED);
        }
        MutableComponent hint = Component.empty();
        if (toggle) hint.append(card("toggle_hint"));
        if (smart) {
            if (toggle) hint.append(" · ");
            hint.append(card("smart_hint"));
        }
        if (!hint.getString().isEmpty()) card.text(hint, Component.empty(), CraftingTooltipCard.MUTED);
        return CraftingListCard.hover(card.build());
    }

    private LargeWorkstationModule module() { return LargeWorkstationModule.byName(state.getStringOr("module", "")); }

    private ECOWorkstationModules.Module moduleDescription(String id, String legacy) {
        return moduleDescription(state, id, legacy);
    }

    private static ECOWorkstationModules.Module moduleDescription(
        CompoundTag tag, String id, String legacy) {
        var parsed = tag.contains(id) ? net.minecraft.resources.Identifier.tryParse(tag.getStringOr(id, "")) : null;
        return parsed == null ? ECOWorkstationModules.builtin(
            LargeWorkstationModule.byName(tag.getStringOr(legacy, "")))
            : ECOWorkstationModules.resolve(parsed);
    }
    private boolean temperatureEnabled() {
        return !state.contains("temperatureEnabled") || state.getBooleanOr("temperatureEnabled", false);
    }
    private Component temperatureBandName() {
        if (state.getBooleanOr("overheated", false)) return tr("status_overheated");
        int tier = LargeWorkstationOverclock.forTemperature(true, state.getDoubleOr("temperature", 0.0)).coolingTier();
        return tier == 0 ? tr("band_regular") : tr("band", displayTier(tier));
    }
    private static Component coolantModeText() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.temperature_disabled");
    }
    private Component pauseText() {
        String key = state.getStringOr("pause", "");
        return key.isEmpty() ? tr("status_idle") : Component.translatable(key)
            .withStyle(key.endsWith(".none") ? ChatFormatting.GRAY : ChatFormatting.GOLD);
    }
    private Component coolantTitle() {
        if (state.getIntOr("coolantTier", 0) == 0) return tr("no_coolant");
        return Component.translatable("gui.neoecoae.large_integrated_working_station.tier."
            + switch (state.getIntOr("coolantTier", 0)) { case 2 -> "water"; case 6 -> "sodium"; case 9 -> "cryotheum"; default -> "none"; });
    }

    private Component coolingName() {
        return tr(switch (state.getIntOr("coolantTier", 0)) {
            case 2 -> "cooling_water";
            case 6 -> "cooling_sodium";
            case 9 -> "cooling_cryotheum";
            default -> "no_coolant";
        });
    }

    private Component shortStatus() {
        if (state.getBooleanOr("overheated", false)) return tr("status_overheated");
        if (state.getBooleanOr("preheating", false)) return tr("preheat_on");
        if (state.getIntOr("totalTasks", 0) == 0) return tr("status_idle");
        return tr(hasProblem() ? "status_paused" : "status_running");
    }

    private boolean hasProblem() {
        String pause = state.getStringOr("pause", "");
        return !pause.isEmpty() && !pause.endsWith(".none") && !state.getBooleanOr("preheating", false);
    }

    @Override
    public void drawBackgroundAdditional(IGUIContext ctx) {
        if (!(ctx instanceof GUIContext context)) return;
        var module = module();
        var description = moduleDescription("moduleId", "module");
        if (module != LargeWorkstationModule.REGULAR) sprite(context, description.icon() == null ? WorkstationTextures.module(module)
            : com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture.of(description.icon()).setSprite(0, 0, 15, 15), 6, 6, 15, 15);
        text(context, Component.translatable("block.neoecoae.large_integrated_working_station"), 25, 9, 115, 0.85f, TEXT);
        text(context, tr("queue_heading"), 9, 28, 72, 0.7f, TEXT);
        text(context, tr("module_slot"), 176, 28, 69, 0.7f, TEXT);
        // The atlas alpha matches the baked circuit exactly; retain the chip and its original shadow.
        if (module != LargeWorkstationModule.REGULAR) {
            sprite(context, description.circuit() == null ? WorkstationTextures.circuit(module)
                : com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture.of(description.circuit()).setSprite(0, 0, 28, 28),
                WorkstationTextures.CIRCUIT_X, WorkstationTextures.CIRCUIT_Y, 28, 28);
        }
        text(context, description.shortTitle(), 181, 41, 35, 0.75f, 0xFFF2F2F2);
        text(context, tr(!state.getBooleanOr("moduleInstalled", false) ? "module_empty" : "module_installed"),
            181, 53, 35, 0.65f, 0xFFBEC6DD);
        if (tasks.isEmpty()) {
            centeredText(context, tr("empty_task"), 9, 65, 69, 0.8f, TEXT);
            centeredText(context, tr("queue_empty_hint"), 9, 77, 69, 0.65f, MUTED);
        }
        centeredText(context, queueRange(), 9, 119, 77, 0.65f, MUTED);
        text(context, tr("parallel"), 103, 39, 57, 0.7f, TEXT);
        text(context, Component.literal(number(state.getLongOr("parallelUsed", 0L)) + " / " + number(state.getLongOr("parallelCapacity", 0L))),
            103, 49, 57, 0.8f, TEXT);
        int parallelWidth = (int) Math.round(57 * ratio(state.getLongOr("parallelUsed", 0L), state.getLongOr("parallelCapacity", 0L)));
        if (parallelWidth > 0) sprite(context, WorkstationTextures.widget(18, 161, parallelWidth, 7), 103, 59, parallelWidth, 7);
        text(context, tr("controls_heading"), 102, 72, 59, 0.7f, TEXT);
        centeredText(context, tr("overclock_short"), 102, 82, 15, 0.65f, TEXT);
        centeredText(context, tr("cooling"), 120, 82, 15, 0.65f, TEXT);
        drawControlReadout(context);
        centeredText(context, temperatureEnabled() ? tr("current_band", temperatureBandName())
            : tr("coolant_mode"), 99, 119, 64, 0.65f, MUTED);
        centeredText(context, tr("thermal_short"), 172, 70, 15, 0.65f, TEXT);
        centeredText(context, tr("energy_short"), 189, 70, 12, 0.65f, TEXT);
        centeredText(context, tr("input_short"), 203, 70, 20, 0.7f, TEXT);
        centeredText(context, tr("output_short"), 226, 70, 20, 0.7f, TEXT);
        int heatHeight = 7 + (int) Math.round(39 * ratio(temperatureEnabled() ? state.getDoubleOr("temperature", 0.0) : 0, 100));
        sprite(context, WorkstationTextures.widget(8, 207 - heatHeight, 10, heatHeight), 175, 124 - heatHeight, 10, heatHeight);
        int energyHeight = (int) Math.round(46 * ratio(state.getDoubleOr("energyUsage", 0.0), state.getDoubleOr("energyScale", 0.0)));
        if (energyHeight > 0) sprite(context, WorkstationTextures.widget(0, 207 - energyHeight, 8, energyHeight),
            192, 124 - energyHeight, 8, energyHeight);
        sprite(context, WorkstationTextures.temperatureIcon(temperatureEnabled() ? state.getDoubleOr("temperature", 0.0) : 0), 180, 136, 12, 12);
        if (state.getIntOr("coolantTier", 0) > 0) sprite(context, WorkstationTextures.coolingIcon(state.getIntOr("coolantTier", 0)), 180, 161, 12, 12);
        if (state.getBooleanOr("overclocked", false)) sprite(context, WorkstationTextures.widget(24, 87, 12, 12), 180, 186, 12, 12);
        int band = WorkstationTextures.temperatureBand(state.getDoubleOr("temperature", 0.0));
        text(context, temperatureEnabled() ? state.getBooleanOr("overheated", false) ? tr("status_overheated")
            : band == 0 ? tr("band_regular") : tr("band", band) : tr("coolant_mode"),
            198, 134, 44, 0.75f, state.getBooleanOr("overheated", false) ? WARNING : TEXT);
        text(context, temperatureEnabled() ? tr("temperature_short", decimal(state.getDoubleOr("temperature", 0.0))) : tr("thermal_disabled_short"),
            198, 144, 44, 0.65f, MUTED);
        text(context, coolingName(), 198, 159, 44, 0.75f, TEXT);
        text(context, temperatureEnabled() ? tr("cooling_rate_short", precise(state.getDoubleOr("coolingPerSecond", 0.0)))
            : tr("thermal_disabled_short"), 198, 169, 44, 0.65f, state.getDoubleOr("coolingPerSecond", 0.0) > 0 ? SUCCESS : MUTED);
        text(context, tr(state.getBooleanOr("overclocked", false) ? "overclock_on" : "overclock_off"),
            198, 184, 44, 0.75f, state.getBooleanOr("overclocked", false) ? SUCCESS : TEXT);
        text(context, tr("parallel_short", number(state.getLongOr("parallelCapacity", 0L))), 198, 194, 44, 0.65f, MUTED);
    }

    private void drawControlReadout(GUIContext context) {
        boolean enabled = state.getBooleanOr("overclocked", false);
        // Keep the readout in the free space to the right of the adjacent toggle slots.
        fill(context, 137, 90, 25, 24, 0xFF9B9EB0);
        fill(context, 138, 91, 23, 10, enabled ? 0xFFD4E6DE : 0xFFD5D6DF);
        fill(context, 138, 102, 23, 11, 0xFFDADBE4);
        controlReadoutText(context, tr(enabled ? "enabled" : "disabled"), 94, 0.6f, enabled ? SUCCESS : MUTED);
        controlReadoutText(context, tr(temperatureEnabled() ? "thermal_mode" : "coolant_mode"), 105, 0.55f, MUTED);
    }

    private void controlReadoutText(GUIContext context, Component value, int y, float scale, int color) {
        // Fit translated mode names inside the inset without clipping or an ellipsis.
        int width = Minecraft.getInstance().font.width(value.getString());
        centeredText(context, value, 138, y, 23, Math.min(scale, 22f / Math.max(1, width)), color);
    }

    private void drawTask(GUIContext context, Task task, int row) {
        int y = 38 + row * 26;
        sprite(context, WorkstationTextures.widget(0, 62, 69, 24), 9, y, 69, 24);
        if (!task.item().isEmpty()) context.graphics.item(task.item(), Math.round(getPositionX()) + 13, Math.round(getPositionY()) + y + 4);
        else if (!task.fluid().isEmpty()) {
            // AE2 blits fluids immediately; submit the buffered card background before drawing its icon.
            context.pose.pushPose();
            context.pose.translate(getPositionX() + 13, getPositionY() + y + 4);
            AEKeyRendering.drawInGui(Minecraft.getInstance(), context.graphics, 0, 0, ECOKeyFactory.fluid(task.fluid()));
            context.pose.popPose();
        }
        text(context, task.title(), 33, y + 1, 43, 0.7f, TEXT);
        text(context, Component.literal("×" + number(task.amount())), 33, y + 8, 23, 0.6f, MUTED);
        text(context, tr(!task.current() ? "task_queued" : task.progress() >= 200 ? "task_output"
            : task.problem() ? "status_paused" : "status_running"), 58, y + 8, 18, 0.55f, task.statusColor());
        // A recessed three-pixel track stays visible even for queued tasks at zero progress.
        fill(context, 33, y + 17, 30, 4, 0xFF413F54);
        fill(context, 34, y + 18, 28, 2, 0xFF696D88);
        int progress = task.progress() <= 0 ? 0 : Math.max(1, (int) Math.round(28 * ratio(task.progress(), 200)));
        if (progress > 0) {
            fill(context, 34, y + 18, progress, 2, task.progress() >= 200 ? 0xFF57CDAE : 0xFFED7CCC);
            fill(context, 34, y + 18, progress, 1, task.progress() >= 200 ? 0xFFB3F1D8 : 0xFFFFB6E7);
        }
    }

    private static UIElement cardRegion(String id, int x, int y, int width, int height, Supplier<HoverTooltips> tooltip) {
        UIElement element = new UIElement();
        element.setId("large-workstation-" + id);
        absolute(element, x, y, width, height);
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip.get());
        return element;
    }

    private static UIElement region(String id, int x, int y, int width, int height, Supplier<List<Component>> tooltip) {
        UIElement element = new UIElement();
        element.setId("large-workstation-" + id);
        absolute(element, x, y, width, height);
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> lines = tooltip.get();
            if (!lines.isEmpty()) event.hoverTooltips = new HoverTooltips(new java.util.ArrayList<Object>(lines), null, null, null, null);
        });
        return element;
    }

    static void absolute(UIElement element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height).paddingAll(0));
    }

    private void sprite(GUIContext context, IGuiTexture texture, int x, int y, int width, int height) {
        context.drawTexture(texture, getPositionX() + x, getPositionY() + y, width, height);
    }
    private void fill(GUIContext context, int x, int y, int width, int height, int color) {
        int left = Math.round(getPositionX()) + x, top = Math.round(getPositionY()) + y;
        context.graphics.fill(left, top, left + width, top + height, color);
    }
    private void text(GUIContext context, Component value, int x, int y, int width, float scale, int color) {
        drawText(context, value, x, y, width, scale, color, false);
    }
    private void centeredText(GUIContext context, Component value, int x, int y, int width, float scale, int color) {
        drawText(context, value, x, y, width, scale, color, true);
    }
    private void drawText(GUIContext context, Component value, int x, int y, int width, float scale, int color, boolean centered) {
        var font = Minecraft.getInstance().font;
        String text = value.getString();
        int available = Math.max(1, (int) (width / scale));
        if (font.width(text) > available) text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
        context.pose.pushPose();
        context.pose.translate(getPositionX() + x + (centered ? (width - font.width(text) * scale) / 2 : 0), getPositionY() + y);
        context.pose.scale(scale, scale);
        GuiDraw.text(context.graphics, font, text, 0, 0, color, false);
        context.pose.popPose();
    }
    private static double ratio(double value, double maximum) {
        return Double.isFinite(value) && Double.isFinite(maximum) && maximum > 0 ? Math.clamp(value / maximum, 0, 1) : 0;
    }
    private static int displayTier(int tier) { return tier == 2 ? 4 : tier; }
    private static MutableComponent tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static MutableComponent card(String key, Object... args) { return tr("card." + key, args); }
    private static Component title(String key) { return tr(key).withStyle(ChatFormatting.YELLOW); }
    private static Component hint(String key) { return tr(key).withStyle(ChatFormatting.GRAY); }
    private static Component settingState(boolean enabled) {
        return tr(enabled ? "enabled" : "disabled").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
    private static String decimal(double value) { return DisplayNumbers.decimal(value, 1); }
    private static String precise(double value) { return DisplayNumbers.decimal(value, 2); }
    private static String signedRate(double value) {
        return DisplayNumbers.signedDecimal(value, 2);
    }
    private static String number(long value) {
        return DisplayNumbers.compact(Math.max(0L, value));
    }

    private record Task(UUID id, ItemStack item, FluidStack fluid, long amount, int progress, String status, String detail,
                        boolean returnable, boolean current, ECOWorkstationModules.Module module,
                        int elapsedTicks, int processingTicks) {
        Component title() { return !item.isEmpty() ? item.getHoverName() : !fluid.isEmpty() ? fluid.getHoverName() : tr("empty_task"); }
        boolean problem() { return current && !status.isEmpty() && !status.endsWith(".none") && !status.equals(PREFIX + "task_output"); }
        int statusColor() {
            return !current ? TASK_QUEUED : progress >= 200 ? MUTED : problem() ? WARNING : TASK_RUNNING;
        }
    }
}
