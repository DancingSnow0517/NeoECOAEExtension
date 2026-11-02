package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SmartCoolingModuleUI {
    private SmartCoolingModuleUI() {}

    public static ModularUI create(BlockUIMenuType.BlockUIHolder holder,
        ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        return ECOModularUI.create(createRoot(host), holder.player);
    }

    static UIElement createRoot(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        UIElement root = new UIElement();
        root.setId("smart-cooling-root");
        root.layout(layout -> layout.width(250).height(218).paddingAll(0));
        root.style(style -> style.backgroundTexture(NETextures.BACKGROUND));
        root.addChild(text("title", "block.neoecoae.large_integrated_working_station_smart_cooling_module",
            10, 8, 230, 22));

        UIElement settings = card("settings", 34, 38);
        settings.addChild(text("target-label", "gui.neoecoae.smart_cooling.target", 8, 6, 154, 26));
        TextField target = new TextField();
        target.setId("target");
        target.setNumbersOnlyInt(0, 99);
        target.setTextResponder(text -> setTarget(host, text));
        target.bind(DataBindingBuilder.string(() -> String.valueOf(host.getTargetTemperature()),
            text -> setTarget(host, text)).build());
        target.textFieldStyle(style -> style.textColor(0xF2F2F2).textShadow(false));
        settings.addChild(place(target, 172, 10, 50, 18));
        root.addChild(settings);

        UIElement statusCard = card("status-card", 76, 32);
        TextElement status = text("status", "gui.neoecoae.smart_cooling.unformed", 8, 6, 218, 20);
        BindableValue<Component> synchronizedStatus = new BindableValue<>(status(host));
        synchronizedStatus.setId("status-sync");
        synchronizedStatus.setDisplay(false);
        synchronizedStatus.registerValueListener(status::setText);
        synchronizedStatus.bind(DataBindingBuilder.componentS2C(() -> status(host)).build());
        status.setText(synchronizedStatus.getValue());
        root.addChild(synchronizedStatus);
        statusCard.addChild(status);
        root.addChild(statusCard);

        UIElement buffer = card("buffer", 112, 42);
        buffer.addChild(text("buffer-label", "gui.neoecoae.fluid_input_hatch.stock", 8, 5, 218, 10));
        TextElement amount = text("buffer-amount", "gui.neoecoae.fluid_input_hatch.stored", 32, 19, 194, 18);
        FluidSlot coolant = new FluidSlot() {
            @Override
            public FluidSlot setValue(FluidStack value, boolean notify) {
                super.setValue(value, notify);
                // Server synchronization is silent; value listeners only run for notified edits.
                amount.setText(Component.translatable("gui.neoecoae.fluid_input_hatch.stored",
                    String.format(Locale.ROOT, "%,d", getValue().getAmount())));
                return this;
            }
        }.bind(host.tank, 0)
            .setAllowClickFilled(false).setAllowClickDrained(true);
        coolant.setId("coolant");
        coolant.amountLabel.setDisplay(false);
        coolant.style(style -> style.backgroundTexture(NETextures.ITEM_SLOT));
        amount.setText(Component.translatable("gui.neoecoae.fluid_input_hatch.stored",
            String.format(Locale.ROOT, "%,d", coolant.getValue().getAmount())));
        buffer.addChild(place(coolant, 8, 18, 18, 18));
        buffer.addChild(amount);
        root.addChild(buffer);

        root.addChild(text("help", "gui.neoecoae.smart_cooling.help", 10, 160, 230, 50));
        return root;
    }

    private static UIElement card(String id, int top, int height) {
        UIElement card = new UIElement();
        card.setId(id);
        card.style(style -> style.backgroundTexture(NETextures.CARD_BACKGROUND));
        return place(card, 8, top, 234, height);
    }

    private static TextElement text(String id, String key, int x, int y, int width, int height) {
        TextElement text = new TextElement();
        text.setId(id);
        // TextElement does not reserve its rendered height by default. Bound both axes and wrap
        // translations inside their own region so a status update cannot overlap another control.
        text.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false)
            .textWrap(TextWrap.WRAP).textColor(0x403E53).textShadow(false));
        place(text, x, y, width, height);
        text.setText(key, true);
        return text;
    }

    private static <T extends UIElement> T place(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(x).top(y).width(width).height(height).paddingAll(0));
        return element;
    }

    private static Component status(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host) {
        var cluster = host.getCluster();
        var controller = cluster == null ? null : cluster.getController();
        if (controller == null) return Component.translatable("gui.neoecoae.smart_cooling.unformed");
        if (!controller.isTemperatureEnabled()) return Component.translatable("gui.neoecoae.smart_cooling.coolant_mode");
        return Component.translatable("gui.neoecoae.smart_cooling.status",
            String.format(Locale.ROOT, "%.2f", controller.getTemperature()),
            controller.getSmartCoolingStatusText());
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
