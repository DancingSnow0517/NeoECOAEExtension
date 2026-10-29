package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import net.minecraft.network.chat.Component;

public final class SmartCoolingModuleUI {
    private SmartCoolingModuleUI() {}

    public static ModularUI create(BlockUIMenuType.BlockUIHolder holder,
        ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        UIElement root = new UIElement();
        root.layout(layout -> layout.width(250).paddingAll(8).gapAll(5));
        root.style(style -> style.backgroundTexture(NETextures.BACKGROUND));
        root.addChild(new TextElement().setText("block.neoecoae.large_integrated_working_station_smart_cooling_module", true));
        root.addChild(new TextElement().setText("gui.neoecoae.smart_cooling.target", true));
        TextField target = new TextField();
        target.setNumbersOnlyInt(0, 99);
        target.setTextResponder(text -> setTarget(host, text));
        target.bind(DataBindingBuilder.string(() -> String.valueOf(host.getTargetTemperature()),
            text -> setTarget(host, text)).build());
        target.layout(layout -> layout.width(70).height(16));
        root.addChild(target);

        TextElement status = new TextElement();
        BindableValue<Component> synchronizedStatus = new BindableValue<>(status(host));
        synchronizedStatus.setDisplay(false);
        synchronizedStatus.registerValueListener(status::setText);
        synchronizedStatus.bind(DataBindingBuilder.componentS2C(() -> status(host)).build());
        status.setText(synchronizedStatus.getValue());
        root.addChild(synchronizedStatus);
        root.addChild(status);
        root.addChild(new FluidSlot().bind(host.tank, 0).setAllowClickFilled(false).setAllowClickDrained(true));
        root.addChild(new TextElement().setText("gui.neoecoae.smart_cooling.help", true));
        return ECOModularUI.create(root, holder.player);
    }

    private static Component status(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        var cluster = host.getCluster();
        var controller = cluster == null ? null : cluster.getController();
        if (controller == null) return Component.translatable("gui.neoecoae.smart_cooling.unformed");
        if (!controller.isTemperatureEnabled()) return Component.translatable("gui.neoecoae.smart_cooling.coolant_mode");
        return Component.translatable("gui.neoecoae.smart_cooling.status",
            String.format(java.util.Locale.ROOT, "%.2f", controller.getTemperature()),
            Component.translatable(controller.isActiveCooling()
                ? "gui.neoecoae.smart_cooling.running" : "gui.neoecoae.smart_cooling.standby"));
    }

    private static void setTarget(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host, String text) {
        if (host.getLevel() == null || host.getLevel().isClientSide) return;
        try {
            host.setTargetTemperature(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            // Partial edits keep the last valid server-side setting.
        }
    }
}
