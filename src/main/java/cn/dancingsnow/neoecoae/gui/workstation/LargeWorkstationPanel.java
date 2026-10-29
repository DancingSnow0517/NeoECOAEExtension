package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationOverclock;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationThermal;
import cn.dancingsnow.neoecoae.gui.common.HostText;
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
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
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
            public void drawBackgroundAdditional(GUIContext context) {
                context.pose.pushPose();
                try {
                    context.pose.translate(0, taskScroll.rowShift(), 0);
                    for (int row = 0; row < tasks.size(); row++) drawTask(context, tasks.get(row), row);
                } finally {
                    context.graphics.flush();
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
                new HoverTooltips(returnTooltip(index), null, null, null));
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
        overclock = toggle("overclock", 102, config.toggleOverclocked(), config.togglePreheating(), () -> controlTooltip(this::overclockTooltip));
        addChild(overclock);
        cooling = toggle("cooling", 146, config.toggleActiveCooling(), null, () -> controlTooltip(this::coolingTooltip));
        addChild(cooling);
        addChild(region("module-logo", 6, 6, 15, 15, this::moduleTooltip));
        addChild(region("machine-title", 25, 6, 115, 14, () -> List.of(
            Component.translatable("block.neoecoae.large_integrated_working_station").withStyle(ChatFormatting.YELLOW), pauseText())));
        addChild(region("module-info", 176, 34, 69, 31, this::moduleTooltip));
        addChild(informationIcon());
        addChild(region("parallel", 99, 35, 64, 32, this::parallelTooltip));
        addChild(region("temperature", 172, 69, 15, 55, this::temperatureTooltip));
        addChild(region("energy", 189, 69, 12, 55, this::energyTooltip));
        addChild(region("temperature-setting", 99, 117, 64, 10, this::temperatureTooltip));
        addChild(region("temperature-info", 176, 130, 68, 24, this::temperatureTooltip));
        addChild(region("cooling-info", 176, 155, 68, 24, this::coolingTooltip));
        addChild(region("overclock-info", 176, 180, 68, 24, this::overclockTooltip));
        registerValueListener(this::read);
        read(config.snapshot().apply(0));
        bind(DataBindingBuilder.tagS2C(() -> config.snapshot().apply(serverOffset)).build());
    }

    private Button toggle(String name, int x, Runnable action, Runnable secondaryAction, Supplier<List<Component>> tooltip) {
        Button button = new Button().noText().setOnServerClick(event -> {
            if (event.button == 0) action.run();
            else if (event.button == 1 && secondaryAction != null) secondaryAction.run();
        });
        button.setId("large-workstation-" + name);
        absolute(button, x, 89, 15, 26);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips =
            new HoverTooltips(tooltip.get(), null, null, null));
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
        lines.add(tr("task_count", state.getInt("totalTasks")));
        if (state.getInt("totalTasks") > 0) lines.add(tr("task_source",
            moduleDescription("recipeModuleId", "recipeModule").title()));
        if (state.getBoolean("ignoreUpgradeCards")) lines.add(tr("compat_all_unlocked").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private void read(Tag value) {
        state = value instanceof CompoundTag tag ? tag : new CompoundTag();
        HolderLookup.Provider registries = config.registries().get();
        List<Task> decoded = new ArrayList<>();
        var entries = state.getList("tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(LargeWorkstationUI.SNAPSHOT_SIZE, entries.size()); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("id")) continue;
            decoded.add(new Task(entry.getUUID("id"), registries == null ? ItemStack.EMPTY
                : ItemStack.parseOptional(registries, entry.getCompound("item")), registries == null ? FluidStack.EMPTY
                : FluidStack.parseOptional(registries, entry.getCompound("fluid")), entry.getLong("amount"),
                entry.getInt("progress"), entry.getString("status"), entry.getString("detail"), entry.getBoolean("returnable"),
                entry.getBoolean("current"), moduleDescription(entry, "moduleId", "module"),
                entry.getInt("elapsedTicks"), entry.getInt("processingTicks")));
        }
        int first = state.contains("taskOffset") ? state.getInt("taskOffset") : state.getInt("offset");
        if (taskScroll.receive(state.getInt("totalTasks"), first, decoded.size())) tasks = List.copyOf(decoded);
        updateTaskRows();
        updateToggle(overclock, state.getBoolean("overclocked"));
        updateToggle(cooling, state.getBoolean("activeCooling"));
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
            public void drawBackgroundAdditional(GUIContext context) {
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

    private int maxOffset() { return Math.max(0, state.getInt("totalTasks") - LargeWorkstationUI.PAGE_SIZE); }

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
        if (task.processingTicks() > 0) lines.add(tr("task_duration", task.elapsedTicks(), task.processingTicks()));
        if (task.returnable()) lines.add(hint("task_return_hint"));
        return lines;
    }

    private List<Component> returnTooltip(int index) {
        Task task = task(index);
        return task == null ? List.of() : List.of(title("return_task"), task.title(), hint("return_task_hint"));
    }

    private Component queueRange() {
        int total = state.getInt("totalTasks");
        return total == 0 ? tr("task_count", 0) : tr("queue_range", taskScroll.firstVisibleRow() + 1,
            taskScroll.lastVisibleRow(), total);
    }

    private List<Component> moduleTooltip() {
        var selected = moduleDescription("moduleId", "module");
        List<Component> lines = new ArrayList<>();
        lines.add(title("module_slot"));
        lines.add(tr("module_current", selected.title()).withColor(selected.color()));
        lines.add(tr("task_source", moduleDescription("recipeModuleId", "recipeModule").title()));
        lines.add(hint("module_slot_hint"));
        lines.add(hint("module_swap_hint"));
        lines.add(tr("module_cards"));
        for (var module : LargeWorkstationModule.values()) {
            if (module.hasUpgradeCard()) lines.add(Component.translatable("item.neoecoae." + module.upgradeCardId().getPath())
                .withColor(module.color()));
        }
        for (var custom : ECOWorkstationModules.registered())
            lines.add(custom.title().copy().withColor(custom.color()));
        if (state.getBoolean("ignoreUpgradeCards")) lines.add(tr("compat_all_unlocked").withStyle(ChatFormatting.GREEN));
        return lines;
    }

    private List<Component> parallelTooltip() {
        return List.of(title("parallel"), tr("parallel_used", HostText.expandedNumber(state.getLong("parallelUsed"))),
            tr("parallel_capacity", HostText.expandedNumber(state.getLong("parallelCapacity"))), hint("parallel_hint"));
    }

    private List<Component> energyTooltip() {
        return List.of(title("energy"), tr("energy_value", decimal(state.getDouble("energyUsage"))),
            tr("fe_value", decimal(state.getDouble("feUsage")), HostText.expandedNumber(state.getLong("feBuffer"))),
            hint("fe_requirement"),
            tr("energy_stored", HostText.expandedNumber((long) state.getDouble("energyStored")),
                HostText.expandedNumber((long) state.getDouble("energyCapacity"))), hint("energy_hint"));
    }

    private List<Component> overclockTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(title("overclock"));
        lines.add(settingState(state.getBoolean("overclocked")));
        lines.add(tr("parallel_capacity", HostText.expandedNumber(state.getLong("parallelCapacity"))));
        lines.add(hint(temperatureEnabled() ? "overclock_thermal_hint" : "overclock_coolant_hint"));
        lines.add(hint("overclock_energy_hint"));
        if (temperatureEnabled()) {
            lines.add(hint("preheat_right_click_hint"));
            lines.add(tr(state.getBoolean("preheating") ? "preheat_on" : "preheat_off"));
            lines.add(tr("preheat_energy", HostText.expandedNumber((long) LargeWorkstationThermal.PREHEAT_AE_PER_TICK)));
            lines.add(hint("preheat_stop_hint"));
        }
        return lines;
    }

    private List<Component> controlTooltip(Supplier<List<Component>> description) {
        List<Component> lines = new ArrayList<>(description.get());
        lines.add(hint("toggle_hint"));
        return lines;
    }

    private List<Component> temperatureTooltip() {
        if (!temperatureEnabled()) return List.of(title("thermal"), coolantModeText(), hint("thermal_disabled_hint"));
        List<Component> lines = new ArrayList<>();
        lines.add(title("temperature_setting"));
        lines.add(tr("temperature_value", precise(state.getDouble("temperature"))));
        var profile = LargeWorkstationOverclock.forTemperature(true, state.getDouble("temperature"));
        long limit = state.getBoolean("overheated") ? 0 : profile.maxParallelism();
        lines.add(tr("current_band", tr("temperature_band_value", temperatureBandName(), HostText.expandedNumber(limit))));
        lines.add(tr("temperature_net_rate", signedRate(state.getDouble("temperatureChangePerSecond"))));
        lines.add(tr("temperature_processing_rate", precise(state.getDouble("processingHeatPerSecond"))));
        if (state.getDouble("preheatPerSecond") > 0) {
            lines.add(tr("temperature_preheat_rate", precise(state.getDouble("preheatPerSecond"))));
        }
        lines.add(tr("temperature_cooling_rate", precise(state.getDouble("coolingPerSecond"))));
        if (state.getDouble("naturalCoolingPerSecond") > 0) {
            lines.add(tr("temperature_natural_rate", precise(state.getDouble("naturalCoolingPerSecond"))));
        }
        lines.add(hint("temperature_automatic_hint"));
        lines.add(hint("temperature_slowing_hint"));
        for (int tier : new int[] {2, 6, 9}) {
            var threshold = LargeWorkstationOverclock.forCoolingTier(tier);
            lines.add(tr("temperature_band_limits", displayTier(tier), (int) threshold.minimumTemperature(),
                HostText.expandedNumber(threshold.maxParallelism())).withStyle(ChatFormatting.GRAY));
        }
        if (state.getBoolean("overheated")) {
            lines.add(pauseText());
            lines.add(tr("recovery_progress", decimal(state.getInt("recoveryTicks") / 20.0),
                LargeWorkstationThermal.RECOVERY_TICKS / 20));
        } else lines.add(hint("temperature_overheat_hint"));
        return lines;
    }

    private List<Component> coolingTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(title("active_cooling"));
        if (state.getBoolean("smartCooling")) {
            lines.add(Component.translatable("gui.neoecoae.smart_cooling.control", state.getInt("coolingTarget"))
                .withStyle(ChatFormatting.AQUA));
        }
        lines.add(settingState(state.getBoolean("activeCooling")));
        lines.add(tr("coolant_value", coolantTitle()));
        if (temperatureEnabled()) {
            lines.add(tr("cooling_rate", precise(state.getDouble("coolingPerSecond"))));
            lines.add(tr("cooling_capacity", decimal(LargeWorkstationThermal.coolingPerSecond(state.getInt("coolantTier"))))
                .withStyle(ChatFormatting.GRAY));
        }
        lines.add(hint(temperatureEnabled() ? "cooling_thermal_hint" : "cooling_coolant_hint"));
        String problem = state.getString("coolingProblem");
        if (state.getBoolean("activeCooling") && !problem.isEmpty() && !problem.endsWith(".none")) {
            lines.add(Component.translatable(problem).withStyle(ChatFormatting.GOLD));
        }
        lines.add(hint("cooling_preheat_hint"));
        return lines;
    }

    private LargeWorkstationModule module() { return LargeWorkstationModule.byName(state.getString("module")); }

    private ECOWorkstationModules.Module moduleDescription(String id, String legacy) {
        return moduleDescription(state, id, legacy);
    }

    private static ECOWorkstationModules.Module moduleDescription(
        CompoundTag tag, String id, String legacy) {
        var parsed = tag.contains(id) ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString(id)) : null;
        return parsed == null ? ECOWorkstationModules.builtin(
            LargeWorkstationModule.byName(tag.getString(legacy)))
            : ECOWorkstationModules.resolve(parsed);
    }
    private boolean temperatureEnabled() {
        return !state.contains("temperatureEnabled") || state.getBoolean("temperatureEnabled");
    }
    private Component temperatureBandName() {
        if (state.getBoolean("overheated")) return tr("status_overheated");
        int tier = LargeWorkstationOverclock.forTemperature(true, state.getDouble("temperature")).coolingTier();
        return tier == 0 ? tr("band_regular") : tr("band", displayTier(tier));
    }
    private static Component coolantModeText() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.temperature_disabled");
    }
    private Component pauseText() {
        String key = state.getString("pause");
        return key.isEmpty() ? tr("status_idle") : Component.translatable(key)
            .withStyle(key.endsWith(".none") ? ChatFormatting.GRAY : ChatFormatting.GOLD);
    }
    private Component coolantTitle() {
        if (state.getInt("coolantTier") == 0) return tr("no_coolant");
        return Component.translatable("gui.neoecoae.large_integrated_working_station.tier."
            + switch (state.getInt("coolantTier")) { case 2 -> "water"; case 6 -> "sodium"; case 9 -> "cryotheum"; default -> "none"; });
    }

    private Component coolingName() {
        return tr(switch (state.getInt("coolantTier")) {
            case 2 -> "cooling_water";
            case 6 -> "cooling_sodium";
            case 9 -> "cooling_cryotheum";
            default -> "no_coolant";
        });
    }

    private Component shortStatus() {
        if (state.getBoolean("overheated")) return tr("status_overheated");
        if (state.getBoolean("preheating")) return tr("preheat_on");
        if (state.getInt("totalTasks") == 0) return tr("status_idle");
        return tr(hasProblem() ? "status_paused" : "status_running");
    }

    private boolean hasProblem() {
        String pause = state.getString("pause");
        return !pause.isEmpty() && !pause.endsWith(".none") && !state.getBoolean("preheating");
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
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
        text(context, tr(!state.getBoolean("moduleInstalled") ? "module_empty" : "module_installed"),
            181, 53, 35, 0.65f, 0xFFBEC6DD);
        if (tasks.isEmpty()) {
            centeredText(context, tr("empty_task"), 9, 65, 69, 0.8f, TEXT);
            centeredText(context, tr("queue_empty_hint"), 9, 77, 69, 0.65f, MUTED);
        }
        centeredText(context, queueRange(), 9, 119, 77, 0.65f, MUTED);
        text(context, tr("parallel"), 103, 39, 57, 0.7f, TEXT);
        text(context, Component.literal(number(state.getLong("parallelUsed")) + " / " + number(state.getLong("parallelCapacity"))),
            103, 49, 57, 0.8f, TEXT);
        int parallelWidth = (int) Math.round(57 * ratio(state.getLong("parallelUsed"), state.getLong("parallelCapacity")));
        if (parallelWidth > 0) sprite(context, WorkstationTextures.widget(18, 161, parallelWidth, 7), 103, 59, parallelWidth, 7);
        text(context, tr("controls_heading"), 102, 72, 59, 0.7f, TEXT);
        centeredText(context, tr("overclock_short"), 102, 82, 15, 0.65f, TEXT);
        centeredText(context, tr("cooling"), 146, 82, 15, 0.65f, TEXT);
        drawControlReadout(context);
        centeredText(context, temperatureEnabled() ? tr("current_band", temperatureBandName())
            : tr("coolant_mode"), 99, 119, 64, 0.65f, MUTED);
        centeredText(context, tr("thermal_short"), 172, 70, 15, 0.65f, TEXT);
        centeredText(context, tr("energy_short"), 189, 70, 12, 0.65f, TEXT);
        centeredText(context, tr("input_short"), 203, 70, 20, 0.7f, TEXT);
        centeredText(context, tr("output_short"), 226, 70, 20, 0.7f, TEXT);
        int heatHeight = 7 + (int) Math.round(39 * ratio(temperatureEnabled() ? state.getDouble("temperature") : 0, 100));
        sprite(context, WorkstationTextures.widget(8, 207 - heatHeight, 10, heatHeight), 175, 124 - heatHeight, 10, heatHeight);
        int energyHeight = (int) Math.round(46 * ratio(state.getDouble("energyUsage"), state.getDouble("energyScale")));
        if (energyHeight > 0) sprite(context, WorkstationTextures.widget(0, 207 - energyHeight, 8, energyHeight),
            192, 124 - energyHeight, 8, energyHeight);
        sprite(context, WorkstationTextures.temperatureIcon(temperatureEnabled() ? state.getDouble("temperature") : 0), 180, 136, 12, 12);
        if (state.getInt("coolantTier") > 0) sprite(context, WorkstationTextures.coolingIcon(state.getInt("coolantTier")), 180, 161, 12, 12);
        if (state.getBoolean("overclocked")) sprite(context, WorkstationTextures.widget(24, 87, 12, 12), 180, 186, 12, 12);
        int band = WorkstationTextures.temperatureBand(state.getDouble("temperature"));
        text(context, temperatureEnabled() ? state.getBoolean("overheated") ? tr("status_overheated")
            : band == 0 ? tr("band_regular") : tr("band", band) : tr("coolant_mode"),
            198, 134, 44, 0.75f, state.getBoolean("overheated") ? WARNING : TEXT);
        text(context, temperatureEnabled() ? tr("temperature_short", decimal(state.getDouble("temperature"))) : tr("thermal_disabled_short"),
            198, 144, 44, 0.65f, MUTED);
        text(context, coolingName(), 198, 159, 44, 0.75f, TEXT);
        text(context, temperatureEnabled() ? tr("cooling_rate_short", precise(state.getDouble("coolingPerSecond")))
            : tr("thermal_disabled_short"), 198, 169, 44, 0.65f, state.getDouble("coolingPerSecond") > 0 ? SUCCESS : MUTED);
        text(context, tr(state.getBoolean("overclocked") ? "overclock_on" : "overclock_off"),
            198, 184, 44, 0.75f, state.getBoolean("overclocked") ? SUCCESS : TEXT);
        text(context, tr("parallel_short", number(state.getLong("parallelCapacity"))), 198, 194, 44, 0.65f, MUTED);
    }

    private void drawControlReadout(GUIContext context) {
        boolean enabled = state.getBoolean("overclocked");
        // Center the readout in the 29-pixel gap between the unchanged toggle slots.
        fill(context, 119, 90, 25, 24, 0xFF9B9EB0);
        fill(context, 120, 91, 23, 10, enabled ? 0xFFD4E6DE : 0xFFD5D6DF);
        fill(context, 120, 102, 23, 11, 0xFFDADBE4);
        controlReadoutText(context, tr(enabled ? "enabled" : "disabled"), 94, 0.6f, enabled ? SUCCESS : MUTED);
        controlReadoutText(context, tr(temperatureEnabled() ? "thermal_mode" : "coolant_mode"), 105, 0.55f, MUTED);
    }

    private void controlReadoutText(GUIContext context, Component value, int y, float scale, int color) {
        // Fit translated mode names inside the inset without clipping or an ellipsis.
        int width = Minecraft.getInstance().font.width(value.getString());
        centeredText(context, value, 120, y, 23, Math.min(scale, 22f / Math.max(1, width)), color);
    }

    private void drawTask(GUIContext context, Task task, int row) {
        int y = 38 + row * 26;
        sprite(context, WorkstationTextures.widget(0, 62, 69, 24), 9, y, 69, 24);
        if (!task.item().isEmpty()) context.graphics.renderItem(task.item(), Math.round(getPositionX()) + 13, Math.round(getPositionY()) + y + 4);
        else if (!task.fluid().isEmpty()) {
            // AE2 blits fluids immediately; submit the buffered card background before drawing its icon.
            context.graphics.flush();
            context.pose.pushPose();
            context.pose.translate(getPositionX() + 13, getPositionY() + y + 4, 0);
            AEKeyRendering.drawInGui(Minecraft.getInstance(), context.graphics, 0, 0, AEFluidKey.of(task.fluid()));
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

    private static UIElement region(String id, int x, int y, int width, int height, Supplier<List<Component>> tooltip) {
        UIElement element = new UIElement();
        element.setId("large-workstation-" + id);
        absolute(element, x, y, width, height);
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> lines = tooltip.get();
            if (!lines.isEmpty()) event.hoverTooltips = new HoverTooltips(lines, null, null, null);
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
        context.pose.translate(getPositionX() + x + (centered ? (width - font.width(text) * scale) / 2 : 0), getPositionY() + y, 0);
        context.pose.scale(scale, scale, 1);
        context.graphics.drawString(font, text, 0, 0, color, false);
        context.pose.popPose();
    }
    private static double ratio(double value, double maximum) {
        return Double.isFinite(value) && Double.isFinite(maximum) && maximum > 0 ? Math.clamp(value / maximum, 0, 1) : 0;
    }
    private static int displayTier(int tier) { return tier == 2 ? 4 : tier; }
    private static MutableComponent tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static Component title(String key) { return tr(key).withStyle(ChatFormatting.YELLOW); }
    private static Component hint(String key) { return tr(key).withStyle(ChatFormatting.GRAY); }
    private static Component settingState(boolean enabled) {
        return tr(enabled ? "enabled" : "disabled").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.1f", Double.isFinite(value) ? value : 0); }
    private static String precise(double value) { return String.format(Locale.ROOT, "%.2f", Double.isFinite(value) ? value : 0); }
    private static String signedRate(double value) {
        return String.format(Locale.ROOT, "%+.2f", Double.isFinite(value) && Math.abs(value) >= 0.005 ? value : 0);
    }
    private static String number(double value) {
        if (!Double.isFinite(value) || value <= 0) return "0";
        if (value < 1_000) return Long.toString((long) value);
        String[] units = {"", "k", "M", "G", "T", "P", "E"};
        int unit = 0;
        while (value >= 1_000 && unit < units.length - 1) { value /= 1_000; unit++; }
        return (value >= 100 ? Long.toString(Math.round(value)) : decimal(value).replaceFirst("\\.0$", "")) + units[unit];
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
