package cn.dancingsnow.neoecoae.gui.common;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class CraftingPlanningModeButton {
    private CraftingPlanningModeButton() {
    }

    public static Button create(
        BooleanSupplier ignoringSubstitutions,
        IntSupplier substitutionPatternCount,
        Runnable toggle,
        int size
    ) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPreIcon(planningModeIcon(ignoringSubstitutions.getAsBoolean()));
        button.buttonStyle(style -> style
            .baseTexture(Sprites.RECT_RD)
            .hoverTexture(Sprites.RECT_RD_LIGHT)
            .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-planning-mode-button");
        button.layout(layout -> layout.width(size).height(size));

        UIElement icon = button.getChildren().getFirst();
        button.setOnServerClick(event -> toggle.run());

        BindableValue<Boolean> syncedMode = new BindableValue<>(ignoringSubstitutions.getAsBoolean());
        syncedMode.bind(DataBindingBuilder.boolS2C(ignoringSubstitutions::getAsBoolean).build());
        syncedMode.registerValueListener(value -> updateIcon(icon, Boolean.TRUE.equals(value)));
        syncedMode.setDisplay(false);
        button.addChild(syncedMode);

        BindableValue<Integer> syncedPatternCount = new BindableValue<>(substitutionPatternCount.getAsInt());
        syncedPatternCount.bind(DataBindingBuilder.intValS2C(substitutionPatternCount::getAsInt).build());
        syncedPatternCount.setDisplay(false);
        button.addChild(syncedPatternCount);

        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(List.of(
                Component.translatable(Boolean.TRUE.equals(syncedMode.getValue())
                    ? "gui.neoecoae.crafting.planning.ignore_substitutions.on"
                    : "gui.neoecoae.crafting.planning.ignore_substitutions.off"),
                Component.translatable(
                    "gui.neoecoae.crafting.planning.substitution_pattern_count",
                    Math.max(0, syncedPatternCount.getValue() == null ? 0 : syncedPatternCount.getValue()))
            ), null, null, null));
        return button;
    }

    private static void updateIcon(UIElement icon, boolean ignoringSubstitutions) {
        icon.style(style -> style.backgroundTexture(planningModeIcon(ignoringSubstitutions)));
    }

    private static IGuiTexture planningModeIcon(boolean ignoringSubstitutions) {
        return AETextures.icon(ignoringSubstitutions
            ? Icon.SUBSTITUTION_DISABLED
            : Icon.SUBSTITUTION_ENABLED);
    }
}
