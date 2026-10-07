package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.core.definitions.AEParts;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
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

    public static UIElement create(Config config, UIElement guideButton, UIElement buildButton, UIElement buildWindow) {
        UIElement root = new UIElement().layout(layout -> layout
            .width(ComputationCpuPanel.WIDTH).height(ComputationCpuPanel.HEIGHT).paddingAll(0));
        root.setOverflowVisible(true);
        UIElement main = new ComputationCpuPanel(config);
        HostElements.absolute(main, 0, 0, ComputationCpuPanel.WIDTH, ComputationCpuPanel.HEIGHT);
        InventorySlots inventory = new InventorySlots();
        inventory.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(7).top(129).width(162).height(77));
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        inventory.getChildren().forEach(child -> child.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        main.addChild(inventory);
        ComputationSettingsPanel settings = new ComputationSettingsPanel(config);
        settings.setDisplay(false);
        Button open = HostSideButtonBar.createButton().noText().addPostIcon(AETextures.icon(ECOIcon.COG));
        open.setId("computation-settings-open");
        HostElements.tooltips(open, Component.translatable("gui.neoecoae.computation.settings.title"));
        open.setOnClick(event -> {
            main.setDisplay(false);
            settings.setDisplay(true);
        });
        settings.addBackButton(() -> {
            settings.setDisplay(false);
            main.setDisplay(true);
        });
        main.addChildren(HostSideButtonBar.left(guideButton, buildButton, open), buildWindow);
        root.addChildren(main, settings);
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

    static Component cpuSelectionModeTooltip(CpuSelectionMode mode) {
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
