package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Standard AE2 side button with a server-owned, persisted creative-input setting. */
public final class CreativeStorageInputButton {
    private CreativeStorageInputButton() {}

    public static Button create(BooleanSupplier ignoring, Runnable toggle) {
        Button button = HostSideButtonBar.createButton().noText().addPreIcon(icon(ignoring.getAsBoolean()));
        var image = button.getChildren().getFirst();
        button.setOnServerClick(event -> toggle.run());
        BindableValue<Boolean> synced = new BindableValue<>(ignoring.getAsBoolean());
        synced.bind(DataBindingBuilder.boolS2C(ignoring::getAsBoolean).build());
        synced.registerValueListener(value -> image.style(style ->
            style.backgroundTexture(icon(Boolean.TRUE.equals(value)))));
        synced.setDisplay(false);
        button.addChild(synced);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(List.of(
                Component.translatable(Boolean.TRUE.equals(synced.getValue())
                    ? "gui.neoecoae.storage_interface.ignore_creative.on"
                    : "gui.neoecoae.storage_interface.ignore_creative.off"),
                Component.translatable("gui.neoecoae.storage_interface.ignore_creative.description")
            ), null, null, null));
        return button;
    }

    private static IGuiTexture icon(boolean ignoring) {
        return SpriteTexture.of(NeoECOAE.id("textures/gui/ignore_creative_" + (ignoring ? "on" : "off") + ".png"));
    }
}
