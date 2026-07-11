package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Supplier;

public final class StorageInterfaceUI {
    private StorageInterfaceUI() {}

    public static ModularUI create(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface, Player player) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(224).height(116).paddingAll(8).gapAll(6).flexDirection(FlexDirection.COLUMN);
        }).addClass("panel_bg");

        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.storage_interface.title")));
        UIElement buttons = new UIElement().layout(layout -> {
            layout.height(20).gapAll(6).flexDirection(FlexDirection.ROW);
        });
        buttons.addChildren(
            modeButton(storageInterface, ECOStorageInterfaceMode.STORAGE, "gui.neoecoae.storage_interface.mode.storage"),
            modeButton(storageInterface, ECOStorageInterfaceMode.INPUT, "gui.neoecoae.storage_interface.mode.input"),
            modeButton(storageInterface, ECOStorageInterfaceMode.OUTPUT, "gui.neoecoae.storage_interface.mode.output")
        );
        root.addChild(buttons);
        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.storage_interface.structure")
            .append(": ").append(Component.translatable(storageInterface.isInfiniteTransferAvailable()
                ? "gui.neoecoae.storage_interface.infinite_ready" : "gui.neoecoae.storage_interface.infinite_unavailable"))));
        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.storage_interface.network")
            .append(": ").append(Component.translatable(storageInterface.isTargetOnline()
                ? "gui.neoecoae.storage_interface.connected" : "gui.neoecoae.storage_interface.disconnected"))));
        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.storage_interface.transfer",
            storageInterface.getTransferredLastTick())));

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }

    private static Button modeButton(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface,
                                     ECOStorageInterfaceMode mode, String key) {
        Button button = new Button();
        button.setText(key, true);
        button.setOnServerClick(event -> storageInterface.setStorageInterfaceMode(mode));
        button.layout(layout -> layout.width(64).height(20));
        return button;
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = new Label();
        label.bind(DataBindingBuilder.componentS2C(text).build());
        label.layout(layout -> layout.height(12));
        return label;
    }
}
