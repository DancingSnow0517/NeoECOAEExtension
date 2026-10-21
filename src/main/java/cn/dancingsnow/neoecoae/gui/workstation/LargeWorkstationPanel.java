package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationOverclock;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationThermal;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.workstation.LargeWorkstationUI.Config;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEventBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
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
import net.minecraftforge.fluids.FluidStack;

/** Fixed three-row viewport. Server snapshots contain only the requested page and machine telemetry. */
final class LargeWorkstationPanel extends BindableValue<Tag> {
    static final int PAGE_SIZE = 3;
    private static final int TEXT = 0xFF303448;
    private static final int MUTED = 0xFF596177;
    private static final int SUCCESS = 0xFF226B58;
    private static final int WARNING = 0xFF9B4438;
    private static final String PREFIX = "gui.neoecoae.workstation.";
    private final Config config;
    private final Player player;
    private final BindableValue<Integer> pageRequest = new BindableValue<>(0);
    private final Button[] returns = new Button[PAGE_SIZE];
    private final Button overclock;
    private final Button cooling;
    private CompoundTag state = new CompoundTag();
    private List<Task> tasks = List.of();
    private int serverOffset;
    private int requestedOffset;

    LargeWorkstationPanel(Config config, Player player) {
        super(new CompoundTag());
        this.config = config;
        this.player = player;
        layout(layout -> layout.width(253).height(214).paddingAll(0));
        setOverflowVisible(true);
        pageRequest.bind(DataBindingBuilder.intValC2S(value -> serverOffset = JdkCompat.clamp(value, 0, 509)).build());
        pageRequest.setDisplay(false);
        addChild(pageRequest);
        var returnTask = addRPCEvent(RPCEventBuilder.simple(UUID.class, id -> {
            var mui = getModularUI();
            if (id != null && mui != null && mui.getMenu() == player.containerMenu
                && player.containerMenu.stillValid(player)) config.returnTask().accept(id);
        }));
        for (int row = 0; row < PAGE_SIZE; row++) {
            final int index = row;
            UIElement hitbox = region("task-" + row, 9, 38 + row * 26, 69, 24, () -> taskTooltip(index));
            addChild(hitbox);
            Button button = new Button().noText().setOnClick(event -> {
                Task task = task(index);
                if (event.button == 0 && task != null && task.returnable()) returnTask.send(task.id());
            });
            button.setId("large-workstation-return-" + row);
            button.buttonStyle(style -> style.baseTexture(WorkstationTextures.widget(0, 6, 12, 11))
                .hoverTexture(WorkstationTextures.widget(12, 6, 12, 11))
                .pressedTexture(WorkstationTextures.widget(24, 6, 12, 11)));
            absolute(button, 65, 51 + row * 26, 12, 11);
            button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips =
                new HoverTooltips(returnTooltip(index), null, null, null));
            addChild(button);
            returns[row] = button;
        }
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0 && event.x >= getPositionX() + 9 && event.x < getPositionX() + 87
                && event.y >= getPositionY() + 38 && event.y < getPositionY() + 114) {
                scrollTo(requestedOffset + (event.deltaY < 0 ? 1 : -1));
                event.stopImmediatePropagation();
            }
        }, true);
        addChild(scrollbar());
        overclock = toggle("overclock", 102, config.toggleOverclocked(), () -> controlTooltip(this::overclockTooltip));
        addChild(overclock);
        cooling = toggle("cooling", 146, config.toggleActiveCooling(), () -> controlTooltip(this::coolingTooltip));
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

    private Button toggle(String name, int x, Runnable action, Supplier<List<Component>> tooltip) {
        Button button = new Button().noText().setOnServerClick(event -> {
            if (event.button == 0) action.run();
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
            LargeWorkstationModule.byName(state.getString("recipeModule")).title()));
        if (state.getBoolean("ignoreUpgradeCards")) lines.add(tr("compat_all_unlocked").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private void read(Tag value) {
        state = value instanceof CompoundTag tag ? tag : new CompoundTag();
        requestedOffset = state.getInt("offset");
        HolderLookup.Provider registries = config.registries().get();
        List<Task> decoded = new ArrayList<>();
        var entries = state.getList("tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(PAGE_SIZE, entries.size()); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("id")) continue;
            decoded.add(new Task(entry.getUUID("id"), registries == null ? ItemStack.EMPTY
                : cn.dancingsnow.neoecoae.platform.nbt.NEStacks.read(registries, entry.getCompound("item")), registries == null ? FluidStack.EMPTY
                : FluidStack.loadFluidStackFromNBT(entry.getCompound("fluid")), entry.getLong("amount"),
                entry.getInt("progress"), entry.getString("status"), entry.getString("detail"), entry.getBoolean("returnable"),
                entry.getBoolean("current"), LargeWorkstationModule.byName(entry.getString("module"))));
        }
        tasks = List.copyOf(decoded);
        for (int row = 0; row < PAGE_SIZE; row++) {
            Task task = task(row);
            if (returns[row] != null) returns[row].setDisplay(task != null && task.returnable());
        }
        updateToggle(overclock, state.getBoolean("overclocked"));
        updateToggle(cooling, state.getBoolean("activeCooling"));
    }

    private static void updateToggle(Button button, boolean enabled) {
        if (button != null) button.buttonStyle(style -> style.baseTexture(WorkstationTextures.toggle(enabled, false))
            .hoverTexture(WorkstationTextures.toggle(enabled, true)).pressedTexture(WorkstationTextures.toggle(enabled, true)));
    }

    private UIElement scrollbar() {
        UIElement track = new UIElement() {
            @Override
            public void drawBackgroundAdditional(GUIContext context) {
                int max = maxOffset();
                int y = max == 0 ? 0 : Math.round(62f * state.getInt("offset") / max);
                context.drawTexture(WorkstationTextures.widget(max == 0 ? 75 : 69, 62, 6, 15).setBorder(2, 0, 2, 0),
                    getPositionX(), getPositionY() + y, getSizeWidth(), 15);
            }
        };
        track.setId("large-workstation-scrollbar");
        absolute(track, 76, 37, 13, 77);
        track.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0 || maxOffset() == 0) return;
            scrollTo(Math.round(JdkCompat.clamp((event.y - track.getPositionY() - 7.5f) / 62, 0f, 1f) * maxOffset()));
            if (track.getModularUI() != null) track.getModularUI().getDragHandler().startDrag(0f, null, track);
            event.hasHandler = true;
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            scrollTo(Math.round(JdkCompat.clamp((event.y - track.getPositionY() - 7.5f) / 62, 0f, 1f) * maxOffset()));
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) scrollTo(requestedOffset + (event.deltaY < 0 ? 1 : -1));
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty()
            .append(title("queue_heading"), queueRange(), hint(maxOffset() == 0 ? "queue_all_visible" : "queue_scroll_hint")));
        return track;
    }

    private int maxOffset() { return Math.max(0, state.getInt("totalTasks") - PAGE_SIZE); }

    private void scrollTo(int offset) {
        requestedOffset = JdkCompat.clamp(offset, 0, maxOffset());
        pageRequest.setValue(requestedOffset);
    }

    private Task task(int index) { return index < tasks.size() ? tasks.get(index) : null; }

    private List<Component> taskTooltip(int index) {
        Task task = task(index);
        if (task == null) return List.of(title("empty_task"), hint("queue_empty_hint"));
        List<Component> lines = new ArrayList<>();
        lines.add(task.title().copy().withStyle(ChatFormatting.YELLOW));
        lines.add(tr(task.fluid().isEmpty() ? "task_amount" : "task_fluid_amount", HostText.expandedNumber(task.amount())));
        lines.add(tr("task_source", task.module().title()));
        lines.add(Component.translatable(task.status()).withStyle(task.problem() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        if (!task.detail().isEmpty()) lines.add(Component.translatable(task.detail()));
        lines.add(tr("progress", JdkCompat.clamp(task.progress(), 0, 200) / 2));
        if (task.returnable()) lines.add(hint("task_return_hint"));
        return lines;
    }

    private List<Component> returnTooltip(int index) {
        Task task = task(index);
        return task == null ? List.of() : List.of(title("return_task"), task.title(), hint("return_task_hint"));
    }

    private Component queueRange() {
        int total = state.getInt("totalTasks");
        return total == 0 ? tr("task_count", 0) : tr("queue_range", state.getInt("offset") + 1,
            Math.min(total, state.getInt("offset") + PAGE_SIZE), total);
    }

    private List<Component> moduleTooltip() {
        var selected = module();
        List<Component> lines = new ArrayList<>();
        lines.add(title("module_slot"));
        lines.add(tr("module_current", selected.title()).withStyle(neColor -> neColor.withColor(selected.color())));
        lines.add(tr("task_source", LargeWorkstationModule.byName(state.getString("recipeModule")).title()));
        lines.add(hint("module_slot_hint"));
        lines.add(hint("module_swap_hint"));
        lines.add(tr("module_cards"));
        for (var module : LargeWorkstationModule.values()) {
            if (module.hasUpgradeCard()) lines.add(Component.translatable("item.neoecoae." + module.upgradeCardId().getPath())
                .withStyle(neColor -> neColor.withColor(module.color())));
        }
        if (state.getBoolean("ignoreUpgradeCards")) lines.add(tr("compat_all_unlocked").withStyle(ChatFormatting.GREEN));
        return lines;
    }

    private List<Component> parallelTooltip() {
        return List.of(title("parallel"), tr("parallel_used", HostText.expandedNumber(state.getLong("parallelUsed"))),
            tr("parallel_capacity", HostText.expandedNumber(state.getLong("parallelCapacity"))), hint("parallel_hint"));
    }

    private List<Component> energyTooltip() {
        return List.of(title("energy"), tr("energy_value", decimal(state.getDouble("energyUsage"))),
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
        lines.add(tr("temperature_value", decimal(state.getDouble("temperature"))));
        lines.add(tr("current_band", displayTier(state.getInt("currentTier"))));
        lines.add(hint("temperature_automatic_hint"));
        lines.add(tr("temperature_idle_loss", decimal(LargeWorkstationThermal.IDLE_COOLING_PER_SECOND)).withStyle(ChatFormatting.GRAY));
        for (int tier : new int[] {2, 6, 9}) {
            var profile = LargeWorkstationOverclock.forCoolingTier(tier);
            lines.add(tr("temperature_band_limits", displayTier(tier), (int) profile.minimumTemperature(),
                HostText.expandedNumber(profile.maxParallelism())).withStyle(ChatFormatting.GRAY));
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
        lines.add(settingState(state.getBoolean("activeCooling")));
        lines.add(tr("coolant_value", coolantTitle()));
        if (temperatureEnabled()) {
            lines.add(tr("cooling_rate", decimal(state.getDouble("coolingPerSecond"))));
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
    private boolean temperatureEnabled() {
        return !state.contains("temperatureEnabled") || state.getBoolean("temperatureEnabled");
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
        if (module != LargeWorkstationModule.REGULAR) sprite(context, WorkstationTextures.module(module), 6, 6, 15, 15);
        text(context, Component.translatable("block.neoecoae.large_integrated_working_station"), 25, 9, 115, 0.85f, TEXT);
        text(context, tr("queue_heading"), 9, 28, 72, 0.7f, TEXT);
        text(context, tr("module_slot"), 176, 28, 69, 0.7f, TEXT);
        // The atlas alpha matches the baked circuit exactly; retain the chip and its original shadow.
        if (module != LargeWorkstationModule.REGULAR) {
            sprite(context, WorkstationTextures.circuit(module), WorkstationTextures.CIRCUIT_X, WorkstationTextures.CIRCUIT_Y, 28, 28);
        }
        text(context, module.shortTitle(), 181, 41, 35, 0.75f, 0xFFF2F2F2);
        text(context, tr(!state.getBoolean("moduleInstalled") ? "module_empty" : "module_installed"),
            181, 53, 35, 0.65f, 0xFFBEC6DD);
        if (tasks.isEmpty()) {
            centeredText(context, tr("empty_task"), 9, 65, 69, 0.8f, TEXT);
            centeredText(context, tr("queue_empty_hint"), 9, 77, 69, 0.65f, MUTED);
        }
        for (int row = 0; row < tasks.size(); row++) drawTask(context, tasks.get(row), row);
        centeredText(context, queueRange(), 9, 119, 77, 0.65f, MUTED);
        text(context, tr("parallel"), 103, 39, 57, 0.7f, TEXT);
        text(context, Component.literal(number(state.getLong("parallelUsed")) + " / " + number(state.getLong("parallelCapacity"))),
            103, 49, 57, 0.8f, TEXT);
        int parallelWidth = (int) Math.round(57 * ratio(state.getLong("parallelUsed"), state.getLong("parallelCapacity")));
        if (parallelWidth > 0) sprite(context, WorkstationTextures.widget(18, 161, parallelWidth, 7), 103, 59, parallelWidth, 7);
        text(context, tr("controls_heading"), 102, 72, 59, 0.7f, TEXT);
        centeredText(context, tr("overclock_short"), 102, 82, 15, 0.65f, TEXT);
        centeredText(context, tr("cooling"), 146, 82, 15, 0.65f, TEXT);
        text(context, tr(state.getBoolean("overclocked") ? "enabled" : "disabled"), 120, 91, 25, 0.6f,
            state.getBoolean("overclocked") ? SUCCESS : MUTED);
        text(context, tr(temperatureEnabled() ? "thermal_mode" : "coolant_mode"), 120, 103, 25, 0.55f, MUTED);
        centeredText(context, temperatureEnabled() ? tr("current_band", displayTier(state.getInt("currentTier")))
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
        text(context, temperatureEnabled() ? tr("cooling_rate_short", decimal(state.getDouble("coolingPerSecond")))
            : tr("thermal_disabled_short"), 198, 169, 44, 0.65f, state.getDouble("coolingPerSecond") > 0 ? SUCCESS : MUTED);
        text(context, tr(state.getBoolean("overclocked") ? "overclock_on" : "overclock_off"),
            198, 184, 44, 0.75f, state.getBoolean("overclocked") ? SUCCESS : TEXT);
        text(context, tr("parallel_short", number(state.getLong("parallelCapacity"))), 198, 194, 44, 0.65f, MUTED);
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
            : task.problem() ? "status_paused" : "status_running"), 58, y + 8, 18, 0.55f, task.problem() ? WARNING : MUTED);
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
        return Double.isFinite(value) && Double.isFinite(maximum) && maximum > 0 ? JdkCompat.clamp(value / maximum, 0, 1) : 0;
    }
    private static int displayTier(int tier) { return tier == 2 ? 4 : tier; }
    private static MutableComponent tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static Component title(String key) { return tr(key).withStyle(ChatFormatting.YELLOW); }
    private static Component hint(String key) { return tr(key).withStyle(ChatFormatting.GRAY); }
    private static Component settingState(boolean enabled) {
        return tr(enabled ? "enabled" : "disabled").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.1f", Double.isFinite(value) ? value : 0); }
    private static String number(double value) {
        if (!Double.isFinite(value) || value <= 0) return "0";
        if (value < 1_000) return Long.toString((long) value);
        String[] units = {"", "k", "M", "G", "T", "P", "E"};
        int unit = 0;
        while (value >= 1_000 && unit < units.length - 1) { value /= 1_000; unit++; }
        return (value >= 100 ? Long.toString(Math.round(value)) : decimal(value).replaceFirst("\\.0$", "")) + units[unit];
    }

    private record Task(UUID id, ItemStack item, FluidStack fluid, long amount, int progress, String status, String detail,
                        boolean returnable, boolean current, LargeWorkstationModule module) {
        Component title() { return !item.isEmpty() ? item.getHoverName() : !fluid.isEmpty() ? fluid.getDisplayName() : tr("empty_task"); }
        boolean problem() { return current && !status.isEmpty() && !status.endsWith(".none") && !status.equals(PREFIX + "task_output"); }
    }
}
