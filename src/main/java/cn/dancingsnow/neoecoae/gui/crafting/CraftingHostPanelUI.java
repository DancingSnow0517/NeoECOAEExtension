package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.common.CraftingPlanningModeButton;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
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
import dev.vfyjxf.taffy.style.TaffyPosition;
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
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000L;

    private CraftingHostPanelUI() {}

    public record Config(
        Supplier<Component> title, Supplier<Component> statusNotice, IntSupplier networkMultiplier,
        BooleanSupplier networkConnected, BooleanSupplier overclocked, Runnable toggleOverclocked,
        BooleanSupplier activeCooling, Runnable toggleActiveCooling, IntSupplier activeWorkerCores,
        IntSupplier workerCores, IntSupplier singleCoreCapacity, LongSupplier ftParallelCapacity,
        LongSupplier overflowCapacity, IntSupplier theoreticalOverclockTimes, IntSupplier effectiveOverclockTimes,
        IntSupplier executionTicks, LongSupplier performanceAverageNanos, LongSupplier energyUsage,
        IntSupplier coolantAmount, IntSupplier coolantCapacity, IntSupplier coolantMaxOverclock,
        Supplier<FluidStack> coolantFluid, Supplier<?> registries, Supplier<?> tasks,
        BooleanSupplier ignoringPatternSubstitutions, IntSupplier substitutionPatternCount,
        Runnable toggleIgnoringPatternSubstitutions, IntSupplier networkFrequency, IntConsumer adjustNetworkFrequency,
        Supplier<CraftingControllerPanelState> panelState, LongSupplier queuedCraftCount,
        Supplier<Component> switchComposition
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

    private static Component coolantText(Config config) {
        if (!CraftingControllerPanelState.coolantVisible(
            config.activeCooling.getAsBoolean(), config.coolantFluid.get(), config.coolantAmount.getAsInt())) {
            return Component.translatable("ldlib.fluid.empty");
        }
        return config.coolantFluid.get().getHoverName();
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
        private static final int[] ICON_X = {0, 12, 24};
        private static final int[] PLUS_X = {104, 116, 128};
        private final Config config;
        private CraftingControllerPanelState state;
        private final BindableValue<Tag> syncedState;
        private final BindableValue<Component> coolantName;
        private final BindableValue<Component> switchText;
        private long energyUsage;
        private int coolantAmount;
        private int coolantCapacity;
        private int executionTicks;

        private ControllerArtwork(Config config) {
            this.config = config;
            CompoundTag initial = snapshot();
            updateState(initial);
            this.syncedState = new BindableValue<>(initial);
            syncedState.bind(DataBindingBuilder.tagS2C(this::snapshot).build());
            syncedState.registerValueListener(this::updateState);
            syncedState.setDisplay(false);
            addChild(syncedState);
            coolantName = new BindableValue<>(coolantText(config));
            coolantName.bind(DataBindingBuilder.componentS2C(() -> coolantText(config)).build());
            coolantName.setDisplay(false);
            addChild(coolantName);
            switchText = new BindableValue<>(config.switchComposition.get());
            switchText.bind(DataBindingBuilder.componentS2C(config.switchComposition).build());
            switchText.setDisplay(false);
            addChild(switchText);
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(UI_WIDTH).height(UI_HEIGHT));
            addChild(tooltipRegion(98, 113, 68, 9, () -> List.of(
                Component.translatable("gui.neoecoae.crafting.ui.recipe_time_ratio"),
                Component.literal(executionTicks + " tick"))));
            addChild(tooltipRegion(176, 78, 8, 46, () -> List.of(
                Component.translatable("gui.neoecoae.crafting.ui.fx_cores"),
                Tooltips.ofPercent(state.energyPermille() / (double) CraftingControllerPanelState.FULL))));
            addChild(tooltipRegion(195, 78, 21, 46, () -> List.of(
                Component.translatable("gui.neoecoae.crafting.ui.energy_usage"),
                Tooltips.ofNumber(energyUsage).append(" AE/t"))));
            addChild(tooltipRegion(226, 78, 21, 46, () -> List.of(
                Component.translatable("gui.neoecoae.host.crafting.coolant"), coolantName.getValue(),
                Tooltips.ofNumber(coolantAmount).append(" / ").append(Tooltips.ofNumber(coolantCapacity)).append(" mB"))));
        }

        private CompoundTag snapshot() {
            CompoundTag tag = config.panelState.get().toTag();
            tag.putLong("energyUsage", Math.max(0L, config.energyUsage.getAsLong()));
            tag.putInt("coolantAmount", Math.max(0, config.coolantAmount.getAsInt()));
            tag.putInt("coolantCapacity", Math.max(0, config.coolantCapacity.getAsInt()));
            tag.putInt("executionTicks", Math.max(1, config.executionTicks.getAsInt()));
            return tag;
        }

        private void updateState(Tag tag) {
            CompoundTag compound = tag instanceof CompoundTag value ? value : new CompoundTag();
            state = CraftingControllerPanelState.fromTag(compound);
            energyUsage = Math.max(0L, compound.getLong("energyUsage"));
            coolantAmount = Math.max(0, compound.getInt("coolantAmount"));
            coolantCapacity = Math.max(0, compound.getInt("coolantCapacity"));
            executionTicks = Math.max(1, compound.getInt("executionTicks"));
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
            if (!state.formed()) return;
            drawTier(context, state.hostTier());
            drawStatusIcons(context);
            drawCoreCells(context);
            drawBars(context);
            drawText(context, Component.literal("FX: " + state.fxCount()), 135);
            drawText(context, coolantName.getValue(), 160);
            drawText(context, switchText.getValue(), 185);
        }

        private void drawText(GUIContext context, Component text, int y) {
            Font font = Minecraft.getInstance().font;
            String value = font.plainSubstrByWidth(text.getString(), 47);
            context.graphics.drawString(font, value, Math.round(getPositionX()) + 196,
                Math.round(getPositionY()) + y, 0xFF302D43, false);
        }

        private void drawTier(GUIContext context, int tier) {
            int sourceX = switch (tier) { case 1 -> 0; case 2 -> 13; case 3 -> 26; default -> -1; };
            if (sourceX >= 0) sprite(context, sourceX, 214, 13, 18, 23, 5, 13, 18);
        }

        private void drawStatusIcons(GUIContext context) {
            if (state.fxCells() != 0) sprite(context, ICON_X[0], 232, 12, 12, 180, 136, 12, 12);
            if (state.coolantOverlay()) sprite(context, ICON_X[1], 232, 12, 12, 180, 161, 12, 12);
            else sprite(context, 0, 244, 12, 12, 180, 161, 12, 12);
            if (state.switchOverlay()) sprite(context, ICON_X[2], 232, 12, 12, 180, 186, 12, 12);
        }

        private void drawCoreCells(GUIContext context) {
            for (int cell = 0; cell < CraftingControllerPanelState.SLOT_COUNT; cell++) {
                if (!state.hasFx(cell)) continue;
                int x = 49 + cell * 18;
                sprite(context, 39, 216, 16, 34, x, 29, 16, 34);
                drawPlus(context, state.upperTier(cell), x + 3, 31, true);
                drawPlus(context, state.lowerTier(cell), x + 3, 53, false);
            }
        }

        private void drawPlus(GUIContext context, int tier, int x, int y, boolean upper) {
            if (tier < 1 || tier > 3) return;
            int sourceY = upper ? 237 : 243;
            sprite(context, PLUS_X[tier - 1], sourceY, 10, upper ? 6 : 5, x, y, 10, upper ? 6 : 5);
        }

        private void drawBars(GUIContext context) {
            drawHorizontalProgress(context, 98, 91, 68, state.queuePermille(), 104, 215);
            drawHorizontalProgress(context, 98, 113, 68, CraftingControllerPanelState.permille(1, executionTicks), 104, 226);
            int height = Math.round(46 * state.energyPermille() / (float) CraftingControllerPanelState.FULL);
            if (height > 0) sprite(context, 254, 1 + 46 - height, 8, height, 176, 78 + 46 - height, 8, height);
            drawVerticalGauge(context, 195, CraftingControllerPanelState.permille(energyUsage, ENERGY_GAUGE_REFERENCE), 0xFFCF72FF);
            drawVerticalGauge(context, 226, CraftingControllerPanelState.permille(coolantAmount, coolantCapacity), 0xFF66C8E8);
        }

        private void drawVerticalGauge(GUIContext context, int x, int permille, int color) {
            int height = Math.round(44 * permille / (float) CraftingControllerPanelState.FULL);
            if (height == 0) return;
            int left = Math.round(getPositionX()) + x + 1;
            int bottom = Math.round(getPositionY()) + 123;
            context.graphics.fill(left, bottom - height, left + 15, bottom, color);
        }

        private void drawHorizontalProgress(GUIContext context, int x, int y, int width, int permille, int sourceX, int sourceY) {
            int fill = Math.round(width * permille / (float) CraftingControllerPanelState.FULL);
            if (fill > 0) sprite(context, sourceX, sourceY, fill, 9, x, y, fill, 9);
        }

        private void sprite(GUIContext context, int u, int v, int sourceWidth, int sourceHeight,
                            int x, int y, int width, int height) {
            context.drawTexture(SpriteTexture.of(NETextures.Crafting.CONTROLLER_ATLAS).setSprite(u, v, sourceWidth, sourceHeight),
                getPositionX() + x, getPositionY() + y, width, height);
        }
    }
}
