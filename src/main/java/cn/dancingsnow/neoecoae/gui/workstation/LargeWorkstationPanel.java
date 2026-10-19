package cn.dancingsnow.neoecoae.gui.workstation;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.gui.workstation.LargeWorkstationUI.Config;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fixed three-row viewport. Server snapshots contain only the requested page and machine telemetry. */
final class LargeWorkstationPanel extends BindableValue<Tag> {
    static final int PAGE_SIZE = 3;
    private static final int TEXT = 0xFF413F54;
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
        pageRequest.bind(DataBindingBuilder.intValC2S(value -> serverOffset = Math.clamp(value, 0, 509)).build());
        pageRequest.setDisplay(false);
        addChild(pageRequest);
        var returnTask = addRPCEvent(RPCEventBuilder.simple(UUID.class, id -> {
            var mui = getModularUI();
            if (id != null && mui != null && mui.getMenu() == player.containerMenu
                && player.containerMenu.stillValid(player)) config.returnTask().accept(id);
        }));
        for (int row = 0; row < PAGE_SIZE; row++) {
            final int index = row;
            UIElement hitbox = region(9, 38 + row * 26, 69, 24, () -> taskTooltip(index));
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
                HoverTooltips.empty().append(tr("return_task")));
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
        overclock = toggle(103, "overclock", config.toggleOverclocked());
        cooling = toggle(128, "cooling", config.toggleActiveCooling());
        addChildren(overclock, cooling);
        addChild(region(6, 6, 15, 15, this::moduleTooltip));
        addChild(region(176, 35, 69, 30, this::moduleTooltip));
        addChild(region(102, 43, 60, 24, () -> List.of(tr("parallel_value",
            number(state.getLong("parallelUsed")), number(state.getLong("parallelCapacity"))))));
        addChild(region(175, 78, 11, 46, () -> thermalInfo(List.of(tr("temperature_value", decimal(state.getDouble("temperature"))),
            tr("temperature_ranges"), pauseText()))));
        addChild(region(191, 78, 8, 46, () -> List.of(tr("energy_value", decimal(state.getDouble("energyUsage"))),
            tr("energy_stored", number(state.getDouble("energyStored")), number(state.getDouble("energyCapacity"))))));
        addChild(infoButton(130, config.cycleOverclockTier(), () -> thermalInfo(List.of(tr("temperature_ranges"),
            tr("selected_band", displayTier(state.getInt("selectedTier"))), tr("select_preheat_band")))));
        addChild(infoButton(155, config.toggleActiveCooling(), () -> List.of(tr("cooling"), tr("coolant_value", coolantTitle()), pauseText())));
        addChild(infoButton(180, config.togglePreheating(), () -> thermalInfo(List.of(tr(state.getBoolean("preheating") ? "preheat_on" : "preheat_off"),
            tr("selected_band", displayTier(state.getInt("selectedTier")))))));
        registerValueListener(this::read);
        read(config.snapshot().apply(0));
        bind(DataBindingBuilder.tagS2C(() -> config.snapshot().apply(serverOffset)).build());
    }

    private Button toggle(int x, String name, Runnable action) {
        Button button = new Button().noText().setOnServerClick(event -> {
            if (event.button == 0) action.run();
        });
        button.setId("large-workstation-" + name);
        absolute(button, x, 88, 15, 26);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty()
            .append(tr(name)).append(tr(state.getBoolean(name.equals("overclock") ? "overclocked" : "activeCooling") ? "enabled" : "disabled"))
            .append(pauseText()));
        return button;
    }

    private Button infoButton(int y, Runnable action, java.util.function.Supplier<List<Component>> tooltip) {
        Button button = new Button().noText().setOnServerClick(event -> { if (event.button == 0) action.run(); });
        button.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY).hoverTexture(IGuiTexture.EMPTY).pressedTexture(IGuiTexture.EMPTY));
        absolute(button, 176, y, 68, 24);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(tooltip.get(), null, null, null));
        return button;
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
                : ItemStack.parseOptional(registries, entry.getCompound("item")), registries == null ? FluidStack.EMPTY
                : FluidStack.parseOptional(registries, entry.getCompound("fluid")), entry.getLong("amount"),
                entry.getInt("progress"), entry.getString("status"), entry.getString("detail"), entry.getBoolean("returnable")));
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
                context.drawTexture(WorkstationTextures.widget(69, 62, 6, 14), getPositionX(), getPositionY() + y, 6, 14);
            }
        };
        absolute(track, 81, 38, 6, 76);
        track.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0 || maxOffset() == 0) return;
            scrollTo(Math.round(Math.clamp((event.y - track.getPositionY() - 7) / 62, 0f, 1f) * maxOffset()));
            if (track.getModularUI() != null) track.getModularUI().getDragHandler().startDrag(0f, null, track);
            event.hasHandler = true;
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            scrollTo(Math.round(Math.clamp((event.y - track.getPositionY() - 7) / 62, 0f, 1f) * maxOffset()));
            event.stopImmediatePropagation();
        });
        track.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) scrollTo(requestedOffset + (event.deltaY < 0 ? 1 : -1));
            event.stopImmediatePropagation();
        });
        return track;
    }

    private int maxOffset() { return Math.max(0, state.getInt("totalTasks") - PAGE_SIZE); }

    private void scrollTo(int offset) {
        requestedOffset = Math.clamp(offset, 0, maxOffset());
        pageRequest.setValue(requestedOffset);
    }

    private Task task(int index) { return index < tasks.size() ? tasks.get(index) : null; }

    private List<Component> taskTooltip(int index) {
        Task task = task(index);
        if (task == null) return List.of(tr("empty_task"));
        List<Component> lines = new ArrayList<>();
        lines.add(task.title());
        lines.add(tr("task_amount", number(task.amount())));
        lines.add(Component.translatable(task.status()));
        if (!task.detail().isEmpty()) lines.add(Component.translatable(task.detail()));
        lines.add(tr("progress", Math.clamp(task.progress(), 0, 200) / 2));
        return lines;
    }

    private List<Component> moduleTooltip() {
        var selected = module();
        List<Component> lines = new ArrayList<>();
        lines.add(tr("module_current", selected.title()).copy().withColor(selected.color()));
        for (var module : LargeWorkstationModule.values()) lines.add(module.title().copy().withColor(module.color()));
        return lines;
    }

    private LargeWorkstationModule module() { return LargeWorkstationModule.byName(state.getString("module")); }
    private boolean temperatureEnabled() {
        return !state.contains("temperatureEnabled") || state.getBoolean("temperatureEnabled");
    }
    private static Component coolantModeText() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.temperature_disabled");
    }
    private List<Component> thermalInfo(List<Component> enabled) {
        return temperatureEnabled() ? enabled : List.of(coolantModeText());
    }
    private Component pauseText() {
        String key = state.getString("pause");
        return key.isEmpty() ? tr("empty_task") : Component.translatable(key);
    }
    private Component coolantTitle() { return Component.translatable("gui.neoecoae.large_integrated_working_station.tier."
        + switch (state.getInt("coolantTier")) { case 2 -> "water"; case 6 -> "sodium"; case 9 -> "cryotheum"; default -> "none"; }); }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var module = module();
        sprite(context, WorkstationTextures.module(module), 6, 6, 15, 15);
        text(context, Component.translatable("block.neoecoae.large_integrated_working_station"), 25, 9, 115, 0.7f, TEXT);
        // Clear the neutral circuit artwork before laying down the selected transparent theme.
        if (module != LargeWorkstationModule.REGULAR) {
            fill(context, 217, 35, 28, 30, 0xFF413F54);
            fill(context, 224, 40, 20, 20, 0xFF5F5F7A);
            fill(context, 224, 60, 20, 2, 0xFF25233A);
            sprite(context, WorkstationTextures.circuit(module), 217, 37, 28, 28);
        }
        text(context, module.title(), 178, 40, 35, 0.6f, 0xFFF2F2F2);
        text(context, tr("task_count", state.getInt("totalTasks")), 178, 51, 35, 0.55f, 0xFFADB0C4);
        if (tasks.isEmpty()) text(context, tr("empty_task"), 16, 72, 55, 0.65f, TEXT);
        for (int row = 0; row < tasks.size(); row++) drawTask(context, tasks.get(row), row);
        text(context, tr("parallel"), 103, 40, 54, 0.7f, TEXT);
        text(context, Component.literal(number(state.getLong("parallelUsed")) + " / " + number(state.getLong("parallelCapacity"))),
            103, 49, 54, 0.6f, TEXT);
        int parallelWidth = (int) Math.round(57 * ratio(state.getLong("parallelUsed"), state.getLong("parallelCapacity")));
        if (parallelWidth > 0) sprite(context, WorkstationTextures.widget(18, 161, parallelWidth, 7), 103, 59, parallelWidth, 7);
        text(context, tr("overclock"), 101, 81, 22, 0.6f, TEXT);
        text(context, tr("cooling"), 126, 81, 27, 0.6f, TEXT);
        int heatHeight = 7 + (int) Math.round(39 * ratio(temperatureEnabled() ? state.getDouble("temperature") : 0, 100));
        sprite(context, WorkstationTextures.widget(8, 207 - heatHeight, 10, heatHeight), 175, 124 - heatHeight, 10, heatHeight);
        int energyHeight = (int) Math.round(46 * ratio(state.getDouble("energyUsage"), state.getDouble("energyScale")));
        if (energyHeight > 0) sprite(context, WorkstationTextures.widget(0, 207 - energyHeight, 8, energyHeight),
            191, 124 - energyHeight, 8, energyHeight);
        sprite(context, WorkstationTextures.temperatureIcon(state.getDouble("temperature")), 178, 135, 18, 18);
        sprite(context, WorkstationTextures.coolingIcon(state.getInt("coolantTier")), 178, 160, 18, 18);
        sprite(context, WorkstationTextures.widget(24, 87, 12, 12), 178, 185, 18, 18);
        int band = WorkstationTextures.temperatureBand(state.getDouble("temperature"));
        text(context, temperatureEnabled() ? (band == 0 ? tr("band_regular") : tr("band", band)) : coolantModeText(), 199, 133, 43, 0.65f, TEXT);
        text(context, Component.literal(temperatureEnabled() ? decimal(state.getDouble("temperature")) + "%" : "--"), 199, 143, 43, 0.6f, TEXT);
        text(context, coolantTitle(), 199, 158, 43, 0.65f, TEXT);
        text(context, tr(state.getBoolean("activeCooling") ? "enabled" : "disabled"), 199, 168, 43, 0.6f, TEXT);
        text(context, temperatureEnabled() ? tr(state.getBoolean("preheating") ? "preheat_on" : "preheat_off") : coolantModeText(), 199, 183, 43, 0.6f, TEXT);
        text(context, temperatureEnabled() ? tr("selected_band", displayTier(state.getInt("selectedTier"))) : Component.literal("--"), 199, 193, 43, 0.6f, TEXT);
    }

    private void drawTask(GUIContext context, Task task, int row) {
        int y = 38 + row * 26;
        sprite(context, WorkstationTextures.widget(0, 62, 69, 24), 9, y, 69, 24);
        if (!task.item().isEmpty()) context.graphics.renderItem(task.item(), Math.round(getPositionX()) + 13, Math.round(getPositionY()) + y + 4);
        else if (!task.fluid().isEmpty()) {
            context.pose.pushPose();
            context.pose.translate(getPositionX() + 13, getPositionY() + y + 4, 0);
            AEKeyRendering.drawInGui(Minecraft.getInstance(), context.graphics, 0, 0, AEFluidKey.of(task.fluid()));
            context.pose.popPose();
        }
        text(context, task.title(), 33, y + 3, 42, 0.6f, TEXT);
        text(context, Component.translatable(task.status()), 33, y + 12, 29, 0.55f, TEXT);
        fill(context, 33, y + 21, 29, 1, 0xFF696D88);
        int progress = (int) Math.round(29 * ratio(task.progress(), 200));
        if (progress > 0) fill(context, 33, y + 21, progress, 1, 0xFFED7CCC);
    }

    private static UIElement region(int x, int y, int width, int height, java.util.function.Supplier<List<Component>> tooltip) {
        UIElement element = new UIElement();
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
        var font = Minecraft.getInstance().font;
        String text = font.plainSubstrByWidth(value.getString(), Math.max(1, (int) (width / scale)));
        context.pose.pushPose();
        context.pose.translate(getPositionX() + x, getPositionY() + y, 0);
        context.pose.scale(scale, scale, 1);
        context.graphics.drawString(font, text, 0, 0, color, false);
        context.pose.popPose();
    }
    private static double ratio(double value, double maximum) {
        return Double.isFinite(value) && Double.isFinite(maximum) && maximum > 0 ? Math.clamp(value / maximum, 0, 1) : 0;
    }
    private static int displayTier(int tier) { return tier == 2 ? 4 : tier; }
    private static Component tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.1f", Double.isFinite(value) ? value : 0); }
    private static String number(double value) {
        if (value >= 1_000_000) return decimal(value / 1_000_000) + "M";
        if (value >= 1_000) return decimal(value / 1_000) + "k";
        return Long.toString(Math.max(0, (long) value));
    }

    private record Task(UUID id, ItemStack item, FluidStack fluid, long amount, int progress, String status, String detail, boolean returnable) {
        Component title() { return !item.isEmpty() ? item.getHoverName() : !fluid.isEmpty() ? fluid.getHoverName() : tr("empty_task"); }
    }
}
