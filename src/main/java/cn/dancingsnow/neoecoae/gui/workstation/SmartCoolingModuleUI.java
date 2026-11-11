package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationThermal;
import cn.dancingsnow.neoecoae.gui.common.AE2TextButton;
import cn.dancingsnow.neoecoae.gui.common.AE2TextField;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchInterfaceConfig;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEmitter;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEventBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.Horizontal;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.TextWrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.FluidSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextField;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidStack;

/**
 * Smart cooling module panel laid out as header, target control, status/buffer, parameters and notes.
 * Artwork comes from the bundled AE2 panel, inset, button, text-field and state-icon sprites.
 */
public final class SmartCoolingModuleUI {
    static final int WIDTH = 236;
    static final int HEIGHT = 236;
    private static final String PREFIX = "gui.neoecoae.smart_cooling.";
    private static final int TEXT = 0xFF413F54;
    private static final int MUTED = 0xFF5E6278;
    private static final int ACCENT = 0xFF2F5E8E;
    private static final int SUCCESS = 0xFF226B58;
    private static final int PENDING = 0xFF8A5A12;
    private static final int WARNING = 0xFF9B4438;

    /** Panel phase shown by the badge and status color; ordinal is synchronized. */
    enum Phase {
        OFFLINE(PENDING, 0xFFE4D6BC),
        DISABLED(PENDING, 0xFFE4D6BC),
        ACTIVE(SUCCESS, 0xFFCFE3D8),
        ALERT(WARNING, 0xFFEBD0CB);

        final int color;
        final int fill;

        Phase(int color, int fill) {
            this.color = color;
            this.fill = fill;
        }

        Component badge() {
            return Component.translatable(PREFIX + "badge." + name().toLowerCase(Locale.ROOT));
        }
    }

    private SmartCoolingModuleUI() {}

    public static ModularUI create(BlockUIMenuType.BlockUIHolder holder,
        ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        return ECOModularUI.create(createRoot(host, holder.player), holder.player);
    }

    static UIElement createRoot(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        return createRoot(host, null);
    }

