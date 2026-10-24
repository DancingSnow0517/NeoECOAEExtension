package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Standard AE2 side button with a server-owned, persisted storage-bus exclusion setting. */
public final class StorageBusExclusionButton {
    private StorageBusExclusionButton() {}

    public static Button create(BooleanSupplier ignoring, Runnable toggle) {
        Button button = HostSideButtonBar.createButton().noText().addPreIcon(icon(ignoring.getAsBoolean()));
        var image = cn.dancingsnow.neoecoae.platform.JdkCompat.first(button.getChildren());
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
                    ? "gui.neoecoae.storage_interface.exclude_storage_bus.on"
                    : "gui.neoecoae.storage_interface.exclude_storage_bus.off"),
                Component.translatable("gui.neoecoae.storage_interface.exclude_storage_bus.description")
            ), null, null, null));
        return button;
    }

    private static IGuiTexture icon(boolean ignoring) {
        return SpriteTexture.of(NeoECOAE.id("textures/gui/widget/exclude_storage_bus_" + (ignoring ? "on" : "off") + ".png"));
    }
}
