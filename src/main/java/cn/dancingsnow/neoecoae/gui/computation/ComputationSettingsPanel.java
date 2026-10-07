package cn.dancingsnow.neoecoae.gui.computation;

import appeng.core.AppEng;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Terminal-style settings page inside the same menu, preserving the selected CPU and inventory. */
final class ComputationSettingsPanel extends UIElement {
    private static final int HEIGHT = 132;
    private static final IGuiTexture BACKGROUND = SpriteTexture.of(AppEng.makeId("textures/guis/background.png"))
        .setSprite(0, 0, 256, 256).setBorder(4, 4, 4, 4);

    ComputationSettingsPanel(ComputationHostPanelUI.Config config) {
        setId("computation-settings");
        HostElements.absolute(this, 0, (ComputationCpuPanel.HEIGHT - HEIGHT) / 2, ComputationCpuPanel.WIDTH, HEIGHT);
        setOverflowVisible(true);
        style(style -> style.backgroundTexture(BACKGROUND));
        label(8, 8, 214, 14, () -> setting("title"));
        toggle("substitutions", 32, () -> !config.ignoringPatternSubstitutions().getAsBoolean(), config.toggleIgnoringPatternSubstitutions());
        label(42, 48, 207, 11, () -> Component.translatable("gui.neoecoae.crafting.planning.substitution_pattern_count",
            Math.max(0, config.substitutionPatternCount().getAsInt())));
        toggle("cycle_planning", 72, config.cyclePlanningEnabled(), config.toggleCyclePlanning());
        toggle("fast_planner", 104, config.fastPlannerEnabled(), config.toggleFastPlanner());
    }

    void addBackButton(Runnable back) {
        Button button = new BackButton().noText();
        button.setId("computation-settings-back");
        HostElements.absolute(button, ComputationCpuPanel.WIDTH - 24, -5, 20, 20);
        button.layout(layout -> layout.paddingAll(0));
        button.setOverflowVisible(true);
        HostElements.tooltips(button, setting("back"));
        button.setOnClick(event -> back.run());
        addChild(button);
    }

    private static final class BackButton extends Button {
        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            // AE2's box tab changes only with focus and draws both sprites at native size.
            ECOIcon background = isFocused() ? ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS : ECOIcon.TAB_BUTTON_BACKGROUND;
            context.drawTexture(AETextures.icon(background), getPositionX(), getPositionY(), background.width, background.height);
            context.drawTexture(AETextures.icon(ECOIcon.BACK), getPositionX() + 2, getPositionY() + 1, 16, 16);
        }
    }

    private void toggle(String key, int y, BooleanSupplier enabled, Runnable action) {
        Button button = new Button().noText();
        button.setId("computation-settings-" + key);
        HostElements.absolute(button, 10, y, 22, 12);
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
        label(42, y + 2, 207, 14, () -> setting(key));
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
