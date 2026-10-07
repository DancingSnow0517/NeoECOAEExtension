package cn.dancingsnow.neoecoae.gui.computation;

import appeng.core.AppEng;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Terminal-style settings page inside the same menu, preserving the selected CPU and inventory. */
final class ComputationSettingsPanel extends UIElement {
    private static final IGuiTexture BACKGROUND = SpriteTexture.of(AppEng.makeId("textures/guis/background.png"))
        .setSprite(0, 0, 256, 256).setBorder(4, 4, 4, 4);

    ComputationSettingsPanel(ComputationHostPanelUI.Config config) {
        setId("computation-settings");
        HostElements.absolute(this, 0, 0, ComputationCpuPanel.WIDTH, ComputationCpuPanel.HEIGHT);
        setOverflowVisible(true);
        style(style -> style.backgroundTexture(BACKGROUND));
        label(8, 8, 214, 14, () -> setting("title"));
        Button cpuMode = ComputationHostPanelUI.createCpuSelectionButton(config);
        cpuMode.setId("computation-settings-cpu-mode");
        HostSideButtonBar.placeButton(cpuMode, 17, 35);
        addChild(cpuMode);
        label(44, 34, 204, 11, () -> setting("cpu_mode"));
        label(44, 47, 204, 22, () -> ComputationHostPanelUI.cpuSelectionModeTooltip(config.cpuSelectionMode().get()));
        toggle("substitutions", 77, () -> !config.ignoringPatternSubstitutions().getAsBoolean(), config.toggleIgnoringPatternSubstitutions());
        label(44, 93, 204, 11, () -> Component.translatable("gui.neoecoae.crafting.planning.substitution_pattern_count",
            Math.max(0, config.substitutionPatternCount().getAsInt())));
        toggle("cycle_planning", 113, config.cyclePlanningEnabled(), config.toggleCyclePlanning());
        toggle("fast_planner", 147, config.fastPlannerEnabled(), config.toggleFastPlanner());
        Button frequency = ComputationHostPanelUI.createNetworkFrequencyButton(config);
        frequency.setId("computation-settings-frequency");
        HostSideButtonBar.placeButton(frequency, 17, 180);
        addChild(frequency);
        label(44, 184, 204, 19, () -> Component.translatable("gui.neoecoae.computation.settings.frequency",
            config.networkFrequency().getAsInt(), NEFrequencyAllocator.FREQUENCY_COUNT));
    }

    void addBackButton(Runnable back) {
        Button button = new Button().noText().addPreIcon(AETextures.icon(ECOIcon.BACK));
        button.setId("computation-settings-back");
        button.buttonStyle(style -> style.baseTexture(AETextures.icon(ECOIcon.TAB_BUTTON_BACKGROUND))
            .hoverTexture(AETextures.icon(ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS))
            .pressedTexture(AETextures.icon(ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS)));
        HostElements.absolute(button, ComputationCpuPanel.WIDTH - 26, -5, 20, 20);
        button.layout(layout -> layout.paddingAll(0));
        HostElements.absolute(button.getChildren().getFirst(), 2, 2, 16, 16);
        HostElements.tooltips(button, setting("back"));
        button.setOnClick(event -> back.run());
        addChild(button);
    }

    private void toggle(String key, int y, BooleanSupplier enabled, Runnable action) {
        Button button = new Button().noText();
        button.setId("computation-settings-" + key);
        HostElements.absolute(button, 14, y, 22, 12);
        button.layout(layout -> layout.paddingAll(0));
        BindableValue<Boolean> value = new BindableValue<>(enabled.getAsBoolean());
        value.bind(DataBindingBuilder.boolS2C(enabled::getAsBoolean).build());
        value.registerValueListener(state -> toggleTexture(button, Boolean.TRUE.equals(state)));
        value.setDisplay(false);
        button.addChild(value);
        toggleTexture(button, enabled.getAsBoolean());
        button.setOnServerClick(event -> action.run());
        HostElements.tooltips(button, setting(key));
        addChild(button);
        label(44, y + 2, 204, 25, () -> setting(key));
    }

    private static void toggleTexture(Button button, boolean enabled) {
        IGuiTexture normal = checkbox(0, enabled ? 40 : 28);
        IGuiTexture hover = checkbox(22, enabled ? 40 : 28);
        button.buttonStyle(style -> style.baseTexture(normal).hoverTexture(hover).pressedTexture(hover));
    }

    private static IGuiTexture checkbox(int x, int y) {
        return SpriteTexture.of(AppEng.makeId("textures/guis/checkbox.png")).setSprite(x, y, 22, 12);
    }

    private void label(int x, int y, int width, int height, Supplier<Component> text) {
        Label label = HostElements.textSegment(text, () -> 0x3F3D52);
        label.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.WRAP));
        addChild(HostElements.absolute(label, x, y, width, height));
    }

    private static Component setting(String key) {
        return Component.translatable("gui.neoecoae.computation.settings." + key);
    }
}
