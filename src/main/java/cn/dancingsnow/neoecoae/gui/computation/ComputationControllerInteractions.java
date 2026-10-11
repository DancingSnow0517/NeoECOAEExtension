package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.core.definitions.AEParts;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostPanelUI.Config;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** CPU selection and network-frequency controls for the computation controller. */
final class ComputationControllerInteractions {
    private static final int CPU_MODE_BUTTON_SIZE = 18;
    private ComputationControllerInteractions() {}
    public static Button createCpuSelectionButton(Config config) {
        Button button = HostSideButtonBar.createButton().noText();
        button.addClass("eco-host-cpu-mode-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));

        UIElement icon = new UIElement().style(style ->
            style.backgroundTexture(cpuSelectionModeIcon(config.cpuSelectionMode().get())));
        button.addChild(icon);
        button.setOnServerClick(event -> {
            if (event.button == 0) config.adjustCpuSelectionMode().accept(1);
            else if (event.button == 1) config.adjustCpuSelectionMode().accept(-1);
        });

        BindableValue<Integer> syncedMode = new BindableValue<>(config.cpuSelectionMode().get().ordinal());
        syncedMode.bind(DataBindingBuilder.intValS2C(() -> config.cpuSelectionMode().get().ordinal()).build());
        syncedMode.registerValueListener(value -> icon.style(style ->
            style.backgroundTexture(cpuSelectionModeIcon(cpuSelectionModeFromOrdinal(value)))));
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
                    if (event.button == 0) config.adjustNetworkFrequency().accept(1);
                    else if (event.button == 1) config.adjustNetworkFrequency().accept(-1);
                });
        button.addClass("eco-host-network-frequency-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));

        BindableValue<Component> syncedTooltip = new BindableValue<>(HostElements.networkFrequencyTooltip(config.networkFrequency().getAsInt()));
        syncedTooltip.bind(DataBindingBuilder.componentS2C(() -> HostElements.networkFrequencyTooltip(config.networkFrequency().getAsInt())).build());
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
