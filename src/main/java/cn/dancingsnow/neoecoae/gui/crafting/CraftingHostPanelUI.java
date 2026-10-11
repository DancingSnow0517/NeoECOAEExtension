package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.common.CraftingPlanningModeButton;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostPanelElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** F-series controller artwork and the small amount of text that cannot be encoded in the supplied atlas. */
public final class CraftingHostPanelUI {
    public static final int UI_WIDTH = 253;
    public static final int UI_HEIGHT = 214;
    private CraftingHostPanelUI() {}

    public record Config(
        Supplier<Component> title, Supplier<Component> statusNotice, IntSupplier networkMultiplier,
        BooleanSupplier networkConnected, BooleanSupplier overclocked, Runnable toggleOverclocked,
        BooleanSupplier activeCooling, Runnable toggleActiveCooling, IntSupplier activeWorkerCores,
        IntSupplier workerCores, IntSupplier singleCoreCapacity, LongSupplier ftParallelCapacity,
        LongSupplier overflowCapacity, IntSupplier theoreticalOverclockTimes, IntSupplier effectiveOverclockTimes,
        IntSupplier executionTicks, LongSupplier performanceAverageNanos, LongSupplier energyUsage,
        IntSupplier coolantAmount, IntSupplier coolantCapacity, IntSupplier coolantMaxOverclock,
        Supplier<FluidStack> coolantFluid, Supplier<FluidStack> outputFluid, IntSupplier outputCapacity,
        Supplier<?> registries, Supplier<?> tasks,
        BooleanSupplier ignoringPatternSubstitutions, IntSupplier substitutionPatternCount,
        Runnable toggleIgnoringPatternSubstitutions, IntSupplier networkFrequency, IntConsumer adjustNetworkFrequency,
        Supplier<CraftingControllerPanelState> panelState, LongSupplier queuedCraftCount,
        LongSupplier batchCapacity, IntSupplier normalSwitchCount, IntSupplier highEnergySwitchCount
    ) {}