    static UIElement createRoot(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host, Player player) {
        View view = new View();
        view.read(snapshot(host));

        UIElement root = new UIElement();
        root.setId("smart-cooling-root");
        root.layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        root.style(style -> style.backgroundTexture(NETextures.BACKGROUND));

        // Header: module item in an AE2 slot, title, subtitle and phase badge.
        UIElement moduleIcon = new UIElement() {
            @Override
            public void drawBackgroundAdditional(GUIContext context) {
                super.drawBackgroundAdditional(context);
                ItemStack stack = new ItemStack(host.getBlockState().getBlock());
                if (!stack.isEmpty()) context.graphics.renderItem(stack,
                    Math.round(getPositionX()) + 1, Math.round(getPositionY()) + 1);
            }
        };
        moduleIcon.setId("module-icon");
        moduleIcon.style(style -> style.backgroundTexture(NETextures.ITEM_SLOT));
        root.addChild(place(moduleIcon, 7, 5, 18, 18));
        root.addChild(text("title", tr("title"), 29, 5, 154, 9, 9, TEXT, Horizontal.LEFT));
        root.addChild(text("subtitle", tr("subtitle"), 29, 15, 154, 9, 7, MUTED, Horizontal.LEFT));
        root.addChild(place(new Badge(() -> view.phase), 185, 7, 44, 12));
        root.addChild(place(new Separator(), 7, 26, 222, 2));

        // Target temperature: large value, AE2 step buttons and text field, thermometer bar.
        RPCEmitter adjust = root.addRPCEvent(RPCEventBuilder.simple(Integer.class, delta -> {
            if (delta != null && !host.isRemoved()) {
                host.setTargetTemperature(host.getTargetTemperature() + cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(delta, -10, 10));
            }
        }));
        UIElement settings = card("settings", 7, 30, 222, 54);
        settings.addChild(text("target-label", tr("target_label"), 6, 5, 100, 9, 8, TEXT, Horizontal.LEFT));
        settings.addChild(text("target-range", tr("target_range"), 110, 6, 106, 8, 7, MUTED, Horizontal.RIGHT));
        TextElement targetValue = text("target-value", percent(view.target), 6, 16, 64, 16, 16, ACCENT, Horizontal.LEFT);
        settings.addChild(targetValue);
        settings.addChild(stepButton("target-decrease", "-", -1, adjust, 138, 17));
        TextField target = new AE2TextField();
        target.setId("target");
        target.setNumbersOnlyInt(0, 99);
        target.setTextResponder(value -> setTarget(host, value));
        target.bind(DataBindingBuilder.string(() -> String.valueOf(host.getTargetTemperature()),
            value -> setTarget(host, value)).build());
        target.textFieldStyle(style -> style.textColor(0xF2F2F2).textShadow(false).focusOverlay(IGuiTexture.EMPTY));
        settings.addChild(place(target, 159, 19, 32, 12));
        settings.addChild(stepButton("target-increase", "+", 1, adjust, 194, 17));
        Bar temperatureBar = new Bar(() -> view.temperature / 100,
            () -> temperatureColor(view.temperature), () -> view.target / 100.0);
        temperatureBar.setId("temperature-bar");
        tooltip(temperatureBar, () -> List.of(
            tr("current", decimal(view.temperature)).withStyle(ChatFormatting.YELLOW),
            tr("target_value", view.target).withStyle(ChatFormatting.GRAY)));
        settings.addChild(place(temperatureBar, 6, 36, 210, 6));
        settings.addChild(text("target-min", tr("range_min"), 6, 45, 100, 8, 7, MUTED, Horizontal.LEFT));
        settings.addChild(text("target-max", tr("range_max"), 110, 45, 106, 8, 7, MUTED, Horizontal.RIGHT));
        root.addChild(settings);

        // Operating status: headline plus a short detail line.
        UIElement statusCard = card("status-card", 7, 88, 109, 58);
        statusCard.addChild(icon("status-icon", ECOIcon.COG, 4, 3, 16));
        statusCard.addChild(text("status-label", tr("status_title"), 22, 7, 82, 9, 8, TEXT, Horizontal.LEFT));
        TextElement status = text("status", status(host), 5, 21, 99, 19, 8, view.phase.color, Horizontal.LEFT);
        TextElement statusHint = text("status-hint", statusHint(host), 5, 41, 99, 15, 7, MUTED, Horizontal.LEFT);
        statusCard.addChild(status);
        statusCard.addChild(statusHint);
        root.addChild(syncedComponent("status-sync", () -> status(host), status::setText));
        root.addChild(syncedComponent("status-hint-sync", () -> statusHint(host), statusHint::setText));
        root.addChild(statusCard);

        // Coolant buffer and a separate ghost slot for the player's fluid selection.
        UIElement buffer = card("buffer", 120, 88, 109, 58);
        buffer.addChild(icon("buffer-icon", ECOIcon.LEVEL_GAUGE, 4, 3, 16));
        buffer.addChild(text("buffer-label", tr("buffer_title"), 22, 7, 62, 9, 8, TEXT, Horizontal.LEFT));
        buffer.addChild(place(new MarkedCoolantSlot(host, player), 86, 3, 18, 18));
        TextElement amount = text("buffer-amount", Component.empty(), 27, 22, 77, 9, 7, TEXT, Horizontal.LEFT);
        TextElement bufferTarget = text("buffer-target", tr("buffer_target", number(host.tank.getCapacity())),
            27, 32, 77, 8, 7, MUTED, Horizontal.LEFT);
        FluidSlot coolant = new FluidSlot() {
            private int shownAmount = -1;

            @Override
            public void drawBackgroundAdditional(GUIContext context) {
                super.drawBackgroundAdditional(context);
                // The tank widget synchronizes silently; refresh the label only when the amount changes.
                int stored = getValue().getAmount();
                if (stored != shownAmount) {
                    shownAmount = stored;
                    amount.setText(tr("buffer_amount", number(stored)));
                }
            }
        }.bind(host.tank, 0)
            .setAllowClickFilled(false).setAllowClickDrained(true);
        coolant.setId("coolant");
        coolant.amountLabel.setDisplay(false);
        // The card already uses the inset sprite; AE2's framed slot keeps the tank distinguishable.
        coolant.style(style -> style.backgroundTexture(AETextures.slotWithFrame()));
        amount.setText(tr("buffer_amount", number(coolant.getValue().getAmount())));
        buffer.addChild(place(coolant, 5, 21, 18, 18));
        buffer.addChild(amount);
        buffer.addChild(bufferTarget);
        Bar bufferBar = new Bar(() -> coolant.getValue().getAmount() / (double) host.tank.getCapacity(),
            () -> 0xFF4F8CC9, () -> Double.NaN);
        bufferBar.setId("buffer-bar");
        tooltip(bufferBar, () -> List.of(
            tr("buffer_amount", number(coolant.getValue().getAmount())).withStyle(ChatFormatting.YELLOW),
            tr("buffer_hint", number(host.tank.getCapacity())).withStyle(ChatFormatting.GRAY)));
        buffer.addChild(place(bufferBar, 5, 46, 99, 6));
        root.addChild(buffer);

        // Operating parameters.
        UIElement energy = card("energy", 7, 150, 109, 26);
        energy.addChild(icon("energy-icon", ECOIcon.POWER_UNIT_AE, 4, 5, 16));
        energy.addChild(text("energy-label", tr("energy"), 23, 4, 82, 8, 7, MUTED, Horizontal.LEFT));
        energy.addChild(text("energy-value", tr("energy_value",
            number(ECOLargeIntegratedWorkingStationBlockEntity.SMART_COOLING_AE_PER_TICK)), 23, 13, 82, 9, 9, TEXT, Horizontal.LEFT));
        root.addChild(energy);
        UIElement coolantUse = card("coolant-use", 120, 150, 109, 26);
        coolantUse.addChild(icon("coolant-use-icon", ECOIcon.FLUID_CELL, 4, 5, 16));
        coolantUse.addChild(text("coolant-use-label", tr("coolant_use"), 23, 4, 82, 8, 7, MUTED, Horizontal.LEFT));
        TextElement coolantValue = text("coolant-use-value", tr("coolant_value", number(view.coolantPerTick)),
            23, 13, 82, 9, 9, TEXT, Horizontal.LEFT);
        coolantUse.addChild(coolantValue);
        root.addChild(coolantUse);

        // Notes: what the module does and what it needs.
        UIElement notes = card("notes", 7, 180, 222, 50);
        notes.addChild(icon("help-icon", ECOIcon.HELP, 4, 3, 16));
        notes.addChild(text("help", tr("notes_desc"), 22, 4, 195, 23, 7, TEXT, Horizontal.LEFT));
        notes.addChild(icon("warning-icon", ECOIcon.BLOCKED, 6, 31, 12));
        notes.addChild(text("help-warning", tr("notes_warning"), 22, 32, 195, 15, 7, WARNING, Horizontal.LEFT));
        root.addChild(notes);

        // Numeric state shared by the badge, bars and value labels.
        BindableValue<Tag> state = new BindableValue<>(snapshot(host));
        state.setId("state-sync");
        state.setDisplay(false);
        state.registerValueListener(value -> {
            view.read(value);
            targetValue.setText(percent(view.target));
            status.textStyle(style -> style.textColor(view.phase.color));
            coolantValue.setText(tr("coolant_value", number(view.coolantPerTick)));
        });
        state.bind(DataBindingBuilder.tagS2C(() -> snapshot(host)).build());
        root.addChild(state);
        return root;
    }

