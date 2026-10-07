package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.core.definitions.AEParts;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.CraftingPlanningModeButton;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Texture-backed controller; LDLib2 retains inventory, toolbar actions and menu synchronization. */
public final class ComputationHostPanelUI {
    private static final int CPU_MODE_BUTTON_SIZE = 18;

    private ComputationHostPanelUI() {}

    public record Config(
        Supplier<Component> title, BooleanSupplier connected, IntSupplier networkMultiplier,
        LongSupplier gameTime, BooleanSupplier canInteract,
        LongSupplier usedBytes, LongSupplier totalBytes, LongSupplier availableBytes,
        IntSupplier usedThreads, IntSupplier totalThreads, IntSupplier parallelCount,
        Supplier<CpuSelectionMode> cpuSelectionMode, IntConsumer adjustCpuSelectionMode,
        Supplier<HolderLookup.Provider> registries, Supplier<List<ECOCraftingCPU>> cpus,
        BooleanSupplier ignoringPatternSubstitutions, IntSupplier substitutionPatternCount,
        Runnable toggleIgnoringPatternSubstitutions, BooleanSupplier cyclePlanningEnabled,
        Runnable toggleCyclePlanning, BooleanSupplier fastPlannerEnabled, Runnable toggleFastPlanner,
        IntSupplier networkFrequency, IntConsumer adjustNetworkFrequency
    ) {}

    public static UIElement create(Config config) {
        UIElement root = new ComputationCpuPanel(config);
        InventorySlots inventory = new InventorySlots();
        inventory.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(7).top(129).width(162).height(77));
        inventory.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        inventory.getChildren().forEach(child -> child.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        root.addChild(inventory);
        return root;
    }