    public static UIElement create(Config config) {
        ControllerArtwork artwork = new ControllerArtwork(config);
        UIElement root = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.RELATIVE).width(UI_WIDTH).height(UI_HEIGHT));
        root.setOverflowVisible(true);
        root.addChild(artwork);
        root.addChild(playerInventory());
        return root;
    }

    private static UIElement playerInventory() {
        InventorySlots inventory = new InventorySlots();
        inventory.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(7).top(129).width(162).height(76));
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        return inventory;
    }

    public static List<Button> createToolbarButtons(Config config) {
        Button overclock = toolbarButton(config.toggleOverclocked, ECOIcon.POWER_UNIT_AE,
            () -> Component.translatable(config.overclocked.getAsBoolean()
                ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off"));
        Button cooling = toolbarButton(config.toggleActiveCooling, ECOIcon.TYPE_FILTER_ALL,
            () -> Component.translatable(config.activeCooling.getAsBoolean()
                ? "gui.neoecoae.crafting.active_cooling.on" : "gui.neoecoae.crafting.active_cooling.off"));
        Button frequency = HostSideButtonBar.createButton().noText().addPreIcon(AETextures.icon(ECOIcon.SCHEDULING_ROUND_ROBIN))
            .setOnServerClick(event -> {
                if (event.button == 0) config.adjustNetworkFrequency.accept(1);
                else if (event.button == 1) config.adjustNetworkFrequency.accept(-1);
            });
        frequency.buttonStyle(style -> style.baseTexture(NETextures.RECT_RD)
            .hoverTexture(NETextures.RECT_RD_LIGHT).pressedTexture(NETextures.RECT_RD_DARK));
        frequency.layout(layout -> layout.width(16).height(16));
        BindableValue<Component> frequencyTip = new BindableValue<>(HostElements.networkFrequencyTooltip(config.networkFrequency.getAsInt()));
        frequencyTip.bind(DataBindingBuilder.componentS2C(() -> HostElements.networkFrequencyTooltip(config.networkFrequency.getAsInt())).build());
        frequencyTip.setDisplay(false);
        frequency.addChild(frequencyTip);
        frequency.addEventListener(UIEvents.HOVER_TOOLTIPS,
            event -> event.hoverTooltips = HoverTooltips.empty().append(frequencyTip.getValue()));
        return List.of(overclock, cooling, CraftingPlanningModeButton.create(
            config.ignoringPatternSubstitutions, config.substitutionPatternCount,
            config.toggleIgnoringPatternSubstitutions, 16), frequency);
    }

    private static Button toolbarButton(Runnable action, ECOIcon icon, Supplier<Component> tooltip) {
        Button button = HostSideButtonBar.createButton().noText().addPreIcon(AETextures.icon(icon))
            .setOnServerClick(event -> action.run());
        button.buttonStyle(style -> style.baseTexture(NETextures.RECT_RD)
            .hoverTexture(NETextures.RECT_RD_LIGHT).pressedTexture(NETextures.RECT_RD_DARK));
        button.layout(layout -> layout.width(16).height(16));
        BindableValue<Component> tip = new BindableValue<>(tooltip.get());
        tip.bind(DataBindingBuilder.componentS2C(tooltip).build());
        tip.setDisplay(false);
        button.addChild(tip);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty().append(tip.getValue()));
        return button;
    }

    public static UIElement createMaintenanceStatusIcon(Supplier<Component> status) {
        BindableValue<Component> syncedStatus = new BindableValue<>(status.get());
        syncedStatus.bind(DataBindingBuilder.componentS2C(status).build());
        syncedStatus.setDisplay(false);
        UIElement icon = new UIElement().addChild(syncedStatus);
        SpriteTexture normal = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information.png"));
        SpriteTexture highlighted = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information_highlight.png"));
        icon.style(style -> style.backgroundTexture(normal));
        icon.addEventListener(UIEvents.MOUSE_ENTER, event -> icon.style(style -> style.backgroundTexture(highlighted)));
        icon.addEventListener(UIEvents.MOUSE_LEAVE, event -> icon.style(style -> style.backgroundTexture(normal)));
        icon.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).right(-25).top(4).width(21).height(21));
        icon.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            maintenanceTooltipLines(syncedStatus.getValue()), null, null, null));
        return icon;
    }

    static List<Component> maintenanceTooltipLines(Component status) {
        List<Component> lines = new ArrayList<>();
        String[] textLines = status.getString().split("\\R", -1);
        var headingColor = status.getStyle().getColor();
        int firstLineColor = headingColor == null ? 0xFFD8B4FE : headingColor.getValue();
        for (int i = 0; i < textLines.length; i++) {
            if (!textLines[i].isEmpty()) {
                lines.add(Component.literal(textLines[i]).withColor(i == 0 ? firstLineColor : 0xFFFF5555));
            }
        }
        return lines;
    }

    public static int formatRecipeTimeTicks(int effectiveOverclockTimes) {
        return (int) Math.ceil(10.0D / (Math.clamp(effectiveOverclockTimes, 0, 9) + 1));
    }

    private static final class ControllerArtwork extends UIElement {
        private static final int[] PLUS_X = {104, 116, 128};
        private static final int PRIMARY = 0xFFE4E4EE;
        private static final int SECONDARY = 0xFFB4B6CC;
        private static final int GAUGE_INTERIOR_HEIGHT = 45;
        private final Config config;
        private CraftingControllerPanelState state;
        private final BindableValue<Tag> syncedState;
        private long energyUsage;
        private long performanceAverageNanos;
        private long queuedCraftCount;
        private long batchCapacity;
        private long ftParallelCapacity;
        private int coolantAmount;
        private int coolantCapacity;
        private int coolantMaxOverclock;
        private int outputCapacity;
        private int activeWorkerCores;
        private int workerCores;
        private int singleCoreCapacity;
        private int theoreticalOverclockTimes;
        private int effectiveOverclockTimes;
        private int executionTicks;
        private int networkMultiplier;
        private int taskCount;
        private int normalSwitchCount;
        private int highEnergySwitchCount;
        private boolean networkConnected;
        private boolean overclocked;
        private boolean activeCooling;
        private FluidStack coolantFluid = FluidStack.EMPTY;
        private FluidStack outputFluid = FluidStack.EMPTY;

        private ControllerArtwork(Config config) {
            this.config = config;
            CompoundTag initial = snapshot();
            updateState(initial);
            syncedState = new BindableValue<>(initial);
            syncedState.bind(DataBindingBuilder.tagS2C(this::snapshot).build());
            syncedState.registerValueListener(this::updateState);
            syncedState.setDisplay(false);
            addChild(syncedState);
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(UI_WIDTH).height(UI_HEIGHT));

            addChild(tooltipRegion(98, 78, 68, 46, this::queueTooltip));
            addChild(tooltipRegion(176, 78, 8, 46, this::energyTooltip));
            addChild(tooltipRegion(195, 78, 20, 46, this::coolantTooltip));
            addChild(tooltipRegion(226, 78, 20, 46, this::outputTooltip));
            addChild(tooltipRegion(196, 130, 48, 22, this::fxTooltip));
            addChild(tooltipRegion(196, 155, 48, 23, this::coolantRowTooltip));
            addChild(tooltipRegion(196, 180, 48, 24, this::switchTooltip));
        }

        private CompoundTag snapshot() {
            CompoundTag tag = config.panelState.get().toTag();
            tag.putLong("energyUsage", Math.max(0L, config.energyUsage.getAsLong()));
            tag.putLong("performanceNanos", Math.max(0L, config.performanceAverageNanos.getAsLong()));
            tag.putLong("queuedCraftCount", Math.max(0L, config.queuedCraftCount.getAsLong()));
            tag.putLong("batchCapacity", Math.max(0L, config.batchCapacity.getAsLong()));
            tag.putLong("ftParallelCapacity", Math.max(0L, config.ftParallelCapacity.getAsLong()));
            tag.putInt("coolantAmount", Math.max(0, config.coolantAmount.getAsInt()));
            tag.putInt("coolantCapacity", Math.max(0, config.coolantCapacity.getAsInt()));
            tag.putInt("coolantMaxOverclock", config.coolantMaxOverclock.getAsInt());
            tag.putInt("outputCapacity", Math.max(0, config.outputCapacity.getAsInt()));
            tag.putInt("activeWorkerCores", Math.max(0, config.activeWorkerCores.getAsInt()));
            tag.putInt("workerCores", Math.max(0, config.workerCores.getAsInt()));
            tag.putInt("singleCoreCapacity", Math.max(0, config.singleCoreCapacity.getAsInt()));
            tag.putInt("theoreticalOverclockTimes", Math.max(0, config.theoreticalOverclockTimes.getAsInt()));
            tag.putInt("effectiveOverclockTimes", Math.max(0, config.effectiveOverclockTimes.getAsInt()));
            tag.putInt("executionTicks", Math.max(1, config.executionTicks.getAsInt()));
            tag.putInt("networkMultiplier", Math.max(1, config.networkMultiplier.getAsInt()));
            tag.putInt("taskCount", taskCount());
            tag.putInt("normalSwitchCount", Math.max(0, config.normalSwitchCount.getAsInt()));
            tag.putInt("highEnergySwitchCount", Math.max(0, config.highEnergySwitchCount.getAsInt()));
            tag.putBoolean("networkConnected", config.networkConnected.getAsBoolean());
            tag.putBoolean("overclocked", config.overclocked.getAsBoolean());
            tag.putBoolean("activeCooling", config.activeCooling.getAsBoolean());
            tag.put("coolantFluid", saveFluid(config.coolantFluid.get()));
            tag.put("outputFluid", saveFluid(config.outputFluid.get()));
            return tag;
        }

        private int taskCount() {
            Object value = config.tasks.get();
            return value instanceof java.util.Collection<?> collection ? collection.size() : 0;
        }

        private CompoundTag saveFluid(FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return new CompoundTag();
            Object registries = config.registries.get();
            if (!(registries instanceof HolderLookup.Provider provider)) return new CompoundTag();
            Tag saved = fluid.saveOptional(provider);
            return saved instanceof CompoundTag compound ? compound : new CompoundTag();
        }

        private FluidStack loadFluid(CompoundTag tag, Supplier<FluidStack> fallbackSupplier) {
            Object registries = config.registries.get();
            FluidStack parsed = registries instanceof HolderLookup.Provider provider
                ? FluidStack.parseOptional(provider, tag) : FluidStack.EMPTY;
            if (!parsed.isEmpty()) return parsed;
            FluidStack fallback = fallbackSupplier.get();
            return fallback == null || fallback.isEmpty() ? FluidStack.EMPTY : fallback.copy();
        }

        private void updateState(Tag value) {
            CompoundTag tag = value instanceof CompoundTag compound ? compound : new CompoundTag();
            state = CraftingControllerPanelState.fromTag(tag);
            energyUsage = Math.max(0L, tag.getLong("energyUsage"));
            performanceAverageNanos = Math.max(0L, tag.getLong("performanceNanos"));
            queuedCraftCount = Math.max(0L, tag.getLong("queuedCraftCount"));
            batchCapacity = Math.max(0L, tag.getLong("batchCapacity"));
            ftParallelCapacity = Math.max(0L, tag.getLong("ftParallelCapacity"));
            coolantAmount = Math.max(0, tag.getInt("coolantAmount"));
            coolantCapacity = Math.max(0, tag.getInt("coolantCapacity"));
            coolantMaxOverclock = tag.getInt("coolantMaxOverclock");
            outputCapacity = Math.max(0, tag.getInt("outputCapacity"));
            activeWorkerCores = Math.max(0, tag.getInt("activeWorkerCores"));
            workerCores = Math.max(0, tag.getInt("workerCores"));
            singleCoreCapacity = Math.max(0, tag.getInt("singleCoreCapacity"));
            theoreticalOverclockTimes = Math.max(0, tag.getInt("theoreticalOverclockTimes"));
            effectiveOverclockTimes = Math.max(0, tag.getInt("effectiveOverclockTimes"));
            executionTicks = Math.max(1, tag.getInt("executionTicks"));
            networkMultiplier = Math.max(1, tag.getInt("networkMultiplier"));
            taskCount = Math.max(0, tag.getInt("taskCount"));
            normalSwitchCount = Math.max(0, tag.getInt("normalSwitchCount"));
            highEnergySwitchCount = Math.max(0, tag.getInt("highEnergySwitchCount"));
            networkConnected = tag.getBoolean("networkConnected");
            overclocked = tag.getBoolean("overclocked");
            activeCooling = tag.getBoolean("activeCooling");
            coolantFluid = loadFluid(tag.getCompound("coolantFluid"), config.coolantFluid);
            outputFluid = loadFluid(tag.getCompound("outputFluid"), config.outputFluid);
        }

        private UIElement tooltipRegion(int x, int y, int width, int height, Supplier<List<Component>> lines) {
            return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(y).width(width).height(height))
                .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                    lines.get(), null, null, null));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            context.drawTexture(NETextures.Crafting.CONTROLLER, getPositionX(), getPositionY(), UI_WIDTH, UI_HEIGHT);
            drawHeader(context);
            if (!state.formed()) return;
            drawTier(context, state.hostTier());
            drawStatusIcons(context);
            drawCoreCells(context);
            drawBars(context);
            drawInfoRows(context);
        }

        private void drawHeader(GUIContext context) {
            drawText(context, config.title.get(), 40, 10, 105, PRIMARY);
            drawCenteredText(context, Component.literal(HostPanelElements.formatPerformanceCornerValue(performanceAverageNanos)),
                210, 10, 39, PRIMARY);
        }

        private void drawInfoRows(GUIContext context) {
            drawInfoRow(context,
                Component.translatable("gui.neoecoae.crafting.ui.fx_active", activeWorkerCores, workerCores),
                Component.translatable("gui.neoecoae.crafting.ui.ft_parallel", ftParallelCapacity), 134);
            Component coolantName = coolantVisible()
                ? coolantFluid.getHoverName() : Component.translatable("ldlib.fluid.empty");
            drawInfoRow(context, coolantName,
                Component.translatable("gui.neoecoae.crafting.ui.coolant_overclock", Math.max(0, coolantMaxOverclock)), 158);
            if (normalSwitchCount == 0 && highEnergySwitchCount == 0) {
                drawInfoRow(context,
                    Component.translatable("gui.neoecoae.crafting.ui.switch_none"),
                    Component.translatable("gui.neoecoae.crafting.ui.network_multiplier", networkMultiplier), 183);
            } else {
                drawInfoRow(context,
                    Component.translatable("gui.neoecoae.crafting.ui.switch_normal", normalSwitchCount),
                    Component.translatable("gui.neoecoae.crafting.ui.switch_high_energy", highEnergySwitchCount), 183);
            }
        }

        private void drawInfoRow(GUIContext context, Component first, Component second, int y) {
            drawText(context, first, 196, y, 48, PRIMARY);
            drawText(context, second, 196, y + 10, 48, SECONDARY);
        }

        private void drawText(GUIContext context, Component text, int x, int y, int width, int color) {
            String value = text == null ? "" : text.getString();
            if (value.isEmpty()) return;
            Font font = Minecraft.getInstance().font;
            value = font.plainSubstrByWidth(value, width);
            context.graphics.drawString(font, value, Math.round(getPositionX()) + x,
                Math.round(getPositionY()) + y, color, false);
        }

        private void drawCenteredText(GUIContext context, Component text, int x, int y, int width, int color) {
            Font font = Minecraft.getInstance().font;
            String value = font.plainSubstrByWidth(text.getString(), width);
            int left = x + Math.max(0, (width - font.width(value)) / 2);
            context.graphics.drawString(font, value, Math.round(getPositionX()) + left,
                Math.round(getPositionY()) + y, color, false);
        }

        private void drawTier(GUIContext context, int tier) {
            int sourceX = switch (tier) { case 1 -> 0; case 2 -> 13; case 3 -> 26; default -> -1; };
            if (sourceX >= 0) sprite(context, sourceX, 214, 13, 18, 23, 5, 13, 18);
        }

        private void drawStatusIcons(GUIContext context) {
            if (state.fxCells() != 0) {
                sprite(context, 0, overclocked ? 244 : 232, 12, 12, 180, 136, 12, 12);
            }
            sprite(context, 12, state.coolantOverlay() ? 232 : 244, 12, 12, 180, 161, 12, 12);
            if (state.switchOverlay()) {
                sprite(context, 24, highEnergySwitchCount > 0 ? 244 : 232, 12, 12, 180, 186, 12, 12);
            }
        }

        private void drawCoreCells(GUIContext context) {
            for (int cell = 0; cell < CraftingControllerPanelState.SLOT_COUNT; cell++) {
                if (!state.hasFx(cell)) continue;
                int x = 49 + cell * 18;
                sprite(context, 39, 216, 16, 34, x, 29, 16, 34);
                drawPlus(context, state.upperTier(cell), x + 3, 32, true);
                drawPlus(context, state.lowerTier(cell), x + 3, 55, false);
            }
        }

        private void drawPlus(GUIContext context, int tier, int x, int y, boolean upper) {
            if (tier < 1 || tier > 3) return;
            int sourceY = upper ? 237 : 243;
            sprite(context, PLUS_X[tier - 1], sourceY, 10, upper ? 6 : 5, x, y, 10, upper ? 6 : 5);
        }

        private void drawBars(GUIContext context) {
            drawCenteredText(context, Component.translatable("gui.neoecoae.crafting.ui.queue",
                queuedCraftCount, formatCapacity(batchCapacity)), 61, 80, 107, PRIMARY);
            drawText(context, Component.literal(percent(state.queuePermille())), 73, 91, 23, PRIMARY);
            drawHorizontalProgress(context, 98, 91, 68, state.queuePermille(), 104, 215);
            drawCenteredText(context, Component.translatable("gui.neoecoae.crafting.ui.runtime",
                effectiveOverclockTimes, theoreticalOverclockTimes), 61, 102, 107, PRIMARY);
            drawText(context, Component.literal(executionTicks + " t"), 78, 113, 18, PRIMARY);
            drawHorizontalProgress(context, 98, 113, 68,
                CraftingControllerPanelState.permille(1, executionTicks), 104, 226);
            int height = Math.round(46 * state.energyPermille() / (float) CraftingControllerPanelState.FULL);
            if (height > 0) sprite(context, 254, 1 + 46 - height, 8, height, 176, 78 + 46 - height, 8, height);
            drawFluidGauge(context, 195, coolantFluid, coolantAmount, coolantCapacity, 209);
            drawFluidGauge(context, 226, outputFluid, outputFluid.getAmount(), outputCapacity, 240);
        }

        private String percent(int permille) {
            return (Math.round(Math.clamp(permille, 0, CraftingControllerPanelState.FULL) / 10.0f)) + "%";
        }

        private String formatCapacity(long value) {
            return value == Long.MAX_VALUE ? "∞" : Long.toString(Math.max(0L, value));
        }

        private void drawFluidGauge(GUIContext context, int x, FluidStack fluid, int amount, int capacity, int tickX) {
            int safeCapacity = Math.max(0, capacity);
            int safeAmount = Math.clamp(amount, 0, safeCapacity);
            int height = safeCapacity <= 0 ? 0 : Math.round(GAUGE_INTERIOR_HEIGHT * safeAmount / (float) safeCapacity);
            if (height > 0 && fluid != null && !fluid.isEmpty()) {
                FluidStack renderStack = fluid.copyWithAmount(Math.max(1, safeAmount));
                context.graphics.flush();
                DrawerHelper.drawFluidForGui(context.graphics, renderStack,
                    Math.round(getPositionX()) + x + 1, Math.round(getPositionY()) + 124 - height,
                    18, height, -1);
                context.graphics.flush();
            }
            drawGaugeTicks(context, tickX);
        }

        private void drawGaugeTicks(GUIContext context, int tickX) {
            for (int y = 84; y <= 114; y += 10) {
                sprite(context, tickX, y, 5, 1, tickX, y, 5, 1);
            }
            for (int y = 89; y <= 119; y += 10) {
                sprite(context, tickX + 2, y, 3, 1, tickX + 2, y, 3, 1);
            }
        }

        private void drawHorizontalProgress(GUIContext context, int x, int y, int width, int permille,
                                            int sourceX, int sourceY) {
            int fill = Math.round(width * Math.clamp(permille, 0, CraftingControllerPanelState.FULL)
                / (float) CraftingControllerPanelState.FULL);
            if (fill > 0) sprite(context, sourceX, sourceY, fill, 9, x, y, fill, 9);
        }

        private void sprite(GUIContext context, int u, int v, int sourceWidth, int sourceHeight,
                            int x, int y, int width, int height) {
            context.drawTexture(SpriteTexture.of(NETextures.Crafting.CONTROLLER_ATLAS)
                    .setSprite(u, v, sourceWidth, sourceHeight),
                getPositionX() + x, getPositionY() + y, width, height);
        }

        private List<Component> queueTooltip() {
            return List.of(Component.translatable("gui.neoecoae.crafting.ui.queue",
                    queuedCraftCount, formatCapacity(batchCapacity)),
                Component.literal(queuedCraftCount + " / " + formatCapacity(batchCapacity)),
                Component.translatable("gui.neoecoae.crafting.ui.queue_percent", percent(state.queuePermille())));
        }

        private List<Component> energyTooltip() {
            return List.of(Component.translatable("gui.neoecoae.crafting.ui.energy_usage"),
                Tooltips.ofNumber(energyUsage).append(" AE/t"),
                Tooltips.ofPercent(state.energyPermille() / (double) CraftingControllerPanelState.FULL));
        }

        private List<Component> coolantTooltip() {
            return List.of(Component.translatable("gui.neoecoae.host.crafting.coolant"),
                coolantVisible() ? coolantFluid.getHoverName() : Component.translatable("ldlib.fluid.empty"),
                Tooltips.ofNumber(coolantAmount).append(" / ").append(Tooltips.ofNumber(coolantCapacity)).append(" mB"),
                Component.translatable("gui.neoecoae.crafting.ui.coolant_overclock", Math.max(0, coolantMaxOverclock)));
        }

        private List<Component> outputTooltip() {
            return List.of(Component.translatable("gui.neoecoae.crafting.ui.cooling_output"),
                outputFluid.isEmpty() ? Component.translatable("ldlib.fluid.empty") : outputFluid.getHoverName(),
                Tooltips.ofNumber(outputFluid.getAmount()).append(" / ")
                    .append(Tooltips.ofNumber(outputCapacity)).append(" mB"));
        }

        private List<Component> fxTooltip() {
            return List.of(Component.translatable("gui.neoecoae.crafting.ui.fx_active", activeWorkerCores, workerCores),
                Component.translatable("gui.neoecoae.crafting.ui.ft_parallel", ftParallelCapacity),
                Component.translatable("gui.neoecoae.crafting.ui.batch_capacity", formatCapacity(batchCapacity)));
        }

        private List<Component> coolantRowTooltip() {
            return coolantTooltip();
        }

        private List<Component> switchTooltip() {
            return List.of(Component.translatable("gui.neoecoae.crafting.ui.switch_detail",
                    normalSwitchCount, highEnergySwitchCount),
                Component.translatable("gui.neoecoae.crafting.ui.network_multiplier", networkMultiplier));
        }

        private boolean coolantVisible() {
            return CraftingControllerPanelState.coolantVisible(activeCooling, coolantFluid, coolantAmount);
        }
    }
}
