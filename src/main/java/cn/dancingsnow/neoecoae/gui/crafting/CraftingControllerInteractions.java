package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider.Part;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTextureState;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTextureSync;
import cn.dancingsnow.neoecoae.gui.common.CraftingPlanningModeButton;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI.Config;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Controller buttons and hover interactions; machine actions are supplied by the host. */
final class CraftingControllerInteractions {
    private CraftingControllerInteractions() {}

    /**
     * Creates a mode button without imposing a position.  Resource-backed UI definitions own
     * the geometry; the overload below remains for callers that still use the legacy helper.
     */
    static Button createModeButton(String mode, BooleanSupplier enabled, Runnable toggle) {
        return createModeButton(mode, enabled, toggle, () -> null,
            new HostPanelTextureSync(() -> null, HostPanelTextureState.Panel.CRAFTING));
    }

    static Button createModeButton(String mode, BooleanSupplier enabled, Runnable toggle,
        Supplier<IECOTier> tier, HostPanelTextureSync textures) {
        Button button = new Button().noText().setOnServerClick(event -> {
            if (event.button == 0) toggle.run();
        });
        // The JSON definition supplies position and size; preserve the native zero-padding
        // contract here so the atlas sprite remains pixel aligned in either path.
        button.layout(layout -> layout.paddingAll(0));
        button.addClass("eco-crafting-" + mode + "-button");
        BindableValue<Boolean> synced = new BindableValue<>(enabled.getAsBoolean());
        synced.bind(DataBindingBuilder.boolS2C(enabled::getAsBoolean).build());
        int index = "active_cooling".equals(mode) ? 0 : 1;
        synced.registerValueListener(value -> updateModeButtonStyle(button, Boolean.TRUE.equals(value), index, tier, textures.get()));
        textures.onChange(() -> updateModeButtonStyle(button, Boolean.TRUE.equals(synced.getValue()), index, tier, textures.get()));
        synced.setDisplay(false);
        button.addChild(synced);
        updateModeButtonStyle(button, Boolean.TRUE.equals(synced.getValue()), index, tier, textures.get());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty().append(
            Component.translatable("gui.neoecoae.crafting." + mode
                + (Boolean.TRUE.equals(synced.getValue()) ? ".on" : ".off"))));
        return button;
    }

    static Button createModeButton(int x, String mode, BooleanSupplier enabled, Runnable toggle) {
        Button button = createModeButton(mode, enabled, toggle);
        button.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(x).top(96).width(15).height(26).paddingAll(0));
        return button;
    }

    private static void updateModeButtonStyle(Button button, boolean enabled, int index, Supplier<IECOTier> tier,
        HostPanelTextureState textures) {
        int sourceX = enabled ? 0 : 30;
        var normalImage = textures.texture(Part.CRAFTING_MODE, index, enabled, false);
        var hoverImage = textures.texture(Part.CRAFTING_MODE, index, enabled, true);
        IGuiTexture normal = normalImage == null ? NETextures.Crafting.widget(sourceX, 89, 15, 26, tier)
            : SpriteTexture.of(normalImage).setSprite(0, 0, 15, 26);
        IGuiTexture hovered = hoverImage == null ? NETextures.Crafting.widget(sourceX + 15, 89, 15, 26, tier)
            : SpriteTexture.of(hoverImage).setSprite(0, 0, 15, 26);
        button.buttonStyle(style -> style.baseTexture(normal).hoverTexture(hovered).pressedTexture(hovered));
    }

    static List<Button> createToolbarButtons(Config config) {
        Button frequency = HostSideButtonBar.createButton().noText().addPreIcon(AETextures.icon(ECOIcon.SCHEDULING_ROUND_ROBIN))
            .setOnServerClick(event -> {
                if (event.button == 0) config.adjustNetworkFrequency().accept(1);
                else if (event.button == 1) config.adjustNetworkFrequency().accept(-1);
            });
        frequency.buttonStyle(style -> style.baseTexture(NETextures.RECT_RD)
            .hoverTexture(NETextures.RECT_RD_LIGHT).pressedTexture(NETextures.RECT_RD_DARK));
        frequency.layout(layout -> layout.width(16).height(16));
        BindableValue<Component> frequencyTip = new BindableValue<>(HostElements.networkFrequencyTooltip(config.networkFrequency().getAsInt()));
        frequencyTip.bind(DataBindingBuilder.componentS2C(() -> HostElements.networkFrequencyTooltip(config.networkFrequency().getAsInt())).build());
        frequencyTip.setDisplay(false);
        frequency.addChild(frequencyTip);
        frequency.addEventListener(UIEvents.HOVER_TOOLTIPS,
            event -> event.hoverTooltips = HoverTooltips.empty().append(frequencyTip.getValue()));
        return List.of(CraftingPlanningModeButton.create(
            config.ignoringPatternSubstitutions(), config.substitutionPatternCount(),
            config.toggleIgnoringPatternSubstitutions(), 16), frequency);
    }

    static UIElement createMaintenanceStatusIcon(Supplier<Component> status) {
        BindableValue<Component> syncedStatus = new BindableValue<>(status.get());
        syncedStatus.bind(DataBindingBuilder.componentS2C(status).build());
        syncedStatus.setDisplay(false);
        UIElement icon = new UIElement().addChild(syncedStatus);
        SpriteTexture normal = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information.png"));
        SpriteTexture highlighted = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information_highlight.png"));
        UIElement information = new UIElement().style(style -> style.backgroundTexture(normal))
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(3).width(21).height(21));
        icon.addChild(information);
        icon.style(style -> style.backgroundTexture(SpriteTexture.of(
            NeoECOAE.id("textures/gui/common/side_bar/gray_mirrored_button_slot_up.png"))));
        // Keep the hover and tooltip hitbox on the visible information button.  The outer
        // element only supplies the 23x30 decorative slot frame and is intentionally larger
        // than the 21x21 icon.
        information.addEventListener(UIEvents.MOUSE_ENTER, event -> information.style(style -> style.backgroundTexture(highlighted)));
        information.addEventListener(UIEvents.MOUSE_LEAVE, event -> information.style(style -> style.backgroundTexture(normal)));
        icon.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).right(-21).top(0).width(23).height(30));
        information.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
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
}
