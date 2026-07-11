package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.animation.Transition;
import com.lowdragmc.lowdraglib2.utils.animation.Animation;
import com.lowdragmc.lowdraglib2.math.interpolate.Eases;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Supplier;
import java.util.Map;
import java.text.NumberFormat;
import java.util.Locale;

public final class StorageInterfaceUI {
    private static final int STATUS_CONNECTED = 0x55CC77;
    private static final int STATUS_DISCONNECTED = 0xDD5555;
    private static final int INFINITE_VALUE = 0xCA6CFF;

    private StorageInterfaceUI() {}

    public static ModularUI create(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface, Player player) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(224).height(116).paddingAll(8).gapAll(6).flexDirection(FlexDirection.COLUMN);
        }).addClass("panel_bg");

        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.storage_interface.title")));
        UIElement contentFrame = new UIElement().layout(layout -> {
            layout.widthPercent(100).flex(1).paddingAll(8).gapAll(5).flexDirection(FlexDirection.COLUMN);
        }).style(style -> style.backgroundTexture(Sprites.BORDER_THICK_RT1));
        contentFrame.addChild(modeSelector(storageInterface));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.structure")
            .append(": ").append(Component.translatable(storageInterface.isInfiniteTransferAvailable()
                ? "gui.neoecoae.storage_interface.infinite_ready" : "gui.neoecoae.storage_interface.infinite_unavailable"))));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.network")
            .append(": ").append(Component.translatable(storageInterface.isTargetOnline()
                ? "gui.neoecoae.storage_interface.connected" : "gui.neoecoae.storage_interface.disconnected")
                .withColor(storageInterface.isTargetOnline() ? STATUS_CONNECTED : STATUS_DISCONNECTED))));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.transfer_prefix")
            .append(Component.literal(NumberFormat.getIntegerInstance(Locale.ROOT)
                .format(storageInterface.getTransferredLastTick())).withColor(INFINITE_VALUE))
            .append(Component.translatable("gui.neoecoae.storage_interface.transfer_suffix"))));
        root.addChild(contentFrame);

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }

    private static UIElement modeSelector(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        final int segmentWidth = 44;
        final int selectorHeight = 16;
        ToggleGroupElement group = new ToggleGroupElement();
        group.layout(layout -> {
            layout.width(segmentWidth * 3).height(selectorHeight).gapAll(0).flexDirection(FlexDirection.ROW);
            layout.marginLeft(30);
        }).style(style -> style.backgroundTexture(Sprites.RECT_RD_DARK));

        UIElement slider = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(storageInterface.getStorageInterfaceMode().ordinal() * segmentWidth);
            layout.top(0).width(segmentWidth).height(selectorHeight);
        }).style(style -> {
            style.backgroundTexture(Sprites.RECT_RD);
            style.transition(new Transition(Map.of(
                LayoutProperties.LEFT, new Animation(0.12F, 0, Eases.QUAD_OUT)
            )));
        });
        group.addChild(slider);

        Toggle storage = modeToggle(storageInterface, ECOStorageInterfaceMode.STORAGE,
            "gui.neoecoae.storage_interface.mode.storage", segmentWidth, selectorHeight);
        Toggle input = modeToggle(storageInterface, ECOStorageInterfaceMode.INPUT,
            "gui.neoecoae.storage_interface.mode.input", segmentWidth, selectorHeight);
        Toggle output = modeToggle(storageInterface, ECOStorageInterfaceMode.OUTPUT,
            "gui.neoecoae.storage_interface.mode.output", segmentWidth, selectorHeight);
        group.addChildren(storage, input, output);
        group.addEventListener(UIEvents.TICK, event -> {
            ECOStorageInterfaceMode mode = storageInterface.getStorageInterfaceMode();
            storage.setOn(mode == ECOStorageInterfaceMode.STORAGE, false);
            input.setOn(mode == ECOStorageInterfaceMode.INPUT, false);
            output.setOn(mode == ECOStorageInterfaceMode.OUTPUT, false);
            slider.layout(layout -> layout.left(mode.ordinal() * segmentWidth));
        });
        return group;
    }

    private static Toggle modeToggle(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface,
                                     ECOStorageInterfaceMode mode, String key, int width, int height) {
        Toggle toggle = new Toggle().noText();
        toggle.toggleStyle(style -> style
            .baseTexture(IGuiTexture.EMPTY)
            .hoverTexture(Sprites.RECT_RD_T)
            .markTexture(IGuiTexture.EMPTY)
            .unmarkTexture(IGuiTexture.EMPTY));
        toggle.toggleButton(button -> {
            button.noText();
            button.setOnServerClick(event -> storageInterface.setStorageInterfaceMode(mode));
            button.layout(layout -> layout.width(width).height(height));
            Label label = new Label();
            label.setText(Component.translatable(key));
            label.textStyle(style -> style
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
            label.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0).top(0).width(width).height(height);
            });
            button.addChild(label);
        });
        toggle.layout(layout -> layout.width(width).height(height).paddingAll(0));
        return toggle;
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        label.layout(layout -> layout.height(12));
        return label;
    }

    private static Label statusLabel(Supplier<Component> text) {
        Label label = boundLabel(text);
        label.layout(layout -> layout.height(12).marginLeft(2));
        return label;
    }
}