    public static Button createCpuSelectionButton(Config config) {
        Button button = HostSideButtonBar.createButton().noText();
        button.addClass("eco-host-cpu-mode-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));

        CpuSelectionIcon icon = new CpuSelectionIcon(config.cpuSelectionMode.get());
        button.addChild(icon);
        button.setOnServerClick(event -> {
            if (event.button == 0) config.adjustCpuSelectionMode.accept(1);
            else if (event.button == 1) config.adjustCpuSelectionMode.accept(-1);
        });

        BindableValue<Integer> syncedMode = new BindableValue<>(config.cpuSelectionMode.get().ordinal());
        syncedMode.bind(DataBindingBuilder.intValS2C(() -> config.cpuSelectionMode.get().ordinal()).build());
        syncedMode.registerValueListener(value -> icon.setMode(cpuSelectionModeFromOrdinal(value)));
        syncedMode.setDisplay(false);
        button.addChild(syncedMode);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            CpuSelectionMode mode = cpuSelectionModeFromOrdinal(syncedMode.getValue());
            event.hoverTooltips = new HoverTooltips(List.of(
                    ButtonToolTips.CpuSelectionMode.text(),
                    cpuSelectionModeTooltip(mode)), null, null, null);
        });
        return button;
    }

    public static Button createNetworkFrequencyButton(Config config) {
        Button button = HostSideButtonBar.createButton()
                .noText()
                .addPreIcon(AETextures.icon(ECOIcon.SCHEDULING_ROUND_ROBIN))
                .setOnServerClick(event -> {
                    if (event.button == 0) config.adjustNetworkFrequency.accept(1);
                    else if (event.button == 1) config.adjustNetworkFrequency.accept(-1);
                });
        button.buttonStyle(style -> style
                .baseTexture(NETextures.RECT_RD)
                .hoverTexture(NETextures.RECT_RD_LIGHT)
                .pressedTexture(NETextures.RECT_RD_DARK));
        button.addClass("eco-host-network-frequency-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));

        BindableValue<Component> syncedTooltip = new BindableValue<>(HostElements.networkFrequencyTooltip(config.networkFrequency.getAsInt()));
        syncedTooltip.bind(DataBindingBuilder.componentS2C(() -> HostElements.networkFrequencyTooltip(config.networkFrequency.getAsInt())).build());
        syncedTooltip.setDisplay(false);
        button.addChild(syncedTooltip);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(syncedTooltip.getValue()));
        return button;
    }

    public static Button createPlanningModeButton(Config config) {
        return CraftingPlanningModeButton.create(
                config.ignoringPatternSubstitutions,
                config.substitutionPatternCount,
                config.toggleIgnoringPatternSubstitutions,
                CPU_MODE_BUTTON_SIZE);
    }

    public static Button createCyclePlanningButton(Config config) {
        Button button = HostSideButtonBar.createButton()
                .noText()
                .addPreIcon(AETextures.icon(ECOIcon.SCHEDULING_ROUND_ROBIN));
        button.buttonStyle(style -> style
                .baseTexture(NETextures.RECT_RD)
                .hoverTexture(NETextures.RECT_RD_LIGHT)
                .pressedTexture(NETextures.RECT_RD_DARK));
        button.addClass("eco-host-cycle-planning-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));
        button.setOnServerClick(event -> config.toggleCyclePlanning.run());

        BindableValue<Boolean> syncedEnabled = new BindableValue<>(config.cyclePlanningEnabled.getAsBoolean());
        syncedEnabled.bind(DataBindingBuilder.boolS2C(config.cyclePlanningEnabled::getAsBoolean).build());
        syncedEnabled.setDisplay(false);
        button.addChild(syncedEnabled);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(Component.translatable(
                        Boolean.TRUE.equals(syncedEnabled.getValue())
                                ? "gui.neoecoae.crafting.cycle_planning.on"
                                : "gui.neoecoae.crafting.cycle_planning.off")));
        return button;
    }

    public static Button createFastPlannerButton(Config config) {
        Button button = HostSideButtonBar.createButton()
                .noText()
                .addPreIcon(AETextures.icon(config.fastPlannerEnabled.getAsBoolean() ? ECOIcon.COG : ECOIcon.COG_DISABLED));
        button.buttonStyle(style -> style
                .baseTexture(NETextures.RECT_RD)
                .hoverTexture(NETextures.RECT_RD_LIGHT)
                .pressedTexture(NETextures.RECT_RD_DARK));
        button.addClass("eco-host-fast-planner-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));
        button.setOnServerClick(event -> config.toggleFastPlanner.run());

        UIElement icon = button.getChildren().getFirst();
        BindableValue<Boolean> syncedEnabled = new BindableValue<>(config.fastPlannerEnabled.getAsBoolean());
        syncedEnabled.bind(DataBindingBuilder.boolS2C(config.fastPlannerEnabled::getAsBoolean).build());
        syncedEnabled.registerValueListener(value -> icon.style(style -> style.backgroundTexture(
                AETextures.icon(Boolean.TRUE.equals(value) ? ECOIcon.COG : ECOIcon.COG_DISABLED))));
        syncedEnabled.setDisplay(false);
        button.addChild(syncedEnabled);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(Component.translatable(
                        Boolean.TRUE.equals(syncedEnabled.getValue())
                                ? "gui.neoecoae.crafting.fast_planner.on"
                                : "gui.neoecoae.crafting.fast_planner.off")));
        return button;
    }

    private static IGuiTexture cpuSelectionModeIcon(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> AETextures.icon(ECOIcon.CRAFT_HAMMER);
            case PLAYER_ONLY -> new ItemStackTexture(new ItemStack(AEParts.TERMINAL));
            case MACHINE_ONLY -> new ItemStackTexture(new ItemStack(AEParts.EXPORT_BUS));
        };
    }

    private static final class CpuSelectionIcon extends UIElement {
        private IGuiTexture texture;

        private CpuSelectionIcon(CpuSelectionMode mode) {
            setMode(mode);
            layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(-3)
                    .top(-2)
                    .width(16)
                    .height(16));
        }

        private void setMode(CpuSelectionMode mode) {
            texture = cpuSelectionModeIcon(mode);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            guiContext.drawTexture(texture, getPositionX(), getPositionY(), 16, 16);
        }
    }

    private static Component cpuSelectionModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> ButtonToolTips.CpuSelectionModeAny.text();
            case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly.text();
            case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly.text();
        };
    }

    private static CpuSelectionMode cpuSelectionModeFromOrdinal(Integer ordinal) {
        CpuSelectionMode[] values = CpuSelectionMode.values();
        int index = ordinal == null ? CpuSelectionMode.ANY.ordinal() : ordinal;
        return index < 0 || index >= values.length ? CpuSelectionMode.ANY : values[index];
    }

}
