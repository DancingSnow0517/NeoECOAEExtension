package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
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
        BindableValue<Boolean> synced = HostElements.hiddenValue(button, ignoring.getAsBoolean(),
            DataBindingBuilder.boolS2C(ignoring::getAsBoolean).build(),
            value -> image.style(style ->
                style.backgroundTexture(icon(Boolean.TRUE.equals(value)))));
        HostElements.tooltips(button, () -> List.of(
            Component.translatable(Boolean.TRUE.equals(synced.getValue())
                ? "gui.neoecoae.storage_interface.ignore_creative.on"
                : "gui.neoecoae.storage_interface.ignore_creative.off"),
            Component.translatable("gui.neoecoae.storage_interface.ignore_creative.description")
        ));
        return button;
    }

    private static IGuiTexture icon(boolean ignoring) {
        return SpriteTexture.of(NeoECOAE.id("textures/gui/widget/ignore_creative_" + (ignoring ? "on" : "off") + ".png"));
    }
}