    /** Marked coolant filter: a phantom slot synchronized as a tag, edited only through the server RPC. */
    private static final class MarkedCoolantSlot extends UIElement {
        private final RPCEmitter click;
        private final BindableValue<CompoundTag> marked = new BindableValue<>(new CompoundTag());

        private MarkedCoolantSlot(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host, Player player) {
            setId("coolant-filter");
            style(style -> style.backgroundTexture(AETextures.slotWithFrame()));
            marked.bind(DataBindingBuilder.tagS2C(() -> host.getMarkedCoolant().writeToNBT(new CompoundTag())).build());
            marked.setDisplay(false);
            addChild(marked);
            click = addRPCEvent(RPCEventBuilder.simple(Integer.class, button -> {
                if (button == null || button < 0 || button > 1 || !canEdit(host, player, this)) return;
                markCoolant(host, player, button);
            }));
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 0 && event.button != 1) return;
                click.send(event.button);
                event.stopImmediatePropagation();
            });
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                var lines = new java.util.ArrayList<Component>();
                lines.add(tr("marked_coolant").withStyle(ChatFormatting.YELLOW));
                FluidStack value = value();
                if (!value.isEmpty()) lines.add(ECOKeyFactory.fluid(value).getDisplayName());
                lines.add(tr("mark_hint").withStyle(ChatFormatting.GRAY));
                event.hoverTooltips = new HoverTooltips(lines, null, null, null);
            });
        }

        private FluidStack value() {
            CompoundTag tag = marked.getValue();
            return tag == null || tag.isEmpty() ? FluidStack.EMPTY : FluidStack.loadFluidStackFromNBT(tag);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            int x = Math.round(getPositionX()) + 1, y = Math.round(getPositionY()) + 1;
            FluidStack value = value();
            if (value.isEmpty()) {
                context.drawTexture(AETextures.icon(ECOIcon.FLUID_CELL), x, y, 16, 16);
            } else {
                appeng.api.client.AEKeyRendering.drawInGui(net.minecraft.client.Minecraft.getInstance(),
                    context.graphics, x, y, ECOKeyFactory.fluid(value));
            }
        }
    }

    static void markCoolant(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host, Player player, int button) {
        FluidStack marked = host.getMarkedCoolant();
        GenericStack current = marked.isEmpty() ? null : new GenericStack(ECOKeyFactory.fluid(marked), 1);
        ItemStack carried = player.containerMenu.getCarried();
        // This compact panel has no player inventory: a held container must also be usable.
        if (carried.isEmpty() && button == 1) carried = player.getMainHandItem();
        var updated = FluidHatchInterfaceConfig.click(current, carried, button);
        host.setMarkedCoolant(updated == null ? FluidStack.EMPTY : ((AEFluidKey) updated.what()).toStack(1));
    }

    private static boolean canEdit(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host, Player player,
        UIElement element) {
        var ui = element.getModularUI();
        return player != null && host.getLevel() != null && !host.getLevel().isClientSide && !host.isRemoved()
            && player.level() == host.getLevel() && host.getLevel().getBlockEntity(host.getBlockPos()) == host
            && player.blockPosition().distSqr(host.getBlockPos()) <= 64 && ui != null
            && ui.getMenu() == player.containerMenu && player.containerMenu.stillValid(player);
    }

    private static UIElement stepButton(String id, String label, int sign, RPCEmitter adjust, int x, int y) {
        AE2TextButton button = new AE2TextButton(label);
        button.setId(id);
        button.setOnClick(event -> adjust.send(sign * (event.isShiftDown() ? 10 : 1)));
        tooltip(button, () -> List.of(
            tr(sign < 0 ? "decrease" : "increase").withStyle(ChatFormatting.YELLOW),
            tr("step_hint").withStyle(ChatFormatting.GRAY)));
        return place(button, x, y, 18, 16);
    }

    private static BindableValue<Component> syncedComponent(String id, Supplier<Component> source,
        java.util.function.Consumer<Component> target) {
        BindableValue<Component> value = new BindableValue<>(source.get());
        value.setId(id);
        value.setDisplay(false);
        value.registerValueListener(target);
        value.bind(DataBindingBuilder.componentS2C(source).build());
        return value;
    }

    private static UIElement card(String id, int x, int y, int width, int height) {
        UIElement card = new UIElement();
        card.setId(id);
        card.style(style -> style.backgroundTexture(NETextures.ITEM_SLOT));
        return place(card, x, y, width, height);
    }

    private static UIElement icon(String id, ECOIcon icon, int x, int y, int size) {
        UIElement element = new UIElement();
        element.setId(id);
        element.setAllowHitTest(false);
        element.style(style -> style.backgroundTexture(AETextures.icon(icon)));
        return place(element, x, y, size, size);
    }

    private static TextElement text(String id, Component value, int x, int y, int width, int height,
        float fontSize, int color, Horizontal align) {
        TextElement text = new TextElement();
        text.setId(id);
        // TextElement does not reserve its rendered height by default. Bound both axes and wrap
        // translations inside their own region so a status update cannot overlap another control.
        text.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false)
            .textWrap(TextWrap.WRAP).fontSize(fontSize).lineSpacing(1)
            .textAlignHorizontal(align).textColor(color).textShadow(false));
        place(text, x, y, width, height);
        text.setText(value);
        return text;
    }

    private static void tooltip(UIElement element, Supplier<List<Component>> lines) {
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(lines.get(), null, null, null));
    }

    private static <T extends UIElement> T place(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(x).top(y).width(width).height(height).paddingAll(0));
        return element;
    }

    private static ECOLargeIntegratedWorkingStationBlockEntity controller(
        ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        var cluster = host.getCluster();
        return cluster == null ? null : cluster.getController();
    }

    static Phase phase(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        var controller = controller(host);
        if (controller == null) return Phase.OFFLINE;
        if (host.getMarkedCoolant().isEmpty() && host.tank.isEmpty()) return Phase.ALERT;
        if (!controller.isTemperatureEnabled()) return Phase.DISABLED;
        return controller.hasSmartCoolingProblem() ? Phase.ALERT : Phase.ACTIVE;
    }

    private static Tag snapshot(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        var controller = controller(host);
        CompoundTag tag = new CompoundTag();
        tag.putByte("phase", (byte) phase(host).ordinal());
        tag.putInt("target", host.getTargetTemperature());
        tag.putDouble("temperature", controller == null ? 0 : controller.getTemperature());
        tag.putInt("coolant", controller == null
            ? LargeWorkstationThermal.COOLANT_PER_TICK * ECOLargeIntegratedWorkingStationBlockEntity.SMART_COOLING_COOLANT_MULTIPLIER
            : controller.getCoolantPerTick());
        return tag;
    }

    static Component status(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        var controller = controller(host);
        if (controller == null) return tr("unformed");
        if (host.getMarkedCoolant().isEmpty() && host.tank.isEmpty()) return tr("unmarked");
        if (!controller.isTemperatureEnabled()) return tr("disabled");
        return controller.getSmartCoolingStatusText();
    }

    static Component statusHint(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        var controller = controller(host);
        if (controller == null) return tr("unformed_hint");
        if (!controller.isTemperatureEnabled()) return tr("coolant_mode");
        return tr("current", decimal(controller.getTemperature()));
    }

    private static void setTarget(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host, String text) {
        if (host.getLevel() == null || host.getLevel().isClientSide) return;
        try {
            host.setTargetTemperature(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            // Partial edits keep the last valid server-side setting.
        }
    }

    /** Same bands as the workstation thermometer: cold, 20%, 50%, 80% and overheated. */
    private static int temperatureColor(double value) {
        return value >= 100 ? 0xFFE34D4D : value >= 80 ? 0xFFE79843
            : value >= 50 ? 0xFFE4C85A : value >= 20 ? 0xFF79B369 : 0xFF7E9FC5;
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    private static Component percent(int value) {
        return Component.literal(value + "%");
    }

    private static String decimal(double value) {
        return DisplayNumbers.decimal(value, 2);
    }

    private static String number(long value) {
        return DisplayNumbers.compact(value);
    }

    /** Client-side copy of the synchronized numeric state. */
    private static final class View {
        Phase phase = Phase.OFFLINE;
        int target = 75;
        double temperature;
        int coolantPerTick;

        void read(Tag value) {
            if (!(value instanceof CompoundTag tag)) return;
            Phase[] phases = Phase.values();
            phase = phases[cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(tag.getByte("phase"), 0, phases.length - 1)];
            target = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(tag.getInt("target"), 0, 99);
            temperature = tag.getDouble("temperature");
            coolantPerTick = Math.max(0, tag.getInt("coolant"));
        }
    }

    /** AE2-style inset bar with an optional target marker. */
    private static final class Bar extends UIElement {
        private final DoubleSupplier ratio;
        private final IntSupplier color;
        private final DoubleSupplier marker;

        private Bar(DoubleSupplier ratio, IntSupplier color, DoubleSupplier marker) {
            this.ratio = ratio;
            this.color = color;
            this.marker = marker;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            context.graphics.fill(x, y, x + width, y + height, 0xFF413F54);
            context.graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF8B8FA6);
            int inner = width - 2;
            int filled = (int) Math.round(inner * clamp01(ratio.getAsDouble()));
            if (filled > 0) {
                int fill = color.getAsInt();
                context.graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fill);
                context.graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0x55FFFFFF);
            }
            double mark = marker.getAsDouble();
            if (Double.isFinite(mark)) {
                int markerX = x + 1 + (int) Math.round(inner * clamp01(mark));
                context.graphics.fill(markerX - 1, y - 2, markerX + 1, y + height + 2, 0xFF413F54);
                context.graphics.fill(markerX, y - 1, markerX + 1, y + height + 1, 0xFFF2F2F2);
            }
        }

        private static double clamp01(double value) {
            return Double.isFinite(value) ? cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(value, 0, 1) : 0;
        }
    }

    /** Phase pill in the header, colored like the status headline. */
    private static final class Badge extends UIElement {
        private final Supplier<Phase> phase;

        private Badge(Supplier<Phase> phase) {
            this.phase = phase;
            setId("badge");
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            Phase current = phase.get();
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            context.graphics.fill(x + 1, y, x + width - 1, y + height, current.color);
            context.graphics.fill(x, y + 1, x + width, y + height - 1, current.color);
            context.graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, current.fill);
            var font = Minecraft.getInstance().font;
            String label = current.badge().getString();
            if (font.width(label) > width - 4) label = font.plainSubstrByWidth(label, width - 4);
            context.graphics.drawString(font, label, x + (width - font.width(label)) / 2, y + 2, current.color, false);
        }
    }

    /** AE2 panels separate header areas with an engraved line. */
    private static final class Separator extends UIElement {
        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            context.graphics.fill(x, y, x + width, y + 1, 0xFF9A9FB4);
            context.graphics.fill(x, y + 1, x + width, y + 2, 0xFFF2F2F2);
        }
    }
}
